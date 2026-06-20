#!/usr/bin/env python3
"""
Teste SIM-FREE das métricas de POSIÇÃO e do `assert` em lista (issue #27).

Sem pytest: rode direto — `python3 test_assert_metric.py` — sai 0 se OK, !=0 se falhar.
Constrói `results` sintéticos na forma de replay_analyze.analyze (cada agente tem `rows`
= lista de (step, x, y, gdist, rzdist, action, result, role, attached)) e verifica:
  exited_region  → último step com posição DENTRO da box;
  entered_region → nº de agentes que entraram na box;
  evaluate(lista) → gate dual (todas as checagens precisam passar).
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import assert_metric as m  # noqa: E402


def _rows(coords):
    """coords = lista de (step, x, y) → rows no formato do analyzer."""
    return [(s, x, y, None, None, "move", "success", "default", 0) for (s, x, y) in coords]


def main():
    box = [5, 2, 10, 11]

    # agente entra na box (y=4) até o step 12, sai (y=14) e fica fora → escape no 12
    escape = {"agentA1": {"rows": _rows([(s, 7, 4 if s <= 12 else 14) for s in range(1, 25)])}}
    assert m.m_exited_region(escape, {"region": box}) == (12, m.m_exited_region(escape, {"region": box})[1]), \
        m.m_exited_region(escape, {"region": box})
    assert m.m_exited_region(escape, {"region": box})[0] == 12
    assert m.m_entered_region(escape, {"region": box})[0] == 1   # entrou (estava dentro)

    # agente fora o tempo todo → nunca entrou (prova de evitação)
    avoid = {"agentA1": {"rows": _rows([(s, 7, 14) for s in range(1, 25)])}}
    assert m.m_entered_region(avoid, {"region": [6, 2, 9, 5]})[0] == 0

    # box sem region → erro de uso
    try:
        m.m_exited_region(escape, {})
        raise AssertionError("deveria exigir region")
    except SystemExit:
        pass

    # assert em LISTA: gate dual passa (saiu no 12 <= 30 E max_stuck 2 <= 5)
    res_ok = {"agentA1": {"rows": escape["agentA1"]["rows"], "max_stuck_run": 2}}
    v_ok = m.evaluate(
        [{"metric": "exited_region", "max": 30, "region": box},
         {"metric": "max_stuck", "max": 5}], res_ok)
    assert v_ok["pass"] is True, v_ok

    # assert em LISTA: falha se UMA checagem falha (preso o tempo todo → max_stuck alto)
    stuck_rows = _rows([(s, 7, 4) for s in range(1, 25)])
    stuck_rows = [(r[0], r[1], r[2], r[3], r[4], r[5], "failed_path", r[7], r[8]) for r in stuck_rows]
    res_bad = {"agentA1": {"rows": stuck_rows, "max_stuck_run": 24}}
    v_bad = m.evaluate(
        [{"metric": "exited_region", "max": 30, "region": box},
         {"metric": "max_stuck", "max": 5}], res_bad)
    assert v_bad["pass"] is False, v_bad
    # a sub-checagem de max_stuck é a que reprova
    descs = [c["desc"] for c in v_bad["checks"] if not c["ok"]]
    assert any("max_stuck" in d for d in descs), v_bad

    # objeto único (retrocompat) ainda funciona
    v_single = m.evaluate({"metric": "entered_region", "max": 0, "region": [6, 2, 9, 5]}, avoid)
    assert v_single["pass"] is True, v_single

    print("OK — test_assert_metric: métricas de posição + assert em lista verdes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
