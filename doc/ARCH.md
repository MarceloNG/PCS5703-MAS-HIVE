# Arquitetura do Sistema — Projeto HIVE / MAPC 2022

Documento de arquitetura completo do sistema multi-agente **Hive**, desenvolvido para a competição MASSim 2022 (Agents Assemble III). Inclui diagramas C4, UML, padrões MAS e detalhamento de todos os componentes implementados. Todos os diagramas utilizam Mermaid.

**Tecnologias:** JaCaMo 1.3.0 (Jason + CArtAgO + MOISE) | Java 21 | MASSim 2022 | React 19 + Three.js

---

## 1. Modelo C4

### 1.1 Nível 1 — Diagrama de Contexto

```mermaid
graph TB
    subgraph Atores
        TEAM["👤 Time HIVE<br/>Desenvolvedores que<br/>configuram e monitoram"]
    end

    subgraph Sistema
        HIVE["🔷 HIVE MAS<br/>SMA com 15 agentes BDI<br/>coordenados por leilao e pool de soloists"]
    end

    subgraph Sistemas_Externos["Sistemas Externos"]
        MASSIM["🔶 Servidor MASSim 2022<br/>Simulador Agents Assemble<br/>Grid 40×40, tasks, normas"]
        MONITOR["🔶 MASSim Web Monitor<br/>Visualização da simulação<br/>HTTP :8000"]
        DASHBOARD["🔶 Hive Dashboard<br/>Command Center React/3D<br/>WebSocket :8765"]
    end

    TEAM -->|"Configura (JCM + JSON)"| HIVE
    HIVE -->|"Envia ações / Recebe percepts<br/>(TCP/JSON porta 12300)"| MASSIM
    MASSIM -->|"Streaming do estado"| MONITOR
    HIVE -->|"Eventos em tempo real<br/>(WebSocket JSON)"| DASHBOARD
    TEAM -->|"Monitora simulação"| MONITOR
    TEAM -->|"Monitora agentes"| DASHBOARD

    style HIVE fill:#1168bd,color:#fff,stroke:#0b4884
    style MASSIM fill:#999,color:#fff,stroke:#666
    style MONITOR fill:#999,color:#fff,stroke:#666
    style DASHBOARD fill:#438a5e,color:#fff,stroke:#2d5e3f
    style TEAM fill:#08427b,color:#fff,stroke:#052e56
```

### 1.2 Nível 2 — Diagrama de Containers

```mermaid
graph TB
    subgraph HIVE_MAS["HIVE MAS (System Boundary)"]
        direction TB
        JACAMO["JaCaMo Runtime<br/><i>Java 21 + Gradle 9.2</i><br/>Integra agentes,<br/>organizacao e ambiente"]

        JASON["Jason Engine<br/><i>AgentSpeak-L (15 agentes)</i><br/>squad_leader ×3, collector ×6<br/>assembler ×3, sentinel ×3"]

        MOISE["MOISE+ Engine<br/><i>hive_org.xml</i><br/>4 roles, 2 groups,<br/>3 schemes, 5 norms"]

        CARTAGO["CArtAgO Workspace<br/><i>Java (5 artefatos)</i><br/>SharedMap, TaskBoard,<br/>SquadCoordinator, HiveDashboard"]

        EISMASSIM["EISAccess Artifacts<br/><i>eismassim-4.5.jar</i><br/>15 instâncias (1 por agente)<br/>EnvironmentInterface singleton"]
    end

    subgraph Dashboard_App["Hive Dashboard (React)"]
        DASH_UI["React 19 + Three.js<br/>Zustand state<br/>Framer Motion"]
    end

    MASSIM_EXT["Servidor MASSim 2022<br/>TCP :12300"]

    JACAMO -->|"Gerencia ciclo de vida"| JASON
    JACAMO -->|"Carrega spec organizacional"| MOISE
    JACAMO -->|"Instancia artefatos"| CARTAGO
    JASON -->|"Observa propriedades /<br/>Executa operacoes"| CARTAGO
    JASON -->|"Percepts / Actions<br/>(via EIS API)"| EISMASSIM
    EISMASSIM -->|"JSON sobre TCP<br/>porta 12300"| MASSIM_EXT
    CARTAGO -->|"WebSocket JSON<br/>porta 8765"| DASH_UI

    style JACAMO fill:#1168bd,color:#fff,stroke:#0b4884
    style JASON fill:#1168bd,color:#fff,stroke:#0b4884
    style MOISE fill:#1168bd,color:#fff,stroke:#0b4884
    style CARTAGO fill:#1168bd,color:#fff,stroke:#0b4884
    style EISMASSIM fill:#1168bd,color:#fff,stroke:#0b4884
    style DASH_UI fill:#438a5e,color:#fff,stroke:#2d5e3f
    style MASSIM_EXT fill:#999,color:#fff,stroke:#666
```

### 1.3 Nível 3 — Diagrama de Componentes

