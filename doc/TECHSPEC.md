# Especificação Técnica — Projeto HIVE / MAPC 2022

Documento técnico completo do sistema multi-agente **Hive**, cobrindo todas as tecnologias, protocolos, dependências, configuração e estrutura de código conforme implementados.

---

## 1. Visão Geral da Arquitetura de Sistema

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         MÁQUINA DO TIME                                   │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                        JaCaMo Runtime                              │  │
│  │                     (JaCaMoLauncher hive.jcm)                      │  │
│  │                                                                    │  │
│  │  ┌───────────────┐  ┌──────────────┐  ┌────────────────────────┐  │  │
│  │  │    Jason       │  │   MOISE+     │  │       CArtAgO          │  │  │
│  │  │  15 agentes    │  │ hive_org.xml │  │     5 artefatos        │  │  │
│  │  │               │  │              │  │                        │  │  │
│  │  │ squad_leader×3│  │ 4 roles      │  │ SharedMap              │  │  │
│  │  │ collector  ×6 │  │ 2 groups     │  │ TaskBoard              │  │  │
│  │  │ assembler  ×3 │  │ 3 schemes    │  │ SquadCoordinator       │  │  │
│  │  │ sentinel   ×3 │  │ 5 norms      │  │ HiveDashboard          │  │  │
│  │  │               │  │              │  │ EISAccess ×15          │  │  │
│  │  └──────┬────────┘  └──────────────┘  └───────────┬────────────┘  │  │
│  │         │                                          │               │  │
│  │         └──────────────────┬───────────────────────┘               │  │
│  │                            │                                       │  │
│  │                   ┌────────┴────────┐                              │  │
│  │                   │   EISAccess ×15 │ (1 por agente)               │  │
│  │                   │   Translator    │                              │  │
│  │                   └────────┬────────┘                              │  │
│  └────────────────────────────┼───────────────────────────────────────┘  │
│                               │ TCP/JSON ×15 conexões                    │
│                               │                                          │
│  ┌────────────────────────────┼────────────────────────────────────┐     │
│  │            HiveDashboard ──┼── WebSocket :8765                  │     │
│  └────────────────────────────┼────────────────────────────────────┘     │
└───────────────────────────────┼──────────────────────────────────────────┘
                                │
                       ┌────────┴────────┐          ┌─────────────────┐
                       │ Servidor MASSim │──HTTP───▶│  Web Monitor    │
                       │  TCP :12300     │  :8000   │  (browser)      │
                       │                 │          └─────────────────┘
                       │ Agents Assemble │
                       │ 2022            │          ┌─────────────────┐
                       └─────────────────┘          │ Hive Dashboard  │
                                                    │ React :5173     │◀─ WS :8765
                                                    └─────────────────┘
```

---

## 2. Tecnologias Externas (Servidores e Infraestrutura)

### 2.1 Servidor MASSim 2022

| Atributo | Valor |
|----------|-------|
| **Nome** | MASSim — Multi-Agent Systems Simulation Platform |
| **Versão** | 2022-1.1.1 |
| **Repositório** | `github.com/agentcontest/massim_2022` |
| **Linguagem** | Java |
| **Build** | Maven (`mvn -pl :server package`) |
| **JDK requerido** | >= 17 |
| **Execução** | `java -jar server-2022-1.1.1-jar-with-dependencies.jar -conf conf/TestConfig.json --monitor` |
| **Porta de agentes** | TCP 12300 (configurável) |
| **Porta do monitor** | HTTP 8000 |
| **Protocolo** | JSON sobre TCP socket (mensagens: `AUTH-REQUEST`, `SIM-START`, `REQUEST-ACTION`, `SIM-END`) |

**Função**: O servidor MASSim gerencia o ambiente (grade toroidal, blocos, dispensers, goal zones, role zones), executa ações dos agentes, aplica normas dinâmicas, gera clear events e calcula pontuações.

**Ciclo de comunicação por step**:

```
Servidor                         Agente
   │                                │
   │──── REQUEST-ACTION (percepts)──►│
   │                                │ ← EISAccess.updatePercepts()
   │                                │ ← Jason percept → plan selection
   │◄──── ACTION (resposta JSON) ───│
   │                                │
   │  (servidor executa todas as    │
   │   ações em ordem aleatória)    │
   │                                │
   │──── REQUEST-ACTION (step+1) ──►│
```

**Timeout**: 4000ms por step. Se o agente não responder, `skip` é executado.

### 2.2 Web Monitor MASSim

| Atributo | Valor |
|----------|-------|
| **Tipo** | Aplicação web embarcada no MASSim |
| **Acesso** | `http://localhost:8000` |
| **Modos** | Live (seguir simulação), Replay (carregar JSON), Static (arquivos locais) |
| **Ativação** | Flag `--monitor` ao iniciar o servidor |
| **Tecnologia** | Frontend web (pixi.js) servido pelo servidor Java |

### 2.3 Hive Dashboard

| Atributo | Valor |
|----------|-------|
| **Tipo** | Aplicação React standalone |
| **Acesso** | `http://localhost:5173` |
| **Conexão** | WebSocket `ws://localhost:8765` |
| **Função** | Monitoring em tempo real dos internals dos agentes (squads, tasks, auctions, positions) |
| **Stack** | React 19.2, TypeScript 6.0, Vite 8.0, Three.js 0.184, Zustand 5.0, Tailwind 4.3 |

