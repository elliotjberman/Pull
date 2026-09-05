// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingNames;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/** Mapping-browser names retain the last selected-track observation within one valid document. */
public final class ControllerMappingNameView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("mapping-names", Set.of (), Set.of ());

    private final Map<String, String> trackNames = new LinkedHashMap<> ();
    private NameObservation lastObservation;
    private String documentId = "";
    private ControllerMappingNames names = ControllerMappingNames.empty ();


    @Override
    public String id ()
    {
        return "controller-mapping-names";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK);
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        final ControllerMappingStorageSnapshot storage = snapshot.bridge ().controllerMappingFeedback ().storage ();
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final NameObservation observation = new NameObservation (storage, selected.exists (), selected.channelId (), selected.name ());
        if (observation.equals (this.lastObservation))
            return;
        this.lastObservation = observation;
        if (!storage.available ())
        {
            this.names = ControllerMappingNames.empty ();
            return;
        }
        final TrackMappingRegistry registry = TrackMappingRegistry.parse (storage.documentId (), storage.value ()).orElse (null);
        if (registry == null)
        {
            this.trackNames.clear ();
            this.documentId = "";
            this.names = ControllerMappingNames.empty ();
            return;
        }
        if (!this.documentId.equals (registry.documentId ()))
            this.trackNames.clear ();
        this.documentId = registry.documentId ();
        this.trackNames.keySet ().retainAll (registry.trackIds ());

        if (selected.exists () && registry.bank (selected.channelId ()) >= 0)
            this.trackNames.put (selected.channelId (), truncate (selected.name ().strip (), ControllerMappingNames.MAX_NAME_LENGTH));

        final Map<ControllerMappingId, String> requestedNames = new LinkedHashMap<> ();
        for (int bank = 0; bank < registry.trackIds ().size (); bank++)
        {
            final String observedName = this.trackNames.get (registry.trackIds ().get (bank));
            if (observedName == null)
                continue;
            final String trackName = observedName.isEmpty () ? "Bank " + (bank + 1) : observedName;
            for (int slot = 0; slot < CoreControllerMappings.CONTROLS_PER_TRACK; slot++)
            {
                final String suffix = " — Drum Controller Toggle " + (slot + 1);
                requestedNames.put (CoreControllerMappings.trackBank (bank).get (slot), truncate (trackName, ControllerMappingNames.MAX_NAME_LENGTH - suffix.length ()) + suffix);
            }
        }
        this.names = requestedNames.isEmpty () ? ControllerMappingNames.empty () : new ControllerMappingNames (this.documentId, storage.revision (), requestedNames);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (Map.of (), Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (),
            DesiredNotePerformance.inactive (), DesiredNoteRepeat.unowned (), new DesiredControllerMappings (Set.of (), this.names));
    }


    private static String truncate (final String value, final int maxLength)
    {
        if (value.length () <= maxLength)
            return value;
        final int end = Character.isHighSurrogate (value.charAt (maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring (0, end);
    }


    private record NameObservation (ControllerMappingStorageSnapshot storage, boolean selectedExists, String selectedId, String selectedName)
    {
        // Other selected-track fields cannot change mapping names.
    }
}
