from functools import lru_cache
from pathlib import Path

from langchain_core.prompts import PromptTemplate

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
# Master system prompt for Agentic ReAct Assistant
# ---------------------------------------------------------------------------
AGENT_SYSTEM: str = _compose_system_prompt("agent_system")

# ---------------------------------------------------------------------------
# Auxiliary string prompts for memory summarization and auto-titling
# ---------------------------------------------------------------------------
SUMMARIZE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("summarize"))
TITLE_PROMPT: PromptTemplate = PromptTemplate.from_template(_load("title"))