### 2.4 Infraestrutura de Rede

| Aspecto | Detalhe |
|---------|---------|
| **Topologia** | Cliente-servidor (15 agentes → 1 servidor MASSim) + WebSocket (1 artefato → N dashboards) |
| **Transporte agentes** | TCP socket persistente (1 por agente, total 15) |
| **Transporte dashboard** | WebSocket (porta 8765) |
| **Formato de mensagens** | JSON em ambos os canais |
| **Tamanho máximo** | 65536 bytes (MASSim), sem limite (WebSocket) |
| **Ambiente** | Local (localhost) para desenvolvimento; mesma LAN para competição |

Não há banco de dados externo, message broker ou serviço cloud. Estado é in-memory nos artefatos CArtAgO, com replays MASSim salvos em disco.

---

## 3. Tecnologias Internas (Frameworks e Linguagens)

### 3.1 JaCaMo — Framework Integrador

| Atributo | Valor |
|----------|-------|
| **Versão** | 1.3.0 |
| **Artefato Gradle** | `org.jacamo:jacamo:1.3.0` |
| **JDK requerido** | >= 21 |
| **Build system** | Gradle 9.2 |
| **Site** | `jacamo-lang.github.io` |
| **Licença** | LGPL |
| **Entry point** | `jacamo.infra.JaCaMoLauncher` |

**O que é**: JaCaMo é a plataforma que integra três dimensões de um SMA:

1. **Agentes** (Jason) — o "quem" age
2. **Ambiente** (CArtAgO) — o "onde" agem
3. **Organização** (MOISE+) — o "como" se organizam

**Arquivo de configuração real** (`hive.jcm`):

```
mas hive {

    // Squad Leaders (1 por squad = 3)
    agent connectionA1  : squad_leader.asl
    agent connectionA2  : squad_leader.asl
    agent connectionA3  : squad_leader.asl

    // Collectors (2 por squad = 6)
    agent connectionA4  : collector.asl
    agent connectionA5  : collector.asl
    agent connectionA6  : collector.asl
    agent connectionA7  : collector.asl
    agent connectionA8  : collector.asl
    agent connectionA9  : collector.asl

    // Assemblers (1 por squad = 3)
    agent connectionA10 : assembler.asl
    agent connectionA11 : assembler.asl
    agent connectionA12 : assembler.asl

    // Sentinels (3)
    agent connectionA13 : sentinel.asl
    agent connectionA14 : sentinel.asl
    agent connectionA15 : sentinel.asl

    asl-path: src/agt, src/agt/common

}
```

**Observação**: Os artefatos não são declarados no `.jcm`. Cada agente cria/busca artefatos programaticamente no `+!start` via `makeArtifact`/`lookupArtifact`.

### 3.2 Jason — Plataforma de Agentes BDI

| Atributo | Valor |
|----------|-------|
| **Versão** | 3.3.1 |
| **Artefato** | `io.github.jason-lang:jason-interpreter:3.3.1` |
| **Linguagem** | AgentSpeak(L) — arquivos `.asl` |
| **Paradigma** | BDI (Belief-Desire-Intention) |
| **Base teórica** | Bratman (Practical Reasoning), Rao & Georgeff (BDI Logics) |

**Conceitos centrais**:

| Conceito | Significado no HIVE | Exemplo |
|----------|--------------------|---------| 
| **Beliefs** | Fatos percebidos ou inferidos | `position(5, 3)`, `energy(100)`, `dispenser(10, 8, b0)` |
| **Goals** | Estados desejados | `!start`, `!do_explore`, `!collect_block(b0)` |
| **Plans** | Regras situacionais | `+step(N) : collecting(T,X,Y) & adjacent(X,Y,Dir) <- action(request(Dir)).` |
| **Events** | Triggers de planos | `+position(X,Y)`, `+step(N)`, `+soloist_task(T,B)` |
| **Internal actions** | Java chamável do .asl | `hive.adjacent_direction(...)`, `hive.path_finder(...)` |
| **Annotations** | Metadata em crenças | `position(5,3)[source(percept)]` |

**Estrutura de inclusão dos .asl** (ordem = prioridade):

```prolog
{ include("common/perception.asl") }       // Processamento de percepts
{ include("common/dashboard_hooks.asl") }   // Report ao dashboard
{ include("common/connect_protocol.asl") }  // Submit/Connect (MÁXIMA)
{ include("common/collection.asl") }        // Request/Attach
{ include("common/navigation.asl") }        // Greedy move + explore
```

A prioridade é garantida pelo Jason: quando múltiplos planos para `+step(N)` satisfazem o contexto, o primeiro declarado (no include mais acima) é selecionado.

**Comunicação entre agentes**:

| Performativa | Uso no HIVE | Exemplo |
|-------------|-------------|---------|
| `tell` | Compartilhar fatos, delegar tasks | `.send(connectionA13, tell, soloist_task(task1, b0))` |
| `untell` | Remover crenças delegadas | `.send(Ag, untell, soloist_task(T,B))` |
| `achieve` | Solicitar execução de goal | `.send(Col, achieve, do_collect(b0))` |
| `broadcast` | Informar todos | `.broadcast(tell, found_dispenser(X,Y,Type))` |

### 3.3 MOISE+ — Modelo Organizacional

