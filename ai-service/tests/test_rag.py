from unittest.mock import MagicMock, Mock, patch

from langchain_core.documents import Document
from app.core.config import Settings

from app.services.rag_service import add_document, answer, format_context


def test_format_context_joins_retrieved_documents():
    documents = [Document(page_content="first"), Document(page_content="second")]
    assert format_context(documents) == "first\n\nsecond"


def test_accepts_huggingface_api_configuration():
    settings = Settings(vector_store="chroma", groq_api_key="groq", huggingface_api_key="hf")
    assert settings.vector_store == "chroma"
    assert settings.embedding_model == "sentence-transformers/all-MiniLM-L6-v2"


def test_allows_openai_providers():
    settings = Settings(llm_provider="openai", embedding_provider="openai", openai_api_key="openai")
    assert settings.llm_provider == "openai"


def test_add_document_passes_content_and_metadata_to_vector_store():
    vector_store = Mock()
    vector_store.add_documents.return_value = ["document-id"]

    with patch("app.services.rag_service.get_vector_store", return_value=vector_store):
        document_id = add_document("A fact", {"source": "manual"})

    assert document_id == "document-id"
    document = vector_store.add_documents.call_args.args[0][0]
    assert document.page_content == "A fact"
    assert document.metadata == {"source": "manual"}


def test_answer_returns_generated_text_and_retrieved_sources():
    documents = [Document(page_content="A fact", metadata={"source": "manual"})]
    chain = MagicMock()
    chain.__or__.return_value = chain
    chain.invoke.return_value = "Generated answer"

    with (
        patch("app.services.rag_service.retrieve", return_value=documents) as retrieve,
        patch("app.services.rag_service.RAG_PROMPT", chain),
        patch("app.services.rag_service.get_llm", return_value=Mock()),
    ):
        answer_text, sources = answer("What is the fact?", top_k=2)

    assert answer_text == "Generated answer"
    assert sources == [{"content": "A fact", "metadata": {"source": "manual"}}]
    retrieve.assert_called_once_with("What is the fact?", 2)
    assert chain.invoke.call_args.args[0] == {
        "context": "A fact",
        "question": "What is the fact?",
    }
