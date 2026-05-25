# HIVE — Hierarchical Intelligent Virtual Ensemble

**Sistema Multi-Agente para o Multi-Agent Programming Contest 2022 (Agents Assemble III)**

```mermaid
graph LR
    subgraph HIVE["🐝 HIVE MAS"]
        J["Jason<br/>15 Agentes BDI"]
        M["MOISE+<br/>Organização"]
        C["CArtAgO<br/>Artefatos"]
    end
    
    MASSIM["🎮 MASSim Server<br/>Agents Assemble 2022"]
    DASH["📊 Dashboard<br/>React + Three.js"]
    
    HIVE -->|"TCP :12300<br/>15 conexões"| MASSIM
    HIVE -->|"WebSocket :8765<br/>JSON broadcast"| DASH
    
    style HIVE fill:#1168bd,color:#fff
    style MASSIM fill:#2d5e3f,color:#fff
    style DASH fill:#6b21a8,color:#fff
```

---

## Informações Acadêmicas

| | |
|---|---|
| **Disciplina** | PCS 5703 — Sistemas Multi-Agentes |
| **Instituição** | Escola Politécnica da Universidade de São Paulo (EPUSP) |
| **Departamento** | Engenharia de Computação e Sistemas Digitais |
| **Período** | 1º Quadrimestre de 2026 |
| **Exercício** | 2º Exercício Prático — Aplicação de programação orientada a multi-agentes no MAPC |
| **Entrega** | 02/06/2026 |
| **Enunciado** | [`doc/5703_ex02_26.pdf`](doc/5703_ex02_26.pdf) |

---

## Visão Geral

O **HIVE** é um sistema multi-agente com arquitetura de enxame hierárquico desenvolvido para competir no cenário **Agents Assemble** do Multi-Agent Programming Contest (MAPC) 2022. Utiliza o arcabouço **JaCaMo** (Jason + CArtAgO + MOISE+) com 15 agentes BDI organizados em 3 esquadrões autônomos + pool de soloists.

### Características Principais

- **15 agentes BDI** com 4 roles especializados (squad_leader, collector, assembler, sentinel)
- **3 esquadrões autônomos** de 4 membros + 3 sentinelas no pool de soloists
- **Leilão distribuído** via artefato `TaskBoard` para alocação ótima de tarefas
- **Pool de soloists universal** — qualquer agente livre executa tasks simples
- **Mapa compartilhado** com A* e exploração por fronteira em grid toroidal 40×40
- **Connect sincronizado** para tasks multi-block com protocolo de comunicação
- **Re-submissão automática** de tarefas para multiplicação de pontos
- **Dashboard React em tempo real** com visualização 2D/3D via WebSocket
- **Resiliência multi-nível** com retry, timeout, stuck detection e energy conservation

---

## Arquitetura do Sistema

### Diagrama de Contexto (C4 Nível 1)

```mermaid
graph TB
    subgraph Atores
        TEAM["👤 Time HIVE<br/>Desenvolvedores"]
    end

    subgraph Sistema
        HIVE["🔷 HIVE MAS<br/>15 agentes BDI<br/>Squads + Soloists"]
    end

    subgraph Externos["Sistemas Externos"]
        MASSIM["🔶 MASSim Server<br/>Grid 40×40<br/>Tasks, Normas, Clear Events"]
        MONITOR["🔶 Web Monitor<br/>HTTP :8000"]
        DASHBOARD["🟢 Hive Dashboard<br/>React :5173"]
    end

    TEAM -->|"Configura (JCM + JSON)"| HIVE
    HIVE -->|"TCP/JSON :12300<br/>15 conexões"| MASSIM
    HIVE -->|"WebSocket :8765<br/>JSON broadcast"| DASHBOARD
    MASSIM -->|"HTTP :8000"| MONITOR
    TEAM -->|"Monitora"| DASHBOARD

    style HIVE fill:#1168bd,color:#fff
    style MASSIM fill:#666,color:#fff
    style MONITOR fill:#666,color:#fff
    style DASHBOARD fill:#438a5e,color:#fff
```

### Diagrama de Containers (C4 Nível 2)

```mermaid
graph TB
    subgraph HIVE_MAS["HIVE MAS — JaCaMo Runtime (JDK 21)"]
        JASON["🧠 Jason Engine<br/>15 agentes AgentSpeak<br/>squad_leader×3, collector×6<br/>assembler×3, sentinel×3"]
        
        MOISE["📋 MOISE+<br/>hive_org.xml<br/>4 roles, 2 groups<br/>3 schemes, 5 norms"]
        
        CARTAGO["🏗️ CArtAgO Workspace<br/>SharedMap (A*, greedy)<br/>TaskBoard (leilão)<br/>SquadCoordinator (pool)<br/>HiveDashboard (WS)"]
        
        EIS["🔌 EISAccess ×15<br/>Singleton EnvironmentInterface<br/>+ Translator"]
    end

    MASSIM["MASSim Server<br/>TCP :12300"]
    DASH["Dashboard React<br/>WS :8765"]

    JASON -->|"Observa / Opera"| CARTAGO
    JASON -->|"Percepts / Actions"| EIS
    JASON -.->|"Obrigações"| MOISE
    EIS -->|"JSON/TCP"| MASSIM
    CARTAGO -->|"Broadcast JSON"| DASH

    style JASON fill:#1168bd,color:#fff
    style MOISE fill:#1168bd,color:#fff
    style CARTAGO fill:#1168bd,color:#fff
    style EIS fill:#1168bd,color:#fff
    style MASSIM fill:#666,color:#fff
    style DASH fill:#438a5e,color:#fff
```

