// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Bounded controller-level Play policy for the one project which owns Bitwig's audio engine.
 *
 * <p>Core owns the semantic target, light policy, and transient animation. The stable shell owns
 * the complete visit/acknowledgement/return transaction required to address another project.</p>
 */
public final class ProjectPlaybackCoordinator
{
    private static final long WAVE_DURATION_NANOS = 250_000_000L;
    private static final double PAD_MAX_DISTANCE = Math.hypot (7, 7);
    private static final double PAD_TRAIL_WIDTH = 2.2;
    private static final double PAD_FRONT_WIDTH = 0.5;
    private static final int DISPLAY_WIDTH = 960;
    private static final int DISPLAY_HEIGHT = 160;
    private static final int DISPLAY_PIXEL_WIDTH = 24;
    private static final int DISPLAY_PIXEL_HEIGHT = 20;
    private static final double DISPLAY_NOISE_EXPONENT = 1.35;
    private static final double DISPLAY_TRAIL_WIDTH = 224;
    private static final double DISPLAY_FRONT_WIDTH = 48;

    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor GREEN = new RgbColor (0, 255, 0);
    private static final RgbColor PURPLE = new RgbColor (128, 0, 255);
    private static final RgbColor WAVE_PURPLE = new RgbColor (160, 48, 255);

    private String engineOwnerIdentity = "";
    private boolean engineOwnerPlaying;
    private boolean waveActive;
    private long waveStartedNanos;
    private RgbColor waveBaseColor = WAVE_PURPLE;
    private double waveProgress;


    void observe (final ControllerSnapshot snapshot)
    {
        final ProjectSnapshot project = Objects.requireNonNull (snapshot, "snapshot").bridge ().project ();
        final TransportSnapshot transport = snapshot.bridge ().transport ();
        if (!project.available ())
            return;

        if (project.engineActive ())
        {
            this.engineOwnerIdentity = project.projectIdentity ();
            if (transport.available ())
                this.engineOwnerPlaying = transport.playing ();
        }
        else if (project.projectIdentity ().equals (this.engineOwnerIdentity))
        {
            this.engineOwnerIdentity = "";
            this.engineOwnerPlaying = false;
        }
    }


    RgbColor playColor (final ControllerSnapshot snapshot)
    {
        final ProjectSnapshot project = snapshot.bridge ().project ();
        if (this.engineOwnerIdentity.isBlank () || !project.available ())
            return OFF;
        if (project.projectIdentity ().equals (this.engineOwnerIdentity))
            return this.engineOwnerPlaying ? GREEN : WHITE;
        return this.engineOwnerPlaying ? PURPLE : WHITE;
    }


    List<CoreEffect> playPressed (final ControllerSnapshot snapshot)
    {
        final ProjectSnapshot project = snapshot.bridge ().project ();
        if (!project.available () || project.commandPending () || this.engineOwnerIdentity.isBlank ())
            return List.of ();

        final boolean remote = !project.projectIdentity ().equals (this.engineOwnerIdentity);
        final boolean desiredPlaying = !this.engineOwnerPlaying;
        if (remote)
            this.startWave (snapshot.monotonicTimeNanos (), desiredPlaying ? WAVE_PURPLE : WHITE);
        return List.of (new SetProjectTransportStateEffect (
            project.projectIdentity (),
            this.engineOwnerIdentity,
            TransportState.PLAYING,
            desiredPlaying));
    }


    ControllerPadGridOverlay padGridOverlay (final ControllerSnapshot snapshot)
    {
        this.advanceWave (Objects.requireNonNull (snapshot, "snapshot").monotonicTimeNanos ());
        if (!this.waveActive)
            return ControllerPadGridOverlay.inactive ();
        if (this.waveProgress >= 1)
            return new ControllerPadGridOverlay (true, maskedGrid ());

        final Map<PadGridPosition, RgbColor> colors = new LinkedHashMap<> (maskedGrid ());
        addRipple (colors, this.waveProgress, this.waveBaseColor);
        return new ControllerPadGridOverlay (true, colors);
    }


