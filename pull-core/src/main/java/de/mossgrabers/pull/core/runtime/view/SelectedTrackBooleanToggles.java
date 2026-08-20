// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/** Serializes selected-track toggles across later authoritative read-back. */
final class SelectedTrackBooleanToggles
{
    private final Map<SelectedTrackBoolean, AuthoritativeBooleanToggle<Target>> lanes = new EnumMap<> (SelectedTrackBoolean.class);


    SelectedTrackBooleanToggles ()
    {
        for (final SelectedTrackBoolean property: SelectedTrackBoolean.values ())
            this.lanes.put (property, new AuthoritativeBooleanToggle<> ());
    }


    /** Advance the selected properties and optionally register one physical toggle request. */
    List<CoreEffect> update (final Set<SelectedTrackBoolean> properties, final Optional<SelectedTrackBoolean> pressed, final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        if (!selected.exists ())
        {
            properties.forEach (property -> this.lanes.get (property).clear ());
            return List.of ();
        }

        final Target target = new Target (selected.generation (), selected.channelId ());
        final List<CoreEffect> effects = new ArrayList<> (properties.size ());
        for (final SelectedTrackBoolean property: properties)
        {
            effects.addAll (this.lanes.get (property).update (
                target,
                value (selected, property),
                snapshot.monotonicTimeNanos (),
                pressed.filter (property::equals).isPresent (),
                (identity, enabled) -> new SetSelectedTrackBooleanEffect (identity.generation (), identity.channelId (), property, enabled.booleanValue ())));
        }
        return List.copyOf (effects);
    }


    private static boolean value (final SelectedTrackSnapshot selected, final SelectedTrackBoolean property)
    {
        return switch (property)
        {
            case ACTIVATED -> selected.activated ();
            case GROUP_EXPANDED -> selected.groupExpanded ();
            case RECORD_ARMED -> selected.recordArmed ();
            case MUTED -> selected.muted ();
            case SOLOED -> selected.soloed ();
        };
    }


    private record Target (long generation, String channelId)
    {
    }
}