### Diagrama de Componentes (C4 Nível 3)

```mermaid
graph TB
    subgraph Agents["Jason — 15 Agentes BDI"]
        SL["squad_leader ×3<br/>Leilão + Delegação"]
        CO["collector ×6<br/>Coleta + Meeting Point"]
        AS["assembler ×3<br/>Connect + Submit"]
        SE["sentinel ×3<br/>Solo Tasks + Patrulha"]
        
        subgraph Common["common/ — Módulos Compartilhados"]
            CP["connect_protocol<br/>(PRIORIDADE MÁX)"]
            CL["collection<br/>(Request/Attach)"]
            NV["navigation<br/>(Greedy/Frontier)"]
            PR["perception<br/>(Percepts)"]
            DH["dashboard_hooks<br/>(WS Report)"]
            CM["communication<br/>(Sync Connect)"]
        end
    end
    
    subgraph Artifacts["CArtAgO — Artefatos Java"]
        SM["SharedMap<br/>ConcurrentHashMap<br/>A* (2000 iter)<br/>Greedy fallback"]
        TB["TaskBoard<br/>Bids + Auction<br/>resolve_auction()"]
        SC["SquadCoordinator<br/>find_free_soloist()<br/>signal_ready()"]
        HD["HiveDashboard<br/>WebSocket :8765<br/>broadcast()"]
    end
    
    subgraph IA["Internal Actions (hive.*)"]
        AD["AdjacentDirection<br/>Toroidal 40×40"]
        CC["ConnectCalculator<br/>RelX, RelY"]
        DC["DirectionCalculator<br/>Greedy n/s/e/w"]
        PF["PathFinder<br/>A* backup"]
        PM["PatternMatcher<br/>Block pattern"]
    end

    SL & CO & AS & SE --> CP & CL & NV & PR & DH
    SL & CO & AS & SE --> SM & TB & SC & HD
    SL & CO & AS & SE --> AD & CC & DC & PF & PM
```

---

## Organização MOISE+

```mermaid
graph TB
    subgraph SS["Especificação Estrutural"]
        HT["hive_team (root)"]
        SG1["squad_group ×3<br/>min=2, max=4"]
        SNG["sentinel_group<br/>min=1, max=2"]
        
        HT --> SG1 & SNG
        
        SG1 --> R_SL["squad_leader (1)"]
        SG1 --> R_CO["collector (1-2)"]
        SG1 --> R_AS["assembler (1)"]
        SNG --> R_SE["sentinel (1-3)"]
        
        R_SL -->|"authority"| R_CO
        R_SL -->|"authority"| R_AS
        R_CO -->|"communication"| R_AS
    end
    
    subgraph FS["Especificação Funcional"]
        S1["exploration_scheme<br/>map_explored (parallel)"]
        S2["task_execution_scheme<br/>task_submitted (sequence)"]
        S3["defense_scheme<br/>team_protected (parallel)"]
        
        S1 --> G1["dispensers_found (ttf=200)"]
        S1 --> G2["goal_zones_found (ttf=200)"]
        S1 --> G3["role_zones_found (ttf=200)"]
        
        S2 --> G4["blocks_collected (ttf=100)"]
        G4 --> G5["blocks_assembled (ttf=50)"]
        G5 --> G6["pattern_submitted (ttf=30)"]
        
        S3 --> G7["goal_zones_guarded"]
        S3 --> G8["threats_cleared"]
    end
    
    subgraph NS["Especificação Normativa"]
        N1["n_scout: leader → m_scout"]
        N2["n_collect: collector → m_collect"]
        N3["n_assemble: assembler → m_assemble"]
        N4["n_submit: assembler → m_submit"]
        N5["n_guard: sentinel → m_guard"]
    end
```

---

## Composição dos Esquadrões

