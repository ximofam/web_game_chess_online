from unittest.mock import Mock, patch

from app.core.config import Settings
from app.services.rag_service import add_document, clear_vector_store


def test_accepts_huggingface_api_configuration():
    settings = Settings(_env_file=None, vector_store="chroma", groq_api_key="groq", huggingface_api_key="hf")
    assert settings.vector_store == "chroma"
    assert settings.embedding_model == "sentence-transformers/all-MiniLM-L6-v2"


def test_allows_openai_providers():
    settings = Settings(llm_provider="openai", embedding_provider="openai", openai_api_key="openai")
    assert settings.llm_provider == "openai"


def test_uses_an_available_groq_router_model_by_default():
    assert Settings(_env_file=None).groq_router_model == "llama-3.3-70b-versatile"


def test_allows_configuring_cors_origins():
    settings = Settings(cors_origins=["http://localhost:5173", "https://app.example.com"])
    assert settings.cors_origins == ["http://localhost:5173", "https://app.example.com"]


def test_add_document_passes_content_and_metadata_to_vector_store():
    vector_store = Mock()
    vector_store.add_documents.return_value = ["document-id"]

    with patch("app.services.rag_service.get_vector_store", return_value=vector_store):
        document_id = add_document("A fact", {"source": "manual"})

    assert document_id == "document-id"
    document = vector_store.add_documents.call_args.args[0][0]
    assert document.page_content == "A fact"
    assert document.metadata == {"source": "manual"}


def test_clear_vector_store_chroma():
    with (
        patch("app.services.rag_service.get_settings") as mock_settings,
        patch("pathlib.Path.exists", return_value=True),
        patch("shutil.rmtree") as mock_rmtree,
    ):
        mock_settings.return_value.vector_store = "chroma"
        mock_settings.return_value.chroma_persist_directory = "data/chroma"
        clear_vector_store()
        mock_rmtree.assert_called_once()


def test_vision_model_settings_and_factory():
    from app.ai.llm import get_vision_llm

    # 1. Custom vision provider & model
    settings = Settings(
        llm_provider="groq",
        groq_api_key="g-key",
        vision_provider="openai",
        openai_api_key="o-key",
        openai_vision_model="gpt-4o",
    )
    assert settings.vision_provider == "openai"
    assert settings.openai_vision_model == "gpt-4o"

    # 2. get_vision_llm cached factory
    with patch("app.ai.llm.get_settings", return_value=settings):
        get_vision_llm.cache_clear()
        llm = get_vision_llm()
        assert llm.model_name == "gpt-4o"
        get_vision_llm.cache_clear()


def test_parse_router_output():
    from app.rag.nodes import _parse_router_output

    # JSON input
    assert _parse_router_output('{"question_type": "rag", "domain": "chess"}') == ("rag", "chess")
    assert _parse_router_output('{"question_type": "rag", "domain": "system"}') == ("rag", "system")
    assert _parse_router_output('{"question_type": "general", "domain": "all"}') == ("general", "all")

    # Markdown wrapped JSON
    markdown_json = '```json\n{"question_type": "rag", "domain": "chess"}\n```'
    assert _parse_router_output(markdown_json) == ("rag", "chess")

    # Fallback text
    assert _parse_router_output("rag") == ("rag", "all")
    assert _parse_router_output("general") == ("general", "all")
    assert _parse_router_output("rag chess rule") == ("rag", "chess")


def test_retrieve_domain_filtering():
    from langchain_core.documents import Document
    from app.core.config import get_settings
    from app.rag.retriever import retrieve

    expected_k = max(4, get_settings().reranker_candidates_k)
    mock_doc = Document(page_content="Chess rule", metadata={"domain": "chess"})
    mock_store = Mock()
    mock_store.similarity_search_with_relevance_scores.return_value = [(mock_doc, 0.9)]

    # 1. Domain specific filter
    with patch("app.rag.retriever.get_vector_store", return_value=mock_store):
        docs = retrieve("how to castle", domain="chess")
        assert len(docs) == 1
        mock_store.similarity_search_with_relevance_scores.assert_called_with(
            "how to castle", k=expected_k, filter={"domain": "chess"}
        )

    # 2. All domains (no filter)
    mock_store.reset_mock()
    with patch("app.rag.retriever.get_vector_store", return_value=mock_store):
        docs = retrieve("general query", domain="all")
        assert len(docs) == 1
        mock_store.similarity_search_with_relevance_scores.assert_called_with(
            "general query", k=expected_k
        )





