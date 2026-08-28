import uuid
from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest
from langchain_core.documents import Document
from langchain_core.messages import AIMessage, HumanMessage, RemoveMessage, SystemMessage


from app.rag.nodes import agent_node, should_continue, summarize_memory
from app.rag.state import RagState
from app.services.chat_service import save_message


# ── Helpers ──────────────────────────────────────────────────────────────────

def _state(**kwargs) -> RagState:
    defaults = dict(
        original_question="",
        question_type="rag",
        messages=[],
        answer="",
    )
    defaults.update(kwargs)
    return defaults  # type: ignore[return-value]


# ── summarize_memory ─────────────────────────────────────────────────────────

def test_summarize_memory_below_threshold_returns_empty():
    state = _state(messages=[HumanMessage("hi"), AIMessage("hello")])
    assert summarize_memory(state) == {}


def test_summarize_memory_above_threshold_summarizes():
    h1 = HumanMessage("m1", id="1")
    a1 = AIMessage("a1", id="2")
    h2 = HumanMessage("m2", id="3")
    a2 = AIMessage("a2", id="4")
    h3 = HumanMessage("m3", id="5")
    a3 = AIMessage("a3", id="6")

    mock_llm = MagicMock()
    mock_llm.invoke.return_value = Mock(content="Summary of turns 1 and 2")

    state = _state(messages=[h1, a1, h2, a2, h3, a3])
    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = summarize_memory(state)

    assert "messages" in out
    # 4 messages to delete + 1 new summary SystemMessage
    assert len(out["messages"]) == 5
    summary_msg = out["messages"][-1]
    assert isinstance(summary_msg, SystemMessage)
    assert "Summary of turns 1 and 2" in summary_msg.content


def test_summarize_memory_merges_existing_summary():

    old_summary = SystemMessage(content="Summary of previous conversation:\nTurn 1 summary", id="old_sum_id")
    h2 = HumanMessage("m2", id="3")
    a2 = AIMessage("a2", id="4")
    h3 = HumanMessage("m3", id="5")
    a3 = AIMessage("a3", id="6")
    h4 = HumanMessage("m4", id="7")
    a4 = AIMessage("a4", id="8")

    mock_llm = MagicMock()
    mock_llm.invoke.return_value = Mock(content="Combined summary of turn 1, 2, 3")

    state = _state(messages=[old_summary, h2, a2, h3, a3, h4, a4])
    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = summarize_memory(state)

    assert "messages" in out
    # 4 dialogue msgs + 1 old summary msg deleted + 1 new summary SystemMessage = 6 total
    assert len(out["messages"]) == 6
    delete_ids = [m.id for m in out["messages"] if isinstance(m, RemoveMessage)]
    assert "old_sum_id" in delete_ids
    summary_msg = out["messages"][-1]
    assert isinstance(summary_msg, SystemMessage)
    assert "Combined summary of turn 1, 2, 3" in summary_msg.content


def test_summarize_memory_with_clean_turns():
    h1 = HumanMessage("what is castling?", id="1")
    ai1 = AIMessage("Castling is a special move...", id="2")
    h2 = HumanMessage("what is en passant?", id="3")
    ai2 = AIMessage("En passant is a pawn capture...", id="4")
    h3 = HumanMessage("thank you!", id="5")

    mock_llm = MagicMock()
    mock_llm.invoke.return_value = Mock(content="User asked about castling and en passant.")

    state = _state(messages=[h1, ai1, h2, ai2, h3])
    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = summarize_memory(state)

    assert "messages" in out
    # 4 messages from turns 1 & 2 deleted + 1 summary msg = 5 total
    assert len(out["messages"]) == 5
    delete_ids = [m.id for m in out["messages"] if isinstance(m, RemoveMessage)]
    assert delete_ids == ["1", "2", "3", "4"]
    llm_prompt = mock_llm.invoke.call_args[0][0]
    assert "what is castling?" in llm_prompt
    assert "Castling is a special move" in llm_prompt
    assert "what is en passant?" in llm_prompt




# ── save_message ──────────────────────────────────────────────────────────────


