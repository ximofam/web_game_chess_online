import argparse
from pathlib import Path

from langchain_core.documents import Document
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter

from app.ai.retriever import get_vector_store


def ingest(docs_path: str, clear: bool = False):
    from app.core.config import get_settings

    if clear:
        print("Clearing existing vector store...")
        settings = get_settings()
        if settings.vector_store == "chroma":
            import shutil
            chroma_path = Path(settings.chroma_persist_directory)
            if chroma_path.exists():
                shutil.rmtree(chroma_path)
        else:
            from sqlalchemy import create_engine, text
            engine = create_engine(settings.database_url)
            with engine.begin() as conn:
                conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_embedding CASCADE;"))
                conn.execute(text("TRUNCATE TABLE ai_service.langchain_pg_collection CASCADE;"))

    print(f"Loading markdown files from {docs_path}...")
    base_dir = Path(docs_path)

    if not base_dir.exists():
        print(f"Error: Directory {docs_path} does not exist.")
        return

    docs = []
    for filepath in base_dir.rglob("*.md"):
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
            rel_path = str(filepath.relative_to(base_dir))
            docs.append(Document(page_content=content, metadata={"source": rel_path}))

    if not docs:
        print("No markdown files found!")
        return

    print(f"Found {len(docs)} documents. Splitting...")

    headers_to_split_on = [
        ("#", "H1"),
        ("##", "H2"),
        ("###", "H3"),
    ]
    markdown_splitter = MarkdownHeaderTextSplitter(headers_to_split_on=headers_to_split_on, strip_headers=False)

    md_header_splits = []
    for doc in docs:
        splits = markdown_splitter.split_text(doc.page_content)
        for split in splits:
            # Lấy các header H1, H2, H3 từ metadata ghép lại thành Context path
            header_context = " > ".join([v for k, v in split.metadata.items() if k in ["H1", "H2", "H3"]])

            # Tiêm Context path trực tiếp vào đầu page_content để Embedding model đọc được!
            if header_context:
                split.page_content = f"[{header_context}]\n{split.page_content}"

            split.metadata.update(doc.metadata)
        md_header_splits.extend(splits)

    text_splitter = RecursiveCharacterTextSplitter(chunk_size=1000, chunk_overlap=100)
    final_chunks = text_splitter.split_documents(md_header_splits)

    print(f"Created {len(final_chunks)} chunks. Inserting into Vector Store...")
    for i in range(10):
        print(final_chunks[i])
    store = get_vector_store()
    store.add_documents(final_chunks)

    print("Ingestion complete!")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Ingest markdown docs into Vector DB")
    parser.add_argument("--path", type=str, default="./docs/business/viechess", help="Path to markdown docs")
    parser.add_argument("--clear", action="store_true", help="Clear existing vectors before ingesting")
    args = parser.parse_args()

    ingest(args.path, args.clear)
