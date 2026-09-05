// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingTarget;
import de.mossgrabers.pull.core.api.ControllerMappingValue;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerMappingStorageEffect;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Four Bitwig-mappable control pads with authoritative mapped-state toggle behavior and feedback. */
public final class DrumControlPadView implements ControllerView
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor ON = new RgbColor (255, 0, 0);
    private static final RgbColor STORAGE_ERROR = new RgbColor (255, 100, 0);
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "control-pads",
        Set.of (
            new SurfaceClaim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());

    private SetControllerMappingStorageEffect pendingStorage;


    @Override
    public String id ()
    {
        return "drum-control-pads";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK);
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof SnapshotChangedEvent) && !(event instanceof ControllerTickEvent))
            return List.of ();
        final ControllerMappingStorageSnapshot storage = snapshot.bridge ().controllerMappingFeedback ().storage ();
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final TrackMappingRegistry registry = eligible (snapshot) ? TrackMappingRegistry.parse (storage.documentId (), storage.value ()).orElse (null) : null;
        if (registry == null || registry.bank (selected.channelId ()) >= 0 || registry.full ())
        {
            this.pendingStorage = null;
            return List.of ();
        }

        final SetControllerMappingStorageEffect request = new SetControllerMappingStorageEffect (context (snapshot), storage.value (), registry.append (selected.channelId ()).encode ());
        if (request.equals (this.pendingStorage))
            return List.of ();
        this.pendingStorage = request;
        return List.of (request);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ControllerMappingFeedbackSnapshot feedback = snapshot.bridge ().controllerMappingFeedback ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final Set<ControllerMappingBinding> bindings = new LinkedHashSet<> (CoreControls.DRUM_CONTROL_PADS.size ());
        final ControllerMappingStorageSnapshot storage = feedback.storage ();
        final boolean eligible = eligible (snapshot);
        final TrackMappingRegistry registry = eligible ? TrackMappingRegistry.parse (storage.documentId (), storage.value ()).orElse (null) : null;
        final int bank = registry == null ? -1 : registry.bank (snapshot.bridge ().selectedTrack ().channelId ());
        final boolean storageError = eligible && (registry == null || bank < 0 && registry.full ());
        final List<ControllerMappingId> mappingIds = bank < 0 ? List.of () : CoreControllerMappings.trackBank (bank);
        final boolean ready = bank >= 0 && mappingIds.stream ().allMatch (feedback::supports);
        for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
        {
            final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (slot);
            final var mappingId = ready ? mappingIds.get (slot) : null;
            final ControllerMappingTarget target = ready ? feedback.targets ().get (mappingId) : null;
            final boolean on = ready && target.hasTarget () && target.value () >= 0.5;
            lights.put (control, storageError ? STORAGE_ERROR : on ? ON : OFF);
            if (ready)
                bindings.add (new ControllerMappingBinding (control, mappingId, on ? ControllerMappingValue.MINIMUM : ControllerMappingValue.MAXIMUM, context (snapshot)));
        }
        return new ViewOutput (
            lights,
            Map.of (),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            DesiredNotePerformance.inactive (),
            DesiredNoteRepeat.unowned (),
            ready ? new DesiredControllerMappings (bindings) : DesiredControllerMappings.empty ());
    }


    private static boolean eligible (final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        return snapshot.bridge ().layout ().drumLayoutActive () && snapshot.bridge ().layout ().drumControllerEngaged () &&
            selected.exists () && selected.canHoldNotes () && TrackMappingRegistry.isUuid (selected.channelId ()) &&
            snapshot.bridge ().controllerMappingFeedback ().storage ().available ();
    }


    private static ControllerMappingContext context (final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final ControllerMappingStorageSnapshot storage = snapshot.bridge ().controllerMappingFeedback ().storage ();
        return new ControllerMappingContext (selected.generation (), selected.channelId (), storage.revision (), storage.documentId ());
    }
}
