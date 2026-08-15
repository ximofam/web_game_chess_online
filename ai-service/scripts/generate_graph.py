"""Generate docs/graph/<timestamp>_graph.md (Mermaid) from the LangGraph definition."""
import os
from datetime import datetime

from app.graph.builder import _build

graph = _build().compile().get_graph()

os.makedirs("docs/graph", exist_ok=True)
timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
filepath = f"docs/graph/{timestamp}_graph.md"

with open(filepath, "w") as f:
    f.write("# RAG Chat — LangGraph\n\n```mermaid\n")
    f.write(graph.draw_mermaid())
    f.write("\n```\n")

print(f"{filepath} generated")
