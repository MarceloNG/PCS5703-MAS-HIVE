---
type: fix
origin: none
status: active
created: 2026-06-19
---

# fix: A* ciente de obstáculos percebidos (issue #15)

## Sumário

O A* do HIVE é cego a obstáculos *visíveis* porque a função `update_cell` escreve
apenas no mapa `cells`, mas `astar()` lê de `obstacles` — que só é populado após
colisão (`failed_path` → `mark_obstacle`). Resultado: o agente *vê* a parede,
caminha em direção a ela, colide, *depois* aprende a desviar.

O fix tem 2 linhas em Java: `update_cell` passa a popular `obstacles` quando o
tipo for `"obstacle"`, usando sentinela `Integer.MAX_VALUE` (nunca decai pelo
filtro `< step - 30`).

Dois cenários minimalistas complementam a correção: um JUnit (rápido, sem sim)
que prova a pipeline percepção→obstáculos em isolamento, e um cenário de sim
(`absolutePosition:false`) que prova "enxerga + desvia" ponta-a-ponta sem
ambiguidade de wrapping toroidal.

---

## Enquadramento do Problema

### Comportamento atual (bug)

```
thing(X, Y, obstacle, _) percebido
  ↓
update_cell(absX, absY, "obstacle", "")
  ↓
cells.put(k, "obstacle:")        ← APENAS cells, não obstacles
  ↓
astar() lê obstacles (vazio)     ← obstáculo invisível ao planejamento
  ↓
agente se move em direção à parede
  ↓
failed_path → mark_obstacle()   ← SÓ AGORA entra em obstacles
```

### Comportamento desejado (após fix)

```
thing(X, Y, obstacle, _) percebido
  ↓
update_cell(absX, absY, "obstacle", "")
  ↓
cells.put(k, "obstacle:")
obstacles.put(k, Integer.MAX_VALUE)  ← permanente, nunca decai
  ↓
astar() lê obstacles (já tem o obstáculo)
  ↓
A* roteia ao redor — zero failed_path na parede
```

### Dois comportamentos distintos para cercar

1. **"Enxerga?"** — obstáculo percebido via percept → marcado em `obstacles`
   *antes* de qualquer colisão (o fix está nessa camada: `update_cell`).
2. **"Desvia?"** — A* usa `obstacles` para planejar rota que evita a parede;
   o agente nunca tenta cruzá-la.

Estes são causalmente encadeados: sem "enxergar" não há "desviar". Mas podem
falhar independentemente — o JUnit cobre "enxerga?" em isolamento; o cenário de
sim cobre "desvia?" ponta-a-ponta.

---

## Requisitos

- R1: Obstáculo percebido (percept `thing(X,Y,obstacle,_)`) deve entrar em
  `SharedMap.obstacles` antes de qualquer colisão.
- R2: `A*` deve rotear ao redor de obstáculos marcados via percepção (não apenas
  por colisão).
- R3: Obstáculos de terreno marcados por percepção nunca devem decair
  (`decay_obstacles` não os remove).
- R4: A API `mark_obstacle` (chamada por `failed_path`) permanece funcional e
  idempotente (já tem guard `containsKey`).
- R5: Dois cenários minimalistas cobrem "enxerga?" (JUnit) e "enxerga+desvia?"
  (sim E2E), com métricas assertáveis.

---

## Decisões Técnicas

**KD1 — Onde fazer o fix: Java side (`update_cell`)**
Alternativas: (a) ASL — handler específico `+thing(X,Y,obstacle,_)` que chama
`mark_obstacle`; (b) Java `update_cell`; (c) `astar()` ler `cells` também.

Escolhemos (b): mais simples, centralizado, zero mudança em ASL. A (a) teria o
problema de timing Jason — o handler cria uma intenção que pode executar *depois*
do `compute_next_move` no mesmo passo, dando um lag de 1 step antes do obstáculo
entrar no planejamento. Com (b), `update_cell` é uma operação CArtAgO síncrona
— ao retornar, `obstacles` já tem o valor; na mesma invocação de `astar()` logo
após, o obstáculo está visível.

