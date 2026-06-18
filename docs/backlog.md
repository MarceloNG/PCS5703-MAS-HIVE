# HIVE — Backlog

Itens de trabalho futuros, **não priorizados** (a ordem aqui não implica prioridade) — **exceto** a
tabela em "Prioridades (revisão vs spec, 2026-06-18)" abaixo, fruto do cruzamento com o livro oficial
do MAPC 2022 e os arquivos de config.

## Status — o que já landou vs WIP (2026-06-18)

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

**🚧 Em andamento (WIP):**
- **Track 3 Fase C — adoção de role.** O **gate de score** no oficial: o role inicial `default` não tem
  `request`/`attach`/`submit`/`connect`. **Boot de validação (2026-06-18) mostrou que a adoção funciona em
  parte, mas "score>0 no oficial" era o critério ERRADO** (conflava Fase C com o pipeline inteiro, que está
  degradado por navegação 70×70 + cross-frame). Achados, reframe e sub-itens em
  **"Fase C — achados do boot (2026-06-18)"** abaixo. `role_adoption.asl` (`+step(N)` antes de `collection`)
  já existe; falta robustez (adopt-spam, alcance de role-zone, transição p/ coleta).

**Próximo, após a Fase C:** validar a adoção **em isolamento** (`conf/IsolationRolesConfig.json`) → medir →
promover a **fusão de mapas (U9)**, agora **confirmada** como bloqueio de multi-bloco no oficial.

## Prioridades (revisão vs spec, 2026-06-18)

Cruzamento de `docs/backlog.md` contra o **livro oficial MAPC 2022** (`local/978-3-031-38712-8.pdf`,
cap. "The MAPC 2022") + os arquivos de config bundled (`sim/sim1.json`, `sim/roles/standard.json`,
`sim/norms/standard.json`) + o exercício da disciplina (`local/5703_ex02_26.pdf`). Ranking fundamentado:

| # | Item | Fundamento (fonte) |
|---|------|--------------------|
| **P0 ✅** | **Fase C — adoção de `worker`** (gate por percept `role/1`): **PROVADA em isolamento (score 0→10, submits 0→3)**. Resta **U4 (elo MOISE+)** + portar ao oficial | Adotar→coletar→submeter→pontuar **fecha** (commits `4b6a2d8`/`b9aa684`). O bloqueio de score restante é **navegação (livelock)**, não adoção. |
| **P1 (score-growth)** | **Redesenho de navegação — artefato GPS + footprint + handedness** (→ **ideação/brainstorm**) | **Bloqueio de score MEDIDO pós-Fase-C** (pinning/livelock no isolamento). Substitui PENALTY=16/jitter; tradeoffs em aberto. Ver bullet "Artefato GPS" no parking lot. |
| **P0** | **Corrigir cardinalidades MOISE+ p/ ≥20 (e plano p/ 40)** | `hive_org.xml` soma `max` = 19 (4+8+4+3) → já não admite os 20 do Sim1 oficial. Edição barata, desbloqueante. |
| **P1** | **Viés single-block no scoring do líder (#1)** | Livro §2: single-block tem *"very small compensation"*; reward sobe com complexidade. Alta alavanca assim que a Fase C pontuar. |
| **P1** | **Experimento de alavanca / harness de métricas** | O próprio backlog condiciona tudo a isso; antecede U9 e promoção do parking lot. |
| **P2 ↑** | **U9 — fusão de mapas / cross-frame (#2)** | **Confirmado (boot 2026-06-18) como o bloqueio de multi-bloco no oficial:** dev que pontua é `absolutePosition:true`; oficial é `false` → `set_meeting_point`/`connect_request` sem origem comum falham. Continua gated atrás de provar a adoção, mas o gate agora tem **evidência dura**, não especulação. |
| **P3 ↓** | **Normas — REBAIXADO** (ver sub-item 2, reescrito) | Livro §4: times de topo **ignoraram normas e comeram a multa**; MMD venceu assim. Trocar "tratar normas genéricas" por: (a) checar se o detach da norma Carry não custa score; (b) decisão deliberada de "comer a multa". |
| **P3** | **Track adversário** | Downstream da Fase C + run 2-times oficial (correto). |

> **Prazo (exercício):** entrega de relatório+código consta como **20/06/2026**; competição vs. turma +
> arguição "a anunciar". Se o relatório já foi entregue, a alavanca de nota restante é a **competição +
> arguição** → reforça P0 (Fase C) como prioridade absoluta. Confirmar o estágio do prazo.

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

**Sub-itens concretos da Fase C (a fazer):**
1. **Gate de re-adoção robusto** — parar o adopt-spam (verificar o update de `my_role(worker)`; talvez gatear por
   resultado de ação/percept, não só por crença).
2. **Alcance de role-zone** — 4/15 é pouco; melhorar exploração/navegação até a role-zone (liga ao livelock de
   navegação no grid grande; `survey`/landmark são opções, mas o livro diz que `survey` quase não foi usado).
3. **Transição pós-adoção → coleta** — garantir que a atribuição do líder vira `request` no worker (a fase longa
   de adoção pode estar *starving* a task).

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
da **Fase C** (adoção de role → pontuar no oficial). Promover quando a Fase C estiver de pé e a medição
mostrar que partilha/montagem move o score.
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

- **Navegação — resíduos do livelock.** #7 custo de congestão/anti-aglomeração no A*; #8 ceder por
  prioridade determinística + jitter; #5 / B8 reserva de célula/footprint (pathfinding cooperativo);
  B1a-em-A* / B1b (o A* hoje penaliza só a célula central — footprint próprio só no escape; footprint
  dos colegas exige infra nova); B2/B3 escape graduado ciente do bloco (rotate como manobra, detach
  parcial, re-attach); B4 detach por custo (ou barato: baixar o limiar fixo de 50→~20); B6/B7 yield
  por carga. **Gate:** só se os **detaches forçados de carregador** seguirem altos depois do #2
  (= movimento ainda custando submits).
- **Navegação — dispersão + handedness consistente (ideia do dono, 2026-06-18; ELEVADO).** O boot da Fase C
  em isolamento mostrou **pinning severo** (agentes presos em 1-3 células por 300 steps; `failed_path` ~50%)
  — a exploração por *frontier* não dispersa sob congestão. Ideia do dono, duas técnicas comprovadas:
  (a) **heading balanceado** — cada agente recebe um setor/direção (n/s/l/o) e segue nela desviando, em vez
  de todos convergirem → ataca direto o "ficam na mesma área / não exploram";
  (b) **desvio com handedness consistente (sentido horário / regra da mão-direita)** — quebra a simetria do
  "dois agentes se encarando e dançando" (ambos giram pro mesmo lado → passam) e serve de
  **footprint-avoidance leve** (sem a infra de reserva de célula do #5/B8). Hoje o escape usa *jitter
  aleatório*, que não quebra simetria de forma estável. **Elevado** porque é o **bloqueio de score medido**
  (não mais alavanca indireta especulativa) — promover junto com a depuração de navegação/submit pós-Fase-C.
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
