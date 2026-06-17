---
title: "A* Livelock: planner sem consciência de colega briga com o escape reativo"
date: 2026-06-17
category: logic-errors
module: navigation
problem_type: logic_error
component: service_object
severity: high
symptoms:
  - "Agentes agem todo step mas não progridem ao se aglomerar perto de paredes/colegas (livelock de movimento)"
  - "Detector de oscilação dispara ~157 vezes em 200 steps (seed 17); modo de falha dominante (~20x as detecções de stuck)"
  - "Adicionar uma camada reativa de escape PIOROU a oscilação (157 para 339), em vez de melhorar"
  - "O reflexo de escape disparou 13x na mesma célula — o agente orbitando um gargalo fixo"
root_cause: logic_error
resolution_type: code_fix
tags:
  - astar
  - livelock
  - multi-agent
  - pathfinding
  - occupancy
  - jacamo
  - jason
  - mapc
---

# A* Livelock: planner sem consciência de colega briga com o escape reativo

## Problem

Os agentes do HIVE (MAS JaCaMo, MAPC 2022) agiam todo step mas não progrediam ao se aglomerar perto de paredes ou uns dos outros. O A* (`astar`/`compute_next_move` em `src/env/env/SharedMap.java`) montava seu conjunto `blocked` **só** de obstáculos estáticos — sem conhecer as posições vivas dos colegas — e roteava um agente direto através do outro. Com vários disputando os mesmos corredores, cada `move` ou colidia (`failed_path`) ou tinha sucesso só para rerotear de volta à mesma célula ocupada — uma oscilação ping-pong que nunca resolvia.

Uma correção anterior ("passo 1", no handler `lastActionResult(failed_path)` de `perception.asl`) corretamente parou de marcar célula de colega como obstáculo permanente (matar obstáculos-fantasma). Mas isso deixou o A* **sem nenhum sinal** sobre onde estão os colegas — então ele seguia gerando rotas através deles.

## Symptoms

Medido com um detector de oscilação (linhas `[OSC]`, seed 17):

- **Baseline (pós passo-1): ~157 oscilações ping-pong em 200 steps** — o modo de falha dominante, ~20x mais frequente que as detecções de stuck genuínas.
- A pior célula isolada foi revisitada 22x em oscilação. O agente agia (não congelava), mas a vazão era ~zero nesses trechos.
- `[STUCK]` (mesma célula por ≥50 steps) quase não disparava — a oscilação alterna células e engana o detector de stuck.

## What Didn't Work

Tentamos primeiro uma **camada reativa de escape** em `.asl` (`!escape_move`/`check_osc`): ao detectar ping-pong (posição atual == a de 2 steps atrás, com destino ativo), `check_osc` setava `escape_pending` e disparava `!escape_move`, que escolhia, pela percepção local, o vizinho livre que mais aproximava do destino.

**Resultado medido: a oscilação subiu de 157 para 339.** `[STUCK]` caiu para ~0.

Causa: o escape é um **reflexo de 1 passo, sem autoridade sobre a rota**. Ele empurra o agente para uma célula lateral livre; mas no step seguinte o move teve sucesso, `escape_pending` é limpo, e o `compute_next_move` roda normalmente — recomputando um A* fresco que roteia direto de volta ao mesmo gargalo bloqueado por colega. É um motor de 2 tempos: escape empurra pro lado, A* puxa de volta, escape de novo, e assim por diante. O agente **orbita** o gargalo indefinidamente. A instrumentação (`[ESC]` + o monitor web ao vivo) confirmou: o escape disparou **13x na mesma célula** sem nunca sair. Trocou "congela → detach forçado" por "orbita pra sempre, sem ganho de vazão".

A lição é estrutural: **um heurístico local reativo não consegue sobrepor um planner global. Ele briga com o planner a cada step, e o planner ganha a rota.**

## Solution

**Tornar o próprio A* ciente de colega, penalizando células ocupadas no custo.**

**1. Índice de ocupação no `SharedMap.java`:**

```java
private ConcurrentHashMap<String, int[]> occupancy;   // nome -> {x, y, step}
private int occupancyStep = 0;
private static final int TEAMMATE_PENALTY = 16;

@OPERATION
void update_occupancy(Object oName, Object ox, Object oy, Object ostep) {
    int s = toInt(ostep);
    occupancy.put(oName.toString(), new int[]{normX(toInt(ox)), normY(toInt(oy)), s});
    if (s > occupancyStep) occupancyStep = s;
}
```

Cada agente empurra sua posição absoluta todo step (em `perception.asl`, ao lado do `update_agent_pos`). As entradas são **carimbadas com o step**; o A* só conta entradas **frescas** (`p[2] >= occupancyStep - 1`), então a célula de um agente desconectado **expira** em vez de virar um poço de penalidade permanente.

**2. Conjunto `occupied` + penalidade no `astar`:**

```java
// antes: o astar só conhecia obstáculos estáticos
int ng = cg + 1;

// depois: overlay de ocupação viva — exceto origem e alvo
Set<String> occupied = new HashSet<>();
for (int[] p : occupancy.values()) {
    if (p[2] >= occupancyStep - 1) { occupied.add(p[0] + "," + p[1]); }
}
occupied.remove(tx + "," + ty);
occupied.remove(fx + "," + fy);
// ...no laço de vizinhos:
int ng = cg + 1 + (occupied.contains(nk) ? TEAMMATE_PENALTY : 0);
```

