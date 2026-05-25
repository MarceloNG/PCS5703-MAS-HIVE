# Arquitetura do Sistema — Projeto HIVE / MAPC 2022

Documento de arquitetura completo com diagramas C4, UML e padrões MAS.
Todos os diagramas utilizam Mermaid.

---

## 1. Modelo C4

### 1.1 Nível 1 — Diagrama de Contexto

Visão de mais alto nível: o sistema HIVE, os atores e sistemas externos com os quais interage.

```mermaid
graph TB
    subgraph Atores
        TEAM["👤 Time HIVE<br/>Desenvolvedores que<br/>configuram e monitoram"]
    end

    subgraph Sistema
        HIVE["🔷 HIVE MAS<br/>SMA com esquadoes autonomos BDI<br/>coordenados por leilao distribuido"]
    end

    subgraph Sistemas Externos
        MASSIM["🔶 Servidor MASSIM 2022<br/>Simulador Agents Assemble<br/>Gerencia ambiente, acoes,<br/>normas e pontuacao"]
        MONITOR["🔶 Web Monitor<br/>Visualizacao em tempo real<br/>da simulacao e replays"]
        OPPONENT["🔶 Time Adversario<br/>SMA concorrente competindo<br/>no mesmo cenario"]
    end

    TEAM -->|"Configura e monitora<br/>(JCM + JSON)"| HIVE
    HIVE -->|"Envia acoes / Recebe percepts<br/>(TCP/JSON porta 12300)"| MASSIM
    MASSIM -->|"Streaming do estado<br/>(HTTP porta 8000)"| MONITOR
    TEAM -->|"Visualiza simulacao<br/>(Browser)"| MONITOR
    OPPONENT -->|"Envia acoes / Recebe percepts<br/>(TCP/JSON)"| MASSIM

    style HIVE fill:#1168bd,color:#fff,stroke:#0b4884
    style MASSIM fill:#999,color:#fff,stroke:#666
    style MONITOR fill:#999,color:#fff,stroke:#666
    style OPPONENT fill:#999,color:#fff,stroke:#666
    style TEAM fill:#08427b,color:#fff,stroke:#052e56
```

### 1.2 Nível 2 — Diagrama de Containers

Decomposição do sistema HIVE nos seus containers (processos/runtimes).

```mermaid
graph TB
    subgraph HIVE_MAS["HIVE MAS (System Boundary)"]
        direction TB
        JACAMO["JaCaMo Runtime<br/><i>Java 21 + Gradle</i><br/>Integra agentes,<br/>organizacao e ambiente"]

        JASON["Jason Engine<br/><i>AgentSpeak-L</i><br/>Motor BDI: squad_leader,<br/>collector, assembler, sentinel"]

        MOISE["MOISE+ Engine<br/><i>XML + NPL</i><br/>Papeis, grupos,<br/>missoes e normas"]

        CARTAGO["CArtAgO Workspace<br/><i>Java</i><br/>SharedMap, TaskBoard,<br/>NormMonitor, SquadCoordinator"]

        EISMASSIM["EISMASSim<br/><i>Java / EIS 0.5</i><br/>Proxy JSON - IILang"]
    end

    MASSIM_EXT["Servidor MASSIM<br/>Simulador Agents Assemble 2022"]

    JACAMO -->|"Gerencia ciclo de vida"| JASON
    JACAMO -->|"Carrega spec organizacional"| MOISE
    JACAMO -->|"Instancia artefatos"| CARTAGO
    JASON -->|"Consulta obrigacoes e papeis"| MOISE
    JASON -->|"Observa propriedades /<br/>Executa operacoes"| CARTAGO
    JASON -->|"Percepts IILang /<br/>Actions IILang"| EISMASSIM
    EISMASSIM -->|"JSON sobre TCP<br/>porta 12300"| MASSIM_EXT

    style JACAMO fill:#1168bd,color:#fff,stroke:#0b4884
    style JASON fill:#1168bd,color:#fff,stroke:#0b4884
    style MOISE fill:#1168bd,color:#fff,stroke:#0b4884
    style CARTAGO fill:#1168bd,color:#fff,stroke:#0b4884
    style EISMASSIM fill:#1168bd,color:#fff,stroke:#0b4884
    style MASSIM_EXT fill:#999,color:#fff,stroke:#666
```

### 1.3 Nível 3 — Diagrama de Componentes

Detalhamento dos componentes dentro de cada container.

