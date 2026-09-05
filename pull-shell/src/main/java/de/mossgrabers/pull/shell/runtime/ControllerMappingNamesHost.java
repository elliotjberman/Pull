// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwAbsoluteControl;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingNames;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;


/** Writes complete presentation names onto existing controls without changing their identities. */
final class ControllerMappingNamesHost
{
    private final Map<ControllerMappingId, IHwAbsoluteControl> controls;
    private final Supplier<ControllerMappingStorageSnapshot> storage;
    private final Map<ControllerMappingId, String> appliedNames = new LinkedHashMap<> ();
    private ControllerMappingNames requested = ControllerMappingNames.empty ();


    ControllerMappingNamesHost (final Map<ControllerMappingId, IHwAbsoluteControl> controls, final Supplier<ControllerMappingStorageSnapshot> storage)
    {
        this.controls = Map.copyOf (Objects.requireNonNull (controls, "controls"));
        this.storage = Objects.requireNonNull (storage, "storage");
    }


    /** Replace the complete desired name set and apply it only in its observed storage context. */
    void request (final ControllerMappingNames requested)
    {
        final ControllerMappingNames checked = Objects.requireNonNull (requested, "requested");
        if (!this.controls.keySet ().containsAll (checked.names ().keySet ()))
            throw new IllegalArgumentException ("Requested controller mapping name endpoint is not installed");
        this.requested = checked;
        this.refresh ();
    }


    /** Revalidate the current context, including when no new core result has arrived. */
    void refresh ()
    {
        Map<ControllerMappingId, String> names = Map.of ();
        if (!this.requested.isEmpty ())
        {
            final ControllerMappingStorageSnapshot observed = this.storage.get ();
            if (observed.available () && observed.revision () == this.requested.storageRevision () &&
                observed.documentId ().equals (this.requested.documentId ()))
                names = this.requested.names ();
        }

        final var previous = this.appliedNames.entrySet ().iterator ();
        while (previous.hasNext ())
        {
            final var entry = previous.next ();
            if (names.containsKey (entry.getKey ()))
                continue;
            this.controls.get (entry.getKey ()).setName ("");
            previous.remove ();
        }

        for (final var entry: names.entrySet ())
        {
            if (entry.getValue ().equals (this.appliedNames.get (entry.getKey ())))
                continue;
            this.controls.get (entry.getKey ()).setName (entry.getValue ());
            this.appliedNames.put (entry.getKey (), entry.getValue ());
        }
    }
}
