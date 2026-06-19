#!/usr/bin/env bash
# run-hive.sh — driver de build/launch/drive das simulações HIVE (MAPC 2022).
#
# Melhora ~/repos/MAPC/scripts/start-massim.sh (cite & improve): parametrizado por
# --conf (roda QUALQUER config do simulador), faz o build do jar se faltar, lança
# servidor + agentes na ordem certa (evita a corrida da janela de launch), espera o
# fim, e ao final imprime o SCORE (results/*.json) e roda o analyzer de replay.
#
# Subcomandos:
#   run    [--conf F|--scenario NN-nome] [--steps N] [--port P] [--monitor [PORT]] [--assert]
#                                   build+launch+espera+score+analyze (bloqueante)
#   score                           score do results/*.json mais recente
#   analyze [replay] [args...]      roda analyzers/replay_analyze.py
#   assert [args...]                roda analyzers/assert_metric.py (PASS/FAIL de capacidade)
#   stop                            mata servidor+agentes desta máquina (não o teu shell)
#
# Configs conhecidas (passar em --conf):
#   conf/OfficialRolesConfig.json  roles REAIS (default restrito) — gate de score / Fase C
#   conf/OfficialTestConfig.json   default permissivo (dev), 70x70
#   conf/FastTestConfig.json       dev rápido, 100 steps
#   conf/TestConfig.json           dev longo, 800 steps
# Gotcha: rodar a config oficial SEM adoção de role => score 0 (default não submete).
#
# Cenários controlados (#11): conf/scenarios/NN-nome.json (+ opcional
#   conf/scenarios/setup/NN-nome.txt). `--scenario NN-nome` resolve o config, injeta o
#   setup no campo `setup` (cópia temp, sem mutar o original) e, com `--assert`, imprime
#   PASS/FAIL da métrica de capacidade declarada no bloco `assert` do cenário.
#   Como adicionar um cenário: conf/scenarios/README.md.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"            # .claude/skills/run-hive -> repo
MASSIM="$REPO/massim_2022"
SERVER_DIR="$MASSIM/server"
JAR="$SERVER_DIR/target/server-2022-1.1-jar-with-dependencies.jar"
GRADLE="${GRADLE_BIN:-/home/mgrim/tools/gradle-8.10/bin/gradle}"
PORT="${HIVE_PORT:-12300}"                      # default 12300; override por --port p/ sim paralela
LOGDIR="${HIVE_LOGDIR:-/tmp/hive-run}"
SCEN_DIR="$REPO/conf/scenarios"
# diretórios de saída do servidor (isolados por porta quando --port != 12300, ver setup_workdir)
RESULTS_DIR="$SERVER_DIR/results"
REPLAYS_DIR="$SERVER_DIR/replays"
SLOGS_DIR="$SERVER_DIR/logs"
EIS_CONF="$REPO/eismassimconfig.json"           # default; --port gera um por-porta
SERVER_LOG="$LOGDIR/server.log"
AGENT_LOG="$LOGDIR/agents.log"
SERVER_PIDF="$LOGDIR/server.pid"
AGENT_PIDF="$LOGDIR/agents.pid"
ANALYZER="$HERE/analyzers/replay_analyze.py"
ASSERTER="$HERE/analyzers/assert_metric.py"
mkdir -p "$LOGDIR"

log() { printf '[run-hive] %s\n' "$*" >&2; }

ensure_jar() {
  if [ ! -f "$JAR" ]; then
    log "jar do servidor ausente — buildando com Maven (mvn package -DskipTests)…"
    mvn -q -f "$MASSIM/pom.xml" package -DskipTests || { log "FALHA no build do jar"; exit 1; }
  fi
  [ -f "$JAR" ] || { log "jar ainda ausente após build: $JAR"; exit 1; }
}

