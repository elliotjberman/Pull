// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.view.ControllerLevelViews;
import de.mossgrabers.pull.core.runtime.view.ControllerMappingNameView;
import de.mossgrabers.pull.core.runtime.view.DrumControlPadView;
import de.mossgrabers.pull.core.runtime.view.ProjectPlaybackCoordinator;
import de.mossgrabers.pull.core.runtime.view.TrackMappingRegistry;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import de.mossgrabers.pull.core.view.CompiledWorkspace;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


/** Names follow observed track UUIDs without participating in native matcher identity. */
class ControllerMappingNameViewTest
{
    private static final String DOCUMENT = uuid (1000);
    private static final String FIRST = uuid (1);
    private static final String SECOND = uuid (2);
    private static final List<String> TRACKS = List.of (FIRST, SECOND);


    @Test
    void selectedRenameChangesMetadataWithoutChangingMatcherBindings ()
    {
        final CompiledWorkspace workspace = CompiledWorkspace.compile ("named controls", List.of (new ControllerMappingNameView (), new DrumControlPadView ()));
        final var before = workspace.start (snapshot (DOCUMENT, FIRST, "Drums A", 1, TRACKS)).desiredOutput ().controllerMappings ();
        final var after = workspace.handle (new SnapshotChangedEvent (2, 2), snapshot (DOCUMENT, FIRST, "Renamed", 1, TRACKS)).desiredOutput ().controllerMappings ();

        assertEquals (4, before.bindings ().size ());
        assertEquals (before.bindings (), after.bindings (), "cosmetic metadata must not enter matcher equality");
        assertEquals ("Drums A — Drum Controller Toggle 1", before.names ().names ().get (mapping (0, 0)));
        assertEquals ("Renamed — Drum Controller Toggle 4", after.names ().names ().get (mapping (0, 3)));
        assertEquals (DOCUMENT, after.names ().documentId ());
        assertEquals (1, after.names ().storageRevision ());
    }


    @Test
    void controllerLevelNamesSurvivePageChangesAndRefreshOnlyTheSelectedTrack ()
    {
        final ControllerLevelViews retained = new ControllerLevelViews (new WorkspaceSelection (WorkspaceSelection.Id.DEFAULT), new ProjectPlaybackCoordinator ());
        final CompiledWorkspace firstPage = CompiledWorkspace.compile ("first page", retained.compose (List.of ()));
        final CompiledWorkspace secondPage = CompiledWorkspace.compile ("second page", retained.composeWithoutNoteController (List.of ()));
        final CoreResult first = firstPage.start (snapshot (DOCUMENT, FIRST, "Drums A", 1, TRACKS));
        assertTrue (first.effects ().isEmpty (), "naming an allocated track must not request registry allocation or target writes");
        assertTrue (first.desiredInputRoutes ().routes ().stream ().noneMatch (route -> CoreControls.DRUM_CONTROL_PADS.contains (route.controlId ())));
        assertTrue (first.desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK));
        final var second = secondPage.start (snapshot (DOCUMENT, SECOND, "Drums B", 1, TRACKS)).desiredOutput ().controllerMappings ();

