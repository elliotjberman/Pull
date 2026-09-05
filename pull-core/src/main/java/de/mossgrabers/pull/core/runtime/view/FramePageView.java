// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Frame page layout, panel choices, and authoritative visibility feedback. */
public final class FramePageView implements ControllerView
{
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor GREY = new RgbColor (30, 30, 30);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final List<String> LOWER = List.of ("Arrange", "Mix", "Edit", "Notes", "Automate", "Device", "Mixer", "Inspector");
    private static final List<String> ARRANGE = List.of ("Clip Launcher", "I/O", "Markers", "Timeline", "FX Tracks", "Follow", "Track Height", "Fullscreen");
    private static final List<String> MIX = List.of ("Clip Launcher", "I/O", "Crossfader", "Device", "Meters", "Sends", "", "Fullscreen");
    private static final List<String> EMPTY = java.util.Collections.nCopies (8, "");
    private static final List<ToggleApplicationPanelEffect.Panel> LOWER_PANELS = List.of (
        ToggleApplicationPanelEffect.Panel.NOTE_EDITOR, ToggleApplicationPanelEffect.Panel.AUTOMATION_EDITOR,
        ToggleApplicationPanelEffect.Panel.DEVICES, ToggleApplicationPanelEffect.Panel.MIXER, ToggleApplicationPanelEffect.Panel.INSPECTOR);
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private final Map<ControlId, ApplicationUiContext> presses = new HashMap<> ();
    private final List<AuthoritativeBooleanToggle<ApplicationUiContext>> toggles = java.util.stream.IntStream.range (0, 7).mapToObj (index -> new AuthoritativeBooleanToggle<ApplicationUiContext> ()).toList ();

