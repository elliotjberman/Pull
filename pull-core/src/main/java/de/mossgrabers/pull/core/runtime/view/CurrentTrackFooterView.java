// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Normal Track footer: release-time modifiers, selected-group navigation, and host-read lights. */
public final class CurrentTrackFooterView implements ControllerView
{
    private static final ControlId DUPLICATE = PushControlIds.button ("DUPLICATE");
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ControlId RECORD = PushControlIds.button ("RECORD");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final ControlId STOP = PushControlIds.button ("STOP_CLIP");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor ARMED = new RgbColor (255, 0, 0);
    private static final List<ControlId> BUTTONS = java.util.stream.IntStream.rangeClosed (1, 8).mapToObj (index -> PushControlIds.button ("ROW1_" + index)).toList ();
    private static final Set<ControllerActionBinding> ACTIONS = BUTTONS.stream ().map (button -> new ControllerActionBinding (button, InputKind.BUTTON, Set.of (new ControllerActionIntent (ControllerActionId.NAVIGATE_SELECTED_TARGET, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)), new ControllerActionIntent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK, Set.of (ControllerStateScope.SESSION_PLAYBACK))))).collect (java.util.stream.Collectors.toUnmodifiableSet ());
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DUPLICATE_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.DELETE_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.RECORD_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.SELECT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private final ButtonGestureConsumption buttonGestures;
    private final SessionStopGesture stopGesture;
    private final Gesture[] gestures = new Gesture[8];
    private final List<AuthoritativeBooleanToggle<CurrentTrackTarget>> arms = lanes ();
    private final List<AuthoritativeBooleanToggle<CurrentTrackTarget>> groups = lanes ();
    private ControllerSnapshot latest;

    public CurrentTrackFooterView ()
    {
        this (new ButtonGestureConsumption (Set.of (RECORD)));
    }

    public CurrentTrackFooterView (final ButtonGestureConsumption buttonGestures)
    {
        this (buttonGestures, new SessionStopGesture ());
    }

    public CurrentTrackFooterView (final ButtonGestureConsumption buttonGestures, final SessionStopGesture stopGesture)
    {
        this.buttonGestures = java.util.Objects.requireNonNull (buttonGestures, "buttonGestures");
        this.stopGesture = java.util.Objects.requireNonNull (stopGesture, "stopGesture");
    }

    @Override
    public String id () { return "current-track-footer"; }

    @Override
    public ViewProfile profile () { return PROFILE; }

    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.CURRENT_TRACK_BANK, BridgeSubscription.CONTROLLER_LAYOUT);
    }

    @Override
    public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }

    @Override
    public CoreExecutionRequirements executionRequirements ()
    {
        return new CoreExecutionRequirements (this.arms.stream ().anyMatch (AuthoritativeBooleanToggle::pending) || this.groups.stream ().anyMatch (AuthoritativeBooleanToggle::pending));
    }

    @Override
    public void reconcile (final ControllerSnapshot snapshot) { this.latest = snapshot; }

    @Override
    public void deactivate ()
    {
        java.util.Arrays.fill (this.gestures, null);
        this.arms.forEach (AuthoritativeBooleanToggle::clear);
        this.groups.forEach (AuthoritativeBooleanToggle::clear);
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final int index = BUTTONS.indexOf (input.controlId ());
        final Gesture gesture = new Gesture ();
        this.gestures[index] = gesture;
        this.latest = snapshot;
        final SessionBankSnapshot session = snapshot.bridge ().sessionBank ();
        if (snapshot.pressedControls ().contains (STOP) && session.shape ().isPresent ())
        {
            this.stopGesture.consume ();
            gesture.longSeen = true;
            final SessionTrackSnapshot track = index < session.tracks ().size () ? session.tracks ().get (index) : SessionTrackSnapshot.empty ();
            final StopSessionTrackEffect stop = track.exists () ? new StopSessionTrackEffect (session.generation (), session.shape (), index, track.channelId (), true) : null;
            return ResolvedControllerAction.of (binding.intent (ControllerActionId.STOP_VISIBLE_SESSION_TRACK), () -> {
                if (this.gestures[index] != gesture || gesture.ready)
                    return List.of ();
                gesture.ready = true;
                if (gesture.ended)
                    this.gestures[index] = null;
                final SessionBankSnapshot live = this.latest.bridge ().sessionBank ();
                final ConsumeControllerButtonEffect consume = new ConsumeControllerButtonEffect (input.controlId ());
                return stop != null && live.generation () == session.generation () && live.shape ().equals (session.shape ()) && index < live.tracks ().size () && live.tracks ().get (index).channelId ().equals (track.channelId ()) ? List.of (consume, stop) : List.of (consume);
            });
        }
        // Snapback may defer this dispatch past LONG or END. The physical edge still captures
        // immutable intent in this provisional gesture; only this callback permits execution.
        return ResolvedControllerAction.of (binding.intent (ControllerActionId.NAVIGATE_SELECTED_TARGET), () -> {
            if (this.gestures[index] != gesture)
                return List.of ();
            gesture.ready = true;
            return this.drain (index, gesture);
        });
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        final List<CoreEffect> effects = new ArrayList<> (this.advanceToggles (snapshot));
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.BUTTON)
            return effects;
        final int index = BUTTONS.indexOf (input.controlId ());
        if (index < 0)
            return effects;
        final Gesture gesture = this.gestures[index];
        if (gesture == null)
            return effects;
        if (input.phase () == InputPhase.LONG && !gesture.longSeen && !gesture.ended)
        {
            gesture.longSeen = true;
            final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
            gesture.intent = new Intent (Operation.PARENT, null, bank.parentGeneration (), bank.cursorChannelId (), snapshot.bridge ().layout (), null);
            effects.add (new ConsumeControllerButtonEffect (BUTTONS.get (index)));
        }
        else if (input.phase () == InputPhase.END && !gesture.ended)
        {
            gesture.ended = true;
            if (!gesture.longSeen)
            {
                gesture.intent = this.captureRelease (index, snapshot);
                // Consumption belongs to this physical gesture. Deferring it with navigation
                // could miss the modifier release or consume a subsequent press instead.
                if (gesture.intent.consume () != null)
                    effects.add (new ConsumeControllerButtonEffect (gesture.intent.consume ()));
            }
        }
        effects.addAll (this.drain (index, gesture));
        return List.copyOf (effects);
    }

    private List<CoreEffect> drain (final int index, final Gesture gesture)
    {
        if (!gesture.ready)
            return List.of ();
        final Intent intent = gesture.intent;
        gesture.intent = null;
        if (gesture.ended)
            this.gestures[index] = null;
        return intent == null ? List.of () : this.execute (intent, this.latest);
    }

    private Intent captureRelease (final int index, final ControllerSnapshot snapshot)
    {
        final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
        final CurrentTrackTarget target = target (bank, index);
        final Set<ControlId> pressed = snapshot.pressedControls ();
        final Operation operation;
        final ControlId consume;
        if (pressed.contains (DUPLICATE)) { operation = Operation.DUPLICATE; consume = DUPLICATE; }
        else if (pressed.contains (DELETE)) { operation = Operation.REMOVE; consume = DELETE; }
        else if (pressed.contains (RECORD)) { operation = Operation.ARM; consume = RECORD; this.buttonGestures.consume (RECORD); }
        else if (pressed.contains (SELECT)) { operation = Operation.NONE; consume = SELECT; }
        else
        {
            consume = null;
            if (target == null)
                operation = Operation.NONE;
            else
            {
                final CurrentTrackSnapshot track = bank.tracks ().get (index);
                operation = !track.track ().selected () ? Operation.SELECT : isGroup (track) ? pressed.contains (SHIFT) ? Operation.GROUP_TOGGLE : Operation.ENTER : Operation.DEVICE;
            }
        }
        return new Intent (operation, target, 0, "", snapshot.bridge ().layout (), consume);
    }

    private List<CoreEffect> execute (final Intent intent, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
        if (intent.operation () == Operation.PARENT)
        {
            if (bank.parentAvailable () && bank.parentGeneration () == intent.parentGeneration () && bank.cursorChannelId ().equals (intent.cursorId ()))
                effects.add (new NavigateTrackParentEffect (intent.parentGeneration (), intent.cursorId ()));
            return List.copyOf (effects);
        }
        final CurrentTrackTarget target = intent.target ();
        if (target == null || !target.equals (target (bank, target.trackIndex ())))
            return List.copyOf (effects);
        final CurrentTrackSnapshot track = bank.tracks ().get (target.trackIndex ());
        switch (intent.operation ())
        {
            case SELECT -> effects.add (new CurrentTrackActionEffect (target, CurrentTrackActionEffect.Action.SELECT));
            case DUPLICATE -> effects.add (new CurrentTrackActionEffect (target, CurrentTrackActionEffect.Action.DUPLICATE));
            case REMOVE -> effects.add (new CurrentTrackActionEffect (target, CurrentTrackActionEffect.Action.REMOVE));
            case ARM -> effects.addAll (this.arms.get (target.trackIndex ()).update (target, track.track ().recordArmed (), snapshot.monotonicTimeNanos (), true, (key, value) -> new SetCurrentTrackBooleanEffect (key, SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, value.booleanValue ())));
            case GROUP_TOGGLE ->
            {
                if (isGroup (track))
                    effects.addAll (this.groups.get (target.trackIndex ()).update (target, track.groupExpanded (), snapshot.monotonicTimeNanos (), true, (key, value) -> new SetCurrentTrackBooleanEffect (key, SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, value.booleanValue ())));
            }
            case ENTER ->
            {
                if (isGroup (track) && track.track ().selected () && target.channelId ().equals (bank.cursorChannelId ()))
                {
                    effects.add (new SetCurrentTrackBooleanEffect (target, SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, true));
                    effects.add (new CurrentTrackActionEffect (target, CurrentTrackActionEffect.Action.ENTER_SELECTED_GROUP));
                }
            }
            case DEVICE ->
            {
                if (track.track ().selected () && !isGroup (track) && intent.layout ().generation () != 0 && intent.layout ().equals (snapshot.bridge ().layout ()))
                    effects.add (new SelectControllerModeEffect (intent.layout ().generation (), "DEVICE_PARAMS"));
            }
            case NONE, PARENT -> { }
        }
        return List.copyOf (effects);
    }

    private List<CoreEffect> advanceToggles (final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
        for (int index = 0; index < 8; index++)
        {
            final CurrentTrackTarget target = target (bank, index);
            if (target == null)
            {
                this.arms.get (index).clear ();
                this.groups.get (index).clear ();
                continue;
            }
            final CurrentTrackSnapshot track = bank.tracks ().get (index);
            effects.addAll (this.arms.get (index).update (target, track.track ().recordArmed (), snapshot.monotonicTimeNanos (), false, (key, value) -> new SetCurrentTrackBooleanEffect (key, SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, value.booleanValue ())));
            if (isGroup (track))
                effects.addAll (this.groups.get (index).update (target, track.groupExpanded (), snapshot.monotonicTimeNanos (), false, (key, value) -> new SetCurrentTrackBooleanEffect (key, SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, value.booleanValue ())));
            else
                this.groups.get (index).clear ();
        }
        return List.copyOf (effects);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, 960, TrackFooterDisplayScene.HEIGHT, OFF));
        for (int index = 0; index < 8; index++)
        {
            final SessionTrackSnapshot track = index < bank.tracks ().size () ? bank.tracks ().get (index).track () : SessionTrackSnapshot.empty ();
            lights.put (BUTTONS.get (index), !track.exists () || !track.activated () ? OFF : track.recordArmed () ? ARMED : track.color ());
            if (track.exists ())
            {
                final String name = track.name ().substring (0, Math.min (12, track.name ().length ()));
                final SessionTrackType type = track.type () == SessionTrackType.GROUP && bank.tracks ().get (index).groupExpanded () ? SessionTrackType.GROUP_OPEN : track.type ();
                final DisplayIcon icon = track.selected () && bank.cursorPinned () ? DisplayIcon.PIN : TrackFooterDisplayScene.icon (type);
                TrackFooterDisplayScene.append (commands, index, 0, name, icon, track.color (), track.selected (), track.activated ());
            }
        }
        return new ViewOutput (lights, Map.of (), new ControllerDisplayScene (960, (int) TrackFooterDisplayScene.HEIGHT, commands));
    }

    private static CurrentTrackTarget target (final CurrentTrackBankSnapshot bank, final int index)
    {
        if (index < 0 || index >= bank.tracks ().size () || !bank.tracks ().get (index).track ().exists ())
            return null;
        return new CurrentTrackTarget (bank.generation (), bank.bankId (), index, bank.tracks ().get (index).track ().channelId ());
    }

    private static boolean isGroup (final CurrentTrackSnapshot track)
    {
        return track.track ().type () == SessionTrackType.GROUP || track.track ().type () == SessionTrackType.GROUP_OPEN;
    }

    private static List<AuthoritativeBooleanToggle<CurrentTrackTarget>> lanes ()
    {
        return java.util.stream.IntStream.range (0, 8).mapToObj (ignored -> new AuthoritativeBooleanToggle<CurrentTrackTarget> ()).toList ();
    }

    private enum Operation { NONE, SELECT, DUPLICATE, REMOVE, ARM, GROUP_TOGGLE, ENTER, DEVICE, PARENT }
    private record Intent (Operation operation, CurrentTrackTarget target, long parentGeneration, String cursorId, ControllerLayoutSnapshot layout, ControlId consume) { }
    private static final class Gesture
    {
        private boolean ready;
        private boolean ended;
        private boolean longSeen;
        private Intent intent;
    }
}
