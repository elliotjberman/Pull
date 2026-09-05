// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredNoteInputTranslation;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetDrumBankPositionEffect;
import de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;


/** Drum octave policy shared by standalone and composite Drum profiles. */
public final class DrumOctaveView implements ControllerView
{
    private static final int LOWEST_BASE = 4;
    private static final int HIGHEST_BASE = 100;
    private static final int DEFAULT_STEP = 16;
    private static final int SHIFT_STEP = 4;
    private static final int DEFAULT_BASE = 36;
    private static final ControlId DOWN = PushControlIds.button ("OCTAVE_DOWN");
    private static final ControlId UP = PushControlIds.button ("OCTAVE_UP");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final RgbColor ON = new RgbColor (255, 255, 255);
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final List<String> NOTE_NAMES = List.of ("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B");
    private static final List<Integer> IDENTITY_VELOCITIES = IntStream.range (0, 128).boxed ().toList ();
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.DRUM_PADS, BridgeSubscription.CONTROLLER_SETTINGS);
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "drum",
        Set.of (
            new SurfaceClaim (SurfaceArea.NAVIGATION_OCTAVE, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_OCTAVE, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.OBSERVE_INPUT)),
        Set.of ());

    // Only unsent intent lives here, while a physical musical gesture fences core replacement.
    private PendingPosition pending;
    // Cosmetic acknowledgement is disposable on reload; it never retains another host command.
    private PendingNotification notification;


    @Override
    public String id ()
    {
        return "drum-octave";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    @Override
    public void deactivate ()
    {
        this.pending = null;
        this.notification = null;
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        if (this.pending != null && (!enabled (snapshot) || !this.pending.matches (snapshot)))
            this.pending = null;
        if (this.notification != null && (!enabled (snapshot) || !this.notification.sameTarget (snapshot)))
            this.notification = null;
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (!enabled (snapshot))
            return List.of ();

        if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.BEGIN && (UP.equals (input.controlId ()) || DOWN.equals (input.controlId ())))
        {
            final int current = this.pending == null ? snapshot.bridge ().layout ().drumBaseMidiNote () : this.pending.requestedBase ();
            final int step = snapshot.pressedControls ().contains (SHIFT) ? SHIFT_STEP : DEFAULT_STEP;
            final int requested = Math.max (LOWEST_BASE, Math.min (HIGHEST_BASE, current + (UP.equals (input.controlId ()) ? step : -step)));
            if (playPadsHeld (snapshot))
            {
                this.pending = PendingPosition.capture (snapshot, requested);
                return List.of ();
            }
            this.pending = null;
            return this.move (snapshot, requested);
        }

        if (this.pending == null || playPadsHeld (snapshot))
            return this.acknowledgedNotification (snapshot);
        final int requested = this.pending.requestedBase ();
        this.pending = null;
        return this.move (snapshot, requested);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final boolean aligned = mappingApplied (snapshot);
        final int base = snapshot.bridge ().drum ().baseMidiNote ();
        return new ViewOutput (Map.of (
            DOWN, aligned && base - DEFAULT_STEP >= LOWEST_BASE ? ON : OFF,
            UP, aligned && base + DEFAULT_STEP <= HIGHEST_BASE ? ON : OFF), Map.of ());
    }


    /** Complete native mapping contributed by the existing Drum note-lifecycle owner. */
    public static DesiredNoteInputTranslation translation (final ControllerSnapshot snapshot)
    {
        if (!enabled (snapshot) || !snapshot.bridge ().controllerSettings ().available () || snapshot.bridge ().drum ().baseMidiNote () != snapshot.bridge ().layout ().drumBaseMidiNote ())
            return DesiredNoteInputTranslation.silent ();

        final int base = snapshot.bridge ().drum ().baseMidiNote ();
        final List<Integer> keys = new ArrayList<> (Collections.nCopies (128, Integer.valueOf (-1)));
        for (int row = 0; row < 4; row++)
        {
            for (int column = 0; column < 4; column++)
            {
                final int note = base + row * 4 + column;
                if (note <= 127)
                    keys.set (DEFAULT_BASE + row * 8 + column, Integer.valueOf (note));
            }
        }
        final var settings = snapshot.bridge ().controllerSettings ();
        final List<Integer> velocities = settings.accentEnabled () ? IntStream.range (0, 128).map (velocity -> velocity == 0 ? 0 : settings.accentVelocity ()).boxed ().toList () : IDENTITY_VELOCITIES;
        return new DesiredNoteInputTranslation (true, keys, velocities);
    }


    /** Whether observed target, requested base, and applied native map agree. */
    public static boolean mappingApplied (final ControllerSnapshot snapshot)
    {
        final DesiredNoteInputTranslation desired = translation (snapshot);
        return desired.allowsNotes () && desired.equals (snapshot.bridge ().layout ().appliedNoteTranslation ());
    }


    private static boolean enabled (final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final NoteViewSnapshot note = snapshot.bridge ().noteView ();
        final DrumContextSnapshot drum = snapshot.bridge ().drum ();
        return snapshot.bridge ().layout ().drumLayoutActive () && snapshot.bridge ().layout ().drumControllerEngaged () &&
            selected.exists () && selected.canHoldNotes () && note.drumControllerApplicable () &&
            selected.generation () == note.targetGeneration () && selected.channelId ().equals (note.targetChannelId ()) && selected.position () == note.trackPosition () &&
            drum.available () && drum.modelAligned () && drum.generation () > 0 &&
            drum.targetGeneration () == selected.generation () && drum.targetChannelId ().equals (selected.channelId ());
    }


    private static boolean playPadsHeld (final ControllerSnapshot snapshot)
    {
        for (int row = 0; row < 4; row++)
        {
            for (int column = 0; column < 4; column++)
            {
                if (snapshot.pressedControls ().contains (PushControlIds.pad (row * 8 + column + 1)))
                    return true;
            }
        }
        return false;
    }


    private List<CoreEffect> move (final ControllerSnapshot snapshot, final int base)
    {
        final DrumContextSnapshot drum = snapshot.bridge ().drum ();
        if (base == snapshot.bridge ().layout ().drumBaseMidiNote () && base == drum.baseMidiNote () && mappingApplied (snapshot))
        {
            this.notification = null;
            return List.of (rangeNotification (base));
        }
        this.notification = new PendingNotification (drum.targetGeneration (), drum.targetChannelId (), drum.deviceId (), base, snapshot.revision ());
        return List.of (new SetDrumBankPositionEffect (drum.generation (), drum.targetChannelId (), base, false));
    }


    private List<CoreEffect> acknowledgedNotification (final ControllerSnapshot snapshot)
    {
        if (this.notification == null || snapshot.revision () <= this.notification.requestRevision () || snapshot.bridge ().layout ().drumBaseMidiNote () != this.notification.base () || snapshot.bridge ().drum ().baseMidiNote () != this.notification.base ())
            return List.of ();
        final int base = this.notification.base ();
        this.notification = null;
        return List.of (rangeNotification (base));
    }


    private static ShowHostNotificationEffect rangeNotification (final int base)
    {
        return new ShowHostNotificationEffect ("Offset: " + (base - DEFAULT_BASE) + " (" + NOTE_NAMES.get (base % 12) + (base / 12 - 2) + ")");
    }


    private record PendingNotification (long targetGeneration, String targetChannelId, String deviceId, int base, long requestRevision)
    {
        private boolean sameTarget (final ControllerSnapshot snapshot)
        {
            final DrumContextSnapshot drum = snapshot.bridge ().drum ();
            return this.targetGeneration == drum.targetGeneration () && this.targetChannelId.equals (drum.targetChannelId ()) && this.deviceId.equals (drum.deviceId ());
        }
    }


    private record PendingPosition (long contextGeneration, long targetGeneration, String targetChannelId, String deviceId, int originalBase, int requestedBase)
    {
        private static PendingPosition capture (final ControllerSnapshot snapshot, final int requested)
        {
            final DrumContextSnapshot drum = snapshot.bridge ().drum ();
            return new PendingPosition (drum.generation (), drum.targetGeneration (), drum.targetChannelId (), drum.deviceId (), snapshot.bridge ().layout ().drumBaseMidiNote (), requested);
        }


        private boolean matches (final ControllerSnapshot snapshot)
        {
            final DrumContextSnapshot drum = snapshot.bridge ().drum ();
            return this.contextGeneration == drum.generation () && this.targetGeneration == drum.targetGeneration () && this.targetChannelId.equals (drum.targetChannelId ()) && this.deviceId.equals (drum.deviceId ()) && this.originalBase == snapshot.bridge ().layout ().drumBaseMidiNote ();
        }
    }
}
