// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Value-only drawing for the bounded remote-project playback ripple. Timing stays with its owner. */
public final class PlaybackRippleRenderer
{
    private static final double PAD_MAX_DISTANCE = Math.hypot (7, 7);
    private static final double PAD_TRAIL_WIDTH = 2.2;
    private static final double PAD_FRONT_WIDTH = 0.5;
    private static final int DISPLAY_WIDTH = 960;
    private static final int DISPLAY_HEIGHT = 160;
    private static final int DISPLAY_PIXEL_WIDTH = 24;
    private static final int DISPLAY_PIXEL_HEIGHT = 20;
    private static final double DISPLAY_NOISE_EXPONENT = 1.35;
    private static final double DISPLAY_TRAIL_WIDTH = 3.5;
    private static final double DISPLAY_FRONT_WIDTH = 0.65;

    private static final RgbColor OFF = new RgbColor (0, 0, 0);

    private PlaybackRippleRenderer () { }

    /** The complete eight-by-eight overlay, including its black background. */
    public static Map<PadGridPosition, RgbColor> pads (final double progress, final RgbColor color)
    {
        validate (progress, color);
        final Map<PadGridPosition, RgbColor> colors = maskedGrid ();
        if (progress < 1) addRipple (colors, progress, color);
        return Map.copyOf (colors);
    }

    /** The complete display overlay; progress one clears the last animation frame. */
    public static ControllerDisplayScene display (final double progress, final RgbColor color, final long seed)
    {
        validate (progress, color);
        final List<DisplayCommand> commands = new ArrayList<> (321);
        commands.add (new DisplayCommand.Rectangle (0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, OFF));
        if (progress < 1) addDisplayRipple (commands, progress, color, Long.hashCode (seed));
        return new ControllerDisplayScene (DISPLAY_WIDTH, DISPLAY_HEIGHT, commands);
    }

    private static void validate (final double progress, final RgbColor color)
    {
        Objects.requireNonNull (color, "color");
        if (!Double.isFinite (progress) || progress < 0 || progress > 1)
            throw new IllegalArgumentException ("Ripple progress must be normalized");
    }

    private static void addRipple (final Map<PadGridPosition, RgbColor> colors, final double progress, final RgbColor baseColor)
    {
        final double easedProgress = cubicBezier (progress, 0.15, 0.85);
        final double radius = easedProgress * (PAD_MAX_DISTANCE + PAD_TRAIL_WIDTH);
        final double endFade = 1.0 - 0.35 * smoothStep (clamp ((progress - 0.7) / 0.3));
        for (int row = 0; row < 8; row++)
        {
            for (int column = 0; column < 8; column++)
            {
                final double distance = Math.hypot (column, row);
                final double intensity = rippleIntensity (radius, distance, PAD_TRAIL_WIDTH, PAD_FRONT_WIDTH) *
                    (1.0 - 0.45 * distance / PAD_MAX_DISTANCE) * endFade;
                if (intensity >= 0.025)
                    colors.put (new PadGridPosition (column, row), shade (baseColor, intensity));
            }
        }
    }


