// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Bounded note-controller views installed by the stable Push shell. */
public enum ControllerNoteView
{
    /** No note-view override. */
    NONE,
    /** Melodic scale layout. */
    PLAY,
    /** Chord layout. */
    CHORDS,
    /** Piano layout. */
    PIANO,
    /** 64-pad drum layout. */
    DRUM64,
    /** Drum sequencer. */
    DRUM,
    /** Pull 4x4 drum-controller layout. */
    DRUM_PAD,
    /** Four-lane drum sequencer. */
    DRUM4,
    /** Eight-lane drum sequencer. */
    DRUM8,
    /** XoX drum sequencer. */
    DRUM_XOX,
    /** Melodic sequencer. */
    SEQUENCER,
    /** Raindrops sequencer. */
    RAINDROPS,
    /** Polyphonic sequencer. */
    POLY_SEQUENCER,
    /** Audio clip-length editor. */
    CLIP_LENGTH;


    /** Resolve a stable view identifier, failing closed for non-note views. */
    public static ControllerNoteView fromStableId (final String viewId)
    {
        if (viewId == null || viewId.isBlank ())
            return NONE;
        try
        {
            return valueOf (viewId);
        }
        catch (final IllegalArgumentException ignored)
        {
            return NONE;
        }
    }


    /** Test whether this value requests an installed note-controller view. */
    public boolean isPresent ()
    {
        return this != NONE;
    }
}
