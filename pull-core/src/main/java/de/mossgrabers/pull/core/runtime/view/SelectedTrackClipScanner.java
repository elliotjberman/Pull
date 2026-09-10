// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.DesiredClipScan;

/** Core-owned scan traversal. A page advances only after later coherent host read-back. */
final class SelectedTrackClipScanner
{
    private DesiredClipScan desired = DesiredClipScan.inactive ();
    private long catalogGeneration = -1;

    DesiredClipScan reconcile (final long targetGeneration, final String channelId, final ClipCatalogSnapshot catalog)
    {
        if (channelId.isEmpty ())
        {
            this.desired = DesiredClipScan.inactive ();
            this.catalogGeneration = -1;
            return this.desired;
        }
        if (this.desired.targetGeneration () != targetGeneration || !this.desired.channelId ().equals (channelId) || this.catalogGeneration != catalog.generation ())
        {
            this.desired = new DesiredClipScan (targetGeneration, channelId, 0);
            this.catalogGeneration = catalog.generation ();
            return this.desired;
        }
        final var observed = catalog.scan ();
        if (observed.ready () && observed.matches (this.desired) && observed.sceneStart () == this.desired.sceneStart ())
        {
            final long next = (long) observed.sceneStart () + observed.pageSize ();
            this.desired = new DesiredClipScan (targetGeneration, channelId, next < observed.sceneCount () ? (int) next : 0);
        }
        return this.desired;
    }
}
