import uuid

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    question: str = Field(min_length=1)


class CreateSessionResponse(BaseModel):
    session_id: uuid.UUID


class ChatResponse(BaseModel):
    answer: str
    question_type: str | None = None


from datetime import datetime

class ChatSessionItem(BaseModel):
    id: uuid.UUID
    title: str | None = None
    created_at: datetime

    class Config:
        from_attributes = True


class ChatSessionListResponse(BaseModel):
    items: list[ChatSessionItem]
    total: int
    page: int
    size: int


class ChatMessageItem(BaseModel):
    id: uuid.UUID
    role: str
    content: str
    question_type: str | None = None
    created_at: datetime

    class Config:
        from_attributes = True


class ChatMessagesResponse(BaseModel):
    items: list[ChatMessageItem]
