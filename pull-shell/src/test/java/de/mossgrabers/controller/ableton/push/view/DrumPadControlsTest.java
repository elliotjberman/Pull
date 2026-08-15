// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Tests view and preference ownership of the drum-controller roll.
 */
class DrumPadControlsTest
{
    @Test
    void automaticRollRequiresBothControllerOwnershipAndTheUserSetting ()
    {
        assertFalse (DrumPadControls.shouldEnableRoll (false, false));
        assertFalse (DrumPadControls.shouldEnableRoll (false, true));
        assertFalse (DrumPadControls.shouldEnableRoll (true, false));
        assertTrue (DrumPadControls.shouldEnableRoll (true, true));
    }
}
