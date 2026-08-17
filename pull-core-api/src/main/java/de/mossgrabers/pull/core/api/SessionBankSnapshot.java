// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/** Authoritative identity and visible-track state for the active bounded Session bank. */
public record SessionBankSnapshot (long generation, SessionBankShape shape, int trackOffset, int sceneOffset, List<SessionTrackSnapshot> tracks)
{
    private static final SessionBankSnapshot EMPTY = new SessionBankSnapshot (0, SessionBankShape.empty (), -1, -1, List.of ());


    /** Validate and copy the bounded bank. */
    public SessionBankSnapshot
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        shape = Objects.requireNonNull (shape, "shape");
        if (trackOffset < -1 || sceneOffset < -1)
            throw new IllegalArgumentException ("Session offsets must be -1 or greater");
        tracks = List.copyOf (Objects.requireNonNull (tracks, "tracks"));
        if (!shape.isPresent ())
        {
            if (trackOffset != -1 || sceneOffset != -1 || !tracks.isEmpty ())
                throw new IllegalArgumentException ("an empty Session bank cannot contain visible state");
        }
        else
        {
            if (trackOffset < 0 || sceneOffset < 0 || tracks.size () != shape.tracks ())
                throw new IllegalArgumentException ("a Session bank must contain exactly one track slot per visible column");
            final Set<String> identities = new HashSet<> ();
            for (final SessionTrackSnapshot track: tracks)
            {
                if (track.exists () && !identities.add (track.channelId ()))
                    throw new IllegalArgumentException ("visible Session tracks must have unique channel IDs");
            }
        }
    }


    /** Get the unsubscribed value. */
    public static SessionBankSnapshot empty ()
    {
        return EMPTY;
    }
}
