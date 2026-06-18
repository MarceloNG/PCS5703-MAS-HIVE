---
date: 2026-06-17
topic: fase-d-posicionamento-relativo
---

# Fase D — Posicionamento relativo (incremento 1)

## Summary

Primeiro incremento da Fase D: cada agente se localiza por *dead-reckoning* no
próprio frame e navega relativo ao que percebe ou lembra, **adaptando o
`SharedMap` existente a um frame por-agente** — preservando o A* toroidal e o
overlay #2 (o fix do livelock). A fusão de mapas cross-agente fica **deferida e
gated por medição**.

## Problem Frame

A competição usa a config padrão do MAPC 2022, com `absolutePosition: false`:
os agentes **não recebem posição absoluta**. Toda a navegação atual do HIVE —
o `SharedMap` keyed em coordenada absoluta, o A* e o `AdjacentDirection` —
assume posição absoluta, fornecida hoje só pela config de dev
(`absolutePosition: true`). No alvo real, um agente não sabe onde está num
referencial global, então não consegue lembrar nem retornar a um alvo fora da
visão, e dois agentes não têm um sistema de coordenadas comum.

O reframe que encolhe o problema: **muita navegação não precisa de mapa
global**. Ir a um alvo *percebido* (dispenser, role-zone, bloco, goal-zone)
funciona em coordenadas relativas (`move_relative`); o mapa global só serve
para *lembrar* onde algo foi visto. O pedaço caro e arriscado é a **fusão
cross-agente** (um frame compartilhado), cujo retorno real é a coordenação de
montagem multi-bloco — não a correção do básico. Logo o básico (localizar +
navegar relativo) destrava ação e pontuação de bloco único no oficial sem
pagar o custo da fusão.

## Key Decisions

- **Escopo incremental, medir antes da fusão.** Este incremento entrega
  *dead-reckoning* por-agente + navegação relativa. A fusão de mapas
  cross-agente é construída depois, só se a medição mostrar que a montagem
  multi-bloco precisa dela. Alinha ao `STRATEGY.md` (decisão guiada por
  evidência) e desacopla o pedaço de maior risco.

- **Adaptar o `SharedMap` (Java), não migrar para crenças.** O frame
  por-agente vive dentro do `SharedMap`; o A* toroidal e o overlay #2 são
  preservados. Isso reaproveita o fix do livelock e o harness JUnit. A
  alternativa (portar o modelo de crenças `.asl` do LI(A)RA) abandonaria o A*
  e sairia do harness — rejeitada. A vantagem dela (descentralizar para fugir
  do gargalo serializado do `SharedMap`) já é endereçada pelo redesign-em-lote
  acordado, independentemente disto.

- **Mapa parametrizado por frame desde já.** A representação nasce de forma que
  a fusão futura entre como uma camada de tradução por offset, sem reescrever o
  mapa — barato agora, caro depois.

- **Validar por convergência de navegação, não por score.** O score no oficial
  depende da Fase C (adoção de role) para liberar as ações de pontuar; a Fase D
  isolada se valida por agentes convergindo a alvos no frame relativo.

## Requirements

**Localização por-agente**

- R1. Cada agente mantém sua posição num **frame local** com origem no início,
  integrando os `move` bem-sucedidos (`lastActionResult(success)`); um `move`
  falho não altera o offset.
- R2. As percepções de visão (relativas) são mapeadas para coordenadas do frame
  local do agente.

**Memória e navegação em frame relativo**

- R3. O `SharedMap` opera num frame relativo **privado por-agente** (pré-fusão):
  alvos vistos (dispenser, role-zone, goal-zone, bloco) são memorizados nesse
  frame para *lembrar* onde foram vistos.
- R4. A navegação a alvo **percebido** usa percepção relativa direta
  (`move_relative`), sem depender do mapa; o mapa serve para navegar a alvo
  **lembrado** fora da visão, via A*.
- R5. O A* toroidal e o overlay #2 são preservados, operando dentro do frame do
  agente. O wrap só se aplica após as dimensões serem conhecidas (R6); antes
  disso, o A* opera sem wrap.
- R6. As dimensões toroidais são **inferidas por observação** (reaparecimento de
  landmark / wrap) e alimentam o parâmetro de grid da Fase B (`GridConfig`).

**Compatibilidade com a fusão futura**

- R7. Mapa e posições são representados de forma **parametrizada por frame**, de
  modo que a fusão (U9) entre como uma camada de tradução por offset, sem
  reescrever o mapa.
- R8. O overlay #2 obtém posições de colega de **percepção direta** (colegas
  visíveis) no frame do agente — não há frame global pré-fusão, então a evitação
  de colega degrada para alcance de visão.

