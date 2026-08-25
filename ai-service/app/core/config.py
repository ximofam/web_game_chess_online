from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    vector_store: Literal["chroma", "pgvector"] = "chroma"
    vector_collection: str = "knowledge_doc"
    chroma_persist_directory: str = "data/chroma"
    retrieval_score_threshold: float = 0.1  # docs below this relevance score are discarded
    database_url: str | None = None
    cors_origins: list[str] = ["http://localhost:5173"]

    llm_provider: Literal["groq", "openai"] = "groq"
    groq_api_key: str | None = None
    groq_model: str = "llama-3.3-70b-versatile"
    groq_router_model: str = "llama-3.3-70b-versatile"
    groq_vision_model: str = "llama-3.2-11b-vision-preview"
    openai_api_key: str | None = None
    openai_model: str = "gpt-4o-mini"
    openai_router_model: str = "gpt-4o-mini"
    openai_vision_model: str = "gpt-4o-mini"

    vision_provider: Literal["groq", "openai"] | None = None

    embedding_provider: Literal["huggingface", "openai"] = "huggingface"
    huggingface_api_key: str | None = None
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"

    jwt_secret: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
