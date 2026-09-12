// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.InsertionPoint;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Scene;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.TrackBank;
import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.bitwig.framework.daw.GroupNavigationHost;
import de.mossgrabers.bitwig.framework.daw.data.CursorTrackImpl;
import de.mossgrabers.bitwig.framework.daw.data.TrackImpl;
import de.mossgrabers.bitwig.framework.daw.data.SceneImpl;
import de.mossgrabers.bitwig.framework.daw.data.bank.SlotBankImpl;
import de.mossgrabers.bitwig.framework.daw.data.bank.SceneBankImpl;
import de.mossgrabers.bitwig.framework.daw.data.bank.TrackBankImpl;
import de.mossgrabers.framework.daw.data.bank.IBank;
import de.mossgrabers.bitwig.framework.daw.data.SlotImpl;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionClipWindowSnapshot;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionLocation;
import de.mossgrabers.pull.core.api.CurrentTrackTarget;
import de.mossgrabers.pull.core.api.effect.CopySessionClipEffect;
import de.mossgrabers.pull.core.api.effect.CreateSessionClipEffect;
import de.mossgrabers.pull.core.api.effect.SessionActionEffect;
import de.mossgrabers.pull.core.api.effect.SetSessionBankPositionEffect;
import de.mossgrabers.pull.core.api.effect.CurrentTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SetCurrentTrackBooleanEffect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;


class SessionClipWindowHostTest
{
    @Test
    void trackOnlySubscriptionNeverReadsSlotOrSceneItems ()
    {
        final Fixture fixture = new Fixture (8);
        fixture.host.refresh (false);
        assertEquals (0, fixture.slotReads);
        assertEquals (0, fixture.sceneReads);
        assertEquals (SessionClipWindowSnapshot.empty (), fixture.host.snapshot ().clips ());

        fixture.host.refresh (true);
        assertEquals (64, fixture.slotReads);
        assertEquals (8, fixture.sceneReads);

        assertTrue (fixture.host.refresh (false));
        assertEquals (64, fixture.slotReads);
        assertEquals (8, fixture.sceneReads);
        assertEquals (SessionClipWindowSnapshot.empty (), fixture.host.snapshot ().clips ());
    }


    @ParameterizedTest
    @ValueSource (ints = { 4, 8 })
    void publishesTheExactBoundedWindowAndNavigation (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.host.refresh (true);
        final SessionClipWindowSnapshot clips = fixture.host.snapshot ().clips ();
        assertEquals (new SessionBankShape (8, rows), clips.shape ());
        assertEquals (8 * rows, clips.slots ().size ());
        assertEquals (rows, clips.scenes ().size ());
        assertTrue (clips.aligned ());
        assertEquals (fixture.sceneOffset + rows - 1, clips.slot (7, rows - 1).scenePosition ());
        assertEquals ("7:" + (rows - 1), clips.slot (7, rows - 1).name ());
        assertTrue (clips.scenes ().getFirst ().selected ());
        assertEquals (32, clips.trackNavigation ().itemCount ());
        assertTrue (clips.trackNavigation ().previousPage ());
        assertFalse (clips.sceneNavigation ().nextPage ());
    }


    @Test
    void commandSubmissionDoesNotBecomePlaybackUntilLaterHostAdvancement ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final long generation = fixture.host.snapshot ().generation ();
        fixture.act (fixture.location (0, 0), SessionActionEffect.Action.LAUNCH);
        assertEquals (1, fixture.launchRequests);
        assertFalse (fixture.host.refresh (true));
        assertFalse (fixture.host.snapshot ().clips ().slot (0, 0).playing ());