**KD2 — Sentinela `Integer.MAX_VALUE` para permanência**
O filtro de decaimento é `e.getValue() < step - 30`. Com `MAX_VALUE`, a condição
nunca é verdadeira. Correto para obstáculos de terreno (não se movem). O
`mark_obstacle` tem guard `containsKey` — chamadas subsequentes por `failed_path`
na mesma célula são idempotentes (não sobrescrevem o sentinela).

**KD3 — `absolutePosition:false` no cenário E2E**
Com `absolutePosition:true` e grid pequeno, o A* toroidal pode escolher rota
que *contorna o mundo* em vez de cruzar a parede — invalidando o cenário. Com
`absolutePosition:false` (dead-reckoning), `gridWidth=gridHeight=0` → `normX/Y`
são identidade → sem wrapping no A*. A rota leste curta sempre vence; a parede
no meio força detour real e mensurável.

**KD4 — Métrica `failed_path_total` no assert**
Métrica `role_adoption` não distingue bug corrigido de não-corrigido (agente chega
à role_zone em ambos, apenas com mais passos). A métrica discriminante é o total
de `failed_path` do time. Adicionar `m_failed_path_total` em `assert_metric.py`
(lê `d["results"].get("failed_path", 0)`) e usar `assert: {"metric":
"failed_path_total", "max": 1}` (ver KD5).

**KD5 — Threshold do assert: `max: 1` (calibrável)**
Com `absolutePosition:false` e 1 agente, o único contexto de `failed_path` é
colisão com terreno. O handler `+thing` e o plano `+step(N)` de navegação são
intenções separadas no scheduler Jason — em geral o scheduler pode executar a
intenção de navegação antes da intenção `+thing`, gerando 1 `failed_path` no
step de primeira percepção. Threshold padrão: `max: 1`. Se empiricamente zero
failed_paths ocorrerem em múltiplos runs consecutivos, reduzir para `max: 0`.

---

## Implementação

### U1. Java: `update_cell` marca obstáculos percebidos

**Goal:** Popular `obstacles` quando tipo for `"obstacle"`, usando sentinela permanente.

**Requirements:** R1, R2, R3, R4

**Dependencies:** —

**Files:**
- `src/env/env/SharedMap.java`

**Approach:** No método `update_cell`, após o bloco `if/else if` existente para
`dispenser`/`goal_zone`/`role_zone`, adicionar:
```
else if (type.equals("obstacle")) {
    obstacles.put(k, Integer.MAX_VALUE);
}
```
Colocar *antes* do fechamento do método. A posição no bloco if/else garante que
dispensers/goal_zones/role_zones mantêm seu fluxo inalterado.

**Patterns to follow:** O `mark_obstacle` em `SharedMap` usa `obstacles.put`
diretamente — a adição em `update_cell` segue o mesmo padrão, sem operação CArtAgO
adicional.

**Test scenarios (ver U2 para implementação completa):**
- `percebidoMarcaObstacle`: `update_cell(1,0,"obstacle","")` → `obstacles.containsKey("1,0")` é true.
- `percebidoNaoDecai`: idem + `decay_obstacles(step=1000)` → chave ainda presente.

**Verification:** `~/tools/gradle-8.10/bin/gradle test` verde; novos testes PASS.

---

### U2. JUnit: pipeline percepção→obstáculos (isolamento)

**Goal:** Cobrir "enxerga?" sem sim — prova a pipeline update_cell→obstacles→A* em ms.

**Requirements:** R1, R2, R3

**Dependencies:** U1

**Files:**
- `src/test/java/env/SharedMapAStarTest.java` (adicionar testes)

**Approach:** Na classe existente `SharedMapAStarTest`, adicionar método helper
`mapWithInit()` que chama `sm.init()` (popula todos os sets — `cells`, `obstacles`,
`knownDispensers`, etc.) e configura `gridWidth=0`, `gridHeight=0` (sem wrapping).
**Todos os testes que chamam `update_cell` devem usar `mapWithInit()`, não
`mapWith()`** — `update_cell` chama `cells.put()` que NPE se `init()` não foi
chamado. Campos são package-private — acesso direto sem reflexão.

