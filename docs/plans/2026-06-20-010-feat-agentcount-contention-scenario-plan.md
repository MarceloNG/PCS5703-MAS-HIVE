---
title: "feat: agentCount por cenário + cenário de contenção multi-task (#42)"
status: active
created_at: 2026-06-20
type: feat
issue: "MarceloNG/PCS5703-MAS-HIVE#42"
---

# feat: `agentCount` por cenário + cenário de contenção multi-task (#42)

## Summary

O harness sempre sobe 15 agentes e o servidor aceita 15 slots — mesmo em cenários que testam
1 capacidade de 1 agente. Isso é a raiz do flakiness do 06c (14 agentes competindo pelo mesmo
bloco) e o motivo do skip atual dos cenários 06c-*. Este plano adiciona `agentCount: N` ao schema
de cenário e ao harness, calibra os cenários existentes com o número correto de agentes, e cria
o cenário de contenção multi-task que é o DoD do #42 e gate do #40.

---

## Problem Frame

Dois problemas acoplados:

**1. Cenários de capacidade isolada têm 15 agentes ativos.** Um cenário como `06c-single-collect`
(testar 1 agente coletando 1 bloco) tem 14 outros agentes competindo pelo mesmo bloco, request
slot e goal zone. O resultado é enxame → flakiness → skip. O workaround atual (estacionar 14
agentes via setup file) é frágil e polui os setups. A causa raiz é que `entities.standard=15` e
`eismassimconfig.count=15` são fixos — o harness sempre conecta o time inteiro.

**2. Não há cenário que teste contenção real.** Todos os cenários testam 1 capacidade isolada
(1 agente, 1 task). O comportamento que derruba o time em produção — N agentes disputando M tasks,
enxame numa só, ociosidade na outra — nunca foi testado em isolamento. O #38 (time flat) revelou
esse gap: a regressão de contenção só apareceu acidentalmente via flakiness do 06c.

---

## Requirements

**Suporte a agentCount no harness**
- R1. Campo `agentCount: N` (inteiro ≥ 1) em cenário JSON configura o número de agentes ativos
  para aquele run. Campo opcional; omitido = comportamento atual (15 agentes).
- R2. Com `agentCount: N`, `run-hive.sh` aplica `AGENTS=N` ao `patch_conf` → `entities.standard: N`
  em todos os blocos `match` da config efetiva.
- R3. Com `agentCount: N`, `run-hive.sh` gera um `eismassimconfig` temporário com `count: N` e
  passa `-PeisConf=` apontando para ele; assim só N agentes autenticam com o servidor.
- R4. Quando `agentCount` não está presente no cenário, o comportamento é idêntico ao atual.

**Calibração dos cenários existentes**
- R5. Cenários de capacidade isolada (1 tarefa, 1-4 agentes suficientes) recebem `agentCount`
  correto para eliminar a interferência dos agentes extras.
- R6. Os dois cenários `06c-*` têm o campo `skip` removido e são calibrados com `agentCount: 2`.
  Os setup files correspondentes são simplificados (sem parking de 14 agentes; agents referenciados
  mudam para A1/A2).

**Cenário de contenção**
- R7. Novo `conf/scenarios/10-contention.json` com 6 workers, 2 tasks concorrentes em regiões
  distintas do mapa, 50 steps.
- R8. O setup coloca 6 agentes em posições iniciais com role-zones próximas, 2 dispensers de
  tipos distintos em lados opostos do grid, 1 ou 2 goal zones centrais, e cria as 2 tasks.
- R9. Assert: `submits_ok >= 1` + `role_adoption >= 4` (pelo menos 4 dos 6 adotaram worker).
- R10. O cenário passa o gate de flakiness com `--repeat 3` (estável, não depende de sorte).

---

## Key Technical Decisions

- **Option B (entities + eismassimconfig), sem geração de hive.jcm temporário.** Cenários de
  navegação (`02-navigate-open`, `03-*`) já têm `entities: {standard: 1}` e funcionam corretamente
  com 14 agentes idle (auth failed, sem entidade MASSim, sem percepções EIS). Isso prova que a
  abordagem é válida: o servidor aceita N slots, os N primeiros agentes autenticam, os 15-N restantes
  ficam idle no JaCaMo sem interferir. Gerar um `hive.jcm` temporário com exatamente N agentes
  (Option C) exigiria mudança no `build.gradle` e lógica de geração não-JSON — custo alto, ganho
  marginal. Fica como extensão futura se o ruído dos idle agents se tornar problema.

