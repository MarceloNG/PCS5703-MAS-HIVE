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
# ============================================================================
set -uo pipefail

REPO="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
cd "$REPO"
DRIVER=".claude/skills/run-hive/run-hive.sh"
SCEN_DIR="conf/scenarios"

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
  if "$DRIVER" run --scenario "$n" --assert; then
    echo "  ✓ [$n] PASS"; pass=$((pass+1))
  else
    echo "  ✗ [$n] FAIL"; fail=$((fail+1)); failed+=("$n")
  fi
done

echo ""; echo "═══════════════════════════════════════════════════"
echo "RESULTADO: $pass PASS · $fail FAIL  (de $((pass+fail)) cenários)"
[ "$fail" -eq 0 ] && { echo "✓ Sem regressão."; exit 0; } || { echo "✗ Falharam: ${failed[*]}"; exit 1; }
