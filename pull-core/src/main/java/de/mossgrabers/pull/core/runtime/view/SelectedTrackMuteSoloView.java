// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;


/** Selected-track Mute and Solo controls, independent of the active page or grid view. */
public final class SelectedTrackMuteSoloView implements ControllerView
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor AVAILABLE = new RgbColor (30, 30, 30);
    private static final RgbColor MUTED = new RgbColor (39, 27, 0);
    private static final RgbColor SOLOED = new RgbColor (89, 89, 0);
    private static final ControlId MUTE = PushControlIds.button ("MUTE");
    private static final ControlId SOLO = PushControlIds.button ("SOLO");
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "default",
        Set.of (
            new SurfaceClaim (SurfaceArea.MUTE_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.MUTE_BUTTON, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SOLO_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOLO_BUTTON, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "selected-track-mute-solo";
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
        return Set.of (BridgeSubscription.SELECTED_TRACK);
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        if (!selected.exists ())
            return new ViewOutput (Map.of (MUTE, OFF, SOLO, OFF), Map.of ());
        return new ViewOutput (Map.of (
            MUTE, selected.muted () ? MUTED : AVAILABLE,
            SOLO, selected.soloed () ? SOLOED : AVAILABLE), Map.of ());
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.BUTTON || input.phase () != InputPhase.END)
            return List.of ();

        final SelectedTrackBoolean property;
        if (MUTE.equals (input.controlId ()))
            property = SelectedTrackBoolean.MUTED;
        else if (SOLO.equals (input.controlId ()))
            property = SelectedTrackBoolean.SOLOED;
        else
            return List.of ();

        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        if (!selected.exists ())
            return List.of ();
        final boolean enabled = property == SelectedTrackBoolean.MUTED ? !selected.muted () : !selected.soloed ();
        return List.of (new SetSelectedTrackBooleanEffect (selected.generation (), selected.channelId (), property, enabled));
    }
}
