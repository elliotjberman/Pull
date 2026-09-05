// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.CurrentTrackBankSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackTarget;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.CurrentTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.NavigateTrackParentEffect;
import de.mossgrabers.pull.core.api.effect.SetCurrentTrackBooleanEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Eight current-bank slots from the two installed main windows and one effect bank. No new
 * Bitwig proxies are created. Cursor-parent navigation has a separate main-bank/cursor fence.
 */
final class CurrentTrackBankHost
{
    private static final int MAX_INSTALLED_BANKS = 3;
    private final IModel model;
    private final Map<ITrackBank, String> banks = new IdentityHashMap<> ();
    private CurrentTrackBankSnapshot snapshot = CurrentTrackBankSnapshot.empty ();
    private Window identity;
    private ParentIdentity parentIdentity;
    private long generation;
    private long parentGeneration;

    CurrentTrackBankHost (final IModel model, final Collection<ITrackBank> mainBanks)
    {
        this.model = Objects.requireNonNull (model, "model");
        for (final ITrackBank bank: mainBanks)
            this.register (bank);
        this.register (model.getTrackBank ());
        this.register (model.getEffectTrackBank ());
    }

    private void register (final ITrackBank bank)
    {
        if (bank == null || this.banks.containsKey (bank))
            return;
        if (this.banks.size () >= MAX_INSTALLED_BANKS)
            throw new IllegalArgumentException ("current-track canopy exceeds three installed banks");
        this.banks.put (bank, "current-bank-" + this.banks.size ());
    }

    boolean refresh ()
    {
        final Window current = this.window (this.model.getCurrentTrackBank ());
        final ParentIdentity parent = this.parent ();
        if (!Objects.equals (current, this.identity))
        {
            this.identity = current;
            this.generation++;
        }
        if (!Objects.equals (parent, this.parentIdentity))
        {
            this.parentIdentity = parent;
            this.parentGeneration++;
        }
        final CurrentTrackBankSnapshot next = current == null ? CurrentTrackBankSnapshot.empty () : this.capture (current, parent);
        final boolean changed = !next.equals (this.snapshot);
        this.snapshot = next;
        return changed;
    }

    CurrentTrackBankSnapshot snapshot ()
    {
        return this.snapshot;
    }

    PreparedTrackAction prepare (final CurrentTrackActionEffect effect)
    {
        final CurrentTrackSnapshot track = this.requireTrack (effect.target ());
        if (effect.action () == CurrentTrackActionEffect.Action.ENTER_SELECTED_GROUP && (!track.track ().selected () || !isGroup (track) || !effect.target ().channelId ().equals (this.snapshot.cursorChannelId ())))
            throw new IllegalArgumentException ("group entry requires the already-selected aligned cursor");
        return new PreparedTrackAction (effect);
    }

    PreparedBoolean prepare (final SetCurrentTrackBooleanEffect effect)
    {
        final CurrentTrackSnapshot track = this.requireTrack (effect.target ());
        if (effect.property () == SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED && !isGroup (track))
            throw new IllegalArgumentException ("group expansion requires a group track");
        return new PreparedBoolean (effect);
    }

    PreparedParent prepare (final NavigateTrackParentEffect effect)
    {
        if (!this.snapshot.parentAvailable () || effect.parentGeneration () != this.snapshot.parentGeneration () || !effect.cursorChannelId ().equals (this.snapshot.cursorChannelId ()))
            throw new IllegalArgumentException ("main parent navigation target is stale");
        return new PreparedParent (effect);
    }

    void apply (final PreparedTrackAction action)
    {
        final CurrentTrackActionEffect effect = action.effect ();
        final ITrack track = this.liveTrack (effect.target ());
        if (track == null)
            return;
        switch (effect.action ())
        {
            case SELECT -> track.select ();
            case DUPLICATE -> track.duplicate ();
            case REMOVE -> track.remove ();
            case ENTER_SELECTED_GROUP ->
            {
                // TrackImpl.enter schedules an unfenced select-first-child when not selected.
                // Requiring the live selected group and aligned cursor admits only its direct path.
                final ICursorTrack cursor = this.model.getCursorTrack ();
                if (track.isGroup () && track.isSelected () && cursor.doesExist () && effect.target ().channelId ().equals (cursor.getChannelID ()))
                    track.enter ();
            }
        }
    }

    void apply (final PreparedBoolean action)
    {
        final SetCurrentTrackBooleanEffect effect = action.effect ();
        final ITrack track = this.liveTrack (effect.target ());
        if (track == null)
            return;
        switch (effect.property ())
        {
            case RECORD_ARMED -> track.setRecArm (effect.enabled ());
            case GROUP_EXPANDED ->
            {
                if (track.isGroup ())
                    track.setGroupExpanded (effect.enabled ());
            }
        }
    }

