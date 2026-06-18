# HIVE — Backlog

Itens de trabalho futuros, **não priorizados** (a ordem aqui não implica prioridade).

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
2. **Normas além de "Carry" + custo-benefício da multa.** Hoje só a norma de carry é tratada
   (`perception.asl` → `carry_limit`; detach de excesso). O `active_norm(Id,Start,End,Reqs,Fine)`
   guarda o período e a multa, mas: outros tipos de norma são **ignorados**; o período (Start/End)
   não é raciocinado proativamente; a multa nunca é **pesada contra o reward** (nunca decide
   "vale violar"). Tratar normas genéricas e fazer a conta multa-vs-ganho.
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

A config oficial (`massim_2022/server/conf/SampleConfig.json` → `sim/sim1.json`) é **70×70**, até
**20 agentes/time**, **750 steps**, e usa **roles dinâmicos**. Auditoria (2026-06-17):

1. **Grid parametrizável.** `set_grid_dimensions(40,40)` está hardcoded em `perception.asl:7`; o wrap
   toroidal usa módulo 40 → coordenadas erradas em 70×70. Parametrizar (config/percepção), não fixar.
2. **Adoção de role — BLOQUEADOR / gate.** O role inicial `default` só tem
   `[skip,move,rotate,adopt,detach,clear]` — **sem `request`/`attach`/`submit`/`connect`**. Para
   coletar/montar/submeter é preciso ir a uma **role-zone** e `adopt(worker|constructor)`. O HIVE
   **não tem lógica de adoção de role** → na config oficial o time **não pontua** (só anda). Requer:
   navegar à role-zone → `adopt` → só então coletar. **Sem isso, o teste oficial dá score 0.**
3. **Escala p/ 20 agentes/time.** O `entities` é o **máximo** de contas (dá pra rodar com menos — 15
   conectam, restantes ociosos). Para competir de igual, subir o squad para 20 e revisar a composição.

> **Ponto em aberto:** enquanto a adoção de role (item 2) não existir, rodar a SampleConfig oficial é
> inútil (agentes sem as ações de coleta). O self-play "de verdade" no cenário oficial depende disso.
> Alternativa runnable hoje: self-play 2-times na **nossa** config 40×40 (role `default` já tem todas
> as ações), com 2 instâncias HIVE (worktree time B: `agentB*`) — mostra competição/contenção real,
> mas não é o mapa oficial.

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
da **Fase C** (adoção de role → pontuar no oficial). Promover quando a Fase C estiver de pé e a medição
mostrar que partilha/montagem move o score.
Origem: [`docs/brainstorms/2026-06-17-fase-d-posicionamento-relativo-requirements.md`](brainstorms/2026-06-17-fase-d-posicionamento-relativo-requirements.md)
(Scope Boundaries / U9), que cita o **LI(A)RA** (time Jason, MAPC 2022 — `github.com/Liga-IA/liara-agents`,
`src/asl/synchronism.asl`) como referência *proven* da técnica.

## Estratégia de testes — validar comportamento isolado, sem rodar o cenário completo

**Dor recorrente:** validar qualquer mudança custou um run de sim (~3-4 min, headless, com gotchas de
porta/órfãos). Mas a maior parte do que validamos por sim é **Java puro** (≈1380 linhas: artefatos +
internal actions) → testável em ms **sem sim**. Hierarquia proposta:

1. **Unit tests (JUnit, sem sim) — maior alavancagem.** Adicionar test source-set + JUnit ao
   `build.gradle`. Testar a lógica pura:
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

- **Navegação — resíduos do livelock.** #7 custo de congestão/anti-aglomeração no A*; #8 ceder por
  prioridade determinística + jitter; #5 / B8 reserva de célula/footprint (pathfinding cooperativo);
  B1a-em-A* / B1b (o A* hoje penaliza só a célula central — footprint próprio só no escape; footprint
  dos colegas exige infra nova); B2/B3 escape graduado ciente do bloco (rotate como manobra, detach
  parcial, re-attach); B4 detach por custo (ou barato: baixar o limiar fixo de 50→~20); B6/B7 yield
  por carga. **Gate:** só se os **detaches forçados de carregador** seguirem altos depois do #2
  (= movimento ainda custando submits).
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
