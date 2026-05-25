# Documento Funcional — Projeto MAPC 2022 / PCS 5703

## Codinome do Time: **HIVE** (Hierarchical Intelligent Virtual Ensemble)

---

## 1. A Ideia Central

### O Quê

Um SMA com **arquitetura de enxame hierárquico** onde 15 agentes operam em **3 esquadrões fixos** de 4 membros + **3 sentinelas** no pool de soloists. Cada esquadrão é autônomo para completar tarefas (explorar, coletar, montar, submeter), coordenado globalmente por um **protocolo de leilão distribuído** via artefato `TaskBoard` e um **pool de soloists** que maximiza o throughput de tasks simples.

### Por Quê

A maioria dos times no MAPC comete um de dois erros:

1. **Centralização excessiva** — um agente "coordenador" vira gargalo e ponto único de falha.
2. **Descentralização total** — agentes independentes competem por recursos entre si, gerando desperdício.

Nossa abordagem é o **meio-termo ótimo**: esquadrões com autonomia local, coordenados por artefatos compartilhados e leilão distribuído. Adicionalmente, um **pool de soloists** permite que qualquer agente livre (inclusive sentinelas) execute tasks de 1 bloco de forma oportunista. Isso dá:

- **Resiliência**: mecanismos de retry, timeout e fallback em todos os módulos.
- **Paralelismo**: múltiplas tasks executadas simultaneamente (squads + soloists).
- **Eficiência**: sem coordenador central; decisão distribuída via artefatos observáveis.
- **Throughput máximo**: tasks simples não ficam ociosas esperando um squad inteiro.

### Para Quem

Time de alunos PCS 5703, competindo contra os demais times da turma no simulador MASSim 2022.

---

## 2. Análise do Cenário — Mecânicas e Decisões Estratégicas

### 2.1 Sistema de Roles (Papéis do Servidor)

O servidor define roles com atributos diferentes:

| Role (Server) | Visão | Speed [0] | Speed [1+] | Clear Chance | Clear Max Energy |
|---------------|-------|-----------|------------|-------------|-----------------|
| `default` | 5 | 2 | — | 0.3 | 60 |
| `worker` | 3 | 3 | 3 | 0.1 | 30 |
| `explorer` | 7 | 3 | — | 0.0 | 0 |
| `constructor` | 4 | 1 | — | 0.0 | 0 |
| `sentinel` | 5 | 2 | — | 0.9 | 100 |

**Decisão implementada**: Na versão atual, agentes operam com role `default`. Troca dinâmica de roles (via role zones) é suportada pela infraestrutura mas não ativamente utilizada nesta versão.

### 2.2 Sistema de Tarefas

- Tarefas aparecem com deadline (steps), reward e padrão de blocos.
- Podem ser submetidas **múltiplas vezes** enquanto a task estiver ativa.
- Só podem ser submetidas em **goal zones** (posição relativa (0,0) em relação ao agente).

**Decisão implementada**:
- Priorização por `Score = (Reward / NBlocks) × 100 - ManhattanDistance(leader, dispenser)`
- Tasks de 1 bloco → delegadas ao **soloist pool** (execução rápida por agente mais próximo)
- Tasks de 2 blocos → delegadas ao squad (connect multi-block)
- **Re-submit** automático: após submit success, o agente tenta submeter novamente antes de finalizar

### 2.3 Dispensers e Blocos

- Dispensers fixos, mapeados incrementalmente no `SharedMap`.
- Grid toroidal 40×40 → distâncias calculadas com wrapping Manhattan.
- Speed degrada com attachments (carry limit).

**Decisão implementada**: Minimizar tempo de carregamento. O collector vai direto ao dispenser mais próximo do tipo necessário, coleta (request + attach) e navega ao meeting point ou goal zone.

### 2.4 Connect — Mecânica Multi-Block

Para tarefas com 2+ blocos, a ação `connect` junta blocos entre dois agentes:

- Ambos executam `connect` no **mesmo step** referenciando o parceiro.
- Usa `hive.connect_calculator` para calcular coordenadas relativas.
- Protocolo de sincronização via módulo `communication.asl`.

