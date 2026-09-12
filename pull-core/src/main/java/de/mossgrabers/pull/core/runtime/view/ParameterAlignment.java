// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;


/** Joins independently sampled parameter and owner domains before admitting new work or feedback. */
final class ParameterAlignment
{
    private ParameterAlignment ()
    {
        // Utility class.
    }


    static boolean matches (final ParameterTargetSnapshot target, final String domain, final String owner)
    {
        return target != null && !owner.isBlank () && domain.equals (target.identity ().domain ()) && owner.equals (target.identity ().ownerId ());
    }


    static ParameterTargetSnapshot target (final ControllerSnapshot snapshot, final ParameterSlot slot)
    {
        if (slot == null)
            return null;
        final ParameterTargetSnapshot target = snapshot.bridge ().parameters ().slots ().get (slot);
        final boolean aligned = switch (slot.bank ())
        {
            case SELECTED_TRACK -> slot.index () < 2 && snapshot.bridge ().selectedTrack ().exists () && matches (target, slot.index () == 0 ? "channel-volume" : "channel-pan", snapshot.bridge ().selectedTrack ().channelId ());
            case SELECTED_TRACK_SENDS -> snapshot.bridge ().selectedTrack ().exists () && matches (target, "channel-send", snapshot.bridge ().selectedTrack ().channelId ());
            case SELECTED_DEVICE_REMOTE -> deviceMatches (snapshot, target, slot.index ());
            case PROJECT_REMOTE -> snapshot.bridge ().automation ().available () && matches (target, "project-remote", snapshot.bridge ().automation ().projectIdentity ());
            case MASTER -> masterContextAligned (snapshot) && matches (target, "project-master", snapshot.bridge ().master ().projectIdentity ()) && target.identity ().index () == slot.index ();
            default -> false;
        };
        return aligned ? target : null;
    }


    private static boolean deviceMatches (final ControllerSnapshot snapshot, final ParameterTargetSnapshot target, final int index)
    {
        final var state = DeviceRemoteControlsView.observedPage (snapshot);
        // The frozen menu publishes ParameterPageBankImpl's index within its eight-page window.
        // The opaque owner fences the full page; the retained target keeps its absolute index.
        return state != null && state.device ().exists () && matches (target, "retained-device-remote", state.parameterOwnerId ()) &&
            target.identity ().page () % ParameterSlot.BANK_SIZE == state.device ().selectedPage () && target.identity ().index () == index;
    }


    static boolean masterContextAligned (final ControllerSnapshot snapshot)
    {
        return snapshot.bridge ().master ().available () && (!snapshot.bridge ().automation ().available () || snapshot.bridge ().automation ().projectIdentity ().equals (snapshot.bridge ().master ().projectIdentity ()));
    }


    static boolean contradicts (final ControllerSnapshot snapshot, final ParameterSlot slot)
    {
        return slot != null && snapshot.bridge ().parameters ().slots ().containsKey (slot) && target (snapshot, slot) == null;
    }


    static Map<ControlId, ParameterSlot> bindings (final ControllerSnapshot snapshot, final Map<ControlId, ParameterSlot> declared)
    {
        final Map<ControlId, ParameterSlot> result = new LinkedHashMap<> ();
        declared.forEach ((control, slot) -> {
            if (target (snapshot, slot) != null)
                result.put (control, slot);
        });
        return Map.copyOf (result);
    }


    static Map<ParameterSlot, ParameterTargetSnapshot> targets (final ControllerSnapshot snapshot)
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> result = new LinkedHashMap<> ();
        snapshot.bridge ().parameters ().slots ().keySet ().forEach (slot -> {
            final ParameterTargetSnapshot target = target (snapshot, slot);
            if (target != null)
                result.put (slot, target);
        });
        return Map.copyOf (result);
    }

}
