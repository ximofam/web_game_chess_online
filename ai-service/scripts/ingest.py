import argparse

from app.services.rag_service import ingest_markdown_directory


def ingest(docs_path: str, clear: bool = False) -> None:
    print(f"Ingesting markdown docs from {docs_path} (clear={clear})...")
    count = ingest_markdown_directory(docs_path, clear=clear)
    print(f"Ingestion complete: {count} chunks indexed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ingest markdown docs into Vector DB")
    parser.add_argument("--path", type=str, default="./docs/business/viechess", help="Path to markdown docs")
    parser.add_argument("--clear", action="store_true", help="Clear existing vectors before ingesting")
    args = parser.parse_args()

    ingest(args.path, args.clear)

