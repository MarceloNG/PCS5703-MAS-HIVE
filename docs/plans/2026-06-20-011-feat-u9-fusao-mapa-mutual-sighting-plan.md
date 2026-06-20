---
title: "feat: U9 — fusão de mapa por avistamento mútuo (#17)"
date: 2026-06-20
status: active
issue: "#17"
track: mover-mapear
depth: Standard
---

# feat: U9 — fusão de mapa por avistamento mútuo (#17)

## Summary

Implementa a fusão de mapa cross-agente no modo `absolutePosition:false` (OfficialRolesConfig). Quando dois agentes se avistam mutuamente no mesmo step, calculam o offset entre os frames dead-reckoning, trocam suas células conhecidas (dispensers, goal-zones, role-zones) e cada um ingere os dados do outro traduzidos para o próprio frame. Após a fusão, cada agente consegue navegar a dispensers que só o colega observou — desbloqueando score na config oficial.

---

## Problem Frame

Em `absolutePosition:false`, cada agente começa com `dr_pos(0,0)` na própria posição de spawn (`perception.asl:22`) e integra deslocamentos a cada move. Dois agentes com spawns distintos têm origens distintas: a célula `(5,3)` no frame do agente A é uma célula absoluta diferente da `(5,3)` no frame do agente B.

O SharedMap é privado por agente (`"map_"+Me`, `shared_map_init.asl`), mas todas as escritas usam `MX+X, MY+Y` onde `(MX,MY)` é o `my_pos` do agente (seu frame DR). Quando B consulta `get_nearest_dispenser` com as próprias coordenadas, recebe posições que foram armazenadas por A no frame de A — coordenada errada, célula vazia, `failed_target`. Resultado: score 0 mesmo com workers adotados (A3: 170 `request`, 169 `failed_target`; A8: 107 `request`, 105 `failed_target` — replay 2026-06-20).

A causa raiz e evidências estão documentadas em `docs/solutions/architecture-patterns/cross-frame-sharedmap-breaks-official-config.md`. A infra de tradução (`translateCells`) já existe em `SharedMap.java:858` como método package-private — falta o @OPERATION wrapper e o handshake que descobre o offset.

Referência de implementação: LI(A)RA `synchronism.asl` — avistamento mútuo no mesmo step → broadcast do par → cálculo do offset → tradução de posições do colega.

---

## Requirements

| ID | Requisito |
|---|---|
| R1 | Dois agentes com `absolutePosition:false` que se avistam mutuamente em step S calculam o offset entre frames DR sem intervenção externa |
| R2 | Cada agente ingere as células conhecidas do colega traduzidas para o próprio frame — obs properties `known_dispenser`/`known_goal_zone`/`known_role_zone` atualizadas |
| R3 | Após fusão, A* traça rota válida até um dispenser que só o colega observou |
| R4 | Verificação geométrica de avistamento mútuo: `rX + rBx == 0 && rY + rBy == 0` — evita falso match quando múltiplos colegas visíveis |
| R5 | `already_merged_with(Peer)` impede re-fusão no passo seguinte (colegas continuam visíveis) |
| R6 | Cenário determinístico `05-map-merge` (2 agentes, `absolutePosition:false`, dispensers em lados opostos) PASS com assert `requests_ok >= 1` |
| R7 | JUnit `SightingHandshakeTest` PASS: dado par (A viu B em (3,2); B viu A em (-3,-2)) → offset calculado corretamente |
| R8 | Sem regressão nos cenários existentes da `regression.sh` |

---

## Key Technical Decisions

**KT1 — Troca de células por mensagem Jason, não translateCells no mapa do colega:** Cada agente tem seu próprio artefato SharedMap (`makeArtifact("map_"+Me, ...)`). `translateCells` opera in-place no mapa do chamador; chamá-la no mapa do colega via CArtAgO exigiria acesso cruzado ao estado interno do artefato e corromperia o frame do colega para futuras escritas. A abordagem: A calcula o offset, pede células brutas a B; B envia suas células no próprio frame + o offset; A aplica a tradução ao ingeri-las com `update_cell`. O protocolo é simétrico — ambos trocam células entre si.