```mermaid
graph TB
    subgraph "Jason Engine — Agentes BDI"
        SL["squad_leader.asl<br/><i>Exploração, coordenação,<br/>leilão de tarefas</i>"]
        CO["collector.asl<br/><i>Coleta de blocos,<br/>transporte ao meeting point</i>"]
        AS["assembler.asl<br/><i>Montagem de padrões,<br/>submit em goal zones</i>"]
        SE["sentinel.asl<br/><i>Patrulha, clear ofensivo,<br/>proteção de assemblers</i>"]

        subgraph "common/ — Planos Reutilizáveis"
            NAV["navigation.asl<br/><i>Pathfinding A*</i>"]
            PER["perception.asl<br/><i>Processamento de percepts</i>"]
            COM["communication.asl<br/><i>Protocolos de mensagem</i>"]
            NOR["norms.asl<br/><i>Compliance de normas</i>"]
        end
    end

    subgraph "CArtAgO Workspace — Artefatos Java"
        SM["SharedMap.java<br/><i>Mapa incremental<br/>compartilhado</i>"]
        TB["TaskBoard.java<br/><i>Board de tarefas +<br/>protocolo de leilão</i>"]
        NM["NormMonitor.java<br/><i>Tracking de normas<br/>do servidor</i>"]
        SC["SquadCoordinator.java<br/><i>Estado dos esquadrões,<br/>meeting points</i>"]
    end

    subgraph "MOISE+ — Organização"
        ORG["hive_org.xml"]
        SS["Structural Spec<br/><i>Papéis + Grupos + Links</i>"]
        FS["Functional Spec<br/><i>Schemes + Goals + Missions</i>"]
        NS["Normative Spec<br/><i>Obrigações por papel</i>"]
        ORG --> SS
        ORG --> FS
        ORG --> NS
    end

    subgraph "Internal Actions — Java"
        PF["PathFinder.java<br/><i>A* em grade com obstáculos</i>"]
        PM["PatternMatcher.java<br/><i>Match padrão de blocos</i>"]
        DC["DirectionCalculator.java<br/><i>Direção relativa n/s/e/w</i>"]
    end

    subgraph "EISMASSim"
        EIS["EIS Proxy<br/><i>JSON ↔ IILang</i>"]
        CFG["eismassimconfig.json"]
    end

    SL --> NAV & PER & COM & NOR
    CO --> NAV & PER & COM & NOR
    AS --> NAV & PER & COM & NOR
    SE --> NAV & PER & COM & NOR

    SL & CO & AS & SE --> SM & TB & NM & SC
    SL & CO & AS & SE --> PF & PM & DC
    SL & CO & AS & SE --> EIS

    EIS --> MASSIM["Servidor MASSIM<br/>TCP:12300"]
```

### 1.4 Nível 4 — Código (Estrutura interna de um agente BDI)

```mermaid
graph TD
    subgraph "squad_leader.asl — Estrutura Interna"
        B["Beliefs<br/><i>my_role, squad_id, energy,<br/>known_dispensers, known_goals,<br/>active_tasks, active_norms</i>"]

        G["Goals<br/><i>!explore_map<br/>!evaluate_tasks<br/>!coordinate_squad<br/>!monitor_norms</i>"]

        subgraph "Plans (regras de seleção por contexto)"
            P1["+!explore_map<br/>: frontier(X,Y)<br/>← navigate_to(X,Y)"]
            P2["+!evaluate_tasks<br/>: task(N,D,R,Reqs)<br/>← calculate_bid(N,R,Reqs)"]
            P3["+!coordinate_squad<br/>: assigned_task(T)<br/>← delegate_collection(T)"]
            P4["+!monitor_norms<br/>: norm(Id,_,_,Reqs,_)<br/>← check_compliance(Reqs)"]
            P5["+clear_marker(X,Y,R)<br/>: in_danger_zone(X,Y,R)<br/>← !evacuate"]
        end

        B --> P1 & P2 & P3 & P4 & P5
        G --> P1 & P2 & P3 & P4
    end
```

---

## 2. Diagramas UML

### 2.1 Diagrama de Classes — Artefatos CArtAgO

