// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.TouchInputEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Validated, deterministic composition of fixed-footprint views.
 */
public final class CompiledWorkspace
{
    private final String                     name;
    private final List<ControllerView>       views;
    private final DesiredInputRoutes         desiredInputRoutes;
    private final DesiredBridgeSubscriptions desiredBridgeSubscriptions;

    private boolean                          started;


    private CompiledWorkspace (final String name, final List<ControllerView> views, final DesiredInputRoutes desiredInputRoutes, final DesiredBridgeSubscriptions desiredBridgeSubscriptions)
    {
        this.name = name;
        this.views = views;
        this.desiredInputRoutes = desiredInputRoutes;
        this.desiredBridgeSubscriptions = desiredBridgeSubscriptions;
    }


    /**
     * Compile a workspace. Declaration order does not affect routing or output order.
     *
     * @param name Workspace name
     * @param views Views to compose
     * @return Compiled workspace
     */
    public static CompiledWorkspace compile (final String name, final List<? extends ControllerView> views)
    {
        final String checkedName = Objects.requireNonNull (name, "name").strip ();
        if (checkedName.isEmpty ())
            throw new IllegalArgumentException ("workspace name must not be blank");

        final List<ControllerView> orderedViews = new ArrayList<> (Objects.requireNonNull (views, "views"));
        orderedViews.forEach (view -> Objects.requireNonNull (view, "view"));
        orderedViews.sort (Comparator.comparing (ControllerView::id));
        validateViews (orderedViews);
        return new CompiledWorkspace (
            checkedName,
            List.copyOf (orderedViews),
            compileInputRoutes (orderedViews),
            compileBridgeSubscriptions (orderedViews));
    }


    /**
     * Get the workspace name.
     *
     * @return Name
     */
    public String name ()
    {
        return this.name;
    }


    /**
     * Start every view and render the complete workspace.
     *
     * @param snapshot Initial snapshot
     * @return Complete core result
     */
    public CoreResult start (final ControllerSnapshot snapshot)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        if (this.started)
            throw new IllegalStateException ("Workspace can only be started once");

