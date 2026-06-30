#!/usr/bin/env bash
# ============================================================
# PSHB Parameter Sweep – simple bash version
# Requires: jq  (brew install jq)
#
# Usage:
#   chmod +x run_sweep.sh
#   ./run_sweep.sh              # uses sweep_config.json
#   ./run_sweep.sh my.json
# ============================================================
set -euo pipefail

CONFIG="${1:-sweep_config.json}"
JAR=$(jq -r '.jar' "$CONFIG")

if [[ ! -f "$JAR" ]]; then
  echo "JAR not found: $JAR"
  echo "Run:  mvn clean package -DskipTests"
  exit 1
fi

# ---- read fixed params ----
FIXED_ARGS=""
while IFS="=" read -r key val; do
  FIXED_ARGS="$FIXED_ARGS -$key $val"
done < <(jq -r '.fixed | to_entries[] | "\(.key)=\(.value)"' "$CONFIG")

# ---- read seeds ----
mapfile -t SEEDS < <(jq -r '.seeds[]' "$CONFIG")

# ---- read sweep params (one_at_a_time only in bash) ----
# Get default = first element of each sweep list
declare -A DEFAULTS
while IFS="=" read -r key val; do
  DEFAULTS[$key]="$val"
done < <(jq -r '.sweep | to_entries[] | "\(.key)=\(.value[0])"' "$CONFIG")

# Build default args string
DEFAULT_ARGS=""
for key in "${!DEFAULTS[@]}"; do
  DEFAULT_ARGS="$DEFAULT_ARGS -$key ${DEFAULTS[$key]}"
done

RUN_IDX=0

run_one() {
  local run_id="$1"
  local extra_args="$2"
  local seed="$3"
  local log_dir="runs/$run_id"
  mkdir -p "$log_dir"
  echo "[$(date +%H:%M:%S)] START  $run_id"
  java -Xmx4g -cp "$JAR" pshb.PSHBHeadless \
    -runId "$run_id" -seed "$seed" \
    $FIXED_ARGS $extra_args \
    > "$log_dir/stdout.log" 2>&1 \
    && echo "[$(date +%H:%M:%S)] OK     $run_id" \
    || echo "[$(date +%H:%M:%S)] FAILED $run_id  (see $log_dir/stdout.log)"
}

# ---- baseline run (all defaults) ----
for seed in "${SEEDS[@]}"; do
  RUN_IDX=$((RUN_IDX + 1))
  run_id=$(printf "run_%04d_s%s" "$RUN_IDX" "$seed")
  run_one "$run_id" "$DEFAULT_ARGS" "$seed"
done

# ---- one-at-a-time sweep ----
while IFS= read -r key; do
  mapfile -t VALS < <(jq -r --arg k "$key" '.sweep[$k][]' "$CONFIG")
  for val in "${VALS[@]}"; do
    if [[ "$val" == "${DEFAULTS[$key]}" ]]; then
      continue  # already covered by baseline
    fi
    SWEEP_ARGS="$DEFAULT_ARGS -$key $val"
    for seed in "${SEEDS[@]}"; do
      RUN_IDX=$((RUN_IDX + 1))
      run_id=$(printf "run_%04d_s%s" "$RUN_IDX" "$seed")
      run_one "$run_id" "$SWEEP_ARGS" "$seed"
    done
  done
done < <(jq -r '.sweep | keys[]' "$CONFIG")

echo ""
echo "Sweep complete. Results are in: runs/"