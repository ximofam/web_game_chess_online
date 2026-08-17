import warnings

warnings.filterwarnings("ignore", message=".*allowed_objects.*")

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.core.config import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    if settings.database_url:
        from app.graph.builder import graph_lifespan
        async with graph_lifespan(settings.database_url) as compiled_graph:
            app.state.graph = compiled_graph
            yield
    else:
        # ponytail: no DB = no chat persistence or checkpointing. Chat endpoint returns 503.
        app.state.graph = None
        yield


app = FastAPI(title="AI Service API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=get_settings().cors_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)
app.include_router(api_router, prefix="/api")
