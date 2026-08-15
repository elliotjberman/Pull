// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipTimelineSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.ControllerLight;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** Core-owned selected audio-clip timeline, range gesture, and complete Push feedback. */
public final class ClipTimelineView implements ControllerView
{
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor GREEN = new RgbColor (0, 255, 0);
    private static final RgbColor RESOLUTION = new RgbColor (110, 44, 0);
    private static final RgbColor RESOLUTION_SELECTED = new RgbColor (255, 84, 0);
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.CLIP_TIMELINE, BridgeSubscription.TRANSPORT);
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "complete",
        Set.of (
            new SurfaceClaim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.GRID_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.GRID_LOWER, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT)),
        Set.of (ControllerViewFacet.CLIP_TIMELINE));
    private static final Map<ControlId, Integer> TIMELINE_PADS = timelinePads ();
    private static final Map<ControlId, Integer> SCENE_BUTTONS = sceneButtons ();

    private final ClipTimelineState state;


    /** Constructor. */
    public ClipTimelineView (final ClipTimelineState state)
    {
        this.state = Objects.requireNonNull (state, "state");
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "clip-timeline";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    /** {@inheritDoc} */
    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.state.reconcile (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input))
            return List.of ();
        if (input.kind () == InputKind.PAD)
        {
            final Integer pad = TIMELINE_PADS.get (input.controlId ());
            return pad == null ? List.of () : this.state.handlePad (pad.intValue (), input, snapshot);
        }
        if (input.kind () == InputKind.BUTTON && input.phase () == InputPhase.BEGIN)
        {
            final Integer scene = SCENE_BUTTONS.get (input.controlId ());
            if (scene != null && scene.intValue () < 3)
                this.state.setResolution (scene.intValue ());
        }
        return List.of ();
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ClipTimelineSnapshot timeline = snapshot.bridge ().clipTimeline ();
        final double quartersPerPad = ClipTimelineState.quartersPerPad (snapshot.bridge ().transport (), this.state.padsPerMeasure ());
        final Map<ControlId, ControllerLight> lights = new LinkedHashMap<> (72);
        int start = 0;
        int end = 0;
        int selectableEnd = 0;
        int playingPad = -1;
        RgbColor outside = BLACK;
        if (timeline.available () && quartersPerPad > 0)
        {
            final double maximum = quartersPerPad * 64;
            start = clamp ((int) Math.floor (Math.max (0, timeline.loopStart ()) / quartersPerPad), 0, 64);
            end = clamp ((int) Math.ceil (Math.min (maximum, timeline.loopStart () + timeline.loopLength ()) / quartersPerPad), 0, 64);
            selectableEnd = clamp ((int) Math.ceil (Math.min (maximum, timeline.selectableEnd ()) / quartersPerPad), 0, 64);
            outside = isNearWhite (timeline.color ()) ? BLACK : WHITE;
            if (timeline.currentStep () >= 0)
                playingPad = (int) Math.floor (timeline.currentStep () * timeline.stepLength () / quartersPerPad);
        }

        for (int timelinePad = 0; timelinePad < 64; timelinePad++)
        {
            final int column = timelinePad % 8;
            final int physicalRow = 7 - timelinePad / 8;
            final RgbColor color = timelinePad >= selectableEnd ? BLACK : timelinePad >= start && timelinePad < end ? timeline.color () : outside;
            final ControllerLight light = timelinePad == playingPad && timelinePad < selectableEnd ? ControllerLight.playing (color, GREEN) : ControllerLight.steady (color);
            lights.put (PushControlIds.pad (physicalRow * 8 + column + 1), light);
        }

        for (int scene = 0; scene < 8; scene++)
        {
            final RgbColor color = scene >= 3 ? BLACK : scene == this.state.resolution () ? RESOLUTION_SELECTED : RESOLUTION;
            lights.put (PushControlIds.button ("SCENE" + (scene + 1)), ControllerLight.steady (color));
        }
        return new ViewOutput (lights, Map.of ());
    }


    private static Map<ControlId, Integer> timelinePads ()
    {
        final Map<ControlId, Integer> pads = new LinkedHashMap<> (64);
        for (int physical = 0; physical < 64; physical++)
        {
            final int column = physical % 8;
            final int physicalRow = physical / 8;
            pads.put (PushControlIds.pad (physical + 1), Integer.valueOf ((7 - physicalRow) * 8 + column));
        }
        return Map.copyOf (pads);
    }


    private static Map<ControlId, Integer> sceneButtons ()
    {
        final Map<ControlId, Integer> scenes = new LinkedHashMap<> (8);
        for (int scene = 0; scene < 8; scene++)
            scenes.put (PushControlIds.button ("SCENE" + (scene + 1)), Integer.valueOf (scene));
        return Map.copyOf (scenes);
    }


    private static boolean isNearWhite (final RgbColor color)
    {
        final int minimum = Math.min (color.red (), Math.min (color.green (), color.blue ()));
        final int maximum = Math.max (color.red (), Math.max (color.green (), color.blue ()));
        return minimum >= 190 && maximum - minimum <= 8;
    }


    private static int clamp (final int value, final int minimum, final int maximum)
    {
        return Math.max (minimum, Math.min (maximum, value));
    }
}
