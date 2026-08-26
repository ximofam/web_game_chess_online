import logging

from langchain_core.messages import AIMessage, HumanMessage, RemoveMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser

from app.ai.llm import get_llm, get_router_llm
from app.ai.prompts import (
    ANALYZE_PROMPT,
    GENERAL_SYSTEM,
    NO_CONTEXT_PROMPT,
    RAG_CHESS_LAW_PROMPT,
    RAG_CHESS_PROMPT,
    RAG_OPENING_PROMPT,
    RAG_PROMPT,
    RAG_SYSTEM_PROMPT,
    REWRITE_PROMPT,
    SUMMARIZE_PROMPT,
)

from app.rag.retriever import retrieve
from app.rag.state import RagState

logger = logging.getLogger(__name__)

import json

_HISTORY_WINDOW = 6


def _parse_router_output(raw: str) -> tuple[str, str]:
    """Safely parse JSON or text output from router LLM into (question_type, domain)."""
    cleaned = raw.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()

    try:
        data = json.loads(cleaned)
        q_type = str(data.get("question_type", "rag")).lower().strip()
        domain = str(data.get("domain", "all")).lower().strip()
    except Exception:
        lowered = cleaned.lower()
        q_type = "general" if "general" in lowered else "rag"
        if "opening" in lowered:
            domain = "chess_opening"
        elif "law" in lowered or "rule" in lowered or "fide" in lowered:
            domain = "chess_law"
        elif "chess" in lowered:
            domain = "chess_law"
        elif "system" in lowered:
            domain = "system"
        else:
            domain = "all"

    final_type = q_type if q_type in ("rag", "general") else "rag"
    valid_domains = ("chess_law", "chess_opening", "chess", "system", "all")
    final_domain = domain if domain in valid_domains else "all"
    return final_type, final_domain


def contextualize_question(state: RagState) -> dict:
    """Resolve pronouns/references using chat history and translate into a standalone
    English search query suitable for PGVector retrieval and routing.
    """
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    history_text = "\n".join(f"{m.type}: {m.content}" for m in history) if history else "No previous conversation."
    prompt = REWRITE_PROMPT.format(history=history_text, question=state["original_question"])
    rewritten = get_router_llm().invoke(prompt).content.strip()
    return {"rewritten_question": rewritten}


def route_question(state: RagState) -> dict:
    # rewritten_question đã được contextualize → standalone → không cần history để classify.
    prompt = ANALYZE_PROMPT.format(question=state["rewritten_question"])
    raw_result = get_router_llm().invoke(prompt).content
    q_type, domain = _parse_router_output(raw_result)
    logger.info("Router classified question: type=%s, domain=%s", q_type, domain)
    return {"question_type": q_type, "domain": domain}


def retrieve_docs(state: RagState) -> dict:
    domain = state.get("domain", "all")
    docs = retrieve(state["rewritten_question"], top_k=4, domain=domain)
    return {"documents": docs}


def generate_rag(state: RagState) -> dict:
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    context = "\n\n".join(d.page_content for d in state["documents"])
    domain = state.get("domain", "all")
    if domain == "chess_opening":
        prompt_template = RAG_OPENING_PROMPT
    elif domain in ("chess_law", "chess"):
        prompt_template = RAG_CHESS_PROMPT
    else:
        prompt_template = RAG_SYSTEM_PROMPT

    answer = (prompt_template | get_llm() | StrOutputParser()).invoke(
        {
            "context": context,
            "history": history,
            "question": state["original_question"],
        }
    )
    return {
        "answer": answer,
        # Accumulate turn in chat_history for subsequent rewrite context
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def no_context_answer(state: RagState) -> dict:
    prompt = NO_CONTEXT_PROMPT.format(question=state["original_question"])
    answer = get_router_llm().invoke(prompt).content.strip()
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_general(state: RagState) -> dict:
    """Xử lý câu hỏi chess kiến thức + chitchat — không cần retrieval."""
    history = state.get("chat_history", [])[-_HISTORY_WINDOW:]
    messages = [
        SystemMessage(GENERAL_SYSTEM),
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