**Validação**

- R9. A lógica de frame, inferência de dimensão e tradução por offset é testável
  em **JUnit**, sem rodar a simulação.

## Key Flows

- F1. Localizar-e-agir (frame relativo)
  - **Trigger:** agente executa `move` e percebe o ambiente a cada step.
  - **Steps:** integra o `move` bem-sucedido no frame local (R1) → mapeia a
    percepção relativa para o frame (R2) → memoriza alvos vistos no
    `SharedMap` por-agente (R3) → navega: relativo direto se o alvo é percebido,
    A* no frame se o alvo é lembrado fora da visão (R4, R5).
  - **Outcome:** o agente age sobre alvos percebidos e retorna a alvos lembrados
    sem `absolutePosition`.

## Acceptance Examples

- AE1. **Covers R1.** **Given** offset inicial (0,0); **When** sequência
  n/e/s/w com sucesso, com um `move` falho no meio; **Then** o offset reflete só
  os moves bem-sucedidos (o falho é no-op).
- AE2. **Covers R6.** **Given** dimensão desconhecida; **When** um landmark é
  reobservado após dar a volta no grid; **Then** a dimensão é inferida e passa a
  alimentar o wrap — antes disso o A* roda sem wrap.
- AE3. **Covers R5.** **Given** dimensões conhecidas e um alvo lembrado próximo
  da borda oposta; **When** o A* planeja; **Then** escolhe o caminho mais curto
  pelo wrap toroidal.

## Success Criteria

- JUnit verde para: álgebra do *dead-reckoning* (R1), inferência de dimensão
  (R6), correção do wrap pós-dimensões (R5), e idempotência/correção da
  tradução por offset (stub da fusão — R7).
- Um boot headless no oficial (`absolutePosition: false`, 70×70): agentes
  convergem de forma confiável a um alvo percebido e a um alvo lembrado no frame
  relativo (métrica de **convergência de navegação**), sem `absolutePosition`.
- O **score não** é o critério da Fase D isolada — depende da Fase C.

## Scope Boundaries

**Deferido (gated por medição)**

- Fusão de mapas cross-agente (U9 — handshake de avistamento mútuo do LI(A)RA).
- Coordenação de montagem multi-bloco por coordenadas compartilhadas.

**Separado (outras fases/tracks)**

- Adoção de role (Fase C) — destrava o score; é o que torna o ganho da Fase D
  mensurável.
- Escala para 20 agentes e composição de squad (U10).
- Redesign-em-lote do `SharedMap` (gargalo serializado) — deve conviver com o
  framing por-agente.

## Dependencies / Assumptions

- A Fase C (adoção de role) é pré-requisito para o *score-lift* observável no
  oficial; a Fase D entrega a fundação de navegação.
- O framing por-agente do `SharedMap` deve permanecer compatível com o
  redesign-em-lote já acordado ([[hive-shared-map-bottleneck]]).
- A técnica de fusão (deferida) tem padrão *proven* e portável no LI(A)RA —
  de-risca a fase seguinte, sem entrar neste incremento.

## Outstanding Questions

**Deferido ao planejamento**

- Representação exata do "parametrizado por frame" no `SharedMap` (R7) — como
  tag/offset por frame sem onerar o caminho do A*.
- Método de inferência de dimensão (R6): robustez com landmark único vs.
  múltiplos; tolerância a *drift*.
- A métrica/limiar que dispara a construção da fusão (o *gate* da U9).

## Sources / Research

- `docs/plans/2026-06-17-003-feat-cenario-oficial-organizacao-plan.md` — Fase D
  (U7 frame local, U8 mapa relativo + inferência, U9 fusão, U10 escala) e a
  Open Question de arquitetura que este brainstorm resolve.
- **LI(A)RA** (time Jason, MAPC 2022, cenário oficial — github.com/Liga-IA/liara-agents):
  `synchronism.asl` (fusão via `mate_filter` / avistamento mútuo) e
  `memory_updates.asl` (*dead-reckoning* `position(X,Y)` + memória por frame).
  Referência externa **citável** no relatório; portar adaptado e melhorado, não
  copiar.
- `src/env/env/SharedMap.java`, `src/java/hive/GridConfig.java`,
  `src/java/hive/AdjacentDirection.java`, `src/agt/common/perception.asl` — o
  que o incremento adapta.
- `STRATEGY.md` e `docs/backlog.md` — âncora de prioridade e gates.
- _Nota interna (não citar no relatório):_ o projeto próprio em `~/repos/MAPC`
  dá o padrão de adoção de role relativo, mas **não** resolve posicionamento
  relativo (roda `absolutePosition: true`, mapa write-once sem merge).