```mermaid
classDiagram
    class Artifact {
        <<abstract>>
        #defineObsProperty(name, args)
        #signal(name, args)
    }

    class SharedMap {
        -cells: ConcurrentHashMap~String,String~
        -dispensers: List~Position~
        -goalZones: List~Position~
        -roleZones: List~Position~
        -obstacles: Set~Position~
        +update_cell(x, y, content) void
        +get_nearest_frontier(agX, agY) Position
        +get_nearest_dispenser(agX, agY, type) Position
        +get_nearest_goal_zone(agX, agY) Position
        +get_path(fromX, fromY, toX, toY) List~Direction~
        +is_explored(x, y) boolean
    }

    class TaskBoard {
        -availableTasks: Map~String,Task~
        -assignedTasks: Map~String,String~
        -bids: Map~String,List~Bid~~
        +register_task(name, deadline, reward, reqs) void
        +evaluate_task(name) double
        +place_bid(taskName, squadId, bidValue) void
        +resolve_auction(taskName) String
        +claim_task(taskName, squadId) void
        +complete_task(taskName) void
        +remove_expired() void
    }

    class NormMonitor {
        -activeNorms: Map~String,Norm~
        -violations: Set~String~
        +update_norms(normsList) void
        +get_carry_limit() int
        +get_role_limit(roleName) int
        +check_compliance(agentName) boolean
        +is_norm_active(normId) boolean
    }

    class SquadCoordinator {
        -squads: Map~String,Squad~
        -meetingPoints: Map~String,Position~
        -readySignals: Map~String,Set~String~~
        +join_squad(squadId, agentName, role) void
        +set_meeting_point(squadId, x, y) void
        +signal_ready(squadId, agentName) void
        +all_ready(squadId) boolean
        +get_squad_members(squadId) List~String~
        +reassign_member(squadId, agentName, newRole) void
    }

    class Task {
        +name: String
        +deadline: int
        +reward: int
        +requirements: List~BlockReq~
    }

    class BlockReq {
        +x: int
        +y: int
        +type: String
    }

    class Squad {
        +id: String
        +members: Map~String,String~
        +currentTask: String
        +state: SquadState
    }

    class Position {
        +x: int
        +y: int
        +manhattanDistance(other) int
    }

    Artifact <|-- SharedMap
    Artifact <|-- TaskBoard
    Artifact <|-- NormMonitor
    Artifact <|-- SquadCoordinator

    TaskBoard --> "*" Task
    Task --> "*" BlockReq
    SquadCoordinator --> "*" Squad
    SharedMap --> "*" Position
```

### 2.2 Diagrama de Classes — Internal Actions Java

```mermaid
classDiagram
    class DefaultInternalAction {
        <<abstract>>
        +execute(ts, un, args) Object
    }

    class PathFinder {
        -openSet: PriorityQueue~Node~
        -closedSet: Set~Position~
        +execute(ts, un, args) Object
        -astar(from, to, obstacles) List~Direction~
        -heuristic(a, b) int
        -reconstructPath(node) List~Direction~
    }

    class PatternMatcher {
        +execute(ts, un, args) Object
        -matchPattern(attachments, requirements) boolean
        -rotatePattern(reqs, direction) List~BlockReq~
        -calculateRotationsNeeded(current, target) int
    }

    class DirectionCalculator {
        +execute(ts, un, args) Object
        -relativeDirection(fromX, fromY, toX, toY) String
        -adjacentDirection(agX, agY, targetX, targetY) String
    }

    DefaultInternalAction <|-- PathFinder
    DefaultInternalAction <|-- PatternMatcher
    DefaultInternalAction <|-- DirectionCalculator
```

### 2.3 Diagrama de Sequência — Ciclo Completo de uma Tarefa

```mermaid
sequenceDiagram
    participant MASSIM as Servidor MASSIM
    participant EIS as EISMASSim
    participant SL as Squad Leader
    participant TB as TaskBoard
    participant C1 as Collector 1
    participant C2 as Collector 2
    participant AS as Assembler
    participant SM as SharedMap
    participant SC as SquadCoordinator

    Note over MASSIM,SC: FASE 1 — Detecção e Leilão

    MASSIM->>EIS: REQUEST-ACTION (percepts com task)
    EIS->>SL: task(task5, 300, 60, [req(0,1,b0), req(1,1,b1)])
    SL->>TB: evaluate_task(task5)
    TB-->>SL: score = 60 / (2 × avg_dist)
    SL->>TB: place_bid(task5, squad_1, score)
    TB->>TB: resolve_auction(task5)
    TB-->>SL: squad_1 venceu

    Note over MASSIM,SC: FASE 2 — Delegação e Coleta

    SL->>SM: get_nearest_dispenser(myX, myY, b0)
    SM-->>SL: dispenser_b0(15, 20)
    SL->>SM: get_nearest_dispenser(myX, myY, b1)
    SM-->>SL: dispenser_b1(22, 18)
    SL->>SM: get_nearest_goal_zone(myX, myY)
    SM-->>SL: goal_zone(30, 25)

    SL->>SC: set_meeting_point(squad_1, 28, 24)
    SL->>C1: .send(achieve, collect_block(b0, 15, 20))
    SL->>C2: .send(achieve, collect_block(b1, 22, 18))

    par Collector 1 vai ao dispenser b0
        C1->>MASSIM: move(s), move(e), ..., request(s), attach(s)
    and Collector 2 vai ao dispenser b1
        C2->>MASSIM: move(e), move(s), ..., request(w), attach(w)
    end

    Note over MASSIM,SC: FASE 3 — Montagem (Connect Sincronizado)

    C1->>SC: signal_ready(squad_1, collector1)
    C2->>SC: signal_ready(squad_1, collector2)
    SC-->>AS: all_ready(squad_1) = true

    Note over C1,AS: Agentes se posicionam adjacentes no meeting point

    par Connect simultâneo
        C1->>MASSIM: connect(assembler1, 0, 2)
    and
        AS->>MASSIM: connect(collector1, 0, -1)
    end

    AS->>MASSIM: rotate(cw)

    par Segundo connect
        C2->>MASSIM: connect(assembler1, 1, 1)
    and
        AS->>MASSIM: connect(collector2, -1, 0)
    end

    Note over MASSIM,SC: FASE 4 — Submissão

    AS->>SM: get_nearest_goal_zone(myX, myY)
    SM-->>AS: goal_zone(30, 25)
    AS->>MASSIM: move(...) até goal zone
    AS->>MASSIM: submit(task5)
    MASSIM-->>AS: lastActionResult(success)

    Note over MASSIM,SC: FASE 5 — Re-submissão

    AS->>MASSIM: submit(task5)
    MASSIM-->>AS: lastActionResult(success)
    AS->>TB: complete_task(task5)
```

