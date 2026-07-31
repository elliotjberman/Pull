// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value and immutability tests for the parent-loaded API.
 */
class CoreApiValueTest
{
    @Test
    void stateEnvelopeCopiesBytesAndUsesContentEquality ()
    {
        final byte [] bytes = new byte []
        {
            1,
            2,
            3
        };
        final StateEnvelope envelope = new StateEnvelope ("schema", 1, bytes);
        bytes[0] = 9;
        final byte [] returnedBytes = envelope.payload ();
        returnedBytes[1] = 9;

        assertEquals (new StateEnvelope ("schema", 1, new byte []
        {
            1,
            2,
            3
        }), envelope);
    }


    @Test
    void snapshotsResultsAndOutputsCopyCollections ()
    {
        final ControlId control = new ControlId ("control");
        final Set<ControlId> pressed = new HashSet<> (Set.of (control));
        final ControllerSnapshot snapshot = new ControllerSnapshot (1, 2, ShellCapabilities.empty (), pressed, Set.of ());
        pressed.clear ();

        final Map<ControlId, RgbColor> lights = new HashMap<> (Map.of (control, new RgbColor (1, 2, 3)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (lights);
        lights.clear ();

        final List<CoreEffect> effects = new ArrayList<> (List.of (new ScheduleTimerEffect (new TimerId ("timer"), 3)));
        final CoreResult result = new CoreResult (output, effects);
        effects.clear ();

        assertTrue (snapshot.pressedControls ().contains (control));
        assertEquals (new RgbColor (1, 2, 3), result.desiredOutput ().lights ().get (control));
        assertEquals (1, result.effects ().size ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.pressedControls ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> result.effects ().clear ());
    }


    @Test
    void versionedCapabilitiesRequireEqualOrNewerImplementations ()
    {
        final ShellCapabilities available = new ShellCapabilities (Map.of ("lights", Integer.valueOf (2), "timers", Integer.valueOf (1)));

        assertTrue (available.supports (new ShellCapabilities (Map.of ("lights", Integer.valueOf (1)))));
        assertTrue (available.supports (new ShellCapabilities (Map.of ("lights", Integer.valueOf (2)))));
        assertFalse (available.supports (new ShellCapabilities (Map.of ("lights", Integer.valueOf (3)))));
        assertFalse (available.supports (new ShellCapabilities (Map.of ("clips", Integer.valueOf (1)))));
    }


    @Test
    void rejectsInvalidBoundaryValues ()
    {
        assertThrows (IllegalArgumentException.class, () -> new ControlId (" "));
        assertThrows (IllegalArgumentException.class, () -> new TimerId (""));
        assertThrows (IllegalArgumentException.class, () -> new ControllerSnapshot (-1, 0, ShellCapabilities.empty (), Set.of (), Set.of ()));
        assertThrows (IllegalArgumentException.class, () -> new ShellCapabilities (Map.of ("lights", Integer.valueOf (0))));
        assertThrows (IllegalArgumentException.class, () -> new RgbColor (256, 0, 0));
    }
}
