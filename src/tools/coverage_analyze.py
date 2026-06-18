#!/usr/bin/env python3
"""
Analyzer de cobertura por heading — valida heading-balanceado.

Lê /tmp/hive-run/agents.log e extrai as entradas NAV (a cada 40 steps)
com posição e nome do agente. Compara o deslocamento real com o heading
esperado do nome (connectionA<N> mod 4 → N/E/S/W).

Uso:
    python3 src/tools/coverage_analyze.py [agents.log]
"""

import sys
import re
from collections import defaultdict

LOG_FILE = sys.argv[1] if len(sys.argv) > 1 else "/tmp/hive-run/agents.log"

HEADING = {0: "N", 1: "E", 2: "S", 3: "W"}
HEADING_SIGN = {
    "N": (0, -1),   # N: y decresce
    "E": (1,  0),   # E: x aumenta
    "S": (0,  1),   # S: y aumenta
    "W": (-1, 0),   # W: x decresce
}

# [NAV] Step N Agent=connectionA5 Pos(X,Y) Map: vis=V ...
NAV_RE = re.compile(
    r"\[NAV\] Step (\d+) Agent=(\S+) Pos\((-?\d+),(-?\d+)\) Map: vis=(\d+)"
)

def extract_index(name):
    m = re.search(r'\d+$', name)
    return int(m.group()) if m else -1

def heading_for(name):
    idx = extract_index(name)
    return HEADING[idx % 4] if idx >= 0 else "?"

records = defaultdict(list)  # agent -> [(step, x, y, vis)]

try:
    with open(LOG_FILE) as f:
        for line in f:
            m = NAV_RE.search(line)
            if m:
                step, agent, x, y, vis = m.groups()
                records[agent].append((int(step), int(x), int(y), int(vis)))
except FileNotFoundError:
    print(f"[ERRO] Arquivo não encontrado: {LOG_FILE}")
    sys.exit(1)

if not records:
    print("[AVISO] Nenhuma linha NAV encontrada. O log contém entradas NAV com Agent=?")
    print("  Verifique se a sim rodou com HeadingValidationConfig e se navigation.asl inclui .my_name(Me).")
    sys.exit(0)

print("=" * 70)
print("ANÁLISE DE COBERTURA POR HEADING")
print("=" * 70)
print(f"{'Agente':<18} {'Heading':>7} {'Steps':>6} {'vis_final':>9} "
      f"{'avg_X':>7} {'avg_Y':>7}  {'Match?'}")
print("-" * 70)

heading_counts = defaultdict(lambda: {"correct": 0, "total": 0})

for agent in sorted(records.keys()):
    pts = records[agent]
    heading = heading_for(agent)
    dx_sign, dy_sign = HEADING_SIGN.get(heading, (0, 0))

    final_vis = pts[-1][3] if pts else 0
    avg_x = sum(x for _, x, _, _ in pts) / len(pts) if pts else 0
    avg_y = sum(y for _, _, y, _ in pts) / len(pts) if pts else 0

    # Verifica se o deslocamento médio é coerente com o heading esperado
    match = "?"
    if heading != "?" and pts:
        if dx_sign != 0:
            match = "✓" if (avg_x * dx_sign) > 0 else "✗"
        elif dy_sign != 0:
            match = "✓" if (avg_y * dy_sign) > 0 else "✗"

        heading_counts[heading]["total"] += 1
        if match == "✓":
            heading_counts[heading]["correct"] += 1

    print(f"{agent:<18} {heading:>7} {len(pts):>6} {final_vis:>9} "
          f"{avg_x:>7.1f} {avg_y:>7.1f}  {match}")

print("-" * 70)
print()
print("COERÊNCIA POR HEADING (deslocamento médio na direção esperada):")
for h in ["N", "E", "S", "W"]:
    c = heading_counts[h]
    if c["total"] > 0:
        pct = 100 * c["correct"] / c["total"]
        bar = "█" * c["correct"] + "░" * (c["total"] - c["correct"])
        print(f"  {h}: {bar}  {c['correct']}/{c['total']}  ({pct:.0f}%)")

print()
print("Interpretação:")
print("  avg_X > 0 → agente foi para Leste  |  avg_X < 0 → Oeste")
print("  avg_Y < 0 → agente foi para Norte  |  avg_Y > 0 → Sul")
print("  Match ✓ = deslocamento coerente com heading esperado pelo nome")