### 2.4 Diagrama de Sequência — Evasão de Clear Event

```mermaid
sequenceDiagram
    participant MASSIM as Servidor MASSIM
    participant AG as Qualquer Agente
    participant SM as SharedMap

    MASSIM->>AG: thing(5, 3, marker, clear)
    MASSIM->>AG: thing(5, 4, marker, clear)
    MASSIM->>AG: thing(6, 3, marker, clear)

    AG->>AG: Detecta markers "clear" → estima centro(5,3) e raio(2)
    AG->>AG: Verifica: my_pos dentro da zona de perigo?

    alt Está na zona de perigo
        AG->>AG: Prioridade máxima: !evacuate
        AG->>AG: Calcula direção de fuga (oposta ao centro)
        AG->>MASSIM: move(n) [foge da zona]
        AG->>MASSIM: move(n) [continua fugindo]
        AG-->>AG: .broadcast(tell, clear_event(5, 3, 2))
    else Não está na zona
        AG->>SM: update_cell(5, 3, "danger_zone")
        AG->>AG: Evita planejar rotas pela zona
    end

    Note over MASSIM,SM: 5 steps depois → evento resolve
    MASSIM->>AG: Obstáculos novos aparecem, blocos destruídos
    AG->>SM: update_cell(X, Y, "obstacle") para novos obstáculos
```

### 2.5 Diagrama de Sequência — Adaptação a Normas

```mermaid
sequenceDiagram
    participant MASSIM as Servidor MASSIM
    participant AG as Todos os Agentes
    participant NM as NormMonitor
    participant SL as Squad Leader
    participant TB as TaskBoard

    MASSIM->>AG: norm(n3, 50, 200, [req(block, any, 1)], 15)
    Note over AG: Norma Carry: máximo 1 bloco, punição 15 energia/step

    AG->>NM: update_norms([norm(n3, carry, 1)])
    NM-->>AG: active_norm(n3, carry, 1)

    SL->>SL: carry_limit agora é 1
    SL->>TB: Repriorizar: favorecer tasks de 1 bloco

    alt Collector carregando 2+ blocos
        AG->>AG: Violando norma! Detach bloco excedente
        AG->>MASSIM: detach(s)
        Note over AG: Fica com apenas 1 bloco → compliance
    end

    alt Task exige 2+ blocos
        SL->>SL: Estratégia: collectors carregam 1 bloco cada
        SL->>SL: Connect imediato no meeting point → descarrega
        Note over SL: Minimiza tempo em violação
    end

    MASSIM->>AG: Step 200: norma n3 expira
    AG->>NM: update_norms([]) → remove n3
    NM-->>AG: carry_limit volta ao normal
    SL->>TB: Repriorizar: voltar a aceitar tasks complexas
```

### 2.6 Diagrama de Estados — Ciclo de Vida do Agente

```mermaid
stateDiagram-v2
    [*] --> Initializing: Simulação inicia

    Initializing --> Exploring: Conectado ao MASSIM

    state "Exploring" as Exploring {
        [*] --> ScanArea: Percebe ao redor
        ScanArea --> UpdateMap: Novos elementos encontrados
        UpdateMap --> CheckFrontier: Mapa atualizado
        CheckFrontier --> NavigateToFrontier: Fronteira encontrada
        CheckFrontier --> SurveyTarget: Sem fronteira próxima
        NavigateToFrontier --> ScanArea: Chegou à fronteira
        SurveyTarget --> ScanArea: Survey completo
    }

    Exploring --> TaskAssigned: Squad recebe task

    state "Executing Task" as ExecutingTask {
        [*] --> Collecting
        state "Collecting" as Collecting {
            [*] --> GoToDispenser
            GoToDispenser --> RequestBlock: Adjacente ao dispenser
            RequestBlock --> AttachBlock: Bloco criado
            AttachBlock --> GoToMeetingPoint: Bloco attached
        }
        Collecting --> Assembling: No meeting point

        state "Assembling" as Assembling {
            [*] --> WaitPartners
            WaitPartners --> ExecuteConnect: Todos prontos
            ExecuteConnect --> RotateBlocks: Connect OK
            RotateBlocks --> VerifyPattern: Rotação completa
            VerifyPattern --> ExecuteConnect: Faltam blocos
            VerifyPattern --> GoToGoalZone: Padrão completo
        }
        Assembling --> Submitting: Na goal zone

        state "Submitting" as Submitting {
            [*] --> SubmitTask
            SubmitTask --> ReSubmit: Sucesso + task ativa
            SubmitTask --> Done: Task expirou
            ReSubmit --> SubmitTask: Re-submissão
            ReSubmit --> Done: Task expirou ou goal zone moveu
        }
    }

    ExecutingTask --> Exploring: Task concluída ou expirada

    state "Emergency" as Emergency {
        [*] --> DetectDanger
        DetectDanger --> Evacuate: Clear markers detectados
        Evacuate --> Safe: Fora da zona de perigo
    }

    Exploring --> Emergency: Clear event detectado
    ExecutingTask --> Emergency: Clear event detectado
    Emergency --> Exploring: Retoma exploração
    Emergency --> ExecutingTask: Retoma task (se viável)

    state "Deactivated" as Deactivated
    Exploring --> Deactivated: Energia = 0
    ExecutingTask --> Deactivated: Energia = 0
    Emergency --> Deactivated: Não evacuou a tempo
    Deactivated --> Exploring: Reativado após N steps
```