# mata processos da sim DESTA máquina por padrão específico (jar/launcher) — nunca
# casa a linha de comando do próprio shell. CUIDADO: em sim paralela (--port), use
# stop_pids (PID-escopo); o pkill por padrão mataria as DUAS sims.
stop_sim() {
  pkill -f "server-2022-1.1-jar-with-dependencies.jar" 2>/dev/null && log "servidor parado" || true
  pkill -f "jacamo.infra.JaCaMoLauncher" 2>/dev/null && log "agentes parados" || true
  rm -f "$SERVER_PIDF" "$AGENT_PIDF"
}

# para apenas os PIDs deste run (server + árvore do gradle) — seguro p/ sim paralela.
stop_pids() {
  local spid apid
  spid="$(cat "$SERVER_PIDF" 2>/dev/null || true)"
  apid="$(cat "$AGENT_PIDF" 2>/dev/null || true)"
  [ -n "$apid" ] && { pkill -P "$apid" 2>/dev/null || true; kill "$apid" 2>/dev/null || true; }
  [ -n "$spid" ] && kill "$spid" 2>/dev/null || true
  rm -f "$SERVER_PIDF" "$AGENT_PIDF"
}

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null && exec 3>&- 3<&- ; }

# Patcha um config p/ um arquivo temporário aplicando overrides (não muta o original):
#   STEPS=N      sobrescreve steps de cada match
#   PORT=P       sobrescreve server.port
#   SETUP=path   injeta o campo setup (path absoluto) em cada match
#   RESULTS/REPLAYS/LOGS=dir  isola server.resultPath/replayPath/logPath (paths absolutos)
# Uso: patch_conf <src> <dst> [STEPS=..] [PORT=..] [SETUP=..] [RESULTS=..] [REPLAYS=..] [LOGS=..]
patch_conf() {
  local src="$1" dst="$2"; shift 2
  python3 - "$src" "$dst" "$@" <<'PY'
import json, sys
src, dst = sys.argv[1], sys.argv[2]
ov = {}
for kv in sys.argv[3:]:
    k, _, v = kv.partition("=")
    ov[k] = v
d = json.load(open(src))
srv = d.setdefault("server", {})
if "PORT" in ov:    srv["port"] = int(ov["PORT"])
if "RESULTS" in ov: srv["resultPath"] = ov["RESULTS"]
if "REPLAYS" in ov: srv["replayPath"] = ov["REPLAYS"]
if "LOGS" in ov:    srv["logPath"] = ov["LOGS"]
for m in d.get("match", []):
    if "STEPS" in ov: m["steps"] = int(ov["STEPS"])
    if "SETUP" in ov: m["setup"] = ov["SETUP"]
json.dump(d, open(dst, "w"), indent=2)
PY
}

cmd_score() {
  local r
  r="$(ls -t "$RESULTS_DIR"/*.json 2>/dev/null | head -1)"
  [ -n "$r" ] || { log "nenhum results/*.json em $RESULTS_DIR"; return 1; }
  log "score ($r):"; cat "$r"; echo
}

cmd_analyze() { python3 "$ANALYZER" "$@"; }
cmd_assert()  { python3 "$ASSERTER" "$@"; }

