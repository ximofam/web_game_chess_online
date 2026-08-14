from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    vector_store: Literal["chroma", "pgvector"] = "chroma"
    vector_collection: str = "knowledge_doc"
    chroma_persist_directory: str = "data/chroma"
    database_url: str | None = None

    llm_provider: Literal["groq", "openai"] = "groq"
    groq_api_key: str | None = None
    groq_model: str = "llama-3.3-70b-versatile"
    openai_api_key: str | None = None
    openai_model: str = "gpt-4o-mini"

    embedding_provider: Literal["huggingface", "openai"] = "huggingface"
    huggingface_api_key: str | None = None
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"

    jwt_secret: str | None = None


@lru_cache
def get_settings() -> Settings:
    return Settings()
