// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw.midi;


/**
 * Authoritative state for a private selection-following track target that receives a note input.
 */
public interface ISelectedTrackNoteTarget
{
    /**
     * Get the stable Bitwig channel ID of the selected target.
     *
     * @return The channel UUID, or an empty string while no target is resolved
     */
    String getChannelID ();


    /**
     * Test whether the selected target exists.
     *
     * @return True if a target exists
     */
    boolean doesExist ();


    /**
     * Test whether the selected target can hold note data.
     *
     * @return True if the target can hold notes
     */
    boolean canHoldNotes ();


    /**
     * Test whether the selected target contains a compatible drum device.
     *
     * @return True if a drum device exists on the target
     */
    boolean hasDrumDevice ();
}
