---
title: "feat: U9 — fusão de mapa por avistamento mútuo (#17)"
date: 2026-06-20
status: active
issue: "#17"
track: mover-mapear
depth: Standard
deepened: 2026-06-20
---

# feat: U9 — fusão de mapa por avistamento mútuo (#17)

## Summary

Implementa a fusão de mapa cross-agente no modo `absolutePosition:false` (OfficialRolesConfig). Quando dois agentes se avistam mutuamente no mesmo step, calculam o offset entre os frames dead-reckoning, trocam suas células conhecidas (dispensers, goal-zones, role-zones) e cada um ingere os dados do outro traduzidos para o próprio frame via `import_*` @OPERATIONs já presentes na codebase. Após a fusão, cada agente consegue navegar a dispensers que só o colega observou — desbloqueando score na config oficial.

Protocolo adaptado de `src/agt/common/map_merge.asl` (codebase) com correções: trigger por +thing handler (por-step) em vez de sub-goal mod-10; verificação de reciprocidade via percept `thing()` direta (sem belief intermediário); `import_*` @OPERATIONs para ingestão idempotente.

---

## Problem Frame

Em `absolutePosition:false`, cada agente começa com `dr_pos(0,0)` na própria posição de spawn (`perception.asl:22`) e integra deslocamentos a cada move. Dois agentes com spawns distintos têm origens distintas: a célula `(5,3)` no frame do agente A é uma célula absoluta diferente da `(5,3)` no frame do agente B.

O SharedMap é privado por agente (`"map_"+Me`, `shared_map_init.asl`), mas todas as escritas usam `MX+X, MY+Y` onde `(MX,MY)` é o `my_pos` do agente (seu frame DR). Quando B consulta `get_nearest_dispenser` com as próprias coordenadas, recebe posições que foram armazenadas por A no frame de A — coordenada errada, célula vazia, `failed_target`. Resultado: score 0 mesmo com workers adotados (A3: 170 `request`, 169 `failed_target`; A8: 107 `request`, 105 `failed_target` — replay 2026-06-20).

A causa raiz e evidências estão documentadas em `docs/solutions/architecture-patterns/cross-frame-sharedmap-breaks-official-config.md`. A infra de tradução (`translateCells`) existe em `SharedMap.java:858` como método package-private. Os @OPERATIONs `import_dispenser`, `import_goal_zone`, `import_role_zone` (SharedMap.java linhas ~627-661) já emitem `defineObsProperty` e garantem idempotência — são a infra correta para ingestão de células externas.

Referências: LI(A)RA `synchronism.asl` — avistamento mútuo → broadcast → offset → tradução. Protocolo existente em `src/agt/common/map_merge.asl` (adaptado aqui).

---

## Requirements

| ID | Requisito |
|---|---|
| R1 | Dois agentes com `absolutePosition:false` que se avistam mutuamente em step S calculam o offset entre frames DR sem intervenção externa |
| R2 | Cada agente ingere as células conhecidas do colega traduzidas para o próprio frame — obs properties `known_dispenser`/`known_goal_zone`/`known_role_zone` atualizadas via `import_*` @OPERATIONs |
| R3 | Após fusão, A* traça rota válida até um dispenser que só o colega observou |
| R4 | Verificação geométrica de reciprocidade: ao receber `i_see_mate(Peer, S, Px, Py, RelX, RelY)`, o receptor confirma `thing(-RelX, -RelY, entity, _)` antes de calcular offset — evita falso match com múltiplos colegas visíveis |
| R5 | `known_offset(Peer, DX, DY)` persistente impede re-handshake desnecessário com o mesmo par |
| R6 | Cenário determinístico `05-map-merge` (2 agentes, `absolutePosition:false`, dispensers em lados opostos, barreira central) PASS com assert `requests_ok >= 1` |
| R7 | JUnit `SightingHandshakeTest` PASS: dado par (A viu B em (3,2); B viu A em (-3,-2)) → offset calculado corretamente |
| R8 | Sem regressão nos cenários existentes da `regression.sh` |

---

## Key Technical Decisions

