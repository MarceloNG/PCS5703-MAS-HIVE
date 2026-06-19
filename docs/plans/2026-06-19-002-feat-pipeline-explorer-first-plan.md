---
title: "feat: pipeline P2 — explorer-first e retry de goal-zone"
type: feat
status: active
date: 2026-06-19
origin: null
---

# feat: pipeline P2 — explorer-first e retry de goal-zone

## Summary

Implementar as mudanças necessárias para obter ≥1 submit confirmado em `OfficialRolesConfig.json`.
O gargalo principal é o **alcance de role-zone**: apenas ~4/15 agentes adotavam worker em 300 steps
no cenário oficial (70×70 cave, 3 role-zones, speed=1 como default). A solução principal é
**explorer-first** — adotar `explorer` (speed=3, vision=7) na primeira role-zone, cobrir o mapa 3×
mais rápido, e adotar `worker` na próxima role-zone encontrada. Complementar: retry periódico de
goal-zone quando `pending_submit` ativo mas sem destino.

Validação em dois estágios: primeiro `conf/IsolationRolesConfig.json` (40×40,
`absolutePosition:true`, seed=17) para isolar a variável adoção; depois `OfficialRolesConfig.json`
como gate final.

---

## Problem Frame

Prioridade 1 (flat workers) entregou: todos os 15 agentes incluem `role_adoption.asl` e tentam
adotar worker. Zero `failed_role` de squad_leader/sentinel. Mas 0 submits no oficial porque:

1. **Alcance fraco de role-zone (b):** 3 role-zones em 70×70 cave. Speed=1 como `default` +
   `failed_path` 40-60% → 4/15 alcançam em 300 steps. Agentes sem worker = score 0.
2. **Transição pós-adoção para goal-zone (c):** `plan(c)` auto-inicia coleta. Mas quando
   `collected_block` dispara e nenhuma goal-zone é conhecida, `pending_submit` fica ativo sem
   `has_destination`. A navegação cai para exploração (correto), mas o retry de goal-zone não é
   periódico — se a goal-zone for descoberta durante a exploração, o agente não reaproveita.
3. **MOISE+ U4 (d):** `organization.asl` já trata o `discharge` via `+my_role(_)` — mas o path
   explorer→worker precisa verificação (dois `+my_role` percepts: um para `explorer`, um para
   `worker`; o discharge precisa aguardar o `worker`).

O sub-item **(a) adopt-spam** está resolvido: `can_score_role :- role(worker)` usa o percept cru e
para a re-adoção. A nova fase explorer não cria spam porque `can_score_role` é falso para explorer
(sem `submit`) → `!ensure_worker_role` continua gerenciando a transição.

---

## Requirements

- **R1** — ≥1 `submit` confirmado em replay com `conf/OfficialRolesConfig.json`.
- **R2** — ≥10/15 agentes adotam `worker` em 300 steps (OfficialRolesConfig).
- **R3** — Nenhum `failed_role` em replay (gate de re-adoção mantido).
- **R4** — Pipeline completo: agent adota worker → coleta → navega a goal-zone → submete.
- **R5** — MOISE+ `worker_role_adopted` descarregado corretamente para o path explorer→worker.
- **R6** — Teste primário em `IsolationRolesConfig.json` (isolamento) antes do oficial.

---

## Key Technical Decisions

**KTD1 — Explorer-first (não direct-worker)**

Decisão: adotar `explorer` na primeira role-zone, cobrir o mapa com speed=3/vision=7, adotar
`worker` na próxima role-zone encontrada.

Razão: speed=3 move 3 células por step (vs 1 do `default`). Após adotar explorer na role-zone #1,
o agente navega à role-zone #2 3× mais rápido. Vision=7 (vs 5) aumenta cobertura de mapa por step
→ detecta role-zones, dispensers e goal-zones de mais longe. No cenário oficial (3 role-zones/70×70
cave), o gargalo é a navegação lenta como default; o explorer elimina esse custo para a segunda
adoção. Referência: Blup (warm-up MAPC 2022, 2º geral) usava `explorer` exatamente por speed=3 de
cobertura/alcance.

Trade-off: 2 steps em role-zone em vez de 1 (adopt explorer + adopt worker). Custo de 1 step por
agente, negligível vs. o ganho de velocidade após o adopt.

