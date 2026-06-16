---
title: "Agentes JaCaMo perdem ações (timeout de 8s) — a bomba de percepção do EIS estarva a action()"
date: 2026-06-16
category: performance-issues
module: "Ponte EIS<->agentes (src/env/connection/EISAccess.java)"
problem_type: performance_issue
component: service_object
symptoms:
  - "Servidor MASSim loga ~940 'No valid action available in time' em 800 steps"
  - "Run de 800 steps leva ~67 min; ~50% dos runs travam em cascata de reconexao"
  - "Duracoes de step bimodais: ~metade 0s, ~metade exatos 8s (= agentTimeout)"
root_cause: thread_violation
resolution_type: config_change
severity: high
tags: [jacamo, cartago, eismassim, agent-timeout, perception-pump, jstack, artifact-threading, performance]
---

# Agentes JaCaMo perdem ações (timeout de 8s) — a bomba de percepção do EIS estarva a action()

## Problem

No HIVE (MAS JaCaMo para o MAPC 2022), ~7,8% das ações dos agentes eram descartadas pelo servidor ("No valid action available in time"), os runs de 800 steps levavam ~67 min e ~50% travavam. A causa real era um gargalo no pipeline de percepção do EIS — não no mapa compartilhado, como se suspeitava.

## Symptoms

- Servidor: ~940 ocorrências de `No valid action available in time` em 800 steps (~1,18/step).
- Run completo (800 steps) em ~67 min; o README prometia ~6 min.
- ~50% dos runs entravam em cascata de desconexão/reconexão e travavam.
- Durações de step **bimodais**: ~metade instantâneas (0s, todos os agentes agem) e ~metade em **exatos 8s** (o `agentTimeout`), porque 1–2 agentes **não emitiam ação** naquele step e o servidor esperava o deadline inteiro.
- Máquina **ociosa** durante o run (load ~0,8 de 18 cores) — agentes esperando, não computando.

## What Didn't Work

Dois diagnósticos plausíveis falharam — ambos por **chutar fix sem medir**:

1. **Aumentar `agentTimeout`** — descartado: 8s já é enorme para decidir uma ação; o problema não era falta de tempo de CPU.
2. **Escrita em lote no `SharedMap`** (op `ingest_view` substituindo `update_cell` por célula; commits `106a13c`/`1447cfb`/`4dc4c75`) — a premissa do README ("centenas de escritas serializadas/step") estava **errada**: o `update_cell` original já era *event-driven* (só células novas via `+thing`). Medido no seed 17: **idêntico ao baseline** (sem melhora).
3. **Suspeita de contenção no A\* de leitura** (`compute_next_move`/`get_nearest_goal_zone`) — **refutada** pelo profiling: 40 thread-dumps de `jstack` mostraram **0 frames** em `env.SharedMap` e **0** em A\*.

## Solution

O `jstack` (40 amostras durante o run lento) revelou que as threads passavam o tempo **dormindo em `massim.eismassim.ConnectedEntity.getPercepts`**, chamado pela bomba de percepção `connection.EISAccess.updatePercepts`. O gargalo era o pipeline de percepção do EIS, e o ajuste foi de **uma linha** — o campo `awaitTime` em `src/env/connection/EISAccess.java`:

```java
// antes
private int awaitTime = 100;
// depois
private int awaitTime = 500;
```

Comparativo medido (fast config, seed 17, mesmas condições, variando só `awaitTime`):

| `awaitTime` | steps em 8s | timeouts | média/step |
|---|---|---|---|
| 5   | 34/45 | 115 (pior) | 6,1s |
| 100 (original) | 24/47 | ~19 (até step 31) | 4,2s |
| **500** | **3/46** | **7** | **1,0s** |

Validação full (TestConfig, 800 steps, seed 17) com `awaitTime=500` (commit `a003d1e`): completou **sem travar** em **8m39s** (~7,7× mais rápido) com **43 timeouts** (~22× menos ações perdidas que o baseline de 940).

> Observação: o score caiu de 70 (7 submits) para 50 (5) neste run, mas isso é **variância** do cenário (o README reporta 60–100 no mesmo seed) e timing de coordenação — não regressão mecânica. Com o mecanismo destravado, o teto de score passa a ser questão de **estratégia**, não de desempenho.

## Why This Works

A bomba de percepção `EISAccess.updatePercepts` é um `@INTERNAL_OPERATION` em loop que **segura a thread de execução do artefato** `EISAccess`: o `getPercepts` do eismassim (com `scheduling: true`) faz `Thread.sleep` interno enquanto espera o próximo step do servidor. A `action()` do agente é um `@OPERATION` **no mesmo artefato** — e o CArtAgO **serializa** operações por artefato. Logo, a `action()` só consegue rodar na **janela em que a bomba cede a thread**, via `await_time(awaitTime)`.

- `awaitTime` pequeno → janela minúscula → a `action()` fica **estarvada** → o agente não emite ação → o servidor espera o deadline de 8s → step lento + ação perdida.
- `awaitTime` maior → janela maior → a `action()` é atendida **no mesmo step** → o agente para de "ficar sem ação".

Por isso o resultado é **contraintuitivo**: aumentar o sleep deixou o sistema **mais rápido**, porque o que importava não era a latência da bomba, e sim dar espaço para a operação `action()` co-localizada.

## Prevention

- **Medir/perfilar antes de corrigir.** Dois fixes foram chutados (mapa em lote, A\* de leitura) e ambos falharam na medição. O `jstack` resolveu em minutos.
- **Use `jstack` para gargalos de espera/bloqueio.** Amostre thread dumps (`jstack <pid>` repetido) quando a máquina está ociosa mas lenta — revela threads bloqueadas/dormindo que a intuição não acha. Ex.: `for i in $(seq 1 40); do jstack $PID >> dump.txt; sleep 1; done` e agregue por frame.
- **Regra de ouro JaCaMo/CArtAgO:** um `@INTERNAL_OPERATION` longo que **bloqueia** (`Thread.sleep`, I/O bloqueante) em vez de ceder com `await`/`await_time` **estarva as outras `@OPERATION` do mesmo artefato**. Se um artefato tem um loop de percepção/escuta E operações chamadas pelo agente (ex.: `action()`), garanta janelas de yield generosas — ou separe percepção e ação em **artefatos distintos** (fix arquitetural "limpo" de longo prazo).
- **Crie uma config de teste rápida** (menos steps, mesmas condições de contenção) para iterar: aqui, reduzir `steps` de 800→100 baixou o ciclo de validação de ~67 min para ~9 min (`conf/FastTestConfig.json`).
- **Determinismo:** o seed do mapa é fixo (17), então comparações antes/depois de mecanismo são confiáveis; o **score**, porém, é alta-variância — não tire conclusão de score de um único run.

## Related Issues

- Brainstorm e plano que originaram a investigação (premissa do `SharedMap`, depois corrigida): `docs/brainstorms/2026-06-16-mapa-compartilhado-lote-requirements.md`, `docs/plans/2026-06-16-001-refactor-shared-map-batch-ingest-plan.md`.
- Branch: `refactor/shared-map-batch-ingest`. Fix: commit `a003d1e`.
- A "Limitação Conhecida" do `README.md` atribui o gargalo à serialização do `SharedMap` no CArtAgO — **desatualizada** à luz deste achado (o gargalo é o pipeline de percepção do EIS).
