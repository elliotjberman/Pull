# Project Macro touch capability audit

## Scope and preserved behavior

Feature: the Project Macro page's eight encoder touches. The fixed Project Macro profile owns
`KNOB1` through `KNOB8` / `TOUCH`, alongside its existing relative turns and parameter display.
Other inherited page profiles keep their existing touch dispatch.

On touch begin, retain the exact current project-remote parameter. Delete plus touch consumes
Delete and resets that parameter before submitting touch begin. On touch end, end that same
parameter's touch; when the user enables Stop automation on knob release, stop active unified Automation Write (Arranger and Clip Launcher in Bitwig 6). Touch emphasis remains rendered by the existing core scene
from the shell's physical touch snapshot. There is no separate long-press behavior.

Required authoritative state: project-remote slot identity/value, physical touch and modifier
state, stop-on-release preference, automation-write state, and current project/target identity.
Required effects: exact-target reset, exact-target touch ownership, mechanical Delete consumption,
and project-fenced absolute automation-write requests.
Required output: the existing core-owned parameter display region.

## Existing and missing canopy

Installed: normalized touch events and `touchedControls`, fixed encoder surface claims,
project-remote parameter targets, reset effects, and core display output. The input router freezes
gesture ownership through release and fences normal core replacement while a core gesture is held.

Missing: exclusive touch admission for the Project Macro profile, replayable bounded exact-target
touch ownership with stable cleanup, stop-on-release preference and automation-write snapshots,
automation-write effects, and Delete consumption admission. `WorkspaceMode.onKnobTouch` becomes
inert only after this complete slice is ready. Its global inherited knob dispatcher remains for
unmigrated pages. This is a Class B canopy expansion and requires one shell install/restart.

Touch capacity is eight physical owners. A target is captured at BEGIN and must not silently move
to a new remote page, parameter, or project during the gesture. Normal release, profile departure,
core failure, selected-target boundary, and extension exit all retire owned touches. The public
desired state is complete and replayable; the shell performs only target validation, lease
retention, effect execution, and cleanup. It must not decide Delete or automation policy.

## API verification and lifecycle limits

The locally resolved API 25 source JAR confirms `Parameter.touch(boolean)` and
`Transport.isArrangerAutomationWriteEnabled()` are nondeprecated. Bitwig's official
[6.0 release notes](https://downloads.bitwig.com/6.0/Release-Notes-6.0.html), Automation Editing,
document the single Automation Write button applying to Arranger and Clip Launcher. The supported
arranger-named property retains its historical method name; this capability observes that property
and submits an absolute `set(false)` request. The separate launcher methods are deprecated and are
not called by the new capability. One transport proxy and its interested Boolean value are eagerly
installed. Snapshot publication is gated by `AUTOMATION`. The first live test must verify the unified
button and read-back in both recording contexts; fake tests cannot prove Bitwig proxy behavior.

A core-owned bounded release session remembers admitted Project touches across page departure.
Departure clears desired parameter-touch ownership immediately; the physical END can still stop
writing through the controller-level core observer if the captured project is still current.
Legacy touches never enter this session. Core failure and exit clean up parameter touches, without
inventing an automation-writing change in the stable shell. Normal reload waits for held gestures.

The existing project-remote target uses a rebindable Bitwig proxy; retaining its Java wrapper does
not pin the old parameter across a remote-page or project change. Cleanup may release only while
the live target identity still matches. After an external rebind, cleanup must fail closed and
report its limit; guaranteed release of the old target would require a directly addressable or
pinned actuator. No API promise about implicit host touch cleanup is assumed.

## Verification plan

Core cases: normal begin/end, Delete reset ordering and consumption, no target/missing capability,
preference off/on, unified automation-write read-back, later host read-back, target change while
held, profile transition, unchanged replay, and exclusive claims only in the Project profile.
Shell cases: prepare/apply target checks, begin once/end once, replay without churn, stale proxy
rebind without retargeting, cleanup after failure/selection/exit, and untouched legacy profiles.
Run the complete package build with deprecation reporting. The first routed live smoke test must
follow the exact installed shell/core build through touch, host automation state, display feedback,
and a normal core reload. No live actions are part of the offline implementation stage.
