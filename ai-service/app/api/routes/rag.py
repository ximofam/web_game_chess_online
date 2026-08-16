import uuid

from fastapi import APIRouter, Depends, HTTPException
from pydantic import ValidationError

from app.core.auth import require_superuser
from app.schemas.rag import DocumentRequest
from app.services.rag_service import add_document

router = APIRouter(prefix="/rag")


@router.post("/documents")
def create_document(
    document: DocumentRequest,
    user_id: uuid.UUID = Depends(require_superuser),
):
    try:
        return {"id": add_document(document.content, document.metadata)}
    except (ValidationError, ValueError) as error:
        raise HTTPException(status_code=503, detail="RAG configuration is invalid") from error
