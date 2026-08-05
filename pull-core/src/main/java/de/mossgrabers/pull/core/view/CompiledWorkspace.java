// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.DesiredParameterLeases;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.SessionBankShape;
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
    private final String                               name;
    private final List<CompiledView>                   views;
    private final DesiredInputRoutes                   desiredInputRoutes;
    private final DesiredBridgeSubscriptions           desiredBridgeSubscriptions;
    private final DesiredControllerWorkspace           desiredControllerWorkspace;
    private final Map<RouteKey, List<ControllerView>>  inputOwners;
    private final Map<ControlId, List<ControllerView>> directInputOwners;
    private final List<ControllerView>                 eventObservers;

    private boolean                                    started;


    private CompiledWorkspace (final String name, final List<CompiledView> views, final DesiredInputRoutes desiredInputRoutes, final DesiredBridgeSubscriptions desiredBridgeSubscriptions, final DesiredControllerWorkspace desiredControllerWorkspace, final Map<RouteKey, List<ControllerView>> inputOwners, final Map<ControlId, List<ControllerView>> directInputOwners)
    {
        this.name = name;
        this.views = views;
        this.desiredInputRoutes = desiredInputRoutes;
        this.desiredBridgeSubscriptions = desiredBridgeSubscriptions;
        this.desiredControllerWorkspace = desiredControllerWorkspace;
        this.inputOwners = inputOwners;
        this.directInputOwners = directInputOwners;
        this.eventObservers = views.stream ().map (CompiledView::view).toList ();
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
        return compile (name, SessionBankShape.empty (), views);
    }


    /**
     * Compile a workspace with a fixed Session bank shape. Declaration order does not affect
     * routing or output order.
     *
     * @param name Workspace name
     * @param sessionBankShape Session bank shape, or empty when no Session grid is present
     * @param views Views to compose
     * @return Compiled workspace
     */
    public static CompiledWorkspace compile (final String name, final SessionBankShape sessionBankShape, final List<? extends ControllerView> views)
    {
        final String checkedName = Objects.requireNonNull (name, "name").strip ();
        if (checkedName.isEmpty ())
            throw new IllegalArgumentException ("workspace name must not be blank");

        final List<CompiledView> orderedViews = new ArrayList<> ();
        Objects.requireNonNull (views, "views").forEach (view -> orderedViews.add (compileView (view)));
        orderedViews.sort (Comparator.comparing (CompiledView::id));
        validateViews (orderedViews);
        final DesiredControllerWorkspace controllerWorkspace = compileControllerWorkspace (checkedName, Objects.requireNonNull (sessionBankShape, "sessionBankShape"), orderedViews);
        return new CompiledWorkspace (
            checkedName,
            List.copyOf (orderedViews),
            compileInputRoutes (orderedViews),
            compileBridgeSubscriptions (orderedViews),
            controllerWorkspace,
            compileInputOwners (orderedViews),
            compileDirectInputOwners (orderedViews));
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
     * Get the complete stable-adapter manifest compiled from the selected view profiles.
     *
     * @return Desired controller workspace, or empty when all views are core-native
     */
    public DesiredControllerWorkspace desiredControllerWorkspace ()
    {
        return this.desiredControllerWorkspace;
    }


    /**
     * Get the selected profile for every composed view.
     *
     * @return Profiles keyed by stable view ID
     */
    public Map<String, ViewProfile> profiles ()
    {
        final Map<String, ViewProfile> profiles = new LinkedHashMap<> ();
        this.views.forEach (view -> profiles.put (view.id (), view.profile ()));
        return Map.copyOf (profiles);
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

        for (final CompiledView view: this.views)
            view.view ().start (snapshot);
        this.started = true;
        return this.render (snapshot, List.of ());
    }


    /**
     * Activate this workspace from the latest authoritative snapshot. The first activation starts
     * every view; later activations reconcile retained view state before rendering.
     *
     * @param snapshot Current snapshot
     * @return Complete core result
     */
    public CoreResult activate (final ControllerSnapshot snapshot)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        if (!this.started)
            return this.start (snapshot);

        for (final CompiledView view: this.views)
            view.view ().reconcile (snapshot);
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
        for (final CompiledView view: this.views)
            view.view ().reconcile (snapshot);
        for (final ControllerView view: this.receivers (event))
            effects.addAll (view.handle (event, snapshot));
        return this.render (snapshot, effects);
    }


    private CoreResult render (final ControllerSnapshot snapshot, final List<CoreEffect> effects)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final Map<ControlId, ClipTargetId> clipBindings = new LinkedHashMap<> ();
        for (final CompiledView view: this.views)
        {
            final ViewOutput output = Objects.requireNonNull (view.view ().render (snapshot), "view output");
            mergeUnique (lights, output.lights (), "light", view.id ());
            mergeUnique (clipBindings, output.clipBindings (), "clip binding", view.id ());
        }

        return new CoreResult (
            new DesiredHardwareOutput (lights),
            this.desiredInputRoutes,
            this.desiredBridgeSubscriptions,
            clipBindings,
            this.desiredControllerWorkspace,
            DesiredParameterLeases.empty (),
            effects);
    }


    private static CompiledView compileView (final ControllerView view)
    {
        final ControllerView checkedView = Objects.requireNonNull (view, "view");
        final String id = Objects.requireNonNull (checkedView.id (), "view id").strip ();
        if (id.isEmpty ())
            throw new IllegalArgumentException ("view id must not be blank");
        return new CompiledView (id, checkedView, Objects.requireNonNull (checkedView.profile (), "view profile"));
    }


    private static void validateViews (final List<CompiledView> views)
    {
        final Set<String> ids = new LinkedHashSet<> ();
        final List<OwnedClaim> claims = new ArrayList<> ();
        for (final CompiledView view: views)
        {
            if (!ids.add (view.id ()))
                throw new IllegalArgumentException ("duplicate view id: " + view.id ());

            for (final SurfaceClaim claim: view.profile ().claims ())
                claims.add (new OwnedClaim (view.id (), claim));
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


    private static DesiredControllerWorkspace compileControllerWorkspace (final String name, final SessionBankShape sessionBankShape, final List<CompiledView> views)
    {
        final Set<ControllerViewFacet> facets = new LinkedHashSet<> ();
        views.forEach (view -> facets.addAll (view.profile ().controllerFacets ()));
        if (facets.isEmpty ())
        {
            if (sessionBankShape.isPresent ())
                throw new IllegalArgumentException ("Session bank shape requires stable controller facets");
            return DesiredControllerWorkspace.empty ();
        }

        if (facets.contains (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) && !facets.contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER))
            throw new IllegalArgumentException ("upper Session scene keys require the upper Session clip grid");
        if (facets.contains (ControllerViewFacet.DRUM_PITCH_BEND) && !facets.contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER))
            throw new IllegalArgumentException ("drum pitch bend requires the lower Drum controller");
        return new DesiredControllerWorkspace (name, facets, sessionBankShape);
    }


    private static boolean conflicts (final SurfaceClaim left, final SurfaceClaim right)
    {
        if (!left.area ().overlaps (right.area ()))
            return false;
        if (left.kind ().ownsOutput () && right.kind ().ownsOutput ())
            return true;
        return left.kind ().ownsInput () && right.kind ().ownsInput ();
    }


    private static DesiredInputRoutes compileInputRoutes (final List<CompiledView> views)
    {
        final Map<RouteKey, InputRouteMode> routes = new LinkedHashMap<> ();
        for (final CompiledView view: views)
        {
            for (final SurfaceClaim claim: view.profile ().claims ())
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
                    for (final InputKind inputKind: claim.area ().inputKinds ())
                    {
                        final RouteKey key = new RouteKey (control, inputKind);
                        routes.merge (key, mode, (left, right) -> left == InputRouteMode.EXCLUSIVE || right == InputRouteMode.EXCLUSIVE ? InputRouteMode.EXCLUSIVE : InputRouteMode.OBSERVE);
                    }
                }
            }
        }

        final Set<InputRoute> inputRoutes = new LinkedHashSet<> ();
        routes.forEach ( (key, mode) -> inputRoutes.add (new InputRoute (key.control (), key.kind (), mode)));
        return new DesiredInputRoutes (inputRoutes);
    }


    private static DesiredBridgeSubscriptions compileBridgeSubscriptions (final List<CompiledView> views)
    {
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> ();
        views.forEach (view -> subscriptions.addAll (Set.copyOf (Objects.requireNonNull (view.view ().bridgeSubscriptions (), "bridge subscriptions"))));
        return new DesiredBridgeSubscriptions (subscriptions);
    }


    private static Map<RouteKey, List<ControllerView>> compileInputOwners (final List<CompiledView> views)
    {
        final Map<RouteKey, List<ControllerView>> owners = new LinkedHashMap<> ();
        for (final CompiledView view: views)
        {
            for (final SurfaceClaim claim: view.profile ().claims ())
            {
                if (!claim.kind ().isInput () || claim.kind () == SurfaceClaim.Kind.STABLE_ADAPTER_INPUT)
                    continue;
                for (final ControlId control: claim.area ().controls ())
                {
                    for (final InputKind kind: claim.area ().inputKinds ())
                        owners.computeIfAbsent (new RouteKey (control, kind), ignored -> new ArrayList<> ()).add (view.view ());
                }
            }
        }
        return immutableOwnerMap (owners);
    }


    private static Map<ControlId, List<ControllerView>> compileDirectInputOwners (final List<CompiledView> views)
    {
        final Map<ControlId, List<ControllerView>> owners = new LinkedHashMap<> ();
        for (final CompiledView view: views)
        {
            for (final SurfaceClaim claim: view.profile ().claims ())
            {
                if (!claim.kind ().isInput () || claim.kind () == SurfaceClaim.Kind.STABLE_ADAPTER_INPUT)
                    continue;
                for (final ControlId control: claim.area ().controls ())
                    owners.computeIfAbsent (control, ignored -> new ArrayList<> ()).add (view.view ());
            }
        }
        return immutableOwnerMap (owners);
    }


    private List<ControllerView> receivers (final CoreEvent event)
    {
        if (event instanceof final ButtonInputEvent button)
            return this.directInputOwners.getOrDefault (button.controlId (), List.of ());
        if (event instanceof final ControllerInputEvent input)
            return this.inputOwners.getOrDefault (new RouteKey (input.controlId (), input.kind ()), List.of ());
        if (event instanceof final TouchInputEvent touch)
            return this.inputOwners.getOrDefault (new RouteKey (touch.controlId (), InputKind.TOUCH), List.of ());
        return this.eventObservers;
    }


    private static <K> Map<K, List<ControllerView>> immutableOwnerMap (final Map<K, List<ControllerView>> owners)
    {
        final Map<K, List<ControllerView>> result = new LinkedHashMap<> ();
        owners.forEach ( (key, value) -> result.put (key, List.copyOf (value)));
        return Map.copyOf (result);
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


    private record CompiledView (String id, ControllerView view, ViewProfile profile)
    {
    }


    private record RouteKey (ControlId control, InputKind kind)
    {
    }
}
