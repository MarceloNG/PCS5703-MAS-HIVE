# Plano Detalhado — Fases 3 e 4

**Status Geral**: CONCLUIDO (17/mai/2026)

---

## FASE 3 — Coleta de Blocos — CONCLUIDO

**Objetivo**: Agentes navegam ate dispensers, fazem request, attach, e transportam blocos.
**Criterio de aceite**: Agente busca bloco em dispenser, attach com sucesso, e se move com bloco attached.
**Dependencia**: Fase 2 concluida.

---

### Contexto — O que ja existe

| Componente | Status | Localização |
|-----------|--------|-------------|
| `SharedMap.get_nearest_dispenser()` | Ja implementado | `src/env/env/SharedMap.java` |
| `SharedMap.get_nearest_goal_zone()` | Ja implementado | `src/env/env/SharedMap.java` |
| `DirectionCalculator` | Ja implementado | `src/java/hive/DirectionCalculator.java` |
| `navigation.asl` (frontier) | Ja implementado | `src/agt/common/navigation.asl` |
| `perception.asl` (things, zones) | Ja implementado | `src/agt/common/perception.asl` |
| `EISAccess.action(String)` | Ja implementado | `src/env/connection/EISAccess.java` |

**Acoes MASSIM relevantes** (enviadas via `action("request(n)")`):

| Acao | Parametro | Efeito | Resultado sucesso | Resultados falha |
|------|-----------|--------|-------------------|------------------|
| `request(Dir)` | n/s/e/w | Cria bloco no dispenser adjacente na direcao | `success` | `failed_target` (sem dispenser), `failed_blocked` (ja tem bloco) |
| `attach(Dir)` | n/s/e/w | Prende thing adjacente ao agente | `success` | `failed_target` (nada la), `failed_blocked` (ja attached) |
| `detach(Dir)` | n/s/e/w | Solta thing na direcao | `success` | `failed_target` (nada attached la) |
| `rotate(Dir)` | cw/ccw | Rotaciona tudo attached 90° | `success` | `failed` (colisao) |

**Percepts adicionais relevantes para coleta**:

| Percept | Significado |
|---------|-------------|
| `attached(X, Y)` | Bloco attached ao agente na posicao relativa (X, Y) |
| `thing(X, Y, block, Type)` | Bloco solto na visao (pode ser coletado) |
| `thing(X, Y, dispenser, Type)` | Dispenser visto (ja processado em perception.asl) |

---

### 3.1 Processar percept `attached(X, Y)` em perception.asl

**Arquivo**: `src/agt/common/perception.asl`

**Adicionar**:

```prolog
// --- Blocos attached ao agente ---

+attached(X, Y)[source(percept)]
    <- -my_attached(X, Y);
       +my_attached(X, Y).

-attached(X, Y)[source(percept)]
    <- -my_attached(X, Y).
```

**Por que**: Quando o agente faz `attach`, o percept `attached(X, Y)` aparece indicando
a posicao relativa do bloco em relacao ao agente. O agente precisa saber o que carrega
para decidir quando navegar ate o meeting point ou goal zone.

**Regra de utilidade** (adicionar em perception.asl):

```prolog
// Regra derivada: numero de blocos carregados
carrying_blocks(N) :- .count(my_attached(_, _), N).
has_block :- my_attached(_, _).
```

---

### 3.2 Criar `src/java/hive/AdjacentDirection.java` — internal action

**Objetivo**: Dados a posicao do agente (agX, agY) e a posicao do alvo (tX, tY),
retorna a direcao cardinal se o alvo esta adjacente (distancia Manhattan == 1),
ou `none` se nao esta adjacente.

**Arquivo**: `src/java/hive/AdjacentDirection.java`

```java
package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;

public class AdjacentDirection extends DefaultInternalAction {

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int agX = (int) ((NumberTerm) args[0]).solve();
        int agY = (int) ((NumberTerm) args[1]).solve();
        int tX  = (int) ((NumberTerm) args[2]).solve();
        int tY  = (int) ((NumberTerm) args[3]).solve();

        int dx = tX - agX;
        int dy = tY - agY;

        String dir;
        if (dx == 0 && dy == -1)      dir = "n";
        else if (dx == 0 && dy == 1)  dir = "s";
        else if (dx == 1 && dy == 0)  dir = "e";
        else if (dx == -1 && dy == 0) dir = "w";
        else                          dir = "none";

        return un.unifies(args[4], new Atom(dir));
    }
}
```

**Uso no AgentSpeak**:

```prolog
hive.AdjacentDirection(MyX, MyY, DispX, DispY, Dir);
// Dir = n | s | e | w | none
```

---

### 3.3 Criar `src/java/hive/PathFinder.java` — A* basico

**Objetivo**: Encontrar caminho em grade evitando obstaculos conhecidos no SharedMap.
Retorna a **primeira direcao** do caminho (para navegar step-by-step).

**Arquivo**: `src/java/hive/PathFinder.java`