**KT1 — Troca de células por mensagem Jason + `import_*` @OPERATIONs:** Cada agente tem seu próprio artefato SharedMap. A abordagem: broadcast `i_see_mate` → receptor verifica reciprocidade e calcula offset → solicita discoveries → emissor envia células no próprio frame → receptor ingere via `import_dispenser(TX, TY, T)` / `import_goal_zone(TX, TY)` / `import_role_zone(TX, TY)` (não `update_cell`). Os `import_*` @OPERATIONs aplicam `normX/normY`, garantem idempotência via Sets e emitem `defineObsProperty` corretamente — já existem em SharedMap.java.

**KT2 — `merge_frame` DEFERIDO para issue própria:** `merge_frame` (wrapper de `translateCells` para reset in-place do frame do próprio agente) não é necessário para o protocolo de troca de células (KT1). Os `import_*` @OPERATIONs já gerenciam as obs properties na ingestão. `merge_frame` entra em escopo apenas quando "resetar o próprio frame" tiver requisito concreto (issue futura).

**KT3 — Verificação de reciprocidade via percept `thing()` direto:** Ao receber `i_see_mate(Peer, S, Px, Py, RelX, RelY)`, o receptor verifica `thing(-RelX, -RelY, entity, _)` no percept atual. Isso é geometricamente equivalente a `rX + rBx == 0` mas elimina o need de um belief intermediário step-tagged (`saw_teammate_at`) — evita o risco de timing (mensagem chegando antes ou depois do ciclo de percepção) e o problema de slot único com múltiplos colegas visíveis.

**KT4 — `computeSightingOffset` como método estático package-private em SharedMap.java:** fórmula: quando receptor (Bx, By) recebe `i_see_mate` de A (Ax, Ay) que viu B em (RelX, RelY), o receptor verifica thing(-RelX, -RelY), usa ExpRX=-RelX, ExpRY=-RelY, e calcula `dX = Bx + ExpRX - Ax = Bx - RelX - Ax`. Isso converte coordenadas do frame de A para o frame de B: `TX = X_A + dX`. Colocado em SharedMap para ser testável por JUnit sem CArtAgO.

**KT5 — Protocolo `i_see_mate` por trigger de percept (não sub-goal mod-10):** O handler `+thing(X,Y,entity,Team)` em `perception.asl` detecta o avistamento e faz `.broadcast(tell, i_see_mate(Me, S, MX, MY, X, Y))`. Isso dispara por-step em cada avistamento (sem throttle mod-10), naturalmente por-colega (cada +thing event é um agente específico no percept), e integrado ao ciclo de percepção já existente. O handler de `+i_see_mate` em `map_merge.asl` verifica reciprocidade com `thing()` direto no mesmo ciclo.

**KT6 — `known_offset(Peer, DX, DY)` como estado de fusão permanente:** ao contrário de `already_merged_with`, armazena o offset calculado, que pode ser consultado para re-importar células em steps futuros. Para o escopo de U9 (par A-B, 05-map-merge), não há expiração — re-sighting do mesmo par reaproveita o offset. Expiração/re-calibração por DR drift fica para issue futura.

**KT7 — Nova métrica `requests_ok` em `assert_metric.py`:** `sum(1 for r in rows if r[5]=="request" and r[6]=="success")`. Evidência direta de que A* navegou ao dispenser correto. A fixture de U4 inclui barreira central para garantir que o sucesso exige fusão (não exploração aleatória).

---

## High-Level Technical Design

### Protocolo `i_see_mate` (adaptado de map_merge.asl existente)

