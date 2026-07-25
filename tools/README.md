# Push 2 development tools

These macOS tools shorten the edit/build/preview loop while developing the Pull
controller extension. The filesystem bridge is disabled unless one of these
tools creates `${TMPDIR}/pull-push2-dev/enabled` before the controller starts.

## Watch, build, and hot swap

Run:

```sh
./tools/push2-dev.sh
```

The script watches `src/main/java` and `pom.xml`, builds `Pull.bwextension`, and
installs it in Bitwig's extension directory. Method-body changes are hot-swapped
into the running controller and followed by a display capture at
`${TMPDIR}/pull-push2-dev/display.png`.

The initial build, POM changes, new classes, removed classes, fields, and method
signature changes require fully quitting and reopening Bitwig. Bitwig's
controller restart API does not reload extension bytecode.

## Controller commands

`push2-command.sh` can request actions from a running development build:

```sh
./tools/push2-command.sh capture
./tools/push2-command.sh brightness 100 80
./tools/push2-command.sh mode volume
./tools/push2-command.sh status
```

Supported modes are `track`, `volume`, `pan`, `user`, and `device-params`.

## JVM hot swap

For method-body-only changes, build the classes and pass their fully qualified
names to:

```sh
./tools/push2-hotswap.sh de.mossgrabers.example.ChangedClass
```

This helper requires macOS, Clang, JDK 21, and one running Bitwig process.
Structural class changes still require fully quitting and reopening Bitwig.
