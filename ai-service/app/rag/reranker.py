import logging
from abc import ABC, abstractmethod
from functools import lru_cache
from typing import Any

import httpx
from langchain_core.documents import Document

from app.core.config import get_settings
from app.utils import sigmoid

logger = logging.getLogger(__name__)


class BaseReranker(ABC):
    """Abstract base class for document rerankers."""

    @abstractmethod
    def rerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        """Rerank documents synchronously against the query, filter by score_threshold, and return top_n."""
        pass

    @abstractmethod
    async def arerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        """Rerank documents asynchronously against the query, filter by score_threshold, and return top_n."""
        pass


class HuggingFaceReranker(BaseReranker):
    """Reranks candidate documents using Hugging Face Inference API."""

    def __init__(
            self,
            model: str = "BAAI/bge-reranker-v2-m3",
            api_key: str | None = None,
            timeout_seconds: float = 5.0,
    ):
        self.model = model
        self.api_key = api_key
        self.timeout_seconds = timeout_seconds
        self.endpoint = f"https://router.huggingface.co/hf-inference/models/{model}"

    def _prepare_payload(self, query: str, documents: list[Document]) -> dict:
        return {
            "inputs": [{"text": query, "text_pair": d.page_content} for d in documents]
        }

    def _prepare_headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        return headers

    def _parse_and_sort(
            self,
            raw_response: Any,
            documents: list[Document],
            top_n: int,
            score_threshold: float | None = None,
    ) -> list[Document]:
        """Parse various Hugging Face response formats and return top_n documents meeting score_threshold."""
        scores: list[tuple[int, float]] = []

        # Format 1: Nested list [[{"label": "...", "score": 0.92}, {"label": "...", "score": 0.05}]]
        if isinstance(raw_response, list) and raw_response and isinstance(raw_response[0], list):
            for i, inner in enumerate(raw_response[0]):
                if isinstance(inner, dict) and "score" in inner:
                    scores.append((i, float(inner["score"])))
                elif isinstance(inner, (int, float)):
                    scores.append((i, float(inner)))

        # Format 2: Flat list of dicts [{"index": 0, "score": 0.92}] or [{"label": "...", "score": 0.92}]
        elif isinstance(raw_response, list) and raw_response and isinstance(raw_response[0], dict):
            for i, item in enumerate(raw_response):
                idx = int(item.get("index", i))
                if "score" in item:
                    scores.append((idx, float(item["score"])))

        # Format 3: Flat list of float scores [0.92, 0.45, 0.78]
        elif isinstance(raw_response, list) and raw_response and isinstance(raw_response[0], (int, float)):
            for i, score in enumerate(raw_response):
                scores.append((i, float(score)))

        if not scores:
            logger.warning("Unrecognized reranker response format: %s", raw_response)
            return documents[:top_n]

        # Sort descending by score
        scores.sort(key=lambda x: x[1], reverse=True)

        # Filter by threshold if specified
        if score_threshold is not None:
            scores = [(idx, s) for idx, s in scores if s >= score_threshold]

        reranked: list[Document] = []
        for idx, score in scores[:top_n]:
            if idx < len(documents):
                doc = documents[idx]
                new_metadata = dict(doc.metadata)
                new_metadata["rerank_score"] = round(score, 4)
                reranked.append(Document(page_content=doc.page_content, metadata=new_metadata))

        return reranked

    def rerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        if not documents:
            return []
        if len(documents) <= 1:
            return documents[:top_n]

        payload = self._prepare_payload(query, documents)
        headers = self._prepare_headers()

        try:
            with httpx.Client(timeout=self.timeout_seconds) as client:
                resp = client.post(self.endpoint, json=payload, headers=headers)
                if resp.status_code != 200:
                    logger.warning(
                        "HuggingFace reranker returned HTTP %d: %s. Falling back to vector order.",
                        resp.status_code,
                        resp.text[:200],
                    )
                    return documents[:top_n]
                print(resp.json())
                return self._parse_and_sort(resp.json(), documents, top_n, score_threshold)
        except Exception as e:
            logger.warning("HuggingFace reranker failed (%s). Falling back to vector order.", e, exc_info=True)
            return documents[:top_n]

    async def arerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        if not documents:
            return []
        if len(documents) <= 1:
            return documents[:top_n]

        payload = self._prepare_payload(query, documents)
        headers = self._prepare_headers()

        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                resp = await client.post(self.endpoint, json=payload, headers=headers)
                if resp.status_code != 200:
                    logger.warning(
                        "HuggingFace reranker returned HTTP %d: %s. Falling back to vector order.",
                        resp.status_code,
                        resp.text[:200],
                    )
                    return documents[:top_n]
                return self._parse_and_sort(resp.json(), documents, top_n, score_threshold)
        except Exception as e:
            logger.warning("HuggingFace reranker failed (%s). Falling back to vector order.", e, exc_info=True)
            return documents[:top_n]