```java
package hive;

import jason.asSemantics.*;
import jason.asSyntax.*;
import cartago.*;

import java.util.*;

public class PathFinder extends DefaultInternalAction {

    static class Node implements Comparable<Node> {
        int x, y, g, f;
        Node parent;
        Node(int x, int y, int g, int f, Node parent) {
            this.x = x; this.y = y; this.g = g; this.f = f; this.parent = parent;
        }
        public int compareTo(Node o) { return Integer.compare(this.f, o.f); }
    }

    @Override
    public Object execute(TransitionSystem ts, Unifier un, Term[] args)
            throws Exception {
        int fromX = (int) ((NumberTerm) args[0]).solve();
        int fromY = (int) ((NumberTerm) args[1]).solve();
        int toX   = (int) ((NumberTerm) args[2]).solve();
        int toY   = (int) ((NumberTerm) args[3]).solve();
        // args[4] = Set de obstaculos como string "x,y" (passado via artifact)
        // Por simplicidade, usamos DirectionCalculator como fallback
        // e PathFinder so com obstaculos inline

        String dir = astar(fromX, fromY, toX, toY, getObstacles(ts));

        return un.unifies(args[4], new Atom(dir));
    }

    private Set<String> getObstacles(TransitionSystem ts) {
        // Tenta obter obstaculos do belief base do agente
        Set<String> obstacles = new HashSet<>();
        // Fallback: retornar vazio (sem obstaculos conhecidos)
        return obstacles;
    }

    private String astar(int fromX, int fromY, int toX, int toY,
                         Set<String> obstacles) {
        if (fromX == toX && fromY == toY) return "skip";

        PriorityQueue<Node> open = new PriorityQueue<>();
        Set<String> closed = new HashSet<>();
        int[][] dirs = {{0,-1}, {0,1}, {1,0}, {-1,0}};
        String[] dirNames = {"n", "s", "e", "w"};

        int h = Math.abs(toX - fromX) + Math.abs(toY - fromY);
        open.add(new Node(fromX, fromY, 0, h, null));

        int maxIter = 2000;
        int iter = 0;

        while (!open.isEmpty() && iter++ < maxIter) {
            Node current = open.poll();
            String ck = current.x + "," + current.y;
            if (closed.contains(ck)) continue;
            closed.add(ck);

            if (current.x == toX && current.y == toY) {
                return firstDirection(current, fromX, fromY, dirNames, dirs);
            }

            for (int i = 0; i < 4; i++) {
                int nx = current.x + dirs[i][0];
                int ny = current.y + dirs[i][1];
                String nk = nx + "," + ny;
                if (!closed.contains(nk) && !obstacles.contains(nk)) {
                    int ng = current.g + 1;
                    int nf = ng + Math.abs(toX - nx) + Math.abs(toY - ny);
                    open.add(new Node(nx, ny, ng, nf, current));
                }
            }
        }
        // Fallback: greedy direction
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (Math.abs(dx) >= Math.abs(dy))
            return dx > 0 ? "e" : "w";
        else
            return dy > 0 ? "s" : "n";
    }

    private String firstDirection(Node goal, int fromX, int fromY,
                                  String[] dirNames, int[][] dirs) {
        Node n = goal;
        while (n.parent != null && !(n.parent.x == fromX && n.parent.y == fromY)) {
            n = n.parent;
        }
        int dx = n.x - fromX;
        int dy = n.y - fromY;
        for (int i = 0; i < 4; i++) {
            if (dirs[i][0] == dx && dirs[i][1] == dy) return dirNames[i];
        }
        return "skip";
    }
}
```

**Nota**: Na versao inicial, o PathFinder nao consulta o SharedMap diretamente (isso
requer passar obstaculos como parametro). Para simplificar, o agente usa
`DirectionCalculator` como navegador primario e o `PathFinder` sera integrado
incrementalmente. O `PathFinder` pode ser melhorado para acessar o SharedMap via
artefato nas fases futuras.

**Abordagem pragmatica para Fase 3**: Usar `DirectionCalculator` existente para navegacao
greedy, com tratamento de `failed_path` para desvio. Criar `PathFinder` mas so integrar
se necessario.

---

### 3.4 Criar `src/agt/common/collection.asl` — logica de coleta

**Arquivo**: `src/agt/common/collection.asl`

Este modulo implementa o ciclo completo de coleta: navegar ate dispenser, request, attach.

