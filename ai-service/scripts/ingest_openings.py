"""Ingest processed Chess Opening Theory JSONL into the vector store.

Usage:
    python -m scripts.ingest_openings                          # default JSONL path
    python -m scripts.ingest_openings --clear                  # wipe old vectors first
    python -m scripts.ingest_openings --path ./docs/chess/openings/chess_opening_theory.jsonl
"""

import argparse
import logging
from pathlib import Path

from app.services.rag_service import ingest_openings_jsonl

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("ingest_openings")

_DEFAULT_JSONL = "./docs/chess/openings/chess_opening_theory.jsonl"


def ingest_openings(jsonl_path: str, clear: bool = False, batch_size: int = 100) -> None:
    logger.info("Ingesting Chess Opening Theory from %s (clear=%s, batch_size=%d)...", jsonl_path, clear, batch_size)
    count = ingest_openings_jsonl(jsonl_path, clear=clear, batch_size=batch_size)
    logger.info("Chess opening ingestion complete: %d chunks indexed into Vector Store.", count)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Ingest Chess Opening Theory JSONL into Vector DB"
    )
    parser.add_argument(
        "--path",
        type=str,
        default=_DEFAULT_JSONL,
        help="Path to the chess opening theory JSONL file",
    )
    parser.add_argument(
        "--clear",
        action="store_true",
        help="Clear existing vectors before ingesting",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=100,
        help="Number of documents to embed and insert per batch",
    )
    args = parser.parse_args()

    ingest_openings(args.path, clear=args.clear, batch_size=args.batch_size)
