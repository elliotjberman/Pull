// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.RibbonPagePresentation;
import de.mossgrabers.pull.core.ui.page.RibbonPageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.*;
import static de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect.Setting.*;

/** Global Ribbon preferences and page return; physical strip policy remains a separate owner. */
public final class RibbonPageView implements ControllerView
{
    private static final ControlId CC_KNOB = PushControlIds.continuous ("KNOB1");
    private static final int[] QUICK_CC = {1, 11, 7, 64};
    private static final List<ControlId> LOWER = java.util.stream.IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.button ("ROW1_" + i)).toList ();
    private static final List<ControlId> UPPER = java.util.stream.IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.button ("ROW2_" + i)).toList ();
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS;
    static
    {
        final Set<ControllerActionBinding> actions = new HashSet<> ();
        for (final ControlId control: LOWER)
        {
            final boolean restore = LOWER.indexOf (control) >= 6;
            actions.add (new ControllerActionBinding (control, InputKind.BUTTON,
                restore ? ControllerActionId.SWITCH_PARAMETER_CONTEXT : ControllerActionId.SET_CONTROLLER_PREFERENCE,
                Set.of (restore ? ControllerStateScope.ACTIVE_PARAMETERS : ControllerStateScope.CONTROLLER_SETTINGS)));
        }
        for (final ControlId control: UPPER.subList (1, 8))
            actions.add (new ControllerActionBinding (control, InputKind.BUTTON, ControllerActionId.SET_CONTROLLER_PREFERENCE, Set.of (ControllerStateScope.CONTROLLER_SETTINGS)));
        ACTIONS = Set.copyOf (actions);
    }
    private final Map<SetControllerIntegerSettingEffect.Setting, ControllerIntegerSetting> settings;
    private final Map<ControlId, RowGesture> rows = new HashMap<> ();
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final PageNavigation navigation;
    private ControllerSnapshot latest;

    public RibbonPageView (final PageNavigation navigation)
    {
        this.navigation = Objects.requireNonNull (navigation, "navigation");
        this.settings = Map.of (RIBBON_FUNCTION, new ControllerIntegerSetting (RIBBON_FUNCTION, navigation),
            RIBBON_CC, new ControllerIntegerSetting (RIBBON_CC, navigation),
            RIBBON_NOTE_REPEAT, new ControllerIntegerSetting (RIBBON_NOTE_REPEAT, navigation));
    }
    @Override public String id () { return "ribbon-page"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.ENCODER_CONFIGURATION); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.settings.values ().stream ().anyMatch (ControllerIntegerSetting::pending)); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.reconcile (snapshot); }
    @Override public void deactivate () { this.admission.clear (); this.rows.clear (); this.settings.values ().forEach (ControllerIntegerSetting::clear); }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        final int lower = LOWER.indexOf (control);
        if (!snapshot.bridge ().controllerSettings ().ribbon ().available () &&
            (CC_KNOB.equals (control) || kind == InputKind.BUTTON && (lower >= 0 && lower < 6 || UPPER.indexOf (control) > 0)))
            return null;
        return ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final InputTarget target, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.BUTTON) this.finish (control);
        return List.of ();
    }

    @Override public void reconcile (final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        for (final var entry: List.copyOf (this.rows.entrySet ()))
            if (!this.navigation.matches (entry.getValue ().origin)) this.finish (entry.getKey ());
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        final ControlId control = input.controlId ();
        this.finish (control);
        final int lower = LOWER.indexOf (control);
        final int upper = UPPER.indexOf (control);
        final boolean restore = lower >= 6;
        final SetControllerIntegerSettingEffect.Setting setting = lower >= 0 ? RIBBON_FUNCTION : upper < 5 ? RIBBON_CC : RIBBON_NOTE_REPEAT;
        final int value = lower >= 0 ? lower : upper < 5 ? QUICK_CC[upper - 1] : upper - 5;
        final RowGesture gesture = new RowGesture (this.admission.begin (), this.navigation.origin (), restore,
            snapshot.bridge ().controllerSettings ().ribbon ().available (), setting, value);
        this.rows.put (control, gesture);
        return this.admission.action (gesture.ticket, binding.intent (), () -> this.drain (control, gesture));
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        final RibbonSettingsSnapshot observed = snapshot.bridge ().controllerSettings ().ribbon ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (observed.available ()) this.settings.forEach ((setting, lane) -> effects.addAll (lane.observe (snapshot, value (observed, setting))));
        else this.settings.values ().forEach (ControllerIntegerSetting::clear);
        if (event instanceof final ControllerInputEvent input)
        {
            if (CC_KNOB.equals (input.controlId ()) && input.kind () == InputKind.RELATIVE && observed.available () && snapshot.bridge ().encoderConfiguration ().available ())
            {
                final ControllerIntegerSetting cc = this.settings.get (RIBBON_CC);
                final int desired = (int) Math.max (0, Math.min (127, cc.intended (observed.cc ()) + input.value () * snapshot.bridge ().encoderConfiguration ().baseStep () * 0.1));
                effects.addAll (cc.request (snapshot, observed.cc (), desired));
            }
            if (input.kind () == InputKind.BUTTON && input.phase () == InputPhase.END && this.rows.containsKey (input.controlId ()))
            {
                final RowGesture gesture = this.rows.get (input.controlId ());
                gesture.ended = true;
                effects.addAll (this.drain (input.controlId (), gesture));
            }
        }
        return List.copyOf (effects);
    }

    private List<CoreEffect> drain (final ControlId control, final RowGesture gesture)
    {
        if (this.rows.get (control) != gesture || !gesture.ended || !gesture.ticket.admitted ()) return List.of ();
        this.finish (control);
        if (!this.navigation.matches (gesture.origin)) return List.of ();
        if (gesture.restore) { this.navigation.restore (gesture.origin); return List.of (); }
        final RibbonSettingsSnapshot observed = this.latest.bridge ().controllerSettings ().ribbon ();
        if (!gesture.available || !observed.available ()) return List.of ();
        return this.settings.get (gesture.setting).request (this.latest, value (observed, gesture.setting), gesture.value);
    }

    private void finish (final ControlId control)
    {
        final RowGesture gesture = this.rows.remove (control);
        if (gesture != null) this.admission.finish (gesture.ticket);
    }

    private static int value (final RibbonSettingsSnapshot state, final SetControllerIntegerSettingEffect.Setting setting)
    {
        return switch (setting)
        {
            case RIBBON_FUNCTION -> state.function ();
            case RIBBON_CC -> state.cc ();
            case RIBBON_NOTE_REPEAT -> state.noteRepeat ();
            default -> throw new IllegalArgumentException ("Not a Ribbon preference");
        };
    }

    @Override public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final RibbonSettingsSnapshot state = snapshot.bridge ().controllerSettings ().ribbon ();
        final var visuals = RibbonPageRenderer.render (new RibbonPagePresentation (state.available (), state.function (), state.cc (), state.noteRepeat ()));
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display ());
    }

    private static final class RowGesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private final PageNavigation.Origin origin;
        private final boolean restore;
        private final boolean available;
        private final SetControllerIntegerSettingEffect.Setting setting;
        private final int value;
        private boolean ended;
        private RowGesture (final DeferredButtonAdmission.Ticket ticket, final PageNavigation.Origin origin, final boolean restore, final boolean available, final SetControllerIntegerSettingEffect.Setting setting, final int value)
        {
            this.ticket = ticket; this.origin = origin; this.restore = restore; this.available = available; this.setting = setting; this.value = value;
        }
    }
}
