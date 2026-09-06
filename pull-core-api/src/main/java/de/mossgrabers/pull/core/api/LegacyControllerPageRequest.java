// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Ordered compatibility request, frozen to the projected core page that originated it. */
public record LegacyControllerPageRequest (long sequence, long originPageRevision, long originTemporaryToken, Operation operation, String legacyModeId, ControllerPageRef capturedTarget, long temporaryRequestSequence)
{
    public enum Operation { SELECT, SELECT_CAPTURED, TEMPORARY, TOGGLE_TEMPORARY, BEGIN_TEMPORARY, END_TEMPORARY, CANCEL_TEMPORARY, RESTORE, SET_PREVIOUS }
    public LegacyControllerPageRequest
    {
        operation = Objects.requireNonNull (operation, "operation");
        legacyModeId = Objects.requireNonNull (legacyModeId, "legacyModeId");
        capturedTarget = Objects.requireNonNull (capturedTarget, "capturedTarget");
        if (operation == Operation.SELECT_CAPTURED ? !capturedTarget.isPresent () : operation != Operation.BEGIN_TEMPORARY && capturedTarget.isPresent ()) throw new IllegalArgumentException ("Captured page is only valid for selection or a temporary-entry precondition");
        if ((operation == Operation.END_TEMPORARY || operation == Operation.CANCEL_TEMPORARY) ? temporaryRequestSequence <= 0 || temporaryRequestSequence >= sequence : temporaryRequestSequence != 0) throw new IllegalArgumentException ("Temporary return requires an earlier entry request");
        if (sequence <= 0 || originPageRevision < 0 || originTemporaryToken < 0) throw new IllegalArgumentException ("Invalid compatibility request sequence or origin");
        if (legacyModeId.length () > 128 || legacyModeId.chars ().anyMatch (Character::isISOControl)) throw new IllegalArgumentException ("Legacy mode ID must be bounded and printable");
        if (operation == Operation.RESTORE || operation == Operation.SELECT_CAPTURED || operation == Operation.END_TEMPORARY || operation == Operation.CANCEL_TEMPORARY ? !legacyModeId.isEmpty () : operation != Operation.SET_PREVIOUS && legacyModeId.isBlank ()) throw new IllegalArgumentException ("Mode ID does not match request operation");
    }
    public LegacyControllerPageRequest (final long sequence, final long originPageRevision, final long originTemporaryToken, final Operation operation, final String legacyModeId, final ControllerPageRef capturedTarget)
    {
        this (sequence, originPageRevision, originTemporaryToken, operation, legacyModeId, capturedTarget, 0);
    }
    public LegacyControllerPageRequest (final long sequence, final long originPageRevision, final long originTemporaryToken, final Operation operation, final String legacyModeId)
    {
        this (sequence, originPageRevision, originTemporaryToken, operation, legacyModeId, ControllerPageRef.none ());
    }
}