| Atributo | Valor |
|----------|-------|
| **Versão** | 1.1 |
| **Artefato** | `org.jacamo:moise:1.1` |
| **Formato** | XML (`src/org/hive_org.xml`) |
| **Base teórica** | Hübner, Sichman & Boissier (2002) |

**Especificação Estrutural**:

| Grupo | Roles | Cardinalidade |
|-------|-------|---------------|
| `hive_team` (root) | squad_leader (3-4), collector (6-8), assembler (3-4), sentinel (1-3) | Top-level |
| `squad_group` (×3) | squad_leader (1), collector (1-2), assembler (1) | min=2, max=4 |
| `sentinel_group` (×1) | sentinel (1-3) | min=1, max=2 |

**Links organizacionais**:

| De | Para | Tipo | Scope |
|----|------|------|-------|
| squad_leader | collector | authority | intra-group |
| squad_leader | assembler | authority | intra-group |
| collector | assembler | communication | intra-group |

**Especificação Funcional (Schemes)**:

| Scheme | Root Goal | Sub-goals | Operador | TTF |
|--------|-----------|-----------|----------|-----|
| `exploration_scheme` | map_explored | dispensers_found, goal_zones_found, role_zones_found | parallel | 200 |
| `task_execution_scheme` | task_submitted | blocks_collected → blocks_assembled → pattern_submitted | sequence | 100/50/30 |
| `defense_scheme` | team_protected | goal_zones_guarded, threats_cleared | parallel | — |

**Missões e Normas**:

| Norm ID | Role | Missão | Tipo |
|---------|------|--------|------|
| `n_scout` | squad_leader | m_scout | obligation |
| `n_collect` | collector | m_collect | obligation |
| `n_assemble` | assembler | m_assemble | obligation |
| `n_submit` | assembler | m_submit | obligation |
| `n_guard` | sentinel | m_guard | obligation |

### 3.4 CArtAgO — Ambiente de Artefatos

| Atributo | Valor |
|----------|-------|
| **Versão** | 3.1 |
| **Artefato** | `org.jacamo:cartago:3.1` |
| **Linguagem** | Java |
| **Paradigma** | A&A (Agents & Artifacts) |
| **Integração Jason** | `org.jacamo:jaca:3.1` |

**Artefatos implementados** (código real):

| Artefato | Package/Classe | Criação | Função |
|----------|---------------|---------|--------|
| `shared_map` | `env.SharedMap` | Singleton (primeiro agente cria, demais buscam) | Mapa compartilhado com A*, greedy, dispensers, goal zones, fronteiras |
| `task_board` | `env.TaskBoard` | Singleton | Registro de tasks + leilão distribuído + re-submit tracking |
| `squad_coordinator` | `env.SquadCoordinator` | Singleton | Squads hardcoded, meeting points, soloist pool, posições |
| `hive_dashboard` | `env.HiveDashboard` | Singleton | WebSocket server :8765, broadcast JSON para React dashboard |
| `<agentName>` | `connection.EISAccess` | 1 por agente (15 instâncias) | Bridge EIS para MASSim via EnvironmentInterface singleton |

**Operações e Signals do SharedMap** (implementação real):

| Operação `@OPERATION` | Parâmetros | Retorno | Função |
|----------------------|------------|---------|--------|
| `update_cell` | x, y, type, details | — | Registra célula + emite signal `new_dispenser`/`new_goal_zone`/`new_role_zone` |
| `mark_visited` | x, y | — | Marca célula como visitada |
| `get_nearest_dispenser` | agX, agY, type | OpFeedbackParam x, y | Manhattan wrapping 40×40 |
| `get_nearest_goal_zone` | agX, agY | OpFeedbackParam x, y | Manhattan wrapping 40×40 |
| `get_alternative_goal_zone` | agX, agY, curX, curY | OpFeedbackParam x, y | Goal zone diferente da atual |
| `get_nearest_frontier` | agX, agY | OpFeedbackParam x, y | Célula não visitada mais próxima |
| `compute_next_move` | fx, fy, tx, ty | OpFeedbackParam dir | A* (max 2000 iter) → fallback greedy |
| `manhattan_dist` | x1, y1, x2, y2 | OpFeedbackParam dist | Distância wrapping no torus |
| `mark_obstacle` / `decay_obstacles` | x, y, step | — | Obstacle tracking com expiração |
| `set_grid_dimensions` | width, height | — | Seta dimensões da grade |
| `get_map_stats` | — | 4 × OpFeedbackParam | Visitados, dispensers, goals, roles |

**Algoritmo A* (SharedMap)**:
- Grid toroidal 40×40 com wrapping Manhattan distance
- Máximo 2000 iterações (evita travamento)
- Fallback: greedy direction para distâncias > 60 ou quando A* esgota
- Obstáculos com decay temporal (expiram após N steps)

**Operações do TaskBoard**:

| Operação | Função |
|----------|--------|
| `register_task(name, deadline, reward, nBlocks)` | Registra task disponível |
| `place_bid(taskName, squadId, bidValue)` | Registra lance no leilão |
| `resolve_auction(taskName)` → winner | Retorna squad com maior bid (após `.wait(50)`) |
| `complete_task(taskName)` | Marca task como finalizada |
| `register_task_block(taskName, blockType)` | Associa bloco à task |
| `get_task_first_block(taskName)` → type | Tipo do 1º bloco da task |
| `get_task_blocks(taskName)` → (b1, b2) | Tipos dos 2 blocos (multi-block) |
| `is_task_assigned(taskName)` → boolean | Checa se task já tem dono |
| `signal_task_ready(taskName)` | Emite signal `new_task_available` |

