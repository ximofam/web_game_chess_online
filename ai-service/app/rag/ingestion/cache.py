import csv
import json
import logging
import re
from pathlib import Path
from typing import Any

from langchain_core.documents import Document

logger = logging.getLogger(__name__)


def sanitize_model_slug(model_name: str) -> str:
    """Convert an embedding model name to a safe filename prefix.

    Example:
        'sentence-transformers/all-MiniLM-L6-v2' -> 'sentence-transformers_all-MiniLM-L6-v2'
        'BAAI/bge-m3' -> 'BAAI_bge-m3'
    """
    return re.sub(r"[/\\:\s]+", "_", model_name.strip())


def get_cache_path(
    model_name: str,
    domain: str,
    cache_dir: str | Path = 'data/cache',
    custom_name: str | None = None,
) -> Path:
    """Return the filesystem path for the CSV embedding cache."""
    base = Path(cache_dir)
    if custom_name:
        return base / custom_name
    slug = sanitize_model_slug(model_name)
    return base / f'{slug}_{domain}_ingest.csv'


def save_embeddings_to_csv(
    filepath: str | Path,
    docs: list[Document],
    embeddings: list[list[float]],
) -> Path:
    """Save document chunks with their metadata and embedding vectors into a CSV cache file.

    Columns:
        - content: Chunk text
        - metadata: JSON-encoded dictionary of chunk metadata
        - embedding: JSON-encoded list of float embedding vector
    """
    if len(docs) != len(embeddings):
        raise ValueError(
            f'Mismatch between number of documents ({len(docs)}) and embeddings ({len(embeddings)})'
        )

    path = Path(filepath)
    path.parent.mkdir(parents=True, exist_ok=True)

    with open(path, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['content', 'metadata', 'embedding'])
        writer.writeheader()
        for doc, emb in zip(docs, embeddings):
            writer.writerow({
                'content': doc.page_content,
                'metadata': json.dumps(doc.metadata, ensure_ascii=False),
                'embedding': json.dumps(emb),
            })

    logger.info('Saved %d embedded chunks to CSV cache: %s', len(docs), path)
    return path


def load_embeddings_from_csv(
    filepath: str | Path,
    expected_dim: int | None = None,
) -> tuple[list[str], list[list[float]], list[dict[str, Any]]]:
    """Load precomputed embeddings from a CSV cache file.

    Returns:
        tuple of (texts, embeddings, metadatas)

    Raises:
        FileNotFoundError: If the cache file does not exist.
        ValueError: If vector dimension does not match expected_dim.
    """
    path = Path(filepath)
    if not path.exists():
        raise FileNotFoundError(f'Embedding cache file not found: {path}')

    texts: list[str] = []
    embeddings: list[list[float]] = []
    metadatas: list[dict[str, Any]] = []

    with open(path, 'r', encoding='utf-8', newline='') as f:
        reader = csv.DictReader(f)
        for row_idx, row in enumerate(reader):
            content = row.get('content', '')
            raw_meta = row.get('metadata', '{}')
            raw_emb = row.get('embedding', '[]')

            meta = json.loads(raw_meta) if raw_meta else {}
            emb = json.loads(raw_emb) if raw_emb else []

            if expected_dim is not None and len(emb) > 0 and len(emb) != expected_dim:
                raise ValueError(
                    f'Vector dimension mismatch at row {row_idx}: expected {expected_dim}, got {len(emb)}'
                )

            texts.append(content)
            embeddings.append(emb)
            metadatas.append(meta)

    logger.info('Loaded %d precomputed chunks from cache: %s', len(texts), path)
    return texts, embeddings, metadatas
