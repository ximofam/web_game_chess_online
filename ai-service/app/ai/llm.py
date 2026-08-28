from functools import lru_cache
from typing import Any, Literal

from langchain_core.language_models.chat_models import BaseChatModel

from app.core.config import Settings, get_settings

ProviderType = Literal["groq", "openai", "ollama"]


class LLMFactory:
    """Centralized, reusable factory for instantiating LangChain chat models."""

    @staticmethod
    def create(
        provider: ProviderType | str,
        model: str,
        *,
        temperature: float = 0.0,
        streaming: bool = False,
        settings: Settings | None = None,
        **kwargs: Any,
    ) -> BaseChatModel:
        """Create a chat model instance for any supported provider without repeating setup logic."""
        cfg = settings or get_settings()
        prov = provider.lower()

        if prov == "ollama":
            from langchain_ollama import ChatOllama

            return ChatOllama(
                model=model,
                base_url=cfg.ollama_base_url,
                temperature=temperature,
                **kwargs,
            )

        if prov == "groq":
            if not cfg.groq_api_key:
                raise ValueError("GROQ_API_KEY is required for Groq provider")
            from langchain_groq import ChatGroq

            return ChatGroq(
                model=model,
                api_key=cfg.groq_api_key,
                temperature=temperature,
                streaming=streaming,
                **kwargs,
            )

        if prov == "openai":
            if not cfg.openai_api_key:
                raise ValueError("OPENAI_API_KEY is required for OpenAI provider")
            from langchain_openai import ChatOpenAI

            return ChatOpenAI(
                model=model,
                api_key=cfg.openai_api_key,
                temperature=temperature,
                streaming=streaming,
                **kwargs,
            )

        raise ValueError(f"Unsupported LLM provider: '{provider}'. Allowed: 'groq', 'openai', 'ollama'")


@lru_cache
def get_llm() -> BaseChatModel:
    """Get the primary Chat / RAG generation LLM."""
    cfg = get_settings()
    prov = cfg.llm_provider
    model_map = {
        "groq": cfg.groq_model,
        "openai": cfg.openai_model,
        "ollama": cfg.ollama_model,
    }
    return LLMFactory.create(prov, model_map[prov], temperature=0.7)


@lru_cache
def get_router_llm() -> BaseChatModel:
    """Get the fast Router / Query Rewrite LLM."""
    cfg = get_settings()
    prov = cfg.router_provider or cfg.llm_provider
    model_map = {
        "groq": cfg.groq_router_model,
        "openai": cfg.openai_router_model,
        "ollama": cfg.ollama_router_model,
    }
    return LLMFactory.create(prov, model_map[prov], temperature=0.0)


@lru_cache
def get_vision_llm() -> BaseChatModel:
    """Get the Multimodal Vision / Diagram parsing LLM."""
    cfg = get_settings()
    prov = cfg.vision_provider or cfg.llm_provider
    model_map = {
        "groq": cfg.groq_vision_model,
        "openai": cfg.openai_vision_model,
        "ollama": cfg.ollama_vision_model,
    }
    return LLMFactory.create(prov, model_map[prov], temperature=0.0)



