---
title: "feat: Allocator descentralizado — seleção por valor + reserva de footprint + dispersão (#40)"
date: 2026-06-19
status: active
issues: ["#40"]
origin: docs/brainstorms/2026-06-19-allocator-descentralizado-requirements.md
worktree: /home/mgrim/repos/PCS5703-MAS-HIVE-sc40
branch: feat/sc-40-allocator
bundles_with: feat/sc-37-org-validator (#38 flat reshape)
---

# feat: Allocator descentralizado (#40)

Substitui o lock `claim_task` (exclusivo, modela errado) pela seleção por **valor** (reward/tempo) +
**reserva de footprint** na goal-zone + **custo** (distância) como critério de agente + **dispersão**
dos não-selecionados. Núcleo 06c-reliability; bundla com #38 antes de ir para a main.

---

## Sumário

- **Problema:** `claim_task` (putIfAbsent) é um lock exclusivo que modela errado as tasks MASSim
  (tasks são não-exclusivas: multi-submit, perduram). Não-vencedores continuam indo ao mesmo
  dispenser/goal-zone → congestão física → 06c flaky ~1/3. (Origin §1-§3.)
- **Solução:** Nova classe Java pura `TaskAllocator` + extensão de `TaskBoard` com `select_task`
  atômico (value-bid + reserva de goal-zone). Modificação mínima em `role_adoption.asl` (plan c).
- **Gate:** JUnit sem sim (R1-R5) + cenário 06c-single-collect com `--assert` confiável.
- **Worktree/branch:** `feat/sc-40-allocator` em `/home/mgrim/repos/PCS5703-MAS-HIVE-sc40`.
  Criar no início do ce-work com `git worktree add` a partir da main.

---

## Problema e contexto

O reshape flat (#38, já no branch `feat/sc-37-org-validator`) aposentou o `squad_leader`, que
provia deconfliction por `find_free_soloist` (1 agente/task). O fix mínimo `claim_task` (putIfAbsent)
destravou o 06c em isolamento, mas **não estabilizou**: o vencedor do lock prossegue, os perdedores
continuam em direção ao mesmo dispenser/goal-zone → congestão → flakiness ~1/3.

A causa-raiz está documentada no requirements doc (§2): tasks MASSim são **não-exclusivas**
(`iterations` [5,10], multi-submit, `maxDuration` [100,200] steps). O recurso disputado não é a
task, é o **espaço físico de montagem/entrega (goal-zone)**. O #40 corrige o modelo.

---

## Rastreio de requisitos (origin §7)

| R-ID | Requisito | Unidade(s) |
|------|-----------|-----------|
| R1 | Rank por valor: `reward / tempo_estimado` (tempo ≈ dist_dispenser + dist_goal_zone) | U1, U2 |
| R2 | Reserva de footprint na goal-zone: célula reservada atomicamente (substitui lock claim) | U1, U3, U2 |
| R3 | Seleção por custo: agente de menor custo (bid = valor; reusar matemática de `find_free_soloist`) | U1, U3 |
| R4 | Dispersão: não-selecionado (reserva falha ou bid perdedor) navega para frontier alternativa | U4, U2 |
| R5 | Single-block = coalizão tamanho 1 (coordinator+provider colapsam) → caminho do 06c | U3, U4 |

---

## Decisões técnicas chave

### KTD-1 — Onde mora o alocador: `TaskAllocator` puro + extensão de `TaskBoard`

Lógica de decisão não-trivial (rank de valor, seleção de goal-zone, cálculo de custo) vai para a
nova classe **`TaskAllocator`** em `src/java/hive/` — pura Java, sem dependência CArtAgO, testável
com JUnit sem simulador. O `TaskBoard` (artefato CArtAgO) a invoca internamente e expõe apenas a
operação `select_task` para os agentes.

Alternativa descartada: engordar o `.asl` com lógica de rank. Viola a convenção "lógica em Java
testável" (AGENTS.md).

### KTD-2 — Bid = valor (reward/custo), não custo puro

Bid do agente = `reward / max(1, dist_disp + dist_goal)`. O `bestBid` existente em `TaskBoard`
(max wins) serve sem alteração: o agente mais próximo + task mais lucrativa ganha. Isso implementa
R1+R3 com a infra já testada.

### KTD-3 — `select_task` atômico (bid + resolve + reserve em uma operação CArtAgO)

A operação `select_task(AgentName, TaskName, BidValue, GoalZoneX, GoalZoneY) → Won` é **atômica**
no `TaskBoard`:
1. Registra o bid do agente.
2. Checa se este agente tem o bid mais alto atual (`bestBid`).
3. Se sim: tenta reservar a célula goal-zone. Se a célula já está reservada por outra task → `Won=false`.
4. Retorna `Won` ao agente.

A atomicidade (bloco `synchronized` na operação) elimina a race condition de dois agentes tentando
reservar a mesma goal-zone no mesmo ciclo. Substitui o `claim_task` (que era putIfAbsent — apenas
lock de task, não de espaço).

### KTD-4 — Footprint single-block = 1 célula da goal-zone

Para R5 (single-block, caminho do 06c), o footprint a reservar é a 1 célula da goal-zone para onde
o agente navega para submit. O agente passa as coordenadas que obtém via `get_nearest_goal_zone`.
Para multi-block (#43, follow-up), `select_task` receberá lista de células.

### KTD-5 — Dispersão: explorar frontier alternativa

Não-vencedor (bid perdedor OU goal-zone reservada) descarta `has_destination` e chama `!explore`
(já existe em `role_adoption.asl`). Não navega para a goal-zone da task → não empilha.

### KTD-6 — Worktree nova: branch isolado para #40

O #40 é um conjunto de mudanças suficientemente independente para merecer branch próprio
(`feat/sc-40-allocator`). O #38 (no `feat/sc-37-org-validator`) e o #40 bundlam na main
via squash merge em série (primeiro #38, depois #40 sobre a main com #38 já nela), ou juntos
se o dono preferir.

---

## High-Level Technical Design

### Fluxo select_task (sequência por agente)

```
role_adoption.asl plan (c) — worker ocioso + task ativa
     │
     ├─ Obter posição: my_pos(MX, MY)
     ├─ Obter task info: known_task(TaskName, Deadline, Reward, BlockType)
     ├─ SharedMap: get_nearest_dispenser(MX, MY, BlockType) → DX, DY
     ├─ SharedMap: get_nearest_goal_zone(MX, MY) → GZX, GZY
     ├─ Calcular bid: BidValue = Reward / max(1, dist(MX,MY,DX,DY) + dist(DX,DY,GZX,GZY))
     │
     └─ TaskBoard.select_task(Me, TaskName, BidValue, GZX, GZY) → Won
              │
              ├─ Won = true  →  +my_active_task(TaskName,"solo")
              │                  +goal_zone_reserved(GZX, GZY)
              │                  !collect_block(BlockType)
              │
              └─ Won = false →  !explore  [dispersão: frontier alternativa]
```

### TaskBoard — operações novas (diagrama de responsabilidades)

```
TaskBoard (CArtAgO artefato)
├── existente: place_bid / resolve_auction / bestBid / evaluate_task / claim_task (→ substituir)
│
└── novo:
    select_task(name, task, bid, gzX, gzY) → won     [atômico, substituirá claim_task no .asl]
    release_task_reservation(taskName)                 [chamado em finalize_task]
    ──────────────────────────────────────────────────
    estado interno (novo):
    ConcurrentHashMap<String,String> goalReservations  // "gzX,gzY" → taskName
    ──────────────────────────────────────────────────
    usa internamente: TaskAllocator.computeValue(reward, distDisp, distGoal) → double
```

### TaskAllocator — classe pura (Java, sem CArtAgO)

```
TaskAllocator (src/java/hive/)
├── computeValue(int reward, int distDisp, int distGoal) → double
│       reward / max(1, distDisp + distGoal)
│
└── rankTasks(List<int[]> tasks, int agX, int agY, Set<int[]> goalZones,
              Set<String> reservedCells) → List<int[]>
        para cada task: para cada goalZone não-reservada, computa value
        retorna ordenado por value desc, anotado com (taskIdx, gzX, gzY, value)
```

---

## Unidades de implementação

### U1. `TaskAllocator` — classe Java pura (lógica de valor e seleção)

**Goal:** Extrair toda lógica de decisão do alocador em classe testável sem CArtAgO.

**Requirements:** R1, R2, R3

**Dependencies:** nenhuma

**Files:**
- `src/java/hive/TaskAllocator.java` — criar

**Approach:**
- Classe com dois métodos estáticos públicos (sem estado): `computeValue` e `rankTasks`.
- `computeValue(int reward, int distDisp, int distGoal)` → `double`: `reward / max(1.0, distDisp + distGoal)`.
- `rankTasks(List<int[]> tasks, int agX, int agY, Set<String> goalZoneKeys, Set<String> reservedKeys)`
  → `List<int[]>` (cada entrada: `[taskIdx, gzX, gzY, valueScaled]`): para cada task × goalZone não-reservada,
  computa `value = computeValue(reward, dist(ag→disp), dist(disp→gz))`. Ordena por value desc.
  Usa distância Manhattan toroidal (sem A*, para rapidez; suficiente para bid).
- Usa `hive.GridConfig` para distância toroidal (já existente no projeto).

**Patterns to follow:** `hive.AStarPathfinder` (toroidal distance math); `hive.GridConfig.width()/height()`.

**Test scenarios:** ver U2 (teste-first — a classe é escrita para ser testada por U2).

**Verification:** compila (`gradle classes -p /home/mgrim/repos/PCS5703-MAS-HIVE-sc40`); sem dependência CArtAgO no import.

---

### U2. `TaskAllocatorTest` — JUnit cobrindo R1-R5

**Goal:** Especificar e verificar o comportamento do alocador sem simulador.

**Requirements:** R1, R2, R3, R4, R5

**Dependencies:** U1

**Files:**
- `src/test/java/hive/TaskAllocatorTest.java` — criar

**Execution note:** Escrever os testes ANTES de terminar a implementação de U1 (test-first na lógica pura).

**Approach:** JUnit 5, sem CArtAgO. Usar dados fixos (posições hardcoded, tasks hardcoded).

**Test scenarios:**

R1 — Rank por valor:
- `testValorMaisAltoComMenorDistancia`: task com `reward=10, dist=5` bate `reward=10, dist=10` → primeiro na lista.
- `testValorEquivalenteQuandoProporçãoIgual`: `reward=10/dist=5` == `reward=20/dist=10` → mesma prioridade (ordem não importa).
- `testDistanciaZeroNaoExplode`: dist=0 → não divide por zero, retorna `reward / 1`.

R2 — Reserva de footprint (seleção exclui goal-zones reservadas):
- `testGoalZoneReservadaNaoEntraNoRank`: goal-zone em `reservedKeys` → não aparece em nenhuma entrada do rank.
- `testGoalZoneLivreAparece`: goal-zone não reservada → aparece no rank.
- `testTodasGoalZonesReservadasRetornaVazio`: lista resultante vazia quando todas reservadas.

R3 — Seleção por custo (agente mais próximo tem bid mais alto):
- `testAgenteProximoTemBidMaisAlto`: dois agentes, A a distância 3 e B a distância 10 da mesma task → value(A) > value(B).

R4 — Dispersão (inferido: rank vazio → agente deve explorar):
- `testRankVazioIndicaDispersar`: `rankTasks` com lista de tasks vazia → lista vazia (agente no .asl interpreta como "dispersar").

R5 — Single-block = coalizão 1 (inferido: task de 1 bloco processada normalmente):
- `testTaskSingleBlockRankeada`: task com 1 bloco entra normalmente no rank com valor correto.

**Verification:** `~/tools/gradle-8.10/bin/gradle test -p /home/mgrim/repos/PCS5703-MAS-HIVE-sc40` — todos verdes.

---

### U3. Estender `TaskBoard` com `select_task` + reserva de footprint

**Goal:** Operação CArtAgO atômica que combina bid + resolução + reserva de goal-zone. Substitui `claim_task`.

**Requirements:** R2, R3, R5

**Dependencies:** U1 (usa `TaskAllocator.computeValue` internamente)

**Files:**
- `src/env/env/TaskBoard.java` — modificar

**Approach:**
- Adicionar campo: `ConcurrentHashMap<String, String> goalReservations` (key = "gzX,gzY", valor = taskName).
- Inicializar no `init()`.
- Nova operação `@OPERATION select_task(Object oAgent, Object oTask, Object oBid, Object oGZX, Object oGZY, OpFeedbackParam<Boolean> won)`:
  - Bloco `synchronized(this)`:
    1. Chama `place_bid` internamente (ou replica a lógica: adiciona à lista de bids do task).
    2. Obtém `bestBidAgent` via `bestBid()` para a task.
    3. Se `bestBidAgent.equals(agentName)`: tenta reservar `"gzX,gzY"` em `goalReservations.putIfAbsent`.
       - Se reserva OK (putIfAbsent retornou null): `won.set(true)`.
       - Se já reservado por outra task: `won.set(false)`.
    4. Se não é o melhor bid: `won.set(false)`.
- Nova operação `@OPERATION release_task_reservation(Object oTask)`: remove entrada de `goalReservations`
  onde valor == taskName. Chamado em `finalize_task`.
- **Manter** `claim_task` existente intacto (é referenciado em outros planos); `select_task` é a nova API.
- A lógica de bid interna usa o `bestBid` já existente (linha ~83-88 do TaskBoard original).

**Patterns to follow:** `TaskBoard.claim_task` (l.147-151) — atomicidade via `synchronized`; `place_bid`/`resolve_auction`/`bestBid` para padrão de leilão.

**Test scenarios:**
- `testSelectTaskWinnerGetsTrueAndReservesCell`: agente com bid mais alto chama `select_task` → `won=true`, célula em `goalReservations`.
- `testSelectTaskLoserGetsFalse`: agente com bid menor chama após o vencedor → `won=false`.
- `testSelectTaskGoalZoneAlreadyReserved`: mesmo o melhor bid falha se goal-zone já reservada por outra task → `won=false`.
- `testReleaseTaskReservationLibera`: `release_task_reservation(task)` → célula removida de `goalReservations`.

Nota: esses testes de integração CArtAgO são mais difíceis de escrever sem simulador. Priorizar os testes de U2 (lógica pura). Para U3, validar principalmente via U5 (simulação 06c).

**Verification:** `gradle test` verde (testes existentes não regridem); nova operação visível no artefato.

---

### U4. Modificar `role_adoption.asl` — plan (c) com `select_task` + dispersão

**Goal:** Rewire do plan (c) — substituir `claim_task` por `select_task` (com bid de valor) e adicionar dispersão para não-vencedores.

**Requirements:** R1, R2, R3, R4, R5

**Dependencies:** U3

**Files:**
- `src/agt/common/role_adoption.asl` — modificar (plan c, l.81-112)

**Approach:**

Estrutura do novo plan (c) (pseudocódigo, não especificação):
```
+!idle_worker_loop
    : can_score_role & my_pos(MX, MY) & known_task(TaskName, Deadline, Reward, BlockType)
    <- // R1: calcular bid de valor
       get_nearest_dispenser(MX, MY, BlockType, DX, DY);
       get_nearest_goal_zone(MX, MY, GZX, GZY);
       // bid = Reward / max(1, dist(ag→disp) + dist(disp→gz)) — via internal action
       compute_task_bid(Reward, MX, MY, DX, DY, GZX, GZY, BidValue);
       // R2+R3+R5: atômico no TaskBoard
       select_task(Me, TaskName, BidValue, GZX, GZY, Won);
       if (Won) {
           +my_active_task(TaskName, "solo");
           ...collect solo (mesma lógica do antigo claim_task won)...
       } else {
           !explore   // R4: dispersão
       }.
```

- `compute_task_bid` = nova internal action em `src/java/hive/` (chama `TaskAllocator.computeValue`;
  precisa das 4 distâncias toroidais; retorna double arredondado para int escalado × 1000 para
  compatibilidade com Jason term).
- Manter o guard `not my_active_task(_, _)` para evitar re-entrar no loop com task ativa.
- `+task_done` handler: chamar `release_task_reservation(TaskName)` no TaskBoard antes de chamar
  `mark_free` (ou como parte de `!finalize_task`).

**Patterns to follow:** `role_adoption.asl` plan (c) existente (l.81-112) — estrutura geral mantida; `hive_agent.asl` `!finalize_task` — ponto de chamada `release_task_reservation`.

**Test scenarios (sim-nível, via U5):**
- Cenário 06c: pelo menos 1 submit OK no tempo do cenário (= agente vencedor completou).
- Cenário 06c: não-vencedores não ficam presos na goal-zone (histograma mostra `move` ativo).
- Cenário 06c: `failed_path` por congestionamento reduz em relação ao baseline de flakiness.

**Verification:** `gradle test` verde (parse do .asl OK); 06c-single-collect `--assert` PASS.

---

### U5. Validação E2E — cenário 06c sob `--assert`

**Goal:** Confirmar que o allocador elimina a flakiness do 06c e não regride outros cenários.

**Requirements:** R1-R5 (integrados)

**Dependencies:** U1, U2, U3, U4

**Files:** nenhum arquivo novo; usa `.claude/skills/run-hive/run-hive.sh`

**Approach:**
- Rodar `run-hive.sh run --scenario 06c-single-collect --assert` com a nova worktree.
- Confirmar PASS (submits_ok ≥ 1).
- Rodar 3× para checar estabilidade (o claim-task falhava ~1/3; o allocator deve ser > 90% estável).
- Rodar `run-hive.sh run --scenario 00-smoke --assert` para confirmar não-regressão de adoção.
- Revisar analyzer: não-vencedores devem mostrar `move` + `skip` em vez de ficar empilhados.

**Test scenarios (run-hive):**
- 3 runs de 06c: todos devem retornar PASS com `submits_ok ≥ 1`.
- 1 run de 00-smoke: PASS com `role_adoption ≥ 10`.

**Verification:** PASS em ≥ 3/3 runs do 06c (vs ~1/3 do baseline). Analyzer mostra dispersão dos não-vencedores.

---

## Escopo / fronteiras

### Dentro do escopo (núcleo #40)
- `TaskAllocator` (valor + seleção de goal-zone) — R1-R5.
- `select_task` no `TaskBoard` (atômico, bid + reserva).
- Modificação do plan (c) em `role_adoption.asl` (dispersão R4).
- JUnit da lógica pura (U2); validação 06c (U5).

### Fora do escopo (follow-ups)
- **#43 — Multi-block:** footprint expandido (N células), recrutamento de providers — gated U9 #17.
- **#44 — Contract-Net recrutamento:** cfp→propose→award descentralizado — gated U9 #17.
- **#45 — Calibração de pesos:** A/B de pesos no allocator via #41/#42.
- **#41 — Gate de flakiness:** harness repeat N×; fora do escopo aqui mas os 3 runs de U5 são proxy.
- **#42 — Cenário de contenção:** multi-task/multi-agente; fora do escopo, 06c é suficiente para o núcleo.

### Deferred to Follow-Up Work
- Extensão de `select_task` para aceitar lista de células (footprint multi-bloco) — #43.
- Parâmetro de peso configurável em `TaskAllocator.computeValue` — #45.
- Analyzer de contenção específico para medir dispersão quantitativa — #42.

---

## Riscos e dependências

| Risco | Probabilidade | Impacto | Mitigação |
|-------|------|---------|-----------|
| `select_task` atômico ainda produz ties (dois agentes com bid idêntico) | Baixa | Baixo | `bestBid` usa Map iteration order como desempate implícito; suficiente para o núcleo |
| Agente ganha bid mas goal-zone fica fora do alcance (mapa desconhecido) | Média | Médio | `get_nearest_goal_zone` retorna -1 se nenhuma conhecida; plan (c) guarda `GZX \== -1` → explora em vez de selecionar |
| `release_task_reservation` não chamado em timeout → reserva vaza | Baixa | Baixo | Adicionar `release_task_reservation` no handler `!check_expired_task` além de `!finalize_task` |
| Parse error no .asl novo → score 0 (gotcha clássico) | Baixa | Alto | `gradle test` (compila mas .asl é runtime) → rodar 00-smoke antes do 06c para confirmar boot |

---

## Fontes e pesquisa

- Requirements: `docs/brainstorms/2026-06-19-allocator-descentralizado-requirements.md` (origem autoritativa)
- Spec MASSim: `massim_2022/docs/scenario.md` §Tasks (l.200-204) — tasks não-exclusivas
- Livro MAPC 2022: MMD §value function (Eq.3-4); LI(A)RA §descentralizado (Jason, similar Contract Net)
- Código base: `src/env/env/TaskBoard.java` (place_bid/bestBid, l.83-88; claim_task, l.147-151);
  `src/env/env/SquadCoordinator.java` (find_free_soloist, l.220-238); `src/env/env/SharedMap.java`
  (wrappedManhattan, occupancy overlay)
- Notas de aula MAS (Notion, Durfee 2001): CNP homogêneo → bid de custo; valor = reward/steps