```mermaid
graph TD
    subgraph Squad1["🟡 Squad 1"]
        A1["connectionA1<br/>LEADER"]
        A4["connectionA4<br/>COLLECTOR"]
        A5["connectionA5<br/>COLLECTOR"]
        A10["connectionA10<br/>ASSEMBLER"]
        A1 -->|authority| A4 & A5 & A10
    end

    subgraph Squad2["🔵 Squad 2"]
        A2["connectionA2<br/>LEADER"]
        A6["connectionA6<br/>COLLECTOR"]
        A7["connectionA7<br/>COLLECTOR"]
        A11["connectionA11<br/>ASSEMBLER"]
        A2 -->|authority| A6 & A7 & A11
    end

    subgraph Squad3["🟣 Squad 3"]
        A3["connectionA3<br/>LEADER"]
        A8["connectionA8<br/>COLLECTOR"]
        A9["connectionA9<br/>COLLECTOR"]
        A12["connectionA12<br/>ASSEMBLER"]
        A3 -->|authority| A8 & A9 & A12
    end

    subgraph Pool["🟢 Soloist Pool (todos os 15 agentes)"]
        A13["connectionA13<br/>SENTINEL"]
        A14["connectionA14<br/>SENTINEL"]
        A15["connectionA15<br/>SENTINEL"]
        PLUS["+ qualquer agente<br/>livre do squad"]
    end
```

---

## Pipeline de Decisão por Step

```mermaid
flowchart TD
    START(["📡 +step(N) — Percepts do MASSim"]) --> D1{am_deactivated?}

    D1 -->|Sim| SKIP1["⏸️ action(skip)"]
    D1 -->|Não| D2{energy < 5?}

    D2 -->|Sim| SKIP2["⚡ action(skip) conservar"]
    D2 -->|Não| D3{pending_submit<br/>+ goalZone(0,0)?}

    D3 -->|Sim| SUBMIT["✅ action(submit(Task))"]
    D3 -->|Não| D4{ready_to_connect?}

    D4 -->|Sim| CONNECT["🔗 action(connect(...))"]
    D4 -->|Não| D5{collecting + adjacent?}

    D5 -->|Sim| REQUEST["📦 action(request(Dir))"]
    D5 -->|Não| D6{collecting?}

    D6 -->|Sim| MOVE_DISP["🚶 action(move(Dir)) → dispenser"]
    D6 -->|Não| D7{has_destination?}

    D7 -->|Sim| MOVE_DEST["🚶 action(move(Dir)) → destino"]
    D7 -->|Não| EXPLORE["🔍 get_nearest_frontier<br/>→ action(move(Dir))"]

    style SKIP1 fill:#e74c3c,color:#fff
    style SKIP2 fill:#e74c3c,color:#fff
    style SUBMIT fill:#27ae60,color:#fff
    style CONNECT fill:#8e44ad,color:#fff
    style REQUEST fill:#2980b9,color:#fff
    style MOVE_DISP fill:#f39c12,color:#fff
    style MOVE_DEST fill:#f39c12,color:#fff
    style EXPLORE fill:#1abc9c,color:#fff
```

---

## Fluxo de Task Solo (Soloist)

```mermaid
sequenceDiagram
    participant M as MASSim
    participant L as Leader
    participant TB as TaskBoard
    participant SC as SquadCoord
    participant S as Soloist

    M->>L: +task(name, deadline, reward, reqs)
    L->>TB: register_task(name, deadline, reward, nBlocks)
    L->>L: Score = (Reward/NBlocks)*100 - dist
    L->>TB: place_bid(name, squad, Score)
    L->>TB: resolve_auction(name) → Winner!
    
    L->>SC: find_free_soloist(dispX, dispY)
    SC-->>L: Soloist mais próximo livre
    L->>SC: mark_busy(Soloist)
    L->>S: .send(tell, soloist_task(name, blockType))

    S->>S: get_nearest_dispenser → navigate
    loop Navigate to Dispenser
        S->>M: action(move(Dir))
    end
    S->>M: action(request(Dir))
    S->>M: action(attach(Dir))
    S->>S: +collected_block(Type)

    S->>S: get_nearest_goal_zone → navigate
    loop Navigate to Goal Zone
        S->>M: action(move(Dir))
    end
    S->>M: action(submit(TaskName))
    M-->>S: lastActionResult(success)
    S->>M: action(submit(TaskName)) [RE-SUBMIT!]
    
    S->>SC: mark_free(Me)
    S->>S: !finalize_task → volta a explorar
```

---

## Fluxo de Task Multi-Block (Connect)

```mermaid
sequenceDiagram
    participant L as Leader
    participant C1 as Collector 1
    participant C2 as Collector 2
    participant A as Assembler
    participant SC as SquadCoord
    participant M as MASSim

    L->>A: collect_and_connect_task(Task, Squad, Block)
    L->>C1: do_collect(BlockType1)
    L->>SC: set_meeting_point(Squad, X, Y)

    par Coleta em Paralelo
        C1->>M: navigate → request → attach (Block1)
    and
        A->>M: navigate → request → attach (Block2)
    end

    C1->>SC: signal_ready(Squad, Me)
    A->>A: all_ready(Squad)? → true!
    
    A->>C1: connect_request(Me, X, Y, Step)
    C1->>A: connect_confirmed(Me, X, Y)

    par Connect Simultâneo
        A->>M: action(connect(Collector1, RelX, RelY))
    and
        C1->>M: action(connect(Assembler, RelX, RelY))
    end

    A->>M: navigate → goal zone
    A->>M: action(submit(Task))
    M-->>A: success!
    A->>M: action(submit(Task)) [RE-SUBMIT!]
    A->>SC: clear_ready(Squad)
```

