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
        assertEquals (Integer.valueOf (2), initial.capabilities ().versions ().get (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD));

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

        environment.failSafeFillOutputOff (FIRST);
        assertEquals (OFF, environment.fillLightColor (FIRST));
        assertEquals (BRIGHT_RED, environment.fillLightColor (SECOND));
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
    void twoFillPadsAcquireAndReleaseIndependentExactLeases ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        environment.setFillPressed (SECOND, true);

        final CoreResult pressBoth = result (
            Map.of (FIRST, BRIGHT_RED, SECOND, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (
                new PressClipTargetEffect (FIRST, 5, FIRST_TARGET, LAUNCH_POLICY),
                new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY)));
        final PreparedCoreResult prepared = environment.prepare (pressBoth);
        environment.commit (1, prepared);
        assertEquals (0, host.target (FIRST).pressCount);
        assertEquals (0, host.target (SECOND).pressCount);

        environment.apply (1);
        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (1, host.target (SECOND).pressCount);

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (SECOND, SECOND_TARGET)));
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).releaseCount);
        assertTrue (environment.isFillPressed (SECOND));

        environment.safetyRelease (SECOND);
        assertEquals (1, host.target (SECOND).releaseCount);
        assertFalse (environment.isFillPressed (SECOND));
    }


    @Test
    void heldReloadReusesItsLeaseAndCannotRedirectIt ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (1, host.prepareCount);
        assertEquals (1, host.target (FIRST).pressCount);

        commitAndApply (environment, 2, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (1, host.prepareCount);
        assertEquals (1, host.target (FIRST).pressCount);
        assertThrows (IllegalStateException.class, () -> environment.prepare (pressResult (5, FIRST, SECOND_TARGET)));
        assertThrows (IllegalStateException.class, () -> environment.prepare (result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 5, FIRST_TARGET, DIFFERENT_LAUNCH_POLICY)))));

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 2, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET)));
        assertEquals (1, host.target (FIRST).releaseCount);
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

        commitAndApply (environment, 2, pressResult (2, FIRST, FIRST_TARGET));
        assertEquals (1, host.target (FIRST).pressCount);
        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 2, releaseResult (FIRST, Map.of (FIRST, SECOND_TARGET)));
        assertEquals (1, host.target (FIRST).releaseCount);
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
    }


    @Test
    void failedReleaseIsRetriedWithoutAffectingAnotherOwner ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final RecordingLog log = new RecordingLog ();
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY), new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        host.target (FIRST).failRelease = true;
        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (SECOND, SECOND_TARGET)));
        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (SECOND).releaseAttempts);

        host.target (FIRST).failRelease = false;
        environment.refresh ();
        assertEquals (2, host.target (FIRST).releaseAttempts);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).releaseCount);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("release failed")));

        environment.safetyRelease (SECOND);
        assertEquals (1, host.target (SECOND).releaseCount);
    }


    @Test
    void failedSupersededReleaseRequiresAFreshReplacementPress ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final RecordingLog log = new RecordingLog ();
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));

        environment.setFillPressed (SECOND, true);
        host.target (FIRST).failRelease = true;
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (
                new ReleaseClipTargetsEffect (FIRST),
                new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (SECOND).pressCount);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("another fill release")));

        host.target (FIRST).failRelease = false;
        environment.refresh ();
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).pressCount);

        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (SECOND, SECOND_TARGET)));
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, pressResult (1, SECOND, SECOND_TARGET));
        assertEquals (1, host.target (SECOND).pressCount);
    }


    @Test
    void invalidationClearsBindingsLightsInputsAndEveryLease ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final DrumFillRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 4, result (
            Map.of (FIRST, BRIGHT_RED, SECOND, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY), new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        environment.invalidate (5);

        assertEquals (Map.of (), host.desiredBindings);
        assertEquals (OFF, environment.fillLightColor (FIRST));
        assertEquals (OFF, environment.fillLightColor (SECOND));
        assertTrue (environment.snapshot ().pressedControls ().isEmpty ());
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (SECOND).releaseCount);
        assertEquals (5, environment.outputGeneration ());
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


    private static final class FakeClipHost implements DrumFillClipHost
    {
        private final Map<ControlId, FakeTarget> targets = new LinkedHashMap<> ();
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
                this.targets.put (owner, new FakeTarget (catalog.clips ().get (index).targetId ()));
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
            this.targets.computeIfAbsent (owner, ignored -> new FakeTarget (target));
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
    }


    private static final class FakeTarget implements DrumFillClipHost.LaunchTarget
    {
        private final ClipTargetId targetId;
        private int pressCount;
        private int releaseAttempts;
        private int releaseCount;
        private boolean failPressAfterApply;
        private boolean failRelease;


        private FakeTarget (final ClipTargetId targetId)
        {
            this.targetId = targetId;
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
            if (this.failPressAfterApply)
                throw new IllegalStateException ("press applied then failed");
        }


        @Override
        public void release ()
        {
            this.releaseAttempts++;
            if (this.failRelease)
                throw new IllegalStateException ("release failed");
            this.releaseCount++;
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