**KT2 — `@OPERATION merge_frame(dX, dY)` como wrapper de translateCells (infra futura):** O comentário em `SharedMap.java:829-831` exige o @OPERATION para que agentes chamem `translateCells` via ASL. Embora não seja usado no protocolo de troca de células (KT1), `merge_frame` re-emite as obs properties `known_*` que ficam stale após a tradução in-place — necessário para qualquer cenário onde o agente precise resetar seu próprio frame (U9 fase 2 / resolução de spawns conhecidos).

**KT3 — Verificação geométrica mutual antes de calcular offset:** Se A viu B em (rX, rY) e B viu A em (rBx, rBy), por geometria: `rX + rBx == 0 && rY + rBy == 0` (exato, inteiros). O handler de `frame_pos` só aceita o match se essa condição passa — caso contrário, múltiplos colegas visíveis gerariam pares falsos e offsets errados.

**KT4 — `computeSightingOffset` como método estático package-private em SharedMap.java:** fórmula: `dX = Ax + relX - Bx; dY = Ay + relY - By`. Colocado em SharedMap para ser testável pelo mesmo setup dos outros testes unitários; sem nova classe utilitária.

**KT5 — `saw_teammate_at(S, rX, rY)` como belief efêmero por step:** capturado no handler `+thing(X,Y,entity,Team)` e consultado quando chega `frame_pos` do mesmo step S. Jason processa percepts antes de mensagens num ciclo BDI, portanto o belief já existe quando o handler de mensagem executa. Sem limpeza manual — o belief é sobrescrito a cada step sem acumulação.

**KT6 — Nova métrica `requests_ok` em `assert_metric.py`:** `sum(1 for r in rows if r[5]=="request" and r[6]=="success")`. Conta requests bem-sucedidos no time — é a evidência direta de que o agente chegou a um dispenser válido (o A* levou ao lugar certo), sem exigir o pipeline completo de coleta.

---

## High-Level Technical Design

### Handshake de avistamento mútuo (sequência)

```mermaid
sequenceDiagram
    participant A as agentA (dr_pos Ax,Ay)
    participant B as agentB (dr_pos Bx,By)

    Note over A,B: Step S — ambos se avistam mutuamente

    A->>A: +thing(rX,rY,entity,myTeam) → +saw_teammate_at(S,rX,rY)
    A->>B: .broadcast(frame_pos(S, A, Ax, Ay, rX, rY))

    B->>B: +thing(rBx,rBy,entity,myTeam) → +saw_teammate_at(S,rBx,rBy)
    B->>A: .broadcast(frame_pos(S, B, Bx, By, rBx, rBy))

    Note over A: recebe frame_pos de B
    A->>A: check: saw_teammate_at(S,rX,rY) ∧ (rX+rBx==0) ∧ (rY+rBy==0)
    A->>A: dX = Ax+rX-Bx; dY = Ay+rY-By
    A->>A: +already_merged_with(B)
    A->>B: .send(B, tell, export_cells(A, dX, dY))

    Note over B: recebe frame_pos de A (simétrico)
    B->>B: check: saw_teammate_at(S,rBx,rBy) ∧ (rBx+rX==0) ∧ (rBy+rY==0)
    B->>B: dX2 = Bx+rBx-Ax; dY2 = By+rBy-Ay  (= -dX, -dY)
    B->>B: +already_merged_with(A)
    B->>A: .send(A, tell, export_cells(B, dX2, dY2))

    Note over B: recebe export_cells(A, dX, dY)
    B->>B: .findall(known_dispenser,...,Ds); .findall(known_goal_zone,...,GZs); ...
    B->>A: .send(A, tell, peer_cells_raw(Ds, GZs, RZs, dX, dY))

    Note over A: recebe export_cells(B, dX2, dY2) — simétrico, envia suas células a B
    A->>B: .send(B, tell, peer_cells_raw(AdDs, AdGZs, AdRZs, dX2, dY2))

    Note over A: recebe peer_cells_raw de B
    A->>A: for d(X,Y,T): update_cell(X+dX, Y+dY, dispenser, T) → novo known_dispenser obs property
    A->>A: for g(X,Y): update_cell(X+dX, Y+dY, "goal_zone", "")
    A->>A: for r(X,Y): update_cell(X+dX, Y+dY, "role_zone", "")
```

### Cálculo do offset (álgebra)

