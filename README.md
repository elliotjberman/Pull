# Pull

An opinionated Push 2 control surface for Bitwig Studio, bent around a specific live-set workflow.

Pull is based on [DrivenByMoss](https://github.com/git-moss/DrivenByMoss).

## Building and installing the extension

1. Install JDK 21, [Maven](https://maven.apache.org/install.html), and Git.
2. Run `mvn clean package` in this repo's root.
3. Copy `target/Pull.bwextension` into Bitwig Studio's extensions folder.

For a targeted extension build, use `mvn -pl pull-shell -am package`. The reactor builds the
current core first and embeds that resolved artifact through the resource-only core bundle.

## Fast core test loop

Run the reloadable controller core and its shell fakes without building the Bitwig extension:

```bash
mvn -pl pull-core -am test
```

After that first run has downloaded the test dependencies, repeat fully offline:

```bash
mvn -o -pl pull-core -am test
```

This loop uses deterministic fake time and does not launch or require Bitwig.

## Reloading a core during development

After installing and starting a reloadable shell, build, publish, and activate a core without
restarting Bitwig:

```bash
tools/reload-core
```

The command runs Maven offline by default. Use `tools/reload-core --online` once if Maven still
needs dependencies. It publishes to `~/.drivenbymoss/pull/reload`; set
`PULL_CORE_RELOAD_DIR` or pass an absolute `--directory` to use the shell's configured directory.

Success means the running shell acknowledged the exact requested build ID—not merely that Maven
succeeded. Each candidate carries an exact fingerprint of the local parent-loaded shell/API
sources. If that differs from the running extension, Bitwig reports `restartRequired` instead of
attempting an unsafe core reload—even when the shell change was already committed.