        assertTrue (second.bindings ().isEmpty ());
        assertEquals (8, second.names ().names ().size ());
        assertEquals ("Drums A — Drum Controller Toggle 1", second.names ().names ().get (mapping (0, 0)));
        assertEquals ("Drums B — Drum Controller Toggle 1", second.names ().names ().get (mapping (1, 0)));
        final var reselected = firstPage.activate (snapshot (DOCUMENT, FIRST, "Renamed while inactive", 1, TRACKS)).desiredOutput ().controllerMappings ().names ();
        assertEquals ("Renamed while inactive — Drum Controller Toggle 1", reselected.names ().get (mapping (0, 0)));
        assertEquals ("Drums B — Drum Controller Toggle 1", reselected.names ().get (mapping (1, 0)));
    }


    @Test
    void newOrInvalidDocumentsClearCachedNamesAndNeverNameAnUnallocatedTrack ()
    {
        final ControllerMappingNameView view = new ControllerMappingNameView ();
        assertEquals (4, names (view, snapshot (DOCUMENT, FIRST, "Original", 1, TRACKS)).names ().size ());
        final String otherDocument = uuid (1001);
        final ControllerMappingNames other = names (view, snapshot (otherDocument, SECOND, "Other document", 2, TRACKS));
        assertEquals (otherDocument, other.documentId ());
        assertFalse (other.names ().containsKey (mapping (0, 0)), "a shared track UUID cannot retain another document's label");
        assertEquals ("Other document — Drum Controller Toggle 1", other.names ().get (mapping (1, 0)));

        final ControllerSnapshot invalid = snapshot (new ControllerMappingStorageSnapshot (true, 3, otherDocument, "broken"), FIRST, "Ignored");
        assertTrue (names (view, invalid).isEmpty ());
        final ControllerMappingNames recovered = names (view, snapshot (otherDocument, FIRST, "Fresh observation", 4, TRACKS));
        assertFalse (recovered.names ().containsKey (mapping (1, 0)));
        assertTrue (names (view, snapshot (ControllerMappingStorageSnapshot.empty (), FIRST, "Unavailable")).isEmpty ());
        assertTrue (names (view, snapshot (otherDocument, FIRST, "Not allocated", 5, List.of (SECOND))).isEmpty ());
    }


    @Test
    void temporaryStorageUnavailabilityHidesNamesWithoutForgettingTheSameDocumentsOwners ()
    {
        final ControllerMappingNameView view = new ControllerMappingNameView ();
        names (view, snapshot (DOCUMENT, FIRST, "Drums A", 1, List.of (FIRST)));
        assertTrue (names (view, snapshot (ControllerMappingStorageSnapshot.empty (), SECOND, "Drums B")).isEmpty ());
        final ControllerMappingNames acknowledged = names (view, snapshot (DOCUMENT, SECOND, "Drums B", 2, TRACKS));
        assertEquals ("Drums A — Drum Controller Toggle 1", acknowledged.names ().get (mapping (0, 0)));
        assertEquals ("Drums B — Drum Controller Toggle 1", acknowledged.names ().get (mapping (1, 0)));

        assertTrue (names (view, snapshot (ControllerMappingStorageSnapshot.empty (), SECOND, "Foreign")).isEmpty ());
        final ControllerMappingNames otherDocument = names (view, snapshot (uuid (1001), SECOND, "Foreign", 3, TRACKS));
        assertFalse (otherDocument.names ().containsKey (mapping (0, 0)));
        assertEquals ("Foreign — Drum Controller Toggle 1", otherDocument.names ().get (mapping (1, 0)));
    }


    @Test
    void registryChangesResolveNamesByUuidAndCoreReplacementStartsWithoutInactiveNames ()
    {
        final ControllerMappingNameView view = new ControllerMappingNameView ();
        names (view, snapshot (DOCUMENT, FIRST, "Drums A", 1, TRACKS));
        names (view, snapshot (DOCUMENT, SECOND, "Drums B", 1, TRACKS));
        final ControllerMappingNames reordered = names (view, snapshot (DOCUMENT, SECOND, "Drums B", 2, List.of (SECOND, FIRST)));
        assertEquals ("Drums B — Drum Controller Toggle 1", reordered.names ().get (mapping (0, 0)));
        assertEquals ("Drums A — Drum Controller Toggle 1", reordered.names ().get (mapping (1, 0)));

        final ControllerMappingNames reassigned = names (view, snapshot (DOCUMENT, uuid (3), "Replacement", 3, List.of (uuid (3), SECOND)));
        assertEquals ("Replacement — Drum Controller Toggle 1", reassigned.names ().get (mapping (0, 0)));
        assertEquals ("Drums B — Drum Controller Toggle 1", reassigned.names ().get (mapping (1, 0)));
        final ControllerMappingNames replacement = names (new ControllerMappingNameView (), snapshot (DOCUMENT, SECOND, "Drums B", 3, TRACKS));
        assertFalse (replacement.names ().containsKey (mapping (0, 0)));
        assertEquals (4, replacement.names ().size ());
    }


    @Test
    void blankAndLongTrackNamesKeepUsefulBoundedNames ()
    {
        final ControllerMappingNameView view = new ControllerMappingNameView ();
        final ControllerMappingNames blank = names (view, snapshot (DOCUMENT, SECOND, "  ", 1, TRACKS));
        assertEquals ("Bank 2 — Drum Controller Toggle 1", blank.names ().get (mapping (1, 0)));
        final ControllerMappingNames longName = names (view, snapshot (DOCUMENT, FIRST, "🥁".repeat (200), 1, TRACKS));
        final String name = longName.names ().get (mapping (0, 0));
        assertTrue (name.length () <= ControllerMappingNames.MAX_NAME_LENGTH);
        assertTrue (name.endsWith (" — Drum Controller Toggle 1"));
        final String prefix = name.substring (0, name.indexOf (" — "));
        assertEquals (0, prefix.length () % 2, "truncation preserves complete surrogate pairs");
        assertEquals (longName, names (view, snapshot (DOCUMENT, FIRST, "🥁".repeat (200), 1, TRACKS)));
    }


    private static ControllerMappingNames names (final ControllerMappingNameView view, final ControllerSnapshot snapshot)
    {
        view.reconcile (snapshot);
        return view.render (snapshot).controllerMappings ().names ();
    }


    private static ControllerSnapshot snapshot (final String document, final String track, final String name, final long revision, final List<String> tracks)
    {
        return snapshot (new ControllerMappingStorageSnapshot (true, revision, document, new TrackMappingRegistry (document, tracks).encode ()), track, name);
    }


    private static ControllerSnapshot snapshot (final ControllerMappingStorageSnapshot storage, final String track, final String name)
    {
        final SelectedTrackSnapshot selected = new SelectedTrackSnapshot (7, track, name, 0, "INSTRUMENT", true, false, false, true, false, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.75, 0.5, new RgbColor (0, 0, 0));
        final Map<ControllerMappingId, ControllerMappingTarget> targets = new LinkedHashMap<> ();
        CoreControllerMappings.trackBank (0).forEach (id -> targets.put (id, new ControllerMappingTarget (true, 0.8)));
        CoreControllerMappings.trackBank (1).forEach (id -> targets.put (id, new ControllerMappingTarget (true, 0.2)));
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), selected,
            new ControllerLayoutSnapshot (1, "DRUM_PAD", "TRACK", true, true, 36, GridPressureConfiguration.OFF),
            NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (),
            new ControllerMappingFeedbackSnapshot (true, targets, storage), MasterSnapshot.empty (), ProjectSnapshot.empty ());
        return new ControllerSnapshot (storage.revision (), 1, ShellCapabilities.empty (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }


    private static ControllerMappingId mapping (final int bank, final int slot)
    {
        return CoreControllerMappings.trackBank (bank).get (slot);
    }


    private static String uuid (final int number)
    {
        return String.format ("00000000-0000-0000-0000-%012d", number);
    }
}
