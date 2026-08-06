#!/bin/bash

set -euo pipefail

PUSH2_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PUSH2_SOURCE="$PUSH2_ROOT/tools/push2-color-test/Push2ColorTest.java"
PUSH2_LCD_SOURCE="$PUSH2_ROOT/tools/push2-color-test/Push2LcdReference.java"
PUSH2_DISPLAY_CALIBRATION_SOURCE="$PUSH2_ROOT/pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/controller/PushColorCalibration.java"
PUSH2_BUILD_DIR="${TMPDIR:-/tmp}/pull-push2-color-test/classes"
PUSH2_CLASS="$PUSH2_BUILD_DIR/Push2ColorTest.class"
PUSH2_BRIDGE_SOURCE="$PUSH2_ROOT/tools/push2-color-test/Push2CoreMIDIBridge.c"
PUSH2_BRIDGE_BUILD_DIR="${TMPDIR:-/tmp}/pull-push2-color-test/native"
PUSH2_BRIDGE="$PUSH2_BRIDGE_BUILD_DIR/Push2CoreMIDIBridge"
PUSH2_JAVA_HOME="${JAVA_HOME:-}"

if [[ -z "$PUSH2_JAVA_HOME" ]]
then
    PUSH2_JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi

if [[ -z "$PUSH2_JAVA_HOME" ]] && command -v brew >/dev/null 2>&1
then
    PUSH2_BREW_JAVA="$(brew --prefix openjdk 2>/dev/null || brew --prefix openjdk@21 2>/dev/null || true)"
    if [[ -n "$PUSH2_BREW_JAVA" ]]
    then
        PUSH2_JAVA_HOME="$PUSH2_BREW_JAVA/libexec/openjdk.jdk/Contents/Home"
    fi
fi

if [[ ! -x "$PUSH2_JAVA_HOME/bin/java" || ! -x "$PUSH2_JAVA_HOME/bin/javac" ]]
then
    echo "A JDK 21 or newer is required. Set JAVA_HOME to its installation directory." >&2
    exit 1
fi

if [[ ! -f "$PUSH2_CLASS" || "$PUSH2_SOURCE" -nt "$PUSH2_CLASS" || "$PUSH2_LCD_SOURCE" -nt "$PUSH2_CLASS" || "$PUSH2_DISPLAY_CALIBRATION_SOURCE" -nt "$PUSH2_CLASS" ]]
then
    mkdir -p "$PUSH2_BUILD_DIR"
    "$PUSH2_JAVA_HOME/bin/javac" --release 21 -encoding UTF-8 -Xlint:all -d "$PUSH2_BUILD_DIR" \
        "$PUSH2_DISPLAY_CALIBRATION_SOURCE" \
        "$PUSH2_LCD_SOURCE" \
        "$PUSH2_SOURCE"
fi

PUSH2_JAVA_OPTIONS=()
PUSH2_CLASSPATH="$PUSH2_BUILD_DIR:$PUSH2_ROOT/pull-shell/target/classes"
if [[ "$(uname -s)" == "Darwin" ]]
then
    if [[ ! -x "$PUSH2_BRIDGE" || "$PUSH2_BRIDGE_SOURCE" -nt "$PUSH2_BRIDGE" ]]
    then
        mkdir -p "$PUSH2_BRIDGE_BUILD_DIR/module-cache"
        CLANG_MODULE_CACHE_PATH="$PUSH2_BRIDGE_BUILD_DIR/module-cache" \
            xcrun clang -std=c11 -Wall -Wextra -Werror "$PUSH2_BRIDGE_SOURCE" \
            -framework CoreMIDI -framework CoreFoundation -o "$PUSH2_BRIDGE"
    fi
    PUSH2_JAVA_OPTIONS+=("-Dpush2.coremidi.bridge=$PUSH2_BRIDGE")

    PUSH2_BITWIG_CONTENTS="/Applications/Bitwig Studio.app/Contents"
    PUSH2_BITWIG_LIBS="$PUSH2_BITWIG_CONTENTS/Java/libs.jar"
    PUSH2_BITWIG_NATIVE_ROOT="$PUSH2_BITWIG_CONTENTS/Frameworks/cp"
    if [[ -f "$PUSH2_BITWIG_LIBS" && -d "$PUSH2_BITWIG_NATIVE_ROOT/org/usb4java" ]]
    then
        PUSH2_CLASSPATH="$PUSH2_CLASSPATH:$PUSH2_BITWIG_LIBS:$PUSH2_BITWIG_NATIVE_ROOT"
        PUSH2_JAVA_OPTIONS+=("--enable-native-access=ALL-UNNAMED")
    fi
fi

exec "$PUSH2_JAVA_HOME/bin/java" "${PUSH2_JAVA_OPTIONS[@]}" -cp "$PUSH2_CLASSPATH" Push2ColorTest "$@"
