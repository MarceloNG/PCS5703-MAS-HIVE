---
title: "feat: Navegação robusta — N1 greedy ciente de obstáculo + N2 exploração dirigida por setor"
date: 2026-06-20
status: active
issue: "#49"
origin: "issue #49 — feat(nav): navegação robusta — fallback A* + exploração dirigida por setor (Eixo N)"
---

# feat: Navegação robusta — N1 greedy ciente de obstáculo + N2 exploração dirigida por setor

**Issue:** #49 · **Frente:** mover-mapear · **Data:** 2026-06-20

## Sumário

Corrige dois gargalos de navegação: `failed_path` 54% dos steps com bloco (N1) e somente
6/15 agentes alcançando role-zone no OfficialRolesConfig 70×70 (N2).

**N1** torna o `greedy()` do cliff `mDist > 60` em `SharedMap.astar()` ciente de obstáculo —
escolhe a direção livre em vez de reta cega. **N2** substitui a exploração por frontier+A*
(que sofre do mesmo cliff) por uma caminhada Bug0: cada agente segue sua direção primária
(N/S/E/W por ID), contornando bloqueios CW sem A*.

N3 (approach com clearance na goal-zone) é adiado — risco de conflito com #51.

---

## Problema

**N1 — greedy cego causa 54% failed_path no oficial:**
```
SharedMap.java:581:  if (mDist > 60) return greedy(fx, fy, tx, ty);
SharedMap.java:655:  private String greedy(...) { /* retorna maior componente sem checar obstáculos */ }
```
No grid 70×70 toroidal (dist máx ≈ 70) → quase todos destinos distantes caem no cliff →
`greedy()` anda em linha reta e bate em obstáculo → `failed_path`. Evidência:
`agentA14: move:298 failed_path:162 (54%)`.

**N2 — exploração por frontier sofre do mesmo cliff:**
`get_nearest_frontier_biased` já atribui setor por `agentIndex % 4` (N/E/S/W), mas a
navegação até a fronteira distante usa `compute_next_move` (→ `astar`) → mesma falha.
Resultado: 6/15 agentes alcançam role-zone em 300 steps (baseline 2026-06-20).

---

## Requisitos

- **R1** — N1: `greedyAware()` deve escolher a direção livre (sem obstáculo em `obstacles`)
  que minimiza a distância ao alvo; se bloqueada, tentar CW até achar livre.
- **R2** — N1: se todas 4 direções bloqueadas, retornar a direção greedy original (degradação
  graciosa — sem loop infinito).
- **R3** — N2: cada agente calcula `primaryDir ∈ {n, e, s, w}` por `agentIndex % 4`;
  no modo exploração (sem task), move em `primaryDir`.
- **R4** — N2: se `primaryDir` bloqueado, tentar CW (n→e→s→w→n) até achar direção livre.
- **R5** — N2: se todas 4 bloqueadas, emitir `skip`.
- **R6** — N2: `explore_primary_dir` é inicializado uma vez via operação Java e persistido
  como crença; rotação temporal a cada 40 steps de exploração (sem task).
