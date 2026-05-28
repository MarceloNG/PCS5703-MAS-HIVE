# 3. Arquitetura do Sistema

Esta seção apresenta a arquitetura do HIVE em três níveis de abstração: a visão geral do sistema e sua integração com o servidor MASSim, a arquitetura interna dos agentes BDI, e a organização dos artefatos de ambiente.

## 3.1 Visão Geral

O HIVE opera como cliente do servidor MASSim, comunicando-se via o middleware **EIS** (*Environment Interface Standard*). A arquitetura geral segue o modelo tripartite do JaCaMo [6]:

```
┌─────────────────────────────────────────────────────────────┐
│                     Servidor MASSim                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Percepts │  │  Tasks   │  │  Norms   │  │  Score   │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
└───────┼──────────────┼──────────────┼──────────────┼────────┘
        │    JSON/TCP   │              │              │
        ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Middleware EIS                              │
│         (traduz percepts MASSim → crenças Jason)             │
└───────┬──────────────┬──────────────┬──────────────┬────────┘
        │              │              │              │
        ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│                 PLATAFORMA JaCaMo                             │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │              DIMENSÃO AGENTE (Jason)                    │  │
│  │                                                        │  │
│  │  Squad 1          Squad 2          Squad 3   Sentinels │  │
│  │  ┌──┐┌──┐┌──┐    ┌──┐┌──┐┌──┐    ┌──┐┌──┐  ┌──┐┌──┐ │  │
│  │  │L1││C1││C2│    │L2││C3││C4│    │L3││C5│  │S1││S2│ │  │
│  │  └──┘└──┘└──┘    └──┘└──┘└──┘    └──┘└──┘  └──┘└──┘ │  │
│  │  ┌──┐             ┌──┐             ┌──┐      ┌──┐     │  │
│  │  │A1│             │A2│             │A3│      │S3│     │  │
│  │  └──┘             └──┘             └──┘      └──┘     │  │
│  └────────────────────┬───────────────────────────────────┘  │
│                       │ opera / observa                      │
│  ┌────────────────────▼───────────────────────────────────┐  │
│  │           DIMENSÃO AMBIENTE (CArtAgO)                   │  │
│  │                                                        │  │
│  │  ┌─────────────┐ ┌───────────┐ ┌──────────────────┐   │  │
│  │  │  SharedMap   │ │ TaskBoard │ │ SquadCoordinator │   │  │
│  │  │ A*, mapa,   │ │ leilão,   │ │ pool soloists,   │   │  │
│  │  │ fronteiras, │ │ registro, │ │ busy/free,       │   │  │
│  │  │ obstáculos  │ │ bids      │ │ claim/release    │   │  │
│  │  └─────────────┘ └───────────┘ └──────────────────┘   │  │
│  │  ┌───────────────┐                                     │  │
│  │  │ HiveDashboard │                                     │  │
│  │  │ WebSocket,    │                                     │  │
│  │  │ métricas      │                                     │  │
│  │  └───────────────┘                                     │  │
│  └────────────────────────────────────────────────────────┘  │
│                       │ regula                               │
│  ┌────────────────────▼───────────────────────────────────┐  │
│  │           DIMENSÃO ORGANIZAÇÃO (MOISE+)                 │  │
│  │                                                        │  │
│  │  Papéis: squad_leader, collector, assembler, sentinel  │  │
│  │  Grupos: squad_group ×3, sentinel_group ×1             │  │
│  │  Esquemas: exploration, task_execution, defense         │  │
│  │  Normas: obrigações e permissões por papel              │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

*L = Leader, C = Collector, A = Assembler, S = Sentinel*

O fluxo de dados a cada step da simulação é:

1. O servidor MASSim envia **percepts** (posição, coisas visíveis, tarefas, normas, energia) via JSON/TCP;
2. O middleware EIS traduz os percepts em **crenças Jason** (e.g., `+thing(2, -1, dispenser, b0)`);
3. Cada agente executa seu **ciclo BDI** [4]: atualiza crenças → seleciona evento → unifica plano → executa ação;
4. Durante a deliberação, o agente pode **operar artefatos** CArtAgO [11] (e.g., `mark_obstacle`, `compute_next_move`);
5. O agente envia sua **ação** de volta ao servidor via EIS (e.g., `move(n)`, `submit(task1)`);
6. O MOISE+ [9] **regula** quais planos e ações são admissíveis conforme o papel do agente.

## 3.2 Arquitetura Interna do Agente

Cada agente BDI é composto por módulos AgentSpeak(L) organizados em camadas de prioridade, implementando uma **arquitetura híbrida** que combina deliberação BDI com hierarquia reativa [1]:

```
┌─────────────────────────────────────────────┐
│              Agente BDI (Jason)              │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │         Base de Crenças               │  │
│  │  my_pos(X,Y)  known_task(...)         │  │
│  │  thing(X,Y,T,D)  attached(D,T)       │  │
│  │  my_role(R)  pending_submit(...)      │  │
│  └──────────────────┬────────────────────┘  │
│                     │                       │
│  ┌──────────────────▼────────────────────┐  │
│  │    Camadas de Planos (por prioridade) │  │
│  │                                       │  │
│  │  P0 ▓▓▓ Sobrevivência / Desativação  │  │
│  │  P1 ▓▓▓ Normas (detach excedente)    │  │
│  │  P2 ▓▓▓ Submissão em goal zone       │  │
│  │  P3 ▓▓▓ Connect (multi-bloco)        │  │
│  │  P4 ░░░ Coleta (request + attach)    │  │
│  │  P5 ░░░ Navegação (A* / greedy)      │  │
│  │  P6 ░░░ Exploração (fronteira)       │  │
│  │                                       │  │
│  │  ▓ = Alta prioridade (preemptivo)     │  │
│  │  ░ = Baixa prioridade (background)    │  │
│  └──────────────────┬────────────────────┘  │
│                     │                       │
│  ┌──────────────────▼────────────────────┐  │
│  │       Módulos AgentSpeak (.asl)       │  │
│  │                                       │  │
│  │  perception.asl     ← percepção       │  │
│  │  connect_protocol.asl ← submissão     │  │
│  │  collection.asl     ← coleta          │  │
│  │  navigation.asl     ← navegação/A*    │  │
│  │  [squad_leader.asl] ← (só líderes)   │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