@pytest.mark.anyio
async def test_save_message_inserts_correct_fields():
    db = MagicMock()
    db.commit = AsyncMock()
    sess_id = uuid.uuid4()

    await save_message(db, sess_id, "user", "Hello", None)

    db.add.assert_called_once()
    msg = db.add.call_args.args[0]
    assert msg.session_id == sess_id
    assert msg.role == "user"
    assert msg.content == "Hello"
    assert msg.question_type is None
    db.commit.assert_awaited_once()


@pytest.mark.anyio
async def test_save_message_stores_question_type_for_assistant():
    db = MagicMock()
    db.commit = AsyncMock()
    sess_id = uuid.uuid4()

    await save_message(db, sess_id, "assistant", "Answer", "rag")

    msg = db.add.call_args.args[0]
    assert msg.role == "assistant"
    assert msg.question_type == "rag"


# ── send_message & ConversationEngine ─────────────────────────────────────────

@pytest.mark.anyio
async def test_send_message_executes_turn_and_triggers_titling():
    from app.models.chat_session import ChatSession
    from app.services.chat_service import send_message

    db = MagicMock()
    db.commit = AsyncMock()
    session = ChatSession(id=uuid.uuid4(), user_id=uuid.uuid4(), title=None)

    mock_graph = AsyncMock()
    mock_graph.ainvoke.return_value = {"answer": "Sicilian defense is...", "question_type": "rag"}

    background_tasks = MagicMock()

    answer, q_type = await send_message(
        db=db,
        session=session,
        graph=mock_graph,
        question="What is Sicilian?",
        background_tasks=background_tasks,
    )

    assert answer == "Sicilian defense is..."
    assert q_type == "rag"
    assert db.add.call_count == 2  # user msg + assistant msg
    assert db.commit.await_count == 2
    mock_graph.ainvoke.assert_awaited_once()
    background_tasks.add_task.assert_called_once()


@pytest.mark.anyio
async def test_send_message_skips_titling_when_session_already_has_title():
    from app.models.chat_session import ChatSession
    from app.services.chat_service import send_message

    db = MagicMock()
    db.commit = AsyncMock()
    session = ChatSession(id=uuid.uuid4(), user_id=uuid.uuid4(), title="Existing Title")

    mock_graph = AsyncMock()
    mock_graph.ainvoke.return_value = {"answer": "Another answer", "question_type": "general"}

    background_tasks = MagicMock()

    answer, q_type = await send_message(
        db=db,
        session=session,
        graph=mock_graph,
        question="What about french defense?",
        background_tasks=background_tasks,
    )

    assert answer == "Another answer"
    assert q_type == "general"
    background_tasks.add_task.assert_not_called()


# ── Graph compilation with MemorySaver adapter ────────────────────────────────

def test_compile_graph_with_memory_saver():
    from langgraph.checkpoint.memory import MemorySaver
    from app.rag.builder import compile_graph

    memory_saver = MemorySaver()
    graph = compile_graph(checkpointer=memory_saver)
    assert graph is not None
    assert graph.checkpointer is memory_saver


# ── Domain-Specific Tools Tests ──────────────────────────────────────────────

def test_search_fide_rules_tool():
    from app.rag.tools import search_fide_rules

    mock_docs = [
        Document(page_content="Article 3.8.1: Castling is allowed when...", metadata={"article": "3.8.1", "title": "Castling"}),
    ]
    with patch("app.rag.tools.retrieve", return_value=mock_docs) as mock_retrieve:
        output = search_fide_rules.invoke({"query": "castling rules"})
        mock_retrieve.assert_called_once_with(query="castling rules", top_k=3, domain="chess_law")
        assert "Article: 3.8.1" in output
        assert "Article 3.8.1: Castling is allowed when..." in output


def test_search_fide_rules_tool_empty_fallback():
    from app.rag.tools import search_fide_rules

    with patch("app.rag.tools.retrieve", return_value=[]):
        output = search_fide_rules.invoke({"query": "non-existent rule"})
        assert "No relevant FIDE Laws of Chess documents found" in output


def test_search_chess_openings_tool():
    from app.rag.tools import search_chess_openings

    mock_docs = [
        Document(page_content="1. e4 c5 is the Sicilian Defense.", metadata={"eco_code": "B20", "eco_name": "Sicilian Defense"}),
    ]
    with patch("app.rag.tools.retrieve", return_value=mock_docs) as mock_retrieve:
        output = search_chess_openings.invoke({"query": "sicilian defense"})
        mock_retrieve.assert_called_once_with(query="sicilian defense", top_k=3, domain="chess_opening")
        assert "ECO: B20" in output
        assert "Opening: Sicilian Defense" in output


