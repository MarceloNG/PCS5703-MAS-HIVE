---
title: "feat: Flat workers — todos os agentes adotam worker (Prioridade 1 estrutural)"
type: feat
date: 2026-06-19
status: active
---

# feat: Flat workers — todos os agentes adotam worker

## Summary

Todos os 15 agentes do HIVE passam a ser capazes de adotar o role MAPC `worker`. Hoje apenas
`collector` e `assembler` (9/15) incluem `role_adoption.asl`; `squad_leader` e `sentinel` não
incluem, e ainda têm um SELF-ASSIGN sem guarda de `can_score_role` que os leva a tentar
`request` → `failed_role`. Este plano adiciona `role_adoption.asl` nos dois tipos ausentes, adiciona
a guarda em SELF-ASSIGN, e estende a obrigação MOISE+ de adoção para cobrir todos os roles org.

---

## Problem Frame

No último run de 300 steps (`OfficialRolesConfig.json`), o replay mostrou:
- `agentA3` (squad_leader): 21 `request` → todos `failed_role` (não tem `worker`).
- `agentA13-A15` (sentinel): mesma situação, silenciosa (sem log de submissão).
- Apenas 4/15 agentes adotaram worker, dos quais 0 completaram um submit.

A causa raiz não é lógica de adoção defeituosa — é que apenas 9 dos 15 agentes sequer tentam
adotar. A evidência do livro MAPC 2022 confirma: nenhum dos 5 times competitivos usou um
padrão squad_leader/sentinel; todos usaram estrutura flat default→worker. O próprio livro
(organizers): *"it is usually not helpful to come up with a centralized solution."*

O plano é **mínimo e reversível**: não remove os tipos de agente nem os roles MOISE+ — mantém a
org (quem coordena, quem coleta) intacta e apenas habilita que TODOS os agentes adotem `worker`.

---

## Requirements

- **R1:** Zero `failed_role` originados de agentes `squad_leader` e `sentinel` em replay oficial.
- **R2:** Todos os 15 agentes tentam adotar `worker` (SELF-ASSIGN e `role_adoption.asl` ativos para todos).
- **R3:** MOISE+ registra a adoção de todos os roles org (norm `n_adopt` cobre squad_leader e sentinel).
- **R4:** A ordem de include em squad_leader e sentinel preserva a prioridade de `role_adoption.asl` sobre `collection.asl`.
- **R5:** Sem regressão nos agentes collector e assembler (lógica inalterada).

Fonte: `docs/backlog.md` §Prioridade 1 — ESTRUTURAL.

---

## Key Technical Decisions

**KTD-1: Não remover tipos de agente JaCaMo, nem unificar em um único `.asl`**

A alternativa de criar um `hive_agent.asl` unificado e usar apenas esse tipo no `hive.jcm`
seria mais alinhada à estrutura flat da competição, mas reescreveria código funcionando e
arriscaria regressões no pipeline collector/assembler. Dado o prazo (entrega 20/06/2026),
a abordagem mínima — adicionar `role_adoption.asl` nos dois ausentes — entrega o mesmo
benefício de score com risco zero de regressão.

**KTD-2: Guarda `can_score_role` em SELF-ASSIGN, não remoção do plano**

O plano SELF-ASSIGN (`connect_protocol.asl:73`) é útil para workers reais (quem já adotou).
Adicionar `: can_score_role` como primeira condição garante que só dispara em agentes com
`worker` ou `constructor` — seja squad_leader, sentinel, ou qualquer outro tipo que ainda
não adotou.

**KTD-3: Estender norm `m_adopt` no XML, não criar scheme separado**

A missão `m_adopt` já existe com `max="12"`. Adicionar normas `n_adopt_sq` e `n_adopt_snt`
e aumentar `max` para 15 reutiliza a infraestrutura já testada (commits U4). Não é necessário
um novo scheme.

**KTD-4: Ordem de include em squad_leader e sentinel — role_adoption ENTRE connect_protocol e collection**

O comentário em `role_adoption.asl` documenta o invariante: deve entrar ANTES de
`collection.asl` para que os planos `+step(N)` de adoção tenham prioridade (ordem de include =
prioridade em Jason). Posição: logo após `connect_protocol.asl`, espelhando collector/assembler.

---

## Scope Boundaries

### In Scope
- `src/agt/common/connect_protocol.asl` — adicionar guarda `can_score_role` ao SELF-ASSIGN.
- `src/agt/squad_leader.asl` — adicionar `{ include("common/role_adoption.asl") }`.
- `src/agt/sentinel.asl` — adicionar `{ include("common/role_adoption.asl") }`.
- `src/org/hive_org.xml` — adicionar norms `n_adopt_sq`, `n_adopt_snt`; aumentar `m_adopt max` para 15.

