# Especificação Técnica — Projeto HIVE / MAPC 2022

---

## 1. Visão Geral da Arquitetura de Sistema

```
┌─────────────────────────────────────────────────────────────────────┐
│                        MÁQUINA DO TIME                              │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                     JaCaMo Runtime                            │  │
│  │                                                               │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐  │  │
│  │  │   Jason      │  │   MOISE+     │  │     CArtAgO         │  │  │
│  │  │  (Agentes)   │  │ (Organização)│  │   (Artefatos)       │  │  │
│  │  │              │  │              │  │                     │  │  │
│  │  │ squad_leader │  │ hive_org.xml │  │ SharedMap.java      │  │  │
│  │  │ collector    │  │              │  │ TaskBoard.java      │  │  │
│  │  │ assembler    │  │ roles        │  │ NormMonitor.java    │  │  │
│  │  │ sentinel     │  │ groups       │  │ SquadCoordinator.java│ │  │
│  │  │              │  │ schemes      │  │                     │  │  │
│  │  └──────┬───────┘  └──────────────┘  └──────────┬──────────┘  │  │
│  │         │                                       │             │  │
│  │         └───────────────┬───────────────────────┘             │  │
│  │                         │                                     │  │
│  │                  ┌──────┴──────┐                               │  │
│  │                  │  EISMASSim  │                               │  │
│  │                  │  (EIS 0.5)  │                               │  │
│  │                  └──────┬──────┘                               │  │
│  └─────────────────────────┼─────────────────────────────────────┘  │
│                            │ TCP/JSON                               │
└────────────────────────────┼────────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │  Servidor MASSIM │
                    │  (porta 12300)   │
                    │                  │
                    │  Agents Assemble │
                    │  2022            │
                    └────────┬─────────┘
                             │ HTTP :8000
                    ┌────────┴────────┐
                    │  Web Monitor    │
                    │  (browser)      │
                    └─────────────────┘
```

---

## 2. Tecnologias Externas (Servidores e Infraestrutura)

### 2.1 Servidor MASSIM 2022

| Atributo | Valor |
|----------|-------|
| **Nome** | MASSim — Multi-Agent Systems Simulation Platform |
| **Versão** | 2022-1.1.1 |
| **Repositório** | `github.com/agentcontest/massim_2022` |
| **Linguagem** | Java |
| **Build** | Maven (`mvn package`) |
| **JDK requerido** | >= 17 |
| **Execução** | `java -jar server-2022-1.1.1-jar-with-dependencies.jar --monitor` |
| **Porta de agentes** | TCP 12300 (configurável) |
| **Porta do monitor** | HTTP 8000 |
| **Protocolo** | JSON sobre TCP socket (mensagens: `AUTH-REQUEST`, `SIM-START`, `REQUEST-ACTION`, `SIM-END`) |

**Função**: O servidor MASSIM é o simulador da competição. Ele gerencia o ambiente (grade, blocos, dispensers, goal zones), executa ações dos agentes, aplica normas, gera clear events e calcula pontuações. Os agentes se conectam remotamente via TCP e trocam mensagens JSON.

**Configuração do servidor** (`conf/server.json`):

```json
{
  "server": {
    "tournamentMode": "round-robin",
    "teamsPerMatch": 2,
    "port": 12300,
    "agentTimeout": 4000,
    "launch": "key"
  },
  "teams": {
    "HIVE": { "prefix": "agent", "password": "1" },
    "Opponent": { "prefix": "agent", "password": "2" }
  },
  "match": [
    {
      "id": "sim1",
      "steps": 800,
      "randomFail": 1,
      "entities": [{ "standard": 15 }]
    }
  ]
}
```

**Ciclo de comunicação por step**:

```
Servidor                         Agente
   │                                │
   │──── REQUEST-ACTION (percepts)──►│
   │                                │ ← agente processa percepts
   │                                │ ← agente decide ação
   │◄──── ACTION (resposta JSON) ───│
   │                                │
   │  (servidor executa todas as    │
   │   ações em ordem aleatória)    │
   │                                │
   │──── REQUEST-ACTION (step+1) ──►│
```

