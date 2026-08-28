from contextlib import asynccontextmanager

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import MemorySaver
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from langgraph.graph import END, StateGraph
from langgraph.graph.state import CompiledStateGraph

from langgraph.prebuilt import ToolNode

from app.rag.nodes import (
    agent_node,
    should_continue,
    summarize_memory,
)
from app.rag.state import RagState
from app.rag.tools import ALL_TOOLS


def build_graph() -> StateGraph:
    """Build the state graph for the Agentic RAG and conversational pipeline."""
    tool_node = ToolNode(ALL_TOOLS)

    g = StateGraph(RagState)
    g.add_node("summarize_memory", summarize_memory)
    g.add_node("agent", agent_node)
    g.add_node("tools", tool_node)

    g.set_entry_point("summarize_memory")
    g.add_edge("summarize_memory", "agent")
    g.add_conditional_edges(
        "agent",
        should_continue,
        {"tools": "tools", "end": END},
    )
    g.add_edge("tools", "agent")
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
