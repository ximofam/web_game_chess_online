.PHONY: run install test clean

# Variables
VENV = .venv
PYTHON = $(VENV)/bin/python
PIP = $(VENV)/bin/pip
UVICORN = $(VENV)/bin/uvicorn

# Khởi chạy server FastAPI (Development)
run:
	$(UVICORN) app.main:app --reload --host 0.0.0.0 --port 8000

# Cài đặt thư viện từ requirements.txt
install:
	$(PIP) install -r requirements.txt

# Chạy test
test:
	$(PYTHON) -m pytest tests/

# Xóa các file cache sinh ra trong quá trình chạy
clean:
	find . -type d -name "__pycache__" -exec rm -rf {} +
	find . -type d -name ".pytest_cache" -exec rm -rf {} +