---

## Algoritmo A* (SharedMap)

```mermaid
flowchart TD
    START["compute_next_move(fx, fy, tx, ty)"] --> DIST{Manhattan<br/>distance > 60?}
    
    DIST -->|Sim| GREEDY["Greedy direction<br/>(sem pathfinding)"]
    DIST -->|Não| ASTAR["A* Search"]
    
    ASTAR --> INIT["OpenSet = {start}<br/>g[start] = 0<br/>f[start] = heuristic"]
    INIT --> LOOP{OpenSet<br/>vazio?}
    
    LOOP -->|Não| POP["current = min f(n)"]
    POP --> GOAL{current<br/>== target?}
    GOAL -->|Sim| PATH["Reconstruct path<br/>→ first direction"]
    GOAL -->|Não| EXPAND["Expand 4 vizinhos (n/s/e/w)<br/>com WRAPPING toroidal"]
    EXPAND --> OBS{É obstáculo?}
    OBS -->|Sim| SKIP_N["Skip vizinho"]
    OBS -->|Não| UPDATE["g[n] = g[curr] + 1<br/>f[n] = g[n] + h(n, target)"]
    UPDATE --> ITER{iterações<br/>> 2000?}
    ITER -->|Não| LOOP
    ITER -->|Sim| FALLBACK["⚠️ Fallback → Greedy"]
    
    LOOP -->|Sim| FALLBACK
    
    GREEDY --> DIR["Calcula melhor<br/>direção (wrapping)"]
    PATH --> RETURN["Return direction<br/>(n/s/e/w)"]
    DIR --> RETURN
    FALLBACK --> DIR

    style PATH fill:#27ae60,color:#fff
    style FALLBACK fill:#e67e22,color:#fff
```

---

## Dashboard — Interface Visual

### Layout 2D (Tela Principal)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ⚡ HIVE COMMAND CENTER          📡 LIVE   Step 0247   Score 00180   [2D] 🕐│
├─────────────────────────────────────────────────────────────────────────────┤
│                          AGENT GRID (15 cards)                              │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│ │ 🟡 A1   │ │ 🟡 A2   │ │ 🟡 A3   │ │ 🔵 A4   │ │ 🔵 A5   │ │ 🔵 A6   │  │
│ │ leader  │ │ leader  │ │ leader  │ │ collect │ │ collect │ │ collect │  │
│ │ (12,8)  │ │ (25,14) │ │ (37,2)  │ │ (14,9)  │ │ (11,7)  │ │ (28,15) │  │
│ │ ■ task5 │ │ □ idle  │ │ ■ task3 │ │ ■ col.. │ │ ■ col.. │ │ □ idle  │  │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘  │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐             │
│ │ 🟣 A10  │ │ 🟣 A11  │ │ 🟣 A12  │ │ 🟢 A13  │ │ 🟢 A14  │ ...        │
│ │ assemb  │ │ assemb  │ │ assemb  │ │ sentinl │ │ sentinl │             │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘             │
├──────────┬────────────────────────────────────────────────┬──────────────┤
│ SQUADS   │              TASK PIPELINE                     │  EVENT FEED  │
│          │                                                │              │
│ Squad 1  │  task5  [■■■■■■■□□□] collecting   ⏱ 120      │  step 247:   │
│  🟡 A1   │  task3  [■■■■■■■■■■] submitting   ⏱ 45       │  A1 won      │
│  🔵 A4,5 │  task8  [■■□□□□□□□□] delegating   ⏱ 280      │  auction     │
│  🟣 A10  │  task12 [■■■■■□□□□□] collecting   ⏱ 190      │  task5       │
│ ──────── │                                                │              │
│ Squad 2  │                                                │  step 245:   │
│  🟡 A2   │                                                │  A13 submit  │
│  🔵 A6,7 │                                                │  task9 ✓     │
│  🟣 A11  │                                                │              │
│ ──────── │                                                │  step 242:   │
│ Squad 3  │                                                │  new_task    │
│  🟡 A3   │                                                │  task12      │
│  🔵 A8,9 │                                                │  reward: 80  │
│  🟣 A12  │                                                │              │
├──────────┴──────────────────────────────┬─────────────────┴──────────────┤
│        BATTLE STATS                     │ AUCTION │   SCORE TIMELINE     │
│                                         │  HALL   │                      │
│  Tasks Completed: 12                    │         │   180 ─┐             │
│  Tasks Active:     4                    │ task12: │        │  ╱──────    │
│  Soloists Busy:    3/15                 │  sq1: 85│   120 ─┤╱            │
│  Map Coverage:    67%                   │  sq2: 72│        │             │
│  Avg Task Time:   38 steps             │  sq3: 91│    60 ─┤             │
│                                         │  ★ sq3  │        │             │
│                                         │         │     0 ─┴──────────── │
└─────────────────────────────────────────┴─────────┴──────────────────────┘
```

### Layout 3D (Three.js Viewport)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ⚡ HIVE COMMAND CENTER          📡 LIVE   Step 0247   Score 00180   [3D] 🕐│
├────────────────────────────────────────────────────────┬────────────────────┤
│                                                        │   EVENT FEED       │
│           🎮 3D VIEWPORT (Three.js)                    │                    │
│                                                        │   step 247:        │
│      ┌─────────────────────────────────┐              │   A1 won auction   │
│      │    ╔══╗      ·  ·  ·  ·         │              │   task5             │
│      │    ║🟡║  ·  🔵  ·  ·  ·         │              │                    │
│      │    ╚══╝      ·  ·  ·  ·         │              │   step 245:        │
│      │     ·  ·  ·  ·  ·  ·  ·         │              │   A13 submit ok    │
│      │     ·  ·  🟢  ·  ·  ·  ·        │              │                    │
│      │     ·  ·  ·  ·  🔴disp ·        │              ├────────────────────┤
│      │     ·  ·  ·  ·  ·  ·  ·         │              │   BATTLE STATS     │
│      │     ·  ·  ·  🟩goal·  ·         │              │                    │
│      │     ·  ·  ·  ·  ·  ·  ·         │              │   Completed: 12    │
│      │     ·  ·  🟣  ·  ·  ·  ·        │              │   Coverage:  67%   │
│      └─────────────────────────────────┘              │                    │
│                                                        ├────────────────────┤
│   Legenda:                                             │   SCORE TIMELINE   │
│   🟡 Leader  🔵 Collector  🟣 Assembler  🟢 Sentinel  │                    │
│   🔴 Dispenser  🟩 Goal Zone  ⬛ Obstáculo             │   180 ──╱────      │
│                                                        │   120 ─╱           │
│   [Orbit Controls: drag=rotate, scroll=zoom]           │     0 ─┴───────── │
└────────────────────────────────────────────────────────┴────────────────────┘
```