```mermaid
graph TB
    subgraph "Jason Engine — 15 Agentes BDI"
        SL["squad_leader.asl ×3<br/><i>Leilão, delegação,<br/>coordenação de squad</i>"]
        CO["collector.asl ×6<br/><i>Coleta de blocos,<br/>soloist tasks, meeting point</i>"]
        AS["assembler.asl ×3<br/><i>Solo/multi-block,<br/>connect + submit</i>"]
        SE["sentinel.asl ×3<br/><i>Patrulha,<br/>soloist tasks</i>"]

        subgraph "common/ — Módulos Compartilhados"
            CP["connect_protocol.asl<br/><i>Submit + Connect<br/>(PRIORIDADE MAX)</i>"]
            CL["collection.asl<br/><i>Request + Attach cycle</i>"]
            NAV["navigation.asl<br/><i>Greedy + Frontier exploration</i>"]
            PER["perception.asl<br/><i>Processamento de percepts</i>"]
            COM["communication.asl<br/><i>Sync msgs para connect</i>"]
            DSH["dashboard_hooks.asl<br/><i>Reportar estado via WS</i>"]
        end
    end

    subgraph "CArtAgO Workspace — Artefatos Java"
        SM["SharedMap.java<br/><i>Mapa compartilhado<br/>A* + fronteira + dispensers</i>"]
        TB["TaskBoard.java<br/><i>Registro de tasks +<br/>leilão distribuído</i>"]
        SC["SquadCoordinator.java<br/><i>Squads, meeting points,<br/>pool de soloists</i>"]
        HD["HiveDashboard.java<br/><i>WebSocket :8765<br/>broadcast JSON</i>"]
    end

    subgraph "Internal Actions — Java (hive.*)"
        AD["AdjacentDirection.java<br/><i>Adjacência com wrap 40×40</i>"]
        CC["ConnectCalculator.java<br/><i>Coords relativas connect</i>"]
        PF["PathFinder.java<br/><i>A* (2000 iter max)</i>"]
        DC["DirectionCalculator.java<br/><i>Direção greedy</i>"]
        PM["PatternMatcher.java<br/><i>Verifica padrão de blocos</i>"]
    end

    subgraph "EIS Bridge"
        EIS["EISAccess.java ×15<br/><i>Singleton EnvironmentInterface</i>"]
        TR["Translator.java<br/><i>IILang ↔ Jason AST</i>"]
    end

    SL & CO & AS & SE --> CP & CL & NAV & PER & DSH
    CO & AS --> COM

    SL & CO & AS & SE --> SM & TB & SC & HD
    SL & CO & AS & SE --> AD & CC & PF & DC & PM
    SL & CO & AS & SE --> EIS
    EIS --> TR

    EIS --> MASSIM["MASSim Server<br/>TCP :12300"]
    HD --> DASHBOARD["Dashboard React<br/>WS :8765"]
```

### 1.4 Nível 4 — Código (Fluxo interno de um step)

```mermaid
flowchart TD
    PERCEPT["MASSim envia REQUEST-ACTION"] --> EIS["EISAccess.updatePercepts()"]
    EIS --> OBS["DefineObsProperty (step, position, thing, task...)"]
    OBS --> PERC["perception.asl: +position(X,Y)<br/>→ mark_visited, update_cell, check_stuck"]

    PERC --> STEP["+step(N) trigger cascata"]

    STEP --> CP_CHECK{"connect_protocol<br/>intercepta?"}
    CP_CHECK -->|"deactivated"| SKIP1["action(skip)"]
    CP_CHECK -->|"energy < 5"| SKIP2["action(skip)"]
    CP_CHECK -->|"pending_submit + goalZone(0,0)"| SUBMIT["action(submit(TaskName))"]
    CP_CHECK -->|"ready_to_connect"| CONNECT["action(connect(...))"]
    CP_CHECK -->|"Não"| CL_CHECK

    CL_CHECK{"collection<br/>intercepta?"}
    CL_CHECK -->|"waiting_attach_result"| ATTACH["action(attach(Dir))"]
    CL_CHECK -->|"waiting_request"| REQUEST["action(request(Dir))"]
    CL_CHECK -->|"collecting + adjacente"| REQUEST
    CL_CHECK -->|"collecting"| MOVE_DISP["action(move(Dir)) → dispenser"]
    CL_CHECK -->|"Não"| NAV_CHECK

    NAV_CHECK{"navigation<br/>executa"}
    NAV_CHECK -->|"has_destination"| MOVE_DEST["action(move(Dir)) → destino"]
    NAV_CHECK -->|"sem destino"| EXPLORE["do_explore → get_nearest_frontier"]
    EXPLORE --> MOVE_FRONT["action(move(Dir)) → fronteira"]
```

---

## 2. Diagramas UML

### 2.1 Diagrama de Classes — Artefatos CArtAgO (Implementação Real)

