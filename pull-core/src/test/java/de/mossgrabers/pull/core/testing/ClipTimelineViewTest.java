// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTimelineSnapshot;
import de.mossgrabers.pull.core.api.ClipTimelineTarget;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.SetClipTimelineRangeEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.LightBlinkRate;
import de.mossgrabers.pull.core.api.output.ControllerLight;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.view.ClipTimelineState;
import de.mossgrabers.pull.core.runtime.view.ClipTimelineView;
import de.mossgrabers.pull.core.view.CompiledWorkspace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Unit tests for core-owned Clip Timeline policy and authoritative feedback. */
class ClipTimelineViewTest
{
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor SESSION_PLAYING_GREEN = new RgbColor (0, 89, 0);
    private static final RgbColor BLUE = new RgbColor (30, 80, 220);
    private static final ClipTimelineTarget TARGET = new ClipTimelineTarget (4, 7, "track-a", 3);


    @Test
    void ownsTheCompleteSurfaceAndPulsesOnlyTheCurrentTimelineStep ()
    {
        final CompiledWorkspace workspace = workspace (new ClipTimelineState (0));
        final var result = workspace.start (snapshot (timeline (BLUE)));

        assertEquals (Set.of (ControllerViewFacet.CLIP_TIMELINE), result.desiredControllerState ().workspace ().facets ());
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), result.desiredInputRoutes ().mode (PushControlIds.pad (57), InputKind.PAD));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), result.desiredInputRoutes ().mode (PushControlIds.pad (57), InputKind.POLY_PRESSURE));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), result.desiredInputRoutes ().mode (PushControlIds.button ("SCENE1"), InputKind.BUTTON));
        assertEquals (64, result.desiredOutput ().lights ().keySet ().stream ().filter (control -> control.value ().startsWith ("push.pad.")).count ());

        final ControllerLight playing = result.desiredOutput ().lights ().get (PushControlIds.pad (57));
        assertEquals (SESSION_PLAYING_GREEN, playing.color ());
        assertEquals (LightBlinkRate.NONE, playing.blinkRate ());
        final ControllerLight selectedEnd = result.desiredOutput ().lights ().get (PushControlIds.pad (58));
        assertEquals (BLUE, selectedEnd.color ());
        assertEquals (LightBlinkRate.NONE, selectedEnd.blinkRate ());
        assertEquals (WHITE, result.desiredOutput ().lights ().get (PushControlIds.pad (59)).color ());
        assertEquals (BLACK, result.desiredOutput ().lights ().get (PushControlIds.pad (61)).color ());
    }


    @Test
    void whiteClipLeavesUnselectedPadsOff ()
    {
        final RgbColor bitwigLightGray = new RgbColor (201, 201, 201);
        final var result = workspace (new ClipTimelineState (0)).start (snapshot (timeline (bitwigLightGray)));

        assertEquals (bitwigLightGray, result.desiredOutput ().lights ().get (PushControlIds.pad (58)).color ());
        assertEquals (BLACK, result.desiredOutput ().lights ().get (PushControlIds.pad (59)).color ());
    }


    @Test
    void playbackPositionMovesTheSinglePulsingPad ()
    {
        final ClipTimelineSnapshot secondStep = new ClipTimelineSnapshot (Optional.of (TARGET), 0, 8, 16, OptionalDouble.of (4), BLUE);
        final Map<ControlId, ControllerLight> lights = workspace (new ClipTimelineState (0)).start (snapshot (secondStep)).desiredOutput ().lights ();

        assertEquals (BLUE, lights.get (PushControlIds.pad (57)).color ());
        assertEquals (SESSION_PLAYING_GREEN, lights.get (PushControlIds.pad (58)).color ());
    }


    @Test
    void currentStepPulseFollowsReconstructedClipBeatPhase ()
    {
        final CompiledWorkspace workspace = workspace (new ClipTimelineState (0));
        final ClipTimelineSnapshot firstPhase = new ClipTimelineSnapshot (Optional.of (TARGET), 0, 8, 16, OptionalDouble.of (0.25), BLUE);
        final ClipTimelineSnapshot secondPhase = new ClipTimelineSnapshot (Optional.of (TARGET), 0, 8, 16, OptionalDouble.of (0.75), BLUE);
        final ControllerLight firstHalf = workspace.start (snapshot (firstPhase)).desiredOutput ().lights ().get (PushControlIds.pad (57));
        final ControllerLight secondHalf = workspace.handle (new SnapshotChangedEvent (2, 2), snapshot (secondPhase)).desiredOutput ().lights ().get (PushControlIds.pad (57));

        assertEquals (SESSION_PLAYING_GREEN, firstHalf.color ());
        assertEquals (BLUE, secondHalf.color ());
        assertEquals (LightBlinkRate.NONE, firstHalf.blinkRate ());
        assertEquals (LightBlinkRate.NONE, secondHalf.blinkRate ());
    }


    @Test
    void rangeGestureEmitsFrozenIdentityButDoesNotOptimisticallyChangeTheFrame ()
    {
        final CompiledWorkspace workspace = workspace (new ClipTimelineState (0));
        final ControllerSnapshot snapshot = snapshot (timeline (BLUE));
        workspace.start (snapshot);

        workspace.handle (pad (1, PushControlIds.pad (57), InputPhase.BEGIN), snapshot);
        final var result = workspace.handle (pad (2, PushControlIds.pad (59), InputPhase.END), snapshot);

        assertEquals (List.of (new SetClipTimelineRangeEffect (TARGET, 0, 12)), result.effects ());
        assertEquals (WHITE, result.desiredOutput ().lights ().get (PushControlIds.pad (59)).color ());
    }


    @Test
    void paddingPadsCannotStartARangeGesture ()
    {
        final CompiledWorkspace workspace = workspace (new ClipTimelineState (0));
        final ControllerSnapshot snapshot = snapshot (timeline (BLUE));
        workspace.start (snapshot);

        workspace.handle (pad (1, PushControlIds.pad (61), InputPhase.BEGIN), snapshot);
        final var result = workspace.handle (pad (2, PushControlIds.pad (61), InputPhase.END), snapshot);

        assertTrue (result.effects ().isEmpty ());
    }


    @Test
    void stoppedRangeAndPaddingStaySteady ()
    {
        final CompiledWorkspace workspace = workspace (new ClipTimelineState (0));
        final ClipTimelineSnapshot stopped = new ClipTimelineSnapshot (Optional.of (TARGET), 0, 8, 16, OptionalDouble.empty (), BLUE);
        final Map<ControlId, ControllerLight> lights = workspace.start (snapshot (stopped)).desiredOutput ().lights ();

        assertEquals (LightBlinkRate.NONE, lights.get (PushControlIds.pad (57)).blinkRate ());
        final ControllerLight padding = lights.get (PushControlIds.pad (62));
        assertEquals (BLACK, padding.color ());
        assertEquals (LightBlinkRate.NONE, padding.blinkRate ());
    }


    @Test
    void sceneResolutionIsSharedPolicyAndChangesFutureGestureGranularity ()
    {
        final ClipTimelineState state = new ClipTimelineState (0);
        final CompiledWorkspace workspace = workspace (state);
        final ControllerSnapshot snapshot = snapshot (timeline (BLUE));
        workspace.start (snapshot);

        workspace.handle (button (1, "SCENE2", InputPhase.BEGIN), snapshot);
        workspace.handle (pad (2, PushControlIds.pad (60), InputPhase.BEGIN), snapshot);
        final var result = workspace.handle (pad (3, PushControlIds.pad (60), InputPhase.END), snapshot);

        assertEquals (1, state.resolution ());
        assertEquals (List.of (new SetClipTimelineRangeEffect (TARGET, 3, 1)), result.effects ());
        assertEquals (new RgbColor (255, 84, 0), result.desiredOutput ().lights ().get (PushControlIds.button ("SCENE2")).color ());
    }


    @Test
    void identityChangeCancelsAnInFlightGesture ()
    {
        final CompiledWorkspace workspace = workspace (new ClipTimelineState (0));
        final ControllerSnapshot first = snapshot (timeline (BLUE));
        workspace.start (first);
        workspace.handle (pad (1, PushControlIds.pad (57), InputPhase.BEGIN), first);

        final ClipTimelineSnapshot replacement = new ClipTimelineSnapshot (Optional.of (new ClipTimelineTarget (5, 8, "track-b", 1)), 0, 8, 16, OptionalDouble.empty (), BLUE);
        final var result = workspace.handle (pad (2, PushControlIds.pad (57), InputPhase.END), snapshot (replacement));

        assertTrue (result.effects ().isEmpty ());
    }


    private static CompiledWorkspace workspace (final ClipTimelineState state)
    {
        return CompiledWorkspace.compile ("Clip Timeline test", List.of (new ClipTimelineView (state)));
    }


    private static ClipTimelineSnapshot timeline (final RgbColor color)
    {
        return new ClipTimelineSnapshot (Optional.of (TARGET), 0, 8, 16, OptionalDouble.of (0), color);
    }


    private static ControllerSnapshot snapshot (final ClipTimelineSnapshot timeline)
    {
        return snapshot (timeline, 1);
    }


    private static ControllerSnapshot snapshot (final ClipTimelineSnapshot timeline, final double transportPosition)
    {
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (
            new TransportSnapshot (true, true, true, false, false, false, false, false, false, 120, transportPosition, 4, 4),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (1, "CLIP_LENGTH", "TRACK", false, false, 0, GridPressureConfiguration.OFF),
            timeline,
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }


    private static ControllerInputEvent pad (final long sequence, final de.mossgrabers.pull.core.api.ControlId control, final InputPhase phase)
    {
        return new ControllerInputEvent (sequence, sequence, control, InputKind.PAD, phase, phase == InputPhase.END ? 0 : 127);
    }


    private static ControllerInputEvent button (final long sequence, final String name, final InputPhase phase)
    {
        return new ControllerInputEvent (sequence, sequence, PushControlIds.button (name), InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
    }
}
