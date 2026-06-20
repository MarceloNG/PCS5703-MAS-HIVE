# Mapa de cobertura — cenários e testes que faltam

Created: 2026-06-20
Track: cross-cutting (pontuar + mover-mapear) · Motivação: evitar surpresas por comportamentos não cobertos

## Problema enquadrado

O time está sendo surpreendido por comportamentos inesperados em `connect_protocol.asl` (708 linhas,
20+ handlers `+step(N)`). Os bugs #47–#52 foram todos descobertos em runs de sim — não por testes
Java nem por cenários determinísticos. O padrão: um guard é adicionado reativamente após cada bug,
mas sem cenário para verificar que o guard funciona e que não regride quando o arquivo é editado.

**Objetivo:** catalogar o que está coberto vs o que falta, por comportamento, e propor os cenários
e testes Java prioritários. Não muda código; protege o que já funciona.

## Cobertura atual

### Java tests (lógica pura, sem sim)

| Classe | O que cobre | Lacuna |
|--------|-------------|--------|
| `RotationsNeededTest` | cálculo de rotações CW/CCW, direção ótima | — |
| `RotationGuardTest` | guard de loop (limiar 3 falhas) | — |
| `AllReqsSatisfiedTest` | verificação de alinhamento bloco↔task | — |
| `DetachGuardTest` | guard de detach consecutivo (limiar 2) | — |
| `SharedMapAStarTest` | A* toroidal, overlay, U-shape, cul-de-sac | — |
| `SharedMapHeadingTest` | heading balanceado, frontiers | — |
| `SharedMapRelativeTest` | dead-reckoning, translateCells | — |
| `TaskAllocatorTest` | leilão por valor/distância | — |
| `TaskBoardTest` | putIfAbsent atômico, claim/release | — |
| `SquadCoordinatorTest` | squads, distância toroidal | — |
| `HiveOrgStructureTest` | validação da org MOISE+ | — |
| `GridConfigTest` | parse de dimensão do grid | — |
| `AdjacentDirectionTest` | wrapDelta, direction toroidal | — |
| `LocalFrameTest` | conversão de frame local | — |

**Lógica Java está bem coberta.** Gaps não são aqui — são nos handlers `.asl`.

### Cenários determinísticos (end-to-end, sim real)

| Cenário | Capacidade isolada | Assert | OK? |
|---------|--------------------|--------|-----|
| `00-smoke` | boot + role adoption | `role_adoption ≥ 1` | ✓ |
| `01-adopt` | 10+ agentes adotam worker | `role_adoption ≥ 10` | ✓ |
| `02-navigate-open` | A* em campo aberto | `role_adoption ≥ 1` | ✓ ¹ |
| `03-obstacle-avoid` | A* com obstáculo | `failed_path_total ≤ 1` | ✓ |
| `03b-obstacle-uhole` | escape de beco U com buraco | `exited_region ≤ 30` + `max_stuck ≤ 5` | ✓ |
| `03c-obstacle-uavoid` | evitar U visível | `exited_region ≤ 28` + `max_stuck ≤ 5` | ✓ |
| `06-single-block` | coleta + submit 1 bloco, sem rotação | `submits_ok ≥ 1` | ✓ |
| `06c-single-collect` | coleta dirigida + submit (worker) | `submits_ok ≥ 1` | ✓ |
| `06c-collect-rotate` | coleta + pré-alinhamento no dispenser + submit | `submits_ok ≥ 1` | ✓ |
| `07a-prime-rotate` | rotação pré-submit para task com 1 bloco | `submits_ok ≥ 1` | ✓ |
| `07a-prime-wrong` | blocos errados → sem submit | `submits_ok = 0` | ✓ |
| `07a-wrong-blocks` | blocos errados (multi) → sem submit | `submits_ok = 0` | ✓ |
| `07a-multi-req` | task de 2 blocos → submit | `submits_ok ≥ 1` | ✓ |
| `09a-norm-carry` | norma Carry anunciada → submit não bloqueia | `submits_ok ≥ 1` | ✓ ² |

¹ `02-navigate-open` asserta role_adoption, não navegação em si — é proxy fraco.  
² `09a-norm-carry` verifica que a norma não quebra o submit, mas **não** verifica que a norma dispara (agente tem 1 bloco, limit=2 → NORM nunca executa).

