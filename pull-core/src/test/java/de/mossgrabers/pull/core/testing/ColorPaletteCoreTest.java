// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ColorPaletteCoreTest
{
    @Test
    void observedColorViewReplacesSessionGridOutputWithoutTakingItsPadActions ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        final RgbColor first = new RgbColor (201, 24, 78);
        final RgbColor second = new RgbColor (60, 130, 230);
        final RgbColor off = new RgbColor (0, 0, 0);
        final var colors = new ColorPaletteSnapshot (0, List.of (first, second));
        host.initialBridge (bridge (1, "SESSION", ColorPaletteSnapshot.empty ()));
        host.start (Optional.empty ());
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (1), InputKind.PAD));

        host.bridge (bridge (2, "COLOR", colors));
        assertEquals (first, host.effects ().desiredOutput ().lights ().get (PushControlIds.pad (1)));
        assertEquals (second, host.effects ().desiredOutput ().lights ().get (PushControlIds.pad (2)));
        assertEquals (off, host.effects ().desiredOutput ().lights ().get (PushControlIds.pad (64)));
        assertTrue (host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (1), InputKind.PAD).isEmpty (), "the unchanged stable color gesture remains the sole pad input path");
        assertFalse (host.effects ().desiredControllerWorkspace ().facets ().contains (ControllerViewFacet.SESSION_GRID_FULL));

        host.bridge (bridge (3, "COLOR", ColorPaletteSnapshot.empty ()));
        for (int pad = 1; pad <= 64; pad++) assertEquals (off, host.effects ().desiredOutput ().lights ().get (PushControlIds.pad (pad)));
        host.bridge (bridge (4, "SESSION", colors));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (1), InputKind.PAD));
        assertNotEquals (first, host.effects ().desiredOutput ().lights ().get (PushControlIds.pad (1)), "retained palette data cannot paint the restored Session grid");
    }

    private static ControllerBridgeSnapshot bridge (final long generation, final String view, final ColorPaletteSnapshot colors)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        final var layout = new ControllerLayoutSnapshot (generation, view, "TRACK", false, false, 0, GridPressureConfiguration.OFF);
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), e.controllerPages (), e.browser (), e.controllerHardware (), new ControllerPageDisplaySnapshot ("TRACK", new ControllerPageDisplayState.Empty (), colors));
    }
}
