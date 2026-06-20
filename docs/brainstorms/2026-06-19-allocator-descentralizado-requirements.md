---
name: allocator-descentralizado
status: active
date: 2026-06-19
issues: ["#40 (núcleo)", "#43 (multi-block)", "#44 (Contract-Net recrutamento)", "#45 (calibração)"]
related: ["#38 (flat reshape)", "#24 (board)", "#36 (espinha org)", "#42 (contenção)", "#41 (flakiness)", "#17 (U9 map-merge)", "#21 (connect)", "#22 (org aloca)"]
---

# Allocator descentralizado — alocação de tarefas pós-achatamento (#40)

Requirements do brainstorm (`ce-brainstorm`, 2026-06-19). Define **o quê** construir; o **como**
fica para `ce-plan`. Substitui a frente que a issue #40 abria ("objeto Java de leilão"), agora
**reescopada pela evidência da spec MASSim + livro MAPC 2022**.

## 1. Problema & contexto

O achatamento do time (#38) aposentou o **squad_leader**, que fazia a **alocação/deconfliction
de tarefa** (`find_free_soloist` → 1 agente por task). O fix mínimo do #38 (`TaskBoard.claim_task`
atômico) re-homou só o **claim solo single-block**: 1 agente reivindica, os outros saem. Resultado
medido: o cenário **06c-single-collect ficou flaky (~1/3 PASS)** — o time flat congestiona. O #40
deve tornar a alocação **confiável** e abrir caminho para **multi-bloco**.

## 2. Descoberta-chave (spec) — o modelo anterior estava errado

A spec autoritativa (`massim_2022/docs/scenario.md` §Tasks, l.200-204) diz:

> *"Each task can be submitted **multiple times** for as long as it is active. A task is replaced
> when (a) foi submetida um certo nº de vezes (`iterations` [5,10], **conta os dois times**), ou
> (b) o deadline chega."*

Ou seja: **tasks NÃO são exclusivas** — perduram (deadline = `maxDuration` [100,200] steps),
podem ser submetidas 5-10×, e há `tasks.concurrent` (~2) ativas por vez. Logo:

- O `find_free_soloist` (1 agente/task) e o `claim_task` (lock exclusivo) **modelam errado o
  domínio**. O `claim` "consertou" o 06c **por acidente**: o 06c é degenerado (1 task / 1 dispenser
  / 1 goal-zone num grid minúsculo) onde 15 corpos não cabem fisicamente — a falha é **congestão
  física de ESPAÇO**, não desperdício de fazer a mesma task.
- O recurso disputado **não é a task** — é o **espaço físico de montagem/entrega** (goal-zone).

## 3. Evidência do livro MAPC 2022 — os 3 times relevantes convergem

Todos **flat/homogêneos** (mesmo código p/ todos os agentes), como o HIVE pós-#38:

| Time | Coordenação | Seleção de task | Anti-congestão |
|---|---|---|---|
| **MMD** (1º, blackboard) | coordenador **por-task** (coordinator + block providers); sub-tasks *"similar to Contract Net"* | **função de valor** `reward×tempo / (agents × (1+crowdedness))`; bid = steps; menor tempo vence | termo **crowdedness** em goal-zones (admitido *"a very simple estimation"*) |
| **FIT BUT** (3º, `deSouches`) | artefato coordenador forma **coalizão** (1 agente/bloco + 1 que entrega) | coalizão gulosa por goal-zone | **1 goal-zone por coalizão**; escolhe a posição de arredores **mais livres** |
| **LI(A)RA** (Jason, 4º) | *"completamente descentralizado, sem mecanismo central"*; **um agente coordena a entrega** de cada task; recruta *"similar a Contract Net"* | plans por prioridade + memória | sincroniza ao se encontrar; *"best match no grupo que pode ajudar"* |

**Três sinais decisivos:**

1. **Ninguém usou líder global fixo** (a hierarquia que aposentamos) — todos usaram **coordenador
   POR-TASK emergente**: um agente homogêneo que por acaso coordena *uma* task, com os outros
   trazendo blocos. É o que o LI(A)RA (nosso espelho Jason) faz, "no central mechanism".
