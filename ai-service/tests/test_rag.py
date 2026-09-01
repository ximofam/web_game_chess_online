import json
from unittest.mock import MagicMock, Mock, patch

import pytest
from langchain_core.documents import Document

from app.core.config import Settings, get_settings
from app.rag.ingestion.cache import (
    get_cache_path,
    load_embeddings_from_csv,
    sanitize_model_slug,
    save_embeddings_to_csv,
)
from app.services.rag_service import (
    add_document,
    clear_domain_vectors,
    clear_vector_store,
    embed_documents_in_batches,
    ingest_domain,
    ingest_fide_pdf,
    ingest_markdown_directory,
    ingest_openings_jsonl,
)


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


def test_clear_domain_vectors():
    mock_engine = MagicMock()
    mock_conn = MagicMock()
    mock_res = MagicMock()
    mock_res.rowcount = 42
    mock_conn.execute.return_value = mock_res
    mock_engine.begin.return_value.__enter__.return_value = mock_conn

    settings = Settings(_env_file=None, database_url="postgresql+psycopg://user:pass@localhost:5432/db")
    with (
        patch("app.services.rag_service.get_settings", return_value=settings),
        patch("sqlalchemy.create_engine", return_value=mock_engine),
    ):
        count = clear_domain_vectors("system")
        assert count == 42
        mock_conn.execute.assert_called_once()
        args = mock_conn.execute.call_args
        assert args[0][1] == {"domain": "system"}


def test_get_vector_store_pgvector_factory():
    from app.ai.vectorstore import get_vector_store, get_engine

    settings = Settings(_env_file=None, database_url="postgresql+psycopg://user:pass@localhost:5432/db")
    mock_pgvector_class = Mock()
    mock_instance = Mock()
    mock_pgvector_class.return_value = mock_instance

    with (
        patch("app.ai.vectorstore.get_settings", return_value=settings),
        patch("app.ai.vectorstore.get_embeddings", return_value=Mock()),
        patch("langchain_postgres.PGVector", mock_pgvector_class),
    ):
        get_engine.cache_clear()
        get_vector_store.cache_clear()
        store = get_vector_store()
        assert store == mock_instance
        mock_pgvector_class.assert_called_once()
        assert mock_pgvector_class.call_args.kwargs["use_jsonb"] is True
        assert mock_pgvector_class.call_args.kwargs["create_extension"] is False
        get_engine.cache_clear()
        get_vector_store.cache_clear()


def test_get_engine_pgvector_listeners():
    from app.ai.vectorstore import get_engine

    settings = Settings(_env_file=None, database_url="postgresql+psycopg://user:pass@localhost:5432/db")
    with patch("app.ai.vectorstore.get_settings", return_value=settings):
        get_engine.cache_clear()
        engine = get_engine()
        assert len(engine.pool.dispatch.connect) > 0
        assert len(engine.pool.dispatch.checkout) > 0

        mock_dbapi_conn = MagicMock()
        mock_cursor = MagicMock()
        mock_dbapi_conn.cursor.return_value.__enter__.return_value = mock_cursor

        connect_called = False
        for fn in engine.pool.dispatch.connect:
            if fn.__name__ == "set_search_path_on_connect":
                fn(mock_dbapi_conn, None)
                mock_cursor.execute.assert_called_with("SET search_path TO ai_service, public;")
                connect_called = True
        assert connect_called

        mock_cursor.reset_mock()
        checkout_called = False
        for fn in engine.pool.dispatch.checkout:
            if fn.__name__ == "set_search_path_on_checkout":
                fn(mock_dbapi_conn, None, None)
                mock_cursor.execute.assert_called_with("SET search_path TO ai_service, public;")
                checkout_called = True
        assert checkout_called

        get_engine.cache_clear()






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
            encode_kwargs={"batch_size": 32, "normalize_embeddings": True},
        )
        get_embeddings.cache_clear()


def test_allows_openai_providers():
    settings = Settings(llm_provider="openai", embedding_provider="openai", openai_api_key="openai")
    assert settings.llm_provider == "openai"


def test_uses_an_available_groq_router_model_by_default():
    assert Settings(_env_file=None).groq_router_model == "llama-3.1-8b-instant"


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

    settings = Settings(
        llm_provider="groq",
        groq_api_key="g-key",
        vision_provider="openai",
        openai_api_key="o-key",
        openai_vision_model="gpt-4o",
    )
    assert settings.vision_provider == "openai"
    assert settings.openai_vision_model == "gpt-4o"

    with patch("app.ai.llm.get_settings", return_value=settings):
        get_vision_llm.cache_clear()
        llm = get_vision_llm()
        assert llm.model_name == "gpt-4o"
        get_vision_llm.cache_clear()


