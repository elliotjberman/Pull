// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.Page;
import de.mossgrabers.pull.core.view.PageId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Declares pages and their fixed compositions over independently selected musical grids. */
public final class ControllerPages
{
    public static final String VS_LIVE_NAME = "VS Live";
    public static final SessionBankShape VS_LIVE_BANK = new SessionBankShape (8, 4);
    public static final SessionBankShape FULL_SESSION_BANK = new SessionBankShape (8, 8);
    private final ControllerPageCompositions compositions = new ControllerPageCompositions ();
    private final ControllerPageCompositions.Background note;
    private final ControllerPageCompositions.Background drum;
    private final ControllerPageCompositions.Background drumLegacy;
    private final ControllerPageCompositions.Background session;
    private final ControllerPageCompositions.Background sessionPending;
    private final ControllerPageCompositions.Background vsLive;

    public ControllerPages (final WorkspaceSelection selection, final ControllerLevelViews controls, final PageNavigation navigation, final TrackMixerPageState trackState)
    {
        final ControllerView drumControls = new DrumControlPadView ();
        // One physical Stop gesture crosses page and grid replacements; every consumer shares it.
        final SessionStopGesture stopGesture = new SessionStopGesture ();
        final ControllerView sessionGrid = SessionView.full (stopGesture);
        final List<ControllerView> drumViews = List.of (new DrumPlayPadView (), new DrumOctaveView (), new DrumFillView (), drumControls, new DrumRateView ());
        final List<ControllerView> legacyPage = List.of (new StableParameterControlsView ());
        this.note = background ("Pull", SessionBankShape.empty (), List.of (), true, false, legacyPage);
        this.drum = background ("Pull Drum", SessionBankShape.empty (), drumViews, true, true, legacyPage);
        this.drumLegacy = background ("Pull Drum", SessionBankShape.empty (), drumViews, true, false, legacyPage);
        this.session = background ("Session", FULL_SESSION_BANK, List.of (sessionGrid), true, true, legacyPage);
        this.sessionPending = background ("Session destination", FULL_SESSION_BANK, List.of (new SessionTemporarySelectionView (selection), sessionGrid), false, true, legacyPage);
        this.vsLive = background (VS_LIVE_NAME, VS_LIVE_BANK, List.of (new SessionNavigationView (), SessionView.upper (true, stopGesture), new DrumPlayPadView (), new DrumOctaveView (), new DrumFillView (), new DrumControllerView (), drumControls, new DrumRateView ()), false, true, List.of ());

        final ControllerView mixerNavigation = new NavigationView (NavigationView.Horizontal.MIXER);
        final ControllerView otherNavigation = new NavigationView (NavigationView.Horizontal.INERT);
        final ControllerView normalFooter = new CurrentTrackFooterView (controls.buttonGestures (), stopGesture, navigation);
        final ControllerView trackStrip = new TrackSelectionStripView (stopGesture);
        final Page normalTrack = new Page (PageId.TRACK, List.of (new TrackMixerControlsView (trackState, true), normalFooter), mixerNavigation);
        final Page vsTrack = new Page (PageId.TRACK, List.of (new TrackMixerControlsView (trackState, false), trackStrip), mixerNavigation);
        final ControllerView macroControls = new ProjectMacroControlsView ();
        final Page normalMacros = new Page (PageId.PROJECT_MACROS, List.of (macroControls, normalFooter), otherNavigation);
        final Page vsMacros = new Page (PageId.PROJECT_MACROS, List.of (macroControls, trackStrip), otherNavigation);
        final List<Page> shared = new ArrayList<> ();
        shared.add (new Page (PageId.MASTER, List.of (new MasterControlView ()), Optional.of (otherNavigation), Set.of (ParameterSlot.MASTER_MIX_VOLUME, ParameterSlot.MASTER_MIX_PAN)));
        shared.add (new Page (PageId.FRAME, List.of (new FramePageView ()), otherNavigation));
        shared.add (new Page (PageId.ACCENT, List.of (new AccentPageView (stopGesture)), otherNavigation));
        shared.add (new Page (PageId.METRONOME, controls.metronomePage (), otherNavigation));
        shared.add (new Page (PageId.AUTOMATION, controls.automationPage (), otherNavigation));
        shared.add (new Page (PageId.VOLUME, List.of (new GlobalMixerControlsView (GlobalMixerControlsView.Role.VOLUME, navigation), normalFooter), mixerNavigation));
        shared.add (new Page (PageId.PAN, List.of (new GlobalMixerControlsView (GlobalMixerControlsView.Role.PAN, navigation), normalFooter), mixerNavigation));
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            shared.add (new Page (PageId.send (index), List.of (GlobalMixerControlsView.send (index, navigation), normalFooter), mixerNavigation));
        final List<Page> normal = new ArrayList<> (shared);
        normal.add (normalTrack);
        normal.add (normalMacros);
        for (final var background: List.of (this.note, this.drum, this.drumLegacy, this.session, this.sessionPending))
            this.compositions.register (controls, background, normal);
        shared.add (vsTrack);
        shared.add (vsMacros);
        this.compositions.register (controls, this.vsLive, shared);
    }

    public CompiledWorkspace select (final ControllerPageRef page, final WorkspaceSelection selection, final ControllerSnapshot snapshot)
    {
        final var background = this.background (selection, snapshot);
        return page.kind () == ControllerPageRef.Kind.CORE ? this.compositions.select (new PageId (page.id ()), background) : this.compositions.legacy (background);
    }

    public Set<ParameterSlot> indications (final ControllerPageRef page, final WorkspaceSelection selection, final ControllerSnapshot snapshot)
    {
        return page.kind () == ControllerPageRef.Kind.CORE ? this.compositions.definition (new PageId (page.id ()), this.background (selection, snapshot)).parameterIndications () : Set.of ();
    }

    private ControllerPageCompositions.Background background (final WorkspaceSelection selection, final ControllerSnapshot snapshot)
    {
        if (selection.active () == WorkspaceSelection.Id.VS_LIVE) return this.vsLive;
        if (selection.pendingDestination () == WorkspaceSelection.Destination.SESSION) return this.sessionPending;
        if (selection.pendingDestination () == WorkspaceSelection.Destination.NOTE) return this.note;
        if (selection.selectedDestination () == WorkspaceSelection.Destination.SESSION || selection.selectedDestination () != WorkspaceSelection.Destination.NOTE && "SESSION".equals (snapshot.bridge ().layout ().viewId ())) return this.session;
        if (snapshot.bridge ().layout ().drumLayoutActive ()) return snapshot.bridge ().layout ().drumControllerEngaged () ? this.drum : this.drumLegacy;
        return this.note;
    }

    private static ControllerPageCompositions.Background background (final String name, final SessionBankShape bank, final List<ControllerView> views, final boolean note, final boolean raw, final List<ControllerView> legacy)
    {
        return new ControllerPageCompositions.Background (name, bank, views, note, raw, legacy);
    }
}