### Design System

```mermaid
graph LR
    subgraph Cores["🎨 Paleta de Cores"]
        C1["#22d3ee<br/>Neon Cyan<br/>(primary)"]
        C2["#34d399<br/>Neon Green<br/>(success/score)"]
        C3["#fbbf24<br/>Neon Amber<br/>(leaders)"]
        C4["#a78bfa<br/>Neon Purple<br/>(assemblers)"]
        C5["#e879f9<br/>Neon Magenta<br/>(alerts)"]
        C6["#f87171<br/>Neon Red<br/>(errors)"]
    end
    
    subgraph Roles["🎭 Cores por Role"]
        R1["🟡 squad_leader → #fbbf24"]
        R2["🔵 collector → #22d3ee"]
        R3["🟣 assembler → #a78bfa"]
        R4["🟢 sentinel → #34d399"]
    end

    subgraph Fonts["📝 Tipografia"]
        F1["Inter (sans)<br/>Headers + Body"]
        F2["JetBrains Mono<br/>Data + Counters"]
    end
    
    style C1 fill:#22d3ee,color:#000
    style C2 fill:#34d399,color:#000
    style C3 fill:#fbbf24,color:#000
    style C4 fill:#a78bfa,color:#000
    style C5 fill:#e879f9,color:#000
    style C6 fill:#f87171,color:#000
```

---

## Mecanismos de Coordenação

### Leilão Distribuído (Contract Net)

```mermaid
sequenceDiagram
    participant TB as TaskBoard
    participant L1 as Leader 1
    participant L2 as Leader 2
    participant L3 as Leader 3

    Note over TB,L3: Signal: new_task_available(task7, 300, 80, 1)
    
    par Avaliação Paralela
        L1->>L1: Score = (80/1)*100 - 12 = 7988
    and
        L2->>L2: Score = (80/1)*100 - 25 = 7975
    and
        L3->>L3: Score = (80/1)*100 - 8 = 7992
    end
    
    L1->>TB: place_bid(task7, squad1, 7988)
    L2->>TB: place_bid(task7, squad2, 7975)
    L3->>TB: place_bid(task7, squad3, 7992)
    
    Note over TB: .wait(50ms) para bids
    
    L1->>TB: resolve_auction(task7)
    TB-->>L1: winner = squad3 ★
    
    Note over L3: Squad 3 delega ao soloist mais próximo
```

### Pool de Soloists