```mermaid
classDiagram
    class Artifact {
        <<CArtAgO>>
        #defineObsProperty(name, args)
        #signal(name, args)
        #removeObsProperty(name)
    }

    class SharedMap {
        -cells: ConcurrentHashMap~String,String~
        -knownDispensers: Set~String~
        -knownGoalZones: Set~String~
        -knownRoleZones: Set~String~
        -visitedCells: Set~String~
        -obstacles: ConcurrentHashMap~String,Integer~
        -gridWidth: int = 40
        -gridHeight: int = 40
        +update_cell(x, y, type, details)
        +mark_visited(x, y)
        +get_nearest_dispenser(agX, agY, type) → (x, y)
        +get_nearest_goal_zone(agX, agY) → (x, y)
        +get_alternative_goal_zone(agX, agY, curX, curY) → (x, y)
        +get_nearest_frontier(agX, agY) → (x, y)
        +compute_next_move(fx, fy, tx, ty) → dir
        +manhattan_dist(x1, y1, x2, y2) → dist
        +mark_obstacle(x, y, step)
        +decay_obstacles(step)
        +get_map_stats() → (visited, disp, goal, role)
        +set_grid_dimensions(width, height)
        -astar(fx, fy, tx, ty): String
        -astarCost(fx, fy, tx, ty): int
        -greedy(fx, fy, tx, ty): String
        -wrappedManhattan(x1, y1, x2, y2): int
    }

    class TaskBoard {
        -knownTasks: ConcurrentHashMap~String,TaskInfo~
        -bids: ConcurrentHashMap~String,List~Bid~~
        -assignedTasks: ConcurrentHashMap~String,String~
        -taskRequirements: ConcurrentHashMap~String,List~
        -signaledTasks: ConcurrentHashMap~String,Long~
        +register_task(name, deadline, reward, nBlocks)
        +signal_task_ready(name)
        +evaluate_task(name, deadline, reward, nBlocks) → score
        +place_bid(taskName, squadId, bidValue)
        +resolve_auction(taskName) → winnerSquad
        +complete_task(taskName)
        +remove_expired(currentStep)
        +register_task_block(taskName, blockType)
        +get_task_first_block(taskName) → blockType
        +get_task_blocks(taskName) → (block1, block2)
        +is_task_assigned(taskName) → boolean
    }

    class SquadCoordinator {
        -agentSquad: ConcurrentHashMap~String,String~
        -squadMembers: ConcurrentHashMap~String,List~
        -squadRole: ConcurrentHashMap~String,String~
        -meetingPoints: ConcurrentHashMap~String,int[]~
        -readyAgents: ConcurrentHashMap~String,Set~
        -soloistBusy: ConcurrentHashMap~String,Boolean~
        -agentPositions: ConcurrentHashMap~String,int[]~
        -squadActiveTask: ConcurrentHashMap~String,String~
        +get_my_squad(agent) → squadId
        +get_squad_collectors(squad) → (col1, col2)
        +get_squad_assembler(squad) → assembler
        +set_meeting_point(squad, x, y)
        +get_meeting_point(squad) → (x, y)
        +signal_ready(squad, agent)
        +all_ready(squad) → boolean
        +clear_ready(squad)
        +find_free_soloist(dispX, dispY) → winner
        +mark_busy(agent) / mark_free(agent)
        +update_agent_pos(agent, x, y)
        +set_squad_task(squad, taskName)
        +get_squad_task(squad) → taskName
    }

    class HiveDashboard {
        -wsServer: DashboardWsServer
        -currentStep: int
        -currentScore: int
        -squads: ConcurrentHashMap~String,JSONObject~
        -tasks: ConcurrentHashMap~String,JSONObject~
        -events: CopyOnWriteArrayList~JSONObject~
        -agentStates: ConcurrentHashMap~String,JSONObject~
        +log_event(type, agent, data)
        +set_step(step)
        +update_score(score)
        +update_task_phase(task, phase, progress)
        +update_squad(squadId, membersJson)
        +register_map_dispenser(x, y, type)
        +register_map_goal_zone(x, y)
        -buildSnapshot(): String
        -broadcast(msg)
    }

    class EISAccess {
        -sharedEI: EnvironmentInterface$
        -agName: String
        -receiving: boolean
        -currentPercepts: List~Percept~
        +init(conf, entityName)
        +action(String)
        ~updatePercepts() [INTERNAL_OPERATION]
    }

    class Translator {
        +perceptToLiteral(Percept): Literal$
        +literalToAction(Literal): Action$
        +parametersToTerms(List): Term[]$
        +parameterToTerm(Parameter): Term$
        +termToParameter(Term): Parameter$
    }

    Artifact <|-- SharedMap
    Artifact <|-- TaskBoard
    Artifact <|-- SquadCoordinator
    Artifact <|-- HiveDashboard
    Artifact <|-- EISAccess
    EISAccess --> Translator
```

### 2.2 Diagrama de Classes — Internal Actions Java

