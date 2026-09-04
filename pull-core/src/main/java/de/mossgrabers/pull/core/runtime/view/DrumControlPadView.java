// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingTarget;
import de.mossgrabers.pull.core.api.ControllerMappingValue;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


/** Four Bitwig-mappable control pads with authoritative mapped-state toggle behavior and feedback. */
public final class DrumControlPadView implements ControllerView
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor ON = new RgbColor (255, 0, 0);
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "control-pads",
        Set.of (
            new SurfaceClaim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());
    @Override
    public String id ()
    {
        return "drum-control-pads";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ControllerMappingFeedbackSnapshot feedback = snapshot.bridge ().controllerMappingFeedback ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final Set<ControllerMappingBinding> bindings = new LinkedHashSet<> (CoreControls.DRUM_CONTROL_PADS.size ());
        final boolean ready = CoreControllerMappings.DRUM_CONTROL_PADS.stream ().allMatch (feedback::supports);
        for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
        {
            final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (slot);
            final var mappingId = CoreControllerMappings.DRUM_CONTROL_PADS.get (slot);
            final ControllerMappingTarget target = feedback.targets ().get (mappingId);
            final boolean on = ready && target.hasTarget () && target.value () >= 0.5;
            lights.put (control, on ? ON : OFF);
            if (ready)
                bindings.add (new ControllerMappingBinding (control, mappingId, on ? ControllerMappingValue.MINIMUM : ControllerMappingValue.MAXIMUM));
        }
        return new ViewOutput (
            lights,
            Map.of (),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            DesiredNotePerformance.inactive (),
            DesiredNoteRepeat.unowned (),
            ready ? new DesiredControllerMappings (bindings) : DesiredControllerMappings.empty ());
    }
}