**Test scenarios:**
- `percebidoMarcaObstacle`: `update_cell(1,0,...obstacle...)` (obstáculo *adjacente* ao start) → `obstacles.containsKey("1,0")` true; `astar(0,0,5,0)` retorna `"n"` ou `"s"` (primeiro passo desvia, não tenta cruzar em (1,0)).
- `percebidoNaoDecai`: idem + `decay_obstacles(step=1000)` → chave ainda presente (`MAX_VALUE` nunca satisfaz `< step-30`).
- `colisaoAposPercebidoIdempotente`: `update_cell(1,0,...obstacle...)` + `mark_obstacle(1,0,42)` → `obstacles.get("1,0")` mantém `Integer.MAX_VALUE` (guard `containsKey` do `mark_obstacle` preserva sentinela).
- `tipoNaoObstacleNaoMarcaObstacles`: mapWithInit recém-criado (sem update_cell) → `obstacles.isEmpty()` true (baseline: outros tipos não populam obstacles; não chamar update_cell com dispenser pois NPE sem runtime CArtAgO).

**Verification:** `~/tools/gradle-8.10/bin/gradle test` verde; todos os testes novos PASS.

---

### U3. Analyzer: métrica `failed_path_total`

**Goal:** Tornar o assert do cenário discriminante (zero failed_path prova "desvia?").

**Requirements:** R5

**Dependencies:** —

**Files:**
- `.claude/skills/run-hive/analyzers/assert_metric.py`

**Approach:** Adicionar à seção `METRICS`:
```python
def m_failed_path_total(results):
    """Total de eventos failed_path no time (MENOR é melhor)."""
    total = sum(d.get("results", {}).get("failed_path", 0) for d in results.values())
    return total, f"{total} failed_path total no time"
```
Registrar em `METRICS = {..., "failed_path_total": m_failed_path_total}`.
`d["results"]` já é um dict de contagens por `actionResult` (ver `replay_analyze.py`
linha ~112: `agent_results[name][result] += 1`).

**Test scenarios:**
- Smoke: `.claude/skills/run-hive/run-hive.sh assert --metric failed_path_total --min 0` sobre um replay existente imprime resultado sem erro.
- Com `--max 0` num cenário limpo: PASS.

**Verification:** O comando acima sai 0. `assert_metric.py --help` mostra `failed_path_total` nas opções de `--metric`.

---

### U4. Cenário 03-obstacle-avoid (E2E: enxerga + desvia)

**Goal:** Provar "enxerga?" e "desvia?" ponta-a-ponta em sim determinística.

**Requirements:** R1, R2, R5

**Dependencies:** U1, U3

**Files:**
- `conf/scenarios/03-obstacle-avoid.json` (novo)
- `conf/scenarios/setup/03-obstacle-avoid.txt` (novo)

**Approach:**

Configuração da sim:
- `absolutePosition: false` — sem wrapping toroidal, A* opera em frame dr_pos (sem-wrap)
- `entities: {"standard": 1}` — apenas agentA1 (sem colisão de spawn/ocupancy)
- Grid 12×10, `roleZones: {"number": 0}`, `goals: {"number": 0}` — zero distrações
- `randomFail: 0`, `randomSeed: 17`, `events.chance: 0`, `regulation.chance: 0` — determinístico
- `steps: 20` — suficiente para detour de ~6 passos + 2 passos de adoção

Setup fixture (`setup/03-obstacle-avoid.txt`) — exatamente 3 linhas terrain + 1 move:
- `terrain 3 4 obstacle` + `terrain 3 5 obstacle` + `terrain 3 6 obstacle` — parede vertical em x=3 (y=4,5,6), Chebyshev=2 do spawn ≤ visão=5; bloqueia rota direta leste
- `terrain 5 5 role` — role_zone visível no step 1, Chebyshev=4 do spawn (dentro de visão=5; agente vê parede e role_zone simultaneamente)
- `move 1 5 agentA1` — spawn em (1,5)

Frame dr_pos equivalente (origin=(0,0)):
- Agente: (0,0), Obstáculos: (2,0),(2,-1),(2,1), Role_zone: (4,0)
- Rota sem fix: A* tenta (0,0)→(1,0)→(2,0) → `failed_path`
- Rota com fix: A* vê (2,0)(2,-1)(2,1) → contorna via (2,-2) ou (2,2) → chega em (6,0)

Assert: `{"metric": "failed_path_total", "max": 1}` (ver KD5; reduzir para 0 se empiricamente zero em múltiplos runs).