- **R7** — ramo `is_stuck` → `get_escape_target` (ray-cast #27) mantido intacto.
- **R8** — `connect_protocol.asl` e `collection.asl` não são tocados (conflito com #51).
- **R9** — DoD N1: `failed_path < 50/300` por agente com bloco no OfficialRolesConfig.
- **R10** — DoD N2: ≥ 10/15 agentes adotam worker em 300 steps no OfficialRolesConfig.

---

## Decisões Técnicas

### KTD-1 — greedy com lookahead de 1 célula, não BFS limitado

**Decisão:** `greedyAware()` checa `obstacles.containsKey(key(fx+ox, fy+oy))` para cada
direção candidata em ordem CW a partir da direção greedy. Sem BFS.

**Rationale:** BFS limitado ($O(r^2)$) a cada step de 15 agentes é custo desnecessário.
O lookahead de 1 célula é suficiente: o agente recalcula a cada step, então um passo correto
é suficiente para escapar do obstáculo.

### KTD-2 — Bug0 puro (primaryDir + CW) substitui frontier+A* na exploração

**Decisão:** `!do_explore` usa Bug0 — sem `has_destination` durante exploração, sem A*.
O ramo `is_stuck` → escape target (ray-cast) é mantido para bolsões.

**Rationale:** o cliff N1 afeta tanto a navegação com bloco quanto a navegação até fronteiras
distantes. Bug0 é puro-reativo, sem dependência de A*, e distribui os 15 agentes pelo grid.

### KTD-3 — explore_primary_dir como crença persistida

**Decisão:** `explore_primary_dir(D)` é inicializado via `get_explore_dir(Me, D)` (operação
Java) na 1ª execução de `!do_explore` e persistido em BB. Rotação temporal atualiza a crença.

**Rationale:** evita chamada Java a cada step; o índice do agente não muda.

### KTD-4 — threshold do cliff mantido em 60

**Decisão:** não alterar `mDist > 60`. Corrigir o `greedy()` cego é suficiente.

**Rationale:** elevar o threshold sem ampliar o budget A* (8000 nós) causaria timeouts.
O cliff continua funcional: A* para perto, `greedyAware` para longe.

### KTD-5 — N3 adiado

**Decisão:** N3 (approach com clearance na goal-zone) adiado para após #51 mergear.

---

## High-Level Technical Design

### N1 — greedyAware

```
astar(fx,fy,tx,ty):
  mDist = wrappedManhattan
  if mDist > 60 → greedyAware(fx,fy,tx,ty)   // ANTES: greedy() cego
  else → A* normal (< 8000 nós)

greedyAware(fx,fy,tx,ty):
  prefDir = {dir com maior componente manhattan}
  candidatos = [prefDir, CW(prefDir), CW²(prefDir), CW³(prefDir)]  // n→e→s→w→n
  para cada candidato:
    se NOT obstacles.containsKey(key(fx+ox, fy+oy)) → retornar candidato
  retornar prefDir  // degradação: todas bloqueadas
```

### N2 — Bug0 directed walk

```
!do_explore(MX, MY):
  [ramo A] is_stuck=1 → get_escape_target → has_destination → compute_next_move (A*)
  [ramo B] not is_stuck:
    se not explore_primary_dir(_) → get_explore_dir(Me, D) → +explore_primary_dir(D)
    se (step mod 40 == 0) → rotacionar D CW → update explore_primary_dir
    explore_primary_dir(PDir)
    !try_directed_move(PDir):
      para cada dir em [PDir, CW(PDir), CW²(PDir), CW³(PDir)]:
        se not cell_blocked(OX,OY) → action(move(dir)) → return
      action(skip)
```

Ponto chave: Bug0 NÃO define `has_destination` → `check_osc`/`escape_pending` ficam
silenciosos (ambos exigem `has_destination` ativo para disparar).

---

## Escopo

### Dentro deste PR
- `greedyAware()` em `SharedMap.java` (N1)
- `@OPERATION get_explore_dir` em `SharedMap.java` (N2 Java)
- `!do_explore` Bug0 em `navigation.asl` (N2 ASL)
- Testes JUnit para `greedyAware` e `get_explore_dir`

### Fora deste PR
- N3 (clearance approach) → follow-up após #51
- #28 (frontier peer-aware) → downstream de N2
- #42 (cenário contenção) → harness separado

---

## Unidades de Implementação

### U1. N1 — greedyAware em SharedMap.java

**Goal:** substituir `greedy()` (cego) por `greedyAware()` (ciente de obstáculo) nos dois
pontos de cliff do A* (`astar` linha 581 e `astarCost` linha 538).

**Requirements:** R1, R2, R9

**Dependencies:** nenhuma

**Files:**
- `src/env/env/SharedMap.java`
- `src/test/java/env/SharedMapAStarTest.java`

**Approach:**
- Adicionar `private String greedyAware(int fx, int fy, int tx, int ty)` em `SharedMap.java`
- Calcular `prefDir` igual ao `greedy()` atual (maior componente manhattan)
- Verificar `obstacles.containsKey(key(fx+normOx, fy+normOy))` para `prefDir`
- Se bloqueado: tentar as 3 rotações CW em ordem (n→e→s→w)
- Se todas bloqueadas: retornar `prefDir` (degradação)
- Substituir `return greedy(...)` por `return greedyAware(...)` nas linhas 581 e 538
- Manter `greedy()` como método privado ou remover (verificar com grep se outros callers)

**Patterns to follow:**
- `greedy()` existente em `SharedMap.java:655` — base para `greedyAware()`
- Ordem CW: n → e → s → w → n, consistente com `pick_escape` em `navigation.asl:226`

**Test scenarios:**
1. Alvo 65 células ao leste, sem obstáculo: deve retornar `"e"` (igual ao atual)
2. Alvo ao leste, obstacle em (fx+1, fy): deve retornar `"n"` ou `"s"` (CW de E)
3. Alvo ao norte, obstacle em (fx, fy-1) e (fx+1, fy), (fx, fy+1) livre: retorna `"s"`
4. Todas as 4 células adjacentes em obstacles: retorna prefDir (sem pânico, sem loop)
5. Regressão `astar()`: destino a 65 células com obstacle na direção greedy → não deve
   retornar a direção do obstacle

**Verification:** `gradle test` verde; `grep "return greedy(" SharedMap.java` retorna vazio
(nenhum caller de `greedy()` sobrevive em `astar`/`astarCost`).

---

### U2. N2 — @OPERATION get_explore_dir em SharedMap.java

**Goal:** expor operação CArtAgO que retorna `n/e/s/w` baseado em `agentIndex % 4`,
permitindo ao ASL inicializar `explore_primary_dir` sem lógica de string.

**Requirements:** R3, R6

**Dependencies:** nenhuma (pode ser paralelo com U1)

**Files:**
- `src/env/env/SharedMap.java`
- `src/test/java/env/SharedMapAStarTest.java` (ou `SharedMapExploreTest.java`)

**Approach:**
- Adicionar `@OPERATION void get_explore_dir(Object oName, OpFeedbackParam<String> dir)`
- Usar `extractAgentIndex(oName.toString())` já existente (linha 458)
- Mapear: `idx % 4 == 0 → "n"`, `1 → "e"`, `2 → "s"`, `3 → "w"`
- Fallback `idx < 0`: retornar `"n"`

**Patterns to follow:**
- `nearestFrontierBiased` (linha 305) já usa `extractAgentIndex` e `idx % 4` —
  esta operação apenas expõe a direção diretamente para o ASL

**Test scenarios:**
1. `get_explore_dir("agentA0")` → `"n"` (0 % 4 = 0)
2. `get_explore_dir("agentA1")` → `"e"` (1 % 4 = 1)
3. `get_explore_dir("agentA2")` → `"s"` (2 % 4 = 2)
4. `get_explore_dir("agentA3")` → `"w"` (3 % 4 = 3)
5. `get_explore_dir("agentA4")` → `"n"` (4 % 4 = 0)
6. `get_explore_dir("agentA14")` → `"s"` (14 % 4 = 2)
7. `get_explore_dir("hive_agent")` → `"n"` (sem dígitos, fallback)

**Verification:** `gradle test` verde; operação acessível via `get_explore_dir(Me, Dir)` no ASL.

---

### U3. N2 — Bug0 directed walk em navigation.asl

**Goal:** substituir o corpo de `!do_explore` pela caminhada Bug0, eliminando a dependência
de A* durante exploração sem task. Manter o ramo `is_stuck` → escape target intacto.

**Requirements:** R3, R4, R5, R6, R7, R10

**Dependencies:** U2

**Files:**
- `src/agt/common/navigation.asl`

**Approach:**

Reescrever `!do_explore(MX, MY)`:

```
// ramo A (mantido): bolsão → ray-cast escape
is_stuck(1) →
  get_escape_target(MX, MY, FX, FY)
  +has_destination(FX, FY)
  compute_next_move → action(move)

// ramo B (novo): Bug0
not is_stuck:
  // inicializar explore_primary_dir uma vez
  if not explore_primary_dir(_):
      .my_name(Me); get_explore_dir(Me, D0); +explore_primary_dir(D0)
  // rotação temporal R6
  if step(S) & (S mod 40 == 0) & explore_primary_dir(CurDir):
      rotate CW → -explore_primary_dir(CurDir); +explore_primary_dir(NDir)
  // Bug0 move
  explore_primary_dir(PDir)
  !try_directed_move(PDir)
```

`!try_directed_move(PDir)`:
- Testar PDir e 3 rotações CW usando `cell_blocked(OX, OY)` e `dir_off` existentes
- Primeiro não-bloqueado → `action(move(Dir))`
- Todos bloqueados → `action(skip)`

SEM `+has_destination` durante Bug0 → `check_osc`/`escape_pending` ficam silenciosos.
SEM chamada a `get_nearest_frontier_biased` → sem A* durante exploração.

**Patterns to follow:**
- `!pick_escape` (linha 221) — CW order n→e→s→w, reutilizar `cell_blocked` e `dir_off`
- `!boxed_step`/`!shake` existentes como referência para o caso "todos bloqueados"

**Test scenarios** (verificados via cenário com fixture determinística):
1. Agente exploração, Norte livre → move N (via `action("move(n)")`)
2. Agente exploração, N bloqueado por obstacle, E livre → move E
3. Agente exploração, N+E+S+W bloqueados → `action("skip")`
4. Agente com `is_stuck=1` → usa caminho A (escape target via ray-cast), não Bug0
5. Smoke parse: `run-hive --steps 15 --conf conf/OfficialRolesConfig.json` — agentes sobem
6. DoD: replay 300 steps OfficialRolesConfig → ≥ 10/15 workers adotados

**Verification:**
- `explore_primary_dir(D)` presente em BB após 1° step de exploração
- Nenhum `failed_path` durante exploração (Bug0 não chama A*)
- Smoke parse passes (mais crítico para ASL: erro de parse → agentes não sobem → score 0)

---

## Dependências e Sequência

```
U1 (greedyAware Java)     ← independente; gradle test em ms
U2 (get_explore_dir Java) ← independente; gradle test em ms
U3 (Bug0 ASL)             ← depende de U2; smoke via run-hive --steps 15
```

U1 e U2 em paralelo → commit Java. U3 após U2.

---

## Riscos

| Risco | Prob | Mitigação |
|---|---|---|
| `greedyAware` entra em loop CW quando todas bloqueadas | Baixa | R2: degradar p/ prefDir |
| Bug0 sem `has_destination` interage mal com `check_osc` | Baixa | `check_osc` exige `has_destination` ativo |
| Rotação temporal (mod 40) com bloco anexado | Baixa | R6 só roda sem task; bloco só existe com task |
| `explore_primary_dir` persiste erroneamente entre sims | Baixa | Limpar em `hive_agent.asl` init se necessário |
| Conflito com #51 | Resolvido | #51 já mergeu; N8 garante non-touch em connect_protocol |

---

## Assumptions

- A1: Base para a branch é o HEAD atual (`f6c826b`) — #51 já aterrou no `marcelo`.
- A2: OfficialRolesConfig usa 15 agentes (agentA0..A14), 300 steps (baseline 6/15 de 2026-06-20).
- A3: Lookahead de 1 célula em `greedyAware` é suficiente — agente recalcula a cada step.
- A4: Rotação temporal de 40 steps é melhor que "nunca rotacionar" como default inicial.
- A5: N3 (clearance approach) fica para follow-up após #51 mergear.

---

## Deferred

- **N3** (approach com clearance na goal-zone) → follow-up pós-#51
- **#28** (frontier peer-aware) → downstream de N2
- Calibração do parâmetro K de rotação temporal (40 steps) → por evidência de replay

---

## Fontes

- Issue #49 (diagnóstico corrigido 2026-06-20)
- `src/env/env/SharedMap.java` — `astar()`, `astarCost()`, `greedy()`, `nearestFrontierBiased()`,
  `extractAgentIndex()`, `inPreferredDirection()`
- `src/agt/common/navigation.asl` — `!do_explore()`, `cell_blocked`, `dir_off`, `!escape_move`
- `src/agt/common/perception.asl` — `check_osc`, `escape_pending`
- `docs/backlog.md` §P2/P1 (diagnóstico N1/N2)
- `STRATEGY.md` (medir → mudar em isolamento → promover por evidência)
