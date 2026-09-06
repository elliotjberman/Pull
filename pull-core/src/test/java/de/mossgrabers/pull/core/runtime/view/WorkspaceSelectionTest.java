// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import org.junit.jupiter.api.Test;
import static de.mossgrabers.pull.core.runtime.view.WorkspaceSelection.Destination.*;
import static de.mossgrabers.pull.core.runtime.view.WorkspaceSelection.Id.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSelectionTest
{
    @Test
    void initialGridSelectionDoesNotWaitForARegisteredPage ()
    {
        for (final String mode: new String[] { "", "MASTER", "WORKSPACE", "DEVICE_PARAMS" })
        {
            final var selection = new WorkspaceSelection (DEFAULT);
            selection.observe (layout (5, "SESSION", mode));
            assertEquals (SESSION, selection.selectedDestination ());
            assertEquals (NONE, selection.pendingDestination ());
        }
    }

    @Test
    void sessionHandoffRequiresLaterGridReadbackButNoTrackPage ()
    {
        final var selection = new WorkspaceSelection (VS_LIVE);
        selection.beginGesture (WorkspaceSelection.Gesture.SESSION, DEFAULT, SESSION, layout (5, "DRUM_PAD", "MASTER"), true);
        selection.endGesture (WorkspaceSelection.Gesture.SESSION, layout (5, "DRUM_PAD", "MASTER"));
        selection.observe (layout (5, "SESSION", "MASTER"));
        assertEquals (SESSION, selection.pendingDestination ());
        selection.observe (layout (6, "DRUM_PAD", "TRACK"));
        assertEquals (SESSION, selection.pendingDestination ());
        selection.observe (layout (7, "SESSION", "MASTER"));
        assertEquals (NONE, selection.pendingDestination ());
        assertEquals (SESSION, selection.selectedDestination ());
    }

    @Test
    void noteHandoffAcknowledgesAnySupportedNoteGridIndependentlyOfPage ()
    {
        final var selection = new WorkspaceSelection (VS_LIVE);
        selection.beginGesture (WorkspaceSelection.Gesture.NOTE, DEFAULT, NOTE, layout (3, "SESSION", "PAN"), true);
        selection.observe (layout (4, "PLAY", "PAN"));
        assertEquals (NONE, selection.pendingDestination ());
        assertEquals (NOTE, selection.selectedDestination ());
    }

    @Test
    void heldSessionRetainsItsFullGridOwnerUntilRelease ()
    {
        final var selection = new WorkspaceSelection (VS_LIVE);
        selection.beginGesture (WorkspaceSelection.Gesture.SESSION, DEFAULT, SESSION, layout (3, "DRUM_PAD", "MASTER"), true);
        selection.observe (layout (4, "SESSION", "MASTER"));
        assertEquals (SESSION, selection.pendingDestination ());
        selection.endGesture (WorkspaceSelection.Gesture.SESSION, layout (4, "SESSION", "MASTER"));
        assertEquals (NONE, selection.pendingDestination ());
        assertEquals (DEFAULT, selection.active ());
    }

    @Test
    void temporarySessionReturnsToItsCapturedWorkspace ()
    {
        final var selection = new WorkspaceSelection (VS_LIVE);
        selection.beginGesture (WorkspaceSelection.Gesture.SESSION, DEFAULT, SESSION, layout (3, "DRUM_PAD", "MASTER"), true);
        selection.makeTemporary (WorkspaceSelection.Gesture.SESSION);
        selection.observe (layout (4, "SESSION", "MASTER"));
        selection.endGesture (WorkspaceSelection.Gesture.SESSION, layout (4, "SESSION", "MASTER"));
        assertEquals (VS_LIVE, selection.active ());
        assertEquals (NONE, selection.selectedDestination ());
        assertEquals (2, selection.requestSequence ());
    }

    private static ControllerLayoutSnapshot layout (final long generation, final String view, final String mode)
    {
        return new ControllerLayoutSnapshot (generation, view, mode, false, false, 0, GridPressureConfiguration.OFF);
    }
}