**Decisão implementada**:
1. Assembler define meeting point (próximo à goal zone)
2. Collectors coletam em paralelo blocos diferentes
3. `signal_ready` no `SquadCoordinator` sincroniza os agentes
4. Connect simultâneo (collector e assembler executam no mesmo step)
5. Assembler navega à goal zone → submit

### 2.5 Normas Dinâmicas

O servidor cria normas que limitam:
- **Carry**: máximo de blocos que um agente pode carregar.
- **Adopt**: máximo de agentes com o mesmo role.

**Decisão implementada**: A infraestrutura de normas existe no `hive_org.xml` (MOISE+ normative spec), mas o monitoramento de normas dinâmicas do servidor não foi implementado como artefato dedicado nesta versão. Os agentes operam dentro dos limites naturais (1-2 blocos por vez).

### 2.6 Clear Events e Evasão

- Clear events desativam agentes e destroem blocos numa área.
- Markers aparecem ~5 steps antes do impacto.

**Decisão implementada**: Detecção via `am_deactivated` belief + `energy < 5` trigger. Quando desativado, o agente executa `skip` até reativar. Evasão proativa de markers não implementada nesta versão (prioridade foi dada ao pipeline de tasks).

---

## 3. Arquitetura Implementada

### 3.1 Organização MOISE+ (hive_org.xml)

```
HIVE Organization (hive_team)
│
├── squad_group ×3 (min=2, max=4)
│   ├── Role: squad_leader (1..1) ── authority → collector, assembler
│   ├── Role: collector (1..2) ── communication → assembler
│   └── Role: assembler (1..1)
│
├── sentinel_group (min=1, max=2)
│   └── Role: sentinel (1..3)
│
├── Scheme: exploration_scheme
│   ├── Goal: map_explored (parallel)
│   │   ├── dispensers_found (ttf=200)
│   │   ├── goal_zones_found (ttf=200)
│   │   └── role_zones_found (ttf=200)
│   └── Mission: m_scout (min=1, max=15)
│
├── Scheme: task_execution_scheme
│   ├── Goal: task_submitted (sequence)
│   │   ├── blocks_collected (ttf=100)
│   │   ├── blocks_assembled (ttf=50)
│   │   └── pattern_submitted (ttf=30)
│   ├── Mission: m_collect (min=1, max=2)
│   ├── Mission: m_assemble (min=1, max=1)
│   └── Mission: m_submit (min=1, max=1)
│
├── Scheme: defense_scheme
│   ├── Goal: team_protected (parallel)
│   │   ├── goal_zones_guarded
│   │   └── threats_cleared
│   └── Mission: m_guard (min=1, max=3)
│
└── Normas:
    ├── n_scout:    squad_leader MUST m_scout
    ├── n_collect:  collector MUST m_collect
    ├── n_assemble: assembler MUST m_assemble
    ├── n_submit:   assembler MUST m_submit
    └── n_guard:    sentinel MUST m_guard
```

### 3.2 Composição dos 15 Agentes

| Agente | Tipo | Squad | Função Principal |
|--------|------|-------|-----------------|
| connectionA1 | squad_leader | squad1 | Leilão + delegação + exploração |
| connectionA2 | squad_leader | squad2 | Idem |
| connectionA3 | squad_leader | squad3 | Idem |
| connectionA4 | collector | squad1 | Coleta de blocos |
| connectionA5 | collector | squad1 | Coleta de blocos |
| connectionA6 | collector | squad2 | Coleta de blocos |
| connectionA7 | collector | squad2 | Coleta de blocos |
| connectionA8 | collector | squad3 | Coleta de blocos |
| connectionA9 | collector | squad3 | Coleta de blocos |
| connectionA10 | assembler | squad1 | Connect + submit |
| connectionA11 | assembler | squad2 | Connect + submit |
| connectionA12 | assembler | squad3 | Connect + submit |
| connectionA13 | sentinel | — (soloist pool) | Tasks solo + patrulha |
| connectionA14 | sentinel | — (soloist pool) | Tasks solo + patrulha |
| connectionA15 | sentinel | — (soloist pool) | Tasks solo + patrulha |

