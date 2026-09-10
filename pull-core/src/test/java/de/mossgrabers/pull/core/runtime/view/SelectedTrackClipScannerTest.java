// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipScanSnapshot;
import de.mossgrabers.pull.core.api.DesiredClipScan;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SelectedTrackClipScannerTest
{
    @Test
    void traversesLaterAcknowledgedPagesAndRepeatsForNewClips ()
    {
        final SelectedTrackClipScanner scanner = new SelectedTrackClipScanner ();
        assertEquals (request (0), scanner.reconcile (7, "track", page (1, 0, false)));
        assertEquals (request (0), scanner.reconcile (7, "track", page (1, 8, true)));
        assertEquals (request (8), scanner.reconcile (7, "track", page (1, 0, true)));
        // Repeated rendering of the same host sample cannot skip ahead.
        assertEquals (request (8), scanner.reconcile (7, "track", page (1, 0, true)));
        assertEquals (request (8), scanner.reconcile (7, "track", page (1, 8, false)));
        assertEquals (request (16), scanner.reconcile (7, "track", page (1, 8, true)));
        assertEquals (request (0), scanner.reconcile (7, "track", page (1, 16, true)));
        assertEquals (request (8), scanner.reconcile (7, "track", page (1, 0, true)));
    }

    @Test
    void scopeExitSelectionChangeAndSceneGenerationResetTraversal ()
    {
        final SelectedTrackClipScanner scanner = new SelectedTrackClipScanner ();
        scanner.reconcile (7, "track", page (1, 0, false));
        scanner.reconcile (7, "track", page (1, 0, true));
        assertEquals (request (0), scanner.reconcile (7, "track", page (2, 8, true)));
        assertEquals (DesiredClipScan.inactive (), scanner.reconcile (7, "", page (2, 8, true)));
        assertEquals (request (0), scanner.reconcile (7, "track", page (2, 8, true)));
        assertEquals (new DesiredClipScan (8, "other", 0), scanner.reconcile (8, "other", page (2, 0, true)));
        assertEquals (new DesiredClipScan (8, "other", 0), scanner.reconcile (8, "other", page (2, 0, true)));
    }

    private static DesiredClipScan request (final int page)
    {
        return new DesiredClipScan (7, "track", page);
    }

    private static ClipCatalogSnapshot page (final long generation, final int start, final boolean ready)
    {
        return new ClipCatalogSnapshot (generation, List.of (), new ClipScanSnapshot (7, "track", 21, start, 8, ready));
    }
}
