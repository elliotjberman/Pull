// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.effect.SessionActionEffect.Action;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.LightBlink;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.*;

import java.util.*;

/** Complete Session gestures and observed feedback for either installed launcher window. */
public final class SessionView implements ControllerView
{
    public static final String SCENE_LAUNCH = "scene-launch";
    private static final ControlId STOP = PushControlIds.button ("STOP_CLIP");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ControlId DUPLICATE = PushControlIds.button ("DUPLICATE");
    private static final ControlId BROWSE = PushControlIds.button ("BROWSE");
    private static final ControlId UP = PushControlIds.button ("ARROW_UP");
    private static final ControlId DOWN = PushControlIds.button ("ARROW_DOWN");
    private static final ControlId PAGE_LEFT = PushControlIds.button ("PAGE_LEFT");
    private static final ControlId PAGE_RIGHT = PushControlIds.button ("PAGE_RIGHT");
    private static final ControlId OCTAVE_DOWN = PushControlIds.button ("OCTAVE_DOWN");
    private static final ControlId OCTAVE_UP = PushControlIds.button ("OCTAVE_UP");
    private static final List<ControlId> TRACKS = controls ("ROW1_", 8);
    private static final List<ControlId> SCENES = controls ("SCENE", 8);
    private static final List<ControlId> PADS = java.util.stream.IntStream.rangeClosed (1, 64).mapToObj (PushControlIds::pad).toList ();
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor RED = new RgbColor (255, 0, 0);
    private static final RgbColor DIM = new RgbColor (30, 30, 30);
    private static final RgbColor ROSE = new RgbColor (114, 17, 106);
    private static final RgbColor GREEN = new RgbColor (0, 89, 0);
    private static final RgbColor GREEN_SELECTED = new RgbColor (0, 255, 0);
    private static final RgbColor AMBER = new RgbColor (89, 29, 0);
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.SESSION_BANK,
        BridgeSubscription.SESSION_CLIPS, BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.PROJECT);

    private final int rows;
    private final boolean scenesEnabled;
    private final ViewProfile profile;
    private final SessionStopGesture stopGesture;
    // One entry per physical launcher control; the shared router owns admission and cancellation.
    private final Map<ControlId, SessionActionEffect> releases = new LinkedHashMap<> ();
    private final Map<ControlId, PendingClip> pending = new LinkedHashMap<> ();
    private SessionLocation copySource;
    private PendingNavigation navigation;

    private SessionView (final int rows, final boolean scenesEnabled, final SessionStopGesture stopGesture)
    {
        this.rows = rows;
        this.scenesEnabled = scenesEnabled;
        this.stopGesture = Objects.requireNonNull (stopGesture, "stopGesture");
        this.profile = profile (rows, scenesEnabled);
    }

    public static SessionView full () { return full (new SessionStopGesture ()); }
    public static SessionView full (final SessionStopGesture stopGesture) { return new SessionView (8, true, stopGesture); }
    public static SessionView upper (final boolean scenesEnabled) { return upper (scenesEnabled, new SessionStopGesture ()); }
    public static SessionView upper (final boolean scenesEnabled, final SessionStopGesture stopGesture) { return new SessionView (4, scenesEnabled, stopGesture); }
    @Override public String id () { return "session-" + this.profile.id (); }
    @Override public ViewProfile profile () { return this.profile; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return SUBSCRIPTIONS; }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (!this.pending.isEmpty () || this.navigation != null, !this.pending.isEmpty () || this.navigation != null); }

    /** Vertical scene arrows remain core-owned when a legacy parameter body owns the horizontal keys. */
    public ControllerView legacyPageNavigation () { return new LegacyPageNavigation (); }

    @Override public void deactivate () { this.navigation = null; this.pending.clear (); }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        if (this.copySource != null && !this.addressable (this.copySource, snapshot)) this.copySource = null;
        this.pending.values ().removeIf (intent -> !this.addressable (intent.target (), snapshot));
    }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (STOP.equals (control)) return TrackInputTargets.stop (control, snapshot);
        if (PADS.contains (control) || SCENES.contains (control))
        {
            final SessionLocation location = this.location (control, snapshot);
            if (location != null) return new InputTarget.SessionLocation (location);
            final var bank = snapshot.bridge ().sessionBank ();
            return new InputTarget.Composite (List.of (new InputTarget.SessionBank (control, bank.generation (), bank.shape ()),
                new InputTarget.Context (control, "project", snapshot.bridge ().project ().projectIdentity (), 0)));
        }
        return ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final InputTarget target, final ControllerSnapshot snapshot)
    {
        if (STOP.equals (control)) this.stopGesture.takeConsumed ();
        this.pending.remove (control);
        return this.finish (control);
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input)) return this.advancePending (snapshot);
        if (input.kind () != InputKind.PAD && input.kind () != InputKind.BUTTON) return List.of ();
        final ControlId control = input.controlId ();
        if (STOP.equals (control)) return this.handleStop (input, snapshot);
        if (input.phase () == InputPhase.END) return this.finish (control);
        if (input.phase () != InputPhase.BEGIN) return List.of ();
        if (held (snapshot, STOP) && !SHIFT.equals (control) && !SELECT.equals (control))
        {
            this.stopGesture.consume ();
            if (TRACKS.contains (control)) return this.stopVisibleTrack (control, snapshot);
        }
        if (PADS.contains (control)) return this.pressPad (control, snapshot);
        if (this.scenesEnabled && SCENES.indexOf (control) >= 0 && SCENES.indexOf (control) < this.rows) return this.pressScene (control, snapshot);
        return this.navigate (control, snapshot);
    }

    private List<CoreEffect> pressPad (final ControlId control, final ControllerSnapshot snapshot)
    {
        if (this.pending.containsKey (control))
            return List.of (SELECT, DELETE, DUPLICATE, STOP, BROWSE).stream ().filter (modifier -> held (snapshot, modifier))
                .<CoreEffect>map (ConsumeControllerButtonEffect::new).toList ();
        final int index = PADS.indexOf (control);
        final int column = index % 8;
        final int row = 7 - index / 8;
        if (row >= this.rows || !this.ready (snapshot)) return List.of ();
        final var bank = snapshot.bridge ().sessionBank ();
        if (held (snapshot, SHIFT) && held (snapshot, SELECT))
        {
            this.navigation = null;
            final int trackPage = bank.trackOffset () / 8;
            final int scenePage = bank.sceneOffset () / this.rows;
            return List.of (new ConsumeControllerButtonEffect (SELECT), new SetSessionBankPositionEffect (bank.generation (), bank.shape (),
                (trackPage / 8 * 8 + column) * 8, (scenePage / this.rows * this.rows + row) * this.rows));
        }
        final SessionLocation target = this.location (control, snapshot);
        if (target == null) return List.of ();
        final var slot = bank.clips ().slot (column, row);
        if (held (snapshot, SELECT)) return consumed (SELECT, slot.exists () ? new SessionActionEffect (target, Action.SELECT) : null);
        if (held (snapshot, DELETE)) return consumed (DELETE, slot.exists () ? new SessionActionEffect (target, Action.REMOVE) : null);
        if (held (snapshot, DUPLICATE))
        {
            if (slot.exists () && slot.hasContent ()) this.copySource = target;
            else if (this.copySource != null) return consumed (DUPLICATE, new CopySessionClipEffect (this.copySource, target));
            return consumed (DUPLICATE, null);
        }
        if (held (snapshot, STOP))
            return consumed (STOP, new StopSessionTrackEffect (bank.generation (), bank.shape (), column, target.channelId (), held (snapshot, SHIFT)));
        if (held (snapshot, BROWSE)) return consumed (BROWSE, new SessionActionEffect (target, Action.BROWSE));

        final var settings = snapshot.bridge ().controllerSettings ().session ();
        if (!settings.available ()) return List.of ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (settings.selectOnLaunch ()) effects.add (new SessionActionEffect (target, Action.SELECT));
        if (!bank.tracks ().get (column).recordArmed () || slot.hasContent ())
            effects.add (this.launch (control, target, held (snapshot, SHIFT)));
        else if (settings.actionForArmedPad () == 0)
        {
            if (!slot.recording ())
            {
                effects.add (new SessionActionEffect (target, Action.START_RECORDING));
                this.pending.put (control, new PendingClip (target, false, false));
            }
            else effects.add (this.launch (control, target, false));
        }
        else if (settings.actionForArmedPad () == 1)
        {
            effects.add (new CreateSessionClipEffect (target, settings.newClipLengthBeats ()));
            this.pending.put (control, new PendingClip (target, true, false));
        }
        return effects;
    }

    private List<CoreEffect> pressScene (final ControlId control, final ControllerSnapshot snapshot)
    {
        final SessionLocation target = this.location (control, snapshot);
        if (target == null) return List.of ();
        final var scene = snapshot.bridge ().sessionBank ().clips ().scenes ().get (SCENES.indexOf (control));
        if (!scene.exists ()) return List.of ();
        if (held (snapshot, DELETE)) return consumed (DELETE, new SessionActionEffect (target, Action.REMOVE));
        if (held (snapshot, DUPLICATE)) return consumed (DUPLICATE, new SessionActionEffect (target, Action.DUPLICATE));
        final List<CoreEffect> effects = new ArrayList<> (List.of (new SessionActionEffect (target, Action.SELECT)));
        if (!scene.name ().isBlank ()) effects.add (new ShowHostNotificationEffect (scene.name ()));
        if (held (snapshot, SELECT)) effects.add (new ConsumeControllerButtonEffect (SELECT));
        else effects.add (this.launch (control, target, held (snapshot, SHIFT)));
        return effects;
    }

    private SessionActionEffect launch (final ControlId control, final SessionLocation target, final boolean alternate)
    {
        this.releases.put (control, new SessionActionEffect (target, alternate ? Action.RELEASE_ALT : Action.RELEASE));
        return new SessionActionEffect (target, alternate ? Action.LAUNCH_ALT : Action.LAUNCH);
    }

    private List<CoreEffect> finish (final ControlId control)
    {
        final var waiting = this.pending.get (control);
        if (waiting != null) this.pending.put (control, new PendingClip (waiting.target (), waiting.created (), true));
        final var release = this.releases.remove (control);
        return release == null ? List.of () : List.of (release);
    }

    private List<CoreEffect> advancePending (final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> (this.advanceNavigation (snapshot));
        final var iterator = this.pending.entrySet ().iterator ();
        while (iterator.hasNext ())
        {
            final var entry = iterator.next ();
            final var intent = entry.getValue ();
            if (!this.addressable (intent.target (), snapshot))
            {
                iterator.remove ();
                continue;
            }
            final var slot = snapshot.bridge ().sessionBank ().clips ().slot (intent.target ().trackIndex (), intent.target ().scenePosition () - snapshot.bridge ().sessionBank ().sceneOffset ());
            if (intent.created () ? !slot.hasContent () : !slot.recording ()) continue;
            iterator.remove ();
            if (intent.created ()) effects.add (new SessionActionEffect (intent.target (), Action.SELECT));
            effects.add (this.launch (entry.getKey (), intent.target (), false));
            if (intent.created ()) effects.add (new SetTransportStateEffect (TransportState.LAUNCHER_OVERDUB, true));
            if (intent.released ()) effects.add (this.releases.remove (entry.getKey ()));
        }
        return effects;
    }

    private List<CoreEffect> navigate (final ControlId control, final ControllerSnapshot snapshot)
    {
        if (!this.ready (snapshot)) return List.of ();
        final var bank = snapshot.bridge ().sessionBank ();
        final boolean tracks = PAGE_LEFT.equals (control) || PAGE_RIGHT.equals (control);
        final boolean arrow = UP.equals (control) || DOWN.equals (control);
        final boolean scenes = this.rows == 8 && (OCTAVE_DOWN.equals (control) || OCTAVE_UP.equals (control) || arrow);
        if (!tracks && !scenes) return List.of ();
        this.navigation = null;
        final boolean previous = PAGE_LEFT.equals (control) || OCTAVE_UP.equals (control) || UP.equals (control);
        final boolean page = !arrow || held (snapshot, SHIFT);
        final var bounds = tracks ? bank.clips ().trackNavigation () : bank.clips ().sceneNavigation ();
        if (previous ? !(page ? bounds.previousPage () : bounds.previousItem ()) : !(page ? bounds.nextPage () : bounds.nextItem ())) return List.of ();
        final int position = Math.max (0, (tracks ? bank.trackOffset () : bank.sceneOffset ()) + (previous ? -1 : 1) * (page ? tracks ? 8 : this.rows : 1));
        if (page) this.navigation = new PendingNavigation (snapshot.bridge ().project ().projectIdentity (), bank.shape (), bank.generation (),
            tracks ? position : bank.trackOffset (), scenes ? position : bank.sceneOffset (), scenes);
        return List.of (new SetSessionBankPositionEffect (bank.generation (), bank.shape (), tracks ? position : -1, scenes ? position : -1));
    }

    private List<CoreEffect> advanceNavigation (final ControllerSnapshot snapshot)
    {
        final var intent = this.navigation;
        if (intent == null) return List.of ();
        final var bank = snapshot.bridge ().sessionBank ();
        if (!intent.project ().equals (snapshot.bridge ().project ().projectIdentity ()) || !intent.shape ().equals (bank.shape ()))
        {
            this.navigation = null;
            return List.of ();
        }
        if (bank.generation () == intent.generation ()) return List.of ();
        if (!this.ready (snapshot)) return List.of ();
        this.navigation = null;
        if (bank.trackOffset () != intent.trackOffset () || bank.sceneOffset () != intent.sceneOffset ()) return List.of ();
        if (intent.scene ())
        {
            if (!bank.clips ().scenes ().getFirst ().exists ()) return List.of ();
            return List.of (new SessionActionEffect (new SessionLocation (intent.project (), bank.generation (), bank.shape (), -1, "", bank.sceneOffset ()), Action.SELECT));
        }
        final var track = bank.tracks ().getFirst ();
        return track.exists () ? List.of (new SelectSessionTrackEffect (bank.generation (), bank.shape (), 0, track.channelId ())) : List.of ();
    }

    private List<CoreEffect> stopVisibleTrack (final ControlId control, final ControllerSnapshot snapshot)
    {
        final var bank = snapshot.bridge ().sessionBank ();
        final int index = TRACKS.indexOf (control);
        if (!bank.shape ().isPresent () || index >= bank.tracks ().size () || !bank.tracks ().get (index).exists ()) return consumed (control, null);
        return consumed (control, new StopSessionTrackEffect (bank.generation (), bank.shape (), index, bank.tracks ().get (index).channelId (), true));
    }

    private List<CoreEffect> handleStop (final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (input.phase () == InputPhase.BEGIN) { this.stopGesture.begin (); return List.of (); }
        if (input.phase () != InputPhase.END || this.stopGesture.takeConsumed ()) return List.of ();
        if (held (snapshot, SHIFT) || held (snapshot, SELECT))
        {
            final var bank = snapshot.bridge ().sessionBank ();
            if (!bank.shape ().isPresent ()) return List.of ();
            final var stop = new StopSessionBankEffect (bank.generation (), bank.shape (), true);
            return held (snapshot, SELECT) ? consumed (SELECT, stop) : List.of (stop);
        }
        final var selected = snapshot.bridge ().selectedTrack ();
        return selected.exists () ? List.of (new SelectedTrackActionEffect (selected.generation (), selected.channelId (), SelectedTrackAction.STOP_IMMEDIATELY)) : List.of ();
    }

    private boolean ready (final ControllerSnapshot snapshot)
    {
        final var bank = snapshot.bridge ().sessionBank ();
        return snapshot.bridge ().project ().available () && bank.shape ().tracks () == 8 && bank.shape ().scenes () == this.rows && bank.clips ().aligned ();
    }

    private SessionLocation location (final ControlId control, final ControllerSnapshot snapshot)
    {
        if (!this.ready (snapshot)) return null;
        final var bank = snapshot.bridge ().sessionBank ();
        final int pad = PADS.indexOf (control);
        final int column = pad < 0 ? -1 : pad % 8;
        final int row = pad < 0 ? SCENES.indexOf (control) : 7 - pad / 8;
        if (row < 0 || row >= this.rows || column >= 0 && !bank.tracks ().get (column).exists ()) return null;
        return new SessionLocation (snapshot.bridge ().project ().projectIdentity (), bank.generation (), bank.shape (), column,
            column < 0 ? "" : bank.tracks ().get (column).channelId (), bank.sceneOffset () + row);
    }

    private boolean addressable (final SessionLocation target, final ControllerSnapshot snapshot)
    {
        if (!this.ready (snapshot)) return false;
        final var bank = snapshot.bridge ().sessionBank ();
        final int row = target.scenePosition () - bank.sceneOffset ();
        return target.projectIdentity ().equals (snapshot.bridge ().project ().projectIdentity ()) && target.generation () == bank.generation () && target.shape ().equals (bank.shape ()) &&
            row >= 0 && row < this.rows && target.trackIndex () >= 0 && bank.tracks ().get (target.trackIndex ()).channelId ().equals (target.channelId ());
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final Map<ControlId, LightBlink> blinks = new LinkedHashMap<> ();
        lights.put (STOP, held (snapshot, STOP) ? RED : WHITE);
        final var bank = snapshot.bridge ().sessionBank ();
        final boolean available = this.ready (snapshot);
        final boolean birdsEye = held (snapshot, SHIFT) && held (snapshot, SELECT);
        for (int row = 0; row < this.rows; row++)
        {
            for (int column = 0; column < 8; column++)
            {
                final ControlId control = PADS.get ((7 - row) * 8 + column);
                if (!available) lights.put (control, OFF);
                else if (birdsEye) lights.put (control, this.birdsEyeColor (column, row, bank));
                else this.drawSlot (control, bank.clips ().slot (column, row), bank.tracks ().get (column).recordArmed (), snapshot.bridge ().controllerSettings ().session ().drawRecordStripe (), lights, blinks);
            }
            if (this.scenesEnabled)
            {
                final var scene = available ? bank.clips ().scenes ().get (row) : SessionSceneSnapshot.empty ();
                lights.put (SCENES.get (row), !scene.exists () ? OFF : scene.selected () ? GREEN_SELECTED : GREEN);
            }
        }
        final var tracks = available ? bank.clips ().trackNavigation () : BankNavigationSnapshot.empty ();
        final var scenes = available ? bank.clips ().sceneNavigation () : BankNavigationSnapshot.empty ();
        lights.put (PAGE_LEFT, tracks.previousPage () ? DIM : OFF);
        lights.put (PAGE_RIGHT, tracks.nextPage () ? DIM : OFF);
        if (this.rows == 8)
        {
            // Preserve Push's existing octave availability convention separately from its direction.
            lights.put (OCTAVE_UP, scenes.nextPage () ? DIM : OFF);
            lights.put (OCTAVE_DOWN, scenes.previousPage () ? DIM : OFF);
        }
        return new ViewOutput (lights, Map.of ()).withLightBlinks (blinks);
    }

    private void drawSlot (final ControlId control, final SessionSlotSnapshot slot, final boolean armed, final boolean stripe,
                          final Map<ControlId, RgbColor> lights, final Map<ControlId, LightBlink> blinks)
    {
        final RgbColor color;
        if (slot.recordingQueued ()) { color = ROSE; blinks.put (control, new LightBlink (OFF, true)); }
        else if (slot.recording ()) { color = slot.color (); blinks.put (control, new LightBlink (ROSE, false)); }
        else if (slot.playbackQueued () || slot.stopQueued ()) { color = slot.color (); blinks.put (control, new LightBlink (GREEN, true)); }
        else if (slot.playing ()) { color = slot.color (); blinks.put (control, new LightBlink (GREEN, false)); }
        else if (slot.hasContent ())
        {
            color = slot.muted () ? DIM : slot.color ();
            if (!slot.muted () && slot.selected ()) blinks.put (control, new LightBlink (WHITE, false));
        }
        else color = slot.exists () && armed && stripe ? AMBER : OFF;
        lights.put (control, color);
    }

    private RgbColor birdsEyeColor (final int column, final int row, final SessionBankSnapshot bank)
    {
        final int trackPage = (bank.trackOffset () + 7) / 8;
        final int scenePage = (bank.sceneOffset () + this.rows - 1) / this.rows;
        if (column == trackPage % 8 && row == scenePage % this.rows) return GREEN;
        return column + trackPage / 8 * 8 < (bank.clips ().trackNavigation ().itemCount () + 7) / 8 &&
            row + scenePage / this.rows * this.rows < (bank.clips ().sceneNavigation ().itemCount () + this.rows - 1) / this.rows ? AMBER : OFF;
    }

    private static ViewProfile profile (final int rows, final boolean scenesEnabled)
    {
        final Set<SurfaceClaim> claims = new LinkedHashSet<> ();
        for (final var area: List.of (SurfaceArea.STOP_CLIP_BUTTON, SurfaceArea.GRID_UPPER, SurfaceArea.NAVIGATION_PAGE)) own (claims, area);
        if (rows == 8)
            for (final var area: List.of (SurfaceArea.GRID_LOWER, SurfaceArea.SCENE_KEYS_UPPER, SurfaceArea.SCENE_KEYS_LOWER, SurfaceArea.NAVIGATION_OCTAVE)) own (claims, area);
        for (final var area: List.of (SurfaceArea.SHIFT_MODIFIER, SurfaceArea.SELECT_MODIFIER, SurfaceArea.DELETE_MODIFIER, SurfaceArea.DUPLICATE_BUTTON,
            SurfaceArea.BROWSE_BUTTON, SurfaceArea.SOFT_KEYS_UPPER, SurfaceArea.SOFT_KEYS_LOWER)) claims.add (new SurfaceClaim (area, SurfaceClaim.Kind.OBSERVE_INPUT));
        if (rows == 8) return ViewProfile.fixed ("full", claims, Set.of (ControllerViewFacet.SESSION_GRID_FULL));
        final var sceneFacet = new ViewFacet (SCENE_LAUNCH, Set.of (new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT)), Set.of (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER));
        return new ViewProfile ("upper", claims, Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER), Map.of (SCENE_LAUNCH, sceneFacet), scenesEnabled ? Set.of (SCENE_LAUNCH) : Set.of ());
    }

    private static void own (final Set<SurfaceClaim> claims, final SurfaceArea area)
    {
        claims.add (new SurfaceClaim (area, SurfaceClaim.Kind.EXCLUSIVE_INPUT));
        claims.add (new SurfaceClaim (area, SurfaceClaim.Kind.OUTPUT));
    }
    private static boolean held (final ControllerSnapshot snapshot, final ControlId control) { return snapshot.pressedControls ().contains (control); }
    private static List<CoreEffect> consumed (final ControlId control, final CoreEffect effect)
    {
        return effect == null ? List.of (new ConsumeControllerButtonEffect (control)) : List.of (new ConsumeControllerButtonEffect (control), effect);
    }
    private static List<ControlId> controls (final String prefix, final int count)
    {
        return java.util.stream.IntStream.rangeClosed (1, count).mapToObj (index -> PushControlIds.button (prefix + index)).toList ();
    }
    private final class LegacyPageNavigation implements ControllerView
    {
        private final ViewProfile arrows = ViewProfile.fixed ("legacy-parameter-arrows", Set.of (
            new SurfaceClaim (SurfaceArea.NAVIGATION_HORIZONTAL, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_HORIZONTAL, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_VERTICAL, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_VERTICAL, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of (ControllerViewFacet.SESSION_GRID_FULL));
        @Override public String id () { return "session-legacy-page-navigation"; }
        @Override public ViewProfile profile () { return this.arrows; }
        @Override public Set<BridgeSubscription> bridgeSubscriptions () { return SUBSCRIPTIONS; }
        @Override public void deactivate () { SessionView.this.navigation = null; }
        @Override
        public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
        {
            return event instanceof final ControllerInputEvent input && input.phase () == InputPhase.BEGIN &&
                (UP.equals (input.controlId ()) || DOWN.equals (input.controlId ())) ? SessionView.this.navigate (input.controlId (), snapshot) : List.of ();
        }
        @Override
        public ViewOutput render (final ControllerSnapshot snapshot)
        {
            final var bounds = SessionView.this.ready (snapshot) ? snapshot.bridge ().sessionBank ().clips ().sceneNavigation () : BankNavigationSnapshot.empty ();
            final boolean shift = held (snapshot, SHIFT);
            return new ViewOutput (Map.of (UP, (shift ? bounds.previousPage () : bounds.previousItem ()) ? DIM : OFF,
                DOWN, (shift ? bounds.nextPage () : bounds.nextItem ()) ? DIM : OFF), Map.of ());
        }
    }

    private record PendingClip (SessionLocation target, boolean created, boolean released) { }
    private record PendingNavigation (String project, SessionBankShape shape, long generation, int trackOffset, int sceneOffset, boolean scene) { }
}