Nota: `can_score_role` é falso para `explorer` (não tem `submit`) → `!ensure_worker_role` continua
gerenciando a transição step-a-step, sem spam.

**KTD2 — Explorer usa `!seek_role_zone` (beeline ou explore)**

Decisão: quando `role(explorer) & not roleZone(0,0)`, chamar `!seek_role_zone(MX, MY)` (o mesma
lógica que o `default` usa para achar role-zone).

Razão: `!seek_role_zone` já implementa "beeline se conhece role-zone, explora se não conhece". O
speed=3 do explorer se aplica automaticamente — a ação `move(Dir)` ao speed=3 move 3 células.
Reutiliza o mecanismo de dispersão (`rz_disperse_until`) sem duplicação de lógica.

**KTD3 — Validar em IsolationRolesConfig antes do oficial**

Decisão: gate de qualidade em `conf/IsolationRolesConfig.json` (40×40, `absolutePosition:true`,
seed=17, roles restritos) antes de rodar `OfficialRolesConfig.json`.

Razão: o oficial tem 3 variáveis acopladas (adoção + navegação 70×70 + cross-frame sem U9). O
IsolationConfig isola a variável adoção em ~3 minutos vs ~30 minutos do oficial. Se ≥1 submit no
isolation → lógica ASL de adoção+coleta está correta; se não → bug na camada de adoção. Formaliza
a filosofia da STRATEGY.md ("medir → mudar em isolamento").

**Limitação do gate (doc-review):** `IsolationRolesConfig` usa `absolutePosition:true`, que elimina
o drift de dead-reckoning. Um pass no IsolationConfig valida a lógica de controle ASL
(default→explorer→worker→collect→submit), mas NÃO garante que a mesma sequência funcione no oficial
(`absolutePosition:false`), onde a role-zone lembrada pode ter drift e o explorer pode navegar para
uma posição errada. O fallback de `!seek_role_zone` (re-explora se chega na posição lembrada sem
percept `roleZone(0,0)`) mitiga parcialmente — mas adiciona latência que pode consumir o ganho de
speed=3. Este risco é rastreado em Risks & Dependencies.

**KTD4 — Retry periódico de goal-zone (não exploração livre sem recheck)**

Decisão: adicionar handler em `connect_protocol.asl` que reexecuta `get_nearest_goal_zone` quando
`pending_submit` ativo e `not has_destination` a cada 15 steps (N mod 15 == 0).

Razão: o path atual define `pending_submit` e verifica goal-zone naquele momento. Se nenhuma é
conhecida, o agente explora corretamente (navigation fallback). Mas quando descobre uma goal-zone
durante a exploração, só reaproveita o mapa no PRÓXIMO `collected_block` ou por acaso ao chegar
perto. O retry periódico fecha esse gap sem mudar a arquitetura de navegação.

---

## Scope Boundaries

**In scope:**
- Modificação de `!ensure_worker_role` em `role_adoption.asl` (U1) — IMPLEMENTAÇÃO
- Verificação de handlers existentes de goal-zone em `connect_protocol.asl` (U2) — VERIFICAÇÃO
- Verificação do elo MOISE+ U4 para path explorer→worker (U3) — VERIFICAÇÃO
- Atualização de `STRATEGY.md` para formalizar cenário focado antes do oficial (U4) — DOCUMENTAÇÃO

**Out of scope (Prioridade 3+):**
- U9 — fusão de mapas cross-agente (multi-block coordenado bloqueado por cross-frame)
- Adoção de `constructor` (Prioridade 4)
- Múltiplos blocos por task (depende de U9)
- Estratégia de leilão avançada (Track 2)

### Deferred to Follow-Up Work

- Timer explícito de explorer phase (N steps max como explorer): não necessário dado o
  `!seek_role_zone` como fallback — o agente acha a role-zone pelo mapa ou explorando.
- IsolationConfig com `absolutePosition:false` para testes mais próximos do oficial:
  deferido para quando U9 estiver no pipeline.

---

## High-Level Technical Design

Estado de máquina de `!ensure_worker_role` com explorer-first:

```
[default] ──roleZone(0,0)──► adopt(explorer) ──► [explorer]
[explorer] ──roleZone(0,0)──► adopt(worker) ──► [worker] ── can_score_role=true ──► plan(c)
[explorer] ──not roleZone──► !seek_role_zone (speed=3) ──► (explora/beelina) ──► roleZone(0,0) ──►
[default] ──not roleZone──► !seek_role_zone (speed=1) ──► (explora/beelina) ──► roleZone(0,0) ──►
```

Contexto de `can_score_role`:
- `default` → false → `!ensure_worker_role` dispara todo step
- `explorer` → false → `!ensure_worker_role` dispara todo step (gerir transição)
- `worker` → true → `!ensure_worker_role` retorna `true` (não age)

Path completo de adoção a submit:
```
[step N]   default, on role-zone  → action("adopt(explorer)")
[step N+1] explorer, on role-zone → action("adopt(worker)")   // speed=3 agora, mas ainda na mesma role-zone
[step N+2] worker, can_score_role=true → plan(c) dispara (se idle)
[step N+x] worker collecta bloco → collected_block → pending_submit
[step N+y] goal-zone encontrada (retry ou explored) → has_destination → navega
[step N+z] on goal-zone → submit(task)
```

---

## Implementation Units

### U1. Explorer-first em `role_adoption.asl`

**Goal:** Modificar `!ensure_worker_role` para a transição em dois passos: `default`→`explorer`→`worker`.

**Requirements:** R1, R2, R3

**Dependencies:** — (standalone)

**Files:**
- Modificar: `src/agt/common/role_adoption.asl`

**Approach:**

Inserir dois novos clauses no `!ensure_worker_role` APÓS o clause `can_score_role` (linha 113) e
ANTES do clause `roleZone(0,0)` existente (linha 117), em ordem de prioridade:

1. `role(explorer) & roleZone(0,0)` → `action("adopt(worker)")` — na role-zone como explorer, adota worker
2. `role(explorer) & my_pos(MX, MY)` → `!seek_role_zone(MX, MY)` — fora da role-zone como explorer, usa speed=3 para buscar/explorar

Modificar o clause existente para o caso `roleZone(0,0)` sem ser explorer:
- Antes: `action("adopt(worker)")` 
- Depois: `action("adopt(explorer)")` — adota explorer, não worker diretamente

O dispatch via `!seek_role_zone` para o explorer é idêntico ao do `default` — reutiliza o mecanismo
de beeline+explore+dispersão existente. A diferença é que o explorer o executa a speed=3.

Atualizar o comentário do `can_score_role` para explicar que `explorer` é estado intermediário
(não pontua mas está no path de adoção).

**Patterns to follow:** Clauses existentes de `!ensure_worker_role` em `src/agt/common/role_adoption.asl:113-131`.

**Test scenarios:**
- `role(explorer) & roleZone(0,0)` → deve emitir `action("adopt(worker)")`, não `action("adopt(explorer)")`
- `role(explorer) & not roleZone(0,0) & role_zone_known` → deve beelinar para role-zone (emit move)
- `role(explorer) & not roleZone(0,0) & not role_zone_known` → deve explorar (emit move)
- `default & roleZone(0,0)` → deve emitir `action("adopt(explorer)")` (não worker diretamente)
- `worker` → `can_score_role` true → `!ensure_worker_role` retorna `true` (não age)
- Dois steps consecutivos na mesma role-zone: step N = adopt(explorer), step N+1 = adopt(worker)
- Adopt-spam: após `role(worker)`, `!ensure_worker_role` não emite `adopt` novamente

**Verification:** Replay de `IsolationRolesConfig.json` mostra agentes com sequência
`adopt(explorer)` → `adopt(worker)` (dois adopt consecutivos); zero `failed_role`; zero re-adoptions
após virar worker.

---

### U2. Verificar handlers existentes de `pending_submit` sem goal-zone

**Goal:** Confirmar que os handlers existentes em `connect_protocol.asl` já cobrem o caso
`pending_submit` ativo sem `has_destination`, e que o caminho de descoberta de goal-zone durante
exploração está funcionando corretamente após o commit `8f9d9c4`.

**Requirements:** R4

**Dependencies:** — (standalone)

**Files:**
- Verificar (sem mudança esperada): `src/agt/common/connect_protocol.asl`
- Modificar se houver bug: `src/agt/common/connect_protocol.asl`

**Approach:**

