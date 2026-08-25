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
        chat_history=[],
        documents=[],
        answer="",
    )
    defaults.update(kwargs)
    return defaults  # type: ignore[return-value]


# ── contextualize_question ───────────────────────────────────────────────────

def test_contextualize_question_no_history_skips_llm():
    state = _state(original_question="What is en passant?", chat_history=[])
    out = contextualize_question(state)
    assert out["rewritten_question"] == "What is en passant?"


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


# ── route_question ───────────────────────────────────────────────────────────

def test_route_question_returns_type():
    mock_result = Mock(content="rag")
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = route_question(_state(rewritten_question="What is rule 3.8.2 in chess?"))

    assert out["question_type"] == "rag"


def test_route_question_defaults_to_rag_on_unknown():
    mock_result = Mock(content="unknown_type")
    mock_llm = MagicMock()
    mock_llm.invoke.return_value = mock_result

    with patch("app.rag.nodes.get_router_llm", return_value=mock_llm):
        out = route_question(_state(rewritten_question="Something random"))

    assert out["question_type"] == "rag"


# ── retrieve_docs ─────────────────────────────────────────────────────────────

def test_retrieve_docs_returns_documents():
    docs = [Document(page_content="fact one"), Document(page_content="fact two")]
    with patch("app.rag.nodes.retrieve", return_value=docs):
        out = retrieve_docs(_state(rewritten_question="how to move a knight?"))
    assert out["documents"] == docs


# ── generate_rag ──────────────────────────────────────────────────────────────

def test_generate_rag_invokes_prompt_chain_and_appends_history():
    chain = MagicMock()
    chain.__or__.return_value = chain
    chain.invoke.return_value = "RAG answer"

    state = _state(
        original_question="original",
        rewritten_question="rewritten chess rule Q",
        documents=[Document(page_content="doc content")],
    )
    with (
        patch("app.rag.nodes.RAG_PROMPT", chain),
        patch("app.rag.nodes.get_llm", return_value=Mock()),
    ):
        out = generate_rag(state)

    assert out["answer"] == "RAG answer"
    assert any(isinstance(m, HumanMessage) for m in out["chat_history"])
    assert any(isinstance(m, AIMessage) for m in out["chat_history"])


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