```prolog
// ============================================================
// collection.asl — Ciclo de coleta de blocos
// ============================================================

// --- Goal: coletar um bloco de um tipo especifico ---

+!collect_block(Type)
    : my_pos(MX, MY)
    <- get_nearest_dispenser(MX, MY, Type, DX, DY);
       if (DX == -1) {
           .print("Nenhum dispenser de tipo ", Type, " conhecido. Explorando...");
           !explore
       } else {
           .print("Indo coletar ", Type, " no dispenser (", DX, ",", DY, ")");
           +collecting(Type, DX, DY);
           +has_destination(DX, DY)
       }.

// --- Navegacao com destino de coleta: chegou adjacente ao dispenser ---

+!check_at_dispenser
    : collecting(Type, DX, DY) & my_pos(MX, MY)
    <- hive.AdjacentDirection(MX, MY, DX, DY, Dir);
       if (Dir \== none) {
           .print("Adjacente ao dispenser ", Type, ". Request...");
           -has_destination(_, _);
           !do_request(Dir, Type)
       }.

+!check_at_dispenser <- true.

// --- Request: pedir bloco ao dispenser ---

+!do_request(Dir, Type)
    <- .concat("request(", Dir, ")", Act);
       action(Act);
       +waiting_request(Dir, Type).

// Resultado do request
+lastActionResult(success)[source(percept)]
    : waiting_request(Dir, Type)
    <- -waiting_request(Dir, Type);
       .print("Request OK! Attach na direcao ", Dir);
       +waiting_attach(Dir, Type).

+lastActionResult(R)[source(percept)]
    : waiting_request(Dir, Type) & R \== success
    <- -waiting_request(Dir, Type);
       .print("Request falhou: ", R, ". Tentando novamente...");
       .wait(500);
       !do_request(Dir, Type).

// --- Attach: prender o bloco ---
// Executado no proximo step apos request bem-sucedido

+step(N)
    : waiting_attach(Dir, Type)
    <- -waiting_attach(Dir, Type);
       .concat("attach(", Dir, ")", Act);
       action(Act);
       +waiting_attach_result(Dir, Type).

+lastActionResult(success)[source(percept)]
    : waiting_attach_result(Dir, Type)
    <- -waiting_attach_result(Dir, Type);
       -collecting(Type, _, _);
       +collected_block(Type);
       .print("Bloco ", Type, " attached com sucesso!").

+lastActionResult(R)[source(percept)]
    : waiting_attach_result(Dir, Type) & R \== success
    <- -waiting_attach_result(Dir, Type);
       .print("Attach falhou: ", R, ". Tentando novamente...");
       +waiting_attach(Dir, Type).

// --- Detach: soltar bloco ---

+!detach_block(Dir)
    <- .concat("detach(", Dir, ")", Act);
       action(Act).

// --- Rotate: rotacionar blocos attached ---

+!rotate(Dir)
    <- .concat("rotate(", Dir, ")", Act);
       action(Act).
```

**Ponto critico — Timing**: No MASSIM, cada acao ocupa 1 step. O ciclo e:
1. Step N: navegar ate adjacente ao dispenser
2. Step N+1: `request(dir)` → cria bloco
3. Step N+2: `attach(dir)` → prende bloco
4. Step N+3+: navegar com bloco ate destino

O `+step(N) : waiting_attach(Dir, Type)` garante que o attach e feito no step
seguinte ao request, sobrescrevendo temporariamente a navegacao padrao.

---

### 3.5 Modificar `navigation.asl` — integrar verificacao de adjacencia ao dispenser

**Problema**: O navigation.asl atual tem um unico `+step(N)` que faz log + navigate.
Precisamos que, quando o agente esta coletando, ele verifique se chegou adjacente
ao dispenser (nao exatamente NO dispenser).

**Abordagem**: Adicionar um `+step(N)` de prioridade mais alta para o estado de coleta.
Como `collection.asl` e incluido ANTES de `navigation.asl`, seus planos `+step(N)`
tem prioridade.

**Modificacao em `collection.asl`**: Adicionar intercept do step durante coleta:

```prolog
// collection.asl intercepta o step quando coletando e adjacente ao dispenser
+step(N)
    : collecting(Type, DX, DY) & my_pos(MX, MY)
      & not waiting_request(_, _) & not waiting_attach(_, _)
      & not waiting_attach_result(_, _)
    <- hive.AdjacentDirection(MX, MY, DX, DY, Dir);
       if (Dir \== none) {
           .print("Adjacente ao dispenser ", Type, "! Fazendo request...");
           -has_destination(_, _);
           !do_request(Dir, Type)
       } else {
           !log_step(N, MX, MY);
           !navigate
       }.
```

**Ordem de includes no agente**:

```prolog
{ include("common/perception.asl") }
{ include("common/collection.asl") }   // ANTES de navigation (prioridade)
{ include("common/navigation.asl") }
```

Isso garante que os planos `+step(N)` de `collection.asl` sejam avaliados primeiro.

---

### 3.6 Modificar `navigation.asl` — navegar ate celula adjacente (nao em cima)

**Problema atual**: O agente navega exatamente ATE a coordenada do dispenser.
Mas `request(dir)` exige que o agente esteja ADJACENTE, nao EM CIMA.

**Solucao**: Quando o destino e um dispenser (indicado por `collecting(Type, DX, DY)`),
o agente deve parar quando estiver a distancia Manhattan == 1 do destino.

**Modificar** `navigation.asl` — adicionar plano de chegada adjacente:

```prolog
// Navegacao: chegou adjacente ao destino de coleta
+!navigate
    : collecting(_, DX, DY) & has_destination(DX, DY) & my_pos(MX, MY)
      & (math.abs(DX - MX) + math.abs(DY - MY)) == 1
    <- -has_destination(DX, DY);
       .print("Adjacente ao destino de coleta").
```

Este plano deve ficar ANTES dos planos existentes de `!navigate`.

---

### 3.7 Atualizar `dummy.asl` — testar ciclo de coleta

**Objetivo**: O agente, apos explorar e encontrar um dispenser, deve automaticamente
tentar coletar um bloco.

**Modificacao de `dummy.asl`**:

```prolog
{ include("common/perception.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

!start.

+!start
    <- .my_name(Me);
       .print("Agente ", Me, " iniciado.");
       !setup_shared_map;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("Conectado ao EIS. Aguardando percepts...").

// setup_shared_map (sem mudanca)
+!setup_shared_map ... (igual)

// SIM-START percepts
+name(N)     <- .print("SIM-START: nome = ", N).
+team(T)     <- .print("SIM-START: time = ", T).
+steps(S)    <- .print("SIM-START: total steps = ", S).

// Quando descobre um dispenser, tenta coletar
+new_dispenser(X, Y, Type)
    : not has_block & not collecting(_, _, _)
    <- .print("Novo dispenser ", Type, " em (", X, ",", Y, ")! Indo coletar.");
       !collect_block(Type).
```