- **Assert `submits_ok >= 1` para o cenário de contenção como gate inicial.** O assert estrito de
  não-enxame (`submits_ok >= 2`, provando que 2 tasks distintas foram completadas em paralelo) é o
  gate real do #40 (TaskAllocator). Hoje, sem alocação descentralizada, não há garantia de que ambas
  as tasks recebam workers — um único agente pode completar 1 task enquanto os outros idle. O assert
  `>= 1` + `role_adoption >= 4` com `--repeat 3` é o gate de smoke para o cenário. Ampliar para
  `>= 2` fica explicitamente gated no #40.

- **Geração do eismassimconfig temporário reutiliza o padrão de porta.** `run-hive.sh` já gera um
  `eismassimconfig` por-porta no `$LOGDIR`. A extensão para `agentCount` segue o mesmo padrão:
  quando `agentCount` está presente no cenário (com ou sem `--port`), gerar
  `$LOGDIR/eismassimconfig.json` com `count: N`. Se `--port` já gerou um, aplicar o `count: N`
  em cima. A variável `$LOGDIR` existe mesmo sem `--port` (`/tmp/hive-run/` por default).

- **`patch_conf` recebe novo override `AGENTS=N`.** Segue o padrão dos overrides existentes
  (`STEPS`, `PORT`, `SETUP`, etc.): aplica `entities.standard: N` em todos os blocos `match` da
  config. Manter a adição de `agentCount` no JSON do cenário como campo top-level (ao lado de
  `assert` e `skip`), não dentro de `match` — é metadado do harness, não do servidor MASSim.

- **Setup files dos cenários calibrados referenciam A1..AN.** Com `agentCount: 2`, só agentA1 e
  agentA2 existem no servidor. Os setup files devem referenciar apenas esses agentes (sem mover
  A3..A15, que não existem). Os arquivos que hoje estacionam 14 agentes na borda serão simplificados
  para só posicionar os N agentes ativos.

---

## Scope Boundaries

### In Scope
- Suporte a `agentCount` no harness (U1)
- Calibração dos cenários existentes + remoção do skip nos 06c (U2)
- Cenário de contenção 10-contention (U3)

### Deferred to Follow-Up Work
- **Option C (hive.jcm temporário via `-PjcmFile` em `build.gradle`):** se agentes idle na org
  MOISE+ causarem falsos positivos de norma ou ruído no replay, implementar como extensão de U1.
- **Métrica `tasks_attempted` em `assert_metric.py`:** mede quantas tasks distintas receberam
  pelo menos 1 worker; gate de não-enxame real. Bloqueado pelo #40 (sem alocação descentralizada,
  a métrica FAIL toda vez e não serve como gate).
- **`submits_ok >= 2` no cenário de contenção:** ampliar o assert após #40 validar que 2 tasks
  são trabalháveis em paralelo de forma estável.
- **Explorar `04b-explore-conflict`:** cenário com entities=6 mas assert trivial (min: 0); fora
  do escopo deste plano (é do track de mapeamento, não de contenção/alocação).

---

## Implementation Units

### U1. Suporte a `agentCount` em `run-hive.sh`

**Goal:** quando o JSON do cenário contém `agentCount: N`, o harness produz um run com exatamente
N agentes autenticados — server slots = N, eismassimconfig count = N.

**Requirements:** R1–R4.

**Dependencies:** nenhuma.

**Files:**
- `.claude/skills/run-hive/run-hive.sh` — modificado
- `.claude/skills/run-hive/SKILL.md` — atualizado (docs)

**Approach:**

Detecção: após ler o JSON do cenário (já disponível em `$scen_conf`), extrair `agentCount` com
um trecho Python inline (padrão do driver). Se presente e ≥ 1, ativar o modo de isolamento de
agente.

Patch do servidor: adicionar suporte a `AGENTS=N` em `patch_conf`. A função já itera sobre os
blocos `match`; adicionar a branch: se `"AGENTS" in ov`, setar `m["entities"]["standard"] = int(ov["AGENTS"])`.

Geração do eismassimconfig: replicar o padrão do bloco de porta. Se `agentCount` foi detectado,
gerar `$LOGDIR/eismassimconfig.json` (criando o LOGDIR se necessário) com `count` alterado para N.
Se `--port` já tiver gerado o arquivo, fazer a patch em cima (abrir, alterar `count`, reescrever).
Definir `eis_arg="-PeisConf=$EIS_CONF"` (ou atualizar o existente).

Log de diagnóstico: emitir linha `[agentCount] N agentes ativos (entities=$N, eis.count=$N)` quando
o modo está ativo.

