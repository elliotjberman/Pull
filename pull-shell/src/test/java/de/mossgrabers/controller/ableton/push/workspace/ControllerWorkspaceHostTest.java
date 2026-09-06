// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
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
            ControllerViewFacet.DRUM_CONTROLLER_LOWER);
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
    }


    @Test
    void neutralLayoutActuallyLeavesMusicalPadsForSession ()
    {
        final ModeManager modes = new ModeManager ();
        final ViewManager views = new ViewManager ();
        modes.register (Modes.TRACK, mode ());
        modes.register (Modes.DEVICE_PARAMS, mode ());
        modes.setDefaultID (Modes.TRACK);
        modes.setActive (Modes.DEVICE_PARAMS);
        views.register (Views.PLAY, view ());
        views.register (Views.SESSION, view ());
        views.setDefaultID (Views.SESSION);
        views.setActive (Views.PLAY);

        ControllerWorkspaceHost.applyPreparedLayout (DesiredControllerLayout.neutral (), views);

        assertEquals (Modes.DEVICE_PARAMS, modes.getActiveID ());
        assertEquals (Views.SESSION, views.getActiveID ());
    }


    @Test
    void noteLayoutActuationDoesNotOwnTheIndependentControllerPage ()
    {
        final ModeManager modes = new ModeManager ();
        final ViewManager views = new ViewManager ();
        modes.register (Modes.TRACK, mode ());
        modes.register (Modes.SCALES, mode ());
        modes.setDefaultID (Modes.TRACK);
        modes.setActive (Modes.SCALES);
        views.register (Views.PLAY, view ());
        views.register (Views.CHORDS, view ());
        views.setDefaultID (Views.PLAY);
        views.setActive (Views.PLAY);

        ControllerWorkspaceHost.applyPreparedLayout (DesiredControllerLayout.note (ControllerNoteView.CHORDS), views);

        assertEquals (Modes.SCALES, modes.getActiveID ());
        assertEquals (Views.CHORDS, views.getActiveID ());
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


    private static IView view ()
    {
        return (IView) Proxy.newProxyInstance (
            IView.class.getClassLoader (),
            new Class<?> []
            {
                IView.class
            },
            (proxy, method, arguments) -> null);
    }
}
