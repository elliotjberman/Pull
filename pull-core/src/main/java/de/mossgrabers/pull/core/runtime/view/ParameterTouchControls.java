// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredParameterTouches;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputPhase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/** Core-owned eight-encoder reset/touch policy shared by parameter pages. */
final class ParameterTouchControls
{
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private final Map<ControlId, Touch> touches = new LinkedHashMap<> ();
    private final ParameterTouchSession session;
    private final boolean followsSelection;


    ParameterTouchControls (final ParameterTouchSession session)
    {
        this (session, true);
    }


    ParameterTouchControls (final ParameterTouchSession session, final boolean followsSelection)
    {
        this.session = Objects.requireNonNull (session, "session");
        this.followsSelection = followsSelection;
    }


    void clear ()
    {
        this.touches.clear ();
    }


    void reconcile (final ControllerSnapshot snapshot)
    {
        this.touches.entrySet ().removeIf (entry ->
            !snapshot.touchedControls ().contains (entry.getKey ()) ||
                this.followsSelection && entry.getValue ().selectionGeneration () != snapshot.bridge ().selectedTrack ().generation () ||
                snapshot.bridge ().parameters ().targetOrNull (entry.getValue ().target ()) == null);
    }


    List<CoreEffect> handle (final ControllerInputEvent input, final ParameterTargetSnapshot target, final ControllerSnapshot snapshot)
    {
        if (input.phase () == InputPhase.BEGIN)
        {
            if (!this.session.begin (input.controlId (), snapshot.bridge ().automation ()))
                return List.of ();
            if (target != null)
                this.touches.put (input.controlId (), new Touch (target.target (), snapshot.bridge ().selectedTrack ().generation ()));
            if (snapshot.pressedControls ().contains (DELETE))
                return target == null ? List.of (new ConsumeControllerButtonEffect (DELETE)) : List.of (new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (target.target ()));
        }
        else if (input.phase () == InputPhase.END)
        {
            this.touches.remove (input.controlId ());
            return this.session.end (input.controlId (), snapshot.bridge ().automation ());
        }
        return List.of ();
    }


    DesiredParameterTouches desired ()
    {
        final Map<ControlId, ParameterTargetRef> targets = new LinkedHashMap<> ();
        this.touches.forEach ((control, touch) -> targets.put (control, touch.target ()));
        return new DesiredParameterTouches (targets);
    }


    void retainTargets (final java.util.Set<ParameterTargetRef> targets)
    {
        this.touches.entrySet ().removeIf (entry -> !targets.contains (entry.getValue ().target ()));
    }


    private record Touch (ParameterTargetRef target, long selectionGeneration)
    {
    }
}