**Patterns to follow:**
- Bloco de patch de porta (~linhas 159–165 de `run-hive.sh`): Python inline para patchar JSON
- Bloco de `patch_conf` (~linhas 84–111): override `STEPS`, `PORT`, `SETUP` como modelo para `AGENTS`
- `extra_flags` → `eis_arg`: mecânica de acumulação de args para `gradle`

**Test scenarios:**

Happy path:
- Cenário com `agentCount: 2`: config efetiva tem `entities.standard: 2`; eismassimconfig tem
  `count: 2`; log mostra `[agentCount] 2 agentes ativos`.
- Cenário sem `agentCount`: comportamento idêntico ao atual — sem alteração em entities ou
  eismassimconfig, sem linha de log de agentCount.
- `agentCount: 2` combinado com `--port 12341`: eismassimconfig por-porta é gerado com porta E
  count corretos; o `-PeisConf=` aponta para um único arquivo combinado.

Validação de campo:
- `agentCount: 0` ou negativo: tratado como "não presente" (aviso no log, comportamento default).
- `agentCount` ausente em cenário sem bloco `match`: sem crash; modo agentCount inativo.

Compatibilidade:
- Cenários existentes sem `agentCount` (todos os atuais): nenhuma diferença de comportamento;
  testes de regressão da suíte passam sem mudança.

**Verification:** rodar `run-hive.sh run --scenario 06c-single-collect --assert` após U2 e
confirmar PASS; inspecionar `$LOGDIR/eismassimconfig.json` e ver `count: 2`.

---

### U2. Calibrar cenários existentes com `agentCount`

**Goal:** adicionar `agentCount: N` ao JSON de cada cenário existente que se beneficia de
isolamento de agentes; simplificar os setup files correspondentes; remover skip dos 06c.

**Requirements:** R5, R6.

**Dependencies:** U1 (harness deve suportar `agentCount` antes de serem úteis).

**Files:**
- `conf/scenarios/06c-single-collect.json` — agentCount: 2, remover skip
- `conf/scenarios/06c-collect-rotate.json` — agentCount: 2, remover skip
- `conf/scenarios/06-single-block.json` — agentCount: 2
- `conf/scenarios/07a-multi-req.json` — agentCount: 4
- `conf/scenarios/07a-prime-rotate.json` — agentCount: 2
- `conf/scenarios/07a-prime-wrong.json` — agentCount: 2
- `conf/scenarios/07a-wrong-blocks.json` — agentCount: 2
- `conf/scenarios/setup/06c-single-collect.txt` — simplificar (remover parking de 14; referenciar A1)
- `conf/scenarios/setup/06c-collect-rotate.txt` — idem
- `conf/scenarios/setup/06-single-block.txt` — idem
- `conf/scenarios/setup/07a-*.txt` — referenciar apenas A1..A4

**Approach:**

Princípio de calibração:
- Cenários de "1 capacidade, 1 agente" (coleta solo, submit solo): `agentCount: 2` (A1 = worker
  ativo; A2 = backup que pode adotar mas não interfere na task specific).
- Cenários de "multi-bloco / multi-agente por design" (07a-multi-req): `agentCount: 4` (suficiente
  para 1 coordinator + 2-3 collectors sem os outros 11 interferindo).
- Cenários de "teste de rejeição" (07a-prime-wrong, 07a-wrong-blocks — assert equals: 0):
  `agentCount: 2` (2 agentes tentando → 0 submits é mais robusto e rápido).
- Manter em 15: `00-smoke` e `01-adopt` testam capacidade do TIME INTEIRO — reduzir quebraria
  o assert `role_adoption >= 10`.

Simplificação dos setup files dos 06c: com `agentCount: 2`, só agentA1 e agentA2 existem no
servidor. Remover todas as linhas `move X Y agentA3..agentA15` e mudar o agente-alvo de A4 para
A1. Manter a posição e layout geométrico (role-zone, dispenser, goal-zone) idênticos ao atual.

Para os 07a com agentCount=4: referenciar agentA1..agentA4 nos setup files; remover linhas que
posicionam A5..A15.

**Patterns to follow:** `conf/scenarios/setup/09a-norm-carry.txt` (setup file simples com 1 agente,
referencia só agentA1) como modelo de setup enxuto.

**Test scenarios:**

Remoção de skip:
- `run-hive.sh run --scenario 06c-single-collect --assert` → PASS (não mais SKIP).
- `run-hive.sh run --scenario 06c-collect-rotate --assert` → PASS.

