# `conf/scenarios/` — cenários controlados (harness #11)

Cenários **determinísticos** para isolar e **asseverar uma capacidade** (não score) do time,
rodados pelo driver `run-hive`. Servem ao princípio do projeto: *medir → mudar em isolamento →
promover por evidência* (ver [STRATEGY.md](../../STRATEGY.md)).

## Convenção de nome

```
conf/scenarios/NN-nome.json          # config do servidor MASSim (+ bloco "assert")
conf/scenarios/setup/NN-nome.txt     # OPCIONAL: fixture executada antes do step 1
```

- `NN` = índice de 2 dígitos (`00`, `01`, …); `nome` = slug curto do que o cenário isola.
- O `.json` é uma config MASSim normal (mesmo schema de `conf/*.json`) + um bloco **`assert`**.
- Se existir `setup/NN-nome.txt`, o `run-hive` injeta seu caminho (absoluto) no campo `setup`
  de cada `match` de uma **cópia temporária** do config — o `.json` original nunca é mutado.

## Bloco `assert` (métrica de capacidade, plugável por cenário)

```json
"assert": { "metric": "role_adoption", "min": 1 }
```

`metric` escolhe a função de medição sobre o **replay** (a verdade do run, não o log).
Combine com um ou mais limiares: `min`, `max`, `equals`. Métricas disponíveis
(em [`.claude/skills/run-hive/analyzers/assert_metric.py`](../../.claude/skills/run-hive/analyzers/assert_metric.py)
— adicione irmãs lá conforme novos cenários precisarem):

| `metric`        | mede                                              | melhor |
|-----------------|---------------------------------------------------|--------|
| `role_adoption` | nº de agentes que adotaram `worker` (1º step)     | maior  |
| `final_workers` | nº de agentes com role final == `worker`          | maior  |
| `submits_ok`    | total de submits bem-sucedidos do time            | maior  |
| `max_stuck`     | pior corrida de `failed_path` (proxy de livelock) | menor  |

## Rodar um cenário (com PASS/FAIL)

```bash
# resolve conf/scenarios/00-smoke.json, injeta o setup, roda, e ao fim imprime PASS/FAIL
.claude/skills/run-hive/run-hive.sh run --scenario 00-smoke --assert
```

- O exit code do comando é o veredito: **0 = PASS**, **1 = FAIL** (assertável em CI/scripts).
- Sem `--assert`, roda o cenário e mostra só score + análise geral do replay.
- Para asseverar sobre um replay já existente, sem rodar a sim:
  `.claude/skills/run-hive/run-hive.sh assert --scenario-conf conf/scenarios/00-smoke.json`.

## Sim paralela (isolamento de porta/workdir)

Duas sims na mesma máquina colidiam (porta 12300 + `results/`/`replays/` compartilhados).
Use `--port` para isolar **porta, eismassimconfig, results, replays e logs** por run:

```bash
# terminal 1
.claude/skills/run-hive/run-hive.sh run --scenario 00-smoke --port 12300 --assert
# terminal 2 (em paralelo, sem colidir)
.claude/skills/run-hive/run-hive.sh run --scenario 01-adopt --port 12400 --assert
```

Com `--port P`, o workdir do run vira `/tmp/hive-run-P/` (server.log, results, replays, logs,
e um `eismassimconfig.json` por-porta). Os agentes apontam para esse eismassim via
`-PeisConf` (build.gradle → `-Dhive.eis.conf`, lido em `EISAccess.init`) — **sem mexer nos
`.asl`**. O `stop` desse run é por PID (não mata a outra sim em paralelo).

> O `--port` foi a peça que destravou sims paralelas (era roadmap do run-hive, hoje feito).

## Receita determinística (ao criar um cenário novo)

Grid pequeno · `randomFail:0` · `randomSeed` fixo · `grid.instructions:[]` ·
`events.chance:0` · `regulation.chance:0` · poucos steps · `absolutePosition:true` (sem drift
de dead-reckoning) · um `setup/NN-nome.txt` com a fixture. Veja `00-smoke.json` como esqueleto.