## Gaps por categoria de comportamento

### Alta prioridade — código foi corrigido, mas sem regressão

Estas são as lacunas mais perigosas: o código foi mudado para corrigir um bug, mas se a correção
for revertida ou movida por acidente, não há cenário para pegar.

#### G1 — Direção do NORM detach (fix #50, commit `71c8b1e`)

**O que a correção fez:** o handler de NORM derivava `w` hardcoded; agora deriva da posição do
bloco (`AX, AY`). Se o bloco estiver em `(1,0)` (leste), a direção deve ser `e`.

**Gap:** nenhum cenário exercita o caminho onde um agente tem bloco a LESTE e NORM dispara.
Se o handler for editado acidentalmente, o erro volta — `failed_target` em loop silencioso.

**Proposta:** `10a-norm-dir-e.json` + setup  
- 1 agente com `carry_limit = 1`, 2 blocos disponíveis (agent pega 2 → excede limite)
- Bloco a LESTE: fixture posiciona agente com bloco attached em `(1,0)`
- Assert: `submits_ok ≥ 1` (se NORM disparar para a direção errada → detach falha → agent
  nunca submete). Proxy indireto mas suficiente.

#### G2 — R3: submit falha com blocos já alinhados (fix #52, commit `9c6c631`)

**O que a correção fez:** substituiu o loop cego `rotate(cw)×4` por decisão objetiva: se
`AllReqsSatisfied` em `submitted_task` → finalizar (não rotacionar). Sem R3, o loop volta.

**Gap:** nenhum cenário exercita o caminho onde submit falha COM blocos já no offset correto.
O `07a-prime-rotate` só testa o happy path (submit sucede).

**Proposta:** `10b-submit-fail-aligned.json` + setup  
- Agente com bloco já alinhado + goal zone movendo (moveProbability > 0 OU step muito curto
  para alcançar antes que a zone mova) → submit falha na 1ª tentativa.
- Assert: `submits_ok ≥ 1` em run suficientemente longo (R3 deve finalizar e re-tentar,
  não rotacionar em loop). Alternativamente: assert `max_stuck ≤ 2`.

*Nota: simular falha de submit é mais difícil; a abordagem mais direta é usar `regulation`
com um Carry norm mínimo + fixture + replay manual. Se a complexidade for alta, este gap
pode ser coberto por um `AllReqsSatisfiedTest` adicional que verifica o ramo R3 da lógica.*

#### G3 — Pré-alinhamento: caminho de abort após 3 falhas (fix #50/#52, commit `79e2547`)

**O que a correção fez:** adicionou pré-rotação no dispenser com guard `prealign_fails ≥ 3`
que aborta a task. O happy path está em `06c-collect-rotate` (`submits_ok ≥ 1`).

**Gap:** o caminho de abort (`prealign_fails ≥ 3 → finalize_task`) nunca é exercitado.
Se o guard for removido, o agente fica em loop de rotação indefinidamente (regressão do
problema original).

**Proposta:** `10c-prealign-abort.json` + setup  
- Fixture: bloco em posição que nenhuma rotação alinha (tipo incompatível com a task).
  Usa `07a-prime-wrong.json` como modelo — bloco wrong → `RotationsNeeded` retorna false
  → handler `not AllReqsSatisfied & not RotationsNeeded` → abortar imediato.
- Assert: `submits_ok = 0` + `max_stuck ≤ 2` (agente aborta, não fica em loop).

*Alternativa mais simples: adaptar `07a-prime-wrong.json` para o contexto do pré-alinhamento
no dispenser — o cenário já testa blocos incompatíveis, e o assert `submits_ok = 0` serve.*

### Média prioridade — comportamentos implementados sem trava

#### G4 — NORM + pending_submit (#51, guard exists but untested)

**Situação:** o handler de NORM tem a guard `not pending_submit(_)`. Se essa linha for
movida ou removida (por edição no arquivo), NORM pode disparar detach enquanto o agente
está prestes a submeter, bloqueando a submissão.

**Gap:** nenhum cenário exercita o caminho onde `carry_limit` é violado E `pending_submit`
está ativo ao mesmo tempo. O `09a-norm-carry` só verifica que a norma não quebra o submit
quando o agente está dentro do limite.

