// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;
import de.mossgrabers.pull.core.view.ViewOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final SelectedTrackBooleanToggles selectedTrackToggles;
    private final AuthoritativeBooleanToggle<String> launcherOverdub = new AuthoritativeBooleanToggle<> ();


    TransportControlView (final ProjectPlaybackCoordinator playbackCoordinator, final SelectedTrackBooleanToggles selectedTrackToggles)
    {
        this.playbackCoordinator = Objects.requireNonNull (playbackCoordinator, "playbackCoordinator");
        this.selectedTrackToggles = Objects.requireNonNull (selectedTrackToggles, "selectedTrackToggles");
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
            return new ViewOutput (Map.of (PLAY_BUTTON, play, RECORD_BUTTON, OFF), Map.of (), de.mossgrabers.pull.core.api.output.ControllerDisplayScene.empty (), this.playbackCoordinator.padGridOverlay (), this.playbackCoordinator.displayOverlay ());

        final RgbColor record;
        if (snapshot.pressedControls ().contains (SHIFT_BUTTON))
            record = transport.launcherOverdub () ? AMBER : WHITE;
        else
            record = snapshot.bridge ().selectedTrack ().recordArmed () ? RED : WHITE;
        return new ViewOutput (Map.of (PLAY_BUTTON, play, RECORD_BUTTON, record), Map.of (), de.mossgrabers.pull.core.api.output.ControllerDisplayScene.empty (), this.playbackCoordinator.padGridOverlay (), this.playbackCoordinator.displayOverlay ());
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        final boolean recordRelease = event instanceof final ControllerInputEvent input && isRecordRelease (input);
        final boolean shiftedRecord = recordRelease && snapshot.pressedControls ().contains (SHIFT_BUTTON);
        final boolean selectedRecord = recordRelease && snapshot.pressedControls ().contains (SELECT_BUTTON);
        effects.addAll (this.selectedTrackToggles.update (
            Set.of (SelectedTrackBoolean.RECORD_ARMED),
            recordRelease && !shiftedRecord && !selectedRecord ? Optional.of (SelectedTrackBoolean.RECORD_ARMED) : Optional.empty (),
            snapshot));
        effects.addAll (this.updateLauncherOverdub (shiftedRecord, snapshot));

        if (event instanceof final ControllerInputEvent input)
        {
            if (isPlayPress (input))
                effects.addAll (this.playbackCoordinator.playPressed (snapshot));
            else if (recordRelease && selectedRecord)
            {
                final SelectedTrackSnapshot selectedTrack = snapshot.bridge ().selectedTrack ();
                if (selectedTrack.exists ())
                    effects.add (new SelectedTrackActionEffect (selectedTrack.generation (), selectedTrack.channelId (), SelectedTrackAction.CREATE_NEW_CLIP));
            }
        }
        return List.copyOf (effects);
    }


    private static boolean isPlayPress (final ControllerInputEvent input)
    {
        return PLAY_BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.BEGIN;
    }


    private List<CoreEffect> updateLauncherOverdub (final boolean pressed, final ControllerSnapshot snapshot)
    {
        final ProjectSnapshot project = snapshot.bridge ().project ();
        final TransportSnapshot transport = snapshot.bridge ().transport ();
        if (!project.available () || project.commandPending () || !transport.available ())
        {
            this.launcherOverdub.clear ();
            return List.of ();
        }
        return this.launcherOverdub.update (
            project.projectIdentity (),
            transport.launcherOverdub (),
            snapshot.monotonicTimeNanos (),
            pressed,
            (identity, enabled) -> new SetProjectTransportStateEffect (identity, identity, TransportState.LAUNCHER_OVERDUB, enabled.booleanValue ()));
    }


    private static boolean isRecordRelease (final ControllerInputEvent input)
    {
        return RECORD_BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.END;
    }
}
