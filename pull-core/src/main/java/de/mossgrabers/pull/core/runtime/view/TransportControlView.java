// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Persistent Push transport lights and Record behavior with observed Shift/Select modifiers.
 */
public final class TransportControlView implements ControllerView
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor RED = new RgbColor (255, 0, 0);
    private static final RgbColor AMBER = new RgbColor (255, 191, 0);
    private static final ControlId PLAY_BUTTON = PushControlIds.button ("PLAY");
    private static final ControlId RECORD_BUTTON = PushControlIds.button ("RECORD");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT_BUTTON = PushControlIds.button ("SELECT");
    private static final Set<SurfaceClaim> CLAIMS = Set.of (
        new SurfaceClaim (SurfaceArea.PLAY_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.RECORD_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.PLAY_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.RECORD_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.SELECT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT));
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", CLAIMS, Set.of ());
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (
        BridgeSubscription.SELECTED_TRACK,
        BridgeSubscription.TRANSPORT,
        BridgeSubscription.PROJECT);

    private final ProjectPlaybackCoordinator playbackCoordinator;


    TransportControlView (final ProjectPlaybackCoordinator playbackCoordinator)
    {
        this.playbackCoordinator = java.util.Objects.requireNonNull (playbackCoordinator, "playbackCoordinator");
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "transport-control";
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
        this.playbackCoordinator.observe (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final TransportSnapshot transport = snapshot.bridge ().transport ();
        final RgbColor play = this.playbackCoordinator.playColor (snapshot);
        if (!transport.engineActive ())
            return new ViewOutput (Map.of (PLAY_BUTTON, play, RECORD_BUTTON, OFF), Map.of (), de.mossgrabers.pull.core.api.output.ControllerDisplayScene.empty (), this.playbackCoordinator.padGridOverlay (snapshot), this.playbackCoordinator.displayOverlay (snapshot));

        final RgbColor record;
        if (snapshot.pressedControls ().contains (SHIFT_BUTTON))
            record = transport.launcherOverdub () ? AMBER : WHITE;
        else
            record = snapshot.bridge ().selectedTrack ().recordArmed () ? RED : WHITE;
        return new ViewOutput (Map.of (PLAY_BUTTON, play, RECORD_BUTTON, record), Map.of (), de.mossgrabers.pull.core.api.output.ControllerDisplayScene.empty (), this.playbackCoordinator.padGridOverlay (snapshot), this.playbackCoordinator.displayOverlay (snapshot));
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (event instanceof final ControllerInputEvent input)
        {
            if (isPlayPress (input))
                return this.playbackCoordinator.playPressed (snapshot);
            if (!isRecordRelease (input))
                return List.of ();

            return this.recordReleased (snapshot);
        }

        if (event instanceof SnapshotChangedEvent || event instanceof ControllerTickEvent)
            return this.playbackCoordinator.advance (snapshot, true);
        return List.of ();
    }


    private List<CoreEffect> recordReleased (final ControllerSnapshot snapshot)
    {

        if (snapshot.pressedControls ().contains (SHIFT_BUTTON))
        {
            if (!snapshot.bridge ().transport ().available ())
                return List.of ();
            return List.of (new SetTransportStateEffect (TransportState.LAUNCHER_OVERDUB, !snapshot.bridge ().transport ().launcherOverdub ()));
        }

        final SelectedTrackSnapshot selectedTrack = snapshot.bridge ().selectedTrack ();
        if (!selectedTrack.exists ())
            return List.of ();

        if (snapshot.pressedControls ().contains (SELECT_BUTTON))
            return List.of (new SelectedTrackActionEffect (selectedTrack.generation (), selectedTrack.channelId (), SelectedTrackAction.CREATE_NEW_CLIP));

        return List.of (new SetSelectedTrackBooleanEffect (
            selectedTrack.generation (),
            selectedTrack.channelId (),
            SelectedTrackBoolean.RECORD_ARMED,
            !selectedTrack.recordArmed ()));
    }


    private static boolean isPlayPress (final ControllerInputEvent input)
    {
        return PLAY_BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.BEGIN;
    }


    private static boolean isRecordRelease (final ControllerInputEvent input)
    {
        return RECORD_BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.END;
    }
}
