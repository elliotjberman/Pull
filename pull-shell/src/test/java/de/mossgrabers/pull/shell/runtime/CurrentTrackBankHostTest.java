// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.pull.core.api.CurrentTrackBankSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackTarget;
import de.mossgrabers.pull.core.api.effect.CurrentTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.NavigateTrackParentEffect;
import de.mossgrabers.pull.core.api.effect.SetCurrentTrackBooleanEffect;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurrentTrackBankHostTest
{
    @Test
    void publishesEightCurrentBankTracksIndependentlyOfMainSessionBank ()
    {
        final Fixture f = new Fixture ();
        f.current = f.effects;
        f.effects.offset = 8;
        f.effects.tracks[0].armed = true;
        f.effects.tracks[0].vu = 64;
        f.host.refresh ();
        final var bank = f.host.snapshot ();
        assertEquals (8, bank.tracks ().size ());
        assertEquals (8, bank.offset ());
        assertEquals ("effect-0", bank.tracks ().getFirst ().track ().channelId ());
        assertTrue (bank.tracks ().getFirst ().track ().recordArmed ());
        assertEquals (64.0 / 127, bank.tracks ().getFirst ().vuLeft ());
        assertEquals ("main-0", bank.cursorChannelId ());
        assertFalse (bank.cursorPinned ());
    }

    @Test
    void absoluteWriteRemainsARequestUntilLaterHostAdvancement ()
    {
        final Fixture f = new Fixture ();
        f.host.refresh ();
        final long generation = f.host.snapshot ().generation ();
        f.host.apply (f.host.prepare (new SetCurrentTrackBooleanEffect (f.target (), SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, true)));
        assertEquals (List.of ("main-0:arm:true"), f.calls);
        f.host.refresh ();
        assertFalse (f.host.snapshot ().tracks ().getFirst ().track ().recordArmed ());
        f.advanceHost ();
        f.host.refresh ();
        assertTrue (f.host.snapshot ().tracks ().getFirst ().track ().recordArmed ());
        assertEquals (generation, f.host.snapshot ().generation ());
    }

    @Test
    void selectionDuplicateAndDeleteOperateOnExactEffectBankSlot ()
    {
        final Fixture f = new Fixture ();
        f.current = f.effects;
        f.host.refresh ();
        for (final var action: List.of (CurrentTrackActionEffect.Action.SELECT, CurrentTrackActionEffect.Action.DUPLICATE, CurrentTrackActionEffect.Action.REMOVE))
            f.host.apply (f.host.prepare (new CurrentTrackActionEffect (f.target (), action)));
        assertEquals (List.of ("effect-0:select", "effect-0:duplicate", "effect-0:remove"), f.calls);
        assertTrue (f.host.snapshot ().tracks ().getFirst ().track ().exists ());
        assertFalse (f.host.snapshot ().tracks ().getFirst ().track ().selected ());
    }

    @Test
    void applyRejectsBankSwitchScrollReplacementAndPositionChangeBeforeRefresh ()
    {
        for (int change = 0; change < 4; change++)
        {
            final Fixture f = new Fixture ();
            f.host.refresh ();
            final var prepared = f.host.prepare (new CurrentTrackActionEffect (f.target (), CurrentTrackActionEffect.Action.REMOVE));
            switch (change)
            {
                case 0 -> f.current = f.upper;
                case 1 -> f.main.offset++;
                case 2 -> f.main.tracks[0].id = "replacement";
                case 3 -> f.main.tracks[0].position++;
            }
            f.host.apply (prepared);
            assertTrue (f.calls.isEmpty (), "stale case " + change);
            f.host.refresh ();
            assertThrows (IllegalArgumentException.class, () -> f.host.prepare (prepared.effect ()));
        }
    }

    @Test
    void emptySlotAndUnknownBankFailClosed ()
    {
        final Fixture f = new Fixture ();
        f.host.refresh ();
        final var old = f.target ();
        f.main.tracks[0].exists = false;
        f.host.refresh ();
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (new CurrentTrackActionEffect (old, CurrentTrackActionEffect.Action.DUPLICATE)));
        f.current = f.newBank ("unregistered");
        f.host.refresh ();
        assertEquals (CurrentTrackBankSnapshot.empty (), f.host.snapshot ());
    }

    @Test
    void groupEntryRequiresAlreadySelectedLiveCursorAndNeverSchedulesSelection ()
    {
        final Fixture f = new Fixture ();
        f.main.tracks[0].group = true;
        f.main.tracks[0].selected = true;
        f.host.refresh ();
        final var enter = f.host.prepare (new CurrentTrackActionEffect (f.target (), CurrentTrackActionEffect.Action.ENTER_SELECTED_GROUP));
        f.main.tracks[0].selected = false;
        f.host.apply (enter);
        assertTrue (f.calls.isEmpty ());
        f.main.tracks[0].selected = true;
        f.cursorId = "other";
        f.host.apply (enter);
        assertTrue (f.calls.isEmpty ());
        f.cursorId = "main-0";
        f.host.apply (enter);
        assertEquals (List.of ("main-0:enter"), f.calls);
        assertFalse (f.main.tracks[0].expanded, "UI expansion is independent of the cursor child selector");
    }

    @Test
    void rejectsUnselectedAndNonGroupEntryAtPreparation ()
    {
        final Fixture f = new Fixture ();
        f.host.refresh ();
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (new CurrentTrackActionEffect (f.target (), CurrentTrackActionEffect.Action.ENTER_SELECTED_GROUP)));
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (new SetCurrentTrackBooleanEffect (f.target (), SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, true)));
        f.main.tracks[0].group = true;
        f.main.tracks[0].selected = false;
        f.host.refresh ();
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (new CurrentTrackActionEffect (f.target (), CurrentTrackActionEffect.Action.ENTER_SELECTED_GROUP)));
    }

    @Test
    void parentTargetsMainCursorWhileCurrentBankIsEffectBank ()
    {
        final Fixture f = new Fixture ();
        f.current = f.effects;
        f.main.parent = true;
        f.cursorParent = true;
        f.host.refresh ();
        final var bank = f.host.snapshot ();
        f.host.apply (f.host.prepare (new NavigateTrackParentEffect (bank.parentGeneration (), bank.cursorChannelId ())));
        assertEquals (List.of ("main:parent"), f.calls);
    }

    @Test
    void parentApplyRejectsCursorPinIdentityAndMainWindowChangesBeforeRefresh ()
    {
        for (int change = 0; change < 3; change++)
        {
            final Fixture f = new Fixture ();
            f.current = f.effects;
            f.main.parent = true;
        f.cursorParent = true;
            f.host.refresh ();
            final var bank = f.host.snapshot ();
            final var action = f.host.prepare (new NavigateTrackParentEffect (bank.parentGeneration (), bank.cursorChannelId ()));
            if (change == 0) f.cursorId = "other";
            else if (change == 1) f.pinned = true;
            else f.main.offset++;
            f.host.apply (action);
            assertTrue (f.calls.isEmpty ());
        }
    }

    @Test
    void parentAvailabilityBelongsToActualCursorRatherThanFirstVisibleMainTrack ()
    {
        final Fixture f = new Fixture ();
        f.pinned = true;
        f.main.parent = false;
        f.cursorParent = true;
        f.host.refresh ();
        final var bank = f.host.snapshot ();
        assertTrue (bank.parentAvailable ());
        final var action = f.host.prepare (new NavigateTrackParentEffect (bank.parentGeneration (), bank.cursorChannelId ()));
        f.cursorParent = false;
        f.host.apply (action);
        assertTrue (f.calls.isEmpty ());
        f.host.refresh ();
        assertFalse (f.host.snapshot ().parentAvailable ());
    }

    @Test
    void canopyRejectsMoreThanThreeInstalledBanks ()
    {
        final Fixture f = new Fixture ();
        assertThrows (IllegalArgumentException.class, () -> new CurrentTrackBankHost (f.model, List.of (f.main.proxy, f.upper.proxy, f.newBank ("extra").proxy)));
    }

    private static final class Fixture
    {
        final List<String> calls = new ArrayList<> ();
        final List<Runnable> pending = new ArrayList<> ();
        final Bank main = this.newBank ("main");
        final Bank upper = this.newBank ("upper");
        final Bank effects = this.newBank ("effect");
        Bank current = this.main;
        String cursorId = "main-0";
        boolean pinned;
        boolean cursorParent;
        final ICursorTrack cursor = proxy (ICursorTrack.class, (ignored, method, args) -> switch (method.getName ())
        {
            case "doesExist" -> true;
            case "getChannelID" -> this.cursorId;
            case "isPinned" -> this.pinned;
            case "hasParent" -> this.cursorParent;
            default -> defaultValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (ignored, method, args) -> switch (method.getName ())
        {
            case "getCurrentTrackBank" -> this.current.proxy;
            case "getTrackBank" -> this.main.proxy;
            case "getEffectTrackBank" -> this.effects.proxy;
            case "getCursorTrack" -> this.cursor;
            case "getValueChanger" -> new TwosComplementValueChanger (128, 1);
            default -> defaultValue (method.getReturnType ());
        });
        final CurrentTrackBankHost host = new CurrentTrackBankHost (this.model, List.of (this.main.proxy, this.upper.proxy));

        Bank newBank (final String name) { return new Bank (name, this.calls, this.pending); }
        CurrentTrackTarget target ()
        {
            final var bank = this.host.snapshot ();
            return new CurrentTrackTarget (bank.generation (), bank.bankId (), 0, bank.tracks ().getFirst ().track ().channelId ());
        }
        void advanceHost () { List.copyOf (this.pending).forEach (Runnable::run); this.pending.clear (); }
    }

    private static final class Bank
    {
        int offset;
        boolean parent;
        final Track[] tracks = new Track[8];
        final ITrackBank proxy;
        Bank (final String name, final List<String> calls, final List<Runnable> pending)
        {
            for (int index = 0; index < 8; index++) this.tracks[index] = new Track (name + "-" + index, index, calls, pending);
            this.proxy = proxy (ITrackBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> this.tracks[(Integer) args[0]].proxy;
                case "getScrollPosition" -> this.offset;
                case "hasParent" -> this.parent;
                case "selectParent" -> { calls.add (name + ":parent"); yield null; }
                default -> defaultValue (method.getReturnType ());
            });
        }
    }

    private static final class Track
    {
        String id;
        int position;
        boolean exists = true;
        boolean selected;
        boolean group;
        boolean expanded;
        boolean armed;
        int vu;
        final ITrack proxy;
        Track (final String id, final int position, final List<String> calls, final List<Runnable> pending)
        {
            this.id = id; this.position = position;
            this.proxy = proxy (ITrack.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "doesExist" -> this.exists;
                case "getChannelID", "getName" -> this.id;
                case "getPosition" -> this.position;
                case "isSelected" -> this.selected;
                case "isActivated" -> true;
                case "isRecArm" -> this.armed;
                case "isGroup" -> this.group;
                case "isGroupExpanded" -> this.expanded;
                case "getVuLeft", "getVuRight" -> this.vu;
                case "getColor" -> ColorEx.BLUE;
                case "getType" -> this.group ? ChannelType.GROUP : ChannelType.INSTRUMENT;
                case "select", "duplicate", "remove", "enter" -> { calls.add (this.id + ":" + method.getName ()); yield null; }
                case "setRecArm" -> { final boolean value = (Boolean) args[0]; calls.add (this.id + ":arm:" + value); pending.add (() -> this.armed = value); yield null; }
                case "setGroupExpanded" -> { final boolean value = (Boolean) args[0]; calls.add (this.id + ":expanded:" + value); pending.add (() -> this.expanded = value); yield null; }
                default -> defaultValue (method.getReturnType ());
            });
        }
    }

    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?>[] { type }, handler));
    }
    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        return null;
    }
}
