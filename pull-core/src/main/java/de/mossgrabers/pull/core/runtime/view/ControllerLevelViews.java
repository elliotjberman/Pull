// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.ControllerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Controller-level policy which remains available around every page-specific workspace.
 */
public final class ControllerLevelViews
{
    private final List<ControllerView> controllerViews;
    private final ControllerView noteViewController;
    private final ButtonGestureConsumption buttonGestures = new ButtonGestureConsumption (java.util.Set.of (de.mossgrabers.pull.core.api.PushControlIds.button ("RECORD")));
    private final List<ControllerView> metronomePage;
    private final List<ControllerView> automationPage;
    private final ControllerView rawPitchBend = new RawPitchBendView ();


    public ControllerLevelViews (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final PageNavigation navigation)
    {
        final ControllerPageTransitions pages = new ControllerPageTransitions (navigation);
        final AuthoritativeBooleanToggle<String> metronomeToggle = new AuthoritativeBooleanToggle<> ();
        final AutomationControlState automationState = new AutomationControlState ();
        final WorkspaceSelection checkedSelection = Objects.requireNonNull (selection, "selection");
        final SelectedTrackBooleanToggles selectedTrackToggles = new SelectedTrackBooleanToggles ();
        this.noteViewController = new NoteViewControllerView (checkedSelection);
        this.metronomePage = List.of (new TransportSettingsPageView (false, automationState));
        this.automationPage = List.of (new TransportSettingsPageView (true, automationState));
        this.controllerViews = List.of (
            new WorkspaceSelectionView (checkedSelection),
            new GlobalParameterControlsView (),
            new TransportControlView (Objects.requireNonNull (playbackCoordinator, "playbackCoordinator"), selectedTrackToggles, this.buttonGestures),
            new TapTempoView (metronomeToggle),
            new MetronomeControlView (metronomeToggle, pages),
            new AutomationControlView (automationState, pages),
            new UndoRedoView (),
            new TrackMixControlView (navigation),
            new MasterButtonView (pages),
            new AccentControlView (pages),
            new SelectedTrackMuteSoloView (selectedTrackToggles));
    }


    /**
     * Compose fresh controller-level views with one workspace's page-specific views.
     *
     * @param workspaceViews Page-specific views
     * @return Complete view list for compilation
     */
    public List<ControllerView> compose (final List<? extends ControllerView> workspaceViews)
    {
        return this.compose (workspaceViews, true, false);
    }


    /** Compose controller-level views where a fixed non-note layout does not sample note policy. */
    public List<ControllerView> composeWithoutNoteController (final List<? extends ControllerView> workspaceViews)
    {
        return this.compose (workspaceViews, false, false);
    }


    /** Include raw strip behavior only in compositions that declare that binding. */
    public List<ControllerView> composeWithRawPitchBend (final List<? extends ControllerView> workspaceViews, final boolean noteController)
    {
        return this.compose (workspaceViews, noteController, true);
    }


    private List<ControllerView> compose (final List<? extends ControllerView> workspaceViews, final boolean noteController, final boolean rawPitchBend)
    {
        final List<? extends ControllerView> checkedWorkspaceViews = Objects.requireNonNull (workspaceViews, "workspaceViews");
        final List<ControllerView> views = new ArrayList<> (this.controllerViews);
        if (noteController)
            views.add (1, this.noteViewController);
        if (rawPitchBend)
            views.add (this.rawPitchBend);
        for (final ControllerView view: checkedWorkspaceViews)
            views.add (Objects.requireNonNull (view, "workspaceView"));
        return List.copyOf (views);
    }


    public ButtonGestureConsumption buttonGestures ()
    {
        return this.buttonGestures;
    }


    /** Fixed full-display page with the current project's metronome and pre-roll settings. */
    public List<ControllerView> metronomePage () { return this.metronomePage; }


    /** Fixed full-display page sharing the retained controller-level Automation Write lane. */
    public List<ControllerView> automationPage () { return this.automationPage; }
}