```mermaid
classDiagram
    class DefaultInternalAction {
        <<Jason>>
        +execute(ts, un, args) Object
    }

    class AdjacentDirection {
        -GRID_WIDTH: int = 40$
        -GRID_HEIGHT: int = 40$
        +execute(ts, un, args) → unifica Dir (n/s/e/w/none)
        -wrapDelta(d, size): int
    }

    class ConnectCalculator {
        +execute(ts, un, args) → unifica RelX, RelY
    }

    class DirectionCalculator {
        +execute(ts, un, args) → unifica Dir (n/s/e/w/skip)
    }

    class PathFinder {
        +execute(ts, un, args) → unifica Dir
        -astar(fromX, fromY, toX, toY, obstacles): String
        -firstDirection(goal, fromX, fromY): String
    }

    class PatternMatcher {
        +execute(ts, un, args) → unifica Result (true/false)
    }

    DefaultInternalAction <|-- AdjacentDirection
    DefaultInternalAction <|-- ConnectCalculator
    DefaultInternalAction <|-- DirectionCalculator
    DefaultInternalAction <|-- PathFinder
    DefaultInternalAction <|-- PatternMatcher
```

### 2.3 Diagrama de Sequência — Fluxo Completo de Task Solo (Soloist)

```mermaid
sequenceDiagram
    participant MASSIM as MASSim Server
    participant EIS as EISAccess
    participant LEAD as Squad Leader
    participant TB as TaskBoard
    participant SC as SquadCoordinator
    participant MAP as SharedMap
    participant SOL as Soloist (Sentinel/Collector)

    Note over MASSIM,SOL: FASE 1 — Detecção e Leilão

    MASSIM->>EIS: REQUEST-ACTION (task percept)
    EIS->>LEAD: +task(Name, Deadline, Reward, Reqs)
    LEAD->>LEAD: +new_task_available(Name, Deadline, Reward, NBlocks)
    LEAD->>MAP: get_task_first_block(Name) → BType
    LEAD->>MAP: get_nearest_dispenser(MX, MY, BType) → (DX, DY)
    LEAD->>MAP: manhattan_dist(MX, MY, DX, DY) → MDist
    LEAD->>LEAD: Score = (Reward/NBlocks)*100 - MDist
    LEAD->>TB: place_bid(Name, MySquad, Score)
    LEAD->>TB: resolve_auction(Name) → Winner

    Note over MASSIM,SOL: FASE 2 — Delegação ao Soloist

    LEAD->>SC: find_free_soloist(DX, DY) → SoloWinner
    LEAD->>SC: mark_busy(SoloWinner)
    LEAD->>SOL: .send(tell, soloist_task(TaskName, BlockType))

    Note over MASSIM,SOL: FASE 3 — Coleta

    SOL->>MAP: get_nearest_dispenser(MX, MY, BlockType) → (DX, DY)
    loop Navegar ao Dispenser
        SOL->>MASSIM: move(Dir)
    end
    SOL->>MASSIM: request(Dir)
    SOL->>MASSIM: attach(Dir)
    SOL->>SOL: +collected_block(Type)

    Note over MASSIM,SOL: FASE 4 — Submit

    SOL->>MAP: get_nearest_goal_zone(MX, MY) → (GX, GY)
    loop Navegar à Goal Zone
        SOL->>MASSIM: move(Dir)
    end
    SOL->>MASSIM: submit(TaskName)
    MASSIM-->>SOL: lastActionResult(success)
    SOL->>MASSIM: submit(TaskName)  [re-submit]
    SOL->>SC: mark_free(Me)
    SOL->>SOL: !finalize_task(TaskName)
```

### 2.4 Diagrama de Sequência — Connect Multi-Block

```mermaid
sequenceDiagram
    participant LEAD as Squad Leader
    participant COL as Collector
    participant ASM as Assembler
    participant SC as SquadCoordinator
    participant MASSIM as MASSim

    Note over LEAD,MASSIM: Delegação

    LEAD->>ASM: collect_and_connect_task(TaskName, Squad, BlockType)
    LEAD->>COL: do_collect(BlockType)

    par Coleta Paralela
        COL->>MASSIM: navigate → request → attach
        COL->>COL: +collected_block(Type)
    and
        ASM->>MASSIM: navigate → request → attach
        ASM->>ASM: +collected_block(Type)
    end

    Note over LEAD,MASSIM: Meeting Point

    COL->>SC: signal_ready(Squad, Me)
    SC-->>ASM: signal agent_ready

    ASM->>ASM: all_ready? → true
    ASM->>COL: connect_request(Me, X, Y, TargetStep)
    COL->>ASM: connect_confirmed(Me, X, Y)

    Note over LEAD,MASSIM: Connect Sincronizado

    par Simultâneo
        ASM->>MASSIM: connect(Collector, TX, TY)
    and
        COL->>MASSIM: connect(Assembler, RelX, RelY)
    end

    Note over LEAD,MASSIM: Submit

    ASM->>MASSIM: navigate → goal zone → submit(TaskName)
    MASSIM-->>ASM: success
    ASM->>SC: clear_ready(Squad)
    ASM->>TB: complete_task(TaskName)
```

### 2.5 Diagrama de Estados — Ciclo de Vida do Agente

