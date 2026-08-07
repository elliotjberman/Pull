// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
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
            Set.of (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER),
            SessionBankShape.empty ())));
        assertThrows (IllegalArgumentException.class, () -> ControllerWorkspaceHost.validate (new DesiredControllerWorkspace (
            "pitch only",
            Set.of (ControllerViewFacet.DRUM_PITCH_BEND),
            SessionBankShape.empty ())));
    }


    @Test
    void masterPagePreservesTheStableBaselineAcrossAWorkspaceGrid ()
    {
        final ModeManager modes = new ModeManager ();
        modes.register (Modes.TRACK, mode ());
        modes.register (Modes.WORKSPACE, mode ());
        modes.register (Modes.MASTER, mode ());
        modes.setDefaultID (Modes.TRACK);
        modes.setActive (Modes.TRACK);
        final ControllerPageLease lease = new ControllerPageLease ();
        final DesiredControllerWorkspace vsLive = new DesiredControllerWorkspace (
            "VS Live",
            Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS),
            SessionBankShape.empty ());
        final DesiredControllerWorkspace master = new DesiredControllerWorkspace (
            "Master",
            Set.of (ControllerViewFacet.MASTER_CONTROLS),
            SessionBankShape.empty ());

        lease.apply (DesiredControllerWorkspace.empty (), vsLive, modes);
        assertEquals (Modes.WORKSPACE, modes.getActiveID ());

        modes.setActive (Modes.MASTER);
        lease.apply (vsLive, master, modes);
        assertEquals (Modes.MASTER, modes.getActiveID ());

        lease.apply (master, DesiredControllerWorkspace.empty (), modes);

        assertEquals (Modes.TRACK, modes.getActiveID ());
    }


    @Test
    void rejectsTwoPageAdaptersInOneWorkspace ()
    {
        assertThrows (IllegalArgumentException.class, () -> ControllerWorkspaceHost.validate (new DesiredControllerWorkspace (
            "ambiguous page",
            Set.of (ControllerViewFacet.MASTER_CONTROLS, ControllerViewFacet.PROJECT_MACRO_CONTROLS),
            SessionBankShape.empty ())));
    }


    private static IMode mode ()
    {
        return (IMode) Proxy.newProxyInstance (
            IMode.class.getClassLoader (),
            new Class<?> []
            {
                IMode.class
            },
            (proxy, method, arguments) -> null);
    }
}
