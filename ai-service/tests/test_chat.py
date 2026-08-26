import uuid
from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest
from langchain_core.documents import Document
from langchain_core.messages import AIMessage, HumanMessage

from app.rag.nodes import (
    contextualize_question,
    generate_general,
    generate_rag,
    retrieve_docs,
    route_question,
)
from app.rag.state import RagState
from app.services.chat_service import save_message


# ── Helpers ──────────────────────────────────────────────────────────────────

def _state(**kwargs) -> RagState:
    defaults = dict(
        original_question="",
        rewritten_question="",
        question_type="rag",
        domain="all",
        chat_history=[],
        documents=[],
        answer="",
    )
    defaults.update(kwargs)
    return defaults  # type: ignore[return-value]


# ── contextualize_question ───────────────────────────────────────────────────

def test_contextualize_question_no_history_calls_llm_for_english_query():
    mock_result = Mock(content="What is en passant?")
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    state = _state(original_question="Bắt tốt qua đường là gì?", chat_history=[])
    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = contextualize_question(state)

    assert out["rewritten_question"] == "What is en passant?"
    prompt_arg = mock_llm.invoke.call_args.args[0]
    assert "Bắt tốt qua đường là gì?" in prompt_arg
    assert "No previous conversation." in prompt_arg


def test_contextualize_question_with_history_calls_llm():
    mock_result = Mock(content="What is the Sicilian Defense?")
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    history = [HumanMessage("Tell me about openings"), AIMessage("Sure!")]
    state = _state(original_question="What about Sicilian?", chat_history=history)

    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = contextualize_question(state)

    assert out["rewritten_question"] == "What is the Sicilian Defense?"
    prompt_arg = mock_llm.invoke.call_args.args[0]
    assert "Tell me about openings" in prompt_arg
    assert "What about Sicilian?" in prompt_arg


def test_contextualize_question_translates_vietnamese_query_to_english():
    mock_result = Mock(content="How does a Bishop move in chess?")
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    history = [HumanMessage("Quân tượng là gì?"), AIMessage("Quân tượng là quân cờ...")]
    state = _state(original_question="Nó đi như thế nào?", chat_history=history)

    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = contextualize_question(state)

    assert out["rewritten_question"] == "How does a Bishop move in chess?"
    prompt_arg = mock_llm.invoke.call_args.args[0]
    assert "Quân tượng là gì?" in prompt_arg
    assert "Nó đi như thế nào?" in prompt_arg



# ── route_question ───────────────────────────────────────────────────────────

def test_route_question_returns_type_and_domain():
    mock_result = Mock(content='{"question_type": "rag", "domain": "chess"}')
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = route_question(_state(rewritten_question="What is rule 3.8.2 in chess?"))

    assert out["question_type"] == "rag"
    assert out["domain"] == "chess"


def test_route_question_defaults_to_rag_and_all_on_unknown():
    mock_result = Mock(content="unknown_type")
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = route_question(_state(rewritten_question="Something random"))

    assert out["question_type"] == "rag"
    assert out["domain"] == "all"



# ── retrieve_docs ─────────────────────────────────────────────────────────────

def test_retrieve_docs_returns_documents():
    docs = [Document(page_content="fact one"), Document(page_content="fact two")]
    with patch("app.rag.nodes.retrieve", return_value=docs):
        out = retrieve_docs(_state(rewritten_question="how to move a knight?"))
    assert out["documents"] == docs


# ── generate_rag ──────────────────────────────────────────────────────────────

def test_generate_rag_invokes_prompt_chain_and_appends_history():
    chess_chain = MagicMock()
    chess_chain.__or__.return_value = chess_chain
    chess_chain.invoke.return_value = "Chess rule answer"

    system_chain = MagicMock()
    system_chain.__or__.return_value = system_chain
    system_chain.invoke.return_value = "System platform answer"

    # 1. Chess domain
    state_chess = _state(
        original_question="original",
        rewritten_question="how does king move?",
        domain="chess",
        documents=[Document(page_content="king move doc")],
    )
    with (
        patch("app.rag.nodes.RAG_CHESS_PROMPT", chess_chain),
        patch("app.rag.nodes.RAG_SYSTEM_PROMPT", system_chain),
        patch("app.rag.nodes.get_llm", return_value=Mock()),
    ):
        out_chess = generate_rag(state_chess)

    assert out_chess["answer"] == "Chess rule answer"
    chess_chain.invoke.assert_called_once()
    assert chess_chain.invoke.call_args.args[0]["question"] == "original"
    system_chain.invoke.assert_not_called()

    # 2. System domain
    state_system = _state(
        original_question="how do I create a room?",
        rewritten_question="how to create room?",
        domain="system",
        documents=[Document(page_content="create room doc")],
    )
    chess_chain.reset_mock()
    system_chain.reset_mock()
    with (
        patch("app.rag.nodes.RAG_CHESS_PROMPT", chess_chain),
        patch("app.rag.nodes.RAG_SYSTEM_PROMPT", system_chain),
        patch("app.rag.nodes.get_llm", return_value=Mock()),
    ):
        out_system = generate_rag(state_system)

    assert out_system["answer"] == "System platform answer"
    system_chain.invoke.assert_called_once()
    assert system_chain.invoke.call_args.args[0]["question"] == "how do I create a room?"
    chess_chain.invoke.assert_not_called()



# ── generate_general ─────────────────────────────────────────────────────────

def test_generate_general_calls_llm_and_appends_history():
    mock_response = Mock(content="General chess answer")
    mock_llm = Mock(invoke=Mock(return_value=mock_response))

    state = _state(original_question="original", rewritten_question="general Q")
    with patch("app.rag.nodes.get_llm", return_value=mock_llm):
        out = generate_general(state)

    assert out["answer"] == "General chess answer"
    call_arg = mock_llm.invoke.call_args.args[0]
    assert "original" in call_arg[-1].content
    assert any(isinstance(m, HumanMessage) for m in out["chat_history"])
    assert any(isinstance(m, AIMessage) for m in out["chat_history"])


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

