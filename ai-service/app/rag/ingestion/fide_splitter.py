"""Structure-aware splitter for the FIDE Laws of Chess PDF.

Instead of naively cutting the document every N tokens, this module
recognises the legal hierarchy of the FIDE rules (Article → section →
subsection) and uses those boundaries as the primary chunking unit.
Token limits are only a *fallback* when a single logical section exceeds
the target window.

Public API
----------
* ``parse_fide_pdf(path)``  – extract raw text from the PDF.
* ``split_fide_document(text, …)`` – produce ``Document`` chunks with
  breadcrumbs + rich metadata ready for embedding and PGVector storage.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Sequence

from langchain_core.documents import Document

from app.rag.ingestion.chess_vision import ChessDiagram


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

_DOCUMENT_TITLE = "FIDE Laws of Chess"
_DOCUMENT_VERSION = "2023"
_DOCUMENT_SOURCE = "FIDE"
_EFFECTIVE_DATE = "2023-01-01"

# Target chunk size in *characters* (≈ tokens × 4 for English).
# 400–700 tokens → ~1 600–2 800 chars.  We use chars because the
# embedding model's tokeniser isn't available here and character counts
# are a reliable, dependency-free proxy.
_DEFAULT_TARGET_CHARS = 2400  # ~600 tokens
_DEFAULT_MAX_CHARS = 4000     # ~1 000 tokens hard cap
_DEFAULT_OVERLAP_CHARS = 300  # ~75 tokens

# ---------------------------------------------------------------------------
# Heading / structure detection patterns
# ---------------------------------------------------------------------------

# "Article 3: The Moves of the Pieces" or "ARTICLE 3: …"
_ARTICLE_RE = re.compile(
    r"^(?:ARTICLE|Article)\s+(\d+)\s*[:\.\-–—]\s*(.+)", re.MULTILINE
)

# Numbered subsection: "3.8.2.1" or "3.8.2.1." with optional trailing text.
_SUBSECTION_RE = re.compile(
    r"^(\d+(?:\.\d+)+)\.?\s+(.*)", re.MULTILINE
)

# "Appendix A" / "APPENDIX A"
_APPENDIX_RE = re.compile(
    r"^(?:APPENDIX|Appendix)\s+([A-Z])\s*[:\.\-–—]?\s*(.*)", re.MULTILINE
)

# "Guideline I" / "GUIDELINE I" / "GUIDELINES I" — Roman numerals I–V
_GUIDELINE_RE = re.compile(
    r"^(?:GUIDELINES?|Guidelines?)\s+(I{1,3}|IV|V)\s*[:\.\-–—]?\s*(.*)", re.MULTILINE
)

# Section headers like "BASIC RULES OF PLAY", "COMPETITIVE RULES OF PLAY",
# "APPENDICES", "GUIDELINES", "GLOSSARY"
_SECTION_HEADER_RE = re.compile(
    r"^(BASIC RULES OF PLAY|COMPETITIVE RULES OF PLAY|APPENDICES|GUIDELINES|GLOSSARY)\s*$",
    re.MULTILINE,
)

# Glossary term pattern — typically a word/phrase followed by a colon or
# standing alone on a line with explanation following.
_GLOSSARY_TERM_RE = re.compile(
    r"^([A-Z][A-Za-z /\-]+)\s*$", re.MULTILINE
)

# ---------------------------------------------------------------------------
# Category helpers
# ---------------------------------------------------------------------------

_BASIC_ARTICLES = {1, 2, 3, 4, 5}
_COMPETITIVE_ARTICLES = {6, 7, 8, 9, 10, 11, 12}


def _article_category(article_num: int | str) -> str:
    """Return the top-level category for a given article number."""
    try:
        num = int(article_num)
    except (TypeError, ValueError):
        return "appendix"
    if num in _BASIC_ARTICLES:
        return "basic_rules"
    if num in _COMPETITIVE_ARTICLES:
        return "competitive_rules"
    return "other"


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class SectionNode:
    """A node in the FIDE document hierarchy tree.

    Attributes
    ----------
    section_id : str
        e.g. "3", "3.8", "3.8.2.1", "A" (appendix), "I" (guideline).
    title : str
        Human-readable title of this section.
    level : str
        One of "article", "section", "subsection", "appendix",
        "guideline", "glossary_term", "introduction", "glossary".
    text : str
        The text body belonging to *this* node (not its children).
    children : list[SectionNode]
        Child nodes.
    diagrams : list[ChessDiagram]
        Illustrative chessboard diagrams associated with this section.
    parent : SectionNode | None
        Back-reference for breadcrumb generation.
    page_start : int | None
        First page (0-indexed) that contains this section's text.
    page_end : int | None
        Last page (0-indexed) that contains this section's text.
    """

    section_id: str
    title: str
    level: str
    text: str = ""
    children: list["SectionNode"] = field(default_factory=list)
    diagrams: list[ChessDiagram] = field(default_factory=list)
    parent: "SectionNode | None" = field(default=None, repr=False)
    page_start: int | None = None
    page_end: int | None = None

    @property
    def article_number(self) -> str:
        """Return the top-level article number (e.g. '3' from '3.8.2')."""
        return self.section_id.split(".")[0]

    @property
    def category(self) -> str:
        return _article_category(self.article_number)

    def breadcrumb(self) -> list[str]:
        """Build a breadcrumb path from root to self."""
        parts: list[str] = []
        node: SectionNode | None = self
        while node is not None:
            label = f"{node.section_id}: {node.title}" if node.title else node.section_id
            parts.append(label)
            node = node.parent
        parts.reverse()
        return parts

    def collect_diagrams(self) -> list[ChessDiagram]:
        """Recursively collect all diagrams in this node and its children."""
        res = list(self.diagrams)
        for child in self.children:
            res.extend(child.collect_diagrams())
        return res

    def full_text(self) -> str:
        """Return title + own text + all children's text concatenated.

        The title is included so that subsection headings like
        "3.8.2 Castling is a move of the king and either rook" are
        present in the chunk body for embedding and search.
        """
        parts: list[str] = []
        if self.title and self.level not in ("introduction", "glossary"):
            parts.append(f"{self.section_id} {self.title}")
        if self.text.strip():
            parts.append(self.text)
        for child in self.children:
            parts.append(child.full_text())
        return "\n".join(parts)

    def char_count(self) -> int:
        return len(self.full_text())


# ---------------------------------------------------------------------------
# PDF text extraction
# ---------------------------------------------------------------------------

def parse_fide_pdf(pdf_path: str | Path) -> str:
    """Extract raw text from the FIDE Laws of Chess PDF.

    Uses PyMuPDF (``fitz``) for reliable text extraction that preserves
    the reading order, headings, and numbered lists.
    """
    import fitz  # PyMuPDF

    doc = fitz.open(str(pdf_path))
    pages: list[str] = []
    for page in doc:
        pages.append(page.get_text("text"))  # type: ignore[attr-defined]
    doc.close()
    return "\n".join(pages)


def parse_fide_pdf_by_page(pdf_path: str | Path) -> list[str]:
    """Extract text from each page of the PDF individually.

    Returns a list where index *i* is the text of page *i*.  Used to
    assign ``page_start`` / ``page_end`` metadata to sections.
    """
    import fitz

    doc = fitz.open(str(pdf_path))
    pages = [page.get_text("text") for page in doc]  # type: ignore[attr-defined]
    doc.close()
    return pages


# ---------------------------------------------------------------------------
# Structure detection & tree building
# ---------------------------------------------------------------------------

def _find_page_for_text(pages: list[str], snippet: str) -> int | None:
    """Return the first 0-indexed page that contains *snippet*."""
    snippet_clean = snippet[:80].strip()
    for i, page_text in enumerate(pages):
        if snippet_clean in page_text:
            return i
    return None


def _find_node_by_section_id(nodes: list[SectionNode], section_id: str) -> SectionNode | None:
    """Find a SectionNode matching section_id anywhere in the subtree."""
    clean_id = section_id.strip()
    for node in nodes:
        if node.section_id == clean_id or node.section_id.lower() == clean_id.lower():
            return node
        found = _find_node_by_section_id(node.children, clean_id)
        if found:
            return found
    return None


def _attach_diagrams_to_tree(root_nodes: list[SectionNode], diagrams: list[ChessDiagram]) -> None:
    """Attach extracted chess diagrams to the most relevant SectionNode."""
    for diagram in diagrams:
        target = _find_node_by_section_id(root_nodes, diagram.section_id)
        if not target:
            parts = diagram.section_id.split(".")
            for k in range(len(parts) - 1, 0, -1):
                prefix = ".".join(parts[:k])
                target = _find_node_by_section_id(root_nodes, prefix)
                if target:
                    break
        if target:
            target.diagrams.append(diagram)


def build_section_tree(
    text: str,
    pages: list[str] | None = None,
    diagrams: list[ChessDiagram] | None = None,
) -> list[SectionNode]:
    """Parse FIDE Laws of Chess text into a hierarchy of ``SectionNode``s.

    The algorithm scans lines top-to-bottom.  When it encounters a
    heading pattern (Article, subsection number, Appendix, Guideline, or
    Glossary marker) it creates a new ``SectionNode`` and attaches it at
    the correct depth in the tree.

    Parameters
    ----------
    text : str
        Full text of the FIDE document.
    pages : list[str] | None
        Per-page text (from ``parse_fide_pdf_by_page``) for page-number
        metadata.  Pass ``None`` to skip page annotation.
    diagrams : list[ChessDiagram] | None
        Extracted chess diagrams with FEN and descriptions to attach to matching nodes.

    Returns
    -------
    list[SectionNode]
        Top-level nodes (Introduction, Articles, Appendices, Guidelines,
        Glossary).
    """
    lines = text.split("\n")
    root_nodes: list[SectionNode] = []

    # Current context pointers.
    current_article: SectionNode | None = None
    current_section_stack: list[SectionNode] = []  # stack of depth → node
    in_glossary = False
    in_appendix: SectionNode | None = None
    in_guideline: SectionNode | None = None
    introduction_node: SectionNode | None = None
    glossary_root: SectionNode | None = None

    # Buffer for accumulating text lines into the current node.
    text_buffer: list[str] = []

    def _flush_buffer(target: SectionNode | None) -> None:
        nonlocal text_buffer
        if target is not None and text_buffer:
            target.text += "\n".join(text_buffer) + "\n"
        text_buffer = []

    def _current_target() -> SectionNode | None:
        """Return the deepest current node to append text to."""
        if current_section_stack:
            return current_section_stack[-1]
        if current_article:
            return current_article
        if in_appendix:
            return in_appendix
        if in_guideline:
            return in_guideline
        if in_glossary and glossary_root and glossary_root.children:
            return glossary_root.children[-1]
        if in_glossary and glossary_root:
            return glossary_root
        return introduction_node

    def _depth_of(section_id: str) -> int:
        """'3' → 0, '3.8' → 1, '3.8.2' → 2, '3.8.2.1' → 3."""
        return section_id.count(".")

    def _attach_subsection(node: SectionNode) -> None:
        """Insert *node* at the correct level in the section stack."""
        nonlocal current_section_stack
        depth = _depth_of(node.section_id)
        # Pop until the stack contains only ancestors.
        while current_section_stack and _depth_of(current_section_stack[-1].section_id) >= depth:
            current_section_stack.pop()
        if current_section_stack:
            parent = current_section_stack[-1]
            node.parent = parent
            parent.children.append(node)
        elif current_article:
            node.parent = current_article
            current_article.children.append(node)
        current_section_stack.append(node)

    for line in lines:
        stripped = line.strip()

        # --- Skip empty and page-noise lines ---
        if not stripped:
            text_buffer.append("")
            continue

        # --- Top-level section headers ---
        if _SECTION_HEADER_RE.match(stripped):
            _flush_buffer(_current_target())
            header_name = stripped.upper()
            if header_name == "GLOSSARY":
                in_glossary = True
                glossary_root = SectionNode(
                    section_id="Glossary",
                    title="Glossary",
                    level="glossary",
                )
                root_nodes.append(glossary_root)
            # We don't create separate nodes for "BASIC RULES" etc. as
            # category is captured in metadata.  Just reset context.
            current_article = None
            current_section_stack = []
            in_appendix = None
            in_guideline = None
            continue

        # --- Article heading ---
        m = _ARTICLE_RE.match(stripped)
        if m:
            _flush_buffer(_current_target())
            in_glossary = False
            in_appendix = None
            in_guideline = None
            current_section_stack = []
            article_num = m.group(1)
            article_title = m.group(2).strip()
            current_article = SectionNode(
                section_id=article_num,
                title=article_title,
                level="article",
                page_start=_find_page_for_text(pages, stripped) if pages else None,
            )
            root_nodes.append(current_article)
            continue

        # --- Appendix heading ---
        m = _APPENDIX_RE.match(stripped)
        if m:
            _flush_buffer(_current_target())
            in_glossary = False
            current_article = None
            current_section_stack = []
            in_guideline = None
            appendix_id = m.group(1)
            appendix_title = m.group(2).strip() if m.group(2) else ""
            in_appendix = SectionNode(
                section_id=appendix_id,
                title=appendix_title or f"Appendix {appendix_id}",
                level="appendix",
                page_start=_find_page_for_text(pages, stripped) if pages else None,
            )
            root_nodes.append(in_appendix)
            continue

        # --- Guideline heading ---
        m = _GUIDELINE_RE.match(stripped)
        if m:
            _flush_buffer(_current_target())
            in_glossary = False
            current_article = None
            current_section_stack = []
            in_appendix = None
            guideline_id = m.group(1)
            guideline_title = m.group(2).strip() if m.group(2) else ""
            in_guideline = SectionNode(
                section_id=guideline_id,
                title=guideline_title or f"Guideline {guideline_id}",
                level="guideline",
                page_start=_find_page_for_text(pages, stripped) if pages else None,
            )
            root_nodes.append(in_guideline)
            continue

        # --- Numbered subsection (e.g. 3.8.2.1) ---
        m = _SUBSECTION_RE.match(stripped)
        if m and not in_glossary:
            _flush_buffer(_current_target())
            sub_id = m.group(1)
            sub_title = m.group(2).strip()
            node = SectionNode(
                section_id=sub_id,
                title=sub_title,
                level="subsection",
                page_start=_find_page_for_text(pages, stripped) if pages else None,
            )
            _attach_subsection(node)
            continue

        # --- Glossary term ---
        if in_glossary and glossary_root is not None:
            # Heuristic: a short uppercase-starting line followed by
            # explanation text on subsequent lines.
            if len(stripped) < 60 and not stripped[0].isdigit() and stripped == stripped.rstrip(":"):
                # Could be a glossary term.  We check if it looks like a
                # term (no sentence punctuation inside).
                if not any(c in stripped for c in ".;,()"):
                    _flush_buffer(_current_target())
                    term_node = SectionNode(
                        section_id=f"Glossary.{stripped}",
                        title=stripped,
                        level="glossary_term",
                        parent=glossary_root,
                    )
                    glossary_root.children.append(term_node)
                    continue

        # --- Introduction (text before first Article) ---
        if not current_article and not in_appendix and not in_guideline and not in_glossary:
            if introduction_node is None:
                introduction_node = SectionNode(
                    section_id="Introduction",
                    title="Introduction / Preface",
                    level="introduction",
                )
                root_nodes.insert(0, introduction_node)
            text_buffer.append(stripped)
            continue

        # --- Default: accumulate text into current target ---
        text_buffer.append(stripped)

    # Flush any remaining buffer.
    _flush_buffer(_current_target())

    # Assign page_end by scanning children.
    if pages:
        _assign_page_ends(root_nodes, pages)

    if diagrams:
        _attach_diagrams_to_tree(root_nodes, diagrams)

    return root_nodes


def _assign_page_ends(nodes: list[SectionNode], pages: list[str]) -> None:
    """Recursively set ``page_end`` from the last page containing text."""
    for node in nodes:
        if node.children:
            _assign_page_ends(node.children, pages)
            last_child = node.children[-1]
            child_end = last_child.page_end
            node.page_end = max(filter(None, [node.page_start, child_end]), default=None)
        else:
            # Leaf node — scan for last occurrence of tail text.
            tail = node.text.strip()[-80:] if node.text.strip() else None
            if tail:
                end_page = _find_page_for_text(pages, tail)
                node.page_end = end_page if end_page is not None else node.page_start
            else:
                node.page_end = node.page_start


# ---------------------------------------------------------------------------
# Chunking
# ---------------------------------------------------------------------------

def _build_breadcrumb_text(node: SectionNode) -> str:
    """Produce a breadcrumb string to prepend to chunk text.

    Example::

        FIDE Laws of Chess
        Basic Rules of Play
        Article 3: The Moves of the Pieces
        Section 3.8: The King
        Subsection 3.8.2: Castling
    """
    parts = [_DOCUMENT_TITLE]

    # Add category header.
    cat = node.category
    if cat == "basic_rules":
        parts.append("Basic Rules of Play")
    elif cat == "competitive_rules":
        parts.append("Competitive Rules of Play")
    elif node.level == "appendix":
        parts.append("Appendices")
    elif node.level == "guideline":
        parts.append("Guidelines")
    elif node.level in ("glossary", "glossary_term"):
        parts.append("Glossary")

    # Walk breadcrumb from node up to root.
    crumbs = node.breadcrumb()
    for crumb in crumbs:
        if crumb.startswith("Introduction"):
            parts.append("Introduction / Preface")
        elif crumb.startswith("Glossary"):
            continue  # already added
        else:
            parts.append(crumb)

    return "\n".join(parts)


def _build_metadata(node: SectionNode, diagrams: list[ChessDiagram] | None = None) -> dict:
    """Build the rich metadata dict for a chunk derived from *node*."""
    meta: dict = {
        "document": _DOCUMENT_TITLE,
        "version": _DOCUMENT_VERSION,
        "source": _DOCUMENT_SOURCE,
        "effective_date": _EFFECTIVE_DATE,
        "domain": "chess",
        "level": node.level,
        "section_id": node.section_id,
        "title": node.title,
        "category": node.category,
    }

    # Decompose section_id into article / section / subsection fields.
    parts = node.section_id.split(".")
    if parts[0].isdigit():
        meta["article"] = f"Article {parts[0]}"
        meta["article_number"] = parts[0]
        if len(parts) >= 2:
            meta["section"] = ".".join(parts[:2])
        if len(parts) >= 3:
            meta["subsection"] = ".".join(parts[:3])
    elif node.level == "appendix":
        meta["appendix"] = node.section_id
    elif node.level == "guideline":
        meta["guideline"] = node.section_id

    # Page numbers.
    if node.page_start is not None:
        meta["page_start"] = node.page_start
    if node.page_end is not None:
        meta["page_end"] = node.page_end

    # Parent section id for parent-child retrieval.
    if node.parent:
        meta["parent_section_id"] = node.parent.section_id

    # Diagrams metadata
    if diagrams:
        meta["diagrams"] = [d.to_dict() for d in diagrams]

    return meta


def _split_long_text(
    text: str,
    max_chars: int,
    overlap_chars: int,
) -> list[str]:
    """Split *text* that exceeds *max_chars* into overlapping pieces.

    Tries to break on paragraph or sentence boundaries.
    """
    if len(text) <= max_chars:
        return [text]

    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = start + max_chars
        if end >= len(text):
            chunks.append(text[start:])
            break
        # Try to break at a paragraph boundary.
        break_at = text.rfind("\n\n", start, end)
        if break_at == -1 or break_at <= start:
            # Try sentence boundary.
            break_at = text.rfind(". ", start, end)
        if break_at == -1 or break_at <= start:
            break_at = end
        else:
            break_at += 1  # include the period/newline

        chunks.append(text[start:break_at])
        start = max(start + 1, break_at - overlap_chars)

    return chunks


def _should_merge_with_parent(node: SectionNode, max_chars: int) -> bool:
    """Return True if *node* is small enough to merge into its parent chunk."""
    return node.char_count() < max_chars // 3


def _format_chunk_content(breadcrumb: str, body: str, diagrams: list[ChessDiagram]) -> str:
    """Format final chunk text by injecting breadcrumbs, body, and diagrams."""
    content = f"{breadcrumb}\n\n{body}"
    if diagrams:
        diagram_blocks = "\n\n".join(d.format_block() for d in diagrams)
        content = f"{content}\n\n{diagram_blocks}"
    return content


def _collect_leaf_chunks(
    node: SectionNode,
    target_chars: int = _DEFAULT_TARGET_CHARS,
    max_chars: int = _DEFAULT_MAX_CHARS,
    overlap_chars: int = _DEFAULT_OVERLAP_CHARS,
) -> list[Document]:
    """Recursively convert a ``SectionNode`` subtree into ``Document`` chunks.

    Strategy:
    1. If the entire subtree (node + children) fits under *target_chars*
       → emit a single chunk.
    2. Otherwise, recurse into children.  Small childless nodes are
       merged with siblings.
    3. If a leaf node's text exceeds *max_chars* → split with overlap.
    """
    total_chars = node.char_count()

    # Case 1: whole subtree fits → single chunk.
    if total_chars <= target_chars:
        breadcrumb = _build_breadcrumb_text(node)
        body = node.full_text().strip()
        if not body:
            return []
        diagrams = node.collect_diagrams()
        content = _format_chunk_content(breadcrumb, body, diagrams)
        return [Document(page_content=content, metadata=_build_metadata(node, diagrams=diagrams))]

    # Case 2: node has children → recurse.
    if node.children:
        docs: list[Document] = []
        # Emit the node's own text (before children) as a chunk if it's non-trivial.
        if node.text.strip():
            own_text = node.text.strip()
            breadcrumb = _build_breadcrumb_text(node)
            own_diagrams = list(node.diagrams)
            if len(own_text) <= max_chars:
                docs.append(
                    Document(
                        page_content=_format_chunk_content(breadcrumb, own_text, own_diagrams),
                        metadata=_build_metadata(node, diagrams=own_diagrams),
                    )
                )
            else:
                for piece in _split_long_text(own_text, max_chars, overlap_chars):
                    docs.append(
                        Document(
                            page_content=_format_chunk_content(breadcrumb, piece, own_diagrams),
                            metadata=_build_metadata(node, diagrams=own_diagrams),
                        )
                    )

        # Group small children together.
        merge_buffer: list[SectionNode] = []
        merge_chars = 0

        def _flush_merge() -> None:
            nonlocal merge_buffer, merge_chars
            if not merge_buffer:
                return
            rep = merge_buffer[0]
            breadcrumb = _build_breadcrumb_text(rep)
            body = "\n\n".join(n.full_text().strip() for n in merge_buffer if n.full_text().strip())
            merged_diagrams: list[ChessDiagram] = []
            for n in merge_buffer:
                merged_diagrams.extend(n.collect_diagrams())
            if body:
                meta = _build_metadata(rep, diagrams=merged_diagrams)
                if len(merge_buffer) > 1:
                    last = merge_buffer[-1]
                    meta["section_id_range"] = f"{rep.section_id}–{last.section_id}"
                docs.append(
                    Document(
                        page_content=_format_chunk_content(breadcrumb, body, merged_diagrams),
                        metadata=meta,
                    )
                )
            merge_buffer = []
            merge_chars = 0

        for child in node.children:
            child_chars = child.char_count()
            if child_chars <= target_chars and (merge_chars + child_chars) <= target_chars:
                merge_buffer.append(child)
                merge_chars += child_chars
            else:
                _flush_merge()
                if child_chars <= target_chars:
                    merge_buffer.append(child)
                    merge_chars = child_chars
                else:
                    docs.extend(_collect_leaf_chunks(child, target_chars, max_chars, overlap_chars))

        _flush_merge()
        return docs

    # Case 3: leaf node too large → split with overlap.
    breadcrumb = _build_breadcrumb_text(node)
    body = node.full_text().strip()
    if not body:
        return []
    leaf_diagrams = node.collect_diagrams()
    pieces = _split_long_text(body, max_chars, overlap_chars)
    docs = []
    for i, piece in enumerate(pieces):
        piece_diagrams = leaf_diagrams if i == 0 else []
        docs.append(
            Document(
                page_content=_format_chunk_content(breadcrumb, piece, piece_diagrams),
                metadata=_build_metadata(node, diagrams=piece_diagrams),
            )
        )
    return docs


# ---------------------------------------------------------------------------
# Public high-level API
# ---------------------------------------------------------------------------

def split_fide_document(
    text: str,
    pages: list[str] | None = None,
    diagrams: list[ChessDiagram] | None = None,
    target_chars: int = _DEFAULT_TARGET_CHARS,
    max_chars: int = _DEFAULT_MAX_CHARS,
    overlap_chars: int = _DEFAULT_OVERLAP_CHARS,
) -> list[Document]:
    """Split the full FIDE Laws of Chess text into retrieval-ready chunks.

    Parameters
    ----------
    text : str
        Full text of the FIDE document (from ``parse_fide_pdf``).
    pages : list[str] | None
        Per-page text for page-number metadata.
    diagrams : list[ChessDiagram] | None
        Extracted chess diagrams with FEN and descriptions.
    target_chars : int
        Preferred chunk size in characters (≈ tokens × 4).
    max_chars : int
        Hard upper limit per chunk.
    overlap_chars : int
        Character overlap when a chunk must be split.

    Returns
    -------
    list[Document]
        Chunks with breadcrumb-prefixed ``page_content``, diagram FEN blocks, and rich
        ``metadata`` ready for PGVector.
    """
    tree = build_section_tree(text, pages, diagrams=diagrams)
    documents: list[Document] = []
    for node in tree:
        documents.extend(_collect_leaf_chunks(node, target_chars, max_chars, overlap_chars))
    return documents

