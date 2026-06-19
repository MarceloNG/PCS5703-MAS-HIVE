#!/usr/bin/env python3
"""
HIVE replay analyzer — GENERAL view.

Melhorado a partir de ~/repos/MAPC/scripts/replay_analyze.py (cite & improve):
  - default aponta para o massim_2022 DESTE repo (sem precisar de env var);
  - --goal/--role são OPCIONAIS (o cenário oficial é absolutePosition:false, então
    distâncias absolutas raramente fazem sentido — colunas omitidas se não passadas);
  - SUMÁRIO no topo focado no sinal que mais usamos: ADOÇÃO DE ROLE (quantos viraram
    worker e em que step) + submits + score do results/*.json casado pelo id do replay.

É o analyzer GERAL. Para focos específicos (navegação/livelock, estratégia de
submit, normas...) crie irmãos em analyzers/<foco>.py — ver SKILL.md ("Analyzers").

Uso:
    python3 analyzers/replay_analyze.py [replay_dir] [--goal X Y] [--role X Y]
                                        [--stuck N] [--agent NOME] [--json]
    replay_dir  default = replay mais recente em massim_2022/server/replays/
"""

import argparse
import json
import os
import sys
from collections import defaultdict
from pathlib import Path

# repo = .../.claude/skills/run-hive/analyzers/replay_analyze.py -> parents[4]
REPO = Path(__file__).resolve().parents[4]
# Sim isolada (harness #11): HIVE_REPLAY_ROOT/HIVE_RESULTS_ROOT redirecionam p/ o workdir
# por-porta (resultPath/replayPath isolados); sem env, usa o default do servidor.
DEFAULT_REPLAY_ROOT = Path(os.environ.get(
    "HIVE_REPLAY_ROOT", REPO / "massim_2022" / "server" / "replays"))
DEFAULT_RESULTS_ROOT = Path(os.environ.get(
    "HIVE_RESULTS_ROOT", REPO / "massim_2022" / "server" / "results"))
STUCK_THRESHOLD = 5


def latest_replay_dir(root: Path):
    dirs = [p for p in root.glob("*_A") if p.is_dir()]
    return max(dirs, key=lambda p: p.stat().st_mtime) if dirs else None


def manhattan(ax, ay, bx, by):
    return abs(ax - bx) + abs(ay - by)


def load_replay(replay_dir: Path):
    """Return list of (step, entities_list) sorted by step."""
    steps = {}
    for f in replay_dir.glob("[0-9]*.json"):
        try:
            data = json.loads(f.read_text(errors="replace"))
        except (json.JSONDecodeError, OSError):
            continue
        for state in data.values():
            if not isinstance(state, dict):
                continue
            step = state.get("step")
            if step is None:
                continue
            steps[step] = state.get("entities", [])
    return sorted(steps.items())


def score_for_replay(replay_dir: Path):
    """Casa o id do replay (sufixo numérico antes de _A) com um results/*.json."""
    name = replay_dir.name  # ex.: 2026-06-18-11-31-06-1781793066442_A
    sim_id = name[:-2] if name.endswith("_A") else name
    sim_id = sim_id.rsplit("-", 1)[-1]  # 1781793066442
    if not DEFAULT_RESULTS_ROOT.exists():
        return None
    for rf in DEFAULT_RESULTS_ROOT.glob("*.json"):
        try:
            d = json.loads(rf.read_text(errors="replace"))
        except (json.JSONDecodeError, OSError):
            continue
        for k, v in d.items():
            if sim_id in k and isinstance(v, dict):
                a = v.get("A", {})
                if isinstance(a, dict) and "score" in a:
                    return a["score"]
    return None