```mermaid
sequenceDiagram
    participant A as agentA (dr_pos Ax,Ay)
    participant B as agentB (dr_pos Bx,By)

    Note over A,B: Step S — ambos se avistam mutuamente

    A->>A: +thing(RelX,RelY,entity,team) [U2] → broadcast
    A->>B: .broadcast(tell, i_see_mate(A, S, Ax, Ay, RelX, RelY))

    B->>B: +thing(-RelX,-RelY,entity,team) [U2] → broadcast
    B->>A: .broadcast(tell, i_see_mate(B, S, Bx, By, -RelX, -RelY))

    Note over B: recebe i_see_mate(A,...) — verifica reciprocidade
    B->>B: thing(-RelX, -RelY, entity, _) ✓ (vê A exatamente aí)
    B->>B: DX = Bx + (-RelX) - Ax; +known_offset(A, DX, DY)
    B->>A: .send(A, tell, request_discoveries(B))

    Note over A: recebe i_see_mate(B,...) — verifica reciprocidade (simétrico)
    A->>A: thing(RelX, RelY, entity, _) ✓ (vê B exatamente aí)
    A->>A: DX2 = Ax + RelX - Bx; +known_offset(B, DX2, DY2)
    A->>B: .send(B, tell, request_discoveries(A))

    Note over A: recebe request_discoveries(B)
    A->>B: .send(B, tell, remote_discoveries(A, Disps_A, Goals_A, Roles_A))

    Note over B: recebe request_discoveries(A)
    B->>A: .send(A, tell, remote_discoveries(B, Disps_B, Goals_B, Roles_B))

    Note over B: recebe remote_discoveries(A) — importa com known_offset(A,DX,DY)
    B->>B: import_dispenser(X_A+DX, Y_A+DY, T); import_goal_zone; import_role_zone

    Note over A: recebe remote_discoveries(B) — importa com known_offset(B,DX2,DY2)
    A->>A: import_dispenser(X_B+DX2, Y_B+DY2, T); import_goal_zone; import_role_zone
```

### Cálculo do offset (álgebra)

```
// B recebe i_see_mate(A, S, Ax, Ay, RelX, RelY):
// A está em (Ax, Ay) no seu frame; A viu B em (RelX, RelY) relativo a A.
// B está em (Bx, By) no seu frame; B vê A em (-RelX, -RelY) relativo a B.
//
// Para converter célula (px, py) do frame-A → frame-B:
//   TX = px + DX,  TY = py + DY   onde:
DX_B = Bx + (-RelX) - Ax = Bx - RelX - Ax
// Verificação: A_pos_in_B_frame = Ax + DX_B = Ax + Bx - RelX - Ax = Bx - RelX ✓
//              (B vê A em -RelX relativo a B, i.e., Bx + (-RelX) = Bx - RelX)
//
// Simetria: A computa DX_A = Ax + RelX - Bx = -DX_B ✓  (offset inverso)
```

---

## Scope Boundaries

**Em escopo:**
- `import_dispenser`, `import_goal_zone`, `import_role_zone` @OPERATIONs em SharedMap.java (verificar/documentar; já existem na codebase)
- `computeSightingOffset` método estático em SharedMap.java (novo, testável)
- Handshake `i_see_mate` / `request_discoveries` / `remote_discoveries` em `map_merge.asl` (adaptar de codebase existente)
- Trigger de avistamento em `perception.asl` (+thing handler → broadcast `i_see_mate`)
- Cenário `05-map-merge` (2 agentes, barreira) e nova métrica `requests_ok`
- JUnit `SightingHandshakeTest`

