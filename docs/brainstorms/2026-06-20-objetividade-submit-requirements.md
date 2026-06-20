# Objetividade do submit — pré-alinhar onde a rotação funciona

Created: 2026-06-20
Track: Fase C / Submits · Issues: #50, #52 (#51 = dup de #50) · Prior art: #14, #18 (Eixo 7a/7a'), #26, #40

## Problema enquadrado

O agente **não é objetivo** ao submeter. O pipeline de pontuação é
`adotar → reconhecer task → coletar → navegar à goal zone → submit`. Hoje o alinhamento
do bloco (pôr o bloco no offset que a task exige, `treq`) acontece de forma **reativa e no
lugar errado**: o agente coleta, navega, chega na goal zone, **descobre** o desalinhamento e
tenta corrigir **dentro da zona** rotacionando/reposicionando em loop.

Dois problemas se somam:

1. **A goal zone é congestionada** (vários agentes disputando as mesmas células). Rotacionar
   ou reposicionar ali **não funciona de forma confiável** — `rotate`/`move` falham ou são
   bloqueados pelos vizinhos. *Rotação é uma operação de alinhamento legítima, mas no lugar
   errado.*
2. **Há dois mecanismos de rotação brigando.** O caminho "inteligente" (`RotationsNeeded` +
   `AllReqsSatisfied`, Eixo 7a', `connect_protocol.asl` l76-122) computa o mínimo de rotações
   e verifica o alinhamento. Mas o caminho de **falha de submit** (`connect_protocol.asl`
   l363-407) tem um loop **cego**: `rotate(cw)` × 4 — que é **identidade** (volta ao mesmo
   lugar) — seguido de reposição × 3, **sem verificar** se o bloco já casa `treq`.

Os bugs #50/#51/#52 são **sintomas** desse design reativo, não causas isoladas:

- **#52** — `agentA15`: 36 submits da task2, 34 falharam. Evidência:
  `submit(task2) attached=[att(0,1)] reqs=[treq(0,1,b1)]` — bloco sul **já casa** o requisito
  sul, e mesmo assim rotaciona cw × 4 (identidade). A causa real da falha (zona errada / task
  expirada / já submetida por outro) **não é rotação**.
- **#50** — `agentA12`: detach 62 / failed_target 61. O handler de norma
  (`connect_protocol.asl` l37-47) usa um `else → w` catch-all; com bloco **não-cardinal**
  (encadeado, ex. `(2,0)`) ele detacha oeste numa célula vazia, em loop ilimitado (sem guard).
- **#51** — duplicata do #50. A alegação "a norma bloqueia o submit" **não se sustenta**: o
  handler já tem `not pending_submit(_) & not submitted_task(_)`. → fechar como dup.

> **Convenção de direções (VERIFICADA — fonte `perception.asl:53-56` `dead_reckon_move`):**
> `n=(0,-1)`, `s=(0,+1)`, `e=(+1,0)`, `w=(-1,0)`, Y-down. O mapeamento offset→direção no
> código **está correto**; o bug do #50 é o catch-all engolindo offsets não-cardinais, **não**
> direção trocada.

## Insight central

**A rotação não está errada — está no lugar errado.** O **dispenser** (onde o agente faz
`attach`) **não** é congestionado; a **goal zone** é. Logo: **relocalizar o alinhamento para o
dispenser** (rotacionar logo após o `attach`, antes de navegar) faz o agente **chegar na zona
já alinhado** e submeter na entrada. A rotação-na-zona deixa de ser o plano e vira **fallback
raro de último recurso** — exatamente porque a congestão a quebra.

## Eixos avaliados

1. **Pré-alinhar na coleta (no dispenser).** Após `attach`, computar `RotationsNeeded` contra o
   `treq` da task escolhida e rotacionar **ali** (descongestionado) até `AllReqsSatisfied`.
   Navegar já alinhado. → **núcleo do rework.** Infra existe (`RotationsNeeded`,
   `AllReqsSatisfied`); falta torná-la o caminho **primário** no dispenser, não um fallback na
   zona.
2. **Submit objetivo na zona.** Na goal zone: se `AllReqsSatisfied` → submeter direto. Se o
   submit **falhar com** `AllReqsSatisfied` true → a causa **não** é rotação → **finalizar**
   (zona/expiração/tomada), sem girar. **Deletar** o loop cego rotate×4 + reposição×3.
3. **NORM/detach correto e bounded.** Handler de norma só sobre bloco **cardinal**
   (`|AX|+|AY|==1`, padrão já usado em l73) → `else→w` só vê `(-1,0)`. + guard de falhas
   consecutivas (análogo ao `DetachGuard` #48) para nunca loopar.
4. **Teste de objetividade.** JUnit sobre `AllReqsSatisfied`/`RotationsNeeded` (lógica pura, já
   testável) + cenário run-hive (`06c-single-collect`, `IsolationRoles`) provando "submete na
   entrada, sem spin na zona".

## Opções (simples ↔ eficiente)

| | A — Submit objetivo (submit-side) | B — Pré-alinhar na coleta (collection-side) | **C — Híbrido (recomendado)** |
|---|---|---|---|
| **O que** | Coleta como está; decisão de submit na zona via `RotationsNeeded`/`AllReqsSatisfied`; deleta o loop cego | Rotaciona no dispenser → chega alinhado → submit na entrada | Pré-alinha no dispenser (**primário**) **+** gate objetivo na zona como fallback raro |
| **Custo** | Baixo | Médio (mexe em `collection.asl` + ordem da navegação) | Médio-alto (as duas, coerentes) |
| **Risco** | Baixo, isolável | Offset depende do lado de acesso ao dispenser | Maior superfície; duas fontes de alinhamento |
| **"Submete na entrada"?** | Não — ainda pode girar na zona (congestionada → falha) | Sim | Sim, com rede de segurança |
| **Contra a congestão** | ❌ ainda rotaciona na zona | ✅ rotaciona no dispenser | ✅ rotaciona no dispenser; zona só fallback |

## Recomendação

**C — híbrido, com o pré-alinhamento no dispenser como caminho PRIMÁRIO** e a rotação-na-zona
**rebaixada a fallback raro de último recurso.** Justificativa: o ponto do dono — *rotar no
congestionamento não funciona* — elimina A (que ainda depende de rotação na zona). B sozinho
deixa o agente sem rede se chegar desalinhado (task trocada em voo, bloco deslocado). C é "a
mais garantida": alinha onde a rotação funciona (dispenser) **e** mantém o submit objetivo na
zona, agora deletando o loop cego que era o #52.

Sequenciamento (STRATEGY.md — menor incremento isolável primeiro, promover por evidência):

- **Fatia 1 (correção + objetividade na zona):** Eixo 2 + Eixo 3 — deletar o loop cego,
  submit dirigido por `AllReqsSatisfied` (finaliza em vez de girar), NORM cardinal + bounded.
  É a parte que **mata #50/#52** reusando infra. Validável em isolamento.
- **Fatia 2 (pré-alinhar no dispenser):** Eixo 1 — mover a rotação de alinhamento para logo
  após o `attach`. É a parte "submete na entrada". Validável por evidência do 06c (rotação na
  zona deve cair a ~0).

## Requisitos

- **R1 — Pré-alinhar no dispenser.** Após `attach` de um bloco para uma task escolhida, o
  agente computa `RotationsNeeded(Task)` contra o `treq` e executa as rotações **no dispenser**
  (área não-congestionada) antes de navegar à goal zone. Chega com `AllReqsSatisfied` true.
- **R2 — Submit objetivo na entrada.** Na goal zone com `AllReqsSatisfied` true → `submit`
  imediato. Sem rotação especulativa na zona no caminho feliz.
- **R3 — Falha de submit é diagnosticada, não "rotacionada".** Se `submit` falha **com**
  `AllReqsSatisfied` true → a causa não é desalinhamento → `finalize_task` (zona errada / task
  expirada / já submetida). **Remover** o loop `rotate(cw)×4 + reposição×3`.
- **R4 — Rotação-na-zona só como fallback raro.** Só permitida quando `AllReqsSatisfied` é
  **false** na zona (desalinhamento genuíno por evento em voo) e **bounded** (não pode loopar);
  do contrário, finaliza.
- **R5 — NORM/detach correto e bounded.** Handler de norma só sobre bloco cardinal
  (`|AX|+|AY|==1`) → direção sempre válida. Guard de falhas consecutivas (análogo `DetachGuard`)
  aborta o detach de norma antes de virar loop.
- **R6 — Teste de objetividade.** (a) JUnit: `RotationsNeeded`/`AllReqsSatisfied` cobrindo
  "bloco já alinhado ⇒ 0 rotações / não rotacionar" (caso `att(0,1)==treq(0,1)` já adicionado em
  `AllReqsSatisfiedTest`). (b) Cenário: `06c-single-collect` e `IsolationRoles` — métrica de
  rotações **na zona** ≈ 0 e `failed_target` de detach < 5/agente.
- **R7 — Sem regressão** nos cenários `01-adopt`, `06-single-block`, `06c-single-collect`,
  `07a-multi-req`, `07a-wrong-blocks`.

## Como validar

- **JUnit (rápido, sem sim):** `RotationsNeeded`/`AllReqsSatisfied` — lógica pura. É o "teste de
  objetividade" pedido pelo dono.
- **Cenário isolado:** `06c-single-collect` (coleta→submit de 1 bloco) com um **analyzer de
  rotações-na-zona** (novo `analyzers/submit_strategy.py` ou métrica plugável): provar que o
  agente submete na entrada e **não** rotaciona na goal zone. + `IsolationRoles` 300 steps para
  o `failed_target` de detach < 5/agente e nº de submits bem-sucedidos vs #48.
- **Métrica de regressão:** suíte `regression.sh` antes de mesclar (mexe em core:
  `connect_protocol`/`collection`/`navigation`).

## Fronteiras de escopo

**Dentro:** alinhamento no dispenser (R1), submit objetivo + remoção do loop cego (R2/R3/R4),
NORM cardinal + bounded (R5), testes (R6/R7).

**Fora (parking lot, gated por evidência):**
- Escolher o **lado de acesso ao dispenser** para minimizar rotações (otimização sobre R1 — só
  se a medição mostrar que as rotações no dispenser são caras).
- Coordenação de **reserva de célula na goal zone** entre agentes (contenção é o track #40/#42;
  este rework assume o allocator/`claim_task` existente).
- Re-alinhamento dinâmico se a task escolhida mudar em voo (R4 cobre o fallback; otimizar a
  troca é follow-up).

## Questões em aberto

- **Q1.** O agente conhece o `treq` (orientação exigida) **no momento do `attach`**? Depende do
  allocator (#40) ter fixado a task antes da coleta. Se a task só é conhecida depois, R1 precisa
  de um ponto de pré-alinhamento "assim que a task for conhecida e antes de entrar na zona"
  (ainda fora da congestão). → resolver no plano.
- **Q2.** Para blocos **únicos** (single-block), "alinhar" = pôr o bloco no offset `treq`
  específico (ex.: sul). Confirmar que `RotationsNeeded` trata o caso 1-bloco (girar para o
  offset certo), não só cadeias multi-bloco.
- **Q3.** O fallback de rotação-na-zona (R4) deve ter qual bound? (1 tentativa? 0 — finalizar
  direto?) → calibrar por evidência.

## Prior art (citar & melhorar)

- **Eixo 7a / 7a' (#14, #18):** `AllReqsSatisfied` (submit multi-req pré-alinhado) e
  `RotationsNeeded` (rotação CW para alinhar). Este rework **reusa** ambos e os move para o
  dispenser como caminho primário, deletando o loop cego paralelo.
- **#26 (06c-single-collect):** o cenário de coleta→submit end-to-end — gate de validação.
- **#48 (`DetachGuard`):** padrão de guard de falhas consecutivas — espelhar para o NORM-detach.
- **LI(A)RA / times MAPC 2022:** referência de coleta/submit homogêneo descentralizado — citar
  no relatório, não copiar.
