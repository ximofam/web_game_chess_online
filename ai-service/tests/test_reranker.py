from unittest.mock import AsyncMock, MagicMock, Mock, patch

import pytest
from langchain_core.documents import Document

from app.rag.reranker import (
    BaseReranker,
    HuggingFaceReranker,
    JinaReranker,
    PassthroughReranker,
    get_reranker,
)
from app.rag.retriever import retrieve


# ── Test Base Interface & Initialization ─────────────────────────────────────

def test_huggingface_reranker_initialization():
    reranker = HuggingFaceReranker(
        model="BAAI/bge-reranker-v2-m3",
        api_key="hf_test_key",
        timeout_seconds=3.0,
    )
    assert reranker.model == "BAAI/bge-reranker-v2-m3"
    assert reranker.api_key == "hf_test_key"
    assert reranker.endpoint == "https://router.huggingface.co/hf-inference/models/BAAI/bge-reranker-v2-m3"
    headers = reranker._prepare_headers()
    assert headers["Authorization"] == "Bearer hf_test_key"


def test_reranker_handles_empty_and_single_doc():
    reranker = HuggingFaceReranker()
    assert reranker.rerank("query", []) == []

    doc = Document(page_content="single doc", metadata={"id": 1})
    result = reranker.rerank("query", [doc], top_n=4)
    assert len(result) == 1
    assert result[0].page_content == "single doc"


# ── Test Response Formats & Sorting ──────────────────────────────────────────

