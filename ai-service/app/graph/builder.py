from contextlib import asynccontextmanager

from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.graph import END, StateGraph

from app.graph.nodes import analyze_question, generate_direct, generate_rag, retrieve_docs
from app.graph.state import RagState


def _build() -> StateGraph:
    g = StateGraph(RagState)
    g.add_node("analyze_question", analyze_question)
    g.add_node("retrieve", retrieve_docs)
    g.add_node("generate_rag", generate_rag)
    g.add_node("generate_direct", generate_direct)
    g.set_entry_point("analyze_question")
    g.add_conditional_edges(
        "analyze_question",
        lambda s: s["question_type"],
        {"system": "retrieve", "chess": "generate_direct"},
    )
    g.add_edge("retrieve", "generate_rag")
    g.add_edge("generate_rag", END)
    g.add_edge("generate_direct", END)
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
