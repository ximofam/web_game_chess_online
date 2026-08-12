from fastapi import FastAPI
from app.api.router import api_router

app = FastAPI(title="AI Service API")

app.include_router(api_router, prefix="/api")
