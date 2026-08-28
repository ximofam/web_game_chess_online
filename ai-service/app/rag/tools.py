from langchain_core.documents import Document
from langchain_core.tools import tool

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
    """Search the official Laws of Chess by the International Chess Federation (FIDE).
    Use this tool when the user asks about:
    - Piece movements and capture rules (King, Queen, Rook, Bishop, Knight, Pawn).
    - Special moves: Castling (kingside/queenside), En passant captures, Pawn promotion.
    - Game outcomes & statuses: Check, Checkmate, Stalemate, 50-move rule, Threefold repetition, Insufficient material to checkmate.
    - Tournament regulations and arbiter decisions: Touch-move rule, chess clock handling, scorekeeping/notation, illegal move penalties.
    - Example board positions and diagrammatic explanations.
    """
    docs = retrieve(query=query, top_k=3, domain="chess_law")
    if not docs:
        return "No relevant FIDE Laws of Chess documents found for the given query."
    return _format_docs(docs)


@tool
def search_chess_openings(query: str) -> str:
    """Search Chess Opening Theory and the Encyclopedia of Chess Openings (ECO).
    Use this tool when the user asks about:
    - Specific openings: Sicilian Defense, Ruy Lopez, French Defense, Caro-Kann, Queen's Gambit, King's Indian Defense, Italian Game, etc.
    - ECO classification codes (e.g. B20, C50, E60) and opening variations.
    - Standard algebraic move sequences (e.g. 1.e4 c5, 1.d4 Nf6).
    - Strategic plans, typical pawn structures, piece placement, and middle-game transitions derived from openings.
    - Counter-moves, sidelines, and opening traps.
    """
    docs = retrieve(query=query, top_k=3, domain="chess_opening")
    if not docs:
        return "No relevant Chess Opening Theory documents found for the given query."
    return _format_docs(docs)


@tool
def search_platform_support(query: str) -> str:
    """Search user documentation and technical support guides for the VieChess online chess platform.
    Use this tool when the user asks about:
    - Web platform features: Creating custom rooms, matchmaking/queueing, inviting friends, spectator mode.
    - Account management: Registration, login, password resets, profile settings, avatar customization.
    - Technical troubleshooting: WebSocket disconnects, automatic reconnection countdown, UI glitches, board move input issues.
    - Community rules, forum guidelines, leaderboards, and platform ratings.
    """
    docs = retrieve(query=query, top_k=3, domain="system")
    if not docs:
        return "No relevant platform documentation found for the given query."
    return _format_docs(docs)


ALL_TOOLS = [search_fide_rules, search_chess_openings, search_platform_support]
