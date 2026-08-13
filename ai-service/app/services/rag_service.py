from langchain_core.documents import Document
from langchain_core.output_parsers import StrOutputParser

from app.ai.llm import get_llm
from app.ai.prompts import RAG_PROMPT
from app.ai.retriever import get_vector_store, retrieve


def format_context(documents: list[Document]) -> str:
    return "\n\n".join(document.page_content for document in documents)


def add_document(content: str, metadata: dict[str, str]) -> str:
    return get_vector_store().add_documents([Document(page_content=content, metadata=metadata)])[0]


def answer(question: str, top_k: int = 4) -> tuple[str, list[dict[str, object]]]:
    documents = retrieve(question, top_k)
    response = (RAG_PROMPT | get_llm() | StrOutputParser()).invoke(
        {"context": format_context(documents), "question": question}
    )
    return response, [{"content": document.page_content, "metadata": document.metadata} for document in documents]