        for (final ControllerView view: this.views)
            view.start (snapshot);
        this.started = true;
        return this.render (snapshot, List.of ());
    }


    /**
     * Route one event and render the complete workspace.
     *
     * @param event Event
     * @param snapshot Authoritative state after the event
     * @return Complete core result
     */
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireStarted ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");

        final List<CoreEffect> effects = new ArrayList<> ();
        for (final ControllerView view: this.views)
            view.reconcile (snapshot);
        for (final ControllerView view: this.views)
        {
            if (receives (view, event))
                effects.addAll (view.handle (event, snapshot));
        }
        return this.render (snapshot, effects);
    }


    private CoreResult render (final ControllerSnapshot snapshot, final List<CoreEffect> effects)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final Map<ControlId, ClipTargetId> clipBindings = new LinkedHashMap<> ();
        DesiredControllerWorkspace controllerWorkspace = DesiredControllerWorkspace.empty ();
        String controllerWorkspaceOwner = "";
        for (final ControllerView view: this.views)
        {
            final ViewOutput output = Objects.requireNonNull (view.render (snapshot), "view output");
            mergeUnique (lights, output.lights (), "light", view.id ());
            mergeUnique (clipBindings, output.clipBindings (), "clip binding", view.id ());
            if (output.controllerWorkspace ().isActive ())
            {
                if (controllerWorkspace.isActive ())
                    throw new IllegalStateException ("multiple controller-workspace owners: " + controllerWorkspaceOwner + " and " + view.id ());
                controllerWorkspace = output.controllerWorkspace ();
                controllerWorkspaceOwner = view.id ();
            }
        }

        return new CoreResult (
            new DesiredHardwareOutput (lights),
            this.desiredInputRoutes,
            this.desiredBridgeSubscriptions,
            clipBindings,
            controllerWorkspace,
            effects);
    }


    private static void validateViews (final List<ControllerView> views)
    {
        final Set<String> ids = new LinkedHashSet<> ();
        final List<OwnedClaim> claims = new ArrayList<> ();
        for (final ControllerView view: views)
        {
            final String id = Objects.requireNonNull (view.id (), "view id").strip ();
            if (id.isEmpty ())
                throw new IllegalArgumentException ("view id must not be blank");
            if (!ids.add (id))
                throw new IllegalArgumentException ("duplicate view id: " + id);

            for (final SurfaceClaim claim: Set.copyOf (Objects.requireNonNull (view.claims (), "view claims")))
                claims.add (new OwnedClaim (id, Objects.requireNonNull (claim, "surface claim")));
        }

        for (int leftIndex = 0; leftIndex < claims.size (); leftIndex++)
        {
            final OwnedClaim left = claims.get (leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < claims.size (); rightIndex++)
            {
                final OwnedClaim right = claims.get (rightIndex);
                if (conflicts (left.claim (), right.claim ()))
                {
                    throw new IllegalArgumentException (
                        "workspace claim conflict between " + left.viewId () + " and " + right.viewId () +
                            " on " + left.claim ().area () + "/" + right.claim ().area ());
                }
            }
        }
    }


    private static boolean conflicts (final SurfaceClaim left, final SurfaceClaim right)
    {
        if (!left.area ().overlaps (right.area ()))
            return false;
        if (left.kind () == SurfaceClaim.Kind.OUTPUT && right.kind () == SurfaceClaim.Kind.OUTPUT)
            return true;
        return left.kind ().ownsInput () && right.kind ().ownsInput ();
    }


    private static DesiredInputRoutes compileInputRoutes (final List<ControllerView> views)
    {
        final Map<RouteKey, InputRouteMode> routes = new LinkedHashMap<> ();
        for (final ControllerView view: views)
        {
            for (final SurfaceClaim claim: view.claims ())
            {
                final InputRouteMode mode;
                if (claim.kind () == SurfaceClaim.Kind.OBSERVE_INPUT)
                    mode = InputRouteMode.OBSERVE;
                else if (claim.kind () == SurfaceClaim.Kind.EXCLUSIVE_INPUT)
                    mode = InputRouteMode.EXCLUSIVE;
                else
                    continue;

                for (final ControlId control: claim.area ().controls ())
                {
                    final RouteKey key = new RouteKey (control, claim.area ().inputKind ());
                    routes.merge (key, mode, (left, right) -> left == InputRouteMode.EXCLUSIVE || right == InputRouteMode.EXCLUSIVE ? InputRouteMode.EXCLUSIVE : InputRouteMode.OBSERVE);
                }
            }
        }

        final Set<InputRoute> inputRoutes = new LinkedHashSet<> ();
        routes.forEach ( (key, mode) -> inputRoutes.add (new InputRoute (key.control (), key.kind (), mode)));
        return new DesiredInputRoutes (inputRoutes);
    }


    private static DesiredBridgeSubscriptions compileBridgeSubscriptions (final List<ControllerView> views)
    {
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> ();
        views.forEach (view -> subscriptions.addAll (Set.copyOf (Objects.requireNonNull (view.bridgeSubscriptions (), "bridge subscriptions"))));
        return new DesiredBridgeSubscriptions (subscriptions);
    }


    private static boolean receives (final ControllerView view, final CoreEvent event)
    {
        if (!(event instanceof ButtonInputEvent || event instanceof ControllerInputEvent || event instanceof TouchInputEvent))
            return true;

        for (final SurfaceClaim claim: view.claims ())
        {
            if (claim.kind ().isInput () && claim.area ().contains (event))
                return true;
        }
        return false;
    }


    private static <V> void mergeUnique (final Map<ControlId, V> target, final Map<ControlId, V> source, final String outputName, final String viewId)
    {
        source.forEach ( (control, value) -> {
            if (target.putIfAbsent (control, value) != null)
                throw new IllegalStateException ("duplicate " + outputName + " for " + control + " while rendering " + viewId);
        });
    }


    private void requireStarted ()
    {
        if (!this.started)
            throw new IllegalStateException ("Workspace is not started");
    }


    private record OwnedClaim (String viewId, SurfaceClaim claim)
    {
    }


    private record RouteKey (ControlId control, InputKind kind)
    {
    }
}
