import uuid

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.auth import get_current_user_id
from app.core.db import get_db, get_session_factory
from app.models.chat import AiChatMessage
from app.schemas.chat import ChatRequest, CreateSessionResponse
from app.services.chat_service import create_session, get_session, save_message

router = APIRouter(prefix="/chat")


@router.post("/session", response_model=CreateSessionResponse)
async def create_chat_session(
    db: AsyncSession = Depends(get_db),
    user_id: uuid.UUID = Depends(get_current_user_id),
):
    """Create a new chat session and return its UUID."""
    session = await create_session(db, user_id)
    return CreateSessionResponse(session_id=session.id)


@router.post("/{session_id}")
async def chat(
    session_id: uuid.UUID,
    body: ChatRequest,
    req: Request,
    user_id: uuid.UUID = Depends(get_current_user_id),
):
    """Send a message to an existing chat session and stream the response."""
    graph = req.app.state.graph
    if graph is None:
        raise HTTPException(status_code=503, detail="DATABASE_URL is required for chat")

    async def event_stream():
        async with get_session_factory()() as db:
            # 1. Verify session exists and belongs to user
            session = await get_session(db, session_id)
            if not session:
                yield f"event: error\ndata: Session not found\n\n"
                return
            if session.user_id != user_id:
                yield f"event: error\ndata: Forbidden\n\n"
                return

            # 2. Save user message
            db.add(AiChatMessage(session_id=session.id, role="user", content=body.question))
            await db.commit()

            full_answer = ""
            question_type: str | None = None
            config = {"configurable": {"thread_id": str(session.id)}}

            try:
                # 3. Stream from LangGraph
                async for event in graph.astream_events(
                    {"original_question": body.question, "chat_history": []},
                    config,
                    version="v2",
                ):
                    node = event.get("metadata", {}).get("langgraph_node")
                    if event["event"] == "on_chain_end" and node == "analyze_question":
                        output = event.get("data", {}).get("output")
                        if isinstance(output, dict) and "question_type" in output:
                            question_type = output["question_type"]

                    if event["event"] == "on_chat_model_stream":
                        chunk = event["data"]["chunk"].content
                        if chunk:
                            full_answer += chunk
                            yield f"data: {chunk}\n\n"

            except Exception as exc:
                yield f"event: error\ndata: {exc}\n\n"
                return

            # 4. Save assistant response
            await save_message(db, session.id, "assistant", full_answer, question_type)
            yield "event: done\ndata: [DONE]\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