```mermaid
stateDiagram-v2
    [*] --> Initializing: !start

    Initializing --> Exploring: Artefatos criados + EIS conectado

    state "Exploring" as Exploring {
        [*] --> GetFrontier: get_nearest_frontier
        GetFrontier --> Navigate: fronteira encontrada
        Navigate --> GetFrontier: chegou ao destino
        GetFrontier --> RandomMove: sem fronteira
        RandomMove --> GetFrontier: novo step
    }

    Exploring --> SoloistTask: +soloist_task(Task, Block)
    Exploring --> CollectOrder: +do_collect(BlockType)
    Exploring --> MultiBlock: +collect_and_connect_task(...)

    state "Soloist Task" as SoloistTask {
        [*] --> CollectBlock: !collect_block(Type)
        CollectBlock --> NavDispenser: get_nearest_dispenser
        NavDispenser --> RequestAttach: adjacente ao dispenser
        RequestAttach --> BlockCollected: attach success
        BlockCollected --> NavGoalZone: +pending_submit
        NavGoalZone --> Submit: goalZone(0,0)
        Submit --> ReSubmit: success → re-submit
        Submit --> RotateTry: failed → rotate cw
        RotateTry --> Submit: retry (até 4×)
        ReSubmit --> Finalize: task completa/expirada
    }

    SoloistTask --> Exploring: !finalize_task

    state "Emergency" as Emergency {
        [*] --> EnergyCheck: energy < 5
        EnergyCheck --> Skip: action(skip)
        [*] --> Deactivated: am_deactivated
        Deactivated --> Skip2: action(skip)
        [*] --> Stuck: 20+ steps mesma pos
        Stuck --> Detach: detach ou finalize
    }

    Exploring --> Emergency: condição detectada
    SoloistTask --> Emergency: condição detectada
    Emergency --> Exploring: condição resolvida
```

### 2.6 Diagrama de Estados — Squad

```mermaid
stateDiagram-v2
    [*] --> Idle: Squad formado (hardcoded)

    Idle --> Bidding: +new_task_available
    Bidding --> Idle: Leilão perdido
    Bidding --> Delegating: Leilão vencido

    Delegating --> SoloistAssigned: find_free_soloist → OK
    Delegating --> FallbackAssembler: find_free_soloist → none

    SoloistAssigned --> Idle: task finalizada
    FallbackAssembler --> Idle: task finalizada

    Idle --> MultiBlock: task com 2+ blocos
    MultiBlock --> Collecting: collectors + assembler delegados
    Collecting --> WaitingReady: blocos coletados
    WaitingReady --> Connecting: all_ready
    Connecting --> Submitting: connect success
    Submitting --> Idle: submit success

    Collecting --> Timeout: 200 steps sem progresso
    WaitingReady --> Timeout: deadline
    Timeout --> Idle: cleanup
```

### 2.7 Diagrama de Atividades — Pipeline de Decisão por Step

```mermaid
flowchart TD
    START(["+step(N) — Percepts recebidos"]) --> DEACT{am_deactivated?}

    DEACT -->|Sim| SKIP_DEACT["action(skip)"]
    DEACT -->|Não| ENERGY{energy < 5?}

    ENERGY -->|Sim| SKIP_ENERGY["action(skip)<br/>conservar energia"]
    ENERGY -->|Não| SUBMIT_CHECK{pending_submit +<br/>goalZone(0,0)?}

    SUBMIT_CHECK -->|Sim| DO_SUBMIT["action(submit(TaskName))"]
    SUBMIT_CHECK -->|Não| SUBMIT_RESULT{submitted_task +<br/>lastAction(submit)?}

    SUBMIT_RESULT -->|success| RESUBMIT["Re-submit ou finalize"]
    SUBMIT_RESULT -->|failed| ROTATE["action(rotate(cw))<br/>retry até 4×"]
    SUBMIT_RESULT -->|Não| CONNECT_CHECK{ready_to_connect?}

    CONNECT_CHECK -->|Sim + entidade adjacente| DO_CONNECT["action(connect(...))"]
    CONNECT_CHECK -->|Sim + sem adjacente| WAIT_CONNECT["action(skip)"]
    CONNECT_CHECK -->|Não| ATTACH_CHECK{waiting_attach_result?}

    ATTACH_CHECK -->|success| COLLECTED["collected_block!<br/>action(skip)"]
    ATTACH_CHECK -->|fail| RETRY_ATTACH["action(attach(Dir))"]
    ATTACH_CHECK -->|Não| REQUEST_CHECK{waiting_request?}

    REQUEST_CHECK -->|success| DO_ATTACH["action(attach(Dir))"]
    REQUEST_CHECK -->|fail| RETRY_REQ["retry request ou mover"]
    REQUEST_CHECK -->|Não| COLLECTING{collecting(Type,DX,DY)?}

    COLLECTING -->|adjacente| DO_REQUEST["action(request(Dir))"]
    COLLECTING -->|não adjacente| MOVE_DISP["action(move(Dir)) → dispenser"]
    COLLECTING -->|Não| NAV_CHECK{has_destination?}

    NAV_CHECK -->|Sim + chegou| ARRIVED["destino alcançado"]
    NAV_CHECK -->|Sim + blocked| RANDOM_DIR["direção aleatória"]
    NAV_CHECK -->|Sim| GREEDY["greedy move → destino"]
    NAV_CHECK -->|Não| EXPLORE["get_nearest_frontier<br/>→ action(move(Dir))"]
```

