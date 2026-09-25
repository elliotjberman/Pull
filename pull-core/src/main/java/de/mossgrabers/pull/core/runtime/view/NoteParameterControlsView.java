// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.EditingPageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.*;
import static de.mossgrabers.pull.core.api.NoteParameterRole.*;

/** Multi-note controls share ordinary target capture, cancellation, and per-value Snapback. */
public final class NoteParameterControlsView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("note-parameters", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final NoteParameterRole[] NOTE = {DURATION, MUTE, VELOCITY, VELOCITY_SPREAD, RELEASE_VELOCITY, CHANCE, OCCURRENCE, RECURRENCE_LENGTH};
    private static final NoteParameterRole[] EXPRESSION = {DURATION, MUTE, null, GAIN, PAN, TRANSPOSE, TIMBRE, PRESSURE};
    private static final NoteParameterRole[] REPEAT = {DURATION, MUTE, null, REPEAT_COUNT, REPEAT_CURVE, REPEAT_VELOCITY_CURVE, REPEAT_VELOCITY_END, null};
    private static final NoteParameterRole[] RECURRENCE = {null, null, null, null, null, null, null, RECURRENCE_LENGTH};
    private final Map<ParameterTargetRef, Double> intents = new HashMap<> ();
    private boolean shift;

    @Override public String id () { return "note-parameters"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public boolean consumesRelativeSamples () { return true; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.CONTROLLER_PAGE_DISPLAY, BridgeSubscription.ENCODER_CONFIGURATION); }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return bindings (NOTE); }
    @Override public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot) { final Map<ControlId, ParameterSlot> result = new LinkedHashMap<> ();
        bindings (roles (snapshot)).forEach ((control, slot) -> { if (!this.parameterGroup (control, snapshot).isEmpty ()) result.put (control, slot); });
        return Map.copyOf (result); }

    @Override
    public List<ParameterSlot> parameterGroup (final ControlId control, final ControllerSnapshot snapshot)
    {
        final var note = observed (snapshot);
        final int index = column (control);
        final var role = index < 0 ? null : roles (snapshot)[index];
        if (note == null || role == null || note.count () > ParameterSlot.NOTE_CAPACITY) return List.of ();
        final List<ParameterSlot> group = new ArrayList<> ();
        for (int cell = 0; cell < note.count (); cell++)
        {
            final var slot = role.slot (cell);
            if (ParameterAlignment.target (snapshot, slot) == null) return List.of ();
            group.add (slot);
        }
        return List.copyOf (group);
    }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        final var note = observed (snapshot);
        return note == null || note.parameterOwner ().isBlank () || (roles (snapshot)[column (control)] != null && this.parameterGroup (control, snapshot).isEmpty ()) ? null : new InputTarget.Context (control, note.page (), note.parameterOwner (), 0);
    }

    @Override public void deactivate () { this.intents.clear (); }
    @Override public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final InputTarget target, final ControllerSnapshot snapshot) { this.intents.clear (); return List.of (); }
    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        final boolean shift = snapshot.pressedControls ().contains (PushControlIds.button ("SHIFT"));
        if (this.shift && !shift) this.intents.clear ();
        this.shift = shift;
        final Map<ParameterTargetRef, ParameterTargetSnapshot> targets = new HashMap<> ();
        snapshot.bridge ().parameters ().slots ().values ().forEach (target -> targets.put (target.target (), target));
        this.intents.entrySet ().removeIf (entry -> !targets.containsKey (entry.getKey ()) || targets.get (entry.getKey ()).isAt (entry.getValue ()));
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof ControllerInputEvent input) || observed (snapshot) == null) return List.of ();
        final int column = column (input.controlId ());
        if (column < 0) return List.of ();
        if (input.kind () == InputKind.TOUCH && input.phase () == InputPhase.END)
        {
            for (final var slot: this.parameterGroup (input.controlId (), snapshot)) this.intents.remove (ParameterAlignment.target (snapshot, slot).target ());
            return List.of ();
        }
        final boolean reset = input.kind () == InputKind.TOUCH && input.phase () == InputPhase.BEGIN && snapshot.pressedControls ().contains (PushControlIds.button ("DELETE"));
        if (!reset && input.kind () != InputKind.RELATIVE) return List.of ();
        if (!reset && !snapshot.bridge ().encoderConfiguration ().available ()) return List.of ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (reset) effects.add (new ConsumeControllerButtonEffect (PushControlIds.button ("DELETE")));
        final var role = roles (snapshot)[column];
        for (final var slot: this.parameterGroup (input.controlId (), snapshot))
        {
            final var target = ParameterAlignment.target (snapshot, slot);
            double value = this.intents.getOrDefault (target.target (), target.value ());
            if (reset) value = reset (role);
            else for (final long sample: input.relativeSamples ()) value = change (role, value, sample, this.shift, snapshot.bridge ().encoderConfiguration ());
            this.intents.put (target.target (), value);
            effects.add (new SetCurrentParameterValueEffect (target.target (), value));
        }
        return List.copyOf (effects);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var note = observed (snapshot);
        final boolean ready = note != null && !note.parameterOwner ().isBlank () && ParameterAlignment.target (snapshot, DURATION.slot (0)) != null;
        final var state = !ready ? EditingPageState.empty () : new EditingPageState.Note (note.exists (), note.page (), note.count (), note.step (), note.key (), note.quartersPerMeasure (), note.transposeRange (), snapshot.pressedControls ().contains (PushControlIds.button ("SHIFT")),
            java.util.stream.IntStream.range (0, 8).mapToObj (index -> snapshot.touchedControls ().contains (knob (index))).toList (), note.data (), note.parameterOwner ());
        return new ViewOutput (Map.of (), Map.of (), EditingPageRenderer.render (state));
    }

    private static double change (final NoteParameterRole role, final double value, final long sample, final boolean fine, final EncoderConfigurationSnapshot configuration)
    {
        final int direction = Long.signum (sample);
        final double delta = TrackEncoderResponse.calibratedDelta (sample, fine, configuration);
        return switch (role)
        {
            case DURATION -> Math.max (0, value + direction * 0.125);
            case MUTE -> direction > 0 ? 1 : 0;
            case OCCURRENCE -> Math.clamp (value + direction, 0, 10);
            case RECURRENCE_LENGTH -> Math.clamp (value + direction, 1, 8);
            case REPEAT_COUNT -> Math.clamp (value + direction, -127, 127);
            case TRANSPOSE -> Math.clamp (value + direction * (Math.abs (delta / configuration.baseStep ()) < 1 ? 0.1 : 1), -96, 96);
            case PAN, TIMBRE, REPEAT_CURVE, REPEAT_VELOCITY_CURVE, REPEAT_VELOCITY_END -> Math.clamp (value + delta / (configuration.valueUpperBound () - 1), -1, 1);
            default -> Math.clamp (value + delta / (configuration.valueUpperBound () - 1), 0, 1);
        };
    }
    private static double reset (final NoteParameterRole role)
    {
        return switch (role) { case DURATION, VELOCITY, RELEASE_VELOCITY, CHANCE, RECURRENCE_LENGTH -> 1; case GAIN -> 0.5; default -> 0; };
    }
    private static EditingPageState.Note observed (final ControllerSnapshot snapshot)
    {
        final var page = snapshot.bridge ().pageDisplay ();
        return "NOTE".equals (page.modeId ()) && page.state () instanceof EditingPageState.Note note && note.exists () ? note : null;
    }
    private static NoteParameterRole[] roles (final ControllerSnapshot snapshot)
    {
        final var note = observed (snapshot);
        return note == null ? new NoteParameterRole[8] : switch (note.page ()) { case "NOTE" -> NOTE; case "EXPRESSIONS" -> EXPRESSION; case "REPEAT" -> REPEAT; case "RECCURRENCE_PATTERN" -> RECURRENCE; default -> new NoteParameterRole[8]; };
    }
    private static Map<ControlId, ParameterSlot> bindings (final NoteParameterRole[] roles)
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < 8; index++) if (roles[index] != null) bindings.put (knob (index), roles[index].slot (0));
        return Map.copyOf (bindings);
    }
    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }
    private static int column (final ControlId control) { for (int index = 0; index < 8; index++) if (knob (index).equals (control)) return index; return -1; }
}
