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
    private final ControllerView frozenSessionArrows = new FrozenSessionArrowsView ();
    private final ControllerView workspaceSelection;
    private final ControllerView noteViewController;
    private final ControllerView globalParameters;
    private final ControllerView transport;
    private final ButtonGestureConsumption buttonGestures = new ButtonGestureConsumption (java.util.Set.of (de.mossgrabers.pull.core.api.PushControlIds.button ("RECORD")));
    private final ControllerPageTransitions pages;
    private final AuthoritativeBooleanToggle<String> metronomeToggle = new AuthoritativeBooleanToggle<> ();
    private final ControllerView tapTempo = new TapTempoView (this.metronomeToggle);
    private final ControllerView metronome;
    private final AutomationControlState automationState = new AutomationControlState ();
    private final ControllerView automation;
    private final List<ControllerView> metronomePage = List.of (new TransportSettingsPageView (false, this.automationState));
    private final List<ControllerView> automationPage = List.of (new TransportSettingsPageView (true, this.automationState));
    private final ControllerView undoRedo = new UndoRedoView ();
    private final ControllerView trackMix;
    private final ControllerView masterButton;
    private final ControllerView accent;
    private final ControllerView selectedTrackMuteSolo;
    private final ControllerView rawPitchBend = new RawPitchBendView ();


    public ControllerLevelViews (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final PageNavigation navigation)
    {
        this.pages = new ControllerPageTransitions (navigation);
        this.metronome = new MetronomeControlView (this.metronomeToggle, this.pages);
        this.automation = new AutomationControlView (this.automationState, this.pages);
        this.trackMix = new TrackMixControlView (navigation);
        this.masterButton = new MasterButtonView (this.pages);
        this.accent = new AccentControlView (this.pages);
        final WorkspaceSelection checkedSelection = Objects.requireNonNull (selection, "selection");
        final SelectedTrackBooleanToggles selectedTrackToggles = new SelectedTrackBooleanToggles ();
        this.workspaceSelection = new WorkspaceSelectionView (checkedSelection);
        this.noteViewController = new NoteViewControllerView (checkedSelection);
        this.globalParameters = new GlobalParameterControlsView ();
        this.transport = new TransportControlView (
            Objects.requireNonNull (playbackCoordinator, "playbackCoordinator"),
            selectedTrackToggles, this.buttonGestures);
        this.selectedTrackMuteSolo = new SelectedTrackMuteSoloView (selectedTrackToggles);
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
        final List<ControllerView> views = new ArrayList<> (checkedWorkspaceViews.size () + 5);
        views.add (this.workspaceSelection);
        if (noteController)
            views.add (this.noteViewController);
        views.add (this.globalParameters);
        views.add (this.transport);
        views.add (this.tapTempo);
        views.add (this.metronome);
        views.add (this.automation);
        views.add (this.undoRedo);
        views.add (this.trackMix);
        views.add (this.masterButton);
        views.add (this.accent);
        views.add (this.selectedTrackMuteSolo);
        if (rawPitchBend)
            views.add (this.rawPitchBend);
        for (final ControllerView view: checkedWorkspaceViews)
            views.add (Objects.requireNonNull (view, "workspaceView"));
        final boolean hasNavigation = checkedWorkspaceViews.stream ().flatMap (view -> view.claims ().stream ()).anyMatch (claim -> claim.area () == de.mossgrabers.pull.core.view.SurfaceArea.NAVIGATION_ARROWS);
        if (!hasNavigation && checkedWorkspaceViews.stream ().anyMatch (view -> view.profile ().controllerFacets ().contains (de.mossgrabers.pull.core.api.ControllerViewFacet.SESSION_GRID_FULL)))
            views.add (this.frozenSessionArrows);
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
