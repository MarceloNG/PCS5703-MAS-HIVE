# Plano Detalhado — Fases 5 e 6

---

## FASE 5 — Coordenacao e Leilao

**Objetivo**: Alocacao de tarefas entre esquadroes via protocolo de leilao distribuido.
**Criterio de aceite**: Quando task aparece, squad leaders fazem bid, melhor squad e alocado, collectors recebem ordem de coleta dos tipos corretos.
**Dependencia**: Fases 3 e 4 concluidas.

---

### Contexto — O que ja existe

| Componente | Status | Localização |
|-----------|--------|-------------|
| `SharedMap` (dispensers, goal zones, frontiers) | Ja implementado | `src/env/env/SharedMap.java` |
| `perception.asl` (processa `task(Name, Deadline, Reward, Reqs)`) | Ja implementado | `src/agt/common/perception.asl` |
| `collection.asl` (ciclo request/attach) | Ja implementado | `src/agt/common/collection.asl` |
| `squad_leader.asl` (explora, my_role_type) | Ja implementado | `src/agt/squad_leader.asl` |
| `collector.asl` (reage a new_dispenser, coleta) | Ja implementado | `src/agt/collector.asl` |
| `assembler.asl` (explora, placeholder) | Ja implementado | `src/agt/assembler.asl` |
| `hive_org.xml` (SS + FS + NS) | Ja implementado | `src/org/hive_org.xml` |

**Percepts MASSIM relevantes para tasks**:

| Percept | Formato | Significado |
|---------|---------|-------------|
| `task(Name, Deadline, Reward, Reqs)` | `task(task0, 70, 40, [req(0,1,b0), req(1,1,b0)])` | Task ativa com deadline (step), reward (pontos) e lista de blocos requeridos |
| `score(N)` | `score(0)` | Pontuacao atual do time |

**Formato dos requirements**: `req(RelX, RelY, BlockType)` — posicao relativa ao agente que faz submit.
Exemplo: `[req(0,1,b1), req(0,2,b1), req(1,1,b1)]` = 3 blocos b1 nas posicoes (0,1), (0,2) e (1,1) relativas ao submitter.

**Acoes MASSIM relevantes**:

| Acao | Parametro | Efeito |
|------|-----------|--------|
| `submit(TaskName)` | Nome da task | Submete o padrao — so funciona em goal zone, com o padrao correto de blocos attached |
| `connect(Partner, X, Y)` | Nome do agente parceiro + posicao relativa do bloco | Transfere bloco de um agente para outro — ambos devem executar no mesmo step |

---

### 5.1 Criar `src/env/env/TaskBoard.java` — Artefato de gerenciamento de tarefas

**Arquivo**: `src/env/env/TaskBoard.java`

**Responsabilidades**:
- Registrar tasks detectadas pelos agentes
- Avaliar tasks (score = reward / custo estimado)
- Gerenciar leilao: receber bids, resolver, atribuir
- Rastrear tasks ativas, atribuidas, completadas e expiradas

**Estrutura de dados interna**:

```java
package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TaskBoard extends Artifact {

    // Task info: name -> {deadline, reward, reqs (list of req(rx,ry,type))}
    private ConcurrentHashMap<String, TaskInfo> knownTasks;
    // Bids: taskName -> list of (squadId, bidValue)
    private ConcurrentHashMap<String, List<Bid>> bids;
    // Assigned: taskName -> squadId
    private ConcurrentHashMap<String, String> assignedTasks;

    static class TaskInfo {
        String name;
        int deadline, reward;
        List<String[]> reqs; // each: {relX, relY, blockType}
        int currentStep;
        TaskInfo(String n, int d, int r, List<String[]> reqs) {
            this.name = n; this.deadline = d; this.reward = r; this.reqs = reqs;
        }
    }

    static class Bid {
        String squadId;
        double value;
        Bid(String s, double v) { this.squadId = s; this.value = v; }
    }

    void init() {
        knownTasks = new ConcurrentHashMap<>();
        bids = new ConcurrentHashMap<>();
        assignedTasks = new ConcurrentHashMap<>();
    }

    @OPERATION
    void register_task(Object oname, Object odeadline, Object oreward, Object onBlocks) {
        String name = oname.toString();
        int deadline = toInt(odeadline);
        int reward = toInt(oreward);
        int nBlocks = toInt(onBlocks);
        if (!knownTasks.containsKey(name) && !assignedTasks.containsKey(name)) {
            knownTasks.put(name, new TaskInfo(name, deadline, reward, new ArrayList<>()));
            signal("new_task_available", name, deadline, reward, nBlocks);
        }
    }

    @OPERATION
    void evaluate_task(Object oname, Object odeadline, Object oreward, Object onBlocks,
                       OpFeedbackParam<Double> score) {
        int reward = toInt(oreward);
        int nBlocks = toInt(onBlocks);
        // Score = reward / nBlocks (simplicidade; pode ser refinado)
        double s = (nBlocks > 0) ? (double) reward / nBlocks : 0;
        score.set(s);
    }

    @OPERATION
    void place_bid(Object otaskName, Object osquadId, Object obidValue) {
        String taskName = otaskName.toString();
        String squadId = osquadId.toString();
        double bidValue = toDouble(obidValue);
        bids.computeIfAbsent(taskName, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new Bid(squadId, bidValue));
    }

    @OPERATION
    void resolve_auction(Object otaskName,
                         OpFeedbackParam<String> winnerSquad) {
        String taskName = otaskName.toString();
        List<Bid> taskBids = bids.get(taskName);
        if (taskBids == null || taskBids.isEmpty()) {
            winnerSquad.set("none");
            return;
        }
        Bid best = taskBids.stream()
            .max(Comparator.comparingDouble(b -> b.value))
            .orElse(null);
        if (best != null) {
            assignedTasks.put(taskName, best.squadId);
            signal("task_assigned", taskName, best.squadId);
            defineObsProperty("assigned_task", taskName, best.squadId);
            winnerSquad.set(best.squadId);
        } else {
            winnerSquad.set("none");
        }
        bids.remove(taskName);
    }

    @OPERATION
    void complete_task(Object otaskName) {
        String taskName = otaskName.toString();
        assignedTasks.remove(taskName);
        knownTasks.remove(taskName);
    }

    @OPERATION
    void remove_expired(Object ocurrentStep) {
        int step = toInt(ocurrentStep);
        List<String> expired = new ArrayList<>();
        for (var entry : knownTasks.entrySet()) {
            if (entry.getValue().deadline <= step) {
                expired.add(entry.getKey());
            }
        }
        for (String name : expired) {
            knownTasks.remove(name);
            assignedTasks.remove(name);
            bids.remove(name);
        }
    }

    @OPERATION
    void is_task_assigned(Object otaskName, OpFeedbackParam<Boolean> result) {
        result.set(assignedTasks.containsKey(otaskName.toString()));
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    private double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(o.toString());
    }
}
```

**Operacoes**:

| Operacao | Parametros | Efeito |
|----------|------------|--------|
| `register_task` | name, deadline, reward, nBlocks | Registra task e sinaliza `new_task_available` |
| `evaluate_task` | name, deadline, reward, nBlocks → score | Calcula score = reward / nBlocks |
| `place_bid` | taskName, squadId, bidValue | Registra bid de um squad |
| `resolve_auction` | taskName → winnerSquad | Resolve leilao: maior bid ganha |
| `complete_task` | taskName | Remove task completada |
| `remove_expired` | currentStep | Remove tasks expiradas |
| `is_task_assigned` | taskName → boolean | Verifica se task ja foi atribuida |

---

### 5.2 Criar `src/env/env/SquadCoordinator.java` — Artefato de coordenacao

**Arquivo**: `src/env/env/SquadCoordinator.java`

**Responsabilidades**:
- Mapear agentes a squads (por convencao: connectionA1→squad1, connectionA2→squad2, connectionA3→squad3)
- Definir meeting point por squad
- Rastrear quem esta pronto para connect
- Designar tipo de bloco a cada collector

```java
package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SquadCoordinator extends Artifact {

    // Squad assignments: agentName -> squadId
    private ConcurrentHashMap<String, String> agentSquad;
    // Squad members by squad: squadId -> list of agentNames
    private ConcurrentHashMap<String, List<String>> squadMembers;
    // Meeting points: squadId -> {x, y}
    private ConcurrentHashMap<String, int[]> meetingPoints;
    // Ready signals: squadId -> set of agentNames ready
    private ConcurrentHashMap<String, Set<String>> readyAgents;
    // Collector assignments: agentName -> blockType to collect
    private ConcurrentHashMap<String, String> collectorAssignments;

    void init() {
        agentSquad = new ConcurrentHashMap<>();
        squadMembers = new ConcurrentHashMap<>();
        meetingPoints = new ConcurrentHashMap<>();
        readyAgents = new ConcurrentHashMap<>();
        collectorAssignments = new ConcurrentHashMap<>();
        setupDefaultSquads();
    }

    private void setupDefaultSquads() {
        // Convencao fixa: 3 squads de 4 agentes cada
        // Squad 1: connectionA1 (leader), connectionA4, connectionA5 (collectors), connectionA10 (assembler)
        // Squad 2: connectionA2 (leader), connectionA6, connectionA7 (collectors), connectionA11 (assembler)
        // Squad 3: connectionA3 (leader), connectionA8, connectionA9 (collectors), connectionA12 (assembler)
        String[][] squads = {
            {"squad1", "connectionA1", "connectionA4", "connectionA5", "connectionA10"},
            {"squad2", "connectionA2", "connectionA6", "connectionA7", "connectionA11"},
            {"squad3", "connectionA3", "connectionA8", "connectionA9", "connectionA12"}
        };
        for (String[] sq : squads) {
            String sid = sq[0];
            List<String> members = new ArrayList<>();
            for (int i = 1; i < sq.length; i++) {
                agentSquad.put(sq[i], sid);
                members.add(sq[i]);
            }
            squadMembers.put(sid, members);
        }
    }

    @OPERATION
    void get_my_squad(Object oagentName, OpFeedbackParam<String> squadId) {
        String ag = oagentName.toString();
        String sq = agentSquad.getOrDefault(ag, "none");
        squadId.set(sq);
    }

    @OPERATION
    void get_squad_collectors(Object osquadId,
                              OpFeedbackParam<String> col1,
                              OpFeedbackParam<String> col2) {
        String sid = osquadId.toString();
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        // Collectors sao os que NAO sao o primeiro (leader) nem o ultimo (assembler)
        List<String> collectors = new ArrayList<>();
        for (String m : members) {
            if (m.contains("4") || m.contains("5") || m.contains("6") ||
                m.contains("7") || m.contains("8") || m.contains("9")) {
                collectors.add(m);
            }
        }
        col1.set(collectors.size() > 0 ? collectors.get(0) : "none");
        col2.set(collectors.size() > 1 ? collectors.get(1) : "none");
    }

    @OPERATION
    void get_squad_assembler(Object osquadId, OpFeedbackParam<String> assembler) {
        String sid = osquadId.toString();
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        for (String m : members) {
            if (m.contains("10") || m.contains("11") || m.contains("12")) {
                assembler.set(m);
                return;
            }
        }
        assembler.set("none");
    }

    @OPERATION
    void set_meeting_point(Object osquadId, Object ox, Object oy) {
        String sid = osquadId.toString();
        int x = toInt(ox), y = toInt(oy);
        meetingPoints.put(sid, new int[]{x, y});
        signal("meeting_point_set", sid, x, y);
    }

    @OPERATION
    void get_meeting_point(Object osquadId,
                           OpFeedbackParam<Integer> resX,
                           OpFeedbackParam<Integer> resY) {
        String sid = osquadId.toString();
        int[] mp = meetingPoints.get(sid);
        if (mp != null) {
            resX.set(mp[0]);
            resY.set(mp[1]);
        } else {
            resX.set(-1);
            resY.set(-1);
        }
    }

    @OPERATION
    void assign_block_to_collector(Object oagentName, Object oblockType) {
        collectorAssignments.put(oagentName.toString(), oblockType.toString());
        signal("collect_order", oagentName.toString(), oblockType.toString());
    }

    @OPERATION
    void get_my_assignment(Object oagentName, OpFeedbackParam<String> blockType) {
        String bt = collectorAssignments.getOrDefault(oagentName.toString(), "none");
        blockType.set(bt);
    }

    @OPERATION
    void signal_ready(Object osquadId, Object oagentName) {
        String sid = osquadId.toString();
        String ag = oagentName.toString();
        readyAgents.computeIfAbsent(sid, k -> ConcurrentHashMap.newKeySet()).add(ag);
        signal("agent_ready", sid, ag);
    }

    @OPERATION
    void all_ready(Object osquadId, OpFeedbackParam<Boolean> result) {
        String sid = osquadId.toString();
        Set<String> ready = readyAgents.get(sid);
        List<String> members = squadMembers.getOrDefault(sid, Collections.emptyList());
        // Pronto quando todos os collectors e assembler sinalizaram
        int expected = members.size() - 1; // menos o leader
        result.set(ready != null && ready.size() >= expected);
    }

    @OPERATION
    void clear_ready(Object osquadId) {
        readyAgents.remove(osquadId.toString());
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }
}
```

---

### 5.3 Atualizar `perception.asl` — Registrar tasks no TaskBoard

**Arquivo**: `src/agt/common/perception.asl`

**Modificacao**: O handler `+task(Name, Deadline, Reward, Reqs)` atualmente so cria `known_task`.
Precisa tambem registrar no TaskBoard (se o agente tiver foco nele).

```prolog
// --- Tasks ---
+task(Name, Deadline, Reward, Reqs)
    <- .length(Reqs, NBlocks);
       -known_task(Name, _, _, _);
       +known_task(Name, Deadline, Reward, NBlocks);
       register_task(Name, Deadline, Reward, NBlocks).
```

**Nota**: `register_task` e uma operacao do TaskBoard. Todos os agentes que observam
o TaskBoard podem chamar. O TaskBoard ignora registros duplicados.

---

### 5.4 Atualizar `squad_leader.asl` — Logica de leilao

**Arquivo**: `src/agt/squad_leader.asl`

O squad leader precisa:
1. Detectar novas tasks disponiveis (via `new_task_available` do TaskBoard)
2. Avaliar a task (score)
3. Fazer bid
4. Se ganhou, delegar coleta aos collectors do seu squad

```prolog
// --- Reagir a nova task disponivel ---

+new_task_available(TaskName, Deadline, Reward, NBlocks)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (MySquad \== "none") {
           evaluate_task(TaskName, Deadline, Reward, NBlocks, Score);
           .print("[LEADER] Task ", TaskName, " disponivel. Score=", Score, ". Fazendo bid...");
           place_bid(TaskName, MySquad, Score);
           // Pequeno delay para outros leaders fazerem bid
           .wait(200);
           resolve_auction(TaskName, Winner);
           if (Winner == MySquad) {
               .print("[LEADER] Ganhamos task ", TaskName, "! Delegando coleta...");
               !delegate_collection(TaskName, NBlocks)
           } else {
               .print("[LEADER] Task ", TaskName, " atribuida a ", Winner)
           }
       }.

// --- Delegar coleta aos collectors do squad ---

+!delegate_collection(TaskName, NBlocks)
    : my_pos(MX, MY)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       // Definir meeting point = goal zone mais proxima
       get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           set_meeting_point(MySquad, GX, GY);
           .print("[LEADER] Meeting point definido em (", GX, ",", GY, ")")
       };
       // Obter collectors do squad
       get_squad_collectors(MySquad, Col1, Col2);
       // Designar bloco b0 ao primeiro collector, b1 ao segundo (simplificacao)
       if (Col1 \== "none") {
           assign_block_to_collector(Col1, "b0");
           .print("[LEADER] ", Col1, " designado para coletar b0")
       };
       if (Col2 \== "none" & NBlocks > 1) {
           assign_block_to_collector(Col2, "b1");
           .print("[LEADER] ", Col2, " designado para coletar b1")
       }.

-!delegate_collection(_, _) <- .print("[LEADER] Falha ao delegar coleta").
```

**Nota**: Na versao simplificada, o leader designa b0 ao primeiro collector e b1 ao segundo.
Uma versao mais sofisticada analisaria os `req()` da task para determinar os tipos exatos.

---

### 5.5 Atualizar `collector.asl` — Receber designacao e navegar ao meeting point

**Arquivo**: `src/agt/collector.asl`

O collector precisa:
1. Receber ordem de coleta (via `collect_order` signal do SquadCoordinator)
2. Coletar o bloco designado (ja implementado em collection.asl)
3. Apos coleta, navegar ao meeting point
4. Sinalizar que esta pronto

```prolog
// --- Reagir a ordem de coleta do leader ---

+collect_order(AgentName, BlockType)
    <- .my_name(Me);
       if (AgentName == Me) {
           .print("[COLLECTOR] Recebi ordem: coletar ", BlockType);
           -collecting(_, _, _);
           -has_destination(_, _);
           -waiting_request(_, _);
           -waiting_attach_result(_, _);
           -collected_block(_);
           !collect_block(BlockType)
       }.

// --- Apos coletar, navegar ao meeting point ---

+collected_block(Type)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (MySquad \== "none") {
           get_meeting_point(MySquad, MPX, MPY);
           if (MPX \== -1) {
               .print("[COLLECTOR] Bloco ", Type, " coletado! Indo ao meeting point (", MPX, ",", MPY, ")");
               +has_destination(MPX, MPY);
               +navigating_to_meeting_point(MySquad)
           } else {
               .print("[COLLECTOR] Bloco coletado, mas sem meeting point definido")
           }
       }.

// --- Chegou ao meeting point → sinalizar pronto ---
// (Detectado pelo navigation.asl quando has_destination e alcancado)

+!check_meeting_point_arrival
    : navigating_to_meeting_point(SquadId) & my_pos(MX, MY)
      & not has_destination(_, _)
    <- -navigating_to_meeting_point(SquadId);
       .my_name(Me);
       signal_ready(SquadId, Me);
       .print("[COLLECTOR] Cheguei ao meeting point. Pronto para connect.").

+!check_meeting_point_arrival <- true.
```

**Nota**: O collector ainda mantem a reacao a `new_dispenser` como fallback quando nao tem
ordem de coleta. Quando recebe `collect_order`, sobrescreve a coleta oportunista.

---

### 5.6 Configurar artefatos no setup dos agentes

Todos os agentes que participam de coordenacao precisam de foco no TaskBoard e SquadCoordinator.

**Abordagem**: Similar ao SharedMap, criar os artefatos no primeiro agente e lookup nos demais.

**Modificacao em cada ASL especializado** (squad_leader, collector, assembler):

```prolog
+!start
    <- .my_name(Me);
       .print("[ROLE] ", Me, " iniciado.");
       !setup_shared_map;
       !setup_task_board;
       !setup_squad_coordinator;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[ROLE] Conectado.").

// TaskBoard setup
+!setup_task_board
    <- lookupArtifact("task_board", TbId); focus(TbId).
-!setup_task_board
    <- .wait(50); !try_create_task_board.
+!try_create_task_board
    <- makeArtifact("task_board", "env.TaskBoard", [], TbId); focus(TbId).
-!try_create_task_board
    <- .wait(100); !setup_task_board.

// SquadCoordinator setup
+!setup_squad_coordinator
    <- lookupArtifact("squad_coordinator", ScId); focus(ScId).
-!setup_squad_coordinator
    <- .wait(50); !try_create_squad_coordinator.
+!try_create_squad_coordinator
    <- makeArtifact("squad_coordinator", "env.SquadCoordinator", [], ScId); focus(ScId).
-!try_create_squad_coordinator
    <- .wait(100); !setup_squad_coordinator.
```

**Sentinels**: NAO precisam de TaskBoard ou SquadCoordinator (nao participam de coleta/montagem).

---

### 5.7 Testar pipeline de leilao

**Cenario de teste**:

1. Iniciar MASSIM com TestConfig (100 steps, tasks com size 1-3)
2. Iniciar JaCaMo com 15 agentes
3. Observar nos logs:
   - Task aparece → `[LEADER] Task task0 disponivel. Score=20.0. Fazendo bid...`
   - Leilao resolvido → `[LEADER] Ganhamos task task0! Delegando coleta...`
   - Meeting point definido → `[LEADER] Meeting point definido em (12,8)`
   - Collector designado → `[LEADER] connectionA4 designado para coletar b0`
   - Collector recebe ordem → `[COLLECTOR] Recebi ordem: coletar b0`
   - Collector coleta → `[COL] Bloco b0 attached com sucesso!`
   - Collector vai ao meeting point → `[COLLECTOR] Indo ao meeting point (12,8)`

**Troubleshooting**:

| Problema | Causa provavel | Solucao |
|----------|---------------|---------|
| `register_task` falha | TaskBoard nao focado pelo agente | Verificar setup_task_board |
| Leilao nao resolve | Todos os leaders fazem bid ao mesmo tempo, race condition | Adicionar .wait(200) antes de resolve |
| Collector nao recebe ordem | Foco no SquadCoordinator ausente | Verificar setup_squad_coordinator |
| Collector nao coleta tipo certo | `collect_block(Type)` busca dispenser do tipo errado | Verificar mapeamento de tipos |
| Meeting point = (-1,-1) | Nenhuma goal zone conhecida ainda | Leader espera goal zone ser descoberta |

---

### 5.8 Resumo de arquivos — Fase 5

| Arquivo | Acao | Descricao |
|---------|------|-----------|
| `src/env/env/TaskBoard.java` | **CRIAR** | Artefato: registro de tasks, leilao, atribuicao |
| `src/env/env/SquadCoordinator.java` | **CRIAR** | Artefato: squads, meeting points, assignments |
| `src/agt/common/perception.asl` | MODIFICAR | `+task` registra no TaskBoard |
| `src/agt/squad_leader.asl` | MODIFICAR | Leilao, avaliacao, delegacao |
| `src/agt/collector.asl` | MODIFICAR | Recebe ordem, navega ao meeting point |
| `src/agt/assembler.asl` | MODIFICAR | Setup de artefatos (TaskBoard, SquadCoordinator) |

---

### Ordem de execucao — Fase 5

```
5.1  Criar TaskBoard.java
5.2  Criar SquadCoordinator.java
5.3  Atualizar perception.asl (register_task)
5.4  Atualizar squad_leader.asl (setup artefatos + leilao + delegacao)
5.5  Atualizar collector.asl (setup artefatos + collect_order + meeting point)
5.6  Atualizar assembler.asl (setup artefatos)
5.7  Compilar e testar pipeline de leilao
5.8  Verificar que collector coleta tipo correto e navega ao meeting point
```

---
---

## FASE 6 — Montagem e Connect

**Objetivo**: Agentes executam `connect` sincronizado para montar padroes complexos de blocos.
**Criterio de aceite**: Dois agentes executam connect com sucesso. Padrao de 2-3 blocos montado.
**Dependencia**: Fase 5 concluida.

---

### Contexto — Mecanica do Connect no MASSIM

**Como connect funciona**:

1. Dois agentes (A e B) devem estar **adjacentes** (distancia Manhattan == 1)
2. Agente A tem bloco(s) attached, agente B tem bloco(s) attached
3. **Ambos** executam `connect(partner, relX, relY)` no **mesmo step**
4. `partner` = nome do agente parceiro (nome MASSIM, ex: `agentA4`)
5. `relX, relY` = posicao relativa do bloco do parceiro em relacao a si mesmo
6. Se bem-sucedido, os blocos de B sao transferidos para A (todos ficam attached a A)

**Resultado success**: Todos os blocos attached a B agora ficam attached a A.
**Resultados de falha**:
- `failed_partner` — parceiro nao executou connect no mesmo step
- `failed_target` — posicao relativa do bloco nao corresponde
- `failed_blocked` — resultado colidia com obstaculo
- `failed` — agentes nao sao adjacentes

**Exemplo concreto**:

```
Antes do connect:
  Agente A (assembler) em (10,10), tem bloco b0 em attached(0,1) = pos absoluta (10,11)
  Agente B (collector) em (11,10), tem bloco b1 em attached(0,1) = pos absoluta (11,11)

  A executa: connect(agentA4, 1, 0)   // B esta a (1,0) de A
  B executa: connect(agentA10, -1, 0)  // A esta a (-1,0) de B

Depois do connect (se success):
  Agente A tem attached(0,1) [b0] e attached(1,1) [b1]
  Agente B nao tem mais nenhum bloco attached
```

**Submit**: Apos montar o padrao completo, o assembler navega ate uma goal zone
e executa `submit(taskName)`. O MASSIM verifica se os blocos attached correspondem
ao padrao `req(relX, relY, type)` da task. Se sim, pontos sao concedidos.

---

### 6.1 Criar `src/agt/common/communication.asl` — Mensagens de sincronizacao

**Arquivo**: `src/agt/common/communication.asl`

Modulo para comunicacao inter-agente via Jason `.send()` e `.broadcast()`.

```prolog
// ============================================================
// communication.asl — Mensagens de sincronizacao para connect
// ============================================================

// --- Assembler envia pedido de connect para collector ---
// Mensagem: tell connect_request(AssemblerName, AssemblerX, AssemblerY, TargetStep)

+!request_connect(CollectorName, TargetStep)
    : my_pos(MX, MY)
    <- .my_name(Me);
       .send(CollectorName, tell, connect_request(Me, MX, MY, TargetStep));
       .print("[COMM] Pedido de connect enviado para ", CollectorName, " no step ", TargetStep).

// --- Collector recebe pedido e confirma ---

+connect_request(AssemblerName, AsmX, AsmY, TargetStep)[source(S)]
    <- .my_name(Me);
       .print("[COMM] Recebi pedido de connect de ", AssemblerName, " para step ", TargetStep);
       +pending_connect(AssemblerName, AsmX, AsmY, TargetStep).

// --- Collector confirma presenca ---

+!confirm_connect(AssemblerName)
    : my_pos(MX, MY)
    <- .my_name(Me);
       .send(AssemblerName, tell, connect_confirmed(Me, MX, MY));
       .print("[COMM] Confirmacao de connect enviada para ", AssemblerName).

+connect_confirmed(CollectorName, ColX, ColY)[source(S)]
    <- .print("[COMM] ", CollectorName, " confirmou connect em (", ColX, ",", ColY, ")");
       +partner_confirmed(CollectorName, ColX, ColY).
```

---

### 6.2 Criar `src/java/hive/ConnectCalculator.java` — Calcular parametros do connect

**Arquivo**: `src/java/hive/ConnectCalculator.java`

Internal action que calcula os parametros `(relX, relY)` para o connect,
dados as posicoes absolutas dos dois agentes.

```java
package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class ConnectCalculator extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        // args[0] = myX, args[1] = myY (minha posicao)
        // args[2] = partnerX, args[3] = partnerY (posicao do parceiro)
        // args[4] = resultado relX, args[5] = resultado relY
        int myX = (int) ((NumberTerm) args[0]).solve();
        int myY = (int) ((NumberTerm) args[1]).solve();
        int partnerX = (int) ((NumberTerm) args[2]).solve();
        int partnerY = (int) ((NumberTerm) args[3]).solve();

        int relX = partnerX - myX;
        int relY = partnerY - myY;

        return un.unifies(args[4], ASSyntax.createNumber(relX)) &&
               un.unifies(args[5], ASSyntax.createNumber(relY));
    }
}
```

**Uso**:

```prolog
// Eu em (10,10), parceiro em (11,10)
hive.ConnectCalculator(10, 10, 11, 10, RelX, RelY);
// RelX = 1, RelY = 0
```

---

### 6.3 Atualizar `assembler.asl` — Logica de connect e submit

**Arquivo**: `src/agt/assembler.asl`

O assembler precisa:
1. Receber sinal de que collectors chegaram ao meeting point
2. Posicionar-se adjacente ao collector
3. Coordenar o connect sincronizado
4. Apos montar o padrao, navegar ate goal zone
5. Executar submit

```prolog
{ include("common/communication.asl") }

// --- Reagir quando todos estao prontos ---

+agent_ready(SquadId, AgentName)
    <- .my_name(Me);
       get_my_squad(Me, MySquad);
       if (SquadId == MySquad) {
           all_ready(MySquad, Ready);
           if (Ready) {
               .print("[ASSEMBLER] Todos prontos no squad ", MySquad, "! Iniciando connect...");
               !start_connect_sequence(MySquad)
           }
       }.

// --- Sequencia de connect ---

+!start_connect_sequence(SquadId)
    : my_pos(MX, MY)
    <- get_squad_collectors(SquadId, Col1, Col2);
       // Pedir connect ao primeiro collector
       if (Col1 \== "none") {
           +waiting_connect_with(Col1);
           !navigate_to_collector_and_connect(Col1)
       }.

+!navigate_to_collector_and_connect(CollectorName)
    <- // Navegar ate o collector (ele esta no meeting point)
       .my_name(Me);
       get_my_squad(Me, MySquad);
       get_meeting_point(MySquad, MPX, MPY);
       +has_destination(MPX, MPY);
       .print("[ASSEMBLER] Navegando ate meeting point para connect com ", CollectorName).

// --- Executar connect quando adjacente ---
// O connect e executado via +step(N) handler quando:
// 1. waiting_connect_with(Partner) esta ativo
// 2. Ambos estao adjacentes
// Este handler sera adicionado ao collection.asl ou a um novo connect.asl

// --- Submit: navegar ate goal zone e submeter ---

+!do_submit(TaskName)
    : my_pos(MX, MY)
    <- get_nearest_goal_zone(MX, MY, GX, GY);
       if (GX \== -1) {
           +has_destination(GX, GY);
           +pending_submit(TaskName);
           .print("[ASSEMBLER] Navegando ate goal zone (", GX, ",", GY, ") para submit ", TaskName)
       } else {
           .print("[ASSEMBLER] Nenhuma goal zone conhecida!")
       }.

// --- Quando chegar a goal zone, submeter ---
// Detectado via step handler quando pending_submit e na goal zone

+step(N)
    : pending_submit(TaskName) & my_pos(MX, MY) & goalZone(0, 0)
    <- -pending_submit(TaskName);
       .concat("submit(", TaskName, ")", Act);
       action(Act);
       .print("[ASSEMBLER] Step ", N, ": submit(", TaskName, ")!").
```

**Nota**: A logica de sincronizacao do connect (ambos executam no mesmo step)
e o aspecto mais complexo. Sera implementada como um handler de `+step(N)` que
verifica se o parceiro esta adjacente e envia `connect(partner, relX, relY)`.

---

### 6.4 Implementar protocolo de connect via `+step(N)`

**Arquivo**: `src/agt/common/connect_protocol.asl` (novo modulo)

```prolog
// ============================================================
// connect_protocol.asl — Protocolo de connect sincronizado
// Incluir no assembler.asl e collector.asl
// ============================================================

// --- Assembler side: executar connect quando adjacente ao collector ---

+step(N)
    : ready_to_connect(Partner, PartnerX, PartnerY) & my_pos(MX, MY)
    <- hive.AdjacentDirection(MX, MY, PartnerX, PartnerY, Dir);
       if (Dir \== none) {
           // Estou adjacente ao parceiro — executar connect
           hive.ConnectCalculator(MX, MY, PartnerX, PartnerY, RelX, RelY);
           .concat("connect(", Partner, ",", RelX, ",", RelY, ")", Act);
           action(Act);
           +waiting_connect_result(Partner);
           -ready_to_connect(Partner, _, _);
           .print("[CONNECT] Step ", N, ": connect(", Partner, ",", RelX, ",", RelY, ")")
       } else {
           // Navegar em direcao ao parceiro
           hive.DirectionCalculator(MX, MY, PartnerX, PartnerY, MoveDir);
           .concat("move(", MoveDir, ")", Act);
           action(Act)
       }.

// --- Collector side: executar connect quando recebe pedido ---

+step(N)
    : pending_connect(AssemblerName, AsmX, AsmY, TargetStep) & my_pos(MX, MY)
      & N >= TargetStep
    <- hive.AdjacentDirection(MX, MY, AsmX, AsmY, Dir);
       if (Dir \== none) {
           hive.ConnectCalculator(MX, MY, AsmX, AsmY, RelX, RelY);
           .concat("connect(", AssemblerName, ",", RelX, ",", RelY, ")", Act);
           action(Act);
           -pending_connect(AssemblerName, _, _, _);
           .print("[CONNECT] Step ", N, ": connect(", AssemblerName, ",", RelX, ",", RelY, ")")
       } else {
           // Mover em direcao ao assembler
           hive.DirectionCalculator(MX, MY, AsmX, AsmY, MoveDir);
           .concat("move(", MoveDir, ")", Act);
           action(Act)
       }.

// --- Resultado do connect ---

+step(N)
    : waiting_connect_result(Partner) & lastActionResult(success)
    <- -waiting_connect_result(Partner);
       .print("[CONNECT] Step ", N, ": Connect com ", Partner, " bem-sucedido!").

+step(N)
    : waiting_connect_result(Partner) & lastActionResult(R) & R \== success
    <- -waiting_connect_result(Partner);
       .print("[CONNECT] Step ", N, ": Connect falhou: ", R, ". Retentando...").
```

**Ponto critico — Sincronizacao**: Ambos os agentes devem executar `connect` no MESMO step.
A estrategia e:
1. O assembler detecta que o collector esta adjacente
2. O assembler adiciona `ready_to_connect(Partner, PX, PY)` e envia `connect_request`
3. O collector recebe e adiciona `pending_connect`
4. No proximo step, ambos verificam adjacencia e executam connect simultaneamente

**Latencia**: A comunicacao via `.send()` e quase instantanea em JaCaMo (mesmo processo).
Portanto, enviar a mensagem e executar connect no mesmo step pode ser viavel.

---

### 6.5 Implementar `PatternMatcher.java` — Verificar padrao de blocos

**Arquivo**: `src/java/hive/PatternMatcher.java`

Verifica se os blocos attached ao agente correspondem ao padrao de uma task.

```java
package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.bb.BeliefBase;

import java.util.*;

public class PatternMatcher extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        // args[0] = lista de reqs: [req(rx,ry,type), ...]
        // args[1] = resultado: true/false
        ListTerm reqs = (ListTerm) args[0];
        BeliefBase bb = ts.getAg().getBB();

        for (Term req : reqs) {
            Structure r = (Structure) req;
            int rx = (int) ((NumberTerm) r.getTerm(0)).solve();
            int ry = (int) ((NumberTerm) r.getTerm(1)).solve();
            String type = r.getTerm(2).toString();

            // Verificar se existe my_attached(rx, ry) com tipo correto
            // Simplificacao: verificar apenas presenca de my_attached(rx, ry)
            Literal check = ASSyntax.createLiteral("my_attached",
                ASSyntax.createNumber(rx), ASSyntax.createNumber(ry));
            if (bb.contains(check) == null) {
                return un.unifies(args[1], Literal.parseLiteral("false"));
            }
        }
        return un.unifies(args[1], Literal.parseLiteral("true"));
    }
}
```

**Nota**: Versao simplificada — verifica presenca de attached nas posicoes corretas,
sem verificar tipo do bloco. Uma versao mais sofisticada cruzaria com `thing(X, Y, block, Type)`.

---

### 6.6 Testar connect com tarefa de 2 blocos

**Cenario de teste**:

1. Iniciar MASSIM com TestConfig (tasks com size [1,3])
2. Iniciar JaCaMo com 15 agentes
3. Observar:
   - Leader detecta task com 2 blocos
   - Leader delega: collector1 → b0, collector2 → b1
   - Collectors coletam blocos e vao ao meeting point
   - Assembler vai ao meeting point
   - Connect executado: assembler fica com 2 blocos
   - Assembler vai a goal zone
   - Submit executado

**Log esperado**:

```
[LEADER] Task task0 disponivel. Score=20.0. Fazendo bid...
[LEADER] Ganhamos task task0! Delegando coleta...
[LEADER] Meeting point definido em (12,8)
[LEADER] connectionA4 designado para coletar b0
[COLLECTOR] Recebi ordem: coletar b0
[COL] Bloco b0 attached com sucesso!
[COLLECTOR] Indo ao meeting point (12,8)
[COLLECTOR] Cheguei ao meeting point. Pronto para connect.
[ASSEMBLER] Todos prontos no squad1! Iniciando connect...
[CONNECT] connect(agentA4, 1, 0) bem-sucedido!
[ASSEMBLER] Navegando ate goal zone para submit task0
[ASSEMBLER] submit(task0)!
```

**Troubleshooting**:

| Problema | Causa provavel | Solucao |
|----------|---------------|---------|
| Connect retorna `failed_partner` | Agentes nao executaram no mesmo step | Melhorar sincronizacao via comunicacao |
| Connect retorna `failed_target` | Posicao relativa errada | Verificar ConnectCalculator |
| Submit retorna `failed` | Agente nao esta em goal zone OU padrao errado | Verificar posicao e PatternMatcher |
| Submit retorna `failed_target` | Blocos attached nao correspondem ao padrao | Verificar rotacao e posicoes |
| Agents nao se encontram | Meeting point mal definido | Verificar get_nearest_goal_zone |

---

### 6.7 Resumo de arquivos — Fase 6

| Arquivo | Acao | Descricao |
|---------|------|-----------|
| `src/agt/common/communication.asl` | **CRIAR** | Mensagens de sincronizacao para connect |
| `src/agt/common/connect_protocol.asl` | **CRIAR** | Protocolo connect via +step(N) |
| `src/java/hive/ConnectCalculator.java` | **CRIAR** | Calcula parametros relX, relY para connect |
| `src/java/hive/PatternMatcher.java` | **CRIAR** | Verifica se blocos correspondem ao padrao |
| `src/agt/assembler.asl` | MODIFICAR | Logica de connect + submit |
| `src/agt/collector.asl` | MODIFICAR | Reagir a connect_request |

---

### Ordem de execucao — Fase 6

```
6.1  Criar communication.asl
6.2  Criar ConnectCalculator.java
6.3  Criar PatternMatcher.java
6.4  Criar connect_protocol.asl
6.5  Atualizar assembler.asl (include communication + connect_protocol + logica submit)
6.6  Atualizar collector.asl (include communication + connect_protocol)
6.7  Compilar e testar connect com 2 agentes (1 collector + 1 assembler)
6.8  Testar submit em goal zone
6.9  Testar pipeline completo: task → leilao → coleta → connect → submit
```

---
---

## Dependencias entre Fases 5 e 6

```
Fase 4 (CONCLUIDA)
  │
  ├── Fase 5 (Coordenacao e Leilao)
  │     5.1-5.2  TaskBoard + SquadCoordinator (artefatos Java)
  │     5.3-5.5  perception + leader + collector (ASL updates)
  │     5.6-5.8  testes de pipeline
  │
  └── Fase 6 (Montagem e Connect) ──── depende de Fase 5
        6.1-6.4  communication + connect_protocol + internal actions
        6.5-6.6  assembler + collector (ASL updates)
        6.7-6.9  testes de connect + submit
```

**Recomendacao**: Executar Fase 5 completamente (testar leilao e delegacao),
depois Fase 6 (connect e submit). A Fase 6 depende criticamente de collectors
chegando ao meeting point com blocos corretos (output da Fase 5).

---

## Metricas de aceite — Fases 5+6 combinadas

| Metrica | Alvo |
|---------|------|
| Tasks detectadas e registradas no TaskBoard | >= 3 por simulacao |
| Leiloes resolvidos (leader ganha e delega) | >= 2 |
| Collectors coletam tipo designado (nao oportunista) | >= 2 |
| Collectors chegam ao meeting point com bloco | >= 2 |
| Connects bem-sucedidos | >= 1 |
| Submits bem-sucedidos em goal zone | >= 1 |
| Pontuacao > 0 ao final da simulacao | Sim |
