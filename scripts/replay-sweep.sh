#!/usr/bin/env bash
#
# Sweeps the speech gate's endpointing settings over the labelled corpus and prints, for
# each configuration, how many recordings still close the right number of turns and what
# the average end-of-utterance wait cost.
#
# This answers ONE HALF of the open question in docs/VOICE-QUALITY-PLAN.md A.1: "if we
# moved endpointing from the provider to our own gate, would the turn boundaries hold?"
# It cannot answer the other half — what the provider costs today — because the
# provider's endpointing happens inside Yandex and no offline replay can reproduce it.
# That number comes from the live ledger: voice.turn.stage.latency{stage=eou} plus
# {stage=stt_final}. Read docs/REGRESSION.md before acting on the output.
#
# Usage:
#   scripts/replay-sweep.sh <corpus-dir> [vad-model] [turn-model]
#
# The corpus directory must hold the .wav files and a manifest.tsv labelling them.

set -euo pipefail

CORPUS="${1:-corpus}"
VAD_MODEL="${2:-${VAD_MODEL_PATH:-models/silero_vad.onnx}}"
TURN_MODEL="${3:-${TURN_MODEL_PATH:-}}"
MANIFEST="$CORPUS/manifest.tsv"

if [ ! -d "$CORPUS" ]; then
  echo "Corpus directory not found: $CORPUS" >&2
  exit 1
fi
if [ ! -f "$VAD_MODEL" ]; then
  echo "VAD model not found: $VAD_MODEL" >&2
  exit 1
fi
if [ ! -f "$MANIFEST" ]; then
  echo "No manifest at $MANIFEST — every run would report turns without checking them." >&2
  echo "See docs/REGRESSION.md for the format." >&2
  exit 1
fi

OUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUT_DIR"' EXIT

# Each entry is a name and the options that differ from the tool's defaults, which are
# what config/speech.yml ships. Add a line to try a setting; nothing else changes.
CONFIGS=(
  "shipped|--post-roll-ms=1000 --short-silence-ms=500"
  "post-roll-700|--post-roll-ms=700 --short-silence-ms=500"
  "post-roll-500|--post-roll-ms=500 --short-silence-ms=400"
  "adaptive-600|--post-roll-ms=1000 --short-silence-ms=500 --dynamic-min-post-roll-ms=600 --reopen-grace-ms=900"
)

if [ -n "$TURN_MODEL" ] && [ -f "$TURN_MODEL" ]; then
  CONFIGS+=(
    "smart-extend|--post-roll-ms=700 --turn-model=$TURN_MODEL --turn-extend-ms=500"
    "smart-early-300|--post-roll-ms=1000 --turn-model=$TURN_MODEL --turn-extend-ms=500 --early-wait-ms=300 --early-threshold=0.9"
  )
else
  echo "No Smart Turn model given — skipping the two configurations that need one."
  echo "  (pass it as the third argument or set TURN_MODEL_PATH)"
  echo
fi

printf '%-18s %8s %8s %10s\n' "config" "checked" "failed" "avg eou"
printf '%-18s %8s %8s %10s\n' "------------------" "--------" "--------" "----------"

for entry in "${CONFIGS[@]}"; do
  name="${entry%%|*}"
  opts="${entry#*|}"
  log="$OUT_DIR/$name.log"

  # The tool exits 1 when a recording's turn count moved; here that is a result to
  # report, not a reason to stop the sweep.
  set +e
  # shellcheck disable=SC2086
  ./gradlew -q turnReplay --no-daemon \
    --args="$CORPUS --vad-model=$VAD_MODEL --manifest=$MANIFEST $opts" \
    > "$log" 2>&1
  set -e

  summary="$(grep -E '^=== [0-9]+ file' "$log" | tail -1)"
  checked="$(echo "$summary" | sed -n 's/.* \([0-9]*\) checked.*/\1/p')"
  failed="$(echo "$summary" | sed -n 's/.* \([0-9]*\) failed.*/\1/p')"
  avg="$(grep -oE 'average eou wait [0-9]+ms' "$log" \
        | grep -oE '[0-9]+' \
        | awk '{ total += $1; n++ } END { if (n > 0) printf "%d", total / n; else print "-" }')"

  printf '%-18s %8s %8s %9sms\n' "$name" "${checked:--}" "${failed:--}" "${avg:--}"
  cp "$log" "./replay-$name.log"
done

echo
echo "Full logs written to ./replay-<config>.log"
echo
echo "How to read this:"
echo "  failed  = recordings whose turn count moved. ANY failure disqualifies the config —"
echo "            a turn boundary that moved is a sentence answered in two halves."
echo "  avg eou = silence the caller sat through after finishing, per closed turn."
echo
echo "A config is only a candidate when failed = 0. Among those, the lowest avg eou wins."
echo "Then compare that number against what the provider costs today, from the live"
echo "ledger (voice.turn.stage.latency{stage=eou} + {stage=stt_final}) — see docs/REGRESSION.md."
