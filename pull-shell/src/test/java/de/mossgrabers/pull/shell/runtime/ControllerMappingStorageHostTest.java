// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;
import de.mossgrabers.pull.core.api.effect.SetControllerMappingStorageEffect;

import com.bitwig.extension.callback.StringValueChangedCallback;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Setting;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Storage fakes separate submission, host application, and observer delivery. */
class ControllerMappingStorageHostTest
{
    @Test
    void eagerlyCreatesOneHiddenBoundedSettingButWaitsForActualObservation ()
    {
        final StorageHarness harness = new StorageHarness ();
        final ControllerMappingStorageHost host = harness.create ();

        assertEquals (1, harness.created);
        assertEquals (ControllerMappingStorageSnapshot.MAX_LENGTH, harness.capacity);
        assertEquals (1, harness.hidden);
        assertEquals (1, harness.observers.size ());
        assertFalse (host.snapshot ().available (), "reading an initial value is not observer readiness");

        harness.deliver ();
        final var observed = host.snapshot ();
        assertTrue (observed.available ());
        assertEquals ("document-a", observed.documentId ());
        assertEquals ("", observed.value ());
        assertSame (observed, host.snapshot ());
        assertTrue (host.matches (context (observed)));
        assertFalse (host.matches (ControllerMappingContext.empty ()));
    }


    @Test
    void acceptsRawObservationBeforeDocumentIdentityIsReady ()
    {
        final StorageHarness harness = new StorageHarness ();
        harness.documentId = "";
        final ControllerMappingStorageHost host = harness.create ();
        harness.deliver ();
        assertFalse (host.snapshot ().available ());

        harness.documentId = "document-a";
        assertTrue (host.snapshot ().available (), "identity readiness does not require a synthetic setting callback");
    }


    @Test
    void compareAndSetDoesNotAcknowledgeSubmittedOrUnobservedWrites ()
    {
        final StorageHarness harness = new StorageHarness ();
        final ControllerMappingStorageHost host = harness.create ();
        harness.deliver ();
        final var before = host.snapshot ();
        final var effect = new SetControllerMappingStorageEffect (context (before), "", "opaque core payload");

        assertTrue (host.compareAndSet (effect));
        assertEquals (List.of ("opaque core payload"), harness.submitted);
        assertSame (before, host.snapshot (), "submission must not publish the requested value");
        assertFalse (host.compareAndSet (effect), "one pending write must not be resubmitted before observer delivery");

        harness.currentValue = harness.submitted.getFirst ();
        assertFalse (host.snapshot ().available (), "unobserved host changes cannot reuse the old revision");
        assertFalse (host.matches (context (before)));
        harness.deliver ();
        final var after = host.snapshot ();
        assertEquals ("opaque core payload", after.value ());
        assertTrue (after.revision () > before.revision ());
        assertFalse (host.compareAndSet (effect));
        assertFalse (host.compareAndSet (new SetControllerMappingStorageEffect (context (after), "wrong baseline", "next")));
        assertEquals (1, harness.submitted.size ());
        assertTrue (host.compareAndSet (new SetControllerMappingStorageEffect (context (after), after.value (), "next")));
    }


    @Test
    void redundantReplacementDoesNotWaitForAnObserverThatMayNeverFire ()
    {
        final StorageHarness harness = new StorageHarness ();
        final ControllerMappingStorageHost host = harness.create ();
        harness.deliver ();
        final var before = host.snapshot ();

        assertTrue (host.compareAndSet (new SetControllerMappingStorageEffect (context (before), "", "")));
        assertTrue (harness.submitted.isEmpty ());
        assertSame (before, host.snapshot ());
        assertTrue (host.compareAndSet (new SetControllerMappingStorageEffect (context (before), "", "real change")));
        assertEquals (List.of ("real change"), harness.submitted);
    }


    @Test
    void changingDocumentsFencesExistingRequestsEvenWhenStoredTextIsUnchanged ()
    {
        final StorageHarness harness = new StorageHarness ();
        final ControllerMappingStorageHost host = harness.create ();
        harness.deliver ();
        final var before = host.snapshot ();
        harness.documentId = "document-b";

        final var after = host.snapshot ();
        assertTrue (after.available ());
        assertEquals (before.value (), after.value ());
        assertEquals ("document-b", after.documentId ());
        assertTrue (after.revision () > before.revision ());
        assertFalse (host.compareAndSet (new SetControllerMappingStorageEffect (context (before), "", "foreign")));
        assertTrue (harness.submitted.isEmpty ());
        assertTrue (host.matches (context (after)));
    }


    @Test
    void repeatedActualDeliveryAdvancesRevisionAndMalformedHostBoundsFailClosed ()
    {
        final StorageHarness harness = new StorageHarness ();
        final ControllerMappingStorageHost host = harness.create ();
        harness.currentValue = "not interpreted by stable";
        harness.deliver ();
        final var before = host.snapshot ();
        harness.deliver ();
        assertNotEquals (before.revision (), host.snapshot ().revision ());
        assertEquals (harness.currentValue, host.snapshot ().value ());

        harness.currentValue = "x".repeat (ControllerMappingStorageSnapshot.MAX_LENGTH + 1);
        harness.deliver ();
        assertFalse (host.snapshot ().available ());
        harness.currentValue = null;
        harness.deliver ();
        assertFalse (host.snapshot ().available ());
    }


    private static ControllerMappingContext context (final ControllerMappingStorageSnapshot snapshot)
    {
        return new ControllerMappingContext (1, "selected-track", snapshot.revision (), snapshot.documentId ());
    }


    private static final class StorageHarness
    {
        private String documentId = "document-a";
        private String currentValue = "";
        private int created;
        private int hidden;
        private int capacity;
        private final List<String> submitted = new ArrayList<> ();
        private final List<StringValueChangedCallback> observers = new ArrayList<> ();
        private final SettableStringValue setting = (SettableStringValue) Proxy.newProxyInstance (SettableStringValue.class.getClassLoader (),
            new Class<?> [] {SettableStringValue.class, Setting.class}, (proxy, method, arguments) -> {
                switch (method.getName ())
                {
                    case "hide" -> this.hidden++;
                    case "get" -> { return this.currentValue; }
                    case "set" -> this.submitted.add ((String) arguments[0]);
                    case "addValueObserver" -> this.observers.add ((StringValueChangedCallback) arguments[0]);
                    default -> { }
                }
                return null;
            });


        private ControllerMappingStorageHost create ()
        {
            final DocumentState document = (DocumentState) Proxy.newProxyInstance (DocumentState.class.getClassLoader (), new Class<?> [] {DocumentState.class},
                (proxy, method, arguments) -> {
                    if (method.getName ().equals ("getStringSetting"))
                    {
                        this.created++;
                        this.capacity = (Integer) arguments[2];
                        assertEquals ("", arguments[3]);
                        return this.setting;
                    }
                    return null;
                });
            return new ControllerMappingStorageHost (document, () -> this.documentId);
        }


        private void deliver ()
        {
            this.observers.forEach (observer -> observer.valueChanged (this.currentValue));
        }
    }
}
