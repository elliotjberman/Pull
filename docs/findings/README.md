# Active Findings

This directory contains temporary architectural investigations: observed limitations, unresolved
questions, and bounded compromises that should remain visible while related work is underway.

Findings are not permanent architecture documents. Each finding must include:

- The observation and concrete evidence.
- The current consequences and safe short-term behavior.
- A proposed mitigation or decision still needed.
- Explicit removal criteria.

When a change satisfies a finding's removal criteria:

1. Move any lasting architectural rule into the appropriate permanent design document.
2. Delete the finding in the same pull request.

Git history is the archive. Do not keep resolved findings here or create a resolved-findings
graveyard.

Before changing adjacent architecture, agents should read the active findings and update or remove
any document their work resolves.

## Active

- [Snapback Parameters V1](snapback-v1-limitations.md)
- [Parameter Targets Are Coupled to Proxy Slots](parameter-target-proxy-coupling.md)
- [Physical Inputs Are Coupled to Semantic Consequences](physical-input-semantic-action-coupling.md)
