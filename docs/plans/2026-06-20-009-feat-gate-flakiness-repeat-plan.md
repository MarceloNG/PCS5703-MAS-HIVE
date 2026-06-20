---
title: "feat: Gate de flakiness — --repeat N --min-pass K no harness de regressão"
status: completed
created_at: 2026-06-20
type: feat
issue: "MarceloNG/PCS5703-MAS-HIVE#41"
---

# feat: Gate de flakiness — `--repeat N --min-pass K` no harness de regressão

## Summary

O `regression.sh` roda cada cenário uma única vez. Variância estocástica (escalonamento Jason, timing de conexão) faz capacidades flaky passarem ~50% das vezes — um run nunca detecta isso. Este plano adiciona `--repeat N --min-pass K`: cada cenário roda N vezes em série; é verde só se ≥K runs passarem; a taxa aparece no relatório (`"06c: 2/5 PASS — FLAKY"`). Fecha #41.

---

## Problem Frame

O `regression.sh` roda cada cenário exatamente uma vez. O comportamento estocástico (escalonamento Jason, timing de conexão dos agentes) faz capacidades instáveis passarem em ~50% dos runs — um único run não distingue "capacidade confiável" de "sorte de 1 tentativa". Isso foi observado concretamente no cenário `06c`: passou isolado e falhou na suíte de 1-run, quase deixando uma regressão de confiabilidade passar.

O gate de flakiness resolve medindo a taxa real: repetir N vezes e exigir ≥K PASSes torna a "aprovação" estatisticamente significativa.

---

## Requirements

**Gate de repetição**
- R1. `regression.sh` aceita `--repeat N` (inteiro ≥ 1; default 1).
- R2. `regression.sh` aceita `--min-pass K` (inteiro; 1 ≤ K ≤ N; default = N quando omitido).
- R3. Com `--repeat N`, cada cenário não-skipped é rodado N vezes em série, acumulando `pass_count`.
- R4. Veredito do cenário: PASS se `pass_count ≥ min-pass`, FAIL caso contrário.

**Relatório**
- R5. Quando N > 1, a linha de resultado por cenário exibe a taxa: `[nome] pass_count/N PASS`.
- R6. Tag `FLAKY` aparece quando `0 < pass_count < min-pass` (instável); `FAIL` quando `pass_count == 0`.
- R7. Quando N == 1 (default), a saída é idêntica à atual — sem regressão de output.
- R8. O sumário final conta cenários (PASS/FAIL/SKIP), não runs individuais; quando N > 1, adiciona contexto de repeat.

---

## Key Technical Decisions

- **Repeat loop em `regression.sh`, não em `run-hive.sh`.** O driver tem contrato de run-único por invocação; a orquestração de múltiplos runs é responsabilidade do suíte runner. Manter a separação evita engordar o driver e deixa cada camada com uma responsabilidade.
- **Min-pass default = N (todos devem passar).** É o conservador correto: a menos que o usuário peça explicitamente tolerância a falhas, um cenário que falha 1× em 5 deve ser considerado instável.
- **`--nn` com `--repeat` reutiliza a mesma porta sequencialmente.** Runs do mesmo cenário são séries — reuso da porta é seguro. O driver já limpa antes de cada run.
- **Bash puro para aggregação** — nenhuma dependência externa nova; o harness já é bash+python e a lógica de contagem é trivial.

---

## Scope Boundaries

