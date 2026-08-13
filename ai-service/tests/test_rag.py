from unittest import TestCase

from langchain_core.documents import Document
from app.core.config import Settings

from app.services.rag_service import format_context


class FormatContextTests(TestCase):
    def test_joins_retrieved_documents(self):
        documents = [Document(page_content="first"), Document(page_content="second")]
        self.assertEqual(format_context(documents), "first\n\nsecond")

    def test_accepts_huggingface_api_configuration(self):
        settings = Settings(groq_api_key="groq", huggingface_api_key="hf")
        self.assertEqual(settings.vector_store, "chroma")
        self.assertEqual(settings.embedding_model, "sentence-transformers/all-MiniLM-L6-v2")

    def test_allows_openai_providers(self):
        settings = Settings(llm_provider="openai", embedding_provider="openai", openai_api_key="openai")
        self.assertEqual(settings.llm_provider, "openai")