**Pool de Soloists**: Todos os 15 agentes podem receber tasks solo via `find_free_soloist()`. Sentinelas são prioridade, mas assemblers e collectors livres também participam.

### 3.3 Ciclo de Vida de uma Task Solo (Implementado)

```
[1. PERCEBER]     Task aparece → TaskBoard.register_task()
       │
       ▼
[2. SINALIZAR]    TaskBoard emite signal new_task_available
       │
       ▼
[3. AVALIAR]      Cada leader calcula Score = (Reward/NBlocks)*100 - dist_dispenser
       │
       ▼
[4. LEILOAR]      Leaders chamam place_bid() + resolve_auction()
                  Maior score vence o leilão
       │
       ▼
[5. DELEGAR]      Leader vencedor: find_free_soloist(dispX, dispY) → agente livre
                  .send(tell, soloist_task(TaskName, BlockType))
       │
       ▼
[6. COLETAR]      Soloist: get_nearest_dispenser → navegar → request → attach
       │
       ▼
[7. SUBMETER]     Soloist: get_nearest_goal_zone → navegar → submit(TaskName)
       │
       ▼
[8. RE-SUBMIT]    Se sucesso: tenta submeter novamente (pontos extras!)
       │
       ▼
[9. FINALIZAR]    mark_free(Me) + cleanup de beliefs + volta a explorar
```

### 3.4 Ciclo de Vida de uma Task Multi-Block (Implementado)

```
[1. PERCEBER]     Task com 2+ blocos detectada
       │
       ▼
[2. LEILOAR]      Mesmo protocolo de leilão (seção 3.3)
       │
       ▼
[3. DELEGAR]      Leader: set_meeting_point(squad, X, Y)
                  .send(collector, tell, do_collect(BlockType))
                  .send(assembler, tell, collect_and_connect_task(...))
       │
       ▼
[4. COLETAR]      Collectors + Assembler em paralelo: dispenser → request → attach
                  signal_ready(squad, Me) ao chegar no meeting point
       │
       ▼
[5. SINCRONIZAR]  SquadCoordinator.all_ready(squad)?
                  Assembler envia connect_request via communication.asl
       │
       ▼
[6. CONNECT]      Ambos executam action(connect(...)) no mesmo step
                  hive.connect_calculator calcula RelX, RelY
       │
       ▼
[7. SUBMETER]     Assembler navega à goal zone → submit → re-submit
       │
       ▼
[8. CLEANUP]      clear_ready(squad) + finalize
```

### 3.5 Prioridade de Decisão (Pipeline por Step)

A ordem de inclusão dos módulos define a prioridade de ação:

| Prioridade | Módulo | Condição | Ação |
|-----------|--------|----------|------|
| **P0** | `connect_protocol.asl` | `am_deactivated` | `skip` |
| **P0** | `connect_protocol.asl` | `energy < 5` | `skip` |
| **P1** | `connect_protocol.asl` | `pending_submit` + `goalZone(0,0)` | `submit(Task)` |
| **P1** | `connect_protocol.asl` | `submitted_task` + `success` | re-submit ou finalize |
| **P1** | `connect_protocol.asl` | `ready_to_connect` | `connect(Agent, X, Y)` |
| **P2** | `collection.asl` | `waiting_attach_result` | `attach(Dir)` |
| **P2** | `collection.asl` | `waiting_request` + success | `attach(Dir)` |
| **P2** | `collection.asl` | `collecting(Type,X,Y)` + adjacente | `request(Dir)` |
| **P2** | `collection.asl` | `collecting(Type,X,Y)` | `move(Dir)` → dispenser |
| **P3** | `navigation.asl` | `has_destination` | greedy move → destino |
| **P3** | `navigation.asl` | sem destino | `get_nearest_frontier` → explore |

### 3.6 Artefatos CArtAgO (Estado Compartilhado)