**Calibração pós-implementação:** Executar o cenário SEM o fix (U1 revertido) e
verificar que `failed_path_total` ≥ 2. Em seguida, com U1 aplicado, verificar ≤ 1.
Verificar manualmente no log/replay que o agente chegou à role_zone (role_adoption ≥ 1)
— este critério não está no assert automático (assert_metric.py suporta uma métrica
por bloco); é validação visual na calibração inicial.

**Test scenarios (cenário é o teste):**
- PASS (com fix): `failed_path_total ≤ 1` em ≤ 20 steps; agente chega à role_zone.
- FAIL esperado (sem fix / regressão): `failed_path_total ≥ 2`.

**Verification:** `.claude/skills/run-hive/run-hive.sh run --scenario 03-obstacle-avoid --port 8003 --assert` → `[PASS]`.

---

### U5. Regressão: adicionar 03-obstacle-avoid ao regression.sh

**Goal:** Garantir que `03-obstacle-avoid` faz parte da suíte de regressão automática.

**Requirements:** R5

**Dependencies:** U3, U4

**Files:**
- `.claude/skills/run-hive/regression.sh` (zero mudança — auto-discovery via `assert` no JSON)

**Approach:** `regression.sh` já descobre todos os `conf/scenarios/*.json` que têm
bloco `assert`. Nenhuma mudança necessária — o cenário 03 será incluído automaticamente.
Verificar apenas que `assert_metric.py` reconhece `failed_path_total` (U3 suficiente).

**Test scenarios:**
- `regression.sh 03-obstacle-avoid` → PASS.
- `regression.sh` (completo) → todos os cenários existentes continuam PASS (regressão).

**Verification:** Saída de `regression.sh` mostra `03-obstacle-avoid PASS` junto aos demais.

---

## Fronteiras e Dependências

### Em escopo
- Fix Java `SharedMap.update_cell` (2 linhas)
- JUnit para a pipeline (testes rápidos)
- Métrica `failed_path_total` no asserter
- Cenário `03-obstacle-avoid` (`absolutePosition:false`, 1 agente)

### Fora de escopo (diferidos)
- **#27 — U-shape / wall-following**: contorno de parede em becos sem saída (depende de U1 deste issue)
- **#16 — handedness inter-agente**: colisões entre agentes (não-terreno)
- **#31 — dead-reckoning E2E**: validação da navegação completa em `absolutePosition:false` (pilha mais ampla)
- Cenário separado para "enxerga?" sem alvo fixo (exploração pura) — coberto indiretamente pelo JUnit U2; sim-E2E adiada se U4 provar comportamento suficiente

### Worktree
- Porta MASSim: **8003** (isola do default 12300)
- Porta dashboard: **8103** (= 8003 + 100)

---

## Riscos

- **Timing Jason**: se o handler `+thing(X,Y,obstacle,_)` criar intenção que executa *após*
  `compute_next_move` no mesmo passo, pode haver 1 `failed_path` no primeiro step de
  percepção. Mitigado pelo KD5 (threshold calibrável). Provar empiricamente ao rodar
  o cenário U4.
- **`mark_visited` remove permanentes**: `mark_visited` chama `obstacles.remove(k)`. Para
  terreno, o agente nunca pisa na célula da parede, então nunca dispara. Mas se a célula
  for pisável por outro motivo, o sentinela seria removido. Aceito por ora — terreno real
  nunca é pisável.

---

## Fontes e Pesquisa

- `src/env/env/SharedMap.java` (verificado): `update_cell` não popula `obstacles`; `astar()`
  lê `obstacles` na linha 450; `mark_obstacle` só é chamado via ASL em `failed_path`.
- `src/agt/common/perception.asl` (verificado): handler `+thing` chama `update_cell` para
  todos os tipos, incluindo `obstacle`. Nenhuma chamada a `mark_obstacle` para percepts.
- `.claude/skills/run-hive/analyzers/replay_analyze.py` (verificado): `d["results"]` acumula
  contagens de `actionResult` — `failed_path` está presente como chave.
- `.claude/skills/run-hive/analyzers/assert_metric.py` (verificado): suporta `max` no spec;
  `METRICS` dict é plugável.
