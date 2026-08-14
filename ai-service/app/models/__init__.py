import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, func
from sqlalchemy.dialects.postgresql import UUID as PgUUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class _Model(Base):
    """Abstract base for all ai_service models.

    Provides:
    - id: UUIDv7 primary key (Python 3.14+ uuid.uuid7())
    - created_at: set by DB on INSERT
    - updated_at: set by DB on INSERT; updated by ORM on UPDATE

    ponytail: updated_at uses Python-level onupdate — raw SQL bypasses it.
    Ceiling: drift if records updated outside ORM (e.g. psql console, bulk SQL).
    Upgrade path: add a PostgreSQL trigger for server-side enforcement.
    """

    __abstract__ = True

    id: Mapped[uuid.UUID] = mapped_column(
        PgUUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid7,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=lambda: datetime.now(timezone.utc),
    )
