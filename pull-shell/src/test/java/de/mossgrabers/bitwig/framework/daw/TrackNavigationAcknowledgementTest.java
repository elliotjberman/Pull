// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.IntegerValue;
import com.bitwig.extension.controller.api.SettableIntegerValue;
import com.bitwig.extension.controller.api.StringValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

import de.mossgrabers.bitwig.framework.daw.data.CursorTrackImpl;
import de.mossgrabers.bitwig.framework.daw.data.bank.TrackBankImpl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;

import org.junit.jupiter.api.Test;

import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedProxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedValue;
import static org.junit.jupiter.api.Assertions.*;


class TrackNavigationAcknowledgementTest
{
    @Test
    void pageSelectionWaitsForBothBankAndTrackReadback ()
    {
        final Fixture f = new Fixture ();
        f.bank.selectNextPage ();
        assertEquals (4, f.requestedOffset);
        f.poll (30);
        assertTrue (f.selected.isEmpty (), "600 ms without host acknowledgement cannot select an old row");
        f.hostOffset = 4;
        f.observedOffset = 4;
        f.poll (10);
        assertTrue (f.selected.isEmpty (), "the offset alone cannot acknowledge stale track proxies");
        f.publishTracks ();
        f.poll (1);
        assertEquals (List.of ("track-4"), f.selected);
    }

    @Test
    void rapidPagePressesAccumulateAndIgnoreIntermediateAcknowledgement ()
    {
        final Fixture f = new Fixture ();
        f.bank.selectNextPage ();
        f.bank.selectNextPage ();
        assertEquals (4, f.requestedOffset, "the second destination remains value-only until the first scroll is acknowledged");
        f.poll (10);
        assertTrue (f.selected.isEmpty ());
        f.hostOffset = 4;
        f.observedOffset = 4;
        f.publishTracks ();
        f.poll (5);
        assertTrue (f.selected.isEmpty (), "the first page acknowledgement must not consume the latest intent");
        assertEquals (8, f.requestedOffset, "the latest destination is submitted only after the first acknowledgement");
        f.hostOffset = 8;
        f.observedOffset = 8;
        f.publishTracks ();
        f.poll (1);
        assertEquals (List.of ("track-8"), f.selected);
    }

    @Test
    void rapidPageReversalUsesPendingDirectionAtBothObservedBoundaries ()
    {
        for (final int origin: List.of (0, 8))
        {
            final Fixture f = new Fixture ();
            f.hostOffset = origin;
            f.observedOffset = origin;
            f.publishTracks ();
            if (origin == 0)
            {
                f.bank.selectNextPage ();
                f.bank.selectPreviousPage ();
            }
            else
            {
                f.bank.selectPreviousPage ();
                f.bank.selectNextPage ();
            }
            assertEquals (4, f.requestedOffset, "the opposite press cannot replace a native scroll already in flight");
            f.poll (10);
            assertTrue (f.selected.isEmpty (), "the cached original page cannot acknowledge a return before the outward scroll applies");
            f.hostOffset = 4;
            f.observedOffset = 4;
            f.publishTracks ();
            f.poll (5);
            assertTrue (f.selected.isEmpty (), "an intermediate outward move must not consume the return request");
            assertEquals (origin, f.requestedOffset, "the return is submitted after the outward scroll is acknowledged");
            f.hostOffset = origin;
            f.observedOffset = origin;
            f.publishTracks ();
            f.poll (1);
            assertEquals (List.of ("track-" + origin), f.selected);
        }
    }

    @Test
    void replacingPendingPagesCannotExtendTheOriginalAcknowledgementDeadline ()
    {
        final Fixture f = new Fixture ();
        f.bank.selectNextPage ();
        f.poll (100);
        f.bank.selectNextPage ();
        f.poll (55);
        f.hostOffset = 4;
        f.observedOffset = 4;
        f.publishTracks ();
        f.poll (5);
        assertEquals (4, f.requestedOffset, "a late acknowledgement cannot submit the abandoned replacement");
        assertTrue (f.selected.isEmpty ());
        assertTrue (f.tasks.isEmpty ());
    }

    @Test
    void projectCursorAndWindowChangesCancelPageContinuation ()
    {
        for (final String changed: List.of ("project", "owner", "cursor", "window", "disabled", "closed"))
        {
            final Fixture f = new Fixture ();
            f.bank.selectNextPage ();
            switch (changed)
            {
                case "project" -> f.project = "other-project";
                case "cursor" -> f.cursorId = "other-track";
                case "owner" -> f.parentId = "other-group";
                case "window" -> f.bank.scrollTo (2);
                case "disabled" -> f.bank.enableObservers (false);
                case "closed" -> f.close ();
                default -> fail ();
            }
            f.poll (1);
            f.project = "project";
            f.parentId = "group";
            f.cursorId = "track-0";
            f.hostOffset = 4;
            f.observedOffset = 4;
            f.publishTracks ();
            f.poll (5);
            assertTrue (f.selected.isEmpty (), changed + " must retire the old request permanently");
        }
    }

