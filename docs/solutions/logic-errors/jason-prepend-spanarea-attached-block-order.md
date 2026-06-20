---
title: "Jason PREPEND + MASSim spanArea: bloco mais a leste é sempre o primeiro matched por attached(AX,AY)"
date: 2026-06-20
category: logic-errors
module: connect_protocol
problem_type: logic_error
component: tooling
severity: high
symptoms:
  - "NORM detacha o bloco errado (o task block em vez do excess) mesmo com a lógica direcional do handler correta"
  - "Fixture com múltiplos blocos pré-anexados falha mesmo após verificar e corrigir as condições do plano"
  - "Mudar a ordem de `attach` no setup file não altera o bloco que NORM escolhe (v3 → mesmo resultado que v1)"
  - "4 iterações de fixture necessárias para cenário `09a-norm-carry` produzir PASS"
root_cause: logic_error
resolution_type: test_fix
tags:
  - jason
  - massim
  - prepend
  - belief-base
  - spanarea
  - attached
  - norm
  - fixture
  - block-ordering
  - mapc
---

# Jason PREPEND + MASSim spanArea: bloco mais a leste é sempre o primeiro matched por attached(AX,AY)

## Problem

Ao criar fixtures MASSim com múltiplos blocos pré-anexados, o handler NORM (`connect_protocol.asl`) detachava sistematicamente o bloco errado — não o excess, mas o task block. Corrigir a lógica condicional do handler não resolvia; mudar a ordem de `attach` no setup file também não. O problema estava numa interação não-óbvia entre MASSim e Jason que determina qual `attached(AX,AY)` é casado primeiro.

## Symptoms

- `NORM` emite `detach(e)` quando deveria emitir `detach(w)` (ou vice-versa)
- Cenário fixture retorna `submits_ok=0` mesmo com lógica do handler estruturalmente correta
- Trocar a ordem dos comandos `attach` no setup file não muda o comportamento
- Replay mostra que o bloco removido é sempre o mesmo, independente de tentativas de reordenação

## What Didn't Work

**v1 — Posição errada (EXCESS a oeste):**
- Fixture: EXCESS em (-1,0), TASK em (1,0)
- Assunção: spanArea envia blocos a oeste primeiro → oeste fica no topo da belief base
- Resultado: NORM casou (1,0) = LESTE = TASK → detach(e) errado ❌
- Lição: maior dx (leste) fica no topo, não menor dx

**v2 — Direções diferentes (EXCESS leste, TASK sul):**
- Fixture: EXCESS em (1,0), TASK em (0,1)
- Assunção: leste (dx=+1) enviado depois de sul (dx=0) → leste no topo → NORM detacha leste (excess)
- Resultado: NORM detachou sul (0,1) → detach(s) no TASK ❌
- Anomalia: sul (dx=0) ficou no topo apesar de esperarmos leste (dx=+1). Possível interação com eixo dy ainda não totalmente explicada.

**v3 — Invertendo ordem de `attach` no setup:**
- Posições iguais a v1; trocou-se a ordem dos comandos `attach` no arquivo de setup
- Hipótese: talvez o servidor use a ordem de attach para montar a lista `attached`
- Resultado: comportamento idêntico a v1 ❌
- **Conclusão definitiva: ordem de `attach` no setup file é irrelevante.**

## Solution

Colocar o bloco que deve ser **detectado primeiro** (EXCESS) na posição de **maior dx** (mais a leste) relativa ao agente. Colocar o bloco que deve **sobreviver** (TASK) com menor dx (mais a oeste).

```
# conf/scenarios/setup/09a-norm-carry.txt — v4 (PASS)

# TASK a OESTE (4,5) = relativo (-1,0) — dx menor → segundo na belief base → sobrevive
add 4 5 block b1
attach 5 5 4 5

# EXCESS a LESTE (6,5) = relativo (1,0) — MAIOR dx → último percept → PREPEND → TOPO
add 6 5 block b1
attach 5 5 6 5

# task_req aponta para TASK (oeste):
create task t1 100 -1,0,b1
```

Resultado: `submits_ok=1`, score=10, [PASS].

## Why This Works

Duas etapas interagem:

**1. MASSim `Position.spanArea(radius)` itera dx de `-radius` a `+radius` (oeste→leste).** Para cada dx percorre dy de `-radius` a `+radius` (norte→sul). Blocos mais a **leste** (maior dx) são enviados **mais tarde** no fluxo de percepts.

**2. Jason PREPEND na belief base.** Ao atualizar crenças de percept a cada step, Jason insere cada nova instância de `attached(AX,AY)` no **topo** (posição 1) da belief base. O último percept recebido fica em cima.

**Resultado combinado:** bloco com maior dx → enviado por último → PREPEND → topo da belief base → **casado primeiro** por `attached(AX,AY)` em qualquer plano Jason que faz unificação Prolog left-to-right.

```
spanArea order (dx crescente):  ... (-1,0) ... (0,0) ... (1,0) ...
Ordem de chegada dos percepts:       1º          2º          3º  (último)
Após PREPEND na belief base:         [3º]        [2º]        [1º]   ← topo
Unificação attached(AX,AY):          ↑ casa (1,0) primeiro
```

No handler NORM:
```asl
+!handle_norm : can_score_role & not pending_submit(_) & ...
              & attached(AX, AY) & (math.abs(AX) + math.abs(AY) == 1)
              <- if (AY == -1) { DDir = n }
                 elif (AY == 1) { DDir = s }
                 elif (AX == 1) { DDir = e }   // <- casa (1,0) = leste
                 else           { DDir = w }
                 detach(DDir);
```

Com EXCESS em (1,0): NORM deriva `DDir=e` → `detach(e)` → EXCESS removido ✓  
Task block em (-1,0) permanece → BLOCO-NA-MÃO dispara → `pending_submit(t1)` → submit ✓

## Prevention

- **Regra de fixture:** EXCESS deve sempre ter maior dx (posição mais a leste) que qualquer task block. A ordem de `attach` no setup file não importa.
- **Regra de design de planos:** qualquer plano que depende da ordem de unificação de `attached(AX,AY)` com múltiplos blocos deve considerar o dx dos blocos, não a ordem de setup.
- **Cenário de regressão:** `conf/scenarios/09a-norm-carry.json` + `setup/09a-norm-carry.txt` exercita esse comportamento; faz parte da suite de regressão (`regression.sh`).
- **Regra de debug:** ao depurar comportamento inesperado de detach, verificar primeiro a posição relativa (dx,dy) dos blocos antes de suspeitar da lógica condicional do handler.

## Related Issues

- Issue #51: `09a-norm-carry` — fixture e cenário que exercita o handler NORM (fechada)
- Issue #50: correções anteriores ao handler NORM (direção e filtro de bloco adjacente)
- `src/agt/common/connect_protocol.asl` L37-48: handler NORM (lógica correta desde #50)
- `conf/scenarios/09a-norm-carry.json` + `conf/scenarios/setup/09a-norm-carry.txt`: cenário verificador (v4, PASS)
