from langchain_core.prompts import ChatPromptTemplate

RAG_PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "Answer only from the supplied context. If it lacks the answer, say you do not know.\n\nContext:\n{context}",
        ),
        ("human", "{question}"),
    ]
)
