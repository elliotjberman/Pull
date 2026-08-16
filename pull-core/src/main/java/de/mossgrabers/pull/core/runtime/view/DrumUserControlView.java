// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetUserControlValueEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Four Bitwig-mappable Boolean controls with authoritative red/off feedback. */
public final class DrumUserControlView implements ControllerView
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor ON = new RgbColor (255, 0, 0);
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "user-controls",
        Set.of (
            new SurfaceClaim (SurfaceArea.DRUM_USER_CONTROL_PADS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.DRUM_USER_CONTROL_PADS, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());


    @Override
    public String id ()
    {
        return "drum-user-controls";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.USER_CONTROLS);
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.PAD || input.phase () != InputPhase.BEGIN)
            return List.of ();
        final int slot = CoreControls.DRUM_USER_CONTROLS.indexOf (input.controlId ());
        if (slot < 0 || !snapshot.bridge ().userControls ().available ())
            return List.of ();
        final boolean on = snapshot.bridge ().userControls ().values ().get (slot).doubleValue () >= 0.5;
        return List.of (new SetUserControlValueEffect (slot, on ? 0 : 1));
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int slot = 0; slot < CoreControls.DRUM_USER_CONTROLS.size (); slot++)
        {
            final boolean on = snapshot.bridge ().userControls ().available () && snapshot.bridge ().userControls ().values ().get (slot).doubleValue () >= 0.5;
            lights.put (CoreControls.DRUM_USER_CONTROLS.get (slot), on ? ON : OFF);
        }
        return new ViewOutput (lights, Map.of ());
    }
}
