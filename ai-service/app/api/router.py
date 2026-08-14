from fastapi import APIRouter
from app.api.routes import chat, health, rag

api_router = APIRouter()
api_router.include_router(health.router, tags=["health"])
api_router.include_router(rag.router, tags=["rag"])
api_router.include_router(chat.router, tags=["chat"])
