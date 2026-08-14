from typing import Annotated, Literal, TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class RagState(TypedDict):
    original_question: str
    rewritten_question: str
    question_type: Literal["system", "chess"]
    # add_messages reducer: appends on each invocation, preserved by checkpointer across turns
    chat_history: Annotated[list[BaseMessage], add_messages]
    documents: list[str]
    answer: str
