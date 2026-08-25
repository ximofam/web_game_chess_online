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
        engine_args={"connect_args": {"options": "-csearch_path=ai_service,public"}},
        use_jsonb=True,
        create_extension=False,
    )


def retrieve(query: str, top_k: int = 4, domain: str | None = None) -> list[Document]:
    """Retrieve relevant documents from vector store, filtered by domain if specified."""
    settings = get_settings()
    store = get_vector_store()

    filter_dict = None
    if domain in ("chess", "system"):
        filter_dict = {"domain": domain}

    if filter_dict:
        results = store.similarity_search_with_relevance_scores(query, k=top_k, filter=filter_dict)
    else:
        results = store.similarity_search_with_relevance_scores(query, k=top_k)

    # similarity_search luôn trả top_k docs dù không liên quan → cần lọc theo score.
    return [doc for doc, score in results if score >= settings.retrieval_score_threshold]
