from functools import lru_cache

from langchain_groq import ChatGroq

from app.core.config import get_settings


@lru_cache
def get_llm():
    settings = get_settings()
    if settings.llm_provider == "groq":
        if not settings.groq_api_key:
            raise ValueError("GROQ_API_KEY is required")
        return ChatGroq(model=settings.groq_model, api_key=settings.groq_api_key)

    if not settings.openai_api_key:
        raise ValueError("OPENAI_API_KEY is required")
    from langchain_openai import ChatOpenAI

    return ChatOpenAI(model=settings.openai_model, api_key=settings.openai_api_key)


@lru_cache
def get_router_llm():
    settings = get_settings()
    if settings.llm_provider == "groq":
        if not settings.groq_api_key:
            raise ValueError("GROQ_API_KEY is required")
        return ChatGroq(model=settings.groq_router_model, api_key=settings.groq_api_key)

    if not settings.openai_api_key:
        raise ValueError("OPENAI_API_KEY is required")
    from langchain_openai import ChatOpenAI

    return ChatOpenAI(model=settings.openai_router_model, api_key=settings.openai_api_key)
