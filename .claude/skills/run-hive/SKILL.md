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

Subcomandos: `run` · `score` (mostra o `results/*.json` mais recente) · `analyze [replay] [args]` · `assert [args]` (PASS/FAIL de capacidade, ver abaixo) · `stop` (mata servidor+agentes desta máquina — por padrão de jar/launcher, nunca o teu shell).

Flags de `run`: `--conf F` | `--scenario NN-nome` (cenário controlado) · `--steps N` · `--port P` (sim paralela isolada) · `--assert` (PASS/FAIL do bloco `assert` do cenário ao fim; exit 0/1) · `--monitor`.

**Regressão de cenários (suíte "na mão"):** `.claude/skills/run-hive/regression.sh [nomes...]` roda **em série** todos os `conf/scenarios/*.json` com bloco `assert` (ou só os nomes passados) e imprime PASS/FAIL de cada um (exit !=0 se algum falhar). É **caro** (1 sim/cenário) — rode **antes de mexer em arquivo core** (`perception`/`navigation`/`role_adoption`) ou antes de mesclar, **não** toda vez. Cada cenário com `assert` funciona como um teste e2e da sua capacidade isolada.

## Cenários controlados (`conf/scenarios/`) — isolar e asseverar uma capacidade

Cenário determinístico = config MASSim + bloco `assert` (métrica de **capacidade**, não score). Convenção `conf/scenarios/NN-nome.json` (+ opcional `setup/NN-nome.txt`, injetado no campo `setup` por uma cópia temp — o original nunca é mutado). Detalhes e métricas: **`conf/scenarios/README.md`**.

```bash
# roda o cenário 00-smoke (resolve config + setup) e imprime PASS/FAIL; exit 0=PASS, 1=FAIL
.claude/skills/run-hive/run-hive.sh run --scenario 00-smoke --assert
# assere sobre um replay já existente (sem rodar a sim)
.claude/skills/run-hive/run-hive.sh assert --scenario-conf conf/scenarios/00-smoke.json
```

## Analyzers — escolha/evolua/crie por foco

A verdade está no replay. `analyzers/` começa com a view **geral**; **adicione irmãos focados** conforme o que você depura (nada se cria do zero, tudo se melhora):

```bash
# view geral: adoção de role (1º step=worker), histograma de ações/resultados, submits, score
.claude/skills/run-hive/analyzers/replay_analyze.py            # replay mais recente
.claude/skills/run-hive/analyzers/replay_analyze.py <replay_dir> --agent agentA4
.claude/skills/run-hive/analyzers/replay_analyze.py --json     # saída assertável
```