    @Test
    void pageTimeoutDoesNotSelectAndLaterRequestStillWorks ()
    {
        final Fixture f = new Fixture ();
        f.bank.selectNextPage ();
        f.poll (160);
        f.hostOffset = 4;
        f.observedOffset = 4;
        f.publishTracks ();
        f.poll (2);
        assertTrue (f.selected.isEmpty ());
        f.bank.selectNextPage ();
        f.hostOffset = 8;
        f.observedOffset = 8;
        f.publishTracks ();
        f.poll (2);
        assertEquals (List.of ("track-8"), f.selected);
    }

    @Test
    void previousItemUsesLastRowOfAcknowledgedPreviousPage ()
    {
        final Fixture f = new Fixture ();
        f.hostOffset = 4;
        f.observedOffset = 4;
        f.publishTracks ();
        f.bank.getItem (0).setSelected (true);
        f.bank.selectPreviousItem ();
        f.poll (5);
        assertTrue (f.selected.isEmpty ());
        f.hostOffset = 0;
        f.observedOffset = 0;
        f.publishTracks ();
        f.poll (1);
        assertEquals (List.of ("track-3"), f.selected);
    }

    @Test
    void enteringGroupWaitsForExactCursorAndNewerGroupReplacesOlder ()
    {
        final Fixture f = new Fixture ();
        f.groups = true;
        f.cursorId = "previous-track";
        f.bank.getItem (0).enter ();
        f.bank.getItem (1).enter ();
        assertEquals (List.of ("track-0"), f.selected, "only one native selection may be in flight");
        f.poll (20);
        assertTrue (f.entered.isEmpty ());
        // The host publishes the older selection first. It must not resurrect entry into group 0.
        f.cursorId = "track-0";
        f.poll (1);
        assertTrue (f.entered.isEmpty ());
        assertEquals (List.of ("track-0", "track-1"), f.selected);
        f.cursorId = "track-1";
        f.poll (1);
        assertEquals (List.of ("track-1"), f.entered);
    }

    @Test
    void replacedSourceProjectAndDeadlineDoNotEnterLateGroup ()
    {
        for (final String changed: List.of ("project", "source", "cursor", "timeout", "closed"))
        {
            final Fixture f = new Fixture ();
            f.groups = true;
            f.bank.getItem (1).enter ();
            switch (changed)
            {
                case "project" -> f.project = "other-project";
                case "source" -> f.observedTrackOffset = 4;
                case "cursor" -> f.cursorId = "unrelated";
                case "timeout" -> f.poll (160);
                case "closed" -> f.close ();
                default -> fail ();
            }
            f.poll (1);
            f.project = "project";
            f.observedTrackOffset = 0;
            f.cursorId = "track-1";
            f.poll (2);
            assertTrue (f.entered.isEmpty (), changed + " must prevent late group entry");
        }
    }

    @Test
    void returnToStillObservedSelectedGroupWaitsForInflightSelection ()
    {
        final Fixture f = new Fixture ();
        f.groups = true;
        f.bank.getItem (0).setSelected (true);
        f.bank.getItem (1).enter ();
        f.bank.getItem (0).enter ();
        f.poll (10);
        assertEquals (List.of ("track-1"), f.selected);
        assertTrue (f.entered.isEmpty (), "cached group 0 is not acknowledgement of the pending return");
        f.cursorId = "track-1";
        f.poll (1);
        assertEquals (List.of ("track-1", "track-0"), f.selected);
        assertTrue (f.entered.isEmpty ());
        f.cursorId = "track-0";
        f.poll (1);
        assertEquals (List.of ("track-0"), f.entered);
    }

    @Test
    void repeatedGroupIntentsUseBoundedStateWithoutExtendingDeadline ()
    {
        final Fixture f = new Fixture ();
        final GroupNavigationHost entries = new GroupNavigationHost (f.host, f.cursor, () -> f.project);
        for (int i = 0; i < 100; i++)
        {
            final String target = "group-" + i;
            assertTrue (entries.enter (target, () -> true, () -> f.selected.add (target), () -> f.entered.add (target)));
            f.poll (1);
        }
        assertEquals (List.of ("group-0"), f.selected, "replacement must remain value-only until the submitted target is observed");
        f.poll (51);
        f.cursorId = "group-0";
        f.poll (1);
        assertTrue (f.entered.isEmpty ());
        assertEquals (List.of ("group-0"), f.selected, "replacements cannot extend the original deadline");
        assertTrue (entries.enter ("retry", () -> true, () -> f.selected.add ("retry"), () -> f.entered.add ("retry")));
        f.cursorId = "retry";
        f.poll (1);
        assertEquals (List.of ("retry"), f.entered);
    }

