---
date: 2026-06-17
topic: astar-ciente-colega
---

# A* ciente de colega vivo (overlay de ocupação por penalização)

## Summary

Fazer o A* (`compute_next_move`/`astar` em `src/env/env/SharedMap.java`) **penalizar** as células ocupadas por colegas vivos com custo alto finito, roteando ao redor da congestão em vez de para dentro dela. Isso conserta a órbita de espaço-aberto que a camada reativa de escape não resolvia. O escape (`.asl`, já implementado) permanece como fallback para o corredor real.

## Problem Frame

A camada reativa de escape (escape para vizinho livre + #4 agindo) foi construída e medida, e **falhou no objetivo**: a oscilação subiu (base ~157 → 339 em 200 steps), o stuck caiu a ~0. Diagnóstico (ce-debug, instrumentado + monitor): o escape é um reflexo de 1 passo **sem autoridade sobre a rota**. Ele empurra o agente para o lado, mas no step seguinte — move bem-sucedido, sem bloqueio — o A* normal reroteia direto de volta ao gargalo bloqueado por colega (o passo-1 fez não marcar colega, então o A* nunca aprende a evitá-lo). Escape e A* viram um motor de 2 tempos: o A* empurra contra o bloqueio, o escape desvia, o A* re-puxa. O agente **orbita o gargalo** (o escape disparou 13× na mesma célula).

A correção tem de mudar a **rota** (o que o A* sabe), não o reflexo: o A* precisa enxergar o colega vivo e contornar.

## Key Decisions

- KD1. **Penalizar (custo alto finito), não bloquear.** Célula de colega recebe custo alto no A*: ele contorna quando há alternativa, mas atravessa se for o único caminho — aí o move falha e o escape cede. Degrada bem em corredor 1-wide (sem deadlock) e compõe com o fallback.
- KD2. **Posições via índice de ocupação dentro do SharedMap.** Cada agente empurra a própria posição ao SharedMap a cada step (análogo ao `update_agent_pos` que já alimenta o `SquadCoordinator`). O A* lê do próprio índice — sem acoplar artefatos nem passar posições como argumento por chamada. Posições são absolutas (`absolutePosition: true`), sem conversão de referencial.
- KD3. **Overlay efêmero, sem decay.** O índice reflete a posição corrente de cada colega (sobrescreve a anterior); não há persistência nem decay. Origem (própria célula) e alvo nunca são penalizados (o `astar` já remove ambos do conjunto bloqueado).
- KD4. **Divisão de trabalho com o escape.** O #2 cuida do roteamento no espaço aberto (A* contorna → acaba a órbita); o escape reativo cobre o bloqueio residual de corredor frente-a-frente (cede/espera). É o que faz a dupla cobrir os dois casos.

## Requirements

**Overlay de ocupação no A***

- R1. O A* (`astar`) atribui custo alto finito às células ocupadas por colegas vivos, em vez de removê-las do grafo, roteando ao redor quando houver alternativa e atravessando só quando for o único caminho.
- R2. A célula de origem (posição do agente que chama) e a célula-alvo nunca são penalizadas.
- R3. O overlay é efêmero: usa as posições do step corrente, sem persistência nem decay; a posição de cada colega sobrescreve a anterior.

**Enabler — posições vivas no SharedMap**

- R4. As posições vivas dos colegas ficam disponíveis ao SharedMap por um índice de ocupação interno, atualizado por cada agente a cada step. As coordenadas são absolutas.

**Composição com o escape**

- R5. O escape reativo (`.asl`) permanece ativo como fallback: o A* (#2) resolve o roteamento no espaço aberto; o escape cobre o bloqueio residual de corredor frente-a-frente, onde nenhum agente tem rota alternativa.

**Validação**

- R6. Medir A/B no seed 17 (mediana de 5 runs, 200 steps): base = estado commitado (sem escape, sem #2) vs candidato = #2 + escape ligados. Aprovar se a mediana de `[OSC]` do candidato cai ≥50% da base E a mediana de submits não cai.

## Acceptance Examples

- AE1. **Cobre R1.** Espaço aberto, um colega no caminho direto ao destino, existe rota lateral; o A* contorna e não passa pela célula do colega.
- AE2. **Cobre R1, R5.** Corredor 1-wide com colega à frente sendo o único caminho; o A* roteia pelo corredor (custo aceito), o move falha porque o colega está lá, e o escape cede o passo.
- AE3. **Cobre R2.** Um colega ocupa a célula-alvo do agente; o alvo não é penalizado e o A* roteia até lá normalmente.
- AE4. **Cobre R2.** A própria posição do agente está no índice de ocupação; ela não é penalizada (origem é removida).
- AE5. **Cobre R3.** Um colega se moveu no step anterior; no step corrente o A* penaliza a nova célula dele, e a célula vaga deixa de ser penalizada assim que o colega reporta a nova posição.

## Success Criteria

- Protocolo: A/B no seed 17, 200 steps, **mediana de 5 runs** por lado (RNG do agente). Base = estado commitado (#1 + #4 só-log, sem escape, sem #2). Candidato = #2 + escape ligados.
- Aprovar o #2: mediana de `[OSC]` do candidato cai **≥50%** vs a base, e a mediana de submits **não regride**.
- Score é só sanidade (sem crash); ruidoso demais para virar critério.

## Scope Boundaries

**Adiado / futuro**

- Reserva de células / pathfinding cooperativo (intention-based, ex.: agentes reservam a próxima célula) — reescrita maior; só se penalize+escape não bastarem.

**Fora desta correção**

- Reframe de duas camadas no SharedMap (`staticObstacles`/`liveOccupancy`); dimensão adversária.

**Intocado**

- Estratégia de tarefas/submit; o fix do EIS (`awaitTime`); o passo-1 (não-marcação de colega permanece — o overlay é efêmero, não marcação no mapa).

## Dependencies / Assumptions

- O escape reativo (`.asl`, U1-U4) está implementado e é mantido como fallback; #2 vai por cima dele.
- Posições absolutas (`absolutePosition: true`) — A* e índice no mesmo referencial.
- Staleness ≤ 1 step: o índice atualiza quando o colega reporta; a penalização tolera (não bloqueia) uma posição com 1 step de atraso.
- Agente desativado (evento `clear`) deixa a última posição parada no índice até reativar — raro e breve; aceitável.

## Outstanding Questions

**Deferido para planejamento**

- Valor da penalidade — calibrar empiricamente pela medição A/B (baixo demais = não desvia; alto demais = vira bloqueio).
- Forma do índice de ocupação — novo op no SharedMap vs estender um existente; nome e quando limpar entradas obsoletas (agente desativado).
- Se o overlay entra só no `astar` (escolha do passo) ou também no `astarCost` (usado por `get_nearest_goal_zone`/`get_alternative_goal_zone` para escolher a goal zone) — provavelmente só no `astar`.

## Sources / Research

- Origem/contexto: [docs/brainstorms/2026-06-17-livelock-escape-reativo-requirements.md](docs/brainstorms/2026-06-17-livelock-escape-reativo-requirements.md) (#2 estava em "Adiado").
- Evidência do ce-debug: o escape reativo orbita o gargalo (OSC 157→339; escape disparou 13× na mesma célula, BOXED só 2×); causa = o A* re-puxa porque não conhece o colega.
- Código: `astar`/`astarCost` montam `blocked` só de `obstacles.keySet()` ([SharedMap.java:283](src/env/env/SharedMap.java#L283), [SharedMap.java:326](src/env/env/SharedMap.java#L326)); `compute_next_move` ([SharedMap.java:262](src/env/env/SharedMap.java#L262)); `manhattan_dist`/`wrappedManhattan` ([SharedMap.java:272](src/env/env/SharedMap.java#L272), [SharedMap.java:53](src/env/env/SharedMap.java#L53)).
- Posições vivas: `SquadCoordinator.agentPositions` + `update_agent_pos` ([SquadCoordinator.java:216](src/env/env/SquadCoordinator.java#L216)); chamada por step em [perception.asl:70](src/agt/common/perception.asl#L70).