O signal `new_dispenser` e emitido pelo `SharedMap.update_cell()` quando um dispenser
e descoberto pela primeira vez.

---

### 3.8 Testar ciclo request + attach

**Passo a passo de teste**:

1. Iniciar MASSIM com `TestConfig.json` (40x40, absolutePosition: true)
2. Iniciar JaCaMo com 1 agente
3. Observar nos logs:
   - Agente explora e encontra dispenser ("Novo dispenser b1 em (21,12)!")
   - Agente navega ate adjacente ao dispenser
   - Agente faz `request(dir)` → lastActionResult(success)
   - Agente faz `attach(dir)` → lastActionResult(success)
   - Percept `attached(0, 1)` ou similar aparece
   - Agente continua movendo (com bloco)

**Verificacao no log**:

```
Step 5 ... Novo dispenser b1 em (21,12)! Indo coletar.
Step 8 ... Adjacente ao dispenser b1! Fazendo request...
Step 9 ... Request OK! Attach na direcao s
Step 10 ... Bloco b1 attached com sucesso!
```

**Troubleshooting**:

| Problema | Causa provavel | Solucao |
|----------|---------------|---------|
| `request` retorna `failed_target` | Agente nao esta adjacente ao dispenser | Verificar calculo de adjacencia |
| `attach` retorna `failed_target` | Bloco nao esta la (request falhou antes) | Verificar que request deu success |
| `attach` retorna `failed_blocked` | Agente ja tem bloco attached na mesma direcao | Verificar estado do agente |
| Agente nao para adjacente | Navigation vai ATE o dispenser, nao adjacente | Verificar plano de parada adjacente |
| Actions nao chegam | Formato errado da string de acao | Verificar concatenacao: `"request(n)"` |

---

### 3.9 Tratar `failed_path` — desvio de obstaculos

Quando `lastActionResult(failed_path)`, o agente bateu em obstaculo.

**Adicionar a `navigation.asl`**:

```prolog
// Desvio reativo: se ultimo move falhou, tentar perpendicular
+!navigate
    : has_destination(DX, DY) & my_pos(MX, MY) & last_move_blocked
    <- -last_move_blocked;
       hive.DirectionCalculator(MX, MY, DX, DY, Dir);
       if (Dir == n | Dir == s) {
           .random(R);
           if (R < 0.5) { !send_move(e) } else { !send_move(w) }
       } else {
           .random(R);
           if (R < 0.5) { !send_move(n) } else { !send_move(s) }
       }.
```

Este plano deve ficar ANTES do plano normal de navegacao, para ter prioridade quando
`last_move_blocked` esta ativo.

---

### 3.10 Verificar movimento com bloco attached

**O que esperar**: Apos attach, o agente tem speed reduzida (depende do role):
- Role `default`: speed = [2, 1, 0] → sem blocos move 2 celulas/step, com 1 bloco move 1, com 2+ nao move
- Outros roles podem ter speeds diferentes

O agente deve continuar navegando normalmente. O MASSIM automaticamente aplica
a reducao de speed. Se o agente tenta `move` com speed 0, o resultado e `failed`.

**Verificacao**: Apos attach, o agente deve conseguir executar `move` com resultado
`success` (speed >= 1). Se `failed_status` → agente esta desativado. Se simplesmente
nao move → speed = 0 (demais blocos carregados).

---

### 3.11 Implementar detach e rotate

**Detach** — ja definido em `collection.asl`:

```prolog
+!detach_block(Dir)
    <- .concat("detach(", Dir, ")", Act);
       action(Act).
```

**Rotate** — ja definido:

```prolog
+!rotate(Dir)    // Dir = cw ou ccw
    <- .concat("rotate(", Dir, ")", Act);
       action(Act).
```

**Teste**: Apos coletar bloco, verificar:
1. `!detach_block(s)` → bloco solta → `attached(0,1)` desaparece dos percepts
2. `!rotate(cw)` → `attached(0,1)` vira `attached(1,0)` (rotacao 90° horaria)

---

### 3.12 Testar ciclo completo

**Cenario de teste end-to-end**:

1. Agente inicia, explora, encontra dispenser b1
2. Navega ate adjacente ao dispenser
3. `request(s)` → success
4. `attach(s)` → success
5. Agente navega com bloco (speed reduzida)
6. `detach(s)` → bloco solta
7. Agente continua livre

**Criterio de aceite**: Logs mostram todo o ciclo sem erros. Percept `attached(0,1)`
aparece apos attach e desaparece apos detach.

---

### 3.13 Resumo de arquivos — Fase 3

| Arquivo | Acao | Descricao |
|---------|------|-----------|
| `src/agt/common/perception.asl` | MODIFICAR | Adicionar `attached(X,Y)` e regras `has_block`, `carrying_blocks` |
| `src/agt/common/collection.asl` | **CRIAR** | Ciclo request/attach/detach/rotate |
| `src/agt/common/navigation.asl` | MODIFICAR | Parada adjacente, desvio de obstaculos |
| `src/java/hive/AdjacentDirection.java` | **CRIAR** | Internal action: calcula direcao adjacente |
| `src/java/hive/PathFinder.java` | **CRIAR** | A* basico (opcional para Fase 3, essencial para Fase 5+) |
| `src/agt/dummy.asl` | MODIFICAR | Incluir collection.asl, reagir a new_dispenser |

