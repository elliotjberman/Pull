// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.shell.input.InputKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Exact permanent-input whitelist tests for core-exclusive Push controls. */
class PushControllerInputBridgeTest
{
    @Test
    void admitsMigratedControllerButtonsToExclusiveRouting ()
    {
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("PLAY"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("RECORD"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SESSION"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("NOTE"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("LAYOUT"), InputKind.BUTTON));
        assertFalse (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SCALES"), InputKind.BUTTON));
        for (final var control: CoreControls.DRUM_USER_CONTROLS)
        {
            assertTrue (PushControllerInputBridge.isCoreOwnedInput (control, InputKind.PAD));
            assertFalse (PushControllerInputBridge.isCoreOwnedInput (control, InputKind.POLY_PRESSURE));
        }
    }
}
