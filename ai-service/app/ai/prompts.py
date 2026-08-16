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


# ---------------------------------------------------------------------------
# Chat-style prompt (RAG pipeline)
# rag_system.txt contains the full system block including the {context} placeholder.
# ---------------------------------------------------------------------------
RAG_PROMPT = ChatPromptTemplate.from_messages(
    [
        ("system", _load("rag_system")),
        MessagesPlaceholder(variable_name="history"),
        ("human", "{question}"),
    ]
)

# ---------------------------------------------------------------------------
# String prompts for router LLM calls (invoke with a plain string).
# Variables: {history}, {question} for classify/rewrite; {history} for summarize.
# ---------------------------------------------------------------------------
ANALYZE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("analyze"))
REWRITE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("rewrite"))
SUMMARIZE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("summarize"))

# ---------------------------------------------------------------------------
# System message strings for generate_direct and generate_chitchat nodes.
# ---------------------------------------------------------------------------
DIRECT_SYSTEM: str = _load("direct_system")
CHITCHAT_SYSTEM: str = _load("chitchat_system")

NO_CONTEXT_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("no_context"))
