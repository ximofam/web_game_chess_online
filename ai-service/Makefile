.PHONY: run install migrate test graph clean

# Variables
VENV = .venv
PYTHON = $(VENV)/bin/python
PIP = $(VENV)/bin/pip
UVICORN = $(VENV)/bin/uvicorn
ALEMBIC = $(VENV)/bin/alembic

# Khởi chạy server FastAPI (Development)
run:
	$(UVICORN) app.main:app --reload --host 0.0.0.0 --port 8000

# Cài đặt thư viện từ requirements.txt
install:
	$(PIP) install -r requirements.txt

# Áp dụng database migrations (cần DATABASE_URL trong .env)
migrate:
	$(ALEMBIC) upgrade head

# Chạy test
test:
	$(PYTHON) -m pytest tests/

# Vẽ LangGraph ra docs/graph.md + docs/graph.png
graph:
	$(PYTHON) -m scripts.generate_graph


# Xóa các file cache sinh ra trong quá trình chạy
clean:
	find . -type d -name "__pycache__" -exec rm -rf {} +
	find . -type d -name ".pytest_cache" -exec rm -rf {} +