def test_search_chess_openings_tool_empty_fallback():
    from app.rag.tools import search_chess_openings

    with patch("app.rag.tools.retrieve", return_value=[]):
        output = search_chess_openings.invoke({"query": "random opening"})
        assert "No relevant Chess Opening Theory documents found" in output


def test_search_platform_support_tool():
    from app.rag.tools import search_platform_support

    mock_docs = [
        Document(page_content="Click 'Create Room' to host a new game.", metadata={"title": "Room Creation"}),
    ]
    with patch("app.rag.tools.retrieve", return_value=mock_docs) as mock_retrieve:
        output = search_platform_support.invoke({"query": "how to create room"})
        mock_retrieve.assert_called_once_with(query="how to create room", top_k=3, domain="system")
        assert "Title: Room Creation" in output
        assert "Click 'Create Room'" in output


def test_search_platform_support_tool_empty_fallback():
    from app.rag.tools import search_platform_support

    with patch("app.rag.tools.retrieve", return_value=[]):
        output = search_platform_support.invoke({"query": "random issue"})
        assert "No relevant platform documentation found" in output



# ── Agent Node & ReAct Logic Tests ───────────────────────────────────────────

def test_agent_node_direct_answer_without_tools():
    from app.rag.nodes import agent_node

    mock_ai_msg = AIMessage(content="Xin chào! Tôi có thể giúp gì cho bạn?")
    mock_llm = MagicMock()
    mock_llm.bind_tools.return_value.invoke.return_value = mock_ai_msg

    state = _state(original_question="Chào bạn", messages=[])
    with patch("app.rag.nodes.get_llm", return_value=mock_llm):
        out = agent_node(state)

    assert out["answer"] == "Xin chào! Tôi có thể giúp gì cho bạn?"
    assert out["question_type"] == "general"
    assert len(out["messages"]) == 1
    assert out["messages"][0] == mock_ai_msg



def test_agent_node_generates_tool_calls():
    from app.rag.nodes import agent_node

    mock_ai_msg = AIMessage(
        content="",
        tool_calls=[{"name": "search_fide_rules", "args": {"query": "en passant"}, "id": "call_123"}],
    )
    mock_llm = MagicMock()
    mock_llm.bind_tools.return_value.invoke.return_value = mock_ai_msg

    state = _state(original_question="Bắt tốt qua đường là gì?", messages=[])
    with patch("app.rag.nodes.get_llm", return_value=mock_llm):
        out = agent_node(state)

    assert "answer" not in out
    assert out["messages"][-1].tool_calls[0]["name"] == "search_fide_rules"



def test_agent_node_synthesizes_final_answer_after_tools():
    from langchain_core.messages import ToolMessage
    from app.rag.nodes import agent_node

    tool_msg = ToolMessage(content="Article 3.7.3: En passant...", tool_call_id="call_123", id="tool_msg_1")
    prev_ai_msg = AIMessage(
        content="",
        tool_calls=[{"name": "search_fide_rules", "args": {"query": "en passant"}, "id": "call_123"}],
        id="ai_call_1",
    )
    final_ai_msg = AIMessage(content="Bắt tốt qua đường theo Điều 3.7.3 là...")

    mock_llm = MagicMock()
    mock_llm.bind_tools.return_value.invoke.return_value = final_ai_msg

    state = _state(
        original_question="Bắt tốt qua đường là gì?",
        messages=[HumanMessage("Bắt tốt qua đường là gì?", id="user_1"), prev_ai_msg, tool_msg],
    )
    with patch("app.rag.nodes.get_llm", return_value=mock_llm):
        out = agent_node(state)

    assert out["answer"] == "Bắt tốt qua đường theo Điều 3.7.3 là..."
    assert out["question_type"] == "rag"
    # Ephemeral Tool Cleanup verification
    delete_ids = [m.id for m in out["messages"] if isinstance(m, RemoveMessage)]
    assert "tool_msg_1" in delete_ids
    assert "ai_call_1" in delete_ids
    assert out["messages"][-1] == final_ai_msg