    ControllerDisplayOverlay displayOverlay (final ControllerSnapshot snapshot)
    {
        this.advanceWave (Objects.requireNonNull (snapshot, "snapshot").monotonicTimeNanos ());
        if (!this.waveActive)
            return ControllerDisplayOverlay.inactive ();

        final List<DisplayCommand> commands = new ArrayList<> (7);
        commands.add (new DisplayCommand.Rectangle (0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, OFF));
        if (this.waveProgress < 1)
            addDisplayRipple (commands, this.waveProgress, this.waveBaseColor);
        return new ControllerDisplayOverlay (true, new ControllerDisplayScene (960, 160, commands));
    }


    List<CoreEffect> advance (final ControllerSnapshot snapshot)
    {
        this.advanceWave (Objects.requireNonNull (snapshot, "snapshot").monotonicTimeNanos ());
        return List.of ();
    }


    public String engineOwnerIdentity ()
    {
        return this.engineOwnerIdentity;
    }


    public boolean engineOwnerPlaying ()
    {
        return this.engineOwnerPlaying;
    }


    /** Get replayable cadence while the transient animation is active. */
    public CoreExecutionRequirements executionRequirements ()
    {
        return this.waveActive ? new CoreExecutionRequirements (true) : CoreExecutionRequirements.empty ();
    }


    public void restoreEngineOwner (final String identity, final boolean playing)
    {
        this.engineOwnerIdentity = Objects.requireNonNullElse (identity, "");
        this.engineOwnerPlaying = !this.engineOwnerIdentity.isBlank () && playing;
    }


    private void startWave (final long nowNanos, final RgbColor color)
    {
        this.waveActive = true;
        this.waveBaseColor = Objects.requireNonNull (color, "color");
        this.waveProgress = 0;
        this.waveStartedNanos = nowNanos;
    }


    private void advanceWave (final long nowNanos)
    {
        if (!this.waveActive || this.waveProgress >= 1)
            return;

        final long elapsed = Math.max (0, nowNanos - this.waveStartedNanos);
        this.waveProgress = Math.min (1.0, (double) elapsed / WAVE_DURATION_NANOS);
        if (this.waveProgress >= 1)
            this.waveActive = false;
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


    private static void addDisplayRipple (final List<DisplayCommand> commands, final double progress, final RgbColor baseColor)
    {
        final double easedProgress = cubicBezier (progress, 0.15, 0.85);
        final double head = easedProgress * (DISPLAY_WIDTH + DISPLAY_TRAIL_WIDTH);
        final double endFade = 1.0 - 0.45 * smoothStep (clamp ((progress - 0.7) / 0.3));
        final int columns = DISPLAY_WIDTH / DISPLAY_PIXEL_WIDTH;
        final int rows = DISPLAY_HEIGHT / DISPLAY_PIXEL_HEIGHT;
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < columns; column++)
            {
                final double centerX = column * DISPLAY_PIXEL_WIDTH + DISPLAY_PIXEL_WIDTH / 2.0;
                final double envelope = rippleIntensity (head, centerX, DISPLAY_TRAIL_WIDTH, DISPLAY_FRONT_WIDTH);
                if (envelope <= 0)
                    continue;

                final double noise = 0.22 + 0.78 * perlin (column * 0.31, row * 0.47);
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


    private static double perlin (final double x, final double y)
    {
        final int x0 = (int) Math.floor (x);
        final int y0 = (int) Math.floor (y);
        final double localX = x - x0;
        final double localY = y - y0;
        final double top = interpolate (
            gradientDot (x0, y0, localX, localY),
            gradientDot (x0 + 1, y0, localX - 1, localY),
            perlinFade (localX));
        final double bottom = interpolate (
            gradientDot (x0, y0 + 1, localX, localY - 1),
            gradientDot (x0 + 1, y0 + 1, localX - 1, localY - 1),
            perlinFade (localX));
        return clamp (0.5 + 0.5 * interpolate (top, bottom, perlinFade (localY)));
    }


    private static double gradientDot (final int x, final int y, final double offsetX, final double offsetY)
    {
        int hash = x * 0x1f123bb5 ^ y * 0x5f356495;
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
