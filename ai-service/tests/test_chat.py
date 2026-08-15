import uuid
from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest
from langchain_core.documents import Document
from langchain_core.messages import AIMessage, HumanMessage

from app.graph.nodes import analyze_question, generate_direct, generate_rag, retrieve_docs
from app.graph.state import RagState
from app.services.chat_service import save_message


# ── Helpers ──────────────────────────────────────────────────────────────────

def _state(**kwargs) -> RagState:
    defaults = dict(
        original_question="",
        rewritten_question="",
        question_type="chess",
        chat_history=[],
        documents=[],
        answer="",
    )
    defaults.update(kwargs)
    return defaults  # type: ignore[return-value]


# ── analyze_question ──────────────────────────────────────────────────────────

def test_analyze_question_returns_type():
    mock_result = Mock(category="chess")
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value.invoke.return_value = mock_result

    with patch("app.graph.nodes.get_router_llm", return_value=mock_llm):
        out = analyze_question(_state(original_question="What is en passant?"))

    assert out["question_type"] == "chess"


def test_analyze_question_includes_history_in_prompt():
    mock_result = Mock(category="system")
    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value.invoke.return_value = mock_result

    history = [HumanMessage("prev question"), AIMessage("prev answer")]
    with patch("app.graph.nodes.get_router_llm", return_value=mock_llm):
        analyze_question(_state(original_question="follow up", chat_history=history))

    prompt_arg = mock_llm.with_structured_output.return_value.invoke.call_args.args[0]
    assert "prev question" in prompt_arg
    assert "follow up" in prompt_arg


# ── retrieve_docs ─────────────────────────────────────────────────────────────

def test_retrieve_docs_returns_page_contents():
    docs = [Document(page_content="fact one"), Document(page_content="fact two")]
    with patch("app.graph.nodes.retrieve", return_value=docs):
        out = retrieve_docs(_state(rewritten_question="how to report a bug?"))
    assert out["documents"] == docs


# ── generate_rag ──────────────────────────────────────────────────────────────

def test_generate_rag_invokes_prompt_chain_and_appends_history():
    chain = MagicMock()
    chain.__or__.return_value = chain
    chain.invoke.return_value = "RAG answer"

    state = _state(
        original_question="original",
        rewritten_question="rewritten platform Q",
        documents=[Document(page_content="doc content")],
    )
    with (
        patch("app.graph.nodes.RAG_PROMPT", chain),
        patch("app.graph.nodes.get_llm", return_value=Mock()),
    ):
        out = generate_rag(state)

    assert out["answer"] == "RAG answer"
    assert any(isinstance(m, HumanMessage) for m in out["chat_history"])
    assert any(isinstance(m, AIMessage) for m in out["chat_history"])


# ── generate_direct ───────────────────────────────────────────────────────────

def test_generate_direct_calls_llm_and_appends_history():
    mock_response = Mock(content="Chess answer")
    mock_llm = Mock(invoke=Mock(return_value=mock_response))

    state = _state(original_question="original", rewritten_question="chess Q")
    with patch("app.graph.nodes.get_llm", return_value=mock_llm):
        out = generate_direct(state)

    assert out["answer"] == "Chess answer"
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

    await save_message(db, sess_id, "assistant", "Answer", "chess")

    msg = db.add.call_args.args[0]
    assert msg.role == "assistant"
    assert msg.question_type == "chess"