```mermaid
flowchart TD
    WIN["Leader venceu leilão<br/>Task de 1 bloco"] --> FIND["SquadCoordinator<br/>find_free_soloist(dispX, dispY)"]
    
    FIND --> CHECK{"Existe agente<br/>livre?"}
    
    CHECK -->|Sim| SELECT["Seleciona mais próximo<br/>(Manhattan wrapping)"]
    SELECT --> MARK["mark_busy(Winner)"]
    MARK --> SEND[".send(tell, soloist_task(Task, Block))"]
    
    CHECK -->|Não| FALLBACK["Fallback: envia ao<br/>assembler do próprio squad"]
    FALLBACK --> SEND2[".send(tell, solo_task(Task, Block))"]
    
    SEND --> EXECUTE["Soloist executa:<br/>dispenser → collect → goal → submit"]
    SEND2 --> EXECUTE
    
    EXECUTE --> FREE["mark_free(Me)<br/>!finalize_task<br/>Volta ao pool"]
    
    style WIN fill:#fbbf24,color:#000
    style SELECT fill:#34d399,color:#000
    style EXECUTE fill:#22d3ee,color:#000
    style FALLBACK fill:#f87171,color:#fff
```

---

## Resiliência

```mermaid
flowchart LR
    subgraph "Camada 1 — Sobrevivência"
        R1["Deactivated → skip"]
        R2["Energy < 5 → skip"]
        R3["Stuck 20 steps → finalize"]
    end
    
    subgraph "Camada 2 — Retry"
        R4["Request fail → random move<br/>+ retry (até 5×)"]
        R5["Submit fail → rotate CW<br/>(até 4×)"]
        R6["8 bloqueios → goal zone<br/>alternativa"]
    end
    
    subgraph "Camada 3 — Timeout"
        R7["200 steps sem progresso<br/>→ cleanup + finalize"]
        R8["Deadline atingido<br/>→ abandon task"]
    end
    
    subgraph "Camada 4 — Ambiente"
        R9["Obstacles com decay<br/>(expiram após N steps)"]
        R10["Frontier regeneration<br/>(novas fronteiras surgem)"]
    end

    style R1 fill:#e74c3c,color:#fff
    style R2 fill:#e74c3c,color:#fff
    style R3 fill:#e74c3c,color:#fff
    style R4 fill:#e67e22,color:#fff
    style R5 fill:#e67e22,color:#fff
    style R6 fill:#e67e22,color:#fff
```

---

## Stack Tecnológico

```mermaid
graph TB
    subgraph Backend["Backend — JaCaMo (JDK 21)"]
        JC["JaCaMo 1.3.0"]
        JS["Jason 3.3.1<br/>(AgentSpeak)"]
        MO["MOISE+ 1.1<br/>(Organizacional)"]
        CA["CArtAgO 3.1<br/>(Artefatos)"]
        EI["eismassim 4.5<br/>(EIS Bridge)"]
        WS["Java-WebSocket 1.5.7"]
        JN["org.json 20240303"]
    end
    
    subgraph Frontend["Frontend — Dashboard (Node 20+)"]
        RE["React 19.2"]
        TH["Three.js 0.184"]
        ZU["Zustand 5.0"]
        FM["Framer Motion 12.38"]
        RC["Recharts 3.8"]
        VT["Vite 8.0"]
        TW["Tailwind 4.3"]
        TS["TypeScript 6.0"]
    end
    
    subgraph Server["Servidor — MASSim"]
        MA["MASSim 2022-1.1.1<br/>(Java 17+, Maven)"]
    end
    
    subgraph Build["Build Tools"]
        GR["Gradle 9.2"]
        MV["Maven 3.8+"]
        NP["npm 10+"]
    end
```

---

## Estrutura do Projeto