| Artefato | Instâncias | Propósito | Dados Principais |
|----------|-----------|-----------|-----------------|
| `SharedMap` | 1 (singleton) | Mapa global incrementally built | Cells, dispensers, goal zones, role zones, fronteiras, obstáculos |
| `TaskBoard` | 1 (singleton) | Registro e leilão de tasks | Tasks conhecidas, bids, assignments, requirements |
| `SquadCoordinator` | 1 (singleton) | Coordenação de squads + soloist pool | Membros, meeting points, ready flags, busy/free |
| `HiveDashboard` | 1 (singleton) | WebSocket server → React dashboard | Step, score, agents, events, squads |
| `EISAccess` | 15 (1/agente) | Bridge com MASSim via EIS | Percepts, actions, EnvironmentInterface singleton |

---

## 4. Estratégias Implementadas

### 4.1 Exploração por Fronteira com Mapa Compartilhado

**Implementação real** (`SharedMap.java` + `navigation.asl`):

1. Cada agente chama `mark_visited(X, Y)` a cada step.
2. `perception.asl` processa percepts e chama `update_cell(X, Y, type, details)` para dispensers, goal zones, etc.
3. Quando sem task, o agente chama `get_nearest_frontier(myX, myY)` → célula não-visitada mais próxima (Manhattan wrapping).
4. Navegação via greedy direction (`DirectionCalculator`) ou `compute_next_move` (A* com fallback).
5. Se `get_nearest_frontier` retorna (-1,-1), todas as células foram visitadas → random movement.

**Vantagem**: Cobertura progressiva do mapa 40×40 sem sobreposição desnecessária.

### 4.2 Leilão Distribuído via TaskBoard

**Implementação real** (`TaskBoard.java` + `squad_leader.asl`):

1. Task detectada → `register_task(name, deadline, reward, nBlocks)`.
2. `signal_task_ready(name)` emite signal para todos os leaders.
3. Cada leader calcula: `Score = (Reward / NBlocks) × 100 - manhattan_dist(myPos, nearestDispenser)`.
4. `place_bid(taskName, mySquad, score)` registra lance.
5. `.wait(50)` para bids chegarem.
6. `resolve_auction(taskName)` → retorna squad com maior bid.
7. Leader vencedor delega via soloist ou squad conforme complexidade.

**Vantagem**: Completamente distribuído. O squad melhor posicionado (menor distância) vence naturalmente.

### 4.3 Pool de Soloists — A Grande Inovação

**Implementação real** (`SquadCoordinator.java`):

```
find_free_soloist(dispX, dispY):
  1. Busca em TODOS os 15 agentes (não apenas sentinelas)
  2. Filtra: soloistBusy[agent] == false
  3. Ordena por Manhattan distance ao dispenser
  4. Retorna o mais próximo livre
  5. Se nenhum livre → fallback para assembler do próprio squad
```

**Vantagem**: Tasks simples (1 bloco) são executadas imediatamente pelo agente mais próximo. Sentinelas que estariam ociosos patrulhando agora contribuem com pontos. Em simulações de 750 steps, isso pode dobrar o número de tasks completadas.

### 4.4 Connect Sincronizado (Multi-Block)

**Implementação real** (`connect_protocol.asl` + `communication.asl`):

1. `signal_ready(squad, me)` no `SquadCoordinator`.
2. Assembler detecta `all_ready(squad)` → envia `connect_request(Me, X, Y, Step)` via `.send`.
3. Collector responde com `connect_confirmed(Me, X, Y)`.
4. `hive.connect_calculator(MyX, MyY, PartnerX, PartnerY, BlockDir, RelX, RelY)` calcula coords.
5. Ambos executam `action(connect(Partner, RelX, RelY))` no step combinado.
6. Se falhar → retry (até 3 tentativas).

### 4.5 Re-submissão Agressiva

**Implementação real** (`connect_protocol.asl`):

```prolog
+step(N) : submitted_task(Task) & lastAction(submit) & lastActionResult(success)
    <- action(submit(Task)).   // Re-submit imediato!

+step(N) : submitted_task(Task) & lastAction(submit) & lastActionResult(failed_target)
    <- !finalize_task(Task).   // Task expirou, finalizar.
```

