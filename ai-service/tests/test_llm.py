import pytest
from unittest.mock import Mock, patch

from app.core.config import Settings
from app.ai.llm import (
    LLMFactory,
    get_llm,
    get_router_llm,
    get_vision_llm,
)


def test_llm_factory_create_ollama():
    settings = Settings(
        _env_file=None,
        llm_provider="ollama",
        ollama_base_url="http://localhost:11434",
        ollama_model="llama3.2:3b",
    )
    mock_ollama_class = Mock()
    mock_instance = Mock()
    mock_ollama_class.return_value = mock_instance

    with patch("langchain_ollama.ChatOllama", mock_ollama_class):
        model = LLMFactory.create("ollama", "llama3.2:3b", temperature=0.7, settings=settings)
        assert model == mock_instance
        mock_ollama_class.assert_called_once_with(
            model="llama3.2:3b",
            base_url="http://localhost:11434",
            temperature=0.7,
        )


def test_llm_factory_create_groq():
    settings = Settings(
        _env_file=None,
        llm_provider="groq",
        groq_api_key="gsk-test-key",
        groq_model="llama-3.3-70b-versatile",
    )
    mock_groq_class = Mock()
    mock_instance = Mock()
    mock_groq_class.return_value = mock_instance

    with patch("langchain_groq.ChatGroq", mock_groq_class):
        model = LLMFactory.create("groq", "llama-3.3-70b-versatile", temperature=0.5, settings=settings)
        assert model == mock_instance
        mock_groq_class.assert_called_once_with(
            model="llama-3.3-70b-versatile",
            api_key="gsk-test-key",
            temperature=0.5,
            streaming=False,
        )


def test_llm_factory_create_openai():
    settings = Settings(
        _env_file=None,
        llm_provider="openai",
        openai_api_key="sk-test-key",
        openai_model="gpt-4o-mini",
    )
    mock_openai_class = Mock()
    mock_instance = Mock()
    mock_openai_class.return_value = mock_instance

    with patch("langchain_openai.ChatOpenAI", mock_openai_class):
        model = LLMFactory.create("openai", "gpt-4o-mini", temperature=0.0, settings=settings)
        assert model == mock_instance
        mock_openai_class.assert_called_once_with(
            model="gpt-4o-mini",
            api_key="sk-test-key",
            temperature=0.0,
            streaming=False,
        )


def test_llm_factory_create_unsupported():
    with pytest.raises(ValueError, match="Unsupported LLM provider"):
        LLMFactory.create("invalid_provider", "some-model")


def test_llm_factory_missing_api_keys():
    settings_no_groq = Settings(_env_file=None, groq_api_key=None)
    with pytest.raises(ValueError, match="GROQ_API_KEY is required"):
        LLMFactory.create("groq", "llama-3.3-70b-versatile", settings=settings_no_groq)

    settings_no_openai = Settings(_env_file=None, openai_api_key=None)
    with pytest.raises(ValueError, match="OPENAI_API_KEY is required"):
        LLMFactory.create("openai", "gpt-4o-mini", settings=settings_no_openai)


def test_llm_factory_granular_providers():
    """Verify that Chat, Router, and Vision can independently use different providers (e.g. Groq + Ollama)."""
    settings = Settings(
        _env_file=None,
        # Chat uses Groq
        llm_provider="groq",
        groq_api_key="gsk-test",
        groq_model="llama-3.3-70b-versatile",
        # Router uses Ollama
        router_provider="ollama",
        ollama_base_url="http://localhost:11434",
        ollama_router_model="llama3.2:1b",
        # Vision uses Ollama
        vision_provider="ollama",
        ollama_vision_model="llama3.2-vision:latest",
    )

    mock_groq = Mock(name="ChatGroq")
    mock_ollama = Mock(name="ChatOllama")

    with (
        patch("app.ai.llm.get_settings", return_value=settings),
        patch("langchain_groq.ChatGroq", return_value=mock_groq) as groq_cls,
        patch("langchain_ollama.ChatOllama", return_value=mock_ollama) as ollama_cls,
    ):
        get_llm.cache_clear()
        get_router_llm.cache_clear()
        get_vision_llm.cache_clear()

        chat_model = get_llm()
        assert chat_model == mock_groq
        groq_cls.assert_called_once_with(
            model="llama-3.3-70b-versatile",
            api_key="gsk-test",
            temperature=0.7,
            streaming=False,
        )

        router_model = get_router_llm()
        assert router_model == mock_ollama
        ollama_cls.assert_any_call(
            model="llama3.2:1b",
            base_url="http://localhost:11434",
            temperature=0.0,
        )

        vision_model = get_vision_llm()
        assert vision_model == mock_ollama
        ollama_cls.assert_any_call(
            model="llama3.2-vision:latest",
            base_url="http://localhost:11434",
            temperature=0.0,
        )

        get_llm.cache_clear()
        get_router_llm.cache_clear()
        get_vision_llm.cache_clear()


def test_llm_factory_cached_helpers():
    """Verify get_llm, get_router_llm, get_vision_llm backward compatibility and caching."""
    settings = Settings(
        _env_file=None,
        llm_provider="ollama",
        ollama_base_url="http://localhost:11434",
        ollama_model="llama3.2:latest",
        ollama_router_model="llama3.2:1b",
        ollama_vision_model="llama3.2-vision:latest",
    )
    mock_instance = Mock()
    with (
        patch("app.ai.llm.get_settings", return_value=settings),
        patch("langchain_ollama.ChatOllama", return_value=mock_instance),
    ):
        get_llm.cache_clear()
        get_router_llm.cache_clear()
        get_vision_llm.cache_clear()

        m1 = get_llm()
        m2 = get_llm()
        assert m1 is m2  # cached

        r1 = get_router_llm()
        r2 = get_router_llm()
        assert r1 is r2  # cached

        v1 = get_vision_llm()
        v2 = get_vision_llm()
        assert v1 is v2  # cached

        get_llm.cache_clear()
        get_router_llm.cache_clear()
        get_vision_llm.cache_clear()