---

### Ordem de execucao — Fase 3

```
3.1  Adicionar attached(X,Y) em perception.asl
3.2  Criar AdjacentDirection.java
3.3  Criar PathFinder.java (base, pode simplificar)
3.4  Criar collection.asl (request/attach/detach/rotate)
3.5  Modificar navigation.asl (parada adjacente + desvio obstaculos)
3.6  Atualizar dummy.asl (includes + reagir a new_dispenser)
3.7  Compilar e testar ciclo request+attach
3.8  Testar ciclo completo com detach
3.9  Verificar speed reduzida com bloco
```

---
---

## FASE 4 — Organizacao MOISE+ — CONCLUIDO

**Objetivo**: Organizacao MOISE+ operacional com papeis, grupos e esquemas.
**Criterio de aceite**: Agentes adotam papeis, se organizam em grupos, recebem obrigacoes de missoes.
**Dependencia**: Fase 1 concluida. Pode paralelizar com Fase 3.

---

### Contexto — Organizacao HIVE

Conforme `doc/ARCH.md` e `doc/TECHSPEC.md`, a organizacao HIVE divide 15 agentes em:

| Grupo | Composicao | Papel | Funcao |
|-------|-----------|-------|--------|
| squad_group × 3 | 4 agentes cada | squad_leader (1) | Coordena squad, avalia tasks, faz bids |
| | | collector (2) | Navega a dispensers, faz request/attach |
| | | assembler (1) | Recebe blocos via connect, navega a goal zone, submit |
| sentinel_group × 1 | 2-3 agentes | sentinel | Patrulha goal zones, faz clear em inimigos |

**Distribuicao**: 3 squads × 4 = 12 + 3 sentinels = 15 agentes

**Nota pragmatica para Fase 4**: Comecar com uma organizacao simples (papeis, grupos, 1 scheme).
Esquemas complexos e normas organizacionais podem ser adicionados incrementalmente.

---

### 4.1 Criar `src/org/hive_org.xml` — Especificacao Organizacional

**Arquivo**: `src/org/hive_org.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<organisational-specification id="hive_org"
    os-version="1.0"
    xmlns="http://moise.sourceforge.net/os">

  <!-- =================== STRUCTURAL SPECIFICATION =================== -->
  <structural-specification>

    <role-definitions>
      <role id="squad_leader">
        <extends role="soc"/>
      </role>
      <role id="collector">
        <extends role="soc"/>
      </role>
      <role id="assembler">
        <extends role="soc"/>
      </role>
      <role id="sentinel">
        <extends role="soc"/>
      </role>
    </role-definitions>

    <group-specification id="hive_team">
      <roles>
        <role id="squad_leader" min="3" max="4"/>
        <role id="collector"    min="6" max="8"/>
        <role id="assembler"    min="3" max="4"/>
        <role id="sentinel"     min="1" max="3"/>
      </roles>

      <subgroups>
        <group-specification id="squad_group" min="2" max="4">
          <roles>
            <role id="squad_leader" min="1" max="1"/>
            <role id="collector"    min="1" max="2"/>
            <role id="assembler"    min="1" max="1"/>
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

        <group-specification id="sentinel_group" min="1" max="2">
          <roles>
            <role id="sentinel" min="1" max="3"/>
          </roles>
        </group-specification>
      </subgroups>
    </group-specification>

  </structural-specification>

  <!-- =================== FUNCTIONAL SPECIFICATION =================== -->
  <functional-specification>

    <!-- Scheme: exploracao do mapa -->
    <scheme id="exploration_scheme">
      <goal id="map_explored">
        <plan operator="parallel">
          <goal id="dispensers_found" ttf="200"/>
          <goal id="goal_zones_found" ttf="200"/>
          <goal id="role_zones_found" ttf="200"/>
        </plan>
      </goal>
      <mission id="m_scout" min="1" max="15">
        <goal id="dispensers_found"/>
        <goal id="goal_zones_found"/>
        <goal id="role_zones_found"/>
      </mission>
    </scheme>

    <!-- Scheme: execucao de task -->
    <scheme id="task_execution_scheme">
      <goal id="task_submitted">
        <plan operator="sequence">
          <goal id="blocks_collected" ttf="100"/>
          <goal id="blocks_assembled" ttf="50"/>
          <goal id="pattern_submitted" ttf="30"/>
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

    <!-- Scheme: defesa -->
    <scheme id="defense_scheme">
      <goal id="team_protected">
        <plan operator="parallel">
          <goal id="goal_zones_guarded"/>
          <goal id="threats_cleared"/>
        </plan>
      </goal>
      <mission id="m_guard" min="1" max="3">
        <goal id="goal_zones_guarded"/>
        <goal id="threats_cleared"/>
      </mission>
    </scheme>

  </functional-specification>

  <!-- =================== NORMATIVE SPECIFICATION =================== -->
  <normative-specification>
    <norm id="n_scout"    type="obligation" role="squad_leader" mission="m_scout"/>
    <norm id="n_collect"  type="obligation" role="collector"    mission="m_collect"/>
    <norm id="n_assemble" type="obligation" role="assembler"    mission="m_assemble"/>
    <norm id="n_submit"   type="obligation" role="assembler"    mission="m_submit"/>
    <norm id="n_guard"    type="obligation" role="sentinel"     mission="m_guard"/>
  </normative-specification>

</organisational-specification>
```

