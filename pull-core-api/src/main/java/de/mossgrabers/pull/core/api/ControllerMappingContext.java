// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Selected target and observed document-storage fence for a mapping request. */
public record ControllerMappingContext (long targetGeneration, String channelId, long storageRevision, String documentId)
{
    private static final ControllerMappingContext EMPTY = new ControllerMappingContext (0, "", 0, "");


    /** Validate a complete owner or the empty compatibility owner. */
    public ControllerMappingContext
    {
        channelId = Objects.requireNonNull (channelId, "channelId");
        documentId = Objects.requireNonNull (documentId, "documentId");
        if (targetGeneration < 0 || storageRevision < 0 || channelId.isBlank () != documentId.isBlank ())
            throw new IllegalArgumentException ("invalid controller mapping context");
        if (channelId.isBlank () && (!channelId.isEmpty () || !documentId.isEmpty () || targetGeneration != 0 || storageRevision != 0))
            throw new IllegalArgumentException ("empty controller mapping context must have no owner");
    }


    /** Whether this context names an authoritative selected owner. */
    public boolean active ()
    {
        return !this.channelId.isEmpty ();
    }


    /** Get the owner used only by compatibility value constructors. */
    public static ControllerMappingContext empty ()
    {
        return EMPTY;
    }
}
