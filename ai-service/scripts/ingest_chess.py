"""Ingest the FIDE Laws of Chess PDF into the vector store.

Delegates to unified domain ingestion with CSV embedding caching.
"""

import argparse
import logging
import os
from pathlib import Path

from app.services.rag_service import ingest_domain
from app.core.config import get_settings
from app.ai.embeddings import get_embeddings
from app.ai.vectorstore import get_vector_store, get_engine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("ingest_chess")

_DEFAULT_PDF = "./docs/chess/fide/20230101Laws-of-Chess.pdf"


def ingest_chess(
    pdf_path: str,
    clear: bool = False,
    refresh_vision: bool = False,
    refresh_cache: bool = False,
    embedding_model: str | None = None,
    cache_dir: str | Path = "data/cache",
    no_cache: bool = False,
) -> None:
    if embedding_model:
        os.environ["EMBEDDING_MODEL"] = embedding_model
        get_settings.cache_clear()
        get_embeddings.cache_clear()
        get_engine.cache_clear()
        get_vector_store.cache_clear()


    logger.info("Ingesting FIDE Laws of Chess from %s (clear=%s, refresh_vision=%s, refresh_cache=%s, cache_dir=%s, no_cache=%s)...", pdf_path, clear, refresh_vision, refresh_cache, cache_dir, no_cache)
    count = ingest_domain(
        domain="chess_law",
        path=pdf_path,
        refresh_cache=refresh_cache,
        clear_domain=not clear,
        refresh_vision=refresh_vision,
        cache_dir=cache_dir,
        no_cache=no_cache,
    )
    logger.info("Chess rules ingestion complete: %d chunks indexed.", count)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Ingest FIDE Laws of Chess PDF into Vector DB"
    )
    parser.add_argument(
        "--path",
        type=str,
        default=_DEFAULT_PDF,
        help="Path to the FIDE Laws of Chess PDF",
    )
    parser.add_argument(
        "--embedding-model",
        "--embed-model",
        type=str,
        default=None,
        dest="embedding_model",
        help="Embedding model override (defaults to EMBEDDING_MODEL from .env)",
    )
    parser.add_argument(
        "--clear",
        action="store_true",
        help="Clear all vectors before ingesting",
    )
    parser.add_argument(
        "--refresh-vision",
        action="store_true",
        help="Re-run Vision LLM on PDF diagram images and refresh the JSON cache",
    )
    parser.add_argument(
        "--refresh-cache",
        action="store_true",
        help="Re-compute embeddings and overwrite the CSV cache",
    )
    parser.add_argument(
        "--no-cache",
        action="store_true",
        help="Disable reading from or writing to the CSV embedding cache",
    )
    parser.add_argument(
        "--cache-dir",
        type=str,
        default="data/cache",
        help="Directory to read and store CSV embedding cache files",
    )
    args = parser.parse_args()

    ingest_chess(
        args.path,
        clear=args.clear,
        refresh_vision=args.refresh_vision,
        refresh_cache=args.refresh_cache,
        embedding_model=args.embedding_model,
        cache_dir=args.cache_dir,
        no_cache=args.no_cache,
    )
