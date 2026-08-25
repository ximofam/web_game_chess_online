"""Vision LLM extractor and manager for FIDE Laws of Chess board diagrams."""
from __future__ import annotations

import base64
import json
import logging
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

_DEFAULT_CACHE_PATH = Path("docs/chess/fide/diagrams_cache.json")


@dataclass
class ChessDiagram:
    id: str
    page: int
    section_id: str
    title: str
    fen: str
    description: str

    def format_block(self) -> str:
        """Format the diagram information as a structured Markdown block for chunk injection."""
        return f"[Diagram / Ví dụ bàn cờ:\n- FEN: {self.fen}\n- Mô tả: {self.description}]"

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.id,
            "page": self.page,
            "section_id": self.section_id,
            "title": self.title,
            "fen": self.fen,
            "description": self.description,
        }


def _call_vision_llm(image_bytes: bytes, page_text: str, page_num: int) -> dict[str, str] | None:
    """Call OpenAI or Groq Vision LLM to parse a chessboard diagram into FEN and description."""
    from app.core.config import get_settings

    settings = get_settings()
    b64_img = base64.b64encode(image_bytes).decode("utf-8")

    prompt = f"""You are a FIDE Chess Arbiter and Computer Vision assistant. Analyze the attached chessboard diagram image from page {page_num} of the FIDE Laws of Chess.

Context from this page:
\"\"\"{page_text[:1200]}\"\"\"

Output a valid JSON object (and nothing else) with the following keys:
- "section_id": The exact FIDE article/subsection number this diagram illustrates (e.g. "3.2", "3.7.3.1", "3.8.2.1", "C").
- "title": Short English title of the rule or example (e.g. "En Passant Capture").
- "fen": Standard FEN notation representing the piece placement in the diagram. If partial/example, use standard empty square numbers.
- "description": Concise Vietnamese explanation of what this diagram demonstrates according to FIDE rules."""

    try:
        from app.ai.llm import get_vision_llm
        from langchain_core.messages import HumanMessage

        llm = get_vision_llm()
        msg = HumanMessage(
            content=[
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_img}"}},
            ]
        )
        resp = llm.invoke([msg])
        content = resp.content.strip()
    except ValueError as e:
        logger.warning("Vision LLM configuration missing: %s. Skipping live vision call.", e)
        return None
    except Exception:
        logger.warning("Vision LLM call failed for image on page %d", page_num, exc_info=True)
        return None


        # Clean JSON markdown fences if present
        if content.startswith("```"):
            lines = content.splitlines()
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].startswith("```"):
                lines = lines[:-1]
            content = "\n".join(lines).strip()

        data = json.loads(content)
        return {
            "section_id": str(data.get("section_id", "")),
            "title": str(data.get("title", "")),
            "fen": str(data.get("fen", "8/8/8/8/8/8/8/8 w - - 0 1")),
            "description": str(data.get("description", "")),
        }
    except Exception:
        logger.warning("Vision LLM call failed for image on page %d", page_num, exc_info=True)
        return None


def extract_diagrams_from_pdf(
    pdf_path: str | Path,
    refresh_vision: bool = False,
    cache_path: str | Path | None = None,
) -> list[ChessDiagram]:
    """Extract or load chessboard diagrams from the FIDE PDF with FEN and descriptions."""
    cache_file = Path(cache_path) if cache_path else _DEFAULT_CACHE_PATH

    # 1. Use local cache if available and refresh_vision is not requested
    if cache_file.exists() and not refresh_vision:
        logger.info("Loading chess diagrams from cache: %s", cache_file)
        try:
            with open(cache_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            diagrams = [ChessDiagram(**item) for item in data]
            return diagrams
        except Exception:
            logger.warning("Failed to load diagrams cache from %s, falling back to extraction", cache_file)

    # 2. Extract diagram images from PDF
    import fitz

    pdf_file = Path(pdf_path)
    if not pdf_file.exists():
        logger.error("PDF not found: %s", pdf_file)
        return []

    doc = fitz.open(str(pdf_file))
    diagrams: list[ChessDiagram] = []

    logger.info("Scanning PDF for chessboard diagrams (refresh_vision=%s)...", refresh_vision)
    for page_num in range(len(doc)):
        page = doc[page_num]
        imgs = page.get_images()
        if not imgs:
            continue

        page_text = page.get_text("text")

        for img_info in imgs:
            xref = img_info[0]
            base_img = doc.extract_image(xref)
            w, h = base_img.get("width", 0), base_img.get("height", 0)

            # Filter out icons and banners
            if w < 150 or h < 150:
                continue

            img_bytes = base_img["image"]
            parsed = _call_vision_llm(img_bytes, page_text, page_num + 1)
            if parsed and parsed.get("section_id"):
                diagram = ChessDiagram(
                    id=f"p{page_num+1}_xref_{xref}",
                    page=page_num + 1,
                    section_id=parsed["section_id"],
                    title=parsed.get("title", f"Diagram Page {page_num+1}"),
                    fen=parsed.get("fen", "8/8/8/8/8/8/8/8 w - - 0 1"),
                    description=parsed.get("description", ""),
                )
                diagrams.append(diagram)

    doc.close()

    # 3. Save cache if new diagrams were extracted
    if diagrams:
        try:
            cache_file.parent.mkdir(parents=True, exist_ok=True)
            with open(cache_file, "w", encoding="utf-8") as f:
                json.dump([asdict(d) for d in diagrams], f, ensure_ascii=False, indent=2)
            logger.info("Saved %d extracted chess diagrams to %s", len(diagrams), cache_file)
        except Exception:
            logger.warning("Failed to save diagrams cache to %s", cache_file)

    return diagrams