### 2.7 Diagrama de Estados — Ciclo de Vida do Esquadrão

```mermaid
stateDiagram-v2
    [*] --> Idle: Esquadrão formado

    Idle --> Bidding: Nova task detectada
    Bidding --> Idle: Leilão perdido
    Bidding --> Assigned: Leilão vencido

    Assigned --> Collecting: Collectors delegados
    Collecting --> Assembling: Todos no meeting point
    Assembling --> Submitting: Padrão montado
    Submitting --> Idle: Task completa

    Collecting --> Degraded: Membro desativado
    Assembling --> Degraded: Membro desativado
    Degraded --> Collecting: Membro substituído/recuperado
    Degraded --> Idle: Task abandonada (deadline)

    Idle --> Reorganizing: Norma Adopt restringe roles
    Reorganizing --> Idle: Roles redistribuídos
```

### 2.8 Diagrama de Atividades — Pipeline de Decisão por Step

```mermaid
flowchart TD
    START([Novo Step — Percepts Recebidos]) --> PARSE[Processar percepts<br/>Atualizar beliefs]
    PARSE --> NORM_CHECK{Norma violada?}

    NORM_CHECK -->|Sim| FIX_NORM[Ajustar: detach bloco<br/>ou trocar role]
    FIX_NORM --> EMERGENCY_CHECK

    NORM_CHECK -->|Não| EMERGENCY_CHECK{Clear event<br/>na zona de perigo?}

    EMERGENCY_CHECK -->|Sim| EVACUATE[Calcular rota de fuga<br/>Executar move]
    EMERGENCY_CHECK -->|Não| ENERGY_CHECK{Energia < 20%?}

    ENERGY_CHECK -->|Sim| CONSERVE[Modo conservador<br/>Evitar ações de alto custo]
    ENERGY_CHECK -->|Não| ROLE_CHECK{Qual meu papel<br/>MOISE+?}

    CONSERVE --> EXECUTE_ACTION

    ROLE_CHECK -->|squad_leader| LEADER_DECIDE
    ROLE_CHECK -->|collector| COLLECTOR_DECIDE
    ROLE_CHECK -->|assembler| ASSEMBLER_DECIDE
    ROLE_CHECK -->|sentinel| SENTINEL_DECIDE

    subgraph "Squad Leader"
        LEADER_DECIDE{Tem task<br/>atribuída?}
        LEADER_DECIDE -->|Não| EXPLORE[Explorar fronteira<br/>+ avaliar novas tasks]
        LEADER_DECIDE -->|Sim| COORDINATE[Coordenar esquadrão<br/>Atualizar meeting point]
    end

    subgraph "Collector"
        COLLECTOR_DECIDE{Tem bloco<br/>para coletar?}
        COLLECTOR_DECIDE -->|Não| WAIT_ORDER[Aguardar ordem<br/>ou explorar]
        COLLECTOR_DECIDE -->|Sim| GO_COLLECT[Navegar ao dispenser<br/>Request + Attach]
        GO_COLLECT --> HAS_BLOCK{Bloco<br/>attached?}
        HAS_BLOCK -->|Sim| GO_MEETING[Navegar ao<br/>meeting point]
        HAS_BLOCK -->|Não| GO_COLLECT
    end

    subgraph "Assembler"
        ASSEMBLER_DECIDE{Parceiros<br/>prontos?}
        ASSEMBLER_DECIDE -->|Não| WAIT_PARTNERS[Aguardar no<br/>meeting point]
        ASSEMBLER_DECIDE -->|Sim| DO_CONNECT[Executar connect<br/>+ rotate]
        DO_CONNECT --> PATTERN_OK{Padrão<br/>completo?}
        PATTERN_OK -->|Não| WAIT_PARTNERS
        PATTERN_OK -->|Sim| GO_SUBMIT[Navegar à goal zone<br/>Submit task]
    end

    subgraph "Sentinel"
        SENTINEL_DECIDE{Inimigo em<br/>goal zone?}
        SENTINEL_DECIDE -->|Sim| ATTACK[Clear no inimigo]
        SENTINEL_DECIDE -->|Não| PATROL[Patrulhar goal zones<br/>conhecidas]
    end

    EXPLORE & COORDINATE --> EXECUTE_ACTION
    WAIT_ORDER & GO_MEETING --> EXECUTE_ACTION
    ATTACK & PATROL --> EXECUTE_ACTION
    WAIT_PARTNERS & GO_SUBMIT --> EXECUTE_ACTION
    EVACUATE --> EXECUTE_ACTION

    EXECUTE_ACTION([Envia ação ao MASSIM])
```