    private static final class Fixture
    {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<> ();
        private final List<String> selected = new ArrayList<> ();
        private final List<String> entered = new ArrayList<> ();
        private String project = "project";
        private String parentId = "group";
        private String cursorId = "track-0";
        private int requestedOffset;
        private int hostOffset;
        private int observedOffset;
        private int observedTrackOffset;
        private boolean groups;
        private final Track root = proxy (Track.class, (p, method, args) -> switch (method.getName ()) {
            case "channelId" -> value (StringValue.class, () -> "root");
            case "exists" -> value (BooleanValue.class, () -> true);
            default -> relaxedValue (method.getReturnType ());
        });
        private final Track parent = proxy (Track.class, (p, method, args) -> switch (method.getName ()) {
            case "channelId" -> value (StringValue.class, () -> this.parentId);
            case "exists" -> value (BooleanValue.class, () -> true);
            default -> relaxedValue (method.getReturnType ());
        });
        private final DeviceBank devices = proxy (DeviceBank.class, (p, method, args) -> "getItemAt".equals (method.getName ())
            ? relaxedProxy (Device.class) : relaxedValue (method.getReturnType ()));
        private final CursorTrack cursor = proxy (CursorTrack.class, (p, method, args) -> switch (method.getName ()) {
            case "channelId" -> value (StringValue.class, () -> this.cursorId);
            case "exists" -> value (BooleanValue.class, () -> true);
            case "createParentTrack" -> this.parent;
            case "createDeviceBank" -> this.devices;
            case "selectFirstChild" -> { this.entered.add (this.cursorId); yield null; }
            default -> relaxedValue (method.getReturnType ());
        });
        private final ControllerHost controller = proxy (ControllerHost.class, (p, method, args) -> {
            if ("scheduleTask".equals (method.getName ()))
            {
                this.tasks.add ((Runnable) args[0]);
                return null;
            }
            return relaxedValue (method.getReturnType ());
        });
        private final HostImpl host = new HostImpl (this.controller);
        private final TrackBank rawBank = proxy (TrackBank.class, (p, method, args) -> switch (method.getName ()) {
            case "getItemAt" -> this.track ((int) args[0]);
            case "scrollPosition" -> proxy (SettableIntegerValue.class, (v, m, a) -> {
                if ("get".equals (m.getName ())) return this.observedOffset;
                if ("set".equals (m.getName ())) this.requestedOffset = (int) a[0];
                return relaxedValue (m.getReturnType ());
            });
            case "itemCount" -> value (IntegerValue.class, () -> 12);
            case "canScrollForwards" -> value (BooleanValue.class, () -> this.observedOffset < 8);
            case "canScrollBackwards" -> value (BooleanValue.class, () -> this.observedOffset > 0);
            case "scrollPageForwards" -> { this.requestedOffset = this.observedOffset + 4; yield null; }
            case "scrollPageBackwards" -> { this.requestedOffset = this.observedOffset - 4; yield null; }
            default -> relaxedValue (method.getReturnType ());
        });
        private final TrackBankImpl bank;
        private final GroupNavigationHost groupEntries;

        private Fixture ()
        {
            final IValueChanger values = relaxedProxy (IValueChanger.class);
            final CursorTrackImpl cursorModel = new CursorTrackImpl (null, this.host, values, this.cursor, this.root, null, null, 0, 0, 0, 0);
            this.bank = new TrackBankImpl (this.host, null, values, this.rawBank, cursorModel, this.root, 4, 0, 0);
            this.groupEntries = new GroupNavigationHost (this.host, this.cursor, () -> this.project);
            this.bank.configurePendingOperations (() -> this.project, false, this.groupEntries);
        }

        private Track track (final int index)
        {
            return proxy (Track.class, (p, method, args) -> switch (method.getName ()) {
                case "channelId" -> value (StringValue.class, () -> "track-" + (this.observedTrackOffset + index));
                case "position" -> value (IntegerValue.class, () -> this.observedTrackOffset + index);
                case "exists" -> value (BooleanValue.class, () -> true);
                case "createDeviceBank" -> this.devices;
                case "isGroup" -> value (BooleanValue.class, () -> this.groups);
                case "createParentTrack" -> this.parent;
                case "selectInEditor" -> { this.selected.add ("track-" + (this.hostOffset + index)); yield null; }
                default -> relaxedValue (method.getReturnType ());
            });
        }

        private void close ()
        {
            this.bank.closePendingOperations ();
            this.groupEntries.close ();
        }

        private void publishTracks ()
        {
            this.observedTrackOffset = this.hostOffset;
        }

        private void poll (final int count)
        {
            for (int i = 0; i < count && !this.tasks.isEmpty (); i++)
                this.tasks.remove ().run ();
        }
    }

    private static <T> T value (final Class<T> type, final Supplier<?> observed)
    {
        return proxy (type, (p, method, args) -> "get".equals (method.getName ()) ? observed.get () : relaxedValue (method.getReturnType ()));
    }
}