class LocalCrossEncoderReranker(BaseReranker):
    """Reranks candidate documents locally in-process using sentence-transformers CrossEncoder."""

    def __init__(
            self,
            model: str = "cross-encoder/ms-marco-MiniLM-L-6-v2",
            device: str = "cpu",
    ):
        self.model_name = model
        self.device = device
        self._model = None

    @property
    def model(self):
        if self._model is None:
            from sentence_transformers import CrossEncoder

            logger.info("Loading local CrossEncoder reranker model: %s on %s", self.model_name, self.device)
            self._model = CrossEncoder(self.model_name, device=self.device)
        return self._model

    def rerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        if not documents:
            return []
        if len(documents) <= 1:
            return documents[:top_n]

        pairs = [[query, doc.page_content] for doc in documents]
        try:
            raw_scores = self.model.predict(pairs)
            if hasattr(raw_scores, "tolist"):
                scores = raw_scores.tolist()
            elif isinstance(raw_scores, (int, float)):
                scores = [float(raw_scores)]
            else:
                scores = [float(s) for s in raw_scores]
            # Normalize logits into [0.0, 1.0] probabilities via sigmoid
            scores = [sigmoid(s) for s in scores]
        except Exception as e:
            logger.warning("Local CrossEncoder reranker failed (%s). Falling back to vector order.", e, exc_info=True)
            return documents[:top_n]

        indexed_scores = list(enumerate(scores))
        indexed_scores.sort(key=lambda x: x[1], reverse=True)

        if score_threshold is not None:
            indexed_scores = [(idx, s) for idx, s in indexed_scores if s >= score_threshold]

        reranked: list[Document] = []
        for idx, score in indexed_scores[:top_n]:
            if idx < len(documents):
                doc = documents[idx]
                new_metadata = dict(doc.metadata)
                new_metadata["rerank_score"] = round(float(score), 4)
                reranked.append(Document(page_content=doc.page_content, metadata=new_metadata))

        return reranked

    async def arerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        import asyncio

        return await asyncio.to_thread(self.rerank, query, documents, top_n, score_threshold)


class PassthroughReranker(BaseReranker):
    """No-op fallback reranker that preserves vector search order."""

    def rerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        return documents[:top_n]

    async def arerank(
            self,
            query: str,
            documents: list[Document],
            top_n: int = 4,
            score_threshold: float | None = None,
    ) -> list[Document]:
        return documents[:top_n]


@lru_cache
def get_reranker() -> BaseReranker:
    """Factory function to obtain the configured reranker instance (guaranteed non-None)."""
    settings = get_settings()
    if settings.reranker_provider in ("huggingface_local", "local"):
        return LocalCrossEncoderReranker(
            model=settings.reranker_model,
            device=settings.reranker_device,
        )
    if settings.reranker_provider == "huggingface":
        return HuggingFaceReranker(
            model=settings.reranker_model,
            api_key=settings.huggingface_api_key,
            timeout_seconds=settings.reranker_timeout_seconds,
        )
    return PassthroughReranker()
