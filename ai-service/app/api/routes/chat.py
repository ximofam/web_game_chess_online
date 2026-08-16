import logging
import uuid

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.auth import get_current_user_id
from app.core.db import get_db
from app.models.chat_session import ChatSession
from app.schemas.chat import (
    ChatMessagesResponse,
    ChatRequest,
    ChatResponse,
    ChatSessionListResponse,
    CreateSessionResponse,
)
from app.services.chat_service import (
    create_session,
    generate_session_title_task,
    get_session,
    get_session_messages,
    get_user_sessions,
    save_message,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat")


@router.post("/sessions", response_model=CreateSessionResponse)
async def create_chat_session(
    db: AsyncSession = Depends(get_db),
    user_id: uuid.UUID = Depends(get_current_user_id),
):
    """Create a new chat session and return its UUID."""
    session = await create_session(db, user_id)
    return CreateSessionResponse(session_id=session.id)


async def get_owned_session(
    session_id: uuid.UUID,
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> ChatSession:
    session = await get_session(db, session_id)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    if session.user_id != user_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    return session


@router.post("/{session_id}", response_model=ChatResponse)
async def chat(
    body: ChatRequest,
    req: Request,
    background_tasks: BackgroundTasks,
    session: ChatSession = Depends(get_owned_session),
    db: AsyncSession = Depends(get_db),
):
    """Send a message to an existing chat session and get the response."""
    graph = req.app.state.graph
    if graph is None:
        raise HTTPException(status_code=503, detail="DATABASE_URL is required for chat")

    await save_message(db, session.id, "user", body.question)

    config = {"configurable": {"thread_id": str(session.id)}}
    try:
        result = await graph.ainvoke(
            {"original_question": body.question, "chat_history": []},
            config,
        )
    except Exception:
        logger.exception("Chat graph invocation failed (session_id=%s)", session.id)
        raise HTTPException(status_code=502, detail="Failed to generate a response")

    answer = result["answer"]
    question_type = result.get("question_type")
    await save_message(db, session.id, "assistant", answer, question_type)

    if session.title is None:
        background_tasks.add_task(generate_session_title_task, session.id, body.question, answer)

    return ChatResponse(answer=answer, question_type=question_type)


@router.get("/sessions", response_model=ChatSessionListResponse)
async def list_sessions(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    """List chat sessions for the current user with pagination."""
    sessions, total = await get_user_sessions(db, user_id, page, size)
    return ChatSessionListResponse(
        items=sessions,
        total=total,
        page=page,
        size=size,
    )


@router.get("/{session_id}/messages", response_model=ChatMessagesResponse)
async def list_messages(
    session: ChatSession = Depends(get_owned_session),
    db: AsyncSession = Depends(get_db),
):
    """List all messages in a specific chat session."""
    messages = await get_session_messages(db, session.id)
    return ChatMessagesResponse(items=messages)
