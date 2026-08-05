// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ControllerWorkspaceHostTest
{
    @Test
    void acceptsFixedFacetsWithoutInterpretingTheWorkspaceName ()
    {
        final Set<ControllerViewFacet> facets = Set.of (
            ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
            ControllerViewFacet.SESSION_SCENE_KEYS_UPPER,
            ControllerViewFacet.DRUM_CONTROLLER_LOWER,
            ControllerViewFacet.DRUM_PITCH_BEND);
        final SessionBankShape shape = new SessionBankShape (8, 4);
        final DesiredControllerWorkspace first = new DesiredControllerWorkspace ("first", facets, shape);
        final DesiredControllerWorkspace second = new DesiredControllerWorkspace ("another name", facets, shape);

        assertEquals (first, ControllerWorkspaceHost.validate (first));
        assertEquals (second, ControllerWorkspaceHost.validate (second));
    }


    @Test
    void rejectsDependentFacetsWithoutTheirFixedOwners ()
    {
        assertThrows (IllegalArgumentException.class, () -> ControllerWorkspaceHost.validate (new DesiredControllerWorkspace (
            "scene keys only",
            Set.of (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER))));
        assertThrows (IllegalArgumentException.class, () -> ControllerWorkspaceHost.validate (new DesiredControllerWorkspace (
            "pitch only",
            Set.of (ControllerViewFacet.DRUM_PITCH_BEND))));
    }
}
