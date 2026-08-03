// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DrumPitchOwnershipStoreTest
{
    private static final String TRACK_A = "00000000-0000-0000-0000-00000000000a";
    private static final String TRACK_B = "00000000-0000-0000-0000-00000000000b";


    @Test
    void roundTripsInDeterministicTrackOrder ()
    {
        final List<DrumPitchOwnershipStore.Ownership> ownerships = List.of (
            ownership (TRACK_B, 6, 7),
            ownership (TRACK_A, 2, 3));

        final String encoded = DrumPitchOwnershipStore.Document.encode (ownerships);
        final DrumPitchOwnershipStore.Snapshot decoded = DrumPitchOwnershipStore.Document.decode (encoded);

        assertEquals ("v1|" + TRACK_A + ",2,3,1|" + TRACK_B + ",6,7,1", encoded);
        assertTrue (decoded.loaded ());
        assertTrue (decoded.valid ());
        assertEquals (List.of (ownership (TRACK_A, 2, 3), ownership (TRACK_B, 6, 7)), decoded.ownerships ());
    }


    @Test
    void malformedDuplicateAndUnsupportedRecordsFailClosed ()
    {
        final List<String> invalid = List.of (
            "",
            "v2",
            "v1|",
            "v1|not-a-uuid,1,2,1",
            "v1|" + TRACK_A + ",2,1,1",
            "v1|" + TRACK_A + ",1,2,2",
            "v1|" + TRACK_A + ",1,2,1|" + TRACK_A + ",3,4,1");

        for (final String encoded: invalid)
        {
            final DrumPitchOwnershipStore.Snapshot snapshot = DrumPitchOwnershipStore.Document.decode (encoded);
            assertTrue (snapshot.loaded ());
            assertFalse (snapshot.valid (), encoded);
            assertTrue (snapshot.ownerships ().isEmpty ());
        }
    }


    @Test
    void registryCapacityIsBounded ()
    {
        final List<DrumPitchOwnershipStore.Ownership> ownerships = new ArrayList<> ();
        for (int index = 0; index <= DrumPitchOwnershipStore.MAX_RECORDS; index++)
            ownerships.add (ownership (String.format ("00000000-0000-0000-0000-%012x", Integer.valueOf (index)), index, index + 1));

        assertThrows (IllegalArgumentException.class, () -> DrumPitchOwnershipStore.Document.encode (ownerships));
    }


    @Test
    void serializesWritesFromAuthoritativeObserverReadBack ()
    {
        final FakeValue value = new FakeValue ();
        final DrumPitchOwnershipStore.Document store = new DrumPitchOwnershipStore.Document (value);
        value.observe ("v1");

        store.save (ownership (TRACK_A, 2, 3));
        store.save (ownership (TRACK_A, 2, 3));

        assertEquals (List.of ("v1|" + TRACK_A + ",2,3,1"), value.requests);
        assertTrue (store.snapshot ().ownerships ().isEmpty ());
        assertThrows (IllegalStateException.class, () -> store.save (ownership (TRACK_B, 6, 7)));

        value.observe (value.requests.getFirst ());
        store.save (ownership (TRACK_B, 6, 7));

        assertEquals (List.of (
            "v1|" + TRACK_A + ",2,3,1",
            "v1|" + TRACK_A + ",2,3,1|" + TRACK_B + ",6,7,1"), value.requests);
        assertEquals (List.of (ownership (TRACK_A, 2, 3)), store.snapshot ().ownerships ());

        value.observe (value.requests.getLast ());
        assertEquals (List.of (ownership (TRACK_A, 2, 3), ownership (TRACK_B, 6, 7)), store.snapshot ().ownerships ());
    }


    private static DrumPitchOwnershipStore.Ownership ownership (final String trackId, final int helperPosition, final int drumPosition)
    {
        return new DrumPitchOwnershipStore.Ownership (trackId, helperPosition, drumPosition, DrumPitchOwnershipStore.CONFIGURATION_VERSION);
    }


    private static final class FakeValue implements DrumPitchOwnershipStore.Document.Value
    {
        private final List<String> requests = new ArrayList<> ();
        private Consumer<String> observer;


        /** {@inheritDoc} */
        @Override
        public void addObserver (final Consumer<String> observer)
        {
            this.observer = observer;
        }


        /** {@inheritDoc} */
        @Override
        public void set (final String text)
        {
            this.requests.add (text);
        }


        private void observe (final String text)
        {
            this.observer.accept (text);
        }
    }
}
