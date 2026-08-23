// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Setting;
import com.bitwig.extension.callback.StringValueChangedCallback;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests for bounded restart-durable exact clip phase anchors. */
class ClipPlaybackPhaseStoreTest
{
    @Test
    void persistenceResourceIsInstalledExplicitlyAndOnlyOnce ()
    {
        final AtomicInteger settingsCreated = new AtomicInteger ();
        final AtomicInteger writes = new AtomicInteger ();
        final SettableStringValue setting = (SettableStringValue) Proxy.newProxyInstance (
            SettableStringValue.class.getClassLoader (),
            new Class []
            {
                SettableStringValue.class,
                Setting.class
            },
            (proxy, method, arguments) -> {
                if ("addValueObserver".equals (method.getName ()))
                    ((StringValueChangedCallback) arguments[0]).valueChanged ("v1");
                else if ("set".equals (method.getName ()))
                    writes.incrementAndGet ();
                return defaultValue (method.getReturnType ());
            });
        final Preferences preferences = proxy (Preferences.class, (proxy, method, arguments) -> {
            if ("getStringSetting".equals (method.getName ()))
            {
                settingsCreated.incrementAndGet ();
                return setting;
            }
            return defaultValue (method.getReturnType ());
        });
        final ControllerHost host = proxy (ControllerHost.class, (proxy, method, arguments) -> "getPreferences".equals (method.getName ()) ? preferences : defaultValue (method.getReturnType ()));
        final ClipPlaybackPhaseStore store = new ClipPlaybackPhaseStore ();

        assertEquals (0, settingsCreated.get ());
        store.installPersistence (host);
        store.remember ("project-a", "track-a|6", 20, 16, 0, 0, 32, true);

        assertEquals (1, settingsCreated.get ());
        assertEquals (1, writes.get ());
        assertThrows (IllegalStateException.class, () -> store.installPersistence (host));
    }


    @Test
    void serializedPhaseRestoresOnlyForTheExactProjectTargetAndGeometry ()
    {
        final ClipPlaybackPhaseStore first = new ClipPlaybackPhaseStore ();
        first.remember ("project-id\u001fproject-name", "track-a|6", 20, 16, 0, 0, 32, true);

        final ClipPlaybackPhaseStore restored = new ClipPlaybackPhaseStore ();
        restored.replaceFromSerialized (first.serialized ());

        assertEquals (OptionalDouble.of (16.5), restored.restore ("project-id\u001fproject-name", "track-a|6", 20.5, 0, 0, 32, true));
        assertTrue (restored.restore ("project-b", "track-a|6", 20.5, 0, 0, 32, true).isEmpty ());
        assertTrue (restored.restore ("project-a", "track-a|7", 20.5, 0, 0, 32, true).isEmpty ());
        assertTrue (restored.restore ("project-a", "track-a|6", 20.5, 0, 0, 16, true).isEmpty ());
    }


    @Test
    void loopingPhaseWrapsAndAuthoritativeStopInvalidatesIt ()
    {
        final ClipPlaybackPhaseStore store = new ClipPlaybackPhaseStore ();
        store.remember ("project-a", "track-a|6", 20, 28, 0, 0, 32, true);

        assertEquals (OptionalDouble.of (2), store.restore ("project-a", "track-a|6", 26, 0, 0, 32, true));
        store.invalidate ("project-a", "track-a|6");
        assertTrue (store.restore ("project-a", "track-a|6", 26, 0, 0, 32, true).isEmpty ());
    }


    @Test
    void arrangerLoopWrapKeepsRetainedClipPhaseCoherentWhileTargetIsHidden ()
    {
        final ClipPlaybackPhaseStore store = new ClipPlaybackPhaseStore ();
        store.remember ("project-a", "track-a|6", 63.8, 31.8, 0, 0, 32, true);
        store.observeTransport ("project-a", true, 63.8, true, 32, 64);
        store.observeTransport ("project-a", true, 32.2, true, 32, 64);

        assertEquals (0.2, store.restore ("project-a", "track-a|6", 32.2, 0, 0, 32, true).orElseThrow (), 1.0e-9);
    }


    @Test
    void nonLoopingBackwardTransportDiscontinuityInvalidatesCurrentProjectOnly ()
    {
        final ClipPlaybackPhaseStore store = new ClipPlaybackPhaseStore ();
        store.remember ("project-a", "track-a|6", 20, 16, 0, 0, 32, true);
        store.remember ("project-b", "track-b|2", 20, 16, 0, 0, 32, true);
        store.observeTransport ("project-a", true, 20, false, 0, 0);
        store.observeTransport ("project-a", true, 10, false, 0, 0);

        assertTrue (store.restore ("project-a", "track-a|6", 10, 0, 0, 32, true).isEmpty ());
        assertEquals (OptionalDouble.of (16), store.restore ("project-b", "track-b|2", 20, 0, 0, 32, true));
    }


    @Test
    void malformedPersistenceFailsClosed ()
    {
        final ClipPlaybackPhaseStore store = new ClipPlaybackPhaseStore ();
        store.replaceFromSerialized ("v1\nnot-base64\tbad\tbad\tbad\tbad\t1\tbad");

        assertTrue (store.restore ("project-a", "track-a|6", 20, 0, 0, 32, true).isEmpty ());
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class []
        {
            type
        }, handler));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive () || type == void.class)
            return null;
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == char.class)
            return Character.valueOf ('\0');
        return Integer.valueOf (0);
    }
}
