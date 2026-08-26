import pytest
from scripts.crawl_wikibooks import (
    PageRaw,
    derive_metadata,
    match_eco_code,
    parse_diagram_template,
    parse_export_xml,
    process_pages,
    squares_to_fen_board,
    title_to_pgn_moves,
)
import mwparserfromhell


def test_squares_to_fen_board():
    # 8x8 matrix representing position after 1. e4 Nc6 2. Bb5
    squares = [
        "rd", "", "bd", "qd", "kd", "bd", "nd", "rd",
        "pd", "pd", "pd", "pd", "pd", "pd", "pd", "pd",
        "", "", "nd", "", "", "", "", "",
        "", "bl", "", "", "", "", "", "",
        "", "", "", "", "pl", "", "", "",
        "", "", "", "", "", "", "", "",
        "pl", "pl", "pl", "pl", "", "pl", "pl", "pl",
        "rl", "nl", "bl", "ql", "kl", "", "nl", "rl",
    ]
    fen = squares_to_fen_board(squares)
    assert fen == "r1bqkbnr/pppppppp/2n5/1B6/4P3/8/PPPP1PPP/RNBQK1NR"


def test_title_to_pgn_moves():
    assert title_to_pgn_moves("Chess Opening Theory/1. e4/1...c5/2. Nf3/2...d6") == "1. e4 c5 2. Nf3 d6"
    assert title_to_pgn_moves("Chess Opening Theory/1. d4/1...d5/2. c4") == "1. d4 d5 2. c4"
    assert title_to_pgn_moves("Chess Opening Theory/1. e4") == "1. e4"
    assert title_to_pgn_moves("Chess Opening Theory") == ""


def test_derive_metadata():
    meta = derive_metadata("Chess Opening Theory/1. e4/1...c5/2. Nf3")
    assert meta["opening_family"] == "1. e4"
    assert meta["move_path"] == ["1. e4", "1...c5", "2. Nf3"]
    assert meta["depth"] == 3
    assert meta["parent_title"] == "Chess Opening Theory/1. e4/1...c5"
    assert meta["moves_pgn"] == "1. e4 c5 2. Nf3"


def test_parse_diagram_template():
    wikitext = "{{Chess Opening Theory/Position|moves=1. e4 c5|caption=Sicilian Defense|fen=rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR}}"
    parsed = mwparserfromhell.parse(wikitext)
    templates = list(parsed.filter_templates())
    diag_data, diag_block = parse_diagram_template(templates[0])

    assert diag_data is not None
    assert diag_data["caption"] == "Sicilian Defense"
    assert diag_data["moves"] == "1. e4 c5"
    assert diag_data["fen"] == "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR"
    assert "[Diagram:" in diag_block
    assert "- Moves: 1. e4 c5" in diag_block
    assert "- Name: Sicilian Defense" in diag_block


def test_parse_export_xml():
    xml_blob = """<mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/">
  <page>
    <title>Chess Opening Theory/1. e4</title>
    <revision>
      <text>King's Pawn Opening text content.</text>
    </revision>
  </page>
  <page>
    <title>Talk:Chess Opening Theory/1. e4</title>
    <revision>
      <text>Ignored talk page.</text>
    </revision>
  </page>
</mediawiki>"""
    pages = parse_export_xml(xml_blob)
    assert len(pages) == 1
    assert pages[0].title == "Chess Opening Theory/1. e4"
    assert "King's Pawn Opening" in pages[0].wikitext


def test_match_eco_code():
    eco_map = {
        "1. e4 c5": ("B20", "Sicilian Defense"),
        "1. e4 c5 2. Nf3": ("B27", "Sicilian Defense: Open"),
    }
    assert match_eco_code("1. e4 c5", eco_map) == ("B20", "Sicilian Defense")
    # Sub-path fallback
    assert match_eco_code("1. e4 c5 2. a3", eco_map) == ("B20", "Sicilian Defense")
    assert match_eco_code("1. f4", eco_map) == (None, None)


def test_process_pages_with_stub_folding():
    eco_map = {
        "1. e4 c5": ("B20", "Sicilian Defense"),
    }
    raw_parent = PageRaw(
        title="Chess Opening Theory/1. e4/1...c5",
        wikitext="The Sicilian Defense is the most popular and best-scoring response to White's first move 1.e4. It leads to sharp asymmetrical positions with dynamic counter-attacking chances for Black across the board.",
    )
    raw_stub = PageRaw(
        title="Chess Opening Theory/1. e4/1...c5/2. a3",
        wikitext="Rare sideline. Mengarini variation.",  # Only 4 words -> stub!
    )
    docs = process_pages([raw_parent, raw_stub], eco_map, min_words=15)

    # Stub should be folded into parent, resulting in 1 rich document
    assert len(docs) == 1
    doc = docs[0]
    assert doc["title"] == "Chess Opening Theory/1. e4/1...c5"
    assert doc["domain"] == "chess_opening"
    assert doc["eco_code"] == "B20"
    assert "Mengarini variation" in doc["content"]
    assert "### Continuations & Sub-variations:" in doc["content"]
    assert "[Opening: Sicilian Defense | ECO Code: B20]" in doc["content"]
