import json
import logging
import time
from pathlib import Path
from typing import Any

from langchain_core.documents import Document
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter

from app.ai.embeddings import get_embeddings
from app.core.config import get_settings
from app.rag.ingestion.cache import (
    get_cache_path,
    load_embeddings_from_csv,
    save_embeddings_to_csv,
)
from app.rag.ingestion.fide_splitter import (
    parse_fide_pdf,
    parse_fide_pdf_by_page,
    split_fide_document,
)
from app.ai.vectorstore import get_vector_store

logger = logging.getLogger(__name__)

_DEFAULT_DOMAIN_PATHS: dict[str, str] = {
    "system": "./docs/business/viechess",
    "chess_law": "./docs/chess/fide/20230101Laws-of-Chess.pdf",
    "chess_opening": "./docs/chess/openings/chess_opening_theory.jsonl",
}


def clear_vector_store() -> None:
    """Wipe all existing vectors from the PGVector store."""
    settings = get_settings()
    if not settings.database_url:
        raise ValueError("DATABASE_URL is required to clear PGVector store")
    from sqlalchemy import create_engine, text

    engine = create_engine(settings.database_url)
    with engine.begin() as conn:
        conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_embedding CASCADE;"))
        conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_collection CASCADE;"))
    logger.info("Truncated PGVector tables in ai_service schema")


def clear_domain_vectors(domain: str) -> int:
    """Delete all vector embeddings belonging to a specific domain from PGVector."""
    settings = get_settings()
    if not settings.database_url:
        raise ValueError("DATABASE_URL is required to clear domain vectors")
    from sqlalchemy import create_engine, text

    engine = create_engine(settings.database_url)
    with engine.begin() as conn:
        result = conn.execute(
            text("DELETE FROM ai_service.langchain_pg_embedding WHERE cmetadata->>'domain' = :domain;"),
            {"domain": domain},
        )
        count = result.rowcount
    logger.info("Deleted %d vector rows for domain '%s' from PGVector", count, domain)
    return count


def add_document(content: str, metadata: dict[str, Any]) -> str:
    """Add a single document chunk directly to the vector store."""
    return get_vector_store().add_documents([Document(page_content=content, metadata=metadata)])[0]


def prepare_system_documents(docs_path: str | Path = "./docs/business/viechess") -> list[Document]:
    """Parse and chunk Markdown documentation for the 'system' domain."""
    base_dir = Path(docs_path)
    if not base_dir.exists():
        raise FileNotFoundError(f"Documentation directory not found: {base_dir}")

    docs: list[Document] = []
    for filepath in sorted(base_dir.rglob("*.md")):
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            rel_path = str(filepath.relative_to(base_dir))
            docs.append(Document(page_content=content, metadata={"source": rel_path, "domain": "system"}))

    if not docs:
        logger.warning("No markdown files found in %s", base_dir)
        return []

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
    logger.info("Prepared %d markdown chunks for domain 'system'", len(final_chunks))
    return final_chunks


def prepare_chess_law_documents(
    pdf_path: str | Path = "./docs/chess/fide/20230101Laws-of-Chess.pdf",
    refresh_vision: bool = False,
) -> list[Document]:
    """Parse and chunk FIDE Laws of Chess PDF with vision diagrams for domain 'chess_law'."""
    pdf = Path(pdf_path)
    if not pdf.exists():
        raise FileNotFoundError(f"FIDE PDF not found at {pdf}")

    from app.rag.ingestion.chess_vision import extract_diagrams_from_pdf

    diagrams = extract_diagrams_from_pdf(pdf, refresh_vision=refresh_vision)
    full_text = parse_fide_pdf(pdf)
    pages = parse_fide_pdf_by_page(pdf)
    chunks = split_fide_document(full_text, pages, diagrams=diagrams)
    logger.info("Prepared %d FIDE chunks (with %d chess diagrams) for domain 'chess_law'", len(chunks), len(diagrams))
    return chunks


