// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.util.OptionalDouble;


/**
 * Reconstructs launcher-clip beat position from authoritative launcher and transport read-back.
 *
 * <p>Bitwig API 21 exposes a playing note-grid step but no audio-clip play position; the installed
 * API 25 reference still adds no such value. This tracker therefore publishes a position only after
 * observing the selected track stopped and then an exact clip playing. The stopped state may arrive
 * from either the cursor clip or the private selection-following target. The cursor may retarget to
 * a different scene as that clip starts, so a stopped edge survives only a same-track retarget and
 * is consumed by the first exact playing target. The anchored position advances from later
 * transport samples and fails closed when the track, target, or transport timeline becomes
 * discontinuous.</p>
 */
final class ClipPlaybackPositionTracker
{
    private static final double POSITION_EPSILON = 1.0e-6;
    private static final double MAX_LOOP_WRAP_SAMPLE_DISTANCE = 1.0;

    record TransportClock (boolean playing, double position, boolean loopEnabled, double loopStart, double loopEnd)
    {
    }

    private String trackIdentity = "";
    private String targetIdentity = "";
    private boolean armed;
    private boolean playing;
    private boolean anchored;
    private double clipPosition;
    private double lastTransportPosition;


    /** Reset all playback history. */
    void reset ()
    {
        this.trackIdentity = "";
        this.targetIdentity = "";
        this.armed = false;
        this.playing = false;
        this.anchored = false;
        this.clipPosition = 0;
        this.lastTransportPosition = 0;
    }


    /**
     * Observe one coherent controller-tick sample.
     *
     * @param trackIdentity Stable selected-track identity
     * @param targetIdentity Exact selected launcher-clip identity
     * @param clipPlaying Authoritative launcher-slot playback state
     * @param transport Authoritative transport clock and arranger-loop state
     * @param playStart Clip play start in quarter-note beats
     * @param loopStart Clip loop start in quarter-note beats
     * @param loopLength Clip loop length in quarter-note beats
     * @param loopEnabled Whether clip looping is enabled
     * @return Tracked clip position, or empty until an observable launch establishes its phase
     */
    OptionalDouble observe (final String trackIdentity, final String targetIdentity, final boolean clipPlaying, final TransportClock transport, final double playStart, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        if (!trackIdentity.equals (this.trackIdentity) || !targetIdentity.equals (this.targetIdentity) || clipPlaying != this.playing)
            this.observePlayback (trackIdentity, targetIdentity, clipPlaying, transport.position (), playStart);

        if (this.anchored && clipPlaying && transport.playing ())
        {
            final OptionalDouble elapsed = elapsedBeats (this.lastTransportPosition, transport);
            if (elapsed.isEmpty ())
                this.anchored = false;
            else if (elapsed.getAsDouble () > 0)
                this.clipPosition = advance (this.clipPosition, elapsed.getAsDouble (), loopStart, loopLength, loopEnabled);
        }
        this.lastTransportPosition = transport.position ();

        return this.anchored ? OptionalDouble.of (this.clipPosition) : OptionalDouble.empty ();
    }


    /**
     * Capture a low-rate launcher playback edge even while timeline snapshots are not requested.
     *
     * @param trackIdentity Stable selected-track identity
     * @param targetIdentity Exact launcher clip identity
     * @param clipPlaying New authoritative launcher state
     * @param transportPosition Transport position observed with the edge
     * @param playStart Clip play start observed with the edge
     */
    void observePlayback (final String trackIdentity, final String targetIdentity, final boolean clipPlaying, final double transportPosition, final double playStart)
    {
        if (!trackIdentity.equals (this.trackIdentity))
        {
            this.reset ();
            this.trackIdentity = trackIdentity;
        }

        if (!targetIdentity.equals (this.targetIdentity))
        {
            this.targetIdentity = targetIdentity;
            this.playing = false;
            this.anchored = false;
        }

        if (!clipPlaying)
        {
            this.armed = true;
            this.playing = false;
            this.anchored = false;
            this.lastTransportPosition = transportPosition;
            return;
        }

        if (!this.playing && this.armed)
        {
            this.clipPosition = playStart;
            this.anchored = true;
            this.armed = false;
        }
        this.playing = true;
        this.lastTransportPosition = transportPosition;
    }


    /** Arm the next exact clip start from authoritative selected-track launcher read-back. */
    void observeTrackStopped (final String trackIdentity)
    {
        if (!trackIdentity.equals (this.trackIdentity))
        {
            this.reset ();
            this.trackIdentity = trackIdentity;
        }
        this.targetIdentity = "";
        this.armed = true;
        this.playing = false;
        this.anchored = false;
    }


    private static double advance (final double position, final double elapsed, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        final double advanced = position + elapsed;
        final double loopEnd = loopStart + loopLength;
        if (!loopEnabled || advanced < loopEnd)
            return advanced;
        return loopStart + positiveRemainder (advanced - loopStart, loopLength);
    }


    private static OptionalDouble elapsedBeats (final double previousPosition, final TransportClock transport)
    {
        final double elapsed = transport.position () - previousPosition;
        if (elapsed >= -POSITION_EPSILON)
            return OptionalDouble.of (Math.max (0, elapsed));

        final double loopStart = transport.loopStart ();
        final double loopEnd = transport.loopEnd ();
        if (!transport.loopEnabled () || !Double.isFinite (loopStart) || !Double.isFinite (loopEnd) || loopEnd <= loopStart || previousPosition < loopEnd - MAX_LOOP_WRAP_SAMPLE_DISTANCE || previousPosition > loopEnd + POSITION_EPSILON || transport.position () < loopStart - POSITION_EPSILON || transport.position () > loopStart + MAX_LOOP_WRAP_SAMPLE_DISTANCE)
            return OptionalDouble.empty ();

        return OptionalDouble.of ((loopEnd - previousPosition) + (transport.position () - loopStart));
    }


    private static double positiveRemainder (final double value, final double divisor)
    {
        final double remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }
}
