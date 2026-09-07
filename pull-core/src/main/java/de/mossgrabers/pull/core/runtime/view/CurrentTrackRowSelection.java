// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import java.util.*;
import java.util.stream.IntStream;

/** Shared plain lower-row selection, including the Session Stop chord and deferred release. */
final class CurrentTrackRowSelection
{
    private static final ControlId STOP = PushControlIds.button ("STOP_CLIP");
    private static final List<ControlId> ROW = IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.button ("ROW1_" + i)).toList ();
    private static final Set<ControllerActionBinding> ACTIONS = ROW.stream ().map (button -> new ControllerActionBinding (button, InputKind.BUTTON, Set.of (
        new ControllerActionIntent (ControllerActionId.NAVIGATE_SELECTED_TARGET, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionIntent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK, Set.of (ControllerStateScope.SESSION_PLAYBACK))))).collect (java.util.stream.Collectors.toUnmodifiableSet ());
    private final SessionStopGesture stopGesture;
    private final PageNavigation pages;
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final Gesture[] rows = new Gesture[8];
    private ControllerSnapshot latest;

    CurrentTrackRowSelection (final SessionStopGesture stopGesture, final PageNavigation pages)
    {
        this.stopGesture = Objects.requireNonNull (stopGesture, "stopGesture");
        this.pages = Objects.requireNonNull (pages, "pages");
    }

    static Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    static boolean accepts (final ControlId control) { return ROW.contains (control); }

    ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        final int index = ROW.indexOf (input.controlId ());
        if (index < 0) throw new IllegalArgumentException ("Not a current-track row control");
        if (this.rows[index] != null) this.admission.finish (this.rows[index].ticket);
        final SessionBankSnapshot session = snapshot.bridge ().sessionBank ();
        final boolean stop = snapshot.pressedControls ().contains (STOP) && session.shape ().isPresent ();
        CoreEffect effect = null;
        if (stop)
        {
            this.stopGesture.consume ();
            if (index < session.tracks ().size () && session.tracks ().get (index).exists ())
                effect = new StopSessionTrackEffect (session.generation (), session.shape (), index, session.tracks ().get (index).channelId (), true);
        }
        else
        {
            final var bank = snapshot.bridge ().currentTrackBank ();
            if (bank.generation () > 0 && index < bank.tracks ().size () && bank.tracks ().get (index).track ().exists ())
                effect = new CurrentTrackActionEffect (new CurrentTrackTarget (bank.generation (), bank.bankId (), index, bank.tracks ().get (index).track ().channelId ()), CurrentTrackActionEffect.Action.SELECT);
        }
        final Gesture gesture = new Gesture (this.admission.begin (), this.pages.origin (), effect, stop);
        this.rows[index] = gesture;
        final var intent = binding.intent (stop ? ControllerActionId.STOP_VISIBLE_SESSION_TRACK : ControllerActionId.NAVIGATE_SELECTED_TARGET);
        final ResolvedControllerAction action = this.admission.action (gesture.ticket, intent, () -> this.drain (index, gesture));
        return stop ? action.withImmediateConsumption (input.controlId ()) : action;
    }

    List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.BUTTON || input.phase () != InputPhase.END)
            return List.of ();
        final int index = ROW.indexOf (input.controlId ());
        if (index < 0 || this.rows[index] == null) return List.of ();
        final Gesture gesture = this.rows[index];
        gesture.ended = true;
        return this.drain (index, gesture);
    }

    void reconcile (final ControllerSnapshot snapshot)
    {
        this.latest = Objects.requireNonNull (snapshot, "snapshot");
        for (int index = 0; index < this.rows.length; index++)
        {
            final Gesture gesture = this.rows[index];
            if (gesture == null || this.matches (index, gesture)) continue;
            // Cancellation is permanent even if the old page or bank later reappears.
            this.admission.finish (gesture.ticket);
            this.rows[index] = null;
        }
    }

    void deactivate ()
    {
        this.admission.clear ();
        Arrays.fill (this.rows, null);
        this.latest = null;
    }

    private List<CoreEffect> drain (final int index, final Gesture gesture)
    {
        if (this.rows[index] != gesture || !gesture.ticket.admitted () || !gesture.ended && !gesture.stop) return List.of ();
        final List<CoreEffect> effects = gesture.effect != null && this.matches (index, gesture) ? List.of (gesture.effect) : List.of ();
        // The input router retains the physical owner until END; semantic work is complete.
        this.admission.finish (gesture.ticket);
        this.rows[index] = null;
        return effects;
    }

    private boolean matches (final int index, final Gesture gesture)
    {
        if (!this.pages.matches (gesture.origin)) return false;
        if (gesture.effect instanceof final CurrentTrackActionEffect action)
        {
            final var target = action.target ();
            final var bank = this.latest.bridge ().currentTrackBank ();
            return bank.generation () == target.generation () && bank.bankId ().equals (target.bankId ()) && index < bank.tracks ().size () &&
                bank.tracks ().get (index).track ().exists () && bank.tracks ().get (index).track ().channelId ().equals (target.channelId ());
        }
        if (gesture.effect instanceof final StopSessionTrackEffect action)
        {
            final var bank = this.latest.bridge ().sessionBank ();
            return bank.generation () == action.targetGeneration () && bank.shape ().equals (action.shape ()) && index < bank.tracks ().size () &&
                bank.tracks ().get (index).exists () && bank.tracks ().get (index).channelId ().equals (action.channelId ());
        }
        return true;
    }

    private static final class Gesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private final PageNavigation.Origin origin;
        private final CoreEffect effect;
        private final boolean stop;
        private boolean ended;
        private Gesture (final DeferredButtonAdmission.Ticket ticket, final PageNavigation.Origin origin, final CoreEffect effect, final boolean stop)
        { this.ticket = ticket; this.origin = origin; this.effect = effect; this.stop = stop; }
    }
}