def test_should_continue_logic():

    from langchain_core.messages import AIMessage
    from app.rag.nodes import should_continue

    # 1. No tool calls -> 'end'
    state_done = _state(messages=[AIMessage(content="Done!")])
    assert should_continue(state_done) == "end"

    # 2. Has tool calls -> 'tools'
    state_tool = _state(
        messages=[AIMessage(content="", tool_calls=[{"name": "search_fide_rules", "args": {}, "id": "1"}])]
    )
    assert should_continue(state_tool) == "tools"

    # 3. Empty messages -> 'end'
    assert should_continue(_state(messages=[])) == "end"


@pytest.mark.anyio
async def test_full_graph_multi_domain_parallel_tool_calling():
    from langgraph.checkpoint.memory import MemorySaver
    from app.rag.builder import compile_graph

    tool_call_msg = AIMessage(
        content="",
        tool_calls=[
            {"name": "search_fide_rules", "args": {"query": "castling rules"}, "id": "call_1"},
            {"name": "search_chess_openings", "args": {"query": "sicilian defense"}, "id": "call_2"},
        ],
    )
    final_answer_msg = AIMessage(content="Trong khai cuộc Sicilian, quy tắc nhập thành là...")

    mock_llm = MagicMock()
    mock_bound_llm = MagicMock()
    mock_llm.bind_tools.return_value = mock_bound_llm
    # First invocation returns parallel tool calls, second invocation returns synthesized answer
    mock_bound_llm.invoke.side_effect = [tool_call_msg, final_answer_msg]

    mock_fide_doc = Document(page_content="Castling rule 3.8.1", metadata={"article": "3.8.1"})
    mock_opening_doc = Document(page_content="Sicilian Defense 1.e4 c5", metadata={"eco_code": "B20"})

    def mock_retrieve_fn(query, top_k=3, domain=None):
        if domain == "chess_law":
            return [mock_fide_doc]
        if domain == "chess_opening":
            return [mock_opening_doc]
        return []

    graph = compile_graph(checkpointer=MemorySaver())

    with (
        patch("app.rag.nodes.get_llm", return_value=mock_llm),
        patch("app.rag.tools.retrieve", side_effect=mock_retrieve_fn),
    ):
        result = await graph.ainvoke(
            {
                "messages": [HumanMessage(content="Nhập thành trong Sicilian thế nào?")],
                "original_question": "Nhập thành trong Sicilian thế nào?",
            },
            {"configurable": {"thread_id": "test-multi-domain-thread"}},
        )

    assert result["answer"] == "Trong khai cuộc Sicilian, quy tắc nhập thành là..."
    assert result["question_type"] == "rag"
    assert len(result["messages"]) > 0



@pytest.mark.anyio
async def test_full_graph_multi_turn_conversation_retains_context():
    from langgraph.checkpoint.memory import MemorySaver
    from app.rag.builder import compile_graph

    # Mock responses for 2 turns
    turn1_response = AIMessage(content="Xin chào! Tôi là VieChess Master AI.")
    turn2_response = AIMessage(content="Bạn có thể hỏi tôi về luật cờ và khai cuộc.")

    mock_llm = MagicMock()
    mock_bound_llm = MagicMock()
    mock_llm.bind_tools.return_value = mock_bound_llm
    mock_bound_llm.invoke.side_effect = [turn1_response, turn2_response]

    graph = compile_graph(checkpointer=MemorySaver())
    thread_config = {"configurable": {"thread_id": "multi-turn-thread-123"}}

    with patch("app.rag.nodes.get_llm", return_value=mock_llm):
        # Turn 1
        r1 = await graph.ainvoke(
            {"messages": [HumanMessage(content="xin chào")], "original_question": "xin chào"},
            thread_config,
        )
        assert r1["answer"] == "Xin chào! Tôi là VieChess Master AI."

        # Turn 2
        r2 = await graph.ainvoke(
            {"messages": [HumanMessage(content="tôi có thể làm gì với bạn?")], "original_question": "tôi có thể làm gì với bạn?"},
            thread_config,
        )
        assert r2["answer"] == "Bạn có thể hỏi tôi về luật cờ và khai cuộc."

        # Verify second LLM call received the second question and history
        second_call_prompt = mock_bound_llm.invoke.call_args_list[1][0][0]
        question_contents = [m.content for m in second_call_prompt if isinstance(m, HumanMessage)]
        assert "xin chào" in question_contents
        assert "tôi có thể làm gì với bạn?" in question_contents




