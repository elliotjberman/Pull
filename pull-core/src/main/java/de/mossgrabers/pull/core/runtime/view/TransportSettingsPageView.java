// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.PreRoll;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetPreRollEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportSettingEffect;
import de.mossgrabers.pull.core.api.effect.TransportSetting;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Two fixed complete transport pages sharing geometry, with independently selected host state. */
final class TransportSettingsPageView implements ControllerView
{
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor GREY = new RgbColor (30, 30, 30);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final ControlId VOLUME = PushControlIds.continuous ("KNOB8");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final List<String> PRE_ROLL_LABELS = List.of ("None", "1 Bar", "2 Bars", "4 Bars");
    private static final List<String> AUTOMATION_LABELS = List.of ("Read", "Latch", "Touch", "Write");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private final AutomationControlState automation;
    private final boolean automationPage;
    private final AuthoritativeBooleanToggle<String> preRollMetronome = new AuthoritativeBooleanToggle<> ();
    private final Map<ControlId, String> presses = new HashMap<> ();

    TransportSettingsPageView (final boolean automationPage, final AutomationControlState automation)
    {
        this.automationPage = automationPage;
        this.automation = java.util.Objects.requireNonNull (automation, "automation");
    }

    @Override public String id () { return this.automationPage ? "automation-page" : "metronome-page"; }
    @Override public String installedModeId () { return this.automationPage ? "AUTOMATION" : "TRANSPORT"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return this.automationPage ? Set.of (BridgeSubscription.AUTOMATION) : Set.of (BridgeSubscription.TRANSPORT_SETTINGS, BridgeSubscription.PARAMETERS, BridgeSubscription.ENCODER_CONFIGURATION); }
    @Override public Set<ParameterBankId> parameterBanks () { return this.automationPage ? Set.of () : Set.of (ParameterBankId.GLOBAL); }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return this.automationPage ? Map.of () : Map.of (VOLUME, ParameterSlot.METRONOME_VOLUME); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.automationPage ? this.automation.pending () : this.preRollMetronome.pending ()); }
    @Override public void deactivate () { this.presses.clear (); this.preRollMetronome.clear (); }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        final String identity = this.automationPage ? snapshot.bridge ().automation ().projectIdentity () : snapshot.bridge ().transportSettings ().projectIdentity ();
        boolean togglePreRollMetronome = false;
        if (event instanceof final ControllerInputEvent input)
        {
            if (!this.automationPage && input.controlId ().equals (VOLUME) && input.kind () == InputKind.RELATIVE)
            {
                final var target = snapshot.bridge ().parameters ().slots ().get (ParameterSlot.METRONOME_VOLUME);
                if (target != null)
                    effects.addAll (TrackEncoderResponse.adjust (ParameterSlot.METRONOME_VOLUME, target, input.value (), snapshot.pressedControls ().contains (SHIFT), snapshot.bridge ().encoderConfiguration ()));
            }
            if (input.kind () == InputKind.BUTTON && SurfaceArea.SOFT_KEYS_LOWER.controls ().contains (input.controlId ()))
            {
                if (input.phase () == InputPhase.BEGIN && !identity.isBlank ()) this.presses.putIfAbsent (input.controlId (), identity);
                else if (input.phase () == InputPhase.END && identity.equals (this.presses.remove (input.controlId ())) && !identity.isBlank ())
                {
                    final int index = lowerKeys ().indexOf (input.controlId ());
                    if (index >= 0 && index < 4)
                    {
                        if (this.automationPage) this.automation.select (snapshot, index == 0 ? null : AutomationWriteMode.values ()[index]);
                        else effects.add (new SetPreRollEffect (identity, PreRoll.values ()[index]));
                    }
                    else if (!this.automationPage && index == 5) togglePreRollMetronome = true;
                }
            }
        }
        if (this.automationPage) effects.addAll (this.automation.advance (snapshot));
        else if (!identity.isBlank ()) effects.addAll (this.preRollMetronome.update (identity, snapshot.bridge ().transportSettings ().metronomeDuringPreRoll (), snapshot.monotonicTimeNanos (), togglePreRollMetronome, (project, enabled) -> new SetTransportSettingEffect (project, TransportSetting.METRONOME_DURING_PRE_ROLL, enabled.booleanValue ())));
        else this.preRollMetronome.clear ();
        return List.copyOf (effects);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, 960, 160, BLACK));
        final boolean available = this.automationPage ? snapshot.bridge ().automation ().available () : snapshot.bridge ().transportSettings ().available ();
        for (int index = 0; index < 8; index++)
        {
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), BLACK);
            final boolean selected = this.selected (index, snapshot);
            lights.put (lowerKeys ().get (index), available && (index < 4 || !this.automationPage && index == 5) ? selected ? WHITE : GREY : BLACK);
            if (available && index < 4) option (commands, index, index == 0 ? this.automationPage ? "Automation Mode" : "Pre-roll" : "", this.automationPage ? AUTOMATION_LABELS.get (index) : PRE_ROLL_LABELS.get (index), selected);
        }
        if (available && !this.automationPage)
        {
            option (commands, 5, "during Pre-Roll?", snapshot.bridge ().transportSettings ().metronomeDuringPreRoll () ? "Yes" : "No", this.selected (5, snapshot));
            text (commands, "Play Metronome", 5, 25, 14);
            final var volume = snapshot.bridge ().parameters ().slots ().get (ParameterSlot.METRONOME_VOLUME);
            final var configuration = snapshot.bridge ().encoderConfiguration ();
            if (volume != null && configuration.available ())
            {
                final double range = configuration.valueUpperBound () - 1.0;
                MixerDisplayScene.append (commands, new MixerControlSnapshot (7, MixerControlKind.KNOB, "Volume", Math.min (1, volume.value () / range), volume.modulatedValue () < 0 ? -1 : Math.min (1, volume.modulatedValue () / range), volume.displayedValue (), MixerControlRole.HOST_COLORED, true, false, Optional.of (WHITE), 0, 0));
            }
        }
        return new ViewOutput (lights, Map.of (), new ControllerDisplayScene (960, 160, commands));
    }

    private boolean selected (final int index, final ControllerSnapshot snapshot)
    {
        if (this.automationPage)
        {
            final var state = snapshot.bridge ().automation ();
            return index == 0 ? !state.writingEnabled () : index < 4 && state.writingEnabled () && state.mode () == AutomationWriteMode.values ()[index];
        }
        return index < 4 ? snapshot.bridge ().transportSettings ().preRoll () == PreRoll.values ()[index] : index == 5 && snapshot.bridge ().transportSettings ().metronomeDuringPreRoll ();
    }

    private static final List<ControlId> LOWER_KEYS = SurfaceArea.SOFT_KEYS_LOWER.controls ().stream ().sorted (java.util.Comparator.comparing (ControlId::value)).toList ();
    private static List<ControlId> lowerKeys () { return LOWER_KEYS; }
    private static void option (final List<DisplayCommand> commands, final int column, final String heading, final String label, final boolean selected)
    {
        text (commands, heading, column, 82, 13);
        commands.add (new DisplayCommand.Rectangle (column * 120 + 8, 108, 104, 28, selected ? WHITE : GREY));
        commands.add (new DisplayCommand.TextBox (label, column * 120 + 12, 110, 96, 24, DisplayTextAlignment.CENTER, selected ? BLACK : WHITE, 14, 10, DisplayTextFit.SHRINK_ELLIPSIS));
    }
    private static void text (final List<DisplayCommand> commands, final String text, final int column, final int y, final int size)
    {
        commands.add (new DisplayCommand.TextBox (text, column * 120 + 8, y, 104, 25, DisplayTextAlignment.LEFT, WHITE, size, 9, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