**Descoberta durante doc-review:** Os handlers em `connect_protocol.asl:319-372` já cobrem o gap
descrito originalmente:

- `lines 319-356`: Handler `pending_submit & has_destination` → recalcula goal-zone a cada 15 steps
  quando já tem destino (pode haver goal-zone melhor conhecida).
- `lines 358-372`: Handler `pending_submit & not goalZone(0,0)` (sem `has_destination` no guard) →
  chama `get_nearest_goal_zone` TODOS os steps e faz `!do_explore` quando nenhuma é conhecida.

O gap original (goal-zone descoberta durante exploração não sendo aproveitada) já está resolvido pelo
handler 358-372 que dispara todo step enquanto `pending_submit` ativo sem ver goalZone diretamente.

**Verificação da implementação existente:**
1. Confirmar via replay `IsolationRolesConfig.json` com U1 implementado que agentes com
   `pending_submit` eventualmente alcançam goal-zones e executam `submit`.
2. Se agentes ficarem presos com `pending_submit` sem navegar a goal-zone → investigar se handler
   358 está disparando (verificar ordem de handlers no arquivo vs SELF-ASSIGN).
3. Se houver bug, corrigir o handler existente; NÃO adicionar handler duplicado.

**Test scenarios:**
- `pending_submit(T) & not goalZone(0,0) & not has_destination` → handler 358 deve disparar todo
  step e chamar `get_nearest_goal_zone`; se encontrada, setar `has_destination`; se não, explorar
- `pending_submit(T) & has_destination(DX,DY)` → handler 321 deve recalcular goal-zone a cada
  15 steps e atualizar destino se necessário

**Verification:** Replay mostra agentes com `pending_submit` eventualmente alcançando goal-zones e
executando `submit`. Confirmar que nenhum agente fica com `pending_submit` ativo por mais de
50 steps sem transição para goal-zone (dada exploração natural).

---

### U3. Verificação do elo MOISE+ U4 para path explorer→worker

**Goal:** Confirmar que `goalAchieved(worker_role_adopted)` é descarregado corretamente quando o
agente transita por `explorer` antes de `worker` (dois `+my_role` percepts no mesmo step ou em
steps distintos).

**Requirements:** R5

**Dependencies:** U1

**Files:**
- Verificar (sem mudança esperada): `src/agt/common/organization.asl`
- Modificar se necessário: `src/agt/common/organization.asl`

**Approach:**

O handler existente é:
```prolog
+my_role(_)
    : adopt_duty(Sch, W) & can_score_role
   <- goalAchieved(worker_role_adopted)...
```

Para o path explorer→worker:
- `+my_role(explorer)` dispara com `adopt_duty` ativo, mas `can_score_role` é **falso** para explorer
  → handler NÃO descarrega (correto — espera worker)
- `+my_role(worker)` dispara com `adopt_duty` ativo e `can_score_role` **verdadeiro**
  → handler descarrega `worker_role_adopted` (correto)

Análise indica nenhuma mudança necessária. A verificação é:
1. Rodar `IsolationRolesConfig.json` com o U1 implementado
2. No log de agentes, confirmar `[ORG] Adotei role de pontuação — descarregando worker_role_adopted`
   após o segundo `adopt` (worker), não após o primeiro (explorer)
3. Se o discharge não aparecer → investigar se `adopt_duty` ainda está ativo no momento de `+my_role(worker)`

Se falhar: adicionar log em `+my_role(explorer)` para confirmar que `can_score_role` é falso naquele
momento. Verificar se algum handler limpa `adopt_duty` prematuramente.

**Patterns to follow:** `src/agt/common/organization.asl:37-41` (handler existente).

**Test scenarios:**
- Path completo explorer→worker: `adopt_duty` deve estar ativo na adoção de worker
- `+my_role(explorer)` com `adopt_duty` → `can_score_role` falso → discharge NÃO dispara
- `+my_role(worker)` com `adopt_duty` → `can_score_role` verdadeiro → discharge dispara
- Após discharge: `adopt_duty` abolido, sem re-discharge

**Verification:** Log mostra `[ORG] Adotei role de pontuação` exatamente uma vez por agente que
virou worker. Nenhuma mensagem de discharge no momento de `adopt(explorer)`.

---

