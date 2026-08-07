#!/bin/sh
# POSIX sh — runs both as the asterisk-ip-sync init step in docker-compose.yml
# (busybox ash inside curlimages/curl) and by hand in Git Bash.
#
# Keeps pjsip.conf's [transport-trunk] external_signaling_address /
# external_media_address in sync with the current public IP (NETWORK.md §10
# "Dinamik IP muammosi"). docker-compose.yml runs this once, before asterisk
# starts, on every `docker compose up` — so a changed ISP IP is picked up
# automatically without a manual check or a mid-session restart.
#
# What it does:
#   1. Fetch the current public IP.
#   2. Compare it to what [transport-trunk] currently advertises.
#   3. If it changed, rewrite BOTH external_*_address lines in that section only
#      ([transport-lan] — the LAN/softphone transport — is never touched).
#
# Usage (manual, from the repo root):
#   ./asterisk/update-external-ip.sh
# Or against a specific file:
#   PJSIP_CONF=/path/to/pjsip.conf ./asterisk/update-external-ip.sh
set -eu

CONF="${PJSIP_CONF:-$(dirname "$0")/etc/asterisk/pjsip.conf}"

log() {
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1"
}

is_ipv4() {
    case "$1" in
        '' | *[!0-9.]*) return 1 ;;
    esac
    [ "$(printf '%s' "$1" | tr -cd '.' | wc -c)" -eq 3 ]
}

current_public_ip() {
    ip=$(curl -s --max-time 5 https://ifconfig.me || true)
    if ! is_ipv4 "$ip"; then
        ip=$(curl -s --max-time 5 https://ipinfo.io/ip || true)
    fi
    is_ipv4 "$ip" && printf '%s' "$ip"
}

# Current value advertised by [transport-trunk] (the two lines are kept
# identical by this script, so reading one is enough to detect drift).
configured_ip() {
    awk '
        /^\[transport-trunk\]/ { in_section = 1; next }
        /^\[/ { in_section = 0 }
        in_section && /^external_signaling_address[ \t]*=/ {
            sub(/^[^=]*=[ \t]*/, "")
            print
            exit
        }
    ' "$CONF"
}

# Rewrites both external_*_address lines inside [transport-trunk] only.
set_configured_ip() {
    new_ip="$1"
    tmp="$CONF.tmp.$$"
    awk -v new_ip="$new_ip" '
        /^\[transport-trunk\]/ { in_section = 1; print; next }
        /^\[/ { in_section = 0; print; next }
        in_section && /^external_signaling_address[ \t]*=/ {
            print "external_signaling_address = " new_ip
            next
        }
        in_section && /^external_media_address[ \t]*=/ {
            print "external_media_address = " new_ip
            next
        }
        { print }
    ' "$CONF" > "$tmp"
    mv "$tmp" "$CONF"
}

if [ ! -f "$CONF" ]; then
    log "ERROR: $CONF not found"
    exit 1
fi

new_ip=$(current_public_ip || true)
if [ -z "${new_ip:-}" ]; then
    log "WARN: could not determine public IP (ifconfig.me / ipinfo.io both failed) — leaving pjsip.conf as-is"
    exit 0
fi

old_ip=$(configured_ip)
if [ -z "$old_ip" ]; then
    log "ERROR: could not find external_signaling_address in [transport-trunk] of $CONF"
    exit 1
fi

if [ "$new_ip" = "$old_ip" ]; then
    log "public IP unchanged ($new_ip)"
    exit 0
fi

log "public IP changed: $old_ip -> $new_ip — updating pjsip.conf"
set_configured_ip "$new_ip"
log "done"
