# AI Coaching Module — Technical Specification

**Status:** draft v1
**Phạm vi:** module AI Coaching cho chess web app, tách biệt khỏi core `chess-web-backend` (Spring Boot), triển khai bằng Python + LangChain.

---

## 1. Mục tiêu

5 tính năng cần đặc tả:

1. AI giải thích nước đi
2. AI trả lời câu hỏi
3. AI phân tích sau ván đấu
4. AI theo dõi quá trình học
5. AI tạo bài tập

Kết luận kiến trúc (đã phân tích trước): đây không phải bài toán RAG chatbot thuần. Chỉ nhánh Q&A kiến thức chung là retrieval-shaped. Các tính năng còn lại cần **engine-grounded generation** (Stockfish + LLM diễn giải) và **data pipeline** (tracking, aggregation). Kiến trúc dưới đây là hybrid: chess engine (ground truth) + structured DB (lịch sử/tiến độ) + vector store (kiến thức/puzzle) đều là tool mà lớp orchestration có thể gọi.

---

## 2. Kiến trúc tổng thể

### 2.1 Polyglot, hai service

| Service | Ngôn ngữ | Trách nhiệm |
|---|---|---|
| `chess-web-backend` | Java / Spring Boot | Game state, auth, WebSocket/STOMP, lobby, guest user — không đổi |
| `ai-coaching-service` | Python / FastAPI + LangChain | Toàn bộ 5 tính năng AI coaching — service mới |

**Vì sao tách service** thay vì nhúng LangChain vào JVM: hệ sinh thái tool-calling / agent / vector-store integration của LangChain là Python-native và trưởng thành nhất ở Python. Tách service giữ ranh giới rõ ràng, tránh AI/generation logic làm phình domain logic của game backend, và cho phép scale/deploy độc lập (AI service cần GPU/API quota riêng, không liên quan tới scaling của WebSocket game server).

### 2.2 Giao tiếp giữa hai service

- **Đồng bộ (REST, do `ai-coaching-service` expose qua FastAPI):** dùng cho tính năng 1 và 2 — nơi user đang chờ phản hồi trực tiếp.
- **Bất đồng bộ (RabbitMQ, dùng lại broker đã có sẵn):**
  - `chess-web-backend` publish event `game.finished` khi ván kết thúc → trigger tính năng 3.
  - `ai-coaching-service` publish event `analysis.completed` sau khi phân tích xong → `chess-web-backend` nhận và đẩy tiếp qua WebSocket/STOMP cho client.
- **Đọc dữ liệu ván đấu:** `ai-coaching-service` không có quyền ghi vào DB của game, chỉ gọi ngược một REST endpoint read-only trên `chess-web-backend` (`GET /games/{id}/moves`) khi cần lịch sử nước đi.

### 2.3 Data ownership

Dùng chung một Postgres instance (đã có pgvector), nhưng `ai-coaching-service` sở hữu schema riêng `ai_coaching`. `chess-web-backend` không ghi trực tiếp vào schema này.

---

## 3. Data model (schema `ai_coaching`)

```
move_analysis
  id, game_id, user_id, move_number, fen_before, move_san,
  eval_before, eval_after, eval_delta,
  classification (best|excellent|good|inaccuracy|mistake|blunder),
  best_move_san, pv (jsonb), motif_tags (text[]), created_at

progress_summary
  id, user_id, period_start, period_end,
  stats (jsonb: blunder_rate_by_phase, top_motifs_missed, time_pressure_blunder_pct...),
  narrative_report (text), generated_at

exercise
  id, source_type (user_blunder|puzzle_bank), source_move_analysis_id (nullable),
  fen, solution_san (jsonb array), theme_tags (text[]), difficulty,
  embedding (vector), created_at

knowledge_doc
  id, source, title, content, embedding (vector)

chat_session / chat_message   -- optional, chỉ cần nếu muốn multi-turn memory cho Q&A
```

---

## 4. Đặc tả kỹ thuật từng tính năng

### 4.1 AI giải thích nước đi

- **Input:** `game_id` + `move_number`, hoặc `fen` + `move` trực tiếp (vị trí không thuộc ván đã lưu).
- **Flow:**
  1. Gọi chess engine tool (`python-chess` → `chess.engine`, UCI tới Stockfish) lấy eval trước/sau và principal variation.
  2. Nếu vị trí trùng khai cuộc đã biết (tra theo ECO code / move sequence trong `knowledge_doc`) → retrieve đoạn lý thuyết liên quan làm context bổ sung.
  3. LLM chain (LCEL) nhận FEN + move + eval_delta + PV + (tuỳ chọn) context khai cuộc → sinh giải thích tự nhiên.
  4. Cache theo hash(FEN + move) — nhiều vị trí khai cuộc lặp lại giữa các user, không cần phân tích lại.
- **Output:** `{ explanation, classification, eval_delta, best_move }`

### 4.2 AI trả lời câu hỏi

Router phân loại câu hỏi trước: kiến thức chung vs. gắn với ván đấu cụ thể (dựa vào có `game_id` kèm request, hoặc một LLM call nhỏ để classify).

- **Nhánh kiến thức chung** — RAG chuẩn: `PGVector` similarity search trên `knowledge_doc` → generate kèm trích dẫn nguồn.
- **Nhánh gắn ván đấu** — LangGraph agent với tool set: `get_position_eval`, `get_game_history`, `search_knowledge_base`. Agent tự quyết định gọi tool nào, có thể gọi nhiều bước.

### 4.3 AI phân tích sau ván đấu

Trigger: event `game.finished` từ RabbitMQ.

