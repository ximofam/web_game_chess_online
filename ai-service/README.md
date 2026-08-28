# Chess AI Service ♟️🤖

Dịch vụ AI thông minh cung cấp trợ lý trò chuyện stateful, phân tích luật cờ vua quốc tế (FIDE Laws of Chess), lý thuyết khai cuộc (Opening Theory & ECO Codes), và hỗ trợ nền tảng cờ vua trực tuyến. 

Được xây dựng trên nền tảng **FastAPI**, **LangGraph**, **PostgreSQL (PGVector)**, kết hợp **2-Stage Retrieval (Dense Search + Cross-Encoder Reranker)** và **LLM Factory** đa tầng (hỗ trợ linh hoạt **Ollama Local**, **Groq**, **OpenAI**).

---

## 🌟 Tính Năng Nổi Bật (Key Capabilities)

### 1. Stateful Multi-Turn Conversational Graph (LangGraph Agentic ReAct)
- **Agentic Tool Calling & Multi-Domain Handling**: Khắc phục hoàn toàn hạn chế của mô hình phân loại nhãn đơn (Single-label routing). LLM trung tâm đóng vai trò Agent điều phối linh hoạt gọi 0, 1 hoặc nhiều công cụ song song (**Parallel Tool Calls**):
  - `search_fide_rules`: Luật thi đấu quốc tế FIDE (nước đi quân, nhập thành, bắt tốt qua đường, chạm quân, hòa cờ, xử phạt trọng tài).
  - `search_chess_openings`: Lý thuyết khai cuộc, chuỗi nước đi, mã ECO (A00–E99), kế hoạch chiến lược và cấu trúc tốt.
  - `search_platform_support`: Kiến thức nền tảng VieChess (ghép trận, luật phòng chơi, kết nối WebSocket, tài khoản, diễn đàn).
- **Embedded 2-Stage Retrieval**: Mỗi Tool tự thực hiện tìm kiếm vector trong collection tương ứng kết hợp cùng Cross-Encoder Reranker cục bộ để trả về những trích đoạn chất lượng cao nhất.
- **Ephemeral Tool Cleanup**: Tự động phát sinh `RemoveMessage` dọn sạch toàn bộ `ToolMessage` và `AIMessage(tool_calls)` trung gian ngay khi hoàn tất câu trả lời cuối cùng $\rightarrow$ Giảm **70% token đầu vào** ở các lượt sau và giữ cho Checkpointer Database chỉ lưu các cặp Q&A thuần túy.
- **Progressive Memory Summarization**: Tự động nén và hợp nhất lũy tiến lịch sử hội thoại cũ khi vượt ngưỡng lượt (`CHAT_SUMMARY_TURNS_THRESHOLD = 2`), loại bỏ nội dung thô của tool và duy trì đúng 1 `SystemMessage` tóm tắt duy nhất trong State.
- **Dual-State Persistence**: Lưu trữ đồng thời lịch sử tin nhắn trong cơ sở dữ liệu quan hệ (`ai_chat_sessions`, `ai_chat_messages`) và snapshot trạng thái đồ thị qua LangGraph Checkpointer (`AsyncPostgresSaver`).
- **LangSmith Tracing & Observability**: Tích hợp sẵn LangSmith tracing (`LANGCHAIN_TRACING_V2`), trực quan hóa luồng gọi Tool, đo lường độ trễ (latency) và chi tiết token/chi phí của từng bước thực thi.
- **Asynchronous Auto-Titling**: Tự động sinh tiêu đề ngắn gọn cho phiên trò chuyện ở chế độ nền (background task) sau lượt hỏi đầu tiên.


