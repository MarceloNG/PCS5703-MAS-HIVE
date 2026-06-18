# AGENTS.md — HIVE

Instruções para agentes de IA (e humanos novos) trabalhando neste repositório. Fonte única;
[CLAUDE.md](CLAUDE.md) apenas importa este arquivo (`@AGENTS.md`).

**HIVE** é um sistema multi-agente JaCaMo/Jason (Jason + CArtAgO + MOISE+) com 15 agentes BDI
para o **Multi-Agent Programming Contest 2022 — "Agents Assemble III"** (cenário MASSim). É um
exercício avaliado da disciplina **PCS5703** (EPUSP): a nota vem de relatório + código +
competição contra a turma. Visão geral completa em [README.md](README.md).

## Comece por aqui (os docs que governam o trabalho)

Leia **antes de propor ou implementar** qualquer mudança. Não re-derive o que já está aqui.

- **[STRATEGY.md](STRATEGY.md) — a âncora de prioridade.** Problema-alvo, abordagem
  (medir → mudar em isolamento → promover só por evidência), métricas (score/submits) e os
  **tracks** de trabalho. Se uma tarefa não serve a um track, questione antes de fazer.
- **[CONCEPTS.md](CONCEPTS.md) — o glossário do domínio.** Vocabulário com sentido específico do
  projeto (SharedMap, frame local, livelock, **role do cenário** vs **role organizacional**, etc.).
  Use estes termos com precisão; a confusão entre os dois tipos de "role" é o erro clássico aqui.
- **[docs/backlog.md](docs/backlog.md) — o que fazer e em que ordem.** Itens de trabalho com a
  tabela de **Prioridades (revisão vs spec)**, o que já landou vs WIP, o parking lot (gated por
  evidência) e o sequenciamento (Fase C → medir → fusão de mapas U9).

Contexto de apoio: `docs/plans/` (planos por feature), `docs/brainstorms/`, `docs/ideation/`,
`docs/solutions/` (aprendizados passados — consulte antes de re-resolver um problema). A spec
oficial e o enunciado estão em `local/` (gitignored).

## Build, test, run

| Ação | Comando |
|---|---|
| **Testes unitários (rápido, sem sim)** | `~/tools/gradle-8.10/bin/gradle test` |
| **Build do jar do servidor MASSim** | `mvn -f massim_2022/pom.xml package -DskipTests` |
| **Rodar a simulação + score + análise** | use a skill `run-hive` (abaixo) |

- **Toolchain:** Java 21, Maven, Python 3, **Gradle 8.10 local** em
  `~/tools/gradle-8.10/bin/gradle` (não há `gradlew` funcional). O jar do servidor **não é
  commitado** — é buildado no 1º uso.
- **Rodar a sim é caro e ruidoso** (minutos a dezenas de minutos; alta variância entre runs).
  Prefira **testes JUnit** para validar lógica Java (≈1380 linhas testáveis em ms). Só rode a sim
  para end-to-end/score. → ver [STRATEGY.md](STRATEGY.md) §Tracks.1 e
  [docs/backlog.md](docs/backlog.md) §Estratégia de testes.
- **A verdade de um run NÃO está no log** (buffer/ruído) — está no **replay**
  (`massim_2022/server/replays/`) e no **score** (`massim_2022/server/results/*.json`).

### Skill `run-hive` (o jeito de rodar/medir)

Driver único parametrizado por config + analyzers de replay. Detalhes e gotchas em
[.claude/skills/run-hive/SKILL.md](.claude/skills/run-hive/SKILL.md).

```bash
# build (se preciso) + servidor + 15 agentes + espera + score + análise
.claude/skills/run-hive/run-hive.sh run --conf conf/OfficialRolesConfig.json
# smoke rápido (sobrescreve os steps)
.claude/skills/run-hive/run-hive.sh run --conf conf/OfficialRolesConfig.json --steps 15
```

`conf/OfficialRolesConfig.json` = roles **reais** (gate de score / Fase C; sem adoção de
`worker` → **score 0**). `conf/OfficialTestConfig.json` = default permissivo (dev, já pontua).

## Mapa do repositório

- `src/agt/` — agentes Jason (`.asl`): `squad_leader`, `collector`, `assembler`, `sentinel`;
  `src/agt/common/` para planos compartilhados (ex.: `role_adoption.asl`).
- `src/java/hive/` — lógica Java pura testável (internal actions, scoring, A*, frame).
- `src/env/` — artefatos CArtAgO (SharedMap, TaskBoard, HiveDashboard, conexão eismassim).
- `src/org/` — organização MOISE+ (`hive_org.xml`).
- `src/test/java/` — JUnit (lógica pura, sem sim).
- `conf/` — configs do servidor MASSim (ver tabela na SKILL do run-hive).
- `hive.jcm` — descritor JaCaMo (ponto de entrada do `gradle run`).
- `massim_2022/` — servidor MASSim (submódulo de build; `replays/`, `results/`).
- `dashboard/` — dashboard React/Three.js em tempo real (WebSocket :8765).

## Convenções

- **Idioma:** docs e comentários em **PT-BR** (acompanhe o repo). Identificadores em código
  seguem o estilo já presente no arquivo.
- **Arquitetura derivada:** lógica de decisão não-trivial mora em **Java testável**
  (internal actions/artefatos); `.asl` é **orquestração fina**. Ao adicionar lógica, prefira
  Java + um teste JUnit a engordar a `.asl`.
- **Dois "roles" distintos** (ver [CONCEPTS.md](CONCEPTS.md)): role do **cenário** (MAPC, gateia
  ações no simulador, aditivo sobre `default`) ≠ role **organizacional** (MOISE+, estrutura de
  time). Não os misture.
- **Mude em isolamento e promova por evidência.** Não empilhe heurística julgando "no olho" numa
  única run — é o anti-padrão que [STRATEGY.md](STRATEGY.md) rejeita.
- **Reuso de prior art** (MAPC 2022, LI(A)RA, times 2022): **cite a referência e melhore**, nunca
  copie verbatim.

## Gotchas (cicatrizes reais)

- **`.java` compila no build, mas `.asl` só é exercitado no `gradle run`** (parse em runtime).
  Erro de parse em `.asl` → agentes não sobem → servidor vazio → **score 0**.
- **Sem agentes conectados → score 0.** Os agentes precisam subir dentro da janela `launch` da
  config; o driver `run-hive` vence essa corrida.
- **Não rode múltiplas sims na mesma máquina ao mesmo tempo** (porta 12300, `results/`,
  `replays/` são compartilhados) — rode **em série**.
- **`run_in_background` + redirect de stdout = log vazio.** Ao chamar o driver, **não**
  redirecione (ele já gerencia os próprios logs).
