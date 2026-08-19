---
status: active
created: 2026-08-19
scope: live-controller-testing
remove_when: one authoritative receipt correlates the installed extension artifact, running shell identity, and active core build for live-test assertions
---

# Live Test Provenance Is Not Authoritative

## Observation

The live lease serializes agents that install, reload, restart, or drive the singleton Bitwig/Push
environment. It does not prove which extension artifact Bitwig loaded or correlate that shell with
the exact active reloadable-core build. Current logs and reload status can identify pieces of that
state, but no single authoritative receipt binds all three identities to a test assertion.

## Current Consequences And Mitigation

Two agents can no longer intentionally mutate the environment at the same time through the guarded
tools, but a tester can still mistake a stale installation or active core for the build under test.
Keep the lease across installation, activation, and authoritative read-back; record the exact core
build ID reported by `tools/reload-core`; and explicitly restart Bitwig after replacing the shell.
Do not treat lease ownership by itself as build-provenance evidence.

## Proposed Resolution And Tests

Publish a bounded live-test receipt from the stable shell that includes:

- A digest or build identity for the installed extension artifact.
- The running stable-shell build identity.
- The active reloadable-core build identity and generation.

The reload and debug clients should require the expected identities and fail closed on mismatch.
An integration test must cover stale installed artifacts, rejected core candidates, successful core
replacement, and a later assertion against the correlated receipt.

## Removal Criteria

Delete this finding when live assertions consume one authoritative receipt that correlates all
three identities and the mismatch cases above are covered. Preserve the durable rule that lease
ownership serializes mutation but never proves build provenance in the permanent testing or
architecture documentation.
