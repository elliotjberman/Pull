// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.api.effect.ProjectNavigationDirection;
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
 * <p>Bitwig's transport proxy addresses only the visible project. A remote toggle therefore
 * navigates through exact observed project identities, waits for every navigation and transport
 * readback, and retraces the acknowledged path to the project the performer was viewing.</p>
 */
public final class ProjectPlaybackCoordinator
{
    private static final int MAX_NAVIGATION_STEPS = 32;
    private static final int PLAYBACK_ACKNOWLEDGEMENT_TICKS = 16;
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
    private Transaction transaction;
    private boolean waveActive;
    private boolean waveRunning;
    private long waveStartedNanos;
    private RgbColor waveBaseColor = WAVE_PURPLE;
    private double waveProgress;
    private long nextNavigationLeaseId = 1;


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
        if (this.transaction != null)
            return List.of ();

        final ProjectSnapshot project = snapshot.bridge ().project ();
        if (!project.available () || project.commandPending () || this.engineOwnerIdentity.isBlank ())
            return List.of ();

        final boolean remote = !project.projectIdentity ().equals (this.engineOwnerIdentity);
        this.transaction = new Transaction (
            project.projectIdentity (),
            this.engineOwnerIdentity,
            !this.engineOwnerPlaying,
            remote ? this.nextNavigationLeaseId++ : 0);
        if (remote)
            this.prepareWave (this.transaction.desiredPlaying ? WAVE_PURPLE : WHITE);
        return this.advance (snapshot, false);
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


    List<CoreEffect> advance (final ControllerSnapshot snapshot, final boolean countAcknowledgementTick)
    {
        this.advanceWave (Objects.requireNonNull (snapshot, "snapshot").monotonicTimeNanos ());
        if (this.transaction == null)
            return List.of ();

        final ProjectSnapshot project = snapshot.bridge ().project ();
        if (!project.available ())
            return List.of ();

        if (this.transaction.awaitingNavigation != null)
        {
            final List<CoreEffect> navigationResult = this.observeNavigationResult (project, snapshot.monotonicTimeNanos ());
            if (navigationResult != null)
                return navigationResult;
        }

        if (project.commandPending ())
            return List.of ();

        if (!project.projectIdentity ().equals (this.transaction.expectedCurrentIdentity))
        {
            this.finishTransaction ();
            return List.of ();
        }

        if (!this.transaction.targetIdentity.equals (this.engineOwnerIdentity))
            this.transaction.stage = Stage.RETURNING;

        return switch (this.transaction.stage)
        {
            case SEARCHING -> this.advanceSearch (project, snapshot.monotonicTimeNanos ());
            case WAITING_FOR_PLAYBACK -> this.advancePlaybackAcknowledgement (snapshot, countAcknowledgementTick);
            case RETURNING -> this.advanceReturn (project);
        };
    }


    public String engineOwnerIdentity ()
    {
        return this.engineOwnerIdentity;
    }


    public boolean engineOwnerPlaying ()
    {
        return this.engineOwnerPlaying;
    }


    /** Get replayable cadence and project-navigation fencing for the current transaction. */
    public CoreExecutionRequirements executionRequirements ()
    {
        if (this.transaction != null && this.transaction.navigationLeaseId != 0)
            return new CoreExecutionRequirements (true, this.transaction.navigationLeaseId, this.transaction.originIdentity);
        return this.transaction != null || this.waveActive ? new CoreExecutionRequirements (true, 0, "") : CoreExecutionRequirements.empty ();
    }


    public void restoreEngineOwner (final String identity, final boolean playing)
    {
        this.engineOwnerIdentity = Objects.requireNonNullElse (identity, "");
        this.engineOwnerPlaying = !this.engineOwnerIdentity.isBlank () && playing;
    }


    private List<CoreEffect> observeNavigationResult (final ProjectSnapshot project, final long nowNanos)
    {
        final AwaitingNavigation awaiting = this.transaction.awaitingNavigation;
        if (project.commandPending ())
            return List.of ();

        if (project.projectIdentity ().equals (awaiting.fromIdentity ()))
        {
            this.transaction.awaitingNavigation = null;
            if (awaiting.returning ())
            {
                this.finishTransaction ();
                return List.of ();
            }

            final boolean boundary = awaiting.direction () == ProjectNavigationDirection.PREVIOUS ? !project.canPrevious () : !project.canNext ();
            if (!boundary)
            {
                this.transaction.stage = Stage.RETURNING;
                return this.advanceReturn (project);
            }

            if (awaiting.direction () == ProjectNavigationDirection.PREVIOUS && this.transaction.searchDirection == ProjectNavigationDirection.PREVIOUS)
            {
                this.transaction.searchDirection = ProjectNavigationDirection.NEXT;
                return this.advanceSearch (project, nowNanos);
            }

            this.transaction.stage = Stage.RETURNING;
            return this.advanceReturn (project);
        }

        this.transaction.awaitingNavigation = null;
        if (awaiting.returning ())
        {
            final PathStep step = this.transaction.path.getLast ();
            if (!project.projectIdentity ().equals (step.fromIdentity ()))
            {
                this.finishTransaction ();
                return List.of ();
            }
            this.transaction.path.removeLast ();
        }
        else
        {
            this.recordSearchStep (new PathStep (awaiting.fromIdentity (), project.projectIdentity (), awaiting.direction ()));
            this.transaction.navigationSteps++;
        }
        this.transaction.expectedCurrentIdentity = project.projectIdentity ();
        return null;
    }