### Out of Scope
- Remover ou renomear tipos de agente JaCaMo, roles MOISE+, ou o `hive.jcm`.
- Unificar todos os agentes em um único arquivo `.asl` (`hive_agent.asl`).
- Mudar cardinalidades de `squad_leader`, `collector`, `assembler`, `sentinel` além da missão `m_adopt`.
- Mudar a lógica interna de `squad_leader.asl` (coordenação, `!setup_squad_coordinator`).
- U9 (fusão de mapas) — Prioridade 3, separada.
- Navegação / `failed_path` — bloqueio ortogonal.

### Deferred to Follow-Up Work
- Unificação em `hive_agent.asl` único — simplificação válida, mas requer migração cuidadosa
  da lógica específica de cada tipo (setup_squad_coordinator no squad_leader, etc.).
- Remover `my_role_type(squad_leader)` / `my_role_type(sentinel)` — não tem efeito agora;
  pode ser limpo junto com a unificação.

---

## High-Level Technical Design

Estado atual vs. estado alvo por tipo de agente:

```
         squad_leader  sentinel  collector  assembler
Inclui role_adoption?    NÃO      NÃO        SIM        SIM
SELF-ASSIGN guardado?    NÃO      NÃO        NÃO        NÃO  ← U1 cobre todos
Norm m_adopt cobre?      NÃO      NÃO        SIM        SIM
                           ↓ após U1-U3 ↓
                         SIM      SIM        SIM        SIM
                         SIM      SIM        SIM        SIM
                         SIM      SIM        SIM        SIM
```

Sequência de ativação de um agente (squad_leader ou sentinel pós-plano):

```
+step(N) [connect_protocol.asl — handlers de prioridade máxima]
    → not can_score_role → role_adoption.asl assume
        → !ensure_worker_role → navega para role-zone → adopt(worker)
        → can_score_role agora TRUE
+step(N) [role_adoption.asl — plano (c): worker idle → auto-coleta]
    → can_score_role & not my_active_task → inicia !collect_block
+step(N) [connect_protocol.asl — SELF-ASSIGN]
    → can_score_role & idle → auto-atribui task (se role_adoption.asl (c) não pegou antes)
```

---

## Implementation Units

### U1. Adicionar guarda `can_score_role` ao SELF-ASSIGN

**Goal:** Impedir que squad_leaders e sentinels (ainda com role `default`) executem SELF-ASSIGN
e tentem `request` → `failed_role`.

**Requirements:** R1, R2

**Dependencies:** nenhuma

**Files:**
- `src/agt/common/connect_protocol.asl`

**Approach:** Adicionar `: can_score_role` como primeira condição do handler SELF-ASSIGN
(`+step(N)` que começa em `(N mod 7) == 4`). A condição é avaliada primeiro (falha rápido)
e não altera a semântica para agentes que já têm `worker`.

**Patterns to follow:** `role_adoption.asl:42-44` — definição de `can_score_role`; já usada
em `role_adoption.asl:78` (plano (c)) como guarda equivalente.

**Test scenarios:**
- Happy path: agente com `role(worker)` satisfaz `can_score_role` → SELF-ASSIGN dispara.
- Bloqueio: agente com `role(default)` → `can_score_role` falha → SELF-ASSIGN não dispara.
- Bloqueio: squad_leader (A1-A3) em step N%7==4, antes de adotar worker → nenhum `request` emitido.
- Verificação no replay: zero resultados `failed_role` associados a ações `request` de A1-A3 ou A13-A15.

**Verification:** Replay de 300 steps com `OfficialRolesConfig.json` mostra 0 `failed_role`
para os agentes A1-A3 e A13-A15.

---

### U2. Adicionar `role_adoption.asl` a `squad_leader.asl` e `sentinel.asl`

**Goal:** Fazer com que squad_leaders e sentinels tentem adotar `worker` em cada step,
na mesma prioridade que collectors e assemblers.

**Requirements:** R2, R4, R5

**Dependencies:** U1 (guarda SELF-ASSIGN deve estar no lugar — se role_adoption dispara
antes que worker seja adotado, o SELF-ASSIGN não pode interferir).

**Files:**
- `src/agt/squad_leader.asl`
- `src/agt/sentinel.asl`