        fixture.advanceHost ();
        assertTrue (fixture.host.refresh (true));
        assertTrue (fixture.host.snapshot ().clips ().slot (0, 0).playing ());
        assertEquals (generation, fixture.host.snapshot ().generation ());
    }


    @ParameterizedTest
    @ValueSource (ints = { 4, 8 })
    void selectionDoesNotStealMainOrAlternateSlotAndSceneReleases (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.host.refresh (true);
        final SessionLocation slot = fixture.location (7, rows - 1);
        final SessionLocation scene = fixture.location (-1, rows - 1);
        fixture.act (slot, SessionActionEffect.Action.LAUNCH_ALT);
        fixture.act (scene, SessionActionEffect.Action.LAUNCH);
        fixture.selectedTrack = 6;
        fixture.host.refresh (true);
        fixture.act (slot, SessionActionEffect.Action.RELEASE_ALT);
        fixture.act (scene, SessionActionEffect.Action.RELEASE);
        fixture.host.releaseOutstanding ();

        assertEquals (List.of ("slot:7:" + (rows - 1) + ":launch:true:true", "scene:" + (rows - 1) + ":launch:true:false", "slot:7:" + (rows - 1) + ":launch:false:true", "scene:" + (rows - 1) + ":launch:false:false"), fixture.requests);
    }


    @ParameterizedTest
    @ValueSource (strings = { "project", "channel", "scene", "slot-bank", "slot" })
    void staleLocationsAreInertAtPreparationAndApplication (final String change)
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final SessionActionEffect effect = new SessionActionEffect (fixture.location (0, 0), SessionActionEffect.Action.LAUNCH);
        final SessionBankHost.PreparedLauncherAction prepared = fixture.host.prepare (effect);
        switch (change)
        {
            case "project" -> fixture.projectIdentity = "other-project";
            case "channel" -> fixture.firstChannelId = "replacement";
            case "scene" -> fixture.sceneOffset++;
            case "slot-bank" -> fixture.slotBankOffset++;
            case "slot" -> fixture.proxySceneOffset++;
            default -> throw new AssertionError (change);
        }
        fixture.host.apply (prepared);
        fixture.host.apply (fixture.host.prepare (effect));
        assertTrue (fixture.requests.isEmpty ());
        assertTrue (fixture.diagnostics.isEmpty ());
    }


    @Test
    void cleanupSubmitsCapturedReleaseBeforePositionWriteAndWaitsForAlignedReadBack ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final SessionLocation location = fixture.location (0, 0);
        fixture.act (location, SessionActionEffect.Action.LAUNCH_ALT);
        final SessionBankHost.PreparedLauncherAction oldLaunch = fixture.host.prepare (new SessionActionEffect (location, SessionActionEffect.Action.LAUNCH));
        fixture.host.apply (fixture.host.prepare (new SetSessionBankPositionEffect (location.generation (), location.shape (), -1, 9)));
        assertEquals (List.of ("slot:0:0:launch:true:true", "slot:0:0:launch:false:true", "scroll-scenes:9"), fixture.requests);
        assertEquals (SessionBankSnapshot.empty (), fixture.host.snapshot ());
        fixture.host.apply (oldLaunch);
        fixture.host.refresh (true);
        assertFalse (fixture.host.snapshot ().clips ().aligned ());
        fixture.sceneOffset = 9;
        fixture.host.refresh (true);
        assertFalse (fixture.host.snapshot ().clips ().aligned ());
        fixture.proxySceneOffset = 9;
        fixture.slotBankOffset = 9;
        fixture.host.refresh (true);
        assertTrue (fixture.host.snapshot ().clips ().aligned ());
        fixture.host.releaseOutstanding ();
        assertEquals (3, fixture.requests.size ());
    }


    @Test
    void externalRebindingRetiresCleanupWithoutReleasingTheReplacement ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        fixture.act (fixture.location (0, 0), SessionActionEffect.Action.LAUNCH);
        fixture.firstChannelId = "replacement";
        fixture.host.refresh (true);
        fixture.firstChannelId = "track-0";
        fixture.host.refresh (true);
        fixture.host.releaseOutstanding ();
        assertEquals (List.of ("slot:0:0:launch:true:false"), fixture.requests);
        assertEquals (1, fixture.diagnostics.size ());
        assertTrue (fixture.diagnostics.getFirst ().contains ("channelId=track-0"));
        assertTrue (fixture.diagnostics.getFirst ().contains ("RELEASE"));
    }


    @ParameterizedTest
    @ValueSource (strings = { "REMOVE", "DUPLICATE" })
    void precedingTrackMutationReleasesTheHeldLaterSlotBeforeSubmittingTheMutation (final String operation)
    {
        for (final boolean coreEffect: List.of (true, false))
        {
            final Fixture fixture = new Fixture (4);
            fixture.firstHasContent = false;
            fixture.host.refresh (true);
            fixture.currentTracks.refresh ();
            final SessionLocation held = fixture.location (7, 0);
            final var oldLaunch = fixture.host.prepare (new SessionActionEffect (held, SessionActionEffect.Action.LAUNCH));
            final var oldCreate = fixture.host.prepare (new CreateSessionClipEffect (fixture.location (0, 0), 4));
            fixture.act (held, SessionActionEffect.Action.LAUNCH_ALT);
            if (coreEffect)
                fixture.currentTracks.apply (fixture.currentTracks.prepare (new CurrentTrackActionEffect (fixture.currentTarget (), CurrentTrackActionEffect.Action.valueOf (operation))));
            else if ("REMOVE".equals (operation))
                fixture.nativeTracks[0].remove ();
            else
                fixture.nativeTracks[0].duplicate ();
            final List<String> expected = List.of ("slot:7:0:launch:true:true", "slot:7:0:launch:false:true", "track:0:" + operation.toLowerCase (java.util.Locale.ROOT));
            assertEquals (expected, fixture.requests);
            assertEquals (1, fixture.mutationGuards, "releasing a launcher must not re-enter the mutation guard");
            assertEquals (SessionBankSnapshot.empty (), fixture.host.snapshot ());
            fixture.host.refresh (true);
            assertTrue (fixture.host.snapshot ().generation () > held.generation (), "submission itself revokes old locations before host advancement");
            fixture.host.apply (oldLaunch);
            fixture.host.apply (oldCreate);
            assertEquals (expected, fixture.requests);
            fixture.advanceHost ();
            fixture.host.refresh (true);
            fixture.act (held, SessionActionEffect.Action.RELEASE_ALT);
            assertEquals (expected, fixture.requests);
            assertTrue (fixture.diagnostics.isEmpty ());
        }
    }


    @ParameterizedTest
    @ValueSource (strings = { "track", "scene", "slot" })
    void directBankWindowWritesReleaseTheSameOwnedLauncher (final String kind)
    {
        final Fixture fixture = new Fixture (4);
        final var changer = new TwosComplementValueChanger (128, 1);
        final InvocationHandler bankCalls = (ignored, method, args) -> switch (method.getName ())
        {
            case "itemCount" -> value (method.getReturnType (), () -> 40);
            case "scrollPosition" -> proxy (method.getReturnType (), (value, action, arguments) -> {
                if ("set".equals (action.getName ()))
                    fixture.requests.add ("window:" + arguments[0]);
                return nativeValue (action.getReturnType ());
            });
            default -> nativeValue (method.getReturnType ());
        };
        final CursorTrackImpl cursor = new CursorTrackImpl (null, fixture.nativeHost, changer, nativeProxy (CursorTrack.class), nativeProxy (Track.class), null, null, 0, 0, 0, 0);
        final IBank<?> bank = switch (kind)
        {
            case "track" -> new TrackBankImpl (fixture.nativeHost, null, changer, proxy (TrackBank.class, bankCalls), cursor, nativeProxy (Track.class), 0, 0, 0);
            case "scene" -> new SceneBankImpl (fixture.nativeHost, changer, proxy (SceneBank.class, bankCalls), 0, cursor);
            case "slot" -> new SlotBankImpl (fixture.nativeHost, changer, null, null, proxy (ClipLauncherSlotBank.class, bankCalls), 0);
            default -> throw new AssertionError (kind);
        };
        fixture.host.refresh (true);
        fixture.act (fixture.location (7, 3), SessionActionEffect.Action.LAUNCH_ALT);
        bank.scrollTo (-1, false);
        assertEquals (1, fixture.requests.size (), "rejected writes leave a held launcher alone");
        bank.scrollTo (9, false);
        assertEquals (List.of ("slot:7:3:launch:true:true", "slot:7:3:launch:false:true", "window:9"), fixture.requests);
        assertEquals (1, fixture.mutationGuards);
    }


    @ParameterizedTest
    @ValueSource (booleans = { false, true })
    void groupEntryGuardsTheActualImmediateOrScheduledChildSelection (final boolean selected)
    {
        final Fixture fixture = new Fixture (4);
        fixture.nativeTracks[0].setSelected (selected);
        fixture.nativeCursorId = selected ? "track-0" : "other-track";
        fixture.host.refresh (true);
        fixture.act (fixture.location (7, 3), SessionActionEffect.Action.LAUNCH);
        fixture.nativeTracks[0].enter ();
        if (!selected)
        {
            assertEquals (List.of ("slot:7:3:launch:true:false", "track:0:select"), fixture.requests);
            assertEquals (0, fixture.mutationGuards);
            fixture.scheduled.removeFirst ().run ();
            assertEquals (0, fixture.mutationGuards, "selection submission cannot acknowledge cursor acquisition");
            fixture.nativeCursorId = "track-0";
            fixture.scheduled.removeFirst ().run ();
        }
        assertEquals (List.of ("slot:7:3:launch:false:false", "enter"), fixture.requests.subList (fixture.requests.size () - 2, fixture.requests.size ()));
        assertEquals (1, fixture.mutationGuards);
    }


    @Test
    void anotherOwnerCannotReplaceTheInstalledMutationGuard ()
    {
        final Fixture fixture = new Fixture (4);
        assertThrows (IllegalStateException.class, () -> fixture.nativeHost.setProjectStructureMutationGuard (() -> { }));
        fixture.host.refresh (true);
        fixture.act (fixture.location (7, 3), SessionActionEffect.Action.LAUNCH);
        fixture.nativeTracks[0].remove ();
        assertEquals (List.of ("slot:7:3:launch:true:false", "slot:7:3:launch:false:false", "track:0:remove"), fixture.requests);
    }


    @ParameterizedTest
    @ValueSource (strings = { "REMOVE", "DUPLICATE" })
    void precedingSceneMutationReleasesLaterLocationsBeforeSubmittingTheMutation (final String operation)
    {
        final Fixture fixture = new Fixture (4);
        fixture.firstHasContent = false;
        fixture.host.refresh (true);
        final SessionLocation held = fixture.location (7, 3);
        final int originalOffset = fixture.host.snapshot ().sceneOffset ();
        final var oldLaunch = fixture.host.prepare (new SessionActionEffect (held, SessionActionEffect.Action.LAUNCH));
        final var oldCreate = fixture.host.prepare (new CreateSessionClipEffect (fixture.location (0, 0), 4));
        fixture.act (held, SessionActionEffect.Action.LAUNCH);
        fixture.act (fixture.location (-1, 2), SessionActionEffect.Action.LAUNCH_ALT);
        fixture.act (fixture.location (-1, 0), SessionActionEffect.Action.valueOf (operation));
        assertEquals (List.of ("slot:7:3:launch:true:false", "scene:2:launch:true:true", "slot:7:3:launch:false:false", "scene:2:launch:false:true", "scene:0:" + operation.toLowerCase (java.util.Locale.ROOT)), fixture.requests);
        assertEquals (SessionBankSnapshot.empty (), fixture.host.snapshot ());
        fixture.host.refresh (true);
        assertEquals (originalOffset, fixture.host.snapshot ().sceneOffset ());
        assertTrue (fixture.host.snapshot ().generation () > held.generation (), "scene edits revoke locations even when all visible positions remain equal");
        fixture.host.apply (oldLaunch);
        fixture.host.apply (oldCreate);
        fixture.act (held, SessionActionEffect.Action.RELEASE);
        fixture.host.releaseOutstanding ();
        assertEquals (5, fixture.requests.size ());
    }


    @Test
    void staleStructuralActionLeavesTheHeldSlotReleaseOwned ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        fixture.currentTracks.refresh ();
        final SessionLocation held = fixture.location (7, 0);
        fixture.act (held, SessionActionEffect.Action.LAUNCH);
        final var stale = fixture.currentTracks.prepare (new CurrentTrackActionEffect (fixture.currentTarget (), CurrentTrackActionEffect.Action.REMOVE));
        fixture.firstChannelId = "replacement";
        fixture.currentTracks.apply (stale);
        assertTrue (fixture.diagnostics.isEmpty ());
        fixture.firstChannelId = "track-0";
        fixture.act (held, SessionActionEffect.Action.RELEASE);
        assertEquals (List.of ("slot:7:0:launch:true:false", "slot:7:0:launch:false:false"), fixture.requests);
    }


    @Test
    void selectionAndRecordArmDoNotReleaseAHeldSessionSlot ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        fixture.currentTracks.refresh ();
        final SessionLocation held = fixture.location (7, 0);
        fixture.act (held, SessionActionEffect.Action.LAUNCH);
        fixture.currentTracks.apply (fixture.currentTracks.prepare (new CurrentTrackActionEffect (fixture.currentTarget (), CurrentTrackActionEffect.Action.SELECT)));
        fixture.currentTracks.apply (fixture.currentTracks.prepare (new SetCurrentTrackBooleanEffect (fixture.currentTarget (), SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, true)));
        assertEquals (List.of ("slot:7:0:launch:true:false", "track:0:select", "track:0:arm:true"), fixture.requests);
        fixture.act (held, SessionActionEffect.Action.RELEASE);
        assertEquals ("slot:7:0:launch:false:false", fixture.requests.getLast ());
    }


    @Test
    void abandonmentRecoversFromAnIgnoredPositionRequestWithoutRevivingItsTargets ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final SessionLocation oldTarget = fixture.location (0, 0);
        final SessionBankHost.PreparedLauncherAction oldLaunch = fixture.host.prepare (new SessionActionEffect (oldTarget, SessionActionEffect.Action.LAUNCH));
        fixture.host.apply (fixture.host.prepare (new SetSessionBankPositionEffect (oldTarget.generation (), oldTarget.shape (), -1, 9)));
        fixture.host.refresh (true);
        assertFalse (fixture.host.snapshot ().clips ().aligned ());

        fixture.host.invalidate ();
        fixture.host.refresh (true);
        assertTrue (fixture.host.snapshot ().clips ().aligned ());
        fixture.host.apply (oldLaunch);
        fixture.act (fixture.location (0, 0), SessionActionEffect.Action.LAUNCH);
        assertEquals (List.of ("scroll-scenes:9", "slot:0:0:launch:true:false"), fixture.requests);
    }


    @Test
    void preparedBeginAndEndRemainOrderedAndDuplicateEndIsInert ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final SessionLocation target = fixture.location (0, 0);
        final SessionBankHost.PreparedLauncherAction begin = fixture.host.prepare (new SessionActionEffect (target, SessionActionEffect.Action.LAUNCH));
        final SessionBankHost.PreparedLauncherAction end = fixture.host.prepare (new SessionActionEffect (target, SessionActionEffect.Action.RELEASE));
        fixture.host.apply (begin);
        fixture.host.apply (end);
        fixture.host.apply (end);
        fixture.host.releaseOutstanding ();
        assertEquals (List.of ("slot:0:0:launch:true:false", "slot:0:0:launch:false:false"), fixture.requests);
    }


    @Test
    void faultCleanupReleasesEachAcquiredLaneOnceWithoutChangingObservedPlayback ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        fixture.act (fixture.location (0, 0), SessionActionEffect.Action.LAUNCH_ALT);
        fixture.act (fixture.location (-1, 0), SessionActionEffect.Action.LAUNCH);
        fixture.advanceHost ();
        fixture.host.refresh (true);
        fixture.host.releaseOutstanding ();
        fixture.host.releaseOutstanding ();
        fixture.host.refresh (true);
        assertEquals (List.of ("slot:0:0:launch:true:true", "scene:0:launch:true:false", "slot:0:0:launch:false:true", "scene:0:launch:false:false"), fixture.requests);
        assertTrue (fixture.host.snapshot ().clips ().slot (0, 0).playing ());
    }


    @Test
    void createRecordCopyAndSceneEditingSubmitOnlyTheRequestedPrimitive ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.firstHasContent = false;
        fixture.firstExists = false;
        fixture.host.refresh (true);
        final SessionLocation target = fixture.location (0, 0);
        fixture.host.apply (fixture.host.prepare (new CreateSessionClipEffect (target, 16)));
        fixture.act (target, SessionActionEffect.Action.START_RECORDING);
        fixture.host.apply (fixture.host.prepare (new CopySessionClipEffect (fixture.location (1, 1), target)));
        fixture.act (fixture.location (-1, 2), SessionActionEffect.Action.SELECT);
        fixture.act (fixture.location (-1, 2), SessionActionEffect.Action.REMOVE);
        fixture.host.refresh (true);
        fixture.act (fixture.location (-1, 2), SessionActionEffect.Action.DUPLICATE);
        assertEquals (List.of ("create:0:5:16", "slot:0:0:record", "slot:0:0:paste:1:1", "scene:2:select", "scene:2:remove", "scene:2:duplicate"), fixture.requests);
        fixture.host.refresh (true);
        assertFalse (fixture.host.snapshot ().clips ().slot (0, 0).hasContent ());
        assertFalse (fixture.host.snapshot ().clips ().slot (0, 0).recording ());
        assertEquals (0, fixture.launchRequests);
        fixture.advanceHost ();
        fixture.host.refresh (true);
        assertTrue (fixture.host.snapshot ().clips ().slot (0, 0).hasContent ());
        assertTrue (fixture.host.snapshot ().clips ().slot (0, 0).recording ());
    }


    @Test
    void copyAndCreateRejectAReboundSlotBankOffset ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.firstHasContent = false;
        fixture.host.refresh (true);
        final SessionBankHost.PreparedCopy copy = fixture.host.prepare (new CopySessionClipEffect (fixture.location (1, 0), fixture.location (0, 0)));
        final SessionBankHost.PreparedCreate create = fixture.host.prepare (new CreateSessionClipEffect (fixture.location (0, 0), 4));
        fixture.slotBankOffset = 6;
        fixture.host.apply (copy);
        fixture.host.apply (create);
        assertTrue (fixture.requests.isEmpty ());
    }


    @ParameterizedTest
    @ValueSource (booleans = { false, true })
    void copyRejectsEitherReplacedChannel (final boolean replaceSource)
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final SessionBankHost.PreparedCopy copy = fixture.host.prepare (new CopySessionClipEffect (fixture.location (replaceSource ? 0 : 1, 0), fixture.location (replaceSource ? 1 : 0, 0)));
        fixture.host.apply (copy);
        assertEquals (1, fixture.requests.size ());
        fixture.requests.clear ();
        fixture.firstChannelId = "replacement";
        fixture.host.apply (copy);
        assertTrue (fixture.requests.isEmpty ());
    }


    @Test
    void browseUsesTheValidatedInsertionPointImmediately ()
    {
        final Fixture fixture = new Fixture (4);
        final InsertionPoint insertion = proxy (InsertionPoint.class, (ignored, method, args) -> {
            if (method.getName ().equals ("browse"))
                fixture.requests.add ("browse:0:0");
            return defaultValue (method.getReturnType ());
        });
        final ClipLauncherSlot nativeSlot = proxy (ClipLauncherSlot.class, (ignored, method, args) -> {
            if (method.getName ().equals ("replaceInsertionPoint"))
                return insertion;
            return value (method.getReturnType (), () -> switch (method.getName ())
            {
                case "exists", "hasContent" -> true;
                case "sceneIndex" -> fixture.proxySceneOffset;
                case "name" -> "clip";
                default -> false;
            });
        });
        fixture.slots[0][0] = new SlotImpl (fixture.tracks[0], nativeSlot, 0);
        fixture.host.refresh (true);
        final SessionActionEffect browse = new SessionActionEffect (fixture.location (0, 0), SessionActionEffect.Action.BROWSE);
        final SessionBankHost.PreparedLauncherAction prepared = fixture.host.prepare (browse);
        fixture.host.apply (prepared);
        assertEquals (List.of ("browse:0:0"), fixture.requests);
        fixture.projectIdentity = "other-project";
        fixture.host.apply (prepared);
        assertEquals (List.of ("browse:0:0"), fixture.requests);
    }


    @Test
    void mixedProxyLocationsRemainUnalignedUntilReadBackCatchesUp ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final long generation = fixture.host.snapshot ().generation ();
        fixture.sceneOffset++;
        fixture.host.refresh (true);
        assertEquals (generation + 1, fixture.host.snapshot ().generation ());
        assertFalse (fixture.host.snapshot ().clips ().aligned ());
        assertEquals (5, fixture.host.snapshot ().clips ().slot (0, 0).scenePosition ());

        fixture.proxySceneOffset = fixture.sceneOffset;
        fixture.slotBankOffset = fixture.sceneOffset;
        fixture.host.refresh (true);
        assertTrue (fixture.host.snapshot ().clips ().aligned ());
        assertEquals (6, fixture.host.snapshot ().clips ().slot (0, 0).scenePosition ());
    }


    private static final class Fixture
    {
        private int sceneOffset = 5;
        private int proxySceneOffset = 5;
        private int slotBankOffset = 5;
        private String projectIdentity = "project";
        private String firstChannelId = "track-0";
        private int selectedTrack;
        private String nativeCursorId = "track-0";
        private int slotReads;
        private int sceneReads;
        private int launchRequests;
        private boolean playing;
        private boolean firstHasContent = true;
        private boolean firstExists = true;
        private boolean recording;
        private boolean createRequested;
        private boolean recordRequested;
        private boolean structuralMutationRequested;
        private boolean windowRebound;
        private final List<String> requests = new ArrayList<> ();
        private final List<Runnable> scheduled = new ArrayList<> ();
        private final List<String> diagnostics = new ArrayList<> ();
        private final HostImpl nativeHost;
        private int mutationGuards;
        private final ISlot[][] slots;
        private final ITrack[] tracks = new ITrack[8];
        private final TrackImpl[] nativeTracks = new TrackImpl[8];
        private final SessionBankHost host;
        private final CurrentTrackBankHost currentTracks;


        private Fixture (final int rows)
        {
            final SessionBankShape shape = new SessionBankShape (8, rows);
            this.nativeHost = new HostImpl (proxy (ControllerHost.class, (ignored, method, args) -> {
                if ("scheduleTask".equals (method.getName ()))
                    this.scheduled.add ((Runnable) args[0]);
                return nativeValue (method.getReturnType ());
            }));
            final var changer = new TwosComplementValueChanger (128, 1);
            this.slots = new ISlot[8][rows];
            final IScene[] scenes = new IScene[rows];
            for (int row = 0; row < rows; row++)
            {
                final int sceneIndex = row;
                final SceneImpl nativeScene = new SceneImpl (nativeHost, proxy (Scene.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "deleteObject", "selectInEditor" -> {
                        this.requests.add ("scene:" + sceneIndex + ":" + ("deleteObject".equals (method.getName ()) ? "remove" : "select"));
                        yield null;
                    }
                    case "nextSceneInsertionPoint" -> proxy (InsertionPoint.class, (point, action, arguments) -> {
                        if ("copySlotsOrScenes".equals (action.getName ()))
                            this.requests.add ("scene:" + sceneIndex + ":duplicate");
                        return nativeValue (action.getReturnType ());
                    });
                    default -> nativeValue (method.getReturnType ());
                }), row);
                scenes[row] = proxy (IScene.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "doesExist" -> true;
                    case "getPosition" -> this.proxySceneOffset + sceneIndex;
                    case "getName" -> "Scene " + sceneIndex;
                    case "isSelected" -> sceneIndex == 0;
                    case "getColor" -> ColorEx.RED;
                    case "launch" -> {
                        this.requests.add ("scene:" + sceneIndex + ":launch:" + args[0] + ":" + args[1]);
                        yield null;
                    }
                    case "select", "remove", "duplicate" -> {
                        method.invoke (nativeScene, args);
                        yield null;
                    }
                    default -> defaultValue (method.getReturnType ());
                });
            }
            for (int column = 0; column < 8; column++)
            {
                final int trackIndex = column;
                final Track rawTrack = proxy (Track.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "isGroup", "exists" -> value (method.getReturnType (), () -> true);
                    case "channelId" -> value (method.getReturnType (), () -> "track-" + trackIndex);
                    case "selectInEditor" -> {
                        this.requests.add ("track:" + trackIndex + ":select");
                        yield null;
                    }
                    case "deleteObject", "duplicate" -> {
                        this.structuralMutationRequested = true;
                        this.requests.add ("track:" + trackIndex + ":" + ("deleteObject".equals (method.getName ()) ? "remove" : "duplicate"));
                        yield null;
                    }
                    case "arm" -> proxy (method.getReturnType (), (value, action, arguments) -> {
                        if ("set".equals (action.getName ()))
                            this.requests.add ("track:" + trackIndex + ":arm:" + arguments[0]);
                        return nativeValue (action.getReturnType ());
                    });
                    default -> nativeValue (method.getReturnType ());
                });
                final CursorTrack rawCursor = proxy (CursorTrack.class, (ignored, method, args) -> {
                    if ("channelId".equals (method.getName ()))
                        return value (method.getReturnType (), () -> this.nativeCursorId);
                    if ("selectFirstChild".equals (method.getName ()))
                        this.requests.add ("enter");
                    return nativeValue (method.getReturnType ());
                });
                this.nativeTracks[column] = new TrackImpl (nativeHost, changer, null, rawCursor, null, rawTrack, rawTrack, column, 0, 0);
                this.nativeTracks[column].configurePendingOperations (new GroupNavigationHost (nativeHost, rawCursor, () -> this.projectIdentity));
                for (int row = 0; row < rows; row++)
                {
                    final int sceneIndex = row;
                    final SlotImpl nativeSlot = new SlotImpl (null, proxy (ClipLauncherSlot.class, (ignored, method, args) -> {
                        final String action = method.getName ();
                        if (Set.of ("launch", "launchAlt", "launchRelease", "launchReleaseAlt").contains (action))
                        {
                            this.launchRequests++;
                            this.requests.add ("slot:" + trackIndex + ":" + sceneIndex + ":launch:" + !action.contains ("Release") + ":" + action.endsWith ("Alt"));
                        }
                        return nativeValue (method.getReturnType ());
                    }), row);
                    this.slots[column][row] = proxy (ISlot.class, (ignored, method, args) -> switch (method.getName ())
                    {
                        case "doesExist" -> trackIndex != 0 || sceneIndex != 0 || this.firstExists;
                        case "hasContent" -> trackIndex != 0 || sceneIndex != 0 || this.firstHasContent;
                        case "getPosition" -> trackIndex == 0 && sceneIndex == 0 && !this.firstExists ? -1 : this.proxySceneOffset + sceneIndex;
                        case "getName" -> trackIndex + ":" + sceneIndex;
                        case "getColor" -> ColorEx.BLUE;
                        case "isPlaying" -> trackIndex == 0 && sceneIndex == 0 && this.playing;
                        case "isRecording" -> trackIndex == 0 && sceneIndex == 0 && this.recording;
                        case "launch" -> {
                            nativeSlot.launch ((Boolean) args[0], (Boolean) args[1]);
                            yield null;
                        }
                        case "startRecording" -> {
                            this.recordRequested = true;
                            this.requests.add ("slot:" + trackIndex + ":" + sceneIndex + ":record");
                            yield null;
                        }
                        case "paste" -> {
                            this.requests.add ("slot:" + trackIndex + ":" + sceneIndex + ":paste:" + ((ISlot) args[0]).getName ());
                            yield null;
                        }
                        case "select", "remove" -> {
                            this.requests.add ("slot:" + trackIndex + ":" + sceneIndex + ":" + method.getName ());
                            yield null;
                        }
                        default -> defaultValue (method.getReturnType ());
                    });
                }
                final ISlotBank slotBank = proxy (ISlotBank.class, (ignored, method, args) -> {
                    if ("getItem".equals (method.getName ()))
                    {
                        this.slotReads++;
                        return this.slots[trackIndex][(Integer) args[0]];
                    }
                    if ("getScrollPosition".equals (method.getName ()))
                        return this.slotBankOffset;
                    return defaultValue (method.getReturnType ());
                });
                this.tracks[column] = proxy (ITrack.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "doesExist", "isActivated" -> true;
                    case "getChannelID" -> trackIndex == 0 ? this.firstChannelId : (this.windowRebound ? "replacement-" : "track-") + trackIndex;
                    case "getPosition" -> 3 + trackIndex;
                    case "getName" -> "Track " + trackIndex;
                    case "getType" -> ChannelType.INSTRUMENT;
                    case "getColor" -> ColorEx.BLUE;
                    case "getSlotBank" -> slotBank;
                    case "isSelected" -> trackIndex == this.selectedTrack;
                    case "select", "setRecArm", "remove", "duplicate" -> {
                        method.invoke (this.nativeTracks[trackIndex], args);
                        yield null;
                    }
                    case "createClip" -> {
                        this.createRequested = true;
                        this.requests.add ("create:" + trackIndex + ":" + (this.slotBankOffset + (Integer) args[0]) + ":" + args[1]);
                        yield null;
                    }
                    default -> defaultValue (method.getReturnType ());
                });
            }
            final SlotBankImpl nativeSceneWindow = new SlotBankImpl (nativeHost, changer, null, null, proxy (ClipLauncherSlotBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "itemCount" -> value (method.getReturnType (), () -> 40);
                case "scrollPosition" -> proxy (method.getReturnType (), (value, action, arguments) -> {
                    if ("set".equals (action.getName ()))
                        this.requests.add ("scroll-scenes:" + arguments[0]);
                    return nativeValue (action.getReturnType ());
                });
                default -> nativeValue (method.getReturnType ());
            }), 0);
            final ISceneBank sceneBank = proxy (ISceneBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> {
                    this.sceneReads++;
                    yield scenes[(Integer) args[0]];
                }
                case "getScrollPosition" -> this.sceneOffset;
                case "getItemCount" -> 40;
                case "canScrollBackwards", "canScrollForwards", "canScrollPageBackwards" -> true;
                case "scrollTo" -> {
                    nativeSceneWindow.scrollTo ((Integer) args[0], false);
                    yield null;
                }
                default -> defaultValue (method.getReturnType ());
            });
            final ITrackBank bank = proxy (ITrackBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> this.tracks[(Integer) args[0]];
                case "getScrollPosition" -> 3;
                case "getItemCount" -> 32;
                case "getSceneBank" -> sceneBank;
                case "canScrollBackwards", "canScrollForwards", "canScrollPageBackwards", "canScrollPageForwards" -> true;
                default -> defaultValue (method.getReturnType ());
            });
            final ICursorTrack cursor = proxy (ICursorTrack.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "doesExist" -> true;
                case "getChannelID" -> this.firstChannelId;
                case "getPosition" -> 3;
                default -> defaultValue (method.getReturnType ());
            });
            final IModel model = proxy (IModel.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getTrackBank", "getCurrentTrackBank" -> bank;
                case "getCursorTrack" -> cursor;
                case "getHost" -> nativeHost;
                case "getValueChanger" -> new TwosComplementValueChanger (128, 1);
                default -> defaultValue (method.getReturnType ());
            });
            this.host = new SessionBankHost (new SessionBankRegistry (model, Set.of (shape), shape), () -> this.projectIdentity, this.diagnostics::add);
            nativeHost.setProjectStructureMutationGuard (() -> {
                this.mutationGuards++;
                this.host.invalidate ();
            });
            this.currentTracks = new CurrentTrackBankHost (model, List.of (bank));
        }


        private SessionLocation location (final int trackIndex, final int row)
        {
            final SessionBankSnapshot snapshot = this.host.snapshot ();
            return new SessionLocation (this.projectIdentity, snapshot.generation (), snapshot.shape (), trackIndex, trackIndex < 0 ? "" : snapshot.tracks ().get (trackIndex).channelId (), snapshot.sceneOffset () + row);
        }


        private void act (final SessionLocation target, final SessionActionEffect.Action action)
        {
            this.host.apply (this.host.prepare (new SessionActionEffect (target, action)));
        }


        private CurrentTrackTarget currentTarget ()
        {
            final var snapshot = this.currentTracks.snapshot ();
            return new CurrentTrackTarget (snapshot.generation (), snapshot.bankId (), 0, snapshot.tracks ().getFirst ().track ().channelId ());
        }


        private void advanceHost ()
        {
            this.playing = this.launchRequests > 0;
            this.firstHasContent |= this.createRequested;
            this.firstExists |= this.createRequested;
            this.recording = this.recordRequested;
            this.windowRebound |= this.structuralMutationRequested;
        }
    }


    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?>[] { type }, handler));
    }


    private static <T> T nativeProxy (final Class<T> type)
    {
        return proxy (type, (ignored, method, args) -> {
            if ("getItemAt".equals (method.getName ()) && com.bitwig.extension.controller.api.DeviceBank.class.isAssignableFrom (type))
                return nativeProxy (com.bitwig.extension.controller.api.Device.class);
            return nativeValue (method.getReturnType ());
        });
    }


    private static Object nativeValue (final Class<?> type)
    {
        if (type == String.class)
            return "";
        return type.isInterface () ? nativeProxy (type) : defaultValue (type);
    }


    private static Object value (final Class<?> type, final Supplier<Object> observed)
    {
        return proxy (type, (ignored, method, args) -> switch (method.getName ())
        {
            case "get", "getLimited" -> observed.get ();
            case "red", "green", "blue" -> 0.0f;
            default -> defaultValue (method.getReturnType ());
        });
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == double.class)
            return 0.0;
        return null;
    }
}