**Fora de escopo:**
- `@OPERATION merge_frame` (wrapper de translateCells para reset in-place do próprio frame): deferido para issue própria
- Propagação transitiva (A fundiu com B, B fundiu com C → A conhece células de C automaticamente): fase posterior
- Resolução das dimensões reais do grid 70×70 a partir das células fundidas (Issue #31)
- Fix do freeze mod-10 (#56): causa raiz independente
- `communication.asl` FIXME (cross-frame connect requests): desbloqueado por U9 mas não corrigido neste escopo
- Fusão de obstacles
- Re-calibração de offset após DR drift longo (follow-up)

### Deferred to Follow-Up Work
- `@OPERATION merge_frame(dX, dY)`: reset in-place do próprio frame (exige removeObsPropertyByTemplate + re-emit; issue com requisito específico)
- Propagação transitiva de mapas (C herda conhecimento de A via B)
- Expiração de `known_offset` após N steps de separação (re-calibração por DR drift)
- Escalonar handshake para 15 agentes: medir latência de message flood (105 broadcasts/step com 15 agentes)

---

## Implementation Units

### U1. Java — `import_*` @OPERATIONs + `computeSightingOffset` em SharedMap

**Goal:** Verificar/documentar os `import_*` @OPERATIONs já existentes e adicionar o método estático de cálculo de offset testável por JUnit.

**Requirements:** R2, R7

**Dependencies:** nenhuma

**Files:**
- Modify: `src/env/env/SharedMap.java`
- Create: `src/test/java/env/SightingHandshakeTest.java`

**Approach:**

Verificar que `import_dispenser(Object ox, Object oy, Object otype)`, `import_goal_zone(Object ox, Object oy)`, `import_role_zone(Object ox, Object oy)` existem em SharedMap.java e estão corretos (normX/normY, Sets de deduplicação, defineObsProperty). Se ausentes ou incompletos, implementar seguindo o padrão abaixo:

```java
@OPERATION
void import_dispenser(Object ox, Object oy, Object otype) {
    int x = normX(toInt(ox)), y = normY(toInt(oy));
    String type = otype.toString();
    String k = x + "," + y, dispKey = k + ":" + type;
    if (knownDispensers.add(dispKey)) {
        cells.put(k, "dispenser:" + type);
        defineObsProperty("known_dispenser", x, y, type);
    }
}
// import_goal_zone e import_role_zone: padrão análogo com knownGoalZones / knownRoleZones
```

Adicionar método estático package-private testável sem CArtAgO:

```java
// Converte coordenadas do frame do emissor para o frame do receptor.
// receptor viu emissor em (-relX,-relY); emissor informou que está em (senderX,senderY).
// dX = receiverX + (-relX) - senderX; célula no frame do emissor → TX = X + dX.
static int[] computeSightingOffset(
        int receiverX, int receiverY,
        int relX, int relY,
        int senderX, int senderY) {
    return new int[]{receiverX - relX - senderX, receiverY - relY - senderY};
}
```

**Patterns to follow:** `shiftKey`, `shiftDispensers` em SharedMap.java para parsing `"x,y:details"`; padrão `normX/normY` nos @OPERATIONs existentes.

**Test scenarios (SightingHandshakeTest):**
- `computeSightingOffset(Bx=0, By=0, relX=5, relY=0, senderX=10, senderY=0)` → `[-15, 0]`
  (B em 0, vê A em -5 rel; A declarou estar em 10 → DX = 0 - 5 - 10 = -15; cell A em X=10: TX=10-15=-5=B_pos ✓)
- `computeSightingOffset(Bx=0, By=0, relX=-3, relY=-2, senderX=3, senderY=2)` → `[0, 0]` (offset zero: A e B no mesmo frame)
- Simetria: `computeSightingOffset(Ax,Ay,relBx,relBy,Bx,By) = -computeSightingOffset(Bx,By,relX,relY,Ax,Ay)` onde relBx=-relX, relBy=-relY
- Sem regressão em `SharedMapRelativeTest` (translateCells original)

**Verification:** `~/tools/gradle-8.10/bin/gradle test --tests "env.SightingHandshakeTest"` PASS.

---

### U2. ASL — trigger de avistamento em `perception.asl`

**Goal:** Quando o agente percebe uma entidade do próprio time, faz broadcast `i_see_mate` para iniciar o handshake de fusão.

**Requirements:** R1, R4

**Dependencies:** U1

**Files:**
- Modify: `src/agt/common/perception.asl`

**Approach:**

Inserir **ANTES** do handler genérico `+thing(X, Y, Type, Details) : my_pos(MX, MY) <- ...` (atualmente na linha 122 de perception.asl). A ordem é obrigatória: Jason seleciona o primeiro plano aplicável; o catch-all unifica com qualquer `thing` incluindo `entity` — se vier depois, este handler nunca dispara.

```prolog
// U9: ao perceber colega do mesmo time, broadcast i_see_mate para handshake de fusão.
// Guard `not (X==0 & Y==0)` exclui self-percept (precaução; MASSim não envia).
+thing(X, Y, entity, Team)
    : my_pos(MX, MY) & my_team(Team) & step(S) & not (X == 0 & Y == 0)
    <- update_cell(MX + X, MY + Y, entity, Team);
       !mark_entity_occupancy(entity, Team, X, Y, MX, MY);
       .my_name(Me);
       .broadcast(tell, i_see_mate(Me, S, MX, MY, X, Y)).
```

Não armazena belief `saw_teammate_at` — a verificação de reciprocidade é feita via `thing()` direto no handler de `+i_see_mate` (U3), no mesmo ciclo de percepção, eliminando risco de timing e slot único.

**Patterns to follow:** handler `+thing(X, Y, Type, Details)` existente (perception.asl:122-125); guard `my_team(Team)` em `+!mark_entity_occupancy` (perception.asl:134-138).

**Test scenarios:**
- No 05-map-merge: após step onde A vê B, `i_see_mate(A, S, Ax, Ay, RelX, RelY)` deve chegar a B (verificado indiretamente pelo PASS do cenário)
- Handler genérico ainda dispara para entidades não-colega (enemy, blocks) — não há regressão
- `regression.sh` PASS: nenhum cenário existente usa `thing(X,Y,entity,Team)` de forma que seja conflitante

**Verification:** `regression.sh` PASS (sem regressão em cenários existentes).

---

### U3. ASL — `map_merge.asl` (handshake de fusão)

**Goal:** Adaptar/criar `map_merge.asl` com o protocolo `i_see_mate` → `request_discoveries` → `remote_discoveries` usando `import_*` @OPERATIONs para ingestão.

**Requirements:** R1, R2, R3, R4, R5

**Dependencies:** U1, U2

**Files:**
- Create/Replace: `src/agt/common/map_merge.asl`
- Modify: `src/agt/hive_agent.asl` (adicionar `{ include("common/map_merge.asl") }` se ausente)

**Approach:**

```prolog
// ============================================================
// map_merge.asl — U9: fusão de mapa por avistamento mútuo
// Protocolo: i_see_mate → reciprocidade via thing() → known_offset
//            → request_discoveries → remote_discoveries → import_*
// ============================================================

// Recebe broadcast de avistamento. Verifica reciprocidade via percept direto.
// DX calculado para converter FRAME DO EMISSOR → FRAME DO RECEPTOR.
+i_see_mate(PeerName, S, PeerX, PeerY, RelX, RelY)[source(PeerName)]
    : my_pos(MX, MY) & my_team(MyTeam)
      & thing(ExpRX, ExpRY, entity, MyTeam)
      & ExpRX == -RelX & ExpRY == -RelY
      & not known_offset(PeerName, _, _)
    <- DX = MX + ExpRX - PeerX;
       DY = MY + ExpRY - PeerY;
       +known_offset(PeerName, DX, DY);
       .send(PeerName, tell, request_discoveries(Me));
       .print("[MERGE] Offset com ", PeerName, ": (", DX, ",", DY, ") step=", S).

// Fallback: reciprocidade não verificada ou offset já conhecido
+i_see_mate(_, _, _, _, _, _)[source(_)] <- true.

// Exporta discoveries ao solicitante
+request_discoveries(RequesterName)[source(RequesterName)]
    <- .abolish(request_discoveries(RequesterName)[source(_)]);
       .findall(disp(X,Y,T), known_dispenser(X,Y,T), Disps);
       .findall(gz(X,Y), known_goal_zone(X,Y), Goals);
       .findall(rz(X,Y), known_role_zone(X,Y), Roles);
       .my_name(Me);
       .send(RequesterName, tell, remote_discoveries(Me, Disps, Goals, Roles)).

// Ingere discoveries do colega, traduzindo ao próprio frame via import_* @OPERATIONs
+remote_discoveries(SenderName, Disps, Goals, Roles)[source(_)]
    : known_offset(SenderName, DX, DY)
    <- .abolish(remote_discoveries(SenderName, _, _, _)[source(_)]);
       for (.member(disp(X,Y,T), Disps)) {
           TX = X + DX; TY = Y + DY;
           import_dispenser(TX, TY, T)
       };
       for (.member(gz(X,Y), Goals)) {
           TX = X + DX; TY = Y + DY;
           import_goal_zone(TX, TY)
       };
       for (.member(rz(X,Y), Roles)) {
           TX = X + DX; TY = Y + DY;
           import_role_zone(TX, TY)
       };
       .length(Disps, LD); .length(Goals, LG); .length(Roles, LR);
       .print("[MERGE] Importei de ", SenderName, ": ", LD, "d ", LG, "g ", LR, "r").

// Fallback: sem known_offset — descarta (offset não estabelecido ainda)
+remote_discoveries(_, _, _, _)[source(_)] <- true.
```

**Patterns to follow:** `communication.asl` (pattern `.send` / `.abolish` de mensagens); padrão `import_*` em SharedMap.java (U1).

**Test scenarios:**
- Cenário 05-map-merge: após cruzamento com barreira, A encontra `known_dispenser` do lado de B e emite `request` bem-sucedido
- `known_offset(B, DX, DY)` presente após primeiro handshake → re-sighting reusar offset (sem novo request_discoveries)
- Handler `+i_see_mate` sem `thing()` compatível: cai no fallback `<- true`, não suspende agente

**Verification:** `run-hive.sh run --scenario 05-map-merge --assert` → PASS (`requests_ok >= 1`).

---

### U4. Cenário `05-map-merge` + métrica `requests_ok`

**Goal:** Fixture determinística que isola a fusão de mapa (dois agentes, dispensers em lados opostos, barreira central que impede exploração direta) e assert mensurável que prova fusão — não exploração aleatória.

**Requirements:** R6 (indiretamente R3 via assert de navegação)

**Dependencies:** U1, U2, U3

**Files:**
- Create: `conf/scenarios/05-map-merge.json`
- Create: `conf/scenarios/setup/05-map-merge.txt`
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
    "roles": [ "... mesmo padrão dos outros cenários ..." ],
    "setup": "../../conf/scenarios/setup/05-map-merge.txt"
  }],
  "assert": { "metric": "requests_ok", "min": 1 },
  "//": "05-map-merge (U9/#17). 2 agentes absolutePosition:false, grid 25x15. A1 em (1,7), A2 em (23,7). Barreira vertical em x=12 bloqueia exploração direta (agentes cruzam apenas pelo corredor em y=7). Dispenser_b0 em (0,7) visível só por A1; dispenser_b1 em (24,7) visível só por A2. Sem fusão: A* de A1 nunca alcança b1 nem vice-versa → requests_ok=0. Com fusão: request bem-sucedido de cada agente ao dispenser do colega."
}
```

`05-map-merge.txt` — setup fixture:
```
# agentes nas extremidades
move 1 7 agentA1
move 23 7 agentA2
# role-zone e goal-zone no centro (corredor de cruzamento)
terrain 12 7 role
terrain 12 8 goal
# barreira vertical em x=12 exceto corredor em y=7 (os agentes se cruzam ali)
terrain 12 1 obstacle
terrain 12 2 obstacle
terrain 12 3 obstacle
terrain 12 4 obstacle
terrain 12 5 obstacle
terrain 12 6 obstacle
terrain 12 9 obstacle
terrain 12 10 obstacle
terrain 12 11 obstacle
terrain 12 12 obstacle
terrain 12 13 obstacle
# dispensers nas extremidades (visíveis apenas do próprio lado)
add 0 7 dispenser b0
add 24 7 dispenser b1
# task com 1 bloco b0
create task t1 200 1,0,b0
```

A barreira em x=12 garante que A1 (lado esquerdo) não pode explorar até o dispenser b1 (x=24) sem fusão; analogamente para A2. O corredor em y=7 é onde os agentes se cruzam, ativando o handshake.

`assert_metric.py` — nova função:
```python
def m_requests_ok(results, spec=None):
    """Total de request actions com resultado success (dispenser correto atingido)."""
    total = sum(
        sum(1 for r in d["rows"] if r[5] == "request" and r[6] == "success")
        for d in results.values()
    )
    return total, f"{total} requests bem-sucedidos no time"
