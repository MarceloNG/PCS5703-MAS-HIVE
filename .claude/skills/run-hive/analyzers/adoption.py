#!/usr/bin/env python3
"""
HIVE adoption analyzer — FOCO em ADOÇÃO DE ROLE (Eixo 1 / issue #12).

Irmão focado de replay_analyze.py (ver SKILL.md §Analyzers — "adicione irmãos
focados"). Enquanto replay_analyze.py dá a view GERAL (1 linha "x/15 viraram
worker"), este mede o SINAL DA ADOÇÃO em detalhe, asseverável por máquina:

  - quantos dos 15 adotam `worker` (estado final = worker);
  - no PATH explorer-first (default→explorer→worker), se cada agente passou por
    `explorer` antes do worker;
  - RE-ADOÇÕES (adopt-spam): nº de ações `adopt` que um agente emite quando JÁ
    estava sobre um role que pontua — o anti-padrão que o gate `can_score_role`
    de role_adoption.asl deve PARAR quando o agente fica sobre a role-zone;
  - time-to-first-adoption: step da 1ª ação adopt(worker) bem-sucedida.

Mecânica (massim_2022/docs/scenario.md §adopt + protocol.md §replay):
  - O replay grava, por step, cada entidade com {name, role, action, actionParams,
    actionResult, pos}. Cada arquivo K.json contém VÁRIOS steps consecutivos
    (chaves "K","K+1",...), então a granularidade é POR STEP (não amostrada),
    o que permite contar CADA adopt — essencial p/ re-adoções.
  - A ação `adopt` tem 1 parâmetro (o role-alvo) e só sucede sobre uma role-zone
    (senão actionResult=failed_location). `role` na entidade é o role ATUAL após
    o step (worker/explorer/default/...). Roles são ADITIVOS no engine; aqui só
    nos importa a identidade do role atual.
  - step "-1" é o estado pré-jogo (action vazia) — IGNORADO na contagem de ações.

ALVO (limiares do cenário conf/scenarios/01-adopt.json): ≥10/15 adotam worker,
0 re-adoções. `--check` faz o gate sair com código !=0 se não bater.

Uso:
    python3 analyzers/adoption.py [replay_dir] [--json] [--check]
                                  [--min-workers N] [--max-readopts N]
    replay_dir  default = replay mais recente em massim_2022/server/replays/
"""

import argparse
import json
import sys
from pathlib import Path

# repo = .../.claude/skills/run-hive/analyzers/adoption.py -> parents[4]
REPO = Path(__file__).resolve().parents[4]
DEFAULT_REPLAY_ROOT = REPO / "massim_2022" / "server" / "replays"

# Limiares-alvo do cenário 01-adopt (issue #12).
DEFAULT_MIN_WORKERS = 10
DEFAULT_MAX_READOPTS = 0

# Roles que PONTUAM no cenário (têm `submit`): atingir um deles encerra a busca
# legítima por role. Adoção emitida JÁ ESTANDO num desses é re-adoção (adopt-spam).
SCORING_ROLES = ("worker", "constructor")


def latest_replay_dir(root: Path):
    dirs = [p for p in root.glob("*_A") if p.is_dir()]
    return max(dirs, key=lambda p: p.stat().st_mtime) if dirs else None


def load_steps(replay_dir: Path):
    """Return {step: [entities]} para todos os steps (cada arquivo tem vários)."""
    steps = {}
    for f in replay_dir.glob("[0-9-]*.json"):
        if f.name == "static.json":
            continue
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
    return steps


def analyze(replay_dir: Path):
    steps = load_steps(replay_dir)
    if not steps:
        return None

    # Sequência por agente, ordenada por step (ignora pré-jogo step<0).
    seq = {}  # name -> list[(step, role, action, params, result)]
    for step in sorted(s for s in steps if s >= 0):
        for e in steps[step]:
            name = e.get("name")
            if not name:
                continue
            role = e.get("role", "?")
            action = e.get("action", "") or ""
            params = e.get("actionParams", []) or []
            result = e.get("actionResult", "") or ""
            seq.setdefault(name, []).append((step, role, action, params, result))

    agents = {}
    for name, rows in sorted(seq.items()):
        adopt_attempts = []           # toda ação adopt (sucesso ou não)
        adopt_ok = []                 # ações adopt bem-sucedidas
        fail_reasons = {}             # motivo do adopt falho -> contagem
        passed_explorer = False       # role==explorer em algum momento
        first_worker_step = None      # step em que role vira worker (estado)
        first_worker_adopt_step = None  # step da 1ª adopt(worker) com sucesso
        readoptions = 0               # adopt emitida estando JÁ sobre role que pontua

        prev_role = None
        for (step, role, action, params, result) in rows:
            target = params[0] if params else None
            if action == "adopt":
                adopt_attempts.append((step, target, result))
                if result == "success":
                    adopt_ok.append((step, target, result))
                    if target == "worker" and first_worker_adopt_step is None:
                        first_worker_adopt_step = step
                else:
                    # failed_location = fora da role-zone; failed_parameter = role
                    # não adotável (catálogo/path errado). Ambos são adopt-spam falho.
                    key = result or "unknown"
                    fail_reasons[key] = fail_reasons.get(key, 0) + 1
                # Re-adoção: o agente já estava sobre um role que PONTUA e ainda
                # assim emite outra adopt → adopt-spam (gate falhou em parar).
                if prev_role in SCORING_ROLES:
                    readoptions += 1
            if role == "explorer":
                passed_explorer = True
            if role == "worker" and first_worker_step is None:
                first_worker_step = step
            prev_role = role

        final_role = rows[-1][1] if rows else "?"
        is_worker = final_role == "worker"
        agents[name] = {
            "final_role": final_role,
            "is_worker": is_worker,
            "passed_explorer": passed_explorer,
            "explorer_first_ok": bool(is_worker and passed_explorer),
            "first_worker_role_step": first_worker_step,
            "first_worker_adopt_step": first_worker_adopt_step,
            "adopt_attempts": len(adopt_attempts),
            "adopt_success": len(adopt_ok),
            "adopt_failed": len(adopt_attempts) - len(adopt_ok),
            "adopt_fail_reasons": fail_reasons,
            "readoptions": readoptions,
        }

    n = len(agents)
    workers = [k for k, v in agents.items() if v["is_worker"]]
    explorer_first = [k for k, v in agents.items() if v["explorer_first_ok"]]
    total_readopts = sum(v["readoptions"] for v in agents.values())
    total_adopt_failed = sum(v["adopt_failed"] for v in agents.values())
    fail_reasons = {}
    for v in agents.values():
        for k, c in v["adopt_fail_reasons"].items():
            fail_reasons[k] = fail_reasons.get(k, 0) + c
    ttfa = [v["first_worker_adopt_step"] for v in agents.values()
            if v["first_worker_adopt_step"] is not None]

    summary = {
        "replay": replay_dir.name,
        "agents_total": n,
        "workers_adopted": len(workers),
        "explorer_first_path": len(explorer_first),
        "total_readoptions": total_readopts,
        "total_failed_adopts": total_adopt_failed,
        "failed_adopt_reasons": fail_reasons,
        "time_to_first_adoption_min": min(ttfa) if ttfa else None,
        "time_to_first_adoption_max": max(ttfa) if ttfa else None,
        "time_to_first_adoption_mean": round(sum(ttfa) / len(ttfa), 1) if ttfa else None,
        "workers": sorted(workers),
    }
    return {"summary": summary, "agents": agents}


