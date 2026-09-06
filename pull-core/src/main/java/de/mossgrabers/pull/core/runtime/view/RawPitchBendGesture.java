// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;

import java.util.List;


/**
 * One begin-frozen raw pitch-bend gesture shared by fixed raw and legacy-continuation profiles.
 * Musical messages use ordinary NoteInput routing; this state does not identify a selected track.
 */
public final class RawPitchBendGesture
{
    private static final int CENTER = 8192;
    private boolean touched;
    private boolean raw;
    private int position = CENTER;
    private long releaseRevision = -1;


    /** Retire the one-result centered output after a later shell snapshot arrives. */
    void reconcile (final ControllerSnapshot snapshot)
    {
        if (this.releaseRevision >= 0 && snapshot.revision () > this.releaseRevision)
            this.releaseRevision = -1;
    }


    List<CoreEffect> handle (final ControllerInputEvent input, final ControllerSnapshot snapshot, final boolean rawProfile)
    {
        if (!PushControlIds.continuous ("TOUCHSTRIP").equals (input.controlId ()))
            return List.of ();
        if (input.kind () == InputKind.TOUCH)
        {
            if (input.phase () == InputPhase.BEGIN && !this.touched)
            {
                this.touched = true;
                this.raw = rawProfile;
                this.position = CENTER;
                this.releaseRevision = -1;
            }
            else if (input.phase () == InputPhase.END)
            {
                final boolean center = this.touched && this.raw;
                this.touched = false;
                this.raw = false;
                this.position = CENTER;
                if (center)
                {
                    this.releaseRevision = snapshot.revision ();
                    return List.of (pitchBend (CENTER));
                }
            }
            return List.of ();
        }
        if (input.kind () != InputKind.ABSOLUTE || !this.touched || !this.raw)
            return List.of ();
        this.position = (int) input.value ();
        return List.of (pitchBend (this.position));
    }


    DesiredTouchStrip output (final boolean rawProfile)
    {
        if (this.touched)
            return this.raw ? DesiredTouchStrip.pitchBend (this.position) : DesiredTouchStrip.unowned ();
        return rawProfile || this.releaseRevision >= 0 ? DesiredTouchStrip.pitchBend (CENTER) : DesiredTouchStrip.unowned ();
    }


    private static SendNoteInputMidiEffect pitchBend (final int value)
    {
        return new SendNoteInputMidiEffect (0xE0, value & 0x7F, value >> 7);
    }
}
