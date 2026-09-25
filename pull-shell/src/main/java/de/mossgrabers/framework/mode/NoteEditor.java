// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.mode;

import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;

import java.util.ArrayList;
import java.util.List;


/**
 * Helper class for managing notes which are edited.
 *
 * @author Jürgen Moßgraber
 */
public class NoteEditor implements INoteEditor
{
    private long selectionRevision;
    private INoteClip                clip  = null;
    private final List<NotePosition> notes = new ArrayList<> ();


    /**
     * Constructor.
     */
    public NoteEditor ()
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public INoteClip getClip ()
    {
        return this.clip;
    }


    /** {@inheritDoc} */
    @Override
    public void clearNotes ()
    {
        this.selectionRevision++;
        this.notes.clear ();
    }


    /** {@inheritDoc} */
    @Override
    public void setNote (final INoteClip clip, final NotePosition notePosition)
    {
        this.selectionRevision++;
        this.notes.clear ();
        this.addNote (clip, notePosition);
    }


    /** {@inheritDoc} */
    @Override
    public void addNote (final INoteClip clip, final NotePosition notePosition)
    {
        this.selectionRevision++;
        // Is the note already edited? Remove it.
        this.removeNote (clip, notePosition);
        this.notes.add (new NotePosition (notePosition.getChannel (), notePosition.getStep (), notePosition.getNote ()));
    }


    /** {@inheritDoc} */
    @Override
    public void removeNote (final INoteClip clip, final NotePosition notePosition)
    {
        this.selectionRevision++;
        if (this.clip != clip)
        {
            this.selectionRevision++;
        this.notes.clear ();
            this.clip = clip;
        }

        for (final NotePosition gridStep: this.notes)
        {
            if (gridStep.equals (notePosition))
            {
                this.notes.remove (gridStep);
                return;
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean isNoteEdited (final INoteClip clip, final NotePosition notePosition)
    {
        if (this.clip != clip)
            return false;

        for (final NotePosition gridStep: this.notes)
        {
            if (gridStep.equals (notePosition))
                return true;
        }
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public List<NotePosition> getNotes ()
    {
        return this.notes.stream ().map (note -> new NotePosition (note.getChannel (), note.getStep (), note.getNote ())).toList ();
    }


    @Override
    public long getSelectionRevision () { return this.selectionRevision; }
}
