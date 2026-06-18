---
date: 2026-06-18
topic: fase-c-adocao-role
---

# Track 3 Fase C — Adoção de role (capacidade de pontuar no oficial)

## Summary

No cenário oficial do MAPC 2022 o agente começa no role `default`, que **não tem**
`request/attach/connect/submit` — sem adotar um role de pontuação ele só anda. A
Fase C dá ao HIVE a lógica de **adoção de role dirigida pela organização MOISE+**:
um agente de execução, ao alcançar uma **role-zone** (usando a navegação relativa
que a Fase D destravou), executa `adopt(worker)` e ganha as ações de coletar,
montar e submeter — convertendo navegação em **score**. Incremento mínimo viável:
todos os executores adotam um único role de pontuação (`worker`).

## Problem Frame

Os roles reais do MAPC 2022 são **aditivos** sobre o `default` (livro MAPC, Table 1):
`worker = default + request,attach,connect,disconnect,submit`. O `default`
(move/rotate/adopt/clear/detach) sozinho **não pontua**. Para adquirir capacidade,
o agente precisa estar **sobre uma role-zone** e usar `adopt` (a role-zone é fixa a
simulação inteira). Hoje o HIVE não tem nenhuma lógica de adoção — só mapeia
`role_zone` na percepção — então no oficial faz **score 0** mesmo navegando bem.

O reframe que encolhe o problema: como os roles são **aditivos**, o `worker` também
**anda e re-adota** (herda o `default`). Logo a adoção é essencialmente "ir à
role-zone e adotar uma vez", **sem** máquina de troca de role — e o pipeline de
coleta/montagem existente revive com as ações novas, sem reescrita. O caro/arriscado
(especialização multi-role, troca dinâmica) fica deferido.

**Distinção central:** "role" tem dois sentidos — (i) **role MOISE+** = função no
time (collector/assembler/…); (ii) **role MAPC** = capacidade no servidor
(default/worker/…), adotada via `adopt` numa role-zone. A Fase C é o **elo**: o role
MOISE+ decide qual role MAPC adotar. Isso dá dentes ao requisito de MOISE+ do
entregável (hoje a org existe mas não dirige capacidade).

## Key Decisions

- **KD1 — Adoção dirigida por MOISE+, mínimo viável = todos viram `worker`.** A org
  dispara/registra a adoção; no incremento 1 todo executor adota o mesmo role de
  pontuação. Sem especialização ainda. Alinha ao requisito de MOISE+ do exercício
  (entregável exige MOISE+ explicitamente).
- **KD2 — Reusar a navegação relativa da Fase D para chegar à role-zone.** A org já
  tem o goal `role_zones_found`; a percepção já mapeia `role_zone`; role-zones são
  **fixas** → landmark estável. Achar → lembrar → navegar (A* no frame relativo) →
  `adopt`.
- **KD3 — Roles são aditivos: `worker` anda e re-adota.** Verificado no livro MAPC
  (Table 1; "the agent can move, rotate, and adopt new roles"). Logo adopt-once, sem
  máquina de troca. Remove o risco "worker parado".
- **KD4 — Config de avaliação fiel obrigatória.** Usar os **roles reais aditivos**
  (default restrito; worker/constructor/explorer/digger = default + extras). O
  `standard.json` bundled é **sample degenerado** (worker sem move) e nossa
  `OfficialTestConfig.json` é permissiva demais (default com tudo) — nenhum dos dois
  reproduz a restrição real.
- **KD5 — Não reescrever o pipeline de coleta/montagem.** A Fase C só o **destrava**
  ao conferir as ações; coleta/conexão/submit já existem.

## Requirements

**Adoção**
- R1. Um executor comprometido a um role/missão MOISE+, ao lembrar uma role-zone,
  navega até ela (Fase D) e executa `adopt(worker)`; passa a ter
  request/attach/connect/submit.
- R2. A adoção é **dirigida e registrada pela organização MOISE+** (não decisão solta
  por agente): o role MOISE+ mapeia ao role MAPC a adotar.
- R3. Pós-adoção, o pipeline existente (request → attach → [connect] → submit) opera
  com as ações novas e o time **pontua**.

**Config & fidelidade**
- R4. Existe um config de avaliação/boot com os **roles reais aditivos** (default sem
  request/attach/submit; worker = default + ações de pontuação). Não usar o
  `standard.json` degenerado nem o `default`-permissivo atual.

**Robustez**
- R5. Recuperação: um agente que perca o estado de pontuação (ex.: desativação) volta
  a ter o role de pontuação **re-adotando** numa role-zone (adopt está em todos os
  roles). Comportamento sob desativação a confirmar (ver Outstanding Questions).
- R6. Respeitar **normas de contagem de role** (upper bound de agentes por role por
  time): a org controla a composição para não violar a norma.

**Validação**
- R7. Boot no oficial (roles reais restritos, 70×70) demonstra o time fazendo
  **score > 0** (vs 0 hoje); sem regressão no dev.

