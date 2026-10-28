"""Mapa wiedzy Graphify z samego kodu aplikacji (wszystkie moduły, bez testów, obrazów i dokumentów).

Analiza jest lokalna (drzewo składni), bez modeli i bez kosztu tokenów. Uruchamiać z korzenia repozytorium interpreterem
pakietu graphifyy; wynik trafia do graphify-out/ (poza gitem). Używa go hook startu sesji w chmurze.
"""
import shutil
from pathlib import Path

from graphify.analyze import god_nodes, suggest_questions, surprising_connections
from graphify.build import build_from_json
from graphify.cluster import cluster, score_all
from graphify.export import to_json
from graphify.extract import collect_files, extract
from graphify.report import generate

ROOT = Path(".")
SOURCES = ["app/src/main/kotlin", "core/*/src/main/kotlin", "feature/*/src/main/kotlin"]
OUT = ROOT / "graphify-out"

files = sorted({f for pattern in SOURCES for d in ROOT.glob(pattern) for f in collect_files(d)})
ext = extract(files, cache_root=ROOT, root=ROOT)
graph = build_from_json({**ext, "hyperedges": []}, root=str(ROOT), directed=False)
parts = cluster(graph)
degree = dict(graph.degree())
labels = {
    c: " / ".join(str(graph.nodes[n].get("label", n)) for n in sorted(ns, key=lambda n: -degree.get(n, 0))[:2])[:48]
    for c, ns in parts.items()
}
shutil.rmtree(OUT, ignore_errors=True)  # świeża budowa; ochrona przed zmniejszeniem grafu nie ma tu sensu
OUT.mkdir()
to_json(graph, parts, str(OUT / "graph.json"), community_labels=labels)
detection = {"total_files": len(files), "total_words": 0, "files": {"code": [str(f) for f in files]},
             "needs_graph": True, "warning": None, "skipped_sensitive": []}
report = generate(graph, parts, score_all(graph, parts), labels, god_nodes(graph),
                  surprising_connections(graph, parts), detection, {"input": 0, "output": 0}, str(ROOT),
                  suggested_questions=suggest_questions(graph, parts, labels))
(OUT / "GRAPH_REPORT.md").write_text(report, encoding="utf-8")
print(f"Graphify: {len(files)} plików, {graph.number_of_nodes()} węzłów, {graph.number_of_edges()} krawędzi, "
      f"{len(parts)} społeczności -> {OUT}")