    void apply (final PreparedParent action)
    {
        final NavigateTrackParentEffect effect = action.effect ();
        final ParentIdentity live = this.parent ();
        if (effect.parentGeneration () == this.parentGeneration && live != null && live.equals (this.parentIdentity) && live.available () && effect.cursorChannelId ().equals (live.cursorId ()))
            this.model.getTrackBank ().selectParent ();
    }

    private CurrentTrackSnapshot requireTrack (final CurrentTrackTarget target)
    {
        if (target.generation () != this.snapshot.generation () || !target.bankId ().equals (this.snapshot.bankId ()))
            throw new IllegalArgumentException ("current-track bank target is stale");
        final CurrentTrackSnapshot track = this.snapshot.tracks ().get (target.trackIndex ());
        if (!track.track ().exists () || !target.channelId ().equals (track.track ().channelId ()))
            throw new IllegalArgumentException ("current-track slot target is stale");
        return track;
    }

    private ITrack liveTrack (final CurrentTrackTarget target)
    {
        final ITrackBank bank = this.model.getCurrentTrackBank ();
        final Window live = this.window (bank);
        if (target.generation () != this.generation || live == null || !live.equals (this.identity) || !target.bankId ().equals (live.bankId ()))
            return null;
        final ITrack track = bank.getItem (target.trackIndex ());
        return track.doesExist () && target.channelId ().equals (track.getChannelID ()) ? track : null;
    }

    private CurrentTrackBankSnapshot capture (final Window current, final ParentIdentity parent)
    {
        final List<CurrentTrackSnapshot> tracks = new ArrayList<> (CurrentTrackBankSnapshot.CAPACITY);
        final ITrackBank bank = this.model.getCurrentTrackBank ();
        final double maximum = Math.max (1, this.model.getValueChanger ().getUpperBound () - 1);
        for (int index = 0; index < CurrentTrackBankSnapshot.CAPACITY; index++)
        {
            final ITrack track = bank.getItem (index);
            if (!track.doesExist () || track.getChannelID ().isBlank () || track.getPosition () < 0)
                tracks.add (CurrentTrackSnapshot.empty ());
            else
                tracks.add (new CurrentTrackSnapshot (new SessionTrackSnapshot (track.getChannelID (), track.getPosition (), track.getName (128), true, track.isSelected (), track.isActivated (), track.isRecArm (), track.isMute (), track.isSolo (), track.isPlaying (), SessionBankHost.toTrackType (track.getType ()), SessionBankHost.toRgb (track.getColor ())), track.isGroupExpanded (), clamp (track.getVuLeft () / maximum), clamp (track.getVuRight () / maximum)));
        }
        final ICursorTrack cursor = this.model.getCursorTrack ();
        return new CurrentTrackBankSnapshot (this.generation, current.bankId (), current.offset (), tracks, cursor.doesExist () ? cursor.getChannelID () : "", cursor.isPinned (), this.parentGeneration, parent != null && parent.available ());
    }

    private Window window (final ITrackBank bank)
    {
        final String bankId = this.banks.get (bank);
        if (bankId == null)
            return null;
        final List<SlotIdentity> slots = new ArrayList<> (CurrentTrackBankSnapshot.CAPACITY);
        for (int index = 0; index < CurrentTrackBankSnapshot.CAPACITY; index++)
        {
            final ITrack track = bank.getItem (index);
            slots.add (new SlotIdentity (track.doesExist () ? track.getChannelID () : "", track.doesExist () ? track.getPosition () : -1));
        }
        return new Window (bankId, Math.max (0, bank.getScrollPosition ()), List.copyOf (slots));
    }

    private ParentIdentity parent ()
    {
        final ICursorTrack cursor = this.model.getCursorTrack ();
        if (cursor == null || !cursor.doesExist ())
            return null;
        final Window main = this.window (this.model.getTrackBank ());
        return main == null ? null : new ParentIdentity (main, cursor.getChannelID (), cursor.isPinned (), cursor.hasParent ());
    }

    private static boolean isGroup (final CurrentTrackSnapshot track)
    {
        return track.track ().type () == de.mossgrabers.pull.core.api.SessionTrackType.GROUP || track.track ().type () == de.mossgrabers.pull.core.api.SessionTrackType.GROUP_OPEN;
    }

    private static double clamp (final double value)
    {
        return Math.max (0, Math.min (1, value));
    }

    private record SlotIdentity (String channelId, int position) { }
    private record Window (String bankId, int offset, List<SlotIdentity> slots) { }
    private record ParentIdentity (Window mainWindow, String cursorId, boolean pinned, boolean available) { }
    record PreparedTrackAction (CurrentTrackActionEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedBoolean (SetCurrentTrackBooleanEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedParent (NavigateTrackParentEffect effect) implements ControllerBridge.PreparedAction { }
}
