---
title: "fix: Loop de pré-rotação sem limite — guard de falhas consecutivas (#47)"
date: 2026-06-19
status: completed
issue: "https://github.com/MarceloNG/PCS5703-MAS-HIVE/issues/47"
track: frente:pontuar
---

# fix: Loop de pré-rotação sem limite — guard de falhas consecutivas (#47)

## Sumário

Adicionar um guard de falhas consecutivas à pré-rotação de submit em
`connect_protocol.asl`. Quando `rotate(Dir)` falha repetidamente (agente ou
obstáculo bloqueando), o plan atual re-dispara infinitamente até o deadline
expirar. O fix introduz a crença `rotate_pre_submit_fails(TaskName, F)` como
contador por-agente por-task, abortando após 3 falhas consecutivas.

Abordagem TDD: primeiro escrever testes vermelhos para a classe Java
`RotationGuard` (que não existe ainda), depois implementá-la, depois integrar
ao `.asl`.

---

## Problema

**Root cause completo (causal chain):**

1. Agente entra em `pending_submit(TaskName)` e chega na `goalZone(0,0)`.
2. `hive.RotationsNeeded(TaskName, R, Dir)` retorna R>0 (bloco desalinhado).
3. Plan dispara → emite `rotate(Dir)`.
4. `rotate(Dir)` falha (`lastActionResult(failed)`) porque outro agente ou
   obstáculo está adjacente.
5. **O percept `attached(AX,AY)` não muda** (bloco não girou).
6. No step seguinte, `RotationsNeeded` relê os mesmos `attached` e retorna
   R>0 novamente → mesmo plan dispara → loop até o deadline expirar.

**Evidência medida** (run IsolationRolesConfig `bwugfcyq3`):
- agentA5: `rotate:135`, `failed:131` (97% de falha). 28 steps consecutivos
  com a mesma mensagem "[SUBMIT] Pre-rotacao pre-submit: cw (1 restantes) p/
  task2" sem progresso.

**Localização do bug:**
`src/agt/common/connect_protocol.asl`, plan (linhas 263-267):
```
+step(N)
    : pending_submit(TaskName) & goalZone(0, 0) & not submitted_task(_)
      & hive.RotationsNeeded(TaskName, R, Dir)
    <- ...rotate(Dir)...
```
Sem nenhum tratamento de `lastActionResult(failed)` neste contexto.

---

## Requisitos

- **R1**: Após N falhas consecutivas de `rotate` na pré-rotação de submit,
  o agente deve abortar a task (via `!finalize_task`) em vez de continuar
  tentando indefinidamente.
- **R2**: O threshold de falhas (N=3) deve ser definido em Java como constante
  testável (`RotationGuard.MAX_CONSECUTIVE_FAILS`).
- **R3**: O contador deve ser por-agente por-task (crença Jason com chave
  `TaskName`).
- **R4**: Ao finalizar a task (por qualquer caminho), o contador deve ser
  limpo para não contaminar tasks futuras.
- **R5**: Regressão `06c-single-collect` deve continuar PASS (`submits_ok ≥ 1`).

---

## Decisões técnicas chave

