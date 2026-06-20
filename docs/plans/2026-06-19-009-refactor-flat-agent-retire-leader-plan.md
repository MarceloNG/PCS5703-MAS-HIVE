---
title: "refactor: agente flat único + aposentar o líder (Stance A completa, issue #38)"
type: refactor
status: active
date: 2026-06-19
issue: 38
sibling: 37
espinha: 36
supersedes: docs/plans/2026-06-19-001-feat-flat-workers-plan.md (KTD-1)
---

# refactor: agente flat único + aposentar o líder central (issue #38, Stance A completa)

## Summary

Colapsar os **4 tipos de agente** (`squad_leader`/`collector`/`assembler`/`sentinel`) em **um
único agente flat** capaz de adotar `worker`, **aposentando o líder central** (leilão/delegação),
e **achatar a camada MOISE+** para casar (um papel funcional, sem hierarquia de autoridade, `max ≥ 20`).
Torna verdes os três invariantes do teste do #37 e alinha o time à estrutura *flat + descentralizada*
que todos os times do contest usaram.

**De-risco por evidência (não no olho):** o que pontua hoje é single-block **solo**, disparado pelo
**auto-start do `role_adoption.asl`** (plano (c)), **sem mensagem do líder** — o líder nem participa
(o próprio plano (c) o exclui via `not my_role_type(squad_leader)`). O único papel não-redundante do
líder é o split+`connect` multi-bloco, **bloqueado na U9** de qualquer forma. A alocação multi-bloco,
quando a U9 destravar, vai para **Contract-Net descentralizado** (#22/#21), não para um chefe.

**Supersede:** este plano substitui o **KTD-1** do `docs/plans/2026-06-19-001-feat-flat-workers-plan.md`
(que manteve os tipos por risco/prazo). A decisão do dono (2026-06-19) é fazer o achatamento completo.

---

## Problem Frame

O HIVE é o único entre 6 times (5 do contest + warm-up) com **squad hierárquico centralizado** —
e é o que destoa. Custos concretos, já levantados no `docs/backlog.md` §Prioridade 1 e na espinha #36:

- **Nenhum time do contest usou líder/squad.** Organizadores (livro MAPC 2022): *"it is usually not
  helpful to come up with a centralized solution."* LI(A)RA (único Jason) usou agentes homogêneos +
  Contract-Net descentralizado.
- **Cardinalidade MOISE+ cap. o time em 19 < 20** (`squad_leader 4 + collector 8 + assembler 4 +
  sentinel 3`) → a org **nem admite** os 20 agentes do Sim1. (É a R1 do teste #37, hoje VERMELHA.)
- **`squad_leader`/`sentinel` geram `failed_role`** e o líder é gargalo + ponto único de falha.
- **O líder quase não é load-bearing hoje:** `role_adoption.asl` plano (c) (`src/agt/common/role_adoption.asl:81`)
  auto-inicia coleta solo sem o líder; os artefatos `task_board`/`squad_coordinator` são criados em
  padrão lookup-or-create por `assembler.asl` também (não só pelo líder).

## Requirements

- **R1 — #37 verde:** soma dos `max` do grupo `hive_team` ≥ 20; todo role declarado compromete `m_adopt`;
  integridade referencial (todo role em link/norma existe). Os 3 métodos de
  `src/test/java/hive/HiveOrgStructureTest.java` passam.
- **R2 — sem regressão de score:** o pipeline solo single-block continua pontuando com o time flat
  (rede: re-rodar o cenário de coleta solo mais curto e asseverar `submits_ok ≥ 1`).
- **R3 — boot flat sem `failed_role`:** os 15 agentes sobem como um único tipo, adotam `worker`, zero
  `failed_role` de tipos removidos (smoke sim curto).
- **R4 — coerência org:** sem hierarquia de autoridade; as normas que sobram referenciam só roles
  existentes; o elo de adoção (`m_adopt`/U4) permanece vivo.

---

## High-Level Technical Design

Antes → depois (estrutura de tipos + onde mora a coordenação):

```
ANTES                                   DEPOIS
4 tipos de agente:                      1 tipo: hive_agent.asl
  squad_leader.asl ── leilão central      (todos os 15+ agentes)
  collector.asl                         coordenação:
  assembler.asl                           - solo single-block: auto-start
  sentinel.asl                              (role_adoption plano c) — já existe
coordenação: LÍDER central                - multi-bloco: Contract-Net
  (eval_and_delegate/auction)               DESCENTRALIZADO → adiado #22/#21
org: hierarquia squad_leader→{col,asm}   org: 1 papel flat, sem links de
     max=19 (não cabe Sim1)                  autoridade, max≥20 (cabe Sim1)
```

O agente flat = **união dos módulos `common/` + setup (EISAccess por-agente + artefatos compartilhados
lookup-or-create) + os handlers reativos não-líder** (`+soloist_task`/`+do_collect`/`+collected_block`→connect,
hoje em collector/assembler) — **menos** a lógica de leilão/delegação do líder.

---

## Key Technical Decisions

- **KTD1 — Um `hive_agent.asl`, deletar os 4 tipos.** Os tipos são finos (só `my_role_type` + `start` +
  includes); o comportamento mora em `common/`. Consolidar num único `.asl` e apontar todos os 15 no
  `hive.jcm` para ele. Inclui `communication.asl` (connect) para preservar a capacidade multi-bloco futura.
- **KTD2 — Aposentar a lógica de líder, preservar os artefatos.** Dropar de vez `eval_and_delegate`,
  `delegate_collection_safe`, `quick_delegate`, `scan_and_delegate_tasks`, `finalize_task` (centralização).
  **Manter** `setup_task_board`/`setup_squad_coordinator` no `start` do agente flat (lookup-or-create) —
  o `mark_busy` do self-start ainda usa o SquadCoordinator, e o padrão já é multi-criador.
- **KTD3 — Manter os handlers reativos `+soloist_task`/`+do_collect`/`+collect_and_connect_task`.** Sem
  remetente (líder) ficam inertes hoje, mas são o ponto de entrada da alocação descentralizada futura
  (#22/#21). Custo zero mantê-los; re-derivá-los depois seria desperdício.
- **KTD4 — Org flat com papel neutro.** Um único role org (nome neutro tipo `field_agent`, **não** `worker`
  para não colidir com o role MAPC — ver Open Questions), `min`/`max` cobrindo ≥20, sem `<link>` de
  autoridade. Religar/abolir as normas `n_collect`/`n_assemble`/`n_submit`/`n_guard` (hoje citam
  collector/assembler/sentinel → quebrariam a R3) e manter uma norma de adoção (`n_adopt`) para o role flat.
- **KTD5 — Limpar a guarda do self-start.** `role_adoption.asl:83` tem `& not my_role_type(squad_leader)`;
  com o tipo único isso vira vacuamente verdadeiro — remover a exclusão para não depender de um tipo extinto.
- **KTD6 — Supersede do plano flat-workers mínimo.** O `2026-06-19-001` escolheu manter os tipos (KTD-1
  dele) por prazo/risco; esta passada faz o completo, com a regressão guardada por R2/R3 e o teste #37.

---

## Implementation Units

### U1. Achatar `hive_org.xml` (faz o #37 verde)

**Goal:** colapsar o grupo `hive_team` para um papel flat, `max ≥ 20`, sem links de autoridade, normas
religadas. R1/R2/R3 do `HiveOrgStructureTest` ficam verdes.

**Requirements:** R1, R4.

**Files:** `src/org/hive_org.xml` (modificar); `src/test/java/hive/HiveOrgStructureTest.java` (rede, não editar).

**Approach:** uma `<role>` flat em `role-definitions` e em `group-specification/roles` com `min`/`max`
somando ≥ 20; remover os `<link>`; religar as normas que referenciam roles extintos (abolir
`n_collect`/`n_assemble`/`n_submit`/`n_guard` ou remapeá-las ao role flat) e manter `n_adopt` no role flat;
os schemes (missions/goals) podem permanecer — só os vínculos role→mission (normas) mudam.

**Test scenarios:**
- `cardinalidadeDoTimeCabeOSim1`: soma dos `max` ≥ 20 → **PASS** (era FAIL).
- `todoRoleComprometeAdoptDoWorker`: role flat ∈ roles com `m_adopt` → **PASS**.
- `integridadeReferencialDeRoles`: nenhum role pendente em link/norma → **PASS**.

**Verification:** `~/tools/gradle-8.10/bin/gradle test` → `HiveOrgStructureTest` 3/3 verdes.

### U2. Criar `src/agt/hive_agent.asl` (o agente flat)

**Goal:** um `.asl` único que consolida o comportamento não-líder + o setup de conexão/artefatos.

**Requirements:** R2, R3.

**Dependencies:** —

**Files:** `src/agt/hive_agent.asl` (criar).

**Approach:** includes de todos os módulos `common/` (incl. `communication.asl`); `+!start` faz
`makeArtifact` do EISAccess (por-agente, crítico), `!setup_task_board`, `!setup_squad_coordinator`
(lookup-or-create) e o registro no dashboard; absorver os handlers reativos `+soloist_task`/`+do_collect`/
`+collected_block`/`+collect_and_connect_task` hoje em `collector.asl`/`assembler.asl`; **não** incluir
`eval_and_delegate`/auction/delegação. Definir `my_role_type(hive_agent)` (ou remover a dependência de
`my_role_type` onde possível).

**Patterns to follow:** `src/agt/collector.asl` (include set mais completo) + o bloco de setup de
`src/agt/assembler.asl` (`setup_task_board`/`setup_squad_coordinator`).

**Test scenarios:** coberto e2e por U4 (sem unit Java — é orquestração `.asl`, exercitada só no `gradle run`).
`Test expectation: none` no nível unit; a prova é o smoke sim de U4.

**Verification:** o agente parseia e sobe no `gradle run` (sem erro de parse → ver U4).

### U3. Rewire do `hive.jcm` + aposentar o líder

**Goal:** os 15 agentes usam `hive_agent.asl`; players da org mapeiam ao papel flat; tipos antigos saem.

**Requirements:** R2, R3, R4.

**Dependencies:** U1 (papel flat existe), U2 (agente flat existe).

**Files:** `hive.jcm` (modificar); deletar `src/agt/squad_leader.asl`, `src/agt/sentinel.asl`,
`src/agt/collector.asl`, `src/agt/assembler.asl`; `src/agt/common/role_adoption.asl` (KTD5 — limpar a guarda).

**Approach:** trocar as 15 linhas `agent connectionAN : <tipo>.asl` por `: hive_agent.asl`; no bloco
`organisation`, a lista `players` mapeia todos ao role flat; remover referências a schemes que dependiam de
roles extintos se necessário (manter `sch_adopt`; `sch_task` pode permanecer se as missions seguem válidas).
Remover o `& not my_role_type(squad_leader)` do plano (c) de `role_adoption.asl`.

**Test scenarios:** coberto por U4.

**Verification:** `gradle run` sobe 15 agentes do mesmo tipo sem erro de parse; org instancia sem role órfão.

### U4. Validação — regressão de score + boot flat

**Goal:** provar que aposentar o líder **não** regrediu o score e que o time flat sobe e adota.

**Requirements:** R2, R3.

**Dependencies:** U1, U2, U3.

**Files:** `conf/scenarios/06c-single-collect.json` (rede de regressão; reusar, não editar);
`conf/scenarios/00-smoke.json` (boot/adoção).

**Approach:** **a sim é cara — usar as mais curtas.** (1) Re-rodar o cenário de coleta solo mais curto
(`06c-single-collect`, que já asserta `submits_ok ≥ 1`) com o time flat → prova R2 (sem o líder, ainda
pontua). (2) Smoke `00-smoke` curto → `role_adoption` alto, zero `failed_role`. Driver: skill `run-hive`
(`run --scenario 06c-single-collect --assert` e `--scenario 00-smoke --assert`).

**Test scenarios:**
- 06c-single-collect com time flat: `submits_ok ≥ 1` → **PASS** (regressão de score).
- 00-smoke com time flat: `role_adoption ≥ N`, zero `failed_role` → **PASS**.

**Verification:** ambos os cenários PASS no `--assert`; `gradle test` segue 3/3 (incl. #37).

---

## Scope Boundaries

**Nesta passada (#38 completa):** colapso de tipos → `hive_agent.asl`, aposentadoria do líder central,
achatamento do `hive_org.xml` + `hive.jcm`, validação de não-regressão.

**Fora (Deferred / outras issues):**
- **Alocação multi-bloco descentralizada (Contract-Net)** → #22 (org-aloca) / #21 (cooperativa connect),
  gated na U9 (fusão de mapa). Os handlers reativos ficam prontos (KTD3) mas sem remetente.
- **Tuning de comportamento** (navegação, exploração explorer-first) → frentes próprias.
- **Nota de reflexão seção 7** → #39 (alimentada por este achatamento + a medição da #22).

---

## Sequencing & Merge Note

```
U1 (org flat → #37 verde) → U2 (hive_agent.asl) → U3 (hive.jcm + aposenta líder) → U4 (regressão+smoke)
```

Tudo na worktree `feat/sc-37-org-validator` (junto com o #37). **#37+#38 mergeiam JUNTAS** na main
(squash, após aprovação do dono) — assim a main passa de verde a verde (o RED do #37 vira verde em U1).
Sem PR; sem atribuição de IA; docs/comentários em PT-BR; commitar o plano junto.

---

## Open Questions (à implementação)

- **Nome do role flat org.** `field_agent` (neutro) vs reusar `collector` como nome do papel único. Evitar
  `worker` (colide com o role MAPC). Decisão de engenharia na U1.
- **`sch_task` sobrevive?** Se as missions `m_collect`/`m_assemble`/`m_submit` seguem fazendo sentido com
  um papel único, manter o scheme e só religar as normas; senão, simplificar para `sch_adopt` + um scheme
  de execução enxuto. Resolver ao mexer no XML (U1) vendo o que a R3 exige.
- **Deletar vs manter os 4 `.asl` antigos.** Default: deletar (completo, sem código morto). Se algum handler
  reativo só existir neles, migrar para `hive_agent.asl` antes de deletar (KTD3).
- **`task_board` ainda é lido por alguém** além do líder? Verificar na U2/U3; se não, pode sair junto (mas o
  `squad_coordinator`/`mark_busy` fica).