    private List<CoreEffect> advanceSearch (final ProjectSnapshot project, final long nowNanos)
    {
        if (project.projectIdentity ().equals (this.transaction.targetIdentity))
        {
            if (!project.engineActive ())
            {
                this.transaction.stage = Stage.RETURNING;
                return this.advanceReturn (project);
            }
            this.transaction.stage = Stage.WAITING_FOR_PLAYBACK;
            this.transaction.playbackAcknowledgementTicks = 0;
            this.startWave (nowNanos);
            return List.of (new SetProjectTransportStateEffect (
                project.projectIdentity (),
                TransportState.PLAYING,
                this.transaction.desiredPlaying));
        }

        if (this.transaction.navigationSteps >= MAX_NAVIGATION_STEPS)
        {
            this.transaction.stage = Stage.RETURNING;
            return this.advanceReturn (project);
        }

        if (this.transaction.searchDirection == ProjectNavigationDirection.PREVIOUS && !project.canPrevious ())
            this.transaction.searchDirection = ProjectNavigationDirection.NEXT;

        final boolean canNavigate = this.transaction.searchDirection == ProjectNavigationDirection.PREVIOUS ? project.canPrevious () : project.canNext ();
        if (!canNavigate)
        {
            this.transaction.stage = Stage.RETURNING;
            return this.advanceReturn (project);
        }
        return this.requestNavigation (project.projectIdentity (), this.transaction.searchDirection, false);
    }


    private List<CoreEffect> advancePlaybackAcknowledgement (final ControllerSnapshot snapshot, final boolean countAcknowledgementTick)
    {
        final ProjectSnapshot project = snapshot.bridge ().project ();
        final TransportSnapshot transport = snapshot.bridge ().transport ();
        if (project.engineActive () && transport.available () && transport.playing () == this.transaction.desiredPlaying)
        {
            this.transaction.stage = Stage.RETURNING;
            return this.advanceReturn (project);
        }

        if (countAcknowledgementTick)
            this.transaction.playbackAcknowledgementTicks++;
        if (this.transaction.playbackAcknowledgementTicks < PLAYBACK_ACKNOWLEDGEMENT_TICKS)
            return List.of ();

        this.transaction.stage = Stage.RETURNING;
        return this.advanceReturn (project);
    }


    private List<CoreEffect> advanceReturn (final ProjectSnapshot project)
    {
        if (this.transaction.path.isEmpty ())
        {
            this.finishTransaction ();
            return List.of ();
        }

        final PathStep step = this.transaction.path.getLast ();
        if (!project.projectIdentity ().equals (step.toIdentity ()))
        {
            this.finishTransaction ();
            return List.of ();
        }
        return this.requestNavigation (project.projectIdentity (), opposite (step.direction ()), true);
    }


    private List<CoreEffect> requestNavigation (final String currentIdentity, final ProjectNavigationDirection direction, final boolean returning)
    {
        this.transaction.awaitingNavigation = new AwaitingNavigation (currentIdentity, direction, returning);
        return List.of (new NavigateProjectEffect (currentIdentity, direction));
    }


    private void recordSearchStep (final PathStep step)
    {
        if (!this.transaction.path.isEmpty ())
        {
            final PathStep previous = this.transaction.path.getLast ();
            if (previous.fromIdentity ().equals (step.toIdentity ()) && previous.toIdentity ().equals (step.fromIdentity ()) && opposite (previous.direction ()) == step.direction ())
            {
                this.transaction.path.removeLast ();
                return;
            }
        }
        this.transaction.path.add (step);
    }


    private static ProjectNavigationDirection opposite (final ProjectNavigationDirection direction)
    {
        return direction == ProjectNavigationDirection.PREVIOUS ? ProjectNavigationDirection.NEXT : ProjectNavigationDirection.PREVIOUS;
    }


    private void prepareWave (final RgbColor color)
    {
        this.waveActive = true;
        this.waveRunning = false;
        this.waveBaseColor = Objects.requireNonNull (color, "color");
        this.waveProgress = 0;
    }


    private void startWave (final long nowNanos)
    {
        if (!this.waveActive || this.waveRunning)
            return;
        this.waveRunning = true;
        this.waveStartedNanos = nowNanos;
    }


    private void advanceWave (final long nowNanos)
    {
        if (!this.waveActive || !this.waveRunning || this.waveProgress >= 1)
            return;

        final long elapsed = Math.max (0, nowNanos - this.waveStartedNanos);
        this.waveProgress = Math.min (1.0, (double) elapsed / WAVE_DURATION_NANOS);
        if (this.waveProgress >= 1 && this.transaction == null)
            this.waveActive = false;
    }


    private void finishTransaction ()
    {
        this.transaction = null;
        if (!this.waveRunning || this.waveProgress >= 1)
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


    private enum Stage
    {
        SEARCHING,
        WAITING_FOR_PLAYBACK,
        RETURNING
    }


    private static final class Transaction
    {
        private final String targetIdentity;
        private final boolean desiredPlaying;
        private final String originIdentity;
        private final long navigationLeaseId;
        private final List<PathStep> path = new ArrayList<> ();
        private String expectedCurrentIdentity;
        private ProjectNavigationDirection searchDirection = ProjectNavigationDirection.PREVIOUS;
        private Stage stage = Stage.SEARCHING;
        private AwaitingNavigation awaitingNavigation;
        private int navigationSteps;
        private int playbackAcknowledgementTicks;


        private Transaction (final String originIdentity, final String targetIdentity, final boolean desiredPlaying, final long navigationLeaseId)
        {
            this.originIdentity = originIdentity;
            this.targetIdentity = targetIdentity;
            this.desiredPlaying = desiredPlaying;
            this.navigationLeaseId = navigationLeaseId;
            this.expectedCurrentIdentity = originIdentity;
        }
    }


    private record AwaitingNavigation (String fromIdentity, ProjectNavigationDirection direction, boolean returning)
    {
    }


    private record PathStep (String fromIdentity, String toIdentity, ProjectNavigationDirection direction)
    {
    }
}
