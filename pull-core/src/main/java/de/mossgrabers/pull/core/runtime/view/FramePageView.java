// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.FramePagePresentation;
import de.mossgrabers.pull.core.ui.page.FramePageRenderer;
import de.mossgrabers.pull.core.view.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Frame page layout, panel choices, and authoritative visibility feedback. */
public final class FramePageView implements ControllerView
{
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
        final FramePagePresentation.Layout layout = switch (state.panelLayout ())
        {
            case "ARRANGE" -> FramePagePresentation.Layout.ARRANGE;
            case "MIX" -> FramePagePresentation.Layout.MIX;
            case "EDIT" -> FramePagePresentation.Layout.EDIT;
            default -> FramePagePresentation.Layout.OTHER;
        };
        final List<Boolean> upperSelected = new ArrayList<> (8);
        for (int index = 0; index < 8; index++)
        {
            final boolean selected = switch (layout)
            {
                case ARRANGE -> index < 7 && arrangerValue (state.arranger (), index);
                case MIX -> index < 6 && mixerValue (state.mixer (), index);
                default -> false;
            };
            upperSelected.add (Boolean.valueOf (state.available () && selected));
        }
        final var visuals = FramePageRenderer.render (new FramePagePresentation (state.available (), layout, upperSelected));
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display ());
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
}
