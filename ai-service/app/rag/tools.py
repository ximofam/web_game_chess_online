from functools import lru_cache

from langchain_core.documents import Document
from langchain_core.runnables import Runnable
from langchain_core.tools import tool

from app.ai.llm import get_llm
from app.rag.retriever import retrieve


def _format_docs(docs: list[Document]) -> str:
    """Format retrieved documents with clean metadata tags and content."""
    formatted_chunks = []
    for i, doc in enumerate(docs, 1):
        meta = doc.metadata or {}
        tags = []
        if "title" in meta:
            tags.append(f"Title: {meta['title']}")
        if "article" in meta:
            tags.append(f"Article: {meta['article']}")
        if "eco_code" in meta:
            tags.append(f"ECO: {meta['eco_code']}")
        if "eco_name" in meta:
            tags.append(f"Opening: {meta['eco_name']}")

        metadata_str = f"[{' | '.join(tags)}]\n" if tags else ""
        formatted_chunks.append(f"--- Document {i} ---\n{metadata_str}{doc.page_content.strip()}")
    return "\n\n".join(formatted_chunks)


@tool
def search_fide_rules(query: str) -> str:
    """Search official FIDE Laws of Chess for formal rule citations, arbiter decisions,
    special moves (castling, en passant, promotion), tiebreaks, and tournament regulations.
    """
    docs = retrieve(query=query, top_k=3, domain="chess_law")
    if not docs:
        return "No relevant FIDE Laws of Chess documents found for the given query."
    return _format_docs(docs)


@tool
def search_chess_openings(query: str) -> str:
    """Search Chess Opening Theory for ECO classification codes, named openings,
    standard move sequences, variations, traps, and opening pawn structures.
    """
    docs = retrieve(query=query, top_k=3, domain="chess_opening")
    if not docs:
        return "No relevant Chess Opening Theory documents found for the given query."
    return _format_docs(docs)


@tool
def search_platform_support(query: str) -> str:
    """Search official VieChess platform guides for room creation, matchmaking,
    account settings, community rules, and technical troubleshooting (e.g. disconnects).
    """
    docs = retrieve(query=query, top_k=3, domain="system")
    if not docs:
        return "No relevant platform documentation found for the given query."
    return _format_docs(docs)


ALL_TOOLS = [search_fide_rules, search_chess_openings, search_platform_support]


@lru_cache
def get_tool_calling_llm() -> Runnable:
    """Get the singleton Chat LLM pre-bound with all domain RAG tools."""
    return get_llm().bind_tools(ALL_TOOLS)
