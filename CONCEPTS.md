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

### Heading-balanceado
Direção preferencial de exploração derivada do nome do agente (índice mod 4 → N/S/L/O), estática para toda a simulação. Durante `!do_explore`, o agente prioriza frontiers que estejam nessa direção relativa à posição atual; quando o setor preferencial está esgotado, cai para a frontier global mais próxima (viés suave, não partição rígida). Ataca a **exploração redundante** (15 agentes percorrendo as mesmas áreas).

### Handedness
Tiebreak consistente no escape reativo: quando múltiplos candidatos estão empatados na menor distância ao destino, o agente escolhe o primeiro na ordem horária (N → L → S → O), em vez de escolha aleatória. Quebra a simetria do deadlock de corredor frente-a-frente (ambos giram para o mesmo lado → passam), substituindo o jitter aleatório do `pick_escape`.

### Beco sem saída (cul-de-sac / U-shape)
Região livre cercada por paredes em **3 lados, com uma só boca** (um U). É **distinto** de uma trema `¨` (dois obstáculos isolados) e de uma barra dupla `||` (corredor de **duas** saídas = passagem) — só o enclausuramento de uma boca é beco. Detecção em `SharedMap.isCulDeSacFrontier`: flood-fill das células livres a partir da fronteira candidata **bloqueando o anel-1 do agente** (o gargalo); se a região fecha dentro do orçamento → beco. Limitada pela **visão** (issue #27): só o que o agente VÊ (paredes em `obstacles`, via #15) pode ser classificado; U maior que a visão não é detectável → cai no regime de escape.

### Preso (stuck por confinamento)
Modo de falha de **exploração** (sem destino de tarefa) em que o agente oscila num ciclo pequeno **sem progredir** — distinto da oscilação ping-pong (que tem destino ativo) e do livelock de aglomeração. É o **ponto cego do `max_stuck`**: oscilar entre células **livres** não gera `failed_path`. Detectado por **bounding-box**: se as posições recentes (janela de 8-10 steps) cabem num quadrado ≤ 3×3 → preso (`SharedMap.isStuck`). Medido no replay pela métrica de posição `exited_region` (não por `failed_path`).

### Escape por abertura (ray-cast)
Resposta ao "preso" (issue #27 — "não ficar preso, SAIR"): o agente acha a **boca** por ray-casting (a direção cardinal com **mais células livres** até bater parede = a abertura) e mira fundo nela; o A\* (ciente das paredes via #15) roteia para **fora** do beco pela boca. Funciona de **qualquer ângulo de entrada**, pequeno ou grande. Substitui o wall-following literal (regra da mão-direita), que **espirala** em pocket aberto (2-3 células de largura) por não estar colado a uma parede — só funciona em corredor.

## Organização & Roles

### Role organizacional (MOISE+)
O papel de um agente na **estrutura de time** definida pela organização MOISE+ (`hive_org`): squad_leader, collector, assembler, sentinel. Muda via `adoptRole` na org (Ora4MAS). É exigido pelo enunciado, mas **não** afeta as ações disponíveis no simulador. Não confundir com o [[role do cenário]].

### Role do cenário (MAPC)
O papel que o **simulador** atribui ao agente e que **gateia quais ações** ele pode executar: default, worker, constructor, explorer, digger. Os roles são **aditivos** sobre o `default` (Table 1 do livro MAPC 2022): `worker = default + request,attach,connect,disconnect,submit` — ou seja, worker/constructor/explorer/digger **também andam, giram e re-adotam** (herdam o default). Só worker/constructor têm as ações que **pontuam** (`request/attach/connect/submit`). Muda via a ação `adopt`, e **só quando o agente está sobre uma role-zone** (que é fixa a simulação inteira); o role é mantido indefinidamente e pode ser trocado por outro `adopt` numa role-zone. No cenário oficial o agente começa como `default` (sem ações de pontuação) → sem adoção, score 0. A união aditiva é feita **pelo servidor no load** (`GameState.parseRoles` lê o 1º role como base e faz `Role.fromJSON` unir as ações ao default), então no JSON o `worker` lista só os extras mas **anda** — o `massim_2022/server/conf/sim/roles/standard.json` bundled é o role-set **real e usável**. O config degenerado é o `conf/OfficialTestConfig.json` do projeto (default inline com TODAS as ações, p/ dev). Distinto do [[role organizacional]].
