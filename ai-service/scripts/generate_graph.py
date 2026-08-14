"""Generate docs/graph.md (Mermaid) and docs/graph.png from the LangGraph definition."""
from app.graph.builder import _build

graph = _build().compile().get_graph()

with open("docs/graph.md", "w") as f:
    f.write("# RAG Chat — LangGraph\n\n```mermaid\n")
    f.write(graph.draw_mermaid())
    f.write("```\n")

with open("docs/graph.png", "wb") as f:
    f.write(graph.draw_mermaid_png())

print("docs/graph.md + docs/graph.png updated")