**Vantagem**: Cada re-submit bem-sucedido = reward adicional sem custo de coleta.

### 4.6 Resiliência Multi-Nível

| Mecanismo | Trigger | Ação | Módulo |
|-----------|---------|------|--------|
| Skip conservador | `energy < 5` ou `am_deactivated` | `action(skip)` | `connect_protocol.asl` |
| Retry de request | `lastActionResult(failed)` (até 5×) | Move aleatório + retry | `collection.asl` |
| Rotação no submit | Submit falha | Rotaciona CW (até 4×) | `connect_protocol.asl` |
| Goal zone alternativa | 8 bloqueios | `get_alternative_goal_zone()` | `connect_protocol.asl` |
| Detecção de stuck | 20 steps mesma posição | Finalize task ou detach | `perception.asl` |
| Task timeout | 200 steps sem progresso | Cleanup + mark_free | Cada agente |
| Obstacle decay | Obstáculo antigo | `decay_obstacles(step)` | `SharedMap.java` |
| WS reconnect | Conexão cai | Retry exponencial (1s→10s) | Dashboard `ws.ts` |

---

## 5. Stack Tecnológico Implementado

| Componente | Tecnologia | Versão | Justificativa |
|-----------|------------|--------|---------------|
| Agentes | **Jason** (AgentSpeak) | 3.3.1 | BDI nativo, ciclo percepção-raciocínio-ação |
| Organização | **MOISE+** | 1.1 | Papéis, grupos, missões e normas formais |
| Ambiente | **CArtAgO** | 3.1 | Artefatos observáveis para estado compartilhado |
| Integração | **JaCaMo** | 1.3.0 | Une Jason + MOISE+ + CArtAgO |
| EIS Bridge | **eismassim** | 4.5 | Proxy JSON ↔ IILang para MASSim |
| WebSocket | **Java-WebSocket** | 1.5.7 | Broadcast para dashboard |
| JSON | **org.json** | 20240303 | Serialização no HiveDashboard |
| Simulador | **MASSim** | 2022-1.1.1 | Servidor Agents Assemble |
| Dashboard | **React** | 19.2 | UI reativa com componentes |
| 3D | **Three.js** + React Three Fiber | 0.184 + 9.6 | Visualização 3D do grid |
| State | **Zustand** | 5.0 | Estado global do dashboard |
| Build | **Gradle** | 9.2 | Build + run do JaCaMo |
| Build (dashboard) | **Vite** | 8.0 | Dev server + bundling |
| Runtime | **JDK** | 21 | Execução do MAS |

---

## 6. Estrutura de Arquivos Implementada

