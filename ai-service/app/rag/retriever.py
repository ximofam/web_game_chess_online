from functools import lru_cache

from langchain_core.documents import Document

from app.ai.embeddings import get_embeddings
from app.ai.reranker import get_reranker
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
    """Retrieve relevant documents using 2-stage retrieval (Vector Search + Reranker)."""
    settings = get_settings()
    store = get_vector_store()

    # Stage 1: Retrieve larger candidate pool from Vector Store
    candidates_k = max(top_k, settings.reranker_candidates_k)
    filter_dict = None
    if domain in ("chess_law", "chess_opening", "chess", "system"):
        filter_dict = {"domain": domain}

    if filter_dict:
        results = store.similarity_search_with_relevance_scores(query, k=candidates_k, filter=filter_dict)
    else:
        results = store.similarity_search_with_relevance_scores(query, k=candidates_k)

    if not results:
        return []

    # Discard documents below minimum vector similarity threshold
    candidates = [doc for doc, score in results if score >= settings.retrieval_score_threshold]
    if not candidates:
        return []

    # Stage 2: Rerank & Select Top-K (BaseReranker or PassthroughReranker)
    threshold = settings.reranker_score_threshold if settings.reranker_provider != "none" else None
    return get_reranker().rerank(
        query=query,
        documents=candidates,
        top_n=top_k,
        score_threshold=threshold,
    )


