import os
import sys
from pathlib import Path

# Ensure root dir is on sys.path
root_dir = Path(__file__).resolve().parent.parent
if str(root_dir) not in sys.path:
    sys.path.insert(0, str(root_dir))

import pytest
import app.rag.ingestion.cache as cache_module


@pytest.fixture(autouse=True)
def guard_workspace_cache(monkeypatch):
    """Safety guard: Prevent any unit test from writing to the real workspace data/cache directory.

    If a test attempts to save embeddings to the workspace data/cache directory,
    it raises a RuntimeError to enforce complete test isolation.
    """
    real_cache_dir = (root_dir / "data" / "cache").resolve()
    orig_save = cache_module.save_embeddings_to_csv

    def safe_save_embeddings_to_csv(filepath, docs, embeddings):
        p = Path(filepath).resolve()
        try:
            p.relative_to(real_cache_dir)
            raise RuntimeError(
                f"SAFETY VIOLATION: Unit test attempted to write directly to real workspace cache at '{p}'. "
                "All tests must pass an isolated cache_dir (e.g. using tmp_path / 'cache')."
            )
        except ValueError:
            pass
        return orig_save(filepath, docs, embeddings)

    monkeypatch.setattr(cache_module, "save_embeddings_to_csv", safe_save_embeddings_to_csv)
