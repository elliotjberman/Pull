// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect;
import de.mossgrabers.pull.core.api.effect.TapTempoEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.InputTarget;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/** Retained native tap, shifted metronome, and physical button-feedback policy. */
public final class TapTempoView implements ControllerView
{
    private static final ControlId TAP = PushControlIds.button ("TAP_TEMPO");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final RgbColor DIM = new RgbColor (60, 60, 60);
    private static final RgbColor BRIGHT = new RgbColor (255, 255, 255);
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.TAP_TEMPO_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.TAP_TEMPO_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private final AuthoritativeBooleanToggle<String> metronome;
    private String noticeProject;

    public TapTempoView () { this (new AuthoritativeBooleanToggle<> ()); }

    TapTempoView (final AuthoritativeBooleanToggle<String> metronome) { this.metronome = java.util.Objects.requireNonNull (metronome, "metronome"); }


    @Override
    public String id () { return "tap-tempo"; }


    @Override
    public ViewProfile profile () { return PROFILE; }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.TRANSPORT, BridgeSubscription.PROJECT);
    }


    @Override
    public CoreExecutionRequirements executionRequirements ()
    {
        return new CoreExecutionRequirements (this.noticeProject != null || this.metronome.pending ());
    }


    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        return TAP.equals (control) ? new InputTarget.Context (control, "project", snapshot.bridge ().project ().projectIdentity (), 0)
            : ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final boolean shifted = snapshot.pressedControls ().contains (SHIFT);
        boolean tap = false;
        boolean toggle = false;
        if (event instanceof final ControllerInputEvent input && TAP.equals (input.controlId ()) && input.kind () == InputKind.BUTTON)
        {
            tap = input.phase () == InputPhase.BEGIN && !shifted;
            toggle = input.phase () == InputPhase.END && shifted;
        }
        final var project = snapshot.bridge ().project ();
        final var transport = snapshot.bridge ().transport ();
        if (!project.available () || project.commandPending () || !transport.available ())
        {
            this.noticeProject = null;
            this.metronome.clear ();
            return List.of ();
        }
        if (this.noticeProject != null && !this.noticeProject.equals (project.projectIdentity ()))
            this.noticeProject = null;
        final List<CoreEffect> effects = new ArrayList<> (this.metronome.update (
            project.projectIdentity (), transport.metronomeEnabled (), snapshot.monotonicTimeNanos (), toggle,
            (identity, enabled) -> new SetProjectTransportStateEffect (identity, identity, TransportState.METRONOME, enabled.booleanValue ())));
        if (tap && transport.engineActive () && project.engineActive ())
        {
            effects.add (new TapTempoEffect (project.projectIdentity ()));
            this.noticeProject = project.projectIdentity ();
        }
        else if (event instanceof ControllerTickEvent && this.noticeProject != null)
        {
            effects.add (new ShowHostNotificationEffect (String.format (Locale.ROOT, "Tempo: %.02f", Double.valueOf (transport.tempo ()))));
            this.noticeProject = null;
        }
        return List.copyOf (effects);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (Map.of (TAP, snapshot.pressedControls ().contains (TAP) ? BRIGHT : DIM), Map.of ());
    }
}