**Operações do SquadCoordinator**:

| Operação | Função |
|----------|--------|
| `get_my_squad(agent)` → squadId | Retorna squad do agente (hardcoded) |
| `get_squad_collectors(squad)` → (c1, c2) | Retorna os 2 collectors do squad |
| `get_squad_assembler(squad)` → asm | Retorna o assembler do squad |
| `set_meeting_point(squad, x, y)` | Define ponto de encontro |
| `get_meeting_point(squad)` → (x, y) | Consulta ponto de encontro |
| `signal_ready(squad, agent)` | Marca agente como pronto |
| `all_ready(squad)` → boolean | Verifica se todos estão prontos |
| `clear_ready(squad)` | Limpa flags de ready |
| `find_free_soloist(dispX, dispY)` → agent | Agente livre mais próximo |
| `mark_busy(agent)` / `mark_free(agent)` | Controle do soloist pool |
| `update_agent_pos(agent, x, y)` | Atualiza posição |
| `set_squad_task(squad, taskName)` | Associa task ao squad |
| `get_squad_task(squad)` → taskName | Consulta task do squad |

### 3.5 EISAccess + Translator — Ponte Agente-Servidor

| Atributo | Valor |
|----------|-------|
| **Versão EISMASSim** | 4.5 |
| **JAR** | `lib/eismassim-4.5-jar-with-dependencies.jar` |
| **EIS Standard** | 0.5.0 |
| **Package** | `connection.EISAccess` (Artifact) + `connection.Translator` (static) |
| **Instâncias** | 15 (uma por agente), compartilhando 1 `EnvironmentInterface` singleton |

**Mecanismo de compartilhamento**: O primeiro `EISAccess` a executar `init()` cria o `EnvironmentInterface` estático e registra as 15 entidades. Instâncias subsequentes reutilizam o singleton.

**Fluxo de dados**:

```
Jason Agent (.asl)
    │
    │  +step(N), +position(X,Y), +thing(X,Y,T,D), +task(Name,D,R,Reqs)
    │  action(move(n)), action(submit(task1)), action(connect(ag,x,y))
    ▼
EISAccess Artifact (CArtAgO)
    │
    │  @INTERNAL_OPERATION updatePercepts() → Translator.perceptToLiteral()
    │  @OPERATION action(String) → Translator.literalToAction()
    ▼
EnvironmentInterface (EIS Singleton)
    │
    │  JSON: {"type":"action","content":{"id":1,"type":"move","p":["n"]}}
    │  JSON: {"type":"request-action","content":{"percept":{...}}}
    ▼
TCP Socket → Servidor MASSim (porta 12300)
```

**Configuração** (`eismassimconfig.json`):

```json
{
  "scenario": "assemble2022",
  "host": "localhost",
  "port": 12300,
  "scheduling": true,
  "timeout": 4000,
  "notifications": false,
  "exceptions": false,
  "multi-entities": [
    {
      "name-prefix": "connectionA",
      "username-prefix": "agentA",
      "password": "1",
      "count": 15,
      "start-index": 1,
      "print-iilang": false,
      "print-json": false
    }
  ]
}
```

**Mapeamento de credenciais**: `connectionA1` → `agentA1` (senha `"1"`) ... `connectionA15` → `agentA15` (senha `"1"`). O nome do agente no JaCaMo (`.jcm`) deve coincidir com o `name-prefix` + index do EIS.

### 3.6 Internal Actions Java (package `hive.*`)

| Classe | Assinatura Jason | Função |
|--------|-----------------|--------|
| `hive.AdjacentDirection` | `hive.adjacent_direction(+AgX, +AgY, +TX, +TY, -Dir)` | Calcula direção adjacente com wrap toroidal 40×40 |
| `hive.ConnectCalculator` | `hive.connect_calculator(+MyX, +MyY, +PartnerX, +PartnerY, +BlockDir, -RelX, -RelY)` | Coordenadas relativas para ação connect |
| `hive.DirectionCalculator` | `hive.direction_calculator(+FromX, +FromY, +ToX, +ToY, -Dir)` | Direção greedy (n/s/e/w/skip) |
| `hive.PathFinder` | `hive.path_finder(+FromX, +FromY, +ToX, +ToY, -Dir)` | A* simplificado (sem obstáculos), fallback greedy |
| `hive.PatternMatcher` | `hive.pattern_matcher(+Attached, +Required, -Result)` | Verifica se blocos attached satisfazem padrão da task |

### 3.7 NPL — Normative Programming Language

| Atributo | Valor |
|----------|-------|
| **Versão** | 0.6.1 |
| **Artefato** | `org.jacamo:npl:0.6.1` |
| **Função** | Interpretador de normas do MOISE+ integrado ao runtime |

### 3.8 HiveDashboard — WebSocket Server

| Atributo | Valor |
|----------|-------|
| **Package** | `env.HiveDashboard` |
| **Dependência** | `org.java-websocket:Java-WebSocket:1.5.7` + `org.json:json:20240303` |
| **Porta** | 8765 |
| **Protocolo** | JSON broadcast para todos os clientes WebSocket conectados |

**Mensagens emitidas**:

