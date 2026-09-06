// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** One primitive against the independently fenced current-bank navigation origin. */
public record CurrentTrackNavigationEffect (long navigationGeneration, String bankId, Operation operation) implements CoreEffect
{
    public enum Operation
    {
        TRACK_SCROLL_PREVIOUS, TRACK_SCROLL_NEXT, TRACK_PAGE_PREVIOUS, TRACK_PAGE_NEXT,
        SCENE_SCROLL_PREVIOUS, SCENE_SCROLL_NEXT, SCENE_PAGE_PREVIOUS, SCENE_PAGE_NEXT,
        CURSOR_SWAP_PREVIOUS, CURSOR_SWAP_NEXT
    }

    public CurrentTrackNavigationEffect
    {
        bankId = Objects.requireNonNull (bankId, "bankId");
        operation = Objects.requireNonNull (operation, "operation");
        if (navigationGeneration <= 0 || bankId.isBlank () || bankId.length () > 128)
            throw new IllegalArgumentException ("bank navigation requires an observed bounded origin");
    }
}
