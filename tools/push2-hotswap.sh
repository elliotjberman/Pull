#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]]
then
    if ! JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"
    then
        if ! command -v brew >/dev/null 2>&1
        then
            echo "Set JAVA_HOME to a JDK 21 installation." >&2
            exit 1
        fi
        JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
    fi
fi
AGENT_LIBRARY="$ROOT/target/libpush2hotswap.dylib"

if [[ "$#" -eq 0 ]]
then
    echo "Usage: $0 CLASS_NAME [CLASS_NAME...]" >&2
    exit 2
fi

BITWIG_PIDS=( $(pgrep -f '^/Applications/Bitwig Studio.app/Contents/MacOS/BitwigStudio --launch$' || true) )
if [[ "${#BITWIG_PIDS[@]}" -ne 1 ]]
then
    echo "Expected one BitwigStudio --launch process, found ${#BITWIG_PIDS[@]}." >&2
    exit 1
fi

CLASS_NAMES="$(IFS=,; echo "$*")"
clang -dynamiclib -std=c11 -Wall -Wextra -Werror \
    -I"$JAVA_HOME/include" \
    -I"$JAVA_HOME/include/darwin" \
    -o "$AGENT_LIBRARY" \
    "$ROOT/tools/hotswap/push2_hotswap_agent.c"
"$JAVA_HOME/bin/jcmd" "${BITWIG_PIDS[0]}" JVMTI.agent_load "$AGENT_LIBRARY" "$ROOT/target/classes::$CLASS_NAMES"
cat /tmp/pull-push2-hotswap