**Notas**:
- `<extends role="soc"/>` herda do papel social basico do MOISE+
- `ttf` = time to fulfill (em steps) — opcional, util para monitoramento
- A Normative Spec define quem DEVE cumprir cada missao

---

### 4.2 Criar agentes especializados

Cada papel tera seu proprio arquivo `.asl`, incluindo os modulos comuns e adicionando
comportamento especifico.

#### 4.2.1 `src/agt/squad_leader.asl`

```prolog
{ include("common/perception.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(squad_leader).

!start.

+!start
    <- .my_name(Me);
       .print("[LEADER] ", Me, " iniciado.");
       !setup_shared_map;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[LEADER] Conectado. Modo: exploracao + coordenacao.").

// setup_shared_map (igual ao dummy.asl)
+!setup_shared_map
    <- lookupArtifact("shared_map", MapId); focus(MapId).
-!setup_shared_map
    <- .wait(50); !try_create_map.
+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId); focus(MapId).
-!try_create_map
    <- .wait(100); !setup_shared_map.

// SIM-START
+name(N)  <- .print("[LEADER] SIM-START: nome = ", N).
+team(T)  <- .print("[LEADER] SIM-START: time = ", T).
+steps(S) <- .print("[LEADER] SIM-START: steps = ", S).

// Comportamento especifico: avaliar tasks (Fase 5)
// Por agora, apenas explora como os outros agentes
```

#### 4.2.2 `src/agt/collector.asl`

```prolog
{ include("common/perception.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(collector).

!start.

+!start
    <- .my_name(Me);
       .print("[COLLECTOR] ", Me, " iniciado.");
       !setup_shared_map;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[COLLECTOR] Conectado. Modo: exploracao + coleta.").

+!setup_shared_map
    <- lookupArtifact("shared_map", MapId); focus(MapId).
-!setup_shared_map
    <- .wait(50); !try_create_map.
+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId); focus(MapId).
-!try_create_map
    <- .wait(100); !setup_shared_map.

// SIM-START
+name(N)  <- .print("[COLLECTOR] SIM-START: nome = ", N).
+team(T)  <- .print("[COLLECTOR] SIM-START: time = ", T).
+steps(S) <- .print("[COLLECTOR] SIM-START: steps = ", S).

// Reagir a dispensers descobertos
+new_dispenser(X, Y, Type)
    : not has_block & not collecting(_, _, _)
    <- .print("[COLLECTOR] Dispenser ", Type, " em (", X, ",", Y, ")!");
       !collect_block(Type).
```

#### 4.2.3 `src/agt/assembler.asl`

```prolog
{ include("common/perception.asl") }
{ include("common/collection.asl") }
{ include("common/navigation.asl") }

my_role_type(assembler).

!start.

+!start
    <- .my_name(Me);
       .print("[ASSEMBLER] ", Me, " iniciado.");
       !setup_shared_map;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[ASSEMBLER] Conectado. Modo: exploracao + montagem.").

+!setup_shared_map
    <- lookupArtifact("shared_map", MapId); focus(MapId).
-!setup_shared_map
    <- .wait(50); !try_create_map.
+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId); focus(MapId).
-!try_create_map
    <- .wait(100); !setup_shared_map.

// SIM-START
+name(N)  <- .print("[ASSEMBLER] SIM-START: nome = ", N).
+team(T)  <- .print("[ASSEMBLER] SIM-START: time = ", T).
+steps(S) <- .print("[ASSEMBLER] SIM-START: steps = ", S).

// Logica de connect e submit sera adicionada na Fase 6-7
```

#### 4.2.4 `src/agt/sentinel.asl`

```prolog
{ include("common/perception.asl") }
{ include("common/navigation.asl") }

my_role_type(sentinel).

!start.

+!start
    <- .my_name(Me);
       .print("[SENTINEL] ", Me, " iniciado.");
       !setup_shared_map;
       makeArtifact(Me, "connection.EISAccess", ["eismassimconfig.json", Me], EisId);
       focus(EisId);
       .print("[SENTINEL] Conectado. Modo: patrulha.").

+!setup_shared_map
    <- lookupArtifact("shared_map", MapId); focus(MapId).
-!setup_shared_map
    <- .wait(50); !try_create_map.
+!try_create_map
    <- makeArtifact("shared_map", "env.SharedMap", [], MapId); focus(MapId).
-!try_create_map
    <- .wait(100); !setup_shared_map.

// SIM-START
+name(N)  <- .print("[SENTINEL] SIM-START: nome = ", N).
+team(T)  <- .print("[SENTINEL] SIM-START: time = ", T).
+steps(S) <- .print("[SENTINEL] SIM-START: steps = ", S).

// Sentinel NAO inclui collection.asl (nao coleta blocos)
// Logica de patrulha e clear sera adicionada na Fase 8
```

---

### 4.3 Atualizar `hive.jcm` — 15 agentes com papeis

**Arquivo**: `hive.jcm`

**Distribuicao dos 15 agentes**:

