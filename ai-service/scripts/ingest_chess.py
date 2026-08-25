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

from __future__ import annotations

import argparse
from pathlib import Path

from app.rag.ingestion.fide_splitter import (
    parse_fide_pdf,
    parse_fide_pdf_by_page,
    split_fide_document,
)
from app.rag.retriever import get_vector_store


_DEFAULT_PDF = "./docs/chess/fide/20230101Laws-of-Chess.pdf"


def ingest_chess(pdf_path: str, clear: bool = False) -> None:
    from app.core.config import get_settings

    if clear:
        print("Clearing existing vector store...")
        settings = get_settings()
        if settings.vector_store == "chroma":
            import shutil
            chroma_path = Path(settings.chroma_persist_directory)
            if chroma_path.exists():
                shutil.rmtree(chroma_path)
        else:
            from sqlalchemy import create_engine, text
            engine = create_engine(settings.database_url)
            with engine.begin() as conn:
                conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_embedding CASCADE;"))
                conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_collection CASCADE;"))

    pdf = Path(pdf_path)
    if not pdf.exists():
        print(f"Error: PDF not found at {pdf}")
        return

    print(f"Extracting text from {pdf}...")
    full_text = parse_fide_pdf(pdf)
    pages = parse_fide_pdf_by_page(pdf)
    print(f"  Extracted {len(pages)} pages, {len(full_text)} characters.")

    print("Building structure-aware chunks...")
    chunks = split_fide_document(full_text, pages)
    print(f"  Created {len(chunks)} chunks.")

    # Preview first 5 chunks.
    for i, chunk in enumerate(chunks[:5]):
        print(f"\n--- Chunk {i + 1} ---")
        print(f"  section_id : {chunk.metadata.get('section_id')}")
        print(f"  title      : {chunk.metadata.get('title')}")
        print(f"  category   : {chunk.metadata.get('category')}")

        print(f"  chars      : {len(chunk.page_content)}")
        preview = chunk.page_content[:200].replace("\n", " ")
        print(f"  preview    : {preview}...")

    print(f"\nInserting {len(chunks)} chunks into Vector Store...")
    store = get_vector_store()
    # Insert in batches to avoid memory issues with large collections.
    batch_size = 50
    for i in range(0, len(chunks), batch_size):
        batch = chunks[i : i + batch_size]
        store.add_documents(batch)
        print(f"  Inserted batch {i // batch_size + 1} ({len(batch)} chunks)")

    print("Chess rules ingestion complete!")


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
    args = parser.parse_args()

    ingest_chess(args.path, args.clear)