## Key Flows

- F1. **Bootstrap → adotar → pontuar.**
  - **Trigger:** início no role `default` (sem ações de pontuação).
  - **Steps:** explorar como default → achar role-zone (goal MOISE+
    `role_zones_found`) → memorizar → navegar até a role-zone (A* relativo, Fase D) →
    `adopt(worker)` → executar tarefa (request/attach/[connect]/submit) → pontuar.
  - **Outcome:** o time pontua no oficial sem `absolutePosition`.

## Acceptance Examples

- AE1. **Cobre R1/R3.** **Given** agente default sem ações de coleta; **When** acha e
  alcança uma role-zone e adota `worker`; **Then** ganha request/attach/submit e
  completa ≥1 task.
- AE2. **Cobre R4.** **Given** o config de avaliação; **Then** o `default` não tem
  request/attach/submit, mas o `worker` (= default + extras) **anda E pontua**.
- AE3. **Cobre R5.** **Given** um agente desativado; **When** reativa; **Then**
  garante o role de pontuação (re-adota na role-zone se necessário).

## Success Criteria

- Boot no oficial (roles reais aditivos, 70×70): agentes navegam a uma role-zone,
  adotam `worker`, e o **time faz score > 0** — a métrica da Fase C (vs score 0 hoje).
- Sem regressão no dev (a adoção não quebra o caminho `absolutePosition:true`).
- A adoção é visivelmente **dirigida pela org MOISE+** (rastreável no relatório).

## Scope Boundaries

**Deferido (gated por medição)**
- Especialização de role: `constructor` (speed 1 sempre — melhor carregando, p/
  multi-bloco), `explorer` (vision 7, speed 3, `survey` p/ achar dispenser/role-zone/
  goal rápido — scout), `digger` (clear ranged). Promover quando o mínimo pontuar e a
  medição mostrar ganho.
- Troca dinâmica de role por fase de tarefa.
- Escala para 20 agentes e composição fina do squad (U10).
- Fusão de mapas cross-agente (U9) — gated nesta fase (precisa de score p/ medir).

**Não-objetivo**
- Reescrever os consumidores do pipeline de coleta/montagem (KD5).

## Dependencies / Assumptions

- **Fase D (navegação relativa)** entregue — pré-requisito para chegar à role-zone
  sem `absolutePosition`.
- **Org MOISE+ viva (Fase A)** — base para dirigir a adoção; já tem o goal
  `role_zones_found` e `commitMission`.
- **Roles reais aditivos** (assumido e verificado no livro MAPC, Table 1): o role de
  pontuação anda. Garantir apenas que o **config usado na avaliação** os reproduz
  (o bundled `standard.json` não).
- Prior art reutilizável: `~/repos/MAPC/src/agt/worker_role.asl` faz `adopt(worker)`
  — citar e melhorar (não copiar).

## Outstanding Questions

**Deferido ao planejamento**
- `worker` vs `constructor` no mínimo: `worker` (speed 2 leve) é melhor p/ bloco
  único; `constructor` (speed 1 sempre) p/ multi-bloco. Mínimo viável = `worker`;
  reavaliar ao entrar montagem multi-bloco.
- Desativação preserva o role adotado? (agente perde attachments — mantém o role?)
  Verificar no massim; decide se R5 precisa re-adoção ativa ou é automático.
- Mapeamento exato role MOISE+ → role MAPC quando a especialização entrar (qual
  role MOISE+ vira constructor/explorer/digger).
- Limite da norma de contagem de role no config de avaliação (afeta quantos workers).

## Sources / Research

- `local/978-3-031-38712-8.pdf` — livro MAPC 2022, Table 1 (roles **aditivos**;
  `adopt` em role-zone; role mantido indefinidamente, re-adoção a qualquer momento numa
  role-zone). Fonte que resolve "o worker anda".
- `massim_2022/docs/scenario.md` — ref [6] do enunciado: mecânica adopt/role-zone;
  role-zones **fixas** a simulação inteira; normas de contagem de role.
- `local/5703_ex02_26.pdf` — enunciado PCS5703: **MOISE+ obrigatório**; avaliação =
  relatório (artigo) + código + **competição** da turma; seções do relatório
  (estratégia do time, características técnicas/recuperação de falhas).
- `src/org/hive_org.xml` — org MOISE+ atual (roles, schemes, `commitMission`, goal
  `role_zones_found`).
- `src/agt/common/perception.asl` — já mapeia `role_zone` (ponto de partida).
- `~/repos/MAPC/src/agt/worker_role.asl` — `adopt(worker)` (prior art a citar/melhorar).
- Configs atuais: `conf/OfficialTestConfig.json` (default permissivo) e
  `massim_2022/server/conf/sim/roles/standard.json` (sample degenerado — worker sem
  move) — nenhum reproduz a restrição real; R4 endereça.
