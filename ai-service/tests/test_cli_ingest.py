import os
from unittest.mock import patch, Mock
import pytest

from scripts.ingest import main as ingest_main
from scripts.ingest_chess import ingest_chess
from scripts.ingest_openings import ingest_openings
from app.core.config import get_settings
from app.ai.embeddings import get_embeddings
from app.ai.vectorstore import get_vector_store, get_engine


@pytest.fixture(autouse=True)
def clean_env():
    old_env = dict(os.environ)
    yield
    os.environ.clear()
    os.environ.update(old_env)
    get_settings.cache_clear()
    get_embeddings.cache_clear()
    get_engine.cache_clear()
    get_vector_store.cache_clear()



def test_cli_ingest_domain_system_with_model_override():
    with (
        patch("sys.argv",
              ["scripts.ingest", "--domain", "system", "--refresh-cache", "--embedding-model", "BAAI/bge-m3"]),
        patch("scripts.ingest.ingest_domain", return_value=10) as mock_ingest,
        patch("scripts.ingest.clear_vector_store") as mock_clear_all,
    ):
        ingest_main()
        mock_clear_all.assert_not_called()
        assert os.environ.get("EMBEDDING_MODEL") == "BAAI/bge-m3"
        assert get_settings().embedding_model == "BAAI/bge-m3"
        mock_ingest.assert_called_once_with(
            domain="system",
            path=None,
            refresh_cache=True,
            batch_size=100,
            clear_domain=True,
            refresh_vision=False,
            cache_dir="data/cache",
            custom_cache_name=None,
            no_cache=False,
        )


def test_cli_ingest_all_domains_with_clear_all():
    with (
        patch("sys.argv", ["scripts.ingest", "--domain", "all", "--clear-all", "--batch-size", "50"]),
        patch("scripts.ingest.ingest_domain", return_value=5) as mock_ingest,
        patch("scripts.ingest.clear_vector_store") as mock_clear_all,
    ):
        ingest_main()
        mock_clear_all.assert_called_once()
        assert mock_ingest.call_count == 3
        domains_called = [call.kwargs["domain"] for call in mock_ingest.call_args_list]
        assert domains_called == ["system", "chess_law", "chess_opening"]
        for call in mock_ingest.call_args_list:
            assert call.kwargs["clear_domain"] is False  # Because clear_all already truncated
            assert call.kwargs["no_cache"] is False


def test_cli_ingest_all_domains_with_no_cache():
    with (
        patch("sys.argv", ["scripts.ingest", "--domain", "all", "--no-cache"]),
        patch("scripts.ingest.ingest_domain", return_value=5) as mock_ingest,
        patch("scripts.ingest.clear_vector_store") as mock_clear_all,
    ):
        ingest_main()
        mock_clear_all.assert_not_called()
        assert mock_ingest.call_count == 3
        for call in mock_ingest.call_args_list:
            assert call.kwargs["no_cache"] is True


def test_scripts_ingest_chess_delegation():
    with patch("scripts.ingest_chess.ingest_domain", return_value=25) as mock_ingest:
        ingest_chess("./custom_fide.pdf", clear=True, refresh_vision=True, refresh_cache=True,
                     embedding_model="custom/model", cache_dir="data/cache", no_cache=True)
        assert os.environ.get("EMBEDDING_MODEL") == "custom/model"
        assert get_settings().embedding_model == "custom/model"
        mock_ingest.assert_called_once_with(
            domain="chess_law",
            path="./custom_fide.pdf",
            refresh_cache=True,
            clear_domain=False,
            refresh_vision=True,
            cache_dir="data/cache",
            no_cache=True,
        )


def test_scripts_ingest_openings_delegation():
    with patch("scripts.ingest_openings.ingest_domain", return_value=50) as mock_ingest:
        ingest_openings("./custom_openings.jsonl", clear=False, batch_size=200, refresh_cache=False,
                         embedding_model="custom/model2", cache_dir="data/cache", no_cache=True)
        assert os.environ.get("EMBEDDING_MODEL") == "custom/model2"
        assert get_settings().embedding_model == "custom/model2"
        mock_ingest.assert_called_once_with(
            domain="chess_opening",
            path="./custom_openings.jsonl",
            refresh_cache=False,
            batch_size=200,
            clear_domain=True,
            cache_dir="data/cache",
            no_cache=True,
        )

