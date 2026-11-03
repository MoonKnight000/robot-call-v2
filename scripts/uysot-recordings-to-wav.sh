#!/usr/bin/env bash
#
# Converts the recordings UysotCallDownloadTool fetched (mp3 and the like) into the
# 8 kHz 16-bit WAV that TurnDatasetTool reads, keeping the channels as they are, and
# reports how many came out stereo.
#
# The stereo count decides the whole plan: TurnDatasetTool labels a pause by who spoke
# next, and only a recording with the operator and the customer on separate channels says
# that. A mono recording is converted too but cannot be labelled without diarization.
#
# Uses a local ffmpeg when there is one, otherwise the ffmpeg in a container.
#
# Usage:
#   scripts/uysot-recordings-to-wav.sh [corpus/uysot]

set -euo pipefail

ROOT="${1:-corpus/uysot}"
IN="$ROOT/audio"
OUT="$ROOT/wav"

if [ ! -d "$IN" ]; then
  echo "No recordings directory: $IN" >&2
  exit 1
fi
mkdir -p "$OUT"

if command -v ffmpeg >/dev/null 2>&1; then
  ffmpeg_run() { ffmpeg "$@"; }
  ffprobe_run() { ffprobe "$@"; }
  MOUNT=""
else
  if ! command -v docker >/dev/null 2>&1; then
    echo "Neither ffmpeg nor docker is available" >&2
    exit 1
  fi
  # pwd -W gives the C:/... form Docker Desktop mounts on Windows; plain pwd elsewhere.
  ABS_ROOT="$(cd "$ROOT" && (pwd -W 2>/dev/null || pwd))"
  ffmpeg_run() { MSYS_NO_PATHCONV=1 docker run --rm -v "$ABS_ROOT:/data" linuxserver/ffmpeg "$@"; }
  ffprobe_run() { MSYS_NO_PATHCONV=1 docker run --rm -v "$ABS_ROOT:/data" --entrypoint ffprobe linuxserver/ffmpeg "$@"; }
  MOUNT="/data"
fi

# Paths as the ffmpeg process sees them: the host path locally, /data/... in the container.
path_for_ffmpeg() {
  if [ -n "$MOUNT" ]; then
    echo "$MOUNT/${1#"$ROOT"/}"
  else
    echo "$1"
  fi
}

stereo=0
mono=0
failed=0
for src in "$IN"/*; do
  [ -f "$src" ] || continue
  case "$src" in *.part) continue ;; esac
  name="$(basename "${src%.*}")"
  dst="$OUT/$name.wav"
  if [ -f "$OUT/mono/$name.wav" ]; then
    mono=$((mono + 1)) # converted on an earlier run
    continue
  fi
  if [ ! -f "$dst" ]; then
    if ! ffmpeg_run -nostdin -loglevel error -y -i "$(path_for_ffmpeg "$src")" \
        -ar 8000 -acodec pcm_s16le "$(path_for_ffmpeg "$dst")"; then
      echo "conversion failed: $src" >&2
      failed=$((failed + 1))
      continue
    fi
  fi
  channels="$(ffprobe_run -v error -select_streams a:0 -show_entries stream=channels -of csv=p=0 \
      "$(path_for_ffmpeg "$dst")" | tr -d '\r')"
  if [ "$channels" = "2" ]; then
    stereo=$((stereo + 1))
  else
    mono=$((mono + 1))
    mkdir -p "$OUT/mono"
    mv "$dst" "$OUT/mono/"
  fi
done

echo "stereo: $stereo (in $OUT — ready for TurnDatasetTool)"
echo "mono:   $mono (moved to $OUT/mono — cannot be labelled by channel)"
echo "failed: $failed"
