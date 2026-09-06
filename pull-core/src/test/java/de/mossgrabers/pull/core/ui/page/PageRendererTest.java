// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Renderers consume immutable presentation values without constructing a host or controller. */
class PageRendererTest
{
    @Test
    void unavailableFrameCannotRenderInventedOptionsEvenWithSelectedPresentationFlags ()
    {
        final var output = FramePageRenderer.render (new FramePagePresentation (false, FramePagePresentation.Layout.ARRANGE, Collections.nCopies (8, Boolean.TRUE)));
        assertEquals (List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0))), output.display ().commands ());
        assertEquals (16, output.lights ().size ());
        assertTrue (output.lights ().values ().stream ().allMatch (new RgbColor (0, 0, 0)::equals));
    }

    @Test
    void unknownWritingModeDoesNotPretendThatReadOrAnotherWriteModeIsSelected ()
    {
        final var output = SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, true, AutomationWriteMode.UNKNOWN));
        for (int index = 1; index <= 4; index++)
            assertEquals (new RgbColor (30, 30, 30), output.lights ().get (PushControlIds.button ("ROW1_" + index)));
    }
}
