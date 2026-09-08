// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** User selects the shared Project Macros page without changing the grid composition. */
public final class UserControlView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("USER");
    private static final ControllerPageRef PAGE = LegacyPageAliases.reference (PageId.PROJECT_MACROS);
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.USER_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.USER_BUTTON, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final PageNavigation navigation;

    public UserControlView (final PageNavigation navigation) { this.navigation = Objects.requireNonNull (navigation, "navigation"); }
    @Override public String id () { return "user-control"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        // Shift+User and reselecting the visible page are inert, including for parameter restoration.
        return PAGE.equals (this.navigation.visible ()) || snapshot.pressedControls ().contains (PushControlIds.button ("SHIFT")) ? null : ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final PageNavigation.Origin origin = this.navigation.origin ();
        return ResolvedControllerAction.of (binding.intent (), () -> {
            this.navigation.select (origin, PAGE);
            return List.of ();
        });
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final int brightness = PAGE.equals (this.navigation.visible ()) ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }
}