    private static void addDisplayRipple (final List<DisplayCommand> commands, final double progress, final RgbColor baseColor, final int seed)
    {
        final double easedProgress = cubicBezier (progress, 0.15, 0.85);
        final double radius = easedProgress * (PAD_MAX_DISTANCE + DISPLAY_TRAIL_WIDTH);
        final double endFade = 1.0 - smoothStep ((progress - 0.65) / 0.35);
        final int columns = DISPLAY_WIDTH / DISPLAY_PIXEL_WIDTH;
        final int rows = DISPLAY_HEIGHT / DISPLAY_PIXEL_HEIGHT;
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < columns; column++)
            {
                // Normalize both axes to the pad grid, with the display origin at bottom-left.
                final double x = (column + 0.5) / columns * 7;
                final double y = (rows - row - 0.5) / rows * 7;
                final double envelope = rippleIntensity (radius, Math.hypot (x, y), DISPLAY_TRAIL_WIDTH, DISPLAY_FRONT_WIDTH);
                if (envelope <= 0)
                    continue;

                final double noise = 0.22 + 0.78 * perlin (column * 0.31, row * 0.47, seed);
                final double intensity = Math.pow (envelope * noise, DISPLAY_NOISE_EXPONENT) * endFade;
                if (intensity < 0.025)
                    continue;
                commands.add (new DisplayCommand.Rectangle (
                    column * DISPLAY_PIXEL_WIDTH,
                    row * DISPLAY_PIXEL_HEIGHT,
                    DISPLAY_PIXEL_WIDTH,
                    DISPLAY_PIXEL_HEIGHT,
                    shade (baseColor, intensity)));
            }
        }
    }


    private static Map<PadGridPosition, RgbColor> maskedGrid ()
    {
        final Map<PadGridPosition, RgbColor> colors = new LinkedHashMap<> (64);
        for (int row = 0; row < 8; row++)
        {
            for (int column = 0; column < 8; column++)
                colors.put (new PadGridPosition (column, row), OFF);
        }
        return colors;
    }


    private static double rippleIntensity (final double radius, final double distance, final double trailWidth, final double frontWidth)
    {
        final double behindHead = radius - distance;
        if (behindHead >= 0)
            return 1.0 - smoothStep (clamp (behindHead / trailWidth));
        return 1.0 - smoothStep (clamp (-behindHead / frontWidth));
    }


    private static double cubicBezier (final double progress, final double control1, final double control2)
    {
        final double t = clamp (progress);
        final double inverse = 1.0 - t;
        return 3 * inverse * inverse * t * control1 + 3 * inverse * t * t * control2 + t * t * t;
    }


    private static double smoothStep (final double value)
    {
        final double t = clamp (value);
        return t * t * (3.0 - 2.0 * t);
    }


    private static double clamp (final double value)
    {
        return Math.max (0, Math.min (1, value));
    }


    private static double perlin (final double x, final double y, final int seed)
    {
        final int x0 = (int) Math.floor (x);
        final int y0 = (int) Math.floor (y);
        final double localX = x - x0;
        final double localY = y - y0;
        final double top = interpolate (
            gradientDot (x0, y0, localX, localY, seed),
            gradientDot (x0 + 1, y0, localX - 1, localY, seed),
            perlinFade (localX));
        final double bottom = interpolate (
            gradientDot (x0, y0 + 1, localX, localY - 1, seed),
            gradientDot (x0 + 1, y0 + 1, localX - 1, localY - 1, seed),
            perlinFade (localX));
        return clamp (0.5 + 0.5 * interpolate (top, bottom, perlinFade (localY)));
    }


    private static double gradientDot (final int x, final int y, final double offsetX, final double offsetY, final int seed)
    {
        int hash = x * 0x1f123bb5 ^ y * 0x5f356495 ^ seed;
        hash ^= hash >>> 15;
        hash *= 0x2c1b3c6d;
        hash ^= hash >>> 12;
        return switch (hash & 7)
        {
            case 0 -> offsetX;
            case 1 -> -offsetX;
            case 2 -> offsetY;
            case 3 -> -offsetY;
            case 4 -> (offsetX + offsetY) * 0.7071067811865476;
            case 5 -> (offsetX - offsetY) * 0.7071067811865476;
            case 6 -> (-offsetX + offsetY) * 0.7071067811865476;
            default -> (-offsetX - offsetY) * 0.7071067811865476;
        };
    }


    private static double perlinFade (final double value)
    {
        return value * value * value * (value * (value * 6 - 15) + 10);
    }


    private static double interpolate (final double from, final double to, final double amount)
    {
        return from + amount * (to - from);
    }


    private static RgbColor shade (final RgbColor color, final double level)
    {
        return new RgbColor (
            (int) Math.round (color.red () * level),
            (int) Math.round (color.green () * level),
            (int) Math.round (color.blue () * level));
    }


}