cmd_run() {
  local conf="$DEFAULT_CONF" steps="" monitor="" scenario="" do_assert="" port_override=""
  while [ $# -gt 0 ]; do case "$1" in
    --conf) conf="$2"; shift 2;;
    --scenario) scenario="$2"; shift 2;;
    --steps) steps="$2"; shift 2;;
    --port) port_override="$2"; shift 2;;       # isola porta+workdir p/ sim paralela
    --assert) do_assert="1"; shift;;            # ao fim, PASS/FAIL da métrica do cenário
    --monitor)                                  # monitor web (porta opcional; default 8000)
        if [ -n "${2:-}" ] && [[ "$2" =~ ^[0-9]+$ ]]; then
            monitor="--monitor $2"; shift 2
        else
            monitor="--monitor 8000"; shift
        fi;;
    *) log "arg desconhecido p/ run: $1"; exit 2;;
  esac; done

  # cenário nomeado -> resolve o config base (inline p/ que o exit propague limpo)
  local scen_conf=""
  if [ -n "$scenario" ]; then
    scen_conf="$SCEN_DIR/${scenario}.json"
    [ -f "$scen_conf" ] || { log "cenário não existe: $scen_conf (veja conf/scenarios/README.md)"; exit 1; }
    conf="$scen_conf"
  fi
  [ -f "$conf" ] || { log "config não existe: $conf"; exit 1; }
  conf="$(cd "$(dirname "$conf")" && pwd)/$(basename "$conf")"

  # --- isolamento de porta/workdir (sim paralela) ----------------------------
  local isolated="" eis_arg=""
  if [ -n "$port_override" ]; then
    PORT="$port_override"; isolated="1"
    LOGDIR="/tmp/hive-run-$PORT"
    SERVER_LOG="$LOGDIR/server.log"; AGENT_LOG="$LOGDIR/agents.log"
    SERVER_PIDF="$LOGDIR/server.pid"; AGENT_PIDF="$LOGDIR/agents.pid"
    RESULTS_DIR="$LOGDIR/results"; REPLAYS_DIR="$LOGDIR/replays"; SLOGS_DIR="$LOGDIR/logs"
    EIS_CONF="$LOGDIR/eismassimconfig.json"
    mkdir -p "$LOGDIR" "$RESULTS_DIR" "$REPLAYS_DIR" "$SLOGS_DIR"
    # eismassimconfig por-porta (top-level "port"; agentes apontam p/ ele via -PeisConf).
    python3 - "$REPO/eismassimconfig.json" "$EIS_CONF" "$PORT" <<'PY'