A seleção de planos segue a semântica padrão do Jason [8]: o interpretador percorre os planos na ordem em que aparecem no código, e seleciona o **primeiro plano aplicável** cuja guarda de contexto é satisfeita pelas crenças atuais. Essa mecânica garante que os planos de sobrevivência e submissão (prioridade alta, no topo dos arquivos) sempre têm precedência sobre exploração e coleta.

## 3.3 Arquitetura dos Artefatos

Os artefatos CArtAgO encapsulam toda a lógica de infraestrutura, separando-a da lógica deliberativa dos agentes [11, 13]:

```
                    15 Agentes BDI
                   /    |    |    \
          opera   /     |    |     \  opera
                 ▼      ▼    ▼      ▼
    ┌────────────────────────────────────────┐
    │           Workspace CArtAgO            │
    │                                        │
    │  ┌──────────────────────────────────┐  │
    │  │          SharedMap               │  │
    │  │  ┌──────────┐  ┌─────────────┐  │  │
    │  │  │ Grid 40² │  │ A* Toroidal │  │  │
    │  │  │ visited  │  │ heurística  │  │  │
    │  │  │ obstacle │  │ Manhattan   │  │  │
    │  │  │ dispenser│  │ wrapping    │  │  │
    │  │  │ goalzone │  │ 2000 iter   │  │  │
    │  │  └──────────┘  └─────────────┘  │  │
    │  │  ┌──────────┐  ┌─────────────┐  │  │
    │  │  │ Frontier │  │  Obstacle   │  │  │
    │  │  │ Cache    │  │  Decay (30) │  │  │
    │  │  └──────────┘  └─────────────┘  │  │
    │  └──────────────────────────────────┘  │
    │  ┌──────────────┐ ┌─────────────────┐  │
    │  │  TaskBoard   │ │ SquadCoord.     │  │
    │  │              │ │                 │  │
    │  │ register_task│ │ find_free_solo  │  │
    │  │ place_bid    │ │ claim_task_solo │  │
    │  │ resolve_auct.│ │ mark_busy/free  │  │
    │  │ remove_expir.│ │ release_agent   │  │
    │  └──────────────┘ └─────────────────┘  │
    │  ┌──────────────┐                      │
    │  │HiveDashboard │ ◄── WebSocket 8080   │
    │  └──────────────┘                      │
    └────────────────────────────────────────┘
```

Todas as operações sobre um mesmo artefato são **serializadas** pelo runtime CArtAgO [12]: se dois agentes invocam `mark_obstacle` no `SharedMap` simultaneamente, a segunda chamada bloqueia até que a primeira complete. Essa propriedade é a fonte do gargalo de desempenho documentado na Seção 5.5 dos resultados.

## 3.4 Fluxo de Coordenação por Leilão

O mecanismo de alocação de tarefas integra os três artefatos em um fluxo coordenado baseado no Contract Net Protocol [22]:

