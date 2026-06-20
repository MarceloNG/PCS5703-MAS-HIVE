#!/usr/bin/env python3
"""
Teste SIM-FREE do submit_strategy.py contra a fixture sintética em
fixtures/synthetic_submit_A/ (objetividade do submit, #50/#52).

Padrão de test_adoption.py: sem dependência de sim. Rode direto —
`python3 test_submit_strategy.py` — sai 0 se OK, !=0 se falhar (também
descoberto por pytest via a função test_submit_strategy).

A fixture exercita os 3 buckets que o analyzer precisa distinguir:
  agentZone  → rotaciona 2× DENTRO da goal zone (5,5) → spin-na-zona (ruim);
  agentDisp  → rotaciona 1× adjacente ao dispenser (1,1) → pré-alinhamento (ok);
  agentRoute → rotaciona 1× na rota e 1× no dispenser → nenhuma na zona.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from submit_strategy import analyze  # noqa: E402

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "synthetic_submit_A"


def test_submit_strategy():
    out = analyze(FIXTURE)
    assert out is not None, "fixture não carregou"
    s = out["summary"]
    a = out["agents"]

    # ----- agregados -----
    assert s["agents_total"] == 3, s
    assert s["rotations_total"] == 5, s
    assert s["rotations_in_zone"] == 2, s          # só agentZone, 2×
    assert s["rotations_at_dispenser"] == 2, s     # agentDisp 1× + agentRoute 1×
    assert s["rotations_on_route"] == 1, s         # agentRoute 1×
    assert s["submits_ok"] == 2, s                 # agentZone + agentDisp
    assert s["agents_with_zone_rotation"] == ["agentZone"], s

    # ----- por agente -----
    assert a["agentZone"]["rotations_in_zone"] == 2
    assert a["agentZone"]["rotations_at_dispenser"] == 0
    assert a["agentZone"]["first_zone_rotation_step"] == 0
    assert a["agentZone"]["submits_ok"] == 1

    assert a["agentDisp"]["rotations_in_zone"] == 0       # pré-alinhou no dispenser
    assert a["agentDisp"]["rotations_at_dispenser"] == 1
    assert a["agentDisp"]["submits_ok"] == 1

    assert a["agentRoute"]["rotations_in_zone"] == 0
    assert a["agentRoute"]["rotations_at_dispenser"] == 1
    assert a["agentRoute"]["rotations_on_route"] == 1
    assert a["agentRoute"]["first_zone_rotation_step"] is None

    # ----- lógica do gate --check (rotações-na-zona <= max) -----
    assert s["rotations_in_zone"] > 0, "baseline-like: gate --check (max=0) deve FALHAR"
    assert not (s["rotations_in_zone"] <= 0), "max=0 → FAIL esperado neste fixture"
    assert s["rotations_in_zone"] <= 2, "max=2 → PASS esperado neste fixture"


def main():
    test_submit_strategy()
    print("OK — submit_strategy.py: todas as asserções passaram contra a fixture sintética.")


if __name__ == "__main__":
    main()
