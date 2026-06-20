---
title: "fix(nav): guard de falha de detach no STUCK recovery (#48)"
status: active
date: 2026-06-20
issue: "#48"
type: fix
---

# fix(nav): guard de falha de detach no STUCK recovery (#48)

## Summary

Adiciona um guard análogo ao `RotationGuard` (#47) para quebrar o loop de `detach` falho no
STUCK recovery. O bug original (107 `detach` por run) foi causado pela ausência de reset do timer
em `check_stuck`; esse reset já existe no código atual. O risco remanescente é que, em edge cases
de unificação de `attached(AX,AY)` com múltiplos blocos, a direção pode estar errada → até 6
`failed_target` por run (DoD exige < 5). O fix: contador `detach_stuck_fails` com abort após 2
falhas consecutivas.

---

## Problem Frame

`check_stuck` em `src/agt/common/perception.asl` (linhas 61–84) dispara `need_detach(DDir)` quando
o agente fica parado por ≥ 50 steps com bloco anexado e `solo_mode | pending_submit`. A direção
é derivada de `attached(AX,AY)` — correto para blocos em posições cardinais.

Com o timer (reset via `.abolish(stuck_since)+stuck_since(X,Y,N)`), o máximo de disparos em 300
steps é ≈ 6. Se a direção computada estiver errada — p.ex., `attached(AX,AY)` unifica no bloco
errado quando há múltiplos blocos — cada disparo gera um `failed_target`. Seis `failed_target`
violam o DoD (< 5 por agente).

O issue #48 documenta evidência de 107 detaches por agente (código mais antigo sem reset), mas o
risco remanescente são os edge cases residuais.

---

## Requirements

- **R1** — Após 2 `failed_target` consecutivos de `detach` durante `solo_mode`, o agente chama
  `!finalize_task` e libera a task (abort limpo).
- **R2** — Contador de falhas resetado na conclusão da task (`!finalize_task`) e após detach bem-
  sucedido com `solo_mode` ativo.
- **R3** — O threshold (2) é definido como constante Java `DetachGuard.MAX_CONSECUTIVE_FAILS` e
  verificado por JUnit, para rastreabilidade e documentação (padrão do projeto; ver RotationGuard).
- **R4** — A cascata `!offline_cascade` continua a ser invocada mesmo quando o guard dispara, para
  não quebrar o dead-reckoning.
- **R5** — DoD: `failed_target` de `detach` < 5 por agente no run IsolationRolesConfig 300 steps.

---

## Key Technical Decisions

| Decisão | Escolha | Rationale |
|---------|---------|-----------|
| Threshold | 2 | Menor que #47 (3) porque cada fire de `check_stuck` já tem intervalo ≥ 50 steps; 2 falhas = 100 steps de desperdício garantido. RotationGuard usa 3 porque rotações são mais frequentes. |
| Crença de contador | `detach_stuck_fails(TaskName, N)` | Scoped por task para não afetar outras tasks em sequência. |
| Abort por finalize_task | Sim | Consistente com #47; libera pool e limpa estado de forma uniforme. |
| Reset no sucesso | Sim, via handler `+lastActionResult(success) : lastAction(detach) & solo_mode(_)` | Garante que apenas FALHAS CONSECUTIVAS contam; uma falha→sucesso não deve abortar. O handler deve replicar o comportamento do handler genérico de sucesso. |
| Localização do guard | `perception.asl` (handler de `lastActionResult`) | Consistente com onde `rotate_pre_submit_fails` é gerido (#47); os handlers de `lastActionResult` centralizam a lógica de resultado de ação. |

---

## Scope Boundaries

### In scope
- Contador de falha de `detach` com abort limpo após 2 falhas consecutivas.
- Classe Java `DetachGuard` com constante + teste JUnit.
- Limpeza de `detach_stuck_fails` em `!finalize_task`.
- Validação por sim IsolationRolesConfig.

### Out of scope
- Redesenho do `check_stuck` ou do timer de 50 steps.
- Fix do Navigation Plan 1 (`need_detach + solo_mode + pending_submit` → move aleatório), que é comportamento intencional.
- Plans de `needs_clear_blocks` e PRE-SUBMIT detach extra blocks em `connect_protocol.asl` — estas já usam `attached(X,Y)` diretamente no contexto e são corretas.

### Deferred to Follow-Up Work
- Analyzer de `detach` por agente (para verificar DoD por inspeção sistemática de replay, não só visual).
- Refinamento da direção em `check_stuck` para múltiplos blocos (selecionar o bloco MAIS DISTANTE como critério de desempate).

---

## Implementation Units

### U1. DetachGuardTest.java — RED (test-first)

**Goal:** Criar os testes JUnit que falharão por ClassNotFoundException antes da classe existir.
Inclui um **teste RED de regressão do bug**: captura matematicamente o cenário de violação do DoD
sem o guard (6 disparos de `check_stuck` > limite DoD de 5), servindo como documentação executável
do problema.

**Requirements:** R3

**Dependencies:** —

**Files:**
- `src/test/java/hive/DetachGuardTest.java` (Create)

**Execution note:** Test-first — escrever e verificar que os testes falham (ClassNotFoundException)
ANTES de criar DetachGuard.java.

**Approach:**

Dois testes na mesma classe:

1. **`maxConsecutiveFails_e2`** — valor da constante é 2 (assertEquals). Padrão RotationGuardTest.

2. **`semGuard_checkStuck_excederia_dod`** — teste RED de regressão do bug:
   ```
   maxDisparos = 300 / 50 = 6  (steps totais / intervalo do timer)
   dodLimit    = 5             (limite do DoD: failed_target < 5 por agente)

   assertTrue(maxDisparos > dodLimit)   // bug: sem guard → 6 > 5 → viola DoD
   assertTrue(DetachGuard.MAX_CONSECUTIVE_FAILS < dodLimit)  // guard fica abaixo do DoD
   ```
   Este teste falha RED (ClassNotFoundException) antes de U2 e documenta o cenário exato:
   `check_stuck` dispara até 6 vezes em 300 steps com timer de 50; sem guard, cada disparo
   pode gerar um `failed_target`, violando o DoD. O guard coma `MAX_CONSECUTIVE_FAILS < 5`
   impede que essa violação ocorra.

**Patterns to follow:** `src/test/java/hive/RotationGuardTest.java`

**Test scenarios:**
- Valor da constante `MAX_CONSECUTIVE_FAILS` é 2 (assertEquals).
- `MAX_CONSECUTIVE_FAILS < 5` (abaixo do limite DoD).
- `300 / 50 > 5` (demonstra que o bug sem guard viola o DoD — regressão).

**Verification:** `~/tools/gradle-8.10/bin/gradle test` → FAIL com ClassNotFoundException (ambos os testes falham antes de U2).

---

### U2. DetachGuard.java

**Goal:** Criar a classe Java com a constante `MAX_CONSECUTIVE_FAILS = 2`.

**Requirements:** R3

**Dependencies:** U1

**Files:**
- `src/java/hive/DetachGuard.java` (Create)

**Approach:**
- Classe pública com constante estática `final int MAX_CONSECUTIVE_FAILS = 2`.
- Javadoc curto mencionando onde sincronizar (perception.asl) e o intervalo de ~50 steps entre disparos.

**Patterns to follow:** `src/java/hive/RotationGuard.java`

**Test scenarios:**
- `DetachGuardTest.maxConsecutiveFails_e2` passa (assertEquals(2, DetachGuard.MAX_CONSECUTIVE_FAILS)).

**Verification:** `~/tools/gradle-8.10/bin/gradle test` → PASS.

---

### U3. perception.asl — guard de falha de detach

**Goal:** Adicionar handlers de `lastActionResult` para detectar falhas de `detach` durante
`solo_mode`, acumular o contador `detach_stuck_fails(TaskName, N)` e acionar `!finalize_task`
após 2 falhas consecutivas.

**Requirements:** R1, R2, R4

**Dependencies:** U2

**Files:**
- `src/agt/common/perception.asl` (Modify)

**Approach:**

Adicionar **antes** do catch-all `+lastActionResult(_)` (linha 268):

1. **Handler de falha** — `+lastActionResult(failed_target) : lastAction(detach) & solo_mode(TaskName)`:
   - Incrementar `detach_stuck_fails(TaskName, N)` via bloco `if/else` (padrão `rotate_pre_submit_fails`).
   - Se o contador atualizado ≥ 2: print, `.abolish(detach_stuck_fails(TaskName, _))`, `!finalize_task(TaskName)`.
   - Sempre terminar com `!offline_cascade` (R4).

2. **Handler de sucesso** — `+lastActionResult(success) : lastAction(detach) & solo_mode(TaskName) & detach_stuck_fails(TaskName, _)`:
   - `.abolish(detach_stuck_fails(TaskName, _))`.
   - Replicar o comportamento do handler genérico: `-last_move_blocked`, `!dead_reckon_move`, `-last_attempted_dir(_)`, `!offline_cascade`.
   - **Nota:** Este handler precede o genérico `+lastActionResult(success)` no arquivo; garante que a cascata rode completa.

O literal `2` no ASL deve ser comentado remetendo a `DetachGuard.MAX_CONSECUTIVE_FAILS`.

**Patterns to follow:**
- `src/agt/common/connect_protocol.asl` linhas 257–295 (`rotate_pre_submit_fails`, P0/P1).
- `src/agt/common/perception.asl` linhas 230–268 (handlers de `lastActionResult`).

**Test scenarios:** (verificados via sim U5)
- Após 1 `failed_target` de detach com `solo_mode`: crença `detach_stuck_fails(T, 1)` existe; task NÃO foi finalizada.
- Após 2 `failed_target` consecutivos: `!finalize_task` disparou; `detach_stuck_fails` abolida; agente voltou ao idle.
- Após 1 `failed_target` + 1 `success` de detach: contador abolido; task continua ativa.
- `+lastActionResult(failed_target)` de `detach` sem `solo_mode`: handler de falha NÃO dispara (cai no catch-all).

**Verification:** Parse `.asl` sem erro (`gradle run` ou `gradle classes` não reporta erro de sintaxe Jason).

---

### U4. hive_agent.asl — limpar detach_stuck_fails em finalize_task

**Goal:** Garantir que `detach_stuck_fails` seja abolida sempre que `!finalize_task` é chamado,
evitando estado stale entre tasks.

**Requirements:** R2

**Dependencies:** U3

**Files:**
- `src/agt/hive_agent.asl` (Modify)

**Approach:**
- Adicionar `.abolish(detach_stuck_fails(_, _));` no corpo de `!finalize_task`, após a linha de
  `.abolish(rotate_pre_submit_fails(_, _));` (linha 188, padrão estabelecido por #47).

**Patterns to follow:** linha 188 de `src/agt/hive_agent.asl` (`.abolish(rotate_pre_submit_fails(_, _))`).

**Test scenarios:**
- (sem teste unitário separado — verificado implicitamente pela sim U5: após abort, counter limpo)

**Verification:** `grep detach_stuck_fails src/agt/hive_agent.asl` → linha presente.

---

### U5. Validação — sim IsolationRolesConfig 300 steps

**Goal:** Verificar o DoD: `detach` com `failed_target` < 5 por agente no run com `IsolationRolesConfig`.

**Requirements:** R5

**Dependencies:** U3, U4

**Files:** nenhum — step de validação.

**Approach:**
- Executar: `run-hive.sh run --conf conf/IsolationRolesConfig.json --port 12348 --monitor 8048` (background).
- Inspecionar histograma do `replay_analyze.py`: coluna `failed_target` por agente que emitiu `detach`.
- DoD: nenhum agente com `failed_target` ≥ 5 originado de `detach`.

**Test scenarios:**
- Nenhum agente exibe `failed_target` ≥ 5 no histograma de ações/resultados.
- Se algum agente abortar task por guard (log `[STUCK] Detach ABORT`), o score não deve regredir
  vs. run sem o guard — o agente liberou a task e pode tentar outra.

**Verification:** Replay analyzer mostra `failed_target` ≤ 4 por agente (ou 0 se a direção for
sempre correta no cenário). Score IsolationRolesConfig ≥ run de referência (tolerância: variância
natural de ±1 pontos).

---

## Risks & Dependencies

| Risco | Probabilidade | Mitigação |
|-------|--------------|-----------|
| Handler de sucesso de `detach` precede o genérico e omite alguma lógica | Média | Replicar comportamento completo do handler genérico; revisar dead_reckon_move e offline_cascade. |
| Falha no parse `.asl` por erro de sintaxe no bloco `if/else` do guard | Baixa | Verificar sintaxe antes de rodar a sim; usar `gradle classes` (compila Java) + `gradle run` rápido (≈ 15 steps). |
| Score IsolationRolesConfig regride após abort precoce de task | Baixa | Threshold 2 é conservador (≥ 100 steps de atraso garantidos antes do abort); agente libera pool e inicia nova task. |

---

## Sources & Research

- Issue #48: `gh issue view 48 --repo MarceloNG/PCS5703-MAS-HIVE`
- Padrão RotationGuard: `src/java/hive/RotationGuard.java` + `src/test/java/hive/RotationGuardTest.java`
- Padrão rotate_pre_submit_fails: `src/agt/common/connect_protocol.asl` linhas 257–295
- check_stuck atual: `src/agt/common/perception.asl` linhas 61–84
- finalize_task: `src/agt/hive_agent.asl` linhas 162–194
- Config de validação: `conf/IsolationRolesConfig.json` (40×40, 300 steps, randomFail:1)
