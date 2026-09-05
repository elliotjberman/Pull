// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;
import de.mossgrabers.pull.core.api.output.TouchStripMode;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class RawPitchBendViewTest
{
    private static final ControlId STRIP = PushControlIds.continuous ("TOUCHSTRIP");


    @Test
    void rawProfileClaimsBothInputsAndCompleteHardwareOutput ()
    {
        final Fixture fixture = new Fixture ();
        final CoreResult result = fixture.raw.start (snapshot (0));

        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().modeOrNull (STRIP, InputKind.TOUCH));
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().modeOrNull (STRIP, InputKind.ABSOLUTE));
        assertEquals (DesiredTouchStrip.pitchBend (8192), result.desiredOutput ().touchStrip ());
        assertTrue (result.effects ().isEmpty ());
        assertTrue (result.desiredControllerState ().workspace ().facets ().isEmpty ());
    }


    @Test
    void rawSamplesPreserveAllFourteenBitsAndReleaseCentersBothOutputs ()
    {
        final Fixture fixture = new Fixture ();
        fixture.raw.start (snapshot (0));
        fixture.raw.handle (touch (1, InputPhase.BEGIN), snapshot (1));

        final CoreResult movement = fixture.raw.handle (position (2, 12289), snapshot (2));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 1, 96)), movement.effects ());
        assertEquals (DesiredTouchStrip.pitchBend (12289), movement.desiredOutput ().touchStrip ());

        final CoreResult release = fixture.raw.handle (touch (3, InputPhase.END), snapshot (3));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 0, 64)), release.effects ());
        assertEquals (DesiredTouchStrip.pitchBend (8192), release.desiredOutput ().touchStrip ());
    }


    @Test
    void modifiersAndLongTouchDoNotChangeTheAcquiredRawMeaning ()
    {
        final Fixture fixture = new Fixture ();
        final Set<ControlId> modifiers = Set.of (PushControlIds.button ("SHIFT"), PushControlIds.button ("DELETE"), PushControlIds.button ("REPEAT"));
        fixture.raw.start (snapshot (0));
        fixture.raw.handle (touch (1, InputPhase.BEGIN), snapshot (1, modifiers));
        assertTrue (fixture.raw.handle (touch (2, InputPhase.LONG), snapshot (2, modifiers)).effects ().isEmpty ());

        final CoreResult movement = fixture.raw.handle (position (3, 16383), snapshot (3, modifiers));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 127, 127)), movement.effects ());
        assertEquals (DesiredTouchStrip.pitchBend (16383), movement.desiredOutput ().touchStrip ());
    }


    @Test
    void rawGestureContinuesAfterItsOriginalWorkspaceDisappears ()
    {
        final Fixture fixture = new Fixture ();
        fixture.raw.start (snapshot (0));
        fixture.raw.handle (touch (1, InputPhase.BEGIN), snapshot (1));
        fixture.raw.handle (position (2, 1), snapshot (2));
        assertEquals (DesiredTouchStrip.pitchBend (1), fixture.legacy.activate (snapshot (3)).desiredOutput ().touchStrip ());

        final CoreResult continued = fixture.legacy.handle (position (4, 16383), snapshot (4));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 127, 127)), continued.effects ());

        final CoreResult ended = fixture.legacy.handle (touch (5, InputPhase.END), snapshot (5));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 0, 64)), ended.effects ());
        assertEquals (DesiredTouchStrip.pitchBend (8192), ended.desiredOutput ().touchStrip ());
        assertEquals (DesiredTouchStrip.pitchBend (8192), fixture.legacy.activate (snapshot (5)).desiredOutput ().touchStrip ());
        assertEquals (DesiredTouchStrip.unowned (), fixture.legacy.activate (snapshot (6)).desiredOutput ().touchStrip ());
    }


    @Test
    void rawProfileCannotStealATouchThatBeganWithLegacyMeaning ()
    {
        final Fixture fixture = new Fixture ();
        final CoreResult legacy = fixture.legacy.start (snapshot (0));
        assertEquals (InputRouteMode.OBSERVE, legacy.desiredInputRoutes ().modeOrNull (STRIP, InputKind.TOUCH));
        assertEquals (InputRouteMode.OBSERVE, legacy.desiredInputRoutes ().modeOrNull (STRIP, InputKind.ABSOLUTE));
        assertEquals (DesiredTouchStrip.unowned (), legacy.desiredOutput ().touchStrip ());
        fixture.legacy.handle (touch (1, InputPhase.BEGIN), snapshot (1));

        assertEquals (DesiredTouchStrip.unowned (), fixture.raw.activate (snapshot (2)).desiredOutput ().touchStrip ());
        final CoreResult movement = fixture.raw.handle (position (3, 12345), snapshot (3));
        assertTrue (movement.effects ().isEmpty ());
        assertEquals (DesiredTouchStrip.unowned (), movement.desiredOutput ().touchStrip ());
        assertTrue (fixture.raw.handle (touch (4, InputPhase.END), snapshot (4)).effects ().isEmpty ());

        fixture.raw.handle (touch (5, InputPhase.BEGIN), snapshot (5));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xE0, 1, 0)), fixture.raw.handle (position (6, 1), snapshot (6)).effects ());
    }


    @Test
    void duplicateBeginDoesNotResetTheCurrentGestureAndOrphanInputIsInert ()
    {
        final Fixture fixture = new Fixture ();
        fixture.raw.start (snapshot (0));
        assertTrue (fixture.raw.handle (position (1, 100), snapshot (1)).effects ().isEmpty ());
        assertTrue (fixture.raw.handle (touch (2, InputPhase.END), snapshot (2)).effects ().isEmpty ());
        fixture.raw.handle (touch (3, InputPhase.BEGIN), snapshot (3));
        fixture.raw.handle (position (4, 300), snapshot (4));
        fixture.legacy.activate (snapshot (5));
        assertEquals (DesiredTouchStrip.pitchBend (300), fixture.legacy.handle (touch (6, InputPhase.BEGIN), snapshot (6)).desiredOutput ().touchStrip ());
    }


    @Test
    void compilerRejectsStripOutputWithoutItsClaim ()
    {
        final RawPitchBendView raw = RawPitchBendView.raw (new RawPitchBendGesture ());
        final ControllerView invalid = new ControllerView ()
        {
            @Override
            public String id () { return "invalid-strip"; }

            @Override
            public ViewProfile profile () { return ViewProfile.fixed ("none", Set.of (), Set.of ()); }

            @Override
            public ViewOutput render (final ControllerSnapshot snapshot) { return raw.render (snapshot); }
        };
        assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("invalid", List.of (invalid)).start (snapshot (0)));
    }


    @Test
    void outputDistinguishesUnownedFromDarkAndRejectsInvalidHardwarePositions ()
    {
        assertFalse (DesiredTouchStrip.unowned ().owned ());
        assertTrue (DesiredTouchStrip.off ().owned ());
        assertEquals (TouchStripMode.OFF, DesiredTouchStrip.off ().mode ());
        assertEquals (DesiredTouchStrip.unowned (), DesiredHardwareOutput.empty ().touchStrip ());
        assertThrows (IllegalArgumentException.class, () -> DesiredTouchStrip.pitchBend (-1));
        assertThrows (IllegalArgumentException.class, () -> DesiredTouchStrip.pitchBend (16384));
        assertThrows (IllegalArgumentException.class, () -> new DesiredTouchStrip (false, TouchStripMode.PITCH_BEND, 8192));
        assertThrows (IllegalArgumentException.class, () -> new DesiredTouchStrip (true, TouchStripMode.OFF, 1));
    }


    private static ControllerInputEvent touch (final long revision, final InputPhase phase)
    {
        return new ControllerInputEvent (revision, revision, STRIP, InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127);
    }


    private static ControllerInputEvent position (final long revision, final int position)
    {
        return new ControllerInputEvent (revision, revision, STRIP, InputKind.ABSOLUTE, InputPhase.UPDATE, position);
    }


    private static ControllerSnapshot snapshot (final long revision)
    {
        return snapshot (revision, Set.of ());
    }


    private static ControllerSnapshot snapshot (final long revision, final Set<ControlId> pressed)
    {
        return new ControllerSnapshot (revision, revision, new ShellCapabilities (Map.of ()), new ClipCatalogSnapshot (0, List.of ()), Map.of (), pressed, Set.of ());
    }


    private static final class Fixture
    {
        private final RawPitchBendGesture gesture = new RawPitchBendGesture ();
        private final CompiledWorkspace raw = CompiledWorkspace.compile ("raw", List.of (RawPitchBendView.raw (this.gesture)));
        private final CompiledWorkspace legacy = CompiledWorkspace.compile ("legacy", List.of (RawPitchBendView.legacyContinuation (this.gesture)));
    }
}
