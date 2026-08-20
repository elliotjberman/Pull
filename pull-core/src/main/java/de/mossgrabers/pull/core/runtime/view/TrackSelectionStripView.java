// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionTrackEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Track labels and direct track selection on the bottom display strip.
 */
public final class TrackSelectionStripView implements ControllerView
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor RECORD_ARMED = new RgbColor (255, 0, 0);
    private static final ControlId STOP_CLIP = PushControlIds.button ("STOP_CLIP");
    private static final ControllerActionIntent SELECT_TRACK = new ControllerActionIntent (
        ControllerActionId.SELECT_VISIBLE_TRACK,
        Set.of (ControllerStateScope.ACTIVE_PARAMETERS));
    private static final ControllerActionIntent STOP_TRACK = new ControllerActionIntent (
        ControllerActionId.STOP_VISIBLE_SESSION_TRACK,
        Set.of (ControllerStateScope.SESSION_PLAYBACK));
    private static final List<ControlId> TRACK_BUTTONS = trackButtons ();
    private static final Set<ControllerActionBinding> ACTION_BINDINGS = trackActionBindings ();
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "default",
        Set.of (
            new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());

    private final SessionStopGesture stopGesture;


    /** Construct a standalone track strip. */
    public TrackSelectionStripView ()
    {
        this (new SessionStopGesture ());
    }


    /** Construct a track strip sharing the active Session view's Stop gesture. */
    public TrackSelectionStripView (final SessionStopGesture stopGesture)
    {
        this.stopGesture = java.util.Objects.requireNonNull (stopGesture, "stopGesture");
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "track-selection-strip";
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
        return Set.of (BridgeSubscription.SESSION_BANK);
    }


    /** {@inheritDoc} */
    @Override
    public Set<ControllerActionBinding> actionBindings ()
    {
        return ACTION_BINDINGS;
    }


    /** {@inheritDoc} */
    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final int index = TRACK_BUTTONS.indexOf (input.controlId ());
        final SessionBankSnapshot bank = snapshot.bridge ().sessionBank ();
        if (snapshot.pressedControls ().contains (STOP_CLIP))
        {
            // This strip owns the row edge in VS Live while SessionView owns the held Stop
            // modifier and shared gesture lifetime. Capture the exact row target instead of
            // selecting it or letting Stop fall through to the selected track.
            this.stopGesture.consume ();
            if (index < 0 || !bank.shape ().isPresent () || index >= bank.tracks ().size ())
                return ResolvedControllerAction.of (binding.intent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK), List::of);
            final SessionTrackSnapshot track = bank.tracks ().get (index);
            if (!track.exists ())
                return ResolvedControllerAction.of (binding.intent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK), List::of);
            final StopSessionTrackEffect effect = new StopSessionTrackEffect (bank.generation (), bank.shape (), index, track.channelId (), true);
            return ResolvedControllerAction.of (binding.intent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK), () -> List.of (effect));
        }
        if (index < 0 || !bank.shape ().isPresent () || index >= bank.tracks ().size ())
            return ResolvedControllerAction.of (binding.intent (ControllerActionId.SELECT_VISIBLE_TRACK), List::of);
        final SessionTrackSnapshot track = bank.tracks ().get (index);
        if (!track.exists ())
            return ResolvedControllerAction.of (binding.intent (ControllerActionId.SELECT_VISIBLE_TRACK), List::of);
        final SelectSessionTrackEffect effect = new SelectSessionTrackEffect (bank.generation (), bank.shape (), index, track.channelId ());
        return ResolvedControllerAction.of (binding.intent (ControllerActionId.SELECT_VISIBLE_TRACK), () -> List.of (effect));
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final List<SessionTrackSnapshot> tracks = snapshot.bridge ().sessionBank ().tracks ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int index = 0; index < TRACK_BUTTONS.size (); index++)
        {
            final SessionTrackSnapshot track = index < tracks.size () ? tracks.get (index) : SessionTrackSnapshot.empty ();
            lights.put (TRACK_BUTTONS.get (index), !track.exists () || !track.activated () ? OFF : track.recordArmed () ? RECORD_ARMED : track.color ());
        }
        return new ViewOutput (
            lights,
            Map.of (),
            TrackFooterDisplayScene.render (tracks));
    }


    private static List<ControlId> trackButtons ()
    {
        final ArrayList<ControlId> controls = new ArrayList<> (8);
        for (int index = 1; index <= 8; index++)
            controls.add (PushControlIds.button ("ROW1_" + index));
        return List.copyOf (controls);
    }


    private static Set<ControllerActionBinding> trackActionBindings ()
    {
        return TRACK_BUTTONS.stream ()
            .map (control -> new ControllerActionBinding (control, InputKind.BUTTON, Set.of (SELECT_TRACK, STOP_TRACK)))
            .collect (java.util.stream.Collectors.toUnmodifiableSet ());
    }
}
