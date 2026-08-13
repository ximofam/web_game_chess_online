from pydantic import BaseModel, Field


class DocumentRequest(BaseModel):
    content: str = Field(min_length=1)
    metadata: dict[str, str] = Field(default_factory=dict)


class AskRequest(BaseModel):
    question: str = Field(min_length=1)
    top_k: int = Field(default=4, ge=1, le=10)
