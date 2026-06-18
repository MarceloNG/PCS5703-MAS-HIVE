#!/usr/bin/env bash
# run-hive.sh — driver de build/launch/drive das simulações HIVE (MAPC 2022).
#
# Melhora ~/repos/MAPC/scripts/start-massim.sh (cite & improve): parametrizado por
# --conf (roda QUALQUER config do simulador), faz o build do jar se faltar, lança
# servidor + agentes na ordem certa (evita a corrida da janela de launch), espera o
# fim, e ao final imprime o SCORE (results/*.json) e roda o analyzer de replay.
#
# Subcomandos:
#   run    [--conf F] [--steps N]   build+launch+espera+score+analyze (bloqueante)
#   score                           score do results/*.json mais recente
#   analyze [replay] [args...]      roda analyzers/replay_analyze.py
#   stop                            mata servidor+agentes desta máquina (não o teu shell)
#
# Configs conhecidas (passar em --conf):
#   conf/OfficialRolesConfig.json  roles REAIS (default restrito) — gate de score / Fase C
#   conf/OfficialTestConfig.json   default permissivo (dev), 70x70
#   conf/FastTestConfig.json       dev rápido, 100 steps
#   conf/TestConfig.json           dev longo, 800 steps
# Gotcha: rodar a config oficial SEM adoção de role => score 0 (default não submete).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"            # .claude/skills/run-hive -> repo
MASSIM="$REPO/massim_2022"
SERVER_DIR="$MASSIM/server"
JAR="$SERVER_DIR/target/server-2022-1.1-jar-with-dependencies.jar"
GRADLE="${GRADLE_BIN:-/home/mgrim/tools/gradle-8.10/bin/gradle}"
PORT=12300
LOGDIR="${HIVE_LOGDIR:-/tmp/hive-run}"
SERVER_LOG="$LOGDIR/server.log"
AGENT_LOG="$LOGDIR/agents.log"
SERVER_PIDF="$LOGDIR/server.pid"
AGENT_PIDF="$LOGDIR/agents.pid"
ANALYZER="$HERE/analyzers/replay_analyze.py"
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
# casa a linha de comando do próprio shell.
stop_sim() {
  pkill -f "server-2022-1.1-jar-with-dependencies.jar" 2>/dev/null && log "servidor parado" || true
  pkill -f "jacamo.infra.JaCaMoLauncher" 2>/dev/null && log "agentes parados" || true
  rm -f "$SERVER_PIDF" "$AGENT_PIDF"
}

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null && exec 3>&- 3<&- ; }

# aplica --steps patchando a config para um arquivo temporário
apply_steps() {
  local conf="$1" steps="$2"
  local out="$LOGDIR/conf.steps${steps}.json"
  python3 - "$conf" "$out" "$steps" <<'PY'
import json,sys
src,dst,steps=sys.argv[1],sys.argv[2],int(sys.argv[3])
d=json.load(open(src))
for m in d.get("match",[]): m["steps"]=steps
json.dump(d,open(dst,"w"),indent=2)
PY
  echo "$out"
}

cmd_score() {
  local r
  r="$(ls -t "$SERVER_DIR"/results/*.json 2>/dev/null | head -1)"
  [ -n "$r" ] || { log "nenhum results/*.json"; return 1; }
  log "score ($r):"; cat "$r"; echo
}

cmd_analyze() { python3 "$ANALYZER" "$@"; }

cmd_run() {
  local conf="$DEFAULT_CONF" steps=""
  while [ $# -gt 0 ]; do case "$1" in
    --conf) conf="$2"; shift 2;;
    --steps) steps="$2"; shift 2;;
    *) log "arg desconhecido p/ run: $1"; exit 2;;
  esac; done
  [ -f "$conf" ] || { log "config não existe: $conf"; exit 1; }
  ensure_jar
  log "limpando sims antigas (porta $PORT)…"; stop_sim; sleep 1
  log "pré-aquecendo classes (gradle classes) p/ evitar a corrida da janela de launch…"
  "$GRADLE" -q classes >/dev/null 2>&1 || { log "FALHA gradle classes — provável erro de compilação"; exit 1; }

  if [ -n "$steps" ]; then conf="$(apply_steps "$conf" "$steps")"; log "steps sobrescrito → $steps ($conf)"; fi
  log "config: $conf"

  : > "$SERVER_LOG"; : > "$AGENT_LOG"
  log "lançando servidor…"
  ( cd "$SERVER_DIR" && exec java -jar "$JAR" -conf "$conf" ) >"$SERVER_LOG" 2>&1 &
  local spid=$!; echo "$spid" > "$SERVER_PIDF"

  log "esperando porta $PORT abrir…"
  local i=0; until port_open; do sleep 1; i=$((i+1)); if [ $i -ge 60 ]; then log "servidor não abriu a porta"; kill "$spid" 2>/dev/null; exit 1; fi; done

  log "lançando 15 agentes (gradle run)…"
  ( cd "$REPO" && exec "$GRADLE" -q --console=plain run ) >"$AGENT_LOG" 2>&1 &
  local apid=$!; echo "$apid" > "$AGENT_PIDF"

  log "sim rodando — aguardando o servidor terminar (PID $spid). Logs: $SERVER_LOG / $AGENT_LOG"
  wait "$spid" 2>/dev/null || true
  log "servidor encerrou. Parando agentes…"; kill "$apid" 2>/dev/null || true; stop_sim
  echo; cmd_score
  if [ -d "$SERVER_DIR/replays" ]; then echo; log "análise do replay mais recente:"; cmd_analyze || true; fi
}

DEFAULT_CONF="$REPO/conf/OfficialRolesConfig.json"
sub="${1:-run}"; shift || true
case "$sub" in
  run)     cmd_run "$@";;
  score)   cmd_score;;
  analyze) cmd_analyze "$@";;
  stop)    stop_sim;;
  *) log "uso: run-hive.sh {run|score|analyze|stop} [--conf F] [--steps N]"; exit 2;;
esac
