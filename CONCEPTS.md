# Concepts

Shared domain vocabulary for this project — entities, named processes, and status concepts with project-specific meaning. Seeded with core domain vocabulary, then accretes as ce-compound and ce-compound-refresh process learnings; direct edits are fine. Glossary only, not a spec or catch-all.

## Navegação

### SharedMap
O modelo de mundo de um agente: o mapa conhecido (paredes, goal zones, dispensers, fronteiras), o pathfinding (A*) e a ocupação viva percebida. A partir da Fase D (U3) é **uma instância por-agente** (`map_<nome>`), cada uma um **frame local privado** — não há mais um artefato único compartilhado pelos 15 (sem posição absoluta não existe frame global pré-fusão). A partilha de descobertas entre agentes é a **fusão (U9)**, deferida, que entra como tradução por offset (`translateCells`). A coordenação entre agentes pré-fusão segue por `task_board` e `squad_coordinator` (esses **continuam compartilhados**) — mas mensagens que carregam **coordenadas** são cross-frame (ver "frame local").

### Frame local
O referencial de coordenadas privado de um agente no oficial (`absolutePosition:false`): origem (0,0) no início, posição mantida por dead-reckoning (`dr_pos`). Coordenadas só são comparáveis **dentro do mesmo frame** — trocar coordenadas entre agentes sem a fusão (U9) é inválido (origens distintas).

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