def analyze(replay_dir, goal, role_zone, stuck_n, agent_filter):
    steps = load_replay(replay_dir)
    if not steps:
        print(f"ERROR: nenhum dado de replay em {replay_dir}", file=sys.stderr)
        return None

    agent_rows = defaultdict(list)
    agent_actions = defaultdict(lambda: defaultdict(int))
    agent_results = defaultdict(lambda: defaultdict(int))

    for step, entities in steps:
        for e in entities:
            name = e.get("name", "?")
            if agent_filter and name != agent_filter:
                continue
            pos = e.get("pos", [])
            if len(pos) < 2:
                continue
            x, y = pos[0], pos[1]
            action = e.get("action", "none")
            result = e.get("actionResult", "none")
            role = e.get("role", "?")
            attached = e.get("attached", [])
            g_dist = manhattan(x, y, *goal) if goal else None
            rz_dist = manhattan(x, y, *role_zone) if role_zone else None
            agent_rows[name].append((step, x, y, g_dist, rz_dist, action, result, role, len(attached)))
            agent_actions[name][action] += 1
            agent_results[name][result] += 1

    results = {}
    for name, rows in sorted(agent_rows.items()):
        rows.sort(key=lambda r: r[0])
        worker_first = next((r[0] for r in rows if r[7] == "worker"), None)
        max_stuck = cur_stuck = 0
        stuck_at_step = None
        for r in rows:
            if r[6] == "failed_path":
                cur_stuck += 1
                if cur_stuck > max_stuck:
                    max_stuck = cur_stuck
                    stuck_at_step = r[0] - cur_stuck + 1
            else:
                cur_stuck = 0
        results[name] = {
            "rows": rows,
            "actions": dict(agent_actions[name]),
            "results": dict(agent_results[name]),
            "worker_first_step": worker_first,
            "max_stuck_run": max_stuck,
            "stuck_at_step": stuck_at_step if max_stuck >= stuck_n else None,
            "final_role": rows[-1][7] if rows else "?",
            "submits_ok": agent_results[name].get("success", 0) if "submit" in agent_actions[name] else 0,
        }
    return results


def print_report(results, replay_dir, score):
    total_steps = max(r[0] for d in results.values() for r in d["rows"]) if results else 0
    n = len(results)
    adopted = [name for name, d in results.items() if d["worker_first_step"] is not None]
    total_submit_actions = sum(d["actions"].get("submit", 0) for d in results.values())

    print(f"\n{'='*70}")
    print(f"HIVE Replay Analysis — {replay_dir.name}")
    print(f"{'='*70}")
    print(f"Steps: {total_steps}   Agentes: {n}   Score (results/*.json): "
          f"{score if score is not None else '?'}")
    print(f"ADOÇÃO DE ROLE: {len(adopted)}/{n} viraram worker"
          + (f"  ({', '.join(sorted(adopted))})" if adopted else "  — NENHUM adotou (gate de score!)"))
    print(f"Ações submit emitidas: {total_submit_actions}")
    print(f"{'='*70}")

    print(f"\n{'Agente':<10} {'RoleAdopt':>10} {'RoleFinal':>11} {'submitOK':>9} {'Stuck@':>9}")
    print(f"{'-'*54}")
    for name, d in sorted(results.items()):
        adopt = f"step {d['worker_first_step']}" if d['worker_first_step'] is not None else "NEVER"
        stuck = f"step {d['stuck_at_step']}" if d['stuck_at_step'] else "-"
        print(f"{name:<10} {adopt:>10} {d['final_role']:>11} {d['submits_ok']:>9} {stuck:>9}")

    print(f"\n{'-'*70}\nHistograma de ações (ação: n  |  resultado: n)\n{'-'*70}")
    for name, d in sorted(results.items()):
        acts = ", ".join(f"{k}:{v}" for k, v in sorted(d["actions"].items()) if k not in ("none", ""))
        ress = ", ".join(f"{k}:{v}" for k, v in sorted(d["results"].items()) if k not in ("none", ""))
        print(f"{name}: {acts}")
        print(f"{'':10}→ {ress}")


def main():
    p = argparse.ArgumentParser(description="Analisa um replay MASSim do HIVE (view geral).")
    p.add_argument("replay_dir", nargs="?", help="dir do replay (default: mais recente)")
    p.add_argument("--goal", nargs=2, type=int, default=None, metavar=("X", "Y"))
    p.add_argument("--role", nargs=2, type=int, default=None, metavar=("X", "Y"))
    p.add_argument("--stuck", type=int, default=STUCK_THRESHOLD)
    p.add_argument("--agent", help="filtrar um agente")
    p.add_argument("--json", action="store_true", dest="as_json")
    args = p.parse_args()

    replay_dir = Path(args.replay_dir) if args.replay_dir else latest_replay_dir(DEFAULT_REPLAY_ROOT)
    if replay_dir is None:
        print("ERROR: nenhum replay encontrado. Rode uma sim primeiro ou passe um caminho.", file=sys.stderr)
        sys.exit(1)
    if not replay_dir.exists():
        print(f"ERROR: {replay_dir} não existe.", file=sys.stderr)
        sys.exit(1)

    goal = tuple(args.goal) if args.goal else None
    role = tuple(args.role) if args.role else None
    results = analyze(replay_dir, goal, role, args.stuck, args.agent)
    if results is None:
        sys.exit(1)

    if args.as_json:
        out = {name: {k: v for k, v in d.items() if k != "rows"} for name, d in results.items()}
        print(json.dumps(out, indent=2))
    else:
        print_report(results, replay_dir, score_for_replay(replay_dir))


if __name__ == "__main__":
    main()
