# Documentação Completa — `src/`

## Código-Fonte do Sistema Multi-Agente Hive

O diretório `src/` contém todo o código-fonte do sistema multi-agente **Hive**, organizado segundo a convenção do **JaCaMo** (Jason + CArtAgO + MOISE). Ele é dividido em 4 subdiretórios, cada um com um papel distinto na arquitetura do sistema.

---

## Índice

1. [Estrutura do Diretório](#estrutura-do-diretório)
2. [Source Sets e Build](#source-sets-e-build)
3. [src/agt — Programas AgentSpeak](#srcagt--programas-agentspeak)
4. [src/env — Artefatos CArtAgO (Java)](#srcenv--artefatos-cartago-java)
5. [src/java — Ações Internas Jason (Java)](#srcjava--ações-internas-jason-java)
6. [src/org — Especificação Organizacional MOISE+](#srcorg--especificação-organizacional-moise)
7. [Diagramas de Arquitetura](#diagramas-de-arquitetura)

---

## Estrutura do Diretório

```
src/
├── agt/                           # Programas AgentSpeak (Jason)
│   ├── squad_leader.asl           # Agente líder de esquadrão
│   ├── collector.asl              # Agente coletor de blocos
│   ├── assembler.asl              # Agente montador/conector
│   ├── sentinel.asl               # Agente patrulheiro/solista
│   ├── dummy.asl                  # Agente mínimo de teste
│   └── common/                    # Módulos compartilhados
│       ├── perception.asl         # Processamento de percepções
│       ├── collection.asl         # Ciclo de coleta de blocos
│       ├── connect_protocol.asl   # Protocolo de connect + submit
│       ├── navigation.asl         # Navegação e exploração
│       ├── communication.asl      # Mensagens de sincronização
│       └── dashboard_hooks.asl    # Integração com dashboard
├── env/                           # Artefatos CArtAgO (Java)
│   ├── connection/                # Bridge EIS/MASSim
│   │   ├── EISAccess.java         # Artefato de conexão (por agente)
│   │   └── Translator.java        # Conversão IILang ↔ Jason AST
│   └── env/                       # Artefatos compartilhados
│       ├── SharedMap.java          # Mapa compartilhado do mundo
│       ├── TaskBoard.java          # Registro e leilão de tasks
│       ├── SquadCoordinator.java   # Coordenação de squads
│       └── HiveDashboard.java      # Dashboard WebSocket
├── java/                          # Ações internas Jason
│   └── hive/                      # Pacote de ações
│       ├── AdjacentDirection.java  # Verifica adjacência (wrap toroidal)
│       ├── ConnectCalculator.java  # Coordenadas relativas para connect
│       ├── DirectionCalculator.java # Direção greedy
│       ├── PathFinder.java         # A* pathfinding
│       └── PatternMatcher.java     # Verifica padrão de blocos
└── org/                           # Especificação organizacional
    └── hive_org.xml               # MOISE+ (roles, groups, schemes, norms)
```

**Total: 23 arquivos** (11 AgentSpeak + 6 Java artefatos + 5 Java ações internas + 1 XML organizacional)

---

## Source Sets e Build

A configuração no `build.gradle` mapeia os subdiretórios de `src/` para source sets do Gradle:

```mermaid
flowchart LR
    subgraph SourceSets["Source Sets (build.gradle)"]
        JAVA_SS["main.java"]
        RES_SS["main.resources"]
    end

    subgraph Dirs["Diretórios src/"]
        ENV["src/env/"]
        JAVA_DIR["src/java/"]
        AGT["src/agt/"]
        ORG["src/org/"]
    end

    subgraph Output["Build Output"]
        CLASSES["build/classes/java/main/"]
        RESOURCES["build/resources/main/"]
    end

    ENV --> JAVA_SS
    JAVA_DIR --> JAVA_SS
    AGT --> RES_SS
    ORG --> RES_SS

    JAVA_SS -->|"compileJava"| CLASSES
    RES_SS -->|"processResources"| RESOURCES
```

| Source Set | Diretórios | Compilação | Output |
|------------|-----------|------------|--------|
| `main.java` | `src/env`, `src/java` | javac (Java 21) | `build/classes/java/main/` |
| `main.resources` | `src/agt`, `src/org` | cópia direta | `build/resources/main/` |

---

## src/agt — Programas AgentSpeak

### Visão Geral

Os programas AgentSpeak definem o comportamento cognitivo dos 15 agentes Jason. Cada arquivo de role inclui módulos compartilhados via `{ include("common/...") }`.

```mermaid
flowchart TD
    subgraph Roles["Agentes (Role-specific)"]
        SL["squad_leader.asl<br/>(3 instâncias)"]
        COL["collector.asl<br/>(6 instâncias)"]
        ASM["assembler.asl<br/>(3 instâncias)"]
        SEN["sentinel.asl<br/>(3 instâncias)"]
        DUM["dummy.asl<br/>(teste)"]
    end

    subgraph Common["Módulos Compartilhados (common/)"]
        PERC["perception.asl"]
        COLL["collection.asl"]
        CONN["connect_protocol.asl"]
        NAV["navigation.asl"]
        COMM["communication.asl"]
        DASH["dashboard_hooks.asl"]
    end

    SL --> PERC & DASH & COLL & NAV
    COL --> PERC & DASH & COMM & CONN & COLL & NAV
    ASM --> PERC & DASH & COMM & CONN & COLL & NAV
    SEN --> PERC & DASH & CONN & COLL & NAV
    DUM --> PERC & COLL & NAV
```

### Prioridade de `+step(N)` Handlers

A ordem de inclusão dos módulos determina qual handler intercepta o step primeiro:

```mermaid
flowchart LR
    P1["connect_protocol.asl<br/>🔴 Máxima"] --> P2["collection.asl<br/>🟡 Média"] --> P3["navigation.asl<br/>🟢 Mínima"]
```

### Agentes por Role

| Role | Arquivo | Instâncias | Responsabilidades |
|------|---------|-----------|-------------------|
| Squad Leader | `squad_leader.asl` | 3 | Leilão de tasks, delegação, coordenação de squad |
| Collector | `collector.asl` | 6 | Coleta de blocos, soloist tasks, meeting point |
| Assembler | `assembler.asl` | 3 | Solo/multi-block tasks, connect, submit |
| Sentinel | `sentinel.asl` | 3 | Patrulha, soloist tasks |
| Dummy | `dummy.asl` | 0 | Agente mínimo para testes |

### Módulos Compartilhados

| Módulo | Linhas | Responsabilidade |
|--------|--------|------------------|
| `perception.asl` | 159 | Processar percepções EIS (posição, things, tasks, normas, energia) |
| `connect_protocol.asl` | 257 | Submit em goal zone, connect sincronizado, energia crítica |
| `collection.asl` | 130 | Ciclo request→attach, retry, desvio de obstáculos |
| `navigation.asl` | 139 | Greedy movement, exploração por fronteira, stuck detection |
| `communication.asl` | 29 | Mensagens assembler↔collector para connect |
| `dashboard_hooks.asl` | 92 | Reportar estado/eventos via WebSocket |

---

## src/env — Artefatos CArtAgO (Java)

### Visão Geral

Artefatos CArtAgO são objetos compartilhados do ambiente que os agentes Jason podem operar. Divididos em dois pacotes:

```mermaid
classDiagram
    class Artifact {
        <<CArtAgO>>
    }

    class EISAccess {
        <<connection>>
        -EnvironmentInterface sharedEI$
        -String agName
        +init(conf, entityName)
        +action(String)
        ~updatePercepts()
    }

    class Translator {
        <<connection>>
        +perceptToLiteral()$
        +literalToAction()$
        +parametersToTerms()$
    }

    class SharedMap {
        <<env>>
        -ConcurrentHashMap cells
        -Set knownDispensers
        -Set knownGoalZones
        +update_cell()
        +get_nearest_dispenser()
        +get_nearest_goal_zone()
        +get_nearest_frontier()
        +compute_next_move()
        +manhattan_dist()
        -astar()
    }

    class TaskBoard {
        <<env>>
        -ConcurrentHashMap knownTasks
        -ConcurrentHashMap bids
        +register_task()
        +place_bid()
        +resolve_auction()
        +get_task_first_block()
    }

    class SquadCoordinator {
        <<env>>
        -ConcurrentHashMap agentSquad
        -ConcurrentHashMap soloistBusy
        +get_my_squad()
        +find_free_soloist()
        +signal_ready()
        +mark_busy() / mark_free()
    }

    class HiveDashboard {
        <<env>>
        -DashboardWsServer wsServer
        +log_event()
        +set_step()
        +update_score()
        +update_task_phase()
    }

    Artifact <|-- EISAccess
    Artifact <|-- SharedMap
    Artifact <|-- TaskBoard
    Artifact <|-- SquadCoordinator
    Artifact <|-- HiveDashboard
    EISAccess --> Translator
```

### Pacote `connection` — Bridge EIS/MASSim

| Classe | Linhas | Descrição |
|--------|--------|-----------|
| `EISAccess.java` | 197 | Artefato por agente; gerencia `EnvironmentInterface` singleton; converte percepções EIS → ObsProperties; executa ações |
| `Translator.java` | 100 | Utilitário de conversão bidirecional: IILang (Numeral, Identifier, Function, ParameterList) ↔ Jason AST (NumberTerm, Atom, Literal, ListTerm) |

**Padrão Singleton:** Uma única instância de `EnvironmentInterface` é compartilhada entre os 15 artefatos `EISAccess` (um por agente), gerenciando o pool de conexões TCP ao MASSim.

### Pacote `env` — Artefatos Compartilhados

| Classe | Linhas | Instância | Operações Principais |
|--------|--------|-----------|---------------------|
| `SharedMap.java` | 395 | `shared_map` (1) | `update_cell`, `get_nearest_dispenser`, `get_nearest_goal_zone`, `get_nearest_frontier`, `compute_next_move`, `mark_obstacle`, `decay_obstacles`, `manhattan_dist` |
| `TaskBoard.java` | 181 | `task_board` (1) | `register_task`, `signal_task_ready`, `place_bid`, `resolve_auction`, `complete_task`, `get_task_first_block`, `get_task_blocks` |
| `SquadCoordinator.java` | 51 | `squad_coordinator` (1) | `mark_busy`, `mark_free`, `update_agent_pos` — regime squad-era removido no #53 (registro leve; rename → `AgentRegistry` é follow-up) |
| `HiveDashboard.java` | 280 | `hive_dashboard` (1) | `log_event`, `set_step`, `update_score`, `update_task_phase`, `update_squad`, `register_map_dispenser` |

### Algoritmos Implementados em SharedMap

| Algoritmo | Método | Descrição |
|-----------|--------|-----------|
| A* (direção) | `astar()` | Pathfinding com até 8000 nós, fallback greedy se grid > 60 manhattan |
| A* (custo) | `astarCost()` | Versão custo-only com 3000 nós para ranking de destinos |
| Greedy | `greedy()` | Direção por maior componente do vetor (com wrap toroidal) |
| Frontier search | `get_nearest_frontier()` | Busca célula não-visitada adjacente a visitada mais próxima |
| Obstacle decay | `decay_obstacles()` | Remove obstáculos registrados há > 30 steps (a cada 5 steps) |
| Wrapped manhattan | `wrappedManhattan()` | Distância considerando grid toroidal |

---

## src/java — Ações Internas Jason (Java)

### Visão Geral

Ações internas são computações Java invocáveis diretamente no AgentSpeak via `pacote.Classe(args)`. Todas estendem `DefaultInternalAction`.

```mermaid
classDiagram
    class DefaultInternalAction {
        <<Jason>>
        +execute(ts, un, args) Object
    }

    class AdjacentDirection {
        -GRID_WIDTH = 40$
        -GRID_HEIGHT = 40$
        +execute() → unifica Dir
        -wrapDelta(d, size) int
    }

    class ConnectCalculator {
        +execute() → unifica RelX, RelY
    }

    class DirectionCalculator {
        +execute() → unifica Dir
    }

    class PathFinder {
        +execute() → unifica Dir
        -astar() String
        -firstDirection() String
    }

    class PatternMatcher {
        +execute() → unifica Result
    }

    DefaultInternalAction <|-- AdjacentDirection
    DefaultInternalAction <|-- ConnectCalculator
    DefaultInternalAction <|-- DirectionCalculator
    DefaultInternalAction <|-- PathFinder
    DefaultInternalAction <|-- PatternMatcher
```

### Detalhamento

| Classe | Assinatura AgentSpeak | Parâmetros | Resultado |
|--------|----------------------|------------|-----------|
| `AdjacentDirection` | `hive.AdjacentDirection(MX, MY, TX, TY, Dir)` | Posição agente + alvo | `n/s/e/w/none` (se adjacente com wrap 40×40) |
| `ConnectCalculator` | `hive.ConnectCalculator(MX, MY, PX, PY, RelX, RelY)` | Posição agente + parceiro | Coordenadas relativas para `connect()` |
| `DirectionCalculator` | `hive.DirectionCalculator(FX, FY, TX, TY, Dir)` | From + To | `n/s/e/w/skip` (greedy, maior componente) |
| `PathFinder` | `hive.PathFinder(FX, FY, TX, TY, Dir)` | From + To | `n/s/e/w/skip` (A*, max 2000 iter, fallback greedy) |
| `PatternMatcher` | `hive.PatternMatcher(Reqs, Result)` | Lista de requirements | `true/false` (verifica `my_attached` na BeliefBase) |

### Uso nos Módulos AgentSpeak

```mermaid
flowchart LR
    subgraph collection.asl
        COL_ADJ["hive.AdjacentDirection<br/>(adjacente ao dispenser?)"]
    end

    subgraph connect_protocol.asl
        CP_ADJ["hive.AdjacentDirection<br/>(collector adjacente?)"]
        CP_CC["hive.ConnectCalculator<br/>(coords para connect)"]
    end

    subgraph navigation.asl
        NAV["(usa greedy inline)"]
    end

    COL_ADJ --> AdjacentDirection
    CP_ADJ --> AdjacentDirection
    CP_CC --> ConnectCalculator
```

---

## src/org — Especificação Organizacional MOISE+

### Visão Geral

O arquivo `hive_org.xml` define a organização formal do sistema segundo o modelo MOISE+, com três dimensões:

```mermaid
flowchart TD
    subgraph MOISE["hive_org.xml"]
        direction TB
        subgraph Structural["Especificação Estrutural"]
            ROLES["Roles:<br/>squad_leader, collector,<br/>assembler, sentinel"]
            GROUPS["Grupos:<br/>hive_team → squad_group + sentinel_group"]
            LINKS["Links:<br/>authority, communication"]
        end

        subgraph Functional["Especificação Funcional"]
            SCH1["exploration_scheme<br/>(dispensers, goals, roles)"]
            SCH2["task_execution_scheme<br/>(collect → assemble → submit)"]
            SCH3["defense_scheme<br/>(guard + clear)"]
        end

        subgraph Normative["Especificação Normativa"]
            NORMS["Normas:<br/>n_scout, n_collect,<br/>n_assemble, n_submit, n_guard"]
        end
    end
```

### Estrutura Organizacional

```mermaid
classDiagram
    class soc {
        <<abstract role>>
    }
    class squad_leader {
        min: 3, max: 4
    }
    class collector {
        min: 6, max: 8
    }
    class assembler {
        min: 3, max: 4
    }
    class sentinel {
        min: 1, max: 3
    }

    soc <|-- squad_leader
    soc <|-- collector
    soc <|-- assembler
    soc <|-- sentinel

    class hive_team {
        <<group>>
    }
    class squad_group {
        <<subgroup>>
        min: 2, max: 4
    }
    class sentinel_group {
        <<subgroup>>
        min: 1, max: 2
    }

    hive_team *-- squad_group
    hive_team *-- sentinel_group
    squad_group --> squad_leader : 1
    squad_group --> collector : 1-2
    squad_group --> assembler : 1
    sentinel_group --> sentinel : 1-3

    squad_leader ..> collector : authority (intra-group)
    squad_leader ..> assembler : authority (intra-group)
    collector ..> assembler : communication (intra-group)
```

### Esquemas Funcionais

| Esquema | Goal Raiz | Sub-goals | Operador | Missões |
|---------|-----------|-----------|----------|---------|
| `exploration_scheme` | `map_explored` | `dispensers_found`, `goal_zones_found`, `role_zones_found` | parallel | `m_scout` |
| `task_execution_scheme` | `task_submitted` | `blocks_collected` → `blocks_assembled` → `pattern_submitted` | sequence | `m_collect`, `m_assemble`, `m_submit` |
| `defense_scheme` | `team_protected` | `goal_zones_guarded`, `threats_cleared` | parallel | `m_guard` |

### Normas (Obrigações)

| Norma | Role | Missão | Significado |
|-------|------|--------|-------------|
| `n_scout` | squad_leader | m_scout | Obrigação de explorar |
| `n_collect` | collector | m_collect | Obrigação de coletar blocos |
| `n_assemble` | assembler | m_assemble | Obrigação de montar blocos |
| `n_submit` | assembler | m_submit | Obrigação de submeter padrão |
| `n_guard` | sentinel | m_guard | Obrigação de proteger zonas |

---

## Diagramas de Arquitetura

### Visão Completa do Sistema

```mermaid
flowchart TB
    subgraph JaCaMo["Plataforma JaCaMo"]
        subgraph Jason["Jason (Agentes)"]
            direction LR
            SL["Squad Leaders ×3<br/>(squad_leader.asl)"]
            COL["Collectors ×6<br/>(collector.asl)"]
            ASM["Assemblers ×3<br/>(assembler.asl)"]
            SEN["Sentinels ×3<br/>(sentinel.asl)"]
        end

        subgraph CArtAgO["CArtAgO (Ambiente)"]
            direction LR
            EIS["EISAccess<br/>(×15, src/env/connection/)"]
            MAP["SharedMap<br/>(src/env/env/)"]
            TB["TaskBoard<br/>(src/env/env/)"]
            SC["SquadCoordinator<br/>(src/env/env/)"]
            HD["HiveDashboard<br/>(src/env/env/)"]
        end

        subgraph InternalActions["Ações Internas (src/java/hive/)"]
            IA["AdjacentDirection<br/>ConnectCalculator<br/>DirectionCalculator<br/>PathFinder<br/>PatternMatcher"]
        end

        subgraph MOISE["MOISE+ (src/org/)"]
            ORG["hive_org.xml<br/>(roles, groups, schemes, norms)"]
        end
    end

    subgraph External["Sistemas Externos"]
        MASSIM["MASSim Server<br/>(:12300)"]
        DASHBOARD["Dashboard UI<br/>(:8765 WebSocket)"]
    end

    Jason -->|"operações @OPERATION"| CArtAgO
    Jason -->|"cálculos"| InternalActions
    Jason -.->|"organização"| MOISE
    EIS <-->|"TCP/JSON"| MASSIM
    HD -->|"WebSocket"| DASHBOARD
```

### Fluxo de Dados por Camada

```mermaid
flowchart LR
    subgraph L1["Camada 1: Percepção"]
        MASSIM["MASSim"] -->|"JSON"| EIS["EISAccess"]
        EIS -->|"ObsProperties"| PERC["perception.asl"]
        PERC -->|"crenças"| MAP["SharedMap"]
    end

    subgraph L2["Camada 2: Decisão"]
        PERC -->|"triggers"| ROLE["Role .asl"]
        ROLE -->|"consultas"| TB["TaskBoard"]
        ROLE -->|"consultas"| SC["SquadCoordinator"]
        ROLE -->|"cálculos"| IA["Internal Actions"]
    end

    subgraph L3["Camada 3: Ação"]
        ROLE -->|"action()"| EIS2["EISAccess"]
        EIS2 -->|"JSON"| MASSIM2["MASSim"]
    end

    subgraph L4["Camada 4: Observação"]
        ROLE -->|"!dash_*"| HD["HiveDashboard"]
        HD -->|"WebSocket"| DASH["Dashboard UI"]
    end
```

### Ciclo de Vida de um Step

```mermaid
sequenceDiagram
    participant M as MASSim
    participant E as EISAccess
    participant P as perception.asl
    participant CP as connect_protocol.asl
    participant CL as collection.asl
    participant N as navigation.asl
    participant A as Artefatos

    M->>E: REQUEST-ACTION (step N, percepts)
    E->>P: ObsProperty: position(X,Y), thing(...), task(...)
    P->>A: update_cell(), mark_visited()
    P->>P: +step(N) trigger

    alt connect_protocol intercepta
        CP->>E: action("submit(task)") ou action("connect(...)")
    else collection intercepta
        CL->>E: action("request(dir)") ou action("attach(dir)")
    else navigation executa
        N->>A: get_nearest_frontier() ou greedy
        N->>E: action("move(dir)")
    end

    E->>M: ACTION JSON
```

### Relação entre Squads e Agentes

```mermaid
flowchart TD
    subgraph Squad1["Squad 1"]
        A1["connectionA1<br/>(leader)"]
        A4["connectionA4<br/>(collector)"]
        A5["connectionA5<br/>(collector)"]
        A10["connectionA10<br/>(assembler)"]
    end

    subgraph Squad2["Squad 2"]
        A2["connectionA2<br/>(leader)"]
        A6["connectionA6<br/>(collector)"]
        A7["connectionA7<br/>(collector)"]
        A11["connectionA11<br/>(assembler)"]
    end

    subgraph Squad3["Squad 3"]
        A3["connectionA3<br/>(leader)"]
        A8["connectionA8<br/>(collector)"]
        A9["connectionA9<br/>(collector)"]
        A12["connectionA12<br/>(assembler)"]
    end

    subgraph Soloists["Pool de Soloists"]
        A13["connectionA13<br/>(sentinel)"]
        A14["connectionA14<br/>(sentinel)"]
        A15["connectionA15<br/>(sentinel)"]
    end

    A1 -->|"authority"| A4 & A5 & A10
    A2 -->|"authority"| A6 & A7 & A11
    A3 -->|"authority"| A8 & A9 & A12
```

---

## Métricas do Código-Fonte

| Categoria | Arquivos | Linhas (aprox.) | Linguagem |
|-----------|----------|----------------|-----------|
| Agentes (roles) | 5 | ~670 | AgentSpeak |
| Módulos compartilhados | 6 | ~800 | AgentSpeak |
| Artefatos CArtAgO | 6 | ~1.450 | Java |
| Ações internas | 5 | ~190 | Java |
| Org. specification | 1 | ~120 | XML (MOISE+) |
| **Total** | **23** | **~3.230** | — |

---

## Resumo

| Aspecto | Detalhe |
|---------|---------|
| Diretório | `src/` (4 subdiretórios) |
| Paradigma | BDI (Belief-Desire-Intention) via Jason |
| Ambiente | CArtAgO (artefatos compartilhados) |
| Organização | MOISE+ (roles, groups, schemes, norms) |
| Agentes | 15 (4 roles distintos + 1 teste) |
| Artefatos | 5 tipos (EISAccess per-agent + 4 compartilhados) |
| Ações internas | 5 (geometria, pathfinding, pattern matching) |
| Algoritmos | A*, greedy navigation, frontier exploration, auction |
| Comunicação | Inter-agente (tell/achieve) + via artefatos (signals) |
| Integração | MASSim via EIS (TCP/JSON) + Dashboard via WebSocket |
