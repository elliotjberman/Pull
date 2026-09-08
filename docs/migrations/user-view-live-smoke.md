# User view live smoke

On 2026-09-08, installed the combined source `474e5380b012b96ba7a16ee77c5b9987446c1bbe`
(PR #56 User view plus PR #57 legacy display cleanup) under the `user-cleanup-live` lease.
The source was checkpointed in a persistent worktree before Bitwig restart.

- Shell SHA-256: `d731cfd4ead35bbe6282cfadabf68512f75dba5bdecfebc0eef8bfe95cfda0a9`.
- Active core build: `20260908T152442Z-44bfede3cd1ccf060d23cf4945d55418`.
- Core SHA-256: `91d19b6bb31615954158ee14c84c25c2325530afb9e91eeee0262d454946efc6`.
- Full offline package: 1,065 tests passed with deprecation reporting enabled.

After restart, the matching core reported `state=active`, `message=Activated`.
The old published API 54 candidate was rejected before publishing the matching API 55 core.

From Session, a routed User BEGIN/END through the generic surface debugger reported APPLIED.
A request-correlated transmitted Push display capture showed project macros, including Off
switches for Rippler and PermReset, with no legacy upper-left menu annotation.
The named Shift+Session project-macros gesture then reached authoritative
`view=WORKSPACE mode=WORKSPACE workspace=true`. Its subsequent transmitted frame was
pixel-identical to the User capture over the macro region (960 by 143 pixels).
The remaining 17-pixel footer belongs to the retained grid composition.

Captures on the test host:
- User: `~/.drivenbymoss/pull/debug/display-1788881239-23233-4391.png`.
- Shift+Session: `~/.drivenbymoss/pull/debug/display-1788881243-23252-31224.png`.

This validates live navigation and display equivalence in the loaded project, not every
parameter interaction or remaining optional shell renderer. Bitwig was left running with
Project Macros visible; the temporary debugger server was stopped and the lease released.
