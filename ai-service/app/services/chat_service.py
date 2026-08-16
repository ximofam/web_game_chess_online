import uuid

from sqlalchemy import desc, func, select
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


async def generate_session_title_task(session_id: uuid.UUID, question: str, answer: str) -> None:
    from app.ai.llm import get_router_llm
    from app.ai.prompts import TITLE_PROMPT
    from app.core.db import get_session_factory
    
    prompt = TITLE_PROMPT.format(question=question, answer=answer)
    try:
        title = get_router_llm().invoke(prompt).content.strip().strip("'\"")
    except Exception:
        return  # Ignore LLM errors in background task

    async with get_session_factory()() as db:
        session = await get_session(db, session_id)
        if session:
            session.title = title
            await db.commit()


async def get_user_sessions(db: AsyncSession, user_id: uuid.UUID, page: int = 1, size: int = 20) -> tuple[list[ChatSession], int]:
    offset = (page - 1) * size
    query = select(ChatSession).where(ChatSession.user_id == user_id)
    
    total = await db.scalar(select(func.count()).select_from(query.subquery()))
    
    stmt = query.order_by(desc(ChatSession.created_at)).offset(offset).limit(size)
    result = await db.execute(stmt)
    return list(result.scalars().all()), total or 0


async def get_session_messages(db: AsyncSession, session_id: uuid.UUID) -> list[AiChatMessage]:
    stmt = select(AiChatMessage).where(AiChatMessage.session_id == session_id).order_by(AiChatMessage.created_at)
    result = await db.execute(stmt)
    return list(result.scalars().all())
