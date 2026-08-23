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

        assertTrue (tracker.observe ("track-a", "clip-a", true, transport (true, 16), noStep (), 2, 2, 8, true).isEmpty ());
        assertTrue (tracker.observe ("track-a", "clip-a", false, transport (true, 18), noStep (), 2, 2, 8, true).isEmpty ());
        assertEquals (OptionalDouble.of (2), tracker.observe ("track-a", "clip-a", true, transport (true, 20), noStep (), 2, 2, 8, true));
        assertEquals (OptionalDouble.of (3.5), tracker.observe ("track-a", "clip-a", true, transport (true, 21.5), noStep (), 2, 2, 8, true));
    }


    @Test
    void wrapsTheTrackedPositionThroughTheAuthoritativeLoop ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("track-a", "clip-a", false, transport (true, 0), noStep (), 0, 0, 8, true);
        tracker.observe ("track-a", "clip-a", true, transport (true, 16), noStep (), 0, 0, 8, true);

        assertEquals (OptionalDouble.of (0.5), tracker.observe ("track-a", "clip-a", true, transport (true, 24.5), noStep (), 0, 0, 8, true));
    }


    @Test
    void holdsPositionWhileTransportIsPaused ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("track-a", "clip-a", false, transport (false, 4), noStep (), 0, 0, 8, true);
        tracker.observe ("track-a", "clip-a", true, transport (true, 8), noStep (), 0, 0, 8, true);

        assertEquals (OptionalDouble.of (1), tracker.observe ("track-a", "clip-a", true, transport (true, 9), noStep (), 0, 0, 8, true));
        assertEquals (OptionalDouble.of (1), tracker.observe ("track-a", "clip-a", true, transport (false, 9), noStep (), 0, 0, 8, true));
        assertEquals (OptionalDouble.of (2), tracker.observe ("track-a", "clip-a", true, transport (true, 10), noStep (), 0, 0, 8, true));
    }


    @Test
    void trackChangeAndBackwardTransportDiscontinuityFailClosed ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observe ("track-a", "clip-a", false, transport (true, 0), noStep (), 0, 0, 8, true);
        tracker.observe ("track-a", "clip-a", true, transport (true, 4), noStep (), 0, 0, 8, true);

        assertTrue (tracker.observe ("track-a", "clip-a", true, transport (true, 3), noStep (), 0, 0, 8, true).isEmpty ());
        assertTrue (tracker.phaseInvalidated ());
        assertTrue (tracker.observe ("track-b", "clip-b", true, transport (true, 5), noStep (), 0, 0, 8, true).isEmpty ());
    }


    @Test
    void observedPlaybackEdgeCanAnchorBeforePositionSamplingBegins ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observePlayback ("track-a", "clip-a", false, 12, 0);
        tracker.observePlayback ("track-a", "clip-a", true, 16, 0);

        assertEquals (OptionalDouble.of (4), tracker.observe ("track-a", "clip-a", true, transport (true, 20), noStep (), 0, 0, 8, true));
    }


    @Test
    void stoppedEdgeSurvivesSameTrackSceneRetargetAtLaunch ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observePlayback ("track-a", "track-a|2", false, 12, 0);
        tracker.observePlayback ("track-a", "track-a|6", true, 16, 4);

        assertEquals (OptionalDouble.of (5), tracker.observe ("track-a", "track-a|6", true, transport (true, 17), noStep (), 4, 0, 8, true));
        assertTrue (tracker.observe ("track-a", "track-a|7", true, transport (true, 18), noStep (), 0, 0, 8, true).isEmpty (), "the stopped edge is consumed by the first exact playing target");
    }


    @Test
    void stalePlayingIdentityRetainsTheLaunchEdgeForTheExactRetargetedClip ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observeTrackStopped ("track-a");
        tracker.observePlayback ("track-a", "track-a|2", true, 63.9, 0);

        assertEquals (0.1, tracker.observe ("track-a", "track-a|2", true, loopingTransport (32, 32, 64), noStep (), 0, 0, 8, true).orElseThrow (), 1.0e-9);
        assertEquals (4.2, tracker.observe ("track-a", "track-a|6", true, loopingTransport (32.1, 32, 64), noStep (), 4, 0, 8, true).orElseThrow (), 1.0e-9);
        assertEquals (4.3, tracker.observe ("track-a", "track-a|6", true, loopingTransport (32.2, 32, 64), noStep (), 4, 0, 8, true).orElseThrow (), 1.0e-9);
    }


    @Test
    void selectedTrackStoppedReadbackArmsTheNextExactClipSample ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observeTrackStopped ("track-a");

        assertEquals (OptionalDouble.of (2), tracker.observe ("track-a", "track-a|6", true, transport (true, 20), noStep (), 2, 2, 8, true));
    }


    @Test
    void advancesAcrossAnObservedArrangerLoopWrap ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observeTrackStopped ("track-a");
        tracker.observe ("track-a", "clip-a", true, loopingTransport (63.9, 32, 64), noStep (), 2, 0, 8, true);

        assertEquals (2.2, tracker.observe ("track-a", "clip-a", true, loopingTransport (32.1, 32, 64), noStep (), 2, 0, 8, true).orElseThrow (), 1.0e-9);
    }


    @Test
    void unrelatedBackwardJumpStillFailsClosedWhenArrangerLoopIsEnabled ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();
        tracker.observeTrackStopped ("track-a");
        tracker.observe ("track-a", "clip-a", true, loopingTransport (48, 32, 64), noStep (), 2, 0, 8, true);

        assertTrue (tracker.observe ("track-a", "clip-a", true, loopingTransport (40, 32, 64), noStep (), 2, 0, 8, true).isEmpty ());
    }


    @Test
    void validPlayingStepAnchorsAnAlreadyPlayingClipAfterTransportResumes ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();

        assertTrue (tracker.observe ("track-a", "clip-a", true, transport (false, 20), step (16), 0, 0, 32, true).isEmpty ());
        assertEquals (OptionalDouble.of (16), tracker.observe ("track-a", "clip-a", true, transport (true, 20), step (16), 0, 0, 32, true));
        assertEquals (OptionalDouble.of (16.5), tracker.observe ("track-a", "clip-a", true, transport (true, 20.5), step (16.5), 0, 0, 32, true));
    }


    @Test
    void retainedExactPhaseAnchorsWhenAudioPlayingStepIsUnavailable ()
    {
        final ClipPlaybackPositionTracker tracker = new ClipPlaybackPositionTracker ();

        assertEquals (OptionalDouble.of (16), tracker.observe ("track-a", "clip-a", true, transport (true, 20), noStep (), step (16), 0, 0, 32, true));
        assertEquals (OptionalDouble.of (16.5), tracker.observe ("track-a", "clip-a", true, transport (true, 20.5), noStep (), step (16.5), 0, 0, 32, true));
    }


    private static OptionalDouble noStep ()
    {
        return OptionalDouble.empty ();
    }


    private static OptionalDouble step (final double position)
    {
        return OptionalDouble.of (position);
    }


    private static ClipPlaybackPositionTracker.TransportClock transport (final boolean playing, final double position)
    {
        return new ClipPlaybackPositionTracker.TransportClock (playing, position, false, 0, 0);
    }


    private static ClipPlaybackPositionTracker.TransportClock loopingTransport (final double position, final double loopStart, final double loopEnd)
    {
        return new ClipPlaybackPositionTracker.TransportClock (true, position, true, loopStart, loopEnd);
    }
}
