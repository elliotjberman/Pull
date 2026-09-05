// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

/** Core-owned Track subpage state shared by normal and VS compositions and their checkpoint. */
public final class TrackMixerPageState
{
    private boolean inputOutputSelected;
    private int sendOffset;
    private long revision;

    public TrackMixerPageState ()
    {
        this (false, 0);
    }

    public TrackMixerPageState (final boolean inputOutputSelected, final int sendOffset)
    {
        requireOffset (sendOffset);
        this.inputOutputSelected = inputOutputSelected;
        this.sendOffset = sendOffset;
    }

    public boolean inputOutputSelected () { return this.inputOutputSelected; }
    public int sendOffset () { return this.sendOffset; }
    public long revision () { return this.revision; }

    public void selectInputOutput (final boolean selected)
    {
        if (this.inputOutputSelected != selected)
        {
            this.inputOutputSelected = selected;
            this.revision++;
        }
    }

    public void selectSendOffset (final int offset)
    {
        requireOffset (offset);
        if (this.sendOffset != offset)
        {
            this.sendOffset = offset;
            this.revision++;
        }
    }

    private static void requireOffset (final int offset)
    {
        if (offset != 0 && offset != 4)
            throw new IllegalArgumentException ("Track send offset must be 0 or 4");
    }
}