| Decisão | Escolha | Alternativa rejeitada |
|---------|---------|----------------------|
| Onde vive o threshold | Java const `RotationGuard.MAX_CONSECUTIVE_FAILS = 3` | Hardcoded só no .asl (não testável) |
| Onde vive o contador | Crença Jason `rotate_pre_submit_fails(TaskName, F)` | IA Java com `static Map<String,Integer>` (estado compartilhado entre agentes) |
| Ação no abort | `!finalize_task(TaskName)` (libera task e agente) | Tentar outra GZ cell (complexo, fora de escopo #47) |
| Limpeza do contador | Em `finalize_task` via `.abolish` | Reset manual por sucesso (redundante se finalize_task cobre) |
| Threshold = 3 | 3 steps consecutivos (razoável para ambiente bloqueado) | 1 (muito agressivo), 5+ (expira a task antes de reagir) |

**Nota de acoplamento**: o valor `3` no `.asl` espelha `RotationGuard.MAX_CONSECUTIVE_FAILS`. Se mudar a constante Java, atualizar o literal no `.asl` também (comentado no código).

---

## Postura de execução

Execution note (TDD): **U1 escreve os testes vermelhos antes de U2 implementar
a classe**. Verificar que os testes falham por `ClassNotFoundException`, não por
erro de asserção.

---

## High-Level Technical Design

Sequência de steps após o fix:

```
Step N: rotate(Dir) → failed
    ↓
Step N+1:
    [plan 1] lastAction(rotate) & lastActionResult(failed) & pending_submit & goalZone(0,0)
             → incrementa rotate_pre_submit_fails(Task, F+1); skip

Step N+2 (se F+1 < 3):
    [plan 2] pending_submit & goalZone(0,0) & RotationsNeeded & F < 3
             → rotate(Dir) → falha novamente

Step N+3 (F=3):
    [plan 0] rotate_pre_submit_fails(Task, F) & F >= 3
             → abolish contador; !finalize_task(Task); skip
```

Prioridade de planos no arquivo (top = maior prioridade):
```
P0: abort (F >= 3)       ← dispara antes da rotação
P1: incrementar falha    ← detecta lastActionResult(failed)
P2: rotate (F < 3)       ← guard adicionado
P3: submit               ← plano existente, não alterado
```

---

## Unidades de implementação

### U1. RotationGuardTest.java (VERMELHO — testes que falham)

**Goal:** Criar testes JUnit que documentam o comportamento esperado de
`RotationGuard` e falham porque a classe não existe.

**Execution note:** Escrever primeiro. Verificar que `gradle test` produz
`ClassNotFoundException: hive.RotationGuard`. Não avançar para U2 antes disso.

**Requirements:** R1, R2

**Dependencies:** nenhuma

**Files:**
- `src/test/java/hive/RotationGuardTest.java` (criar)

**Approach:**
Classe de testes com método estático `RotationGuard.shouldAbort(int failCount)`.
Padrão idêntico a `RotationsNeededTest.java`: sem dependência de Jason, sem
simulador, só Java pura.

**Patterns to follow:** `src/test/java/hive/RotationsNeededTest.java`

**Test scenarios:**
- `naoAborta_com0Falhas()`: `shouldAbort(0)` → `false`
- `naoAborta_com1Falha()`: `shouldAbort(1)` → `false`
- `naoAborta_com2Falhas()`: `shouldAbort(2)` → `false`
- `aborta_exatamenteNoMaximo()`: `shouldAbort(3)` → `true`
- `aborta_acimaDo_Maximo()`: `shouldAbort(4)` → `true`
- `maxConsecutiveFails_e3()`: `RotationGuard.MAX_CONSECUTIVE_FAILS == 3`

**Verification:** `~/tools/gradle-8.10/bin/gradle test` falha com
`ClassNotFoundException: hive.RotationGuard` em todos os 6 testes.

---

### U2. RotationGuard.java (VERDE — implementar a classe)

**Goal:** Implementar `RotationGuard` como utilitário estático puro. Sem estado.
O contador fica nas crenças Jason; esta classe apenas encapsula a lógica de
threshold.

**Requirements:** R1, R2

**Dependencies:** U1

**Files:**
- `src/java/hive/RotationGuard.java` (criar)

**Approach:**
```
package hive;

public class RotationGuard {
    // Sincronizar com o literal no connect_protocol.asl se mudar
    public static final int MAX_CONSECUTIVE_FAILS = 3;

    public static boolean shouldAbort(int consecutiveFails) {
        return consecutiveFails >= MAX_CONSECUTIVE_FAILS;
    }
}
```
Sem campo estático (sem estado compartilhado entre agentes). Sem dependências
de Jason — testável em JUnit puro.

**Patterns to follow:** `src/java/hive/RotationsNeeded.java` (mesma estrutura
de package, sem imports de Jason no corpo de lógica pura)

**Test scenarios:** (herdados de U1 — os 6 testes passam após implementação)

**Verification:** `gradle test` — 6 testes do `RotationGuardTest` passam; todos
os testes anteriores continuam passando.

---

### U3. Fix connect_protocol.asl + limpeza em finalize_task

**Goal:** Integrar o guard no fluxo de pré-rotação de submit. Adicionar três
planos novos e atualizar `finalize_task` para limpar a crença.

**Requirements:** R1, R3, R4

**Dependencies:** U2

**Files:**
- `src/agt/common/connect_protocol.asl` (modificar)
- `src/agt/hive_agent.asl` (modificar — adicionar abolish em `finalize_task`)

**Approach:**

Inserir na seção `--- ROTAÇÃO PRÉ-SUBMIT ---` de `connect_protocol.asl`,
**antes** do plan de rotação existente (linhas 263-267), na ordem:

1. **Plano P0 — abort quando F >= 3**:
   Contexto: `pending_submit(T) & goalZone(0,0) & not submitted_task(_)
   & rotate_pre_submit_fails(T,F) & F >= 3`
   Body: `.abolish(rotate_pre_submit_fails(T,_))`, `!finalize_task(T)`, `skip`.

2. **Plano P1 — incrementar falha**:
   Contexto: `pending_submit(T) & goalZone(0,0) & not submitted_task(_)
   & lastAction(rotate) & lastActionResult(failed)`
   Body: incrementar `rotate_pre_submit_fails(T,F)` (abolish+assert), `skip`.

3. **Plano P2 — rotação com guard** (substitui o plan atual 263-267):
   Contexto: adicionar `(not rotate_pre_submit_fails(T,F) | F < 3)` ao guard
   existente.

Em `hive_agent.asl`, em `+!finalize_task(TaskName)` (linha 162), adicionar:
```
.abolish(rotate_pre_submit_fails(_, _));
```
após os `.abolish` existentes.

**Patterns to follow:**
- Padrão de abort: mesma estrutura de `trying_rotate cleanup` (linhas 124-136)
- Padrão de increment: mesma estrutura de `nav_block_count` (linhas 440-441)

**Test scenarios (via análise do .asl):**
- Parse sem erro: `gradle run` sobe 15 agentes sem ClassNotFoundException
- Plano P0 tem prioridade sobre P2 (aparece antes no arquivo)
- Plano P1 cobre `lastActionResult(failed)` sem colidir com os planos de
  `submitted_task` (esses têm `not submitted_task(_)` distinto)

**Verification:**
- `gradle classes` compila sem erro
- `.asl` carrega: nenhum erro de parse em agent log (verificar manualmente
  após `gradle run`)
- Belief `rotate_pre_submit_fails` não aparece em `finalize_task` de agentes
  que completaram tasks com sucesso (limpa corretamente)

---

### U4. Regressão 06c-single-collect

**Goal:** Confirmar que o fix não quebrou o caminho de submit feliz.

**Requirements:** R5

**Dependencies:** U3

**Files:** nenhum (apenas validação)

**Approach:**
Rodar `06c-single-collect` com `--assert` na worktree `sc47`:
```bash
.claude/skills/run-hive/run-hive.sh run \
  --scenario 06c-single-collect \
  --assert \
  --port 12347 \
  --monitor 8047
```

**Test scenarios:**
- `[PASS] submits_ok >= 1`: ao menos 1 submit bem-sucedido no time

**Verification:** Exit 0 do script (`[PASS]` na saída do assert).

---

## Fronteiras de escopo

**Dentro do escopo:**
- Guard de falhas consecutivas na pré-rotação de submit (context `pending_submit & goalZone(0,0)`)
- Nova classe Java `RotationGuard` com threshold como constante testável
- Limpeza de `rotate_pre_submit_fails` em `finalize_task`

**Fora do escopo (Deferred to Follow-Up Work):**
- #48 — loop de detach no STUCK recovery (issue separada)
- #49 — A* fallback e exploração por setor (Eixo N)
- Estratégia de fallback de GZ cell quando rotação falha repetidamente
  (mover para outra posição da goal-zone antes de abortar) — valor incremental,
  pode ser #47b
- Gate de harness de flakiness (#41)

---

## Dependências / pré-requisitos

- Main em `c736307` (issues #47/#48/#49 criadas, backlog atualizado)
- Worktree `sc47` a criar em `/home/mgrim/repos/PCS5703-MAS-HIVE-sc47`,
  branch `feat/sc-47-rotation-fix`
- Portas: `--port 12347 --monitor 8047` (convenção issue NN → 12300+NN / 8000+NN)
- Gradle local: `~/tools/gradle-8.10/bin/gradle`

---

## Riscos

| Risco | Probabilidade | Mitigação |
|-------|--------------|-----------|
| Prioridade de planos errada: P2 dispara antes de P0/P1 | Média | Inserir P0, P1, P2 nessa ordem (antes do plan atual 263) |
| `rotate_pre_submit_fails` persiste entre runs de task | Baixa | `finalize_task` limpa via `.abolish(rotate_pre_submit_fails(_,_))` |
| Parser .asl rejeita sintaxe de crença com aritmética inline | Baixa | Usar variável intermediária: `NF = F + 1; +rotate_pre_submit_fails(T, NF)` |

---

## Sources & Research

- `src/agt/common/connect_protocol.asl` — plano bugado (linhas 263-267)
- `src/agt/hive_agent.asl:162` — `finalize_task` (padrão de abolish)
- `src/java/hive/RotationsNeeded.java` — IA stateless (não requer alteração)
- `src/test/java/hive/RotationsNeededTest.java` — padrão de teste a seguir
- Run `bwugfcyq3` (IsolationRolesConfig) — evidência: A5 rotate:135/failed:131
- Issue #47: [github.com/MarceloNG/PCS5703-MAS-HIVE/issues/47](https://github.com/MarceloNG/PCS5703-MAS-HIVE/issues/47)
