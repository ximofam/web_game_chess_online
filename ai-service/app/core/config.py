from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    vector_collection: str = "knowledge_doc"
    retrieval_score_threshold: float = 0.5  # docs below this relevance score are discarded
    database_url: str | None = None
    cors_origins: list[str] = ["http://localhost:5173"]

    llm_provider: Literal["groq", "openai"] = "groq"
    groq_api_key: str | None = None
    groq_model: str = "llama-3.3-70b-versatile"
    groq_router_model: str = "llama-3.1-8b-instant"
    groq_vision_model: str = "llama-3.2-11b-vision-preview"
    openai_api_key: str | None = None
    openai_model: str = "gpt-4o-mini"
    openai_router_model: str = "gpt-4o-mini"
    openai_vision_model: str = "gpt-4o-mini"

    vision_provider: Literal["groq", "openai"] | None = None

    embedding_provider: Literal["huggingface", "huggingface_local", "local", "openai"] = "huggingface"
    huggingface_api_key: str | None = None
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    embedding_device: str = "cpu"

    reranker_provider: Literal["huggingface", "huggingface_local", "local", "none"] = "huggingface_local"
    reranker_model: str = "cross-encoder/ms-marco-MiniLM-L-6-v2"
    reranker_device: str = "cpu"
    reranker_candidates_k: int = 8
    reranker_score_threshold: float = 0.05
    reranker_timeout_seconds: float = 10.0

    jwt_secret: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
