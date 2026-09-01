#!/bin/bash
set -e

echo "Applying database migrations..."
alembic upgrade head

echo "Checking if Vector Store ingestion is needed..."
# Run a quick python script to check if vector embeddings exist
CHECK_RESULT=$(python -c "
from app.core.config import get_settings
from sqlalchemy import create_engine, text
import os

settings = get_settings()
try:
    if settings.vector_store == 'pgvector':
        if not settings.database_url:
            print('NO_DATA:DATABASE_URL_NOT_SET')
        else:
            engine = create_engine(settings.database_url)
            with engine.connect() as conn:
                count = conn.execute(text('SELECT count(*) FROM ai_service.langchain_pg_embedding')).scalar()
                if count and count > 0:
                    print(f'HAS_DATA:{count}')
                else:
                    print('NO_DATA:EMPTY_TABLE')
    else:
        if os.path.exists(settings.chroma_persist_directory) and os.listdir(settings.chroma_persist_directory):
            print('HAS_DATA')
        else:
            print('NO_DATA')
except Exception as e:
    print(f'NO_DATA:ERROR:{e}')
")

echo "Check result: $CHECK_RESULT"

if echo "$CHECK_RESULT" | grep -q "HAS_DATA"; then
    echo "Vector store already populated. Skipping ingestion."
else
    echo "Vector store is empty or unpopulated. Running unified ingestion (without CSV cache)..."
    python -m scripts.ingest --domain all --no-cache
fi

echo "Starting FastAPI server..."
exec uvicorn app.main:app --host 0.0.0.0 --port 8000