def prepare_chess_opening_documents(
    jsonl_path: str | Path = "./docs/chess/openings/chess_opening_theory.jsonl",
) -> list[Document]:
    """Parse and chunk Chess Opening Theory JSONL with breadcrumbs for domain 'chess_opening'."""
    path = Path(jsonl_path)
    if not path.exists():
        raise FileNotFoundError(f"Openings JSONL file not found at {path}")

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

            header = item.get("header", "").strip()
            body = item.get("body", "").strip()

            if not header or not body:
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

            if len(body) > 1500:
                splits = text_splitter.split_text(body)
                for split in splits:
                    chunk_text = f"{header}\n\n{split}".strip() if header else split
                    docs.append(Document(page_content=chunk_text, metadata=metadata))
            else:
                chunk_text = f"{header}\n\n{body}".strip() if header else content
                docs.append(Document(page_content=chunk_text, metadata=metadata))

    logger.info("Prepared %d opening chunks for domain 'chess_opening'", len(docs))
    return docs


def embed_documents_in_batches(
    emb_client: Any,
    texts: list[str],
    batch_size: int = 32,
    max_retries: int = 3,
    backoff_factor: float = 2.0,
) -> list[list[float]]:
    """Embed documents in micro-batches with automatic retries for rate-limits and timeouts."""
    all_embeddings: list[list[float]] = []
    total = len(texts)
    if total == 0:
        return []

    total_batches = (total + batch_size - 1) // batch_size
    logger.info("Computing embeddings for %d chunks in %d micro-batches (batch_size=%d)...", total, total_batches, batch_size)

    for batch_idx, i in enumerate(range(0, total, batch_size), start=1):
        batch_texts = texts[i : i + batch_size]
        success = False
        last_error = None

        for attempt in range(1, max_retries + 1):
            try:
                b_embs = emb_client.embed_documents(batch_texts)
                all_embeddings.extend(b_embs)
                success = True
                processed = min(i + batch_size, total)
                logger.info(
                    "Embedding progress: batch %d/%d completed (%d/%d chunks, %.1f%%)",
                    batch_idx,
                    total_batches,
                    processed,
                    total,
                    (processed / total) * 100,
                )
                break
            except Exception as exc:
                last_error = exc
                if attempt == max_retries:
                    break
                wait_time = backoff_factor ** attempt
                logger.warning(
                    "Embedding batch %d/%d failed (attempt %d/%d): %s. Retrying in %.1fs...",
                    batch_idx,
                    total_batches,
                    attempt,
                    max_retries,
                    exc,
                    wait_time,
                )
                time.sleep(wait_time)

        if not success:
            raise RuntimeError(
                f"Failed to embed batch {batch_idx}/{total_batches} ({len(batch_texts)} texts) after {max_retries} attempts: {last_error}"
            ) from last_error

    return all_embeddings


