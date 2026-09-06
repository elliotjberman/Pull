// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNoteInputTranslation;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.runtime.view.SessionView;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class MusicalInputClaimsTest
{
    private static final SurfaceClaim LOWER = new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.MUSICAL_INPUT);


    @Test
    void playableAndSilentTablesRequireTheirOwnViewsMusicalClaim ()
    {
        final ControllerView otherOwner = view ("other", Set.of (LOWER), DesiredNoteInputTranslation.unowned (), false);
        for (final DesiredNoteInputTranslation table: List.of (map (36), DesiredNoteInputTranslation.silent ()))
        {
            final ControllerView unclaimed = view ("unclaimed", Set.of (), table, false);
            final var compiled = CompiledWorkspace.compile ("invalid", List.of (unclaimed, otherOwner));
            final var error = assertThrows (IllegalStateException.class, () -> compiled.start (snapshot ()));
            assertTrue (error.getMessage ().contains ("without a musical-input claim"));
        }
    }


    @Test
    void callbackOrRgbClaimsDoNotGrantNativeNoteOwnership ()
    {
        for (final SurfaceClaim.Kind kind: List.of (SurfaceClaim.Kind.OBSERVE_INPUT, SurfaceClaim.Kind.EXCLUSIVE_INPUT, SurfaceClaim.Kind.OUTPUT))
        {
            final ControllerView owner = view ("invalid", Set.of (new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, kind)), map (36), false);
            assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("invalid", List.of (owner)).start (snapshot ()));
        }
    }


    @Test
    void musicalOwnershipDoesNotCreateControllerRoutesOrActionReceivers ()
    {
        final ControllerView owner = view ("native", Set.of (LOWER), map (36), false);
        final var compiled = CompiledWorkspace.compile ("native", List.of (owner));
        final var result = compiled.start (snapshot ());
        assertTrue (result.desiredInputRoutes ().routes ().isEmpty ());
        assertTrue (compiled.handle (new ControllerInputEvent (1, 1, PushControlIds.pad (1), InputKind.PAD, InputPhase.BEGIN, 100), snapshot ()).effects ().isEmpty ());
    }


    @Test
    void enabledKeysMustStayWithinTheSameViewsPhysicalFootprint ()
    {
        final ControllerView owner = view ("lower", Set.of (LOWER), map (68), false);
        final var error = assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("invalid", List.of (owner)).start (snapshot ()));
        assertTrue (error.getMessage ().contains ("outside its musical-input footprint"));
    }


    @Test
    void overlappingMusicalClaimsConflictEvenBeforeEitherViewEnablesNotes ()
    {
        final ControllerView first = view ("first", Set.of (LOWER), DesiredNoteInputTranslation.unowned (), false);
        final ControllerView second = view ("second", Set.of (new SurfaceClaim (SurfaceArea.GRID_LOWER, SurfaceClaim.Kind.MUSICAL_INPUT)), DesiredNoteInputTranslation.unowned (), false);
        assertThrows (IllegalArgumentException.class, () -> CompiledWorkspace.compile ("overlap", List.of (first, second)));
    }


    @Test
    void enabledNotesCannotBorrowAnotherViewsControllerPadsIncludingSemanticFillActions ()
    {
        final ControllerView nativeUpper = view ("native", Set.of (new SurfaceClaim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.MUSICAL_INPUT)), map (68), true);
        final var upper = CompiledWorkspace.compile ("overlap", new SessionBankShape (8, 4), List.of (nativeUpper, SessionView.upper (false)));
        assertTrue (assertThrows (IllegalStateException.class, () -> upper.start (snapshot ())).getMessage ().contains ("controller pad"));

        final ControllerView nativeFill = view ("native", Set.of (new SurfaceClaim (SurfaceArea.GRID_LOWER, SurfaceClaim.Kind.MUSICAL_INPUT)), map (48), false);
        final ControllerView fill = view ("fill", Set.of (new SurfaceClaim (SurfaceArea.DRUM_FILL_PADS, SurfaceClaim.Kind.DIRECT_INPUT)), DesiredNoteInputTranslation.unowned (), false);
        assertTrue (assertThrows (IllegalStateException.class, () -> CompiledWorkspace.compile ("overlap", List.of (nativeFill, fill)).start (snapshot ())).getMessage ().contains ("controller pad"));
    }


    @Test
    void lowerDrumNativeTranslationComposesWithUpperSessionAndLowerObservers ()
    {
        final ControllerView nativeLower = view ("native", Set.of (LOWER), map (36, 63), true);
        final ControllerView observer = view ("observer", Set.of (new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.OBSERVE_INPUT)), DesiredNoteInputTranslation.unowned (), false);
        final var result = CompiledWorkspace.compile ("composite", new SessionBankShape (8, 4), List.of (nativeLower, observer, SessionView.upper (false))).start (snapshot ());
        assertEquals (map (36, 63), result.desiredControllerState ().notePerformance ().translation ());
        assertTrue (result.desiredControllerState ().notePerformance ().inputRoute ().active ());
    }


    private static ControllerView view (final String id, final Set<SurfaceClaim> claims, final DesiredNoteInputTranslation translation, final boolean composite)
    {
        return new ControllerView ()
        {
            @Override
            public String id () { return id; }

            @Override
            public ViewProfile profile () { return ViewProfile.fixed ("fixed", claims, composite ? Set.of (ControllerViewFacet.DRUM_CONTROLLER_LOWER) : Set.of ()); }

            @Override
            public ViewOutput render (final ControllerSnapshot snapshot)
            {
                if (!translation.owned ())
                    return ViewOutput.empty ();
                final boolean active = translation.allowsNotes ();
                final DesiredNotePerformance performance = new DesiredNotePerformance (
                    active && !composite ? DesiredControllerLayout.note (ControllerNoteView.DRUM_PAD) : DesiredControllerLayout.empty (),
                    active ? DesiredNoteInputRoute.selectedTrack (1, "track") : DesiredNoteInputRoute.disabled (),
                    translation);
                return new ViewOutput (Map.of (), Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), performance, DesiredNoteRepeat.unowned ());
            }
        };
    }


    private static DesiredNoteInputTranslation map (final int... physicalNotes)
    {
        final List<Integer> keys = new ArrayList<> (DesiredNoteInputTranslation.silent ().keyTranslation ());
        for (final int physicalNote: physicalNotes)
            keys.set (physicalNote, Integer.valueOf (60));
        return new DesiredNoteInputTranslation (true, keys, DesiredNoteInputTranslation.silent ().velocityTranslation ());
    }


    private static ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
    }
}
