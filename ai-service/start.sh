#!/bin/bash
set -e

echo "Applying database migrations..."
alembic upgrade head

echo "Checking if Vector Store ingestion is needed..."
# Run a quick python script to check if data exists
CHECK_RESULT=$(python -c "
from app.core.config import get_settings
from sqlalchemy import create_engine, text

settings = get_settings()
try:
    if settings.vector_store == 'pgvector':
        engine = create_engine(settings.database_url)
        with engine.connect() as conn:
            count = conn.execute(text('SELECT count(*) FROM ai_service.langchain_pg_collection')).scalar()
            if count == 0:
                print('NO_DATA')
            else:
                print('HAS_DATA')
    else:
        import os
        if not os.path.exists(settings.chroma_persist_directory):
            print('NO_DATA')
        else:
            print('HAS_DATA')
except Exception as e:
    print('NO_DATA')
")

if echo "$CHECK_RESULT" | grep -q "NO_DATA"; then
    echo "Vector store is empty. Running ingestion..."
    if [ -d "./docs/business/viechess" ]; then
        echo "Ingesting system documentation..."
        python -m scripts.ingest --path ./docs/business/viechess
    else
        echo "Warning: docs/business/viechess folder not found. Skipping system ingest."
    fi

    if [ -f "./docs/chess/fide/20230101Laws-of-Chess.pdf" ]; then
        echo "Ingesting FIDE chess rules..."
        python -m scripts.ingest_chess --path ./docs/chess/fide/20230101Laws-of-Chess.pdf
    else
        echo "Warning: docs/chess/fide/20230101Laws-of-Chess.pdf not found. Skipping chess ingest."
    fi
else
    echo "Vector store already populated. Skipping ingestion."
fi

echo "Starting FastAPI server..."
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
