// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.ColorPaletteRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Palette lights only; the existing color-selection gesture remains with its frozen adapter. */
public final class ColorPaletteView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("color-palette", Set.of (
        new SurfaceClaim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.GRID_LOWER, SurfaceClaim.Kind.OUTPUT)), Set.of ());

    @Override public String id () { return "color-palette"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_PAGE_DISPLAY); }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var palette = "COLOR".equals (snapshot.bridge ().layout ().viewId ()) ? snapshot.bridge ().pageDisplay ().colorPalette () : ColorPaletteSnapshot.empty ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> (64);
        ColorPaletteRenderer.pads (palette).forEach ((position, color) -> lights.put (PushControlIds.pad (position.row () * 8 + position.column () + 1), color));
        return new ViewOutput (lights, Map.of ());
    }
}
