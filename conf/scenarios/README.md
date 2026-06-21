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

| `metric`            | mede                                                         | melhor | params   |
|---------------------|--------------------------------------------------------------|--------|----------|
| `role_adoption`     | nº de agentes que adotaram `worker` (1º step)                | maior  | —        |
| `final_workers`     | nº de agentes com role final == `worker`                     | maior  | —        |
| `submits_ok`        | total de submits bem-sucedidos do time                       | maior  | —        |
| `max_stuck`         | pior corrida de `failed_path` (proxy de livelock)            | menor  | —        |
| `failed_path_total` | total de eventos `failed_path` no time                       | menor  | —        |
| `requests_ok`       | total de requests bem-sucedidos (fusão de mapa)              | maior  | —        |
| `deactivated_count` | total de desativações por clear-event (U10/#20)              | menor  | —        |
| `exited_region`     | último step com posição DENTRO da box (prova de escape)      | menor  | `region` |
| `entered_region`    | nº de agentes que entraram na box (prova de evitação)        | menor  | `region` |

As métricas de **posição** (`exited_region`/`entered_region`) leem o ground-truth do replay
e exigem o param `region: [x0,y0,x1,y1]` (box inclusiva, coords do servidor).

### `assert` em lista (gate com múltiplas métricas)

O bloco `assert` aceita **uma lista** de checagens — todas precisam passar:

```json
"assert": [
  { "metric": "deactivated_count", "max": 0 },
  { "metric": "max_stuck", "max": 5 }
]
```

## Rodar um cenário (com PASS/FAIL)

```bash
# resolve conf/scenarios/10-survival.json, injeta o setup, roda, e ao fim imprime PASS/FAIL
.claude/skills/run-hive/run-hive.sh run --scenario 10-survival --assert --port 12320 --monitor 8020
```

- O exit code é o veredito: **0 = PASS**, **1 = FAIL** (assertável em CI/scripts).
- Sem `--assert`, roda o cenário e mostra só score + análise geral do replay.
- Para asseverar sobre um replay já existente (sem rodar a sim):
  `.claude/skills/run-hive/run-hive.sh assert --scenario-conf conf/scenarios/10-survival.json`.

## Sim paralela (isolamento de porta/workdir)

Use `--port` para isolar porta, eismassimconfig, results, replays e logs por run:

```bash
# Cada worktree/issue usa sua própria porta (NN = número da issue)
.claude/skills/run-hive/run-hive.sh run --scenario 10-survival --port 12320 --monitor 8020 --assert
```

Com `--port P`, o workdir do run vira `/tmp/hive-run-P/`. Os agentes apontam para esse
eismassim via `-PeisConf` (build.gradle → `-Dhive.eis.conf`). O `stop` desse run é por PID.

## Receita determinística (ao criar um cenário novo)

Grid pequeno · `randomFail:0` · `randomSeed` fixo · `grid.instructions:[]` ·
`regulation.chance:0` · poucos steps · `absolutePosition:true` (sem drift de dead-reckoning)
· um `setup/NN-nome.txt` com a fixture. Para cenários de **sobrevivência**:
`events.chance:100` + `warning` suficiente + agentes espalhados = cobertura estatística alta
mesmo sem calibrar seed vs posição de eventos.