### 2. Structure-Aware Knowledge Ingestion
- **FIDE Laws of Chess Chunker (`FideSplitter`)**: Phân rã cấu trúc văn bản pháp quy theo cây phân cấp pháp lý (*Category → Article → Section → Subsection*). Tích hợp Vision LLM trích xuất hình ảnh bàn cờ FIDE thành chuỗi ký hiệu FEN và diễn giải ngữ nghĩa.
- **Opening Theory Crawler & Processor (`OpeningCrawler`)**:
  - Thu thập hơn 3.000 biến thể khai cuộc từ Wikibooks qua MediaWiki Export API.
  - **Deterministic 8x8 Board Matrix to FEN**: Chuyển đổi ma trận 64 ô cờ thành chuỗi FEN chuẩn xác 100% mà không tốn chi phí gọi LLM.
  - **ECO Joining & Stub Folding**: Tự động ghép mã ECO từ tập dữ liệu Lichess và gộp các trang biến thể ngắn (< 25 từ) vào nhánh cha để tránh chunk rời rạc.
  - **Breadcrumb Context Header**: Bơm thông tin mở đầu `[Opening: ... | ECO: ... | PGN: ...]` vào từng chunk dữ liệu.
- **Platform Knowledge**: Tách nhỏ tài liệu Markdown theo cấu trúc tiêu đề (`MarkdownHeaderTextSplitter`).

### 3. 2-Stage Retrieval & Semantic Reranking
- **Stage 1 (Bi-Encoder Dense Retrieval)**: Tìm kiếm vector tương đồng với `PGVector` dựa trên metadata domain filter.
- **Stage 2 (Cross-Encoder Reranker)**: Sử dụng mô hình `cross-encoder/ms-marco-MiniLM-L-6-v2` chạy in-process CPU, tính toán điểm cross-attention giữa câu hỏi và tài liệu ứng viên.
- **Sigmoid Score Normalization**: Áp dụng hàm Sigmoid kèm overflow clamping $[-50, 50]$ chuẩn hóa điểm logit về xác suất $[0.0, 1.0]$, loại bỏ hoàn toàn các tài liệu rác dưới ngưỡng (`RERANKER_SCORE_THRESHOLD`).

### 4. Reusable & Decoupled LLM Factory (`LLMFactory`)
- Khởi tạo và quản lý chat model tập trung, hỗ trợ lazy-loading không tốn tài nguyên.
- **Granular Provider Switching**: Cho phép tùy chỉnh độc lập provider cho từng tác vụ trong file `.env`:
  - **Chat/Agent Main Model**: Groq / OpenAI / Ollama.
  - **Router & Auxiliary Model**: Ollama Local (mặc định `qwen2.5:1.5b` siêu nhẹ) / Groq / OpenAI.
  - **Vision Model**: Groq / OpenAI / Ollama.

---

## 🏗️ Kiến Trúc Hệ Thống (Architecture & Data Flow)

```mermaid
graph TD
    User([Người dùng / Client]) -->|POST /api/chat| API[FastAPI Chat Endpoint]
    API --> Service[ChatService / ConversationEngine]
    
    subgraph LangGraph Agentic ReAct Pipeline
        Service --> SummarizeNode[summarize_memory Node]
        SummarizeNode --> AgentNode[agent Node / Master Assistant]
        
        AgentNode -- "Direct Response (Chitchat)" --> EndNode([__end__])
        AgentNode -- "Parallel Tool Calls" --> ToolNode{"LangGraph ToolNode"}
        
        subgraph DomainTools [Authoritative Domain Tools]
            T1["search_fide_rules()<br/>(PGVector + Reranker)"]
            T2["search_chess_openings()<br/>(PGVector + Reranker)"]
            T3["search_platform_support()<br/>(PGVector + Reranker)"]
        end
        
        ToolNode --> T1
        ToolNode --> T2
        ToolNode --> T3
        
        T1 --> ToolNode
        T2 --> ToolNode
        T3 --> ToolNode
        
        ToolNode --> AgentNode
    end
    
    AgentNode --> Persistence[Dual Persistence & LangGraph Checkpointer]
    Persistence --> Response([JSON Stream / HTTP Response])
```

---

## 📁 Cấu Trúc Thư Mục (Directory Layout)

