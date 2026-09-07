// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;


/** Raw 14-bit strip behavior; the shared input lifecycle cancels it when its binding leaves view. */
public final class RawPitchBendView implements ControllerView
{
    private static final ControlId STRIP = PushControlIds.continuous ("TOUCHSTRIP");
    private static final int CENTER = 8192;
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "raw",
        Set.of (
            new SurfaceClaim (SurfaceArea.TOUCH_STRIP, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.TOUCH_STRIP, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());

    private boolean touched;
    private int position = CENTER;


    @Override
    public String id ()
    {
        return "touch-strip";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || !STRIP.equals (input.controlId ()))
            return List.of ();
        if (input.kind () == InputKind.TOUCH)
        {
            if (input.phase () == InputPhase.BEGIN && !this.touched)
            {
                this.touched = true;
                this.position = CENTER;
            }
            else if (input.phase () == InputPhase.END)
                return this.finish ();
            return List.of ();
        }
        if (input.kind () != InputKind.ABSOLUTE || !this.touched)
            return List.of ();
        this.position = (int) input.value ();
        return List.of (pitchBend (this.position));
    }


    @Override
    public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final de.mossgrabers.pull.core.view.InputTarget target, final ControllerSnapshot snapshot)
    {
        return STRIP.equals (control) && kind == InputKind.TOUCH ? this.finish () : List.of ();
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (
            Map.of (), Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (), DesiredNotePerformance.inactive (), DesiredNoteRepeat.unowned (),
            DesiredControllerMappings.empty (), DesiredTouchStrip.pitchBend (this.position));
    }


    private List<CoreEffect> finish ()
    {
        final boolean center = this.touched;
        this.touched = false;
        this.position = CENTER;
        return center ? List.of (pitchBend (CENTER)) : List.of ();
    }


    private static SendNoteInputMidiEffect pitchBend (final int value)
    {
        return new SendNoteInputMidiEffect (0xE0, value & 0x7F, value >> 7);
    }
}