```

Adicionar `"requests_ok": m_requests_ok` ao dicionário `METRICS`.

**Patterns to follow:** `conf/scenarios/06-single-block.json` + `setup/06-single-block.txt` (padrão de fixture + assert); `m_submits_ok` em `assert_metric.py` (padrão de nova métrica); `terrain X Y obstacle` para barreira.

**Test scenarios:**
- Sem U9 (baseline): `requests_ok == 0` — confirma que sem fusão o cenário FAIL (barreira impede exploração direta)
- Com U9: `requests_ok >= 1` — agente navegou ao dispenser do colega via A* com conhecimento fundido
- Smoke de infra: servidor inicia, 2 agentes conectam, steps rodam sem crash

**Verification:** `run-hive.sh run --scenario 05-map-merge --assert` → `PASS: requests_ok=N >= 1`.

---

## Open Questions (deferred to implementation)

- **Lifetime de `known_offset`:** sem expiração, re-sighting do mesmo par reutiliza o offset calculado no primeiro avistamento. Para o 05-map-merge (100 steps, 2 agentes) isso é correto. Para 300 steps com 15 agentes, accumulated DR drift pode tornar o offset stale. Medir erro pós-entrega; expirar com janela de N steps se necessário.
- **`thing()` verifica entidade genérica, não identidade:** `thing(ExpRX, ExpRY, entity, MyTeam)` não vincula ao nome de PeerName — se dois colegas estiverem em posições simétricas, pode haver ambiguidade. Para o 05-map-merge (par único) é unambíguo. Para 15 agentes em cluster, investigar se falso match ocorre na prática.
- **Latência de message flood (15 agentes):** até 105 broadcasts `i_see_mate` por step com 15 agentes. Medir impacto no ciclo BDI pós-entrega do 05-map-merge.
- **Barreira via `terrain X Y obstacle` confirmada:** GameState.java:259 valida `equalsIgnoreCase("obstacle")` e cria obstáculo no grid. Fixture pode usar diretamente.

---

## Risks & Dependencies

| Risco | Mitigação |
|---|---|
| `thing(ExpRX, ExpRY, entity, _)` casa com entidade errada se dois colegas visíveis em posição por acaso simétrica | Para 05-map-merge (par único): risco zero. Para 15 agentes: medir frequência no replay oficial pós-entrega |
| MASSim `entities: {standard: 2}` com 15 agentes no `hive.jcm`: connectionA3..A15 não associam entidade, ficam aguardando | EISAccess outer catch previne crash; verificar que 13 agentes sem entidade não levam o JVM a zombie |
| `for` loop Jason com lista grande de células pode atrasar step | Para 05-map-merge (< 20 células): negligível; para 15 agentes (centenas de células): medir latência pós-entrega |
| `terrain X Y obstacle` pode não ser keyword válida no MASSim setup | Checar GameState.java setup parser antes de criar fixture; alternativa: usar distância de exploração como barreira implícita |

---

## Sources & Research

- Issue #17 (MarceloNG/PCS5703-MAS-HIVE): scope original, referência LI(A)RA
- `docs/solutions/architecture-patterns/cross-frame-sharedmap-breaks-official-config.md`: root cause documentado, evidências A3/A8
- `src/env/env/SharedMap.java:858-887`: `translateCells` existente (package-private, não chamado pelo protocolo U9)
- `src/env/env/SharedMap.java:627-661`: `import_dispenser`, `import_goal_zone`, `import_role_zone` @OPERATIONs (infra de ingestão)
- `src/agt/common/map_merge.asl`: protocolo existente na codebase (adaptado em U3)
- `src/test/java/env/SharedMapRelativeTest.java`: testes de translateCells (não regredir)
- `src/agt/common/communication.asl:5-9`: FIXME apontando U9 como pré-requisito
- `src/agt/common/shared_map_init.asl`: arquitetura por-agente (`makeArtifact("map_"+Me, ...)`)
- `conf/scenarios/06-single-block.json`: padrão de fixture + assert
- Replay 2026-06-20 (OfficialRolesConfig, 300 steps): evidência quantitativa (169/107 `failed_target`)