def test_reranker_parses_nested_pipeline_scores_list():
    reranker = HuggingFaceReranker()
    docs = [
        Document(page_content="doc 0 (low)", metadata={"id": 0}),
        Document(page_content="doc 1 (high)", metadata={"id": 1}),
        Document(page_content="doc 2 (medium)", metadata={"id": 2}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = [
        [
            {"label": "LABEL_0", "score": 0.15},
            {"label": "LABEL_0", "score": 0.95},
            {"label": "LABEL_0", "score": 0.60},
        ]
    ]

    with patch("httpx.Client.post", return_value=mock_resp):
        reranked = reranker.rerank("test query", docs, top_n=2)

    assert len(reranked) == 2
    assert reranked[0].page_content == "doc 1 (high)"
    assert reranked[0].metadata["rerank_score"] == 0.95
    assert reranked[1].page_content == "doc 2 (medium)"
    assert reranked[1].metadata["rerank_score"] == 0.60



def test_reranker_parses_dict_indexed_scores():
    reranker = HuggingFaceReranker()
    docs = [
        Document(page_content="doc A", metadata={"id": "A"}),
        Document(page_content="doc B", metadata={"id": "B"}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = [
        {"index": 0, "score": 0.32},
        {"index": 1, "score": 0.88},
    ]

    with patch("httpx.Client.post", return_value=mock_resp):
        reranked = reranker.rerank("test query", docs, top_n=2)

    assert len(reranked) == 2
    assert reranked[0].page_content == "doc B"
    assert reranked[0].metadata["rerank_score"] == 0.88


# ── Test Async arerank ───────────────────────────────────────────────────────

@pytest.mark.anyio
async def test_async_arerank():
    reranker = HuggingFaceReranker()
    docs = [
        Document(page_content="doc low", metadata={"id": 0}),
        Document(page_content="doc high", metadata={"id": 1}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = [0.2, 0.9]

    with patch("httpx.AsyncClient.post", new_callable=AsyncMock, return_value=mock_resp):
        reranked = await reranker.arerank("query", docs, top_n=2)

    assert len(reranked) == 2
    assert reranked[0].page_content == "doc high"
    assert reranked[0].metadata["rerank_score"] == 0.9


# ── Test Safe Fallback on Failure ────────────────────────────────────────────

def test_reranker_fallback_on_http_error():
    reranker = HuggingFaceReranker()
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 503
    mock_resp.text = "Model loading"

    with patch("httpx.Client.post", return_value=mock_resp):
        result = reranker.rerank("query", docs, top_n=1)

    assert len(result) == 1
    assert result[0].page_content == "doc 1"


def test_reranker_fallback_on_exception():
    reranker = HuggingFaceReranker()
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
    ]

    with patch("httpx.Client.post", side_effect=Exception("Connection refused")):
        result = reranker.rerank("query", docs, top_n=2)

    assert len(result) == 2
    assert result[0].page_content == "doc 1"


def test_reranker_filters_by_score_threshold_internally():
    reranker = HuggingFaceReranker()
    docs = [
        Document(page_content="doc 0 (low)", metadata={"id": 0}),
        Document(page_content="doc 1 (high)", metadata={"id": 1}),
        Document(page_content="doc 2 (medium)", metadata={"id": 2}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = [0.15, 0.95, 0.60]

    with patch("httpx.Client.post", return_value=mock_resp):
        reranked = reranker.rerank("test query", docs, top_n=3, score_threshold=0.50)

    assert len(reranked) == 2
    assert reranked[0].page_content == "doc 1 (high)"
    assert reranked[1].page_content == "doc 2 (medium)"


def test_passthrough_reranker():
    reranker = PassthroughReranker()
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
        Document(page_content="doc 3"),
    ]
    assert reranker.rerank("query", docs, top_n=2) == docs[:2]


def test_local_cross_encoder_reranker_rerank():
    from app.rag.reranker import LocalCrossEncoderReranker

    reranker = LocalCrossEncoderReranker(model="cross-encoder/ms-marco-MiniLM-L-6-v2", device="cpu")
    docs = [
        Document(page_content="irrelevant doc", metadata={"id": "0"}),
        Document(page_content="highly relevant chess rule", metadata={"id": "1"}),
    ]

    mock_model = Mock()
    mock_model.predict.return_value = [-3.0, 5.0]
    reranker._model = mock_model

    reranked = reranker.rerank("chess rule", docs, top_n=2, score_threshold=0.5)

    assert len(reranked) == 1
    assert reranked[0].page_content == "highly relevant chess rule"
    assert reranked[0].metadata["rerank_score"] == 0.9933


def test_local_cross_encoder_reranker_fallback_on_exception():
    from app.rag.reranker import LocalCrossEncoderReranker

    reranker = LocalCrossEncoderReranker(model="cross-encoder/ms-marco-MiniLM-L-6-v2", device="cpu")
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
    ]

    mock_model = Mock()
    mock_model.predict.side_effect = RuntimeError("Prediction failure")
    reranker._model = mock_model

    result = reranker.rerank("query", docs, top_n=2)
    assert len(result) == 2
    assert result[0].page_content == "doc 1"


def test_get_reranker_returns_local_when_provider_is_local():
    from app.rag.reranker import LocalCrossEncoderReranker

    settings = MagicMock(
        reranker_provider="huggingface_local",
        reranker_model="cross-encoder/ms-marco-MiniLM-L-6-v2",
        reranker_device="cpu",
    )
    with patch("app.rag.reranker.get_settings", return_value=settings):
        get_reranker.cache_clear()
        reranker = get_reranker()
        assert isinstance(reranker, LocalCrossEncoderReranker)
        get_reranker.cache_clear()


def test_get_reranker_returns_passthrough_when_provider_is_none():
    with patch("app.rag.reranker.get_settings", return_value=MagicMock(reranker_provider="none")):
        get_reranker.cache_clear()
        reranker = get_reranker()
        assert isinstance(reranker, PassthroughReranker)
        get_reranker.cache_clear()


# ── Test Retriever Integration ───────────────────────────────────────────────

def test_retrieve_calls_reranker_when_enabled():
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
    ]
    mock_store = MagicMock()
    mock_store.similarity_search_with_relevance_scores.return_value = [
        (docs[0], 0.8),
        (docs[1], 0.7),
    ]

    mock_reranker = MagicMock()
    mock_reranker.rerank.return_value = [
        Document(page_content="doc 2", metadata={"rerank_score": 0.85})
    ]

    with (
        patch("app.rag.retriever.get_vector_store", return_value=mock_store),
        patch("app.rag.retriever.get_reranker", return_value=mock_reranker),
    ):
        result = retrieve("query", top_k=1)

    assert len(result) == 1
    assert result[0].page_content == "doc 2"
    mock_reranker.rerank.assert_called_once()
    kwargs = mock_reranker.rerank.call_args.kwargs
    assert kwargs["top_n"] == 1
    assert "score_threshold" in kwargs


def test_retrieve_uses_passthrough_when_reranker_is_disabled():
    docs = [
        Document(page_content="good doc"),
        Document(page_content="bad doc"),
    ]
    mock_store = MagicMock()
    mock_store.similarity_search_with_relevance_scores.return_value = [
        (docs[0], 0.8),
        (docs[1], 0.05),  # below retrieval_score_threshold 0.1
    ]

    with (
        patch("app.rag.retriever.get_vector_store", return_value=mock_store),
        patch("app.rag.retriever.get_reranker", return_value=PassthroughReranker()),
    ):
        result = retrieve("query", top_k=2)

    assert len(result) == 1
    assert result[0].page_content == "good doc"


# ── Test Jina Reranker ────────────────────────────────────────────────────────

def test_jina_reranker_initialization():
    reranker = JinaReranker(
        model="jina-reranker-v3.5",
        api_key="jina_test_key",
        timeout_seconds=5.0,
    )
    assert reranker.model == "jina-reranker-v3.5"
    assert reranker.api_key == "jina_test_key"
    assert reranker.endpoint == "https://api.jina.ai/v1/rerank"
    headers = reranker._prepare_headers()
    assert headers["Authorization"] == "Bearer jina_test_key"
    assert headers["Accept"] == "application/json"
    assert headers["Content-Type"] == "application/json"


def test_jina_reranker_handles_empty_and_single_doc():
    reranker = JinaReranker(api_key="test")
    assert reranker.rerank("query", []) == []

    doc = Document(page_content="single doc", metadata={"id": 1})
    result = reranker.rerank("query", [doc], top_n=4)
    assert len(result) == 1
    assert result[0].page_content == "single doc"


def test_jina_reranker_parses_results_and_sorts():
    reranker = JinaReranker(api_key="test")
    docs = [
        Document(page_content="doc 0 (low)", metadata={"id": 0}),
        Document(page_content="doc 1 (high)", metadata={"id": 1}),
        Document(page_content="doc 2 (medium)", metadata={"id": 2}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {
        "model": "jina-reranker-v3.5",
        "results": [
            {"index": 0, "relevance_score": 0.20},
            {"index": 1, "relevance_score": 0.95},
            {"index": 2, "relevance_score": 0.65},
        ],
    }

    with patch("httpx.Client.post", return_value=mock_resp):
        reranked = reranker.rerank("test query", docs, top_n=2)

    assert len(reranked) == 2
    assert reranked[0].page_content == "doc 1 (high)"
    assert reranked[0].metadata["rerank_score"] == 0.95
    assert reranked[1].page_content == "doc 2 (medium)"
    assert reranked[1].metadata["rerank_score"] == 0.65


def test_jina_reranker_filters_by_score_threshold():
    reranker = JinaReranker(api_key="test")
    docs = [
        Document(page_content="doc 0", metadata={"id": 0}),
        Document(page_content="doc 1", metadata={"id": 1}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {
        "model": "jina-reranker-v3.5",
        "results": [
            {"index": 1, "relevance_score": 0.92},
            {"index": 0, "relevance_score": 0.03},
        ],
    }

    with patch("httpx.Client.post", return_value=mock_resp):
        reranked = reranker.rerank("query", docs, top_n=2, score_threshold=0.5)

    assert len(reranked) == 1
    assert reranked[0].page_content == "doc 1"
    assert reranked[0].metadata["rerank_score"] == 0.92


@pytest.mark.anyio
async def test_jina_reranker_async_arerank():
    reranker = JinaReranker(api_key="test")
    docs = [
        Document(page_content="doc low", metadata={"id": 0}),
        Document(page_content="doc high", metadata={"id": 1}),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {
        "model": "jina-reranker-v3.5",
        "results": [
            {"index": 0, "relevance_score": 0.15},
            {"index": 1, "relevance_score": 0.88},
        ],
    }

    with patch("httpx.AsyncClient.post", new_callable=AsyncMock, return_value=mock_resp):
        reranked = await reranker.arerank("query", docs, top_n=2)

    assert len(reranked) == 2
    assert reranked[0].page_content == "doc high"
    assert reranked[0].metadata["rerank_score"] == 0.88


def test_jina_reranker_fallback_on_http_error():
    reranker = JinaReranker(api_key="test")
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 500
    mock_resp.text = "Internal Server Error"

    with patch("httpx.Client.post", return_value=mock_resp):
        result = reranker.rerank("query", docs, top_n=1)

    assert len(result) == 1
    assert result[0].page_content == "doc 1"


def test_jina_reranker_fallback_on_exception():
    reranker = JinaReranker(api_key="test")
    docs = [
        Document(page_content="doc 1"),
        Document(page_content="doc 2"),
    ]

    with patch("httpx.Client.post", side_effect=Exception("Timeout")):
        result = reranker.rerank("query", docs, top_n=2)

    assert len(result) == 2
    assert result[0].page_content == "doc 1"


def test_get_reranker_returns_jina_when_provider_is_jina():
    settings = MagicMock(
        reranker_provider="jina",
        reranker_model="jina-reranker-v3.5",
        jina_api_key="jina_secret_key",
        reranker_timeout_seconds=8.0,
    )
    with patch("app.rag.reranker.get_settings", return_value=settings):
        get_reranker.cache_clear()
        reranker = get_reranker()
        assert isinstance(reranker, JinaReranker)
        assert reranker.model == "jina-reranker-v3.5"
        assert reranker.api_key == "jina_secret_key"
        assert reranker.timeout_seconds == 8.0
        get_reranker.cache_clear()