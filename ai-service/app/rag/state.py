from typing import Annotated, TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class RagState(TypedDict, total=False):
    original_question: str
    question_type: str | None
    # Primary message stream with add_messages reducer across checkpointed turns
    messages: Annotated[list[BaseMessage], add_messages]
    answer: str

