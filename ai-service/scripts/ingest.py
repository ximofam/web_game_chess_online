"""Unified Document Ingestion CLI for AI Service.

Supports ingesting documents by domain ('system', 'chess_law', 'chess_opening', 'all')
with CSV embedding caching and optional embedding model overrides via environment variable.
"""

import argparse
import logging
import os
from pathlib import Path

from app.services.rag_service import clear_vector_store, ingest_domain
from app.core.config import get_settings
from app.ai.embeddings import get_embeddings
from app.rag.retriever import get_vector_store

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("ingest")

_ALL_DOMAINS = ["system", "chess_law", "chess_opening"]


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Unified Ingestion CLI for VieChess AI Service Vector DB"
    )
    parser.add_argument(
        "--domain",
        type=str,
        default="all",
        choices=["system", "chess_law", "chess_opening", "all"],
        help="Domain to ingest (default: all)",
    )
    parser.add_argument(
        "--path",
        type=str,
        default=None,
        help="Optional custom source path for the specified domain",
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
        "--refresh-cache",
        action="store_true",
        help="Recompute embeddings and overwrite the CSV cache",
    )
    parser.add_argument(
        "--clear-all",
        action="store_true",
        help="Truncate the entire PGVector store before ingesting",
    )
    parser.add_argument(
        "--no-clear-domain",
        action="store_true",
        help="Do not delete existing domain vectors prior to inserting",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=100,
        help="Batch size for embedding and vector insertion (default: 100)",
    )
    parser.add_argument(
        "--refresh-vision",
        action="store_true",
        help="Re-run Vision LLM on PDF diagram images (for chess_law domain)",
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
        help="Directory for CSV embedding cache files (default: data/cache)",
    )
    parser.add_argument(
        "--cache-name",
        type=str,
        default=None,
        help="Custom CSV cache filename override",
    )

    args = parser.parse_args()

    # Override EMBEDDING_MODEL environment variable if provided
    if args.embedding_model:
        logger.info("Overriding EMBEDDING_MODEL environment variable: %s", args.embedding_model)
        os.environ["EMBEDDING_MODEL"] = args.embedding_model
        get_settings.cache_clear()
        get_embeddings.cache_clear()
        get_vector_store.cache_clear()

    if args.clear_all:
        logger.info("Truncating entire PGVector store (--clear-all)...")
        clear_vector_store()

    domains = _ALL_DOMAINS if args.domain == "all" else [args.domain]
    clear_domain = not args.clear_all and not args.no_clear_domain

    total_ingested = 0
    for domain in domains:
        logger.info("=== Starting ingestion for domain: %s ===", domain)
        path = args.path if (len(domains) == 1 and args.path) else None
        count = ingest_domain(
            domain=domain,
            path=path,
            refresh_cache=args.refresh_cache,
            batch_size=args.batch_size,
            clear_domain=clear_domain,
            refresh_vision=args.refresh_vision,
            cache_dir=args.cache_dir,
            custom_cache_name=args.cache_name if len(domains) == 1 else None,
            no_cache=args.no_cache,
        )
        total_ingested += count
        logger.info("=== Completed domain %s: %d chunks ===", domain, count)

    logger.info("Ingestion finished! Total chunks across %d domain(s): %d", len(domains), total_ingested)


if __name__ == "__main__":
    main()