**Timeout**: 4000ms por step. Se o agente não responder, a ação padrão (`skip`) é executada.

### 2.2 Web Monitor

| Atributo | Valor |
|----------|-------|
| **Tipo** | Aplicação web embarcada no MASSIM |
| **Acesso** | `http://localhost:8000` |
| **Função** | Visualização em tempo real da simulação e replays |
| **Ativação** | Flag `--monitor` ao iniciar o servidor |
| **Tecnologia** | Frontend web servido pelo servidor Java |

**Uso no desenvolvimento**: Indispensável para debug visual. Permite ver posições dos agentes, blocos, dispensers, goal zones e o resultado de cada ação.

### 2.3 Infraestrutura de Rede

| Aspecto | Detalhe |
|---------|---------|
| **Topologia** | Cliente-servidor (N agentes → 1 servidor MASSIM) |
| **Transporte** | TCP socket persistente por agente |
| **Formato de mensagens** | JSON |
| **Tamanho máximo de pacote** | 65536 bytes (configurável) |
| **Ambiente de execução** | Local (localhost) para desenvolvimento; mesma rede para competição |

Não há banco de dados externo, message broker ou serviço cloud. Toda a persistência é in-memory no servidor MASSIM, com replays salvos em disco como JSON.

---

## 3. Tecnologias Internas (Frameworks e Linguagens)

### 3.1 JaCaMo — Framework Integrador

| Atributo | Valor |
|----------|-------|
| **Versão** | 1.3 (latest stable) |
| **Artefato Maven/Gradle** | `org.jacamo:jacamo:1.3` |
| **JDK requerido** | >= 21 |
| **Build system** | Gradle |
| **Site** | `jacamo-lang.github.io` |
| **Licença** | LGPL |

**O que é**: JaCaMo é a plataforma que integra três dimensões de um SMA:

1. **Agentes** (Jason) — o "quem" age
2. **Ambiente** (CArtAgO) — o "onde" agem
3. **Organização** (MOISE+) — o "como" se organizam

**Arquivo de configuração** (`hive.jcm`):

```
mas hive {

    agent squad_leader : squad_leader.asl {
        focus: shared_map
        focus: task_board
        focus: norm_monitor
        roles: squad_leader in squad_group
    }

    agent collector : collector.asl {
        focus: shared_map
        focus: task_board
        roles: collector in squad_group
    }

    agent assembler : assembler.asl {
        focus: shared_map
        focus: task_board
        roles: assembler in squad_group
    }

    agent sentinel : sentinel.asl {
        focus: shared_map
        roles: sentinel in sentinel_group
    }

    workspace hive_workspace {
        artifact shared_map: env.SharedMap()
        artifact task_board: env.TaskBoard()
        artifact norm_monitor: env.NormMonitor()
        artifact squad_coordinator: env.SquadCoordinator()
    }

    organisation hive_org : org/hive_org.xml {
        group squad_group : squad_group
        group sentinel_group : sentinel_group
        scheme exploration : exploration_scheme
        scheme task_execution : task_execution_scheme
        scheme defense : defense_scheme
    }

    platform: jacamo.platform.eis.EISPlatform("eismassimconfig.json")
}
```

### 3.2 Jason — Plataforma de Agentes BDI

| Atributo | Valor |
|----------|-------|
| **Versão** | 3.3.1 |
| **Artefato** | `io.github.jason-lang:jason-interpreter:3.3.1` |
| **Linguagem** | AgentSpeak(L) — arquivos `.asl` |
| **Paradigma** | BDI (Belief-Desire-Intention) |
| **Base teórica** | Bratman (Practical Reasoning), Rao & Georgeff (BDI Logics) |
| **Repositório** | `github.com/jason-lang/jason` |

**O que é**: Jason é um interpretador para a linguagem AgentSpeak(L), que implementa o modelo BDI de agentes racionais. É a camada onde toda a lógica de decisão dos agentes é programada.

**Conceitos centrais**:

| Conceito AgentSpeak | Significado no projeto | Exemplo |
|---------------------|----------------------|---------|
| **Beliefs** (crenças) | Fatos que o agente acredita serem verdade | `dispenser(3, 5, b0).` `goal_zone(10, 12).` |
| **Goals** (objetivos) | Estados que o agente quer alcançar | `!explore_map.` `!collect_block(b0).` |
| **Plans** (planos) | Receitas de como alcançar objetivos | `+!explore_map : frontier(X,Y) <- move_to(X,Y).` |
| **Events** | Triggers que disparam planos | `+task(Name, Deadline, Reward, Reqs)` (nova task percebida) |
| **Internal actions** | Operações em Java chamáveis de .asl | `.send(leader, tell, found_dispenser(X,Y,Type))` |
| **Annotations** | Metadata em crenças | `dispenser(3,5,b0)[source(percept)]` |

**Estrutura de um arquivo `.asl`** (exemplo simplificado do collector):

```prolog
// Crenças iniciais
my_role(collector).
squad(none).

// Plano: quando recebo designação de task, vou ao dispenser
+!collect_block(Type)
    : dispenser(DX, DY, Type) & my_pos(MX, MY)
    <- .print("Indo coletar bloco ", Type, " em ", DX, ",", DY);
       !navigate_to(DX, DY);
       request(direction_to(DX, DY));
       attach(direction_to(DX, DY));
       !deliver_to_assembler.

// Plano: navegar até um ponto (simplificado)
+!navigate_to(TX, TY)
    : my_pos(TX, TY)
    <- .print("Cheguei ao destino").

+!navigate_to(TX, TY)
    : my_pos(MX, MY) & next_step(MX, MY, TX, TY, Dir)
    <- move(Dir);
       !navigate_to(TX, TY).
```

**Comunicação entre agentes Jason**:

| Performativa | Uso no HIVE | Exemplo |
|-------------|-------------|---------|
| `tell` | Compartilhar crenças (mapa, dispensers) | `.send(leader, tell, found_dispenser(5,3,b0))` |
| `achieve` | Pedir que outro agente atinja um goal | `.send(collector1, achieve, collect_block(b0))` |
| `askOne` | Perguntar uma crença | `.send(leader, askOne, goal_zone(_,_))` |
| `broadcast` | Enviar para todos | `.broadcast(tell, clear_event(10,15,3))` |

### 3.3 MOISE+ — Modelo Organizacional

| Atributo | Valor |
|----------|-------|
| **Versão** | 1.1 |
| **Artefato** | `org.jacamo:moise:1.1` |
| **Formato** | XML (`.xml`) |
| **Componentes** | Structural Spec, Functional Spec, Normative Spec |
| **Base teórica** | Hübner, Sichman & Boissier (2002) |

**O que é**: MOISE+ é um modelo para especificar organizações de agentes em três dimensões:

1. **Structural Specification (SS)** — papéis, grupos e links entre papéis
2. **Functional Specification (FS)** — esquemas, goals e missões
3. **Normative Specification (NS)** — obrigações e permissões

