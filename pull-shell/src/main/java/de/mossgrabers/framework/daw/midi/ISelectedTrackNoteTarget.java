// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw.midi;


/**
 * Authoritative state and actions for a private selection-following track target.
 *
 * <p>The target observes selection and owns the stable shell actuator for the permanent controller
 * note input. Reloadable policy may acquire or release that actuator without receiving Bitwig
 * objects.</p>
 */
public interface ISelectedTrackNoteTarget
{
    /**
     * Submit the permanent controller note input's direct selected-track route. Bitwig provides no
     * attachment read-back, so callers must not treat return from this void operation as proof that
     * the musical path is audible.
     *
     * @param active True to attach directly to this private selected-track cursor
     */
    void submitNoteInputRoute (boolean active);


    /**
     * Capture the bounded authoritative state of the selected target.
     *
     * @return The current target snapshot
     */
    SelectedTrackNoteTargetSnapshot snapshot ();


    /**
     * Get the identity generation of the selected target. The generation changes whenever the
     * private cursor resolves to a different target or its existence changes.
     *
     * @return The target generation
     */
    long getGeneration ();


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


    /**
     * Get the current velocity for a playing MIDI note on the authoritative selected target.
     *
     * @param note MIDI note number in the range 0..127
     * @return The current velocity, or zero when the note is not playing
     */
    int getPlayingVelocity (int note);


    /**
     * Set whether the selected target is activated.
     *
     * @param activated True to activate the target
     */
    void setActivated (boolean activated);


    /**
     * Set whether the selected target is expanded when it is a group.
     *
     * @param expanded True to expand the group
     */
    void setGroupExpanded (boolean expanded);


    /**
     * Set whether the selected target is armed.
     *
     * @param armed True to arm the target
     */
    void setArmed (boolean armed);


    /**
     * Set the selected target's monitor mode.
     *
     * @param mode The absolute monitor mode
     */
    void setMonitorMode (SelectedTrackMonitorMode mode);


    /**
     * Set whether the selected target is muted.
     *
     * @param muted True to mute the target
     */
    void setMuted (boolean muted);


    /**
     * Set whether the selected target is soloed.
     *
     * @param soloed True to solo the target
     */
    void setSoloed (boolean soloed);


    /**
     * Set the selected target volume.
     *
     * @param normalizedVolume Normalized volume in the range 0..1
     */
    void setVolume (double normalizedVolume);


    /**
     * Set the selected target pan.
     *
     * @param normalizedPan Normalized pan in the range 0..1, with 0.5 at center
     */
    void setPan (double normalizedPan);


    /**
     * Stop launcher playback on the selected target.
     */
    void stop ();


    /**
     * Return the selected target to arranger playback.
     */
    void returnToArrangement ();


}
