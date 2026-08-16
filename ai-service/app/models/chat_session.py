import uuid
from typing import TYPE_CHECKING

from sqlalchemy import String
from sqlalchemy.dialects.postgresql import UUID as PgUUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models import _Model

if TYPE_CHECKING:
    from app.models.chat import AiChatMessage


class ChatSession(_Model):
    __tablename__ = "chat_sessions"
    __table_args__ = {"schema": "ai_service"}

    # user_id remains a pure UUID link to the public.users table
    user_id: Mapped[uuid.UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    title: Mapped[str | None] = mapped_column(String(100), nullable=True)

    messages: Mapped[list["AiChatMessage"]] = relationship(
        "AiChatMessage", back_populates="session", cascade="all, delete-orphan"
    )
