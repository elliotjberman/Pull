// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwAbsoluteControl;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingNames;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** Verifies name-write requests, not native browser rendering or learned-binding acknowledgement. */
class ControllerMappingNamesHostTest
{
    private static final String DOCUMENT = "11111111-1111-1111-1111-111111111111";
    private static final ControllerMappingId FIRST = new ControllerMappingId ("first");
    private static final ControllerMappingId SECOND = new ControllerMappingId ("second");


    @Test
    void changesOnlyUnequalNamesOnTheExistingControls ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.request (names (1, Map.of (FIRST, "Drums Toggle 1", SECOND, "Bass Toggle 1")));
        fixture.host.request (names (1, Map.of (FIRST, "Renamed Drums Toggle 1", SECOND, "Bass Toggle 1")));

        assertEquals (List.of ("Drums Toggle 1", "Renamed Drums Toggle 1"), fixture.first.writes);
        assertEquals (List.of ("Bass Toggle 1"), fixture.second.writes);
    }


    @Test
    void unchangedNamesDoNotResubmitOnReplayOrRevisionChange ()
    {
        final Fixture fixture = new Fixture ();
        final Map<ControllerMappingId, String> labels = Map.of (FIRST, "Drums Toggle 1");
        fixture.host.request (names (1, labels));
        fixture.host.request (names (1, labels));
        fixture.storage.set (storage (DOCUMENT, 2));
        fixture.host.request (names (2, labels));

        assertEquals (List.of ("Drums Toggle 1"), fixture.first.writes);
        assertEquals (List.of (), fixture.second.writes);
    }


    @Test
    void omittedAndEmptyNamesResetOnlyPreviouslyNamedControls ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.request (names (1, Map.of (FIRST, "Drums Toggle 1", SECOND, "Bass Toggle 1")));
        fixture.host.request (names (1, Map.of (SECOND, "Bass Toggle 1")));

        assertEquals (List.of ("Drums Toggle 1", ""), fixture.first.writes);
        assertEquals (List.of ("Bass Toggle 1"), fixture.second.writes);

        fixture.host.request (ControllerMappingNames.empty ());
        fixture.host.request (ControllerMappingNames.empty ());

        assertEquals (List.of ("Drums Toggle 1", ""), fixture.first.writes);
        assertEquals (List.of ("Bass Toggle 1", ""), fixture.second.writes);
    }


    @Test
    void rejectsUnknownEndpointsBeforeChangingAnyNames ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.request (names (1, Map.of (FIRST, "Drums Toggle 1")));

        assertThrows (IllegalArgumentException.class, () -> fixture.host.request (
            names (2, Map.of (SECOND, "Bass Toggle 1", new ControllerMappingId ("missing"), "Missing"))));

        assertEquals (List.of ("Drums Toggle 1"), fixture.first.writes);
        assertEquals (List.of (), fixture.second.writes);
    }


    @Test
    void refreshResetsNamesWhenRevisionDocumentOrAvailabilityChanges ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.request (names (1, Map.of (FIRST, "Drums Toggle 1")));
        fixture.storage.set (storage (DOCUMENT, 2));
        fixture.host.refresh ();
        fixture.host.refresh ();
        assertEquals (List.of ("Drums Toggle 1", ""), fixture.first.writes);

        fixture.host.request (names (2, Map.of (FIRST, "Drums Toggle 1")));
        fixture.storage.set (storage ("22222222-2222-2222-2222-222222222222", 2));
        fixture.host.refresh ();
        assertEquals (List.of ("Drums Toggle 1", "", "Drums Toggle 1", ""), fixture.first.writes);

        fixture.storage.set (storage (DOCUMENT, 3));
        fixture.host.request (names (3, Map.of (FIRST, "Drums Toggle 1")));
        fixture.storage.set (ControllerMappingStorageSnapshot.empty ());
        fixture.host.refresh ();
        assertEquals (List.of ("Drums Toggle 1", "", "Drums Toggle 1", "", "Drums Toggle 1", ""), fixture.first.writes);
    }


    @Test
    void requestedNamesWaitForTheirObservedStorageContext ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.request (names (2, Map.of (FIRST, "Drums Toggle 1")));
        assertEquals (List.of (), fixture.first.writes);

        fixture.storage.set (storage (DOCUMENT, 2));
        fixture.host.refresh ();
        assertEquals (List.of ("Drums Toggle 1"), fixture.first.writes);
    }


    @Test
    void emptyNamesDoNotSampleStorage ()
    {
        final AtomicInteger reads = new AtomicInteger ();
        final ControllerMappingNamesHost host = new ControllerMappingNamesHost (Map.of (), () -> {
            reads.incrementAndGet ();
            return ControllerMappingStorageSnapshot.empty ();
        });
        host.refresh ();
        host.request (ControllerMappingNames.empty ());
        host.refresh ();
        assertEquals (0, reads.get ());
    }


    private static ControllerMappingNames names (final long revision, final Map<ControllerMappingId, String> names)
    {
        return new ControllerMappingNames (DOCUMENT, revision, names);
    }


    private static ControllerMappingStorageSnapshot storage (final String document, final long revision)
    {
        return new ControllerMappingStorageSnapshot (true, revision, document, "opaque");
    }


    private static final class Fixture
    {
        private final Control first = new Control ();
        private final Control second = new Control ();
        private final AtomicReference<ControllerMappingStorageSnapshot> storage = new AtomicReference<> (storage (DOCUMENT, 1));
        private final ControllerMappingNamesHost host = new ControllerMappingNamesHost (Map.of (FIRST, this.first.control, SECOND, this.second.control), this.storage::get);
    }


    private static final class Control
    {
        private final List<String> writes = new ArrayList<> ();
        private final IHwAbsoluteControl control = (IHwAbsoluteControl) Proxy.newProxyInstance (
            IHwAbsoluteControl.class.getClassLoader (), new Class<?> [] {IHwAbsoluteControl.class}, (proxy, method, arguments) -> {
                if (!method.getName ().equals ("setName"))
                    throw new AssertionError ("Name changes must not operate on mapping bindings: " + method.getName ());
                this.writes.add ((String) arguments[0]);
                return null;
            });
    }
}
