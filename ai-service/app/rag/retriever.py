from langchain_core.documents import Document

from app.ai.vectorstore import get_engine, get_vector_store
from app.core.config import get_settings
from app.rag.reranker import get_reranker




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