```
PCS5703_MAS/
├── build.gradle                    # Build: java 21, JaCaMo 1.3.0, eismassim 4.5, WS, JSON
├── settings.gradle                 # rootProject.name = 'hive'
├── hive.jcm                        # 15 agentes declarados (asl-path: src/agt, src/agt/common)
├── eismassimconfig.json            # EIS: connectionA1-15, agentA1-15, localhost:12300
├── logging.properties              # JVM: INFO level, ConsoleHandler
│
├── lib/
│   └── eismassim-4.5-jar-with-dependencies.jar
│
├── src/
│   ├── agt/                        # ══ AGENTES JASON ══
│   │   ├── squad_leader.asl       #   Leilão, delegação, exploração, coordenação
│   │   ├── collector.asl          #   Coleta, meeting point, soloist
│   │   ├── assembler.asl          #   Connect multi-block, submit, soloist
│   │   ├── sentinel.asl           #   Soloist tasks, patrulha
│   │   ├── dummy.asl              #   Agente mínimo (testes)
│   │   └── common/                #   Módulos compartilhados (inclusão = prioridade):
│   │       ├── perception.asl     #     Processamento de percepts, update_cell, stuck detection
│   │       ├── dashboard_hooks.asl#     Report de estado ao HiveDashboard
│   │       ├── connect_protocol.asl#    Submit, connect, re-submit (PRIORIDADE MÁX)
│   │       ├── collection.asl     #     Request, attach, retry, multi-dispenser
│   │       ├── navigation.asl     #     Greedy move, frontier exploration, random escape
│   │       └── communication.asl  #     Sync messages para connect multi-agente
│   │
│   ├── org/                        # ══ ORGANIZAÇÃO MOISE+ ══
│   │   └── hive_org.xml           #   4 roles, hive_team > squad_group + sentinel_group
│   │
│   ├── env/                        # ══ ARTEFATOS CArtAgO ══
│   │   ├── env/
│   │   │   ├── SharedMap.java     #     Mapa: A*, greedy, frontier, dispensers, obstacles
│   │   │   ├── TaskBoard.java     #     Tasks + leilão + re-submit tracking
│   │   │   ├── SquadCoordinator.java#   Squads hardcoded + soloist pool + meeting points
│   │   │   └── HiveDashboard.java #     WebSocket :8765, broadcast JSON
│   │   └── connection/
│   │       ├── EISAccess.java     #     Bridge EIS (singleton EnvironmentInterface)
│   │       └── Translator.java    #     IILang ↔ Jason Literal (static utilities)
│   │
│   └── java/                       # ══ INTERNAL ACTIONS ══
│       └── hive/
│           ├── AdjacentDirection.java   # Adjacência toroidal 40×40
│           ├── ConnectCalculator.java   # Coords relativas para connect
│           ├── DirectionCalculator.java # Direção greedy (n/s/e/w/skip)
│           ├── PathFinder.java          # A* backup (sem obstáculos)
│           └── PatternMatcher.java      # Match padrão de blocos
│
├── conf/
│   └── TestConfig.json             # MASSim: 40×40, 750 steps, 15 agents, 3 block types
│
├── dashboard/                      # ══ FRONTEND REACT ══
│   ├── package.json                # React 19, Three.js, Zustand, Vite 8, Tailwind 4
│   └── src/
│       ├── App.tsx                 #   Layout 2D/3D toggle
│       ├── store.ts               #   Zustand (HiveState)
│       ├── ws.ts                   #   useHiveSocket() + reconnect exponencial
│       └── components/             #   Header, AgentGrid, SquadsPanel, TaskPipeline,
│                                   #   EventFeed, AuctionHall, BattleStats,
│                                   #   ScoreTimeline, GridScene3D
│
├── massim_2022/                    # ══ PLATAFORMA MASSIM (submodule) ══
│   ├── server/                     #   Servidor de simulação
│   ├── protocol/                   #   Protocolo JSON
│   ├── eismassim/                  #   EIS bridge (fonte do JAR)
│   └── monitor/                    #   Web monitor frontend
│
└── doc/                            # ══ DOCUMENTAÇÃO ══
    ├── ARCH.md                     #   Arquitetura C4, UML, padrões MAS
    ├── TECHSPEC.md                 #   Especificação técnica completa
    └── funcIdea.md                 #   Este documento
```

---

## 7. Fluxos de Dados Principais

### 7.1 Ciclo por Step (15 agentes em paralelo)

```
MASSim Server
    │ REQUEST-ACTION (JSON com percepts)
    ▼
EISAccess.updatePercepts() → Translator.perceptToLiteral()
    │ defineObsProperty: step, position, thing, task, energy, ...
    ▼
Jason Engine (plan selection)
    │ +step(N) trigger → cascata de prioridade:
    │   1. connect_protocol intercepta? → submit/connect/skip
    │   2. collection intercepta? → request/attach/move_to_dispenser
    │   3. navigation executa → greedy/frontier
    ▼
action(X) belief
    │ EISAccess.action(String) → Translator.literalToAction()
    ▼
EnvironmentInterface.performAction()
    │ JSON: {"type":"move","content":{"id":N,"type":"move","p":["n"]}}
    ▼
MASSim Server (executa ação)
```

### 7.2 Fluxo de Leilão

