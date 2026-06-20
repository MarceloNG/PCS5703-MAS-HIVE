#!/usr/bin/env bash
# ============================================================================
# regression.sh — suíte de regressão de CENÁRIOS (gate de capacidade).
#
# Roda EM SÉRIE todos os conf/scenarios/*.json que têm bloco "assert" e reporta
# PASS/FAIL de cada um. Sai !=0 se qualquer um falhar.
#
# "Na mão": é CARO (1 sim por cenário por repeat, ~1-2 min cada) — NÃO precisa
# rodar toda vez; rode antes de mexer em arquivo core (perception/navigation/
# role_adoption) ou antes de mesclar, p/ pegar regressão. Cada cenário é um
# "teste" da sua capacidade isolada (adoção, submit, ...).
#
# Uso:
#   .claude/skills/run-hive/regression.sh              # todos os cenários com assert
#   .claude/skills/run-hive/regression.sh 01-adopt 06-single-block   # subconjunto
#   .claude/skills/run-hive/regression.sh --nn 01      # porta 12301, monitor 8001
#   .claude/skills/run-hive/regression.sh --repeat 5   # 5 runs/cenário, todos devem passar
#   .claude/skills/run-hive/regression.sh --repeat 5 --min-pass 4   # tolera 1 falha
#   .claude/skills/run-hive/regression.sh --nn 01 --repeat 3 06c-single-collect
# ============================================================================
set -uo pipefail

REPO="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$REPO"
DRIVER=".claude/skills/run-hive/run-hive.sh"
SCEN_DIR="conf/scenarios"

# --- Parsing de flags (separar --nn/--repeat/--min-pass dos nomes de cenários) ---
nn=""
repeat=""
min_pass=""
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
    --repeat)
      repeat="${2:-}"
      if [[ ! "$repeat" =~ ^[0-9]+$ ]] || [ "$repeat" -lt 1 ]; then
        echo "ERRO: --repeat requer inteiro ≥ 1. Recebido: '$repeat'" >&2
        exit 2
      fi
      shift 2
      ;;
    --min-pass)
      min_pass="${2:-}"
      if [[ ! "$min_pass" =~ ^[0-9]+$ ]] || [ "$min_pass" -lt 1 ]; then
        echo "ERRO: --min-pass requer inteiro ≥ 1. Recebido: '$min_pass'" >&2
        exit 2
      fi
      shift 2
      ;;
    *) scene_args+=("$1"); shift;;
  esac
done
set -- "${scene_args[@]+"${scene_args[@]}"}"

# --- Validação cruzada e defaults ---
if [ -n "$min_pass" ] && [ -z "$repeat" ]; then
  echo "ERRO: --min-pass requer --repeat." >&2
  exit 2
fi
repeat="${repeat:-1}"
min_pass="${min_pass:-$repeat}"
if [ "$min_pass" -gt "$repeat" ]; then
  echo "ERRO: --min-pass ($min_pass) maior que --repeat ($repeat)." >&2
  exit 2
fi

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
[ "$repeat" -gt 1 ] && echo "  [gate de flakiness: repeat=$repeat, min-pass=$min_pass]"
echo "═══════════════════════════════════════════════════"

pass=0; fail=0; skip=0; failed=(); skipped=()
for n in "${names[@]}"; do
  echo ""; echo "──── cenário: $n ────"
  conf_file="$SCEN_DIR/${n}.json"
  skip_reason="$(python3 -c "import json,sys; d=json.load(open('$conf_file')); print(d.get('skip',''))" 2>/dev/null)"
  if [ -n "$skip_reason" ]; then
    echo "  ⊘ [$n] SKIP — $skip_reason"; skip=$((skip+1)); skipped+=("$n")
    continue
  fi

  # Repeat loop — acumula pass_count sobre N runs.
  pass_count=0; i=0
  while [ "$i" -lt "$repeat" ]; do
    i=$((i+1))
    [ "$repeat" -gt 1 ] && echo "    run $i/$repeat..."
    if "$DRIVER" run --scenario "$n" --assert "${extra_flags[@]+"${extra_flags[@]}"}"; then
      pass_count=$((pass_count+1))
    fi
  done

  if [ "$repeat" -eq 1 ]; then
    # N=1 → formato original, sem taxa (backward compat).
    if [ "$pass_count" -eq 1 ]; then
      echo "  ✓ [$n] PASS"; pass=$((pass+1))
    else
      echo "  ✗ [$n] FAIL"; fail=$((fail+1)); failed+=("$n")
    fi
  else
    # N>1 → mostrar taxa; FLAKY se alguns passaram mas não atingiu min-pass.
    if [ "$pass_count" -ge "$min_pass" ]; then
      echo "  ✓ [$n] $pass_count/$repeat PASS"; pass=$((pass+1))
    else
      if [ "$pass_count" -gt 0 ]; then
        echo "  ✗ [$n] $pass_count/$repeat PASS — FLAKY"
      else
        echo "  ✗ [$n] $pass_count/$repeat PASS — FAIL"
      fi
      fail=$((fail+1)); failed+=("$n")
    fi
  fi
done

echo ""; echo "═══════════════════════════════════════════════════"
[ "$repeat" -gt 1 ] && echo "  (repeat=$repeat, min-pass=$min_pass — cada cenário rodado $repeat×)"
echo "RESULTADO: $pass PASS · $fail FAIL · $skip SKIP  (de $((pass+fail+skip)) cenários)"
[ "${#skipped[@]}" -gt 0 ] && echo "  ⊘ Skipped: ${skipped[*]}"
[ "$fail" -eq 0 ] && { echo "✓ Sem regressão."; exit 0; } || { echo "✗ Falharam: ${failed[*]}"; exit 1; }
