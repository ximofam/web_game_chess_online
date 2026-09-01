from functools import lru_cache
import os

from app.core.config import get_settings


@lru_cache
def get_embeddings():
    settings = get_settings()
    if settings.embedding_provider in ("huggingface_local", "local"):
        import torch

        if settings.embedding_device == "cpu" and hasattr(torch, "set_num_threads"):
            cpu_count = os.cpu_count() or 4
            try:
                torch.set_num_threads(cpu_count)
            except Exception:
                pass

        from langchain_huggingface import HuggingFaceEmbeddings

        return HuggingFaceEmbeddings(
            model_name=settings.embedding_model,
            model_kwargs={"device": settings.embedding_device},
            encode_kwargs={"batch_size": 32, "normalize_embeddings": True},
        )

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
