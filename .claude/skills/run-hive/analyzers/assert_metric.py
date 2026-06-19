#!/usr/bin/env python3
"""
HIVE harness de cenários (#11) — ASSERÇÃO DE MÉTRICA DE CAPACIDADE.

Roda sobre um replay e imprime PASS/FAIL de uma métrica de **capacidade** (não score),
a partir da spec do cenário. A métrica é **plugável por cenário**: o JSON do cenário
declara um bloco `assert` (ver conf/scenarios/README.md), ex.:

    "assert": { "metric": "role_adoption", "min": 12 }

Reusa os loaders do analyzer geral (replay_analyze.py) — "tudo se melhora, nada do zero".
Cada métrica é uma função pura sobre o replay carregado; adicione irmãs em METRICS conforme
novos cenários precisarem (submits, navegação, attach...).

Uso:
    assert_metric.py --spec '<json>'  [replay_dir]
    assert_metric.py --metric role_adoption --min 12  [replay_dir]
    assert_metric.py --scenario-conf conf/scenarios/01-adopt.json  [replay_dir]

Saída: linha humana PASS/FAIL + exit code 0 (PASS) / 1 (FAIL) / 2 (erro de uso/dados).
Com --json, imprime também o objeto da medição (assertável por outro harness).
"""

import argparse
import json
import sys
from pathlib import Path

# reusa os loaders do analyzer geral (mesmo diretório)
sys.path.insert(0, str(Path(__file__).resolve().parent))
from replay_analyze import (  # noqa: E402
    DEFAULT_REPLAY_ROOT,
    analyze,
    latest_replay_dir,
)


# ---------------------------------------------------------------------------
# Métricas de capacidade (plugáveis). Cada função recebe `results` (saída de
# replay_analyze.analyze) e devolve (valor:number, detalhe:str).
# ---------------------------------------------------------------------------

def m_role_adoption(results):
    """Quantos dos N agentes chegaram a adotar `worker` (1º step como worker)."""
    adopted = [n for n, d in results.items() if d.get("worker_first_step") is not None]
    return len(adopted), f"{len(adopted)}/{len(results)} adotaram worker"


def m_final_workers(results):
    """Quantos agentes terminaram a sim com role final == worker."""
    fin = [n for n, d in results.items() if d.get("final_role") == "worker"]
    return len(fin), f"{len(fin)}/{len(results)} terminaram como worker"


def m_submits_ok(results):
    """Total de submits bem-sucedidos no time (capacidade de entrega)."""
    total = sum(d.get("submits_ok", 0) for d in results.values())
    return total, f"{total} submits OK no time"


def m_max_stuck(results):
    """Maior corrida de failed_path entre os agentes (proxy de livelock — MENOR é melhor)."""
    worst = max((d.get("max_stuck_run", 0) for d in results.values()), default=0)
    return worst, f"pior corrida de failed_path = {worst} steps"


def m_failed_path_total(results):
    """Total de eventos failed_path no time (MENOR é melhor — issue #15)."""
    total = sum(d.get("results", {}).get("failed_path", 0) for d in results.values())
    return total, f"{total} failed_path total no time"


METRICS = {
    "role_adoption": m_role_adoption,
    "final_workers": m_final_workers,
    "submits_ok": m_submits_ok,
    "max_stuck": m_max_stuck,
    "failed_path_total": m_failed_path_total,
}


def evaluate(spec, results):
    """Aplica a spec (`metric` + `min`/`max`/`equals`) à medição. Devolve dict do veredito."""
    metric = spec.get("metric")
    fn = METRICS.get(metric)
    if fn is None:
        raise SystemExit(
            f"ERRO: métrica desconhecida '{metric}'. Conhecidas: {', '.join(sorted(METRICS))}"
        )
    value, detail = fn(results)

    checks = []
    if "min" in spec:
        checks.append((value >= spec["min"], f"{value} >= min {spec['min']}"))
    if "max" in spec:
        checks.append((value <= spec["max"], f"{value} <= max {spec['max']}"))
    if "equals" in spec:
        checks.append((value == spec["equals"], f"{value} == {spec['equals']}"))
    if not checks:
        raise SystemExit("ERRO: a spec de assert precisa de ao menos um de min/max/equals.")

    passed = all(ok for ok, _ in checks)
    return {
        "metric": metric,
        "value": value,
        "detail": detail,
        "checks": [{"ok": ok, "desc": desc} for ok, desc in checks],
        "pass": passed,
    }


def load_spec(args):
    if args.spec:
        return json.loads(args.spec)
    if args.scenario_conf:
        conf = json.loads(Path(args.scenario_conf).read_text())
        spec = conf.get("assert")
        if not spec:
            raise SystemExit(f"ERRO: {args.scenario_conf} não tem bloco 'assert'.")
        return spec
    if args.metric:
        spec = {"metric": args.metric}
        if args.min is not None:
            spec["min"] = args.min
        if args.max is not None:
            spec["max"] = args.max
        if args.equals is not None:
            spec["equals"] = args.equals
        return spec
    raise SystemExit("ERRO: passe --spec, --scenario-conf ou --metric.")


def main():
    p = argparse.ArgumentParser(description="Assere uma métrica de capacidade a partir do replay.")
    p.add_argument("replay_dir", nargs="?", help="dir do replay (default: mais recente)")
    p.add_argument("--spec", help="spec JSON inline: {\"metric\":..., \"min\":...}")
    p.add_argument("--scenario-conf", help="config do cenário; lê o bloco 'assert' dele")
    p.add_argument("--metric", choices=sorted(METRICS), help="métrica direta (alternativa a --spec)")
    p.add_argument("--min", type=float)
    p.add_argument("--max", type=float)
    p.add_argument("--equals", type=float)
    p.add_argument("--json", action="store_true", dest="as_json", help="imprime o veredito em JSON")
    args = p.parse_args()

    spec = load_spec(args)

    replay_dir = Path(args.replay_dir) if args.replay_dir else latest_replay_dir(DEFAULT_REPLAY_ROOT)
    if replay_dir is None or not replay_dir.exists():
        print("ERRO: nenhum replay encontrado. Rode uma sim primeiro ou passe um caminho.",
              file=sys.stderr)
        sys.exit(2)

    results = analyze(replay_dir, goal=None, role_zone=None, stuck_n=5, agent_filter=None)
    if not results:
        print(f"ERRO: nenhum dado de replay em {replay_dir}", file=sys.stderr)
        sys.exit(2)

    verdict = evaluate(spec, results)
    tag = "PASS" if verdict["pass"] else "FAIL"
    crit = " e ".join(c["desc"] for c in verdict["checks"])
    print(f"[{tag}] métrica={verdict['metric']} → {verdict['detail']}  (critério: {crit})")
    if args.as_json:
        verdict["replay"] = replay_dir.name
        print(json.dumps(verdict, indent=2, ensure_ascii=False))
    sys.exit(0 if verdict["pass"] else 1)


if __name__ == "__main__":
    main()
