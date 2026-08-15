import logging
from typing import Literal

from langchain_core.messages import AIMessage, HumanMessage, RemoveMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser
from pydantic import BaseModel, Field

from app.ai.llm import get_llm, get_router_llm
from app.ai.prompts import (
    ANALYZE_PROMPT,
    CHITCHAT_SYSTEM,
    DIRECT_SYSTEM,
    RAG_PROMPT,
    REWRITE_PROMPT,
    SUMMARIZE_PROMPT,
)
from app.ai.retriever import retrieve
from app.graph.state import RagState

logger = logging.getLogger(__name__)

_HISTORY_WINDOW = 6


class QuestionCategory(BaseModel):
    category: Literal["system", "chess", "chitchat"] = Field(
        description="'system' if asking about how to use THIS website/platform (e.g., how to play a game on this site, how to create a room, matchmaking, account, buttons, UI, errors). "
                    "'chess' ONLY if asking about general real-world chess knowledge/theory (e.g., how chess pieces move in general, chess rules, openings, grandmasters) completely unrelated to this website's interface. "
                    "'chitchat' for greetings, thanks, small talk, or anything unrelated to chess or the platform."
    )


def analyze_question(state: RagState) -> dict:
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    history_text = "\n".join(f"{m.type}: {m.content}" for m in history) or "(none)"
    prompt = ANALYZE_PROMPT.format(history=history_text, question=state["original_question"])
    result = get_router_llm().with_structured_output(QuestionCategory).invoke(prompt)
    return {"question_type": result.category}


def rewrite_question(state: RagState) -> dict:
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    history_text = "\n".join(f"{m.type}: {m.content}" for m in history) or "(none)"
    prompt = REWRITE_PROMPT.format(history=history_text, question=state["original_question"])
    # Dùng router LLM (model nhỏ hơn) — rewrite là tác vụ đơn giản, không cần model lớn.
    rewritten = get_router_llm().invoke(prompt).content.strip()
    return {"rewritten_question": rewritten}


def retrieve_docs(state: RagState) -> dict:
    # Trả về list[Document] để giữ metadata (source, score) cho citation sau này.
    docs = retrieve(state["rewritten_question"], top_k=4)
    return {"documents": docs}


def generate_rag(state: RagState) -> dict:
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    context = "\n\n".join(d.page_content for d in state["documents"])
    answer = (RAG_PROMPT | get_llm() | StrOutputParser()).invoke(
        {
            "context": context,
            "history": history,
            "question": state["rewritten_question"],
        }
    )
    return {
        "answer": answer,
        # Accumulate turn in chat_history for subsequent rewrite context
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def no_context_answer(state: RagState) -> dict:
    """Short-circuit khi retrieve không tìm được document nào, tránh tốn LLM call."""
    answer = (
        "Tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong cơ sở dữ liệu. "
        "Hãy thử diễn đạt lại câu hỏi hoặc liên hệ hỗ trợ để được giúp đỡ."
    )
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_direct(state: RagState) -> dict:
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    messages = [
        SystemMessage(DIRECT_SYSTEM),
        *history,
        HumanMessage(state["original_question"]),
    ]
    answer = get_llm().invoke(messages).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_chitchat(state: RagState) -> dict:
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    messages = [
        SystemMessage(CHITCHAT_SYSTEM),
        *history,
        HumanMessage(state["original_question"]),
    ]
    answer = get_llm().invoke(messages).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def summarize_memory(state: RagState) -> dict:
    history = state.get("chat_history", [])
    # summarize_memory chạy đầu turn (trước khi generate thêm turn mới).
    # Threshold >= 6 msgs (3 Q&A pairs) tương đương cũ "> 6 sau generate".
    if len(history) < 6:
        return {}

    # Summarize all EXCEPT the last 2 (the latest turn we just added)
    messages_to_summarize = history[:-2]
    history_text = "\n".join(f"{m.type}: {m.content}" for m in messages_to_summarize)

    summary_prompt = SUMMARIZE_PROMPT.format(history=history_text)
    summary = get_router_llm().invoke(summary_prompt).content

    msgs_with_id = [m for m in messages_to_summarize if getattr(m, "id", None)]
    msgs_without_id = [m for m in messages_to_summarize if not getattr(m, "id", None)]

    if msgs_without_id:
        logger.warning(
            "summarize_memory: %d/%d messages have no id and cannot be removed from checkpointer. "
            "History may grow unbounded for this session.",
            len(msgs_without_id),
            len(messages_to_summarize),
        )

    delete_msgs = [RemoveMessage(id=m.id) for m in msgs_with_id]
    new_summary_msg = SystemMessage(content=f"Summary of previous conversation:\n{summary}")

    return {"chat_history": delete_msgs + [new_summary_msg]}
