// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;

import java.util.List;
import java.util.ArrayList;


/** Compiled, read-back-acknowledged handoff to an inherited stable destination. */
public final class StableDestinationWorkspace
{
    /** Bounded full Session bank already installed by the stable shell. */
    public static final SessionBankShape SESSION_BANK = new SessionBankShape (8, 8);


    private StableDestinationWorkspace ()
    {
        // Utility class.
    }


    /** Select an explicit page while the Session destination is acknowledged. */
    public static CompiledWorkspace session (final WorkspaceSelection selection, final ControllerLevelViews controllerViews, final ControllerView sessionView, final List<? extends ControllerView> pageViews)
    {
        final List<ControllerView> views = new ArrayList<> (pageViews);
        views.add (new SessionTemporarySelectionView (selection));
        views.add (sessionView);
        return CompiledWorkspace.compile (
            "Session destination",
            SESSION_BANK,
            controllerViews.composeWithRawPitchBend (views, false));
    }


    /** Keep the semantic Session view selected after its default Track/Mix page is acknowledged. */
    public static CompiledWorkspace selectedSession (final ControllerLevelViews controllerViews, final ControllerView sessionView)
    {
        return selectedSession (controllerViews, sessionView, List.of ());
    }


    /** Retain the Session grid under an independently selected page. */
    public static CompiledWorkspace selectedSession (final ControllerLevelViews controllerViews, final ControllerView sessionView, final List<? extends ControllerView> pageViews)
    {
        final List<ControllerView> views = new ArrayList<> (pageViews);
        views.add (sessionView);
        return CompiledWorkspace.compile (
            "Session",
            SESSION_BANK,
            controllerViews.composeWithRawPitchBend (views, true));
    }


    /** Select the Track page while the preferred Note destination is acknowledged. */
    public static CompiledWorkspace note (final ControllerLevelViews controllerViews, final List<? extends ControllerView> pageViews)
    {
        return CompiledWorkspace.compile ("Note destination", controllerViews.compose (pageViews));
    }
}