- `analyzers/replay_analyze.py` — **geral**: o sinal da Fase C (quantos viraram `worker` e quando), `failed_role`/`failed_path`, submits, score casado pelo id do replay.
- `analyzers/assert_metric.py` — **asserção de capacidade** (harness #11): PASS/FAIL de uma métrica plugável (`role_adoption`, `final_workers`, `submits_ok`, `max_stuck`) contra `min`/`max`/`equals`; lê o bloco `assert` do cenário ou flags diretas; reusa os loaders do geral. Honra `HIVE_REPLAY_ROOT`/`HIVE_RESULTS_ROOT` (sim isolada por porta).
- `analyzers/adoption.py` — **foco: ADOÇÃO DE ROLE** (Eixo 1 / issue #12). Detalha o sinal: quantos viram `worker`, quantos pelo path explorer-first (`default→explorer→worker`), **RE-adoções** (adopt-spam pós-sucesso; alvo 0), **adopts falhos por motivo** (`failed_location` fora da role-zone, `failed_parameter` role/path inválido) e time-to-first-adoption. Tem `--check` (gate: sai !=0 se < `--min-workers` ou > `--max-readopts`) p/ asseverar limiares. Validado sim-free pela fixture sintética `analyzers/fixtures/synthetic_adopt_A/` via `analyzers/test_adoption.py` (`python3 test_adoption.py`).

  ```bash
  .claude/skills/run-hive/analyzers/adoption.py <replay_dir> --json
  .claude/skills/run-hive/analyzers/adoption.py <replay_dir> --check --min-workers 10 --max-readopts 0
  ```
- `analyzers/submit_strategy.py` — **foco: ESTRATÉGIA DE SUBMIT** (#50/#52). Classifica rotações por localização: na zone (in-zone), no dispenser (U3 pré-alinhamento), em rota (on-route). Tem `--check` (gate: sai !=0 se rotações-na-zone > `--max-zone-rotations`) e `--json`. Teste sim-free: `python3 .claude/skills/run-hive/analyzers/test_submit_strategy.py`. **Não** roda via `gradle test` — invocação direta.

  ```bash
  .claude/skills/run-hive/analyzers/submit_strategy.py <replay_dir> --json
  .claude/skills/run-hive/analyzers/submit_strategy.py <replay_dir> --check --max-zone-rotations 0
  ```
- **A fazer conforme a necessidade** (convenção, ainda não criados): `analyzers/navigation.py` (livelock/stuck/oscilação), `analyzers/norms.py` (multas vs reward). (`submit_strategy.py` feito: #50/#52.) Cada track de trabalho pode pedir um analyzer próprio — **crie e melhore-os aqui**.

## Run (human path) — assistir ao vivo

```bash
# mesmo run, com o monitor web em http://localhost:8000/ (visualização do grid)
.claude/skills/run-hive/run-hive.sh run --conf conf/OfficialRolesConfig.json --monitor
```

Por padrão o driver roda **sem** monitor (headless: a verdade vem do replay/score). `--monitor` é só para um humano assistir — qualquer replay também pode ser revisto depois no replay-viewer do monitor. Para rodar **duas sims em paralelo** sem colidir, use `--port P` (isola porta, eismassimconfig, `results/`/`replays/`/`logs/` em `/tmp/hive-run-P/`); sem `--port` é **série** (default 12300 compartilhado).

## Gotchas (cicatrizes reais desta sessão)

- **`run_in_background` + redirect = log vazio.** Se você roda algo em background E redireciona `> arquivo`, o arquivo de output da task fica 0 byte (a saída foi para o seu redirect). O driver evita isso gerenciando os próprios logs; ao chamá-lo, **não** redirecione.
- **Sem agentes conectados → servidor roda vazio e sai com score 0.** O `gradle run` precisa subir dentro da janela `launch` (25s) da config. O driver pré-aquece `gradle classes` e só lança os agentes **depois** que a porta 12300 abre, justamente para vencer essa corrida.
- **O log não é confiável; o replay é.** O log dos agentes mistura buffer do gradle + ruído de shutdown (`Socket closed`, `Error receiving json object. Stop receiving.` — isso é o **fim normal**, não crash). Para saber o que aconteceu, **rode o analyzer no replay**.
- **`gradle run` deixa um daemon Gradle 9.x vivo** (extensão do VSCode) — não confunda com a sim. Identifique a sim por `server-2022-...jar` e `jacamo.infra.JaCaMoLauncher` (o que o `stop` mata).
- **Java compila mudança de `.java`, mas `.asl` só é exercitado no `gradle run`** (parse em runtime). Erro de parse de `.asl` → agentes não sobem → servidor vazio → score 0.

## Roadmap / evoluir (lembrar conforme a implementação cresce)

**Política (dono, 2026-06-18):** evoluir **sob demanda** — implementar cada item abaixo só quando um track de trabalho realmente precisar dele; não antecipar. Manter esta lista para não re-derivar.

"Tudo se melhora" — capacidades previstas (vamos precisar de todas), por custo crescente:

- ✅ **`--monitor`** — assistir ao vivo (feito).
- ✅ **Harness de cenários (`--scenario` + `--assert`)** — `conf/scenarios/NN-nome.json` + `setup/NN-nome.txt`, asserção de capacidade plugável (`assert_metric.py`). Feito (#11).
- ✅ **Sims em paralelo (`--port`)** — isola porta + eismassimconfig por-porta (via `-PeisConf` → `-Dhive.eis.conf` em `EISAccess`, sem mexer nos `.asl`) + `results/`/`replays/`/`logs/` por run. Feito (#11).
- ⏳ **Analyzers por foco** — criar irmãos em `analyzers/` conforme o track: `navigation.py` (livelock/stuck/oscilação), `norms.py` (multa vs reward). (`submit_strategy.py` feito: #50/#52.) A view geral já existe.
- ⏳ **HIVE vs HIVE (self-play, 2 times — "Brasil x Brasil")** — o MASSim **suporta nativamente** (é o formato do torneio: times A+B). Falta o nosso lado: (a) config 2-times (adaptar `massim_2022/server/conf/SampleConfig.json`, que já tem A+B/`teamsPerMatch:2`); (b) 2º set de agentes do time B — entidades eismassim `agentB*` + um launch JaCaMo do time B (o backlog planeja via worktree "time B: `agentB*`"). Habilita medir adversário/contenção real (track adversário, hoje deferido).

## Troubleshooting

| Sintoma | Causa / fix |
|---|---|
| `score 0` no oficial, agentes andam | role-adoption não fechou (rode o analyzer: `ADOÇÃO DE ROLE: x/15`). Sem adotar `worker`, `request`→`failed_role`. |
| `gradle classes` falha | erro de compilação Java em `src/java` ou `src/env` — corrija antes de bootar. |
| porta 12300 não abre | sim antiga órfã — `run-hive.sh stop`; ou jar não buildou (`mvn -f massim_2022/pom.xml package -DskipTests`). |
| analyzer: "nenhum dado de replay" | passou um replay em progresso/vazio; use um `*_A` finalizado ou rode uma sim. |