```
PCS5703_MAS/
│
├── 📄 build.gradle                    # Build: Java 21, JaCaMo 1.3.0, deps
├── 📄 settings.gradle                 # rootProject.name = 'hive'
├── 📄 hive.jcm                        # 15 agentes JaCaMo
├── 📄 eismassimconfig.json            # EIS → MASSim (connectionA1-15)
├── 📄 logging.properties              # JVM logging (INFO)
├── 📁 lib/                            # eismassim-4.5 JAR
│
├── 📁 src/                            # ═══ CÓDIGO FONTE ═══
│   ├── 📁 agt/                        #   Agentes Jason (AgentSpeak)
│   │   ├── squad_leader.asl           #     Líder: leilão + delegação
│   │   ├── collector.asl              #     Coletor: blocos + meeting
│   │   ├── assembler.asl             #     Montador: connect + submit
│   │   ├── sentinel.asl              #     Sentinela: solo + patrulha
│   │   ├── dummy.asl                 #     Teste mínimo
│   │   └── 📁 common/                #     Módulos compartilhados:
│   │       ├── connect_protocol.asl   #       Submit/Connect (P0)
│   │       ├── collection.asl        #       Request/Attach (P1)
│   │       ├── navigation.asl        #       Greedy/Frontier (P2)
│   │       ├── perception.asl        #       Percepts processing
│   │       ├── communication.asl     #       Sync msgs connect
│   │       └── dashboard_hooks.asl   #       WS reporting
│   │
│   ├── 📁 org/                        #   Organização MOISE+
│   │   └── hive_org.xml              #     4 roles, 3 schemes, 5 norms
│   │
│   ├── 📁 env/                        #   Artefatos CArtAgO (Java)
│   │   ├── 📁 env/
│   │   │   ├── SharedMap.java        #     Mapa: A*, greedy, frontier
│   │   │   ├── TaskBoard.java        #     Tasks + leilão
│   │   │   ├── SquadCoordinator.java #     Squads + soloist pool
│   │   │   └── HiveDashboard.java   #     WebSocket :8765
│   │   └── 📁 connection/
│   │       ├── EISAccess.java        #     EIS bridge (×15)
│   │       └── Translator.java       #     IILang ↔ Jason
│   │
│   └── 📁 java/hive/                 #   Internal Actions
│       ├── AdjacentDirection.java     #     Toroidal 40×40
│       ├── ConnectCalculator.java     #     RelX, RelY connect
│       ├── DirectionCalculator.java  #     Greedy direction
│       ├── PathFinder.java           #     A* backup
│       └── PatternMatcher.java       #     Pattern matching
│
├── 📁 conf/                           # Config MASSim server
│   └── TestConfig.json               #   40×40, 750 steps
│
├── 📁 dashboard/                      # ═══ FRONTEND REACT ═══
│   ├── package.json                  #   React 19, Three.js, Zustand
│   ├── vite.config.ts               #   Vite 8 + React
│   ├── tsconfig.json                 #   TypeScript 6
│   └── 📁 src/
│       ├── App.tsx                   #     Layout (2D/3D toggle)
│       ├── 📁 lib/
│       │   ├── store.ts             #     Zustand (HiveState)
│       │   └── ws.ts                #     useHiveSocket + reconnect
│       └── 📁 components/
│           ├── Header.tsx           #     Step, score, status
│           ├── AgentGrid.tsx        #     Cards 15 agentes
│           ├── SquadsPanel.tsx      #     3 squads + membros
│           ├── TaskPipeline.tsx     #     Pipeline visual
│           ├── EventFeed.tsx        #     Log tempo real
│           ├── AuctionHall.tsx     #     Leilões ativos
│           ├── BattleStats.tsx     #     Métricas agregadas
│           ├── ScoreTimeline.tsx   #     Gráfico (Recharts)
│           └── GridScene3D.tsx    #     Three.js viewport
│
├── 📁 massim_2022/                    # ═══ PLATAFORMA MASSim ═══
│   ├── 📁 server/                    #   Servidor simulação
│   ├── 📁 protocol/                  #   Protocolo JSON
│   ├── 📁 eismassim/                 #   EIS bridge (fonte JAR)
│   └── 📁 monitor/                   #   Web monitor
│
└── 📁 doc/                            # ═══ DOCUMENTAÇÃO ═══
    ├── ARCH.md                       #   Arquitetura C4 + UML + MAS
    ├── TECHSPEC.md                   #   Spec técnica completa
    ├── funcIdea.md                   #   Documento funcional
    └── *.pdf                         #   Enunciado + análise
```

---

## Como Executar

### Pré-requisitos

| Software | Versão | Uso |
|----------|--------|-----|
| JDK | 21+ | JaCaMo runtime |
| JDK | 17+ | MASSim server |
| Node.js | 20+ | Dashboard (opcional) |
| Maven | 3.8+ | Build MASSim (se necessário) |

### 1. Iniciar o Servidor MASSim

```bash
cd massim_2022/server
java -jar target/server-2022-1.1.1-jar-with-dependencies.jar \
     -conf ../../conf/TestConfig.json --monitor
```

- Aguardar: `Listening on port 12300...`
- Monitor: http://localhost:8000

### 2. Iniciar o Sistema HIVE

```bash
./gradlew run
```

- 15 agentes conectam automaticamente
- WebSocket dashboard inicia em :8765
- Logs no console (JaCaMo + Jason `.print()`)

### 3. Iniciar Dashboard (opcional)

```bash
cd dashboard
npm install    # primeira vez
npm run dev
```

- Acessar: http://localhost:5173
- Conecta automaticamente via `ws://localhost:8765`

### Portas

| Porta | Protocolo | Serviço |
|-------|-----------|---------|
| 12300 | TCP/JSON | MASSim Server |
| 8000 | HTTP | MASSim Web Monitor |
| 8765 | WebSocket | HiveDashboard |
| 5173 | HTTP | Vite (Dashboard) |

---

## Documentação Completa

### Documentos Centrais

| Documento | Conteúdo |
|-----------|----------|
| [`doc/ARCH.md`](doc/ARCH.md) | Modelo C4 (4 níveis), UML (classes, sequência, estado, atividades), padrões MAS (BDI camadas, Contract Net, Soloists, A&A), ADRs |
| [`doc/TECHSPEC.md`](doc/TECHSPEC.md) | Tecnologias, protocolos EIS, percepts/ações completos, dependências, config, ambiente, métricas |
| [`doc/funcIdea.md`](doc/funcIdea.md) | Ideia central, mecânicas, estratégias, fluxos de dados, riscos, diferenciais competitivos |

