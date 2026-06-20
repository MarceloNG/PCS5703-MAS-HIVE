## Residual Review Findings

Fonte: ce-code-review run `20260620-095152-0699746a` — branch `feat/sc-50-52-objetividade-submit` → squash merge em `main` (2026-06-20).

Findings aplicados no squash merge (F2, F3, F4, F5, F7, F8). Findings abaixo ficaram **deferred** por requerem verificação em sim ou cobertura de teste adicional.

---

### P1 — collected_block não abolido em finalize_task (F1)

**Arquivo:** `src/agt/hive_agent.asl:169`

Adicionar `.abolish(collected_block(_));` em `!finalize_task` — impede belief orfão que dispara U3 na task seguinte (rotação thrash 1095 rotates/299 steps observada).

**Pré-requisito:** F7 (known_task guard no incompatível) já aplicado. Testar em sim isolada após adicionar a linha — a tentativa anterior sem F7 gerou score=0.

**Caminho de validação:** `OfficialRolesConfig.json` 300 steps; esperar score ≥ 40 e rotações totais < 300.

---

### P1 — prealign_fails abort path sem cobertura de teste (F6)

**Arquivo:** `src/agt/common/connect_protocol.asl:156`

Path de abort após 3 falhas de rotate no dispenser (guard exausto) não é exercitado por nenhum cenário. Criar variante de `06c-collect-rotate` com dispenser posicionado de forma que todas as 4 rotações falhem (ex.: bloco incompatível, forma L).

**Sugestão:** `conf/scenarios/06c-prealign-abort.json` — dispenser com task multi-bloco em ângulo que nenhuma rotação alinha; assert submits_ok=0.

---

### P2 — prealign_fails não resetado no re-collect pós-submit (F9)

**Arquivo:** `src/agt/common/connect_protocol.asl:~385`

Path de re-coleta após submit bem-sucedido (linha 399, `!collect_block(BType)`) não executa `.abolish(prealign_fails(TaskName, _))`. Com 2 falhas acumuladas, o agent entra no re-collect com guard quase esgotado.

**Fix:** Adicionar `.abolish(prealign_fails(TaskName, _))` no handler de submit success re-collect (linhas 382-400), junto com os demais .abolish já presentes.

---

### P2 — DoD de 06c-collect-rotate usa submits_ok, não zone-rotation gate (F10)

**Arquivo:** `conf/scenarios/06c-collect-rotate.json:56`

O assert `{ metric: "submits_ok", min: 1 }` não verifica que as rotações ocorreram no dispenser (U3) e não na zona. O bounded fallback in-zone também passaria esse assert.

**Fix:** Após cada run de 06c-collect-rotate, executar:
```bash
python3 .claude/skills/run-hive/analyzers/submit_strategy.py <replay> --check --max-zone-rotations 0
```
Ou adicionar ao `regression.sh` a chamada do submit_strategy.py para cenários `*collect-rotate*`.

---

*Gerado por ce-code-review (mode:agent) — run 20260620-095152-0699746a — 2026-06-20.*
