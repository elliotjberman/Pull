// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Objects;
import java.util.Set;


/** Finishes admitted touches after their parameter page has departed, without admitting new ones. */
public final class ParameterTouchReleaseView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("retained-release", Set.of (new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private final ParameterTouchSession session;


    public ParameterTouchReleaseView (final ParameterTouchSession session)
    {
        this.session = Objects.requireNonNull (session, "session");
    }


    @Override
    public String id ()
    {
        return "parameter-touch-release";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.AUTOMATION);
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.TOUCH && input.phase () == InputPhase.END)
            return this.session.end (input.controlId (), snapshot.bridge ().automation ());
        return List.of ();
    }
}