---

## 3. Padrões de Arquitetura MAS

### 3.1 Padrão: Arquitetura BDI em Camadas (Híbrida)

Inspirado nas Touring Machines (Ferguson, 1992) e INTERRAP (Müller, 1996), cada agente opera com três camadas de processamento com prioridades distintas.

```mermaid
graph TB
    subgraph "Arquitetura Híbrida BDI — Camadas por Prioridade"
        direction TB

        subgraph "Camada 3 — Social / Organizacional" 
            S1["Coordenação de esquadrão"]
            S2["Leilão de tarefas<br/>(Contract Net)"]
            S3["Redistribuição de papéis<br/>(MOISE+)"]
            S4["Compartilhamento de mapa<br/>(broadcast)"]
        end

        subgraph "Camada 2 — Deliberativa / BDI"
            D1["Seleção de goals<br/>(explorar, coletar, montar)"]
            D2["Planejamento de rota<br/>(A*)"]
            D3["Avaliação de tarefas<br/>(reward / custo)"]
            D4["Commitment strategy<br/>(manter intenção)"]
        end

        subgraph "Camada 1 — Reativa (PRIORIDADE MÁXIMA)"
            R1["Evasão de clear events"]
            R2["Detach em violação de norma"]
            R3["Desvio de obstáculo"]
            R4["Resposta a desativação"]
        end
    end

    PERCEPTS([Percepts do MASSIM]) --> R1 & R2 & R3 & R4
    R1 & R2 & R3 & R4 -->|Se não ativado| D1 & D2 & D3 & D4
    D1 & D2 & D3 & D4 -->|Quando ocioso ou<br/>coordenação necessária| S1 & S2 & S3 & S4
    S1 & S2 & S3 & S4 --> ACTION([Ação Final])
    D1 & D2 & D3 & D4 --> ACTION
    R1 & R2 & R3 & R4 --> ACTION

    style R1 fill:#ff6b6b,color:#fff
    style R2 fill:#ff6b6b,color:#fff
    style R3 fill:#ff6b6b,color:#fff
    style R4 fill:#ff6b6b,color:#fff
```

**Implementação em AgentSpeak**: A prioridade é controlada pela ordem dos planos no arquivo `.asl` e por anotações de prioridade. Planos reativos (clear event, norma) são declarados primeiro e com contextos mais específicos, garantindo que sejam selecionados antes dos planos deliberativos.

### 3.2 Padrão: Contract Net Protocol (Leilão Distribuído)

Protocolo de coordenação para alocação de tarefas entre esquadrões, baseado no Contract Net de Smith (1980).

```mermaid
sequenceDiagram
    participant TB as TaskBoard<br/>(Artefato)
    participant SL1 as Squad Leader 1
    participant SL2 as Squad Leader 2
    participant SL3 as Squad Leader 3

    Note over TB,SL3: Nova task aparece nos percepts de todos

    TB->>TB: register_task(task7, 500, 80, reqs)

    par Avaliação paralela
        SL1->>TB: evaluate_task(task7)
        TB-->>SL1: base_score = 80/3 = 26.7
        SL1->>SL1: bid = 26.7 / dist_to_dispensers = 4.5
    and
        SL2->>TB: evaluate_task(task7)
        TB-->>SL2: base_score = 26.7
        SL2->>SL2: bid = 26.7 / dist_to_dispensers = 2.1
    and
        SL3->>TB: evaluate_task(task7)
        TB-->>SL3: base_score = 26.7
        SL3->>SL3: bid = 26.7 / dist_to_dispensers = 6.8
    end

    SL1->>TB: place_bid(task7, squad_1, 4.5)
    SL2->>TB: place_bid(task7, squad_2, 2.1)
    SL3->>TB: place_bid(task7, squad_3, 6.8)

    TB->>TB: resolve_auction(task7)<br/>Maior bid = squad_3 (6.8)

    TB-->>SL3: won_auction(task7)
    TB-->>SL1: lost_auction(task7)
    TB-->>SL2: lost_auction(task7)

    SL3->>TB: claim_task(task7, squad_3)
```

