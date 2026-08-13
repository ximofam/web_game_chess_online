from functools import lru_cache

from app.core.config import get_settings


@lru_cache
def get_embeddings():
    settings = get_settings()
    if settings.embedding_provider == "huggingface":
        if not settings.huggingface_api_key:
            raise ValueError("HUGGINGFACE_API_KEY is required")
        from langchain_huggingface import HuggingFaceEndpointEmbeddings

        return HuggingFaceEndpointEmbeddings(
            model=settings.embedding_model,
            huggingfacehub_api_token=settings.huggingface_api_key,
        )

    if not settings.openai_api_key:
        raise ValueError("OPENAI_API_KEY is required")
    from langchain_openai import OpenAIEmbeddings

    return OpenAIEmbeddings(model=settings.embedding_model, api_key=settings.openai_api_key)
