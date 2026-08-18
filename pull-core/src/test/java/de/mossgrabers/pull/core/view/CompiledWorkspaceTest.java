// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.view.ProjectMacroControlsView;
import de.mossgrabers.pull.core.runtime.view.SessionView;
import de.mossgrabers.pull.core.runtime.view.TrackSelectionStripView;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelectionView;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Ownership and determinism tests for fixed-footprint workspace compilation.
 */
class CompiledWorkspaceTest
{
    @Test
    void rejectsOverlappingOutputOwners ()
    {
        final TestView first = view ("first", claim (SurfaceArea.DRUM_FILL_PADS, SurfaceClaim.Kind.OUTPUT));
        final TestView second = view ("second", claim (SurfaceArea.DRUM_FILL_PADS, SurfaceClaim.Kind.OUTPUT));

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("conflict", List.of (first, second)));
    }


    @Test
    void rejectsLightOutsideTheEmittingViewsOutputClaims ()
    {
        final ControllerView invalid = new ControllerView ()
        {
            @Override
            public String id ()
            {
                return "invalid-light";
            }


            @Override
            public ViewProfile profile ()
            {
                return ViewProfile.fixed ("invalid-light", Set.of (claim (SurfaceArea.PLAY_BUTTON, SurfaceClaim.Kind.OUTPUT)), Set.of ());
            }


            @Override
            public ViewOutput render (final ControllerSnapshot ignored)
            {
                return new ViewOutput (Map.of (PushControlIds.button ("RECORD"), new RgbColor (255, 0, 0)), Map.of ());
            }
        };
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("invalid-light", List.of (invalid));

        final IllegalStateException failure = assertThrows (IllegalStateException.class, () -> workspace.start (snapshot ()));
        assertTrue (failure.getMessage ().contains ("outside its output claims"));
    }


    @Test
    void rejectsOverlappingExclusiveInputOwners ()
    {
        final TestView first = view ("first", claim (SurfaceArea.RECORD_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT));
        final TestView second = view ("second", claim (SurfaceArea.RECORD_BUTTON, SurfaceClaim.Kind.DIRECT_INPUT));

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("conflict", List.of (first, second)));
    }


    @Test
    void allowsSharedObserversAndPublishesOneRoute ()
    {
        final TestView first = view ("first", claim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT));
        final TestView second = view ("second", claim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT));

        final CoreResult result = CompiledWorkspace.compile ("observers", List.of (first, second)).start (snapshot ());

        assertEquals (1, result.desiredInputRoutes ().routes ().size ());
        assertEquals (
            InputRouteMode.OBSERVE,
            result.desiredInputRoutes ().mode (PushControlIds.button ("SHIFT"), InputKind.BUTTON).orElseThrow ());
    }


    @Test
    void gridClaimsPublishPadAndPressureRoutesTogether ()
    {
        final TestView view = view ("grid", claim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.OBSERVE_INPUT));

        final CoreResult result = CompiledWorkspace.compile ("grid inputs", List.of (view)).start (snapshot ());
        final var firstPad = PushControlIds.pad (1);

        assertEquals (32, result.desiredInputRoutes ().routes ().size ());
        assertEquals (InputRouteMode.OBSERVE, result.desiredInputRoutes ().mode (firstPad, InputKind.PAD).orElseThrow ());
        assertEquals (InputRouteMode.OBSERVE, result.desiredInputRoutes ().mode (firstPad, InputKind.POLY_PRESSURE).orElseThrow ());
    }


    @Test
    void declarationOrderDoesNotChangeCompiledState ()
    {
        final TestView record = new TestView (
            "record",
            Set.of (claim (SurfaceArea.RECORD_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT)),
            Set.of (BridgeSubscription.SELECTED_TRACK));
        final TestView shift = new TestView (
            "shift",
            Set.of (claim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)),
            Set.of (BridgeSubscription.TRANSPORT));

        final CoreResult forward = CompiledWorkspace.compile ("ordered", List.of (record, shift)).start (snapshot ());
        final CoreResult reversed = CompiledWorkspace.compile ("ordered", List.of (shift, record)).start (snapshot ());

        assertEquals (forward, reversed);
    }


    @Test
    void detectsOverlapBetweenWholeAndNamedGridRegions ()
    {
        final TestView wholeLowerGrid = view ("whole", claim (SurfaceArea.GRID_LOWER, SurfaceClaim.Kind.OUTPUT));
        final TestView playableDrums = view ("drums", claim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.OUTPUT));

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("conflict", List.of (wholeLowerGrid, playableDrums)));
    }


    @Test
    void stableAdapterOutputConflictsWithCoreOutput ()
    {
        final TestView stableGrid = view ("stable", claim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT), Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER));
        final TestView coreGrid = view ("core", claim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.OUTPUT));

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("conflict", List.of (stableGrid, coreGrid)));
    }


    @Test
    void stableAdapterClaimsRequireAControllerFacet ()
    {
        final SurfaceClaim claim = claim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT);

        assertThrows (IllegalArgumentException.class, () -> ViewProfile.fixed ("invalid", Set.of (claim), Set.of ()));
    }


    @Test
    void freezesTheSelectedProfileAtCompileTime ()
    {
        final AtomicInteger profileCalls = new AtomicInteger ();
        final ControllerView view = new ControllerView ()
        {
            @Override
            public String id ()
            {
                return "fixed";
            }


            @Override
            public ViewProfile profile ()
            {
                profileCalls.incrementAndGet ();
                return ViewProfile.fixed ("selected", Set.of (claim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
            }
        };

        final CompiledWorkspace workspace = CompiledWorkspace.compile ("fixed", List.of (view));
        workspace.start (snapshot ());
        workspace.profiles ();

        assertEquals (1, profileCalls.get ());
    }


    @Test
    void selectedFacetContributesFixedClaimsAndStableAdapters ()
    {
        final SessionView session = SessionView.upper (true);
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("session", new SessionBankShape (8, 4), List.of (session));

        assertEquals (Set.of (SessionView.SCENE_LAUNCH), workspace.profiles ().get (session.id ()).enabledFacets ());
        assertEquals (Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER, ControllerViewFacet.SESSION_SCENE_KEYS_UPPER), workspace.desiredControllerWorkspace ().facets ());
        assertTrue (session.claims ().contains (new SurfaceClaim (SurfaceArea.STOP_CLIP_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT)));
        assertTrue (session.claims ().contains (new SurfaceClaim (SurfaceArea.STOP_CLIP_BUTTON, SurfaceClaim.Kind.OUTPUT)));
    }


    @Test
    void projectMacrosOwnEncoderTurnsAndTheirDisplayRegionWhileStableAdaptsTouch ()
    {
        final ControllerView footer = displayRegionView (
            "test footer",
            SurfaceArea.DISPLAY_BOTTOM_STRIP,
            new ControllerDisplayScene (960, 17, List.of (new DisplayCommand.Rectangle (0, 0, 960, 17, new RgbColor (0, 0, 0)))));
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("macros", List.of (new ProjectMacroControlsView (), footer));
        final CoreResult result = workspace.start (parameterSnapshot ());
        final ControlId firstKnob = PushControlIds.continuous ("KNOB1");

        assertEquals (Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS), result.desiredControllerState ().workspace ().facets ());
        assertEquals (8, result.desiredInputRoutes ().routes ().size ());
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().mode (firstKnob, InputKind.RELATIVE).orElseThrow ());
        assertEquals (Set.of (ParameterBankId.PROJECT_REMOTE), result.desiredParameterBanks ().banks ());
        assertTrue (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));
        assertTrue (result.desiredOutput ().display ().isPresent ());

        final CoreResult adjusted = workspace.handle (
            new ControllerInputEvent (1, 1, firstKnob, InputKind.RELATIVE, InputPhase.UPDATE, 3),
            parameterSnapshot ());
        assertEquals (List.of (new AdjustParameterValueEffect (PROJECT_TARGET, 30)), adjusted.effects ());

        final CoreResult decreased = workspace.handle (
            new ControllerInputEvent (2, 2, firstKnob, InputKind.RELATIVE, InputPhase.UPDATE, -2),
            parameterSnapshot ());
        assertEquals (List.of (new AdjustParameterValueEffect (PROJECT_TARGET, -20)), decreased.effects ());
    }


    @Test
    void composesDisjointViewDisplayRegionsIntoOneCompleteViewport ()
    {
        final RgbColor upperColor = new RgbColor (10, 20, 30);
        final RgbColor footerColor = new RgbColor (40, 50, 60);
        final ControllerView upper = displayRegionView (
            "upper",
            SurfaceArea.DISPLAY_PARAMETERS,
            new ControllerDisplayScene (960, 143, List.of (
                new DisplayCommand.Rectangle (0, 0, 960, 143, upperColor),
                new DisplayCommand.TextAt ("clipped", 0, 143, upperColor, 512))));
        final ControllerView footer = displayRegionView (
            "footer",
            SurfaceArea.DISPLAY_BOTTOM_STRIP,
            new ControllerDisplayScene (960, 17, List.of (new DisplayCommand.Rectangle (0, 0, 960, 17, footerColor))));

        final ControllerDisplayScene composed = CompiledWorkspace.compile ("display regions", List.of (upper, footer)).start (snapshot ()).desiredOutput ().display ();

        assertEquals (960, composed.width ());
        assertEquals (160, composed.height ());
        assertEquals (List.of (
            new DisplayCommand.PushClip (0, 0, 960, 143),
            new DisplayCommand.Rectangle (0, 0, 960, 143, upperColor),
            new DisplayCommand.TextAt ("clipped", 0, 143, upperColor, 512),
            new DisplayCommand.PopClip (),
            new DisplayCommand.PushClip (0, 143, 960, 17),
            new DisplayCommand.Rectangle (0, 143, 960, 17, footerColor),
            new DisplayCommand.PopClip ()), composed.commands ());
    }


    @Test
    void rejectsPartialOrOverflowingDisplayRegionComposition ()
    {
        final ControllerView partial = displayRegionView (
            "partial",
            SurfaceArea.DISPLAY_PARAMETERS,
            new ControllerDisplayScene (960, 143, List.of (new DisplayCommand.Rectangle (0, 0, 960, 143, new RgbColor (1, 2, 3)))));
        final ControllerView overflow = displayRegionView (
            "overflow",
            SurfaceArea.DISPLAY_BOTTOM_STRIP,
            new ControllerDisplayScene (960, 17, List.of (new DisplayCommand.Rectangle (0, 0, 961, 17, new RgbColor (4, 5, 6)))));

        assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("partial", List.of (partial)).start (snapshot ()));
        assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("overflow", List.of (partial, overflow)).start (snapshot ()));
    }


    @Test
    void rejectsDisplayRegionOutsideTheEmittingViewsClaim ()
    {
        final ControllerView parasitic = new ControllerView ()
        {
            @Override
            public String id ()
            {
                return "parasitic display";
            }


            @Override
            public ViewProfile profile ()
            {
                return ViewProfile.fixed ("parasitic display", Set.of (), Set.of ());
            }


            @Override
            public ViewOutput render (final ControllerSnapshot ignored)
            {
                return new ViewOutput (
                    Map.of (),
                    Map.of (),
                    new ControllerDisplayScene (960, 17, List.of (new DisplayCommand.Rectangle (0, 0, 960, 17, new RgbColor (1, 2, 3)))));
            }
        };

        assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("parasitic display", List.of (parasitic)).start (snapshot ()));
    }


    @Test
    void compilesPhysicalEdgesIntoViewOwnedSemanticActions ()
    {
        final ControlId left = PushControlIds.button ("PAGE_LEFT");
        final ControlId right = PushControlIds.button ("PAGE_RIGHT");
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("actions", List.of (new ActionView (Set.of (left, right))));
        final CoreResult result = workspace.start (snapshot ());

        assertEquals (2, result.desiredControllerActions ().bindings ().size ());
        assertEquals (ControllerActionId.SELECT_PARAMETER_PAGE, result.desiredControllerActions ().bindingOrNull (left, InputKind.BUTTON).action ());
        assertEquals (InputRouteMode.OBSERVE, result.desiredInputRoutes ().mode (left, InputKind.BUTTON).orElseThrow ());
        assertNotNull (workspace.resolveAction (new de.mossgrabers.pull.core.api.event.ControllerInputEvent (1, 0, right, InputKind.BUTTON, de.mossgrabers.pull.core.api.event.InputPhase.BEGIN, 127), snapshot ()));
    }


    @Test
    void rejectsSemanticActionWithoutSameViewControlAndKindClaim ()
    {
        final ControlId session = PushControlIds.button ("SESSION");
        final ControllerView otherSessionObserver = view ("session observer", claim (SurfaceArea.SESSION_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT));
        final ControlId knob = PushControlIds.continuous ("KNOB1");

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("other view claim", List.of (new ActionView (Set.of (session)), otherSessionObserver)));
        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("wrong input kind", List.of (new ActionView (Set.of (knob), InputKind.TOUCH, SurfaceArea.ENCODER_TURNS))));
    }


    @Test
    void resolvedWorkspaceActionRetainsItsBeginTimeModifierMeaning ()
    {
        final ControlId shift = PushControlIds.button ("SHIFT");
        final ControlId session = PushControlIds.button ("SESSION");
        final WorkspaceSelection selection = new WorkspaceSelection (WorkspaceSelection.Id.DEFAULT);
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("workspace action", List.of (new WorkspaceSelectionView (selection)));
        final ControllerSnapshot shifted = snapshot (Set.of (shift));
        workspace.start (shifted);

        final ResolvedControllerAction action = workspace.resolveAction (new ControllerInputEvent (1, 0, session, InputKind.BUTTON, InputPhase.BEGIN, 127), shifted);
        workspace.handleAction (action, snapshot ());

        assertEquals (WorkspaceSelection.Id.VS_LIVE, selection.active ());
    }


    @Test
    void resolvedTrackSelectionRetainsItsBeginTimeBankIdentity ()
    {
        final ControllerView upper = displayRegionView (
            "upper",
            SurfaceArea.DISPLAY_PARAMETERS,
            new ControllerDisplayScene (960, 143, List.of (new DisplayCommand.Rectangle (0, 0, 960, 143, new RgbColor (0, 0, 0)))));
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("tracks", List.of (upper, new TrackSelectionStripView ()));
        final ControllerSnapshot begin = sessionSnapshot (7, "track-a");
        workspace.start (begin);

        final ResolvedControllerAction action = workspace.resolveAction (
            new ControllerInputEvent (1, 0, PushControlIds.button ("ROW1_1"), InputKind.BUTTON, InputPhase.BEGIN, 127),
            begin);
        final CoreResult result = workspace.handleAction (action, sessionSnapshot (8, "track-b"));

        assertEquals (List.of (new SelectSessionTrackEffect (7, new SessionBankShape (8, 4), 0, "track-a")), result.effects ());
    }


    @Test
    void rejectsConflictingPhysicalParameterMappings ()
    {
        final ControlId knob = PushControlIds.continuous ("KNOB1");
        final ControllerView first = parameterView ("first", knob, ParameterSlot.active (0));
        final ControllerView second = parameterView ("second", knob, ParameterSlot.active (1));

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("conflict", List.of (first, second)));
    }


    @Test
    void controllerMappingCompositionRejectsPhysicalAndSemanticCollisions ()
    {
        final ControlId firstPhysical = CoreControls.DRUM_CONTROL_PADS.getFirst ();
        final ControlId secondPhysical = CoreControls.DRUM_RATES.getFirst ();
        final ControllerMappingId firstSemantic = new ControllerMappingId ("semantic.first");
        final ControllerMappingId secondSemantic = new ControllerMappingId ("semantic.second");
        final ControllerMappingBinding first = new ControllerMappingBinding (firstPhysical, firstSemantic);

        final CompiledWorkspace physicalCollision = CompiledWorkspace.compile ("physical collision", List.of (mappingView (
            "same physical",
            SurfaceArea.DRUM_CONTROL_PADS,
            first,
            new ControllerMappingBinding (firstPhysical, secondSemantic))));
        final CompiledWorkspace semanticCollision = CompiledWorkspace.compile ("semantic collision", List.of (
            mappingView ("first", SurfaceArea.DRUM_CONTROL_PADS, first),
            mappingView ("same semantic", SurfaceArea.DRUM_RATE_PADS, new ControllerMappingBinding (secondPhysical, firstSemantic))));

        assertThrows (IllegalArgumentException.class, () -> physicalCollision.start (snapshot ()));
        assertThrows (IllegalStateException.class, () -> semanticCollision.start (snapshot ()));
    }


    @Test
    void controllerMappingCompositionMergesDisjointVirtualEndpoints ()
    {
        final ControllerMappingBinding first = new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.getFirst (), new ControllerMappingId ("semantic.first"));
        final ControllerMappingBinding second = new ControllerMappingBinding (CoreControls.DRUM_RATES.getFirst (), new ControllerMappingId ("semantic.second"));

        final CoreResult result = CompiledWorkspace.compile ("virtual mappings", List.of (
            mappingView ("first", SurfaceArea.DRUM_CONTROL_PADS, first),
            mappingView ("second", SurfaceArea.DRUM_RATE_PADS, second))).start (snapshot ());

        assertEquals (Set.of (first, second), result.desiredOutput ().controllerMappings ().bindings ());
    }


    @Test
    void rejectsControllerMappingOutsideTheEmittingViewsClaims ()
    {
        final ControllerMappingBinding binding = new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.getFirst (), new ControllerMappingId ("semantic.first"));
        final ControllerView owner = new TestView (
            "owner",
            Set.of (
                claim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
                claim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.OUTPUT)),
            Set.of (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK));
        final ControllerView parasitic = mappingView (
            "parasitic",
            Set.of (),
            Set.of (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK),
            binding);
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("parasitic mapping", List.of (owner, parasitic));

        assertThrows (IllegalStateException.class, () -> workspace.start (snapshot ()));
    }


    @Test
    void rejectsControllerMappingWithoutTheEmittingViewsFeedbackSubscription ()
    {
        final ControllerMappingBinding binding = new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.getFirst (), new ControllerMappingId ("semantic.first"));
        final ControllerView mapping = mappingView (
            "unsubscribed mapping",
            Set.of (
                claim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
                claim (SurfaceArea.DRUM_CONTROL_PADS, SurfaceClaim.Kind.OUTPUT)),
            Set.of (),
            binding);
        final ControllerView unrelatedSubscriber = new TestView ("unrelated subscriber", Set.of (), Set.of (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK));
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("unsubscribed mapping", List.of (mapping, unrelatedSubscriber));

        assertThrows (IllegalStateException.class, () -> workspace.start (snapshot ()));
    }


    @Test
    void workspaceOwnsThePhysicalControlToParameterSlotMapping ()
    {
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("macros", List.of (new ProjectMacroControlsView ()));

        assertEquals (ParameterSlot.projectRemote (0), workspace.parameterSlotOrNull (PushControlIds.continuous ("KNOB1")));
        assertEquals (ParameterSlot.projectRemote (7), workspace.parameterSlotOrNull (PushControlIds.continuous ("KNOB8")));
    }


    private static TestView view (final String id, final SurfaceClaim claim)
    {
        return view (id, claim, Set.of ());
    }


    private static TestView view (final String id, final SurfaceClaim claim, final Set<ControllerViewFacet> controllerFacets)
    {
        return new TestView (id, Set.of (claim), Set.of (), controllerFacets);
    }


    private static SurfaceClaim claim (final SurfaceArea area, final SurfaceClaim.Kind kind)
    {
        return new SurfaceClaim (area, kind);
    }


    private static ControllerView parameterView (final String id, final ControlId control, final ParameterSlot slot)
    {
        return new ControllerView ()
        {
            @Override
            public String id ()
            {
                return id;
            }


            @Override
            public ViewProfile profile ()
            {
                return ViewProfile.fixed (id, Set.of (), Set.of ());
            }


            @Override
            public Map<ControlId, ParameterSlot> parameterBindings ()
            {
                return Map.of (control, slot);
            }
        };
    }


    private static ControllerView displayRegionView (final String id, final SurfaceArea area, final ControllerDisplayScene scene)
    {
        return new ControllerView ()
        {
            @Override
            public String id ()
            {
                return id;
            }


            @Override
            public ViewProfile profile ()
            {
                return ViewProfile.fixed (id, Set.of (claim (area, SurfaceClaim.Kind.OUTPUT)), Set.of ());
            }


            @Override
            public ViewOutput render (final ControllerSnapshot ignored)
            {
                return new ViewOutput (Map.of (), Map.of (), scene);
            }
        };
    }


    private static ControllerView mappingView (final String id, final SurfaceArea area, final ControllerMappingBinding... bindings)
    {
        return mappingView (
            id,
            Set.of (
                claim (area, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
                claim (area, SurfaceClaim.Kind.OUTPUT)),
            Set.of (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK),
            bindings);
    }


    private static ControllerView mappingView (final String id, final Set<SurfaceClaim> claims, final Set<BridgeSubscription> subscriptions, final ControllerMappingBinding... bindings)
    {
        return new ControllerView ()
        {
            @Override
            public String id ()
            {
                return id;
            }


            @Override
            public ViewProfile profile ()
            {
                return ViewProfile.fixed (id, claims, Set.of ());
            }


            @Override
            public Set<BridgeSubscription> bridgeSubscriptions ()
            {
                return subscriptions;
            }


            @Override
            public ViewOutput render (final ControllerSnapshot ignored)
            {
                return new ViewOutput (
                    Map.of (),
                    Map.of (),
                    ControllerDisplayScene.empty (),
                    ControllerPadGridOverlay.inactive (),
                    ControllerDisplayOverlay.inactive (),
                    DesiredNotePerformance.inactive (),
                    DesiredNoteRepeat.unowned (),
                    new DesiredControllerMappings (Set.of (bindings)));
            }
        };
    }


    private static ControllerSnapshot snapshot ()
    {
        return snapshot (Set.of ());
    }


    private static ControllerSnapshot snapshot (final Set<ControlId> pressedControls)
    {
        return new ControllerSnapshot (
            0,
            0,
            ShellCapabilities.empty (),
            ClipCatalogSnapshot.empty (),
            Map.of (),
            pressedControls,
            Set.of ());
    }


    private static final ParameterTargetRef PROJECT_TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "project-1", 0);


    private static ControllerSnapshot parameterSnapshot ()
    {
        final ParameterBridgeSnapshot parameters = new ParameterBridgeSnapshot (
            Map.of (ParameterSlot.projectRemote (0), new ParameterTargetSnapshot (PROJECT_TARGET, 64, 0.5)),
            Map.of ());
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            ControllerLayoutSnapshot.empty (),
            DrumContextSnapshot.empty (),
            parameters);
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), java.util.Optional.empty (), Set.of (), Set.of ());
    }


    private static ControllerSnapshot sessionSnapshot (final long generation, final String firstChannel)
    {
        final SessionBankShape shape = new SessionBankShape (8, 4);
        final java.util.ArrayList<SessionTrackSnapshot> tracks = new java.util.ArrayList<> ();
        tracks.add (new SessionTrackSnapshot (firstChannel, 0, firstChannel, true, false, true, false, false, false, false, new RgbColor (10, 20, 30)));
        while (tracks.size () < shape.tracks ())
            tracks.add (SessionTrackSnapshot.empty ());
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new SessionBankSnapshot (generation, shape, 0, 0, tracks),
            ControllerLayoutSnapshot.empty (),
            de.mossgrabers.pull.core.api.NoteViewSnapshot.empty (),
            de.mossgrabers.pull.core.api.NoteRepeatSnapshot.empty (),
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            de.mossgrabers.pull.core.api.MasterSnapshot.empty (),
            de.mossgrabers.pull.core.api.ProjectSnapshot.empty ());
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), java.util.Optional.empty (), Set.of (), Set.of ());
    }


    private record TestView (String id, Set<SurfaceClaim> declaredClaims, Set<BridgeSubscription> bridgeSubscriptions, Set<ControllerViewFacet> controllerFacets) implements ControllerView
    {
        private TestView (final String id, final Set<SurfaceClaim> declaredClaims, final Set<BridgeSubscription> bridgeSubscriptions)
        {
            this (id, declaredClaims, bridgeSubscriptions, Set.of ());
        }


        @Override
        public ViewProfile profile ()
        {
            return ViewProfile.fixed ("test", this.declaredClaims, this.controllerFacets);
        }
    }


    private record ActionView (Set<ControlId> controls, InputKind inputKind, SurfaceArea claimedArea) implements ControllerView
    {
        private ActionView (final Set<ControlId> controls)
        {
            this (controls, InputKind.BUTTON, SurfaceArea.NAVIGATION_PAGE);
        }


        @Override
        public String id ()
        {
            return "actions";
        }


        @Override
        public ViewProfile profile ()
        {
            return ViewProfile.fixed ("actions", Set.of (claim (this.claimedArea, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
        }


        @Override
        public Set<ControllerActionBinding> actionBindings ()
        {
            return this.controls.stream ()
                .map (control -> new ControllerActionBinding (
                    control,
                    this.inputKind,
                    ControllerActionId.SELECT_PARAMETER_PAGE,
                    Set.of (ControllerStateScope.ACTIVE_PARAMETERS)))
                .collect (java.util.stream.Collectors.toUnmodifiableSet ());
        }
    }
}
