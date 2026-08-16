from pydantic import BaseModel, Field


class DocumentRequest(BaseModel):
    content: str = Field(min_length=1)
    metadata: dict[str, str] = Field(default_factory=dict)
