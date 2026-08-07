// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.shell.input.InputKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Exact permanent-input whitelist tests for core-exclusive Push controls. */
class PushControllerInputBridgeTest
{
    @Test
    void admitsPlayAndRecordButNotAnUnmigratedButtonToExclusiveRouting ()
    {
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("PLAY"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("RECORD"), InputKind.BUTTON));
        assertFalse (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SESSION"), InputKind.BUTTON));
    }
}
