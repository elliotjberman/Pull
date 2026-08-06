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
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.runtime.view.ProjectMacroControlsView;
import de.mossgrabers.pull.core.runtime.view.SessionClipGridView;
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
        final SessionClipGridView session = new SessionClipGridView (true);
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("session", new SessionBankShape (8, 4), List.of (session));

        assertEquals (Set.of (SessionClipGridView.SCENE_LAUNCH), workspace.profiles ().get (session.id ()).enabledFacets ());
        assertEquals (Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER, ControllerViewFacet.SESSION_SCENE_KEYS_UPPER), workspace.desiredControllerWorkspace ().facets ());
        assertEquals (4, session.claims ().size ());
    }


    @Test
    void projectMacrosOwnEncoderTurnsWhileStableAdaptsTouchAndDisplay ()
    {
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("macros", List.of (new ProjectMacroControlsView ()));
        final CoreResult result = workspace.start (parameterSnapshot ());
        final ControlId firstKnob = PushControlIds.continuous ("KNOB1");

        assertEquals (Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS), result.desiredControllerWorkspace ().facets ());
        assertEquals (8, result.desiredInputRoutes ().routes ().size ());
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().mode (firstKnob, InputKind.RELATIVE).orElseThrow ());
        assertEquals (Set.of (ParameterBankId.PROJECT_REMOTE), result.desiredParameterBanks ().banks ());
        assertTrue (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));

        final CoreResult adjusted = workspace.handle (
            new ControllerInputEvent (1, 1, firstKnob, InputKind.RELATIVE, InputPhase.UPDATE, 3),
            parameterSnapshot ());
        assertEquals (List.of (new AdjustParameterValueEffect (PROJECT_TARGET, 3)), adjusted.effects ());
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
    void rejectsConflictingPhysicalParameterMappings ()
    {
        final ControlId knob = PushControlIds.continuous ("KNOB1");
        final ControllerView first = parameterView ("first", knob, ParameterSlot.active (0));
        final ControllerView second = parameterView ("second", knob, ParameterSlot.active (1));

        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("conflict", List.of (first, second)));
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
