// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.event.InputKind;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        final TestView view = view ("grid", claim (SurfaceArea.DRUM_FILL_PADS, SurfaceClaim.Kind.OBSERVE_INPUT));

        final CoreResult result = CompiledWorkspace.compile ("grid inputs", List.of (view)).start (snapshot ());
        final ControlId firstPad = CoreControls.DRUM_FILL_1;

        assertEquals (24, result.desiredInputRoutes ().routes ().size ());
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


    private static TestView view (final String id, final SurfaceClaim claim)
    {
        return new TestView (id, Set.of (claim), Set.of ());
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


    private record TestView (String id, Set<SurfaceClaim> claims, Set<BridgeSubscription> bridgeSubscriptions) implements ControllerView
    {
    }
}
