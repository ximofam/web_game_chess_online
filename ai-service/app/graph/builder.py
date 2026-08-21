from contextlib import asynccontextmanager

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.graph import END, StateGraph

from app.graph.nodes import (
    contextualize_question,
    route_question,
    generate_general,
    generate_rag,
    no_context_answer,
    retrieve_docs,
    summarize_memory,
)
from app.graph.state import RagState


def _build() -> StateGraph:
    # summarize_memory chạy đầu mỗi turn (prune-before-process).
    # Khi thêm nhánh mới, chỉ cần nối thẳng vào END — không cần wire vào summarize_memory.
    g = StateGraph(RagState)
    g.add_node("summarize_memory", summarize_memory)
    g.add_node("contextualize_question", contextualize_question)
    g.add_node("route_question", route_question)
    g.add_node("retrieve", retrieve_docs)
    g.add_node("generate_rag", generate_rag)
    g.add_node("no_context_answer", no_context_answer)
    g.add_node("generate_general", generate_general)
    g.set_entry_point("summarize_memory")
    g.add_edge("summarize_memory", "contextualize_question")
    g.add_edge("contextualize_question", "route_question")
    g.add_conditional_edges(
        "route_question",
        lambda s: s["question_type"],
        {"rag": "retrieve", "general": "generate_general"},
    )
    # Short-circuit khi không tìm được document nào — tránh tốn LLM call với context rỗng.
    g.add_conditional_edges(
        "retrieve",
        lambda s: "generate_rag" if s.get("documents") else "no_context_answer",
        {"generate_rag": "generate_rag", "no_context_answer": "no_context_answer"},
    )
    g.add_edge("generate_rag", END)
    g.add_edge("no_context_answer", END)
    g.add_edge("generate_general", END)
    return g


@asynccontextmanager
async def graph_lifespan(database_url: str):
    """Context manager that yields a compiled graph with a live PostgresSaver checkpointer.

    Strips the SQLAlchemy dialect prefix and injects search_path so LangGraph's
    auto-created tables (checkpoints, checkpoint_writes) land in ai_service schema.

    ponytail: checkpointer.setup() runs on every startup — safe at this scale.
    Ceiling: multiple replicas racing on startup. Upgrade path: run setup() once in
    a dedicated init job / migration step before scaling out.
    """
    pg_url = database_url.replace("postgresql+psycopg", "postgresql")
    sep = "&" if "?" in pg_url else "?"
    pg_url += f"{sep}options=-csearch_path%3Dai_service,public"

    async with AsyncPostgresSaver.from_conn_string(pg_url) as checkpointer:
        await checkpointer.setup()
        yield _build().compile(checkpointer=checkpointer)