def print_report(out):
    s = out["summary"]
    print(f"\n{'='*64}")
    print(f"HIVE Adoption Analysis — {s['replay']}")
    print(f"{'='*64}")
    print(f"Adoção de worker : {s['workers_adopted']}/{s['agents_total']}")
    print(f"Path explorer-1st: {s['explorer_first_path']}/{s['agents_total']} "
          f"(default→explorer→worker)")
    print(f"RE-adoções       : {s['total_readoptions']}  (adopt-spam pós-sucesso; alvo=0)")
    reasons = ", ".join(f"{k}:{v}" for k, v in sorted(s["failed_adopt_reasons"].items())) \
        or "—"
    print(f"Adopts falhos    : {s['total_failed_adopts']}  ({reasons})")
    ttfa = s["time_to_first_adoption_mean"]
    print(f"Time-to-adopt    : min={s['time_to_first_adoption_min']} "
          f"max={s['time_to_first_adoption_max']} mean={ttfa}")
    print(f"{'='*64}")
    print(f"\n{'Agente':<10} {'final':>11} {'expl1st':>8} {'adoptOK':>8} "
          f"{'falhos':>7} {'re-ad':>6} {'tAdopt':>7}")
    print(f"{'-'*64}")
    for name, a in sorted(out["agents"].items()):
        ef = "sim" if a["explorer_first_ok"] else ("-" if a["is_worker"] else "x")
        t = a["first_worker_adopt_step"]
        print(f"{name:<10} {a['final_role']:>11} {ef:>8} {a['adopt_success']:>8} "
              f"{a['adopt_failed']:>7} {a['readoptions']:>6} "
              f"{t if t is not None else '-':>7}")


def main():
    p = argparse.ArgumentParser(description="Analisa ADOÇÃO DE ROLE num replay MASSim (Eixo 1).")
    p.add_argument("replay_dir", nargs="?", help="dir do replay (default: mais recente)")
    p.add_argument("--json", action="store_true", dest="as_json",
                   help="saída JSON asseverável")
    p.add_argument("--check", action="store_true",
                   help="gate: sai !=0 se não bater limiares-alvo")
    p.add_argument("--min-workers", type=int, default=DEFAULT_MIN_WORKERS)
    p.add_argument("--max-readopts", type=int, default=DEFAULT_MAX_READOPTS)
    args = p.parse_args()

    replay_dir = Path(args.replay_dir) if args.replay_dir \
        else latest_replay_dir(DEFAULT_REPLAY_ROOT)
    if replay_dir is None:
        print("ERROR: nenhum replay encontrado. Rode uma sim ou passe um caminho.",
              file=sys.stderr)
        sys.exit(1)
    if not replay_dir.exists():
        print(f"ERROR: {replay_dir} não existe.", file=sys.stderr)
        sys.exit(1)

    out = analyze(replay_dir)
    if out is None:
        print(f"ERROR: nenhum dado de replay em {replay_dir}", file=sys.stderr)
        sys.exit(1)

    if args.as_json:
        print(json.dumps(out, indent=2))
    else:
        print_report(out)

    if args.check:
        s = out["summary"]
        ok = (s["workers_adopted"] >= args.min_workers
              and s["total_readoptions"] <= args.max_readopts)
        verdict = "PASS" if ok else "FAIL"
        print(f"\n[GATE {verdict}] workers={s['workers_adopted']}/{s['agents_total']} "
              f"(min {args.min_workers}), re-adoptions={s['total_readoptions']} "
              f"(max {args.max_readopts})", file=sys.stderr)
        sys.exit(0 if ok else 2)


if __name__ == "__main__":
    main()
