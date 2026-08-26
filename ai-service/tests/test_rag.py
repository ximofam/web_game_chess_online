from unittest.mock import MagicMock, Mock, patch

from app.core.config import Settings
from app.services.rag_service import add_document, clear_vector_store


def test_accepts_huggingface_api_configuration():
    settings = Settings(_env_file=None, groq_api_key="groq", huggingface_api_key="hf")
    assert settings.embedding_model == "sentence-transformers/all-MiniLM-L6-v2"


def test_clear_vector_store_pgvector():
    mock_engine = MagicMock()
    mock_conn = MagicMock()
    mock_engine.begin.return_value.__enter__.return_value = mock_conn

    settings = Settings(_env_file=None, database_url="postgresql+psycopg://user:pass@localhost:5432/db")
    with (
        patch("app.services.rag_service.get_settings", return_value=settings),
        patch("sqlalchemy.create_engine", return_value=mock_engine),
    ):
        clear_vector_store()
        assert mock_conn.execute.call_count == 2


def test_get_vector_store_pgvector_factory():
    from app.rag.retriever import get_vector_store

    settings = Settings(_env_file=None, database_url="postgresql+psycopg://user:pass@localhost:5432/db")
    mock_pgvector_class = Mock()
    mock_instance = Mock()
    mock_pgvector_class.return_value = mock_instance

    with (
        patch("app.rag.retriever.get_settings", return_value=settings),
        patch("app.rag.retriever.get_embeddings", return_value=Mock()),
        patch("langchain_postgres.PGVector", mock_pgvector_class),
    ):
        get_vector_store.cache_clear()
        store = get_vector_store()
        assert store == mock_instance
        mock_pgvector_class.assert_called_once()
        get_vector_store.cache_clear()


def test_accepts_huggingface_local_embedding_configuration():
    settings = Settings(
        _env_file=None,
        embedding_provider="huggingface_local",
        embedding_model="sentence-transformers/all-MiniLM-L6-v2",
        embedding_device="cpu",
    )
    assert settings.embedding_provider == "huggingface_local"
    assert settings.embedding_device == "cpu"


def test_get_embeddings_local_factory():
    from app.ai.embeddings import get_embeddings

    settings = Settings(
        _env_file=None,
        embedding_provider="huggingface_local",
        embedding_model="sentence-transformers/all-MiniLM-L6-v2",
        embedding_device="cpu",
    )
    mock_emb_class = Mock()
    mock_instance = Mock()
    mock_emb_class.return_value = mock_instance

    with (
        patch("app.ai.embeddings.get_settings", return_value=settings),
        patch("langchain_huggingface.HuggingFaceEmbeddings", mock_emb_class),
    ):
        get_embeddings.cache_clear()
        emb = get_embeddings()
        assert emb == mock_instance
        mock_emb_class.assert_called_once_with(
            model_name="sentence-transformers/all-MiniLM-L6-v2",
            model_kwargs={"device": "cpu"},
        )
        get_embeddings.cache_clear()


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
    assert _parse_router_output('{"question_type": "rag", "domain": "chess_law"}') == ("rag", "chess_law")
    assert _parse_router_output('{"question_type": "rag", "domain": "chess_opening"}') == ("rag", "chess_opening")
    assert _parse_router_output('{"question_type": "rag", "domain": "system"}') == ("rag", "system")
    assert _parse_router_output('{"question_type": "general", "domain": "all"}') == ("general", "all")

    # Markdown wrapped JSON
    markdown_json = '```json\n{"question_type": "rag", "domain": "chess_opening"}\n```'
    assert _parse_router_output(markdown_json) == ("rag", "chess_opening")

    # Fallback text
    assert _parse_router_output("rag") == ("rag", "all")
    assert _parse_router_output("general") == ("general", "all")
    assert _parse_router_output("rag chess opening theory") == ("rag", "chess_opening")
    assert _parse_router_output("rag chess rule fide") == ("rag", "chess_law")


