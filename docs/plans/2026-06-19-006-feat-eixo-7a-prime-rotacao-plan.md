---
title: "feat: Eixo 7a' — rotação pré-submit para alinhar blocos desalinhados"
status: active
date: 2026-06-19
issue: "#18"
track: "Fase C / Submits"
---

# feat: Eixo 7a' — rotação pré-submit para alinhar blocos desalinhados

## Summary

Após o Eixo 7a (#18), o HIVE submete tasks multi-requisito quando os blocos estão pré-alinhados
nas posições exatas exigidas. O Eixo 7a' adiciona a capacidade de **rotacionar** blocos pré-anexados
em orientação girada para alinhá-los antes do submit. Um agente com 2 blocos em cadeia Norte
(offsets `(0,-1)` e `(0,-2)`) e uma task exigindo cadeia Leste (`(1,0)` e `(2,0)`) deve executar
1 `rotate(cw)` e então submeter. Se a forma dos blocos for incompatível com qualquer rotação
(não é uma versão girada da forma exigida), o agente não tenta submit.

---

## Problem Frame

O guard `hive.AllReqsSatisfied` do Eixo 7a bloqueia o submit quando os blocos estão em posições
erradas — comportamento correto. Mas blocos podem estar "certos" em forma, apenas girados:
e.g., cadeia Norte vs. cadeia Leste exigida pela task. Nesses casos, 1-3 `rotate(cw)` alinham
os blocos sem perda. Sem esta capacidade, o agente fica inativo carregando blocos utilizáveis.

A capacidade é testável de forma isolada: pré-fixar blocos girados + task incompatível com a
orientação atual + verificar que o agente rotaciona e submete com sucesso.

---

## Requirements

- **R1** — Um agente com N blocos pré-alinhados cujos offsets são uma versão CW-rotacionada
  dos offsets exigidos pela task deve (a) calcular quantas rotações CW são necessárias, (b)
  executá-las passo a passo, (c) verificar com AllReqsSatisfied e submeter.
- **R2** — Um agente com blocos em forma incompatível com qualquer rotação da task não deve
  tentar submit (submits_ok = 0).
- **R3** — A lógica de rotação não deve regredir os cenários 01-adopt, 06-single-block,
  06c-single-collect, 07a-multi-req, 07a-wrong-blocks.

---

## Key Technical Decisions

### KTD-1: Nova Java IA `hive.RotationsNeeded(TaskName, R)`

Implementar como internal action Java (testável, análoga a `AllReqsSatisfied`). Lê
`task_req(TaskName,DX,DY,_)` e `attached(DX,DY)` da belief base, testa 1-3 rotações CW
(`(dx,dy) → (-dy, dx)`) usando `AllReqsSatisfied.check()` estático, unifica `R` com o mínimo
de rotações (1-3) que satisfaz todos os requisitos. Falha se:

- R=0: blocos já alinhados (AllReqsSatisfied.check retorna true na posição original; o caminho
  multi-bloco existente trata esse caso).
- Impossível: nenhuma das 3 rotações satisfaz todos os requisitos.

**Alternativa rejeitada:** implementar a lógica em Jason puro com `forall`/contagem. Rejeitada
porque Jason não tem operador de rotação embutido e a lógica de loop "tenta N rotações" é
propensa a erros e não testável sem a sim.

### KTD-2: Planos ASL em `connect_protocol.asl` — fila de rotação com `trying_rotate`

Adicionar crença `trying_rotate(TaskName, RC)` (RC = rotações restantes) e 3 planos:

1. **Continue** (`trying_rotate(TaskName, RC) & RC > 0`): `rotate(cw)`, decrementa RC.
2. **Finalize** (`trying_rotate(TaskName, 0)`): chama `AllReqsSatisfied` (gate defensivo),
   procede com submit (mesmo corpo do BLOCO-NA-MÃO multi-bloco).
3. **Initiate** (após falha do BLOCO-NA-MÃO existente): `not trying_rotate & NBlocks > 1` →
   chama `hive.RotationsNeeded(TaskName, R)` → `+trying_rotate(TaskName, R)` → `rotate(cw)`.

Continue e Finalize ficam **antes** do BLOCO-NA-MÃO no arquivo (maior prioridade Jason),
Initiate fica **depois** (só é atingido se AllReqsSatisfied falhou no BLOCO-NA-MÃO).

**Alternativa rejeitada:** tratar a rotação dentro do corpo do plano BLOCO-NA-MÃO via loop
Jason. Jason não tem construção de loop por steps; cada rotação é uma ação por passo.

### KTD-3: Dois cenários de isolamento (positivo + negativo)

- **Positivo (`07a-prime-rotate`)**: agentA4 em (3,3)/role-zone, 2 blocos pré-anexados em
  cadeia Norte `(0,-1)`+`(0,-2)`, task exige cadeia Leste `(1,0)`+`(2,0)`. 1 CW alinha.
  Assert: `submits_ok ≥ 1`.
- **Negativo (`07a-prime-wrong`)**: mesma task, mas blocos em L-shape `(1,0)`+`(0,1)`.
  Nenhuma das 4 rotações da L-shape produz a cadeia Leste exigida. Assert: `submits_ok = 0`.

### KTD-4: Isolamento de porta para execução em worktree

Game server: `--port 12301` (evita conflito com porta padrão 12300 de outra worktree).
Monitor web: fixado em `:8000` pelo MASSim server (não configurável sem modificar o servidor);
usar `--monitor` apenas se a porta 8000 estiver livre. Para execução headless em worktree,
omitir `--monitor` é o default seguro.

---

## High-Level Technical Design

### Rotação CW em coordenadas MASSim

MASSim: X cresce para Leste, Y cresce para Sul.
CW rotation: `(dx, dy) → (−dy, dx)`

| Rotações | Cadeia Norte → | Resultado |
|---|---|---|
| 0× | `(0,−1), (0,−2)` | Norte — sem mudança |
| 1× CW | `(1,0), (2,0)` | **Leste ✓** |
| 2× CW | `(0,1), (0,2)` | Sul |
| 3× CW | `(−1,0), (−2,0)` | Oeste |

L-shape `(1,0)+(0,1)` após todas as rotações:

| Rotações | Resultado | Match `(1,0)+(2,0)`? |
|---|---|---|
| 0× | `(1,0), (0,1)` | ✗ |
| 1× | `(0,1), (−1,0)` | ✗ |
| 2× | `(−1,0), (0,−1)` | ✗ |
| 3× | `(0,−1), (1,0)` | ✗ |

### Sequência de planos Jason por step (quando trying_rotate está ativo)

```
Step T  : [BLOCO-NA-MÃO multi-bloco] → AllReqsSatisfied falha → plan fails
          → [Initiate] → RotationsNeeded=1 → +trying_rotate(T1,1) → rotate(cw)

Step T+1: [Continue] trying_rotate(T1,1) → RC=0 → .abolish + +trying_rotate(T1,0) → rotate(cw)

Step T+2: [Finalize] trying_rotate(T1,0) → AllReqsSatisfied ✓ → submit path
```

*(Para R=1 o "continue" já faz a última rotação e passa RC=0 para o "finalize" no passo seguinte)*

---

## Scope Boundaries

### Em escopo

- IA `RotationsNeeded` + testes JUnit
- 3 planos ASL (continue, finalize, initiate)
- 2 novos cenários de isolamento (positivo e negativo)

### Deferred to Follow-Up Work

- **07b**: coleta + montagem multi-bloco cooperativo (depende de connect cooperativo #21)
- **07a' com coleta**: agente coleta blocos e tenta rotacionar para alinhar (intenção futura)
- Suporte a `--monitor-port` no `run-hive.sh` para isolar a porta :8000 do monitor MASSim
  em worktrees paralelas (requer patch no servidor MASSim ou workaround de proxy)
- Casos com N > 2 blocos e múltiplas rotações

### Fora de escopo

- Rotação pós-submit-failed (já existe em `connect_protocol.asl`)
- Qualquer mudança no servidor MASSim

---

## Implementation Units

### U1. RotationsNeeded.java + RotationsNeededTest.java

**Goal:** Nova internal action Java que determina o número mínimo de CW rotations (1–3)
para alinhar os blocos anexados com os requisitos da task. Falha se já alinhado (R=0)
ou se nenhuma rotação ajuda.

**Requirements:** R1, R2

**Dependencies:** nenhuma (reutiliza AllReqsSatisfied.check — já existe)

**Files:**
- `src/java/hive/RotationsNeeded.java` — criar
- `src/test/java/hive/RotationsNeededTest.java` — criar

**Approach:**
- Ler `task_req(TaskName,DX,DY,_)` e `attached(DX,DY)` da BeliefBase (mesmo padrão de
  `AllReqsSatisfied.java`)
- Para `rotations ∈ {1, 2, 3}`: aplicar `(dx,dy) → (-dy, dx)` `rotations` vezes a cada posição
  anexada; chamar `AllReqsSatisfied.check(reqs, rotated)`
- Se `check` retornar true: unificar `args[1]` com `rotations` e retornar true
- Se nenhum funcionar: retornar false (plan falha)
- Se check funcionar para R=0 (já alinhado): pular (retornar false)

**Patterns to follow:** `src/java/hive/AllReqsSatisfied.java` (estrutura idêntica de leitura
da BB, delegação a método estático `check`)

**Test scenarios:**
- Cadeia Norte `(0,-1),(0,-2)` vs task `(1,0),(2,0)`: deve retornar R=1
- Cadeia Leste `(1,0),(2,0)` vs task `(1,0),(2,0)`: deve FALHAR (já alinhado, R=0)
- L-shape `(1,0),(0,1)` vs task `(1,0),(2,0)`: deve FALHAR (impossível)
- Cadeia Sul `(0,1),(0,2)` vs task `(1,0),(2,0)`: deve retornar R=3
- Cadeia Oeste `(-1,0),(-2,0)` vs task `(1,0),(2,0)`: deve retornar R=2
- Single block `(0,-1)` vs task `(1,0)` (NBlocks=1): deve retornar R=1
- Task vazia (sem req): deve FALHAR (check() retorna true com R=0, que é excluído)

**Verification:** `~/tools/gradle-8.10/bin/gradle test` passa com todos os casos acima.

---

### U2. connect_protocol.asl — planos de rotação

**Goal:** Adicionar 3 novos planos `+step(N)` para o ciclo de rotação pré-submit.

**Requirements:** R1, R2

**Dependencies:** U1 (IA RotationsNeeded)

**Files:**
- `src/agt/common/connect_protocol.asl` — modificar

**Approach:**

Inserir antes do bloco `BLOCOS-NA-MÃO → SUBMIT multi-bloco` atual (linhas ~76–101):

```
// ROTATION CONTINUE: ainda há rotações pendentes
+step(N)
    : trying_rotate(TaskName, RC) & RC > 0
      & not submitted_task(_) & not pending_submit(_)
    <- NewRC = RC - 1;
       .abolish(trying_rotate(TaskName, _));
       +trying_rotate(TaskName, NewRC);
       action("rotate(cw)").

// ROTATION FINALIZE: rotações concluídas — verificar e submeter
+step(N)
    : trying_rotate(TaskName, 0) & my_pos(MX, MY)
      & not submitted_task(_) & not pending_submit(_)
    <- .abolish(trying_rotate(TaskName, _));
       hive.AllReqsSatisfied(TaskName);   // gate defensivo: se ainda falhar, plan falha
       .my_name(Me);
       // ... mesmo corpo do BLOCO-NA-MÃO multi-bloco (mark_busy, +my_active_task, etc.) ...
       action("skip").
```

Inserir depois do bloco BLOCO-NA-MÃO multi-bloco (após a linha ~101):

```
// ROTATION INITIATE: blocos anexados mas desalinhados — tentar rotacionar
+step(N)
    : can_score_role
      & not my_active_task(_, _) & not pending_submit(_) & not submitted_task(_)
      & not needs_clear_blocks(_) & not collecting(_, _, _)
      & not trying_rotate(_, _)
      & known_task(TaskName, Deadline, _, NBlocks) & NBlocks > 1 & Deadline > N
      & .count(attached(_, _), NumAtt) & NumAtt >= NBlocks
    <- hive.RotationsNeeded(TaskName, R);   // falha se R=0 ou impossível
       +trying_rotate(TaskName, R);
       action("rotate(cw)").
```

**Nota de execução:** os planos Continue e Finalize aparecem antes do BLOCO-NA-MÃO no
arquivo — prioridade Jason mais alta — garantindo que o loop de rotação toma o controle
enquanto `trying_rotate` está ativo.

**Patterns to follow:** planos `BLOCO-NA-MÃO` existentes (linhas 76–131 de
`connect_protocol.asl`) para o corpo do Finalize.

**Test scenarios:**
- Sim cenário 07a-prime-rotate: agentA4 com cadeia Norte + task Leste → rotate(cw) em step T,
  skip no T+1 (Finalize), navega para goal, submete → `submits_ok ≥ 1`
- Sim cenário 07a-prime-wrong: agentA4 com L-shape + task Leste → RotationsNeeded falha →
  nenhum submit → `submits_ok = 0`
- Regressão 07a-multi-req: cadeia já alinhada → planos Continue/Finalize não disparam,
  AllReqsSatisfied passa no BLOCO-NA-MÃO original → `submits_ok ≥ 1` (sem regressão)

**Verification:** cenários 07a-prime-rotate (PASS) e 07a-prime-wrong (PASS) via
`run-hive.sh run --scenario 07a-prime-rotate --assert --port 12301`

---

### U3. Cenário positivo `07a-prime-rotate`

**Goal:** Fixture determinística isolando a capacidade de rotação: blocos em cadeia Norte
pré-anexados → 1 CW → cadeia Leste → submit bem-sucedido.

**Requirements:** R1

**Dependencies:** U2

**Files:**
- `conf/scenarios/07a-prime-rotate.json` — criar
- `conf/scenarios/setup/07a-prime-rotate.txt` — criar

**Approach:**

`07a-prime-rotate.json`: clonar estrutura de `07a-multi-req.json` com:
- `"steps": 40` (1 rotation + navegação até goal)
- `"randomSeed": 19`, `"randomFail": 0`, `"absolutePosition": true`
- `"assert": { "metric": "submits_ok", "min": 1 }`
- `"setup": "../../conf/scenarios/setup/07a-prime-rotate.txt"`

`setup/07a-prime-rotate.txt`:
```
# Estacionar 14 não-alvo na borda (padrão anti-colisão)
move 0 11 agentA1 ... move 1 10 agentA15

# agentA4 em (3,3), role-zone → adota worker
move 3 3 agentA4
terrain 3 3 role

# Cadeia Norte: (0,-1) e (0,-2) — 1 CW → (1,0) e (2,0)
add 3 2 block b1
attach 3 3 3 2         # agent(3,3) ↔ block(3,2) → attached(0,-1) [Norte]
add 3 1 block b1
attach 3 2 3 1         # block(3,2) ↔ block(3,1) → attached(0,-2) via cadeia

# Goal ao sul
terrain 3 7 goal

# Task exige cadeia Leste: b1@(1,0) + b1@(2,0) — 1 CW a partir de Norte
create task t1 100 1,0,b1;2,0,b1
```

**Test scenarios:**
- Após rodar o cenário: `submits_ok ≥ 1`, score > 0 (task vale 40 pontos)
- agentA4 deve mostrar `rotate(cw)` no histograma de ações no step~3
- agentA4 deve mostrar `submit:1` na análise do replay

**Verification:** `run-hive.sh run --scenario 07a-prime-rotate --assert --port 12301` → PASS

---

### U4. Cenário negativo `07a-prime-wrong`

**Goal:** Verificar que o agente NÃO submete quando os blocos têm forma incompatível com
qualquer rotação da task.

**Requirements:** R2

**Dependencies:** U2

**Files:**
- `conf/scenarios/07a-prime-wrong.json` — criar
- `conf/scenarios/setup/07a-prime-wrong.txt` — criar

**Approach:**

`07a-prime-wrong.json`: análogo a `07a-wrong-blocks.json` mas com shape incompatível:
- `"assert": { "metric": "submits_ok", "equals": 0 }`
- `"steps": 40`, seed diferente (ex.: 23)

`setup/07a-prime-wrong.txt`:
```
# Estacionar 14 não-alvo na borda
move 0 11 agentA1 ... move 1 10 agentA15

# agentA4 em (3,3), role-zone → adota worker
move 3 3 agentA4
terrain 3 3 role

# L-shape: (1,0) e (0,1) — nenhuma rotação produz (1,0)+(2,0)
add 4 3 block b1
attach 3 3 4 3         # agent(3,3) ↔ block(4,3) → attached(1,0) [Leste]
add 3 4 block b1
attach 3 3 3 4         # agent(3,3) ↔ block(3,4) → attached(0,1)  [Sul]

# Goal ao sul
terrain 3 7 goal

# Task exige cadeia Leste: (1,0)+(2,0) — incompatível com qualquer rotação da L-shape
create task t1 100 1,0,b1;2,0,b1
```

**Por que L-shape é incompatível:**
- `(1,0)+(0,1)` após 1× CW → `(0,1)+(−1,0)` ✗
- após 2× CW → `(−1,0)+(0,−1)` ✗
- após 3× CW → `(0,−1)+(1,0)` ✗

**Test scenarios:**
- Após rodar o cenário: `submits_ok = 0`, score = 0
- agentA4 emite `no_action` em todos os steps após falha do RotationsNeeded
  (RotationsNeeded falha → initiate plan falha → AllReqsSatisfied falha no BLOCO-NA-MÃO →
  nenhum plano restante com ação significativa)

**Verification:** `run-hive.sh run --scenario 07a-prime-wrong --assert --port 12301` → PASS

---

### U5. JUnit + regressão completa

**Goal:** Confirmar que U1-U4 não introduzem regressões e que a suíte de 6 cenários passa.

**Requirements:** R1, R2, R3

**Dependencies:** U1–U4

**Files:** nenhum novo; lê os existentes via `regression.sh`

**Approach:**
1. `~/tools/gradle-8.10/bin/gradle test` — inclui `RotationsNeededTest` (U1)
2. `regression.sh` — corre 6 cenários em série:
   - 01-adopt, 06-single-block, 06c-single-collect, 07a-multi-req, 07a-wrong-blocks (existentes)
   - 07a-prime-rotate, 07a-prime-wrong (novos)

Para rodar na worktree isolada sem conflito de porta:
```bash
HIVE_PORT=12301 .claude/skills/run-hive/regression.sh
```
(ou passar `--port 12301` individualmente — verificar suporte na regression.sh)

**Test scenarios:**
- Todos os 6 cenários: PASS

**Verification:** regression.sh exit 0 com todos os cenários PASS.

---

## Open Questions

- A `regression.sh` aceita variável `HIVE_PORT` para isolamento ou precisa de flag `--port`?
  (Verificar no script antes de rodar; se não suportar, rodar cenários individualmente com
  `--port 12301`.)

---

## Risks & Dependencies

| Risco | Mitigação |
|---|---|
| Parse ASL errado nos novos planos → agentes não sobem → score 0 | Rodar smoke (`--steps 5`) antes de full run |
| `RotationsNeeded` retorna R errado por bug de rotação | Testes JUnit cobrindo todas as 4 rotações |
| `trying_rotate` vaza entre tasks (belief sem limpar) | `.abolish(trying_rotate(TaskName, _))` em Continue e Finalize antes de qualquer ação |
| Monitor MASSim (:8000) em conflito com outra worktree | Usar `--monitor` só se porta 8000 livre; default headless é seguro |

---

## Sources & Research

- [connect_protocol.asl](src/agt/common/connect_protocol.asl) — padrão dos planos BLOCO-NA-MÃO existentes
- [src/java/hive/AllReqsSatisfied.java](src/java/hive/AllReqsSatisfied.java) — padrão de IA + static check para reusar
- [conf/scenarios/07a-multi-req.json](conf/scenarios/07a-multi-req.json) — template de cenário multi-req
- [conf/scenarios/setup/07a-multi-req.txt](conf/scenarios/setup/07a-multi-req.txt) — padrão de fixture com cadeia de blocos
- [conf/scenarios/07a-wrong-blocks.json](conf/scenarios/07a-wrong-blocks.json) — template de cenário negativo
- [docs/plans/2026-06-19-005-feat-multi-req-submit-plan.md](docs/plans/2026-06-19-005-feat-multi-req-submit-plan.md) — entrega 7a que este plano estende
