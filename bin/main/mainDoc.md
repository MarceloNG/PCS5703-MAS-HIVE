# Documentação Completa — `bin/main/`

## Sistema Multi-Agente Hive (JaCaMo + MASSim)

Este diretório contém os **programas AgentSpeak** e a **especificação organizacional MOISE+** que compõem o sistema multi-agente **Hive**, projetado para a competição **MASSim** (Multi-Agent Systems Simulation). O sistema utiliza a plataforma **JaCaMo** (Jason + CArtAgO + MOISE).

---

## Índice

1. [Estrutura do Diretório](#estrutura-do-diretório)
2. [Arquitetura Geral](#arquitetura-geral)
3. [Agentes (Papéis)](#agentes-papéis)
4. [Módulos Compartilhados](#módulos-compartilhados)
5. [Especificação Organizacional (MOISE+)](#especificação-organizacional-moise)
6. [Fluxos de Execução](#fluxos-de-execução)
7. [Diagramas de Arquitetura](#diagramas-de-arquitetura)

---

## Estrutura do Diretório

```
bin/main/
├── hive_org.xml              # Especificação organizacional MOISE+
├── squad_leader.asl          # Agente líder de esquadrão
├── collector.asl             # Agente coletor de blocos
├── assembler.asl             # Agente montador/conector
├── sentinel.asl              # Agente patrulheiro/solista
├── dummy.asl                 # Agente mínimo de teste
└── common/
    ├── perception.asl        # Processamento de percepções
    ├── collection.asl        # Ciclo de coleta de blocos
    ├── connect_protocol.asl  # Protocolo de connect + submit
    ├── navigation.asl        # Navegação e exploração
    ├── communication.asl     # Mensagens de sincronização
    └── dashboard_hooks.asl   # Integração com dashboard WebSocket
```

---

## Arquitetura Geral

```mermaid
flowchart TB
    subgraph MASSim["Servidor MASSim"]
        SIM[("Simulação<br/>Grid World")]
    end

    subgraph JaCaMo["Plataforma JaCaMo"]
        direction TB
        subgraph Agents["15 Agentes Jason"]
            SL["Squad Leaders<br/>(connectionA1-A3)"]
            COL["Collectors<br/>(connectionA4-A9)"]
            ASM["Assemblers<br/>(connectionA10-A12)"]
            SEN["Sentinels<br/>(connectionA13-A15)"]
        end

        subgraph Artifacts["Artefatos CArtAgO (Java)"]
            EIS["EISAccess<br/>(1 por agente)"]
            MAP["SharedMap"]
            TB["TaskBoard"]
            SC["SquadCoordinator"]
            HD["HiveDashboard"]
        end

        subgraph InternalActions["Ações Internas (Java)"]
            ADJ["AdjacentDirection"]
            CC["ConnectCalculator"]
            DC["DirectionCalculator"]
            PF["PathFinder"]
            PM["PatternMatcher"]
        end
    end

    subgraph External["Sistemas Externos"]
        DASH["Dashboard UI<br/>(WebSocket :8765)"]
    end

    SIM <-->|"EIS Protocol"| EIS
    EIS --> Agents
    Agents -->|"operações"| Artifacts
    Agents -->|"cálculos"| InternalActions
    HD -->|"WebSocket"| DASH
```

---

## Agentes (Papéis)

### Squad Leader (`squad_leader.asl`)

| Atributo | Valor |
|----------|-------|
| Instâncias | 3 (connectionA1, A2, A3) |
| `my_role_type` | `squad_leader` |
| Módulos incluídos | `perception`, `dashboard_hooks`, `collection`, `navigation` |

**Responsabilidades:**
- Coordenar o esquadrão (1 líder + 2 coletores + 1 assembler)
- Avaliar e licitar por tasks disponíveis (sistema de leilão)
- Delegar coleta de blocos para agentes soloists ou assemblers
- Registrar composição do squad no dashboard

**Fluxo principal:**
1. Inicialização → cria/conecta artefatos compartilhados
2. Percebe `new_task_available` → calcula score → `place_bid`
3. Se ganhar leilão → `find_free_soloist` → delega task
4. Fallback: envia `solo_task` ao assembler do squad

---

### Collector (`collector.asl`)

| Atributo | Valor |
|----------|-------|
| Instâncias | 6 (connectionA4–A9) |
| `my_role_type` | `collector` |
| Módulos incluídos | `perception`, `dashboard_hooks`, `communication`, `connect_protocol`, `collection`, `navigation` |

**Responsabilidades:**
- Coletar blocos em dispensers
- Executar tasks como soloist (coleta + submit)
- Navegar ao meeting point para connect multi-bloco
- Coleta oportunista quando ocioso

**Modos de operação:**
- **Soloist**: recebe task do líder, coleta bloco, navega a goal zone, submete
- **Multi-block**: coleta e vai ao meeting point para connect com assembler
- **Oportunista**: coleta ao descobrir dispenser (quando sem task ativa)

---

### Assembler (`assembler.asl`)

| Atributo | Valor |
|----------|-------|
| Instâncias | 3 (connectionA10–A12) |
| `my_role_type` | `assembler` |
| Módulos incluídos | `perception`, `dashboard_hooks`, `communication`, `connect_protocol`, `collection`, `navigation` |

**Responsabilidades:**
- Executar tasks solo (1 bloco) e soloist (via pool)
- Coordenar connect multi-bloco com collectors
- Submeter padrões completos na goal zone
- Reagir quando todos collectors estão prontos

**Modos de operação:**
- **Solo/Soloist**: coleta 1 bloco → goal zone → submit
- **Multi-block**: coleta bloco → meeting point → connect com collector → goal zone → submit

---

### Sentinel (`sentinel.asl`)

| Atributo | Valor |
|----------|-------|
| Instâncias | 3 (connectionA13–A15) |
| `my_role_type` | `sentinel` |
| Módulos incluídos | `perception`, `dashboard_hooks`, `connect_protocol`, `collection`, `navigation` |

**Responsabilidades:**
- Patrulhar e explorar o mapa
- Executar tasks como soloist (pool de soloists)
- Retornar à patrulha após completar task

---

### Dummy (`dummy.asl`)

| Atributo | Valor |
|----------|-------|
| Instâncias | 0 (teste) |
| `my_role_type` | — |
| Módulos incluídos | `perception`, `collection`, `navigation` |

Agente mínimo para testes. Reage a dispensers descobertos tentando coleta oportunista.

---

## Módulos Compartilhados

### Prioridade de `+step(N)`

A ordem de inclusão dos módulos define a prioridade dos handlers de step:

```mermaid
flowchart LR
    CP["connect_protocol.asl<br/>(prioridade máxima)"] --> COL["collection.asl<br/>(prioridade média)"] --> NAV["navigation.asl<br/>(prioridade mínima)"]
```

---

### `perception.asl` — Processamento de Percepções

Processa percepções recebidas do servidor MASSim via EIS:

| Percepção | Ação |
|-----------|------|
| `position(X,Y)` | Atualiza mapa, verifica stuck, cleanup periódico |
| `thing(X,Y,Type,Details)` | Atualiza célula no SharedMap |
| `goalZone(X,Y)` | Registra zona de objetivo |
| `roleZone(X,Y)` | Registra zona de papel |
| `task(Name,Deadline,Reward,Reqs)` | Registra task no TaskBoard |
| `norm(Id,Start,End,Reqs,Fine)` | Registra norma ativa |
| `score(S)` | Atualiza pontuação |
| `energy(E)` | Monitora energia (alerta < 10) |
| `deactivated(Bool)` | Gerencia estado ativo/desativado |
| `lastActionResult(R)` | Rastreia obstáculos e bloqueios |
| `attached(X,Y)` | Rastreia blocos anexados |

**Regras derivadas:**
- `my_pos(X,Y)` — posição atual
- `carrying_blocks(N)` — quantidade de blocos carregados
- `has_block` — possui bloco anexado

---

### `connect_protocol.asl` — Protocolo de Connect e Submit

Handler de maior prioridade. Gerencia:

1. **Desativação**: skip quando `am_deactivated`
2. **Energia crítica**: skip quando energia < 5
3. **Submit**: submete task quando em goal zone com `pending_submit`
4. **Resultado de submit**: re-submete em sucesso, rotaciona em falha (até 4x)
5. **Connect (assembler)**: detecta entidade adjacente, executa `connect()`
6. **Connect (collector)**: navega ao assembler ou executa `connect()`
7. **Navegação para submit**: greedy movement para goal zone com desvio de obstáculos

---

### `collection.asl` — Ciclo de Coleta de Blocos

Gerencia o ciclo completo de coleta:

```mermaid
stateDiagram-v2
    [*] --> Navegando: !collect_block(Type)
    Navegando --> Adjacente: chegou ao dispenser
    Adjacente --> Requesting: request(Dir)
    Requesting --> Attaching: request success
    Requesting --> Requesting: request fail (retry)
    Requesting --> Navegando: 5 falhas → outro dispenser
    Attaching --> Coletado: attach success
    Attaching --> Attaching: attach fail (retry)
    Coletado --> [*]: +collected_block(Type)
    Navegando --> Navegando: blocked → desvio aleatório
```

---

### `navigation.asl` — Navegação e Exploração

Prioridade mais baixa — executa quando nenhum protocolo específico intercepta:

| Contexto | Comportamento |
|----------|---------------|
| Chegou ao meeting point (collector) | `signal_ready` no SquadCoordinator |
| Chegou ao meeting point (assembler) | Aguarda connect |
| Destino genérico alcançado | Inicia exploração |
| Stuck detectado (solo) | Finaliza task |
| Stuck detectado (geral) | Detach forçado |
| Bloqueado | Direção aleatória |
| Com destino | Greedy movement (manhattan) |
| Sem destino | Exploração por fronteira (`get_nearest_frontier`) |

---

### `communication.asl` — Sincronização para Connect

Protocolo de mensagens entre assembler e collector:

```mermaid
sequenceDiagram
    participant ASM as Assembler
    participant COL as Collector

    ASM->>COL: connect_request(Me, X, Y, TargetStep)
    Note over COL: Armazena pending_connect
    COL->>ASM: connect_confirmed(Me, X, Y)
    Note over ASM: Armazena partner_confirmed
    ASM->>ASM: connect(Partner, RelX, RelY)
    COL->>COL: connect(AsmName, RelX, RelY)
```

---

### `dashboard_hooks.asl` — Integração com Dashboard

Reporta estado dos agentes via WebSocket para visualização em tempo real:

| Hook | Dados |
|------|-------|
| `!dash_log(EventType, Json)` | Evento genérico (bid, collect, submit, etc.) |
| `!dash_step_safe` | Step atual + estado do agente |
| `!dash_agent_state` | Posição, role, energia, ação, destino |
| `!dash_score(S)` | Pontuação do time |
| `!dash_task_phase(Task, Phase, Progress)` | Progresso da task |
| `!dash_squad(Squad, Members)` | Composição do squad |

---

## Especificação Organizacional (MOISE+)

### Estrutura — `hive_org.xml`

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

    squad_leader ..> collector : authority
    squad_leader ..> assembler : authority
    collector ..> assembler : communication
```

### Esquemas Funcionais

```mermaid
flowchart TB
    subgraph Exploration["Esquema: exploration_scheme"]
        ME["map_explored"] --> DF["dispensers_found<br/>ttf=200"]
        ME --> GZ["goal_zones_found<br/>ttf=200"]
        ME --> RZ["role_zones_found<br/>ttf=200"]
    end

    subgraph TaskExec["Esquema: task_execution_scheme"]
        TS["task_submitted"] --> BC["blocks_collected<br/>ttf=100"]
        BC --> BA["blocks_assembled<br/>ttf=50"]
        BA --> PS["pattern_submitted<br/>ttf=30"]
    end

    subgraph Defense["Esquema: defense_scheme"]
        TP["team_protected"] --> GG["goal_zones_guarded"]
        TP --> TC["threats_cleared"]
    end
```

### Normas

| Norma | Tipo | Papel | Missão |
|-------|------|-------|--------|
| `n_scout` | obrigação | squad_leader | m_scout (exploração) |
| `n_collect` | obrigação | collector | m_collect (coletar blocos) |
| `n_assemble` | obrigação | assembler | m_assemble (montar blocos) |
| `n_submit` | obrigação | assembler | m_submit (submeter padrão) |
| `n_guard` | obrigação | sentinel | m_guard (guardar zonas) |

---

## Fluxos de Execução

### Inicialização do Agente

```mermaid
flowchart TD
    START["!start"] --> NAME["Identificar (.my_name)"]
    NAME --> SM["!setup_shared_map"]
    SM --> TB["!setup_task_board"]
    TB --> SC["!setup_squad_coordinator"]
    SC --> HD["!setup_hive_dashboard"]
    HD --> EIS["makeArtifact(EISAccess)"]
    EIS --> FOCUS["focus(EIS)"]
    FOCUS --> READY["Pronto para percepções"]

    SM --> |"lookup falha"| CREATE_MAP["makeArtifact(SharedMap)"]
    CREATE_MAP --> |"falha"| SM
```

### Fluxo de Task Solo (Soloist)

```mermaid
sequenceDiagram
    participant MASSim as MASSim Server
    participant LEAD as Squad Leader
    participant TB as TaskBoard
    participant SC as SquadCoordinator
    participant SOL as Soloist (Sent/Col/Asm)
    participant MAP as SharedMap

    MASSim->>LEAD: task percept
    LEAD->>TB: register_task + place_bid
    LEAD->>TB: resolve_auction → Winner
    LEAD->>SC: find_free_soloist(dispX, dispY)
    SC-->>LEAD: SoloWinner
    LEAD->>SC: mark_busy(SoloWinner)
    LEAD->>SOL: tell(soloist_task(TaskName, BlockType))

    SOL->>MAP: get_nearest_dispenser
    MAP-->>SOL: (DX, DY)
    loop Navegação + Coleta
        SOL->>MASSim: move/request/attach
        MASSim-->>SOL: action result
    end
    SOL->>SOL: +collected_block(Type)
    SOL->>MAP: get_nearest_goal_zone
    loop Navegação para Goal Zone
        SOL->>MASSim: move
    end
    SOL->>MASSim: submit(TaskName)
    MASSim-->>SOL: success
    SOL->>SC: mark_free(Me)
    SOL->>SOL: !finalize_task
```

### Fluxo Multi-Block (Connect)

```mermaid
sequenceDiagram
    participant LEAD as Squad Leader
    participant COL as Collector
    participant ASM as Assembler
    participant SC as SquadCoordinator

    LEAD->>ASM: collect_and_connect_task
    LEAD->>COL: do_collect(BlockType)

    par Coleta Paralela
        COL->>COL: !collect_block → collected_block
        ASM->>ASM: !collect_block → collected_block
    end

    COL->>SC: signal_ready(Squad, Me)
    Note over ASM: Detecta all_ready
    ASM->>COL: connect_request(Me, X, Y, Step)
    COL->>ASM: connect_confirmed(Me, X, Y)

    par Connect Simultâneo
        ASM->>ASM: connect(Collector, RelX, RelY)
        COL->>COL: connect(Assembler, RelX, RelY)
    end

    ASM->>ASM: Nav → goal zone → submit
```

### Sistema de Leilão

```mermaid
flowchart TD
    NT["new_task_available<br/>(Task, Deadline, Reward, NBlocks)"] --> CHECK{"Squad != none<br/>& TimeLeft > 40?"}
    CHECK -->|Sim| SCORE["Score = (Reward/NBlocks)*100 - Distância"]
    CHECK -->|Não| IGNORE["Ignorar task"]
    SCORE --> BID["place_bid(Task, Squad, Score)"]
    BID --> WAIT[".wait(50)"]
    WAIT --> RESOLVE["resolve_auction(Task, Winner)"]
    RESOLVE --> WON{"Winner == MySquad?"}
    WON -->|Sim| DELEGATE["!delegate_collection"]
    WON -->|Não| LOG["Log: perdeu leilão"]
    DELEGATE --> SOLOIST{"Soloist livre?"}
    SOLOIST -->|Sim| ASSIGN["mark_busy + send soloist_task"]
    SOLOIST -->|Não| FALLBACK["send solo_task ao assembler"]
```

---

## Composição dos Esquadrões

| Squad | Líder | Coletores | Assembler |
|-------|-------|-----------|-----------|
| squad1 | connectionA1 | connectionA4, A5 | connectionA10 |
| squad2 | connectionA2 | connectionA6, A7 | connectionA11 |
| squad3 | connectionA3 | connectionA8, A9 | connectionA12 |

**Pool de Soloists:** connectionA13, A14, A15 (sentinels) + assemblers/collectors quando livres

---

## Artefatos CArtAgO (Dependências Java)

| Artefato | Classe | Responsabilidade |
|----------|--------|------------------|
| `shared_map` | `env.SharedMap` | Mapa compartilhado (dispensers, goal zones, obstáculos, fronteiras) |
| `task_board` | `env.TaskBoard` | Registro de tasks, leilão/bidding, atribuições |
| `squad_coordinator` | `env.SquadCoordinator` | Membros de squad, meeting points, pool soloists, readiness |
| `hive_dashboard` | `env.HiveDashboard` | Dashboard WebSocket (porta 8765) |
| `<agentName>` | `connection.EISAccess` | Bridge EIS por agente (connect ao MASSim) |

---

## Ações Internas (pacote `hive`)

| Ação | Uso | Descrição |
|------|-----|-----------|
| `hive.AdjacentDirection` | `collection.asl`, `connect_protocol.asl` | Verifica se alvo é adjacente (wrap toroidal) |
| `hive.ConnectCalculator` | `connect_protocol.asl` | Calcula coordenadas relativas para `connect()` |
| `hive.DirectionCalculator` | — | Direção greedy para alvo |
| `hive.PathFinder` | — | A* pathfinding |
| `hive.PatternMatcher` | — | Verifica blocos attached vs requirements da task |

---

## Mecanismos de Resiliência

| Mecanismo | Localização | Descrição |
|-----------|-------------|-----------|
| Retry de request | `collection.asl` | Até 5 tentativas, depois busca outro dispenser |
| Rotação no submit | `connect_protocol.asl` | Até 4 rotações CW antes de desistir |
| Detecção de stuck | `perception.asl` | 20 steps na mesma posição → detach/finalizar |
| Task timeout | agentes | 200 steps sem progresso → cleanup |
| Task expirada | agentes | Deadline atingido → cleanup |
| Energia crítica | `connect_protocol.asl` | Energia < 5 → skip para conservar |
| Desvio de obstáculos | `navigation.asl`, `collection.asl` | Direção aleatória ao encontrar bloqueio |
| Goal zone alternativa | `connect_protocol.asl` | Troca após 8 bloqueios |
| Fallback `-!` | todos os módulos | Planos de falha evitam crash |

---

## Relação `bin/main/` ↔ Código-Fonte

`bin/main/` é um **espelho 1:1** gerado pelo Gradle:

| Origem | Destino |
|--------|---------|
| `src/agt/*.asl` | `bin/main/*.asl` |
| `src/agt/common/*.asl` | `bin/main/common/*.asl` |
| `src/org/hive_org.xml` | `bin/main/hive_org.xml` |

O código Java (artefatos + ações internas) reside em `src/env/` e `src/java/` e **não** é copiado para `bin/main/`.