def ingest_domain(
    domain: str,
    path: str | Path | None = None,
    refresh_cache: bool = False,
    batch_size: int = 100,
    clear_domain: bool = True,
    refresh_vision: bool = False,
    cache_dir: str | Path = "data/cache",
    custom_cache_name: str | None = None,
    no_cache: bool = False,
) -> int:
    """Ingest a single domain using embedding CSV cache when available."""
    if domain not in _DEFAULT_DOMAIN_PATHS:
        raise ValueError(f"Unsupported domain '{domain}'. Must be one of {list(_DEFAULT_DOMAIN_PATHS.keys())}")

    target_path = Path(path) if path is not None else Path(_DEFAULT_DOMAIN_PATHS[domain])
    if not target_path.exists():
        logger.warning("Document source path for domain '%s' does not exist at %s. Skipping.", domain, target_path)
        return 0

    settings = get_settings()
    model_name = settings.embedding_model
    cache_file = get_cache_path(model_name, domain, cache_dir=cache_dir, custom_name=custom_cache_name)

    texts: list[str] = []
    embeddings: list[list[float]] = []
    metadatas: list[dict[str, Any]] = []

    # 1. Check CSV cache (unless no_cache is requested)
    if not no_cache and cache_file.exists() and not refresh_cache:
        logger.info("Cache HIT: Loading precomputed embeddings from %s...", cache_file)
        texts, embeddings, metadatas = load_embeddings_from_csv(cache_file)
    else:
        logger.info("Cache MISS / REFRESH / NO-CACHE: Chunking and computing embeddings for domain '%s' using model '%s'...", domain, model_name)
        if domain == "system":
            docs = prepare_system_documents(target_path)
        elif domain == "chess_law":
            docs = prepare_chess_law_documents(target_path, refresh_vision=refresh_vision)
        elif domain == "chess_opening":
            docs = prepare_chess_opening_documents(target_path)
        else:
            docs = []

        if not docs:
            logger.warning("No documents found or prepared for domain '%s' at %s", domain, target_path)
            return 0

        texts = [doc.page_content for doc in docs]
        metadatas = [doc.metadata for doc in docs]

        emb_client = get_embeddings()
        logger.info("Embedding %d chunks with model '%s'...", len(texts), model_name)
        # Micro-batching with max batch size 32 to prevent API timeouts / payload limits
        embeddings = embed_documents_in_batches(
            emb_client=emb_client,
            texts=texts,
            batch_size=min(batch_size, 32),
            max_retries=3,
        )

        # Save to CSV cache only if caching is enabled
        if not no_cache:
            save_embeddings_to_csv(cache_file, docs, embeddings)

    if not texts:
        return 0

    # 2. Clear old vectors for this domain if requested
    if clear_domain and settings.database_url:
        clear_domain_vectors(domain)

    # 3. Add embeddings to PGVector in batches
    total_chunks = len(texts)
    total_batches = (total_chunks + batch_size - 1) // batch_size
    logger.info("Inserting %d chunks into PGVector store in %d batches (batch_size=%d)...", total_chunks, total_batches, batch_size)

    store = get_vector_store()
    for batch_idx, i in enumerate(range(0, total_chunks, batch_size), start=1):
        b_texts = texts[i : i + batch_size]
        b_embeddings = embeddings[i : i + batch_size]
        b_metas = metadatas[i : i + batch_size]
        store.add_embeddings(texts=b_texts, embeddings=b_embeddings, metadatas=b_metas)
        processed = min(i + batch_size, total_chunks)
        logger.info("Inserted batch %d/%d (%d/%d chunks, %.1f%%)", batch_idx, total_batches, processed, total_chunks, (processed / total_chunks) * 100)

    logger.info("Successfully ingested %d chunks for domain '%s' into Vector Store (model='%s').", total_chunks, domain, model_name)
    return total_chunks


def ingest_markdown_directory(
    docs_path: str | Path = "./docs/business/viechess",
    clear: bool = False,
    refresh_cache: bool = False,
    cache_dir: str | Path = "data/cache",
    custom_cache_name: str | None = None,
    no_cache: bool = False,
) -> int:
    """Ingest Markdown docs for domain 'system' (backward-compatible helper)."""
    if clear:
        clear_vector_store()
    return ingest_domain(
        domain="system",
        path=docs_path,
        refresh_cache=refresh_cache,
        clear_domain=not clear,
        cache_dir=cache_dir,
        custom_cache_name=custom_cache_name,
        no_cache=no_cache,
    )


def ingest_fide_pdf(
    pdf_path: str | Path = "./docs/chess/fide/20230101Laws-of-Chess.pdf",
    clear: bool = False,
    refresh_vision: bool = False,
    batch_size: int = 50,
    refresh_cache: bool = False,
    cache_dir: str | Path = "data/cache",
    custom_cache_name: str | None = None,
    no_cache: bool = False,
) -> int:
    """Ingest FIDE Laws PDF for domain 'chess_law' (backward-compatible helper)."""
    if clear:
        clear_vector_store()
    return ingest_domain(
        domain="chess_law",
        path=pdf_path,
        refresh_cache=refresh_cache,
        batch_size=batch_size,
        clear_domain=not clear,
        refresh_vision=refresh_vision,
        cache_dir=cache_dir,
        custom_cache_name=custom_cache_name,
        no_cache=no_cache,
    )


def ingest_openings_jsonl(
    jsonl_path: str | Path = "./docs/chess/openings/chess_opening_theory.jsonl",
    clear: bool = False,
    batch_size: int = 100,
    refresh_cache: bool = False,
    cache_dir: str | Path = "data/cache",
    custom_cache_name: str | None = None,
    no_cache: bool = False,
) -> int:
    """Ingest Chess Opening JSONL for domain 'chess_opening' (backward-compatible helper)."""
    if clear:
        clear_vector_store()
    return ingest_domain(
        domain="chess_opening",
        path=jsonl_path,
        refresh_cache=refresh_cache,
        batch_size=batch_size,
        clear_domain=not clear,
        cache_dir=cache_dir,
        custom_cache_name=custom_cache_name,
        no_cache=no_cache,
    )