| Tipo | Trigger | Conteúdo |
|------|---------|----------|
| `snapshot` | Nova conexão WS | Estado completo (step, score, agents, squads, tasks, events) |
| `step_update` | A cada step | `{ step, score, agents: {...} }` |
| `event` | `log_event()` | `{ type, agent, data, timestamp }` |
| `task_update` | Task registrada/completada | `{ task, phase, progress }` |
| `squad_update` | Squad modificado | `{ squad, members, task }` |

---

## 4. Linguagens de Programação

| Linguagem | Uso | Arquivos | Volume |
|-----------|-----|----------|--------|
| **AgentSpeak(L)** | Lógica BDI dos 15 agentes | `src/agt/**/*.asl` (11 arquivos) | ~1.470 linhas |
| **Java** | Artefatos + Internal Actions | `src/env/**/*.java` + `src/java/**/*.java` (11 arquivos) | ~1.640 linhas |
| **XML** | Especificação organizacional MOISE+ | `src/org/hive_org.xml` | 120 linhas |
| **JSON** | Configuração MASSim + EIS + Dashboard | `*.json` | ~100 linhas |
| **JCM** | Declaração do MAS JaCaMo | `hive.jcm` | 28 linhas |
| **Gradle (Groovy DSL)** | Build e dependências | `build.gradle` | 51 linhas |
| **TypeScript** | Dashboard React | `dashboard/src/**/*.{ts,tsx}` | ~2.000 linhas |
| **Properties** | Logging JVM | `logging.properties` | 4 linhas |

---

## 5. Dependências Completas

### 5.1 Build do Projeto (`build.gradle`)

```groovy
plugins {
    id 'java'
}

defaultTasks 'run'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven { url "https://raw.githubusercontent.com/jacamo-lang/mvn-repo/master" }
    maven { url "https://repo.gradle.org/gradle/libs-releases" }
    mavenCentral()
}

dependencies {
    implementation 'org.jacamo:jacamo:1.3.0'
    implementation files('lib/eismassim-4.5-jar-with-dependencies.jar')
    implementation 'org.java-websocket:Java-WebSocket:1.5.7'
    implementation 'org.json:json:20240303'
}

sourceSets {
    main {
        java {
            srcDirs = ['src/env', 'src/java']
        }
        resources {
            srcDirs = ['src/agt', 'src/org']
        }
    }
}

task run(type: JavaExec, dependsOn: 'classes') {
    group = 'application'
    description 'runs the JaCaMo application'
    mainClass = 'jacamo.infra.JaCaMoLauncher'
    args 'hive.jcm'
    classpath sourceSets.main.runtimeClasspath
    standardInput = System.in
    jvmArgs '-Djava.util.logging.config.file=logging.properties'
}
```

### 5.2 Árvore de Dependências — Backend (JaCaMo)

```
org.jacamo:jacamo:1.3.0
├── io.github.jason-lang:jason-interpreter:3.3.1
├── org.jacamo:cartago:3.1
├── org.jacamo:jaca:3.1
├── org.jacamo:moise:1.1
├── org.jacamo:npl:0.6.1
├── org.jacamo:intmas:1.0.0
├── org.jacamo:sai:0.5.4
├── org.antlr:antlr4:4.12.0
├── org.jacamo:jacamo-rest:0.9-SNAPSHOT
└── org.gradle:gradle-tooling-api:8.10

eismassim-4.5-jar-with-dependencies.jar (local)
└── eis:0.5.0 (Environment Interface Standard)

org.java-websocket:Java-WebSocket:1.5.7
org.json:json:20240303
```

### 5.3 Árvore de Dependências — Frontend (Dashboard)

```
react: 19.2.6
react-dom: 19.2.6
zustand: 5.0.13              (state management)
three: 0.184.0               (3D rendering)
@react-three/fiber: 9.6.1    (React bridge para Three.js)
@react-three/drei: 10.7.7    (helpers Three.js)
framer-motion: 12.38.0       (animações)
lucide-react: 1.16.0         (ícones SVG)
recharts: 3.8.1              (gráficos)

Dev:
typescript: 6.0.2
vite: 8.0.12
@vitejs/plugin-react: 6.0.1
tailwindcss: 4.3.0
eslint: 10.3.0
```

---

## 6. Percepts e Ações — Protocolo EIS Completo

### 6.1 Percepts recebidos por step (JSON → IILang → Jason beliefs)

| Percept EIS | Crença Jason | Descrição |
|-------------|-------------|-----------|
| `step(N)` | `+step(N)` | Step atual (trigger principal de decisão) |
| `position(X, Y)` | `+position(X, Y)` | Posição absoluta (não relativa!) do agente — **custom do Hive** via Translator |
| `thing(X, Y, Type, Details)` | `+thing(X, Y, Type, Details)` | Entidade na visão (relativa): block, dispenser, entity, marker, obstacle |
| `task(Name, Deadline, Reward, Reqs)` | `+task(Name, Deadline, Reward, Reqs)` | Tarefa ativa com lista de blocos exigidos |
| `attached(X, Y)` | `+attached(X, Y)` | Bloco/entidade attached ao agente |
| `energy(N)` | `+energy(N)` | Energia atual (0 = deactivated) |
| `norm(Id, Start, End, Reqs, Fine)` | `+norm(Id, Start, End, Reqs, Fine)` | Norma dinâmica do servidor |
| `goalZone(X, Y)` | `+goalZone(X, Y)` | Célula de goal zone na visão (relativa) |
| `roleZone(X, Y)` | `+roleZone(X, Y)` | Célula de role zone na visão (relativa) |
| `role(Name)` | `+role(Name)` | Role atual do agente no servidor |
| `lastAction(Act)` | `+lastAction(Act)` | Última ação executada |
| `lastActionResult(Result)` | `+lastActionResult(Result)` | Resultado: success, failed, failed_random, failed_path |
| `lastActionParams(Params)` | `+lastActionParams(Params)` | Parâmetros da última ação |
| `score(N)` | `+score(N)` | Pontuação acumulada do time |
| `deactivated(true/false)` | `+deactivated(true/false)` | Se agente está desativado |
| `dispenser(X, Y, Type)` | `+dispenser(X, Y, Type)` | Dispenser na visão (custom via Translator) |

