// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.runtime.view.ProjectMacroControlsView;
import de.mossgrabers.pull.core.runtime.view.SessionClipGridView;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


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
    void stableAdapterInputIsOwnedWithoutPublishingACoreRoute ()
    {
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("macros", List.of (new ProjectMacroControlsView ()));
        final CoreResult result = workspace.start (snapshot ());

        assertEquals (Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS), result.desiredControllerWorkspace ().facets ());
        assertEquals (0, result.desiredInputRoutes ().routes ().size ());
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


    private static ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (
            0,
            0,
            ShellCapabilities.empty (),
            ClipCatalogSnapshot.empty (),
            Map.of (),
            Set.of (),
            Set.of ());
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
}
