from functools import lru_cache

from langchain_core.documents import Document

from app.ai.embeddings import get_embeddings
from app.core.config import get_settings


@lru_cache
def get_vector_store():
    settings = get_settings()
    if settings.vector_store == "chroma":
        from langchain_chroma import Chroma

        return Chroma(
            collection_name=settings.vector_collection,
            embedding_function=get_embeddings(),
            persist_directory=settings.chroma_persist_directory,
        )

    if not settings.database_url:
        raise ValueError("DATABASE_URL is required for PGVector")
    from langchain_postgres import PGVector

    return PGVector(
        embeddings=get_embeddings(),
        collection_name=settings.vector_collection,
        connection=settings.database_url,
        use_jsonb=True,
    )


def retrieve(query: str, top_k: int) -> list[Document]:
    return get_vector_store().similarity_search(query, k=top_k)