Isolamento verificado:
- Replay do cenário 06c-single-collect mostra exatamente 2 entidades no step 1 (não 15).
- Setup file não faz referência a agentA3+ (grep setup/*.txt).

Asserts intactos:
- Todos os outros cenários (`00-smoke`, `01-adopt`, `03-*`, `09a-norm-carry`) continuam PASS —
  nenhum assert foi mudado neste unit, só agentCount e setup.

Regressão de suíte:
- `regression.sh` (sem --repeat) roda toda a suíte sem FAIL (cenários skip removidos agora passam;
  nenhum cenário previamente PASS virou FAIL).

**Verification:** `regression.sh` full suite PASS sem SKIP (exceto se algum cenário tiver skip
intencional por razão diferente de agentCount).

---

### U3. Cenário de contenção multi-task (`10-contention`)

**Goal:** criar o cenário que testa N workers disputando M tasks — a classe de defeito que causou
o #38 — com assert verificável por sim.

**Requirements:** R7–R10.

**Dependencies:** U1 (agentCount: 6 no JSON do cenário), U2 (padrão de setup simplificado).

**Files:**
- `conf/scenarios/10-contention.json` — criado
- `conf/scenarios/setup/10-contention.txt` — criado

**Approach:**

Design do cenário: grid 30×30, 6 agentes (agentA1..A6), 50 steps.
- **Geometria:** 2 dispensers em lados opostos (ex: b1 em (5, 5), b2 em (25, 5)); 1 goal zone
  central (ex: (15, 25)); 6 role zones espalhadas na área central.
- **Tasks:** `t1` (1 bloco b1, offset leste) e `t2` (1 bloco b2, offset oeste), ambas com
  deadline 50. As tasks são independentes e completáveis por agentes distintos.
- **Posicionamento inicial:** 6 agentes em posições distintas na faixa central do mapa (y=12..18),
  cada um sobre uma role-zone (adoção imediata no step 1). Sem dois agentes na mesma célula.

Assert:
```json
"assert": [
  { "metric": "role_adoption", "min": 4 },
  { "metric": "submits_ok", "min": 1 }
]
```

O assert `submits_ok >= 1` valida que o cenário é completável (ao menos 1 task foi submetida em
50 steps com 6 workers). O `role_adoption >= 4` valida que a maioria adotou worker (senão o cenário
travaria em adoção). O assert estrito de não-enxame (`submits_ok >= 2`) fica deferred ao #40.

**Patterns to follow:**
- `conf/scenarios/06c-single-collect.json` (referência de cenário de coleta com assert)
- `conf/scenarios/setup/09a-norm-carry.txt` (setup limpo com agentes em posições planejadas)
- Gramática do setup: `move X Y agentAN | terrain X Y role|goal | add X Y dispenser <tipo> | create task <n> <dl> bx,by,t`

**Test scenarios:**

Execução básica:
- `run-hive.sh run --scenario 10-contention --assert` → PASS (submits >= 1, role_adoption >= 4).
- Config efetiva tem `entities.standard: 6`; eismassimconfig tem `count: 6`.

Flakiness gate:
- `regression.sh --repeat 3 10-contention` → `3/3 PASS` (cenário estável, não depende de luck).
- Ou no mínimo `2/3 PASS` — se o cenário for flaky com a lógica atual, documentar a taxa real e
  ajustar o setup até atingir 3/3 antes de marcar U3 como completo.

Isolamento do assert:
- `run-hive.sh run --scenario 10-contention` (sem --assert) mostra score + replay analysis.
- Replay mostra exatamente 6 entidades (não 15), 2 tasks no board.

**Verification:** `regression.sh --nn 42 --repeat 3 10-contention` PASS (3/3 ou 2/3 documentado)
após U1 e U2 em vigor.

---

## Open Questions

- **Taxa mínima aceitável para o gate de flakiness do 10-contention:** o requisito deste plano
  é "estável" — definido operacionalmente como 3/3 PASS com `--repeat 3`. Se o cenário passar
  só 2/3, revisar o setup (mais steps, posições iniciais, grid layout) antes de aceitar.
- **`agentCount` e `--repeat` com `--port`:** o eismassimconfig por-porta já integra a patch
  de count (combinados no mesmo arquivo pelo U1). Confirmar em testes que a combinação
  `--nn 42 --repeat 3` não gera conflito de arquivos entre os runs sequenciais.

---

## Sources & Research

- Insight direto da observação de `entities: {standard: 15}` em todos os cenários (conversa, 2026-06-20)
- Cenário `02-navigate-open` (entities=1) como evidência de que 14 idle agents não causam falso positivo
- Cenário `04b-explore-conflict` (entities=6) como precedente de contagem reduzida
- Issue #42 (DoD): `conf/scenarios/NN-contention.json` com asserts de não-enxame + no-idle + submit estável
- Issue #40 (gate): o cenário de contenção é a prova de aceitação e2e do TaskAllocator
