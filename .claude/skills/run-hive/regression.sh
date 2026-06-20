#!/usr/bin/env bash
# ============================================================================
# regression.sh — suíte de regressão de CENÁRIOS (gate de capacidade).
#
# Roda EM SÉRIE todos os conf/scenarios/*.json que têm bloco "assert" e reporta
# PASS/FAIL de cada um. Sai !=0 se qualquer um falhar.
#
# "Na mão": é CARO (1 sim por cenário, ~1-2 min cada) — NÃO precisa rodar toda
# vez; rode antes de mexer em arquivo core (perception/navigation/role_adoption)
# ou antes de mesclar, p/ pegar regressão. Cada cenário é um "teste" da sua
# capacidade isolada (adoção, submit, ...).
#
# Uso:
#   .claude/skills/run-hive/regression.sh              # todos os cenários com assert
#   .claude/skills/run-hive/regression.sh 01-adopt 06-single-block   # subconjunto
#   .claude/skills/run-hive/regression.sh --nn 01      # porta 12301, monitor 8001
#   .claude/skills/run-hive/regression.sh --nn 01 06-single-block   # subconjunto + monitor
# ============================================================================
set -uo pipefail

REPO="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$REPO"
DRIVER=".claude/skills/run-hive/run-hive.sh"
SCEN_DIR="conf/scenarios"

# --- Parsing de flags (separar --nn NN dos nomes de cenários) ---
nn=""
scene_args=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --nn)
      nn="${2:-}"
      if [[ ! "$nn" =~ ^[0-9]{2}$ ]]; then
        echo "ERRO: --nn requer exatamente 2 dígitos (ex.: --nn 01). Recebido: '$nn'" >&2
        exit 2
      fi
      shift 2
      ;;
    *) scene_args+=("$1"); shift;;
  esac
done
set -- "${scene_args[@]+"${scene_args[@]}"}"

# Flags extras para o driver quando --nn é fornecido
extra_flags=()
if [ -n "$nn" ]; then
  extra_flags=(--port "123${nn}" --monitor "80${nn}")
  echo "  [--nn ${nn}] porta sim=123${nn}  monitor=http://localhost:80${nn}/"
fi

# Quais cenários: args explícitos, ou todos os *.json com bloco "assert".
if [ "$#" -gt 0 ]; then
  names=("$@")
else
  names=()
  for f in "$SCEN_DIR"/*.json; do
    n="$(basename "$f" .json)"
    if python3 -c "import json,sys; sys.exit(0 if 'assert' in json.load(open('$f')) else 1)" 2>/dev/null; then
      names+=("$n")
    fi
  done
fi

echo "═══════════════════════════════════════════════════"
echo "REGRESSÃO de cenários (série): ${names[*]}"
echo "═══════════════════════════════════════════════════"

pass=0; fail=0; failed=()
for n in "${names[@]}"; do
  echo ""; echo "──── cenário: $n ────"
  if "$DRIVER" run --scenario "$n" --assert "${extra_flags[@]+"${extra_flags[@]}"}"; then
    echo "  ✓ [$n] PASS"; pass=$((pass+1))
  else
    echo "  ✗ [$n] FAIL"; fail=$((fail+1)); failed+=("$n")
  fi
done

echo ""; echo "═══════════════════════════════════════════════════"
echo "RESULTADO: $pass PASS · $fail FAIL  (de $((pass+fail)) cenários)"
[ "$fail" -eq 0 ] && { echo "✓ Sem regressão."; exit 0; } || { echo "✗ Falharam: ${failed[*]}"; exit 1; }
