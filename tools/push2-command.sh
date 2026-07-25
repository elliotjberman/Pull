#!/bin/bash

set -u

DEV_DIR="${TMPDIR:-/tmp}/pull-push2-dev"

mkdir -p "$DEV_DIR"
touch "$DEV_DIR/enabled"

bump_marker ()
{
    local marker="$1"
    local next_marker="${marker}.next.$$"
    printf '%s %s %s\n' "$(date +%s)" "$$" "$RANDOM" > "$next_marker"
    mv -f "$next_marker" "$marker"
}

write_brightness_marker ()
{
    local marker="$DEV_DIR/brightness"
    local next_marker="${marker}.next.$$"
    printf '%s %s\n' "$1" "$2" > "$next_marker"
    mv -f "$next_marker" "$marker"
}

case "${1:-}" in
    capture)
        bump_marker "$DEV_DIR/capture"
        echo "Requested a Push 2 display capture."
        ;;
    brightness)
        display="${2:-}"
        leds="${3:-}"
        if [[ ! "$display" =~ ^[0-9]+$ || ! "$leds" =~ ^[0-9]+$ || "$display" -gt 100 || "$leds" -gt 100 ]]
        then
            echo "Usage: $0 brightness DISPLAY_PERCENT LED_PERCENT" >&2
            exit 2
        fi
        write_brightness_marker "$display" "$leds"
        bump_marker "$DEV_DIR/capture"
        echo "Requested Push 2 brightness: display ${display}%, LEDs ${leds}%."
        ;;
    mode)
        if [[ -z "${2:-}" ]]
        then
            echo "Usage: $0 mode MODE" >&2
            exit 2
        fi
        case "$2" in
            TRACK|track) marker="$DEV_DIR/mode-track" ;;
            VOLUME|volume) marker="$DEV_DIR/mode-volume" ;;
            PAN|pan) marker="$DEV_DIR/mode-pan" ;;
            USER|user) marker="$DEV_DIR/mode-user" ;;
            DEVICE_PARAMS|device_params|device-params) marker="$DEV_DIR/mode-device-params" ;;
            *)
                echo "Unsupported Push 2 mode: $2" >&2
                exit 2
                ;;
        esac
        bump_marker "$marker"
        echo "Requested Push 2 mode: $2"
        ;;
    status)
        for path in "$DEV_DIR/ready" "$DEV_DIR/display-debug.txt" "$DEV_DIR/mode-applied" "$DEV_DIR/brightness-applied" "$DEV_DIR/display.png"
        do
            if [[ -e "$path" ]]
            then
                stat -f '%Sm %N' -t '%Y-%m-%d %H:%M:%S' "$path"
            else
                echo "Missing: $path"
            fi
        done
        ;;
    *)
        echo "Usage: $0 {capture|brightness DISPLAY_PERCENT LED_PERCENT|mode MODE|status}" >&2
        exit 2
        ;;
esac
