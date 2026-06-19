#!/usr/bin/env python3
"""
Teste SIM-FREE do adoption.py contra a fixture sintética em
fixtures/synthetic_adopt_A/ (issue #12 / Eixo 1).

Sem pytest: rode direto — `python3 test_adoption.py` — sai 0 se OK, !=0 se falhar.
A fixture exercita os 4 casos que o analyzer precisa distinguir:
  agentE → explorer-first limpo (default→adopt explorer→adopt worker), 0 spam;
  agentS → adopt-spam (adota worker e RE-adota 2× já sendo worker);
  agentF → 1 adopt falho (failed_location) e depois 1 sucesso;
  agentD → nunca adota (fica default).
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from adoption import analyze  # noqa: E402

FIXTURE = Path(__file__).resolve().parent / "fixtures" / "synthetic_adopt_A"


def main():
    out = analyze(FIXTURE)
    assert out is not None, "fixture não carregou"
    s = out["summary"]
    a = out["agents"]

    # ----- agregados -----
    assert s["agents_total"] == 4, s
    assert s["workers_adopted"] == 3, s            # E, S, F viram worker; D não
    assert s["explorer_first_path"] == 1, s        # só E passou por explorer
    assert s["total_readoptions"] == 2, s          # só S, 2×
    assert s["total_failed_adopts"] == 1, s        # só F, 1×
    assert s["time_to_first_adoption_min"] == 1, s  # S adota worker no step 1
    assert s["time_to_first_adoption_max"] == 3, s  # E adota worker no step 3

    # ----- por agente -----
    assert a["agentE"]["explorer_first_ok"] is True
    assert a["agentE"]["passed_explorer"] is True
    assert a["agentE"]["adopt_success"] == 2       # explorer + worker
    assert a["agentE"]["readoptions"] == 0
    assert a["agentE"]["first_worker_adopt_step"] == 3

    assert a["agentS"]["readoptions"] == 2         # adopt-spam detectado
    assert a["agentS"]["adopt_attempts"] == 3
    assert a["agentS"]["explorer_first_ok"] is False

    assert a["agentF"]["adopt_failed"] == 1        # failed_location contado
    assert a["agentF"]["adopt_success"] == 1
    assert a["agentF"]["first_worker_adopt_step"] == 2
    assert a["agentF"]["adopt_fail_reasons"] == {"failed_location": 1}
    assert s["failed_adopt_reasons"] == {"failed_location": 1}

    assert a["agentD"]["is_worker"] is False
    assert a["agentD"]["adopt_attempts"] == 0

    print("OK — adoption.py: todas as asserções passaram contra a fixture sintética.")


if __name__ == "__main__":
    main()
