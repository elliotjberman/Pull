---
status: active
created: 2026-09-05
scope: track-scoped-native-controller-mapping
remove_when: bounded per-track native mapping allocation and document lifecycle are proven and their contract is recorded in permanent architecture documentation
---

# Track-Scoped MIDI Learn V1 Lifecycle TODOs

## Observation

The user explicitly accepted a bounded per-track V1 with the general lifecycle work left as TODOs.
API 44 implements that scope with 128 banks of four permanent native endpoints; the original four
shared endpoints remain constructed but inert. This acceptance does not establish the unverified
host contracts below or remove the need for an exact-build physical learning smoke test.

On 2026-09-05, the scratch identity probe held live lease `pr37-track-identity-probe` against PR #37
commit `748c0df241791d93a0500df81007fa2f22bcd723`, with recorded active core build
`20260904T214803Z-089985e92a1271f46260e6385804bc61`. Later selected-track read-back showed:

| Operation | Observed channel UUID |
| --- | --- |
| Create instrument track Inst 1 | `8153103e-aa55-41aa-a7b9-7694b6429635` |
| Duplicate to Inst 2 | `5e10dc9a-b8c9-4771-bd30-c0a536b6ba2b` |
| Rename duplicate to PR37 Identity B | Same duplicate UUID |
| Delete duplicate | Selection moved to audio track `051c8e60-b918-4aff-b85f-38e60219ce78` |
| Undo deletion | Duplicate UUID returned |
| Save, close, and reopen | Duplicate UUID persisted |

The scratch project was then closed, the original project restored, and the lease released. These
observations cover that scratch project only. Reorder/group movement, duplication of actual native
learned mappings, and a persistent allocation registry were not verified.

## Observed Mapping-Name Runtime Restriction

The user confirmed that the API 44 physical two-track native mappings worked, then requested
track-name labels and explicitly allowed deferral if the API could not support them. An API 45
naming experiment kept permanent IDs/matchers separate from `HardwareControl.setName(String)`;
517 offline tests passed, but the exact live build rejected the setter.

At 2026-09-05 17:18:23 local time, BitwigStudio.log reported `This can only be called during driver
initialization`, with `jaS.setName` → `AbstractHwAbsoluteControl.setName` →
`ControllerMappingNamesHost.refresh`. The failing core build was
`20260905T211419Z-18d085e09eaac9481c26008146a9c811` and extension SHA-256 was
`3d8ad883ce6c321c8595d3094584334104d0ed3db74ea75724387edbd9051a0b`.
Inspection of the installed Bitwig implementation confirmed that both `setName(String)` and
`setLabel(String)` call `checkIsInitializingDriver()` before accepting a value. The locally resolved
API 25 source declares both without deprecation or an initialization-only warning; its existence
alone therefore does not establish runtime mutability.

The naming experiment was removed. V1 remains on core API 44 and uses permanent
`Drum Controller N` labels numbered 1–512: bank 1 contains 1–4, bank 2 contains 5–8, and so on.
These numbers identify allocated controls, not track positions. Track renames do not change the
UUID-to-bank allocation or the permanent native control identities.
A future naming design must demonstrate a supported runtime presentation mechanism in the actual
host before adding a cache or API transport. Do not revive the runtime setters or recreate controls
under new identities on rename; either would break the current bounded contract.

## Bitwig Report Draft — Runtime Mapping-Source Labels

TODO: send the following report to Bitwig and establish a supported naming contract before
revisiting track-name labels. This draft has not been sent.

**Environment:** Bitwig Studio 6.1 on macOS, Java controller extension targeting controller API 25.

**Impact:** Our extension preallocates persistent hardware controls for independent per-track MIDI
mappings. We want Bitwig's mapping-source labels to identify the owning track while preserving each
control's permanent ID and learned bindings. Fixed names such as `Drum Controller 1` work, but cannot
identify the track to the user. Even a frozen first-known track name is difficult: our new track and
document-state proxies provide the required metadata through later host updates, after the controls
have been created. This timing is evidence from our current proxy setup, not an explicit API timing
guarantee. Bitwig itself already has the track metadata.

**Observed behavior:** In our extension, calling `HardwareControl.setName(String)` after
initialization failed live at 2026-09-05 17:18:23 with `This can only be called during driver
initialization`. The stack passed through `jaS.setName` and our `ControllerMappingNamesHost.refresh`.
Inspection of this installed Bitwig version also found the same `checkIsInitializingDriver()` guard
in `HardwareElement.setLabel(String)`; we did not separately reproduce a `setLabel` failure live.
The locally resolved API 25 source documents neither setter as initialization-only or deprecated.

