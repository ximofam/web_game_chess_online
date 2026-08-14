import uuid

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    question: str = Field(min_length=1)


class CreateSessionResponse(BaseModel):
    session_id: uuid.UUID
