// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests for bounded restart-durable exact clip phase anchors. */
class ClipPlaybackPhaseStoreTest
{
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
    void malformedPersistenceFailsClosed ()
    {
        final ClipPlaybackPhaseStore store = new ClipPlaybackPhaseStore ();
        store.replaceFromSerialized ("v1\nnot-base64\tbad\tbad\tbad\tbad\t1\tbad");

        assertTrue (store.restore ("project-a", "track-a|6", 20, 0, 0, 32, true).isEmpty ());
    }
}