### 6.2 Ações enviadas (AgentSpeak → IILang → JSON)

| Ação no .asl | JSON enviado | Custo Energia |
|--------------|-------------|---------------|
| `action(move(n/s/e/w))` | `{"type":"move","p":["n"]}` | 1 |
| `action(attach(n/s/e/w))` | `{"type":"attach","p":["s"]}` | 0 |
| `action(detach(n/s/e/w))` | `{"type":"detach","p":["n"]}` | 0 |
| `action(request(n/s/e/w))` | `{"type":"request","p":["e"]}` | 0 |
| `action(submit(TaskName))` | `{"type":"submit","p":["task2"]}` | 0 |
| `action(connect(Agent, X, Y))` | `{"type":"connect","p":["agentA2","0","2"]}` | 0 |
| `action(rotate(cw/ccw))` | `{"type":"rotate","p":["cw"]}` | 0 |
| `action(clear(X, Y))` | `{"type":"clear","p":["3","-1"]}` | 30 |
| `action(adopt(RoleName))` | `{"type":"adopt","p":["explorer"]}` | 0 |
| `action(survey(Type))` | `{"type":"survey","p":["dispenser"]}` | 1 |
| `action(skip)` | `{"type":"skip","p":[]}` | 0 |

**Padrão Hive**: Todas as ações são emitidas via belief `action(X)` que é capturada pelo EISAccess em `updatePercepts()` e convertida.

---

## 7. Configuração do Servidor MASSim (TestConfig.json)

Configuração local usada para desenvolvimento e testes:

| Parâmetro | Valor | Descrição |
|-----------|-------|-----------|
| `tournamentMode` | round-robin | Modo de torneio |
| `teamsPerMatch` | 1 | Um time por partida (teste solo) |
| `launch` | 25s | Tempo até iniciar automaticamente |
| `port` | 12300 | Porta TCP |
| `agentTimeout` | 4000ms | Timeout por step |
| `steps` | 750 | Duração da simulação |
| `randomFail` | 1 | 1% chance de falha aleatória |
| `grid.width/height` | 40 | Grade toroidal 40×40 |
| `grid.instructions` | [0.5, 0.5, 0.5] | Probabilidades de dispensers |
| `blockTypes` | 3 | Tipos de bloco (b0, b1, b2) |
| `entities.standard` | 15 | Agentes por time |
| `tasks.regularity` | 4 | Frequência de novas tasks |
| `tasks.maxDuration` | 300 | Duração máxima de uma task |
| `events.chance` | 15 | Chance de clear events (%) |
| `events.radius` | [1, 3] | Raio do clear (1 a 3) |
| `norms.chance` | 20 | Chance de normas dinâmicas (%) |

**Roles disponíveis (servidor)**:

| Role | Visão | Speed | Clear Chance | Clear Max Energy |
|------|-------|-------|-------------|-----------------|
| `default` | 5 | 2 | 0.3 | 60 |
| `worker` | 3 | 3 | 0.1 | 30 |
| `explorer` | 7 | 3 | 0.0 | 0 |
| `constructor` | 4 | 1 | 0.0 | 0 |
| `sentinel` | 5 | 2 | 0.9 | 100 |

---

## 8. Protocolos de Comunicação

### 8.1 Agente ↔ Servidor MASSim

| Camada | Protocolo | Formato |
|--------|-----------|---------|
| Transporte | TCP socket persistente (1 por agente) | Bytes |
| Aplicação | MASSim Protocol v2022 | JSON |
| Abstração | EIS / IILang | Objetos Java |
| Integração | EISAccess artifact | CArtAgO operations |
| Agente | AgentSpeak beliefs + action/1 | Prolog-like |

**Sequência de mensagens por sessão**:

```
1. AUTH-REQUEST  (agente → servidor)  {"type":"auth-request","content":{"user":"agentA1","pw":"1"}}
2. AUTH-RESPONSE (servidor → agente)  {"type":"auth-response","content":{"result":"ok"}}
3. SIM-START     (servidor → agente)  {"type":"sim-start","content":{"percept":{...}}}
4. REQUEST-ACTION×N steps
5. SIM-END       (servidor → agente)  {"type":"sim-end","content":{"score":...,"ranking":...}}
```

### 8.2 Agente ↔ Agente (intra-time)

