// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.List;
import java.util.Objects;

/** Eight tracks from the current main/effect bank, with a separately fenced main-parent cursor. */
public record CurrentTrackBankSnapshot (long generation, String bankId, int offset, List<CurrentTrackSnapshot> tracks, String cursorChannelId, boolean cursorPinned, long parentGeneration, boolean parentAvailable, BankNavigationSnapshot trackNavigation, BankNavigationSnapshot sceneNavigation, int sceneOffset, long navigationGeneration)
{
    public static final int CAPACITY = 8;

    public CurrentTrackBankSnapshot
    {
        bankId = Objects.requireNonNull (bankId, "bankId");
        tracks = List.copyOf (Objects.requireNonNull (tracks, "tracks"));
        cursorChannelId = Objects.requireNonNull (cursorChannelId, "cursorChannelId");
        trackNavigation = Objects.requireNonNull (trackNavigation, "trackNavigation");
        sceneNavigation = Objects.requireNonNull (sceneNavigation, "sceneNavigation");
        if (generation < 0 || parentGeneration < 0 || navigationGeneration < 0 || offset < 0 || sceneOffset < 0)
            throw new IllegalArgumentException ("bank generations and offset cannot be negative");
        if (!tracks.isEmpty () && (tracks.size () != CAPACITY || bankId.isBlank () || generation == 0))
            throw new IllegalArgumentException ("available current banks require exactly eight slots and an identity");
        if (tracks.isEmpty () && (!bankId.isEmpty () || generation != 0 || offset != 0 || !cursorChannelId.isEmpty () || cursorPinned || parentGeneration != 0 || parentAvailable || navigationGeneration != 0 || sceneOffset != 0 || !trackNavigation.equals (BankNavigationSnapshot.empty ()) || !sceneNavigation.equals (BankNavigationSnapshot.empty ())))
            throw new IllegalArgumentException ("unavailable current banks cannot contain state");
        if (parentAvailable && (parentGeneration == 0 || cursorChannelId.isBlank ()))
            throw new IllegalArgumentException ("parent navigation requires an observed cursor");
    }

    /** Compatibility snapshot without the independently fenced navigation domain. */
    public CurrentTrackBankSnapshot (final long generation, final String bankId, final int offset, final List<CurrentTrackSnapshot> tracks, final String cursorChannelId, final boolean cursorPinned, final long parentGeneration, final boolean parentAvailable)
    {
        this (generation, bankId, offset, tracks, cursorChannelId, cursorPinned, parentGeneration, parentAvailable, BankNavigationSnapshot.empty (), BankNavigationSnapshot.empty (), 0, 0);
    }

    public static CurrentTrackBankSnapshot empty ()
    {
        return new CurrentTrackBankSnapshot (0, "", 0, List.of (), "", false, 0, false);
    }
}
