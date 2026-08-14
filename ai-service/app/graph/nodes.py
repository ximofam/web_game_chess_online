from typing import Literal

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser
from pydantic import BaseModel, Field

from app.ai.llm import get_llm
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
    # ponytail: Sliding window of 6 cuts cost now. Ceiling: loses long context.
    # Upgrade path: LLM summarization of past messages (memory summarization).
    history = state.get("chat_history", [])[-6:]
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
    result = get_llm().with_structured_output(QuestionAnalysis).invoke(prompt)
    return {
        "rewritten_question": result.rewritten_question,
        "question_type": result.category,
    }


def retrieve_docs(state: RagState) -> dict:
    docs = retrieve(state["rewritten_question"], top_k=4)
    return {"documents": [d.page_content for d in docs]}


def generate_rag(state: RagState) -> dict:
    recent_history = state.get("chat_history", [])[-6:]
    answer = (RAG_PROMPT | get_llm() | StrOutputParser()).invoke(
        {
            "context": "\n\n".join(state["documents"]),
            "history": recent_history,
            "question": state["rewritten_question"],
        }
    )
    return {
        "answer": answer,
        # Accumulate turn in chat_history for subsequent rewrite context
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_direct(state: RagState) -> dict:
    recent_history = state.get("chat_history", [])[-6:]
    messages = [
        SystemMessage("Answer this chess-related question clearly and accurately."),
        *recent_history,
        HumanMessage(state["rewritten_question"]),
    ]
    answer = get_llm().invoke(messages).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_chitchat(state: RagState) -> dict:
    recent_history = state.get("chat_history", [])[-6:]
    messages = [
        SystemMessage(
            "You are a friendly assistant for a chess platform. "
            "Respond briefly and warmly to this message. If it's off-topic "
            "small talk, gently note you're best at chess and platform questions."
        ),
        *recent_history,
        HumanMessage(state["rewritten_question"]),
    ]
    answer = get_llm().invoke(messages).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }
