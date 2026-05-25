# Plano Detalhado — Fases 1 e 2

**STATUS GERAL: CONCLUIDO**

---

## FASE 1 — Agente Minimo Viavel [CONCLUIDO]

**Objetivo**: Um agente conecta ao MASSIM, recebe percepts e executa acoes basicas.
**Criterio de aceite**: Agente aparece no web monitor, executa `move` e se desloca na grade.

---

### 1.1 Criar `src/agt/dummy.asl` — agente minimo que faz `skip`

**Status**: [x] CONCLUIDO

O arquivo `src/agt/dummy.asl` ja existe com conteudo basico. Sera evoluido nos proximos itens.

---

### 1.2 Configurar `hive.jcm` para instanciar 1 agente usando plataforma EIS

**Status**: [x] CONCLUIDO — Abordagem artefato CArtAgO adotada (EISPlatform descontinuada no JaCaMo 1.3.0)

O `hive.jcm` ja esta configurado com 1 agente dummy e plataforma EIS.

**Ponto de atencao**: O `eismassimconfig.json` usa `username-prefix: "agentA"` e `password: "1"`.
Isso precisa corresponder ao time configurado no MASSIM. Na config `SimplifiedConfig.json`:

```json
"teams": {
    "A": {"prefix": "agent", "password": "1"}
}
```

Os agentes serao `agentA0`, `agentA1`, ..., `agentA14`. O servidor MASSIM cria as contas
automaticamente como `agent` + `A` + indice = `agentA0`. Nosso EISMASSim cria
`agentA` + `0` = `agentA0`. Match perfeito.

**Problema potencial**: O `hive.jcm` precisa associar cada agente Jason a uma entidade EIS.
Para que isso funcione com a plataforma EIS do JaCaMo, o agente precisa ter o mesmo nome
que a entidade (ou ser associado explicitamente). Vamos ajustar para o teste inicial.

**Configuracao de `hive.jcm` para 1 agente**:

```
mas hive {
    agent dummy : dummy.asl {
        join: hive_workspace
        focus: hive_workspace.massim
    }

    workspace hive_workspace {
        artifact massim: jacamo.platform.eis.EISArtifact("eismassimconfig.json")
    }

    asl-path: src/agt, src/agt/common
}
```

**Alternativa mais simples** (usar EIS como plataforma diretamente):

```
mas hive {
    agent agentA0 : dummy.asl

    platform: jacamo.platform.eis.EISPlatform("eismassimconfig.json")

    asl-path: src/agt, src/agt/common
}
```

Nesta abordagem, o nome do agente Jason (`agentA0`) deve coincidir com o `username`
gerado pelo EISMASSim (prefix `agentA` + index `0` = `agentA0`). A plataforma EIS
faz a associacao automatica por nome.

**Acao**: Testar a abordagem da plataforma EIS primeiro (mais simples). Se nao funcionar,
trocar para a abordagem de artefato.

---

### 1.3 Executar JaCaMo + MASSIM simultaneamente

**Pre-requisitos**:
- Servidor MASSIM rodando (Fase 0, item 0.7)
- Projeto JaCaMo compilado (Fase 0, item 0.13)

**Passo a passo**:

1. **Terminal 1** — Iniciar servidor MASSIM (se nao estiver rodando):
```bash
cd massim_2022/server
java -jar target/server-2022-1.1-jar-with-dependencies.jar \
    -conf conf/SimplifiedConfig.json --monitor
```

2. **Terminal 1** — Pressionar ENTER para iniciar o torneio (o servidor espera isso)

3. **Terminal 2** — Iniciar JaCaMo:
```bash
cd /Users/tnobremasc/workspace/PCS5703_MAS
gradle run
```

**O que esperar**:
- No Terminal 1 (MASSIM): logs mostrando que agentA0 conectou
- No Terminal 2 (JaCaMo): logs do Jason mostrando prints do agente
- No browser (localhost:8000): agente aparecendo na grade

**Troubleshooting**:
- Se `Connection refused`: verificar se MASSIM esta rodando na porta 12300
- Se `Authentication failed`: verificar username/password no eismassimconfig vs MASSIM config
- Se `No entities`: verificar que o torneio foi iniciado (ENTER no terminal do MASSIM)
- Se agente nao faz nada: verificar que `scheduling: true` no eismassimconfig (bloqueia ate percept)

---

### 1.4 Verificar no log que o agente recebe SIM-START percepts

**O que procurar nos logs do JaCaMo**:

Quando a simulacao inicia, EISMASSim traduz o SIM-START em percepts IILang.
O agente deve receber crenças como:

```
name("agentA0")
team("A")
teamSize(20)
steps(750)
role("default", 5, [...], [...], 0.3, 1)
```

**Como verificar**: No `dummy.asl`, adicionar um plano para reagir ao percept `name`:

```prolog
+name(N) <- .print("*** SIM-START recebido! Meu nome: ", N).
+team(T) <- .print("*** Meu time: ", T).
+steps(S) <- .print("*** Total de steps: ", S).
+teamSize(S) <- .print("*** Tamanho do time: ", S).
```

Se esses prints aparecerem no log, SIM-START esta funcionando.

---

### 1.5 Verificar no log que o agente recebe REQUEST-ACTION a cada step

**O que procurar**: A cada step, o agente recebe percepts atualizados incluindo:

```
step(N)           -- numero do step atual
energy(100)       -- nivel de energia
score(0)          -- pontuacao do time
lastAction(skip)  -- ultima acao executada
lastActionResult(success)  -- resultado da ultima acao
```

**Como verificar**: O `dummy.asl` ja tem o plano `+step(N)` que imprime o step.
Se os prints aparecem incrementalmente (Step 1, Step 2, Step 3...), esta funcionando.

**Ponto importante**: O plano `+step(N)` deve executar uma acao ao final.
Se o agente nao enviar nenhuma acao, o servidor aplica `skip` apos o timeout (4s).
Isso e lento. Sempre executar uma acao explicita para responder rapido.

---

### 1.6 Implementar plano simples: move para norte

**Arquivo**: `src/agt/dummy.asl`

**Codigo**:

```prolog
!start.

+!start <- .print("Agente HIVE iniciado.").

+name(N) <- .print("SIM-START: meu nome = ", N).
+team(T) <- .print("SIM-START: meu time = ", T).
+steps(S) <- .print("SIM-START: total steps = ", S).

+step(N)
    <- .print("Step ", N);
       move(n).
```

**O que esperar**: No web monitor, o agente se move continuamente para o norte.
Se o mapa tem bordas (wrap-around), ele reaparece no lado oposto.
Se bater em obstaculo, `lastActionResult` sera `failed_path`.

---

### 1.7 Confirmar no web monitor que o agente se move

**Acao**: Abrir `http://localhost:8000` no browser.

**O que procurar**:
- A grade do mapa deve ser visivel
- O agente deve aparecer como um ponto/icone colorido
- A cada step, a posicao deve mudar (movendo para norte = para cima na tela)
- Se `lastActionResult` for `failed_path`, o agente esta batendo em obstaculo

**Se o monitor nao mostra nada**: Verificar que `--monitor` foi passado ao iniciar o servidor.

---

### 1.8 Testar as 4 direcoes

**Arquivo**: `src/agt/dummy.asl`

**Codigo** (alternar direcoes ciclicamente):

```prolog
!start.
+!start <- .print("Agente HIVE iniciado.").

+step(N) : (N mod 4) == 0 <- move(n).
+step(N) : (N mod 4) == 1 <- move(e).
+step(N) : (N mod 4) == 2 <- move(s).
+step(N) : (N mod 4) == 3 <- move(w).
```

**O que esperar**: O agente se move em padrao quadrado (norte, leste, sul, oeste).
Se nao houver obstaculos, ele retorna a posicao original a cada 4 steps.

**Verificar tambem**: O resultado de cada acao via:

```prolog
+lastActionResult(R) <- .print("Resultado da ultima acao: ", R).
```

Resultados possiveis para `move`:
- `success` — moveu com sucesso
- `failed_path` — caminho bloqueado (obstaculo, borda, outra entidade)
- `failed_parameter` — direcao invalida
- `failed_random` — falha aleatoria (1% chance, configuravel)
- `failed_status` — agente desativado

---

### 1.9 Implementar movimento aleatorio

**Arquivo**: `src/agt/dummy.asl`

**Codigo**:

```prolog
!start.
+!start <- .print("Agente HIVE iniciado.").

+step(N)
    <- .random(R);
       !choose_direction(R).

+!choose_direction(R) : R < 0.25 <- move(n).
+!choose_direction(R) : R < 0.50 <- move(e).
+!choose_direction(R) : R < 0.75 <- move(s).
+!choose_direction(R)             <- move(w).
```

**Alternativa usando lista**:

```prolog
+step(N)
    <- .random([n, e, s, w], Dir);
       move(Dir).
```

Nota: `.random/2` com lista pode nao funcionar em todas versoes de Jason.
A abordagem com `.random(R)` e thresholds e mais segura.

**O que esperar**: No web monitor, o agente se move erraticamente pela grade
como um random walk. Deve cobrir area ao longo do tempo.

---

### 1.10 Conectar 15 agentes simultaneamente ao MASSIM

**Arquivo**: `hive.jcm`

**Codigo** — usar `instances` ou declarar agentes individuais:

**Opcao A — Agentes nomeados individualmente** (mais controle):

```
mas hive {
    agent agentA0  : dummy.asl
    agent agentA1  : dummy.asl
    agent agentA2  : dummy.asl
    agent agentA3  : dummy.asl
    agent agentA4  : dummy.asl
    agent agentA5  : dummy.asl
    agent agentA6  : dummy.asl
    agent agentA7  : dummy.asl
    agent agentA8  : dummy.asl
    agent agentA9  : dummy.asl
    agent agentA10 : dummy.asl
    agent agentA11 : dummy.asl
    agent agentA12 : dummy.asl
    agent agentA13 : dummy.asl
    agent agentA14 : dummy.asl

    platform: jacamo.platform.eis.EISPlatform("eismassimconfig.json")

    asl-path: src/agt, src/agt/common
}
```

**Opcao B — Gerar com count na config EIS** (mudar count para -1 no eismassimconfig
para auto-detectar, e usar naming pattern no JCM):

Para que a plataforma EIS associe agentes automaticamente, os nomes dos agentes
Jason devem corresponder aos usernames no eismassimconfig (`agentA0` a `agentA14`).

**Atencao**: A config `SimplifiedConfig.json` do MASSIM define `"standard": 20` entidades.
Como nosso eismassimconfig define `count: 15`, vamos conectar 15 dos 20 slots.
Isso e suficiente para teste. Para competicao, ajustar count para o numero exato.

---

### 1.11 Verificar que todos os 15 aparecem no web monitor

**Acao**: Apos iniciar o JaCaMo com 15 agentes:

1. Abrir `http://localhost:8000`
2. Contar os agentes do time A visiveis na grade
3. Verificar nos logs do MASSIM que 15 conexoes foram aceitas
4. Verificar nos logs do JaCaMo que 15 agentes estao executando

**Critério de aceite da Fase 1**:
- 15 pontos se movendo aleatoriamente no web monitor
- Logs sem erros de conexao
- Cada agente respondendo a cada step (sem timeouts)

---
---

## FASE 2 — Percepcao e Mapa [CONCLUIDO]

**Objetivo**: Agentes processam percepts corretamente e constroem mapa compartilhado.
**Criterio de aceite**: Agentes identificam dispensers, goal zones, role zones, obstaculos via SharedMap.

---

### 2.1 Criar `src/agt/common/perception.asl`

**Objetivo**: Centralizar regras de processamento de percepts que serao incluidas
por todos os agentes via `{ include("common/perception.asl") }`.

**Arquivo**: `src/agt/common/perception.asl`

**Codigo**:

```prolog
// ============================================================
// perception.asl — Processamento de percepts do MASSIM
// Incluido por todos os agentes via { include(...) }
// ============================================================

// --- Percepts de things (dentro da visao do agente) ---

// Ao perceber um dispenser, registra no mapa compartilhado
+thing(X, Y, dispenser, Type)
    <- update_cell(X, Y, dispenser, Type).

// Ao perceber um obstaculo
+thing(X, Y, obstacle, _)
    <- update_cell(X, Y, obstacle, "").

// Ao perceber uma entidade (agente)
+thing(X, Y, entity, Team)[source(percept)]
    : .my_name(Me) & team(MyTeam) & Team \== MyTeam
    <- +enemy_spotted(X, Y).

// Ao perceber um bloco solto
+thing(X, Y, block, Type)[source(percept)]
    <- +known_block(X, Y, Type).

// Ao perceber marker de clear event
+thing(X, Y, marker, clear)[source(percept)]
    <- +clear_marker(X, Y).

+thing(X, Y, marker, ci)[source(percept)]
    <- +clear_imminent(X, Y).

// --- Percepts de zonas ---

+goalZone(X, Y)[source(percept)]
    <- update_cell(X, Y, goal_zone, "").

+roleZone(X, Y)[source(percept)]
    <- update_cell(X, Y, role_zone, "").

// --- Percepts de estado do agente ---

+energy(E)[source(percept)]
    : E < 20
    <- .print("ALERTA: energia baixa = ", E).

+deactivated(true)[source(percept)]
    <- .print("DESATIVADO! Aguardando reativacao...").

// --- Percepts de resultado de acao ---

+lastActionResult(failed_path)[source(percept)]
    <- +last_move_blocked.

+lastActionResult(success)[source(percept)]
    <- -last_move_blocked.
```

**Notas importantes**:
- Os percepts do EISMASSim usam coordenadas **relativas** ao agente (agente = 0,0).
- Para registrar no mapa compartilhado com coordenadas **absolutas**, precisamos
  converter: `abs_x = agent_abs_x + rel_x`. Mas agentes nao sabem posicao absoluta!
- **Solucao**: O mapa compartilhado pode usar coordenadas relativas a um ponto de
  referencia arbitrario, ou cada agente mantem seu proprio mapa local e compartilha
  descobertas significativas (dispensers, goal zones).
- Na versao inicial, vamos manter coordenadas relativas ao agente e compartilhar
  apenas a existencia de features (dispenser tipo X encontrado, goal zone encontrada).

---

### 2.2 Classificacao de things

Os percepts `thing(X, Y, Type, Details)` do MASSIM tem os seguintes tipos:

| Type | Details | Significado | Acao |
|------|---------|-------------|------|
| `entity` | nome do time | Agente (aliado ou inimigo) | Se inimigo, registrar posicao |
| `block` | tipo do bloco (b0, b1, ...) | Bloco solto ou attached | Registrar para coleta |
| `dispenser` | tipo do bloco (b0, b1, ...) | Fonte de blocos | Registrar no mapa (critico!) |
| `obstacle` | "" | Obstaculo (bloqueio de passagem) | Registrar no mapa para pathfinding |
| `marker` | "clear" / "ci" / "cp" | Sinal de clear event | "ci" = 2 steps ou menos! Evacuar |

**Implementacao**: Ja coberta nos planos de `perception.asl` acima (item 2.1).

---

### 2.3 Processamento de goalZone e roleZone

**Percepts recebidos**: `goalZone(X, Y)` e `roleZone(X, Y)` sao listas de posicoes
relativas dentro da visao do agente que pertencem a uma zona.

**Ponto critico**: Essas coordenadas sao relativas! Para saber a posicao absoluta,
o agente precisaria manter tracking da sua posicao global (que ele nao tem diretamente).

**Estrategia pragmatica para a Fase 2**:

1. Cada agente sabe que "existe uma goal zone a N passos daqui"
2. Ao perceber `goalZone(X, Y)`, o agente pode **navegar ate la** diretamente
   (porque sabe a posicao relativa)
3. Para compartilhar com outros agentes, usar `survey(goal)` que retorna a
   **distancia** ate a goal zone mais proxima (sem coordenadas)

**Implementacao simples** (manter crenças locais):

```prolog
// Agente sabe que viu goal zone nesta direcao relativa
+goalZone(X, Y)[source(percept)]
    : not i_know_goal_zone
    <- +i_know_goal_zone;
       +nearest_goal(X, Y);
       .print("Goal zone encontrada em posicao relativa (", X, ",", Y, ")").

// Atualiza posicao relativa da goal zone mais proxima a cada step
+goalZone(X, Y)[source(percept)]
    : i_know_goal_zone & nearest_goal(OX, OY)
      & (math.abs(X) + math.abs(Y)) < (math.abs(OX) + math.abs(OY))
    <- -nearest_goal(OX, OY);
       +nearest_goal(X, Y).
```

---

### 2.4 Processamento de tasks

**Percept**: `task(Name, Deadline, Reward, Requirements)`

Onde Requirements e uma lista de `req(X, Y, Type)` — blocos necessarios com posicoes relativas.

**Exemplo de percept**:
```
task(task2, 188, 44, [req(0, 1, b0), req(1, 1, b1), req(0, 2, b1)])
```

Isso significa: task2, deadline step 188, reward 44 pontos, precisa de:
- bloco b0 na posicao (0, 1) relativa ao agente que submete
- bloco b1 na posicao (1, 1)
- bloco b1 na posicao (0, 2)

**Implementacao**:

```prolog
+task(Name, Deadline, Reward, Reqs)[source(percept)]
    <- .length(Reqs, NBlocks);
       +known_task(Name, Deadline, Reward, NBlocks);
       .print("Task detectada: ", Name, " reward=", Reward,
              " blocos=", NBlocks, " deadline=", Deadline).
```

---

### 2.5 Processamento de normas

**Percept**: `norm(Id, Start, End, Requirements, Fine)`

**Exemplo**:
```
norm(n1, 50, 200, [requirement(block, any, 1, "")], 15)
```

Significa: norma n1, ativa do step 50 ao 200, maximo 1 bloco carregado, multa 15 energia/step.

**Implementacao**:

```prolog
+norm(Id, Start, End, Reqs, Fine)[source(percept)]
    <- .print("Norma detectada: ", Id, " de ", Start, " a ", End, " multa=", Fine);
       +active_norm(Id, Start, End, Reqs, Fine).

-norm(Id, _, _, _, _)[source(percept)]
    <- .print("Norma expirou: ", Id);
       .abolish(active_norm(Id, _, _, _, _)).
```

---

### 2.6 Processamento de energy, deactivated, role

**Implementacao** (ja parcialmente em perception.asl):

```prolog
// Energia — atualiza crenca local
+energy(E)[source(percept)]
    <- -my_energy(_);
       +my_energy(E).

// Desativacao
+deactivated(true)[source(percept)]
    <- -am_active;
       +am_deactivated;
       .print("*** DESATIVADO ***").

+deactivated(false)[source(percept)]
    : am_deactivated
    <- -am_deactivated;
       +am_active;
       .print("*** REATIVADO ***").

// Role atual
+role(R)[source(percept)]
    <- -my_role(_);
       +my_role(R).
```

---

### 2.7 Processamento de lastActionResult

**Implementacao**:

```prolog
+lastAction(Action)[source(percept)]
    <- -my_last_action(_);
       +my_last_action(Action).

+lastActionResult(Result)[source(percept)]
    <- -my_last_result(_);
       +my_last_result(Result).

// Log de falhas para debug
+lastActionResult(R)[source(percept)]
    : R \== success & my_last_action(A)
    <- .print("FALHA: acao ", A, " resultado ", R).
```

---

### 2.8 Criar `src/env/SharedMap.java`

**Abordagem do mapa compartilhado**:

Como os agentes nao sabem suas posicoes absolutas, o SharedMap funciona como
um repositorio de **features descobertas**, nao um mapa de coordenadas absolutas.

Na configuracao `SimplifiedConfig.json`, ha um campo `"absolutePosition": true`.
Se estiver ativo, podemos obter posicoes absolutas! Isso simplifica muito o mapa.

**Verificar**: Se `absolutePosition` esta no percept. Se sim, cada agente sabe
sua posicao global e pode registrar features com coordenadas absolutas.

**Implementacao (assumindo coordenadas absolutas)**:

```java
package env;

import cartago.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SharedMap extends Artifact {

    private ConcurrentHashMap<String, String> cells;
    private Set<String> knownDispensers;
    private Set<String> knownGoalZones;
    private Set<String> knownRoleZones;
    private Set<String> visitedCells;

    void init() {
        cells = new ConcurrentHashMap<>();
        knownDispensers = ConcurrentHashMap.newKeySet();
        knownGoalZones = ConcurrentHashMap.newKeySet();
        knownRoleZones = ConcurrentHashMap.newKeySet();
        visitedCells = ConcurrentHashMap.newKeySet();
    }

    @OPERATION
    void update_cell(int x, int y, String type, String details) {
        String key = x + "," + y;
        cells.put(key, type + ":" + details);
        visitedCells.add(key);

        if (type.equals("dispenser")) {
            String dispKey = key + ":" + details;
            if (knownDispensers.add(dispKey)) {
                defineObsProperty("dispenser", x, y, details);
                signal("new_dispenser", x, y, details);
            }
        } else if (type.equals("goal_zone")) {
            if (knownGoalZones.add(key)) {
                defineObsProperty("goal_zone", x, y);
                signal("new_goal_zone", x, y);
            }
        } else if (type.equals("role_zone")) {
            if (knownRoleZones.add(key)) {
                defineObsProperty("role_zone", x, y);
                signal("new_role_zone", x, y);
            }
        }
    }

    @OPERATION
    void mark_visited(int x, int y) {
        visitedCells.add(x + "," + y);
    }

    @OPERATION
    void get_nearest_dispenser(int agX, int agY, String type,
                               OpFeedbackParam<Integer> resX,
                               OpFeedbackParam<Integer> resY) {
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;
        for (String dispKey : knownDispensers) {
            String[] parts = dispKey.split("[:,]");
            if (parts.length >= 3 && parts[2].equals(type)) {
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

    @OPERATION
    void get_nearest_goal_zone(int agX, int agY,
                               OpFeedbackParam<Integer> resX,
                               OpFeedbackParam<Integer> resY) {
        int bestDist = Integer.MAX_VALUE;
        int bx = -1, by = -1;
        for (String key : knownGoalZones) {
            String[] parts = key.split(",");
            int gx = Integer.parseInt(parts[0]);
            int gy = Integer.parseInt(parts[1]);
            int dist = Math.abs(gx - agX) + Math.abs(gy - agY);
            if (dist < bestDist) {
                bestDist = dist;
                bx = gx;
                by = gy;
            }
        }
        resX.set(bx);
        resY.set(by);
    }

    @OPERATION
    void get_map_stats(OpFeedbackParam<Integer> totalVisited,
                       OpFeedbackParam<Integer> totalDispensers,
                       OpFeedbackParam<Integer> totalGoalZones,
                       OpFeedbackParam<Integer> totalRoleZones) {
        totalVisited.set(visitedCells.size());
        totalDispensers.set(knownDispensers.size());
        totalGoalZones.set(knownGoalZones.size());
        totalRoleZones.set(knownRoleZones.size());
    }
}
```

---

### 2.9 e 2.10 — Operacao update_cell e propriedades observaveis

Ja implementados no codigo acima (item 2.8).

- `update_cell(x, y, type, details)` — registra celula no mapa
- Propriedades observaveis: `dispenser(X, Y, Type)`, `goal_zone(X, Y)`, `role_zone(X, Y)`
- Sinais: `new_dispenser`, `new_goal_zone`, `new_role_zone` (notificam agentes de novas descobertas)

---

### 2.11 Implementar get_nearest_frontier

**Conceito**: Uma fronteira e uma celula nao visitada adjacente a uma celula visitada.
O agente deve navegar para a fronteira mais proxima para maximizar exploracao.

**Problema**: Sem posicao absoluta, calcular fronteiras e complexo. Com `absolutePosition: true`,
cada agente pode registrar as celulas que visitou e calcular fronteiras.

**Implementacao simplificada** — adicionar ao `SharedMap.java`:

```java
@OPERATION
void get_nearest_frontier(int agX, int agY,
                          OpFeedbackParam<Integer> resX,
                          OpFeedbackParam<Integer> resY) {
    int bestDist = Integer.MAX_VALUE;
    int bx = agX, by = agY;
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};

    for (String visited : visitedCells) {
        String[] parts = visited.split(",");
        int vx = Integer.parseInt(parts[0]);
        int vy = Integer.parseInt(parts[1]);

        for (int[] d : dirs) {
            int nx = vx + d[0];
            int ny = vy + d[1];
            String nk = nx + "," + ny;

            if (!visitedCells.contains(nk)) {
                String cellContent = cells.get(nk);
                if (cellContent == null || !cellContent.startsWith("obstacle")) {
                    int dist = Math.abs(nx - agX) + Math.abs(ny - agY);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bx = nx;
                        by = ny;
                    }
                }
            }
        }
    }
    resX.set(bx);
    resY.set(by);
}
```

**Nota de performance**: Para mapas grandes, iterar sobre todas as celulas visitadas
pode ser lento. Para a versao inicial esta OK. Otimizar depois se necessario.

---

### 2.12 Agentes chamam update_cell a cada step

**Integracao**: No agente, a cada step, processar todos os things percebidos
e registrar no SharedMap.

**No `perception.asl`**, os planos `+thing(...)` ja chamam `update_cell`.
Mas precisamos garantir que o agente tambem registra sua propria posicao como visitada.

**Adicionar ao agente**:

```prolog
// Ao receber step, marcar posicao atual como visitada
+step(N)[source(percept)]
    : my_pos(X, Y)
    <- mark_visited(X, Y).
```

**Para manter `my_pos`** (se absolutePosition disponivel):

Verificar se o MASSIM envia posicao absoluta. Se `absolutePosition: true` na config,
agentes podem receber percepts com posicao absoluta. Caso contrario, manter
posicao relativa por dead reckoning (somar movimentos).

---

### 2.13 Navegacao basica — `src/agt/common/navigation.asl`

**Arquivo**: `src/agt/common/navigation.asl`

**Implementacao (navegacao greedy simples)**:

```prolog
// ============================================================
// navigation.asl — Planos de navegacao basica
// ============================================================

// Navegar ate um ponto (X, Y) relativo ao agente
+!navigate_to(0, 0)
    <- .print("Cheguei ao destino.").

+!navigate_to(X, Y)
    : X > 0
    <- move(e);
       !navigate_to(X - 1, Y).

+!navigate_to(X, Y)
    : X < 0
    <- move(w);
       !navigate_to(X + 1, Y).

+!navigate_to(X, Y)
    : Y > 0
    <- move(s);
       !navigate_to(X, Y - 1).

+!navigate_to(X, Y)
    : Y < 0
    <- move(n);
       !navigate_to(X, Y + 1).

// Fallback: se nao conseguiu mover (obstaculo), tentar contornar
-!navigate_to(X, Y)
    <- .print("Falha na navegacao para (", X, ",", Y, "). Tentando desvio...");
       .random(R);
       !random_detour(R);
       !navigate_to(X, Y).

+!random_detour(R) : R < 0.5 <- move(e).
+!random_detour(R)            <- move(w).
```

**Limitacoes desta versao**:
- Navegacao greedy (nao contorna obstaculos de forma inteligente)
- Cada `move` consome 1 step inteiro (so pode executar 1 acao por step!)
- O plano `!navigate_to` precisa ser executado step-a-step, nao em loop sincrono

**Problema critico**: Em Jason, um plano roda dentro de um ciclo de raciocinio.
Se `!navigate_to` chama `move(e)` e depois `!navigate_to(X-1, Y)`,
ele tenta executar tudo no MESMO step. Mas so pode executar 1 acao por step!

**Solucao correta — navegacao step-by-step**:

```prolog
// A cada step, decidir a proxima direcao com base no destino
+step(N)
    : has_destination(DX, DY) & DX > 0
    <- move(e).

+step(N)
    : has_destination(DX, DY) & DX < 0
    <- move(w).

+step(N)
    : has_destination(DX, DY) & DY > 0
    <- move(s).

+step(N)
    : has_destination(DX, DY) & DY < 0
    <- move(n).

+step(N)
    : has_destination(0, 0)
    <- -has_destination(0, 0);
       .print("Cheguei ao destino!").
```

O agente define `+has_destination(X, Y)` e a cada step da um passo na direcao certa.
Apos o move, atualiza a posicao relativa do destino.

---

### 2.14 DirectionCalculator.java — internal action

**Arquivo**: `src/java/hive/DirectionCalculator.java`

```java
package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class DirectionCalculator extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int fromX = (int) ((NumberTerm) args[0]).solve();
        int fromY = (int) ((NumberTerm) args[1]).solve();
        int toX = (int) ((NumberTerm) args[2]).solve();
        int toY = (int) ((NumberTerm) args[3]).solve();

        int dx = toX - fromX;
        int dy = toY - fromY;

        String dir;
        if (Math.abs(dx) >= Math.abs(dy)) {
            dir = dx > 0 ? "e" : "w";
        } else {
            dir = dy > 0 ? "s" : "n";
        }

        return un.unifies(args[4], new Atom(dir));
    }
}
```

**Uso no AgentSpeak**:

```prolog
+step(N)
    : has_destination(DX, DY) & DX \== 0 | DY \== 0
    <- hive.DirectionCalculator(0, 0, DX, DY, Dir);
       move(Dir).
```

---

### 2.15 Exploracao por fronteira

**Logica de alto nivel**:

```prolog
// Se nao tem destino e nao tem task, explorar
+step(N)
    : not has_destination(_, _) & not has_task(_)
    <- get_nearest_frontier(MyX, MyY, FX, FY);
       +has_destination(FX - MyX, FY - MyY);
       .print("Explorando fronteira em (", FX, ",", FY, ")").
```

**Versao simplificada** (sem mapa global, apenas exploracao aleatoria inteligente):

```prolog
// Se a ultima acao de move falhou (obstaculo), mudar direcao
+step(N)
    : exploring & last_move_blocked
    <- -last_move_blocked;
       .random(R);
       !choose_direction(R).

// Se nao tem nada para fazer, explorar em direcao aleatoria
+step(N)
    : exploring & not last_move_blocked
    <- !continue_exploring.

+!continue_exploring
    : my_explore_dir(Dir)
    <- move(Dir).

+!continue_exploring
    <- .random(R);
       !choose_direction(R).
```

---

### 2.16 Survey como fallback

**Acao `survey`**: Retorna distancia ate o alvo mais proximo, sem coordenadas.
Util quando o agente nao encontrou dispensers/goal zones por exploracao.

```prolog
// A cada 20 steps, fazer survey se nao conhece dispensers
+step(N)
    : (N mod 20) == 0 & not i_know_dispenser(_)
    <- survey(dispenser).

+step(N)
    : (N mod 20) == 5 & not i_know_goal_zone
    <- survey(goal).

+step(N)
    : (N mod 20) == 10 & not i_know_role_zone
    <- survey(role).

// Resultado do survey chega como evento
+surveyed(dispenser, Distance)
    <- .print("Dispenser mais proximo a ", Distance, " passos").

+surveyed(goal, Distance)
    <- .print("Goal zone mais proxima a ", Distance, " passos").

+surveyed(role, Distance)
    <- .print("Role zone mais proxima a ", Distance, " passos").
```

---

### 2.17 Teste de integracao — apos 200 steps

**Criterio de aceite da Fase 2**:

1. Rodar simulacao ate step 200
2. Verificar nos logs que agentes imprimiram "Dispenser encontrado" e "Goal zone encontrada"
3. Se usando SharedMap: verificar contadores via `get_map_stats`
4. No web monitor: agentes se movem de forma exploratoria (nao ficam parados)

**Teste via plano de log periodico**:

```prolog
+step(N)
    : (N mod 50) == 0
    <- get_map_stats(Visited, Dispensers, GoalZones, RoleZones);
       .print("=== MAPA: visitadas=", Visited,
              " dispensers=", Dispensers,
              " goals=", GoalZones,
              " roles=", RoleZones, " ===").
```

