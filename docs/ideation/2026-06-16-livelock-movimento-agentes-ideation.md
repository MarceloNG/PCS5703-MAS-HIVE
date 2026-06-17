---
date: 2026-06-16
topic: livelock-movimento-agentes
focus: "evitar o livelock físico de movimento dos agentes (aglomeração / paredes)"
mode: repo-grounded
---

# Ideation: Evitar o livelock de movimento dos agentes

## Como esta versão foi produzida

Várias passadas, mescladas e deduplicadas:
1. **Passada ancorada:** ideação inline do orquestrador.
2. **Passada cega — movimento (#1-#8):** 5 subagentes independentes (1 falhou), cada um com uma lente diferente, **sem acesso a este doc** nem uns aos outros — só o diagnóstico como base.
3. **Passada cega — dimensão do bloco (B1-B8):** 4 subagentes cegos focados em footprint/rotate/detach (ver "Dimensão do bloco").
4. **Passada cega — dimensão adversária (ADV-0..ADV-5):** 3 subagentes cegos focados na competição entre times (ver "Dimensão adversária").

A **convergência** (entre passadas e entre subagentes cegos de uma mesma passada, marcada em cada ideia) é o principal sinal de qualidade: ideias que vários subagentes independentes encontraram, sem se ver, são as de maior confiança.

## Grounding Context (Codebase)

Diagnóstico confirmado (ce-debug): os agentes entram em **livelock** de movimento (agem, mas não progridem) quando se aglomeram ou ficam perto de paredes. Aprofundamento: o lock persiste **menos por bloqueio físico** (transitório) e **mais porque o agente "não enxerga os movimentos livres reais"** — o modelo de navegação está cego aos colegas e poluído com obstáculos-fantasma.

- O pathfinding A\* (`compute_next_move` em [SharedMap.java:262](../../src/env/env/SharedMap.java#L262)) monta `blocked` só de `obstacles.keySet()` ([SharedMap.java:283](../../src/env/env/SharedMap.java#L283)) e **ignora as posições vivas dos colegas** → roteia um agente através do outro.
- `move` falho por colega ocupando a célula (MASSim → `failed_path`) → [perception.asl:154](../../src/agt/common/perception.asl#L154) chama `mark_obstacle` → obstáculo **estático** no mapa **compartilhado** que persiste **~30 steps** ([SharedMap.java:258](../../src/env/env/SharedMap.java#L258)).
- Quando bloqueado, o agente só tenta **uma** direção perpendicular fixa ([navigation.asl:81-95](../../src/agt/common/navigation.asl#L81-L95)), sem enumerar vizinhos livres.
- A detecção de stuck ([perception.asl:20-40](../../src/agt/common/perception.asl#L20-L40)) exige **mesma célula exata** por ≥50 steps → é **cega à oscilação A↔B** (o agente muda de célula mas não progride). Disparou 85× tarde; ~180 oscilações no run.

Recursos: cada agente percebe `thing(RelX,RelY,entity,Time)` na visão 5; `SquadCoordinator` já mantém `agentPositions` (nome→[x,y]) e tem padrão de claim atômico (`claim_task_soloist`); `SharedMap` tem `obstacles` (decay), `visitedCells`, A\*.

## Topic Axes

- Evitar a colisão (não mover para célula ocupada)
- Não poluir o mapa (não marcar obstáculo falso de agente)
- Resolução de conflito (quem cede / quebra de simetria)
- Pathfinding ciente de agentes
- Dispersão / anti-aglomeração

## Ideias finais (mescladas, deduplicadas, rankeadas)

> **Status (2026-06-16, pós-revisão + decisões do dono):** prioridade **reestruturada como sequência incremental** (ver "Recomendação final"). **Decisões tomadas:** (1) **não pré-comprometer** — fazer **só o passo 1 (#1+B5)** e **medir** stuck/oscilação; escalar pro Java só por evidência. (2) **dimensão adversária FORA do núcleo** — vira track próprio junto com "jogar ofensivo". Núcleo (só colegas): **#1+B5 + medir → #4 → (se evidência) enabler+#2+B1a**. **Adiados:** B1b, B2/B3, B4, #5-#8, B6-B8 e **toda a dimensão adversária (ADV-\*)**. **Não** levar o bundle inteiro ao `/ce-brainstorm` de uma vez.

### 1. Não marcar obstáculo por falha de movimento (obstáculo só vem da percepção real)
**Description:** No handler de `failed_path`, não chamar `mark_obstacle` (o bloqueio quase sempre é um colega transitório). Obstáculo estático só quando a visão percebe `thing(_,_,obstacle,_)`.
**Axis:** Não poluir o mapa
**Basis:** `direct:` perception.asl:146-157 + SharedMap.java:258 (decay ~30 steps).
**Rationale:** Mata os fantasmas na origem — a causa direta de o lock persistir muito além do bloqueio físico real.
**Convergência:** 5/5 cegos + 1ª passada. **Downsides:** ⚠️ **descoberto na implementação (2026-06-16):** o A\* só conhece obstáculo por **colisão** — `obstacles` é populado **só** por `mark_obstacle`; a percepção de parede vai p/ `cells`, que o A\* **não lê** (SharedMap.java:283/326 leem só `obstacles`). Logo, só remover o `mark_obstacle` rotearia **através das paredes**. O fix real só marca quando a célula-alvo **não** é `entity` (agente); fazer paredes virem da percepção é a Opção 2 (Java, parte do reframe). **Confidence:** 88% · **Complexity:** Low · **Status:** ✅ FEITO + MEDIDO (Opção .asl — `perception.asl:146-160`; medido em 200 steps / 1 run: **570 fantasmas de colega evitados = 36% das marcações de obstáculo**)

### 2. Ocupação viva dos colegas no A\* (overlay efêmero por step)
**Description:** O `compute_next_move` recebe as posições vivas dos colegas (de `agentPositions`) como células bloqueadas **só naquele cálculo** (sem virar obstáculo persistente, sem decay), exceto a própria e a célula-alvo. O agente passa a rotear **em volta** dos colegas.
**Axis:** Pathfinding ciente de agentes
**Basis:** `direct:` SharedMap.java:283 (`blocked` só de obstáculos); SquadCoordinator.java:217 (`agentPositions` já existe). Requer o enabler abaixo.
**Rationale:** Ataca a causa-raiz nº1 (A\* cego aos colegas) reusando dado já coletado, sem criar fantasmas.
**Convergência:** 5/5 cegos + 1ª passada (alavanca-mãe). **Downsides:** acoplar posições vivas ao SharedMap; posições ~1 step desatualizadas. **Confidence:** 85% · **Complexity:** Medium · **Status:** Unexplored

### 3. Enumerar vizinhos livres reais (escape hatch) em vez de uma perpendicular
**Description:** Operação `get_free_neighbors(X,Y)` no SharedMap que retorna as direções dos 4 vizinhos não-obstáculo e não-ocupados-por-colega; ao bloquear, o agente vai para o vizinho livre que mais aproxima do destino (bypassando o mapa poluído com ground-truth).
**Axis:** Resolução de conflito / Ver os movimentos
**Basis:** `direct:` navigation.asl:81-95 (hoje tenta só 1 perpendicular); a visão já entrega os vizinhos.
**Rationale:** Cura diretamente o "não enxerga os movimentos livres" — sempre acha saída se ela existir.
**Convergência:** 5/5 cegos + 1ª passada. **Downsides:** heurística local (1-2 passos subótimos). **Confidence:** 80% · **Complexity:** Medium · **Status:** Unexplored

### 4. Detecção precoce de oscilação A↔B (o stuck atual é cego a ela)
**Description:** Manter histórico curto das últimas posições (ou lista tabu); detectar ping-pong A↔B em ~4 amostras e reagir cedo (replanejar via #3 / abandonar destino), em vez de esperar os 50 steps de mesma-célula-exata.
**Axis:** Resolução de conflito
**Basis:** `direct:` perception.asl:20-40 — `check_stuck` exige `SX==X & SY==Y`, então **oscilação (que muda de célula) NUNCA dispara** o detector. Achado novo da passada cega.
**Rationale:** O modo de falha mais frequente (as ~180 oscilações) passa hoje 100% despercebido pelo detector de 50 steps.
**Convergência:** 4/5 cegos (novo na passada cega; ausente na ancorada). **Downsides:** heurística; rastrear histórico. **Confidence:** 78% · **Complexity:** Low-Medium · **Status:** ✅ FEITO + MEDIDO (só-log — `perception.asl` `check_osc`/`osc_shift`; medido: **157 oscilações / 200 steps ≈ 20× o `[STUCK]`=8**)

### 5. Reserva de célula por step (move-claim: 1 agente por célula)
**Description:** Antes de `move(Dir)`, o agente reserva a célula-alvo (`claim_cell(X,Y,step)`); se um colega já reservou para o mesmo step, escolhe outra direção livre. Reservas expiram a cada step.
**Axis:** Evitar a colisão
**Basis:** `external:` cooperative pathfinding / reservation (Silver, WHCA*); `direct:` SquadCoordinator já tem claim atômico (`claim_task_soloist`, `computeIfAbsent`). *(Versão leve de 1 step — corrige a rejeição apressada da 1ª passada, que descartou a tabela global pesada.)*
**Rationale:** Previne a colisão de cabeça na origem, eliminando o `failed_path` que gera os fantasmas.
**Convergência:** 5/5 cegos (nova). **Downsides:** lógica extra + estado central por step. **Confidence:** 72% · **Complexity:** Medium · **Status:** Unexplored

### 6. Checagem pré-movimento de ocupação (ideia semente do usuário)
**Description:** Antes de `move(Dir)`, checar se a célula-alvo tem um colega percebido (`thing(_,_,entity,_)`); se tiver, não move pra lá. Versão local e leve de #2/#5.
**Axis:** Evitar a colisão
**Basis:** `reasoned:`+`direct:` — o agente já percebe `entity`; basta consultar antes de agir.
**Rationale:** A peça central da intuição do usuário; cobre o caso comum com custo mínimo (sobreposta por #3/#5 nos casos difíceis).
**Convergência:** 1ª passada (#1) + implícita nas cegas. **Downsides:** só vê agentes na visão. **Confidence:** 78% · **Complexity:** Low · **Status:** Unexplored

### 7. Custo de congestão / repulsão anti-aglomeração no A\*
**Description:** Somar ao custo do A\* um termo por densidade de colegas próximos (de `agentPositions`/visão), para as rotas contornarem regiões lotadas; o time se auto-distribui.
**Axis:** Dispersão / anti-aglomeração
**Basis:** `external:` campos de potencial (Khatib 1986) / flow fields; `direct:` SharedMap.java usa custo aditivo (`ng = cg + 1`).
**Rationale:** Efeito de 2ª ordem — previne a formação dos aglomerados, não só resolve a colisão.
**Convergência:** 4/5 cegos + 1ª passada. **Downsides:** mexe na função de custo do A\* (Java, risco/complexidade). **Confidence:** 60% · **Complexity:** Medium-High · **Status:** Unexplored

### 8. Ceder passagem por prioridade determinística + jitter
**Description:** Em contenção, o agente de menor prioridade (nome/hash; quem não carrega bloco) cede/espera 1 step; um pequeno jitter quebra simetria.
**Axis:** Resolução de conflito
**Basis:** `reasoned:` MAPF / pare-de-4-vias / CSMA backoff; nomes dão ordem total determinística (seed 17 reproduzível).
**Rationale:** Resolve o deadlock simétrico que #6 sozinha não cobre.
**Convergência:** 3/5 cegos + 1ª passada (#3). **Downsides:** mais lógica AgentSpeak. **Confidence:** 68% · **Complexity:** Medium · **Status:** Unexplored

## Enabler & reframe arquitetural (suportam as ideias acima)

- **Enabler — PEÇA-CHAVE arquitetural (NÃO "baixo risco", revisão feasibility P1):** dar ao `SharedMap` acesso às posições vivas dos colegas. Hoje `agentPositions` vive no `SquadCoordinator` (artefato CArtAgO **separado, sem memória compartilhada** com o `SharedMap`). #2/#3/B1 dependem disso. **Opção recomendada:** espelhar uma camada `liveOccupancy` no próprio `SharedMap`, alimentada pelo `update_agent_pos` que `perception.asl:46` **já chama a cada step** (materializa o reframe das duas camadas). Complexidade real: **Medium**; é a **primeira decisão arquitetural a fechar** — mas como **spike barato ANTES do passo 3** (medir o custo por-step do A\* com o overlay na fast config e decidir o formato do `liveOccupancy`), não como rodapé nem item solto do passo 3. A `@OPERATION` do SharedMap é o caminho mais serializado do projeto.
- **Reframe — duas camadas:** `staticObstacles` (paredes, só percepção, duradouro) vs `liveOccupancy` (colegas, reconstruída por step, efêmera). O A\* soma as duas só no cálculo. Unifica #1+#2 e elimina a tentativa de fazer um `obstacles` único servir a dois tempos de vida incompatíveis (a origem do "fantasma de ~30 steps").

## Dimensão do bloco: footprint / rotate / detach (2ª rodada cega)

As ideias #1-#8 tratam o agente como **uma célula**. Mas o agente pode **carregar blocos anexados**, e a mecânica MASSim muda o problema (confirmado em `massim_2022/docs/scenario.md`): a estrutura (agente + anexos) **move/gira como corpo rígido**; o `move` falha (`failed_path`) se **qualquer** célula-alvo do footprint bloquear; o `rotate(cw/ccw)` reorienta o footprint (cada anexo precisa de célula final livre); o `detach` larga um bloco. 4 subagentes cegos focados nessa dimensão, mesclados:

### B1a. Validar o FOOTPRINT PRÓPRIO no escape/A\* (barato, dado disponível)
Ao escolher move/escape, validar a célula-alvo de **cada** bloco anexado do **próprio** agente (não só a célula central). O agente já percebe seus `attached(X,Y)`. **Estende #2/#3.** **Convergência:** 2/4. **Confidence:** 82% · **Complexity:** Low-Medium · **Status:** núcleo (passo 3)

### B1b. Overlay do FOOTPRINT DOS COLEGAS — exige INFRA NOVA (ADIADO)
Bloquear/penalizar no A\* o footprint inteiro dos colegas (não 1 célula). **Não há fonte de dados hoje** (achado da revisão, feasibility P1): `SquadCoordinator.agentPositions` guarda só a célula central, e cada agente percebe `attached()` só de **si**. Exige infra nova — cada agente publicando seu footprint absoluto por step e o A\* lendo isso. Mesmo o enabler (`liveOccupancy`) só espelha a **célula central** de cada colega — não resolve B1b. **NÃO é "estender #2".** **Confidence:** ~55% · **Complexity:** Medium-High · **Status:** ADIADO (só se #2 célula-central não bastar)

### B2. Escape GRADUADO ciente do bloco, disparado pela detecção de oscilação (#4)
Ao travar/oscilar carregando, escalonar do barato ao caro: **(a)** vizinho livre para o footprint inteiro → mover; **(b)** senão `rotate(cw/ccw)` para reorientar o footprint (**preserva o bloco** — "manobra do sofá"); **(c)** senão `detach` **parcial** (só o bloco que colide); **(d)** registrar `dropped_block` para re-`attach` barato (1 step vs re-coletar). **Estende #3/#4 e substitui o detach-aos-50-steps.** Basis `direct:` navigation.asl:65-77 hoje só faz detach (perde o bloco); scenario: rotate/detach. **Convergência:** 3/4 (peça-chave). **Confidence:** 80% · **Complexity:** Medium

### B3. `rotate` como manobra de navegação (não só de montagem)
Girar o footprint para encaixar/abrir a direção do destino quando o move reto falha; direção (cw/ccw) escolhida **geometricamente** (pré-validar as células finais dos anexos). Núcleo do passo (b) de B2. Basis `direct:` scenario.md (rotate); navigation.asl nunca usa rotate para navegar. **Convergência:** 4/4 (5 variações). **Confidence:** 78% · **Complexity:** Medium

### B4. `detach` consciente do custo (gatilho custo-benefício, não 50 fixo)
Largar quando `steps_travado × penalidade_deadline > custo_de_recoleta` (dispenser perto + bloco abundante ⇒ solta cedo); detach **defensivo** perto do deadline ou antes de uma `deactivated` levar TODOS os anexos. **Substitui o limiar fixo de 50** (perception.asl:22). **Convergência:** 1/4 (detach-agent). **Confidence:** 75% · **Complexity:** Low-Medium

### B5. Não marcar fantasma carregando
Em `failed_path` com bloco, **não** marcar nenhuma das 2 células-alvo (perception.asl:154-157 hoje marca ambas; é ambíguo qual bloqueou ⇒ falso-positivo provável). **Estende #1.** **Convergência:** footprint-agent. **Confidence:** 85% · **Complexity:** Low

### B6. Prioridade/yield por carga (carregado > leve)
Em contenção, o agente **leve** cede ao **carregado** (caro de remanobrar); empate por nome. **Estende #8** com a assimetria de carga. **Convergência:** coordination-agent. **Confidence:** 70% · **Complexity:** Medium

### B7. Carregador evita zonas apertadas (custo + despacho)
Custo suave alto para carregadores em células de pouca folga ("caminhão não entra em viela"); e `find_free_soloist` manda o **leve** para a zona apertada, não o carregado. **Estende #7**; preventivo na origem. **Convergência:** coordination-agent. **Confidence:** 62% · **Complexity:** Medium-High

### B8. Reserva de footprint + reserva temporal (célula, step)
Reservar o footprint inteiro e a célula do **próximo** step; conflito ⇒ maior footprint ganha o "verde", o outro faz `skip`. **Estende #5.** **Convergência:** 2/4. **Confidence:** 68% · **Complexity:** Medium

## Dimensão adversária (3ª rodada cega)

As ideias acima (e o código atual) tratam só o PRÓPRIO time. Mas são **dois times competindo**: o agente percebe `thing(X,Y,entity,Team)` com o time. O `+thing` **vincula** o campo do time (como `Details`) e o repassa ao `update_cell` — mas **nenhuma camada faz a comparação amigo/inimigo**; A\*, navegação e checagens ignoram o time. Bloco largado (`detach`) é pegável por qualquer um, inclusive adversário. 3 subagentes cegos, mesclados:

### ADV-0. Usar o campo `Team` para distinguir amigo/inimigo (enabler de custo ~zero)
O campo do time **já é percebido e vinculado** pelo `+thing` (como `Details`); o que falta é **comparar** o time de cada `entity` percebida com o próprio time (`+team(T)`, hoje só usado p/ `.print`) e **propagá-lo** às camadas de navegação/overlay. Destrava todas as ADV abaixo sem dado novo. **Convergência:** 3/3 · **Complexity:** Low

### ADV-1. Detach que NÃO presenteia o inimigo
Antes de `detach(Dir)`, checar se há `entity` adversária adjacente à célula onde o bloco cai; largar longe de oponentes (de preferência perto de colega que recolha); só largar em direção "contaminada" se for a única saída. **Sharpening de B4/B2.** **Convergência:** 3/3 · **Complexity:** Low-Medium

### ADV-2. Perto de oponente: rotacionar/segurar em vez de largar
Bloco anexado a você **não** é pegável pelo inimigo (`attach` → `failed_blocked`). Com oponente por perto, preferir `rotate`/segurar (escape de B2/B3) a `detach`. **Integra B2/B3** com motivo extra: negar valor ao inimigo. **Complexity:** integra ao escape graduado

### ADV-3. Oponente como obstáculo (overlay local, decay curto) — NUNCA fantasma estático
Oponentes só aparecem na percepção local (visão 5). Registrá-los num `opponentCells` com decay curto (~3-5 steps, pois andam) como **custo alto (NÃO bloqueio)** no A\* — consistente com #2 e o passo 3 (bloqueio pode fechar o A\* numa aglomeração de oponentes e PIORAR o lock); a evitação dura (não-pisar) fica na **guarda `.asl` barata do ADV-4**, não no `blocked` do A\*. **Não** marcá-los como `obstacle` permanente. **Assimetria-chave:** colega = bloqueio "negociável" (esperar resolve); oponente = "hostil" (nunca cede → contornar, não esperar). **Estende #1/#3 via `Team`.** **Convergência:** 3/3 · **Complexity:** Medium

### ADV-4. Checagem de oponente no pré-movimento / vizinho-livre
A checagem local de #3/#6 conta célula com `entity` adversária como ocupada (via `Team`). Guarda barata em `.asl`, sem tocar o A\*. **Complexity:** Low

### ADV-5. Ceder passagem (yield) só para COLEGAS, nunca para oponente
A lógica de ceder/recuar (B6, #8) dispara só quando quem está na frente é colega; contra oponente, manter a célula/contornar (recuar = presentear espaço). **Refina B6/#8.** **Convergência:** 2/3 · **Complexity:** Low-Medium

### Estratégicas / ofensivas (FORA DO ESCOPO deste doc — além do livelock de movimento)

> **Fora de escopo deste documento:** estes itens — e também o **ADV-1** (*detach que não presenteia o inimigo*) — são **ofensiva competitiva**, não livelock de movimento. Ficam registrados, mas pertencem a um doc próprio de comportamento competitivo; **não entram no núcleo** deste.
- **Evitar marcadores de `clear` event** (`ci`/`cp`) como zona temporária intransponível — entrar nelas desativa o agente (o "presente" mais caro ao inimigo). *(novo; vale tratar à parte)*
- **Contestar chokepoint por ratio de força local** (minoria → rota alternativa via `get_alternative_goal_zone`; maioria → insistir).
- **Bloqueio legal / bloco-isca / preferir goal zone menos disputada** — negação de área; é estratégia ofensiva, não movimento.

## Recomendação final — prioridade SEQUENCIADA (pós-revisão ce-doc-review)

**Premissa a validar primeiro (revisão P0):** o doc assume que consertar o livelock ⇒ mais score, mas isso **não foi provado e já foi refutado** neste projeto (o fix do EIS deu 22× menos timeouts e o score caiu 70→50, variância). Só `submit` pontua. **Passo 0 obrigatório:** medir, nos runs/replays existentes, **quantos submits se perdem por travamento** (tasks com bloco coletado que expiram com o agente travado a caminho da goal zone). **Atenção:** o sinal `need_detach` (produzido em `perception.asl:31`, consumido em `navigation.asl:41-77`) **NÃO** mede isso diretamente — só dispara após **50 steps na mesma célula** carregando p/ submit e é **cego à oscilação**; é proxy grosseiro e tardio. O passo 0 precisa de **instrumentação nova** correlacionando expiração de deadline com `stuck`/oscilação. Se for baixo → o livelock é elegância de movimento, não resultado, e a alavanca real é estratégia. Se for alto → a sequência abaixo se justifica.

> **DECISÃO (2026-06-16):** não pré-comprometer com a instrumentação pesada de "submits perdidos". Fazer **primeiro o passo 1 (#1+B5)** — ~4 linhas, zero Java, conserta um bug real de qualquer forma — e **medir stuck/oscilação** (≥3 runs p/ variância). Escalar pro Java (enabler+#2) **só** se o stuck cair **e** se observar carregadores chegando mais à goal zone. A medição fina de submits-perdidos por replay fica como gate **antes do Java**, não antes do passo 1. **Hipótese que distingue do EIS:** o bug do EIS era *não enviar* ação (perda de tempo de relógio); o livelock é *enviar ação que não progride* (perda potencial de submits quando o carregador não chega à goal zone a tempo) — classes diferentes; só a medição confirma se vira score.

**Métrica (revisão P1):** primário = queda de **stuck (era 85)** e **oscilações (era 180)** — baixo ruído, atribuível. **Atenção: 85/180 são de um único run (seed 17) — provisórios; re-medir como média±variância em ≥3 runs no passo 0 antes de tratá-los como baseline.** Score é **observacional** e medido em **≥3 runs** — um run só no seed 17 é estatisticamente cego (15 agentes concorrentes + `.random` não-semeado + `randomFail:1` ⇒ o seed reproduz o servidor, não o comportamento dos agentes). Medir a variância do baseline (≥3 runs) antes de creditar/culpar qualquer mudança.

**Sequência (do mais barato/seguro ao mais caro), validando entre cada passo:**

| Passo | Peça | Por quê / onde | Risco |
|---|---|---|---|
| 1 | **#1 + B5** (não marcar fantasma de agente) — ✅ **FEITO + MEDIDO** | `perception.asl:146-160`: só marca obstáculo se a célula-alvo **não** tem `entity` percebido. **Correção:** não era "só remover, zero Java" — o A\* só conhece parede por **colisão**, então remover tudo rotearia através de paredes. `.asl` puro, parseado OK. **Medido (200 steps, 1 run): 570 fantasmas evitados = 36% das marcações de obstáculo.** | baixíssimo |
| 1+ | Medir baseline stuck/oscilação (≥3 runs, p/ variância), antes/depois do passo 1 | fixa a faixa de variância e mede o efeito do passo 1 | nenhum |
| 2 | **#4** (detector de oscilação) — **só-log** — ✅ **FEITO + MEDIDO** | `perception.asl` `check_osc` (ping-pong A↔B, com destino). **Medido (200 steps): 157 oscilações ≈ 20× o `[STUCK]`=8** — confirma que oscilação é o modo de falha dominante e era invisível. | baixo |
| — | **Gate p/ o Java:** só seguir se o passo 1 baixar stuck **e** se ver carregadores chegando mais à goal zone | medição fina de submits-perdidos por replay aqui, não antes | nenhum |
| 3 | **Enabler + #2 (célula central) + B1a (footprint próprio)** | A\* para de rotear através dos colegas; overlay como **custo alto, NÃO bloqueio** (bloqueio pode fechar o A\* em aglomeração e PIORAR o lock). *Enabler decidido antes, via spike (ver "Enabler & reframe").* | médio (1º Java) |
| 3+ | **#3** (escape por vizinho livre) | substep do passo 3, acionado pelo detector #4; valida o footprint próprio (B1a) | baixo-médio |

**Resultado medido do passo 1 (2026-06-16, 200 steps, 1 run, instrumentado `[OBSMARK]`/`[OBSSKIP]`):** das **1585** tentativas de marcar obstáculo, **570 (36%) eram fantasmas de colega** — o passo 1 cortou todas, na origem. É **piso, não teto**: fantasmas em que o colega já saiu da célula no step seguinte escapam para as 1015 marcações "reais" (pegá-los exige o **#2**, consciência de colegas vivos no A\*). `[STUCK]`=10, submits=6, score=60 são **contexto** (1 run, sem baseline pareado — não atribuíveis ao passo 1). **Nota da config de teste:** roda **1 time só** (`teams per match: 1`) — sem adversário; reforça que adiar a dimensão adversária foi certo (nem é testável aqui).

**Resultado medido do #4 (2026-06-16, 200 steps, só-log):** **157 ping-pongs** detectados vs **8 `[STUCK]`** — oscilação é **~20× mais frequente** que o que o `check_stuck` (50-steps) enxergava; era o ponto cego dominante, agora mensurável. Confirma a hipótese do #4 e dá ao gate um alvo real. **Lição-bônus de variância:** este run deu **score 20 / 2 submits** vs **60 / 6** do run anterior do *mesmo* código de comportamento (o #4 é só-log, não muda nada) — exatamente o ruído que invalida score de 1 run.

**Dimensão adversária — FORA do núcleo (decisão 2026-06-16):** o núcleo acima trata **só colegas**. Toda a consciência de adversário (ADV-0..ADV-5) vira um **track próprio**, avaliado junto com a alavanca **direta** "jogar ofensivo" — não embutido no fix de movimento. Quando esse track abrir, o catálogo já está pronto acima: ADV-0 (usar o `Team`), ADV-3/4 (oponente = obstáculo de custo alto, **contornar — nunca esperar**), ADV-5 (ceder só a colegas), ADV-1/2 (não largar bloco onde o inimigo pega; segurar/rotacionar perto de oponente — bloco anexado é imune a roubo). Marcadores de `clear` event e disputa de chokepoint também moram nesse track.

**Adiados — fora do núcleo, acionar SÓ por evidência (não por completude):**
- **B1b** (footprint dos COLEGAS) — exige infra nova; não é "estender #2".
- **B2/B3** (rotate / detach parcial / re-attach) — peça mais cara, menos validada (Unexplored); risco de **nova oscilação** (rotate falha em aglomeração) e **perda de blocos em massa**. Só se #1-#4 deixarem o agente travando *carregando* E isso custar submits.
- **B4** (detach por custo) — convergência 1/4; alternativa barata: só **reduzir o limiar fixo** de 50→~20.
- **B6/B7/B8, #5-#8** — reserva.
- **Toda a dimensão adversária (ADV-0..ADV-5) + estratégias ofensivas** — movida para um **track próprio** (alavanca direta "jogar ofensivo"), fora deste fix de movimento (decisão 2026-06-16).

**Custo de oportunidade (revisão P1):** este bundle é alavanca **indireta** de score. Antes de investir, comparar com a alavanca **direta**: mais agentes submetendo / jogar ofensivo (seu instinto anterior) / reduzir tasks expiradas. **Cuidado:** o passo 0 mede só o potencial da alavanca **indireta** (submits perdidos por travamento) — responde "a indireta vale >0?", **não** ranqueia indireta vs direta. Para comparar de fato, é preciso um diagnóstico paralelo que estime o teto da **direta** (quantos agentes não submetem / quantas tasks expiram por outros motivos).

> Nota de processo: esta seção foi reescrita após revisão multi-persona (ce-doc-review) que apontou bundle inflado, premissa não-provada (e já refutada), rótulos de dificuldade incorretos (enabler e footprint-dos-colegas) e métrica ruidosa. O conteúdo original (#1-#8, B1-B8) permanece acima como catálogo de ideias; o que mudou foi a **priorização e a sequência**.

## Rejection Summary

| Idea | Reason Rejected |
|------|-----------------|
| Reservation table global (janela temporal completa, WHCA* cheia) | Caro/complexo no CArtAgO; a versão leve de 1 step (#5) entrega o essencial |
| Distribuir squads por região do mapa | Mudança de estratégia (escopo maior); dispersão fina de meeting-points cabe em #7 |
| Sem A\* em curta distância (greedy puro perto do destino) | Subsumida por #3 (free-neighbor) + #2; ganho marginal isolado |
| Decay curto/separado p/ obstáculo de falha | Band-aid; #1 (não marcar) torna desnecessário |

## Deferred / Open Questions

### From 2026-06-16 review (ce-doc-review, round 1)

- **[P1] Premissa (livelock → score):** ✅ **RESOLVIDO (2026-06-16):** não pré-comprometer — fazer **só o passo 1 (#1+B5)** e medir stuck/oscilação; escalar pro Java só por evidência (queda de stuck **e** carregadores chegando mais à goal zone). Hipótese que distingue do EIS registrada na "Recomendação final" (EIS = não enviar ação / perda de tempo; livelock = ação sem progresso / perda potencial de submits). *(adversarial, product-lens)*
- **[P1] Dimensão adversária no núcleo:** ✅ **RESOLVIDO (2026-06-16):** tirada do núcleo; vira **track próprio** junto com "jogar ofensivo" (alavanca direta). Catálogo ADV-0..ADV-5 preservado acima como referência para quando esse track abrir. *(scope-guardian, product-lens)*
- **[P2] Contagem de convergência da B1a ("2/4"):** inconsistente com "4 subagentes cegos" da dimensão do bloco — auditar contra os logs das passadas cegas (ou marcar como pós-deduplicação). Sem acesso aos logs originais, não dá p/ resolver com confiança. *(coherence)*
