---
title: "fix: desbloquear coleta no oficial — medir 300 steps + diferir SELF-ASSIGN"
date: 2026-06-18
type: fix
status: active
origin: none
---

# fix: desbloquear coleta no oficial — medir 300 steps + diferir SELF-ASSIGN

## Summary

Workers adotam `worker` corretamente mas nunca executam `request`/`attach` na config oficial
(70×70, `absolutePosition:false`). Causa principal: SELF-ASSIGN dispara no step 4, antes de
qualquer exploração, enviando todos os 15 agentes para `searching_dispenser(b0)`. No grid 70×70
com `failed_path` 40-60%, agentes precisam de mais steps para encontrar um dispenser pessoalmente.

Este plano:
1. Mede se 300 steps já são suficientes para pelo menos 1 submit (custo zero de código).
2. **Se U1 mostrar 0 submits:** adiciona guarda `N > 30` ao SELF-ASSIGN e ao plano (c) de
   `role_adoption.asl` para que os agentes explorem antes de assumir tarefas.

**Nota:** `!pick_escape` já usa tiebreak horário (n→e→s→w, commit `a8e82ad`) — sem código
adicional necessário para handedness.

---

## Problem Frame

| Sintoma | Evidência |
|---------|-----------|
| Workers com `role(worker)` só fazem `move`/`no_action` | Replay OfficialRolesConfig 150 steps |
| `[SELF] Step 4: Auto-assigned task3 type=b0` para 6-7 agentes | Log da última run |
| `[COL] Nenhum dispenser b0 conhecido, explorando...` no step 4 | Log — sem exploração ainda |
| 0 `request`/`attach`/`submit` em 150 e 300 steps | Replay analysis |

**Raiz causal:**

```
SELF-ASSIGN step 4 (N mod 7)==4
    → !collect_block(b0)
    → get_nearest_dispenser → DX=-1 (nenhum visto ainda)
    → searching_dispenser(b0)
    → !do_explore (a cada 10 steps re-checa)
    → grid 70×70 + failed_path 40-60%
    → worker não acha dispenser em 150 steps
    → score=0
```

---

## Requirements

- R1: Pelo menos 1 `submit` confirmado em replay com `conf/OfficialRolesConfig.json` em ≤300 steps.
- R2: `failed_path` medido antes e depois de cada mudança de código.
- R3: Nenhuma regressão na config `IsolationRolesConfig.json` (baseline: score ≥10, boot 2026-06-17).

---

## Key Technical Decisions

### KTD1: Medir antes de mudar código (STRATEGY.md §Abordagem)

O step 4 do SELF-ASSIGN enviou todos ao `searching_dispenser` — mas com 300 steps, workers que
adotam em steps 30-155 têm 145-270 steps de exploração pessoal. Cobertura estimada: 60-80 moves
bem-sucedidos × 25 células por posição = ~1500-2000 células de um grid de 4900. Se há ≥3
dispensers b0, probabilidade de encontrar 1 é > 50%.

Se U1 mostrar ≥1 submit → R1 satisfeita sem código. Se 0 submits → implementar U2.

### KTD2: Threshold N > 30 para SELF-ASSIGN

`(N mod 7) == 4` dispara nos steps 4, 11, 18, 25, 32, 39... Adicionar `& N > 30` faz o primeiro
disparo no step 32, após 30 steps de exploração livre. Com `failed_path` ~50%, são ~15 moves
bem-sucedidos = ~375 células vistas. Melhor que 0 no step 4.

**Alternativa considerada:** `known_dispenser(_, _, _)` como trigger (só self-assigm após ver
dispenser). Mais preciso, mas requer que a crença `known_dispenser` seja populada pelo artefato
SharedMap — verificar se `focused` está ativo para esse obs-property antes de usar.

**Alternativa considerada:** N > 60 (dispara no step 67). Dobra o tempo de exploração; tradeoff:
workers com deadline curto podem perder a tarefa. 300-step runs podem revelar o threshold ótimo.

### KTD3: Handedness já implementada

`!pick_escape` usa n→e→s→w (sentido horário) como tiebreak, implementado em `a8e82ad`.
O teste combinado (heading-balanceado + handedness) mostrou delta neutro. Handedness isolada
não foi testada separadamente; o código já a implementa. Nenhuma mudança necessária aqui.

---

## Implementation Units

### U1. Medir baseline com 300 steps

**Goal:** Determinar se o tempo de simulação é o fator limitante; estabelecer métricas de
referência antes de qualquer mudança de código.

**Requirements:** R1, R2

**Dependencies:** nenhuma

**Files:** nenhum (run de medição, sem código)

**Approach:**
Executar a skill `run-hive` com 300 steps na config oficial. Analisar replay e results JSON para:
- Número de `submit` (DoD principal)
- Step do primeiro `request` bem-sucedido por agente
- `failed_path` rate (moves falhos / total moves) por agente — comparável com medições anteriores
- Quantos workers (`role(worker)`) existem nos steps 150-300
- Steps em que cada worker encontra o primeiro dispenser b0 (via `request` ou `[COL] Indo coletar`)

**Critério de gate:**
- ≥1 submit → R1 satisfeita sem código, registrar o resultado e encerrar.
- 0 submits → implementar U2.

**Test scenarios:**
- Run completo 300 steps com `conf/OfficialRolesConfig.json`
- Verificar `massim_2022/server/results/*.json` para `score > 0`
- Verificar `massim_2022/server/replays/` para ação `submit` no replay

**Verification:** `results/*.json` mostra `score > 0` OU o analisador de replay extrai pelo menos
1 ação `submit` com `result: success`.

