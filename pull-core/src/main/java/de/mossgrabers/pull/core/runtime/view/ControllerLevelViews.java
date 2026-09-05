// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.RetainedControllerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Controller-level policy which remains available around every page-specific workspace.
 */
public final class ControllerLevelViews
{
    private final ControllerView mixerNavigation = retained (new NavigationView (NavigationView.Horizontal.MIXER));
    private final ControllerView inertPageNavigation = retained (new NavigationView (NavigationView.Horizontal.INERT));
    private final ControllerView frozenSessionArrows = new FrozenSessionArrowsView ();
    private final ControllerView workspaceSelection;
    private final ControllerView noteViewController;
    private final ControllerView globalParameters;
    private final ControllerView transport;
    private final ButtonGestureConsumption buttonGestures = new ButtonGestureConsumption (java.util.Set.of (de.mossgrabers.pull.core.api.PushControlIds.button ("RECORD")));
    private final AuthoritativeBooleanToggle<String> metronomeToggle = new AuthoritativeBooleanToggle<> ();
    private final ControllerView tapTempo = retained (new TapTempoView (this.metronomeToggle));
    private final ControllerView metronome = retained (new MetronomeControlView (this.metronomeToggle));
    private final AutomationControlState automationState = new AutomationControlState ();
    private final ControllerView automation = retained (new AutomationControlView (this.automationState));
    private final List<ControllerView> metronomePage = List.of (retained (new TransportSettingsPageView (false, this.automationState)));
    private final List<ControllerView> automationPage = List.of (retained (new TransportSettingsPageView (true, this.automationState)));
    private final ControllerView undoRedo = retained (new UndoRedoView ());
    private final ControllerView trackMix = retained (new TrackMixControlView ());
    private final ControllerView masterButton = retained (new MasterButtonView ());
    private final ControllerView accent = retained (new AccentControlView ());
    private final ControllerView selectedTrackMuteSolo;
    private final RawPitchBendGesture pitchBend = new RawPitchBendGesture ();
    private final ControllerView rawPitchBend = retained (RawPitchBendView.raw (this.pitchBend));
    private final ControllerView legacyPitchBend = retained (RawPitchBendView.legacyContinuation (this.pitchBend));
    private final ParameterTouchSession parameterTouches = new ParameterTouchSession ();
    private final ControllerView parameterTouchRelease = retained (new ParameterTouchReleaseView (this.parameterTouches));


    /** Construct one retained controller-level policy set for a core generation. */
    public ControllerLevelViews (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        final WorkspaceSelection checkedSelection = Objects.requireNonNull (selection, "selection");
        final SelectedTrackBooleanToggles selectedTrackToggles = new SelectedTrackBooleanToggles ();
        this.workspaceSelection = retained (new WorkspaceSelectionView (checkedSelection));
        this.noteViewController = retained (new NoteViewControllerView (checkedSelection));
        this.globalParameters = retained (new GlobalParameterControlsView ());
        this.transport = retained (new TransportControlView (
            Objects.requireNonNull (playbackCoordinator, "playbackCoordinator"),
            selectedTrackToggles, this.buttonGestures));
        this.selectedTrackMuteSolo = retained (new SelectedTrackMuteSoloView (selectedTrackToggles));
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


    /** Select the fixed raw strip profile while retaining its gesture across every page. */
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
        views.add (this.parameterTouchRelease);
        views.add (rawPitchBend ? this.rawPitchBend : this.legacyPitchBend);
        for (final ControllerView view: checkedWorkspaceViews)
            views.add (Objects.requireNonNull (view, "workspaceView"));
        final boolean hasNavigation = checkedWorkspaceViews.stream ().flatMap (view -> view.claims ().stream ()).anyMatch (claim -> claim.area () == de.mossgrabers.pull.core.view.SurfaceArea.NAVIGATION_ARROWS);
        if (!hasNavigation)
        {
            final String page = checkedWorkspaceViews.stream ().map (ControllerView::installedModeId).filter (id -> !id.isEmpty ()).findFirst ().orElse ("");
            if (!page.isEmpty ())
                views.add (java.util.Set.of ("TRACK", "VOLUME", "PAN", "CROSSFADER", "SEND1", "SEND2", "SEND3", "SEND4", "SEND5", "SEND6", "SEND7", "SEND8").contains (page) ? this.mixerNavigation : this.inertPageNavigation);
            else if (checkedWorkspaceViews.stream ().anyMatch (view -> view.profile ().controllerFacets ().contains (de.mossgrabers.pull.core.api.ControllerViewFacet.SESSION_GRID_FULL)))
                views.add (this.frozenSessionArrows);
        }
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


    public ParameterTouchSession parameterTouches ()
    {
        return this.parameterTouches;
    }


    private static ControllerView retained (final ControllerView view)
    {
        return new RetainedControllerView (view);
    }
}
