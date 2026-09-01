from functools import lru_cache
from typing import Any

from sqlalchemy import create_engine, event

from app.ai.embeddings import get_embeddings
from app.core.config import get_settings


@lru_cache
def get_engine():
    """Create and cache the SQLAlchemy Engine with search_path protection for PostgreSQL."""
    settings = get_settings()
    if not settings.database_url:
        raise ValueError("DATABASE_URL is required for PGVector")

    is_postgres = "postgres" in settings.database_url.lower()
    connect_args = {"options": "-csearch_path=ai_service,public"} if is_postgres else {}

    engine = create_engine(
        settings.database_url,
        connect_args=connect_args,
        pool_pre_ping=True,
    )

    if is_postgres:
        @event.listens_for(engine, "connect")
        def set_search_path_on_connect(dbapi_connection: Any, connection_record: Any) -> None:
            with dbapi_connection.cursor() as cursor:
                cursor.execute("SET search_path TO ai_service, public;")

        @event.listens_for(engine, "checkout")
        def set_search_path_on_checkout(
            dbapi_connection: Any, connection_record: Any, connection_proxy: Any
        ) -> None:
            with dbapi_connection.cursor() as cursor:
                cursor.execute("SET search_path TO ai_service, public;")

    return engine


@lru_cache
def get_vector_store():
    """Initialize and cache the PGVector store instance."""
    settings = get_settings()
    if not settings.database_url:
        raise ValueError("DATABASE_URL is required for PGVector")
    from langchain_postgres import PGVector

    engine = get_engine()
    return PGVector(
        embeddings=get_embeddings(),
        collection_name=settings.vector_collection,
        connection=engine,
        use_jsonb=True,
        create_extension=False,
    )
