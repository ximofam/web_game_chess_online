.PHONY: run install migrate test graph clean crawl-openings ingest ingest-clear ingest-refresh ingest-all ingest-all-clear ingest-all-refresh ingest-system ingest-system-refresh ingest-chess ingest-chess-clear ingest-chess-refresh ingest-openings ingest-openings-clear ingest-openings-refresh

# Variables
VENV = .venv
PYTHON = $(VENV)/bin/python
PIP = $(VENV)/bin/pip
UVICORN = $(VENV)/bin/uvicorn
ALEMBIC = $(VENV)/bin/alembic

# Optional parameters (e.g. make ingest MODEL=BAAI/bge-m3 ARGS="--refresh-cache")
MODEL ?=
ARGS ?=
EMBED_OPT = $(if $(MODEL),--embedding-model $(MODEL),)

# Khởi chạy server FastAPI (Development)
run:
	$(UVICORN) app.main:app --reload --host 0.0.0.0 --port 8000

# Cài đặt thư viện từ requirements.txt
install:
	$(PIP) install -r requirements.txt

# Áp dụng database migrations (cần DATABASE_URL trong .env)
migrate:
	$(ALEMBIC) upgrade head

# Chạy test suite
test:
	$(PYTHON) -m pytest tests/

# Vẽ LangGraph ra docs/graph.md + docs/graph.png
graph:
	$(PYTHON) -m scripts.generate_graph

# ==============================================================================
# Document Ingestion (Unified & Domain-based with CSV Cache)
# ==============================================================================

# Ingest all domains into Vector Store (uses CSV cache if available)
ingest:
	$(PYTHON) -m scripts.ingest --domain all $(EMBED_OPT) $(ARGS)

# Ingest all domains with entire store cleared first
ingest-clear:
	$(PYTHON) -m scripts.ingest --domain all --clear-all $(EMBED_OPT) $(ARGS)

# Force re-embedding and overwrite CSV cache for all domains
ingest-refresh:
	$(PYTHON) -m scripts.ingest --domain all --refresh-cache $(EMBED_OPT) $(ARGS)

# Clear entire store AND refresh CSV cache
ingest-clear-refresh:
	$(PYTHON) -m scripts.ingest --domain all --clear-all --refresh-cache $(EMBED_OPT) $(ARGS)

# Aliases
ingest-all: ingest
ingest-all-clear: ingest-clear
ingest-all-refresh: ingest-refresh

# --- Domain: system (Markdown docs) ---
ingest-system:
	$(PYTHON) -m scripts.ingest --domain system $(EMBED_OPT) $(ARGS)

ingest-system-refresh:
	$(PYTHON) -m scripts.ingest --domain system --refresh-cache $(EMBED_OPT) $(ARGS)

# --- Domain: chess_law (FIDE Laws of Chess PDF) ---
ingest-chess:
	$(PYTHON) -m scripts.ingest --domain chess_law $(EMBED_OPT) $(ARGS)

ingest-chess-clear:
	$(PYTHON) -m scripts.ingest_chess --clear $(EMBED_OPT) $(ARGS)

ingest-chess-refresh:
	$(PYTHON) -m scripts.ingest --domain chess_law --refresh-cache $(EMBED_OPT) $(ARGS)

# --- Domain: chess_opening (Opening Theory JSONL) ---
crawl-openings:
	$(PYTHON) -m scripts.crawl_wikibooks

ingest-openings:
	$(PYTHON) -m scripts.ingest --domain chess_opening $(EMBED_OPT) $(ARGS)

ingest-openings-clear:
	$(PYTHON) -m scripts.ingest_openings --clear $(EMBED_OPT) $(ARGS)

ingest-openings-refresh:
	$(PYTHON) -m scripts.ingest --domain chess_opening --refresh-cache $(EMBED_OPT) $(ARGS)

# ==============================================================================
# Cleanup
# ==============================================================================

# Xóa các file cache sinh ra trong quá trình chạy
clean:
	find . -type d -name "__pycache__" -exec rm -rf {} +
	find . -type d -name ".pytest_cache" -exec rm -rf {} +
	find . -type d -name ".ruff_cache" -exec rm -rf {} +
