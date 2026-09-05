// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** Raw installed encoder range and user sensitivity preferences; core chooses their meaning. */
public record EncoderConfigurationSnapshot (boolean available, int valueUpperBound, double baseStep, int normalSensitivity, int fineSensitivity)
{
    public EncoderConfigurationSnapshot
    {
        if (!Double.isFinite (baseStep) || baseStep < 0 || valueUpperBound < 0 || normalSensitivity < -100 || normalSensitivity > 100 || fineSensitivity < -100 || fineSensitivity > 100)
            throw new IllegalArgumentException ("Invalid encoder configuration");
        if (available && (valueUpperBound < 2 || baseStep == 0))
            throw new IllegalArgumentException ("Available encoder configuration requires a positive range and step");
        if (!available && (valueUpperBound != 0 || baseStep != 0 || normalSensitivity != 0 || fineSensitivity != 0))
            throw new IllegalArgumentException ("Unavailable encoder configuration cannot contain state");
    }

    public static EncoderConfigurationSnapshot empty ()
    {
        return new EncoderConfigurationSnapshot (false, 0, 0, 0, 0);
    }
}
