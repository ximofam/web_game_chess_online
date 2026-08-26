from functools import lru_cache
from pathlib import Path

from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder, PromptTemplate

_TEMPLATES_DIR = Path(__file__).parent / "prompt_templates"


@lru_cache
def _load(name: str) -> str:
    """Read and cache a prompt template file. Restart required to pick up edits."""
    path = _TEMPLATES_DIR / f"{name}.txt"
    if not path.exists():
        raise FileNotFoundError(f"Prompt template not found: {path}")
    return path.read_text(encoding="utf-8").strip()


@lru_cache
def _compose_system_prompt(task_name: str) -> str:
    """Compose the base system prompt (persona, global invariants, language hierarchy)
    with a task-specific prompt template.
    """
    base = _load("base_system")
    task = _load(task_name)
    return f"{base}\n\n{task}"


# ---------------------------------------------------------------------------
# Chat-style prompts (RAG pipeline)
# Composes base_system with domain-specific task prompts
# ---------------------------------------------------------------------------
RAG_SYSTEM_PROMPT = ChatPromptTemplate.from_messages(
    [
        ("system", _compose_system_prompt("rag_system")),
        MessagesPlaceholder(variable_name="history"),
        ("human", "{question}"),
    ]
)

RAG_CHESS_PROMPT = ChatPromptTemplate.from_messages(
    [
        ("system", _compose_system_prompt("rag_chess")),
        MessagesPlaceholder(variable_name="history"),
        ("human", "{question}"),
    ]
)

RAG_CHESS_LAW_PROMPT = RAG_CHESS_PROMPT

RAG_OPENING_PROMPT = ChatPromptTemplate.from_messages(
    [
        ("system", _compose_system_prompt("rag_opening")),
        MessagesPlaceholder(variable_name="history"),
        ("human", "{question}"),
    ]
)

# Default alias for backwards compatibility
RAG_PROMPT = RAG_SYSTEM_PROMPT

# ---------------------------------------------------------------------------
# String prompts for router LLM calls (invoke with a plain string).
# Variables: {history}, {question} for classify/rewrite; {history} for summarize.
# ---------------------------------------------------------------------------
ANALYZE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("analyze"))
REWRITE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("rewrite"))
SUMMARIZE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("summarize"))
TITLE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("title"))

# ---------------------------------------------------------------------------
# System message string for generate_general node.
# ---------------------------------------------------------------------------
GENERAL_SYSTEM: str = _compose_system_prompt("general_system")

NO_CONTEXT_PROMPT: PromptTemplate = PromptTemplate.from_template(
    _compose_system_prompt("no_context")
)