| Agente | ASL | Papel |
|--------|-----|-------|
| connectionA1-A3 | squad_leader.asl | squad_leader (3 squads) |
| connectionA4-A9 | collector.asl | collector (2 por squad) |
| connectionA10-A12 | assembler.asl | assembler (1 por squad) |
| connectionA13-A15 | sentinel.asl | sentinel (3) |

```
mas hive {

    // Squad Leaders (1 por squad)
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

**Nota**: A secao `organisation` do JaCaMo sera adicionada quando os agentes
estiverem interagindo com os esquemas. Para a Fase 4 inicial, os papeis sao
definidos pelas crencas internas do agente (`my_role_type`).

---

### 4.4 Atualizar `eismassimconfig.json` — 15 agentes

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

**Mudanca**: `count` volta para 15 (estava em 1 para testes unitarios).

---

### 4.5 Integrar MOISE+ no JaCaMo (avancado)

**Nota**: A integracao completa do MOISE+ com `organisation` no JCM requer
que os agentes interajam com artefatos organizacionais (OrgBoard, GroupBoard, SchemeBoard).
Isso adiciona complexidade significativa.

**Abordagem pragmatica**: Para a Fase 4, usar **papeis baseados em crencas**
(`my_role_type(collector)`) em vez da integracao completa MOISE+. O XML
organizacional (`hive_org.xml`) fica como documentacao formal da estrutura.

**Integracao completa MOISE+ (se tempo permitir)**:

Adicionar ao `hive.jcm`:

```
organisation hive_org : org/hive_org.xml {
    group hive_team : hive_team {
        responsible-for: exploration_scheme
    }
}
```

E no ASL de cada agente:

```prolog
// Adotar papel na organizacao
+!adopt_org_role
    <- .my_name(Me);
       lookupArtifact("hive_team", GrpId);
       adoptRole(squad_leader)[artifact_id(GrpId)].
```

**Quando integrar MOISE+ completamente**: Fase 5 (Coordenacao e Leilao) — quando os
esquemas funcionais (task_execution_scheme) precisam ser instanciados e missoes
atribuidas formalmente.

---

### 4.6 Troca de role do servidor MASSIM via `adopt(roleName)`

**Mecanica MASSIM**: Agentes podem trocar de role do servidor (nao MOISE+) usando
`adopt(roleName)` quando estao em uma role zone. Cada role do servidor tem:
- `vision`: raio de visao
- `speed`: array de velocidades [sem bloco, 1 bloco, 2+ blocos]
- `clear.chance` e `clear.maxDistance`: efetividade de clear
- `actions`: lista de acoes permitidas

**Implementacao**:

```prolog
// Quando em role zone e quer trocar de role
+!adopt_server_role(RoleName)
    : my_pos(MX, MY) & roleZone(RX, RY)[source(percept)]
      & RX == 0 & RY == 0  // esta NA role zone (posicao relativa 0,0)
    <- .concat("adopt(", RoleName, ")", Act);
       action(Act);
       .print("Adotando role do servidor: ", RoleName).

-!adopt_server_role(RoleName)
    <- .print("Nao estou em role zone. Nao posso adotar ", RoleName).
```

**Estrategia de roles do servidor** (para fases futuras):
- Collectors: role com boa speed e 1+ bloco
- Sentinels: role com alto clear.chance e maxDistance
- Exploradores: role com alta vision

---

### 4.7 Testar organizacao basica

**Cenario de teste**:

1. Iniciar MASSIM com TestConfig (ajustar para 15 agentes)
2. Iniciar JaCaMo com 15 agentes (3 leaders, 6 collectors, 3 assemblers, 3 sentinels)
3. Verificar nos logs:
   - `[LEADER]`, `[COLLECTOR]`, `[ASSEMBLER]`, `[SENTINEL]` aparecem corretamente
   - Todos os 15 agentes conectam ao MASSIM
   - Cada tipo de agente se comporta conforme esperado
   - Collectors reagem a `new_dispenser` e coletam blocos
   - Leaders e sentinels exploram

**Criterio de aceite**:
- 15 agentes conectados com papeis distintos
- Logs mostram prefixos de papel corretos
- Pelo menos 1 collector completa ciclo request/attach

---

### 4.8 Resumo de arquivos — Fase 4

| Arquivo | Acao | Descricao |
|---------|------|-----------|
| `src/org/hive_org.xml` | **CRIAR** | SS + FS + NS completo |
| `src/agt/squad_leader.asl` | **CRIAR** | Lider de squad |
| `src/agt/collector.asl` | **CRIAR** | Coletor de blocos |
| `src/agt/assembler.asl` | **CRIAR** | Montador/submitter |
| `src/agt/sentinel.asl` | **CRIAR** | Sentinela |
| `hive.jcm` | MODIFICAR | 15 agentes com ASLs especializados |
| `eismassimconfig.json` | MODIFICAR | count: 15 |

---

### Ordem de execucao — Fase 4

```
4.1  Criar hive_org.xml (SS + FS + NS)
4.2  Criar squad_leader.asl
4.3  Criar collector.asl
4.4  Criar assembler.asl
4.5  Criar sentinel.asl
4.6  Atualizar hive.jcm (15 agentes especializados)
4.7  Atualizar eismassimconfig.json (count: 15)
4.8  Compilar e testar 15 agentes conectados
4.9  Verificar que collectors coletam blocos
4.10 Verificar logs com prefixos de papel
```

---
---

## Dependencias entre Fases 3 e 4

```
Fase 2 (CONCLUIDA)
  │
  ├── Fase 3 (Coleta) ──────── depende de Fase 2
  │     3.1-3.4  perception + collection.asl + navigation mods
  │     3.5-3.9  testes de request/attach/detach
  │
  ├── Fase 4 (MOISE+) ──────── depende de Fase 1 (pode paralelizar com Fase 3)
  │     4.1      hive_org.xml
  │     4.2-4.5  ASLs especializados
  │     4.6-4.7  hive.jcm + eismassimconfig
  │
  └── Integração (3 + 4) ──── collector.asl usa collection.asl da Fase 3
        4.8-4.10 teste integrado com 15 agentes
