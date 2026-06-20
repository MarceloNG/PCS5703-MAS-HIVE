#!/usr/bin/env python3
"""
HIVE submit-strategy analyzer — FOCO em ROTAÇÕES-NA-ZONA (Objetividade do submit, #50/#52).

Irmão focado de replay_analyze.py (ver SKILL.md §Analyzers). Prova a DoD do rework
"submete na entrada, sem spin na zona": conta, por agente, ações `rotate` executadas
ENQUANTO o agente está numa célula de GOAL ZONE (congestionada) — distinguindo-as das
rotações de PRÉ-ALINHAMENTO no DISPENSER (descongestionado, desejável) e das rotações
na ROTA (terceiro caso — ver review scope-guardian).

  - rotações-na-zona  : `rotate` com pos do agente DENTRO de uma goal zone;
  - rotações-no-disp. : `rotate` a Manhattan <= 1 de um dispenser (pré-alinhamento U3);
  - rotações-na-rota  : `rotate` em qualquer outro lugar.

Pós-rework (pré-alinhamento no dispenser, U3): rotações-na-zona ≈ 0.
Baseline (loop cego `rotate(cw)×4` na zona, #52): rotações-na-zona > 0. O `--check`
FALHA no baseline e PASSA pós-U3.

Membership de zona (massim Grid/ZoneList.findOneZoneAt): célula (x,y) está numa goal
zone sse distanceTo(centro) <= r para algum goalZone {pos,r} do step. distanceTo do
massim é Manhattan (|dx|+|dy|) — a mesma métrica de adjacência de connect (== 1).

Mecânica de replay (igual a adoption.py): cada K.json contém VÁRIOS steps (chaves
"K","K+1",...); cada state tem {step, entities:[{name,action,actionResult,pos,...}],
goalZones:[{pos,r}], dispensers:[{pos,...}]}. step "-1"/<0 (pré-jogo) é ignorado.

Uso:
    python3 analyzers/submit_strategy.py [replay_dir] [--json] [--check]
                                         [--max-zone-rotations N]
    replay_dir  default = replay mais recente em massim_2022/server/replays/
                (honra HIVE_REPLAY_ROOT — sim isolada por porta, harness #11)
"""

import argparse
import json
import os
import sys
from pathlib import Path

# repo = .../.claude/skills/run-hive/analyzers/submit_strategy.py -> parents[4]
REPO = Path(__file__).resolve().parents[4]
# Sim isolada (harness #11): HIVE_REPLAY_ROOT redireciona p/ o workdir por-porta.
DEFAULT_REPLAY_ROOT = Path(os.environ.get(
    "HIVE_REPLAY_ROOT", REPO / "massim_2022" / "server" / "replays"))

# DoD: rotações-na-zona ≈ 0 após o pré-alinhamento no dispenser (U3).
DEFAULT_MAX_ZONE_ROTATIONS = 0


def latest_replay_dir(root: Path):
    dirs = [p for p in root.glob("*_A") if p.is_dir()]
    return max(dirs, key=lambda p: p.stat().st_mtime) if dirs else None


def _manhattan(ax, ay, bx, by):
    return abs(ax - bx) + abs(ay - by)


def load_steps(replay_dir: Path):
    """Return {step: state} para todos os steps (cada arquivo tem vários)."""
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
            steps[step] = state
    return steps


def _in_goal_zone(x, y, goal_zones):
    """massim ZoneList.findOneZoneAt: distanceTo(centro) <= r (Manhattan)."""
    for gz in goal_zones:
        pos = gz.get("pos") or []
        if len(pos) >= 2 and _manhattan(x, y, pos[0], pos[1]) <= gz.get("r", 0):
            return True
    return False


def _at_dispenser(x, y, dispensers):
    """Agente coleta/pré-alinha adjacente ao dispenser (Manhattan <= 1)."""
    for d in dispensers:
        pos = d.get("pos") or []
        if len(pos) >= 2 and _manhattan(x, y, pos[0], pos[1]) <= 1:
            return True
    return False


