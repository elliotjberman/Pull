// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;


/** Complete document-scoped mapping names; omitted endpoints return to their initialization names. */
public record ControllerMappingNames (String documentId, long storageRevision, Map<ControllerMappingId, String> names)
{
    /** Maximum named endpoints in the installed track inventory. */
    public static final int CAPACITY = CoreControllerMappings.TRACK_CONTROL_PADS.size ();

    /** Maximum characters in one display name. */
    public static final int MAX_NAME_LENGTH = 256;

    private static final ControllerMappingNames EMPTY = new ControllerMappingNames ("", 0, Map.of ());


    /** Validate bounded metadata independently of native matcher bindings. */
    public ControllerMappingNames
    {
        documentId = Objects.requireNonNull (documentId, "documentId");
        names = Map.copyOf (Objects.requireNonNull (names, "names"));
        if (storageRevision < 0 || names.size () > CAPACITY)
            throw new IllegalArgumentException ("invalid controller mapping name bounds");
        if (documentId.isBlank () && (!documentId.isEmpty () || storageRevision != 0 || !names.isEmpty ()))
            throw new IllegalArgumentException ("controller mapping names require a document owner");
        if (names.values ().stream ().anyMatch (name -> name.isBlank () || name.length () > MAX_NAME_LENGTH))
            throw new IllegalArgumentException ("invalid controller mapping display name");
    }


    /** Whether no endpoint has a requested display name. */
    public boolean isEmpty ()
    {
        return this.names.isEmpty ();
    }


    /** Restore all endpoint names to their initialization values. */
    public static ControllerMappingNames empty ()
    {
        return EMPTY;
    }
}
