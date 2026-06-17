---
date: 2026-06-17
topic: livelock-escape-reativo
---

# Camada reativa de escape para o livelock de movimento

## Summary

Adicionar uma camada **reativa de escape** à navegação dos agentes: quando um `move` falha ou o detector de oscilação dispara, o agente escolhe — pela própria visão — um vizinho adjacente livre que mais o aproxima do destino, evitando a célula de onde acabou de quicar; se estiver encurralado, cede o passo. Tudo em `.asl`. O núcleo do A* (`compute_next_move`) e o `SharedMap` ficam intactos.

## Problem Frame

Os agentes entram em livelock de movimento ao se aglomerar ou perto de paredes: agem a cada step mas não progridem. A medição (seed 17, 200 steps, com `#1` e o detector `#4` em modo só-log) mostrou que o modo de falha **dominante** é a oscilação A↔B — **157 oscilações contra 8 disparos de stuck (~20×)**. O `check_stuck`, que exige a mesma célula por ≥50 steps, é cego a esse padrão (o agente troca de célula, só não avança).

Hoje, ao bloquear, o agente tenta **uma** direção perpendicular fixa ([navigation.asl:81-95](src/agt/common/navigation.asl#L81-L95)) sem verificar se a célula está de fato livre, e o detector de oscilação apenas registra log — nada reage a ele. O resultado é que o agente continua a quicar até o detach forçado de 50 steps, perdendo o bloco.

## Key Decisions

- **Reativo antes de proativo, com medição como gate.** Construímos só a camada de escape (reação quando trava/oscila), não a consciência de colega no A*. É o caminho de menor risco para um dono novato em Java, e a queda de oscilação medida é o que decide se vale escalar para a reescrita em Java (`#2`). Alinha com a postura "só o passo, e medir".
- **A verdade de "vizinho livre" vem da percepção local, não do `SharedMap`.** As 4 células adjacentes estão sempre dentro da visão 5, então a crença `thing(DX,DY,...)` do próprio agente é ground-truth em tempo real para colega vivo, bloco e parede. O `SharedMap` seria pior aqui: sem o enabler (adiado) ele não rastreia ocupação viva de colega, só obstáculo estático (e poluído por colisão). Mantém a camada 100% em `.asl`.
- **O escape é disparado por dois gatilhos e tem memória anti-volta.** Falha de `move` (substituindo a perpendicular fraca de hoje) e detecção de oscilação roteiam para o mesmo escape. A célula-parceira da oscilação é excluída dos candidatos (reusando o estado `osc_p1`/`osc_p2` que o detector já mantém) — sem isso o agente volta ao ping-pong e o detector volta a ser só-log.
- **Encurralado → ceder, não se debater.** Sem vizinho livre útil, o agente emite `skip` e cede passagem; um movimento aleatório após K steps encurralados quebra deadlock de simetria (todos cederem juntos). O detach forçado de 50 steps permanece como rede de segurança no extremo.

## Requirements

**Escape para vizinho livre (#3)**

- R1. O escape é acionado em dois casos: quando o último `move` falhou (hoje sinalizado por `last_move_blocked`) e quando o detector de oscilação dispara.
- R2. Uma direção candidata só é legal quando, na percepção local, a célula-alvo do agente **e** as células futuras de todo bloco anexado estão livres — sem `thing` percebido de obstáculo, entidade (colega/oponente) ou bloco. Há um `attached` por bloco carregado e um agente pode carregar estrutura multi-célula, então **todos** os `attached` devem ser iterados. Os adjacentes do agente e dos blocos estão sempre na visão 5.
- R3. Entre as direções candidatas legais, o agente move-se para a que mais aproxima do destino atual (distância no grid toroidal), excluindo a célula-parceira da oscilação dos candidatos.
- R4. Quando nenhuma direção candidata legal útil existe (todas bloqueadas, ou a única livre é a célula-parceira excluída), o agente emite `skip` naquele step em vez de mover.
- R5. Após K steps consecutivos encurralado (R4), o agente faz um único movimento aleatório **entre as direções legais** (livres por R2) — relaxando só o critério "aproxima do destino" e ainda excluindo a célula-parceira da oscilação — para quebrar simetria; se nenhuma for legal, continua em `skip`. K é parametrizável.

**Cobertura (onde o escape age)**

- R6. O escape substitui a reação a bloqueio em todos os handlers de navegação com destino ativo — navegação genérica ([navigation.asl:81](src/agt/common/navigation.asl#L81)), coleta `collecting` ([collection.asl:71](src/agt/common/collection.asl#L71)) e transporte para submit `pending_submit` ([connect_protocol.asl:276](src/agt/common/connect_protocol.asl#L276)) —, que hoje usam desvio perpendicular/aleatório. O fallback de nível de destino do submit (trocar de goal zone após bloqueios repetidos) é **mantido** como complementar ao escape de nível de célula.

**Resposta à oscilação (#4 agindo)**

- R7. O detector de oscilação deixa de ser só-log. Em `+position` ele **não emite ação**: apenas registra um pedido de escape pendente e atualiza `osc_p1`/`osc_p2`. A ação de escape sai **somente no próximo `+step` que navega com destino ativo**, que consome o pedido; se nesse meio-tempo o destino é alcançado ou o agente entra em estado sem navegação, o pedido é descartado.

**Integração e invariantes**

- R8. Uma ação por step: o escape (pela falha de `move` ou pelo pedido pendente de oscilação) pré-empta o movimento normal do step. O agente emite exatamente uma ação por step — o `move` de escape ou o `skip` — nunca uma segunda além do `compute_next_move` padrão.
- R9. O A* (`compute_next_move`) é **stateless por step**: recalcula a direção a cada `+step`, sem caminho persistido. Quando o escape dispara, `compute_next_move` apenas não é chamado naquele step (o handler de escape emite a ação no lugar) — nada é cacheado ou descartado. O núcleo do A* e o `SharedMap` não são alterados; o detach forçado de 50 steps permanece como rede de segurança.
- R10. A camada se aplica à navegação com destino ativo (`has_destination`); a exploração pura sem destino mantém o comportamento atual.

## Acceptance Examples

- AE1. **Cobre R1, R3, R7.** Dado um agente oscilando entre A e B rumo a um destino; quando o detector dispara em A; então no próximo `+step` o agente move-se para uma direção legal lateral que aproxima do destino, e não de volta para B.
- AE2. **Cobre R1, R2, R3.** Dado que o `move` para a célula-alvo falhou porque um colega a ocupa; quando há outra direção legal; então o agente move-se para a direção legal mais próxima do destino.
- AE3. **Cobre R2.** Dado um agente carregando um bloco; quando uma direção é livre para a célula do agente mas a célula futura do bloco está bloqueada; então essa direção não é candidata legal.
- AE4. **Cobre R4.** Dado que não há direção candidata legal; quando o escape é acionado; então o agente emite `skip` e permanece na célula.
- AE5. **Cobre R5.** Dado que o agente ficou encurralado por K steps seguidos e existe ao menos uma direção fisicamente livre; quando o escape é acionado de novo; então ele faz um movimento aleatório entre as livres; se nenhuma estiver livre, continua em `skip`.
- AE6. **Cobre R3, R4.** Dado que a única direção livre é a célula-parceira da oscilação; quando o escape é acionado; então o agente cede (R4) em vez de voltar a quicar.
- AE7. **Cobre R2.** Dado um agente carregando dois blocos (dois `attached`); quando uma direção deixa livre a célula de um bloco mas não a do outro; então essa direção não é candidata legal.

## Success Criteria

- Protocolo: comparar **N = 5 runs** no seed 17, 200 steps, **base vs camada ligada**, pela **mediana** de cada métrica (RNG do agente — um run só não basta). Base = comportamento atual (perpendicular fraca + `#4` só-log, onde os 157 foram medidos); camada ligada = escape substituindo essa reação.
- Aprovar a camada: mediana de `[OSC]` cai **pelo menos à metade** da base (157 → ≤ ~78) e a mediana de `[STUCK]` não sobe acima da base (8). O alvo de 50% é calibração inicial — ajustável após o primeiro par de medições.
- Não-regressão: mediana de submits não cai vs a base. Score é só sanidade (sem crash); é ruidoso demais em 1 run para virar critério.
- Gate para o `#2` (decisão de maior custo — ancorar em métrica **causada por movimento**, não em submits): escalar só se, com `[OSC]` em queda, a mediana de **detaches forçados de carregador** (perda de bloco por stuck) **não** cair — sinal de que o movimento continua sendo o gargalo. "Submits estáveis após queda de `[OSC]`" é **inconclusivo** (pode refletir limite de estratégia, não de movimento) e não dispara `#2` por si só.

## Scope Boundaries

**Adiado (atrás da medição desta camada)**

- `#2` — A* enxergar colegas vivos como bloqueio efêmero.
- Enabler — expor as posições vivas dos colegas ao `SharedMap`.

**Fora da identidade desta correção**

- Reframe de duas camadas no `SharedMap` (`staticObstacles` / `liveOccupancy`).
- Dimensão adversária (ADV-*) — vira track próprio, junto com "jogar ofensivo".

**Intocado**

- Estratégia de tarefas/submit.
- O fix do EIS (`awaitTime`) já feito.

## Dependencies / Assumptions

- Uma ação por step: MASSim/CArtAgO aceita uma ação por agente por step, então o escape precisa substituir o move normal, não somá-lo (R8).
- O detector de oscilação já existe e mantém `osc_p1`/`osc_p2`; tanto a detecção quanto a exclusão anti-volta (R3) dependem desse estado.
- `#1` (não marcar obstáculo-fantasma por falha de movimento) já está em vigor e permanece.
- Seed 17 dá mapa determinístico; o comportamento do agente não é determinístico, daí a exigência de múltiplos runs. Limitação conhecida: o gate (construir ou não o `#2`) repousa numa **única topologia** — N=5 trata o RNG, não a geometria do mapa.

## Outstanding Questions

**Deferido para planejamento**

- Distância toroidal: o "mais próximo do destino" do escape (R3) deve usar a **mesma** distância que a heurística do A*, senão o escape briga com a rota e gera nova oscilação. Os três revisores marcaram como load-bearing — resolver antes de codar o R3.
- Ponte de referencial: `osc_p1`/`osc_p2` são absolutos (X,Y) e a percepção `thing` é relativa (DX,DY) — a exclusão anti-volta (R3) precisa converter entre os dois.
- Sequenciamento com o submit: o escape de nível de célula coexiste com a cadência rotate + troca-de-goal-zone do `connect_protocol` (`nav_block_count`) — definir a ordem para não conflitar.
- Desempate quando duas direções legais são equidistantes do destino: ordem fixa pode fazer dois agentes frente-a-frente escolherem a mesma lateral e formarem nova oscilação — avaliar jitter.
- Valor de K (steps encurralado antes do movimento aleatório) — começar em ~3 e ajustar empiricamente.
- AE2 independe do contador K (vale sempre que existe direção legal; K só rege o caminho de ceder do R5).

## Sources / Research

- Ideação de origem: [docs/ideation/2026-06-16-livelock-movimento-agentes-ideation.md](docs/ideation/2026-06-16-livelock-movimento-agentes-ideation.md).
- Escape fraco atual: [navigation.asl:81-95](src/agt/common/navigation.asl#L81-L95); chamada do A*: [navigation.asl:101](src/agt/common/navigation.asl#L101) e [navigation.asl:112](src/agt/common/navigation.asl#L112).
- `#1` + detector de oscilação (`check_osc`/`osc_shift`): [src/agt/common/perception.asl](src/agt/common/perception.asl).
- O A* lê obstáculos só de `obstacles.keySet()` e nunca lê as células percebidas — aprende parede só por colisão: [src/env/env/SharedMap.java](src/env/env/SharedMap.java).
- Medido (seed 17): oscilação 157 / 200 steps ≈ 20× stuck (8); `#1` cortou 570 marcações-fantasma (36%); score variou 60 vs 20 no mesmo comportamento.
