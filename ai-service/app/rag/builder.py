from contextlib import asynccontextmanager

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import MemorySaver
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.graph import END, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.rag.nodes import (
    contextualize_question,
    generate_general,
    generate_rag,
    no_context_answer,
    retrieve_docs,
    route_question,
    summarize_memory,
)
from app.rag.state import RagState


def build_graph() -> StateGraph:
    """Build the state graph for the RAG and conversational pipeline."""
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


_build = build_graph  # Backward compatibility


def compile_graph(checkpointer: BaseCheckpointSaver | None = None) -> CompiledStateGraph:
    """Compile the state graph with an explicit checkpointer adapter."""
    return build_graph().compile(checkpointer=checkpointer)


@asynccontextmanager
async def graph_lifespan(database_url: str | None = None):
    """Context manager that yields a compiled graph with a live checkpointer.

    Uses AsyncPostgresSaver when database_url is provided, or MemorySaver for in-memory/test environments.
    """
    if database_url:
        pg_url = database_url.replace("postgresql+psycopg", "postgresql")
        sep = "&" if "?" in pg_url else "?"
        pg_url += f"{sep}options=-csearch_path%3Dai_service,public"

        async with AsyncPostgresSaver.from_conn_string(pg_url) as checkpointer:
            await checkpointer.setup()
            yield compile_graph(checkpointer=checkpointer)
    else:
        yield compile_graph(checkpointer=MemorySaver())

