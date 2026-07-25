#!/bin/bash

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$ROOT/src/main/java"
POM="$ROOT/pom.xml"
EXTENSION_DIR="$HOME/Documents/Bitwig Studio/Extensions"
EXTENSION="$EXTENSION_DIR/Pull.bwextension"
NEXT_EXTENSION="$EXTENSION_DIR/.Pull.bwextension.next"
DEV_DIR="${TMPDIR:-/tmp}/pull-push2-dev"
CAPTURE_MARKER="$DEV_DIR/capture"
DEBUG_MARKER="$DEV_DIR/display-debug.txt"

mkdir -p "$DEV_DIR"
touch "$DEV_DIR/enabled"
STAMP="$(mktemp "$DEV_DIR/stamp.XXXXXX")"

cleanup ()
{
    rm -f "$STAMP" "$NEXT_EXTENSION"
}

trap cleanup EXIT
trap 'exit 130' INT TERM

read_marker ()
{
    if [[ -f "$1" ]]
    then
        cat "$1"
    fi
}

bump_marker ()
{
    local marker="$1"
    local next_marker="${marker}.next.$$"
    printf '%s %s %s\n' "$(date +%s)" "$$" "$RANDOM" > "$next_marker"
    mv -f "$next_marker" "$marker"
}

wait_for_marker_change ()
{
    local marker="$1"
    local previous="$2"
    local description="$3"
    local attempt

    for attempt in $(seq 1 120)
    do
        local current
        current="$(read_marker "$marker")"
        if [[ -n "$current" && "$current" != "$previous" ]]
        then
            return 0
        fi
        sleep 0.25
    done

    echo "[push2-dev] Timed out waiting for $description."
    return 1
}

class_names_for_sources ()
{
    local source
    local source_relative
    local class_stem
    local class_file
    local class_relative
    local class_name
    local class_names=()

    shopt -s nullglob
    for source in "$@"
    do
        source_relative="${source#"$SOURCE_DIR/"}"
        class_stem="$ROOT/target/classes/${source_relative%.java}"
        for class_file in "$class_stem.class" "$class_stem"\$*.class
        do
            class_relative="${class_file#"$ROOT/target/classes/"}"
            class_name="${class_relative%.class}"
            class_name="${class_name//\//.}"
            case " ${class_names[*]} " in
                *" $class_name "*) ;;
                *) class_names+=("$class_name") ;;
            esac
        done
    done
    shopt -u nullglob

    printf '%s\n' "${class_names[@]}"
}

capture_display ()
{
    if [[ ! -f "$DEV_DIR/ready" ]]
    then
        echo "[push2-dev] Restart Bitwig once to enable automatic display capture."
        return 0
    fi

    local capture_before
    capture_before="$(read_marker "$DEBUG_MARKER")"
    bump_marker "$CAPTURE_MARKER"
    if wait_for_marker_change "$DEBUG_MARKER" "$capture_before" "the display capture"
    then
        echo "[push2-dev] Captured $DEV_DIR/display.png"
    fi
}

build_install_and_update ()
{
    local changed_sources=()
    local requires_restart=false
    local source

    while IFS= read -r source
    do
        changed_sources+=("$source")
    done < <(find "$SOURCE_DIR" -type f -name '*.java' -newer "$STAMP" -print)

    if [[ "$POM" -nt "$STAMP" ]]
    then
        requires_restart=true
    fi

    # Capture the source state before building so edits made during the build trigger another pass.
    touch "$STAMP"
    echo "[push2-dev] Building..."
    local maven_goal=(package)
    if [[ "$requires_restart" == true || "${#changed_sources[@]}" -eq 0 ]]
    then
        maven_goal=(clean package)
    fi
    if ! (cd "$ROOT" && mvn -DskipTests "${maven_goal[@]}")
    then
        echo "[push2-dev] Build failed; keeping the currently loaded extension."
        return 1
    fi

    local built_extension="$ROOT/target/Pull.bwextension"
    if [[ ! -f "$built_extension" || ! "$built_extension" -nt "$STAMP" ]]
    then
        echo "[push2-dev] Could not find the newly built Pull extension."
        return 1
    fi

    mkdir -p "$EXTENSION_DIR"
    cp "$built_extension" "$NEXT_EXTENSION"
    if ! unzip -tq "$NEXT_EXTENSION"
    then
        echo "[push2-dev] Built extension is invalid; keeping the currently installed extension."
        rm -f "$NEXT_EXTENSION"
        return 1
    fi
    mv -f "$NEXT_EXTENSION" "$EXTENSION"
    echo "[push2-dev] Installed $EXTENSION"

    if [[ "$requires_restart" == true || "${#changed_sources[@]}" -eq 0 ]]
    then
        echo "[push2-dev] Fully quit and reopen Bitwig to load this build."
        return 0
    fi

    local class_names=()
    while IFS= read -r class_name
    do
        if [[ -n "$class_name" ]]
        then
            class_names+=("$class_name")
        fi
    done < <(class_names_for_sources "${changed_sources[@]}")

    if [[ "${#class_names[@]}" -eq 0 ]]
    then
        echo "[push2-dev] No compiled classes found; fully quit and reopen Bitwig."
        return 0
    fi

    if "$ROOT/tools/push2-hotswap.sh" "${class_names[@]}"
    then
        capture_display
        return 0
    fi

    echo "[push2-dev] Hot swap rejected the change; fully quit and reopen Bitwig."
    return 0
}

wait_for_change ()
{
    while true
    do
        sleep 0.5
        if [[ "$POM" -nt "$STAMP" ]]
        then
            return
        fi

        local changed
        changed="$(find "$SOURCE_DIR" \( -type f -o -type d \) -newer "$STAMP" -print -quit)"
        if [[ -n "$changed" ]]
        then
            return
        fi
    done
}

build_install_and_update || true
echo "[push2-dev] Watching src/main/java and pom.xml. Press Ctrl-C to stop."

while true
do
    wait_for_change
    sleep 0.2
    build_install_and_update || true
done