```
  Servidor MASSim                 TaskBoard           SquadCoordinator
       │                             │                       │
       │  nova task (percept)        │                       │
       ├────────────────────────────►│                       │
       │                             │  register_task        │
       │                             │◄─────────────         │
       │                             │                       │
       │    Squad Leader L1          │                       │
       │         │                   │                       │
       │         │  place_bid(score) │                       │
       │         │──────────────────►│                       │
       │         │                   │                       │
       │    Squad Leader L2          │                       │
       │         │  place_bid(score) │                       │
       │         │──────────────────►│                       │
       │         │                   │                       │
       │    L1 (primeiro a chegar)   │                       │
       │         │ resolve_auction   │                       │
       │         │──────────────────►│                       │
       │         │◄────── winner=L1  │                       │
       │         │                   │                       │
       │         │  find_free_soloist(dispX, dispY)          │
       │         │──────────────────────────────────────────►│
       │         │◄────── agentName=C3                       │
       │         │                   │                       │
       │         │  .send(C3, achieve, go_collect(...))      │
       │         │─────────────────────────────────►  C3     │
       │         │                                    │      │
       │                                              │      │
       │   C3 navega, coleta, submete                 │      │
       │◄─────────────────────────────────── submit   │      │
       │                                              │      │
```

O protocolo apresenta duas adaptações em relação ao Contract Net clássico [22]:

1. **Manager distribuído**: Qualquer líder pode invocar `resolve_auction` — o primeiro a fazê-lo obtém o resultado. Isso elimina um ponto central de falha;
2. **Contractor universal**: O `SquadCoordinator` seleciona o soloist mais próximo do dispenser por distância Manhattan toroidal, independentemente do squad ou papel do agente — maximizando a utilização.

## 3.5 Ciclo de Vida de uma Task (1 bloco)

O diagrama abaixo ilustra o ciclo completo de uma task de 1 bloco, desde o anúncio pelo servidor até a submissão:

```
  Step N    │ Servidor anuncia task T1 (deadline=N+200, reward=10, bloco=b0)
            │
  Step N+1  │ Líder L1 detecta known_task(T1,...) → place_bid → resolve_auction
            │ L1 invoca find_free_soloist → obtém C3 (mais próximo do dispenser_b0)
            │ L1 envia .send(C3, achieve, go_collect(T1, b0, dx, dy))
            │
  Step N+2  │ C3 recebe go_collect → atualiza intenção: !go_to(dx, dy)
   ...      │ C3 navega via A* em direção ao dispenser_b0
  Step N+k  │
            │
  Step N+k  │ C3 chega ao dispenser → executa request(direction) + attach(direction)
            │ C3 atualiza intenção: !go_to(gx, gy) [goal zone mais próxima]
            │
   ...      │ C3 navega com bloco acoplado em direção à goal zone
  Step N+m  │
            │
  Step N+m  │ C3 chega à goal zone com bloco correto
            │ C3 verifica: estou em goal zone? bloco correto acoplado? task ativa?
            │ C3 executa submit(T1) → servidor valida → +10 pontos
            │
  Step N+m+1│ C3 fica livre → volta a explorar ou pega nova task via self-assignment
```

**Tempo típico**: 30–80 steps do anúncio à submissão, dependendo da distância ao dispenser e da densidade de obstáculos.

## 3.6 Mapeamento Código-Fonte ↔ Arquitetura

A tabela abaixo relaciona os componentes arquiteturais aos arquivos do projeto:

| Componente | Arquivo(s) | Dimensão |
|---|---|---|
| Percepção e atualização de crenças | `src/agt/common/perception.asl` | Agente |
| Submissão, normas, connect | `src/agt/common/connect_protocol.asl` | Agente |
| Coleta de blocos | `src/agt/common/collection.asl` | Agente |
| Navegação A* e exploração | `src/agt/common/navigation.asl` | Agente |
| Lógica do líder (scan, delegação) | `src/agt/squad_leader.asl` | Agente |
| Mapa compartilhado e A* | `src/env/env/SharedMap.java` | Ambiente |
| Quadro de tarefas e leilão | `src/env/env/TaskBoard.java` | Ambiente |
| Pool de soloists | `src/env/env/SquadCoordinator.java` | Ambiente |
| Dashboard WebSocket | `src/env/env/HiveDashboard.java` | Ambiente |
| Especificação organizacional | `src/org/org.xml` | Organização |
| Configuração de agentes | `hive.jcm` | JaCaMo |
| Configuração do servidor | `conf/TestConfig.json` | MASSim |
| Configuração EIS | `eismassimconfig.json` | Middleware |