---

## 3. Organização MOISE+

### 3.1 Especificação Estrutural

```mermaid
graph LR
    subgraph "hive_team (Root Group)"
        subgraph "squad_group ×3 (min=2, max=4)"
            SL["squad_leader<br/>min=1, max=1"]
            COL2["collector<br/>min=1, max=2"]
            ASM2["assembler<br/>min=1, max=1"]
        end

        subgraph "sentinel_group (min=1, max=2)"
            SEN2["sentinel<br/>min=1, max=3"]
        end
    end

    SL -->|"authority<br/>(intra-group)"| COL2
    SL -->|"authority<br/>(intra-group)"| ASM2
    COL2 -->|"communication<br/>(intra-group)"| ASM2
```

### 3.2 Especificação Funcional

```mermaid
flowchart TB
    subgraph "exploration_scheme"
        ME["map_explored"] -->|parallel| DF["dispensers_found (ttf=200)"]
        ME -->|parallel| GZ["goal_zones_found (ttf=200)"]
        ME -->|parallel| RZ["role_zones_found (ttf=200)"]
    end

    subgraph "task_execution_scheme"
        TS["task_submitted"] -->|sequence| BC["blocks_collected (ttf=100)"]
        BC --> BA["blocks_assembled (ttf=50)"]
        BA --> PS["pattern_submitted (ttf=30)"]
    end

    subgraph "defense_scheme"
        TP["team_protected"] -->|parallel| GG["goal_zones_guarded"]
        TP -->|parallel| TC["threats_cleared"]
    end
```

### 3.3 Especificação Normativa

| Norma | Tipo | Role → Missão | Significado |
|-------|------|---------------|-------------|
| `n_scout` | obrigação | squad_leader → m_scout | Líder deve explorar o mapa |
| `n_collect` | obrigação | collector → m_collect | Coletor deve coletar blocos |
| `n_assemble` | obrigação | assembler → m_assemble | Montador deve montar blocos |
| `n_submit` | obrigação | assembler → m_submit | Montador deve submeter padrões |
| `n_guard` | obrigação | sentinel → m_guard | Sentinela deve guardar zonas |

---

## 4. Composição dos Esquadrões (Implementação)

```mermaid
flowchart TD
    subgraph Squad1["Squad 1"]
        A1["connectionA1<br/>🟡 leader"]
        A4["connectionA4<br/>🔵 collector"]
        A5["connectionA5<br/>🔵 collector"]
        A10["connectionA10<br/>🟣 assembler"]
    end

    subgraph Squad2["Squad 2"]
        A2["connectionA2<br/>🟡 leader"]
        A6["connectionA6<br/>🔵 collector"]
        A7["connectionA7<br/>🔵 collector"]
        A11["connectionA11<br/>🟣 assembler"]
    end

    subgraph Squad3["Squad 3"]
        A3["connectionA3<br/>🟡 leader"]
        A8["connectionA8<br/>🔵 collector"]
        A9["connectionA9<br/>🔵 collector"]
        A12["connectionA12<br/>🟣 assembler"]
    end

    subgraph SoloistPool["Pool de Soloists (todos os 15 agentes)"]
        A13["connectionA13<br/>🟢 sentinel"]
        A14["connectionA14<br/>🟢 sentinel"]
        A15["connectionA15<br/>🟢 sentinel"]
        NOTE["+ assemblers/collectors<br/>quando livres"]
    end

    A1 -->|"authority"| A4 & A5 & A10
    A2 -->|"authority"| A6 & A7 & A11
    A3 -->|"authority"| A8 & A9 & A12
```

---

## 5. Padrões de Arquitetura MAS

### 5.1 Padrão: Arquitetura BDI em Camadas (Subsumption)

A seleção de ação segue prioridade por módulo de inclusão no AgentSpeak:

```mermaid
graph LR
    subgraph "Prioridade (maior → menor)"
        direction TB
        P0["P0: SOBREVIVÊNCIA<br/>Desativado → skip<br/>Energia < 5 → skip"]
        P1["P1: SUBMIT/CONNECT<br/>pending_submit → submit<br/>ready_to_connect → connect"]
        P2["P2: COLETA<br/>waiting_request/attach → retry<br/>collecting → move/request"]
        P3["P3: NAVEGAÇÃO<br/>has_destination → greedy move<br/>sem destino → explorar fronteira"]
    end

    P0 -->|"connect_protocol.asl"| P1
    P1 -->|"collection.asl"| P2
    P2 -->|"navigation.asl"| P3

    style P0 fill:#e74c3c,color:#fff
    style P1 fill:#e67e22,color:#fff
    style P2 fill:#3498db,color:#fff
    style P3 fill:#2ecc71,color:#fff
```