### Deferred to Follow-Up Work
- Variante `randomFail > 0`: rodar cenários com falha de ação ativa para medir robustez (marcado "Opcional" na issue #41). Depende de um `--random-fail` no `run-hive.sh` que ainda não existe.
- Execução paralela dos N repeats (reduziria o tempo de wait; requer isolamento de porta automático por repeat-idx).

---

## Implementation Units

### U1. Repeat loop + aggregation em `regression.sh`

**Goal:** adicionar parsing de `--repeat N` e `--min-pass K`, substituir o run-único por um loop de N invocações do driver, e emitir o relatório com taxa.

**Requirements:** R1–R8.

**Dependencies:** nenhuma (autocontido em `regression.sh`).

**Files:**
- `.claude/skills/run-hive/regression.sh` — modificado

**Approach:**

Parsing (no bloco `while` existente, ao lado de `--nn`): capturar `--repeat` e `--min-pass` como variáveis; validar que N ≥ 1 e K ≤ N; after the parse loop, default `min_pass=${min_pass:-$repeat}`.

Per-scenario block (substituir o `if "$DRIVER" run ...` existente): loop de `$repeat` runs acumulando `pass_count`. O veredito por cenário é calculado uma vez ao fim do loop interno.

Relatório por cenário:
- N == 1 (default): manter formato atual (`✓ [$n] PASS` / `✗ [$n] FAIL`)
- N > 1: `✓/✗ [$n] pass_count/N PASS` + tag opcional (`FLAKY` se `pass_count < min_pass && pass_count > 0`; `FAIL` se `pass_count == 0`)

Sumário final: quando N > 1, adicionar linha de contexto `(repeat=${repeat}, min-pass=${min_pass})` antes da linha de resultado.

**Patterns to follow:** estrutura de parsing `--nn` existente; flags validadas com mensagem de erro para stderr + `exit 2`; acumuladores `pass`/`fail`/`skip` já presentes (reaproveitar o mesmo padrão para o cenário inteiro, não para os runs individuais).

**Test scenarios:**

Happy path:
- `--repeat 5` em cenário estável (ex.: `01-adopt`) → todos os 5 passam; linha reporta `5/5 PASS`; veredito PASS.
- `--repeat 5 --min-pass 4` com 5/5 PASS → mesma saída, veredito PASS.
- `--repeat 1` (explícito) em cenário que passa → saída idêntica ao formato atual sem rate.

Detecção de flakiness:
- Cenário que passa 2/5 runs com `--repeat 5` (min-pass default = 5) → reporta `2/5 PASS — FLAKY`; veredito FAIL; cenário aparece na lista `failed`.
- `--repeat 5 --min-pass 3` com cenário que passa 3/5 → veredito PASS (atinge min-pass); linha não tem tag FLAKY.
- `--repeat 5 --min-pass 3` com cenário que passa 2/5 → veredito FAIL + tag FLAKY.
- Cenário que falha todas as 5 vezes → reporta `0/5 PASS — FAIL`; veredito FAIL (não FLAKY).

Validação de flags:
- `--repeat 0` → erro no stderr + exit 2.
- `--min-pass 0` → erro no stderr + exit 2.
- `--min-pass 6` com `--repeat 5` → erro "min-pass maior que repeat" + exit 2.
- `--min-pass 3` sem `--repeat` → erro no stderr ("--min-pass requer --repeat") + exit 2.

Compatibilidade retroativa:
- Sem nenhum flag novo → saída byte-a-byte igual à atual (PASS/FAIL, sumário idêntico).
- `--nn` combinado com `--repeat 3` → cada um dos 3 runs usa a mesma porta `123NN` sequencialmente; todos completam sem conflito.

Skip:
- Cenário com campo `skip` ainda é pulado mesmo com `--repeat 5`; `skip_count++` ocorre uma vez, não N.

Sumário:
- Com `--repeat 5`, sumário final inclui `(repeat=5, min-pass=5)` e conta cenários (não runs).

**Verification:** rodar `regression.sh --repeat 3` sobre um subconjunto de cenários estáveis e confirmar que todos reportam `3/3 PASS`; rodar `regression.sh --repeat 1` e confirmar que a saída é idêntica à atual.

---

### U2. Documentar `--repeat` e `--min-pass` no SKILL.md

**Goal:** atualizar a referência do skill para que futuros usuários (e agentes) saibam que a suíte suporta gate de flakiness.

**Requirements:** R1, R2 (surface na doc).

**Dependencies:** U1.

**Files:**
- `.claude/skills/run-hive/SKILL.md` — modificado (seção "Regressão de cenários")

**Approach:** expandir o parágrafo existente sobre `regression.sh` para mencionar `--repeat N` e `--min-pass K`, o formato de relatório com taxa, e o padrão de uso típico (ex.: `--repeat 5 --min-pass 4` para flakiness tolerante a 1 falha).

**Test expectation:** none — mudança de documentação, sem lógica nova.

**Verification:** ler a seção modificada e confirmar que um novo usuário consegue usar o gate sem ler o código.