1. Worker (Celery hoặc `aio-pika` consumer) gọi `GET /games/{id}/moves` lấy toàn bộ nước đi.
2. Chạy Stockfish tuần tự qua từng nước, ghi kết quả vào `move_analysis`.
3. Phân loại theo centipawn-loss threshold (tham khảo chuẩn Lichess: ~0–20cp best/excellent, 20–100 inaccuracy, 100–300 mistake, >300 blunder — cần tinh chỉnh theo giai đoạn ván).
4. Xác định critical moment (nước làm eval đổi chiều thắng/thua rõ rệt).
5. LLM chain tổng hợp toàn bộ `move_analysis` của ván thành báo cáo tường thuật.
6. Publish `analysis.completed`.

### 4.4 AI theo dõi quá trình học

Không cần LLM ở bước tính toán. Scheduled job (APScheduler hoặc Celery beat), chạy định kỳ:

1. Aggregate `move_analysis` theo user trong một khoảng thời gian (tỷ lệ blunder theo giai đoạn ván, motif hay bị bỏ lỡ, tương quan với thời gian còn lại trên đồng hồ).
2. Ghi số liệu vào `progress_summary.stats`.
3. Một LLM call cuối cùng biến `stats` (jsonb) thành `narrative_report` — đây là bước AI duy nhất trong tính năng này. Toàn bộ phần trước là SQL aggregation thuần, không có retrieval nào tham gia.

### 4.5 AI tạo bài tập

- **Nguồn 1 (ưu tiên, cá nhân hoá):** lấy các `move_analysis` có classification `mistake`/`blunder` của user, verify bằng engine rằng vị trí có lời giải thắng bắt buộc rõ ràng và duy nhất trước khi đưa vào `exercise`.
- **Nguồn 2 (bổ sung):** import một puzzle bank mở, tính embedding theo `theme_tags`.
- **Truy xuất:** similarity search trên `exercise.embedding` theo vector đại diện điểm yếu hiện tại (lấy từ `progress_summary.stats.top_motifs_missed`).
- **Ràng buộc quan trọng:** LLM chỉ sinh phần hint/lời dẫn, **không bao giờ** được sinh ra FEN hay lời giải — LLM hallucinate vị trí bất hợp lệ là rủi ro thật, mọi thế cờ đưa vào `exercise` phải đi qua engine verify trước.

---

## 5. Kiến trúc nội bộ `ai-coaching-service`

- **Stack:** FastAPI (REST), LangChain + LangGraph (agent), `langchain-postgres` (`PGVector`) cho vector store, `python-chess` cho board/engine logic.
- **Cấu trúc thư mục đề xuất:**
  ```
  app/tools/       chess_engine_tool.py, knowledge_search_tool.py, game_data_tool.py
  app/chains/       explain_move_chain.py, post_game_report_chain.py, progress_report_chain.py
  app/agents/       qa_agent.py   (LangGraph, cho câu hỏi tự do cần agent tự chọn tool)
  app/workers/      post_game_worker.py (RabbitMQ consumer), progress_scheduler.py
  app/models/       Pydantic schema cho request/response và structured output
  ```
- **Structured output:** dùng Pydantic model qua `with_structured_output` cho mọi nơi cần dữ liệu có cấu trúc (classification, các phần trong report) — tránh parse text tự do.
- Tính năng 4.1/4.2 dùng **chain/agent trực tiếp** (đồng bộ, cần phản hồi nhanh); 4.3/4.4 chạy trong **worker** (bất đồng bộ, không giới hạn thời gian chặt).

---

## 6. Non-functional requirements

- **Latency:** 4.1/4.2 cần phản hồi nhanh (mục tiêu <3–5s) vì user đang chờ trong lúc chơi — cache eval theo FEN, giới hạn Stockfish depth phù hợp cho mục đích giải thích (khác với 4.3 có thể chạy depth sâu hơn vì async).
- **Cost:** mỗi LLM call có phí — cân nhắc model rẻ hơn cho tác vụ đơn giản (classify câu hỏi, sinh hint ngắn), model mạnh hơn cho báo cáo tổng hợp.
- **Idempotency:** worker phân tích hậu ván phải idempotent — nếu event `game.finished` bị deliver lại, không phân tích trùng (check tồn tại trước khi insert).
- **Engine pooling:** Stockfish là external process — cần pool instance thay vì spawn mới mỗi request, có timeout và giới hạn concurrent analysis.

---

## 7. Rủi ro chính

| Rủi ro | Giảm thiểu |
|---|---|
| LLM hallucinate vị trí/nước đi bất hợp lệ khi sinh bài tập | Luôn verify bằng engine trước khi lưu vào `exercise`; LLM không bao giờ trực tiếp tạo ra FEN |
| Stockfish process quản lý kém → nghẽn | Pool engine instance, timeout, giới hạn concurrency |
| `chess-web-backend` down/chậm khi `ai-coaching-service` cần đọc game data | Retry có backoff, timeout hợp lý trên REST call ngược |

---

## 8. Đề xuất triển khai theo giai đoạn

1. **Phase 1:** 4.1 (explain move) + 4.3 (post-game analysis) — giá trị cao nhất, engine-grounded, không phụ thuộc RAG.
2. **Phase 2:** 4.2 nhánh RAG (Q&A kiến thức chung) — cần chuẩn bị `knowledge_doc` trước.
3. **Phase 3:** 4.4 (progress tracking) — cần đủ dữ liệu tích lũy từ phase 1.
4. **Phase 4:** 4.5 (exercise generation) — phụ thuộc dữ liệu blunder cá nhân từ phase 1 + puzzle bank.
