// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.callback.NoteStepChangedCallback;
import com.bitwig.extension.controller.api.*;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.defaultValue;

class CursorClipObservedStateTest
{
    @Test
    void editingIntentsCannotReplaceLaterHostReadbackOrSuppressItsObservation ()
    {
        final var observer = new AtomicReference<NoteStepChangedCallback> ();
        final var velocity = new AtomicReference<> (.2);
        final var state = new AtomicReference<> (NoteStep.State.NoteOn);
        final List<Double> submitted = new ArrayList<> ();
        final List<Runnable> scheduled = new ArrayList<> ();
        final NoteStep note = proxy (NoteStep.class, (p, method, args) -> switch (method.getName ()) {
            case "x", "channel" -> 0;
            case "y" -> 60;
            case "state" -> state.get ();
            case "velocity" -> velocity.get ();
            case "velocitySpread" -> .3;
            case "occurrence" -> NoteOccurrence.values ()[0];
            case "setVelocity" -> { submitted.add ((Double) args[0]); yield null; }
            default -> empty (method.getReturnType ());
        });
        final PinnableCursorClip nativeClip = proxy (PinnableCursorClip.class, (p, method, args) -> switch (method.getName ()) {
            case "addNoteStepObserver" -> { observer.set ((NoteStepChangedCallback) args[0]); yield null; }
            case "getStep" -> note;
            default -> empty (method.getReturnType ());
        });
        final CursorTrack track = proxy (CursorTrack.class, (p, method, args) -> "createLauncherCursorClip".equals (method.getName ()) ? nativeClip : empty (method.getReturnType ()));
        final IHost host = proxy (IHost.class, (p, method, args) -> {
            if ("scheduleTask".equals (method.getName ())) scheduled.add ((Runnable) args[0]);
            return empty (method.getReturnType ());
        });
        final CursorClipImpl clip = new CursorClipImpl (host, (ControllerHost) empty (ControllerHost.class), track, proxy (IValueChanger.class, (p, method, args) -> empty (method.getReturnType ())), 8, 128, () -> "project");
        final NotePosition position = new NotePosition (0, 0, 60);
        observer.get ().noteStepChanged (note);
        assertEquals (.2, clip.getObservedStep (position).getVelocity ());
        assertEquals (.3, clip.getObservedStep (position).getVelocitySpread ());
        clip.startEdit (List.of (position));
        submitted.clear ();
        clip.updateStepVelocity (position, .9);
        scheduled.remove (0).run ();
        assertEquals (List.of (.9), submitted);
        assertEquals (.2, clip.getObservedStep (position).getVelocity (), "submission is not read-back");
        velocity.set (.75);
        observer.get ().noteStepChanged (note);
        assertEquals (.9, clip.getStep (position).getVelocity (), "frozen edit working copy remains separate");
        assertEquals (.75, clip.getObservedStep (position).getVelocity (), "host observations survive the legacy edit filter");
        assertEquals (StepState.OFF, clip.getObservedStep (new NotePosition (16, 0, 59)).getState (), "invalid channels cannot alias another observed note");
        state.set (NoteStep.State.Empty);
        observer.get ().noteStepChanged (note);
        assertEquals (StepState.OFF, clip.getObservedStep (position).getState ());
    }
    private static Object empty (final Class<?> type)
    {
        if (type == String.class) return "";
        return type.isInterface () ? proxy (type, (p, method, args) -> empty (method.getReturnType ())) : defaultValue (type);
    }
}
