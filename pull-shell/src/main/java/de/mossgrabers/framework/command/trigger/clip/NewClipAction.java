// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.command.trigger.clip;

import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;

import java.util.Objects;
import java.util.Optional;


/**
 * Display-independent operation which creates and launches a clip on the model cursor track.
 */
public final class NewClipAction
{
    private final IModel model;


    /**
     * Constructor.
     *
     * @param model The model
     */
    public NewClipAction (final IModel model)
    {
        this.model = Objects.requireNonNull (model, "model");
    }


    /**
     * Create and launch a clip.
     *
     * @param lengthInBeats Clip length in beats
     * @param enableOverdub True to enable overdub for the new clip
     * @return The operation result
     */
    public Result execute (final int lengthInBeats, final boolean enableOverdub)
    {
        final ITrack cursorTrack = this.model.getCursorTrack ();
        if (!cursorTrack.doesExist ())
            return Result.NO_TRACK;

        final ISlotBank slotBank = cursorTrack.getSlotBank ();
        final Optional<ISlot> selectedSlot = slotBank.getSelectedItem ();
        final int slotIndex = selectedSlot.isEmpty () ? 0 : selectedSlot.get ().getIndex ();
        final Optional<ISlot> slot = slotBank.getEmptySlot (slotIndex);
        if (slot.isEmpty ())
            return Result.NO_EMPTY_SLOT;

        this.model.createNoteClip (cursorTrack, slot.get (), lengthInBeats, enableOverdub);
        return Result.CREATED;
    }


    /** Result of a create-and-launch request. */
    public enum Result
    {
        /** A clip was created and launched. */
        CREATED,

        /** No cursor track exists. */
        NO_TRACK,

        /** No empty slot is available in the current page. */
        NO_EMPTY_SLOT
    }
}
