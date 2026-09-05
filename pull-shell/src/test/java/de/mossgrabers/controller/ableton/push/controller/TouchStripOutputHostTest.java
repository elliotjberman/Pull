// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;
import de.mossgrabers.pull.core.api.output.TouchStripMode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TouchStripOutputHostTest
{
    @Test
    void legacyWritersCannotOverrideCoreAndRelinquishingUsesLatestLegacyState ()
    {
        final List<DesiredTouchStrip> sent = new ArrayList<> ();
        final TouchStripOutputHost host = new TouchStripOutputHost (sent::add);
        host.apply (DesiredTouchStrip.pitchBend (12000), 3);
        host.legacyMode (TouchStripMode.VOLUME);
        host.legacyPosition (2000);
        assertEquals (List.of (DesiredTouchStrip.pitchBend (12000)), sent);
        assertFalse (host.legacyInputEnabled ());

        host.apply (DesiredTouchStrip.unowned (), 3);
        assertEquals (new DesiredTouchStrip (true, TouchStripMode.VOLUME, 2000), sent.getLast ());
        assertTrue (host.legacyInputEnabled ());
    }


    @Test
    void missingOrFailedCoreStaysDarkAndInertDespiteLegacyWrites ()
    {
        final List<DesiredTouchStrip> sent = new ArrayList<> ();
        final TouchStripOutputHost host = new TouchStripOutputHost (sent::add);
        host.legacyMode (TouchStripMode.PITCH_BEND);
        host.legacyPosition (8192);
        assertEquals (List.of (DesiredTouchStrip.off ()), sent);
        assertFalse (host.legacyInputEnabled ());

        host.apply (DesiredTouchStrip.pitchBend (9000), 1);
        host.apply (DesiredTouchStrip.off (), 1);
        host.legacyMode (TouchStripMode.DISCRETE);
        host.legacyPosition (127);
        assertEquals (DesiredTouchStrip.off (), sent.getLast ());
        assertEquals (3, sent.size ());
        assertFalse (host.legacyInputEnabled ());
    }


    @Test
    void generationAndHardwareReconnectReplayWithoutRepeatedUnchangedWrites ()
    {
        final List<DesiredTouchStrip> sent = new ArrayList<> ();
        final TouchStripOutputHost host = new TouchStripOutputHost (sent::add);
        host.apply (DesiredTouchStrip.pitchBend (8192), 1);
        host.apply (DesiredTouchStrip.pitchBend (8192), 1);
        assertEquals (1, sent.size ());
        host.apply (DesiredTouchStrip.pitchBend (8192), 2);
        host.forceFlush ();
        assertEquals (3, sent.size ());
    }


    @Test
    void failedTransmissionIsNotCachedAsApplied ()
    {
        final AtomicBoolean fail = new AtomicBoolean (true);
        final List<DesiredTouchStrip> sent = new ArrayList<> ();
        final TouchStripOutputHost host = new TouchStripOutputHost (output -> {
            if (fail.get ())
                throw new IllegalStateException ("output unavailable");
            sent.add (output);
        });
        assertThrows (IllegalStateException.class, () -> host.apply (DesiredTouchStrip.pitchBend (1), 1));
        fail.set (false);
        host.apply (DesiredTouchStrip.pitchBend (1), 1);
        assertEquals (List.of (DesiredTouchStrip.pitchBend (1)), sent);
    }
}
