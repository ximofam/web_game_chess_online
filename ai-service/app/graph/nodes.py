from typing import Literal

from langchain_core.messages import AIMessage, HumanMessage, RemoveMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser
from pydantic import BaseModel, Field

from app.ai.llm import get_llm, get_router_llm
from app.ai.prompts import RAG_PROMPT
from app.ai.retriever import retrieve
from app.graph.state import RagState


class QuestionAnalysis(BaseModel):
    rewritten_question: str = Field(
        description="The question rewritten to be standalone, incorporating context "
                    "from chat history. If already standalone, return it unchanged."
    )
    category: Literal["system", "chess", "chitchat"] = Field(
        description="'system' if the question is about the platform/app itself "
                    "(features, account, matchmaking, lobby, how the site works). "
                    "'chess' if it's about chess as a game (rules, openings, "
                    "players, history, strategy) unrelated to the platform. "
                    "'chitchat' for greetings, thanks, small talk, or anything "
                    "unrelated to chess or the platform."
    )


def analyze_question(state: RagState) -> dict:
    # ponytail: The router only needs the immediate context (last 2 turns) to resolve pronouns.
    # Passing the full summary here wastes tokens.
    history = state.get("chat_history", [])[-4:]
    history_text = (
            "\n".join(f"{m.type}: {m.content}" for m in history) or "(none)"
    )
    prompt = (
        "Given the chat history and a follow-up question, do two things:\n"
        "1. Rewrite the follow-up question to be standalone with full context.\n"
        "2. Classify it as 'system' (about the platform) or 'chess' (about chess).\n\n"
        f"Chat history:\n{history_text}\n\n"
        f"Follow-up question: {state['original_question']}"
    )
    result = get_router_llm().with_structured_output(QuestionAnalysis).invoke(prompt)
    return {
        "rewritten_question": result.rewritten_question,
        "question_type": result.category,
    }


def retrieve_docs(state: RagState) -> dict:
    docs = retrieve(state["rewritten_question"], top_k=4)
    return {"documents": [d.page_content for d in docs]}


def generate_rag(state: RagState) -> dict:
    history = state.get("chat_history", [])
    answer = (RAG_PROMPT | get_llm() | StrOutputParser()).invoke(
        {
            "context": "\n\n".join(state["documents"]),
            "history": history,
            "question": state["rewritten_question"],
        }
    )
    return {
        "answer": answer,
        # Accumulate turn in chat_history for subsequent rewrite context
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_direct(state: RagState) -> dict:
    history = state.get("chat_history", [])
    messages = [
        SystemMessage("Answer this chess-related question clearly and accurately."),
        *history,
        HumanMessage(state["rewritten_question"]),
    ]
    answer = get_llm().invoke(messages).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_chitchat(state: RagState) -> dict:
    history = state.get("chat_history", [])
    messages = [
        SystemMessage(
            "You are a friendly assistant for a chess platform. "
            "Respond briefly and warmly to this message. If it's off-topic "
            "small talk, gently note you're best at chess and platform questions."
        ),
        *history,
        HumanMessage(state["rewritten_question"]),
    ]
    answer = get_llm().invoke(messages).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def summarize_memory(state: RagState) -> dict:
    history = state.get("chat_history", [])
    # 1 system msg + 3 human/ai pairs = 7 messages. If > 6, we summarize.
    if len(history) <= 6:
        return {}

    # Summarize all EXCEPT the last 2 (the latest turn we just added)
    messages_to_summarize = history[:-2]
    history_text = "\n".join(f"{m.type}: {m.content}" for m in messages_to_summarize)

    summary_prompt = (
        "Distill the following chat history into a concise summary. "
        "Keep user preferences (like language) and key discussion points.\n\n"
        f"{history_text}"
    )
    summary = get_router_llm().invoke(summary_prompt).content

    delete_msgs = [RemoveMessage(id=m.id) for m in messages_to_summarize if getattr(m, 'id', None)]
    new_summary_msg = SystemMessage(content=f"Summary of previous conversation:\n{summary}")

    return {"chat_history": delete_msgs + [new_summary_msg]}
