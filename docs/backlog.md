# HIVE — Backlog

Itens de trabalho futuros, **não priorizados** (a ordem aqui não implica prioridade) — **exceto** a
tabela em "Prioridades (revisão vs spec, 2026-06-18)" abaixo, fruto do cruzamento com o livro oficial
do MAPC 2022 e os arquivos de config.

## Status — o que já landou vs WIP (2026-06-20)

**✅ Concluído:**
- **Track 3 Fase D — posicionamento relativo (incremento 1).** Dead-reckoning por-agente, `SharedMap`
  por instância (`map_<nome>`), `translateCells` (costura R7 da fusão), overlay #2 por entidade
  percebida + filtro de time inimigo. **Verificado por boot:** o cenário oficial (`absolutePosition:false`,
  70×70) agora **roda** e os agentes navegam/convergem a alvos no frame relativo (antes nem rodava → score
  0 por inércia). Plano:
  [`2026-06-17-004-...`](plans/2026-06-17-004-feat-fase-d-posicionamento-relativo-plan.md). *Deferido:*
  U4 (inferência de dimensão toroidal) e U9 (fusão de mapas — seção própria abaixo).
- **Grid parametrizável** (Adaptação oficial, item 1 — `hive.GridConfig` + `-PgridW/-PgridH`).
- **Harness JUnit** (Testes, item 1 — 44 testes verdes: A* toroidal/overlay, dead-reckoning, tradução de frame, grid, leilão).
- **Navegação: heading-balanceado + handedness (plano 002, 2026-06-18).** U1 (Java `get_nearest_frontier_biased`
  + 9 testes JUnit), U2 (ASL `!do_explore` usa heading), U3 (tiebreak horário em `pick_escape`).
  Validado visualmente pelo monitor (agentes mantêm direção preferida). Delta de score: neutro (3/15 adoptions
  = baseline) — gargalo confirmado como `failed_path` 40-60%, não dispersão.