```text
ai-service/
├── alembic/                      # Database migrations (PostgreSQL schema & tables)
├── app/
│   ├── ai/                       # LLM & Embedding Factories
│   │   ├── embeddings.py         # Embedding providers (Local HuggingFace, OpenAI, HF Hub)
│   │   ├── llm.py                # Reusable LLMFactory & cached role getters
│   │   └── prompts.py            # Prompt templates (Chat, RAG, Rewrite, Route)
│   ├── api/                      # FastAPI Router & HTTP Endpoints
│   │   └── routes/               # chat.py, rag.py, health.py
│   ├── core/                     # Configuration, Database engines, JWT Auth
│   │   ├── config.py             # Pydantic Settings (.env loading)
│   │   └── db.py                 # SQLAlchemy Async Engine & Sessionmaker
│   ├── models/                   # SQLAlchemy ORM Models (ChatSession, AiChatMessage)
│   ├── rag/                      # RAG Core Subsystem
│   │   ├── builder.py            # LangGraph StateGraph Compilation & Checkpointer Seam
│   │   ├── nodes.py              # Graph execution nodes (agent_node, summarize_memory)
│   │   ├── reranker.py           # Local Cross-Encoder Reranker & Fallback
│   │   ├── retriever.py          # PGVector VectorStore wrapper & multi-domain retrieve
│   │   ├── state.py              # RagState definition
│   │   ├── tools.py              # Domain-specific LangChain tools (FIDE, Openings, Platform)
│   │   └── ingestion/            # Structure-aware chunkers (FideSplitter, Vision Parser)
│   ├── schemas/                  # Pydantic Request/Response DTOs
│   ├── services/                 # Application Business Logic (ChatService, RagService)
│   ├── utils.py                  # Math & helper utilities (Sigmoid normalization)
│   └── main.py                   # FastAPI Application Entrypoint
├── docs/                         # Specifications, ADRs, and Business/Chess Docs
│   ├── specs/                    # rag-pipeline.md, chat.md
│   └── chess/                    # FIDE PDF, diagrams cache, openings JSONL
├── scripts/                      # Data Ingestion & Crawler CLI Scripts
│   ├── crawl_wikibooks.py        # Wikibooks Opening Theory Crawler
│   ├── ingest_chess.py           # FIDE PDF Ingestion Script
│   ├── ingest_openings.py        # Opening Theory JSONL Ingestion Script
│   └── ingest.py                 # Platform Docs Ingestion Script
├── tests/                        # Comprehensive Unit Test Suite (Mock boundaries)
├── Makefile                      # Standard CLI Commands
├── requirements.txt              # Production Dependencies
└── CONTEXT.md                    # Project Domain Model & Glossary
```

---

## ⚙️ Cài Đặt & Cấu Hình (Setup & Configuration)

