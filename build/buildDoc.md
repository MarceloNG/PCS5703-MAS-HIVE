# Documentação Completa — `build/`

## Artefatos de Build do Projeto Hive-MAPC

O diretório `build/` contém os artefatos gerados pelo **Gradle 9.2** durante a compilação e empacotamento do projeto **hive-mapc**. Este é o output padrão do plugin `java` do Gradle, configurado via `build.gradle` para compilar o sistema multi-agente JaCaMo.

---

## Índice

1. [Estrutura do Diretório](#estrutura-do-diretório)
2. [Pipeline de Build](#pipeline-de-build)
3. [Classes Compiladas](#classes-compiladas)
4. [Recursos (Resources)](#recursos-resources)
5. [Artefato JAR](#artefato-jar)
6. [Relatórios](#relatórios)
7. [Arquivos Temporários](#arquivos-temporários)
8. [Diagramas de Arquitetura](#diagramas-de-arquitetura)

---

## Estrutura do Diretório

```
build/
├── classes/
│   └── java/
│       └── main/
│           ├── connection/
│           │   ├── EISAccess.class
│           │   └── Translator.class
│           ├── env/
│           │   ├── HiveDashboard.class
│           │   ├── HiveDashboard$DashboardWsServer.class
│           │   ├── SharedMap.class
│           │   ├── SquadCoordinator.class
│           │   ├── TaskBoard.class
│           │   ├── TaskBoard$Bid.class
│           │   └── TaskBoard$TaskInfo.class
│           └── hive/
│               ├── AdjacentDirection.class
│               ├── ConnectCalculator.class
│               ├── DirectionCalculator.class
│               ├── PathFinder.class
│               ├── PathFinder$Node.class
│               └── PatternMatcher.class
├── generated/
│   └── sources/
│       ├── annotationProcessor/java/main/  (vazio)
│       └── headers/java/main/              (vazio)
├── libs/
│   └── hive-mapc.jar
├── reports/
│   └── problems/
│       └── problems-report.html
├── resources/
│   └── main/
│       ├── hive_org.xml
│       ├── assembler.asl
│       ├── collector.asl
│       ├── dummy.asl
│       ├── sentinel.asl
│       ├── squad_leader.asl
│       └── common/
│           ├── collection.asl
│           ├── communication.asl
│           ├── connect_protocol.asl
│           ├── dashboard_hooks.asl
│           ├── navigation.asl
│           └── perception.asl
└── tmp/
    ├── compileJava/
    │   ├── compileTransaction/
    │   │   ├── backup-dir/
    │   │   └── stash-dir/
    │   │       └── SharedMap.class.uniqueId0
    │   └── previous-compilation-data.bin
    └── jar/
        └── MANIFEST.MF
```

---

## Pipeline de Build

```mermaid
flowchart TD
    subgraph Sources["Código Fonte"]
        SRC_ENV["src/env/<br/>connection/*.java<br/>env/*.java"]
        SRC_JAVA["src/java/<br/>hive/*.java"]
        SRC_AGT["src/agt/<br/>*.asl + common/*.asl"]
        SRC_ORG["src/org/<br/>hive_org.xml"]
    end

    subgraph GradleTasks["Tasks Gradle"]
        COMPILE["compileJava<br/>(Java 21)"]
        PROCESS["processResources"]
        CLASSES["classes<br/>(compileJava + processResources)"]
        JAR["jar"]
        RUN["run<br/>(JavaExec)"]
    end

    subgraph BuildOutput["build/"]
        CLS["classes/java/main/<br/>(15 .class files)"]
        RES["resources/main/<br/>(12 resource files)"]
        LIB["libs/hive-mapc.jar"]
        RPT["reports/problems/"]
        TMP["tmp/compileJava/"]
    end

    SRC_ENV --> COMPILE
    SRC_JAVA --> COMPILE
    COMPILE --> CLS
    COMPILE --> TMP

    SRC_AGT --> PROCESS
    SRC_ORG --> PROCESS
    PROCESS --> RES

    COMPILE --> CLASSES
    PROCESS --> CLASSES
    CLASSES --> JAR --> LIB
    CLASSES --> RUN

    RUN -->|"JaCaMoLauncher<br/>hive.jcm"| EXEC["Execução do MAS"]
```

### Configuração do Build (`build.gradle`)

| Parâmetro | Valor |
|-----------|-------|
| Projeto | `hive-mapc` |
| Plugin | `java` |
| Java Toolchain | 21 |
| Task padrão | `run` |
| Main class | `jacamo.infra.JaCaMoLauncher` |
| Argumento | `hive.jcm` |

### Source Sets

| Source Set | Diretórios | Tipo |
|------------|-----------|------|
| `main.java` | `src/env`, `src/java` | Código Java |
| `main.resources` | `src/agt`, `src/org` | Recursos (AgentSpeak + MOISE+) |

### Dependências

| Dependência | Versão | Propósito |
|-------------|--------|-----------|
| `org.jacamo:jacamo` | 1.3.0 | Plataforma JaCaMo (Jason + CArtAgO + MOISE) |
| `eismassim` | 4.5 (local jar) | Bridge EIS para MASSim |
| `org.java-websocket:Java-WebSocket` | 1.5.7 | Dashboard WebSocket |
| `org.json:json` | 20240303 | Manipulação JSON |

### Repositórios Maven

```mermaid
flowchart LR
    BUILD["build.gradle"] --> R1["JaCaMo MVN Repo<br/>(GitHub raw)"]
    BUILD --> R2["Gradle Libs Releases"]
    BUILD --> R3["Maven Central"]
```

---

## Classes Compiladas

### Pacote `connection` — Bridge EIS/MASSim

```mermaid
classDiagram
    class EISAccess {
        <<Artifact>>
        -EnvironmentInterface sharedEI$
        -String agName
        -boolean receiving
        -List~Percept~ currentPercepts
        +init(conf, entityName)
        +action(String action)
        ~updatePercepts() INTERNAL_OPERATION
        +handlePercept(agent, percept)
    }

    class Translator {
        <<utility>>
        +perceptToLiteral(Percept) Literal$
        +literalToAction(Literal) Action$
        +termToParameter(Term) Parameter$
        +parameterToTerm(Parameter) Term$
        +parametersToTerms(List) Term[]$
    }

    EISAccess --> Translator : usa
    EISAccess ..|> AgentListener : implements
    EISAccess --|> Artifact : extends
```

**`EISAccess`** — Artefato CArtAgO que funciona como bridge entre agentes Jason e o servidor MASSim:
- Gerencia instância compartilhada (`sharedEI`) do `EnvironmentInterface`
- Loop interno (`updatePercepts`) que converte percepções EIS em observable properties
- Operação `action(String)` traduz literais Jason em ações EIS com retry (3x)
- Tratamento especial para percepções SIM-START (enviadas apenas no primeiro step)

**`Translator`** — Classe utilitária de conversão bidirecional:
- Percepções EIS → Literais Jason (Numeral, Identifier, Function, ParameterList)
- Literais Jason → Ações EIS (NumberTerm, StringTerm, ListTerm, Literal)

---

### Pacote `env` — Artefatos CArtAgO Compartilhados

```mermaid
classDiagram
    class SharedMap {
        <<Artifact>>
        -ConcurrentHashMap cells
        -Set knownDispensers
        -Set knownGoalZones
        -Set knownRoleZones
        -Set visitedCells
        -ConcurrentHashMap obstacles
        -int gridWidth, gridHeight
        +update_cell(x, y, type, details)
        +mark_visited(x, y)
        +get_nearest_dispenser(agX, agY, type) → (x, y)
        +get_nearest_goal_zone(agX, agY) → (x, y)
        +get_alternative_goal_zone(agX, agY, curX, curY) → (x, y)
        +get_nearest_frontier(agX, agY) → (x, y)
        +get_map_stats() → (visited, disp, goal, role)
        +mark_obstacle(x, y, step)
        +decay_obstacles(step)
        +compute_next_move(fx, fy, tx, ty) → dir
        +manhattan_dist(x1, y1, x2, y2) → dist
        +set_grid_dimensions(width, height)
        -astar(fx, fy, tx, ty) String
        -astarCost(fx, fy, tx, ty) int
        -greedy(fx, fy, tx, ty) String
    }

    class TaskBoard {
        <<Artifact>>
        -ConcurrentHashMap~TaskInfo~ knownTasks
        -ConcurrentHashMap~List~Bid~~ bids
        -ConcurrentHashMap assignedTasks
        -ConcurrentHashMap taskRequirements
        +register_task(name, deadline, reward, nBlocks)
        +signal_task_ready(name)
        +evaluate_task(name, deadline, reward, nBlocks) → score
        +place_bid(taskName, squadId, bidValue)
        +resolve_auction(taskName) → winnerSquad
        +complete_task(taskName)
        +remove_expired(currentStep)
        +is_task_assigned(taskName) → bool
        +register_task_block(taskName, blockType)
        +get_task_first_block(taskName) → blockType
        +get_task_blocks(taskName) → (block1, block2)
    }

    class TaskInfo {
        <<inner class>>
        +String name
        +int deadline, reward, nBlocks
        +List blockTypes
    }

    class Bid {
        <<inner class>>
        +String squadId
        +double value
    }

    class SquadCoordinator {
        <<Artifact>>
        -ConcurrentHashMap agentSquad
        -ConcurrentHashMap squadMembers
        -ConcurrentHashMap squadRole
        -ConcurrentHashMap meetingPoints
        -ConcurrentHashMap readyAgents
        -ConcurrentHashMap collectorAssignments
        -ConcurrentHashMap squadActiveTask
        -ConcurrentHashMap soloistBusy
        -ConcurrentHashMap agentPositions
        -ConcurrentHashMap taskSoloist
        +get_my_squad(agent) → squadId
        +get_squad_collectors(squad) → (col1, col2)
        +get_squad_assembler(squad) → assembler
        +get_squad_leader(squad) → leader
        +set_meeting_point(squad, x, y)
        +get_meeting_point(squad) → (x, y)
        +assign_block_to_collector(agent, blockType)
        +get_my_assignment(agent) → blockType
        +set_squad_task(squad, taskName)
        +get_squad_task(squad) → taskName
        +signal_ready(squad, agent)
        +all_ready(squad) → bool
        +clear_ready(squad)
        +mark_busy(agent)
        +mark_free(agent)
        +update_agent_pos(agent, x, y)
        +find_free_soloist(dispX, dispY) → winner
        +is_soloist_busy(agent) → bool
        +claim_task_soloist(task, agent) → claimed
        +release_task_soloist(task)
        +release_agent_from_task(task, agent)
    }

    class HiveDashboard {
        <<Artifact>>
        -DashboardWsServer wsServer
        -int currentStep, currentScore
        -ConcurrentHashMap squads
        -ConcurrentHashMap tasks
        -CopyOnWriteArrayList events
        -ConcurrentHashMap agentStates
        +log_event(type, agent, data)
        +set_step(step)
        +update_score(score)
        +update_task_phase(task, phase, progress)
        +update_squad(squadId, membersJson)
        +register_map_dispenser(x, y, type)
        +register_map_goal_zone(x, y)
        +remove_task(task)
        ~buildSnapshot() String
        ~broadcast(msg)
    }

    class DashboardWsServer {
        <<inner class>>
        +onOpen(conn, handshake)
        +onClose(conn, code, reason, remote)
        +onMessage(conn, message)
        +onError(conn, ex)
        +onStart()
        +broadcastMessage(msg)
    }

    TaskBoard *-- TaskInfo
    TaskBoard *-- Bid
    HiveDashboard *-- DashboardWsServer
    HiveDashboard --|> Artifact
    SharedMap --|> Artifact
    TaskBoard --|> Artifact
    SquadCoordinator --|> Artifact
```

---

### Pacote `hive` — Ações Internas Jason

```mermaid
classDiagram
    class DefaultInternalAction {
        <<Jason>>
        +execute(ts, un, args) Object
    }

    class AdjacentDirection {
        -int GRID_WIDTH = 40$
        -int GRID_HEIGHT = 40$
        +execute(ts, un, args) Object
        -wrapDelta(d, size) int
    }

    class ConnectCalculator {
        +execute(ts, un, args) Object
    }

    class DirectionCalculator {
        +execute(ts, un, args) Object
    }

    class PathFinder {
        +execute(ts, un, args) Object
        -astar(fromX, fromY, toX, toY, obstacles) String
        -firstDirection(goal, fromX, fromY, dirNames, dirs) String
    }

    class Node {
        <<inner class>>
        +int x, y, g, f
        +Node parent
        +compareTo(Node) int
    }

    class PatternMatcher {
        +execute(ts, un, args) Object
    }

    DefaultInternalAction <|-- AdjacentDirection
    DefaultInternalAction <|-- ConnectCalculator
    DefaultInternalAction <|-- DirectionCalculator
    DefaultInternalAction <|-- PathFinder
    DefaultInternalAction <|-- PatternMatcher
    PathFinder *-- Node
```

| Classe | Assinatura no AgentSpeak | Função |
|--------|--------------------------|--------|
| `AdjacentDirection` | `hive.AdjacentDirection(MX, MY, TX, TY, Dir)` | Retorna direção se alvo adjacente (com wrap toroidal 40×40) |
| `ConnectCalculator` | `hive.ConnectCalculator(MX, MY, PX, PY, RelX, RelY)` | Calcula coordenadas relativas para ação `connect()` |
| `DirectionCalculator` | `hive.DirectionCalculator(FX, FY, TX, TY, Dir)` | Direção greedy (maior componente do vetor) |
| `PathFinder` | `hive.PathFinder(FX, FY, TX, TY, Dir)` | A* com limite de 2000 iterações, fallback greedy |
| `PatternMatcher` | `hive.PatternMatcher(Reqs, Result)` | Verifica se blocos attached satisfazem requirements da task |

---

## Recursos (Resources)

O diretório `build/resources/main/` é uma cópia fiel dos sources declarados em `main.resources`:

```mermaid
flowchart LR
    subgraph Fonte["Source"]
        AGT["src/agt/*.asl"]
        COMMON["src/agt/common/*.asl"]
        ORG["src/org/hive_org.xml"]
    end

    subgraph Destino["build/resources/main/"]
        R_ASL["*.asl (5 agentes + dummy)"]
        R_COMMON["common/*.asl (6 módulos)"]
        R_ORG["hive_org.xml"]
    end

    AGT -->|"processResources"| R_ASL
    COMMON -->|"processResources"| R_COMMON
    ORG -->|"processResources"| R_ORG
```

| Arquivo | Tipo | Descrição |
|---------|------|-----------|
| `squad_leader.asl` | AgentSpeak | Agente líder de esquadrão |
| `collector.asl` | AgentSpeak | Agente coletor de blocos |
| `assembler.asl` | AgentSpeak | Agente montador/conector |
| `sentinel.asl` | AgentSpeak | Agente patrulheiro/solista |
| `dummy.asl` | AgentSpeak | Agente mínimo de teste |
| `common/perception.asl` | AgentSpeak | Processamento de percepções |
| `common/collection.asl` | AgentSpeak | Ciclo de coleta |
| `common/connect_protocol.asl` | AgentSpeak | Protocolo connect + submit |
| `common/navigation.asl` | AgentSpeak | Navegação e exploração |
| `common/communication.asl` | AgentSpeak | Mensagens de sincronização |
| `common/dashboard_hooks.asl` | AgentSpeak | Hooks para dashboard |
| `hive_org.xml` | MOISE+ XML | Especificação organizacional |

---

## Artefato JAR

| Atributo | Valor |
|----------|-------|
| Caminho | `build/libs/hive-mapc.jar` |
| Manifest | `Manifest-Version: 1.0` |
| Conteúdo | Classes compiladas + resources |

O JAR é gerado pela task `jar` do Gradle mas **não é utilizado diretamente para execução**. A task `run` usa o classpath do source set `main` (incluindo dependências) e invoca `jacamo.infra.JaCaMoLauncher` com o argumento `hive.jcm`.

---

## Relatórios

### `build/reports/problems/problems-report.html`

Relatório de problemas de configuração do Gradle. Atualmente contém:

| Severidade | Problema | Localização |
|------------|----------|-------------|
| WARNING | Sintaxe deprecated `propName value` (usar `propName = value`) | `build.gradle:14` |

Este warning será removido no Gradle 10. A linha afetada é a declaração de repositório Maven com sintaxe `url "..."` (deveria ser `url = uri("...")`).

---

## Arquivos Temporários

### `build/tmp/compileJava/`

| Arquivo | Propósito |
|---------|-----------|
| `previous-compilation-data.bin` | Dados de compilação incremental (quais classes mudaram) |
| `compileTransaction/stash-dir/SharedMap.class.uniqueId0` | Backup da última compilação incremental de `SharedMap` |
| `compileTransaction/backup-dir/` | Diretório de backup (vazio) |

### `build/tmp/jar/`

| Arquivo | Conteúdo |
|---------|----------|
| `MANIFEST.MF` | `Manifest-Version: 1.0` |

### `build/generated/sources/`

Diretórios vazios gerados automaticamente pelo Gradle para:
- Processadores de anotação (`annotationProcessor/java/main/`)
- Headers JNI (`headers/java/main/`)

Nenhum processador de anotação está configurado no projeto.

---

## Diagramas de Arquitetura

### Visão Geral do Sistema Compilado

```mermaid
flowchart TB
    subgraph Runtime["Execução (./gradlew run)"]
        JCM["JaCaMoLauncher"] -->|"carrega"| HiveJCM["hive.jcm"]
        HiveJCM -->|"instancia 15 agentes"| Agents["Agentes Jason"]
        HiveJCM -->|"carrega .asl de"| Resources["build/resources/main/"]
        Agents -->|"cria artefatos"| Artifacts["Classes env.*"]
        Agents -->|"usa ações internas"| IA["Classes hive.*"]
        Artifacts -->|"EIS bridge"| EIS["connection.EISAccess"]
        EIS -->|"JSON/TCP"| MASSim["MASSim Server"]
    end

    subgraph Classpath["Runtime Classpath"]
        CLS_BUILD["build/classes/java/main/"]
        RES_BUILD["build/resources/main/"]
        DEP_JACAMO["jacamo-1.3.0.jar"]
        DEP_EIS["eismassim-4.5.jar"]
        DEP_WS["Java-WebSocket-1.5.7.jar"]
        DEP_JSON["json-20240303.jar"]
    end

    Runtime --> Classpath
```

### Mapeamento Fonte → Build → Runtime

```mermaid
flowchart LR
    subgraph Fonte["Fonte"]
        direction TB
        F1["src/env/env/*.java"]
        F2["src/env/connection/*.java"]
        F3["src/java/hive/*.java"]
        F4["src/agt/*.asl"]
        F5["src/org/hive_org.xml"]
    end

    subgraph Build["Build Output"]
        direction TB
        B1["build/classes/java/main/env/*.class"]
        B2["build/classes/java/main/connection/*.class"]
        B3["build/classes/java/main/hive/*.class"]
        B4["build/resources/main/*.asl"]
        B5["build/resources/main/hive_org.xml"]
    end

    subgraph Runtime["Runtime"]
        direction TB
        R1["Artefatos CArtAgO<br/>(SharedMap, TaskBoard, etc.)"]
        R2["EIS Bridge<br/>(EISAccess, Translator)"]
        R3["Ações Internas<br/>(PathFinder, etc.)"]
        R4["Programas dos Agentes"]
        R5["Organização MOISE+"]
    end

    F1 -->|"compileJava"| B1
    F2 -->|"compileJava"| B2
    F3 -->|"compileJava"| B3
    F4 -->|"processResources"| B4
    F5 -->|"processResources"| B5

    B1 -->|"classloader"| R1
    B2 -->|"classloader"| R2
    B3 -->|"classloader"| R3
    B4 -->|"JaCaMo asl-path"| R4
    B5 -->|"MOISE parser"| R5
```

### Interações entre Componentes Compilados

```mermaid
sequenceDiagram
    participant JaCaMo as JaCaMoLauncher
    participant Agent as Agente Jason (.asl)
    participant EIS as EISAccess
    participant SM as SharedMap
    participant TB as TaskBoard
    participant SC as SquadCoordinator
    participant HD as HiveDashboard
    participant IA as Ações Internas (hive.*)
    participant MASSim as MASSim Server

    JaCaMo->>Agent: instancia (baseado em hive.jcm)
    Agent->>EIS: makeArtifact("connection.EISAccess")
    EIS->>MASSim: connect (JSON/TCP)
    Agent->>SM: lookupArtifact/makeArtifact("shared_map")
    Agent->>TB: lookupArtifact/makeArtifact("task_board")
    Agent->>SC: lookupArtifact/makeArtifact("squad_coordinator")
    Agent->>HD: lookupArtifact/makeArtifact("hive_dashboard")

    loop A cada step
        MASSim-->>EIS: percepts (PerceptUpdate)
        EIS-->>Agent: observable properties (step, position, things, etc.)
        Agent->>SM: update_cell, mark_visited, get_nearest_*
        Agent->>TB: register_task, place_bid, resolve_auction
        Agent->>SC: get_my_squad, find_free_soloist, signal_ready
        Agent->>IA: AdjacentDirection, PathFinder, etc.
        Agent->>HD: log_event, set_step, update_*
        Agent->>EIS: action("move(n)"), action("submit(task1)")
        EIS->>MASSim: perform action
    end
```

---

## Comandos de Build

| Comando | Descrição |
|---------|-----------|
| `./gradlew classes` | Compila Java + copia resources |
| `./gradlew jar` | Gera `build/libs/hive-mapc.jar` |
| `./gradlew run` | Compila e executa o MAS (task padrão) |
| `./gradlew clean` | Remove todo o diretório `build/` |
| `./gradlew printCp` | Imprime o classpath de runtime |

### Execução Manual Equivalente

```bash
java -Djava.util.logging.config.file=logging.properties \
     -cp "$(./gradlew -q printCp)" \
     jacamo.infra.JaCaMoLauncher hive.jcm
```

---

## Resumo de Métricas

| Métrica | Valor |
|---------|-------|
| Classes compiladas | 15 (incluindo inner classes) |
| Pacotes Java | 3 (`connection`, `env`, `hive`) |
| Recursos copiados | 12 arquivos |
| Tamanho total de fontes Java | ~1.300 linhas |
| Dependências externas | 4 |
| Java version | 21 |
| Gradle version | 9.2 |
