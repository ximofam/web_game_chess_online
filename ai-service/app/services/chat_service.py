import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.chat import AiChatMessage
from app.models.chat_session import ChatSession


async def create_session(db: AsyncSession, user_id: uuid.UUID) -> ChatSession:
    session = ChatSession(user_id=user_id)
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return session


async def get_session(db: AsyncSession, session_id: uuid.UUID) -> ChatSession | None:
    result = await db.execute(select(ChatSession).where(ChatSession.id == session_id))
    return result.scalar_one_or_none()


async def save_message(
    db: AsyncSession,
    session_id: uuid.UUID,
    role: str,
    content: str,
    question_type: str | None = None,
) -> None:
    db.add(
        AiChatMessage(
            session_id=session_id,
            role=role,
            content=content,
            question_type=question_type,
        )
    )
    await db.commit()
