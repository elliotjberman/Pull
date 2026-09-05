// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.*;
import java.util.*;
import java.util.stream.IntStream;

/** Complete fixed-velocity page, including inherited current-track selection. */
public final class AccentPageView implements ControllerView
{
    private static final long TIMEOUT_NANOS = 5_000_000_000L;
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final ControlId STOP = PushControlIds.button ("STOP_CLIP");
    private static final List<ControlId> KNOBS = IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.continuous ("KNOB" + i)).toList ();
    private static final List<ControlId> ROW = IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.button ("ROW1_" + i)).toList ();
    private static final Set<ControllerActionBinding> ACTIONS = ROW.stream ().map (button -> new ControllerActionBinding (button, InputKind.BUTTON, Set.of (
        new ControllerActionIntent (ControllerActionId.NAVIGATE_SELECTED_TARGET, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionIntent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK, Set.of (ControllerStateScope.SESSION_PLAYBACK))))).collect (java.util.stream.Collectors.toUnmodifiableSet ());
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private final SessionStopGesture fullSessionStop;
    private final SessionStopGesture compositeStop;
    private final Gesture[] rows = new Gesture[8];
    private final Set<ControlId> touched = new HashSet<> ();
    private ControllerSnapshot latest;
    private Integer requested;
    private int desired;
    private int observedBeforeRequest;
    private long submittedAt;
    private long submittedRevision;

    public AccentPageView (final SessionStopGesture fullSessionStop, final SessionStopGesture compositeStop)
    {
        this.fullSessionStop = Objects.requireNonNull (fullSessionStop, "fullSessionStop");
        this.compositeStop = Objects.requireNonNull (compositeStop, "compositeStop");
    }

    @Override public String id () { return "accent-page"; }
    @Override public String installedModeId () { return "ACCENT"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.ENCODER_CONFIGURATION, BridgeSubscription.CURRENT_TRACK_BANK); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.requested != null); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.latest = snapshot; }
    @Override public void reconcile (final ControllerSnapshot snapshot) { this.latest = snapshot; }
    @Override public void deactivate () { Arrays.fill (this.rows, null); this.touched.clear (); this.requested = null; }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        final int index = ROW.indexOf (input.controlId ());
        final SessionBankSnapshot session = snapshot.bridge ().sessionBank ();
        final boolean stop = snapshot.pressedControls ().contains (STOP) && session.shape ().isPresent ();
        CoreEffect effect = null;
        if (stop)
        {
            this.fullSessionStop.consume ();
            this.compositeStop.consume ();
            if (index < session.tracks ().size () && session.tracks ().get (index).exists ())
                effect = new StopSessionTrackEffect (session.generation (), session.shape (), index, session.tracks ().get (index).channelId (), true);
        }
        else
        {
            final var bank = snapshot.bridge ().currentTrackBank ();
            if (bank.generation () > 0 && index < bank.tracks ().size () && bank.tracks ().get (index).track ().exists ())
                effect = new CurrentTrackActionEffect (new CurrentTrackTarget (bank.generation (), bank.bankId (), index, bank.tracks ().get (index).track ().channelId ()), CurrentTrackActionEffect.Action.SELECT);
        }
        final Gesture gesture = new Gesture (effect, stop);
        this.rows[index] = gesture;
        final ResolvedControllerAction action = ResolvedControllerAction.of (binding.intent (stop ? ControllerActionId.STOP_VISIBLE_SESSION_TRACK : ControllerActionId.NAVIGATE_SELECTED_TARGET), () -> {
            if (this.rows[index] != gesture || gesture.admitted) return List.of ();
            gesture.admitted = true;
            return this.drain (index, gesture);
        });
        return stop ? action.withImmediateConsumption (input.controlId ()) : action;
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        final List<CoreEffect> effects = new ArrayList<> (this.advanceVelocity (snapshot));
        if (!(event instanceof final ControllerInputEvent input)) return List.copyOf (effects);
        if (KNOBS.contains (input.controlId ()))
        {
            if (input.kind () == InputKind.TOUCH)
            {
                if (input.phase () == InputPhase.BEGIN) this.touched.add (input.controlId ());
                else if (input.phase () == InputPhase.END) this.touched.remove (input.controlId ());
            }
            else if (input.kind () == InputKind.RELATIVE && snapshot.bridge ().controllerSettings ().available () && snapshot.bridge ().encoderConfiguration ().available ())
            {
                final int base = this.requested == null ? snapshot.bridge ().controllerSettings ().accentVelocity () : this.desired;
                this.desired = (int) Math.max (1, Math.min (127, base + input.value () * snapshot.bridge ().encoderConfiguration ().baseStep () * 0.1));
                if (this.requested == null && this.desired != base) effects.add (this.submitVelocity (snapshot));
            }
        }
        if (input.kind () == InputKind.BUTTON && input.phase () == InputPhase.END)
        {
            final int index = ROW.indexOf (input.controlId ());
            if (index >= 0 && this.rows[index] != null)
            {
                this.rows[index].ended = true;
                effects.addAll (this.drain (index, this.rows[index]));
            }
        }
        return List.copyOf (effects);
    }

    private List<CoreEffect> drain (final int index, final Gesture gesture)
    {
        if (!gesture.admitted || !gesture.ended && !gesture.stop) return List.of ();
        if (gesture.ended) this.rows[index] = null;
        if (gesture.sent || gesture.effect == null) return List.of ();
        gesture.sent = true;
        if (gesture.effect instanceof final CurrentTrackActionEffect action)
        {
            final var target = action.target ();
            final var bank = this.latest.bridge ().currentTrackBank ();
            if (bank.generation () != target.generation () || !bank.bankId ().equals (target.bankId ()) || index >= bank.tracks ().size () || !bank.tracks ().get (index).track ().channelId ().equals (target.channelId ())) return List.of ();
        }
        else if (gesture.effect instanceof final StopSessionTrackEffect action)
        {
            final var bank = this.latest.bridge ().sessionBank ();
            if (bank.generation () != action.targetGeneration () || !bank.shape ().equals (action.shape ()) || index >= bank.tracks ().size () || !bank.tracks ().get (index).channelId ().equals (action.channelId ())) return List.of ();
        }
        return List.of (gesture.effect);
    }

    private List<CoreEffect> advanceVelocity (final ControllerSnapshot snapshot)
    {
        final var settings = snapshot.bridge ().controllerSettings ();
        if (!settings.available ()) { this.requested = null; return List.of (); }
        if (this.requested == null) return List.of ();
        final int observed = settings.accentVelocity ();
        if (snapshot.revision () > this.submittedRevision && observed == this.requested.intValue ())
        {
            this.requested = null;
            return this.desired == observed ? List.of () : List.of (this.submitVelocity (snapshot));
        }
        if (observed != this.observedBeforeRequest || snapshot.monotonicTimeNanos () - this.submittedAt >= TIMEOUT_NANOS) this.requested = null;
        return List.of ();
    }

    private CoreEffect submitVelocity (final ControllerSnapshot snapshot)
    {
        this.requested = Integer.valueOf (this.desired);
        this.observedBeforeRequest = snapshot.bridge ().controllerSettings ().accentVelocity ();
        this.submittedAt = snapshot.monotonicTimeNanos ();
        this.submittedRevision = snapshot.revision ();
        return new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, this.desired);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int i = 1; i <= 8; i++) { lights.put (PushControlIds.button ("ROW1_" + i), BLACK); lights.put (PushControlIds.button ("ROW2_" + i), BLACK); }
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, 960, 160, BLACK));
        final var settings = snapshot.bridge ().controllerSettings ();
        final var encoder = snapshot.bridge ().encoderConfiguration ();
        if (settings.available () && encoder.available ())
        {
            final double intensity = this.touched.contains (KNOBS.get (7)) ? 1 : 0.5;
            final RgbColor text = color (190, 235, 247, intensity);
            commands.add (new DisplayCommand.TextAt ("Accent", 848, 31, text, 12.5));
            commands.add (new DisplayCommand.TextAt (Integer.toString (settings.accentVelocity ()), 848, 64, text, 30));
            final double value = Math.floor (settings.accentVelocity () * (encoder.valueUpperBound () - 1.0) / 127) / encoder.valueUpperBound ();
            commands.add (new DisplayCommand.DottedArc (873, 105, 25, 220, -260, 220, 1.1, color (20, 54, 65, intensity)));
            commands.add (new DisplayCommand.DottedArc (873, 105, 25, 220, -260 * value, Math.max (2, (int) Math.ceil (220 * value)), 1.1, color (132, 214, 255, intensity)));
        }
        return new ViewOutput (lights, Map.of (), new ControllerDisplayScene (960, 160, commands));
    }

    private static RgbColor color (final int red, final int green, final int blue, final double intensity) { return new RgbColor ((int) Math.round (red * intensity), (int) Math.round (green * intensity), (int) Math.round (blue * intensity)); }
    private static final class Gesture
    {
        private final CoreEffect effect;
        private final boolean stop;
        private boolean admitted;
        private boolean ended;
        private boolean sent;
        private Gesture (final CoreEffect effect, final boolean stop) { this.effect = effect; this.stop = stop; }
    }
}
