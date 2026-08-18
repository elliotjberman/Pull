// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Core-owned selected-track Mix controls above an independently composable track footer. */
public final class TrackMixerControlsView implements ControllerView
{
    private static final double PARAMETER_STEP_SIZE = 10.0;
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = createParameterBindings ();
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "selected-track-mix",
        Set.of (
            new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.TRACK_MIXER_PAGE));


    @Override
    public String id ()
    {
        return "track-mixer-controls";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.SELECTED_TRACK);
    }


    @Override
    public Set<ParameterBankId> parameterBanks ()
    {
        return Set.of (ParameterBankId.ACTIVE);
    }


    @Override
    public Map<ControlId, ParameterSlot> parameterBindings ()
    {
        return PARAMETER_BINDINGS;
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.RELATIVE)
            return List.of ();
        final ParameterSlot slot = PARAMETER_BINDINGS.get (input.controlId ());
        final ParameterTargetSnapshot target = slot == null ? null : snapshot.bridge ().parameters ().slots ().get (slot);
        return target == null ? List.of () : List.of (new AdjustParameterValueEffect (target.target (), input.value () * PARAMETER_STEP_SIZE));
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (
            Map.of (),
            Map.of (),
            TrackMixerDisplayScene.render (snapshot.bridge ().selectedTrack (), snapshot.bridge ().parameters ().slots ()));
    }


    private static Map<ControlId, ParameterSlot> createParameterBindings ()
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> (ParameterSlot.BANK_SIZE);
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            bindings.put (PushControlIds.continuous ("KNOB" + (index + 1)), ParameterSlot.active (index));
        return Map.copyOf (bindings);
    }
}
