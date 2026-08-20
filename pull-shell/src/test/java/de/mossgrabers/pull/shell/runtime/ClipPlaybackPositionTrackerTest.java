// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Unit tests for fail-closed audio-clip playback phase reconstruction. */
class ClipPlaybackPositionTrackerTest
{
    private static final double TEMPO = 120;


    @Test
    void anchorsOnlyAfterAnObservedStoppedToPlayingTransition ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();

        assertTrue (tracker.observe ("clip-a", true, true, 16, TEMPO, 0, 2, 2, 8, true).isEmpty ());
        assertTrue (tracker.observe ("clip-a", false, true, 18, TEMPO, 1, 2, 2, 8, true).isEmpty ());
        assertEquals (OptionalDouble.of (2), tracker.observe ("clip-a", true, true, 20, TEMPO, 2, 2, 2, 8, true));
        assertEquals (OptionalDouble.of (3.5), tracker.observe ("clip-a", true, true, 20, TEMPO, 750_000_002, 2, 2, 8, true));
    }


    @Test
    void wrapsTheTrackedPositionThroughTheAuthoritativeLoop ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("clip-a", false, true, 0, TEMPO, 0, 0, 0, 8, true);
        tracker.observe ("clip-a", true, true, 16, TEMPO, 1, 0, 0, 8, true);

        assertEquals (OptionalDouble.of (0.5), tracker.observe ("clip-a", true, true, 16, TEMPO, 4_250_000_001L, 0, 0, 8, true));
    }


    @Test
    void holdsPositionWhileTransportIsPaused ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("clip-a", false, false, 4, TEMPO, 0, 0, 0, 8, true);
        tracker.observe ("clip-a", true, true, 8, TEMPO, 1, 0, 0, 8, true);

        assertEquals (OptionalDouble.of (1), tracker.observe ("clip-a", true, true, 8, TEMPO, 500_000_001, 0, 0, 8, true));
        assertEquals (OptionalDouble.of (1), tracker.observe ("clip-a", true, false, 8, TEMPO, 1_000_000_001, 0, 0, 8, true));
        assertEquals (OptionalDouble.of (2), tracker.observe ("clip-a", true, true, 8, TEMPO, 1_500_000_001, 0, 0, 8, true));
    }


    @Test
    void targetChangeAndBackwardTransportDiscontinuityFailClosed ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("clip-a", false, true, 0, TEMPO, 0, 0, 0, 8, true);
        tracker.observe ("clip-a", true, true, 4, TEMPO, 1, 0, 0, 8, true);

        assertTrue (tracker.observe ("clip-a", true, true, 3, TEMPO, 2, 0, 0, 8, true).isEmpty ());
        assertTrue (tracker.observe ("clip-b", true, true, 5, TEMPO, 3, 0, 0, 8, true).isEmpty ());
    }


    @Test
    void observedPlaybackEdgeCanAnchorBeforePositionSamplingBegins ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observePlayback ("clip-a", false, 12, 0);
        tracker.observePlayback ("clip-a", true, 16, 0);

        assertEquals (OptionalDouble.of (4), tracker.observe ("clip-a", true, true, 20, TEMPO, 1, 0, 0, 8, true));
    }


    @Test
    void staleInterestedTransportPositionAdvancesFromMonotonicTempoClock ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observePlayback ("clip-a", false, 12, 0);
        tracker.observePlayback ("clip-a", true, 16, 0);

        assertEquals (OptionalDouble.of (0), tracker.observe ("clip-a", true, true, 16, TEMPO, 1, 0, 0, 8, true));
        assertEquals (OptionalDouble.of (0.5), tracker.observe ("clip-a", true, true, 16, TEMPO, 250_000_001, 0, 0, 8, true));
    }
}