    @Override public String id () { return "frame-page"; }
    @Override public String installedModeId () { return "FRAME"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.APPLICATION_UI); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.toggles.stream ().anyMatch (AuthoritativeBooleanToggle::pending)); }
    @Override public void deactivate () { this.presses.clear (); this.toggles.forEach (AuthoritativeBooleanToggle::clear); }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final ApplicationUiSnapshot state = snapshot.bridge ().applicationUi ();
        if (!state.available ())
        {
            this.deactivate ();
            return List.of ();
        }
        final List<CoreEffect> effects = new ArrayList<> ();
        int toggle = -1;
        if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.BUTTON && (SurfaceArea.SOFT_KEYS_LOWER.controls ().contains (input.controlId ()) || SurfaceArea.SOFT_KEYS_UPPER.controls ().contains (input.controlId ())))
        {
            if (input.phase () == InputPhase.BEGIN)
                this.presses.putIfAbsent (input.controlId (), state.context ());
            else if (input.phase () == InputPhase.END && state.context ().equals (this.presses.remove (input.controlId ())))
            {
                final int lower = index (input.controlId (), false);
                final int upper = index (input.controlId (), true);
                if (lower >= 0 && lower < 3)
                    effects.add (new SetApplicationLayoutEffect (state.context (), SetApplicationLayoutEffect.Layout.valueOf (List.of ("ARRANGE", "MIX", "EDIT").get (lower))));
                else if (lower >= 3)
                    effects.add (new ToggleApplicationPanelEffect (state.context (), LOWER_PANELS.get (lower - 3)));
                else if (upper == 7 && ("ARRANGE".equals (state.panelLayout ()) || "MIX".equals (state.panelLayout ())))
                    effects.add (new ToggleApplicationPanelEffect (state.context (), ToggleApplicationPanelEffect.Panel.FULLSCREEN));
                else if (upper >= 0 && ("ARRANGE".equals (state.panelLayout ()) || "MIX".equals (state.panelLayout ()) && upper < 6))
                    toggle = upper;
            }
        }
        for (int index = 0; index < 7; index++)
        {
            final int column = index;
            if ("ARRANGE".equals (state.panelLayout ()))
                effects.addAll (this.toggles.get (index).update (state.context (), arrangerValue (state.arranger (), index), snapshot.monotonicTimeNanos (), toggle == index,
                    (context, enabled) -> new SetArrangerBooleanEffect (context, SetArrangerBooleanEffect.Property.values ()[column], enabled.booleanValue ())));
            else if ("MIX".equals (state.panelLayout ()) && index < 6)
                effects.addAll (this.toggles.get (index).update (state.context (), mixerValue (state.mixer (), index), snapshot.monotonicTimeNanos (), toggle == index,
                    (context, enabled) -> new SetMixerBooleanEffect (context, SetMixerBooleanEffect.Property.values ()[column], enabled.booleanValue ())));
            else
                this.toggles.get (index).clear ();
        }
        return List.copyOf (effects);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ApplicationUiSnapshot state = snapshot.bridge ().applicationUi ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, 960, 160, BLACK));
        final List<String> upper = !state.available () ? EMPTY : "ARRANGE".equals (state.panelLayout ()) ? ARRANGE : "MIX".equals (state.panelLayout ()) ? MIX : EMPTY;
        for (int index = 0; index < 8; index++)
        {
            final boolean lowerSelected = state.available () && index < 3 && List.of ("ARRANGE", "MIX", "EDIT").get (index).equals (state.panelLayout ());
            final boolean upperSelected = state.available () && ("ARRANGE".equals (state.panelLayout ()) && index < 7 ? arrangerValue (state.arranger (), index) : "MIX".equals (state.panelLayout ()) && index < 6 && mixerValue (state.mixer (), index));
            lights.put (key (index, false), !state.available () ? BLACK : lowerSelected ? WHITE : GREY);
            lights.put (key (index, true), upper.get (index).isEmpty () ? BLACK : upperSelected ? WHITE : GREY);
            if (!state.available ()) continue;
            option (commands, index, 0, upper.get (index), upperSelected);
            option (commands, index, 160 - 2 * (160.0 / 12 + 4), LOWER.get (index), lowerSelected);
            if (index == 0 && !upper.get (index).isEmpty ()) heading (commands, index, 35, "ARRANGE".equals (state.panelLayout ()) ? "Arranger" : "Mixer");
            if (index == 0 || index == 3) heading (commands, index, 80, index == 0 ? "Layouts" : "Panels");
        }
        return new ViewOutput (lights, Map.of (), new ControllerDisplayScene (960, 160, commands));
    }

    private static boolean arrangerValue (final ArrangerUiSnapshot state, final int index)
    {
        return switch (index) { case 0 -> state.clipLauncherVisible (); case 1 -> state.ioSectionVisible (); case 2 -> state.cueMarkersVisible (); case 3 -> state.timelineVisible (); case 4 -> state.effectTracksVisible (); case 5 -> state.playbackFollowEnabled (); case 6 -> state.doubleRowTrackHeight (); default -> false; };
    }

    private static boolean mixerValue (final MixerUiSnapshot state, final int index)
    {
        return switch (index) { case 0 -> state.clipLauncherVisible (); case 1 -> state.ioSectionVisible (); case 2 -> state.crossFadeVisible (); case 3 -> state.deviceSectionVisible (); case 4 -> state.meterSectionVisible (); case 5 -> state.sendSectionVisible (); default -> false; };
    }

    private static int index (final ControlId control, final boolean upper)
    {
        for (int index = 0; index < 8; index++) if (key (index, upper).equals (control)) return index;
        return -1;
    }
    private static ControlId key (final int index, final boolean upper) { return PushControlIds.button ((upper ? "ROW2_" : "ROW1_") + (index + 1)); }
    private static void option (final List<DisplayCommand> commands, final int column, final double top, final String text, final boolean selected)
    {
        if (text.isEmpty ()) return;
        final double height = 2 * (160.0 / 12 + 4);
        commands.add (new DisplayCommand.Rectangle (column * 120, top, 118, height, selected ? WHITE : new RgbColor (63, 63, 63)));
        commands.add (new DisplayCommand.TextBox (text, column * 120, top, 118, height, DisplayTextAlignment.CENTER, selected ? BLACK : WHITE, height / 2, 10, DisplayTextFit.SHRINK_ELLIPSIS));
    }
    private static void heading (final List<DisplayCommand> commands, final int column, final double top, final String text)
    {
        commands.add (new DisplayCommand.TextBox (text, column * 120, top, 240, 45, DisplayTextAlignment.LEFT, WHITE, 22.5, 14, DisplayTextFit.CLIP));
    }
}
