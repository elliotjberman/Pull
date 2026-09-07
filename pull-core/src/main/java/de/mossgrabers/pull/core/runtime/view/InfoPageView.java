// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.InfoPagePresentation;
import de.mossgrabers.pull.core.ui.page.InfoPageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.*;

/** Observed hardware identity and the complete inherited Info page controls. */
public final class InfoPageView implements ControllerView
{
    private static final ControlId STOP = PushControlIds.button ("STOP_CLIP");
    private static final List<ControlId> TABS = List.of (PushControlIds.button ("ROW2_1"), PushControlIds.button ("ROW2_2"));
    private static final Set<ControllerActionBinding> ACTIONS;
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    static
    {
        final Set<ControllerActionBinding> actions = new HashSet<> (CurrentTrackRowSelection.actionBindings ());
        for (final ControlId tab: TABS)
            actions.add (new ControllerActionBinding (tab, InputKind.BUTTON, Set.of (new ControllerActionIntent (
                ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)))));
        ACTIONS = Set.copyOf (actions);
    }

    private final PageNavigation navigation;
    private final SessionStopGesture stopGesture;
    private final CurrentTrackRowSelection rows;
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final TabGesture[] tabs = new TabGesture[2];

    public InfoPageView (final SessionStopGesture stopGesture, final PageNavigation navigation)
    {
        this.navigation = Objects.requireNonNull (navigation, "navigation");
        this.stopGesture = Objects.requireNonNull (stopGesture, "stopGesture");
        this.rows = new CurrentTrackRowSelection (stopGesture, navigation);
    }

    @Override public String id () { return "info-page"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CURRENT_TRACK_BANK, BridgeSubscription.CONTROLLER_HARDWARE); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.reconcile (snapshot); }
    @Override public void deactivate () { this.rows.deactivate (); this.admission.clear (); Arrays.fill (this.tabs, null); }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.rows.reconcile (snapshot);
        for (int index = 0; index < this.tabs.length; index++)
            if (this.tabs[index] != null && !this.navigation.matches (this.tabs[index].origin)) this.finish (index);
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (CurrentTrackRowSelection.accepts (input.controlId ())) return this.rows.resolveAction (binding, input, snapshot);
        if (snapshot.pressedControls ().contains (STOP) && snapshot.bridge ().sessionBank ().shape ().isPresent ()) this.stopGesture.consume ();
        final int index = TABS.indexOf (input.controlId ());
        this.finish (index);
        final TabGesture gesture = new TabGesture (this.admission.begin (), this.navigation.origin ());
        this.tabs[index] = gesture;
        return this.admission.action (gesture.ticket, binding.intent (ControllerActionId.SWITCH_PARAMETER_CONTEXT), () -> this.drain (index, gesture));
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        final List<CoreEffect> effects = this.rows.handle (event, snapshot);
        if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.END)
        {
            final int index = TABS.indexOf (input.controlId ());
            if (index >= 0 && this.tabs[index] != null)
            {
                this.tabs[index].ended = true;
                this.drain (index, this.tabs[index]);
            }
        }
        return effects;
    }

    private List<CoreEffect> drain (final int index, final TabGesture gesture)
    {
        if (this.tabs[index] != gesture || !gesture.ended || !gesture.ticket.admitted ()) return List.of ();
        this.finish (index);
        this.navigation.temporary (gesture.origin, this.navigation.resolve (index == 0 ? "INFO" : "SETUP"));
        return List.of ();
    }

    private void finish (final int index)
    {
        if (this.tabs[index] != null) this.admission.finish (this.tabs[index].ticket);
        this.tabs[index] = null;
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var hardware = snapshot.bridge ().controllerHardware ();
        final var presentation = new InfoPagePresentation (hardware.available (), hardware.firmwareMajor () + "." + hardware.firmwareMinor () + " Build " + hardware.firmwareBuild (),
            Integer.toString (hardware.boardRevision ()), Integer.toString (hardware.serialNumber ()));
        final var visuals = InfoPageRenderer.render (presentation);
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display ());
    }

    private static final class TabGesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private final PageNavigation.Origin origin;
        private boolean ended;
        private TabGesture (final DeferredButtonAdmission.Ticket ticket, final PageNavigation.Origin origin) { this.ticket = ticket; this.origin = origin; }
    }
}
