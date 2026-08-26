import logging
from pathlib import Path
from typing import Any

from langchain_core.documents import Document
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter

from app.core.config import get_settings
from app.rag.ingestion.fide_splitter import (
    parse_fide_pdf,
    parse_fide_pdf_by_page,
    split_fide_document,
)
from app.rag.retriever import get_vector_store

logger = logging.getLogger(__name__)


def clear_vector_store() -> None:
    """Wipe existing vectors from the configured vector store (Chroma or PGVector)."""
    settings = get_settings()
    if settings.vector_store == "chroma":
        import shutil

        chroma_path = Path(settings.chroma_persist_directory)
        if chroma_path.exists():
            shutil.rmtree(chroma_path)
            logger.info("Cleared Chroma vector store directory: %s", chroma_path)
    else:
        from sqlalchemy import create_engine, text

        if not settings.database_url:
            raise ValueError("DATABASE_URL is required to clear PGVector store")
        engine = create_engine(settings.database_url)
        with engine.begin() as conn:
            conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_embedding CASCADE;"))
            conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_collection CASCADE;"))
        logger.info("Truncated PGVector tables in ai_service schema")


def add_document(content: str, metadata: dict[str, Any]) -> str:
    """Add a single document chunk directly to the vector store."""
    return get_vector_store().add_documents([Document(page_content=content, metadata=metadata)])[0]


def ingest_markdown_directory(docs_path: str | Path, clear: bool = False) -> int:
    """Ingest a directory of Markdown documentation files into the vector store."""
    if clear:
        clear_vector_store()

    base_dir = Path(docs_path)
    if not base_dir.exists():
        raise FileNotFoundError(f"Documentation directory not found: {base_dir}")

    docs: list[Document] = []
    for filepath in base_dir.rglob("*.md"):
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            rel_path = str(filepath.relative_to(base_dir))
            docs.append(Document(page_content=content, metadata={"source": rel_path, "domain": "system"}))

    if not docs:
        logger.warning("No markdown files found in %s", base_dir)
        return 0

    headers_to_split_on = [
        ("#", "H1"),
        ("##", "H2"),
        ("###", "H3"),
    ]
    markdown_splitter = MarkdownHeaderTextSplitter(headers_to_split_on=headers_to_split_on, strip_headers=False)

    md_header_splits: list[Document] = []
    for doc in docs:
        splits = markdown_splitter.split_text(doc.page_content)
        for split in splits:
            header_context = " > ".join([v for k, v in split.metadata.items() if k in ["H1", "H2", "H3"]])
            if header_context:
                split.page_content = f"[{header_context}]\n{split.page_content}"
            split.metadata.update(doc.metadata)
        md_header_splits.extend(splits)

    text_splitter = RecursiveCharacterTextSplitter(chunk_size=1000, chunk_overlap=100)
    final_chunks = text_splitter.split_documents(md_header_splits)

    store = get_vector_store()
    store.add_documents(final_chunks)
    logger.info("Ingested %d markdown chunks into vector store", len(final_chunks))
    return len(final_chunks)


def ingest_fide_pdf(
        pdf_path: str | Path,
        clear: bool = False,
        refresh_vision: bool = False,
        batch_size: int = 50,
) -> int:
    """Ingest the FIDE Laws of Chess PDF using structure-aware legal hierarchy chunking and Vision diagrams."""
    if clear:
        clear_vector_store()

    pdf = Path(pdf_path)
    if not pdf.exists():
        raise FileNotFoundError(f"FIDE PDF not found at {pdf}")

    from app.rag.ingestion.chess_vision import extract_diagrams_from_pdf

    diagrams = extract_diagrams_from_pdf(pdf, refresh_vision=refresh_vision)
    full_text = parse_fide_pdf(pdf)
    pages = parse_fide_pdf_by_page(pdf)
    chunks = split_fide_document(full_text, pages, diagrams=diagrams)

    store = get_vector_store()
    for i in range(0, len(chunks), batch_size):
        batch = chunks[i: i + batch_size]
        store.add_documents(batch)

    logger.info("Ingested %d FIDE chunks (with %d chess diagrams) into vector store", len(chunks), len(diagrams))
    return len(chunks)


def ingest_openings_jsonl(
    jsonl_path: str | Path,
    clear: bool = False,
    batch_size: int = 100,
) -> int:
    """Ingest processed Chess Opening Theory JSONL records into the vector store under domain='chess_opening'.
    
    Ensures Breadcrumb Context Header is injected into EVERY split chunk of multi-chunk documents.
    """
    if clear:
        clear_vector_store()

    path = Path(jsonl_path)
    if not path.exists():
        raise FileNotFoundError(f"Openings JSONL file not found at {path}")

    import json

    docs: list[Document] = []
    text_splitter = RecursiveCharacterTextSplitter(chunk_size=1500, chunk_overlap=150)

    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            item = json.loads(line)
            content = item.get("content", "").strip()
            if not content:
                continue

            metadata = {
                "source": item.get("url", item.get("title", "")),
                "title": item.get("title", ""),
                "domain": "chess_opening",
                "eco_code": item.get("eco_code") or "",
                "eco_name": item.get("eco_name") or "",
                "opening_family": item.get("opening_family") or "",
                "moves_pgn": item.get("moves_pgn") or "",
                "depth": item.get("depth", 0),
            }

            # 1. Extract or reconstruct header block
            header = item.get("header", "").strip()
            body = item.get("body", "").strip()

            if not header or not body:
                # Fallback: parse header lines starting with '[' from top of content
                lines = content.split("\n")
                header_lines = []
                body_lines = []
                in_header = True
                for l in lines:
                    if in_header and l.startswith("[") and l.endswith("]"):
                        header_lines.append(l)
                    elif in_header and not l.strip():
                        continue
                    else:
                        in_header = False
                        body_lines.append(l)

                header = "\n".join(header_lines).strip()
                body = "\n".join(body_lines).strip()
                if not body:
                    body = content

            # 2. Split body if necessary, and inject breadcrumb header to ALL chunks
            if len(body) > 1500:
                splits = text_splitter.split_text(body)
                for split in splits:
                    chunk_text = f"{header}\n\n{split}".strip() if header else split
                    docs.append(Document(page_content=chunk_text, metadata=metadata))
            else:
                chunk_text = f"{header}\n\n{body}".strip() if header else content
                docs.append(Document(page_content=chunk_text, metadata=metadata))

    if not docs:
        logger.warning("No valid opening documents found in %s", path)
        return 0

    total_docs = len(docs)
    total_batches = (total_docs + batch_size - 1) // batch_size
    logger.info("Ingesting %d opening chunks into vector store in %d batches (batch_size=%d)...", total_docs, total_batches, batch_size)

    store = get_vector_store()
    for batch_idx, i in enumerate(range(0, total_docs, batch_size), start=1):
        batch = docs[i : i + batch_size]
        store.add_documents(batch)
        processed = min(i + batch_size, total_docs)
        pct = (processed / total_docs) * 100
        logger.info(
            "Ingestion progress: batch %d/%d completed (%d/%d chunks, %.1f%%)",
            batch_idx,
            total_batches,
            processed,
            total_docs,
            pct,
        )

    logger.info("Successfully ingested %d opening chunks into vector store (domain='chess_opening')", total_docs)
    return total_docs