**Minimal reproduction sketch:** The following Java-like pseudocode isolates the attempted sequence;
it has not been independently executed as a minimal reproduction. The live evidence above came from
the full extension.

```java
void init() {
    surface = host.createHardwareSurface();
    control = surface.createAbsoluteHardwareKnob("PERMANENT_DRUM_CONTROL_1");
    control.setName("Drum Controller 1");
    track = host.createCursorTrack("name-probe", "Name probe", 0, 0, true);
    track.name().markInterested();
}

void flush() {
    if (!attempted && !track.name().get().isBlank()) {
        attempted = true;
        control.setName(track.name().get() + " Drum Controller 1");
    }
}
```

**Requested support:** Is there a supported way to update a native mapping-source display name after
initialization without changing the persistent hardware control identity or its learned bindings?
If not, please consider adding one, and document the initialization restriction on both existing
setters. The display name should be able to follow later track metadata and renames independently of
the control's identity.

## Contract Gaps And Open Questions

- API 25 exposes channel identity without a documented lifetime guarantee. The observations above
  are useful evidence, not a promise covering every project operation.
- API 25 does not expose learned-binding enumeration, serialization, copying, or target-owner
  validation. Whether track duplication copies a learned binding, and what that means for an
  inactive source endpoint, still needs a direct native-mapping probe.
- Hidden string storage through `DocumentState` offers no documented atomic-write or undo contract,
  nor document-addressed compare-and-set. Registry behavior under undo/redo and project switching
  still needs host experiments. V1 gates matching on later storage read-back rather than treating
  submission as persistence.
- `ProjectImpl` combines root-channel UUID and project name. V1 instead embeds the observed
  document master UUID in its registry, but uniqueness across copies remains unproven. A stronger
  document ownership contract is still a TODO.
- A native learned action executes in Bitwig's matcher path, outside Pull's effect executor. There
  is no installed apply-time selection check on that path. A design may acknowledge context
  propagation latency, but must not promise a strict instantaneous selection fence.

These are contract gaps and untested behaviors. No duplicate-mapping, undo-registry, or
cross-project ownership failure was observed in this probe.

## Accepted Bounded V1

Use an eagerly created endpoint pool with an append-only, document-persisted allocation
from track UUID to one four-endpoint bank, up to 128 historical allocations per document. Core owns
allocation policy and the active bank lease; stable owns bounded resources, owner storage,
authoritative observations, and matcher handoff.
Deleted tracks retain tombstones so undo can recover their original allocation. A newly duplicated
track receives a fresh bank. Never recycle a tombstoned bank onto another track while its native
learned bindings cannot be inspected or cleared safely. Capacity therefore counts historically
allocated tracks, not merely current tracks; exhaustion must fail closed with a clear diagnostic.

Core parses the raw hidden DocumentState payload containing document master UUID and track UUIDs.
Matching waits for observed storage acknowledgement; leases fence the document, selected-track
UUID/generation, and storage revision. Corrupt storage or exhausted capacity leaves the four pads
amber and inert. This mechanism does not enumerate or validate Bitwig's native learned targets.
TODO: prove duplicate native-binding behavior, registry undo/project boundaries, safe clearing and
recycling, and stronger document identity before claiming a general lifecycle solution.

## Capability Audit And Next Probes

Existing PAD29–32/PAD input, all modifier/release variants, RGB ownership, raw target feedback,
and matcher lifecycle remain the baseline. No new pad gesture or target-write effect is needed.
The V1 expansion adds a bounded per-track endpoint inventory, raw document owner/storage
read-back and writes, and fenced bank activation. Allocation state is document-persisted; core
retains policy while the parent owns the persistence and endpoint mechanisms.

This accepted V1 is a Class B bounded canopy expansion requiring API/shell installation and a
Bitwig restart. It supersedes the earlier recommendation to defer all per-track behavior. Full
native lifecycle support remains pending the probes above. Offline coverage and live verification
for the final API 44 build must be reported separately; the earlier identity probe is not proof of
the new registry or native mappings. See `../../TESTING.md` for the required smoke sequence.

## Removal Criteria

Delete this finding when native mapping duplication and document/registry lifecycle tests pass,
permanent endpoint ownership cannot silently transfer to another track or document within the
supported contract, and capacity, tombstones, context latency, and unsupported cases are documented
in the permanent mapping design. Add deterministic regressions for representable invariants and
retain the native-host probes for behavior fakes cannot establish.
