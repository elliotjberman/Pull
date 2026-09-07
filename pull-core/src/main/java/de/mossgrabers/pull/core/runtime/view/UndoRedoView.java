// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ProjectHistoryAction;
import de.mossgrabers.pull.core.api.effect.ProjectHistoryEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.InputTarget;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;


/** Project-history release policy and authoritative Undo/Redo availability light. */
public final class UndoRedoView implements ControllerView
{
    private static final ControlId UNDO = PushControlIds.button ("UNDO");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor DIM = new RgbColor (60, 60, 60);
    private static final RgbColor BRIGHT = new RgbColor (255, 255, 255);
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.UNDO_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.UNDO_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());


    @Override
    public String id () { return "undo-redo"; }


    @Override
    public ViewProfile profile () { return PROFILE; }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.PROJECT); }


    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        return UNDO.equals (control) ? new InputTarget.Context (control, "project", snapshot.bridge ().project ().projectIdentity (), 0)
            : ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || !UNDO.equals (input.controlId ()) || input.kind () != InputKind.BUTTON)
            return List.of ();
        if (input.phase () != InputPhase.END || !available (snapshot))
            return List.of ();
        return List.of (new ProjectHistoryEffect (snapshot.bridge ().project ().projectIdentity (), snapshot.pressedControls ().contains (SHIFT) ? ProjectHistoryAction.REDO : ProjectHistoryAction.UNDO));
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final RgbColor color = available (snapshot) ? snapshot.pressedControls ().contains (UNDO) ? BRIGHT : DIM : OFF;
        return new ViewOutput (Map.of (UNDO, color), Map.of ());
    }


    private static boolean available (final ControllerSnapshot snapshot)
    {
        final var project = snapshot.bridge ().project ();
        return project.available () && !project.commandPending () && (snapshot.pressedControls ().contains (SHIFT) ? project.canRedo () : project.canUndo ());
    }
}
