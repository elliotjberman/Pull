// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.InfoPagePresentation;
import de.mossgrabers.pull.core.ui.page.InfoPageRenderer;
import de.mossgrabers.pull.core.ui.page.SetupPagePresentation;
import de.mossgrabers.pull.core.ui.page.SetupPageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.*;

/** Complete Info/Setup family with shared navigation and observed hardware preferences. */
public final class ConfigurationPageView implements ControllerView
{
    public enum Kind { INFO, SETUP }
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final List<ControlId> KNOBS = java.util.stream.IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.continuous ("KNOB" + i)).toList ();
    private static final Map<Integer, SetControllerIntegerSettingEffect.Setting> SETTINGS = Map.of (
        1, SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS,
        2, SetControllerIntegerSettingEffect.Setting.LED_BRIGHTNESS,
        4, SetControllerIntegerSettingEffect.Setting.PAD_SENSITIVITY,
        5, SetControllerIntegerSettingEffect.Setting.PAD_GAIN,
        6, SetControllerIntegerSettingEffect.Setting.PAD_DYNAMICS);
    private static final ControlId STOP = PushControlIds.button ("STOP_CLIP");
    private static final List<ControlId> TABS = List.of (PushControlIds.button ("ROW2_1"), PushControlIds.button ("ROW2_2"));
    private static final Set<ControllerActionBinding> ACTIONS;
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.DELETE_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
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

    private final Kind kind;
    private final Map<Integer, ControllerIntegerSetting> settings = new LinkedHashMap<> ();
    private final Set<Integer> touched = new HashSet<> ();
    private final PageNavigation navigation;
    private final SessionStopGesture stopGesture;
    private final CurrentTrackRowSelection rows;
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final TabGesture[] tabs = new TabGesture[2];

    public ConfigurationPageView (final Kind kind, final SessionStopGesture stopGesture, final PageNavigation navigation)
    {
        this.kind = Objects.requireNonNull (kind, "kind");
        if (kind == Kind.SETUP) SETTINGS.forEach ((index, setting) -> this.settings.put (index, new ControllerIntegerSetting (setting, navigation)));
        this.navigation = Objects.requireNonNull (navigation, "navigation");
        this.stopGesture = Objects.requireNonNull (stopGesture, "stopGesture");
        this.rows = new CurrentTrackRowSelection (stopGesture, navigation);
    }

    @Override public String id () { return this.kind == Kind.INFO ? "info-page" : "setup-page"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return this.kind == Kind.INFO ? Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CURRENT_TRACK_BANK, BridgeSubscription.CONTROLLER_HARDWARE) :
        Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CURRENT_TRACK_BANK, BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.ENCODER_CONFIGURATION); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.settings.values ().stream ().anyMatch (ControllerIntegerSetting::pending)); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.reconcile (snapshot); }
    @Override public void deactivate () { this.rows.deactivate (); this.admission.clear (); Arrays.fill (this.tabs, null); this.touched.clear (); this.settings.values ().forEach (ControllerIntegerSetting::clear); }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.BUTTON && CurrentTrackRowSelection.accepts (control))
            return this.rows.inputTarget (control, snapshot);
        if (this.kind == Kind.SETUP && KNOBS.contains (control))
            return new InputTarget.Context (control, "setup-hardware", "", snapshot.bridge ().controllerSettings ().hardware ().available () ? 1 : 0);
        return ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final InputTarget target, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.BUTTON)
        {
            this.rows.cancel (control);
            final int index = TABS.indexOf (control);
            if (index >= 0) this.finish (index);
        }
        if (kind == InputKind.TOUCH) this.touched.remove (KNOBS.indexOf (control));
        return List.of ();
    }

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
        final List<CoreEffect> effects = new ArrayList<> (this.rows.handle (event, snapshot));
        final var hardware = snapshot.bridge ().controllerSettings ().hardware ();
        if (this.kind == Kind.SETUP)
        {
            if (hardware.available ()) this.settings.forEach ((index, setting) -> effects.addAll (setting.observe (snapshot, observed (hardware, index))));
            else this.settings.values ().forEach (ControllerIntegerSetting::clear);
            if (event instanceof final ControllerInputEvent input && KNOBS.contains (input.controlId ()))
            {
                final int index = KNOBS.indexOf (input.controlId ());
                if (input.kind () == InputKind.TOUCH)
                {
                    if (input.phase () == InputPhase.BEGIN)
                    {
                        this.touched.add (index);
                        if (snapshot.pressedControls ().contains (DELETE))
                        {
                            effects.add (new ConsumeControllerButtonEffect (DELETE));
                            if (hardware.available () && this.settings.containsKey (index))
                                effects.addAll (this.settings.get (index).request (snapshot, observed (hardware, index), index < 3 ? 100 : 5));
                        }
                    }
                    else if (input.phase () == InputPhase.END) this.touched.remove (index);
                }
                else if (input.kind () == InputKind.RELATIVE && hardware.available () && snapshot.bridge ().encoderConfiguration ().available () && this.settings.containsKey (index))
                {
                    final ControllerIntegerSetting setting = this.settings.get (index);
                    final int observed = observed (hardware, index);
                    final int desired = (int) Math.max (0, Math.min (index < 3 ? 100 : 10, setting.intended (observed) + input.value () * snapshot.bridge ().encoderConfiguration ().baseStep () * 0.1));
                    effects.addAll (setting.request (snapshot, observed, desired));
                }
            }
        }
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

    private static int observed (final ControllerHardwareSettingsSnapshot hardware, final int index)
    {
        return switch (index)
        {
            case 1 -> hardware.displayBrightness ();
            case 2 -> hardware.ledBrightness ();
            case 4 -> hardware.sensitivity ();
            case 5 -> hardware.gain ();
            case 6 -> hardware.dynamics ();
            default -> throw new IllegalArgumentException ("Unused hardware setting column");
        };
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
        if (this.kind == Kind.SETUP)
        {
            final var settings = snapshot.bridge ().controllerSettings ().hardware ();
            final var presentation = new SetupPagePresentation (settings.available (), settings.displayBrightness (), settings.ledBrightness (), settings.sensitivity (), settings.gain (), settings.dynamics (), this.touched, settings.velocityCurve ());
            final var visuals = SetupPageRenderer.render (presentation);
            return new ViewOutput (visuals.lights (), Map.of (), visuals.display ());
        }
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