import json, sys
src, dst, port = sys.argv[1], sys.argv[2], int(sys.argv[3])
d = json.load(open(src)); d["port"] = port
json.dump(d, open(dst, "w"), indent=2)
PY
    eis_arg="-PeisConf=$EIS_CONF"
    log "ISOLAMENTO: porta=$PORT  workdir=$LOGDIR  eisConf=$EIS_CONF"
  fi

  ensure_jar
  if [ -n "$isolated" ]; then
    log "limpando ESTE run (PID-escopo, porta $PORT)…"; stop_pids; sleep 1
  else
    log "limpando sims antigas (porta $PORT)…"; stop_sim; sleep 1
  fi
  log "pré-aquecendo classes (gradle classes) p/ evitar a corrida da janela de launch…"
  "$GRADLE" -q classes >/dev/null 2>&1 || { log "FALHA gradle classes — provável erro de compilação"; exit 1; }

  # --- monta a config efetiva (cópia temp; nunca muta o original) ------------
  local patch_args=()
  [ -n "$steps" ] && patch_args+=(STEPS="$steps")
  [ -n "$isolated" ] && patch_args+=(PORT="$PORT" RESULTS="$RESULTS_DIR" REPLAYS="$REPLAYS_DIR" LOGS="$SLOGS_DIR")
  # setup file do cenário (path absoluto -> independe da cwd do servidor)
  if [ -n "$scenario" ]; then
    local setup_src="$SCEN_DIR/setup/${scenario}.txt"
    if [ -f "$setup_src" ]; then
      patch_args+=(SETUP="$(cd "$(dirname "$setup_src")" && pwd)/$(basename "$setup_src")")
      log "setup do cenário: $setup_src"
    fi
  fi
  if [ "${#patch_args[@]}" -gt 0 ]; then
    local eff="$LOGDIR/conf.effective.json"
    patch_conf "$conf" "$eff" "${patch_args[@]}"
    conf="$eff"
  fi
  log "config efetiva: $conf"

  # porta do monitor (para a mensagem de log)
  local mon_port=8000
  [[ "$monitor" =~ ([0-9]+)$ ]] && mon_port="${BASH_REMATCH[1]}"

  # baseline p/ detectar conclusão pelo ARTEFATO (novo result_*.json), não pela morte do
  # processo — com --monitor o servidor segue vivo servindo :$mon_port e nunca encerra.
  local res0; res0="$(ls -t "$RESULTS_DIR"/*.json 2>/dev/null | head -1)"
  : > "$SERVER_LOG"; : > "$AGENT_LOG"
  log "lançando servidor…"
  ( cd "$SERVER_DIR" && exec java -jar "$JAR" -conf "$conf" $monitor ) >"$SERVER_LOG" 2>&1 &
  local spid=$!; echo "$spid" > "$SERVER_PIDF"

  log "esperando porta $PORT abrir…"
  local i=0; until port_open; do sleep 1; i=$((i+1)); if [ $i -ge 60 ]; then log "servidor não abriu a porta"; kill "$spid" 2>/dev/null; exit 1; fi; done

  log "lançando 15 agentes (gradle run)…"
  ( cd "$REPO" && exec "$GRADLE" -q --console=plain $eis_arg run ) >"$AGENT_LOG" 2>&1 &
  local apid=$!; echo "$apid" > "$AGENT_PIDF"

  log "sim rodando — aguardando conclusão (novo result_*.json OU fim do processo). Logs: $SERVER_LOG / $AGENT_LOG"
  local waited=0 max_wait="${HIVE_MAX_WAIT:-1800}" res_now=""
  while :; do
    # 1) processo encerrou (caso sem --monitor) → terminou
    kill -0 "$spid" 2>/dev/null || { log "servidor encerrou (processo)."; break; }
    # 2) artefato novo apareceu (vale também com --monitor, que mantém o processo vivo)
    res_now="$(ls -t "$RESULTS_DIR"/*.json 2>/dev/null | head -1)"
    if [ -n "$res_now" ] && [ "$res_now" != "$res0" ]; then
      log "sim concluído — result: $(basename "$res_now") (o --monitor não pendura mais o driver)."; break
    fi
    sleep 2; waited=$((waited+2))
    [ "$waited" -ge "$max_wait" ] && { log "timeout ${max_wait}s aguardando conclusão — seguindo p/ análise."; break; }
  done
  kill "$apid" 2>/dev/null || true   # agentes (gradle) já cumpriram seu papel
  if [ -n "$monitor" ] && kill -0 "$spid" 2>/dev/null; then
    disown "$spid" 2>/dev/null || true   # mantém o monitor vivo após o driver sair (humano segue olhando)
    log "monitor segue em http://localhost:$mon_port (PID $spid). Encerre com: $0 stop"
  else
    if [ -n "$isolated" ]; then stop_pids; else stop_sim; fi
  fi
  echo; cmd_score

  if [ -d "$REPLAYS_DIR" ]; then
    echo; log "análise do replay mais recente:"
    HIVE_REPLAY_ROOT="$REPLAYS_DIR" HIVE_RESULTS_ROOT="$RESULTS_DIR" python3 "$ANALYZER" || true
  fi

  # --- asserção de métrica de capacidade (PASS/FAIL) -------------------------
  if [ -n "$do_assert" ]; then
    [ -n "$scen_conf" ] || { log "--assert requer --scenario (a métrica vem do bloco 'assert' do cenário)"; exit 2; }
    echo; log "asserção de capacidade (cenário $scenario):"
    HIVE_REPLAY_ROOT="$REPLAYS_DIR" python3 "$ASSERTER" --scenario-conf "$scen_conf"
    exit $?   # propaga o exit code do assert (0=PASS, 1=FAIL) como veredito do run
  fi
}

DEFAULT_CONF="$REPO/conf/OfficialRolesConfig.json"
sub="${1:-run}"; shift || true
case "$sub" in
  run)     cmd_run "$@";;
  score)   cmd_score;;
  analyze) cmd_analyze "$@";;
  assert)  cmd_assert "$@";;
  stop)    stop_sim;;
  *) log "uso: run-hive.sh {run|score|analyze|assert|stop} [--conf F|--scenario NN] [--steps N] [--port P] [--monitor [PORT]] [--assert]"; exit 2;;
esac
