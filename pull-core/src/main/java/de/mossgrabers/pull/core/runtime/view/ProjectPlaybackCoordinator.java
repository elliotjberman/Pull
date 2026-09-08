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
import de.mossgrabers.pull.core.api.output.RgbColor;

import de.mossgrabers.pull.core.ui.page.PlaybackRippleRenderer;

import java.util.List;
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
        this.advanceWave (Objects.requireNonNull (snapshot, "snapshot").monotonicTimeNanos ());
        final ProjectSnapshot project = snapshot.bridge ().project ();
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


    ControllerPadGridOverlay padGridOverlay ()
    {
        return this.waveActive ? new ControllerPadGridOverlay (true, PlaybackRippleRenderer.pads (this.waveProgress, this.waveBaseColor)) : ControllerPadGridOverlay.inactive ();
    }


    ControllerDisplayOverlay displayOverlay ()
    {
        return this.waveActive ? new ControllerDisplayOverlay (true, PlaybackRippleRenderer.display (this.waveProgress, this.waveBaseColor)) : ControllerDisplayOverlay.inactive ();
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


}
