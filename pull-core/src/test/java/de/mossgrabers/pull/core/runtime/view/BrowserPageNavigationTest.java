// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BrowserPageNavigationTest
{
    private static final ControllerSnapshot SNAPSHOT = new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
    private final PageNavigation pages = PageNavigation.defaults ();
    private final CompiledWorkspace workspace = CompiledWorkspace.compile ("browser-actions", List.of ());
    private final BrowserPageNavigation browser = new BrowserPageNavigation (this.pages);
    BrowserPageNavigationTest () { this.workspace.start (SNAPSHOT); }

    @Test
    void openAndCloseBeforeSamplingNeverSelectAnInactiveBrowser ()
    {
        this.browser.reconcile (new BrowserSnapshot (1, true));
        this.browser.reconcile (new BrowserSnapshot (2, false));
        assertNull (this.browser.resolveAction ());
        assertEquals ("TRACK", this.pages.legacyAlias ());
    }

    @Test
    void lateCloseCannotDismissAReplacementPage ()
    {
        this.browser.reconcile (new BrowserSnapshot (1, true));
        this.dispatch (this.browser.resolveAction ());
        assertEquals ("BROWSER", this.pages.legacyAlias ());
        final long newer = this.pages.temporary (this.pages.origin (), this.pages.resolve ("ACCENT"));
        this.browser.reconcile (new BrowserSnapshot (2, false));
        this.dispatch (this.browser.resolveAction ());
        assertEquals (newer, this.pages.state ().temporaryToken ());
        assertEquals ("ACCENT", this.pages.legacyAlias ());
    }

    @Test
    void reopeningInvalidatesTheOlderDeferredClose ()
    {
        this.browser.reconcile (new BrowserSnapshot (1, true));
        this.dispatch (this.browser.resolveAction ());
        this.browser.reconcile (new BrowserSnapshot (2, false));
        final var close = this.browser.resolveAction ();
        this.browser.reconcile (new BrowserSnapshot (3, true));
        this.dispatch (close);
        this.dispatch (this.browser.resolveAction ());
        assertEquals ("BROWSER", this.pages.legacyAlias ());
        this.browser.reconcile (new BrowserSnapshot (4, false));
        this.dispatch (this.browser.resolveAction ());
        assertEquals ("TRACK", this.pages.legacyAlias ());
    }

    private void dispatch (final ResolvedControllerAction action)
    {
        if (action != null) this.workspace.dispatchAction (action, SNAPSHOT);
    }
}