def test_retrieve_domain_filtering():
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


# ==============================================================================
# Embedding CSV Cache & Domain Ingestion Tests
# ==============================================================================

def test_cache_model_slug_and_path():
    assert sanitize_model_slug("BAAI/bge-m3") == "BAAI_bge-m3"
    assert sanitize_model_slug("sentence-transformers/all-MiniLM-L6-v2") == "sentence-transformers_all-MiniLM-L6-v2"

    path = get_cache_path("BAAI/bge-m3", "system", cache_dir="data/cache")
    assert str(path) == "data/cache/BAAI_bge-m3_system_ingest.csv"

    custom_path = get_cache_path("BAAI/bge-m3", "system", cache_dir="data/cache", custom_name="custom.csv")
    assert str(custom_path) == "data/cache/custom.csv"


def test_save_and_load_embeddings_csv(tmp_path):
    docs = [
        Document(page_content="Doc chunk 1", metadata={"domain": "system", "source": "rules.md"}),
        Document(page_content="Doc chunk 2", metadata={"domain": "system", "source": "faq.md"}),
    ]
    embeddings = [
        [0.1, 0.2, 0.3],
        [-0.4, 0.5, -0.6],
    ]
    cache_file = tmp_path / "test_cache.csv"
    saved_path = save_embeddings_to_csv(cache_file, docs, embeddings)
    assert saved_path.exists()

    texts, loaded_embs, metadatas = load_embeddings_from_csv(cache_file, expected_dim=3)
    assert texts == ["Doc chunk 1", "Doc chunk 2"]
    assert loaded_embs == embeddings
    assert metadatas == [
        {"domain": "system", "source": "rules.md"},
        {"domain": "system", "source": "faq.md"},
    ]


def test_load_embeddings_csv_dimension_validation(tmp_path):
    docs = [Document(page_content="Doc 1", metadata={"domain": "system"})]
    embeddings = [[0.1, 0.2, 0.3]]
    cache_file = tmp_path / "test_dim.csv"
    save_embeddings_to_csv(cache_file, docs, embeddings)

    with pytest.raises(ValueError, match="Vector dimension mismatch"):
        load_embeddings_from_csv(cache_file, expected_dim=768)


def test_ingest_domain_cache_miss_creates_csv_and_inserts(tmp_path):
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    (docs_dir / "test.md").write_text("# Title\n\nSome system content", encoding="utf-8")

    cache_dir = tmp_path / "cache"
    mock_store = Mock()
    mock_emb_client = Mock()
    mock_emb_client.embed_documents.return_value = [[0.11, 0.22, 0.33]]

    with (
        patch("app.services.rag_service.get_vector_store", return_value=mock_store),
        patch("app.services.rag_service.get_embeddings", return_value=mock_emb_client),
        patch("app.services.rag_service.clear_domain_vectors", return_value=0) as mock_clear,
    ):
        count = ingest_domain(
            domain="system",
            path=docs_dir,
            cache_dir=cache_dir,
            refresh_cache=False,
            clear_domain=True,
        )

        assert count >= 1
        mock_emb_client.embed_documents.assert_called_once()
        mock_clear.assert_called_once_with("system")
        mock_store.add_embeddings.assert_called_once()

    model_name = get_settings().embedding_model
    expected_csv = get_cache_path(model_name, "system", cache_dir=cache_dir)
    assert expected_csv.exists()


def test_ingest_domain_cache_hit_skips_embedding_model(tmp_path):
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    cache_dir = tmp_path / "cache"
    cache_dir.mkdir()

    model_name = get_settings().embedding_model
    cache_file = get_cache_path(model_name, "system", cache_dir=cache_dir)
    save_embeddings_to_csv(
        cache_file,
        [Document(page_content="Pre-cached content", metadata={"domain": "system", "source": "pre.md"})],
        [[0.99, 0.88, 0.77]],
    )

    mock_store = Mock()
    mock_emb_client = Mock()

    with (
        patch("app.services.rag_service.get_vector_store", return_value=mock_store),
        patch("app.services.rag_service.get_embeddings", return_value=mock_emb_client),
        patch("app.services.rag_service.clear_domain_vectors", return_value=0),
    ):
        count = ingest_domain(
            domain="system",
            path=docs_dir,
            cache_dir=cache_dir,
            refresh_cache=False,
            clear_domain=False,
        )

        assert count == 1
        mock_emb_client.embed_documents.assert_not_called()
        mock_store.add_embeddings.assert_called_once_with(
            texts=["Pre-cached content"],
            embeddings=[[0.99, 0.88, 0.77]],
            metadatas=[{"domain": "system", "source": "pre.md"}],
        )


