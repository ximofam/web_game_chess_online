from fastapi import APIRouter, HTTPException
from pydantic import ValidationError

from app.schemas.rag import AskRequest, DocumentRequest
from app.services.rag_service import add_document, answer

router = APIRouter(prefix="/rag")


@router.post("/documents")
def create_document(document: DocumentRequest):
    try:
        return {"id": add_document(document.content, document.metadata)}
    except (ValidationError, ValueError) as error:
        raise HTTPException(status_code=503, detail="RAG configuration is invalid") from error


@router.post("/ask")
def ask_question(request: AskRequest):
    try:
        answer_text, sources = answer(request.question, request.top_k)
        return {"answer": answer_text, "sources": sources}
    except (ValidationError, ValueError) as error:
        raise HTTPException(status_code=503, detail="RAG configuration is invalid") from error
