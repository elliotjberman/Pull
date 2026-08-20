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
    @Test
    void anchorsOnlyAfterAnObservedStoppedToPlayingTransition ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();

        assertTrue (tracker.observe ("track-a", "clip-a", true, true, 16, 2, 2, 8, true).isEmpty ());
        assertTrue (tracker.observe ("track-a", "clip-a", false, true, 18, 2, 2, 8, true).isEmpty ());
        assertEquals (OptionalDouble.of (2), tracker.observe ("track-a", "clip-a", true, true, 20, 2, 2, 8, true));
        assertEquals (OptionalDouble.of (3.5), tracker.observe ("track-a", "clip-a", true, true, 21.5, 2, 2, 8, true));
    }


    @Test
    void wrapsTheTrackedPositionThroughTheAuthoritativeLoop ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("track-a", "clip-a", false, true, 0, 0, 0, 8, true);
        tracker.observe ("track-a", "clip-a", true, true, 16, 0, 0, 8, true);

        assertEquals (OptionalDouble.of (0.5), tracker.observe ("track-a", "clip-a", true, true, 24.5, 0, 0, 8, true));
    }


    @Test
    void holdsPositionWhileTransportIsPaused ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("track-a", "clip-a", false, false, 4, 0, 0, 8, true);
        tracker.observe ("track-a", "clip-a", true, true, 8, 0, 0, 8, true);

        assertEquals (OptionalDouble.of (1), tracker.observe ("track-a", "clip-a", true, true, 9, 0, 0, 8, true));
        assertEquals (OptionalDouble.of (1), tracker.observe ("track-a", "clip-a", true, false, 9, 0, 0, 8, true));
        assertEquals (OptionalDouble.of (2), tracker.observe ("track-a", "clip-a", true, true, 10, 0, 0, 8, true));
    }


    @Test
    void trackChangeAndBackwardTransportDiscontinuityFailClosed ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("track-a", "clip-a", false, true, 0, 0, 0, 8, true);
        tracker.observe ("track-a", "clip-a", true, true, 4, 0, 0, 8, true);

        assertTrue (tracker.observe ("track-a", "clip-a", true, true, 3, 0, 0, 8, true).isEmpty ());
        assertTrue (tracker.observe ("track-b", "clip-b", true, true, 5, 0, 0, 8, true).isEmpty ());
    }


    @Test
    void observedPlaybackEdgeCanAnchorBeforePositionSamplingBegins ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observePlayback ("track-a", "clip-a", false, 12, 0);
        tracker.observePlayback ("track-a", "clip-a", true, 16, 0);

        assertEquals (OptionalDouble.of (4), tracker.observe ("track-a", "clip-a", true, true, 20, 0, 0, 8, true));
    }


    @Test
    void stoppedEdgeSurvivesSameTrackSceneRetargetAtLaunch ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observePlayback ("track-a", "track-a|2", false, 12, 0);
        tracker.observePlayback ("track-a", "track-a|6", true, 16, 4);

        assertEquals (OptionalDouble.of (5), tracker.observe ("track-a", "track-a|6", true, true, 17, 4, 0, 8, true));
        assertTrue (tracker.observe ("track-a", "track-a|7", true, true, 18, 0, 0, 8, true).isEmpty (), "the stopped edge is consumed by the first exact playing target");
    }
}