```
// A em frame-A está em (Ax, Ay). A vê B a relativo (rX, rY).
// B em frame-B está em (Bx, By).
// Para converter célula C de frame-B → frame-A:
//   C_A = C_B + (dX, dY)   onde:
dX = Ax + rX - Bx
dY = Ay + rY - By
// Verificação: B_A = (Ax+rX, Ay+rY) (A viu B aí). B_B = (Bx, By). B_A = B_B + (dX,dY) ✓
```

---

## Scope Boundaries

**Em escopo:**
- Handshake de avistamento mútuo (par A-B) e troca de células conhecidas
- `@OPERATION merge_frame(dX, dY)` em SharedMap + re-emissão de obs properties
- Cenário `05-map-merge` (2 agentes) e nova métrica `requests_ok`
- JUnit `SightingHandshakeTest`

**Fora de escopo:**
- Propagação transitiva (A fundiu com B, B fundiu com C → A conhece células de C automaticamente): fase posterior; o handshake par-a-par se repete quando A e C se avistarem
- Resolução das dimensões reais do grid 70×70 a partir das células fundidas (Issue #31 / U4 deferida)
- Fix do freeze mod-10 (#56): causa raiz independente; quando U9 estiver pronto, DX deixa de ser -1 e o freeze desaparece naturalmente
- `communication.asl` FIXME (cross-frame connect requests): desbloqueado por U9 mas não corrigido neste escopo

### Deferred to Follow-Up Work
- Propagação transitiva de mapas (C herda conhecimento de A via B)
- Fusão de obstacles (obstáculos observados por B traduzidos ao frame de A)
- Escalonar handshake para 15 agentes: testar 05-map-merge com 3+ agentes, verificar sem corrida de merge

---

## Implementation Units

### U1. Java — `merge_frame` @OPERATION + `computeSightingOffset` em SharedMap

**Goal:** Expor `translateCells` como @OPERATION chamável por ASL e adicionar o método estático de cálculo de offset.

**Requirements:** R2, R7

**Dependencies:** nenhuma

**Files:**
- Modify: `src/env/env/SharedMap.java`
- Create: `src/test/java/env/SightingHandshakeTest.java`

**Approach:**
Adicionar logo acima de `translateCells`:

```java
// @OPERATION wrapper p/ U9 (ASL-callable). Chama translateCells e re-emite
// as obs properties known_* que ficam stale após a translação in-place.
@OPERATION
void merge_frame(Object odX, Object odY) {
    int dX = toInt(odX), dY = toInt(odY);
    translateCells(dX, dY);
    // re-emite known_dispenser: remove stale e redefine do estado atual
    removeObsPropertyByTemplate("known_dispenser", null, null, null);
    for (String k : knownDispensers) {
        int ci = k.indexOf(','), co = k.indexOf(':');
        int x = Integer.parseInt(k.substring(0, ci));
        int y = Integer.parseInt(k.substring(ci + 1, co));
        String details = k.substring(co + 1);
        defineObsProperty("known_dispenser", x, y, details);
    }
    removeObsPropertyByTemplate("known_goal_zone", null, null);
    for (String k : knownGoalZones) {
        int ci = k.indexOf(',');
        defineObsProperty("known_goal_zone",
            Integer.parseInt(k.substring(0, ci)),
            Integer.parseInt(k.substring(ci + 1)));
    }
    removeObsPropertyByTemplate("known_role_zone", null, null);
    for (String k : knownRoleZones) {
        int ci = k.indexOf(',');
        defineObsProperty("known_role_zone",
            Integer.parseInt(k.substring(0, ci)),
            Integer.parseInt(k.substring(ci + 1)));
    }
    cachedFrontiers = new ArrayList<>();
    lastFrontierVisitedSize = -1;
}
```

Adicionar método estático package-private (testável sem CArtAgO):
```java
static int[] computeSightingOffset(int ax, int ay, int relX, int relY, int bx, int by) {
    return new int[]{ax + relX - bx, ay + relY - by};
}
```

**Patterns to follow:** `shiftKey`, `shiftDispensers` em SharedMap.java para o parsing `"x,y:details"`.

**Test scenarios:**
- `computeSightingOffset(8, 0, 2, 0, -8, 0)` → `[18, 0]` (cenário do 05-map-merge)
- `computeSightingOffset(0, 0, 3, 2, 0, 0)` com B viu A em (-3,-2) → offset `[3, 2]` (issue #17 exemplo)
- Simetria: `computeSightingOffset(Ax,Ay,rX,rY,Bx,By) = -computeSightingOffset(Bx,By,rBx,rBy,Ax,Ay)` (offset inverso)
- Zero offset: quando A e B estão no mesmo frame (ambos spawn no mesmo ponto)

**Verification:** `~/tools/gradle-8.10/bin/gradle test --tests "env.SightingHandshakeTest"` PASS (todos os cenários acima).

---

### U2. ASL — captura de avistamento em `perception.asl`

**Goal:** Quando o agente percebe uma entidade do próprio time, registra o avistamento e faz broadcast para o handshake.

**Requirements:** R1, R4

**Dependencies:** U1

**Files:**
- Modify: `src/agt/common/perception.asl`

**Approach:**
Inserir ANTES do handler genérico `+thing(X, Y, Type, Details) : my_pos(MX, MY) <- ...` (linhas 122-125):

```prolog
// U9: captura avistamento de colega (entity do próprio time) para handshake de fusão.
// Roda ANTES do handler genérico (ordem de seleção Jason). Guard `not (X==0 & Y==0)`
// exclui self-percept (não existe no MASSim mas previne acidente).
+thing(X, Y, entity, Team)
    : my_pos(MX, MY) & my_team(Team) & step(S) & not (X == 0 & Y == 0)
    <- update_cell(MX + X, MY + Y, entity, Team);
       !mark_entity_occupancy(entity, Team, X, Y, MX, MY);
       -saw_teammate_at(S, _, _);                    // descarta step anterior se houver
       +saw_teammate_at(S, X, Y);
       .my_name(Me);
       .broadcast(tell, frame_pos(S, Me, MX, MY, X, Y)).
```

`-saw_teammate_at(S, _, _)` limpa sighting de mesmo step antes de adicionar novo — simplifica o caso de múltiplos colegas visíveis (mantém apenas o último, suficiente para o handshake do par mais recente). Para 2 agentes no 05-map-merge, sempre unambíguo.

**Patterns to follow:** handler `+thing(X, Y, Type, Details)` existente (perception.asl:122-125); pattern de guard de my_team em `+!mark_entity_occupancy` (perception.asl:134-138).

**Test scenarios:**
- Parse: no cenário 05-map-merge, após step onde A vê B, a crença `saw_teammate_at(S, rX, rY)` deve existir (verificado indiretamente pelo PASS do cenário)
- Regressão: handler genérico ainda dispara para entidades não-colega (enemy entities, blocks)

**Verification:** `regression.sh` PASS (sem regressão em cenários existentes).

---

### U3. ASL — `frame_sync.asl` (handshake de fusão)

**Goal:** Planos de resposta ao handshake: handler do broadcast, exportação de células, ingestão.

**Requirements:** R1, R2, R3, R4, R5

**Dependencies:** U1, U2

**Files:**
- Create: `src/agt/common/frame_sync.asl`
- Modify: `src/agt/hive_agent.asl` (adicionar `{ include("common/frame_sync.asl") }`)

**Approach:**

```prolog
// ============================================================
// frame_sync.asl — U9: fusão de mapa por avistamento mútuo
// ============================================================

// Handler do broadcast de avistamento. Condições:
//  1. Mesmo step S (sighting fresco)
//  2. Eu também vi um colega nesse step (saw_teammate_at)
//  3. Verificação geométrica: o avistamento é mútuo (anti-pares cruzados)
//  4. Ainda não fundi com esse peer
+frame_pos(S, Peer, Bx, By, rBx, rBy)[source(Peer)]
    : my_pos(MX, MY) & step(S)
      & saw_teammate_at(S, rX, rY)
      & (rX + rBx) == 0 & (rY + rBy) == 0
      & not already_merged_with(Peer)
    <- DX = MX + rX - Bx;
       DY = MY + rY - By;
       +already_merged_with(Peer);
       .send(Peer, tell, export_cells(Me, DX, DY));
       .print("[MERGE] Fusão com ", Peer, " offset=(", DX, ",", DY, ")").

+frame_pos(_, _, _, _, _, _) <- true.   // descarta frames de outros steps ou já fundidos

// Exporta células brutas (no próprio frame) para o solicitante.
// DX,DY = offset que o solicitante vai aplicar ao ingeri-las.
+export_cells(Requester, DX, DY)[source(Requester)]
    <- .findall(d(X,Y,T), known_dispenser(X,Y,T), Ds);
       .findall(g(X,Y), known_goal_zone(X,Y), GZs);
       .findall(r(X,Y), known_role_zone(X,Y), RZs);
       .send(Requester, tell, peer_cells_raw(Ds, GZs, RZs, DX, DY));
       .print("[MERGE] Enviando ", .length(Ds,LD), LD, " dispensers + zonas a ", Requester).

+export_cells(_, _, _) <- true.

// Ingere células do colega, traduzindo por (DX, DY) ao inserir.
+peer_cells_raw(Ds, GZs, RZs, DX, DY)[source(_)]
    <- for (.member(d(X,Y,T), Ds)) {
           TX = X + DX; TY = Y + DY;
           update_cell(TX, TY, dispenser, T)
       };
       for (.member(g(X,Y), GZs)) {
           TX = X + DX; TY = Y + DY;
           update_cell(TX, TY, "goal_zone", "")
       };
       for (.member(r(X,Y), RZs)) {
           TX = X + DX; TY = Y + DY;
           update_cell(TX, TY, "role_zone", "")
       };
       .print("[MERGE] Mapa fundido: ", .length(Ds,LD), LD, " dispensers importados").

+peer_cells_raw(_, _, _, _, _) <- true.
```

**Patterns to follow:** `communication.asl` (pattern de `.send` / handlers com source annotation); `perception.asl` (guards de my_team).

**Test scenarios:**
- Cenário 05-map-merge: após cruzamento, agente A encontra `known_dispenser` do lado de B e emite `request` bem-sucedido
- `already_merged_with(B)` presente após primeiro handshake → sem re-fusão no step seguinte
- Handler `+frame_pos` sem `saw_teammate_at` compatível: cai na regra fallback (`<- true`), não suspende

**Verification:** `run-hive.sh run --scenario 05-map-merge --assert` → PASS (`requests_ok >= 1`).

---

### U4. Cenário `05-map-merge` + métrica `requests_ok`

**Goal:** Fixture determinística que isola a fusão de mapa (dois agentes, dispensers em lados opostos, absolutePosition:false) e assert mensurável.

**Requirements:** R6, R7 (indiretamente R3 via assert de navegação)

**Dependencies:** U1, U2, U3

**Files:**
- Create: `conf/scenarios/05-map-merge.json`
- Create: `conf/scenarios/setup/05-map-merge.txt`
- Modify: `conf/scenarios/README.md` (adicionar `requests_ok` à tabela de métricas)
- Modify: `.claude/skills/run-hive/analyzers/assert_metric.py` (adicionar `m_requests_ok`)

**Approach:**

`05-map-merge.json` — config chave:
```json
{
  "match": [{
    "steps": 100,
    "randomSeed": 42,
    "randomFail": 0,
    "entities": { "standard": 2 },
    "absolutePosition": false,
    "grid": { "height": 15, "width": 25, "goals": {"number":1}, "roleZones": {"number":1} },
    "dispensers": [0, 0],
    "tasks": { "size":[1,1], "concurrent":1, "iterations":[5,8], "maxDuration":[200,400] },
    "events": {"chance": 0},
    "regulation": {"chance": 0},
    "roles": [ ... mesmo padrão de outros cenários ... ],
    "setup": "../../conf/scenarios/setup/05-map-merge.txt"
  }],
  "assert": { "metric": "requests_ok", "min": 1 },
  "//": "Cenário 05-map-merge (U9 / issue #17). 2 agentes absolutePosition:false, grid 25x15. agentA1 começa à esquerda (2,7), dispenser_b0 visível só pelo lado esquerdo; agentA2 começa à direita (22,7), dispenser_b1 visível só pelo lado direito. Caminham em direção um ao outro, se avistam (~step 8), executam handshake U9, cada um conhece o dispenser do colega e faz request bem-sucedido. ASSERT: requests_ok >= 1."
}
```

`05-map-merge.txt` — setup fixture:
```
# posiciona agentA1 à esquerda, agentA2 à direita
move 2 7 agentA1
move 22 7 agentA2
# role-zone e goal-zone no centro
terrain 12 7 role
terrain 13 7 goal
# dispenser b0 à esquerda (só visível por A1 inicialmente)
add 1 7 dispenser b0
# dispenser b1 à direita (só visível por A2 inicialmente)
add 23 7 dispenser b1
# task com 1 bloco b0 a offset (1,0) do submit (formato: bx,by,tipo)
create task t1 200 1,0,b0
```

`assert_metric.py` — nova função:
```python
def m_requests_ok(results, spec=None):
    """Total de request actions com resultado success (navegar ao dispenser correto)."""
    total = sum(
        sum(1 for r in d["rows"] if r[5] == "request" and r[6] == "success")
        for d in results.values()
    )
    return total, f"{total} requests bem-sucedidos no time"
```

Adicionar `"requests_ok": m_requests_ok` ao dicionário `METRICS`.

**Patterns to follow:** `06-single-block.json` + `setup/06-single-block.txt` (padrão de fixture + assert); `m_submits_ok` em `assert_metric.py` (padrão de nova métrica).

**Test scenarios:**
- Sem U9: `requests_ok == 0` (cenário deve FAIL antes da implementação — confirma que o fix resolve)
- Com U9: `requests_ok >= 1` (agente navegou ao dispenser correto e conseguiu o bloco)
- Fixture smoke: servidor inicia, agentes conectam, steps rodam sem crash (test de infra)

**Verification:** `run-hive.sh run --scenario 05-map-merge --assert` → `PASS: requests_ok=N >= 1`.

---

## Open Questions (deferred to implementation)

- **Como `removeObsPropertyByTemplate` se comporta quando não há obs property do tipo?** CArtAgO pode lançar exceção. Envolver em try-catch ou verificar antes. Confirmar durante implementação de U1.
- **Múltiplos colegas visíveis no mesmo step:** a regra `-saw_teammate_at(S, _, _); +saw_teammate_at(S, X, Y)` mantém apenas o mais recente. Se dois colegas forem visíveis simultaneamente, o handshake pode ocorrer com ambos em steps diferentes. Testar no cenário oficial (15 agentes) pós-entrega do 05-map-merge.
- **Timing: `frame_pos` pode chegar ANTES de `saw_teammate_at(S,...)`** se o cycle do agente receptor processa mensagens antes de percepts no mesmo step. Se isso ocorrer, o handler cai na regra fallback. Mitigação: checar se basta aguardar — o colegas vão se reavistarem steps seguintes (guard `already_merged_with` não existe ainda para o par, então a tentativa seguinte funciona).

---

## Risks & Dependencies

| Risco | Mitigação |
|---|---|
| `removeObsPropertyByTemplate` sem match → exceção CArtAgO | try-catch no `merge_frame` @OPERATION |
| MASSim limita `entities: {standard: 2}` mas requer mínimo distinto | testar com config antes de fixar o fixture |
| `for` loop Jason com lista grande de células pode bloquear step | para 05-map-merge (< 20 células): negligível; para 15 agentes: medir latência pós-entrega |
| `.findall(known_dispenser,...)` em Jason retorna beliefs do artefato em foco — se vários artefatos focados tiverem `known_dispenser` obs properties, pode poluir | cada agente foca APENAS o próprio `"map_"+Me`; sem foco cruzado pré-U9 |

---

## Sources & Research

- Issue #17 (MarceloNG/PCS5703-MAS-HIVE): scope original, referência LI(A)RA
- `docs/solutions/architecture-patterns/cross-frame-sharedmap-breaks-official-config.md`: root cause documentado, exemplos de frames, evidência A3/A8
- `src/env/env/SharedMap.java:825-888`: `translateCells` existente, comentário explicando ausência de @OPERATION
- `src/test/java/env/SharedMapRelativeTest.java`: testes de translateCells (não regredir)
- `src/agt/common/communication.asl:5-9`: FIXME apontando U9 como pré-requisito para coords válidas em connect
- `src/agt/common/shared_map_init.asl`: confirmação da arquitetura por-agente (`makeArtifact("map_"+Me, ...)`)
- Replay 2026-06-20 (OfficialRolesConfig, 300 steps): evidência quantitativa (169/107 `failed_target`)
