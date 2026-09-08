// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.page.*;
import de.mossgrabers.pull.core.view.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns presentation while the selected legacy page's controls and lights remain frozen. */
public final class LegacyPageDisplayView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("observed-page-display", Set.of (
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final ControllerDisplayScene BLANK = new ControllerDisplayScene (960, 160, List.of (
        new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0))));
    private final PageNavigation navigation;

    public LegacyPageDisplayView (final PageNavigation navigation) { this.navigation = java.util.Objects.requireNonNull (navigation); }
    @Override public String id () { return "observed-page-display"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_PAGE_DISPLAY); }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ControllerPageDisplaySnapshot observed = snapshot.bridge ().pageDisplay ();
        if (this.navigation.visible ().kind () != ControllerPageRef.Kind.LEGACY || !this.navigation.legacyAlias ().equals (observed.modeId ()))
            return new ViewOutput (Map.of (), Map.of (), BLANK);
        final ControllerDisplayScene scene = switch (observed.state ())
        {
            case DevicePageState state -> DevicePageRenderer.render (state);
            case OptionPageState state -> OptionPageRenderer.render (state);
            case EditingPageState state -> EditingPageRenderer.render (state);
            case ControllerPageDisplayState.Empty state -> BLANK;
        };
        return new ViewOutput (Map.of (), Map.of (), scene.isPresent () ? scene : BLANK);
    }
}
