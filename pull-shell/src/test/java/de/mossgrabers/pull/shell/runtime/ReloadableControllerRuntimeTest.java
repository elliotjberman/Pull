// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.midi.MidiConstants;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Physical routing tests for the permanent shell facade.
 */
class ReloadableControllerRuntimeTest
{
    private static final ClipLaunchPolicy LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);
    private static final int [] EXPECTED_FILL_NOTES =
    {
        48,
        49,
        50,
        51,
        56,
        57,
        58,
        59,
        64,
        65,
        66,
        67
    };


    @Test
    void exposesTwelveFillPadsInBottomUpRowMajorOrder ()
    {
        final int [] notes = ReloadableControllerRuntime.fillPadNotes ();
        assertArrayEquals (EXPECTED_FILL_NOTES, notes);
        for (final int note: notes)
            assertTrue (ReloadableControllerRuntime.isFillPad (note));
        assertFalse (ReloadableControllerRuntime.isFillPad (47));
        assertFalse (ReloadableControllerRuntime.isFillPad (52));
        assertFalse (ReloadableControllerRuntime.isFillPad (68));

        notes[0] = 0;
        assertArrayEquals (EXPECTED_FILL_NOTES, ReloadableControllerRuntime.fillPadNotes ());
    }


    @Test
    void routesSingleActiveFillHandoffAndPreservesPhysicalReleaseSafety ()
    {
        final FakeClipHost clipHost = new FakeClipHost (9);
        final ControllerRuntimeEnvironment environment = createArmedEnvironment (clipHost);
        final List<CoreEvent> events = new ArrayList<> ();
        final long [] coreGeneration =
        {
            1
        };
        final Predicate<CoreEvent> handler = event -> {
            events.add (event);
            if (event instanceof final ButtonInputEvent button && button.pressed ())
            {
                final ClipTargetId targetId = clipHost.targetId (button.controlId ());
                final CoreResult press = result (
                    clipHost.allBindings (),
                    List.of (new PressClipTargetEffect (button.controlId (), clipHost.catalogGeneration (), targetId, LAUNCH_POLICY)));
                final long generation = ++coreGeneration[0];
                environment.commit (generation, environment.prepare (press));
                environment.apply (generation);
            }
            // Deliberately omit release effects. The physical-UP safety path must still return
            // the one active fill.
            return true;
        };
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (environment, NoOpLog.INSTANCE, handler);
        runtime.start ();

        assertFalse (runtime.routeGridEvent (true, ButtonEvent.DOWN, 52));
        assertFalse (runtime.routeGridEvent (false, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[0]));

        final ControlId firstControl = CoreControls.drumFills ().getFirst ();
        final ControlId secondControl = CoreControls.drumFills ().get (1);
        assertTrue (runtime.routeGridEvent (true, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[0]));
        assertTrue (environment.isFillPressed (firstControl));
        assertEquals (1, clipHost.target (firstControl).pressCount);
        assertEquals (0, clipHost.target (secondControl).pressCount);
        assertTrue (environment.snapshot ().activeClipLaunchOwner ().isEmpty ());

        acknowledgeLaunch (clipHost, runtime, firstControl);
        assertEquals (firstControl, environment.snapshot ().activeClipLaunchOwner ().orElseThrow ());

        assertTrue (runtime.routeGridEvent (true, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[1]));
        assertTrue (environment.isFillPressed (firstControl));
        assertTrue (environment.isFillPressed (secondControl));
        assertEquals (1, clipHost.target (firstControl).releaseCount);
        assertEquals (0, clipHost.target (secondControl).pressCount);
        assertEquals (firstControl, environment.snapshot ().activeClipLaunchOwner ().orElseThrow ());

        acknowledgeReturn (clipHost, runtime, firstControl);
        assertEquals (1, clipHost.target (firstControl).retireCount);
        assertEquals (0, clipHost.target (secondControl).pressCount);
        assertTrue (environment.snapshot ().activeClipLaunchOwner ().isEmpty ());

        // The next host sample is an intentional barrier: the replacement cannot become a
        // native Return child of the fill which Bitwig only just reported as stopped.
        runtime.tick ();
        assertEquals (1, clipHost.target (secondControl).pressCount);
        assertTrue (environment.snapshot ().activeClipLaunchOwner ().isEmpty ());
        acknowledgeLaunch (clipHost, runtime, secondControl);
        assertEquals (secondControl, environment.snapshot ().activeClipLaunchOwner ().orElseThrow ());

        assertTrue (runtime.routeGridEvent (false, ButtonEvent.LONG, EXPECTED_FILL_NOTES[0]));
        assertTrue (runtime.routeGridEvent (false, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[0]));
        assertEquals (2, buttonEvents (events).size ());

        runtime.routePhysicalMidiRelease (false, MidiConstants.CMD_NOTE_OFF | 1, EXPECTED_FILL_NOTES[0], 0);
        assertTrue (environment.isFillPressed (firstControl));
        assertEquals (1, clipHost.target (firstControl).releaseCount);

        // This callback sits below active-view dispatch and therefore still sees the exact UP when
        // a view switch consumes the command-layer release.
        runtime.routePhysicalMidiRelease (false, MidiConstants.CMD_NOTE_OFF, EXPECTED_FILL_NOTES[0], 0);
        assertFalse (environment.isFillPressed (firstControl));
        assertTrue (environment.isFillPressed (secondControl));
        assertEquals (1, clipHost.target (firstControl).releaseCount);
        assertEquals (0, clipHost.target (secondControl).releaseCount);
        assertEquals (3, buttonEvents (events).size ());
        assertFalse (buttonEvents (events).getLast ().pressed ());

        // The command-layer UP produced by the same MIDI message is consumed after it updates its
        // hardware state, so it cannot leak into the newly active view.
        assertTrue (runtime.routeGridEvent (false, ButtonEvent.UP, EXPECTED_FILL_NOTES[0]));
        assertFalse (runtime.routeGridEvent (false, ButtonEvent.UP, EXPECTED_FILL_NOTES[0]));

        assertTrue (runtime.routeGridEvent (false, ButtonEvent.UP, EXPECTED_FILL_NOTES[1]));
        assertFalse (environment.isFillPressed (secondControl));
        assertEquals (1, clipHost.target (secondControl).releaseCount);
        assertEquals (secondControl, environment.snapshot ().activeClipLaunchOwner ().orElseThrow ());
        acknowledgeReturn (clipHost, runtime, secondControl);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertTrue (environment.snapshot ().activeClipLaunchOwner ().isEmpty ());

        runtime.close ();

        assertEquals (1, clipHost.target (firstControl).releaseCount);
        assertEquals (1, clipHost.target (secondControl).releaseCount);
    }


    @Test
    void retriesAuthoritativeSnapshotUntilAcceptedAfterAnInterveningNoOpInput ()
    {
        final FakeClipHost clipHost = new FakeClipHost (9);
        final ControllerRuntimeEnvironment environment = createArmedEnvironment (clipHost);
        final ControlId firstControl = CoreControls.drumFills ().getFirst ();
        environment.acknowledgeSnapshotChange (environment.snapshotRevision ());
        environment.setFillPressed (firstControl, true);
        final CoreResult press = result (
            clipHost.allBindings (),
            List.of (new PressClipTargetEffect (firstControl, clipHost.catalogGeneration (), clipHost.targetId (firstControl), LAUNCH_POLICY)));
        environment.commit (2, environment.prepare (press));
        environment.apply (2);
        clipHost.advanceLaunch (firstControl);
        environment.refresh ();
        assertEquals (firstControl, environment.snapshot ().activeClipLaunchOwner ().orElseThrow ());

        final AtomicInteger snapshotAttempts = new AtomicInteger ();
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (
            environment,
            NoOpLog.INSTANCE,
            event -> !(event instanceof SnapshotChangedEvent) || snapshotAttempts.incrementAndGet () >= 2);
        runtime.start ();

        // An unheld UP still refreshes stable state, but must not consume the pending owner change.
        assertTrue (runtime.routeGridEvent (true, ButtonEvent.UP, EXPECTED_FILL_NOTES[1]));
        assertEquals (0, snapshotAttempts.get ());

        runtime.tick ();
        assertEquals (1, snapshotAttempts.get ());
        runtime.tick ();
        assertEquals (2, snapshotAttempts.get ());
        runtime.tick ();
        assertEquals (2, snapshotAttempts.get ());

        runtime.close ();
    }


    private static ControllerRuntimeEnvironment createArmedEnvironment (final FakeClipHost clipHost)
    {
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (clipHost, NoOpLog.INSTANCE, () -> 0);
        final CoreResult bindings = result (clipHost.allBindings (), List.of ());
        environment.commit (1, environment.prepare (bindings));
        environment.apply (1);
        environment.refresh ();
        return environment;
    }


    private static List<ButtonInputEvent> buttonEvents (final List<CoreEvent> events)
    {
        return events.stream ().filter (ButtonInputEvent.class::isInstance).map (ButtonInputEvent.class::cast).toList ();
    }


    private static CoreResult result (final Map<ControlId, ClipTargetId> bindings, final List<CoreEffect> effects)
    {
        return new CoreResult (
            DesiredHardwareOutput.empty (),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            bindings,
            DesiredControllerWorkspace.empty (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterBanks.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterInteraction.empty (),
            effects);
    }


    private static void acknowledgeLaunch (final FakeClipHost clipHost, final ReloadableControllerRuntime runtime, final ControlId owner)
    {
        clipHost.advanceLaunch (owner);
        runtime.tick ();
    }


    private static void acknowledgeReturn (final FakeClipHost clipHost, final ReloadableControllerRuntime runtime, final ControlId owner)
    {
        clipHost.advanceReturn (owner);
        runtime.tick ();
    }


    private static final class FakeClipHost implements DrumFillClipHost
    {
        private final ClipCatalogSnapshot catalog;
        private final Map<ControlId, FakeTarget> targets;
        private Map<ControlId, ClipTargetId> armedBindings = Map.of ();
        private String playing = "root";


        private FakeClipHost (final long generation)
        {
            final Map<ControlId, FakeTarget> createdTargets = new LinkedHashMap<> ();
            final List<CatalogClip> clips = new ArrayList<> ();
            for (int index = 0; index < CoreControls.drumFills ().size (); index++)
            {
                final ControlId control = CoreControls.drumFills ().get (index);
                final FakeTarget target = new FakeTarget (this, index + 1L);
                createdTargets.put (control, target);
                clips.add (new CatalogClip (target.targetId (), "fill " + (index + 1)));
            }
            this.targets = Map.copyOf (createdTargets);
            this.catalog = new ClipCatalogSnapshot (generation, clips);
        }


        @Override
        public boolean refresh ()
        {
            return false;
        }


        @Override
        public ClipCatalogSnapshot clipCatalog ()
        {
            return this.catalog;
        }


        @Override
        public void setDesiredBindings (final long catalogGeneration, final Map<ControlId, ClipTargetId> bindings)
        {
            if (catalogGeneration != this.catalog.generation ())
                throw new IllegalArgumentException ("Unknown catalog generation");
            this.armedBindings = Map.copyOf (bindings);
        }


        @Override
        public Map<ControlId, ClipTargetId> armedClipTargets ()
        {
            return this.armedBindings;
        }


        @Override
        public LaunchTarget prepare (final ControlId owner, final long catalogGeneration, final ClipTargetId targetId)
        {
            assertEquals ("root", this.playing, "A replacement cannot resolve before Bitwig reports the base playing again");
            if (catalogGeneration != this.catalog.generation () || !targetId.equals (this.armedBindings.get (owner)))
                throw new IllegalArgumentException ("Unknown target");
            final FakeTarget target = this.targets.get (owner);
            if (target == null || !target.targetId ().equals (targetId))
                throw new IllegalArgumentException ("Unknown owner");
            return target;
        }


        private long catalogGeneration ()
        {
            return this.catalog.generation ();
        }


        private Map<ControlId, ClipTargetId> allBindings ()
        {
            final Map<ControlId, ClipTargetId> bindings = new LinkedHashMap<> ();
            for (final Map.Entry<ControlId, FakeTarget> target: this.targets.entrySet ())
                bindings.put (target.getKey (), target.getValue ().targetId ());
            return Map.copyOf (bindings);
        }


        private ClipTargetId targetId (final ControlId control)
        {
            return this.target (control).targetId ();
        }


        private FakeTarget target (final ControlId control)
        {
            return this.targets.get (control);
        }


        private void advanceLaunch (final ControlId owner)
        {
            final FakeTarget target = this.target (owner);
            assertTrue (target.pressRequested, "A host launch can advance only after command submission");
            assertFalse (target.launchAcknowledged, "A host launch can be acknowledged only once");
            assertEquals ("root", this.playing, "Only one fill may play above the opaque base");
            this.playing = target.name ();
            target.launchAcknowledged = true;
        }


        private void advanceReturn (final ControlId owner)
        {
            final FakeTarget target = this.target (owner);
            assertTrue (target.releaseRequested, "A host Return can advance only after command submission");
            assertFalse (target.returnAcknowledged, "A host Return can be acknowledged only once");
            assertEquals (target.name (), this.playing, "Only the playing fill can Return to base");
            this.playing = "root";
            target.returnAcknowledged = true;
        }
    }


    private static final class FakeTarget implements DrumFillClipHost.LaunchTarget
    {
        private final FakeClipHost host;
        private final ClipTargetId targetId;
        private int pressCount;
        private int releaseCount;
        private int retireCount;
        private boolean pressRequested;
        private boolean launchAcknowledged;
        private boolean releaseRequested;
        private boolean returnAcknowledged;


        private FakeTarget (final FakeClipHost host, final long value)
        {
            this.host = host;
            this.targetId = new ClipTargetId (value);
        }


        @Override
        public ClipTargetId targetId ()
        {
            return this.targetId;
        }


        @Override
        public void press (final ClipLaunchPolicy launchPolicy)
        {
            assertEquals (LAUNCH_POLICY, launchPolicy);
            assertEquals ("root", this.host.playing, "A fill must launch from the opaque base");
            this.pressCount++;
            this.pressRequested = true;
            this.launchAcknowledged = false;
            this.releaseRequested = false;
            this.returnAcknowledged = false;
        }


        @Override
        public void release ()
        {
            assertFalse (this.releaseRequested, "A Return must not be requested twice");
            assertTrue (this.pressRequested, "Only a submitted launch can be released");
            assertEquals (this.name (), this.host.playing, "Only the playing fill can Return to base");
            this.releaseCount++;
            this.releaseRequested = true;
        }


        @Override
        public DrumFillClipHost.PlaybackState playbackState ()
        {
            return new DrumFillClipHost.PlaybackState (this.name ().equals (this.host.playing), false, false);
        }


        @Override
        public void retire ()
        {
            assertTrue (this.returnAcknowledged, "A target must remain frozen until Bitwig acknowledges Return");
            assertFalse (this.playbackState ().playing ());
            this.retireCount++;
        }


        private String name ()
        {
            return Long.toString (this.targetId.value ());
        }
    }


    private enum NoOpLog implements RuntimeLog
    {
        INSTANCE;


        @Override
        public void info (final String message)
        {
            // No-op test log.
        }


        @Override
        public void warn (final String message)
        {
            // No-op test log.
        }
    }
}