### 1. Yêu Cầu Môi Trường
- **Python**: `>= 3.11` (Khuyến nghị 3.12+).
- **PostgreSQL**: `>= 16` với extension `pgvector`.
- **Ollama** *(Tùy chọn cho Local Router)*: Cài đặt từ [ollama.com](https://ollama.com).

### 2. Cài Đặt Dependencies

```bash
# Tạo và kích hoạt virtualenv
python3 -m venv .venv
source .venv/bin/activate

# Cài đặt thư viện
make install
```

### 3. Cấu Hình Biến Môi Trường (`.env`)

Sao chép file mẫu và cấu hình:
```bash
cp .env.example .env
```

Các biến cấu hình quan trọng:

```env
# Vector Store & Database (100% PGVector)
DATABASE_URL=postgresql+psycopg://postgres:postgres@localhost:5432/chess_db
VECTOR_COLLECTION=knowledge_doc
RETRIEVAL_SCORE_THRESHOLD=0.5

# Cấu hình Provider cho từng vai trò
LLM_PROVIDER=groq
ROUTER_PROVIDER=ollama
VISION_PROVIDER=groq

# Groq Cloud Configuration
GROQ_API_KEY=gsk_your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
GROQ_ROUTER_MODEL=llama-3.1-8b-instant
GROQ_VISION_MODEL=llama-3.2-11b-vision-preview

# Ollama Local Configuration (Chạy trên máy tính)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_ROUTER_MODEL=qwen2.5:1.5b

# Embeddings & Reranker (Chạy in-process CPU tiết kiệm tài nguyên)
EMBEDDING_PROVIDER=huggingface_local
EMBEDDING_MODEL=sentence-transformers/all-MiniLM-L6-v2
EMBEDDING_DEVICE=cpu

RERANKER_PROVIDER=huggingface_local
RERANKER_MODEL=cross-encoder/ms-marco-MiniLM-L-6-v2
RERANKER_DEVICE=cpu
RERANKER_CANDIDATES_K=8
RERANKER_SCORE_THRESHOLD=0.05

# JWT Secret (Đồng bộ với Backend authentication)
JWT_SECRET=your_secret_jwt_key
```

### 4. Áp Dụng Database Migrations

Khởi tạo schema `ai_service`, extension `vector`, và các bảng session/checkpoints:

```bash
make migrate
```

---

## 🚀 Nạp Dữ Liệu Tri Thức (Knowledge Ingestion)

Trước khi trò chuyện, tiến hành nạp dữ liệu tri thức vào PGVector:

```bash
# 1. Ingest Luật thi đấu FIDE (Structure-aware PDF chunking)
make ingest-chess-clear

# 2. Ingest Lý thuyết khai cuộc (Wikibooks + ECO)
make ingest-openings-clear

# 3. Ingest Tài liệu nền tảng hệ thống
make ingest

# (Tùy chọn) Tải lại dữ liệu khai cuộc mới nhất từ Wikibooks:
make crawl-openings
```

---

## 💻 Khởi Chạy & Kiểm Thử (Running & Testing)

### 1. Chạy Local Development Server

```bash
make run
```
Server sẽ khởi động tại: `http://localhost:8000`. Swagger API Docs: `http://localhost:8000/docs`.

### 2. Chạy Kiểm Thử (Unit Tests)

Bộ kiểm thử tuân thủ nguyên tắc không gọi dịch vụ bên ngoài (LLM, HF, PGVector được mock boundary):

```bash
make test
```

---

## 📡 Danh Sách API Chính (REST Endpoints)

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/chat` | Gửi tin nhắn và nhận câu trả lời AI theo phiên (hỗ trợ JWT Auth hoặc Anonymous) |
| `GET` | `/api/chat/sessions` | Lấy danh sách các phiên trò chuyện của user |
| `GET` | `/api/chat/sessions/{session_id}/messages` | Lấy toàn bộ lịch sử tin nhắn của một phiên |
| `DELETE` | `/api/chat/sessions/{session_id}` | Xóa một phiên trò chuyện |
| `POST` | `/api/rag/documents` | Nạp thủ công một tài liệu text vào Knowledge Index |
| `DELETE` | `/api/rag/documents` | Xóa toàn bộ tài liệu trong Knowledge Index |
| `GET` | `/api/health` | Kiểm tra trạng thái hoạt động của AI Service |

---

## 🛠️ Bảng Lệnh Makefile (CLI Reference)

| Lệnh | Chức năng |
| :--- | :--- |
| `make run` | Khởi chạy FastAPI server với Uvicorn (hot reload) |
| `make install` | Cài đặt các package cần thiết từ `requirements.txt` |
| `make migrate` | Áp dụng các migrations mới nhất vào PostgreSQL |
| `make test` | Chạy toàn bộ test suite với Pytest |
| `make graph` | Xuất sơ đồ luồng LangGraph ra file markdown/ảnh |
| `make ingest` | Nạp tài liệu hệ thống vào Vector Store |
| `make ingest-chess` | Nạp luật FIDE Laws PDF vào Vector Store |
| `make ingest-chess-clear` | Xóa và nạp lại toàn bộ luật FIDE |
| `make crawl-openings` | Crawl dữ liệu khai cuộc từ Wikibooks MediaWiki API |
| `make ingest-openings` | Nạp lý thuyết khai cuộc vào Vector Store |
| `make ingest-openings-clear`| Xóa và nạp lại lý thuyết khai cuộc |
| `make clean` | Dọn dẹp cache `__pycache__` và `.pytest_cache` |

---

## 📜 Giấy Phép (License)

Dự án phục vụ mục đích học tập và nghiên cứu trong khuôn khổ Đồ Án Tốt Nghiệp ngành Khoa Học Máy Tính (OUCS2302).
