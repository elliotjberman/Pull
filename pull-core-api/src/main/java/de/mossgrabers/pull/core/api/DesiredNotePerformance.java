// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * One complete replayable Note-view lifecycle: visible layout plus its musical input route.
 *
 * @param layout Desired installed note-controller layout
 * @param inputRoute Desired target-fenced musical input route
 */
public record DesiredNotePerformance (DesiredControllerLayout layout, DesiredNoteInputRoute inputRoute)
{
    private static final DesiredNotePerformance INACTIVE = new DesiredNotePerformance (DesiredControllerLayout.empty (), DesiredNoteInputRoute.disabled ());


    /** Validate components; their composed physical ownership is checked by DesiredControllerState. */
    public DesiredNotePerformance
    {
        layout = Objects.requireNonNull (layout, "layout");
        inputRoute = Objects.requireNonNull (inputRoute, "inputRoute");
    }


    /** @return The inactive Note lifecycle. */
    public static DesiredNotePerformance inactive ()
    {
        return INACTIVE;
    }
}
