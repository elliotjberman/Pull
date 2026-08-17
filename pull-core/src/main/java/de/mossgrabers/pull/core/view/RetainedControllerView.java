// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * One view instance shared by alternate compiled overlays in a core generation.
 *
 * <p>Each compiled workspace starts independently. This wrapper starts its delegate only once and
 * reconciles it on later first activations so page overlays cannot replace active gesture state.</p>
 */
public final class RetainedControllerView implements ControllerView
{
    private final ControllerView delegate;
    private boolean started;


    public RetainedControllerView (final ControllerView delegate)
    {
        this.delegate = Objects.requireNonNull (delegate, "delegate");
    }


    @Override
    public String id ()
    {
        return this.delegate.id ();
    }


    @Override
    public ViewProfile profile ()
    {
        return this.delegate.profile ();
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return this.delegate.bridgeSubscriptions ();
    }


    @Override
    public Set<ControllerActionBinding> actionBindings ()
    {
        return this.delegate.actionBindings ();
    }


    @Override
    public Map<ControlId, ParameterSlot> parameterBindings ()
    {
        return this.delegate.parameterBindings ();
    }


    @Override
    public Set<ParameterBankId> parameterBanks ()
    {
        return this.delegate.parameterBanks ();
    }


    @Override
    public void start (final ControllerSnapshot snapshot)
    {
        if (this.started)
            this.delegate.reconcile (snapshot);
        else
        {
            this.delegate.start (snapshot);
            this.started = true;
        }
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.delegate.reconcile (snapshot);
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        return this.delegate.handle (event, snapshot);
    }


    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        return this.delegate.resolveAction (binding, input, snapshot);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return this.delegate.render (snapshot);
    }
}
