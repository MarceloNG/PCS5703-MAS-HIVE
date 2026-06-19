---
name: HIVE
last_updated: 2026-06-17
---

# HIVE Strategy

## Target problem

Maximizar o score do time HIVE no cenário oficial da competição **MASSim / MAPC 2022
("Agents Assemble")** — coleta de blocos → montagem em padrões → submit de tarefas —
competindo contra os demais times da turma (PCS5703). É difícil porque (a) a programação
orientada a agentes torna o fluxo pouco linear: mexer em A reflete em B; e (b) há **alta
variância entre runs** (resultados não se repetem mesmo com seed), o que torna difícil saber
se uma mudança realmente moveu o score. O esforço também precisa gerar um **relatório em
formato de artigo científico** + código avaliável — derivados da própria implementação.

## Our approach

Dirigir tudo por **medição de score que controla a variância, mudando em isolamento e
promovendo só por evidência**. Como cada run é cara, lenta e ruidosa: (1) lógica de decisão
não-trivial migra para **Java testável** (internal actions/artefatos), deixando as `.asl` como
orquestração fina — domando a não-linearidade e permitindo unit test sem rodar a sim;
(2) quando rodamos, **métricas estruturadas** extraem o máximo sinal (múltiplas runs, A/B) — e
essas mesmas métricas viram a evidência do relatório (§5 Estratégia, §6 Características
técnicas); (3) itens do backlog só sobem de prioridade quando a medição mostra que movem o
score — exceto quando a evidência já é conclusiva e estática (ex.: ações de role na config). Rejeita
o modo antigo: empilhar heurística e julgar "no olho" numa única run.

## Who it's for

**Primário:** a equipe de desenvolvimento do HIVE (você + a disciplina PCS5703). Está
contratando o sistema para colocar em campo um time de agentes que **pontua de forma
competitiva no MAPC 2022 sem gastar semanas em mudanças que não movem o score** — dado
código não-linear e runs caras/ruidosas.

## Key metrics

- **Score (média ± dispersão sobre N runs)** — north star (lagging); a dispersão é o que torna
  o A/B confiável apesar da variância. Fonte: `results/*.json` do server.
- **Submits por run** — driver mais direto do score (leading). Fonte: `BattleStats`/`HiveDashboard`.
- **Razão coleta→submit** — blocos/tasks coletados que de fato viram submit; regride quando o
  time coleta solo e nunca submete. Fonte: `BattleStats`.
- **Penalidades por run** — multas de norma + desativações por clear-event; lado-custo.
  Fonte: `BattleStats` + log.

## Tracks

### 1. Medição & validação isolada (fundacional)

Infra que torna a abordagem possível: source-set JUnit (testar a lógica Java sem run), **ponto
único de instrumentação** (métrica nova barata), export do `BattleStats` e A/B com controle de
variância. Primeiro entregável: a **atribuição de deadline perdido — movimento vs estratégia**
(decide qual track de otimização atacar). Inclui **logs estruturados** (NDJSON por
step/agente/evento, estendendo o `dash_log` que já existe) no lugar de `.print` ad-hoc — facilita
debug e alimenta as métricas. Saídas estruturadas alimentam §5/§6 do relatório.

**Harness de execução & medição:** a skill **`run-hive`** (`.claude/skills/run-hive/`)
operacionaliza o "quando rodamos" — driver parametrizado por config
(`run-hive.sh run --conf <config> [--steps N] [--monitor]`) que builda o jar, lança
servidor+agentes (vencendo a janela de launch) e extrai o **score**, mais `analyzers/` que lêem o
**replay** (a verdade, não o log ruidoso) por foco — começando pela view geral (adoção de role,
histograma de ações, `failed_role`, submits). Princípio: **criar/evoluir um analyzer por track**
conforme a necessidade (navegação, submit, normas), e evoluir o driver sob demanda (sims em
paralelo, self-play HIVE×HIVE). É onde "rodar e medir" deste track vive.

_Por que serve à abordagem:_ é a própria abordagem — medir pra achar a alavanca, validar em
isolamento, gate por evidência; habilita os demais tracks. A medição *no alvo real* depende do
Track 3 (antes disso, mede-se no proxy 40×40).

**Hierarquia de configs de teste:** antes de lançar o run lento, valide na config rápida.

| Nível | Config | Tempo | Uso |
|---|---|---|---|
| 1 — isolação | `conf/IsolationRolesConfig.json` | ~3 min | 40×40, `absolutePosition:true`, seed=17 — isola variável de adoção/coleta; valida lógica ASL sem drift de dead-reckoning |
| 2 — gate de score | `conf/OfficialRolesConfig.json` | ~30 min | 70×70, roles reais, 300 steps — gate final para score competitivo |

**Princípio:** mudança na lógica de adoção ou coleta passa pelo nível 1 antes do nível 2. O nível 1
não substitui o 2 (`absolutePosition:true` elimina o drift que existe no oficial), mas filtra
regressões óbvias de ASL em ~10× menos tempo. Se o resultado no nível 2 não for bom, ajuste o
nível 1 para isolar a variável testada — sem criar dificuldades extras, focando no que está sendo
implementado.

### 2. Estratégia de tarefas (coleta → montagem → submit)

Corrigir como o time **escolhe e completa** tarefas: viés single-block no scoring do líder,
rotate-loop do submit, custo-benefício de normas — e, upstream, **entender as regras**: como
tasks surgem/expiram e por que leilões (avaliar manter vs remover).

_Por que serve à abordagem:_ é o gargalo de score suspeito (pós-livelock), a ser confirmado pela
medição do Track 1. Lógica é Java puro → desenvolvível e unit-testável já; validação end-to-end
no alvo real fica gated atrás do Track 3.

### 3. Cenário oficial & organização (MOISE+ / roles)

Tornar o HIVE rodável e competitivo na config oficial **70×70 / 750 steps** — assumida como a da
competição avaliada ("cenário e simulador de 2022", enunciado). **Pré-requisito de pontuar:** no
oficial o role inicial `default` não tem `request/attach/connect/submit`; sem ir a uma role-zone e
`adopt(worker|constructor)`, o time só anda → **score 0** (verificado em
`massim_2022/server/conf/sim/roles/standard.json`). O HIVE só pontua hoje porque a config de dev
(40×40) dá todas as ações ao `default`. Cobre também o **MOISE+** exigido pelo enunciado (vale
pela nota independe da config) e a escala p/ ~20 agentes + grid parametrizável.

_Por que serve à abordagem:_ é o portão para pontuar **e** medir no alvo real — sem ele, Tracks 1
e 2 só operam num proxy. Evidência já conclusiva (estática, na config), então não espera o
experimento de alavanca.

## Not working on (gated)

- **Resíduos de navegação** (custo de congestão no A*, ceder por prioridade, reserva de
  célula/footprint) — só promove se os detaches forçados de carregador seguirem altos após a medição.
- **Track adversário / jogo ofensivo** — downstream de adoção de role + run 2-times no cenário oficial.
