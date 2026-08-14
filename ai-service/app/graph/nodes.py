from typing import Literal

from langchain_core.messages import AIMessage, HumanMessage
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
    category: Literal["system", "chess"] = Field(
        description="'system' if the question is about the platform/app itself "
                    "(features, account, matchmaking, lobby, how the site works). "
                    "'chess' if it's about chess as a game (rules, openings, "
                    "players, history, strategy) unrelated to the platform."
    )


def analyze_question(state: RagState) -> dict:
    history = state.get("chat_history", [])
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
    answer = (RAG_PROMPT | get_llm() | StrOutputParser()).invoke(
        {
            "context": "\n\n".join(state["documents"]),
            "question": state["rewritten_question"],
        }
    )
    return {
        "answer": answer,
        # Accumulate turn in chat_history for subsequent rewrite context
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }


def generate_direct(state: RagState) -> dict:
    answer = get_llm().invoke(
        "Answer this chess-related question clearly and accurately.\n\n"
        f"Question: {state['rewritten_question']}"
    ).content
    return {
        "answer": answer,
        "chat_history": [HumanMessage(state["original_question"]), AIMessage(answer)],
    }
