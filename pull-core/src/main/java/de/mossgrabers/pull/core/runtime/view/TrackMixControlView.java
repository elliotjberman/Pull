// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retained Mix-button entry, momentary return, and VU preference policy. */
public final class TrackMixControlView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("TRACK");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final Set<String> GLOBAL_MODES = Set.of ("VOLUME", "PAN", "CROSSFADER", "SEND1", "SEND2", "SEND3", "SEND4", "SEND5", "SEND6", "SEND7", "SEND8");
    private static final Set<String> OTHER_LIT_MODES = Set.of ("TRACK", "TRACK_DETAILS", "REC_ARM");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.MIX_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.MIX_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON, Set.of (
        new ControllerActionIntent (ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionIntent (ControllerActionId.SET_CONTROLLER_PREFERENCE, Set.of (ControllerStateScope.CONTROLLER_SETTINGS)))));
    private final AuthoritativeBooleanToggle<String> vuToggle = new AuthoritativeBooleanToggle<> ();
    // Value-only continuations, bounded by the semantic-action queue plus the currently held edge.
    private final List<Gesture> continuations = new ArrayList<> ();
    private Gesture held;
    private long epoch;
    private final PageNavigation navigation;

    public TrackMixControlView (final PageNavigation navigation) { this.navigation = java.util.Objects.requireNonNull (navigation, "navigation"); }

    @Override public String id () { return "track-mix-control"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.CURRENT_TRACK_BANK); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.vuToggle.pending () || !this.continuations.isEmpty ()); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); }
    @Override public void deactivate () { this.epoch++; this.held = null; this.continuations.clear (); this.vuToggle.clear (); }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final boolean shift = snapshot.pressedControls ().contains (SHIFT);
        final String visible = this.navigation.legacyAlias ();
        final var settings = snapshot.bridge ().controllerSettings ();
        final String destination = "TRACK".equals (visible) ? settings.globalMixMode () : "TRACK";
        final ControllerPageRef previous = !"TRACK".equals (visible) && !GLOBAL_MODES.contains (visible) ? this.navigation.state ().selected () : ControllerPageRef.none ();
        final Gesture gesture = new Gesture (this.epoch, shift, this.navigation.origin (), destination, previous, firstUnselectedTrack (snapshot));
        this.held = gesture;
        // Capture the variant and every target before Snapback can defer dispatch. LONG and END
        // mutate only this gesture's lifetime; a later modifier or selection cannot retarget it.
        final var intent = binding.intent (shift ? ControllerActionId.SET_CONTROLLER_PREFERENCE : ControllerActionId.SWITCH_PARAMETER_CONTEXT);
        return ResolvedControllerAction.of (intent, () -> this.dispatch (gesture, snapshot));
    }

    private List<CoreEffect> dispatch (final Gesture gesture, final ControllerSnapshot origin)
    {
        if (gesture.epoch != this.epoch || gesture.dispatched)
            return List.of ();
        gesture.dispatched = true;
        if (gesture.preference)
            return this.updateVu (origin, true);
        // A deferred entry whose exact page has already changed is no longer applicable.
        if (gesture.destination.isBlank () || !this.navigation.select (gesture.origin, this.navigation.resolve (gesture.destination)))
            return List.of ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (gesture.firstTrack != null)
            effects.add (new CurrentTrackActionEffect (gesture.firstTrack, CurrentTrackActionEffect.Action.SELECT));
        if (gesture.previous.isPresent () && !(gesture.ended && !gesture.longPress))
        {
            if (this.continuations.size () >= DesiredParameterInteraction.PENDING_ACTION_CAPACITY + 1)
                throw new IllegalStateException ("Mix-button continuation capacity exhausted");
            this.continuations.add (gesture);
        }
        return List.copyOf (effects);
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (event instanceof final ControllerInputEvent input && BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            if (input.phase () == InputPhase.LONG) this.held.longPress = true;
            else if (input.phase () == InputPhase.END)
            {
                this.held.ended = true;
                this.held = null;
            }
        }
        final List<CoreEffect> effects = new ArrayList<> (this.updateVu (snapshot, false));
        for (final Iterator<Gesture> iterator = this.continuations.iterator (); iterator.hasNext ();)
        {
            final Gesture gesture = iterator.next ();
            if (!gesture.ended) continue;
            // Mix deliberately returns its captured prior page even after an intervening page.
            if (gesture.longPress) this.navigation.select (gesture.previous);
            iterator.remove ();
        }
        return List.copyOf (effects);
    }

    private List<CoreEffect> updateVu (final ControllerSnapshot snapshot, final boolean pressed)
    {
        final var settings = snapshot.bridge ().controllerSettings ();
        if (!settings.available ()) { this.vuToggle.clear (); return List.of (); }
        return this.vuToggle.update ("controller", settings.vuMetersEnabled (), snapshot.monotonicTimeNanos (), pressed,
            (target, enabled) -> new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.VU_METERS, enabled.booleanValue ()));
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final String mode = this.navigation.legacyAlias ();
        final int brightness = mode.isEmpty () ? 0 : GLOBAL_MODES.contains (mode) || OTHER_LIT_MODES.contains (mode) ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }

    private static CurrentTrackTarget firstUnselectedTrack (final ControllerSnapshot snapshot)
    {
        final var bank = snapshot.bridge ().currentTrackBank ();
        if (bank.tracks ().isEmpty () || bank.tracks ().stream ().anyMatch (track -> track.track ().selected ())) return null;
        final var first = bank.tracks ().getFirst ().track ();
        return first.exists () ? new CurrentTrackTarget (bank.generation (), bank.bankId (), 0, first.channelId ()) : null;
    }

    private static final class Gesture
    {
        private final long epoch;
        private final boolean preference;
        private final PageNavigation.Origin origin;
        private final String destination;
        private final ControllerPageRef previous;
        private final CurrentTrackTarget firstTrack;
        private boolean dispatched;
        private boolean longPress;
        private boolean ended;

        private Gesture (final long epoch, final boolean preference, final PageNavigation.Origin origin, final String destination, final ControllerPageRef previous, final CurrentTrackTarget firstTrack)
        {
            this.epoch = epoch;
            this.preference = preference;
            this.origin = origin;
            this.destination = destination;
            this.previous = previous;
            this.firstTrack = firstTrack;
        }
    }
}