- **#38 Flat workers + #40 Allocator descentralizado (squash merged main, commit `d0f63a9`, 2026-06-19).**
  - #38: todos os 15 agentes partem de `hive_agent.asl`, `role_adoption.asl` incluso, sem `squad_leader`/`sentinel`.
  - #40: `TaskBoard.select_task` com `putIfAbsent` (1 agente/task atômico), guard de pré-rotação ótima
    em `connect_protocol.asl` (`RotationsNeeded.java`).
  - **Validado 3/3 PASS** no cenário `06c-single-collect` (submits_ok ≥ 1).
  - **IsolationRolesConfig (40×40): 15/15 adotaram worker, 10 submits emitidos, 0 bem-sucedidos**
    → revelou 2 bugs de loop (issues #47/#48) + gap de navegação (#49).
  - **OfficialRolesConfig (70×70): 6/15 adotaram worker, 0 submits** → gap de navegação/exploração.

**✅ #47 — Loop de pré-rotação (squash merged main, commit `4b50e15`, 2026-06-20).**
  Contador `rotate_pre_submit_fails(TaskName,F)` + guard P2 NAF-safe. Abort em F≥3 via
  `!finalize_task`. Antes: A5 rotate:135 failed:131 (97%). Depois: rotate:30 failed:1.
  06c PASS: submits_ok=1, score=10.

**⚠️ Regressão em aberto (frente:pontuar — P0):**
- **#48 — Loop de detach no STUCK recovery**: detach emitido na direção errada → `failed_target`
  em loop (A7: 106/107, A13: 99/102, A2: 32/33 failed_target).

**🚧 Gaps de navegação em aberto (frente:mover-mapear):**
- **#49 — Navegação robusta (Eixo N):** SharedMap vazio → A* falha → agentes em skip 257-282/300 steps.
  Exploração não dirigida → 6/15 alcançam role-zone no oficial. Sub-itens: N1 (fallback A*), N2 (setor de exploração), N3 (approach com clearance).

**Próximo, após fixes de regressão:** #49 N1 (fallback A*) → #49 N2 (exploração por setor) → validar
≥10/15 role-zone no oficial → #32 (capstone Eixo M) → Prioridade 3 (U9 mapa merge).

> **Decisão do dono (2026-06-19, via revisão da espinha #30):** a U9 **não é mais "pós-entrega"** — é
> **escopo comprometido** da frente Mover & Mapear (issue #17), a ser **entregue antes do delivery** e
> **validada em `absolutePosition:false`** (a condição da competição; `true` só serve ao 1º isolamento). A
> ordenação por score abaixo (single-block primeiro, por ser o gate inicial) continua, mas o **teto
> single-block deixa de ser aceito como ceiling** — vamos entregar a fusão de mapa. Ver espinha #30 e #17.

## Prioridades ordenadas — visão atual (2026-06-19)

Lista única de issues por prioridade de execução, derivada do diagnóstico dos runs IsolationRolesConfig + OfficialRolesConfig de 2026-06-19. Substitui a intuição pela evidência dos histogramas de ação.

| P | Issue | Categoria | Evidência / Justificativa | DoD |
|---|-------|-----------|---------------------------|-----|
| ~~**P0**~~ | ~~**#47** — Loop pré-rotação~~ | ~~fix · frente:pontuar~~ | ✅ landed `4b50e15` | ✅ |
| **P0** | **#48** — Loop detach no STUCK recovery | fix · frente:mover-mapear | A7: 106/107 detach failed_target. Bug simétrico ao #47. | `failed_target:detach < 5/agente` |
| **P1** | **#49 N1** — Fallback A* quando mapa vazio | feat · frente:mover-mapear | A1/A4/A11/A12: 257-282 skips com bloco em mãos. SharedMap vazio → skip-loop. | `skip < 50/300` para agentes com bloco |
| **P1** | **#49 N2** — Exploração dirigida por setor | feat · frente:mover-mapear | 6/15 alcançam role-zone no oficial. Exploração aleatória não cobre o grid a tempo. | ≥10/15 adotam worker no OfficialRolesConfig |
| **P2** | **#32** — Eixo M: capstone navegação oficial | validação · frente:mover-mapear | Capstone que integra #47+#48+#49 na escala real (70×70, 15 ag). | ≥10/15 role-zone + `failed_path < 20%` |
| **P3** | **#17** — U9 fusão de mapas (map-merge) | feat · frente:mover-mapear | Com mapa fundido: dispensers/goal-zones compartilhados → menos re-exploração; habilita multi-bloco. | ≥2 agentes fundem mapas no replay oficial |
| **P3** | **#42** — Cenário de contenção multi-agente | harness · frente:infra | Gate do allocator (#40) em escala real. Confirma que 06c não é suficiente como gate. | PASS cenário contenção multi-task |
| **P4** | **#46** — ZoneQueue (fila de zona com capacidade) | feat · frente:pontuar | Evita aglomeração na goal-zone. Capacidade 1 ou 2 por posição. | Fila funcional com simulação controlada |
| **P4** | **#43** — Allocator multi-block (coalizão) | feat · frente:pontuar | Pós-U9: multi-bloco coordenado. Alta recompensa, bloqueado por frame compartilhado. | ≥1 submit multi-block no oficial |
| **P5** | **#41** — Harness de flakiness (N× runs) | feat · frente:infra | Gatea P4+ — não otimizar alocador sem medição estável. | ≥K PASS em N runs automáticos |
| **P5** | **#44** — Contract-Net descentralizado | feat · frente:pontuar | CNP para recrutar providers. Bloqueado por multi-block. | CFP→propose→accept no replay |
| **P5** | **#45** — Calibrar pesos do allocator | feat · frente:pontuar | Refinamento pós-medição. Bloqueado por harness #41. | Score melhora vs baseline |
| **bloq** | **#16** — Eixo 4a: frente-a-frente | cenário · frente:mover-mapear | Bloqueado — aguarda navegação base (#49). | — |
| **bloq** | **#28** — Eixo 4b: exploração sem zigzag | cenário · frente:mover-mapear | Parcialmente coberto por #49 N2. | — |
| **bloq** | **#29** — Eixo 4c: desviar com bloco/footprint | cenário · frente:mover-mapear | Bloqueado — aguarda #49. | — |
| **bloq** | **#31** — Eixo 2b: dead-reckoning | cenário · frente:mover-mapear | Bloqueado — aguarda base de navegação. | — |
| **bloq** | **#21** — Eixo 8: connect 2 agentes | cenário · frente:pontuar | Bloqueado por frame compartilhado (U9). | — |
| **pós** | **#22, #23, #35, #36, #39, #20** | org/adversário/normas | Pós-delivery ou pós-U9. | — |

> Critério de ordenação: (1) bugs de regressão antes de features; (2) menor granularidade primeiro (N1 antes N2); (3) evidência medida prevalece sobre intuição.

---

## Prioridades (revisão vs livro MAPC 2022, 2026-06-19)

Cruzamento de `docs/backlog.md` contra o **livro oficial MAPC 2022** (`local/978-3-031-38712-8.pdf`)
cap. "The MAPC 2022" (organizers) + papers dos 5 times + config bundled (`sim/roles/standard.json`,
`sim/norms/standard.json`) + exercício PCS5703. **Numeração 1→4 por impacto em score.**

### O que a competição mostrou — onde o HIVE destoa

| Time | Plataforma | Roles adotados | Padrão de agentes | Resultado |
|------|------------|---------------|-------------------|-----------|
| **MMD** | Python | worker + constructor | Flat, todos workers | **1º lugar** (30pts, 10/12 wins) |
| **GOALdigger** | GOAL | worker + digger | Flat | 2º (22pts) |
| **FIT BUT** | Java/JADE | worker + digger | Flat | 3º (19pts) |
| **GOAL-DTU** | GOAL | default + worker | Flat | 4º (9pts) |
| **LI(A)RA** | **Jason** | default + worker | Flat | 4º (9pts, ~1100 linhas) |
| **Blup** (warm-up) | Kotlin/OR-Tools | explorer → worker | Flat + groups | 2º geral (11/15 warm-up) |
| **HIVE** | JaCaMo/Jason | ? (score=0) | **Squad hierárquico** ← destoa |

Observações do livro (organizers, cap. "The MAPC 2022"):
- *"it is usually not helpful to come up with a centralized solution"* — citação direta.
- Nenhum time usou `squad_leader` ou hierarquia de squads.
- `survey` não foi usado por nenhum time.
- Single-block tem *"very small compensation"* — multi-block é onde o score cresce.
- MMD **ignorou normas** e venceu. Times de topo pagaram a multa sem resistir.

### Tabela de prioridades

| # | Item | Fundamento | DoD |
|---|------|------------|-----|
| **1 — ESTRUTURAL** | **Abandonar squad_leader → flat workers.** Remover `squad_leader.asl` e `sentinel.asl` como tipos de agente JaCaMo. Todos os 15 agentes (ou mais) partem de um único tipo capaz de adotar `worker`. MOISE+ mantém roles organizacionais (quem coordena) mas sem bloquear adoção de role MAPC. | Nenhum time competitivo usou squad_leader. O HIVE com squad_leader tem: (a) SELF-ASSIGN sem `can_score_role` → squad_leaders fazem `request` → `failed_role` ×21 no último run; (b) sentinels (A13-A15) nunca podem adotar worker; (c) apenas 9/15 agentes são candidatos a worker. | Todos os 15 agentes no `hive.jcm` incluem `role_adoption.asl`. Zero `failed_role` de agentes squad_leader/sentinel em replay. |
| **2 — PIPELINE** | **≥1 submit confirmado no oficial** (`OfficialRolesConfig.json`). Engloba sub-itens da Fase C: (a) gate de re-adoção sem adopt-spam; (b) ≥10/15 alcançam role-zone em 300 steps; (c) worker adotado executa `request`/`attach`/`submit`; (d) elo MOISE+ U4 fecha. | Prerequisito de tudo — sem score não há nada para multiplicar. Heading-balanceado testado → delta neutro → fix de navegação (handedness no escape, `failed_path`) ainda necessário para chegar à role-zone. | ≥1 `submit` confirmado no replay com `OfficialRolesConfig.json`. |
| **3 — MAPA** | **U9 — fusão de mapas cross-agente (mutual sighting).** Quando dois agentes se veem no mesmo step, calcular o offset entre frames e mesclar SharedMaps. | Teto sem U9: single-block solo (um agente coleta e submete sozinho — workers não sabem onde estão dispensers/goal-zones de colegas). Com U9: multi-block coordenado (`connect`) viável → muito mais score. LI(A)RA (Jason, 4th) fez isso como diferencial. `SharedMap.translateCells(dX,dY)` já implementa a álgebra — falta o handshake. | ≥2 agentes fundem mapas após avistamento mútuo em replay. Workers encontram dispensers/goal-zones de colegas via SharedMap fundido. |
| **4 — OTIMIZAÇÃO** | **Constructor + decisão deliberada de ignorar normas (MMD-style).** Alguns agentes adotam `constructor` (speed=2 vazio, speed=1 com ≤2 blocos) para coverage rápida e multi-block delivery. Normas: eat-the-penalty deliberadamente (não conformidade automática). | MMD venceu com worker+constructor+ignorar normas. Single-block tem "very small compensation". Constructor move rápido vazio → encontra role-zones, dispensers, goal-zones mais rápido. Normas são raras (chance:15) e baratas (multas 1-10 energia) — conformidade pode custar mais que a multa. | Score cresce vs baseline de Priority 2. Constructor adotado e movendo. |

> **Prazo (exercício):** entrega de relatório+código **20/06/2026**; competição vs. turma + arguição "a
> anunciar". **⚡ AÇÃO IMEDIATA:** confirmar se relatório entregue — (a) entregue → 100% código/score
> competitivo; (b) pendente → dividir esforço explicitamente.

## Fase C — achados do boot (2026-06-18): o que falta

Boots de validação rodados pela skill **`run-hive`** (driver + analyzer de replay; a verdade vem do
**replay**, não do log). A adoção **funciona em parte**, mas revelou que **"score>0 no oficial" era o
critério ERRADO** — conflava "a Fase C funciona" com "o pipeline inteiro pontua no oficial", e o oficial
está degradado por motivos **independentes da adoção**.

**O que o replay mostrou (oficial 70×70, 300 steps):**
- ✅ Roles certos: `worker = default ∪ {request,attach,connect,disconnect,submit}` (merge do engine) — **tem `submit`**.
- ✅ Líder + MOISE+ vivos: `[LEADER]`, `assign`, e o scheme commitando `m_collect/m_assemble/m_submit`.
- ⚠️ **Adoção fraca:** só **4/15** alcançam uma role-zone em 300 steps (descoberta/alcance ruim).
- 🐞 **Adopt-spam:** um agente virou worker mas re-adotou **209×** — o gate `can_score_role :- my_role(worker)`
  não para a re-adoção de forma confiável quando o agente **fica** sobre a role-zone (os que saíram pararam por acaso).
- ❌ **Workers não transicionam p/ `request`:** após adotar, ficam só andando (nenhum `request`/`attach`) →
  **0 submits**. A atribuição do líder não está virando coleta no worker adotado.
- 🔑 **Cross-frame confirmado:** os configs dev que **pontuam** são **40×40 + `absolutePosition:true`**; o
  oficial é **`false`** → sem origem comum, `set_meeting_point`/`connect_request` falham (o **#2 da Fase D**)
  → **multi-bloco não fecha no oficial sem a U9** (por isso a U9 subiu p/ P2↑).

**Reframe do critério de sucesso da Fase C** (mudar em isolamento — STRATEGY):
- **NÃO** "score>0 no oficial" (depende de navegação 70×70 + cross-frame/U9 + maturidade do leilão).
- **SIM** "adota `worker` e o `request` passa (sem `failed_role`), e o pipeline volta a pontuar" — validado em
  **`conf/IsolationRolesConfig.json`** (FastTest 40×40 + `absolutePosition:true` + roles restritos; **só a
  variável adoção muda** vs o config que já pontua). Se pontua → adoção provada; se não → bug da camada de adoção.

**Sub-itens concretos da Fase C (Prioridade 2 — todos bloqueiam ≥1 submit no oficial):**

> **⚠️ Causa raiz estrutural (Prioridade 1, revisão 2026-06-19):** parte dos bloqueios abaixo são
> **sintoma** de squad_leader/sentinel nunca adotarem worker. A Prioridade 1 (flat workers) resolve os
> sub-itens (a) e parcialmente (b) antes de qualquer mudança de lógica de adoção.

1. **Gate de re-adoção robusto** — parar o adopt-spam (verificar o update de `my_role(worker)`; gatear por
   resultado de ação/percept, não só por crença). DoD: 0 re-adoções em replay de 300 steps.
2. **Alcance de role-zone** — 4/15 é pouco; melhorar navegação até a role-zone (livelock `failed_path`
   40-60% no grid grande). Após Prioridade 1: ≥15/15 tentam adotar (agora só 9/15 tentam). DoD: ≥10/15
   alcançam role-zone em 300 steps (config isolamento).
3. **Transição pós-adoção → coleta** — garantir que worker adotado executa `request`/`submit`. **✅ Parcial
   (2026-06-18):** plano (c) em `role_adoption.asl` auto-inicia coleta solo + guard `& N > 30` adicionados
   (commits `ecc97df`/`8fbc7cc`). **Bloqueio remanescente:** workers em `pending_submit` com bloco em mãos
   reportam `"Nenhuma goal zone conhecida"` — SharedMap é per-agente, worker só conhece goal zones que
   pessoalmente viu. Fix (2026-06-18, commit `8f9d9c4`): explore com bloco ao invés de skip. DoD: ≥1
   `submit` confirmado em replay.
4. **U4 — elo MOISE+ completo** — verificar que `assign` do líder chega ao worker adotado como obrigação
   (`m_collect`) e worker executa (não apenas MOISE+ vivo/logando). DoD: replay mostra scheme `m_collect`
   comprometido **e** worker executando `request`.

## Fases de capacidade — construir antes de pontuar (dono, 2026-06-18)

Princípio (estende a STRATEGY): há **fases em que pontuar NÃO é a métrica** — a métrica é a **capacidade**
(cobertura de mapa, steps-ao-alvo, qualidade do merge). Atacar **uma dificuldade por vez**, em **cenários
controlados** (grid pequeno, `randomSeed` fixo, feature isolada), **sem rodar a sim cheia**.
**Determinismo:** com `randomSeed` fixo o **cenário** é reproduzível (mesmo grid/tasks/normas) — a
**variância** vem dos **AGENTES** (escalonamento Jason, `.random`, timing), então testes de capacidade
isolam a lógica do ruído de score.

**Ideias factíveis a sequenciar (uma por vez, em ordem das prioridades):**
1. **Flat workers (Prioridade 1 — estrutural).** Ver tabela de prioridades. Metrics: zero `failed_role`
   de squad_leader/sentinel, ≥15/15 tentando adotar.
2. **Pipeline básico: ≥1 submit (Prioridade 2).** Sub-itens Fase C acima. Metric: ≥1 submit em replay oficial.
3. **Explorer-first (Blup/Paula, warm-up, quase vice).** `default` → acha role-zone → adota `explorer`
   (speed=3 sem blocos, vision=7) → cobre o mapa rápido → troca p/ `worker` para coletar. **Distinção do
   livro (2026-06-19):** Blup usava `explorer` para **speed=3** (alcança role-zones, dispensers, goal-zones
   em menos steps), não para vision=7 especificamente. Gate: verificar se speed=3 reduz
   time-to-first-adoption mesmo com `failed_path` alto. Metric: time-to-first-adoption em replay.
4. **Merge de mapas (U9, Prioridade 3).** Mapa coletivo cedo → habilita multi-bloco (`connect`). Só
   necessário no oficial (`absolutePosition:false`).
5. **Navegação sem livelock (GPS/footprint/handedness).** O `failed_path` 40-60% é o gargalo medido.
   Handedness no escape: não testado isolado ainda — gate antes de promover (ver parking lot).

**Por que serve à abordagem:** "mudar em isolamento, promover por evidência" — mas com a **métrica certa
para a fase** (capacidade, não score). Uma por vez evita empilhar heurística no escuro.

**Sequência sugerida:** `/ce-brainstorm` da navegação (fixa tradeoffs do GPS) → harness de **cenários
controlados de capacidade** (estende "Estratégia de testes" §3) → explorer-first + U9 quando a navegação
não for mais o gargalo.

## Estratégia de coleta / montagem / submit (observado em 2026-06-17)

**Contexto:** com o livelock de navegação resolvido (#2 A*-ciente-de-colega + escape reativo
como fallback), o próximo gargalo de score deixa de ser movimento e passa a ser a camada de
**estratégia de tarefas** (coleta, montagem, submit). Fora do escopo do fix de navegação.

**Sintomas observados ao vivo (monitor + log, run de 200 steps):**
- **Super-coleta:** a norma de carry-limit ("Carry") dispara muito (~96 NORMs/run) — agentes
  pegam blocos além do permitido e detacham o excesso.
- **Spinning de submit:** `rotate(cw)` repetido para orientar o bloco no submit
  (`[SUBMIT] FALHOU (rotacao N/4)`), às vezes em vários agentes ao mesmo tempo.
- **Não-convergência:** muitos agentes coletam bloco mas ficam espalhados / no canto, longe
  das goal zones, sem submeter — preferem coleta **solo** em vez de montar/cooperar.

**Investigação futura sugerida:** `/ce-debug` ou `/ce-ideate` sobre (a) o rotate-loop do submit
(task cuja orientação as 4 rotações não satisfazem? ciclo rotaciona→falha→re-coleta?) e
(b) a política coleta-solo vs montagem-cooperativa, incluindo a interação com a norma "Carry".
Métrica desse track = **submits/score**, não `[OSC]`. **Não é navegação.**

### Sub-itens concretos (auditoria de código, 2026-06-17)

1. **Viés single-block no scoring do líder.** Em `squad_leader.asl` o score é `Reward×100`
   para task de 1 bloco vs `(Reward/NBlocks)×10` para multi → single-block ganha o leilão
   quase sempre (100R vs 5R p/ 2 blocos), mesmo quando a multi-bloco vale mais. Resultado:
   o time ataca quase só tasks solo de 1 bloco e **ignora as multi-bloco (maior reward)**.
   Rever a fórmula para considerar reward total e viabilidade de montagem.
2. **Normas — REBAIXADO (P3) pela evidência do livro.** Hoje só a norma de carry é tratada
   (`perception.asl` → `carry_limit`; detach de excesso). **A spec contradiz o impulso de "tratar
   normas genéricas":** o livro §4 (*Lessons Learned/Norms*) observa que os times de topo **ignoraram
   as normas e simplesmente aceitaram a multa** (*"teams to pay little attention to the norms and just
   accept any coming punishment"*; *"almost no corresponding drops in the number of worker agents"*) — e
   o MMD **venceu** assim. A config confirma que normas são **raras e baratas** (`sim/norms/standard.json`:
   `chance:15`, `simultaneous:1`, multas 1–10 energia, recupera 1/step de 100). Reescopo:
   - **(a) Risco latente no que JÁ existe:** o detach do excesso (conformidade da norma Carry) pode
     estar **custando score** — largar um bloco útil p/ poupar 2–3 de energia é trade ruim. Medir /
     possivelmente reverter para "comer a multa".
   - **(b) Exposição nova com a Fase C:** ao adotar `worker`, ligam-se as normas **`Adopt`**
     (`playing:0` e `playing:50%` no config) — mas a recomendação da spec continua: **ignorar**. Tornar
     isso uma decisão *deliberada* (eat-the-penalty), não uma rotina de conformidade.
3. **Medir a priorização + cenário oficial.** Instrumentar pontos capturados vs disponíveis,
   multas pagas e tasks de alto valor perdidas. Rodar a config oficial do MAPC 2022
   (`massim_2022/server/conf/SampleConfig.json`, 2 times) para realismo competitivo — aí
   aparecem também lacunas de adversário e de múltiplos roles (não cobertas pela config de dev).
4. **Entender a vida das tasks + justificar o design de leilões.** Antes de otimizar a *escolha*
   de tasks, entender as regras: como as tasks surgem para os agentes (aparecem para todos? em
   que janela de steps? — ver `tasks.concurrent`/`iterations`/`maxDuration` na config), quando
   expiram, e como isso interage com o leilão. Levantado pelo dono (2026-06-17): **por que o
   projeto usa leilões** (decisão do colega) e **avaliar manter vs remover** o mecanismo. Upstream
   do item 1 (viés single-block): não dá pra medir/otimizar bem "tasks perdidas" sem entender o
   ciclo de vida da task.

## Adaptação ao cenário oficial MAPC 2022 (pré-requisito p/ rodar 2-times oficial)

A config **bundled** (`massim_2022/server/conf/SampleConfig.json` → `sim/sim1.json`) é **70×70**, até
**20 agentes/time**, **750 steps**, e usa **roles dinâmicos**. Auditoria (2026-06-17):

> **Correção (revisão vs spec, 2026-06-18):** os 750 steps/70×70 são do *sample bundled*, **não** do
> torneio. O torneio 2022 (livro §3) foram **3 simulações**: **Sim1** = 400 steps / 70×70 / 20 ag.;
> **Sim2** = 600 / 70×70 / **obstáculos densos**; **Sim3** = **800 steps / 100×100 / 40 agentes**. Se a
> competição da turma espelhar o torneio, o **Sim3 (100×100, 40 agentes)** é um ponto cego — não modelado
> em lugar nenhum do backlog (ver item 3). (Aviso de nomenclatura: `conf/OfficialTestConfig.json` chama-se
> "Official" mas usa um `default` degenerado *com todas as ações* + 15 ag./150 steps — não é o oficial.)

1. ✅ **FEITO — Grid parametrizável.** Resolvido: `hive.GridConfig` + flags `-PgridW/-PgridH` (Track 3
   Fase B); e na Fase D, no oficial as dims ficam 0 (frame não-normalizado, A* sem-wrap) — eliminando o
   módulo errado de 40 num 70×70. Não há mais `set_grid_dimensions(40,40)` hardcoded.
2. 🚧 **WIP — Fase C: Adoção de role (BLOQUEADOR / gate de score).** O role inicial `default` só tem
   `[skip,move,rotate,adopt,detach,clear]` — **sem `request`/`attach`/`submit`/`connect`**. Para
   coletar/montar/submeter é preciso ir a uma **role-zone** e `adopt(worker|constructor)`. O HIVE
   **não tem lógica de adoção de role** → na config oficial o time **não pontua** (só anda). Requer:
   navegar à role-zone → `adopt` → só então coletar. **Sem isso, o teste oficial dá score 0.**
   **Costurar com o MOISE+ (graduado):** as obrigações da org (`collector→m_collect`,
   `assembler→m_submit` em `src/org/hive_org.xml`) **pressupõem** que o agente pode coletar/montar — i.e.,
   que já adotou `worker`/`constructor`. No oficial, um `collector` está *obrigado* a coletar e
   *fisicamente não consegue* até adotar. A Fase C tem de ligar a camada org à camada de role MAPC (é
   justo o que o relatório avalia: "facilidade/dificuldade do modelo organizacional").
3. **Escala p/ 20 (e 40) agentes/time.** O `entities` é o **máximo** de contas (dá pra rodar com menos —
   15 conectam, restantes ociosos). **Blocker concreto:** as cardinalidades do MOISE+ em
   `src/org/hive_org.xml` somam `max` = **19** (`squad_leader 4 + collector 8 + assembler 4 + sentinel 3`)
   → o time **já não admite os 20 do Sim1**, e está longe dos **40 do Sim3**. Subir o squad p/ 20 exige
   editar essas cardinalidades; competir no Sim3 exige repensar a composição p/ 40.
   > **Decisão Sim3 (20/06):** Sim3 (100×100, 40 agentes) está **fora de escopo** para a entrega de
   > 20/06 — aceitamos score 0 se a competição incluir Sim3. Focar Sim1 (70×70, 20 ag.) como
   > cenário-alvo. Reavaliar pós-entrega.

> **Atualização (2026-06-18, pós-Fase D):** o oficial **já roda** (a Fase D destravou navegação sem
> `absolutePosition` — boot confirmou agentes convergindo a alvos). O que falta para **pontuar** é só a
> adoção de role (item 2, **Fase C — WIP**): sem ela os agentes andam/exploram mas não têm as ações de
> coleta/submit. Alternativa runnable hoje p/ contenção real: self-play 2-times na **nossa** config 40×40
> (role `default` já tem todas as ações), com 2 instâncias HIVE (worktree time B: `agentB*`).

## Fusão de mapas cross-agente — agentes juntam mapas ao achar um referencial comum (U9, deferido da Fase D)

**Levantado pelo dono (2026-06-17): feature importante p/ entregar mais pontos.** Hoje (incremento 1
da Fase D) cada agente tem um mapa **privado** no seu próprio frame dead-reckoned (`map_<nome>`); sem
posição absoluta, não há frame global, então as descobertas **não são compartilhadas**.

**A ideia (dono):** quando dois agentes do time **se enxergam no mesmo step**, cada um passa a ter a
**referência que transforma** os dados do mapa do colega — basta saber a **posição relativa** deles
(o offset entre os dois frames). Daí toda posição informada pelo colega é traduzida pro frame próprio.

**Mecanismo (padrão *proven* do LI(A)RA, `src/asl/synchronism.asl`):** ao perceber um colega na visão,
registrar `found_mate(...)` e **broadcast**; quem viu o par no **mesmo step** com offsets consistentes
computa `mate_filter(Colega, dX, dY)` (o offset entre frames); a partir daí, posições recebidas do
colega entram traduzidas por `(+dX, +dY)`.

**Já está meio caminho andado:** a costura `SharedMap.translateCells(dX, dY)` (R7, commit `12500f0`)
**já implementa e testa** a álgebra de tradução toroidal do mapa por um offset. Falta: (a) o handshake
de avistamento mútuo que **descobre** o `dX,dY`; (b) propagar/importar os dados do colega traduzidos
(dispensers/goal-zones/role-zones/obstáculos) — sem reescrever o mapa.

**Mecanismos de ancoragem — como DESCOBRIR o offset entre frames (progressão de robustez; visão do
dono, 2026-06-18).** O avistamento mútuo é só o **primeiro** de três caminhos para achar o
ponto-em-comum que alinha dois mapas; juntos, eles fazem os mapas de todos convergirem para **um único
mapa compartilhado**:

1. **Cruzamento (avistamento mútuo).** Dois agentes se veem no mesmo step → o offset relativo é direto
   (o que o LI(A)RA faz). Robusto, mas só dispara quando os caminhos se cruzam.
2. **Landmark com identificador.** Ambos viram a **mesma feature fixa e identificável** (goal-zone,
   dispenser de um tipo, role-zone) → alinhar os frames por ela **sem precisar se cruzar**. Cuidado:
   as features do MAPC **não são únicas** (vários dispensers `b0`, várias goal-zones) → um landmark
   sozinho é **ambíguo**.
3. **Assinatura (conjunto de pontos).** Uma **constelação** de features próximas — as posições
   relativas entre elas — forma uma assinatura local única o bastante para casar dois mapas mesmo sem
   unicidade individual. É essencialmente **registro de nuvem de pontos / scan-matching**; resolve a
   ambiguidade do (2) e é o caminho mais geral (e o mais complexo de implementar).

**Convergência para um mapa global.** Achado um offset A↔B (por qualquer um dos 3), a tradução é
**transitiva**: A↔B + B↔C ⇒ A↔C por composição — **gossip multi-hop** (justamente a fraqueza 1-hop do
LI(A)RA a melhorar). No limite, todos os frames colapsam num **único mapa compartilhado**, restaurando
a partilha que a instância-por-agente (U3) abriu mão e habilitando um **path finder rápido e eficaz
sobre o conhecimento coletivo** (A* sobre o mapa de todos, muito menos re-exploração) — o instrumento
estratégico de pontuação que o dono aponta.

**Arquitetura do merge — SOLÚVEL; decisão em aberto (pergunta do dono, 2026-06-18).** As duas formas
abaixo são as duas opções reais, e a álgebra (tradução por offset toroidal) **já está resolvida e
testada** na `translateCells` — então isto NÃO é uma incógnita técnica, é uma escolha de *onde* aplicar
a tradução:

- **(A) Descentralizado — cada agente agrega no PRÓPRIO frame.** Cada agente mantém seu `map_<nome>`
  (origem própria) e, ao receber dados de um colega + o offset, traduz e **ingere no seu frame**. Cada
  um fica com o mapa-união, na sua coordenada. **Recomendado** p/ este código: estende o que já existe
  (instância-por-agente U3 + `translateCells`) sem reintroduzir artefato compartilhado; a navegação é
  trivial (o `my_pos`/`dr_pos` do agente já está nesse frame — nada a converter na hora do A*); degrada
  bem (cada um funde o que conseguiu). Custo: N cópias do mapa (memória **negligível** a 70×70 — o
  review de performance confirmou).
- **(B) Centralizado — um mapa merged único, cada agente converte p/ a sua origem.** Um mapa global
  num frame canônico (ex.: o frame do 1º agente ancorado); cada agente traduz **a sua posição/consultas**
  ↔ global na hora de navegar (não o mapa inteiro — isso seria O(células) por agente por update).
  Reintroduz um artefato compartilhado (a contenção CArtAgO que o projeto já mediu — embora o profiling
  tenha mostrado que não era o gargalo) e a escolha de "qual frame é o global".

**As duas são equivalentes na matemática** (mesma tradução por offset); diferem só em onde a tradução
mora — (A) traduz dado-que-entra e guarda no meu frame; (B) guarda em global e traduz minha-consulta. A
recomendação é **(A)**. **A parte genuinamente difícil NÃO é o merge — é a DESCOBERTA confiável do
offset (ancoragem acima) + o DRIFT do dead-reckoning** (offsets aproximados que acumulam erro; quando
A↔B↔C fecha um ciclo que discorda de um A↔C medido, é preciso reconciliar). Isso é literalmente o
problema de **SLAM** (mapeamento e localização simultâneos) da robótica: ancoragem ≈ data association /
scan-matching; composição transitiva ≈ pose graph; reconciliar ciclos ≈ loop closure. Há solução
conhecida e madura na literatura — é trabalho de engenharia, não pesquisa em aberto.

**Cuidados de engenharia (o que torna isto não-trivial):** (i) **wrap toroidal** no alinhamento — a
`translateCells` já trata; (ii) o **drift** do dead-reckoning faz os offsets serem aproximados → vale
re-ancorar periodicamente (qualquer dos 3) e **resolver conflitos** de célula na fusão (um diz parede,
outro diz livre — manter o mais recente / maior evidência); (iii) **ambiguidade** quando vários pares
casam no mesmo step → escolher o de maior corroboração; (iv) custo: a fusão deve continuar barata por
step (a `translateCells` é O(células) — ver nota de performance do review).

**Por que move o score:** agentes compartilham descobertas → acham dispenser/goal-zone/role-zone muito
mais rápido (menos re-exploração); e um **frame compartilhado habilita montagem multi-bloco coordenada
por coordenadas** (rendezvous/connect), que é onde está o reward alto. Bônus: restaura a partilha de
mapa que o dev perdeu ao virar instância-por-agente.

**Também conserta a regressão cross-frame (#2 do review Fase D):** hoje `connect_request`
(`communication.asl`) e `set_meeting_point` (`squad_leader.asl`) trocam coordenadas no frame
dead-reckoned do remetente — sem origem comum, o destinatário as lê no frame errado e a navegação ao
rendezvous falha no oficial (pré-fusão só converge por adjacência percebida). O frame compartilhado da
U9 torna a troca de coordenadas válida de novo. Marcado com `FIXME Fase D (#2, cross-frame)` nos dois
sites do código.

**Melhorar vs LI(A)RA (não copiar):** o LI(A)RA é imperfeito — sem **wrap toroidal** na tradução (a
nossa `translateCells` já tem), gossip **multi-hop** é TODO (sync 1-hop), drift corrigido por
brute-force, e risco de **ambiguidade** quando vários pares se veem no mesmo step. Tratar esses pontos.

**Gate (sequenciamento, NÃO incógnita de viabilidade):** isto **não está deferido por ser arriscado ou
incerto** — a solução é conhecida (ver Arquitetura acima; é SLAM, problema resolvido na literatura). O
gate é de **prioridade/medição**: a fusão só vira score se houver score para multiplicar, e isso depende
da **Fase C** (adoção de role → pontuar no oficial).

> **Atualização (dono, 2026-06-19):** gate de prioridade **resolvido** — a U9 é **escopo comprometido**
> (não mais condicionada a "se o prazo comportar") e **validada em `absolutePosition:false`**. Single-block
> continua sendo o gate de score **inicial**, mas não bloqueia o desenvolvimento paralelo da fusão de mapa.

> ⚠️ **Dependência circular identificada (2026-06-18):** (1) U9 está gated atrás da Fase C provar score
> no oficial. (2) Fase C com `absolutePosition:false` requer que os agentes encontrem role-zones, mas
> sem mapa compartilhado (U9) a busca de role-zone por dead-reckoning individual é muito menos eficaz
> (12/15 agentes não alcançam em 300 steps). **Teto sem U9:** competir apenas com single-block (um
> agente coleta, submete sozinho); multi-bloco coordenado (`connect`) é inviável sem frame compartilhado.
> **Decisão de escopo (REVISTA — dono, 2026-06-19):** ~~aceitar o teto single-block como ceiling sem U9,
> tratando-a como multiplicador pós-entrega~~ → **a U9 é escopo comprometido da frente Mover & Mapear
> (#17), entregue antes do delivery e validada em `absolutePosition:false`.** A dependência circular acima
> é, na verdade, um **argumento PARA fazer a U9** (ela destrava a busca de role-zone no oficial), não para
> deferi-la. Single-block continua como gate de score inicial, mas **não é mais o teto aceito**. Ver espinha #30.
Origem: [`docs/brainstorms/2026-06-17-fase-d-posicionamento-relativo-requirements.md`](brainstorms/2026-06-17-fase-d-posicionamento-relativo-requirements.md)
(Scope Boundaries / U9), que cita o **LI(A)RA** (time Jason, MAPC 2022 — `github.com/Liga-IA/liara-agents`,
`src/asl/synchronism.asl`) como referência *proven* da técnica.

## Estratégia de testes — validar comportamento isolado, sem rodar o cenário completo

**Dor recorrente:** validar qualquer mudança custou um run de sim (~3-4 min, headless, com gotchas de
porta/órfãos). Mas a maior parte do que validamos por sim é **Java puro** (≈1380 linhas: artefatos +
internal actions) → testável em ms **sem sim**. Hierarquia proposta:

1. ✅ **FEITO (base) — Unit tests (JUnit, sem sim) — maior alavancagem.** Test source-set + JUnit já no
   `build.gradle`; 44 testes verdes (A* toroidal/overlay `SharedMapAStarTest`, `AdjacentDirection`,
   `GridConfig`, `TaskBoard`, `SquadCoordinator`, `LocalFrame`, tradução de frame `SharedMapRelativeTest`).
   *Resta:* regressão explícita do viés single-block do leiloeiro. Lógica pura testada:
   - `SharedMap.astar` / `wrappedManhattan` / overlay #2: dado grid + obstáculos + ocupação, asserir a
     direção — ex.: "contorna colega", "wrap toroidal 39→0", "origem/alvo não penalizados",
     "PENALTY alto → desvia". Seria a **regressão do #2 sem nenhum run**.
   - `TaskBoard.resolve_auction` / scoring: asserir vencedor; documentar/regredir o viés single-block.
   - `hive.*` (AdjacentDirection toroidal, etc.).
2. **Parse `as2j` das `.asl`** (já usamos ad-hoc; formalizar como check rápido).
3. **Mini-cenários** (grid pequeno ex. 10×10, poucos steps, setup à mão) só p/ comportamento que
   **emerge da interação** (ex.: 2 agentes num corredor p/ o escape ceder). Muito mais rápido que o cheio.
4. **Run cheio** (200/750 steps) só p/ end-to-end / score — raro.

5. **Métricas / harness de avaliação.** Validar comportamento precisa de métricas estruturadas (não
   grep ad-hoc como nesta sessão). Coletar por run, em formato assertável (JSON/CSV):
   - **Experimento de alavanca (pré-requisito da priorização).** Correlacionar expiração de
     deadline com `[STUCK]`/`[OSC]` (movimento) vs tasks perdidas por viés single-block /
     agentes que nunca submetem (estratégia) — responde *qual alavanca move o score* antes de
     investir em qualquer track. Cf. fix do EIS (−22× timeouts, mas score caiu 70→50 = variância):
     ganho indireto ≠ score. **Sem essa medição, priorizar o backlog é chute.**
   - **Score** (`results/*.json` do server), **submits**, **penalidades** (multas de norma; desativações
     por clear-event), e markers de comportamento (`[OSC]`, `[STUCK]`, `[DETACH]`, rotações de submit,
     NORMs, agentes ociosos, blocos coletados vs submetidos, time-to-first-submit, taxa de chegada à
     goal zone). Criar **novas métricas** conforme necessário.
   - **Facilitar adicionar métrica nova** — um ponto único de instrumentação/extração.
   - **Logs estruturados (NDJSON) para debug.** Persistir eventos estruturados (step, agente, tipo,
     payload) — estendendo o `dash_log` que já emite JSON ao dashboard — num arquivo por run, em vez
     de `.print`/INFO ad-hoc. Levantado pelo dono (2026-06-17): facilita debug (a dor de grep ad-hoc
     do log foi sentida ao validar a Fase A do Track 3) e é o mesmo "ponto único" das métricas.
   - Base possível: o artefato `HiveDashboard` **já conta** submits/conexões/blocos/leilões/falhas
     (BattleStats) — exportar offline + usar o replay do server. Habilita **A/B e regressão de
     comportamento** entre mudanças (foi o que fizemos à mão no sweep do #2).

**Princípio de arquitetura derivado:** mover lógica de decisão não-trivial p/ internal actions/artefatos
Java (testáveis) e manter `.asl` como orquestração fina. Ex.: o scoring do líder (viés single-block) hoje
está em `squad_leader.asl` (difícil de unit-testar); em Java seria testável.

> **Follow-up do review do PR #5 (2026-06-17 — altitude).** O backfill do Track 1 testou o A* do
> `SharedMap` e o `SquadCoordinator` **in-place**, afrouxando visibilidade (`private`→package-private),
> de propósito para **não tocar na lógica do #2** (risco de regressão). O passo limpo é **extrair o A\*
> (toroidal + overlay de ocupação #2) do `SharedMap` para uma classe pura testável** — como já foi feito
> com `AdjacentDirection.direction` e `TaskBoard.bestBid` —, colapsando o acoplamento dos testes a campos
> internos (`obstacles`/`occupancy`/`gridWidth/Height`/`astar`). **Fazer com cuidado + boot** (toca
> navegação); os testes de caracterização atuais travam o comportamento e habilitam a extração segura.
> (O `SquadCoordinator.wrapDist` já foi deduplicado p/ `AdjacentDirection.wrapDelta` no próprio PR #5.)

## Parking lot — considerado, mas **não priorizado** (gated por evidência)

Ideias dos docs (ideação/brainstorms) **pensadas e conscientemente adiadas** — registradas para não
re-derivar. **Não são trabalho pronto:** quase todas são alavanca *indireta* de score (mexem em
movimento, não em submit) e os próprios docs as condicionam a evidência. Promover **só** se o
"experimento de alavanca" (acima) mostrar que vale. (Confiança das ideias na ideação: 60–82%.)

- **✅ LANDOU (issue #27, 2026-06-19) — sair de beco (U-shape) "não ficar preso, SAIR".** Promoção
  do ex-parking-lot "wall-follower horário". **Achados que reformataram a ideia (medir → mudar por
  evidência):**
  - **O gate do GPS dissolveu com o #15.** A ideia original era gateada "pelo GPS" (condição de parada).
    Mas o #15 (paredes percebidas → `obstacles`, confirmado por diagnóstico: obst=9-11) deu a condição
    de parada de graça — o A\* já roteia para fora quando há rota limpa.
  - **O wall-follower LITERAL (mão-direita) NÃO serve aqui:** **espirala** em pocket aberto (interior
    2-3 células) por não estar colado a uma parede — é algoritmo de corredor/labirinto. Provado por
    teste (girava no interior sem achar a boca).
  - **O `max_stuck` tem ponto cego:** o agente preso oscila entre células **livres** → zero `failed_path`
    → `max_stuck=0` enquanto está preso. Resolvido com **métricas de posição** (`exited_region`/
    `entered_region`, leem o ground-truth do replay) — ver `assert_metric.py`.
  - **Detecção de cul-de-sac robusta de todo ângulo = chokepoint/min-cut, difícil** (3 heurísticas
    testadas; cada uma vaza em algum ângulo). O dono **reframou**: o requisito é "**não ficar preso,
    sair**" (gatilho fácil: "estou preso?" por posição) e não "isto é um beco?" (difícil).
  - **Solução entregue:** detecção de **preso por bounding-box** (`isStuck`: posições recentes num
    quadrado ≤3×3) → **escape por ray-cast da abertura** (`get_escape_target`: a direção cardinal mais
    aberta = a boca) → A\* roteia para fora. Funciona de qualquer ângulo, pequeno ou grande, limitado
    pela visão. **Bônus:** filtro de cul-de-sac na seleção de fronteira (`isCulDeSacFrontier`, anel do
    agente) reduz a entrada (não robusto de todo ângulo — o escape é a garantia).
  - **Cercado por cenários (PASS):** `03b-obstacle-uhole` (começa dentro, sai), `03c-obstacle-uavoid`
    (entra e escapa, qualquer ângulo). Plano: [`2026-06-19-008-...`](plans/2026-06-19-008-feat-ushape-avoid-escape-plan.md).
  - **Resíduo p/ competição (não isolável barato):** o `failed_path` 40-60% no oficial 70×70 é o
    **greedy-fallback** do A\* (`astar` cai em `greedy()` cego quando `mDist>60`) + contenção
    multi-agente — fenômeno emergente, não o U isolado de 1 agente. Esse é o próximo alvo de navegação.
- **Navegação — resíduos do livelock.** #7 custo de congestão/anti-aglomeração no A*; #8 ceder por
  prioridade determinística + jitter; #5 / B8 reserva de célula/footprint (pathfinding cooperativo);
  B1a-em-A* / B1b (o A* hoje penaliza só a célula central — footprint próprio só no escape; footprint
  dos colegas exige infra nova); B2/B3 escape graduado ciente do bloco (rotate como manobra, detach
  parcial, re-attach); B4 detach por custo (ou barato: baixar o limiar fixo de 50→~20); B6/B7 yield
  por carga. **Gate:** só se os **detaches forçados de carregador** seguirem altos depois do #2
  (= movimento ainda custando submits).
- **Navegação — dispersão + handedness consistente (ideia do dono, 2026-06-18).** O boot da Fase C
  em isolamento mostrou **pinning severo** (agentes presos em 1-3 células por 300 steps; `failed_path` ~50%).
  Duas técnicas, com **resultado divergente após testes**:
  (a) **heading balanceado** (setor por agente) — **testado, delta neutro** (2026-06-18). Não reduz
  `failed_path`. Dispersão geográfica **não é** a causa do livelock → **descartada** como fix primário.
  (b) **handedness consistente no escape (sentido horário)** — **não testado isoladamente**. Pode ainda
  contribuir para quebra de simetria em colisões 2-a-2 sem depender de dispersão geográfica. Gate:
  testar handedness-only no escape e medir `failed_path` antes de promover. Se delta positivo →
  promover ao P1 de navegação. Se neutro → parking lot permanente.
- **Artefato GPS — roteamento sobre qualquer mapa (ideia do dono, 2026-06-18; → ideação/brainstorm).**
  Um artefato CArtAgO **GPS** que **cria e recalcula rotas** sobre QUALQUER mapa (o `map_<nome>` individual,
  o de um agente específico, ou o fundido da U9), **minimizando o nº de steps até o destino considerando
  obstáculos**. Centraliza/extrai o A\* (hoje embutido no `SharedMap`) — alinhado ao follow-up do PR #5
  (extrair o A\* p/ classe pura testável) e habilitado pela U9 (rotear sobre o mapa fundido).
  **Tradeoffs em aberto, a decidir por ideação/brainstorm (o dono levantou; há mais ideias):**
  - **Ocupação viva na rota?** enxergar colegas/adversários ao rotear (hoje: overlay #2 com `PENALTY=16`) —
    manter, ajustar, ou substituir por footprint?
  - **Handedness** — desviar de agente **sempre no mesmo sentido (horário)** (quebra simetria; substitui o
    *jitter* aleatório do escape #3/#4).
  - **Footprint como custo** — penalizar o footprint (próprio e dos colegas) como **+steps no A\***, em vez de
    só reagir no escape; e **olhar além do footprint imediato** (look-ahead).
  - **Substituir o "locking" atual** (`PENALTY=16`, #2) por um custo de ocupação/footprint mais principiado.
  - **Qual o melhor tradeoff de navegação** é pergunta de pesquisa.
  **Dependências / o que já temos:** extração do A\* (PR #5 follow-up) **habilita** o GPS; **U9** fornece o
  mapa a rotear; **dispersão+handedness** (bullet acima) é a política de movimento que o GPS encapsula; o A\*
  do `SharedMap` (overlay #2 `PENALTY=16` + escape jitter #3/#4) é o **ponto de partida** a substituir.
  **Sequência sugerida:** ideação/brainstorm (fixar os tradeoffs) → extrair A\* → GPS → integrar
  footprint/handedness. **Score-growth lever medido** (o pinning é o bloqueio atual).
- **Dívida arquitetural — A\* aprende parede só por colisão.** Paredes percebidas vão p/ `cells`, que
  o A* não lê; ele só conhece obstáculo via `mark_obstacle` (colisão). Reframe de 2 camadas:
  `staticObstacles` (da percepção) + `liveOccupancy` (já existe, via #2). Latente; morde em
  mapas/densidades onde "aprender batendo" custa caro.
- **Track adversário / jogo ofensivo (ADV-0..ADV-5).** Usar o campo `Team` (ADV-0); oponente = custo
  alto, **contornar nunca esperar** (ADV-3/4); ceder só a colega (ADV-5); não presentear bloco ao
  inimigo / segurar-rotacionar perto de oponente (ADV-1/2); evitar marcadores de `clear` (`ci`/`cp`);
  disputa de chokepoint / negação de área. **Downstream:** só testável após adoção de role + run
  2-times no cenário oficial (ver seções acima).

> Origem: [`docs/ideation/2026-06-16-livelock-movimento-agentes-ideation.md`](ideation/2026-06-16-livelock-movimento-agentes-ideation.md)
> (#1–#8, B1–B8, ADV-0..ADV-5) e os brainstorms/planos do livelock. O que **landou** (#1, #4, #2
> PENALTY=16, escape como fallback, fix do EIS) está em [`docs/solutions/`](solutions/).