### 3.3 Padrão: Organização MOISE+ — Estrutural × Funcional × Normativo

```mermaid
graph LR
    subgraph "Structural Specification"
        direction TB
        R_SL["Role: squad_leader"]
        R_CO["Role: collector"]
        R_AS["Role: assembler"]
        R_SE["Role: sentinel"]

        G_SQ["Group: squad_group<br/>(2..4 membros)"]
        G_SN["Group: sentinel_group<br/>(1..2 membros)"]

        G_SQ --- R_SL & R_CO & R_AS
        G_SN --- R_SE

        R_SL -->|authority| R_CO
        R_SL -->|authority| R_AS
        R_CO -->|communication| R_AS
    end

    subgraph "Functional Specification"
        direction TB
        SCH_E["Scheme: exploration"]
        SCH_T["Scheme: task_execution"]
        SCH_D["Scheme: defense"]

        SCH_E --> M_SC["Mission: m_scout"]
        SCH_T --> M_CO2["Mission: m_collect"]
        SCH_T --> M_AS2["Mission: m_assemble"]
        SCH_T --> M_SU["Mission: m_submit"]
        SCH_D --> M_GU["Mission: m_guard"]
    end

    subgraph "Normative Specification"
        direction TB
        N1["squad_leader MUST m_scout"]
        N2["collector MUST m_collect"]
        N3["assembler MUST m_assemble"]
        N4["assembler MUST m_submit"]
        N5["sentinel MUST m_guard"]
    end

    R_SL -.->|obrigação| N1
    R_CO -.->|obrigação| N2
    R_AS -.->|obrigação| N3 & N4
    R_SE -.->|obrigação| N5

    M_SC -.->|vinculada| N1
    M_CO2 -.->|vinculada| N2
    M_AS2 -.->|vinculada| N3
    M_SU -.->|vinculada| N4
    M_GU -.->|vinculada| N5
```

### 3.4 Padrão: Shared Environment (A&A — Agents & Artifacts)

```mermaid
graph TB
    subgraph "Agents (Jason)"
        A1["squad_leader_1"]
        A2["collector_1"]
        A3["collector_2"]
        A4["assembler_1"]
        A5["sentinel_1"]
    end

    subgraph "Workspace (CArtAgO)"
        SM["SharedMap<br/><br/>ObsProps:<br/>dispenser(X,Y,Type)<br/>goal_zone(X,Y)<br/>frontier(X,Y)"]
        TB2["TaskBoard<br/><br/>ObsProps:<br/>available_task(N,D,R,Reqs)<br/>assigned_task(Squad,Task)<br/>task_score(Name,Score)"]
        NM["NormMonitor<br/><br/>ObsProps:<br/>active_norm(Id,Type,Limit)<br/>carry_limit(N)"]
        SC2["SquadCoordinator<br/><br/>ObsProps:<br/>meeting_point(Sq,X,Y)<br/>connect_ready(Sq,Ag)"]
    end

    A1 -->|focus + observe| SM & TB2 & NM & SC2
    A2 -->|focus + observe| SM & TB2 & SC2
    A3 -->|focus + observe| SM & TB2 & SC2
    A4 -->|focus + observe| SM & TB2 & SC2
    A5 -->|focus + observe| SM & NM

    A1 -->|update_cell()| SM
    A2 -->|update_cell()| SM
    A3 -->|update_cell()| SM
    A4 -->|update_cell()| SM
    A5 -->|update_cell()| SM

    A1 -->|place_bid()<br/>claim_task()| TB2
    A4 -->|complete_task()| TB2
    A1 -->|set_meeting_point()| SC2
    A2 & A3 -->|signal_ready()| SC2
```

### 3.5 Padrão: Subsumption de Prioridades (Brooks simplificado)

A seleção da ação final segue um modelo de subsumption onde comportamentos de maior prioridade suprimem os de menor prioridade.

```mermaid
graph LR
    subgraph "Prioridade (maior → menor)"
        direction TB
        P0["P0: SOBREVIVÊNCIA<br/>Evacuação de clear event<br/>Reativação pós-desativação"]
        P1["P1: COMPLIANCE<br/>Detach para cumprir norma Carry<br/>Trocar role para norma Adopt"]
        P2["P2: TASK EXECUTION<br/>Coletar, montar, submeter<br/>Connect sincronizado"]
        P3["P3: EXPLORAÇÃO<br/>Navegar a fronteira<br/>Survey dispenser/goal"]
        P4["P4: MANUTENÇÃO<br/>Patrulhar (sentinel)<br/>Reposicionar"]
    end

    P0 -->|suprime| P1
    P1 -->|suprime| P2
    P2 -->|suprime| P3
    P3 -->|suprime| P4

    style P0 fill:#e74c3c,color:#fff
    style P1 fill:#e67e22,color:#fff
    style P2 fill:#3498db,color:#fff
    style P3 fill:#2ecc71,color:#fff
    style P4 fill:#95a5a6,color:#fff
```