2. **A deconfliction certa é na GOAL-ZONE, não na task** (FIT BUT reserva 1 goal-zone/coalizão e
   escolhe o ponto mais livre; MMD penaliza crowdedness). O LI(A)RA descreve a dor exata —
   *agentes esperando espaço para montar/entregar* (cap. LI(A)RA, l.7309-7315).
3. **MMD (vencedor) avisa: com 2 tasks concurrent, seleção sofisticada NÃO foi crítica** (l.4233-4235;
   e *"not sure it really played an important role"*, l.3232-3234). O lever foi **fusão de mapa
   (U9 #17)** + o **pipeline de montagem**. → **não over-engenheirar o allocator.**

## 4. Reframe do problema

#40 **não** é "atribuição exclusiva / quem ganha a task". É **seleção por valor + balanceamento
de carga anti-congestão**: cada agente escolhe `argmax(reward/tempo)` e um termo de congestão
(reserva de goal-zone) impede empilhamento, espalhando entre as `concurrent` tasks / dispensers /
goal-zones. É um **jogo de congestão (potential game)**, não um leilão. No 06c isso resolve do
jeito certo: quando o espaço de entrega "enche", o agente marginal dispersa/espera — sem lock
artificial.

## 5. Alternativas avaliadas (eixos do dono) — prós/cons

| Alt | Mecanismo | Prós | Cons / veredito |
|---|---|---|---|
| **Seleção por valor + reserva de goal-zone** ⭐ | Allocator Java: `argmax(reward/tempo)` + reserva de footprint na goal-zone + bid de custo + dispersão | Alinhado à spec (não-exclusivo); **simples** (o vencedor confirma que basta); 1 função pura testável; subsume seleção+dispersão; é o consenso dos 3 times | **Recomendado.** Núcleo do #40. |
| Leilão/Contract-Net **sobre tasks** | `place_bid`/`resolve_auction` por task, vencedor único | reusa o leilão existente; familiar | **Mis-modela**: trava como exclusiva uma task que rende 5-10×; deixa reward na mesa. Preterido. |
| `claim_task` (lock, #38) | putIfAbsent exclusivo | já existe; destrava o 06c em isolamento | modelo errado (exclusividade artificial); flaky 1/3. **Substituído** pela reserva de espaço. |
| Contract-Net no **`connect`** (recrutamento) | handshake de parceria p/ multi-bloco | uso legítimo de CNP (recurso exclusivo = a parceria) | É o multi-block coop → **follow-up #44**, gated U9 #17. |
| CBBA (bundles + consenso) | leilão multi-item com consenso | lookahead multi-task ótimo | over-kill agora (só 2 tasks concurrent). Preterido p/ a fase atual. |

> Nota: o paper que o dono passou ([arXiv 2604.17353](https://arxiv.org/abs/2604.17353)) é
> **homônimo** ("Hive", escalonamento de sistemas de **LLM**) — sem relação com alocação em grid.
> Descartado.

## 6. Recomendação + arestas que SUPERAM o prior art (*cite & improve*)

**Núcleo (#40): seleção por valor + reserva de goal-zone + custo + dispersão.** É a melhor 1ª
opção — e o argumento mais forte é o caveat do **vencedor** (o allocator não decidiu o contest;
mapa+montagem decidiram). Over-engenheirar é o erro documentado.

Duas arestas concretas onde **batemos** o prior art:

1. **Reservar o FOOTPRINT de montagem (não só um ponto), com congestão principiada.** MMD admite
   que o `crowdedness` foi ad-hoc; FIT BUT reserva "no olho". **Nós temos o que eles não tinham:**
   o SharedMap com overlay de footprint/ocupação (#2, `PENALTY=16`). Reservar **as células que o
   padrão precisa** (checando livres no mapa) mata o "esperando espaço" do LI(A)RA na origem.
   → entra **dentro do núcleo #40**.
2. **Allocator MEDIDO, não chutado.** Os times voaram cego (LI(A)RA *"did not track what was going
   on"*; MMD debugou por print). O HIVE tem `run-hive` + contenção (#42) + flakiness (#41).
   Calibrar os pesos por **evidência** é a STRATEGY do projeto. → **follow-up #45**.

**Meta:** o esforço poupado no allocator vai para a **U9 (#17)** — o lever real segundo o vencedor.

## 7. Requisitos do núcleo (#40)

Objeto Java testável (decisão em Java; `.asl` orquestração fina):

- **R1 — Rank por valor.** Dadas as tasks ativas + posição do agente, ordenar por `valor =
  reward / tempo_estimado` (tempo ≈ distância ao dispenser + à goal-zone). Anti-crowding simples.
- **R2 — Reserva de footprint na goal-zone.** Para a task escolhida, reservar o conjunto de células
  (footprint do padrão) na goal-zone com arredores mais livres (via SharedMap overlay). Substitui o
  lock `claim_task` por reserva de **espaço**.
- **R3 — Seleção por custo.** Escolher o(s) agente(s) de menor custo (bid = distância/steps; reusar
  a matemática de `find_free_soloist`).
- **R4 — Dispersão.** Agente não-selecionado (sem reserva, valor marginal ≤ 0) **dispersa** (sai da
  frente da goal-zone) em vez de empilhar.
- **R5 — Single-block = coalizão tamanho 1** (coordinator+provider colapsam) → caminho do 06c.

**Testável (pseudo-unit primeiro):** JUnit sem sim — task de maior valor escolhida; goal-zone
reservada ao de menor custo; não-selecionados dispersam (não empilham); no-idle enquanto há valor
positivo.

## 8. Escopo / follow-ups (allocator completo — board #24)

- **#43 — multi-block:** coalizão (|blocos| providers + coordenador), mesma goal-zone reservada;
  providers→dispenser→coordenador→`connect`→submit. **Gated U9 #17 / connect #21.**
- **#44 — Contract-Net descentralizado:** recrutamento de providers (cfp→propose→award) sem chefe
  central, estilo LI(A)RA. **Gated U9 #17 / connect #21.**
- **#45 — calibração por evidência:** pesos do allocator parametrizados, A/B no #42 sob #41.
- **#22 — org aloca + mede vs baseline** consome este mecanismo.

**Fora de escopo do núcleo:** CBBA; leilão sobre tasks; jogo adversário.

## 9. Validação

- **JUnit** (núcleo, sem sim): R1-R5 acima.
- **Cenário de contenção #42** (multi-agente/multi-task): submits ≥ baseline.
- **Gate de flakiness #41**: 06c estável (≥K/N PASS) — o critério que o `claim` sozinho não passou.
- **Bundle:** o núcleo #40 **merge junto com #38** (que não merge sozinho — 06c regrediu p/ ~1/3).

## 10. Reuso de código existente

- `TaskBoard` (`place_bid`/`resolve_auction`/`bestBid`/`evaluate_task`) — leilão já testado;
  reaproveitar a infra de board como **contador/reserva**, não como lock.
- `SquadCoordinator.find_free_soloist` — já computa agente-livre-mais-próximo (distância toroidal):
  a função-bid de custo.
- `SharedMap` (overlay #2 footprint/ocupação, `translateCells`) — a base da reserva de footprint.

## 11. Questões em aberto (p/ ce-plan)

- Forma exata do termo anti-crowding (constante simples vs custo de footprint vivo) — começar simples,
  calibrar no #45.
- Onde mora a reserva: estender `TaskBoard` (board) vs novo artefato `Allocator`. Decidir no plano.
- Estimativa de "tempo até submit" no rank — distância A* real vs Manhattan toroidal (custo de cálculo).

## 12. Referências

- Spec: `massim_2022/docs/scenario.md` (§Tasks l.200-204; §Configuration `tasks` l.778-782; `connect` l.273-292).
- Livro MAPC 2022 (`local/978-3-031-38712-8.pdf`): MMD §value function (Eq.3-4) + caveat; FIT BUT
  `deSouches`/coalizão; LI(A)RA §A.4-A.5 (decentralizado, "similar to Contract Net").
- Notas de aula MAS (Notion): Durfee 2001 — Contract Net p/ task sharing **heterogêneo**; homogêneo
  (ToH) ⇒ alocação trivial → bid de **custo**.
- [GCAA — Greedy Decentralized Auction (arXiv 2107.00144)](https://arxiv.org/abs/2107.00144);
  [CBBA](https://www.emergentmind.com/topics/consensus-based-bundle-algorithm-cbba) (avaliados).
- LI(A)RA: `github.com/Liga-IA/liara-agents` (`synchronism.asl` = ref. da U9 #17).