### U4. STRATEGY.md — cenário focado antes do oficial

**Goal:** Formalizar na STRATEGY.md a prática de validar em config focada (`IsolationRolesConfig.json`)
antes de rodar a config oficial cara, como parte da abordagem "medir → mudar em isolamento".

**Requirements:** R6

**Dependencies:** —

**Files:**
- Modificar: `STRATEGY.md`

**Approach:**

Na seção **Track 1 (Medição & Validação)** ou equivalente, adicionar parágrafo explícito sobre a
hierarquia de configs de teste:

1. **Config focada (rápida, ~3 min):** `conf/IsolationRolesConfig.json` (40×40, `absolutePosition:true`,
   seed=17, roles restritos) — isola variável de adoção; confirma que pipeline coleta→submit funciona
   sem ruído de cross-frame/navegação 70×70.
2. **Config oficial (gate de score, ~30 min):** `conf/OfficialRolesConfig.json` (70×70, roles reais,
   300 steps) — gate final para score competitivo.
3. **Princípio:** antes de mudar a lógica de adoção/coleta, validar em `IsolationRolesConfig`. Só
   promover para `OfficialRolesConfig` quando a config focada confirmar ≥1 submit. Evita gastar
   30 min para descobrir bug que 3 min revelaria.

**Test scenarios:** none (documentation-only unit). Verificar que o texto é legível e não contradiz
a estratégia existente.

**Verification:** `STRATEGY.md` contém seção ou parágrafo explícito sobre hierarquia de configs de
teste com `IsolationRolesConfig` como primeiro gate.

---

## Risks & Dependencies

| Risco | Mitigação |
|-------|-----------|
| Explorer speed=3 com `failed_path` alto pode não trazer ganho real | Medição: comparar time-to-worker no replay isolation com/sem explorer-first — o backlog.md (linha 48) exige essa métrica antes de promover. IsolationRolesConfig é o laboratório para esse A/B. |
| Agent fica preso como explorer (nunca acha role-zone após adotar explorer) | `!seek_role_zone` já tem fallback de exploração; 3 role-zones em 40×40 são mais densas |
| MOISE+ U4 discharge dispara para `explorer` (falso positivo) | Análise mostra `can_score_role` é falso para explorer — sem risco; mas verificar (U3) |
| Explorer com bloco anexado tem speed=0 (paralisia) | `plan(c)` é guardado por `can_score_role=false` para explorer → coleta não inicia durante fase explorer. Validar no replay que nenhum agente entra na fase explorer com blocos anteriores. |
| IsolationConfig pass ≠ oficial pass (absolutePosition:true vs false) | DR drift pode fazer explorer beelinar para posição errada de role-zone; `!seek_role_zone` tem fallback de re-exploração mas adiciona latência. Risco mitigado, mas oficial exige validação independente. |

---

## System-Wide Impact

- **Todos os agentes** (collector, assembler, sentinel, squad_leader): afetados via `role_adoption.asl`
  (shared include). O path muda de default→worker para default→explorer→worker.
- **connect_protocol.asl**: handler de retry de goal-zone compartilhado por todos.
- **Sem regressão esperada em OfficialTestConfig.json**: `can_score_role` é verdadeiro para `default`
  na config dev (default permissivo com submit) → `!ensure_worker_role` retorna `true` imediatamente
  → sem adoção de explorer, sem mudança de comportamento na config dev.

---

## Sources & Research

- `docs/backlog.md` §Prioridades → tabela P2/PIPELINE e achados de boot (fase C sub-itens)
- `massim_2022/server/conf/sim/roles/standard.json` → `explorer: speed=[3,0], vision=7`
- `conf/OfficialRolesConfig.json` → 70×70 cave, 3 role-zones, 300 steps
- `conf/IsolationRolesConfig.json` → 40×40, absolutePosition:true, seed=17 (já existe)
- MAPC 2022 book (local/): Blup (warm-up, 2º) adotava `explorer` para speed=3
- `src/agt/common/role_adoption.asl` → `!ensure_worker_role`, `!seek_role_zone`, `can_score_role`
- `src/agt/common/connect_protocol.asl` → SELF-ASSIGN, submit pipeline
- `src/agt/common/organization.asl` → `+my_role(_)` + `goalAchieved(worker_role_adopted)`