---

## 4. Deployment — Diagrama de Implantação

```mermaid
graph TB
    subgraph "Máquina A — Servidor de Simulação"
        MASSIM_JAR["massim-server.jar<br/>JDK 17+<br/>TCP :12300<br/>HTTP :8000 (monitor)"]
        CONF["conf/<br/>server.json<br/>accounts.json"]
        REPLAYS["replays/<br/>*.json"]
        MASSIM_JAR --- CONF & REPLAYS
    end

    subgraph "Máquina B — Time HIVE"
        GRADLE["Gradle Build<br/>JDK 21+"]
        JACAMO_RT["JaCaMo Runtime"]
        HIVE_JCM["hive.jcm"]
        EIS_JAR["eismassim.jar"]
        EIS_CONF["eismassimconfig.json"]
        ASL_FILES[".asl files<br/>(4 agentes × N instâncias)"]
        ORG_XML["hive_org.xml"]
        ENV_JAVA["artefatos .java"]

        GRADLE --> JACAMO_RT
        JACAMO_RT --- HIVE_JCM & EIS_JAR & ASL_FILES & ORG_XML & ENV_JAVA
        EIS_JAR --- EIS_CONF
    end

    subgraph "Máquina C — Time Adversário"
        OPP["SMA Adversário<br/>(qualquer plataforma)"]
    end

    subgraph "Browser"
        MON["Web Monitor<br/>Visualização"]
    end

    JACAMO_RT -->|"TCP :12300<br/>15 conexões<br/>(1 por agente)"| MASSIM_JAR
    OPP -->|"TCP :12300<br/>15 conexões"| MASSIM_JAR
    MASSIM_JAR -->|"HTTP :8000"| MON
```

---

## 5. Decisões Arquiteturais (ADRs)

### ADR-001: Esquadrões autônomos vs. coordenador central

- **Contexto**: Times MAPC precisam coordenar 15 agentes para completar tarefas.
- **Decisão**: Esquadrões de 3-4 agentes com autonomia local, coordenados por leilão distribuído via artefato TaskBoard.
- **Justificativa**: Elimina ponto único de falha; permite paralelismo natural (3-4 esquadrões × tarefas simultâneas); alinhado com princípio de autonomia de Wooldridge.
- **Trade-off**: Coordenação inter-esquadrão é mais fraca; possível duplicação de esforço se dois esquadrões buscam o mesmo dispenser.

### ADR-002: Artefatos CArtAgO para estado compartilhado vs. mensagens puras

- **Contexto**: Agentes precisam compartilhar mapa, estado de tarefas e normas.
- **Decisão**: Usar artefatos CArtAgO (SharedMap, TaskBoard, etc.) como fonte de verdade, complementados por mensagens Jason para alertas urgentes.
- **Justificativa**: Artefatos são observáveis por todos os agentes (sem polling); propriedades observáveis geram percepts automáticos; reduz volume de mensagens.
- **Trade-off**: Acoplamento com CArtAgO; artefatos são pontos de contenção em escrita concorrente (mitigado por ConcurrentHashMap).

### ADR-003: Navegação A* em Java vs. planejamento em AgentSpeak

- **Contexto**: Agentes precisam navegar em grade com obstáculos.
- **Decisão**: Implementar A* como internal action Java, chamável do AgentSpeak.
- **Justificativa**: A* é computacionalmente intensivo; Java é mais eficiente que AgentSpeak para algoritmos iterativos; reutilizável por todos os agentes.
- **Trade-off**: Lógica de navegação fica fora do AgentSpeak (menos "puro BDI"); necessário manter mapa de obstáculos sincronizado.

### ADR-004: Sentinel ofensivo dedicado vs. todos os agentes com capacidade de clear

- **Contexto**: Ação clear pode negar pontos ao adversário.
- **Decisão**: 1-2 agentes dedicados como sentinels com role de alto clear.
- **Justificativa**: Especialização permite role otimizado para clear; não desperdiça agentes produtivos; cria vantagem assimétrica.
- **Trade-off**: 1-2 agentes a menos para tarefas produtivas; sentinel é inútil se adversário não usa goal zones previsíveis.

### ADR-005: Adaptação de roles do servidor via role zones

- **Contexto**: O servidor MASSIM define roles com capabilities diferentes; role zones são fixas.
- **Decisão**: Agentes trocam de role do servidor conforme a fase (exploração → role de alta visão; coleta → role de boa speed com carga; ataque → role de alto clear).
- **Justificativa**: Maximiza a eficiência de cada agente em cada momento; explora uma mecânica que times simplistas ignoram.
- **Trade-off**: Precisa navegar até role zones para trocar; overhead de tempo de viagem; dependente de role zones mapeadas.
