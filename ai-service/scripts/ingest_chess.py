"""Ingest the FIDE Laws of Chess PDF into the vector store.

This script uses the structure-aware ``fide_splitter`` instead of the
generic ``RecursiveCharacterTextSplitter``.  It:

1. Extracts text from the PDF (page-by-page for page metadata).
2. Builds the FIDE document hierarchy (Articles → subsections).
3. Produces retrieval-ready chunks with breadcrumbs + rich metadata.
4. Inserts the chunks into the configured vector store (Chroma or PGVector).

Usage::

    python -m scripts.ingest_chess              # default PDF path
    python -m scripts.ingest_chess --clear      # wipe old vectors first
    python -m scripts.ingest_chess --path ./docs/chess/fide/20230101Laws-of-Chess.pdf
"""

import argparse

from app.services.rag_service import ingest_fide_pdf

_DEFAULT_PDF = "./docs/chess/fide/20230101Laws-of-Chess.pdf"


def ingest_chess(pdf_path: str, clear: bool = False, refresh_vision: bool = False) -> None:
    print(f"Ingesting FIDE Laws of Chess from {pdf_path} (clear={clear}, refresh_vision={refresh_vision})...")
    count = ingest_fide_pdf(pdf_path, clear=clear, refresh_vision=refresh_vision)
    print(f"Chess rules ingestion complete: {count} chunks indexed.")


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
        "--clear",
        action="store_true",
        help="Clear existing vectors before ingesting",
    )
    parser.add_argument(
        "--refresh-vision",
        action="store_true",
        help="Re-run Vision LLM on PDF diagram images and refresh the JSON cache",
    )
    args = parser.parse_args()

    ingest_chess(args.path, clear=args.clear, refresh_vision=args.refresh_vision)


