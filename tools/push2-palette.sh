#!/bin/sh

set -eu

PALETTE_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PALETTE_GENERATOR="$PALETTE_ROOT/tools/push2-palette/generate.py"

COMMAND=${1:-status}

case "$COMMAND" in
    list|status|check)
        exec python3 "$PALETTE_GENERATOR" "--$COMMAND"
        ;;
    use)
        if [ "$#" -ne 2 ]; then
            echo "Usage: $0 use PROFILE" >&2
            exit 2
        fi
        exec python3 "$PALETTE_GENERATOR" --use "$2"
        ;;
    *)
        echo "Usage: $0 {list|status|check|use PROFILE}" >&2
        exit 2
        ;;
esac