---

### U2. Diferir SELF-ASSIGN para após exploração inicial

**Goal:** Impedir que agentes entrem em `searching_dispenser` no step 4 antes de qualquer
exploração. Dar ~30 steps de exploração livre antes do primeiro auto-assign.

**Requirements:** R1, R2, R3

**Dependencies:** U1 (só implementar se U1 mostrar 0 submits)

**Files:**
- `src/agt/common/connect_protocol.asl` (modificar linha 73-74)
- `src/agt/common/role_adoption.asl` (modificar plano (c), linhas 78-100)

**Approach:**
Adicionar a condição `& N > 30` ao contexto do SELF-ASSIGN. O plano inteiro do SELF-ASSIGN
(linhas 73-108) tem estas condições no contexto:

```prolog
// ANTES
+step(N)
    : (N mod 7) == 4
      & not my_active_task(_, _) & not collecting(_, _, _)
      ...

// DEPOIS — adicionar guard de step mínimo
+step(N)
    : (N mod 7) == 4
      & N > 30
      & not my_active_task(_, _) & not collecting(_, _, _)
      ...
```

O próximo disparo após `N > 30` será no step 32. Com `failed_path` ~50%, 32 steps = ~16 moves
bem-sucedidos. Considerar elevar para `N > 60` se a medição de U1 mostrar que 32 steps ainda é
insuficiente.

**Padrões a seguir:**
- Outras guards em `connect_protocol.asl` combinam condições com `&` (ex.: `& step(CS)`) — mesmo padrão.
- Não alterar a lógica do corpo do SELF-ASSIGN nem do plano (c).

**Atenção — plano (c) em `role_adoption.asl` burla o guard de N>30 em SELF-ASSIGN:**
Com `N > 30` em SELF-ASSIGN, workers que adotam `worker` antes do próximo `(N mod 7)==4` ficam
idle (sem `my_active_task`). Nesse intervalo, o plano (c) dispara imediatamente e define
`searching_dispenser` — anulando a janela de exploração que U2 tenta criar. A correção cobre
os dois caminhos de auto-atribuição:

```prolog
// role_adoption.asl — plano (c) DEPOIS: adicionar guard de step mínimo
+step(N)
    : can_score_role & N > 30                           // ← guard adicionado
      & not my_active_task(_, _) & not collecting(_, _, _)
      & not solo_mode(_) & not searching_dispenser(_)
      ...
```

**Test scenarios:**
- Com `N > 30`: `[SELF]` não aparece nos steps 4, 11, 18, 25 do log.
- Com `N > 30`: `[SELF]` aparece no step 32 (ou 39 se 32 não tiver condições satisfeitas).
- `failed_path` rate antes vs depois: medir se houve redução ou estabilidade.
- Config `IsolationRolesConfig.json`: ainda pontua (sem regressão — no isolamento os steps são
  suficientes mesmo com o delay de 30).
- Replay `OfficialRolesConfig.json` 300 steps: verificar se agentes que self-assignam no step 32+
  têm `searching_dispenser` menos tempo ou encontram dispensers mais rápido.

**Verification:** 
- Log não mostra `[SELF] Step 4` nem `[SELF] Step 11`.
- Log mostra `[SELF] Step 32` (ou similar ≥30).
- Log não mostra `[ROLE] Step N: Worker idle — auto-coleta` com N < 30.
- `IsolationRolesConfig`: score ≥10 (sem regressão; baseline 10, boot 2026-06-17).
- Medir `failed_path` antes/depois para confirmar que a mudança não piorou navegação.

---

## Scope Boundaries

### Fora do escopo
- U9 (fusão de mapas cross-frame) — não resolve score=0 para single-block; downstream.
- Heading-balanceado — testado, delta neutro; mantido mas sem novas mudanças.
- Handedness no escape — já implementada em `!pick_escape` (n→e→s→w), sem mudanças.
- Fix do `failed_path` via A* / footprint — P1 de navegação, separado.
- Multi-block tasks — requer U9 para frame compartilhado; downstream da coleta solo.

### Deferred to Follow-Up Work
- Threshold ótimo do SELF-ASSIGN (`N > 30` vs `N > 60`) — a ser ajustado empiricamente
  com base nas medições de U1 e pós-U2.
- Trigger baseado em `known_dispenser(_, _, _)` — alternativa mais principiada; avaliar
  se o threshold fixo `N > 30` for insuficiente.

---

## Risks & Dependencies

| Risco | Probabilidade | Mitigação |
|-------|---------------|-----------|
| 300 steps ainda insuficientes (0 submits) | Média | Implementar U2 imediatamente; aumentar threshold se necessário |
| Regressão em IsolationRolesConfig | Baixa | `N > 30` não afeta config de 300 steps onde workers adotam em steps 30-80 e SELF-ASSIGN no step 32 ainda funciona |
| SELF-ASSIGN `N > 30` conflita com plano (c) de role_adoption.asl | Baixa | Plano (c) só dispara com `can_score_role & not my_active_task` — sem sobreposição com SELF-ASSIGN em non-workers |

---

## Sources & Research

- `docs/backlog.md` §"Fase C — achados do boot" — sub-itens Fase C, gates, DoD
- `docs/backlog.md` §"Parking lot" — dispersão + handedness, gate da medição
- `docs/solutions/architecture-patterns/cross-frame-sharedmap-breaks-official-config.md`
  — por que single-block não precisa de U9
- `docs/solutions/logic-errors/astar-livelock-teammate-unaware-planner.md`
  — contexto do livelock de navegação (problema ortogonal)
