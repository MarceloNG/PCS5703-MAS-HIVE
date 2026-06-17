# Concepts

Shared domain vocabulary for this project — entities, named processes, and status concepts with project-specific meaning. Seeded with core domain vocabulary, then accretes as ce-compound and ce-compound-refresh process learnings; direct edits are fine. Glossary only, not a spec or catch-all.

## Navegação

### SharedMap
O modelo de mundo compartilhado que todos os agentes leem e escrevem: o mapa conhecido (paredes, goal zones, dispensers, fronteiras), o pathfinding (A*) e a ocupação viva dos colegas. É a fonte única de verdade para decisões de navegação — um artefato compartilhado, não a visão local de um agente.

### Livelock de movimento
Modo de falha em que os agentes **agem** todo step mas **não progridem** espacialmente ao se aglomerar perto de paredes ou uns dos outros. Distinto de "stuck" (congelado na mesma célula): no livelock o agente se move, só não chega a lugar nenhum útil.

### Oscilação (ping-pong)
O padrão concreto por trás do livelock: o agente alterna entre duas células (A↔B) com um destino ativo, sem avançar. É o ponto cego da detecção de stuck (que só vê a mesma célula por muitos steps); detectada separadamente por comparar a posição atual com a de dois steps atrás.

### Overlay de ocupação
A consciência efêmera de colega no A*: as células ocupadas por colegas vivos recebem **penalidade de custo** (não bloqueio) no pathfinding, por step, para que a rota contorne a congestão. Exclui a própria célula e o destino. É efêmero (expira quando o colega não reporta mais), nunca vira obstáculo persistente — ao contrário das paredes.

### Escape reativo
Camada `.asl` de último recurso: quando um move falha ou a oscilação dispara, o agente vai para um vizinho livre (pela percepção local) que mais aproxima do destino, ou cede o passo se encurralado. É **fallback** para corredor frente-a-frente — o roteamento no espaço aberto é responsabilidade do A* (via overlay de ocupação), não do reflexo.

## Organização & Roles

### Role organizacional (MOISE+)
O papel de um agente na **estrutura de time** definida pela organização MOISE+ (`hive_org`): squad_leader, collector, assembler, sentinel. Muda via `adoptRole` na org (Ora4MAS). É exigido pelo enunciado, mas **não** afeta as ações disponíveis no simulador. Não confundir com o [[role do cenário]].

### Role do cenário (MAPC)
O papel que o **simulador** atribui ao agente e que **gateia quais ações** ele pode executar: default, worker, constructor, explorer. Só worker/constructor têm `request/attach/connect/submit` (as ações que pontuam). Muda via a ação `adopt`, e só quando o agente está sobre uma **role-zone**. No cenário oficial o agente começa como `default` (sem ações de pontuação) → sem adoção, score 0. Distinto do [[role organizacional]].
