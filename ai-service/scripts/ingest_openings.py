"""Ingest processed Chess Opening Theory JSONL into the vector store.

Delegates to unified domain ingestion with CSV embedding caching.
"""

import argparse
import logging
import os
from pathlib import Path

from app.services.rag_service import ingest_domain
from app.core.config import get_settings
from app.ai.embeddings import get_embeddings
from app.rag.retriever import get_vector_store

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("ingest_openings")

_DEFAULT_JSONL = "./docs/chess/openings/chess_opening_theory.jsonl"


def ingest_openings(
    jsonl_path: str,
    clear: bool = False,
    batch_size: int = 100,
    refresh_cache: bool = False,
    embedding_model: str | None = None,
    cache_dir: str | Path = "data/cache",
    no_cache: bool = False,
) -> None:
    if embedding_model:
        os.environ["EMBEDDING_MODEL"] = embedding_model
        get_settings.cache_clear()
        get_embeddings.cache_clear()
        get_vector_store.cache_clear()

    logger.info("Ingesting Chess Opening Theory from %s (clear=%s, batch_size=%d, refresh_cache=%s, cache_dir=%s, no_cache=%s)...", jsonl_path, clear, batch_size, refresh_cache, cache_dir, no_cache)
    count = ingest_domain(
        domain="chess_opening",
        path=jsonl_path,
        refresh_cache=refresh_cache,
        batch_size=batch_size,
        clear_domain=not clear,
        cache_dir=cache_dir,
        no_cache=no_cache,
    )
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
        "--batch-size",
        type=int,
        default=100,
        help="Number of documents to embed and insert per batch",
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

    ingest_openings(
        args.path,
        clear=args.clear,
        batch_size=args.batch_size,
        refresh_cache=args.refresh_cache,
        embedding_model=args.embedding_model,
        cache_dir=args.cache_dir,
        no_cache=args.no_cache,
    )
