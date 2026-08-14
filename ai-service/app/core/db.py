from functools import lru_cache

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import get_settings


@lru_cache
def get_session_factory() -> async_sessionmaker[AsyncSession]:
    """Cached async session factory. Requires DATABASE_URL."""
    settings = get_settings()
    if not settings.database_url:
        raise RuntimeError("DATABASE_URL is required for database access")
    engine = create_async_engine(
        settings.database_url,
        connect_args={"options": "-csearch_path=ai_service,public"},
    )
    return async_sessionmaker(engine, expire_on_commit=False)


async def get_db() -> AsyncSession:
    """FastAPI dependency yielding an AsyncSession."""
    async with get_session_factory()() as session:
        yield session
