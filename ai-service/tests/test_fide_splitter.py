"""Unit tests for the FIDE structure-aware splitter.

These tests verify the parsing, tree-building, and chunking logic
*without* calling any external service (PDF library, LLM, embedding,
or database).
"""

from __future__ import annotations

import textwrap

import pytest
from langchain_core.documents import Document

from app.rag.ingestion.fide_splitter import (
    SectionNode,
    _build_breadcrumb_text,
    _build_metadata,
    _split_long_text,
    build_section_tree,
    split_fide_document,
)


# ---------------------------------------------------------------------------
# Fixtures: a minimal but realistic FIDE-like text.
# ---------------------------------------------------------------------------

SAMPLE_FIDE_TEXT = textwrap.dedent("""\
    FIDE Laws of Chess
    Taking effect from 1 January 2023

    BASIC RULES OF PLAY

    Article 1: The Nature and Objectives of the Game of Chess
    1.1 The game of chess is played between two opponents.
    1.2 The player with the light-coloured pieces starts first.
    1.3 The objective is to checkmate the opponent's king.

    Article 3: The Moves of the Pieces
    3.1 It is not permitted to move a piece to a square occupied by a piece of the same colour.
    3.2 The bishop may move to any square along a diagonal.
    3.3 The rook may move to any square along the file or the rank.
    3.7 The pawn moves forward.
    3.7.1 The pawn may move forward to the square immediately in front.
    3.7.2 The pawn may capture diagonally.
    3.7.3 A pawn attacking a square crossed by an opponent's pawn.
    3.7.3.1 This capture is only legal on the move following the advance and is called en passant.
    3.7.3.2 This en passant capture must be made immediately.
    3.8 The king moves one square in any direction.
    3.8.1 The king cannot move to a square attacked by an opponent's piece.
    3.8.2 Castling is a move of the king and either rook.
    3.8.2.1 The right to castle has been lost if the king has already moved.
    3.8.2.2 Castling is prevented temporarily if the king is in check.
    3.9 A king is said to be in check if it could be captured.
    3.10 Legal and illegal moves.
    3.10.1 A move is legal if all the requirements of Article 3 are met.
    3.10.2 A move is illegal if it does not satisfy the conditions.

    COMPETITIVE RULES OF PLAY

    Article 9: The Drawn Game
    9.1 A player may offer a draw at any time.
    9.2 The game is drawn upon a correct claim by a player when the same position has occurred three times.
    9.3 The game is drawn upon a correct claim if 50 moves have been made without pawn move or capture.

    APPENDICES

    Appendix C: Algebraic Notation
    C.1 Each piece is indicated by its first letter.
    C.2 K = King, Q = Queen, R = Rook, B = Bishop, N = Knight.
    C.3 The symbol x indicates a capture.

    GUIDELINES

    Guideline I: Adjourned Games
    Games may be adjourned and resumed at a later time.

    GLOSSARY

    Stalemate
    A position where the player has no legal move and the king is not in check.
    Promotion
    When a pawn reaches the last rank it must be changed for a piece.
    En passant
    A special pawn capture described in Article 3.7.3.
""")


# ---------------------------------------------------------------------------
# build_section_tree
# ---------------------------------------------------------------------------