def test_ingest_domain_refresh_cache_recomputes_and_overwrites(tmp_path):
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    (docs_dir / "updated.md").write_text("# Updated Title\n\nNew content", encoding="utf-8")
    cache_dir = tmp_path / "cache"
    cache_dir.mkdir()

    model_name = get_settings().embedding_model
    cache_file = get_cache_path(model_name, "system", cache_dir=cache_dir)
    save_embeddings_to_csv(
        cache_file,
        [Document(page_content="Old content", metadata={"domain": "system"})],
        [[0.01, 0.02, 0.03]],
    )

    mock_store = Mock()
    mock_emb_client = Mock()
    mock_emb_client.embed_documents.return_value = [[0.55, 0.66, 0.77]]

    with (
        patch("app.services.rag_service.get_vector_store", return_value=mock_store),
        patch("app.services.rag_service.get_embeddings", return_value=mock_emb_client),
        patch("app.services.rag_service.clear_domain_vectors", return_value=0),
    ):
        count = ingest_domain(
            domain="system",
            path=docs_dir,
            cache_dir=cache_dir,
            refresh_cache=True,
            clear_domain=False,
        )

        assert count >= 1
        mock_emb_client.embed_documents.assert_called_once()
        texts, embeddings, metadatas = load_embeddings_from_csv(cache_file)
        assert "[Updated Title]\nNew content" in texts[0] or "New content" in texts[0]
        assert embeddings[0] == [0.55, 0.66, 0.77]


def test_ingest_domain_no_cache_skips_reading_and_writing_csv(tmp_path):
    docs_dir = tmp_path / "docs"
    docs_dir.mkdir()
    (docs_dir / "item.md").write_text("# Direct Title\n\nDirect body", encoding="utf-8")
    cache_dir = tmp_path / "cache"
    cache_dir.mkdir()

    model_name = get_settings().embedding_model
    cache_file = get_cache_path(model_name, "system", cache_dir=cache_dir)

    mock_store = Mock()
    mock_emb_client = Mock()
    mock_emb_client.embed_documents.return_value = [[0.123, 0.456]]

    with (
        patch("app.services.rag_service.get_vector_store", return_value=mock_store),
        patch("app.services.rag_service.get_embeddings", return_value=mock_emb_client),
        patch("app.services.rag_service.clear_domain_vectors", return_value=0),
    ):
        count = ingest_domain(
            domain="system",
            path=docs_dir,
            cache_dir=cache_dir,
            refresh_cache=False,
            clear_domain=False,
            no_cache=True,
        )

        assert count >= 1
        mock_emb_client.embed_documents.assert_called_once()
        mock_store.add_embeddings.assert_called_once()
        # Ensure CSV cache file was NEVER created on disk
        assert not cache_file.exists()


def test_ingest_markdown_directory_wrapper():
    with patch("app.services.rag_service.ingest_domain", return_value=15) as mock_ingest:
        count = ingest_markdown_directory("./custom/path", clear=True, refresh_cache=True, cache_dir="/tmp/cache")
        assert count == 15
        mock_ingest.assert_called_once_with(
            domain="system",
            path="./custom/path",
            refresh_cache=True,
            clear_domain=False,
            cache_dir="/tmp/cache",
            custom_cache_name=None,
            no_cache=False,
        )


def test_ingest_fide_pdf_wrapper():
    with patch("app.services.rag_service.ingest_domain", return_value=30) as mock_ingest:
        count = ingest_fide_pdf("./fide.pdf", clear=False, refresh_vision=True, batch_size=25, cache_dir="/tmp/cache")
        assert count == 30
        mock_ingest.assert_called_once_with(
            domain="chess_law",
            path="./fide.pdf",
            refresh_cache=False,
            batch_size=25,
            clear_domain=True,
            refresh_vision=True,
            cache_dir="/tmp/cache",
            custom_cache_name=None,
            no_cache=False,
        )



