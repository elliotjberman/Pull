// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Observed bounded document storage; the reloadable core interprets its contents. */
public record ControllerMappingStorageSnapshot (boolean available, long revision, String documentId, String value)
{
    /** Maximum persisted characters in the installed storage slot. */
    public static final int MAX_LENGTH = 8192;

    private static final ControllerMappingStorageSnapshot EMPTY = new ControllerMappingStorageSnapshot (false, 0, "", "");


    /** Validate raw storage without interpreting its payload. */
    public ControllerMappingStorageSnapshot
    {
        documentId = Objects.requireNonNull (documentId, "documentId");
        value = Objects.requireNonNull (value, "value");
        if (revision < 0 || value.length () > MAX_LENGTH)
            throw new IllegalArgumentException ("invalid controller mapping storage bounds");
        if (available && documentId.isBlank ())
            throw new IllegalArgumentException ("available controller mapping storage requires a document identity");
        if (!available && (!documentId.isEmpty () || !value.isEmpty ()))
            throw new IllegalArgumentException ("unavailable controller mapping storage must be empty");
    }


    /** Get unavailable storage. */
    public static ControllerMappingStorageSnapshot empty ()
    {
        return EMPTY;
    }
}
