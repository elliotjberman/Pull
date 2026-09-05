// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectControllerModeEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Master is a page over the selected composition; holding its button opens the Frame page. */
public final class MasterButtonView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("MASTERTRACK");
    private static final long ACKNOWLEDGEMENT_TIMEOUT_NANOS = 5_000_000_000L;
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.MASTER_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.MASTER_BUTTON, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final List<Gesture> pending = new ArrayList<> ();
    private Gesture held;
    private ControllerSnapshot latest;
    private long epoch;
    private boolean returnOnRelease;

    @Override public String id () { return "master-button"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_LAYOUT); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.pending.stream ().anyMatch (gesture -> gesture.entrySubmitted || gesture.ended)); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.latest = snapshot; }
    @Override public void deactivate () { this.epoch++; this.pending.clear (); this.held = null; this.returnOnRelease = false; }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        final var layout = snapshot.bridge ().layout ();
        for (final Gesture gesture: this.pending)
            if (gesture.entrySubmitted && snapshot.revision () > gesture.submittedRevision && "FRAME".equals (layout.modeId ()) && layout.temporaryMode ())
                gesture.entryObserved = true;
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (!browser (snapshot)) this.returnOnRelease = false;
        if (this.pending.size () >= DesiredParameterInteraction.PENDING_ACTION_CAPACITY + 1)
            throw new IllegalStateException ("Master-button continuation capacity exhausted");
        final Gesture gesture = new Gesture (this.epoch, this.returnOnRelease);
        this.pending.add (gesture);
        this.held = gesture;
        return ResolvedControllerAction.of (binding.intent (), () -> {
            if (gesture.epoch != this.epoch || gesture.admitted) return List.of ();
            gesture.admitted = true;
            return this.advance (gesture, this.latest);
        });
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (event instanceof final ControllerInputEvent input && BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            final Gesture gesture = this.held;
            final var layout = snapshot.bridge ().layout ();
            if (input.phase () == InputPhase.LONG && !browser (snapshot))
            {
                this.returnOnRelease = true;
                gesture.returnOnRelease = true;
                gesture.entry = new SelectControllerModeEffect (layout.generation (), "FRAME", SelectControllerModeEffect.Operation.TEMPORARY);
            }
            else if (input.phase () == InputPhase.END)
            {
                gesture.ended = true;
                this.held = null;
                gesture.releaseSuppressed = browser (snapshot);
                if (!gesture.releaseSuppressed && !gesture.returnOnRelease)
                    gesture.shortRelease = "MASTER".equals (layout.modeId ()) ? SelectControllerModeEffect.restore (layout.generation ()) : new SelectControllerModeEffect (layout.generation (), "MASTER");
            }
        }
        final List<CoreEffect> effects = new ArrayList<> ();
        for (final Gesture gesture: List.copyOf (this.pending)) effects.addAll (this.advance (gesture, snapshot));
        return List.copyOf (effects);
    }

    private List<CoreEffect> advance (final Gesture gesture, final ControllerSnapshot snapshot)
    {
        if (!gesture.admitted) return List.of ();
        if (gesture.entry != null && !gesture.entrySubmitted)
        {
            gesture.entrySubmitted = true;
            gesture.submittedRevision = snapshot.revision ();
            gesture.submittedAt = snapshot.monotonicTimeNanos ();
            // A request and its dependent restore are never issued in the same result.
            return List.of (gesture.entry);
        }
        if (!gesture.ended) return List.of ();
        if (gesture.releaseSuppressed)
        {
            this.pending.remove (gesture);
            return List.of ();
        }
        if (gesture.shortRelease != null)
        {
            this.pending.remove (gesture);
            return List.of (gesture.shortRelease);
        }
        if (gesture.returnOnRelease && (gesture.entry == null || gesture.entryObserved))
        {
            this.pending.remove (gesture);
            return List.of (SelectControllerModeEffect.restore (snapshot.bridge ().layout ().generation ()));
        }
        if (gesture.entrySubmitted && snapshot.monotonicTimeNanos () - gesture.submittedAt >= ACKNOWLEDGEMENT_TIMEOUT_NANOS)
            this.pending.remove (gesture);
        return List.of ();
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final String mode = snapshot.bridge ().layout ().modeId ();
        final int brightness = mode.isEmpty () ? 0 : Set.of ("MASTER", "MASTER_TEMP", "FRAME").contains (mode) ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }

    private static boolean browser (final ControllerSnapshot snapshot) { return "BROWSER".equals (snapshot.bridge ().layout ().modeId ()); }

    private static final class Gesture
    {
        private final long epoch;
        private boolean admitted;
        private boolean returnOnRelease;
        private boolean ended;
        private boolean releaseSuppressed;
        private SelectControllerModeEffect entry;
        private SelectControllerModeEffect shortRelease;
        private boolean entrySubmitted;
        private boolean entryObserved;
        private long submittedRevision;
        private long submittedAt;

        private Gesture (final long epoch, final boolean returnOnRelease) { this.epoch = epoch; this.returnOnRelease = returnOnRelease; }
    }
}