```
Task aparece nos percepts de todos
    │
    ▼
TaskBoard.register_task(name, deadline, reward, nBlocks)
TaskBoard.signal_task_ready(name) → signal new_task_available
    │
    ▼
Leaders (×3): +new_task_available(Name, Deadline, Reward, NBlocks)
    │ Cada um calcula Score
    │ place_bid(taskName, mySquad, score)
    │ .wait(50)
    │ resolve_auction(taskName) → winnerSquad
    ▼
Leader vencedor:
    │ NBlocks == 1? → find_free_soloist → .send(tell, soloist_task)
    │ NBlocks >= 2? → delegate to squad collectors + assembler
    ▼
Soloist ou Squad executa o pipeline
```

### 7.3 Fluxo de Dashboard

```
Artefatos Java (SharedMap, TaskBoard, SquadCoordinator)
    │ Eventos: log_event(), set_step(), update_score()
    ▼
HiveDashboard.java (CArtAgO Artifact)
    │ buildSnapshot() → JSON
    │ broadcast(msg)
    ▼
DashboardWsServer (Java-WebSocket :8765)
    │ WebSocket frames
    ▼
React Dashboard (useHiveSocket hook)
    │ Zustand store update
    ▼
UI Components (re-render)
```

---

## 8. Métricas e Performance Esperada

| Métrica | Alvo | Mecanismo |
|---------|------|-----------|
| Tasks solo/simulação (750 steps) | 15-25 | Pool de soloists otimizado |
| Tasks multi-block/simulação | 3-8 | Connect sincronizado |
| Tempo médio task solo | 30-50 steps | Greedy navigation + nearest dispenser |
| Cobertura do mapa em 200 steps | > 50% | Frontier exploration × 15 agentes |
| Taxa de re-submit | 1.5-2× por task | Re-submit automático |
| Uptime (sem deactivation) | > 90% | Energy conservation + skip |
| Retry success rate | > 70% | 5× retry com random escape |

---

## 9. Riscos e Mitigações (Retrospectiva)

| Risco | Status | Mitigação Aplicada |
|-------|--------|-------------------|
| Integração JaCaMo-MASSim | ✅ Resolvido | EISAccess com singleton + Translator estático |
| Connect sincronizado falha | ⚠️ Parcial | Protocolo communication.asl + retries (funciona ~70%) |
| Curva AgentSpeak | ✅ Resolvido | Módulos common/ com planos reutilizáveis |
| Stuck detection | ✅ Resolvido | 20-step timer + finalize + detach |
| Concorrência em artefatos | ✅ Resolvido | ConcurrentHashMap em todos os artefatos |
| Prazo curto | ✅ Resolvido | Priorização: solo pipeline primeiro, multi-block depois |
| A* performance | ✅ Resolvido | Limite 2000 iterações + fallback greedy |
| Dashboard latência | ✅ Resolvido | WebSocket broadcast + Zustand batching |

---

## 10. Diferencial Competitivo — O Que Torna HIVE Eficaz

1. **Pool de Soloists universal** — qualquer agente livre (incluindo sentinelas) executa tasks de 1 bloco, maximizando throughput. Times que não fazem isso desperdiçam 3-6 agentes ociosos.

2. **Leilão distribuído real** — via artefato `TaskBoard` com `resolve_auction()`. Sem coordenador central, sem gargalo. O squad mais próximo vence naturalmente.

3. **Re-submissão automática** — cada submit bem-sucedido é seguido imediatamente por outro attempt. Em 750 steps, isso pode gerar 30-50% mais pontos.

4. **Prioridade por inclusão** — sistema elegante: a ordem dos `{ include(...) }` define prioridade de ação. Submit > Collect > Navigate. Sem ifs complexos.

5. **Resiliência multi-camada** — retry de request (5×), rotação no submit (4×), stuck detection (20 steps), energy conservation, goal zone alternativa, obstacle decay. O sistema degrada gracefully.

6. **Observabilidade total** — Dashboard React com WebSocket mostra em tempo real: estado de cada agente, squads, leilões, tasks, mapa. Essencial para debug durante desenvolvimento.

7. **Fundamentação acadêmica** — BDI (Bratman/Rao-Georgeff), Contract Net (Smith 1980), MOISE+ (Hübner-Sichman-Boissier 2002), A&A (Ricci-Viroli-Omicini 2007). Cada decisão arquitetural é justificável.