**Especificação XML** (`hive_org.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<organisational-specification id="hive_org"
    os-version="1.0"
    xmlns="http://moise.sourceforge.net/os">

  <!-- STRUCTURAL SPECIFICATION -->
  <structural-specification>
    <role-definitions>
      <role id="squad_leader"/>
      <role id="collector"/>
      <role id="assembler"/>
      <role id="sentinel"/>
    </role-definitions>

    <group-specification id="squad_group"
        min="2" max="4">
      <roles>
        <role id="squad_leader" min="1" max="1"/>
        <role id="collector" min="1" max="2"/>
        <role id="assembler" min="1" max="1"/>
      </roles>
      <links>
        <link from="squad_leader" to="collector"
              type="authority" scope="intra-group"/>
        <link from="squad_leader" to="assembler"
              type="authority" scope="intra-group"/>
        <link from="collector" to="assembler"
              type="communication" scope="intra-group"/>
      </links>
    </group-specification>

    <group-specification id="sentinel_group"
        min="1" max="2">
      <roles>
        <role id="sentinel" min="1" max="2"/>
      </roles>
    </group-specification>
  </structural-specification>

  <!-- FUNCTIONAL SPECIFICATION -->
  <functional-specification>
    <scheme id="exploration_scheme">
      <goal id="map_explored">
        <plan operator="parallel">
          <goal id="dispensers_found"/>
          <goal id="goal_zones_found"/>
          <goal id="role_zones_found"/>
        </plan>
      </goal>
      <mission id="m_scout" min="1" max="4">
        <goal id="dispensers_found"/>
        <goal id="goal_zones_found"/>
        <goal id="role_zones_found"/>
      </mission>
    </scheme>

    <scheme id="task_execution_scheme">
      <goal id="task_submitted">
        <plan operator="sequence">
          <goal id="blocks_collected"/>
          <goal id="blocks_assembled"/>
          <goal id="pattern_submitted"/>
        </plan>
      </goal>
      <mission id="m_collect" min="1" max="2">
        <goal id="blocks_collected"/>
      </mission>
      <mission id="m_assemble" min="1" max="1">
        <goal id="blocks_assembled"/>
      </mission>
      <mission id="m_submit" min="1" max="1">
        <goal id="pattern_submitted"/>
      </mission>
    </scheme>

    <scheme id="defense_scheme">
      <goal id="team_protected">
        <plan operator="parallel">
          <goal id="goal_zones_guarded"/>
          <goal id="threats_cleared"/>
        </plan>
      </goal>
      <mission id="m_guard" min="1" max="2">
        <goal id="goal_zones_guarded"/>
        <goal id="threats_cleared"/>
      </mission>
    </scheme>
  </functional-specification>

  <!-- NORMATIVE SPECIFICATION -->
  <normative-specification>
    <norm id="n_scout"
          type="obligation"
          role="squad_leader"
          mission="m_scout"/>
    <norm id="n_collect"
          type="obligation"
          role="collector"
          mission="m_collect"/>
    <norm id="n_assemble"
          type="obligation"
          role="assembler"
          mission="m_assemble"/>
    <norm id="n_submit"
          type="obligation"
          role="assembler"
          mission="m_submit"/>
    <norm id="n_guard"
          type="obligation"
          role="sentinel"
          mission="m_guard"/>
  </normative-specification>
</organisational-specification>
```

### 3.4 CArtAgO — Ambiente de Artefatos

| Atributo | Valor |
|----------|-------|
| **Versão** | 3.1 |
| **Artefato** | `org.jacamo:cartago:3.1` |
| **Linguagem** | Java |
| **Paradigma** | A&A (Agents & Artifacts) |
| **Integração Jason** | `org.jacamo:jaca:3.1` |

**O que é**: CArtAgO (Common ARTifact infrastructure for AGents in Open environments) permite criar artefatos — objetos compartilhados no ambiente que agentes podem observar e manipular. Os artefatos são escritos em Java e acessados pelos agentes Jason via operações e propriedades observáveis.

**Artefatos do projeto HIVE**:

| Artefato | Classe Java | Propriedades observáveis | Operações |
|----------|------------|-------------------------|-----------|
| **SharedMap** | `env.SharedMap` | `dispenser(X,Y,Type)`, `goal_zone(X,Y)`, `role_zone(X,Y)`, `obstacle(X,Y)`, `frontier(X,Y)` | `update_cell(X,Y,Content)`, `get_nearest_frontier(AgX,AgY)`, `get_nearest_dispenser(AgX,AgY,Type)` |
| **TaskBoard** | `env.TaskBoard` | `available_task(Name,Deadline,Reward,Reqs)`, `assigned_task(SquadId,TaskName)`, `task_score(Name,Score)` | `evaluate_task(Name)`, `bid_task(Name,Bid)`, `claim_task(Name,SquadId)`, `complete_task(Name)` |
| **NormMonitor** | `env.NormMonitor` | `active_norm(Id,Type,Limit)`, `carry_limit(N)`, `role_limit(RoleName,N)` | `update_norms(NormsList)`, `check_compliance(AgentName)` |
| **SquadCoordinator** | `env.SquadCoordinator` | `squad_member(SquadId,AgName,Role)`, `meeting_point(SquadId,X,Y)`, `connect_ready(SquadId,AgName)` | `join_squad(SquadId)`, `set_meeting_point(SquadId,X,Y)`, `signal_ready(SquadId)`, `request_connect(Partner,BX,BY)` |

**Exemplo de artefato** (`SharedMap.java`):

```java
package env;

import cartago.*;
import java.util.concurrent.ConcurrentHashMap;

public class SharedMap extends Artifact {

    private ConcurrentHashMap<String, String> cells;

    void init() {
        cells = new ConcurrentHashMap<>();
    }

    @OPERATION
    void update_cell(int x, int y, String content) {
        String key = x + "," + y;
        cells.put(key, content);

        if (content.startsWith("dispenser")) {
            String type = content.split(":")[1];
            defineObsProperty("dispenser", x, y, type);
        } else if (content.equals("goal_zone")) {
            defineObsProperty("goal_zone", x, y);
        } else if (content.equals("role_zone")) {
            defineObsProperty("role_zone", x, y);
        } else if (content.equals("obstacle")) {
            defineObsProperty("obstacle", x, y);
        }
    }

    @OPERATION
    void get_nearest_dispenser(int agX, int agY, String type,
                               OpFeedbackParam<Integer> resX,
                               OpFeedbackParam<Integer> resY) {
        // Busca o dispenser mais próximo do tipo solicitado
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;
        for (var entry : cells.entrySet()) {
            if (entry.getValue().equals("dispenser:" + type)) {
                String[] parts = entry.getKey().split(",");
                int dx = Integer.parseInt(parts[0]);
                int dy = Integer.parseInt(parts[1]);
                int dist = Math.abs(dx - agX) + Math.abs(dy - agY);
                if (dist < bestDist) {
                    bestDist = dist;
                    bx = dx;
                    by = dy;
                }
            }
        }
        resX.set(bx);
        resY.set(by);
    }
}
```

### 3.5 EISMASSim — Ponte Agente-Servidor

| Atributo | Valor |
|----------|-------|
| **Versão** | 2022-1.1.1 (inclusa no MASSIM) |
| **Dependência EIS** | EIS 0.5.0 (`github.com/eishub/eis`) |
| **Artefato** | `eismassim-2022-1.1.1-jar-with-dependencies.jar` |
| **Função** | Proxy client-side que traduz JSON ↔ IILang (percepts/actions) |

**O que é**: EISMASSim é a biblioteca Java que faz a ponte entre os agentes JaCaMo e o servidor MASSIM. Ela implementa o Environment Interface Standard (EIS), convertendo mensagens JSON do servidor em percepts que Jason entende (formato IILang), e convertendo ações AgentSpeak em mensagens JSON.

**Fluxo de dados**:

```
Jason Agent (.asl)
    │
    │  percepts IILang: thing(2,-1,block,b1), task(task2,188,44,[...])
    │  actions IILang:  Action("move", Identifier("n"))
    ▼
JaCaMo EIS Platform
    │
    │  Tradução automática IILang ↔ JSON
    ▼
EISMASSim Library
    │
    │  JSON: {"type":"action","content":{"id":1,"type":"move","p":["n"]}}
    │  JSON: {"type":"request-action","content":{"percept":{...}}}
    ▼
TCP Socket → Servidor MASSIM (porta 12300)
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
  "multi-entities": [
    {
      "name-prefix": "connectionHIVE",
      "username-prefix": "agentHIVE",
      "password": "1",
      "count": 15,
      "start-index": 0,
      "print-iilang": false,
      "print-json": false
    }
  ]
}
```

**Percepts recebidos por step** (tradução JSON → IILang → crenças Jason):

| Percept EIS (IILang) | Crença Jason resultante | Descrição |
|----------------------|------------------------|-----------|
| `thing(X, Y, Type, Details)` | `thing(X, Y, Type, Details)[source(percept)]` | Entidade, bloco, dispenser, obstáculo ou marker na visão |
| `task(Name, Deadline, Reward, Reqs)` | `task(Name, Deadline, Reward, Reqs)[source(percept)]` | Tarefa ativa com padrão de blocos |
| `attached(X, Y)` | `attached(X, Y)[source(percept)]` | Coisa attached ao agente |
| `energy(N)` | `energy(N)[source(percept)]` | Nível de energia atual |
| `step(N)` | `step(N)[source(percept)]` | Step atual da simulação |
| `norm(Id, Start, End, Reqs, Fine)` | `norm(Id, Start, End, Reqs, Fine)[source(percept)]` | Norma ativa ou anunciada |
| `goalZone(X, Y)` | `goalZone(X, Y)[source(percept)]` | Célula de goal zone na visão |
| `roleZone(X, Y)` | `roleZone(X, Y)[source(percept)]` | Célula de role zone na visão |
| `role(Name)` | `role(Name)[source(percept)]` | Role atual do agente |
| `lastActionResult(Result)` | `lastActionResult(Result)[source(percept)]` | Resultado da última ação |
| `score(N)` | `score(N)[source(percept)]` | Pontuação atual do time |

**Ações enviadas** (AgentSpeak → IILang → JSON):

| Ação AgentSpeak | IILang | JSON enviado |
|----------------|--------|-------------|
| `move(n)` | `Action("move", Identifier("n"))` | `{"type":"move","p":["n"]}` |
| `attach(s)` | `Action("attach", Identifier("s"))` | `{"type":"attach","p":["s"]}` |
| `request(e)` | `Action("request", Identifier("e"))` | `{"type":"request","p":["e"]}` |
| `submit(task2)` | `Action("submit", Identifier("task2"))` | `{"type":"submit","p":["task2"]}` |
| `connect(agent2, 0, 2)` | `Action("connect", Identifier("agent2"), Numeral(0), Numeral(2))` | `{"type":"connect","p":["agent2","0","2"]}` |
| `rotate(cw)` | `Action("rotate", Identifier("cw"))` | `{"type":"rotate","p":["cw"]}` |
| `clear(3, -1)` | `Action("clear", Numeral(3), Numeral(-1))` | `{"type":"clear","p":["3","-1"]}` |
| `adopt(explorer)` | `Action("adopt", Identifier("explorer"))` | `{"type":"adopt","p":["explorer"]}` |
| `survey(dispenser)` | `Action("survey", Identifier("dispenser"))` | `{"type":"survey","p":["dispenser"]}` |
| `detach(n)` | `Action("detach", Identifier("n"))` | `{"type":"detach","p":["n"]}` |
| `skip` | `Action("skip")` | `{"type":"skip","p":[]}` |

### 3.6 NPL — Normative Programming Language

| Atributo | Valor |
|----------|-------|
| **Versão** | 0.6.1 |
| **Artefato** | `org.jacamo:npl:0.6.1` |
| **Função** | Interpretador de normas do MOISE+, integrado ao runtime |

Componente interno do JaCaMo que processa as normas definidas na especificação organizacional. No nosso caso, garante que os agentes cumpram suas obrigações (ex: collector MUST fulfill m_collect). Não requer configuração direta.

---

## 4. Linguagens de Programação

| Linguagem | Uso no projeto | Arquivos |
|-----------|---------------|----------|
| **AgentSpeak(L)** | Lógica BDI dos agentes (crenças, planos, goals) | `*.asl` |
| **Java** | Artefatos CArtAgO, internal actions customizadas, build | `*.java` |
| **XML** | Especificação organizacional MOISE+ | `hive_org.xml` |
| **JSON** | Configuração do MASSIM, EISMASSim, comunicação agente-servidor | `*.json` |
| **JCM** | Configuração do projeto JaCaMo (declaração de agentes, workspaces, organização) | `hive.jcm` |
| **Gradle (Groovy DSL)** | Build system e gerenciamento de dependências | `build.gradle` |

---

## 5. Dependências Completas

### 5.1 Build do Projeto (`build.gradle`)

```groovy
plugins {
    id 'java'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url "https://raw.githubusercontent.com/jacamo-lang/mvn-repo/master" }
}

dependencies {
    // JaCaMo (inclui Jason 3.3.1 + MOISE+ 1.1 + CArtAgO 3.1)
    implementation 'org.jacamo:jacamo:1.3'

    // EISMASSim (ponte com servidor MASSIM)
    implementation files('lib/eismassim-2022-1.1.1-jar-with-dependencies.jar')
}
```

### 5.2 Árvore de Dependências

```
org.jacamo:jacamo:1.3
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

eismassim-2022-1.1.1-jar-with-dependencies.jar
└── eis:0.5.0 (Environment Interface Standard)
```

---

## 6. Requisitos de Ambiente de Desenvolvimento

### 6.1 Hardware mínimo

| Recurso | Mínimo | Recomendado |
|---------|--------|-------------|
| **CPU** | 4 cores | 8 cores (servidor MASSIM + JaCaMo simultâneos) |
| **RAM** | 4 GB | 8 GB |
| **Disco** | 2 GB livres | 5 GB (com logs e replays) |
| **Rede** | Loopback (localhost) | LAN para competição |

### 6.2 Software requerido

| Software | Versão | Propósito |
|----------|--------|-----------|
| **JDK** | >= 21 | Runtime para JaCaMo e compilação de artefatos |
| **JDK** (MASSIM) | >= 17 | Build e execução do servidor MASSIM |
| **Gradle** | >= 8.0 | Build do projeto JaCaMo |
| **Maven** | >= 3.8 | Build do servidor MASSIM (`mvn package`) |
| **Git** | >= 2.0 | Controle de versão |
| **IDE** | IntelliJ IDEA ou VS Code | Desenvolvimento (suporte a Java + Gradle) |
| **Browser** | Qualquer moderno | Web Monitor do MASSIM |

### 6.3 Variáveis de ambiente

```bash
export JAVA_HOME=/path/to/jdk-21
export JACAMO_HOME=/path/to/jacamo
export PATH=$JAVA_HOME/bin:$JACAMO_HOME/bin:$PATH
```

---

## 7. Estrutura Final de Arquivos

```
hive-project/
│
├── build.gradle                          # Build config + dependências
├── settings.gradle                       # Nome do projeto Gradle
│
├── lib/
│   └── eismassim-2022-1.1.1-jar-with-dependencies.jar
│
├── src/
│   ├── agt/                              # === AGENTES JASON ===
│   │   ├── squad_leader.asl              #   Líder de esquadrão (BDI)
│   │   ├── collector.asl                 #   Coletor de blocos (BDI)
│   │   ├── assembler.asl                 #   Montador/submitter (BDI)
│   │   ├── sentinel.asl                  #   Sentinela tático (BDI)
│   │   └── common/
│   │       ├── navigation.asl            #   Planos de navegação reutilizáveis
│   │       ├── perception.asl            #   Processamento de percepts
│   │       ├── communication.asl         #   Protocolos de mensagem
│   │       └── norms.asl                 #   Adaptação a normas
│   │
│   ├── org/                              # === ORGANIZAÇÃO MOISE+ ===
│   │   └── hive_org.xml                  #   SS + FS + NS completo
│   │
│   ├── env/                              # === ARTEFATOS CArtAgO ===
│   │   ├── SharedMap.java                #   Mapa compartilhado incremental
│   │   ├── TaskBoard.java                #   Board de tarefas + leilão
│   │   ├── NormMonitor.java              #   Monitor de normas do servidor
│   │   └── SquadCoordinator.java         #   Coordenação de esquadrões
│   │
│   └── java/                             # === INTERNAL ACTIONS ===
│       └── hive/
│           ├── PathFinder.java           #   A* para navegação em grade
│           ├── PatternMatcher.java        #   Verificar padrão de blocos vs task
│           └── DirectionCalculator.java  #   Calcular direção relativa
│
├── hive.jcm                              # Config principal JaCaMo
├── eismassimconfig.json                  # Config conexão com MASSIM
│
├── conf/                                 # === CONFIGURAÇÃO MASSIM ===
│   ├── server.json                       #   Config do servidor de simulação
│   └── accounts.json                     #   Times e credenciais
│
├── doc/
│   ├── funcIdea.md                       #   Documento funcional
│   ├── TECHSPEC.md                       #   Este documento
│   ├── ARCH.md                           #   Documento de arquitetura
│   └── relatorio.tex                     #   Relatório final (artigo)
│
└── massim_2022/                          # Servidor MASSIM (submodule ou clone)
    ├── server/
    ├── eismassim/
    └── docs/
```

---

## 8. Protocolos de Comunicação — Resumo

### 8.1 Agente ↔ Servidor MASSIM

| Camada | Protocolo | Formato |
|--------|-----------|---------|
| Transporte | TCP socket persistente | Bytes |
| Aplicação | MASSIM Protocol v2022 | JSON |
| Abstração | EIS / IILang | Objetos Java |
| Agente | AgentSpeak percepts/actions | Prolog-like |

### 8.2 Agente ↔ Agente (intra-time)

| Mecanismo | Tecnologia | Uso |
|-----------|-----------|-----|
| Mensagens diretas | Jason `.send` | Comunicação ponto-a-ponto entre agentes |
| Broadcast | Jason `.broadcast` | Alertas (clear events, novas tasks) |
| Artefatos compartilhados | CArtAgO | Mapa global, task board, estado do esquadrão |
| Organização | MOISE+ / NPL | Obrigações, papéis, coordenação normativa |

### 8.3 Agente ↔ Organização

| Mecanismo | Direção | Uso |
|-----------|---------|-----|
| `lookupArtifact` / `focus` | Agente → Org | Agente observa o estado da organização |
| `adoptRole` | Agente → Org | Agente assume um papel no grupo |
| `commitMission` | Agente → Org | Agente se compromete com uma missão |
| `goalAchieved` | Agente → Org | Agente sinaliza conclusão de goal |
| Obligation percepts | Org → Agente | Organização notifica obrigações pendentes |

---

## 9. Mapa de Versões — Resumo Rápido

| Componente | Versão | Tipo |
|-----------|--------|------|
| JDK (desenvolvimento) | 21+ | Runtime |
| JDK (MASSIM server) | 17+ | Runtime |
| JaCaMo | 1.3 | Framework |
| Jason Interpreter | 3.3.1 | Framework |
| MOISE+ | 1.1 | Framework |
| CArtAgO | 3.1 | Framework |
| JaCa (Jason-CArtAgO bridge) | 3.1 | Framework |
| NPL | 0.6.1 | Framework |
| ANTLR4 | 4.12.0 | Parser |
| EIS | 0.5.0 | Standard |
| EISMASSim | 2022-1.1.1 | Library |
| MASSIM Server | 2022-1.1.1 | Server |
| Gradle | 8.x | Build |
| Maven | 3.8+ | Build (MASSIM) |
