// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.event.InputKind;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Complete replayable set of controller inputs observed or exclusively owned by the reloadable
 * core. Temporary deferred and suppressed stable dispatch remain explicit core policy; they are
 * distinct from permanent exclusive ownership.
 *
 * <p>A route absent from this set remains solely with the stable controller. The shell freezes an
 * routed edge acquired at gesture begin until the matching end event. Exclusive ownership is
 * accepted only for a physical input whose stable semantic binding is deliberately inert.</p>
 *
 */
public final class DesiredInputRoutes
{
    private static final DesiredInputRoutes EMPTY = new DesiredInputRoutes (Set.of ());

    private final Set<InputRoute> routes;
    private final Map<ControlId, Map<InputKind, InputRouteMode>> routeModes;


    /**
     * Validate and copy the routes.
     */
    public DesiredInputRoutes (final Set<InputRoute> routes)
    {
        this.routes = Set.copyOf (Objects.requireNonNull (routes, "routes"));
        final Map<ControlId, EnumMap<InputKind, InputRouteMode>> modes = new LinkedHashMap<> ();
        for (final InputRoute route: this.routes)
        {
            final EnumMap<InputKind, InputRouteMode> controlModes = modes.computeIfAbsent (route.controlId (), ignored -> new EnumMap<> (InputKind.class));
            if (controlModes.putIfAbsent (route.kind (), route.mode ()) != null)
                throw new IllegalArgumentException ("input routes must have unique control and kind pairs");
        }
        final Map<ControlId, Map<InputKind, InputRouteMode>> copiedModes = new LinkedHashMap<> ();
        modes.forEach ( (control, controlModes) -> copiedModes.put (control, Map.copyOf (controlModes)));
        this.routeModes = Map.copyOf (copiedModes);
    }


    /**
     * Get the complete immutable route set.
     *
     * @return Routes
     */
    public Set<InputRoute> routes ()
    {
        return this.routes;
    }


    /**
     * Get empty desired routing, which leaves every unmigrated input with the stable controller.
     *
     * @return Empty desired routing
     */
    public static DesiredInputRoutes empty ()
    {
        return EMPTY;
    }


    /**
     * Get the core disposition for an input route.
     *
     * @param controlId Physical control
     * @param kind Input kind
     * @return Route mode, or empty when only the stable controller receives the input
     */
    public Optional<InputRouteMode> mode (final ControlId controlId, final InputKind kind)
    {
        return Optional.ofNullable (this.modeOrNull (controlId, kind));
    }


    /**
     * Get a route mode without allocating an {@link Optional}. Intended for the hot input path.
     *
     * @param controlId Physical control
     * @param kind Input kind
     * @return Route mode, or {@code null} when only the stable controller receives the input
     */
    public InputRouteMode modeOrNull (final ControlId controlId, final InputKind kind)
    {
        final Map<InputKind, InputRouteMode> controlModes = this.routeModes.get (Objects.requireNonNull (controlId, "controlId"));
        return controlModes == null ? null : controlModes.get (Objects.requireNonNull (kind, "kind"));
    }


    /**
     * Test whether the core receives an input route.
     *
     * @param controlId Physical control
     * @param kind Input kind
     * @return True for observed or exclusively owned routes
     */
    public boolean observes (final ControlId controlId, final InputKind kind)
    {
        return this.modeOrNull (controlId, kind) != null;
    }


    /**
     * Test whether the core exclusively owns an input route.
     *
     * @param controlId Physical control
     * @param kind Input kind
     * @return True for an exclusive route
     */
    public boolean ownsExclusively (final ControlId controlId, final InputKind kind)
    {
        return this.modeOrNull (controlId, kind) == InputRouteMode.EXCLUSIVE;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object object)
    {
        return this == object || object instanceof final DesiredInputRoutes other && this.routes.equals (other.routes);
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return this.routes.hashCode ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "DesiredInputRoutes[routes=" + this.routes + "]";
    }
}