class TestBuildSectionTree:
    def test_detects_introduction(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        intro = tree[0]
        assert intro.level == "introduction"
        assert "FIDE Laws of Chess" in intro.text

    def test_detects_articles(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        articles = [n for n in tree if n.level == "article"]
        assert len(articles) == 3  # Articles 1, 3, 9
        ids = {a.section_id for a in articles}
        assert ids == {"1", "3", "9"}

    def test_article_has_title(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        assert "Moves of the Pieces" in art3.title

    def test_detects_subsections(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        # Direct children of Article 3: 3.1, 3.2, 3.3, 3.7, 3.8, 3.9, 3.10
        child_ids = {c.section_id for c in art3.children}
        assert "3.7" in child_ids
        assert "3.8" in child_ids
        assert "3.10" in child_ids

    def test_nested_subsections(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        sec_3_8 = next(c for c in art3.children if c.section_id == "3.8")
        child_ids = {c.section_id for c in sec_3_8.children}
        assert "3.8.1" in child_ids
        assert "3.8.2" in child_ids

    def test_deeply_nested_subsections(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        sec_3_8 = next(c for c in art3.children if c.section_id == "3.8")
        sec_3_8_2 = next(c for c in sec_3_8.children if c.section_id == "3.8.2")
        child_ids = {c.section_id for c in sec_3_8_2.children}
        assert "3.8.2.1" in child_ids
        assert "3.8.2.2" in child_ids

    def test_castling_subtree_is_kept_together(self):
        """3.8.2, 3.8.2.1, and 3.8.2.2 should be in the same subtree."""
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        sec_3_8 = next(c for c in art3.children if c.section_id == "3.8")
        sec_3_8_2 = next(c for c in sec_3_8.children if c.section_id == "3.8.2")
        full = sec_3_8_2.full_text()
        assert "right to castle has been lost" in full
        assert "prevented temporarily" in full

    def test_en_passant_subtree(self):
        """3.7.3.1 and 3.7.3.2 should be children of 3.7.3."""
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        sec_3_7 = next(c for c in art3.children if c.section_id == "3.7")
        sec_3_7_3 = next(c for c in sec_3_7.children if c.section_id == "3.7.3")
        child_ids = {c.section_id for c in sec_3_7_3.children}
        assert "3.7.3.1" in child_ids
        assert "3.7.3.2" in child_ids

    def test_detects_appendix(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        appendices = [n for n in tree if n.level == "appendix"]
        assert len(appendices) == 1
        assert appendices[0].section_id == "C"

    def test_detects_guideline(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        guidelines = [n for n in tree if n.level == "guideline"]
        assert len(guidelines) == 1
        assert guidelines[0].section_id == "I"

    def test_detects_glossary(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        glossary = next(n for n in tree if n.level == "glossary")
        term_titles = {c.title for c in glossary.children}
        assert "Stalemate" in term_titles
        assert "Promotion" in term_titles
        assert "En passant" in term_titles

    def test_glossary_terms_have_definitions(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        glossary = next(n for n in tree if n.level == "glossary")
        stalemate = next(c for c in glossary.children if c.title == "Stalemate")
        assert "no legal move" in stalemate.text

    def test_parent_references(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art3 = next(n for n in tree if n.section_id == "3")
        sec_3_8 = next(c for c in art3.children if c.section_id == "3.8")
        assert sec_3_8.parent is art3

    def test_article_9_category(self):
        tree = build_section_tree(SAMPLE_FIDE_TEXT)
        art9 = next(n for n in tree if n.section_id == "9")
        assert art9.category == "competitive_rules"


# ---------------------------------------------------------------------------
# SectionNode
# ---------------------------------------------------------------------------

class TestSectionNode:
    def test_article_number_simple(self):
        node = SectionNode(section_id="3", title="Moves", level="article")
        assert node.article_number == "3"

    def test_article_number_from_subsection(self):
        node = SectionNode(section_id="3.8.2.1", title="", level="subsection")
        assert node.article_number == "3"

    def test_category_basic(self):
        node = SectionNode(section_id="3", title="", level="article")
        assert node.category == "basic_rules"

    def test_category_competitive(self):
        node = SectionNode(section_id="9", title="", level="article")
        assert node.category == "competitive_rules"

    def test_breadcrumb(self):
        root = SectionNode(section_id="3", title="Moves", level="article")
        child = SectionNode(section_id="3.8", title="King", level="subsection", parent=root)
        crumbs = child.breadcrumb()
        assert crumbs == ["3: Moves", "3.8: King"]

    def test_full_text_includes_children(self):
        root = SectionNode(section_id="3.8", title="King", level="subsection", text="King intro.\n")
        child = SectionNode(section_id="3.8.1", title="", level="subsection", text="King move.\n", parent=root)
        root.children.append(child)
        full = root.full_text()
        assert "King intro." in full
        assert "King move." in full


# ---------------------------------------------------------------------------
# Metadata
# ---------------------------------------------------------------------------

class TestBuildMetadata:
    def test_basic_fields(self):
        node = SectionNode(section_id="3.8.2", title="Castling", level="subsection")
        meta = _build_metadata(node)
        assert meta["document"] == "FIDE Laws of Chess"
        assert meta["version"] == "2023"
        assert meta["domain"] == "chess"
        assert meta["section_id"] == "3.8.2"

    def test_article_decomposition(self):
        node = SectionNode(section_id="3.8.2.1", title="", level="subsection")
        meta = _build_metadata(node)
        assert meta["article"] == "Article 3"
        assert meta["article_number"] == "3"
        assert meta["section"] == "3.8"
        assert meta["subsection"] == "3.8.2"

    def test_appendix_metadata(self):
        node = SectionNode(section_id="C", title="Algebraic Notation", level="appendix")
        meta = _build_metadata(node)
        assert meta["appendix"] == "C"
        assert meta["category"] == "appendix"


    def test_parent_section_id(self):
        parent = SectionNode(section_id="3.8", title="King", level="subsection")
        child = SectionNode(section_id="3.8.2", title="Castling", level="subsection", parent=parent)
        meta = _build_metadata(child)
        assert meta["parent_section_id"] == "3.8"


# ---------------------------------------------------------------------------
# Breadcrumb
# ---------------------------------------------------------------------------

class TestBreadcrumb:
    def test_article_breadcrumb(self):
        node = SectionNode(section_id="3", title="The Moves of the Pieces", level="article")
        bc = _build_breadcrumb_text(node)
        assert "FIDE Laws of Chess" in bc
        assert "Basic Rules of Play" in bc
        assert "3: The Moves of the Pieces" in bc

    def test_subsection_breadcrumb(self):
        art = SectionNode(section_id="3", title="Moves", level="article")
        sec = SectionNode(section_id="3.8", title="King", level="subsection", parent=art)
        sub = SectionNode(section_id="3.8.2", title="Castling", level="subsection", parent=sec)
        bc = _build_breadcrumb_text(sub)
        assert "3: Moves" in bc
        assert "3.8: King" in bc
        assert "3.8.2: Castling" in bc



# ---------------------------------------------------------------------------
# Text splitting (fallback for large sections)
# ---------------------------------------------------------------------------

class TestSplitLongText:
    def test_short_text_returns_single(self):
        assert _split_long_text("short", max_chars=100, overlap_chars=10) == ["short"]

    def test_long_text_is_split(self):
        text = "A" * 500
        pieces = _split_long_text(text, max_chars=200, overlap_chars=50)
        assert len(pieces) > 1
        # Every piece should be at most max_chars.
        for p in pieces:
            assert len(p) <= 200

    def test_prefers_paragraph_boundary(self):
        text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        pieces = _split_long_text(text, max_chars=30, overlap_chars=5)
        assert len(pieces) >= 2


# ---------------------------------------------------------------------------
# split_fide_document (end-to-end)
# ---------------------------------------------------------------------------

class TestSplitFideDocument:
    def test_produces_documents(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        assert len(docs) > 0
        assert all(isinstance(d, Document) for d in docs)

    def test_chunks_have_metadata(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        for doc in docs:
            assert "document" in doc.metadata
            assert "domain" in doc.metadata
            assert doc.metadata["domain"] == "chess"

    def test_chunks_have_breadcrumb_in_content(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        for doc in docs:
            assert "FIDE Laws of Chess" in doc.page_content

    def test_castling_rules_not_split_from_parent(self):
        """3.8.2, 3.8.2.1, and 3.8.2.2 should appear together in one chunk
        because they are small enough to merge."""
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        # Find chunks that mention castling rights.
        castling_chunks = [
            d for d in docs
            if "right to castle has been lost" in d.page_content
        ]
        assert len(castling_chunks) >= 1
        # The chunk that has 3.8.2.1 should also have 3.8.2.2.
        chunk = castling_chunks[0]
        assert "prevented temporarily" in chunk.page_content

    def test_glossary_is_indexed(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        glossary_docs = [d for d in docs if "glossary" in d.metadata.get("level", "")]
        # At least some glossary chunks should exist.
        assert len(glossary_docs) > 0

    def test_appendix_c_is_separate(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        appendix_docs = [d for d in docs if d.metadata.get("appendix") == "C"]
        assert len(appendix_docs) >= 1
        content = " ".join(d.page_content for d in appendix_docs)
        assert "Knight" in content or "capture" in content

    def test_article_9_has_correct_category(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        art9_docs = [d for d in docs if d.metadata.get("article_number") == "9"]
        for doc in art9_docs:
            assert doc.metadata["category"] == "competitive_rules"

    def test_article_1_has_basic_rules_category(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        art1_docs = [d for d in docs if d.metadata.get("article_number") == "1"]
        for doc in art1_docs:
            assert doc.metadata["category"] == "basic_rules"

    def test_no_empty_chunks(self):
        docs = split_fide_document(SAMPLE_FIDE_TEXT)
        for doc in docs:
            # After removing the breadcrumb, there should be real content.
            lines = doc.page_content.strip().split("\n")
            # At least the breadcrumb + some content.
            assert len(lines) >= 2


# ---------------------------------------------------------------------------
# ChessDiagram integration tests
# ---------------------------------------------------------------------------

class TestChessDiagramIntegration:
    def test_attaches_diagram_to_matching_subsection(self):
        from app.rag.ingestion.chess_vision import ChessDiagram

        diagram = ChessDiagram(
            id="p6_test_1",
            page=6,
            section_id="3.7.3.1",
            title="En Passant Demo",
            fen="8/8/8/3pP3/8/8/8/8 w - d6 0 1",
            description="Tốt trắng bắt Tốt đen qua đường.",
        )

        docs = split_fide_document(SAMPLE_FIDE_TEXT, diagrams=[diagram])
        en_passant_docs = [d for d in docs if "en passant" in d.page_content.lower()]
        assert len(en_passant_docs) >= 1

        matched_doc = next((d for d in en_passant_docs if "8/8/8/3pP3/8/8/8/8 w - d6 0 1" in d.page_content), None)
        assert matched_doc is not None
        assert "Diagram / Ví dụ bàn cờ:" in matched_doc.page_content
        assert "Tốt trắng bắt Tốt đen qua đường." in matched_doc.page_content

        assert "diagrams" in matched_doc.metadata
        assert len(matched_doc.metadata["diagrams"]) == 1
        assert matched_doc.metadata["diagrams"][0]["id"] == "p6_test_1"
        assert matched_doc.metadata["diagrams"][0]["section_id"] == "3.7.3.1"

    def test_loads_diagrams_from_cache(self, tmp_path):
        import json
        from app.rag.ingestion.chess_vision import extract_diagrams_from_pdf

        cache_file = tmp_path / "diagrams_cache.json"
        data = [
            {
                "id": "p4_test_99",
                "page": 4,
                "section_id": "3.2",
                "title": "Bishop Move",
                "fen": "8/8/8/3B4/8/8/8/8 w - - 0 1",
                "description": "Tượng đi chéo.",
            }
        ]
        cache_file.write_text(json.dumps(data), encoding="utf-8")

        diagrams = extract_diagrams_from_pdf("dummy.pdf", refresh_vision=False, cache_path=cache_file)
        assert len(diagrams) == 1
        assert diagrams[0].id == "p4_test_99"
        assert diagrams[0].section_id == "3.2"
        assert "Tượng đi chéo." in diagrams[0].format_block()

