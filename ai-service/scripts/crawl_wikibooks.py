"""Crawl and process 'Chess Opening Theory' from Wikibooks (en.wikibooks.org) via MediaWiki API.

Features:
- Exports wikitext in bulk via generator=allpages + export=1 in ~7 round trips for 3000+ pages.
- Deterministic 8x8 matrix to FEN converter for chess diagram templates without Vision LLM cost.
- Joins with lichess-org/chess-openings ECO dataset (A00-E99) by matching normalized move paths.
- Automatically folds short stub pages (< 25 words) into direct parent pages under '### Continuations & Sub-variations'.
- Injects hierarchical breadcrumbs and formatted board examples into RAG-ready markdown.
- Outputs structured JSONL records tagged with domain="chess_opening".

Usage:
    python -m scripts.crawl_wikibooks --out docs/chess/openings/chess_opening_theory.jsonl
    python -m scripts.crawl_wikibooks --limit 100 # test run on first 100 pages
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import time
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import mwparserfromhell
import requests

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("crawl_wikibooks")

API_URL = "https://en.wikibooks.org/w/api.php"
BOOK_ROOT = "Chess Opening Theory"
USER_AGENT = "chess-rag-crawler/0.1 (graduation project; contact: ximofam@example.com)"

_DEFAULT_OUTPUT_DIR = Path("docs/chess/openings")
_DEFAULT_OUTPUT_FILE = _DEFAULT_OUTPUT_DIR / "chess_opening_theory.jsonl"
_DEFAULT_ECO_CACHE = _DEFAULT_OUTPUT_DIR / "eco_openings.tsv"

PIECE_MAP: dict[str, str] = {
    "rd": "r", "nd": "n", "bd": "b", "qd": "q", "kd": "k", "pd": "p",
    "rl": "R", "nl": "N", "bl": "B", "ql": "Q", "kl": "K", "pl": "P",
}


@dataclass
class PageRaw:
    title: str
    wikitext: str


@dataclass
class ProcessedPage:
    title: str
    url: str
    opening_family: str | None
    move_path: list[str]
    depth: int
    parent_title: str | None
    moves_pgn: str
    eco_code: str | None
    eco_name: str | None
    clean_text: str
    diagrams: list[dict[str, Any]]
    is_stub: bool
    folded: bool = False
    continuations: list[str] = field(default_factory=list)


def _local_tag(tag: str) -> str:
    """Strip XML namespace: '{http://www.mediawiki.org/xml/export-0.11/}page' -> 'page'."""
    return tag.split("}", 1)[-1]


def _children(elem: ET.Element, name: str) -> list[ET.Element]:
    """Direct children of elem whose local tag matches name."""
    return [c for c in elem if _local_tag(c.tag) == name]


def parse_export_xml(xml_blob: str) -> list[PageRaw]:
    """Parse MediaWiki XML export blob into a list of PageRaw objects."""
    if not xml_blob:
        return []
    root = ET.fromstring(xml_blob)
    pages: list[PageRaw] = []
    for page_el in _children(root, "page"):
        titles = _children(page_el, "title")
        revisions = _children(page_el, "revision")
        if not titles or not revisions:
            continue
        title = (titles[0].text or "").strip()
        text_els = _children(revisions[-1], "text")
        text = text_els[0].text if text_els else ""
        if title and title.startswith(BOOK_ROOT) and text:
            pages.append(PageRaw(title=title, wikitext=text))
    return pages


def fetch_all_wikibooks_pages(
    session: requests.Session,
    prefix: str = BOOK_ROOT,
    limit: int | None = None,
) -> list[PageRaw]:
    """Crawl pages under BOOK_ROOT from Wikibooks MediaWiki API with pagination."""
    pages: list[PageRaw] = []
    params: dict[str, Any] = {
        "action": "query",
        "format": "json",
        "generator": "allpages",
        "gapprefix": prefix,
        "gapnamespace": 0,
        "gaplimit": 500,
        "export": 1,
    }

    batch_idx = 1
    while True:
        logger.info("Fetching batch %d from Wikibooks API (collected %d pages)...", batch_idx, len(pages))
        resp = session.get(API_URL, params=params, timeout=45)
        resp.raise_for_status()
        data = resp.json()

        xml_blob = data.get("query", {}).get("export", {}).get("*", "")
        if xml_blob:
            batch_pages = parse_export_xml(xml_blob)
            pages.extend(batch_pages)
            logger.info("Batch %d yielded %d pages.", batch_idx, len(batch_pages))

        if limit and len(pages) >= limit:
            pages = pages[:limit]
            break

        cont = data.get("continue")
        if not cont:
            break
        params.update(cont)
        batch_idx += 1
        time.sleep(1)

    logger.info("Successfully fetched %d total pages under '%s'.", len(pages), prefix)
    return pages


def squares_to_fen_board(squares: list[str]) -> str:
    """Convert an 8x8 grid of square tokens (ranks 8 down to 1) into standard FEN board notation."""
    ranks: list[str] = []
    for r in range(8):
        rank_squares = squares[r * 8 : (r + 1) * 8]
        rank_str = ""
        empty_count = 0
        for sq in rank_squares:
            piece = PIECE_MAP.get(sq.strip().lower())
            if piece:
                if empty_count > 0:
                    rank_str += str(empty_count)
                    empty_count = 0
                rank_str += piece
            else:
                empty_count += 1
        if empty_count > 0:
            rank_str += str(empty_count)
        ranks.append(rank_str)
    return "/".join(ranks)


def title_to_pgn_moves(title: str) -> str:
    """Extract and format standard PGN move sequence from Wikibooks page title.
    
    Example:
        'Chess Opening Theory/1. e4/1...c5/2. Nf3/2...d6' -> '1. e4 c5 2. Nf3 d6'
    """
    parts = title.split("/")[1:]
    formatted: list[str] = []
    for p in parts:
        p = p.strip()
        if "..." in p:
            _, mv = p.split("...", 1)
            formatted.append(mv.strip())
        elif "." in p:
            num, mv = p.split(".", 1)
            formatted.append(f"{num.strip()}. {mv.strip()}")
        else:
            formatted.append(p)
    return " ".join(formatted).strip()


def derive_metadata(title: str) -> dict[str, Any]:
    """Derive hierarchical opening metadata from title path."""
    parts = [p.strip() for p in title.split("/") if p.strip()]
    move_path = parts[1:] if len(parts) > 1 else []
    parent_title = "/".join(parts[:-1]) if len(parts) > 1 else None
    family = move_path[0] if move_path else None
    moves_pgn = title_to_pgn_moves(title)

    return {
        "opening_family": family,
        "move_path": move_path,
        "depth": len(move_path),
        "parent_title": parent_title,
        "moves_pgn": moves_pgn,
    }


def parse_diagram_template(template: mwparserfromhell.nodes.Template) -> tuple[dict[str, Any] | None, str | None]:
    """Extract structured chess diagram info and generate FEN board from template parameters."""
    name = str(template.name).strip().lower()
    if not any(k in name for k in ["chess opening theory/position", "chess/board", "chess diagram"]):
        return None, None

    named_params: dict[str, str] = {}
    positional_params: list[str] = []

    for p in template.params:
        p_name = str(p.name).strip()
        p_val = str(p.value).strip()
        if p_name.isdigit():
            positional_params.append(p_val)
        elif p_name:
            named_params[p_name] = p_val
        else:
            positional_params.append(p_val)

    caption = named_params.get("caption") or named_params.get("name") or (positional_params[0] if positional_params else "")
    moves = named_params.get("moves") or ""
    fen_str = named_params.get("fen") or ""

    # Check for 64 square tokens in positional params
    square_candidates = [v for v in positional_params if v in PIECE_MAP or v == ""]
    if len(square_candidates) >= 64 and not fen_str:
        fen_str = squares_to_fen_board(square_candidates[:64])

    if not fen_str and not moves and not caption:
        return None, None

    diagram_data = {
        "template": str(template.name).strip(),
        "caption": caption,
        "moves": moves,
        "fen": fen_str,
    }

    # Format diagram markdown block for RAG ingestion (Pure English)
    lines = ["[Diagram:"]
    if fen_str:
        lines.append(f"- FEN: {fen_str}")
    if moves:
        lines.append(f"- Moves: {moves}")
    if caption:
        lines.append(f"- Name: {caption}")
    lines.append("]")
    diagram_block = "\n".join(lines)

    return diagram_data, diagram_block


def clean_wikitext_to_prose(wikitext: str) -> tuple[str, list[dict[str, Any]], list[str]]:
    """Parse wikitext into clean markdown prose, extracting diagrams into structured blocks."""
    parsed = mwparserfromhell.parse(wikitext)

    diagrams: list[dict[str, Any]] = []
    diagram_blocks: list[str] = []

    for template in list(parsed.filter_templates()):
        diag_data, diag_block = parse_diagram_template(template)
        if diag_data and diag_block:
            diagrams.append(diag_data)
            diagram_blocks.append(diag_block)
            parsed.replace(template, f"\n\n{diag_block}\n\n")
        else:
            t_name = str(template.name).strip().lower()
            # Drop purely decorative/navigational templates
            if any(k in t_name for k in ["chessmid", "chessfooter", "footer", "table", "navbar", "citation"]):
                try:
                    parsed.remove(template)
                except ValueError:
                    pass

    # Strip wikitext to plain text while keeping structure
    text = parsed.strip_code(normalize=True, collapse=True)
    # Remove HTML tags & ref tags
    text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"<ref[^>]*/>", "", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    # Clean multiple blank lines
    text = re.sub(r"\n{3,}", "\n\n", text).strip()

    return text, diagrams, diagram_blocks


def load_or_fetch_eco_dataset(cache_path: Path = _DEFAULT_ECO_CACHE) -> dict[str, tuple[str, str]]:
    """Load or download the lichess-org/chess-openings TSV database (A00-E99)."""
    eco_map: dict[str, tuple[str, str]] = {}
    cache_path.parent.mkdir(parents=True, exist_ok=True)

    if cache_path.exists():
        logger.info("Loading cached ECO openings from %s...", cache_path)
        with open(cache_path, "r", encoding="utf-8") as f:
            for line in f:
                parts = line.strip().split("\t")
                if len(parts) >= 3:
                    eco, name, pgn = parts[0], parts[1], parts[2]
                    eco_map[pgn.strip()] = (eco.strip(), name.strip())
        logger.info("Loaded %d ECO openings from cache.", len(eco_map))
        return eco_map

    logger.info("Fetching ECO database from lichess-org/chess-openings...")
    tsv_lines: list[str] = []
    for letter in ["a", "b", "c", "d", "e"]:
        url = f"https://raw.githubusercontent.com/lichess-org/chess-openings/master/{letter}.tsv"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=15) as resp:
                content = resp.read().decode("utf-8")
                for line in content.strip().split("\n"):
                    parts = line.strip().split("\t")
                    if len(parts) >= 3 and parts[0] != "eco":
                        eco, name, pgn = parts[0], parts[1], parts[2]
                        eco_map[pgn.strip()] = (eco.strip(), name.strip())
                        tsv_lines.append(f"{eco}\t{name}\t{pgn}")
        except Exception as e:
            logger.warning("Could not download ECO %s.tsv: %s", letter, e)

    if tsv_lines:
        with open(cache_path, "w", encoding="utf-8") as f:
            f.write("\n".join(tsv_lines) + "\n")
        logger.info("Saved %d ECO openings to cache %s.", len(tsv_lines), cache_path)

    return eco_map


def match_eco_code(moves_pgn: str, eco_map: dict[str, tuple[str, str]]) -> tuple[str | None, str | None]:
    """Look up ECO code and standard name by exact or longest prefix match on moves_pgn."""
    if not moves_pgn or not eco_map:
        return None, None

    # 1. Exact match
    if moves_pgn in eco_map:
        return eco_map[moves_pgn]

    # 2. Prefix match (longest matching sub-sequence)
    tokens = moves_pgn.split()
    while tokens:
        sub_pgn = " ".join(tokens)
        if sub_pgn in eco_map:
            return eco_map[sub_pgn]
        tokens.pop()

    return None, None


def process_pages(
    pages_raw: list[PageRaw],
    eco_map: dict[str, tuple[str, str]],
    min_words: int = 25,
) -> list[dict[str, Any]]:
    """Process raw pages into hierarchical documents with stub folding and breadcrumbs."""
    page_dict: dict[str, ProcessedPage] = {}

    # 1. First pass: parse each page
    for p in pages_raw:
        meta = derive_metadata(p.title)
        clean_text, diagrams, _ = clean_wikitext_to_prose(p.wikitext)
        moves_pgn = meta["moves_pgn"]
        eco_code, eco_name = match_eco_code(moves_pgn, eco_map)

        word_count = len(clean_text.split())
        is_stub = word_count < min_words

        url = "https://en.wikibooks.org/wiki/" + p.title.replace(" ", "_")
        page_dict[p.title] = ProcessedPage(
            title=p.title,
            url=url,
            opening_family=meta["opening_family"],
            move_path=meta["move_path"],
            depth=meta["depth"],
            parent_title=meta["parent_title"],
            moves_pgn=moves_pgn,
            eco_code=eco_code,
            eco_name=eco_name,
            clean_text=clean_text,
            diagrams=diagrams,
            is_stub=is_stub,
        )

    # 2. Second pass: fold stubs into parents
    folded_count = 0
    for title, page in list(page_dict.items()):
        if page.is_stub and page.parent_title and page.parent_title in page_dict:
            parent = page_dict[page.parent_title]
            last_move = page.move_path[-1] if page.move_path else page.title
            stub_summary = f"- **{last_move}**: {page.clean_text if page.clean_text else 'Sub-variation sideline.'}"
            if page.diagrams and page.diagrams[0].get("fen"):
                stub_summary += f" (FEN: `{page.diagrams[0]['fen']}`)"
            parent.continuations.append(stub_summary)
            page.folded = True
            folded_count += 1

    logger.info("Stub folding complete: %d stubs folded into parent pages.", folded_count)

    # 3. Third pass: construct final documents (Pure English metadata headers)
    documents: list[dict[str, Any]] = []
    for title, page in page_dict.items():
        if page.folded:
            continue

        breadcrumb_parts = ["Chess Opening Theory"] + page.move_path
        breadcrumb_str = " > ".join(breadcrumb_parts)
        title_suffix = f" ({page.eco_name})" if page.eco_name else ""

        header_lines = [
            f"[Opening: {page.eco_name or page.title.split('/')[-1]} | ECO Code: {page.eco_code or 'N/A'}]",
            f"[Path: {breadcrumb_str}{title_suffix}]",
            f"[PGN Moves: {page.moves_pgn or 'N/A'}]",
        ]

        header_text = "\n".join(header_lines)

        body_components = [page.clean_text]
        if page.continuations:
            body_components.append("### Continuations & Sub-variations:\n" + "\n".join(page.continuations))
        body_text = "\n\n".join(part for part in body_components if part.strip())

        final_content = f"{header_text}\n\n{body_text}".strip()

        doc_record = {
            "title": page.title,
            "url": page.url,
            "domain": "chess_opening",
            "opening_family": page.opening_family,
            "move_path": page.move_path,
            "depth": page.depth,
            "parent_title": page.parent_title,
            "moves_pgn": page.moves_pgn,
            "eco_code": page.eco_code,
            "eco_name": page.eco_name,
            "is_stub": page.is_stub,
            "diagrams": page.diagrams,
            "header": header_text,
            "body": body_text,
            "content": final_content,
            "license": "CC BY-SA 3.0 (Wikibooks contributors)",
        }
        documents.append(doc_record)

    logger.info("Constructed %d rich opening documents ready for vector indexing.", len(documents))
    return documents


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        type=Path,
        default=_DEFAULT_OUTPUT_FILE,
        help="Path to output JSONL file",
    )
    parser.add_argument(
        "--min-words",
        type=int,
        default=25,
        help="Word count threshold below which a page is treated as a stub and folded into parent",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Maximum pages to fetch (useful for quick testing)",
    )
    parser.add_argument(
        "--prefix",
        type=str,
        default=BOOK_ROOT,
        help="Wikibooks page prefix to crawl",
    )
    parser.add_argument(
        "--offline-xml",
        type=Path,
        default=None,
        help="Path to an offline exported XML file to parse instead of making API calls",
    )
    args = parser.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    eco_map = load_or_fetch_eco_dataset()

    if args.offline_xml and args.offline_xml.exists():
        logger.info("Reading offline XML from %s...", args.offline_xml)
        with open(args.offline_xml, "r", encoding="utf-8") as f:
            raw_pages = parse_export_xml(f.read())
    else:
        session = requests.Session()
        session.headers.update({"User-Agent": USER_AGENT})
        raw_pages = fetch_all_wikibooks_pages(session, prefix=args.prefix, limit=args.limit)

    documents = process_pages(raw_pages, eco_map, min_words=args.min_words)

    with open(args.out, "w", encoding="utf-8") as f:
        for doc in documents:
            f.write(json.dumps(doc, ensure_ascii=False) + "\n")

    logger.info("Successfully exported %d records to %s", len(documents), args.out)


if __name__ == "__main__":
    main()
