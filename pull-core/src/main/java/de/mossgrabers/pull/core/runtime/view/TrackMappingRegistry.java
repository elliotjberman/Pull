// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.CoreControllerMappings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Append-only v1 bank ownership. Deleted tracks retain their slots so undo can recover them.
 *
 * TODO(Pull architecture): revisit document-copy/native-binding semantics and safe bank reclamation
 * with a richer ownership contract; this bounded v1 never recycles historical assignments.
 */
public record TrackMappingRegistry (String documentId, List<String> trackIds)
{
    /** Validate persisted identities independently of current track position or name. */
    public TrackMappingRegistry
    {
        trackIds = List.copyOf (trackIds);
        if (!isUuid (documentId) || trackIds.size () > CoreControllerMappings.TRACK_BANK_COUNT || new HashSet<> (trackIds).size () != trackIds.size () || trackIds.stream ().anyMatch (id -> !isUuid (id)))
            throw new IllegalArgumentException ("invalid controller mapping registry");
    }


    /** Read a registry only for the currently observed document; an empty setting starts fresh. */
    public static Optional<TrackMappingRegistry> parse (final String documentId, final String value)
    {
        try
        {
            if (value.isEmpty ())
                return Optional.of (new TrackMappingRegistry (documentId, List.of ()));
            final String [] fields = value.split ("\n", -1);
            if (fields.length < 2 || !fields[0].equals ("v1") || !fields[1].equals (documentId))
                return Optional.empty ();
            return Optional.of (new TrackMappingRegistry (documentId, List.of (fields).subList (2, fields.length)));
        }
        catch (final IllegalArgumentException ex)
        {
            return Optional.empty ();
        }
    }


    /** Existing zero-based bank index, or -1 when this track has never been allocated. */
    public int bank (final String trackId)
    {
        return this.trackIds.indexOf (trackId);
    }


    /** Whether no additional historical track identities can be allocated. */
    public boolean full ()
    {
        return this.trackIds.size () == CoreControllerMappings.TRACK_BANK_COUNT;
    }


    /** Allocate the next unused bank without changing any existing assignment. */
    public TrackMappingRegistry append (final String trackId)
    {
        if (this.bank (trackId) >= 0)
            return this;
        final List<String> appended = new ArrayList<> (this.trackIds);
        appended.add (trackId);
        return new TrackMappingRegistry (this.documentId, appended);
    }


    /** Serialize one versioned payload, including the owning document UUID. */
    public String encode ()
    {
        return "v1\n" + this.documentId + (this.trackIds.isEmpty () ? "" : "\n" + String.join ("\n", this.trackIds));
    }


    /** Check the complete host UUID shape rather than accepting UUID's abbreviated syntax. */
    public static boolean isUuid (final String value)
    {
        if (value == null || value.length () != 36)
            return false;
        try
        {
            return UUID.fromString (value).toString ().equalsIgnoreCase (value);
        }
        catch (final IllegalArgumentException ex)
        {
            return false;
        }
    }
}