def test_ingest_openings_jsonl(tmp_path):
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

    cache_dir = tmp_path / "cache"
    mock_store = Mock()
    mock_emb_client = Mock()
    mock_emb_client.embed_documents.return_value = [[0.1, 0.2, 0.3]]

    with (
        patch("app.services.rag_service.get_vector_store", return_value=mock_store),
        patch("app.services.rag_service.get_embeddings", return_value=mock_emb_client),
        patch("app.services.rag_service.clear_domain_vectors", return_value=0),
    ):
        count = ingest_openings_jsonl(sample_file, refresh_cache=True, cache_dir=cache_dir)
        assert count == 1
        mock_store.add_embeddings.assert_called_once()
        call_kwargs = mock_store.add_embeddings.call_args.kwargs
        assert len(call_kwargs["texts"]) == 1
        assert call_kwargs["metadatas"][0]["domain"] == "chess_opening"
        assert call_kwargs["metadatas"][0]["eco_code"] == "B20"
        assert "[Opening: Sicilian Defense | ECO Code: B20]" in call_kwargs["texts"][0]


def test_ingest_openings_jsonl_multi_chunk_breadcrumb_injection(tmp_path):
    sample_file = tmp_path / "long_openings.jsonl"
    long_body = "The Sicilian Defense leads to complex tactical struggles. " * 50

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

    cache_dir = tmp_path / "cache"
    mock_store = Mock()
    mock_emb_client = Mock()
    mock_emb_client.embed_documents.side_effect = lambda texts: [[0.1, 0.2, 0.3] for _ in texts]

    with (
        patch("app.services.rag_service.get_vector_store", return_value=mock_store),
        patch("app.services.rag_service.get_embeddings", return_value=mock_emb_client),
        patch("app.services.rag_service.clear_domain_vectors", return_value=0),
    ):
        count = ingest_openings_jsonl(sample_file, refresh_cache=True, cache_dir=cache_dir)
        assert count > 1
        all_texts = []
        all_metas = []
        for call in mock_store.add_embeddings.call_args_list:
            all_texts.extend(call.kwargs["texts"])
            all_metas.extend(call.kwargs["metadatas"])

        assert len(all_texts) == count
        for i, text in enumerate(all_texts):
            assert text.startswith("[Opening: Sicilian Defense | ECO Code: B20]"), f"Chunk {i} missing header!"
            assert all_metas[i]["domain"] == "chess_opening"


def test_embed_documents_in_batches_success():
    texts = [f"Text chunk {i}" for i in range(10)]
    mock_client = Mock()
    mock_client.embed_documents.side_effect = lambda batch: [[0.1 * len(batch)] * 3 for _ in batch]

    embeddings = embed_documents_in_batches(mock_client, texts, batch_size=3)
    assert len(embeddings) == 10
    # 10 texts / 3 = 4 batches (3, 3, 3, 1)
    assert mock_client.embed_documents.call_count == 4


def test_embed_documents_in_batches_retry_on_failure():
    texts = ["chunk 1", "chunk 2"]
    mock_client = Mock()
    # Fails on first call, succeeds on second call
    mock_client.embed_documents.side_effect = [
        RuntimeError("504 Gateway Timeout"),
        [[0.1, 0.2], [0.3, 0.4]],
    ]

    with patch("time.sleep") as mock_sleep:
        embeddings = embed_documents_in_batches(mock_client, texts, batch_size=2, max_retries=2, backoff_factor=1.0)
        assert len(embeddings) == 2
        assert mock_client.embed_documents.call_count == 2
        mock_sleep.assert_called_once_with(1.0)


def test_embed_documents_in_batches_raises_after_max_retries():
    texts = ["chunk 1", "chunk 2"]
    mock_client = Mock()
    mock_client.embed_documents.side_effect = RuntimeError("504 Gateway Timeout")

    with patch("time.sleep"):
        with pytest.raises(RuntimeError, match="Failed to embed batch"):
            embed_documents_in_batches(mock_client, texts, batch_size=2, max_retries=2, backoff_factor=1.0)


def test_workspace_cache_guard_prevents_unisolated_writes():
    """Verify that the test fixture prevents any test from accidentally writing into data/cache/."""
    from pathlib import Path
    from app.rag.ingestion.cache import save_embeddings_to_csv

    target_path = Path("data/cache/accidental_write.csv")
    with pytest.raises(RuntimeError, match="SAFETY VIOLATION"):
        save_embeddings_to_csv(target_path, [Document(page_content="leak")], [[0.1, 0.2]])


