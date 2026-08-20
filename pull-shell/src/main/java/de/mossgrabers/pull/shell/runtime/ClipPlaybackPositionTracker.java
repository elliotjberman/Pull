// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.util.OptionalDouble;


/**
 * Reconstructs launcher-clip beat position from authoritative launcher, transport, and tempo
 * read-back.
 *
 * <p>Bitwig API 21 exposes a playing note-grid step but no audio-clip play position; the installed
 * API 25 reference still adds no such value. This tracker therefore publishes a position only after
 * observing the exact clip stopped and then playing. It advances that anchored position from the
 * shell's monotonic controller clock and subscribed tempo, opportunistically reconciling later
 * transport-position samples. This is necessary because Bitwig's interested transport position is
 * authoritative when it changes but does not continuously publish during launcher playback. The
 * tracker fails closed when the target, transport position, or shell clock becomes
 * discontinuous.</p>
 */
final class ClipPlaybackPositionTracker
{
    private static final double POSITION_EPSILON = 1.0e-6;

    private String targetIdentity = "";
    private boolean armed;
    private boolean playing;
    private boolean anchored;
    private double clipPosition;
    private double lastObservedTransportPosition;
    private double estimatedTransportPosition;
    private long lastMonotonicTimeNanos;
    private boolean clockInitialized;


    /** Reset all playback history. */
    void reset ()
    {
        this.targetIdentity = "";
        this.armed = false;
        this.playing = false;
        this.anchored = false;
        this.clipPosition = 0;
        this.lastObservedTransportPosition = 0;
        this.estimatedTransportPosition = 0;
        this.lastMonotonicTimeNanos = 0;
        this.clockInitialized = false;
    }


    /**
     * Observe one coherent controller-tick sample.
     *
     * @param identity Exact selected launcher-clip identity
     * @param clipPlaying Authoritative launcher-slot playback state
     * @param transportPlaying Authoritative transport playback state
     * @param transportPosition Current transport position in quarter-note beats
     * @param tempo Current authoritative tempo in quarter-note beats per minute
     * @param monotonicTimeNanos Current shell-monotonic controller time
     * @param playStart Clip play start in quarter-note beats
     * @param loopStart Clip loop start in quarter-note beats
     * @param loopLength Clip loop length in quarter-note beats
     * @param loopEnabled Whether clip looping is enabled
     * @return Tracked clip position, or empty until an observable launch establishes its phase
     */
    OptionalDouble observe (final String identity, final boolean clipPlaying, final boolean transportPlaying, final double transportPosition, final double tempo, final long monotonicTimeNanos, final double playStart, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        if (!identity.equals (this.targetIdentity) || clipPlaying != this.playing)
            this.observePlayback (identity, clipPlaying, transportPosition, playStart);

        if (this.anchored && clipPlaying)
        {
            if (!Double.isFinite (transportPosition) || transportPosition < 0 || monotonicTimeNanos < 0 || transportPosition < this.lastObservedTransportPosition - POSITION_EPSILON)
                this.anchored = false;
            else if (!this.clockInitialized)
                this.initializeClock (transportPlaying, transportPosition, monotonicTimeNanos, loopStart, loopLength, loopEnabled);
            else if (monotonicTimeNanos < this.lastMonotonicTimeNanos || transportPlaying && (!Double.isFinite (tempo) || tempo <= 0))
                this.anchored = false;
            else
                this.advanceClock (transportPlaying, transportPosition, tempo, monotonicTimeNanos, loopStart, loopLength, loopEnabled);
        }

        return this.anchored ? OptionalDouble.of (this.clipPosition) : OptionalDouble.empty ();
    }


    /**
     * Capture a low-rate launcher playback edge even while timeline snapshots are not requested.
     *
     * @param identity Exact launcher clip identity
     * @param clipPlaying New authoritative launcher state
     * @param transportPosition Transport position observed with the edge
     * @param playStart Clip play start observed with the edge
     */
    void observePlayback (final String identity, final boolean clipPlaying, final double transportPosition, final double playStart)
    {
        if (!identity.equals (this.targetIdentity))
        {
            this.reset ();
            this.targetIdentity = identity;
        }

        if (!clipPlaying)
        {
            this.armed = true;
            this.playing = false;
            this.anchored = false;
            this.lastObservedTransportPosition = transportPosition;
            this.estimatedTransportPosition = transportPosition;
            this.clockInitialized = false;
            return;
        }

        if (!this.playing && this.armed)
        {
            this.clipPosition = playStart;
            this.anchored = true;
            this.clockInitialized = false;
        }
        this.playing = true;
        this.lastObservedTransportPosition = transportPosition;
        this.estimatedTransportPosition = transportPosition;
    }


    private void initializeClock (final boolean transportPlaying, final double transportPosition, final long monotonicTimeNanos, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        if (transportPlaying)
        {
            final double observedAdvance = transportPosition - this.lastObservedTransportPosition;
            if (observedAdvance > 0)
                this.clipPosition = advance (this.clipPosition, observedAdvance, loopStart, loopLength, loopEnabled);
        }
        this.lastObservedTransportPosition = transportPosition;
        this.estimatedTransportPosition = transportPosition;
        this.lastMonotonicTimeNanos = monotonicTimeNanos;
        this.clockInitialized = true;
    }


    private void advanceClock (final boolean transportPlaying, final double transportPosition, final double tempo, final long monotonicTimeNanos, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        if (transportPlaying)
        {
            final double observedAdvance = transportPosition - this.lastObservedTransportPosition;
            final double elapsedBeats = (monotonicTimeNanos - this.lastMonotonicTimeNanos) / 60_000_000_000.0 * tempo;
            final double positionAdvance;
            if (observedAdvance > POSITION_EPSILON)
            {
                positionAdvance = Math.max (0, transportPosition - this.estimatedTransportPosition);
                this.estimatedTransportPosition = Math.max (this.estimatedTransportPosition, transportPosition);
            }
            else
            {
                positionAdvance = elapsedBeats;
                this.estimatedTransportPosition += elapsedBeats;
            }
            if (positionAdvance > 0)
                this.clipPosition = advance (this.clipPosition, positionAdvance, loopStart, loopLength, loopEnabled);
        }
        else
            this.estimatedTransportPosition = transportPosition;

        this.lastObservedTransportPosition = transportPosition;
        this.lastMonotonicTimeNanos = monotonicTimeNanos;
    }


    private static double advance (final double position, final double elapsed, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        final double advanced = position + elapsed;
        final double loopEnd = loopStart + loopLength;
        if (!loopEnabled || advanced < loopEnd)
            return advanced;
        return loopStart + positiveRemainder (advanced - loopStart, loopLength);
    }


    private static double positiveRemainder (final double value, final double divisor)
    {
        final double remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }
}