A penalidade entra só no `astar` (escolha do passo), **não** no `astarCost` (seleção de goal zone), para não distorcer a escolha de destino com ruído de ocupação transitória.

O escape reativo é **mantido como fallback** para corredor 1-wide frente-a-frente real. Seu anti-volta usa **reversão de direção** (não coordenada), ficando independente do tamanho do grid:

```jason
// anti-volta: a direcao candidata e o reverso do ultimo move
is_bounce(n) :- last_attempted_dir(s).
is_bounce(s) :- last_attempted_dir(n).
is_bounce(e) :- last_attempted_dir(w).
is_bounce(w) :- last_attempted_dir(e).
```

**Calibração (A/B determinístico, seed 17, mediana de N runs):**

| PENALTY | OSC @100 | OSC @200 | Submits @200 |
|---------|----------|----------|--------------|
| 8       | 48       | —        | —            |
| 16      | 43       | 78       | 3            |
| 24      | 31       | 83       | 1            |

PENALTY=24 deu a menor oscilação de curto prazo mas **regrediu os submits** (detours longos demais estarvam a vazão). **PENALTY=16 escolhido**: ~50% de redução de oscilação sem regredir submits. A pior célula caiu de 22x para 6x revisitas; o resíduo é oscilação difusa/transitória, não loops localizados.

## Why This Works

O A* gerava rotas erradas porque seu `blocked` descrevia o **mapa estático**, não a configuração dinâmica dos agentes. O overlay de ocupação corrige a **entrada** do planner — as rotas passam a contornar colegas do mesmo jeito que contornam paredes, sem nenhuma camada corretiva pós-hoc.

**Penalizar em vez de bloquear** é essencial em espaço estreito: num corredor 1-wide, se o colega é o único caminho, o A* ainda atravessa (a custo alto), o move falha, e o escape-fallback cede. Bloquear deadlockaria o agente ali permanentemente.

A **expiração por step** (`>= occupancyStep - 1`) evita acúmulo de entradas obsoletas: se um agente desconecta, sua última célula deixa de ser penalizada após 1 step em vez de virar fantasma permanente.

O anti-volta por direção (`is_bounce`) independe do tamanho do grid porque opera sobre n/s/e/w, não sobre aritmética de coordenadas — um teste por módulo do grid exigiria saber `gridWidth`/`gridHeight` dentro do `.asl` e quebraria em mapas de outro tamanho (a config oficial do MAPC é 70×70).

A lição arquitetural central: **um heurístico local reativo não sobrepõe um planner global — ele briga com ele.** O fix pertence ao modelo de custo do planner, não a um wrapper em volta dele.

## Prevention

- **Conserte a ENTRADA do planner, não a saída.** Quando um planner global gera rotas ruins, dê informação melhor (`blocked`/custo corretos) em vez de um reflexo corretivo — o reflexo é sobrescrito no próximo ciclo de planejamento.
- **Penalize ocupação transitória, não bloqueie.** Entidades dinâmicas (agentes) podem ser o único caminho num corredor; um custo finito deixa o A* passar se não houver alternativa.
- **Calibre pesos de custo por métrica ancorada no problema, não por score.** Score é ruidoso demais em poucos runs; oscilação/detach são causados diretamente pelo bug e legíveis num grep de log (`[OSC]`/`[STUCK]`/`[DETACH]`).
- **Carimbe overlays dinâmicos com step.** Dado empurrado a um artefato por um agente vivo deve levar timestamp para que entradas obsoletas expirem.
- **Valide com seed determinístico + mediana de N runs.** O mapa é seed-fixo, mas a ordem dos agentes tem variância; use a mediana de vários runs no mesmo seed para distinguir sinal de ruído.
- **Backlog (testabilidade):** a lógica de decisão de movimento mora em `.asl` + no `astar` Java. O `astar` é unit-testável (instanciar `SharedMap`, chamar a op); a lógica `.asl` exige o interpretador Jason. Mover decisão para internal actions Java permitiria testar o sweep de penalidade sem rodar o sim inteiro.

## Related Issues

- Upstream desta solução (mesma branch `fix/livelock-navigation`): ideação [`docs/ideation/2026-06-16-livelock-movimento-agentes-ideation.md`](../../ideation/2026-06-16-livelock-movimento-agentes-ideation.md); brainstorms do escape reativo (tentativa que falhou) e do A*-ciente-de-colega em [`docs/brainstorms/`](../../brainstorms/); planos em [`docs/plans/`](../../plans/) (`...-001-...escape-reativo`, `...-002-...astar-ciente-colega`).
- Pré-requisito: o "passo 1" (não marcar colega como obstáculo, em `perception.asl`) — sem ele o overlay seria redundante e o fantasma voltaria.
- Ponto cego do `check_stuck` (`perception.asl`): só vê mesma-célula por ≥50 steps; a oscilação A↔B o burla. O `check_osc` cobre essa lacuna.
- Adjacente (overlap baixo, não contraditório): [`docs/solutions/performance-issues/eis-perception-pump-starves-agent-action.md`](../performance-issues/eis-perception-pump-starves-agent-action.md) — outro gargalo no mesmo `SharedMap`, mas de origem/solução ortogonais; compartilha a lição "medir antes de corrigir".
</content>
