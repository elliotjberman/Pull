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

/** Setup-button entry and exact-page return, available around every workspace. */
public final class SetupControlView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("SETUP");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.SETUP_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SETUP_BUTTON, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final PageNavigation navigation;

    public SetupControlView (final PageNavigation navigation) { this.navigation = Objects.requireNonNull (navigation, "navigation"); }
    @Override public String id () { return "setup-control"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final PageNavigation.Origin origin = this.navigation.origin ();
        final boolean restore = this.navigation.visible ().equals (this.navigation.resolve ("SETUP"));
        return ResolvedControllerAction.of (binding.intent (), () -> {
            if (restore) this.navigation.restore (origin);
            else this.navigation.temporary (origin, this.navigation.resolve ("SETUP"));
            return List.of ();
        });
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final String page = this.navigation.legacyAlias ();
        final int brightness = "SETUP".equals (page) || "INFO".equals (page) ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }
}
