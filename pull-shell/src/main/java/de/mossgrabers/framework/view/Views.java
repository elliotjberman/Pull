// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.view;



/**
 * Static view IDs.
 *
 * @author Jürgen Moßgraber
 */
public enum Views
{
    /** View for playing notes. */
    PLAY,
    /** View for playing chords. */
    CHORDS,
    /** View for a session grid with clips. */
    SESSION,
    /** View for a sequencer. */
    SEQUENCER,
    /** View for playing drums and sequencing. */
    DRUM,
    /** View for playing a 4x4 drum pad grid. */
    DRUM_PAD,
    /** View for raindrops sequencer. */
    RAINDROPS,
    /** View for playing in piano keyboard style. */
    PIANO,
    /** View sending program changes. */
    PRG_CHANGE,
    /** View for editing the clip length. */
    CLIP_LENGTH,
    /** View for XoX drum sequencing. */
    DRUM_XOX,
    /** View for drum sequencing with 4 sounds. */
    DRUM4,
    /** View for drum sequencing with 8 sounds. */
    DRUM8,
    /** View for drum playing with 64 pads. */
    DRUM64,
    /** View for selecting a color. */
    COLOR,
    /** View for the poly sequencer. */
    POLY_SEQUENCER,

    /** View for shift options. */
    SHIFT,
    /** View for note repeat options. */
    REPEAT_NOTE,
    /** View for editing note parameters. */
    NOTE_EDIT_VIEW,
    /** View for mixing. */
    MIX,
    /** Stable adapter for a reloadable fixed-facet workspace. */
    WORKSPACE;


    /** The name of the play view. */
    public static final String              NAME_PLAY           = "Play";
    /** The name of the chords view. */
    public static final String              NAME_CHORDS         = "Chords";
    /** The name of the piano view. */
    public static final String              NAME_PIANO          = "Piano";
    /** The name of the drum view. */
    public static final String              NAME_DRUM           = "Drum";
    /** The name of the drum pad view. */
    public static final String              NAME_DRUM_PAD       = "Drum Pads";
    /** The name of the XoX drum view. */
    public static final String              NAME_DRUM_XOX       = "Drum XoX";
    /** The name of the drum4 view. */
    public static final String              NAME_DRUM4          = "Drum 4";
    /** The name of the drum 8 view. */
    public static final String              NAME_DRUM8          = "Drum 8";
    /** The name of the sequencer view. */
    public static final String              NAME_SEQUENCER      = "Sequencer";
    /** The name of the raindrops view. */
    public static final String              NAME_RAINDROPS      = "Raindrop";
    /** The name of the poly-sequencer view. */
    public static final String              NAME_POLY_SEQUENCER = "Poly Seq.";
    /** The name of the clip length view. */
    public static final String              NAME_CLIP_LENGTH    = "Clip Length";
}