def analyze(replay_dir: Path):
    steps = load_steps(replay_dir)
    if not steps:
        return None

    agents = {}
    for step in sorted(s for s in steps if s >= 0):
        state = steps[step]
        goal_zones = state.get("goalZones") or []
        dispensers = state.get("dispensers") or []
        for e in state.get("entities", []):
            name = e.get("name")
            if not name:
                continue
            a = agents.setdefault(name, {
                "rotations_total": 0,
                "rotations_in_zone": 0,
                "rotations_at_dispenser": 0,
                "rotations_on_route": 0,
                "submits_ok": 0,
                "first_zone_rotation_step": None,
            })
            action = e.get("action", "") or ""
            result = e.get("actionResult", "") or ""
            if action == "submit" and result == "success":
                a["submits_ok"] += 1
            if action != "rotate":
                continue
            a["rotations_total"] += 1
            pos = e.get("pos") or []
            if len(pos) < 2:
                a["rotations_on_route"] += 1
                continue
            x, y = pos[0], pos[1]
            if _in_goal_zone(x, y, goal_zones):
                a["rotations_in_zone"] += 1
                if a["first_zone_rotation_step"] is None:
                    a["first_zone_rotation_step"] = step
            elif _at_dispenser(x, y, dispensers):
                a["rotations_at_dispenser"] += 1
            else:
                a["rotations_on_route"] += 1

    summary = {
        "replay": replay_dir.name,
        "agents_total": len(agents),
        "rotations_total": sum(v["rotations_total"] for v in agents.values()),
        "rotations_in_zone": sum(v["rotations_in_zone"] for v in agents.values()),
        "rotations_at_dispenser": sum(v["rotations_at_dispenser"] for v in agents.values()),
        "rotations_on_route": sum(v["rotations_on_route"] for v in agents.values()),
        "submits_ok": sum(v["submits_ok"] for v in agents.values()),
        "agents_with_zone_rotation": sorted(
            k for k, v in agents.items() if v["rotations_in_zone"] > 0),
    }
    return {"summary": summary, "agents": agents}


def print_report(out):
    s = out["summary"]
    print(f"\n{'='*64}")
    print(f"HIVE Submit-Strategy Analysis — {s['replay']}")
    print(f"{'='*64}")
    print(f"Rotações TOTAIS    : {s['rotations_total']}")
    print(f"  na ZONA (ruim)   : {s['rotations_in_zone']}   (alvo ≈ 0 pós-pré-alinhamento)")
    print(f"  no DISPENSER     : {s['rotations_at_dispenser']}   (pré-alinhamento desejável)")
    print(f"  na ROTA          : {s['rotations_on_route']}")
    print(f"Submits OK         : {s['submits_ok']}")
    zr = ", ".join(s["agents_with_zone_rotation"]) or "—"
    print(f"Agentes c/ spin-zona: {zr}")
    print(f"{'='*64}")
    print(f"\n{'Agente':<10} {'rotTot':>7} {'zona':>6} {'disp':>6} {'rota':>6} "
          f"{'subOK':>6} {'1ªzona':>7}")
    print(f"{'-'*54}")
    for name, a in sorted(out["agents"].items()):
        t = a["first_zone_rotation_step"]
        print(f"{name:<10} {a['rotations_total']:>7} {a['rotations_in_zone']:>6} "
              f"{a['rotations_at_dispenser']:>6} {a['rotations_on_route']:>6} "
              f"{a['submits_ok']:>6} {t if t is not None else '-':>7}")


def main():
    p = argparse.ArgumentParser(
        description="Analisa ROTAÇÕES-NA-ZONA num replay MASSim (objetividade do submit).")
    p.add_argument("replay_dir", nargs="?", help="dir do replay (default: mais recente)")
    p.add_argument("--json", action="store_true", dest="as_json",
                   help="saída JSON asseverável")
    p.add_argument("--check", action="store_true",
                   help="gate: sai !=0 se rotações-na-zona > max")
    p.add_argument("--max-zone-rotations", type=int, default=DEFAULT_MAX_ZONE_ROTATIONS)
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
        ok = s["rotations_in_zone"] <= args.max_zone_rotations
        verdict = "PASS" if ok else "FAIL"
        print(f"\n[GATE {verdict}] rotações-na-zona={s['rotations_in_zone']} "
              f"(max {args.max_zone_rotations}); dispenser={s['rotations_at_dispenser']} "
              f"rota={s['rotations_on_route']} submits={s['submits_ok']}", file=sys.stderr)
        sys.exit(0 if ok else 2)


if __name__ == "__main__":
    main()