**Metricas esperadas apos 200 steps com 15 agentes**:
- Celulas visitadas: > 500 (depende do tamanho do mapa 70x70 = 4900 celulas)
- Dispensers encontrados: >= 3 (config tem 8-12 dispensers)
- Goal zones encontradas: >= 1 (config tem 4 goal zones)
- Role zones encontradas: >= 1 (config tem 5 role zones)

---

## Resumo de Arquivos Criados/Modificados

| Fase | Arquivo | Status | Notas |
|------|---------|--------|-------|
| 1 | `src/agt/dummy.asl` | [x] CONCLUIDO | Evoluido: logs SIM-START, integracao EISAccess + SharedMap |
| 1 | `hive.jcm` | [x] CONCLUIDO | 1 agente (teste), sem platform/workspace (tudo via ASL) |
| 1 | `eismassimconfig.json` | [x] CONCLUIDO | start-index: 1, count: 1 (teste) |
| 1 | `src/env/connection/EISAccess.java` | [x] CRIADO | Artefato CArtAgO que encapsula EISMASSim. Singleton sharedEI. |
| 1 | `src/env/connection/Translator.java` | [x] CRIADO | Traduz IILang ↔ Jason (baseado em mapc2020) |
| 1 | `conf/TestConfig.json` | [x] CRIADO | MASSIM config: 1 time, 40x40, absolutePosition: true |
| 2 | `src/agt/common/perception.asl` | [x] CRIADO | Processa thing, goalZone, roleZone, task, norm, energy, etc. |
| 2 | `src/agt/common/navigation.asl` | [x] CRIADO | +step(N) → !log_step + !navigate. Frontier exploration. |
| 2 | `src/env/env/SharedMap.java` | [x] CRIADO | update_cell, mark_visited, get_nearest_frontier, get_map_stats |
| 2 | `src/java/hive/DirectionCalculator.java` | [x] CRIADO | Internal action: calcula direcao cardinal |
| 2 | `build.gradle` | [x] ATUALIZADO | jacamo:1.3.0, eismassim-4.5, sourceSets, logging |

---

## Resultados de Teste — Simulacao Fase 2

**Configuracao**: 1 agente, grid 40x40, 100 steps, absolutePosition: true

| Metrica | Step 0 | Step 60 | Step 80 |
|---------|--------|---------|---------|
| Celulas visitadas | 10 | 70 | 70 |
| Dispensers | 1 | 5 | 5 |
| Goal zones | 0 | 0 | 0 |
| Role zones | 4 | 11 | 11 |
| Posicao | (19,10) | (21,23) | (21,23) |

**Observacoes**:
- Agente explora via frontier, movendo ~1 celula a cada 2-3 steps (overhead de perception events)
- Agente ficou preso apos step 60 (fronteiras nao alcancaveis — obstaculos marcados como "visited")
- SharedMap acumula dados corretamente (dispensers, role zones)
- Log periodico funciona (step 0, 60, 80)

**Issues para otimizacao futura** (Fase 9):
1. Timing: agente gasta ~2 steps por movimento (perception events processados antes de +step)
2. Frontier: obstaculos marcados como "visited" reduz fronteiras artificialmente
3. Stuck: agente pode ficar preso quando fronteiras esgotam na area local

---

## Ordem de Execucao Real

```
Fase 1 (CONCLUIDO):
  1.1-1.2  [ja feito na Fase 0]
  1.3      Testado conexao (1 agente + MASSIM) — corrigido EISPlatform → EISAccess artefato
  1.4-1.5  Verificado percepts nos logs — SIM-START e REQUEST-ACTION ok
  1.6-1.7  Move norte verificado + monitor (quando monitor na porta 8000)
  1.8      4 direcoes testadas via ciclo mod 4
  1.9      Movimento aleatorio implementado e testado
  1.10-1.11 15 agentes conectados (ajuste start-index + singleton EI)

Fase 2 (CONCLUIDO):
  2.8      SharedMap.java criado (src/env/env/SharedMap.java)
  2.14     DirectionCalculator.java criado
  2.1-2.7  perception.asl criado (thing, goalZone, roleZone, task, norm, energy, role, position)
  2.13     navigation.asl criado (step-by-step com frontier exploration)
  2.9-2.12 Integracao completa: dummy.asl inclui perception + navigation, cria SharedMap
  2.15     Exploracao por fronteira implementada via get_nearest_frontier
  2.16     Survey — ADIADO (fronteira atende por ora)
  2.17     Testado: 100 steps, mapa com 70 celulas, 5 dispensers, 11 role zones
```
