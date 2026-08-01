// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReactivateClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transaction, ownership, and generation tests for the stable drum-fill shell environment.
 */
class DrumFillRuntimeEnvironmentTest
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor DIM_RED = new RgbColor (127, 0, 0);
    private static final RgbColor BRIGHT_RED = new RgbColor (255, 0, 0);
    private static final ControlId FIRST = CoreControls.DRUM_FILL_1;
    private static final ControlId SECOND = CoreControls.DRUM_FILL_2;
    private static final ClipTargetId FIRST_TARGET = new ClipTargetId (1);
    private static final ClipTargetId SECOND_TARGET = new ClipTargetId (2);
    private static final ClipLaunchPolicy LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);
    private static final ClipLaunchPolicy DIFFERENT_LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.DEFAULT,
        ClipLaunchMode.DEFAULT,
        ClipReleaseTrigger.MAIN);


    @Test
    void capturesFreshSnapshotsAndCompleteIndependentRedOutputBuffers ()
    {
        final FakeClipHost host = host (7, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final AtomicLong clock = new AtomicLong (100);
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (host, new RecordingLog (), clock::getAndIncrement);

        final ControllerSnapshot initial = environment.snapshot ();
        final ControllerSnapshot secondSnapshot = environment.snapshot ();
        assertNotSame (initial, secondSnapshot);
        assertEquals (initial.revision (), secondSnapshot.revision ());
        assertTrue (secondSnapshot.monotonicTimeNanos () > initial.monotonicTimeNanos ());
        assertEquals (7, initial.clipCatalog ().generation ());
        assertEquals (FIRST_TARGET, initial.armedClipTargets ().get (FIRST));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.INPUT_DRUM_FILL));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.BINDING_CLIP_TARGET));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION));
        assertEquals (Integer.valueOf (3), initial.capabilities ().versions ().get (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD));
        assertTrue (initial.clipLaunchSessionTargets ().isEmpty ());
        assertEquals (Optional.empty (), initial.activeClipLaunchOwner ());

        final ButtonInputEvent firstDown = environment.setFillPressed (FIRST, true);
        final ButtonInputEvent secondDown = environment.setFillPressed (SECOND, true);
        assertEquals (1, firstDown.sequence ());
        assertEquals (2, secondDown.sequence ());
        assertEquals (2, environment.snapshot ().revision ());
        assertTrue (environment.snapshot ().pressedControls ().containsAll (List.of (FIRST, SECOND)));

        final ButtonInputEvent duplicateDown = environment.setFillPressed (FIRST, true);
        assertEquals (3, duplicateDown.sequence ());
        assertEquals (2, environment.snapshot ().revision ());
        final SnapshotChangedEvent changed = environment.snapshotChangedEvent ();
        assertEquals (4, changed.sequence ());

        final PreparedCoreResult prepared = environment.prepare (result (
            Map.of (FIRST, DIM_RED, SECOND, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of ()));
        assertEquals (OFF, environment.fillLightColor (FIRST));
        environment.commit (11, prepared);
        assertEquals (DIM_RED, environment.fillLightColor (FIRST));
        assertEquals (BRIGHT_RED, environment.fillLightColor (SECOND));
        assertEquals (OFF, environment.fillLightColor (CoreControls.DRUM_FILL_3));
        assertEquals (11, environment.outputGeneration ());
        assertEquals (0, host.bindingUpdateCount);

        environment.apply (11);
        assertEquals (1, host.bindingUpdateCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), host.desiredBindings);
    }


    @Test
    void requiresTheExactAlreadyArmedBindingBeforeAButtonCanLaunch ()
    {
        final FakeClipHost host = host (4, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (pressResult (3, FIRST, FIRST_TARGET)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (pressResult (4, FIRST, SECOND_TARGET)));
        assertThrows (IllegalStateException.class, () -> environment (host (4, FIRST_TARGET)).prepare (pressResult (4, FIRST, FIRST_TARGET)));
        assertEquals (0, host.prepareCount);
        assertEquals (0, host.target (FIRST).pressCount);
    }


    @Test
    void releasingTheLatestFillUnwindsTheNativeReturnChainToRoot ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
        assertTrue (environment.refresh ());
        assertTrue (environment.refresh ());
        environment.acknowledgeSnapshotChange (environment.snapshotRevision ());
        assertFalse (environment.refresh ());

        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));
        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (1, host.target (SECOND).pressCount);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
        assertEquals ("2", host.playing ());

        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (SECOND).releaseCount);
        assertEquals (List.of ("press 1", "press 2", "release 2"), host.launchEvents);
        assertEquals ("2", host.playing ());
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), environment.snapshot ().clipLaunchSessionTargets ());

        host.advanceReturn ();
        assertEquals ("1", host.playing ());
        assertEquals (0, host.target (FIRST).releaseCount);
        environment.refresh ();
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);
        assertEquals ("1", host.playing ());
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
        assertEquals (1, host.target (SECOND).retireCount);

        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals (1, host.target (FIRST).retireCount);

        environment.safetyRelease (FIRST);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (SECOND).releaseCount);
    }


    @Test
    void releaseTransitionGapRetainsThePredecessorUntilItsPlaybackAppears ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));

        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        host.advanceReturn ();
        host.setPlaybackHidden (FIRST, true);

        environment.refresh ();

        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (FIRST).retireCount);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().containsKey (FIRST));

        host.setPlaybackHidden (FIRST, false);
        environment.refresh ();

        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (FIRST).retireCount);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
    }


    @Test
    void refreshPublishesPlaybackThatChangedBetweenHostSamples ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.setPlaybackHidden (FIRST, true);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        environment.acknowledgeSnapshotChange (environment.snapshotRevision ());
        assertFalse (environment.refresh ());
        final long hiddenRevision = environment.snapshotRevision ();

        host.setPlaybackHidden (FIRST, false);

        assertTrue (environment.refresh ());
        assertEquals (hiddenRevision + 1, environment.snapshotRevision ());
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET)));
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
    }


    @Test
    void releasingARetiredAncestorCannotInterruptTheLatestFill ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        environment.safetyRelease (FIRST);

        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).releaseCount);
        assertEquals ("2", host.playing ());
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());

        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        assertEquals (List.of ("press 1", "press 2", "release 2"), host.launchEvents);
        assertEquals ("2", host.playing ());
        drainReturnChain (host, environment);
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);
        assertEquals ("root", host.playing ());
    }


    @Test
    void rawSafetyReleaseOfTheTopOwnerUnwindsNewestToOldest ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));

        environment.safetyRelease (SECOND);

        assertEquals (List.of ("press 1", "press 2", "release 2"), host.launchEvents);
        assertEquals ("2", host.playing ());
        drainReturnChain (host, environment);
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);
        assertEquals ("root", host.playing ());
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
    }


    @Test
    void retainedLeaseReactivatesWithoutASecondPressAndCannotBePressedAgain ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (1, host.prepareCount);
        assertEquals (1, host.target (FIRST).pressCount);

        commitAndApply (environment, 2, result (Map.of (), Map.of (FIRST, FIRST_TARGET), List.of (new ReactivateClipTargetEffect (FIRST))));
        assertEquals (1, host.prepareCount);
        assertEquals (1, host.target (FIRST).pressCount);
        assertThrows (IllegalStateException.class, () -> environment.prepare (pressResult (5, FIRST, FIRST_TARGET)));
        assertThrows (IllegalStateException.class, () -> environment.prepare (pressResult (5, FIRST, SECOND_TARGET)));
        assertThrows (IllegalStateException.class, () -> environment.prepare (result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 5, FIRST_TARGET, DIFFERENT_LAUNCH_POLICY)))));

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 2, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET)));
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals ("1", host.playing ());
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
        assertEquals (1, host.target (FIRST).retireCount);
    }


    @Test
    void reloadOutputWithoutEffectsPreservesTheOrderedSessionAndActiveOwner ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));

        commitAndApply (environment, 2, result (Map.of (FIRST, DIM_RED, SECOND, BRIGHT_RED), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), List.of ()));

        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (List.of ("press 1", "press 2"), host.launchEvents);

        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 2, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        drainReturnChain (host, environment);
        assertEquals ("root", host.playing ());
    }


    @Test
    void catalogChangesDoNotRetargetOrReleaseAHeldActuator ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));

        host.queueState (catalog (2, SECOND_TARGET, FIRST_TARGET), Map.of (FIRST, FIRST_TARGET));
        assertTrue (environment.refresh ());
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (2, environment.snapshot ().clipCatalog ().generation ());
        assertTrue (environment.isFillPressed (FIRST));

        commitAndApply (environment, 2, result (
            Map.of (),
            Map.of (FIRST, SECOND_TARGET),
            List.of (new ReactivateClipTargetEffect (FIRST))));
        assertEquals (1, host.target (FIRST).pressCount);
        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 2, releaseResult (FIRST, Map.of (FIRST, SECOND_TARGET)));
        assertEquals (1, host.target (FIRST).releaseCount);
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
        assertEquals (Map.of (FIRST, SECOND_TARGET), host.desiredBindings);
    }


    @Test
    void generationFenceDiscardsAPreparedPressAfterTheCatalogMoves ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        final PreparedCoreResult prepared = environment.prepare (pressResult (1, FIRST, FIRST_TARGET));
        environment.commit (8, prepared);

        host.queueState (catalog (2, SECOND_TARGET), Map.of ());
        environment.refresh ();
        environment.apply (7);
        environment.apply (8);

        assertEquals (0, host.target (FIRST).pressCount);
        assertEquals (1, host.bindingUpdateCount);
    }


    @Test
    void rejectsUnknownOutputsBindingsEffectsAndDuplicateOwnerEffects ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        final ControlId unknown = new ControlId ("unknown.control");

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (unknown, DIM_RED), Map.of (), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (unknown, FIRST_TARGET), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (FIRST, new ClipTargetId (99)), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, FIRST_TARGET), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (), List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY)))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (FIRST, SECOND_TARGET), List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY)))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (), List.of (new ScheduleTimerEffect (new TimerId ("timer"), 1)))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY), new ReleaseClipTargetsEffect (FIRST)))));
        assertEquals (0, host.prepareCount);
    }


    @Test
    void retainedTargetsCannotBeReboundToAnotherOwner ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (
            Map.of (),
            Map.of (SECOND, FIRST_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, FIRST_TARGET, LAUNCH_POLICY)))));
        assertEquals (0, host.target (SECOND).pressCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
    }


    @Test
    void pressThatAppliesThenThrowsIsCompensatedAndRetainedUntilClean ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.target (FIRST).failPressAfterApply = true;
        host.target (FIRST).failRelease = true;
        final RecordingLog log = new RecordingLog ();
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);

        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (FIRST).releaseCount);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("press failed")));

        host.target (FIRST).failPressAfterApply = false;
        host.target (FIRST).failRelease = false;
        environment.refresh ();
        assertEquals (2, host.target (FIRST).releaseAttempts);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals ("1", host.playing ());
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
    }


    @Test
    void failedPressStopsLaterAcquisitionsInTheSameResult ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        host.target (FIRST).failPressAfterApply = true;
        host.target (FIRST).failRelease = true;
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        environment.setFillPressed (SECOND, true);

        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY), new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (SECOND).pressCount);

        host.target (FIRST).failPressAfterApply = false;
        host.target (FIRST).failRelease = false;
        environment.refresh ();
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).pressCount);
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
    }


    @Test
    void failedTopReleaseStopsTheUnwindAndRetriesNewestFirst ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final RecordingLog log = new RecordingLog ();
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        host.target (SECOND).failRelease = true;
        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        assertEquals (1, host.target (SECOND).releaseAttempts);
        assertEquals (0, host.target (FIRST).releaseAttempts);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals ("2", host.playing ());

        host.target (SECOND).failRelease = false;
        environment.refresh ();
        assertEquals (2, host.target (SECOND).releaseAttempts);
        assertEquals (0, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (SECOND).releaseCount);
        assertEquals (List.of ("press 1", "press 2", "release 2"), host.launchEvents);
        assertEquals ("2", host.playing ());

        advanceReturnAndRefresh (host, environment);
        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);
        assertEquals ("1", host.playing ());

        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("Fill-session release failed")));
    }


    @Test
    void pendingUnwindBlocksANewFillWithoutLaunchingItLater ()
    {
        final ClipTargetId thirdTarget = new ClipTargetId (3);
        final ControlId third = CoreControls.DRUM_FILL_3;
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET, thirdTarget);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        host.arm (third, thirdTarget);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET, third, thirdTarget), List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        host.target (SECOND).failRelease = true;
        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET, third, thirdTarget)));
        environment.setFillPressed (third, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET, third, thirdTarget), List.of (new PressClipTargetEffect (third, 1, thirdTarget, LAUNCH_POLICY))));

        assertEquals (0, host.target (third).pressCount);
        host.target (SECOND).failRelease = false;
        environment.refresh ();
        assertEquals (0, host.target (third).pressCount);
        assertEquals ("2", host.playing ());
        drainReturnChain (host, environment);
        assertEquals ("root", host.playing ());

        environment.setFillPressed (third, false);
        commitAndApply (environment, 1, releaseResult (third, Map.of (third, thirdTarget)));
        environment.setFillPressed (third, true);
        commitAndApply (environment, 1, pressResult (1, third, thirdTarget));
        assertEquals (1, host.target (third).pressCount);
    }


    @Test
    void pressingARetiredAncestorReactivatesItAcrossCatalogReplacementWithoutASecondPress ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));
        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));

        host.queueState (catalog (2, SECOND_TARGET), Map.of (SECOND, SECOND_TARGET));
        assertTrue (environment.refresh ());
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (SECOND, SECOND_TARGET), List.of (new ReactivateClipTargetEffect (FIRST))));

        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (1, host.target (SECOND).pressCount);
        assertEquals (1, host.target (SECOND).releaseCount);
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals ("2", host.playing ());

        advanceReturnAndRefresh (host, environment);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals ("1", host.playing ());
        assertEquals (1, host.target (SECOND).retireCount);

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (SECOND, SECOND_TARGET)));
        advanceReturnAndRefresh (host, environment);
        assertEquals ("root", host.playing ());
    }


    @Test
    void releasingAReactivationOwnerDuringFailedUnwindStillReturnsToRoot ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));
        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));

        host.target (SECOND).failRelease = true;
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), List.of (new ReactivateClipTargetEffect (FIRST))));
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());

        environment.setFillPressed (FIRST, false);
        environment.safetyRelease (FIRST);
        host.target (SECOND).failRelease = false;
        environment.refresh ();

        assertEquals (1, host.target (SECOND).releaseCount);
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals ("2", host.playing ());

        advanceReturnAndRefresh (host, environment);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals ("1", host.playing ());

        advanceReturnAndRefresh (host, environment);
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals ("root", host.playing ());
    }


    @Test
    void invalidationClearsBindingsLightsInputsAndEveryLease ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 4, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 4, result (
            Map.of (FIRST, BRIGHT_RED, SECOND, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        environment.invalidate (5);

        assertEquals (Map.of (), host.desiredBindings);
        assertEquals (OFF, environment.fillLightColor (FIRST));
        assertEquals (OFF, environment.fillLightColor (SECOND));
        assertTrue (environment.snapshot ().pressedControls ().isEmpty ());
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (SECOND).releaseCount);
        assertEquals (List.of ("press 1", "press 2", "release 2"), host.launchEvents);
        assertEquals ("2", host.playing ());

        drainReturnChain (host, environment);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);
        assertEquals ("root", host.playing ());
        assertEquals (5, environment.outputGeneration ());
    }


    @Test
    void failedInvalidationUnwindRetriesInOrderAndPublishesTheFinalOwnerChange ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 4, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 4, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));
        environment.setFillPressed (FIRST, false);
        environment.setFillPressed (SECOND, false);
        environment.acknowledgeSnapshotChange (environment.snapshotRevision ());

        host.target (SECOND).failRelease = true;
        final long retainedRevision = environment.snapshotRevision ();
        environment.invalidate (5);

        assertEquals (retainedRevision, environment.snapshotRevision ());
        assertEquals (1, host.target (SECOND).releaseAttempts);
        assertEquals (0, host.target (FIRST).releaseAttempts);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());

        host.target (SECOND).failRelease = false;
        assertFalse (environment.refresh ());
        assertEquals (List.of ("press 1", "press 2", "release 2"), host.launchEvents);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());

        advanceReturnAndRefresh (host, environment);
        assertEquals (retainedRevision + 1, environment.snapshotRevision ());
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);

        advanceReturnAndRefresh (host, environment);

        assertEquals (retainedRevision + 2, environment.snapshotRevision ());
        assertEquals (List.of ("press 1", "press 2", "release 2", "release 1"), host.launchEvents);
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals ("root", host.playing ());
    }


    private static DrumFillRuntimeEnvironment environment (final FakeClipHost host)
    {
        return new DrumFillRuntimeEnvironment (host, new RecordingLog (), () -> 0);
    }


    private static FakeClipHost host (final long generation, final ClipTargetId... targets)
    {
        return new FakeClipHost (catalog (generation, targets));
    }


    private static ClipCatalogSnapshot catalog (final long generation, final ClipTargetId... targets)
    {
        final List<CatalogClip> clips = new ArrayList<> (targets.length);
        for (final ClipTargetId target: targets)
            clips.add (new CatalogClip (target, "fill " + target.value ()));
        return new ClipCatalogSnapshot (generation, clips);
    }


    private static CoreResult pressResult (final long catalogGeneration, final ControlId owner, final ClipTargetId target)
    {
        return result (Map.of (), Map.of (owner, target), List.of (new PressClipTargetEffect (owner, catalogGeneration, target, LAUNCH_POLICY)));
    }


    private static CoreResult releaseResult (final ControlId owner, final Map<ControlId, ClipTargetId> bindings)
    {
        return result (Map.of (), bindings, List.of (new ReleaseClipTargetsEffect (owner)));
    }


    private static CoreResult result (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> bindings, final List<CoreEffect> effects)
    {
        return new CoreResult (new DesiredHardwareOutput (lights), bindings, effects);
    }


    private static void commitAndApply (final DrumFillRuntimeEnvironment environment, final long generation, final CoreResult result)
    {
        environment.commit (generation, environment.prepare (result));
        environment.apply (generation);
    }


    private static void advanceReturnAndRefresh (final FakeClipHost host, final DrumFillRuntimeEnvironment environment)
    {
        host.advanceReturn ();
        environment.refresh ();
    }


    private static void drainReturnChain (final FakeClipHost host, final DrumFillRuntimeEnvironment environment)
    {
        while (host.hasPendingReturn ())
            advanceReturnAndRefresh (host, environment);
    }


    private static final class FakeClipHost implements DrumFillClipHost
    {
        private final Map<ControlId, FakeTarget> targets = new LinkedHashMap<> ();
        private final List<String> launchEvents = new ArrayList<> ();
        private final List<String> playbackStack = new ArrayList<> (List.of ("root"));
        private ClipCatalogSnapshot catalog;
        private Map<ControlId, ClipTargetId> armed = Map.of ();
        private ClipCatalogSnapshot queuedCatalog;
        private Map<ControlId, ClipTargetId> queuedArmed;
        private Map<ControlId, ClipTargetId> desiredBindings = Map.of ();
        private long desiredGeneration;
        private int bindingUpdateCount;
        private int prepareCount;


        private FakeClipHost (final ClipCatalogSnapshot catalog)
        {
            this.catalog = catalog;
            for (int index = 0; index < catalog.clips ().size () && index < CoreControls.DRUM_FILLS.size (); index++)
            {
                final ControlId owner = CoreControls.DRUM_FILLS.get (index);
                this.targets.put (owner, new FakeTarget (catalog.clips ().get (index).targetId (), this.launchEvents, this.playbackStack));
            }
        }


        @Override
        public boolean refresh ()
        {
            if (this.queuedCatalog == null)
                return false;
            this.catalog = this.queuedCatalog;
            this.armed = this.queuedArmed;
            this.queuedCatalog = null;
            this.queuedArmed = null;
            return true;
        }


        @Override
        public ClipCatalogSnapshot clipCatalog ()
        {
            return this.catalog;
        }


        @Override
        public void setDesiredBindings (final long catalogGeneration, final Map<ControlId, ClipTargetId> bindings)
        {
            this.desiredGeneration = catalogGeneration;
            this.desiredBindings = Map.copyOf (bindings);
            this.bindingUpdateCount++;
        }


        @Override
        public Map<ControlId, ClipTargetId> armedClipTargets ()
        {
            return this.armed;
        }


        @Override
        public LaunchTarget prepare (final ControlId owner, final long catalogGeneration, final ClipTargetId targetId)
        {
            if (catalogGeneration != this.catalog.generation ())
                throw new IllegalArgumentException ("stale generation");
            if (!targetId.equals (this.armed.get (owner)))
                throw new IllegalArgumentException ("target is not armed");
            final FakeTarget target = this.targets.get (owner);
            if (target == null || !targetId.equals (target.targetId ()))
                throw new IllegalArgumentException ("unknown target");
            this.prepareCount++;
            return target;
        }


        private void arm (final ControlId owner, final ClipTargetId target)
        {
            final Map<ControlId, ClipTargetId> updated = new LinkedHashMap<> (this.armed);
            updated.put (owner, target);
            this.armed = Map.copyOf (updated);
            this.targets.computeIfAbsent (owner, ignored -> new FakeTarget (target, this.launchEvents, this.playbackStack));
        }


        private void queueState (final ClipCatalogSnapshot newCatalog, final Map<ControlId, ClipTargetId> newArmed)
        {
            this.queuedCatalog = newCatalog;
            this.queuedArmed = Map.copyOf (newArmed);
        }


        private FakeTarget target (final ControlId owner)
        {
            return this.targets.get (owner);
        }


        private boolean hasPendingReturn ()
        {
            return this.targets.values ().stream ().anyMatch (FakeTarget::hasPendingReturn);
        }


        private void advanceReturn ()
        {
            final List<FakeTarget> pending = this.targets.values ().stream ().filter (FakeTarget::hasPendingReturn).toList ();
            assertEquals (1, pending.size (), "Bitwig may acknowledge only the one requested Return layer");
            final FakeTarget target = pending.getFirst ();
            final String targetName = Long.toString (target.targetId.value ());
            assertEquals (targetName, this.playbackStack.getLast (), "Return frames must settle newest first");
            this.playbackStack.removeLast ();
            target.returnAcknowledged = true;
        }


        private void setPlaybackHidden (final ControlId owner, final boolean hidden)
        {
            this.target (owner).playbackHidden = hidden;
        }


        private String playing ()
        {
            return this.playbackStack.getLast ();
        }
    }


    private static final class FakeTarget implements DrumFillClipHost.LaunchTarget
    {
        private final ClipTargetId targetId;
        private final List<String> launchEvents;
        private final List<String> playbackStack;
        private int pressCount;
        private int releaseAttempts;
        private int releaseCount;
        private int retireCount;
        private boolean failPressAfterApply;
        private boolean failRelease;
        private boolean releaseRequested;
        private boolean returnAcknowledged;
        private boolean retired;
        private boolean playbackHidden;


        private FakeTarget (final ClipTargetId targetId, final List<String> launchEvents, final List<String> playbackStack)
        {
            this.targetId = targetId;
            this.launchEvents = launchEvents;
            this.playbackStack = playbackStack;
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
            this.pressCount++;
            this.releaseRequested = false;
            this.returnAcknowledged = false;
            this.retired = false;
            final String target = Long.toString (this.targetId.value ());
            this.launchEvents.add ("press " + target);
            this.playbackStack.add (target);
            if (this.failPressAfterApply)
                throw new IllegalStateException ("press applied then failed");
        }


        @Override
        public void release ()
        {
            this.releaseAttempts++;
            if (this.failRelease)
                throw new IllegalStateException ("release failed");
            assertFalse (this.releaseRequested, "A successful host release must not be requested twice");
            final String target = Long.toString (this.targetId.value ());
            assertEquals (target, this.playbackStack.getLast (), "Return frames must unwind newest first");
            this.launchEvents.add ("release " + target);
            this.releaseRequested = true;
            this.releaseCount++;
        }


        @Override
        public DrumFillClipHost.PlaybackState playbackState ()
        {
            final String target = Long.toString (this.targetId.value ());
            return new DrumFillClipHost.PlaybackState (!this.playbackHidden && target.equals (this.playbackStack.getLast ()), false, false);
        }


        @Override
        public void retire ()
        {
            assertFalse (this.playbackState ().playing (), "A playing target cannot be retired");
            this.retired = true;
            this.retireCount++;
        }


        private boolean hasPendingReturn ()
        {
            return this.releaseRequested && !this.returnAcknowledged;
        }
    }


    private static final class RecordingLog implements RuntimeLog
    {
        private final List<String> warnings = new ArrayList<> ();


        @Override
        public void info (final String message)
        {
            // Not needed by these tests.
        }


        @Override
        public void warn (final String message)
        {
            this.warnings.add (message);
        }
    }
}