### 5.2 Padrão: Contract Net (Leilão Distribuído via TaskBoard)

```mermaid
sequenceDiagram
    participant TB as TaskBoard (Artefato)
    participant SL1 as Leader squad1
    participant SL2 as Leader squad2
    participant SL3 as Leader squad3

    Note over TB,SL3: Task aparece → signal new_task_available

    par Avaliação paralela
        SL1->>SL1: Score = (Reward/NBlocks)*100 - dist_dispenser
    and
        SL2->>SL2: Score = (Reward/NBlocks)*100 - dist_dispenser
    and
        SL3->>SL3: Score = (Reward/NBlocks)*100 - dist_dispenser
    end

    SL1->>TB: place_bid(task, squad1, Score1)
    SL2->>TB: place_bid(task, squad2, Score2)
    SL3->>TB: place_bid(task, squad3, Score3)

    Note over TB: .wait(50) para bids chegarem

    SL1->>TB: resolve_auction(task) → Winner
    Note over TB: Maior score vence
```

**Fórmula de Score:**
```
Score = (Reward / NBlocks) × 100 - ManhattanDistance(leader, nearest_dispenser)
```

### 5.3 Padrão: Pool de Soloists

Mecanismo adaptativo que permite qualquer agente livre executar tasks solo:

```mermaid
flowchart TD
    LEADER["Leader ganha leilão"] --> FIND["find_free_soloist(dispX, dispY)"]
    FIND --> SEARCH{"Agente livre<br/>mais próximo?"}
    SEARCH -->|Sim| ASSIGN["mark_busy(Winner)<br/>.send(tell, soloist_task)"]
    SEARCH -->|Não| FALLBACK["send(solo_task) ao assembler do squad"]

    ASSIGN --> SOLOIST["Soloist executa:<br/>collect → nav goal → submit"]
    FALLBACK --> ASSEMBLER["Assembler executa:<br/>collect → nav goal → submit"]

    SOLOIST --> DONE["mark_free(Me)<br/>!finalize_task"]
    ASSEMBLER --> DONE
```

### 5.4 Padrão: Shared Environment (Agents & Artifacts)

```mermaid
graph TB
    subgraph "15 Agentes Jason"
        ALL["Todos os agentes"]
    end

    subgraph "CArtAgO Workspace (artefatos singleton)"
        SM["SharedMap<br/><br/><b>Signals:</b><br/>new_dispenser(X,Y,Type)<br/>new_goal_zone(X,Y)<br/><br/><b>Ops:</b> update_cell, get_nearest_*"]
        TB2["TaskBoard<br/><br/><b>Signals:</b><br/>new_task_available(...)<br/>task_assigned(Task,Squad)<br/><br/><b>Ops:</b> place_bid, resolve_auction"]
        SC2["SquadCoordinator<br/><br/><b>Signals:</b><br/>agent_ready(Squad,Agent)<br/>meeting_point_set(Squad,X,Y)<br/><br/><b>Ops:</b> find_free_soloist, mark_*"]
        HD2["HiveDashboard<br/><br/><b>Broadcast WS:</b><br/>snapshot, events, step_update<br/><br/><b>Ops:</b> log_event, update_*"]
    end

    ALL -->|"focus() + observe signals"| SM & TB2 & SC2 & HD2
    ALL -->|"update_cell, mark_visited"| SM
    ALL -->|"register_task, place_bid"| TB2
    ALL -->|"signal_ready, mark_busy/free"| SC2
    ALL -->|"log_event, set_step"| HD2
```

---

## 6. Mecanismos de Resiliência

| Mecanismo | Módulo | Trigger | Ação |
|-----------|--------|---------|------|
| Retry de request | `collection.asl` | request failed (até 5×) | Move aleatório + retry; após 5 falhas busca outro dispenser |
| Rotação no submit | `connect_protocol.asl` | submit failed | Rotaciona CW (até 4×), depois desiste |
| Detecção de stuck | `perception.asl` | 20 steps na mesma posição | Se solo_mode → finalize task; senão → detach |
| Task timeout | Cada agente | 200 steps sem progresso | cleanup + finalize |
| Task expirada | Cada agente | Deadline atingido | cleanup + finalize |
| Energia crítica | `connect_protocol.asl` | energy < 5 | action(skip) para conservar |
| Goal zone alternativa | `connect_protocol.asl` | 8 bloqueios | Troca para goal zone diferente |
| Fallback `-!` | Todos os módulos | Falha em plano | Plan failure não causa crash |
| Reconnect exponencial | Dashboard (`ws.ts`) | WebSocket desconecta | Retry 1s, 2s, 4s... (max 10s) |

---

## 7. Deployment