**Approach:** Em cada arquivo, adicionar `{ include("common/role_adoption.asl") }` logo
após `{ include("common/connect_protocol.asl") }` e antes de `{ include("common/collection.asl") }`.
Não remover nenhum include existente. Espelha exatamente a ordem de collector e assembler.

**Patterns to follow:** `src/agt/collector.asl:8-9` — `connect_protocol.asl` seguido de
`role_adoption.asl`; `src/agt/assembler.asl:8-9` — mesma ordem.

**Test scenarios:**
- Happy path: squad_leader adota `worker` em replay de 300 steps antes do step 100.
- Happy path: sentinel adota `worker` em replay de 300 steps antes do step 100.
- Sem regressão: collector e assembler continuam adotando worker no mesmo padrão do baseline.
- Sem regressão: squad_leader ainda executa `!setup_squad_coordinator` e coordena leilão.
- Edge case: squad_leader sobre role-zone → adopt(worker) dispara uma vez, sem adopt-spam.

**Verification:** Replay mostra os agentes A1-A3 e A13-A15 com `role: worker` listado no
histograma de adoção do analyzer (`replay_analyze.py`). Baseline: ≥12/15 adotam em 300 steps
(vs. ≤4/15 antes).

---

### U3. Estender norm de adoção no `hive_org.xml` para squad_leader e sentinel

**Goal:** Fazer com que a obrigação MOISE+ de adotar `worker` cubra todos os 15 agentes,
não apenas collectors e assemblers.

**Requirements:** R3

**Dependencies:** U2 (agentes precisam ter `role_adoption.asl` para satisfazer a norma).

**Files:**
- `src/org/hive_org.xml`

**Approach:**
1. Adicionar normas `n_adopt_sq` (squad_leader → m_adopt) e `n_adopt_snt` (sentinel → m_adopt)
   na `<normative-specification>`.
2. Aumentar `max` da missão `m_adopt` de `12` para `15` (cobre os 15 agentes ativos).
3. Não alterar nenhuma outra cardinalidade ou norma.

**Patterns to follow:** `hive_org.xml:120-121` — `n_adopt_col` e `n_adopt_asm` como referência
de sintaxe para as novas normas.

**Test scenarios:**
- Happy path: log do MOISE+ mostra squad_leader commitando `m_adopt` no início da sim.
- Happy path: log do MOISE+ mostra sentinel commitando `m_adopt`.
- Sem regressão: collector e assembler continuam com `m_adopt` commitado (norm `n_adopt_col`/`n_adopt_asm` inalteradas).
- Edge case: com 3 squad_leaders + 3 sentinels + 6 collectors + 3 assemblers = 15 agentes todos comprometidos → `m_adopt max=15` não rejeita nenhum.

**Verification:** Log do JaCaMo mostra `[MOISE]` registrando o commit de `m_adopt` para os
tipos squad_leader e sentinel. Replay não mostra violation de norm de adoção.

---

## Risks & Dependencies

| Risco | Probabilidade | Mitigação |
|-------|---------------|-----------|
| squad_leader adota worker mas a lógica de coordenação (`!setup_squad_coordinator`) conflita com o pipeline de coleta | Baixa — a adoção só muda o role MAPC, não o fluxo MOISE+ | Rodar replay e verificar que o líder ainda participa do leilão |
| Adopt-spam para squad_leader/sentinel (re-adoção em loop) | Média — o mesmo bug visto em collectors antes | Existente: `can_score_role` via `role(worker)` já deve parar o loop; monitorar no replay |
| Erro de parse em `.asl` por include na posição errada | Baixa — espelha padrão já funcional | Testar com `gradle run --steps 5` (smoke) antes de rodar 300 steps |

---

## Open Questions

Nenhuma bloqueante. Questões deferidas à implementação:
- Descobrir se `squad_leader` com role `worker` ainda coordena o leilão corretamente (verificação pós-U2).
- Avaliar se `sentinel.asl` tem lógica específica (`!setup_squad_coordinator` ausente — OK) que
  conflite com `role_adoption.asl` (baixo risco, verificar replay).

---

## Sources & Research

- `docs/backlog.md` §Prioridade 1 — ESTRUTURAL (análise dos 5 times MAPC 2022, evidência do livro).
- `local/978-3-031-38712-8.pdf` cap. "The MAPC 2022" — citação direta sobre soluções centralizadas.
- Replay último run (2026-06-18): agentA3, 21 `request` → `failed_role` (evidência dura).
- `src/agt/collector.asl:8-9` e `src/agt/assembler.asl:8-9` — padrão de include a espelhar.
- `src/agt/common/role_adoption.asl:1-22` — comentário canônico sobre ordem de include e prioridade.
