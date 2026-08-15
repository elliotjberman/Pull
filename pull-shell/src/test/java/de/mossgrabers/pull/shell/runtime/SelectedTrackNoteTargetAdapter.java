// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;


/** Test adapter with inert selected-track mutations. */
abstract class SelectedTrackNoteTargetAdapter implements ISelectedTrackNoteTarget
{
    protected long   generation = 1;
    protected String channelID  = "track-a";


    final void switchTo (final long newGeneration, final String newChannelID)
    {
        this.generation = newGeneration;
        this.channelID = newChannelID;
    }


    @Override
    public long getGeneration ()
    {
        return this.generation;
    }


    @Override
    public String getChannelID ()
    {
        return this.channelID;
    }


    @Override
    public boolean doesExist ()
    {
        return true;
    }


    @Override
    public boolean canHoldNotes ()
    {
        return true;
    }


    @Override
    public boolean hasDrumDevice ()
    {
        return false;
    }


    @Override
    public int getPlayingVelocity (final int note)
    {
        return 0;
    }


    @Override
    public void setActivated (final boolean activated)
    {
        // Inert by default.
    }


    @Override
    public void setGroupExpanded (final boolean expanded)
    {
        // Inert by default.
    }


    @Override
    public void setArmed (final boolean armed)
    {
        // Inert by default.
    }


    @Override
    public void setMonitorMode (final SelectedTrackMonitorMode mode)
    {
        // Inert by default.
    }


    @Override
    public void setMuted (final boolean muted)
    {
        // Inert by default.
    }


    @Override
    public void setSoloed (final boolean soloed)
    {
        // Inert by default.
    }


    @Override
    public void setVolume (final double normalizedVolume)
    {
        // Inert by default.
    }


    @Override
    public void setPan (final double normalizedPan)
    {
        // Inert by default.
    }


    @Override
    public void stop ()
    {
        // Inert by default.
    }


    @Override
    public void returnToArrangement ()
    {
        // Inert by default.
    }
}