```mermaid
graph TB
    subgraph "Processo 1 — MASSim Server"
        MASSIM_JAR["java -jar server.jar<br/>-conf TestConfig.json<br/>--monitor 8000<br/><br/>JDK 17+<br/>TCP :12300 (agentes)<br/>HTTP :8000 (monitor web)"]
    end

    subgraph "Processo 2 — Hive MAS"
        GRADLE["./gradlew run<br/><br/>JaCaMoLauncher hive.jcm<br/>JDK 21<br/><br/>15 agentes Jason<br/>5 artefatos CArtAgO<br/>WebSocket :8765 (dashboard)"]
    end

    subgraph "Processo 3 — Dashboard (opcional)"
        VITE["cd dashboard && npm run dev<br/><br/>React 19 + Vite 8<br/>HTTP :5173<br/>Conecta ws://localhost:8765"]
    end

    subgraph "Configuração"
        CONF["conf/TestConfig.json<br/>eismassimconfig.json<br/>hive.jcm<br/>logging.properties"]
    end

    GRADLE -->|"15 TCP connections<br/>porta 12300"| MASSIM_JAR
    GRADLE -->|"WebSocket :8765"| VITE
    CONF --> MASSIM_JAR
    CONF --> GRADLE
```

---

## 8. Decisões Arquiteturais (ADRs)

### ADR-001: Pool de Soloists vs. Squads rígidos

- **Contexto**: Tasks de 1 bloco são frequentes e squads de 4 agentes são overkill.
- **Decisão**: Todos os 15 agentes participam de um pool de soloists. Líder busca o agente livre mais próximo ao dispenser.
- **Justificativa**: Maximiza throughput de tasks simples; reduz tempo ocioso; sentinels contribuem produtivamente.
- **Trade-off**: Squads ficam com menos agentes disponíveis para tasks multi-block.

### ADR-002: Artefatos CArtAgO com ConcurrentHashMap

- **Contexto**: 15 agentes acessam estado compartilhado simultaneamente.
- **Decisão**: Usar `ConcurrentHashMap` em todos os artefatos; operações atômicas; sem locks explícitos.
- **Justificativa**: Acesso concorrente seguro sem contenção; simplicidade de implementação; boa performance para operações predominantemente de leitura.
- **Trade-off**: Não garante consistência transacional entre múltiplas operações (aceitável neste contexto).

### ADR-003: A* no SharedMap (Java) vs. A* como Internal Action

- **Contexto**: Pathfinding precisa de acesso ao mapa de obstáculos.
- **Decisão**: A* implementado diretamente no SharedMap (método `astar()`), com fallback greedy para distâncias > 60.
- **Justificativa**: Acesso direto à estrutura de obstáculos sem cópia; limitação de 8000 nós evita travamento; fallback greedy garante resposta.
- **Trade-off**: `PathFinder.java` (internal action) fica redundante — usado apenas como backup sem obstáculos.

### ADR-004: Prioridade via ordem de inclusão dos .asl

- **Contexto**: Jason seleciona o primeiro plano `+step(N)` cujo contexto é satisfeito.
- **Decisão**: Incluir `connect_protocol.asl` antes de `collection.asl` antes de `navigation.asl`.
- **Justificativa**: Garante que submit/connect têm prioridade máxima; coleta vem antes de exploração; padrão simples e auditável.
- **Trade-off**: Adição de novos módulos requer cuidado com a posição de inclusão.

### ADR-005: Dashboard separado (React) vs. MASSim Monitor

- **Contexto**: O monitor MASSim mostra o grid mas não os internals dos agentes (squads, leilões, beliefs).
- **Decisão**: Dashboard React dedicado conectado via WebSocket ao artefato HiveDashboard.
- **Justificativa**: Visibilidade total do estado interno (squads, tasks, auctions, agent states); visualização 3D; independent do MASSim.
- **Trade-off**: Overhead de manutenção de artefato + frontend separado; porta adicional (8765).

### ADR-006: EnvironmentInterface singleton compartilhado

- **Contexto**: eismassim pode criar 1 conexão TCP por agente ou compartilhar.
- **Decisão**: Singleton `EnvironmentInterface` com 15 entidades registradas, compartilhado entre os 15 artefatos EISAccess.
- **Justificativa**: Uma única instância gerencia o pool de conexões; evita duplicação de resources; simplifica inicialização.
- **Trade-off**: Lock contention em `getPercepts()`/`performAction()` se muitos agentes acessam simultaneamente (mitigado pelo scheduling mode do EIS).

---

## 9. Métricas do Sistema

| Aspecto | Valor |
|---------|-------|
| Agentes | 15 (4 roles) |
| Artefatos CArtAgO | 5 tipos (19 instâncias total) |
| Ações internas Java | 5 |
| Linhas de código (src/) | ~3.230 |
| Linhas AgentSpeak | ~1.470 |
| Linhas Java | ~1.640 |
| Linhas XML (MOISE+) | ~120 |
| Dashboard (frontend) | ~2.000 linhas TypeScript |
| Dependências | JaCaMo 1.3.0, eismassim 4.5, Java-WebSocket 1.5.7, json-20240303 |
| Java version | 21 |
| Gradle version | 9.2 |
