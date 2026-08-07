// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Common authoritative transport state.
 *
 * @param available True when the stable shell exposes transport state
 * @param engineActive True when the current project's audio engine is active
 * @param playing Transport playback state
 * @param recording Arranger record state
 * @param arrangerOverdub Arranger overdub state
 * @param launcherOverdub Clip-launcher overdub state
 * @param loopEnabled Arranger loop state
 * @param metronomeEnabled Metronome state
 * @param fillModeEnabled Global fill-mode state
 * @param tempo Tempo in beats per minute
 * @param positionBeats Transport position in beats
 * @param numerator Time-signature numerator, or zero while unavailable
 * @param denominator Time-signature denominator, or zero while unavailable
 */
public record TransportSnapshot (boolean available, boolean engineActive, boolean playing, boolean recording, boolean arrangerOverdub, boolean launcherOverdub, boolean loopEnabled, boolean metronomeEnabled, boolean fillModeEnabled, double tempo, double positionBeats, int numerator, int denominator)
{
    private static final TransportSnapshot EMPTY = new TransportSnapshot (false, false, false, false, false, false, false, false, false, 0, 0, 0, 0);


    /**
     * Validate transport values.
     */
    public TransportSnapshot
    {
        requireFiniteNonNegative (tempo, "tempo");
        requireFiniteNonNegative (positionBeats, "positionBeats");
        if (numerator < 0)
            throw new IllegalArgumentException ("numerator must not be negative");
        if (denominator < 0)
            throw new IllegalArgumentException ("denominator must not be negative");
        if (available && (tempo <= 0 || numerator <= 0 || denominator <= 0))
            throw new IllegalArgumentException ("available transport must have positive tempo and time signature");
    }


    /**
     * Get unavailable transport state.
     *
     * @return Empty transport state
     */
    public static TransportSnapshot empty ()
    {
        return EMPTY;
    }


    private static void requireFiniteNonNegative (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0)
            throw new IllegalArgumentException (name + " must be finite and not negative");
    }
}