| Mecanismo | Tecnologia | Uso no HIVE |
|-----------|-----------|-------------|
| Mensagens tell | Jason `.send` | Delegação de tasks (`soloist_task`, `do_collect`, `collect_and_connect_task`) |
| Mensagens untell | Jason `.send` | Cleanup pós-task |
| Artefatos observáveis | CArtAgO signals | `new_task_available`, `new_dispenser`, `agent_ready`, `meeting_point_set` |
| Artefatos consultáveis | CArtAgO operations | `get_nearest_dispenser`, `find_free_soloist`, `resolve_auction` |

### 8.3 Artefato → Dashboard (WebSocket)

| Direção | Formato | Trigger |
|---------|---------|---------|
| HiveDashboard → React | JSON broadcast | `log_event()`, `set_step()`, `update_score()` |
| React → HiveDashboard | — | Apenas leitura (sem comandos) |

---

## 9. Estrutura de Arquivos (Implementação Real)

```
PCS5703_MAS/
│
├── build.gradle                              # Build config + dependências
├── settings.gradle                           # rootProject.name = 'hive'
├── hive.jcm                                  # Config JaCaMo (15 agentes)
├── eismassimconfig.json                      # Conexão EIS → MASSim
├── logging.properties                        # JVM logging (INFO level)
│
├── lib/
│   └── eismassim-4.5-jar-with-dependencies.jar   # Bridge EIS (local JAR)
│
├── src/
│   ├── agt/                                  # === AGENTES JASON (AgentSpeak) ===
│   │   ├── squad_leader.asl                  #   Líder (leilão, delegação, explore)
│   │   ├── collector.asl                     #   Coletor (blocos, meeting point)
│   │   ├── assembler.asl                     #   Montador (connect, submit)
│   │   ├── sentinel.asl                      #   Sentinela (solo tasks, patrulha)
│   │   ├── dummy.asl                         #   Agente mínimo para testes
│   │   └── common/                           #   Módulos compartilhados:
│   │       ├── connect_protocol.asl          #     Submit + Connect (prioridade máxima)
│   │       ├── collection.asl                #     Request + Attach cycle
│   │       ├── navigation.asl                #     Greedy move + frontier exploration
│   │       ├── perception.asl                #     Processamento de percepts
│   │       ├── communication.asl             #     Sync para connect multi-agente
│   │       └── dashboard_hooks.asl           #     Report de estado ao dashboard
│   │
│   ├── org/                                  # === ORGANIZAÇÃO MOISE+ ===
│   │   └── hive_org.xml                      #   SS + FS + NS (4 roles, 3 schemes)
│   │
│   ├── env/                                  # === ARTEFATOS CArtAgO (Java) ===
│   │   └── env/
│   │       ├── SharedMap.java                #   Mapa global (A*, greedy, frontier)
│   │       ├── TaskBoard.java                #   Tarefas + leilão distribuído
│   │       ├── SquadCoordinator.java         #   Squads + soloist pool
│   │       └── HiveDashboard.java            #   WebSocket server (:8765)
│   │   └── connection/
│   │       ├── EISAccess.java                #   Bridge EIS (1 instância/agente)
│   │       └── Translator.java               #   IILang ↔ Jason Literal
│   │
│   └── java/                                 # === INTERNAL ACTIONS (Java) ===
│       └── hive/
│           ├── AdjacentDirection.java         #   Adjacência toroidal 40×40
│           ├── ConnectCalculator.java         #   Coords relativas para connect
│           ├── DirectionCalculator.java       #   Direção greedy
│           ├── PathFinder.java               #   A* sem obstáculos (backup)
│           └── PatternMatcher.java           #   Match padrão de blocos
│
├── conf/                                     # === CONFIG MASSim SERVER ===
│   └── TestConfig.json                       #   Config local (40×40, 750 steps)
│
├── dashboard/                                # === FRONTEND REACT ===
│   ├── package.json                          #   Deps (React 19, Three.js, Zustand)
│   ├── vite.config.ts                        #   Vite 8 + React plugin
│   ├── tailwind.config.ts                    #   Tailwind 4
│   ├── tsconfig.json                         #   TypeScript 6
│   └── src/
│       ├── App.tsx                            #   Layout principal (2D/3D toggle)
│       ├── main.tsx                           #   Entry point React
│       ├── store.ts                          #   Zustand store (HiveState)
│       ├── ws.ts                             #   useHiveSocket() hook
│       └── components/
│           ├── Header.tsx                    #   Step, score, controles
│           ├── AgentGrid.tsx                 #   Cards dos 15 agentes
│           ├── SquadsPanel.tsx               #   Estado dos 3 squads
│           ├── TaskPipeline.tsx              #   Pipeline de tasks
│           ├── EventFeed.tsx                 #   Log de eventos
│           ├── AuctionHall.tsx              #   Visualização de leilões
│           ├── BattleStats.tsx              #   Estatísticas agregadas
│           ├── ScoreTimeline.tsx            #   Gráfico temporal
│           └── GridScene3D.tsx             #   Visualização Three.js
│
├── massim_2022/                              # === PLATAFORMA MASSim ===
│   ├── server/                               #   Servidor de simulação (Maven)
│   ├── protocol/                             #   Protocolo de comunicação
│   ├── eismassim/                            #   EIS bridge (gera o JAR)
│   ├── javaagents/                           #   Exemplos de agentes Java
│   └── monitor/                              #   Web monitor frontend
│
├── doc/                                      # === DOCUMENTAÇÃO ===
│   ├── ARCH.md                               #   Arquitetura C4 + UML + padrões MAS
│   ├── TECHSPEC.md                           #   Este documento
│   └── *.pdf                                 #   Enunciados e análises
│
└── build/                                    # === OUTPUT GRADLE ===
    ├── classes/java/main/                    #   .class compilados
    ├── resources/main/                       #   .asl + .xml copiados
    └── libs/hive.jar                         #   JAR gerado
```

