import logging

from langchain_core.messages import AIMessage, HumanMessage, RemoveMessage, SystemMessage, ToolMessage

from app.ai.llm import get_llm, get_router_llm
from app.ai.prompts import AGENT_SYSTEM, SUMMARIZE_PROMPT
from app.rag.state import RagState
from app.rag.tools import ALL_TOOLS

logger = logging.getLogger(__name__)

# Number of completed conversation turns (1 turn = 1 User + 1 AI) before triggering progressive summarization
CHAT_SUMMARY_TURNS_THRESHOLD = 2


def summarize_memory(state: RagState) -> dict:
    """Progressive summarization: Merges old summary with older clean Q&A turns."""
    messages = state.get("messages", [])

    # 1. Find existing summary message (if any)
    old_summary_msg = next(
        (m for m in messages if
         isinstance(m, SystemMessage) and str(m.content).startswith("Summary of previous conversation:")),
        None,
    )

    # 2. Extract dialogue messages (excluding system messages)
    dialogue_msgs = [m for m in messages if not isinstance(m, SystemMessage)]

    # 3. Find the beginning of the latest turn (the last HumanMessage in dialogue_msgs)
    last_human_idx = None
    for i in range(len(dialogue_msgs) - 1, -1, -1):
        if isinstance(dialogue_msgs[i], HumanMessage):
            last_human_idx = i
            break

    min_required_messages = max(2, CHAT_SUMMARY_TURNS_THRESHOLD * 2)

    # If completed past messages are below threshold, don't summarize yet
    if last_human_idx is None or last_human_idx < min_required_messages:
        return {}

    # Messages to summarize: everything before the latest turn (clean HumanMessage & AIMessage)
    messages_to_summarize = dialogue_msgs[:last_human_idx]
    new_lines = "\n".join(
        f"{m.type}: {m.content}"
        for m in messages_to_summarize if isinstance(m.content, str) and m.content
    )
    if not new_lines:
        return {}

    if old_summary_msg:
        history_text = f"Existing Summary:\n{old_summary_msg.content}\n\nNew conversation lines to incorporate:\n{new_lines}"
    else:
        history_text = new_lines

    summary_prompt = SUMMARIZE_PROMPT.format(history=history_text)
    new_summary = get_router_llm().invoke(summary_prompt).content

    delete_ids = [m.id for m in messages_to_summarize if getattr(m, "id", None)]
    if old_summary_msg and getattr(old_summary_msg, "id", None):
        delete_ids.append(old_summary_msg.id)

    delete_msgs = [RemoveMessage(id=mid) for mid in delete_ids]
    new_summary_msg = SystemMessage(content=f"Summary of previous conversation:\n{new_summary}")

    return {"messages": delete_msgs + [new_summary_msg]}


def agent_node(state: RagState) -> dict:
    """Agent node that decides whether to answer directly or emit tool calls."""
    messages = state.get("messages", [])
    question = state.get("original_question", "")

    # Fallback if messages list is empty but original_question is provided
    if not messages and question:
        messages = [HumanMessage(content=question)]

    # Separate system messages (summaries) from dialogue messages
    system_msgs = [m for m in messages if isinstance(m, SystemMessage)]
    dialogue_msgs = [m for m in messages if not isinstance(m, SystemMessage)]

    # Ensure Master System Prompt (AGENT_SYSTEM) is placed at the absolute top
    has_master = any("VieChess Master AI" in str(m.content) for m in system_msgs)
    master_prompt = [] if has_master else [SystemMessage(content=AGENT_SYSTEM)]
    prompt_messages = master_prompt + system_msgs + dialogue_msgs

    llm = get_llm().bind_tools(ALL_TOOLS)
    response = llm.invoke(prompt_messages)

    if not response.tool_calls:
        # Final answer completed: Check if tools were executed in the current turn
        has_used_tools = bool(messages and isinstance(messages[-1], ToolMessage))
        q_type = "rag" if has_used_tools else "general"
        answer_text = response.content if isinstance(response.content, str) else str(response.content)

        # Ephemeral Tool Cleanup: Purge intermediate ToolMessages and tool-calling AIMessages from checkpointer
        intermediate_tool_msgs = [
            m for m in messages
            if isinstance(m, ToolMessage) or (isinstance(m, AIMessage) and getattr(m, "tool_calls", None))
        ]
        cleanup_cmds = [RemoveMessage(id=m.id) for m in intermediate_tool_msgs if getattr(m, "id", None)]

        return {
            "messages": cleanup_cmds + [response],
            "answer": answer_text,
            "question_type": q_type,
        }

    # Tool calls emitted
    return {
        "messages": [response],
    }


def should_continue(state: RagState) -> str:
    """Check whether the agent wants to call a tool or finish."""
    messages = state.get("messages", [])
    if not messages:
        return "end"
    last_message = messages[-1]
    if getattr(last_message, "tool_calls", None):
        return "tools"
    return "end"
