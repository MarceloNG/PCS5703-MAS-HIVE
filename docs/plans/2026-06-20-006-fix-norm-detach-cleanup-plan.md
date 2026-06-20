---
date: 2026-06-20
type: fix
title: "fix(norm): fixture 09a-norm-carry determinístico + cleanup explorer"
status: active
closes: "#51"
branch: "fix/51-norm-detach"
---

# fix(norm): fixture 09a-norm-carry determinístico + cleanup explorer

## Summary

A lógica do handler NORM (carry_limit excedida) já foi corrigida em `81ca995` (#50/#52):
filtro de distância (`math.abs(AX)+math.abs(AY)==1`) previne `detach` com direção errada;
`norm_detach_blocked` limita retries a 2. O que resta é:

1. **Fixture de DoD ausente** — `09a-norm-carry.json` aponta para o setup de outra feature
   (`06c-single-collect.txt`). Sem fixture correta, o DoD da issue não pode ser verificado
   de forma determinística.
2. **Comentários desatualizados** — `role_adoption.asl` e 3 cenários ainda mencionam
   `explorer` no caminho de adoção, que foi colapsado para `default→worker` direto em #54.

---

## Problem Frame

### Bug original (#51)

`agentA12` acumulou 62 `failed_target` de `detach` porque:
- Bloco não-adjacente em `(2,0)` satisfazia `attached(AX,AY)` sem filtro de distância
- `elif (AX==1)` falha para `AX=2` → `else { DDir=w }` → `detach(w)` com bloco a leste → `failed_target`
- Sem `norm_detach_blocked` → mesmo plano disparava no próximo step → 62 repetições

### Status da correção

Ambos os bugs estão corrigidos em `marcelo` HEAD (`dc7db64`):
- `& attached(AX,AY) & (math.abs(AX)+math.abs(AY)==1)` — filtra distância ≠ 1
- `& not norm_detach_blocked` + NORM DETACH GUARD em `perception.asl` — limita a 2 retries

### O que este plano entrega

- Setup fixture determinístico que demonstra o fix em ação (excesso removido → submit bem-sucedido)
- Limpeza de comentários desatualizados sobre `explorer` no fluxo de adoção

---

## Scope Boundaries

**Em escopo:**
- Criar `conf/scenarios/setup/09a-norm-carry.txt` com fixture de norma+excesso
- Atualizar `conf/scenarios/09a-norm-carry.json` (path, regulation, steps, comment)
- Limpar comentários de `explorer` em `role_adoption.asl`
- Atualizar descrições de cenários em `00-smoke.json`, `01-adopt.json`, `06-single-block.txt`

**Fora de escopo:**
- Modificar lógica de detach/submit (já correta)
- Novos guards ou heurísticas de NORM
- Configurações diagonais no PRE-SUBMIT (issue separada se evidência aparecer)

### Deferred to Follow-Up Work

- Verificação end-to-end do DoD via `IsolationRolesConfig 300 steps` — requer sim completa
  (DoD item 3); feita separadamente após este fix fazer merge

---

## Key Technical Decisions

| ID | Decisão | Rationale |
|----|---------|-----------|
| KTD1 | `carry_limit=1` via `quantity=[1,1]` na norma | Leva a 1 bloco → violação com 2 blocos pré-anexados; expõe o handler NORM |
| KTD2 | Excess block a OESTE (`-1,0`) criado antes do task block (`1,0`) | Garante FIFO no belief base: x=-1 < x=1 em ordering x-sort do MASSim → NORM unifica com excesso primeiro; `else { DDir=w }` dá direção correta para bloco a oeste |
| KTD3 | Task block a LESTE `(1,0)`, `task_req(t1, 1, 0, b1)` | Mesma posição do 06-single-block; BLOCO-NA-MÃO (L254-276 do connect_protocol.asl) ativa imediatamente após NORM limpar o excesso |
| KTD4 | `steps=20` no JSON (reduzido de 68) | 4 steps são suficientes (adopt→NORM detach→pending_submit→submit); 20 dá margem sem sim longa |

---

## Implementation Units

### U1. Criar `conf/scenarios/setup/09a-norm-carry.txt`

**Goal:** Fixture determinístico que posiciona 1 agente na goal-zone + role-zone com EXCESS block
a oeste e task block a leste, carry_limit=1 → NORM detacha excesso → submit ocorre.

**Requirements:** DoD #51 — agente submete sem loop de `failed_target` de norma.

**Dependencies:** Nenhuma.

**Files:**
- `conf/scenarios/setup/09a-norm-carry.txt` (criar)

**Approach:**

Seguir a gramática dos outros setup files (`conf/scenarios/setup/06-single-block.txt`):
- `move X Y agentN` — posicionar agente
- `terrain X Y role|goal` — marcar célula
- `add X Y block TYPE` — adicionar bloco pré-posicionado  
- `attach X1 Y1 X2 Y2` — criar ligação (células adjacentes)
- `create task NAME DEADLINE bx,by,type` — criar task

Layout (grid 12×12, eixo x=leste(+), y=sul(+)):
- `agentA4` em `(5,5)` — agente-alvo (acessa worker role via adoption)
- role-zone e goal-zone em `(5,5)` — adota worker e está sobre goal-zone simultaneamente
- Excess block (b1) em `(4,5)` = relativo `(-1,0)` oeste → criar PRIMEIRO via `add 4 5 block b1` + `attach 5 5 4 5`
- Task block (b1) em `(6,5)` = relativo `(1,0)` leste → criar SEGUNDO via `add 6 5 block b1` + `attach 5 5 6 5`
- Task `t1`, deadline 100, requisito `1,0,b1` (bloco a leste)
- 14 agentes não-alvo estacionados em borda inferior (padrão do repo — libera (5,5))

**FIFO ordering note:** O excess em `(4,5)` deve ser criado antes do task block em `(6,5)` (comandos `add`+`attach` na ordem). MASSim envia percepts em x-sort ascending; `-1 < 1` garante que Jason receba `attached(-1,0)` antes de `attached(1,0)`. NORM unifica com `(-1,0)` → `else { DDir=w }` → `detach(w)` correto.

**Patterns to follow:** `conf/scenarios/setup/06-single-block.txt` — mesma geometria (agent centrado em (5,5), borda inferior para não-alvo, combinação role+goal na mesma célula).

**Test scenarios:**
- Happy path: com `carry_limit=1` e 2 blocos pré-anexados, NORM dispara em step ~2 e emite `detach(w)` (excesso); step ~3 BLOCO-NA-MÃO detecta task block em `(1,0)` → `pending_submit`; step ~4 SUBMIT dispara → `submits_ok >= 1`
- Guard do excesso: NORM emite exatamente 1 `detach(w)` (não 62) — verificável no replay via `"action":"detach"`
- Direção correta: nenhum `failed_target` com `lastAction(detach)` nos primeiros 10 steps

**Verification:** Rodar `run-hive.sh --conf conf/scenarios/09a-norm-carry.json --steps 15` → assert `submits_ok: 1` passa. No replay: 1 `detach(w)` com `result:success`, nenhum `detach:failed_target`.

---

### U2. Atualizar `conf/scenarios/09a-norm-carry.json`

**Goal:** Corrigir o campo `setup` para o novo fixture; ajustar `quantity`, `steps` e atualizar o comment `//` para descrever o que o cenário realmente testa.

**Requirements:** Setup field aponta para fixture correto; regulation reflete `carry_limit=1`.

**Dependencies:** U1 (fixture deve existir antes do path ser referenciado).

**Files:**
- `conf/scenarios/09a-norm-carry.json` (modificar)

**Approach:**

Mudanças pontuais no JSON:

1. `"setup"`: trocar `"../../conf/scenarios/setup/06c-single-collect.txt"` → `"../../conf/scenarios/setup/09a-norm-carry.txt"`

2. `"regulation.subjects[0].optional.quantity"`: trocar `[2, 2]` → `[1, 1]`  
   (carry_limit fica fixo em 1; agente com 2 blocos viola → NORM handler dispara)

3. `"steps"`: trocar `68` → `20` (4 steps necessários; 20 dá margem)

4. `"regulation.subjects[0].announcement"`: manter `[2, 2]` (norma ativa no step 3, antes de NORM handler tentar disparar — timing correto)

5. `"//"` comment: atualizar para descrever o novo comportamento:
   > Cenário 09a-norm-carry (#51 DoD). Isola o handler NORM com carry_limit=1: agente pré-posicionado na goal-zone com 2 blocos pré-anexados (excesso oeste + task block leste). Verifica que NORM corretamente emite detach(w) [não direção errada], task block sobrevive, e submit ocorre após limpeza. Fix #51: filtro de distância (math.abs==1) + norm_detach_blocked. chance:100 + announcement:[2,2] = norma determinística ativa no step 3.

**Patterns to follow:** Outros JSONs de cenário (e.g., `conf/scenarios/06c-single-collect.json`) — estrutura e comentários no campo `"//"`.

**Test scenarios:**
- JSON válido após edição (sem erro de parse)
- Campo `setup` aponta para caminho que existe no repo

**Verification:** `cat conf/scenarios/09a-norm-carry.json | python3 -m json.tool` retorna sem erro; `regulation.subjects[0].optional.quantity` = [1, 1].

---

### U3. Limpar comentários `explorer` em `role_adoption.asl`

**Goal:** Atualizar comentários que mencionam `[explorer, worker]` no caminho de adoção, refletindo a decisão do #54 (colapsado para `[worker]` direto).

**Requirements:** Comentários do código não mentem sobre o comportamento atual.

**Dependencies:** Nenhuma (mudança cosmética, sem impacto em runtime).

**Files:**
- `src/agt/common/role_adoption.asl` (modificar)

**Approach:**

Dois comentários a atualizar:

**Linha 25-26** (antes de `role_adoption_path([worker]).`):
```
// ÚNICO PONTO DE ADOÇÃO: para reintroduzir explorer/constructor,
// trocar para [explorer, worker] (e adicionar arm role(explorer) + defensivo abaixo).
```
→ Manter intenção mas remover "[explorer, worker]" como exemplo concreto de reintrodução.
Novo texto sugerido (direcional, não prescritivo):
```
// ÚNICO PONTO DE ADOÇÃO: alterar este fato para reintroduzir degraus intermediários.
// Decisão: explorer removido do path em #54 (sem evidência de ganho de speed=3).
```

**Linha 144** (arm `!ensure_worker_role : roleZone(0, 0) & role_adoption_path([Next|_]) & not role(Next)`):
```
// not role(Next): evita re-adoptar o mesmo role em loop se o path for [explorer, worker]
```
→ O guard ainda faz sentido (evita re-adopt do worker); só o exemplo está obsoleto.
Novo texto sugerido:
```
// not role(Next): evita re-adoptar o mesmo role em loop (idempotência do adopt)
```

**Patterns to follow:** Idioma de comentários do próprio `role_adoption.asl` — uma linha, PT-BR, sem ponto no final.

**Test scenarios:**
- Comportamento em runtime: inalterado (só comentários)
- Parse: `.asl` deve continuar parseando sem erro (`gradle run` no 00-smoke)

**Verification:** `grep -n "explorer" src/agt/common/role_adoption.asl` retorna somente as linhas do `can_score_role` (que menciona `explorer` no catálogo de roles — correto) e não mais nas linhas 25-26, 46, 144.

---

### U4. Atualizar descrições de cenários desatualizadas

**Goal:** Remover referências a `default→explorer→worker` em 3 arquivos onde o path agora é
`default→worker` direto (colapsado em #54).

**Requirements:** Documentação inline de cenários reflete a decisão arquitetural atual.

**Dependencies:** Nenhuma (mudança cosmética).

**Files:**
- `conf/scenarios/00-smoke.json` (modificar)
- `conf/scenarios/01-adopt.json` (modificar)  
- `conf/scenarios/setup/06-single-block.txt` (modificar)

**Approach:**

**`conf/scenarios/00-smoke.json`** — campo `"//"`, trecho:
> `role_adoption >= 1 (ao menos um agente adota worker => a cadeia default->explorer->worker fechou)`
→ trocar `a cadeia default->explorer->worker fechou` por `adoção default->worker direto (#54)`

**`conf/scenarios/01-adopt.json`** — campo `"//"`, trecho:
> `METRICA: quantos dos 15 adotam worker (path default->explorer->worker) e quantas RE-adocoes`
→ trocar `(path default->explorer->worker)` por `(path default->worker direto, #54)`

**`conf/scenarios/setup/06-single-block.txt`** — comentário na linha 41:
> `# - role-zone: adota worker (path default->explorer->worker, provado em #12)`
→ trocar `(path default->explorer->worker, provado em #12)` por `(path default->worker direto, #54 — #12 provou adoção)`

**Patterns to follow:** Estilo de comentários existentes nos mesmos arquivos.

**Test scenarios:**
- `grep -r "default->explorer" conf/` retorna 0 linhas após as edições
- JSON files continuam parseando sem erro

**Verification:** `grep -rn "explorer" conf/scenarios/` retorna somente as entradas dentro de `"roles": [...]` (definições do servidor) e zero nos campos de descrição/comentário.

---

## Risks & Dependencies

| Risco | Mitigação |
|-------|-----------|
| Ordering de percepts `attached(AX,AY)` diferente do esperado → NORM detacha task block | Verificar no replay `09a-norm-carry`: exactly 1 `detach(w):success`. Se NORM detachar task block (east) em vez de excess (west), revisar posicionamento (usar excess a norte (0,-1) que tem y=-1 < y=0, garantindo ordenação) |
| `carry_limit=1` + step 1 de adoção (role adoption toma o step) pode atrasar NORM por N steps | `announcement=[2,2]` → norma ativa no step 3; role adoption completa em 1-2 steps (role-zone direto na célula do agente) — janela OK |
| `steps=20` muito curto se o agente não adotar a tempo | Agente inicia NA role-zone → adopt no step 1 → slack de 16 steps após adoção |

---

## Sources & Research

- Issue #51 (MarceloNG/PCS5703-MAS-HIVE) — DoD e root cause
- `src/agt/common/connect_protocol.asl` L37-48 (NORM handler, fix de 81ca995)
- `src/agt/common/connect_protocol.asl` L254-276 (BLOCO-NA-MÃO → submit direto)
- `src/agt/common/perception.asl` L305-322 (NORM DETACH GUARD)
- `conf/scenarios/setup/06-single-block.txt` — padrão de fixture (gramática, posicionamento)
- `conf/scenarios/09a-norm-carry.json` — arquivo existente com setup errado
- `src/agt/common/role_adoption.asl` L25-27, L144 — comentários a limpar
- Issue #54 (MarceloNG/PCS5703-MAS-HIVE) — decisão de colapso default→worker