---

## 10. Execução — Passo a Passo

### 10.1 Iniciar Servidor MASSim

```bash
cd massim_2022/server
java -jar target/server-2022-1.1.1-jar-with-dependencies.jar \
     -conf ../../conf/TestConfig.json --monitor
```

Aguardar mensagem: `Listening on port 12300...`
Monitor disponível em: `http://localhost:8000`

### 10.2 Iniciar Sistema HIVE

```bash
cd PCS5703_MAS
./gradlew run
```

O Gradle compila `src/env` + `src/java`, copia `src/agt` + `src/org` para resources, e executa `JaCaMoLauncher hive.jcm`.

Os 15 agentes conectam sequencialmente (cada um cria seu artefato `EISAccess`).

### 10.3 Iniciar Dashboard (opcional)

```bash
cd dashboard
npm install   # primeira vez
npm run dev
```

Acessar `http://localhost:5173`. Conecta automaticamente via WebSocket em `ws://localhost:8765`.

### 10.4 Logging

Configurado via `logging.properties`:

```properties
handlers = java.util.logging.ConsoleHandler
.level = INFO
java.util.logging.ConsoleHandler.level = ALL
java.util.logging.ConsoleHandler.formatter = java.util.logging.SimpleFormatter
```

Logs adicionais de cada agente via `.print(...)` no AgentSpeak (stdout formatado por Jason).

---

## 11. Requisitos de Ambiente

### 11.1 Software requerido

| Software | Versão | Propósito |
|----------|--------|-----------|
| **JDK** | >= 21 | Runtime JaCaMo + compilação |
| **JDK** (MASSim) | >= 17 | Build e execução do servidor |
| **Gradle** | >= 9.0 (wrapper incluído) | Build do HIVE |
| **Maven** | >= 3.8 | Build do MASSim (`mvn package`) |
| **Node.js** | >= 20 | Dashboard React |
| **npm** | >= 10 | Dependências do dashboard |
| **Git** | >= 2.0 | Controle de versão |
| **Browser** | Qualquer moderno | Monitor + Dashboard |

### 11.2 Hardware mínimo

| Recurso | Mínimo | Recomendado |
|---------|--------|-------------|
| **CPU** | 4 cores | 8 cores (server + JaCaMo + dashboard) |
| **RAM** | 4 GB | 8 GB |
| **Disco** | 2 GB | 5 GB (logs + replays) |
| **Rede** | Loopback | LAN para competição |

### 11.3 Portas utilizadas

| Porta | Protocolo | Serviço |
|-------|-----------|---------|
| 12300 | TCP | MASSim Server (agentes) |
| 8000 | HTTP | MASSim Web Monitor |
| 8765 | WebSocket | HiveDashboard → React |
| 5173 | HTTP | Vite dev server (Dashboard) |

---

## 12. Mapa de Versões — Resumo

| Componente | Versão | Tipo |
|-----------|--------|------|
| JDK (desenvolvimento) | 21+ | Runtime |
| JDK (MASSim server) | 17+ | Runtime |
| Gradle | 9.2 | Build |
| JaCaMo | 1.3.0 | Framework |
| Jason Interpreter | 3.3.1 | Framework |
| MOISE+ | 1.1 | Framework |
| CArtAgO | 3.1 | Framework |
| JaCa (Jason-CArtAgO) | 3.1 | Framework |
| NPL | 0.6.1 | Framework |
| ANTLR4 | 4.12.0 | Parser |
| EIS | 0.5.0 | Standard |
| EISMASSim | 4.5 | Library |
| Java-WebSocket | 1.5.7 | Library |
| org.json | 20240303 | Library |
| MASSim Server | 2022-1.1.1 | Server |
| Maven (MASSim) | 3.8+ | Build |
| React | 19.2.6 | Frontend |
| TypeScript | 6.0.2 | Frontend |
| Vite | 8.0.12 | Frontend |
| Tailwind CSS | 4.3.0 | Frontend |
| Three.js | 0.184.0 | Frontend |
| Zustand | 5.0.13 | Frontend |
| Framer Motion | 12.38.0 | Frontend |
| Recharts | 3.8.1 | Frontend |
| Node.js | 20+ | Runtime (Dashboard) |

---

## 13. Métricas do Código

| Métrica | Valor |
|---------|-------|
| Total de arquivos fonte | ~30 |
| Linhas AgentSpeak (.asl) | ~1.470 |
| Linhas Java (artefatos + actions) | ~1.640 |
| Linhas XML (MOISE+) | 120 |
| Linhas TypeScript (dashboard) | ~2.000 |
| Linhas Config (JSON + JCM + properties) | ~180 |
| **Total estimado** | **~5.410 linhas** |
| Agentes declarados | 15 |
| Artefatos CArtAgO (tipos) | 5 |
| Artefatos CArtAgO (instâncias) | 19 (4 singletons + 15 EISAccess) |
| Internal Actions | 5 |
| Módulos compartilhados (.asl) | 6 |
| Componentes React | 9 |
