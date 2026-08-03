// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Deterministic managed Drum Pitch device-state tests.
 */
class SelectedTrackDrumPitchHostTest
{
    private static final ParameterTargetId TARGET = new ParameterTargetId (0);
    private static final long ONE_SECOND = 1_000_000_000L;
    private static final long TEN_SECONDS = 10_000_000_000L;
    private static final String TRACK_UUID = "00000000-0000-0000-0000-00000000000a";


    @Test
    void exposesAndWritesBendFullPhysicalNormalizedRangeWithoutOptimism ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.25))));
        final SelectedTrackDrumPitchHost host = readyHost (adapter);
        final long generation = host.state ().generation ();

        assertEquals (0.25, slot (host).normalizedValue ());
        host.setImmediately (generation, TARGET, 0.0);
        host.setImmediately (generation, TARGET, 0.5);
        host.setImmediately (generation, TARGET, 1.0);

        assertEquals (List.of (
            new Semitones (0, 0.0),
            new Semitones (0, 0.5),
            new Semitones (0, 1.0)), adapter.commands);
        assertEquals (0.25, slot (host).normalizedValue ());

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 1.0)));
        host.refresh ();
        assertEquals (1.0, slot (host).normalizedValue ());
        assertEquals (generation, host.state ().generation ());

        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (generation, TARGET, -0.01));
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (generation, TARGET, 1.01));
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (generation, TARGET, Double.NaN));
    }


    @Test
    void provisionsOnceAndAppliesLatestIntentOnlyAfterSubscribedHelperAppears ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final FakeClock clock = new FakeClock ();
        final SelectedTrackDrumPitchHost host = readyHost (adapter, clock);
        final long absentGeneration = host.state ().generation ();

        assertTrue (slot (host).exists ());
        host.setImmediately (absentGeneration, TARGET, 0.25);
        host.setImmediately (absentGeneration, TARGET, 0.8);
        for (int index = 0; index < 20; index++)
            host.refresh ();

        assertEquals (List.of (new Insert ("track-a")), adapter.commands);
        assertEquals (0.5, slot (host).normalizedValue ());

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 3, 0.5)));
        host.refresh ();
        assertEquals (List.of (new Insert ("track-a"), new Semitones (0, 0.8)), adapter.commands);
        assertEquals (0.5, slot (host).normalizedValue ());

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 3, 0.8)));
        host.refresh ();
        assertEquals (0.8, slot (host).normalizedValue ());
        assertTrue (host.state ().generation () > absentGeneration);
    }


    @Test
    void nativeInsertionIsAcquiredOnlyFromCausalTopologyAndPersistedBeforeWrite ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample (TRACK_UUID, 3, List.of ()));
        final FakeOwnershipStore ownership = new FakeOwnershipStore (DrumPitchOwnershipStore.Snapshot.loadedEmpty ());
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, ownership, clock, message -> { });
        host.refresh ();

        host.setImmediately (host.state ().generation (), TARGET, 0.9);
        adapter.current = sample (
            TRACK_UUID,
            4,
            List.of (nativeHelper (0, 3, true, 0.5, nonCanonicalConfiguration ())));
        host.refresh ();
        assertEquals (List.of (new Insert (TRACK_UUID), new Enabled (0, false)), adapter.commands);

        adapter.current = sample (
            TRACK_UUID,
            4,
            List.of (nativeHelper (0, 3, false, 0.5, nonCanonicalConfiguration ())));
        clock.advance (ONE_SECOND);
        host.refresh ();

        adapter.current = sample (
            TRACK_UUID,
            4,
            List.of (nativeHelper (0, 3, false, 0.5, canonicalConfiguration ())));
        clock.advance (ONE_SECOND);
        host.refresh ();

        adapter.current = sample (TRACK_UUID, 4, List.of (nativeHelper (0, 3, true, 0.5, canonicalConfiguration ())));
        host.refresh ();

        final DrumPitchOwnershipStore.Ownership saved = new DrumPitchOwnershipStore.Ownership (
            TRACK_UUID,
            3,
            4,
            DrumPitchOwnershipStore.CONFIGURATION_VERSION);
        assertEquals (List.of (saved), ownership.requests);
        assertTrue (adapter.commands.stream ().noneMatch (Semitones.class::isInstance));

        ownership.current = new DrumPitchOwnershipStore.Snapshot (true, true, List.of (saved));
        host.refresh ();

        assertEquals (List.of (
            new Insert (TRACK_UUID),
            new Enabled (0, false),
            new Configure (0),
            new Enabled (0, true),
            new Semitones (0, 0.9)), adapter.commands);
    }


    @Test
    void nativeProvisioningWaitsForValidDocumentOwnershipReadback ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample (TRACK_UUID, 3, List.of ()));
        final FakeOwnershipStore ownership = new FakeOwnershipStore (DrumPitchOwnershipStore.Snapshot.loading ());
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, ownership, new FakeClock (), message -> { });
        host.refresh ();

        assertFalse (slot (host).exists ());
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), TARGET, 0.8));
        assertTrue (adapter.commands.isEmpty ());

        ownership.current = DrumPitchOwnershipStore.Snapshot.loadedEmpty ();
        host.refresh ();
        assertTrue (slot (host).exists ());
    }


    @Test
    void concurrentTopologyChangeCannotBeMistakenForNativeInsertion ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample (TRACK_UUID, 3, List.of (unbrandedHelper (0, 1, 0.5))));
        final FakeOwnershipStore ownership = new FakeOwnershipStore (DrumPitchOwnershipStore.Snapshot.loadedEmpty ());
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, ownership, new FakeClock (), message -> { });
        host.refresh ();
        host.setImmediately (host.state ().generation (), TARGET, 0.8);

        adapter.current = sample (
            TRACK_UUID,
            4,
            List.of (
                unbrandedHelper (0, 2, 0.5),
                nativeHelper (1, 3, true, 0.5, nonCanonicalConfiguration ())));
        host.refresh ();

        assertFalse (slot (host).exists ());
        assertEquals (List.of (new Insert (TRACK_UUID)), adapter.commands);
        assertTrue (ownership.requests.isEmpty ());
    }


    @Test
    void persistedOwnershipReacquiresExactNativeHelperAfterRestart ()
    {
        final DrumPitchOwnershipStore.Ownership record = new DrumPitchOwnershipStore.Ownership (
            TRACK_UUID,
            3,
            4,
            DrumPitchOwnershipStore.CONFIGURATION_VERSION);
        final FakeOwnershipStore ownership = new FakeOwnershipStore (new DrumPitchOwnershipStore.Snapshot (true, true, List.of (record)));
        final FakeAdapter adapter = new FakeAdapter (sample (TRACK_UUID, 6, List.of (nativeHelper (0, 5, true, 0.4, canonicalConfiguration ()))));
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, ownership, new FakeClock (), message -> { });
        host.refresh ();

        assertFalse (slot (host).exists ());
        assertEquals (List.of (new DrumPitchOwnershipStore.Ownership (
            TRACK_UUID,
            5,
            6,
            DrumPitchOwnershipStore.CONFIGURATION_VERSION)), ownership.requests);

        ownership.current = new DrumPitchOwnershipStore.Snapshot (true, true, ownership.requests);
        host.refresh ();
        host.setImmediately (host.state ().generation (), TARGET, 0.7);

        assertEquals (List.of (new Semitones (0, 0.7)), adapter.commands);
    }


    @Test
    void staleOwnershipReprovisionsCausallyWithoutAdoptingAnotherBend ()
    {
        final DrumPitchOwnershipStore.Ownership stale = new DrumPitchOwnershipStore.Ownership (
            TRACK_UUID,
            2,
            3,
            DrumPitchOwnershipStore.CONFIGURATION_VERSION);
        final FakeOwnershipStore ownership = new FakeOwnershipStore (new DrumPitchOwnershipStore.Snapshot (true, true, List.of (stale)));
        final FakeAdapter adapter = new FakeAdapter (sample (TRACK_UUID, 3, List.of (unbrandedHelper (0, 1, 0.2))));
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, ownership, new FakeClock (), message -> { });
        host.refresh ();

        assertTrue (slot (host).exists ());
        host.setImmediately (host.state ().generation (), TARGET, 0.8);

        assertEquals (List.of (new Insert (TRACK_UUID)), adapter.commands);
        assertTrue (ownership.requests.isEmpty ());
    }


    @Test
    void topologyMustSettleButOneSettledSampleIsEnough ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample (false, true, "track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, new FakeClock ());

        for (int index = 0; index < 10; index++)
            host.refresh ();
        assertFalse (slot (host).coherent ());
        assertFalse (slot (host).exists ());
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), TARGET, 0.7));
        assertTrue (adapter.commands.isEmpty ());

        adapter.current = sample (true, true, "track-a", 3, List.of ());
        host.refresh ();
        assertTrue (slot (host).coherent ());
        assertTrue (slot (host).exists ());

        host.setImmediately (host.state ().generation (), TARGET, 0.7);
        assertEquals (List.of (new Insert ("track-a")), adapter.commands);
    }


    @Test
    void trackScopedInsertionSurvivesSelectionAtoBtoA ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, clock);

        host.setImmediately (host.state ().generation (), TARGET, 0.8);
        adapter.current = sample ("track-b", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();
        host.setImmediately (host.state ().generation (), TARGET, 0.6);

        adapter.current = sample ("track-a", 3, List.of ());
        host.refresh ();
        host.setImmediately (host.state ().generation (), TARGET, 0.7);

        assertEquals (List.of (
            new Insert ("track-a"),
            new Semitones (0, 0.6)), adapter.commands);

        clock.advance (TEN_SECONDS);
        host.refresh ();
        assertEquals (List.of (
            new Insert ("track-a"),
            new Semitones (0, 0.6),
            new Insert ("track-a")), adapter.commands);

        clock.advance (TEN_SECONDS);
        host.refresh ();
        assertEquals (2, adapter.insertCount ("track-a"));
    }


    @Test
    void aHelperObservedOnAnotherTrackCannotAcknowledgePendingInsertion ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, clock);

        host.setImmediately (host.state ().generation (), TARGET, 0.8);
        adapter.current = sample ("track-b", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();
        clock.advance (TEN_SECONDS);
        adapter.current = sample ("track-a", 3, List.of ());
        host.refresh ();
        host.setImmediately (host.state ().generation (), TARGET, 0.7);

        assertEquals (2, adapter.insertCount ("track-a"));
    }


    @Test
    void synchronousInsertionFailureBacksOffAndStillCapsAttempts ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        adapter.insertFailures = 1;
        final SelectedTrackDrumPitchHost host = readyHost (adapter, clock);

        assertDoesNotThrow (() -> host.setImmediately (host.state ().generation (), TARGET, 0.8));
        host.refresh ();
        assertEquals (1, adapter.insertCount ("track-a"));

        clock.advance (TEN_SECONDS - 1);
        host.refresh ();
        assertEquals (1, adapter.insertCount ("track-a"));

        clock.advance (1);
        host.refresh ();
        assertEquals (2, adapter.insertCount ("track-a"));

        clock.advance (TEN_SECONDS);
        host.refresh ();
        assertEquals (2, adapter.insertCount ("track-a"));

        clock.advance (TEN_SECONDS);
        host.refresh ();
        assertFalse (slot (host).exists ());
    }


    @Test
    void exhaustedInsertionWithdrawsTargetAndReportsExactlyOnce ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final List<String> warnings = new ArrayList<> ();
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, clock, warnings::add);
        host.refresh ();

        host.setImmediately (host.state ().generation (), TARGET, 0.8);
        clock.advance (TEN_SECONDS);
        host.refresh ();
        assertTrue (slot (host).exists ());
        assertEquals (2, adapter.insertCount ("track-a"));

        clock.advance (TEN_SECONDS);
        host.refresh ();
        host.refresh ();

        assertFalse (slot (host).exists ());
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), TARGET, 0.2));
        assertEquals (1, warnings.stream ().filter (warning -> warning.contains ("did not appear")).count ());

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();
        assertTrue (slot (host).exists ());
        assertEquals (2, adapter.insertCount ("track-a"));
        assertTrue (adapter.commands.stream ().noneMatch (Semitones.class::isInstance));
    }


    @Test
    void fullTrackAttemptRegistryWithdrawsNewTargetsAndReportsExactlyOnce ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample ("track-0", 3, List.of ()));
        final List<String> warnings = new ArrayList<> ();
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, clock, warnings::add);
        host.refresh ();

        for (int track = 0; track < 32; track++)
        {
            if (track > 0)
            {
                adapter.current = sample ("track-" + track, 3, List.of ());
                host.refresh ();
            }
            assertTrue (slot (host).exists ());
            host.setImmediately (host.state ().generation (), TARGET, 0.6);
        }

        adapter.current = sample ("track-32", 3, List.of ());
        host.refresh ();
        host.refresh ();

        assertFalse (slot (host).exists ());
        assertEquals (1, warnings.stream ().filter (warning -> warning.contains ("registry is full")).count ());
    }


    @Test
    void ownedDisabledOrMisconfiguredHelperIsRepairedBeforeExposure ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, false, true, 0.35, nonCanonicalConfiguration ()))));
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, clock);

        host.refresh ();
        assertTrue (slot (host).coherent ());
        assertFalse (slot (host).exists ());
        assertEquals (List.of (new Configure (0)), adapter.commands);
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), TARGET, 0.8));

        host.refresh ();
        assertEquals (List.of (new Configure (0)), adapter.commands);

        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, false, true, 0.35, canonicalConfiguration ())));
        clock.advance (ONE_SECOND);
        host.refresh ();
        assertEquals (List.of (new Configure (0), new Enabled (0, true)), adapter.commands);
        assertFalse (slot (host).exists ());

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.35)));
        host.refresh ();
        assertTrue (slot (host).exists ());
        assertEquals (0.35, slot (host).normalizedValue ());
    }


    @Test
    void enabledDriftedHelperIsDisabledBeforeConfigurationAndReenabledAfterReadback ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, true, true, 0.5, nonCanonicalConfiguration ()))));
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, clock);

        host.refresh ();
        assertEquals (List.of (new Enabled (0, false)), adapter.commands);

        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, false, true, 0.5, nonCanonicalConfiguration ())));
        clock.advance (ONE_SECOND);
        host.refresh ();
        assertEquals (List.of (new Enabled (0, false), new Configure (0)), adapter.commands);

        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, false, true, 0.5, canonicalConfiguration ())));
        clock.advance (ONE_SECOND);
        host.refresh ();
        assertEquals (List.of (
            new Enabled (0, false),
            new Configure (0),
            new Enabled (0, true)), adapter.commands);
    }


    @Test
    void recoverableCommandFailuresAreReportedWithoutOptimisticState ()
    {
        final FakeClock clock = new FakeClock ();
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.25))));
        final List<String> warnings = new ArrayList<> ();
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, clock, warnings::add);
        host.refresh ();
        adapter.valueFailures = 2;

        host.setImmediately (host.state ().generation (), TARGET, 0.75);
        assertEquals (0.25, slot (host).normalizedValue ());
        clock.advance (ONE_SECOND);
        host.refresh ();

        assertEquals (1, warnings.stream ().filter (warning -> warning.contains ("pitch write failed")).count ());
        assertEquals (0.25, slot (host).normalizedValue ());
    }


    @Test
    void pendingPitchSurvivesConfigurationRepair ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, new FakeClock ());

        host.setImmediately (host.state ().generation (), TARGET, 0.9);
        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, false, true, 0.5, nonCanonicalConfiguration ())));
        host.refresh ();
        assertEquals (List.of (new Insert ("track-a"), new Configure (0)), adapter.commands);

        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, false, true, 0.5, canonicalConfiguration ())));
        host.refresh ();
        assertEquals (List.of (new Insert ("track-a"), new Configure (0)), adapter.commands);

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();
        assertEquals (List.of (
            new Insert ("track-a"),
            new Configure (0),
            new Semitones (0, 0.9)), adapter.commands);
    }


    @Test
    void staggeredBrandMetadataRetainsPendingPitchWithoutDuplicateInsertion ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, new FakeClock ());

        host.setImmediately (host.state ().generation (), TARGET, 0.9);
        adapter.current = sample (
            "track-a",
            4,
            List.of (helper (
                0,
                2,
                SelectedTrackDrumPitchHost.HELPER_PRESET_NAME,
                "",
                true,
                true,
                0.5,
                canonicalConfiguration ())));
        host.refresh ();
        assertFalse (slot (host).exists ());
        assertEquals (List.of (new Insert ("track-a")), adapter.commands);

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();
        assertEquals (List.of (new Insert ("track-a"), new Semitones (0, 0.9)), adapter.commands);
    }


    @Test
    void missingParametersRetainPendingPitchUntilHydrated ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, new FakeClock ());

        host.setImmediately (host.state ().generation (), TARGET, 0.9);
        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, true, false, 0.5, missingConfigurationParameter ()))) ;
        host.refresh ();
        assertEquals (List.of (new Insert ("track-a")), adapter.commands);

        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();
        assertEquals (List.of (new Insert ("track-a"), new Semitones (0, 0.9)), adapter.commands);
    }


    @Test
    void conflictingNonblankBrandMetadataFailsClosed ()
    {
        final List<SelectedTrackDrumPitchHost.HelperSample> conflicts = List.of (
            helper (0, 2, SelectedTrackDrumPitchHost.HELPER_PRESET_NAME, "Wrong creator", true, true, 0.5, canonicalConfiguration ()),
            helper (0, 2, "Wrong name", SelectedTrackDrumPitchHost.HELPER_PRESET_CREATOR, true, true, 0.5, canonicalConfiguration ()));

        for (final SelectedTrackDrumPitchHost.HelperSample conflict: conflicts)
        {
            final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 4, List.of (conflict)));
            final SelectedTrackDrumPitchHost host = readyHost (adapter);
            assertFalse (slot (host).exists ());
            assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), TARGET, 0.8));
            assertTrue (adapter.commands.isEmpty ());
        }
    }


    @Test
    void optionalInsertionCapabilityDoesNotHideExistingOwnedHelper ()
    {
        final FakeAdapter absentAdapter = new FakeAdapter (sample (true, false, "track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost absent = readyHost (absentAdapter);
        assertTrue (slot (absent).coherent ());
        assertFalse (slot (absent).exists ());
        assertThrows (IllegalArgumentException.class, () -> absent.setImmediately (absent.state ().generation (), TARGET, 0.8));

        final FakeAdapter existingAdapter = new FakeAdapter (sample (
            true,
            false,
            "track-a",
            4,
            List.of (ownedHelper (0, 2, 0.3))));
        final SelectedTrackDrumPitchHost existing = readyHost (existingAdapter);
        assertTrue (slot (existing).exists ());
        existing.setImmediately (existing.state ().generation (), TARGET, 0.7);
        assertEquals (List.of (new Semitones (0, 0.7)), existingAdapter.commands);
    }


    @Test
    void unbrandedBendIsNeverAdoptedOrMutated ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 4, List.of (unbrandedHelper (0, 2, 0.1))));
        final SelectedTrackDrumPitchHost host = readyHost (adapter);

        host.setImmediately (host.state ().generation (), TARGET, 0.0);

        assertEquals (List.of (new Insert ("track-a")), adapter.commands);
        assertEquals (0.5, slot (host).normalizedValue ());
    }


    @Test
    void brandedHelperMayHaveOtherTopLevelDevicesBeforeDrum ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 6, List.of (ownedHelper (0, 2, 0.4))));
        final SelectedTrackDrumPitchHost host = readyHost (adapter);

        host.setImmediately (host.state ().generation (), TARGET, 0.6);

        assertEquals (List.of (new Semitones (0, 0.6)), adapter.commands);
    }


    @Test
    void duplicateOrPostDrumBrandedHelpersFailClosed ()
    {
        final List<SelectedTrackDrumPitchHost.HostSample> invalidSamples = List.of (
            sample ("track-a", 6, List.of (ownedHelper (0, 2, 0.5), ownedHelper (1, 4, 0.5))),
            sample ("track-a", 3, List.of (ownedHelper (0, 3, 0.5))),
            sample ("track-a", 3, List.of (ownedHelper (0, 4, 0.5))));

        for (final SelectedTrackDrumPitchHost.HostSample invalidSample: invalidSamples)
        {
            final FakeAdapter adapter = new FakeAdapter (invalidSample);
            final SelectedTrackDrumPitchHost host = readyHost (adapter);
            assertFalse (slot (host).exists ());
            assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), TARGET, 0.8));
            assertTrue (adapter.commands.isEmpty ());
        }
    }


    @Test
    void observingOwnedHelperAcknowledgesOnlyThatTracksInsertion ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, new FakeClock ());

        host.setImmediately (host.state ().generation (), TARGET, 0.75);
        adapter.current = sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.75)));
        host.refresh ();

        adapter.current = sample ("track-a", 3, List.of ());
        host.refresh ();
        host.setImmediately (host.state ().generation (), TARGET, 0.25);

        assertEquals (List.of (new Insert ("track-a"), new Insert ("track-a")), adapter.commands);
    }


    @Test
    void selectedTrackSwitchDropsPendingPitchIntent ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 3, List.of ()));
        final SelectedTrackDrumPitchHost host = readyHost (adapter, new FakeClock ());

        host.setImmediately (host.state ().generation (), TARGET, 0.8);
        adapter.current = sample ("track-b", 4, List.of (ownedHelper (0, 2, 0.5)));
        host.refresh ();

        assertEquals (List.of (new Insert ("track-a")), adapter.commands);
        assertEquals ("track-b", host.state ().trackId ());
        assertEquals (0.5, slot (host).normalizedValue ());
    }


    @Test
    void freshTrackRetargetFencesPreparedWrite ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.5))));
        final SelectedTrackDrumPitchHost host = readyHost (adapter);
        final long oldGeneration = host.state ().generation ();

        adapter.current = sample (false, true, "track-b", 4, List.of (ownedHelper (0, 2, 0.5)));

        assertThrows (IllegalStateException.class, () -> host.setImmediately (oldGeneration, TARGET, 0.8));
        assertTrue (adapter.commands.isEmpty ());
        assertEquals ("track-b", host.state ().trackId ());
        assertFalse (slot (host).coherent ());
        assertTrue (host.state ().generation () > oldGeneration);
    }


    @Test
    void configurationChangesInvalidatePreparedGeneration ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", 4, List.of (ownedHelper (0, 2, 0.5))));
        final SelectedTrackDrumPitchHost host = readyHost (adapter);
        final long oldGeneration = host.state ().generation ();

        adapter.current = sample (
            "track-a",
            4,
            List.of (ownedHelper (0, 2, true, true, 0.5, nonCanonicalConfiguration ())));

        assertThrows (IllegalStateException.class, () -> host.setImmediately (oldGeneration, TARGET, 0.8));
        assertTrue (adapter.commands.isEmpty ());
        assertTrue (host.state ().generation () > oldGeneration);
        assertFalse (slot (host).exists ());
    }


    @Test
    void missingDrumAndFullOrOverflowingCanopyFailClosed ()
    {
        final FakeAdapter noDrumAdapter = new FakeAdapter (new SelectedTrackDrumPitchHost.HostSample (
            true,
            true,
            true,
            "track-a",
            true,
            0,
            SelectedTrackDrumPitchHost.DeviceSample.missing (),
            0,
            List.of ()));
        final SelectedTrackDrumPitchHost noDrum = readyHost (noDrumAdapter);
        assertTrue (slot (noDrum).coherent ());
        assertFalse (slot (noDrum).exists ());

        final List<SelectedTrackDrumPitchHost.HelperSample> full = new ArrayList<> ();
        for (int index = 0; index < SelectedTrackDrumPitchHost.HELPER_SCAN_CAPACITY; index++)
            full.add (unbrandedHelper (index, index + 10, 0.5));
        final SelectedTrackDrumPitchHost fullCanopy = readyHost (new FakeAdapter (sample ("track-a", 4, full)));
        assertFalse (slot (fullCanopy).exists ());

        final FakeAdapter overflowAdapter = new FakeAdapter (new SelectedTrackDrumPitchHost.HostSample (
            true,
            true,
            true,
            "track-a",
            true,
            1,
            new SelectedTrackDrumPitchHost.DeviceSample (true, 3),
            SelectedTrackDrumPitchHost.HELPER_SCAN_CAPACITY + 1,
            full));
        final SelectedTrackDrumPitchHost overflow = readyHost (overflowAdapter);
        assertFalse (slot (overflow).coherent ());
        assertFalse (slot (overflow).exists ());
    }


    private static SelectedTrackDrumPitchHost readyHost (final FakeAdapter adapter)
    {
        return readyHost (adapter, new FakeClock ());
    }


    private static SelectedTrackDrumPitchHost readyHost (final FakeAdapter adapter, final FakeClock clock)
    {
        final SelectedTrackDrumPitchHost host = new SelectedTrackDrumPitchHost (adapter, clock);
        host.refresh ();
        return host;
    }


    private static SelectedTrackParameterHost.Slot slot (final SelectedTrackDrumPitchHost host)
    {
        return host.state ().slots ().getFirst ();
    }


    private static SelectedTrackDrumPitchHost.HostSample sample (final String trackId, final int drumPosition, final List<SelectedTrackDrumPitchHost.HelperSample> helpers)
    {
        return sample (true, true, trackId, drumPosition, helpers);
    }


    private static SelectedTrackDrumPitchHost.HostSample sample (final boolean topologySettled, final boolean insertionSupported, final String trackId, final int drumPosition, final List<SelectedTrackDrumPitchHost.HelperSample> helpers)
    {
        return new SelectedTrackDrumPitchHost.HostSample (
            topologySettled,
            insertionSupported,
            true,
            trackId,
            true,
            1,
            new SelectedTrackDrumPitchHost.DeviceSample (true, drumPosition),
            helpers.size (),
            helpers);
    }


    private static SelectedTrackDrumPitchHost.HelperSample ownedHelper (final int bankIndex, final int position, final double semitonesNormalized)
    {
        return ownedHelper (bankIndex, position, true, true, semitonesNormalized, canonicalConfiguration ());
    }


    private static SelectedTrackDrumPitchHost.HelperSample ownedHelper (final int bankIndex, final int position, final boolean enabled, final boolean semitonesExists, final double semitonesNormalized, final List<SelectedTrackDrumPitchHost.ConfigurationSample> configuration)
    {
        return helper (
            bankIndex,
            position,
            SelectedTrackDrumPitchHost.HELPER_PRESET_NAME,
            SelectedTrackDrumPitchHost.HELPER_PRESET_CREATOR,
            enabled,
            semitonesExists,
            semitonesNormalized,
            configuration);
    }


    private static SelectedTrackDrumPitchHost.HelperSample unbrandedHelper (final int bankIndex, final int position, final double semitonesNormalized)
    {
        return helper (bankIndex, position, "User Bend", "Somebody Else", true, true, semitonesNormalized, canonicalConfiguration ());
    }


    private static SelectedTrackDrumPitchHost.HelperSample nativeHelper (final int bankIndex, final int position, final boolean enabled, final double semitonesNormalized, final List<SelectedTrackDrumPitchHost.ConfigurationSample> configuration)
    {
        return helper (bankIndex, position, "", "", enabled, true, semitonesNormalized, configuration);
    }


    private static SelectedTrackDrumPitchHost.HelperSample helper (final int bankIndex, final int position, final String presetName, final String presetCreator, final boolean enabled, final boolean semitonesExists, final double semitonesNormalized, final List<SelectedTrackDrumPitchHost.ConfigurationSample> configuration)
    {
        return new SelectedTrackDrumPitchHost.HelperSample (
            bankIndex,
            true,
            position,
            presetName,
            presetCreator,
            enabled,
            semitonesExists,
            semitonesNormalized,
            configuration);
    }


    private static List<SelectedTrackDrumPitchHost.ConfigurationSample> canonicalConfiguration ()
    {
        final List<SelectedTrackDrumPitchHost.ConfigurationSample> configuration = new ArrayList<> ();
        for (final SelectedTrackDrumPitchHost.ConfigurationSpec specification: SelectedTrackDrumPitchHost.CANONICAL_CONFIGURATION)
            configuration.add (new SelectedTrackDrumPitchHost.ConfigurationSample (specification.parameterId (), true, specification.expectedRaw ()));
        return configuration;
    }


    private static List<SelectedTrackDrumPitchHost.ConfigurationSample> nonCanonicalConfiguration ()
    {
        final List<SelectedTrackDrumPitchHost.ConfigurationSample> configuration = new ArrayList<> (canonicalConfiguration ());
        final SelectedTrackDrumPitchHost.ConfigurationSample first = configuration.getFirst ();
        configuration.set (0, new SelectedTrackDrumPitchHost.ConfigurationSample (first.parameterId (), true, 0));
        return configuration;
    }


    private static List<SelectedTrackDrumPitchHost.ConfigurationSample> missingConfigurationParameter ()
    {
        final List<SelectedTrackDrumPitchHost.ConfigurationSample> configuration = new ArrayList<> (canonicalConfiguration ());
        final SelectedTrackDrumPitchHost.ConfigurationSample first = configuration.getFirst ();
        configuration.set (0, new SelectedTrackDrumPitchHost.ConfigurationSample (first.parameterId (), false, first.rawValue ()));
        return configuration;
    }


    private interface Command
    {
    }


    private record Insert (String trackId) implements Command
    {
    }


    private record Configure (int helperIndex) implements Command
    {
    }


    private record Enabled (int helperIndex, boolean enabled) implements Command
    {
    }


    private record Semitones (int helperIndex, double normalizedValue) implements Command
    {
    }


    private static final class FakeClock implements LongSupplier
    {
        private long now;


        /** {@inheritDoc} */
        @Override
        public long getAsLong ()
        {
            return this.now;
        }


        private void advance (final long nanoseconds)
        {
            this.now += nanoseconds;
        }
    }


    private static final class FakeOwnershipStore implements DrumPitchOwnershipStore
    {
        private final List<Ownership> requests = new ArrayList<> ();
        private Snapshot current;


        private FakeOwnershipStore (final Snapshot current)
        {
            this.current = current;
        }


        /** {@inheritDoc} */
        @Override
        public Snapshot snapshot ()
        {
            return this.current;
        }


        /** {@inheritDoc} */
        @Override
        public void save (final Ownership ownership)
        {
            this.requests.add (ownership);
        }
    }


    private static final class FakeAdapter implements SelectedTrackDrumPitchHost.Adapter
    {
        private final List<Command> commands = new ArrayList<> ();
        private SelectedTrackDrumPitchHost.HostSample current;
        private int insertFailures;
        private int valueFailures;


        private FakeAdapter (final SelectedTrackDrumPitchHost.HostSample current)
        {
            this.current = current;
        }


        /** {@inheritDoc} */
        @Override
        public SelectedTrackDrumPitchHost.HostSample sample ()
        {
            return this.current;
        }


        /** {@inheritDoc} */
        @Override
        public void insertHelperBeforeDrum ()
        {
            this.commands.add (new Insert (this.current.trackId ()));
            if (this.insertFailures > 0)
            {
                this.insertFailures--;
                throw new IllegalStateException ("synthetic insertion failure");
            }
        }


        /** {@inheritDoc} */
        @Override
        public void configureHelper (final int helperIndex)
        {
            this.commands.add (new Configure (helperIndex));
        }


        /** {@inheritDoc} */
        @Override
        public void setHelperEnabled (final int helperIndex, final boolean enabled)
        {
            this.commands.add (new Enabled (helperIndex, enabled));
        }


        /** {@inheritDoc} */
        @Override
        public void setSemitonesNormalized (final int helperIndex, final double normalizedValue)
        {
            this.commands.add (new Semitones (helperIndex, normalizedValue));
            if (this.valueFailures > 0)
            {
                this.valueFailures--;
                throw new IllegalStateException ("synthetic value failure");
            }
        }


        private int insertCount (final String trackId)
        {
            int count = 0;
            for (final Command command: this.commands)
            {
                if (command instanceof final Insert insert && insert.trackId ().equals (trackId))
                    count++;
            }
            return count;
        }
    }
}