### Documentação por Módulo

| Documento | Escopo |
|-----------|--------|
| [`bin/main/mainDoc.md`](bin/main/mainDoc.md) | AgentSpeak compilado + MOISE+ (arquitetura agentes, fluxos, módulos) |
| [`build/buildDoc.md`](build/buildDoc.md) | Pipeline Gradle, classes compiladas, dependências resolvidas |
| [`conf/confgDoc.md`](conf/confgDoc.md) | Parâmetros MASSim (grid, tasks, normas, roles, clear events) |
| [`dashboard/dashboardDoc.md`](dashboard/dashboardDoc.md) | Componentes React, WebSocket, Zustand, Three.js, design system |
| [`massim_2022/massimDoc.md`](massim_2022/massimDoc.md) | Módulos Maven, protocolo TCP/JSON, cenário, integração HIVE |
| [`src/srcDoc.md`](src/srcDoc.md) | AgentSpeak, artefatos Java, internal actions, MOISE+, algoritmos |

---

## Correspondência com o Relatório

O enunciado ([doc/5703_ex02_26.pdf](doc/5703_ex02_26.pdf)) define o template. Mapa para a documentação:

| Seção do Relatório | Documentação |
|--------------------|-------------|
| **1. Introdução** | [`funcIdea.md`](doc/funcIdea.md) §1-2 |
| **2. Análise e especificação do SMA** | [`funcIdea.md`](doc/funcIdea.md) §3 + [`ARCH.md`](doc/ARCH.md) §3 + [`srcDoc.md`](src/srcDoc.md) §5 |
| **3. Arquitetura e design** | [`ARCH.md`](doc/ARCH.md) — C4, UML, sequência, estado |
| **4. Linguagens e plataforma** | [`TECHSPEC.md`](doc/TECHSPEC.md) §3-5 |
| **5. Estratégia para time** | [`funcIdea.md`](doc/funcIdea.md) §4 + [`ARCH.md`](doc/ARCH.md) §5 |
| **6. Características técnicas** | [`TECHSPEC.md`](doc/TECHSPEC.md) §6-10 + [`funcIdea.md`](doc/funcIdea.md) §4.6 |
| **7. Discussão e conclusão** | [`funcIdea.md`](doc/funcIdea.md) §9-10 |

---

## Fundamentação Teórica

| Conceito | Referência | Aplicação no HIVE |
|----------|-----------|-------------------|
| Modelo BDI | Bratman (1987), Rao & Georgeff (1991) | Arquitetura dos 15 agentes |
| AgentSpeak(L) | Rao (1996), Bordini & Hübner (2006) | Linguagem de programação (.asl) |
| MOISE+ | Hübner, Sichman & Boissier (2002) | Organização: roles, groups, norms |
| Contract Net | Smith (1980) | Leilão distribuído (TaskBoard) |
| A&A | Ricci, Viroli & Omicini (2007) | Artefatos CArtAgO compartilhados |
| JaCaMo | Boissier et al. (2013) | Framework integrador |
| Subsumption | Brooks (1986) | Prioridade de comportamentos |
| LTI-USP | Stabile & Sichman (2021) | Referência MAPC anterior |

---

## Métricas

| Métrica | Valor |
|---------|-------|
| Código total | ~5.410 linhas |
| AgentSpeak (.asl) | ~1.470 linhas / 11 arquivos |
| Java (artefatos + actions) | ~1.640 linhas / 11 arquivos |
| TypeScript (dashboard) | ~2.000 linhas |
| XML (MOISE+) | 120 linhas |
| Agentes BDI | 15 |
| Artefatos CArtAgO | 5 tipos / 19 instâncias |
| Internal Actions Java | 5 |
| Componentes React | 9 |
| Documentação | 6 docs por diretório + 3 centrais |

---

## Referências

[1] Multi Agent Programming Contest. http://www.multiagentcontest.org/

[2] JaCaMo project. https://jacamo-lang.github.io

[3] Hübner, J.F., Sichman, J.S., Boissier, O. (2002). *A Model for the Structural, Functional and Deontic Specification of Organizations in Multiagent Systems*. SBIA'02, LNAI 2507, pp. 118-128. Springer.

[4] Bordini, R.H., Hübner, J.F. (2006). *An overview of Jason*. ALP Newsletter, 19(3).

[5] Stabile, M.F., Sichman, J.S. (2021). *The LTI-USP Strategy to the 2020/2021 Multi-Agent Programming Contest*. MAPC 2021, LNCS 12947. Springer.

[6] Multi-Agent Programming Contest Scenario Description 2022. https://github.com/agentcontest/massim_2022/blob/main/docs/scenario.md

---

<p align="center">
  <strong>PCS 5703 — Sistemas Multi-Agentes</strong><br/>
  Escola Politécnica da USP — 2026
</p>