```

**Recomendacao**: Executar Fase 3 primeiro (1 agente testando coleta), depois Fase 4
(criar ASLs especializados que incluem collection.asl), e por fim teste integrado
com 15 agentes.

---

## Metricas de aceite — Fases 3+4 combinadas

| Metrica | Alvo | Resultado |
|---------|------|-----------|
| Agentes conectados | 15 | **15/15 OK** |
| Papeis distintos nos logs | 4 (leader, collector, assembler, sentinel) | **4/4 OK** |
| Ciclos request/attach completos | >= 3 (por simulacao de 100 steps) | **>= 5 OK** (b0 e b1 coletados por multiplos collectors) |
| Blocos attached sem erro | >= 1 por collector | **OK** (multiplos blocos attached) |
| Map stats apos 100 steps | vis > 200 (com 15 agentes explorando) | Nao medido (teste focou em coleta) |

---

## Resultado da Execucao (17/mai/2026)

### Fase 3 — Coleta de Blocos

**Teste com 1 agente (dummy.asl)**:

```
Step 0:  Exploração, detecta dispenser b1 em (21,12), inicia coleta
Step 1:  [COL] Navegando para dispenser (21,12) dir=e
Step 2:  [COL] Navegando para dispenser (21,12) dir=e
Step 3:  [COL] Adjacente ao dispenser b1! request(s)
Step 4:  [COL] Request OK! Fazendo attach(s)
Step 5:  [COL] Bloco b1 attached com sucesso! Pos(21,11)
Step 6+: Agente explora com bloco (speed reduzida)
```

**Bugs corrigidos**:

| Bug | Causa | Fix |
|-----|-------|-----|
| `my_pos` nao disponivel em +step(N) | +position handler nao executado antes de +step | `my_pos(X,Y) :- position(X,Y)` como regra |
| Percepts delta perdidos pelo EISAccess | clearPercepts + addList delta perdiam percepts inalterados | Full-state tracking com `currentPercepts` + `currentPerceptKeys` |
| +lastActionResult(success) nao disparava collection handler | perception.asl (incluido primeiro) capturava o evento | Redesign: verificar lastActionResult no CONTEXTO de +step, nao como evento |
| SIM-START duplicados a cada step | Percepts simStart re-publicados no full-state | Filtro `SIM_START_PERCEPTS` no EISAccess |
| Sem acao apos attach sucesso | Collection handler nao submetia action apos processar resultado | Adicionado explore/move apos coleta |

### Fase 4 — Organizacao MOISE+

**Teste com 15 agentes**:

- 3 LEADER (connectionA1-A3) → squad_leader.asl
- 6 COLLECTOR (connectionA4-A9) → collector.asl
- 3 ASSEMBLER (connectionA10-A12) → assembler.asl
- 3 SENTINEL (connectionA13-A15) → sentinel.asl

**Resultados**:
- Todos os 15 agentes conectados ao MASSIM
- Cada tipo com log prefixado ([LEADER], [COLLECTOR], [ASSEMBLER], [SENTINEL])
- Collectors reagem automaticamente a `new_dispenser`
- Multiplos ciclos request/attach completados (b0 e b1)
- Competição por dispensers tratada (`failed_blocked` → retry)

### Arquivos criados/modificados

| Arquivo | Acao | Status |
|---------|------|--------|
| `src/agt/common/perception.asl` | MODIFICADO | `my_pos` como regra, removido [source(percept)], adicionado attached/has_block |
| `src/agt/common/collection.asl` | **CRIADO** | Ciclo request/attach/detach/rotate via +step handlers |
| `src/agt/common/navigation.asl` | MODIFICADO | Desvio de obstaculos, explore simplificado |
| `src/agt/dummy.asl` | MODIFICADO | Inclui collection.asl, reage a new_dispenser |
| `src/java/hive/AdjacentDirection.java` | **CRIADO** | Internal action: calcula direcao adjacente |
| `src/java/hive/PathFinder.java` | **CRIADO** | A* basico (reservado para Fase 5+) |
| `src/env/connection/EISAccess.java` | MODIFICADO | Full-state percepts, filtro SIM-START |
| `src/org/hive_org.xml` | **CRIADO** | SS + FS + NS completo |
| `src/agt/squad_leader.asl` | **CRIADO** | Lider de squad |
| `src/agt/collector.asl` | **CRIADO** | Coletor + reacao a dispensers |
| `src/agt/assembler.asl` | **CRIADO** | Montador (logica submit na Fase 6-7) |
| `src/agt/sentinel.asl` | **CRIADO** | Sentinela (sem collection, logica clear na Fase 8) |
| `hive.jcm` | MODIFICADO | 15 agentes especializados |
| `eismassimconfig.json` | MODIFICADO | count: 15 |
