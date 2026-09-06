// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Exercise actual production composition selection rather than only constructing the views. */
class RawPitchBendCoreIntegrationTest
{
    private static final ControlId STRIP = PushControlIds.continuous ("TOUCHSTRIP");

    @Test
    void rawGesturePreservesFourteenBitsAndCentersMidiAndLightsOnRelease ()
    {
        final Fixture fixture = new Fixture ("SESSION", false, false);
        fixture.start ();
        assertTrue (fixture.input (InputKind.ABSOLUTE, InputPhase.UPDATE, 12289).effects ().isEmpty (), "motion without a touch must be inert");
        fixture.pressed = Set.of (PushControlIds.button ("SHIFT"), PushControlIds.button ("DELETE"), PushControlIds.button ("REPEAT"));
        fixture.input (InputKind.TOUCH, InputPhase.BEGIN, 127);
        final CoreResult moved = fixture.input (InputKind.ABSOLUTE, InputPhase.UPDATE, 12289);
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 1, 96)), moved.effects ());
        assertEquals (DesiredTouchStrip.pitchBend (12289), moved.desiredOutput ().touchStrip ());
        assertEquals (DesiredTouchStrip.pitchBend (12289), fixture.input (InputKind.TOUCH, InputPhase.BEGIN, 127).desiredOutput ().touchStrip (), "duplicate BEGIN must not recenter");
        final CoreResult release = fixture.input (InputKind.TOUCH, InputPhase.END, 0);
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 0, 64)), release.effects ());
        assertEquals (DesiredTouchStrip.pitchBend (8192), release.desiredOutput ().touchStrip ());
        assertTrue (fixture.input (InputKind.TOUCH, InputPhase.END, 0).effects ().isEmpty ());
    }

    @Test
    void enteringRawModeCannotStealATouchThatBeganWithLegacyMeaning ()
    {
        final Fixture fixture = new Fixture ("DRUM_PAD", true, false);
        fixture.start ();
        fixture.input (InputKind.TOUCH, InputPhase.BEGIN, 127);
        fixture.engaged = true;
        fixture.changed ();
        final CoreResult held = fixture.input (InputKind.ABSOLUTE, InputPhase.UPDATE, 12289);
        assertTrue (held.effects ().isEmpty ());
        assertEquals (DesiredTouchStrip.unowned (), held.desiredOutput ().touchStrip ());
        assertTrue (fixture.input (InputKind.TOUCH, InputPhase.END, 0).effects ().isEmpty ());
        fixture.input (InputKind.TOUCH, InputPhase.BEGIN, 127);
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 127, 127)), fixture.input (InputKind.ABSOLUTE, InputPhase.UPDATE, 16383).effects ());
    }


    @Test
    void initialSessionAndEngagedDrumOwnRawWhileOrdinaryNoteRetainsLegacy ()
    {
        assertRaw (new Fixture ("SESSION", false, false).start ());
        assertRaw (new Fixture ("DRUM_PAD", true, true).start ());
        assertLegacy (new Fixture ("PLAY", false, false).start ());
        assertLegacy (new Fixture ("DRUM_PAD", true, false).start ());
    }


    @Test
    void actualCoreKeepsRawGestureAcrossEngagementAndPageChanges ()
    {
        final Fixture fixture = new Fixture ("DRUM_PAD", true, true);
        fixture.start ();
        fixture.input (InputKind.TOUCH, InputPhase.BEGIN, 127);
        fixture.input (InputKind.ABSOLUTE, InputPhase.UPDATE, 13000);
        fixture.mode = "MASTER";
        assertEquals (DesiredTouchStrip.pitchBend (13000), fixture.changed ().desiredOutput ().touchStrip ());
        fixture.engaged = false;
        assertEquals (DesiredTouchStrip.pitchBend (13000), fixture.changed ().desiredOutput ().touchStrip ());
        final CoreResult release = fixture.input (InputKind.TOUCH, InputPhase.END, 0);
        assertTrue (release.effects ().contains (new SendNoteInputMidiEffect (0xE0, 0, 64)));
        assertEquals (DesiredTouchStrip.pitchBend (8192), release.desiredOutput ().touchStrip ());
        assertLegacy (fixture.changed ());
    }


    @Test
    void compositeKeepsItsDeclaredRawStripWithoutAnApplicableDrumTarget ()
    {
        final Fixture fixture = new Fixture ("PLAY", false, false);
        fixture.start ();
        fixture.pressed = Set.of (PushControlIds.button ("SHIFT"));
        fixture.button ("SHIFT", InputPhase.BEGIN);
        fixture.pressed = Set.of (PushControlIds.button ("SHIFT"), PushControlIds.button ("SESSION"));
        fixture.button ("SESSION", InputPhase.BEGIN);
        fixture.pressed = Set.of (PushControlIds.button ("SHIFT"));
        final CoreResult entered = fixture.button ("SESSION", InputPhase.END);
        assertRaw (entered);
        assertEquals (ControllerPages.VS_LIVE_NAME + " / project-macros", entered.desiredControllerState ().workspace ().name ());
    }


    private static void assertRaw (final CoreResult result)
    {
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().modeOrNull (STRIP, InputKind.TOUCH));
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().modeOrNull (STRIP, InputKind.ABSOLUTE));
        assertEquals (DesiredTouchStrip.pitchBend (8192), result.desiredOutput ().touchStrip ());
    }


    private static void assertLegacy (final CoreResult result)
    {
        assertEquals (InputRouteMode.OBSERVE, result.desiredInputRoutes ().modeOrNull (STRIP, InputKind.TOUCH));
        assertEquals (DesiredTouchStrip.unowned (), result.desiredOutput ().touchStrip ());
    }


    private static final class Fixture
    {
        private final PullCoreProvider provider = new PullCoreProvider ();
        private final ControllerCore core = this.provider.create ();
        private final String view;
        private final boolean drum;
        private boolean engaged;
        private String mode = "TRACK";
        private long revision;
        private Set<ControlId> pressed = Set.of ();
        private Set<ControlId> touched = Set.of ();


        private Fixture (final String view, final boolean drum, final boolean engaged)
        {
            this.view = view;
            this.drum = drum;
            this.engaged = engaged;
        }


        private CoreResult start ()
        {
            return this.core.start (this.snapshot (), Optional.empty ());
        }


        private CoreResult changed ()
        {
            this.revision++;
            return this.core.handle (new SnapshotChangedEvent (this.revision, this.revision), this.snapshot ());
        }


        private CoreResult input (final InputKind kind, final InputPhase phase, final int value)
        {
            this.revision++;
            if (kind == InputKind.TOUCH)
                this.touched = phase == InputPhase.END ? Set.of () : Set.of (STRIP);
            return this.core.handle (new ControllerInputEvent (this.revision, this.revision, STRIP, kind, phase, value), this.snapshot ());
        }


        private CoreResult button (final String button, final InputPhase phase)
        {
            this.revision++;
            return this.core.handle (new ControllerInputEvent (this.revision, this.revision, PushControlIds.button (button), InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ());
        }


        private ControllerSnapshot snapshot ()
        {
            final ControllerLayoutSnapshot layout = new ControllerLayoutSnapshot (this.revision, this.view, this.mode, this.drum, this.engaged, 36, GridPressureConfiguration.OFF);
            final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), layout, DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty ());
            return new ControllerSnapshot (this.revision, this.revision, this.provider.descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, this.touched);
        }
    }
}