**Proposta:** `10d-norm-pending-submit.json` + setup  
- Agente com bloco coletado, `carry_limit = 1`, task válida (pending_submit ativo).
- Regulation: norma Carry com `quantity ≤ 1` (agent já está no limite).  
- Assert: `submits_ok ≥ 1` (NORM não deve disparar detach enquanto pending_submit; se
  disparar, bloco é perdido → sem submit).

#### G5 — NORM guard limit: norm_detach_blocked após 2 falhas

**Situação:** após 2 `failed_target` consecutivos de detach fora de `solo_mode`, o handler
em `perception.asl` seta `norm_detach_blocked`, impedindo novas tentativas. Sem este guard,
o agente tenta detach em loop infinito.

**Gap:** nenhum cenário exercita 2 falhas consecutivas de NORM-detach. A lógica está em
`perception.asl:320` mas não é exercitada por nenhum cenário.

**Proposta:** pode ser coberto por um Java test em `perception.asl` — mas `.asl` não tem
teste unitário. Alternativa: adicionar um `AllReqsSatisfiedTest` que verifica o limiar do
`DetachGuard` (que é a base lógica da guard). A lógica Java (`DetachGuard.java`) já está
coberta; o risco é na cola ASL.

*Risco: cenário de NORM-detach-fail requer fixture muito específica (bloco em célula que
nega o detach). Pode ser deixado para depois se os guards G1–G4 estiverem verdes.*

#### G6 — Detach em STUCK recovery: ASL finalize flow (#48)

**Situação:** `DetachGuardTest` testa o Java guard (limiar 2), mas o fluxo ASL completo
(stuck → 2 detach failures → `!finalize_task`) não tem cenário.

**Proposta:** `10e-stuck-detach-abort.json` + setup  
- Agente com bloco attached em beco sem saída (U-shape obstruindo goal zone); agente entra
  em STUCK → tenta detach → falha 2× → abort.
- Assert: `submits_ok = 0` + `max_stuck ≤ 5` (agent sai do STUCK sem loop infinito).

### Baixa prioridade / fora do scope imediato

- **CLEAR blocks handler** (`needs_clear_blocks`): sem bugs conhecidos; difícil de isolar.
- **Energia baixa** (`E < 5`): não está causando bugs; skip conservativo.
- **Corrida de 2 agentes por 1 task**: coberta pelo Java (`TaskBoardTest`); risk no sim é baixo
  porque `putIfAbsent` é atômico.
- **Goal zone movendo** (`moveProbability > 0`): agrega variância; não isola bem.

## Métricas existentes que servem

Os gaps acima usam métricas que já existem no `assert_metric.py`:

| Métrica | Serve para |
|---------|------------|
| `submits_ok ≥ N` | provar que o agente submeteu a despeito de interferência |
| `submits_ok = 0` | provar que bloco incompatível ou abort correto (não loop) |
| `max_stuck ≤ N` | provar que não há loop de ações repetidas |
| `failed_path_total ≤ N` | provar que navegação não entrou em livelock |

Não são necessárias novas métricas para os gaps G1–G5.

## Critérios de conclusão

- [ ] **G1**: `10a-norm-dir-e` PASS (NORM dispara, direção correta, agent submete)
- [ ] **G2**: `10b-submit-fail-aligned` PASS ou Java test para ramo R3
- [ ] **G3**: `10c-prealign-abort` PASS (blocos incompatíveis → abort, sem loop)
- [ ] **G4**: `10d-norm-pending-submit` PASS (NORM não bloqueia pending_submit)
- [ ] **G5 e G6**: decidir se cenário ou Java test por risco/custo após G1–G4

Todos os cenários existentes (`00` → `09a`) continuam PASS após adicionar os novos.

## Outstanding Questions

- OQ-1: O `09a-norm-carry` usa o setup de `06c-single-collect` (setup via campo `setup`).
  Faz sentido criar setups específicos para G1 e G4, ou adaptar o fixture existente?
- OQ-2: G2 (submit-fail-aligned) é difícil de exercitar deterministicamente sem hackear
  o servidor. Alternativa: verificar R3 por Java test direto em `AllReqsSatisfied` ou
  `RotationsNeeded`. Aceitar essa troca?
- OQ-3: Existe algum comportamento em `navigation.asl` (heading, frontier, A* fallback)
  que deveria entrar neste mapa? O `02-navigate-open` é proxy fraco para navegação.
