---
name: run-hive
description: Build, launch, drive and score the HIVE MAPC 2022 multi-agent simulation. Use to run/boot/start a sim with a given server config (dev or official roles), measure the score, and analyze a replay (role adoption, actions, stuck, submits). Triggers — "run hive", "boot the sim", "rodar a simulação", "score", "analisar replay", "testar config", "Fase C boot".
---

# run-hive

HIVE é um time JaCaMo/Jason (MAPC 2022, cenário Agents Assemble III). Rodar = **2 processos**: o **servidor MASSim** (Java) + os **15 agentes BDI** (`gradle run` → `hive.jcm`). É **headless** (não há GUI para screenshot; há um monitor web opcional em :8000). A verdade de um run **não está no log** (buffer/ruído) — está no **replay** (`massim_2022/server/replays/`) e no **score** (`massim_2022/server/results/*.json`).

Tudo é dirigido por um único driver — **`.claude/skills/run-hive/run-hive.sh`** — e uma família de **analyzers** em `.claude/skills/run-hive/analyzers/`. (Paths relativos à raiz do repo.)

> Origem: melhora `~/repos/MAPC/scripts/{start-massim.sh,replay_analyze.py}` (cite & improve) — parametrizado por config, com build automático do jar e extração de score/análise.

## Prerequisites (já presentes neste ambiente)

- **Java 21**, **Maven** (`mvn`), **Python 3**.
- **Gradle 8.10 local** em `/home/mgrim/tools/gradle-8.10/bin/gradle` (não há `gradlew` funcional; override com `GRADLE_BIN=`).
- O jar do servidor **não é commitado** — o driver o builda no 1º uso (`mvn -f massim_2022/pom.xml package -DskipTests`).

## Run (agent path) — use isto

```bash
# build (se preciso) + launch servidor+agentes + espera o fim + score + análise:
.claude/skills/run-hive/run-hive.sh run --conf conf/OfficialRolesConfig.json

# smoke rápido (sobrescreve os steps da config — ~1-2 min em vez de ~30):
.claude/skills/run-hive/run-hive.sh run --conf conf/OfficialRolesConfig.json --steps 15
```

`run` é **bloqueante** (espera o servidor terminar). Um run cheio (300–800 steps) leva **minutos a dezenas de minutos** (gargalo CArtAgO ~1–6 s/step) → lance com **`run_in_background: true`** e **não** redirecione stdout (o driver já escreve `server.log`/`agents.log` em `/tmp/hive-run/`; redirecionar por cima esvazia o arquivo da task — gotcha real).

Configs (passar em `--conf`):

| Config | Roles | Uso |
|---|---|---|
| `conf/OfficialRolesConfig.json` | **reais** (default restrito, sem submit) | gate de score / Fase C — sem adoção de role dá **score 0** |
| `conf/OfficialTestConfig.json` | default permissivo (dev) | dev 70×70, default já pontua |
| `conf/FastTestConfig.json` | dev | dev rápido (100 steps) |
| `conf/TestConfig.json` | dev | dev longo (800 steps) |

Subcomandos: `run` · `score` (mostra o `results/*.json` mais recente) · `analyze [replay] [args]` · `stop` (mata servidor+agentes desta máquina — por padrão de jar/launcher, nunca o teu shell).

## Analyzers — escolha/evolua/crie por foco

A verdade está no replay. `analyzers/` começa com a view **geral**; **adicione irmãos focados** conforme o que você depura (nada se cria do zero, tudo se melhora):

```bash
# view geral: adoção de role (1º step=worker), histograma de ações/resultados, submits, score
.claude/skills/run-hive/analyzers/replay_analyze.py            # replay mais recente
.claude/skills/run-hive/analyzers/replay_analyze.py <replay_dir> --agent agentA4
.claude/skills/run-hive/analyzers/replay_analyze.py --json     # saída assertável
```

- `analyzers/replay_analyze.py` — **geral**: o sinal da Fase C (quantos viraram `worker` e quando), `failed_role`/`failed_path`, submits, score casado pelo id do replay.
- **A fazer conforme a necessidade** (convenção, ainda não criados): `analyzers/navigation.py` (livelock/stuck/oscilação), `analyzers/submit_strategy.py` (rotate-loop de submit, coleta-solo vs montagem), `analyzers/norms.py` (multas vs reward). Cada track de trabalho pode pedir um analyzer próprio — **crie e melhore-os aqui**.

## Run (human path)

Monitor web opcional: lançar o servidor com `--monitor` e abrir `http://localhost:8000/`. Inútil headless; o `run` do driver não usa.

## Gotchas (cicatrizes reais desta sessão)

- **`run_in_background` + redirect = log vazio.** Se você roda algo em background E redireciona `> arquivo`, o arquivo de output da task fica 0 byte (a saída foi para o seu redirect). O driver evita isso gerenciando os próprios logs; ao chamá-lo, **não** redirecione.
- **Sem agentes conectados → servidor roda vazio e sai com score 0.** O `gradle run` precisa subir dentro da janela `launch` (25s) da config. O driver pré-aquece `gradle classes` e só lança os agentes **depois** que a porta 12300 abre, justamente para vencer essa corrida.
- **O log não é confiável; o replay é.** O log dos agentes mistura buffer do gradle + ruído de shutdown (`Socket closed`, `Error receiving json object. Stop receiving.` — isso é o **fim normal**, não crash). Para saber o que aconteceu, **rode o analyzer no replay**.
- **`gradle run` deixa um daemon Gradle 9.x vivo** (extensão do VSCode) — não confunda com a sim. Identifique a sim por `server-2022-...jar` e `jacamo.infra.JaCaMoLauncher` (o que o `stop` mata).
- **Java compila mudança de `.java`, mas `.asl` só é exercitado no `gradle run`** (parse em runtime). Erro de parse de `.asl` → agentes não sobem → servidor vazio → score 0.

## Troubleshooting

| Sintoma | Causa / fix |
|---|---|
| `score 0` no oficial, agentes andam | role-adoption não fechou (rode o analyzer: `ADOÇÃO DE ROLE: x/15`). Sem adotar `worker`, `request`→`failed_role`. |
| `gradle classes` falha | erro de compilação Java em `src/java` ou `src/env` — corrija antes de bootar. |
| porta 12300 não abre | sim antiga órfã — `run-hive.sh stop`; ou jar não buildou (`mvn -f massim_2022/pom.xml package -DskipTests`). |
| analyzer: "nenhum dado de replay" | passou um replay em progresso/vazio; use um `*_A` finalizado ou rode uma sim. |
