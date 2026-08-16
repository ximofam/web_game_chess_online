# Chat API Specification

## 1. Overview
The Chat API manages interactive AI sessions utilizing a LangGraph-powered RAG pipeline. It handles session creation, conversation history retrieval, and asynchronous conversational title generation.

## 2. Endpoints

All endpoints are protected and require a valid user authentication token. The routes are mapped under the prefix `/chat`.

### 2.1. Create Chat Session
Creates a new isolated chat session for the current user.

- **URL:** `POST /chat/sessions`
- **Request Body:** None
- **Response:** `200 OK`
  ```json
  {
    "session_id": "uuid-string"
  }
  ```

### 2.2. List Chat Sessions
Retrieves a paginated list of the user's chat sessions, sorted by creation date (newest first).

- **URL:** `GET /chat/sessions`
- **Query Parameters:**
  - `page` (int, default: 1): Page number (1-indexed).
  - `size` (int, default: 20): Number of items per page.
- **Response:** `200 OK`
  ```json
  {
    "items": [
      {
        "id": "uuid-string",
        "title": "Short Summary Title",
        "created_at": "2023-10-01T12:00:00Z"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20
  }
  ```

### 2.3. Send Chat Message
Submits a user message to an existing session and triggers the LangGraph agent to generate an answer. The response is synchronous (REST).

- **URL:** `POST /chat/{session_id}`
- **Path Parameter:** `session_id` (UUID)
- **Request Body:**
  ```json
  {
    "question": "What is the en passant rule?"
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "answer": "En passant is a special pawn capture...",
    "question_type": "chess"
  }
  ```

*Note: If this is the first message in the session, a background task (`generate_session_title_task`) is queued to automatically generate and assign a 6-word summary title to the session without blocking the user response.*

### 2.4. Get Session Messages
Retrieves the chronological history of messages in a specific session.

- **URL:** `GET /chat/{session_id}/messages`
- **Path Parameter:** `session_id` (UUID)
- **Response:** `200 OK`
  ```json
  {
    "items": [
      {
        "id": "uuid-string",
        "role": "user",
        "content": "What is the en passant rule?",
        "question_type": null,
        "created_at": "2023-10-01T12:05:00Z"
      },
      {
        "id": "uuid-string",
        "role": "assistant",
        "content": "En passant is a special pawn capture...",
        "question_type": "chess",
        "created_at": "2023-10-01T12:05:05Z"
      }
    ]
  }
  ```

## 3. Architecture details

### 3.1. Ownership and Security
All endpoints scoped to a `session_id` enforce ownership verification via a shared FastAPI dependency (`get_owned_session`). If a user attempts to access a session they do not own, a `403 Forbidden` is returned. A `404 Not Found` is returned if the session does not exist.

### 3.2. Title Generation Strategy (Background Tasks)
To keep the chat endpoint ultra-fast and avoid exposing the LLM latency of title generation to the user:
- The system employs an **Auto-titling Strategy** after the very first turn.
- The `generate_session_title_task` spins up an isolated background database transaction.
- It prompts a lightweight router model to summarize the user's first question and the AI's answer into a short <6-word string.

### 3.3. Routing and Categorization
Messages are dynamically categorized via the `analyze_question` graph node into one of three buckets: `system`, `chess`, or `chitchat`. This determines the downstream path the agent takes (e.g., retrieving internal platform docs vs generating general chess knowledge directly). To ensure reliability across diverse LLM providers, this node outputs raw text instead of utilizing brittle `Structured Output` schemas.