def test_retrieve_domain_filtering():
    from langchain_core.documents import Document
    from app.core.config import get_settings
    from app.rag.retriever import retrieve

    expected_k = max(4, get_settings().reranker_candidates_k)
    mock_doc = Document(page_content="Chess rule", metadata={"domain": "chess_law"})
    mock_store = Mock()
    mock_store.similarity_search_with_relevance_scores.return_value = [(mock_doc, 0.9)]

    # 1. Domain specific filter: chess_law
    with patch("app.rag.retriever.get_vector_store", return_value=mock_store):
        docs = retrieve("how to castle", domain="chess_law")
        assert len(docs) == 1
        mock_store.similarity_search_with_relevance_scores.assert_called_with(
            "how to castle", k=expected_k, filter={"domain": "chess_law"}
        )

    # 2. Domain specific filter: chess_opening
    mock_store.reset_mock()
    with patch("app.rag.retriever.get_vector_store", return_value=mock_store):
        docs = retrieve("sicilian defense moves", domain="chess_opening")
        assert len(docs) == 1
        mock_store.similarity_search_with_relevance_scores.assert_called_with(
            "sicilian defense moves", k=expected_k, filter={"domain": "chess_opening"}
        )

    # 3. All domains (no filter)
    mock_store.reset_mock()
    with patch("app.rag.retriever.get_vector_store", return_value=mock_store):
        docs = retrieve("general query", domain="all")
        assert len(docs) == 1
        mock_store.similarity_search_with_relevance_scores.assert_called_with(
            "general query", k=expected_k
        )


def test_ingest_openings_jsonl(tmp_path):
    import json
    from app.services.rag_service import ingest_openings_jsonl

    sample_file = tmp_path / "test_openings.jsonl"
    record1 = {
        "title": "Chess Opening Theory/1. e4/1...c5",
        "url": "https://en.wikibooks.org/wiki/Chess_Opening_Theory/1._e4/1...c5",
        "header": "[Opening: Sicilian Defense | ECO Code: B20]\n[Path: Chess Opening Theory > 1. e4 > 1...c5 (Sicilian Defense)]\n[PGN Moves: 1. e4 c5]",
        "body": "1. e4 c5 is the Sicilian Defense.",
        "content": "[Opening: Sicilian Defense | ECO Code: B20]\n[Path: Chess Opening Theory > 1. e4 > 1...c5 (Sicilian Defense)]\n[PGN Moves: 1. e4 c5]\n\n1. e4 c5 is the Sicilian Defense.",
        "eco_code": "B20",
        "eco_name": "Sicilian Defense",
        "opening_family": "1. e4",
        "moves_pgn": "1. e4 c5",
        "depth": 2,
    }
    with open(sample_file, "w", encoding="utf-8") as f:
        f.write(json.dumps(record1) + "\n")

    mock_store = Mock()
    with patch("app.services.rag_service.get_vector_store", return_value=mock_store):
        count = ingest_openings_jsonl(sample_file)
        assert count == 1
        mock_store.add_documents.assert_called_once()
        docs = mock_store.add_documents.call_args[0][0]
        assert len(docs) == 1
        assert docs[0].metadata["domain"] == "chess_opening"
        assert docs[0].metadata["eco_code"] == "B20"
        assert "[Opening: Sicilian Defense | ECO Code: B20]" in docs[0].page_content


def test_ingest_openings_jsonl_multi_chunk_breadcrumb_injection(tmp_path):
    import json
    from app.services.rag_service import ingest_openings_jsonl

    sample_file = tmp_path / "long_openings.jsonl"
    long_body = "The Sicilian Defense leads to complex tactical struggles. " * 50  # ~3000 chars

    record = {
        "title": "Chess Opening Theory/1. e4/1...c5",
        "url": "https://en.wikibooks.org/wiki/Chess_Opening_Theory/1._e4/1...c5",
        "header": "[Opening: Sicilian Defense | ECO Code: B20]\n[Path: Chess Opening Theory > 1. e4 > 1...c5]\n[PGN Moves: 1. e4 c5]",
        "body": long_body,
        "content": f"[Opening: Sicilian Defense | ECO Code: B20]\n\n{long_body}",
        "eco_code": "B20",
        "eco_name": "Sicilian Defense",
        "opening_family": "1. e4",
        "moves_pgn": "1. e4 c5",
        "depth": 2,
    }
    with open(sample_file, "w", encoding="utf-8") as f:
        f.write(json.dumps(record) + "\n")

    mock_store = Mock()
    with patch("app.services.rag_service.get_vector_store", return_value=mock_store):
        count = ingest_openings_jsonl(sample_file)
        assert count > 1  # Successfully split into multiple chunks
        all_docs = []
        for call in mock_store.add_documents.call_args_list:
            all_docs.extend(call[0][0])

        assert len(all_docs) == count
        # Verify that EVERY chunk has the breadcrumb header injected
        for i, doc in enumerate(all_docs):
            assert doc.page_content.startswith("[Opening: Sicilian Defense | ECO Code: B20]"), f"Chunk {i} missing header!"
            assert doc.metadata["domain"] == "chess_opening"





