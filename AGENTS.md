# Repository Agent Instructions

## Bitwig controller API compatibility

- This repository currently targets Bitwig controller API 21. Keep the
  `com.bitwig:extension-api` version in `pom.xml` and
  `AbstractControllerExtensionDefinition.getRequiredAPIVersion()` exactly synchronized.
  Do not confuse this Bitwig API version with the separate reloadable-core API version.
- Before adding or changing a direct `com.bitwig.extension.*` call, verify the exact method
  and overload against the locally resolved `extension-api` JAR for the declared version.
  Do not rely on old examples, memory, or successful compilation alone.
- Do not introduce calls marked `@Deprecated` in the declared Bitwig API. Bitwig may reject
  deprecated controller methods at runtime instead of merely logging a warning.
- For changes that touch Bitwig API objects, run the complete package build with deprecation
  reporting enabled:

  ```bash
  mvn -o -Dmaven.compiler.showDeprecation=true package
  ```

  Treat every deprecation warning in changed code as a blocking failure.
- Fake-host tests do not exercise Bitwig's runtime proxy enforcement. After the offline build
  passes, explicitly call out when a first live Bitwig smoke test is still required.
- When a controller fails during startup, inspect `~/Library/Logs/Bitwig/BitwigStudio.log` and
  `~/Library/Logs/Bitwig/BitwigStudio-previous-run.log` for the full stack trace before patching.
