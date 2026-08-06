// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transaction and generation tests for the controller-thread runtime owner.
 */
class RuntimeManagerTest
{
    @Test
    void activatesStructurallyIndependentCoresAndTransfersCompatibleState ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RecordingLog log = new RecordingLog ();
        final RuntimeManager manager = new RuntimeManager (environment, log);
        final TestCore firstCore = new TestCore (7);
        final TestCore secondCore = new TestCore (0);
        final TestSource firstSource = source ("build-a", firstCore);
        final TestSource secondSource = source ("build-b", secondCore);

        manager.start ();
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("build-a", firstSource, () -> true).state ());
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("build-b", secondSource, () -> true).state ());

        assertArrayEquals (new byte []
        {
            7
        }, secondCore.previousState.orElseThrow ().payload ());
        assertTrue (firstSource.closed);
        assertEquals ("build-b", manager.activeBuildId ());
        assertEquals (2, manager.activeGeneration ());
        assertEquals (List.of (1L, 2L), environment.committedGenerations);
        assertEquals (List.of (1L, 2L), environment.appliedGenerations);

        manager.close ();
        assertTrue (secondSource.closed);
    }


    @Test
    void brokenCandidateLeavesPreviousCoreActive ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RuntimeManager manager = new RuntimeManager (environment, new RecordingLog ());
        final TestCore stableCore = new TestCore (1);
        final TestCore brokenCore = new TestCore (2);
        brokenCore.failStart = true;
        final TestSource stableSource = source ("stable", stableCore);
        final TestSource brokenSource = source ("broken", brokenCore);

        manager.start ();
        manager.activate ("stable", stableSource, () -> true);
        final ActivationResult failure = manager.activate ("broken", brokenSource, () -> true);

        assertEquals (ActivationResult.State.FAILED, failure.state ());
        assertEquals ("stable", failure.activeBuildId ());
        assertEquals ("stable", manager.activeBuildId ());
        assertEquals (1, manager.activeGeneration ());
        assertFalse (stableSource.closed);
        assertTrue (brokenSource.closed);
        assertEquals (List.of (1L), environment.committedGenerations);
        assertEquals (List.of (1L), environment.appliedGenerations);

        manager.close ();
    }


    @Test
    void transactionOwnerRejectsReplacementUntilItsEnvironmentIsIdle ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RuntimeManager manager = new RuntimeManager (environment, new RecordingLog ());
        manager.start ();
        manager.activate ("stable", source ("stable", new TestCore (1)), () -> true);

        environment.replacementAllowed = false;
        final TestSource blocked = source ("blocked", new TestCore (2));
        final ActivationResult result = manager.activate ("blocked", blocked, () -> true);

        assertEquals (ActivationResult.State.BLOCKED, result.state ());
        assertEquals ("stable", manager.activeBuildId ());
        assertEquals (1, manager.activeGeneration ());
        assertTrue (blocked.closed);

        environment.replacementAllowed = true;
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("replacement", source ("replacement", new TestCore (3)), () -> true).state ());
        assertEquals ("replacement", manager.activeBuildId ());
        manager.close ();
    }


    @Test
    void checkpointFailureFallsBackToAuthoritativeSnapshot ()
    {
        final RecordingLog log = new RecordingLog ();
        final RuntimeManager manager = new RuntimeManager (new TestEnvironment (ShellCapabilities.empty ()), log);
        final TestCore firstCore = new TestCore (1);
        firstCore.failCheckpoint = true;
        final TestCore secondCore = new TestCore (0);

        manager.start ();
        manager.activate ("first", source ("first", firstCore), () -> true);
        final ActivationResult result = manager.activate ("second", source ("second", secondCore), () -> true);

        assertEquals (ActivationResult.State.ACTIVE, result.state ());
        assertTrue (secondCore.previousState.isEmpty ());
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("checkpoint")));
        manager.close ();
    }


    @Test
    void supersededCandidateNeverCommitsBufferedResult ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RuntimeManager manager = new RuntimeManager (environment, new RecordingLog ());
        final TestCore stableCore = new TestCore (1);
        final TestCore supersededCore = new TestCore (2);
        final TestSource supersededSource = source ("superseded", supersededCore);
        final AtomicInteger latestChecks = new AtomicInteger ();

        manager.start ();
        manager.activate ("stable", source ("stable", stableCore), () -> true);
        final ActivationResult result = manager.activate ("superseded", supersededSource, () -> latestChecks.incrementAndGet () == 1);

        assertEquals (ActivationResult.State.SUPERSEDED, result.state ());
        assertEquals ("stable", manager.activeBuildId ());
        assertEquals (List.of (1L), environment.committedGenerations);
        assertEquals (List.of (1L), environment.appliedGenerations);
        assertTrue (supersededSource.closed);
        manager.close ();
    }


    @Test
    void staleGenerationEventsAreDiscarded ()
    {
        final RuntimeManager manager = new RuntimeManager (new TestEnvironment (ShellCapabilities.empty ()), new RecordingLog ());
        final TestCore firstCore = new TestCore (1);
        final TestCore secondCore = new TestCore (2);
        final CoreEvent event = new ButtonInputEvent (1, 0, new ControlId ("test"), true);

        manager.start ();
        manager.activate ("first", source ("first", firstCore), () -> true);
        final long oldGeneration = manager.activeGeneration ();
        manager.activate ("second", source ("second", secondCore), () -> true);

        assertFalse (manager.handle (oldGeneration, event));
        assertEquals (0, secondCore.handleCount);
        assertTrue (manager.handle (manager.activeGeneration (), event));
        assertEquals (1, secondCore.handleCount);
        manager.close ();
    }


    @Test
    void descriptorMismatchIsRejectedBeforeCoreCreation ()
    {
        final RuntimeManager manager = new RuntimeManager (new TestEnvironment (ShellCapabilities.empty ()), new RecordingLog ());
        final TestCore core = new TestCore (0);
        final TestProvider provider = new TestProvider (descriptor ("different"), core);
        final TestSource source = new TestSource (provider);

        manager.start ();
        final ActivationResult result = manager.activate ("requested", source, () -> true);

        assertEquals (ActivationResult.State.FAILED, result.state ());
        assertEquals (0, provider.createCount);
        assertTrue (source.closed);
        manager.close ();
        manager.close ();
    }


    @Test
    void missingCapabilitiesAreRejectedBeforeCoreCreation ()
    {
        final ShellCapabilities requiredCapabilities = new ShellCapabilities (Map.of ("missing.capability", Integer.valueOf (1)));
        final RuntimeManager manager = new RuntimeManager (new TestEnvironment (ShellCapabilities.empty ()), new RecordingLog ());
        final TestCore core = new TestCore (0);
        final CoreDescriptor descriptor = new CoreDescriptor (CoreApi.VERSION, "requested", "test.state", 1, requiredCapabilities);
        final TestProvider provider = new TestProvider (descriptor, core);
        final TestSource source = new TestSource (provider);

        manager.start ();
        final ActivationResult result = manager.activate ("requested", source, () -> true);

        assertEquals (ActivationResult.State.FAILED, result.state ());
        assertEquals (0, provider.createCount);
        assertTrue (source.closed);
        manager.close ();
    }


    @Test
    void atomicCommitFailureLeavesThePreviousCoreActive ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RuntimeManager manager = new RuntimeManager (environment, new RecordingLog ());
        final TestCore stableCore = new TestCore (1);
        final TestCore candidateCore = new TestCore (2);
        final TestSource stableSource = source ("stable", stableCore);
        final TestSource candidateSource = source ("candidate", candidateCore);

        manager.start ();
        manager.activate ("stable", stableSource, () -> true);
        environment.failNextCommit = true;
        final ActivationResult result = manager.activate ("candidate", candidateSource, () -> true);

        assertEquals (ActivationResult.State.FAILED, result.state ());
        assertEquals ("stable", result.activeBuildId ());
        assertEquals ("stable", manager.activeBuildId ());
        assertFalse (stableSource.closed);
        assertTrue (candidateSource.closed);
        assertEquals (List.of (1L), environment.committedGenerations);
        assertEquals (List.of (1L), environment.appliedGenerations);

        manager.close ();
    }


    @Test
    void prepareFailureLeavesThePreviousCoreActive ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RuntimeManager manager = new RuntimeManager (environment, new RecordingLog ());
        final TestCore stableCore = new TestCore (1);
        final TestCore candidateCore = new TestCore (2);
        final TestSource stableSource = source ("stable", stableCore);
        final TestSource candidateSource = source ("candidate", candidateCore);

        manager.start ();
        manager.activate ("stable", stableSource, () -> true);
        environment.failNextPrepare = true;
        final ActivationResult result = manager.activate ("candidate", candidateSource, () -> true);

        assertEquals (ActivationResult.State.FAILED, result.state ());
        assertEquals ("stable", manager.activeBuildId ());
        assertEquals (1, manager.activeGeneration ());
        assertFalse (stableSource.closed);
        assertTrue (candidateSource.closed);
        assertEquals (List.of (1L), environment.committedGenerations);
        assertEquals (List.of (1L), environment.appliedGenerations);

        manager.close ();
    }


    @Test
    void publishesCandidateBeforeApplyingAndClosesPreviousSourceAfterward ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RuntimeManager manager = new RuntimeManager (environment, new RecordingLog ());
        final TestCore stableCore = new TestCore (1);
        final TestSource stableSource = source ("stable", stableCore);
        final List<String> order = new ArrayList<> ();
        final List<CommitObservation> commitObservations = new ArrayList<> ();
        final List<ApplyObservation> applyObservations = new ArrayList<> ();

        manager.start ();
        manager.activate ("stable", stableSource, () -> true);
        environment.onCommit = generation -> commitObservations.add (new CommitObservation (generation, manager.activeBuildId (), manager.activeGeneration (), stableSource.closed));
        environment.onApply = generation -> {
            order.add ("apply");
            applyObservations.add (new ApplyObservation (generation, manager.activeBuildId (), manager.activeGeneration (), stableSource.closed, environment.committedGenerations));
        };
        stableSource.onClose = () -> order.add ("close");

        final ActivationResult result = manager.activate ("candidate", source ("candidate", new TestCore (2)), () -> true);

        assertEquals (ActivationResult.State.ACTIVE, result.state ());
        assertEquals (List.of ("apply", "close"), order);
        assertEquals (List.of (new CommitObservation (2, "stable", 1, false)), commitObservations);
        assertEquals (List.of (new ApplyObservation (2, "candidate", 2, false, List.of (1L, 2L))), applyObservations);
        manager.close ();
    }


    @Test
    void applyFailureKeepsCommittedCandidateActive ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RecordingLog log = new RecordingLog ();
        final RuntimeManager manager = new RuntimeManager (environment, log);
        final TestCore stableCore = new TestCore (1);
        final TestCore candidateCore = new TestCore (2);
        final TestSource stableSource = source ("stable", stableCore);
        final TestSource candidateSource = source ("candidate", candidateCore);

        manager.start ();
        manager.activate ("stable", stableSource, () -> true);
        environment.failNextApply = true;
        final ActivationResult result = manager.activate ("candidate", candidateSource, () -> true);

        assertEquals (ActivationResult.State.ACTIVE, result.state ());
        assertEquals ("candidate", manager.activeBuildId ());
        assertEquals (2, manager.activeGeneration ());
        assertTrue (stableSource.closed);
        assertFalse (candidateSource.closed);
        assertEquals (List.of (1L, 2L), environment.committedGenerations);
        assertEquals (List.of (1L, 2L), environment.appliedGenerations);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("Committed core effects failed")));

        manager.close ();
    }


    @Test
    void eventApplyFailureIsLoggedAfterTheResultCommits ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RecordingLog log = new RecordingLog ();
        final RuntimeManager manager = new RuntimeManager (environment, log);
        final CoreEvent event = new ButtonInputEvent (1, 0, new ControlId ("test"), true);

        manager.start ();
        manager.activate ("stable", source ("stable", new TestCore (1)), () -> true);
        environment.failNextApply = true;

        assertTrue (manager.handle (manager.activeGeneration (), event));
        assertEquals ("stable", manager.activeBuildId ());
        assertEquals (List.of (1L, 1L), environment.committedGenerations);
        assertEquals (List.of (1L, 1L), environment.appliedGenerations);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("Committed core effects failed")));
        manager.close ();
    }


    @Test
    void eventPrepareFailureFaultsAndInvalidatesTheActiveCore ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RecordingLog log = new RecordingLog ();
        final RuntimeManager manager = new RuntimeManager (environment, log);
        final TestCore core = new TestCore (1);
        final CoreEvent event = new ButtonInputEvent (1, 0, new ControlId ("test"), true);

        manager.start ();
        final TestSource source = source ("stable", core);
        manager.activate ("stable", source, () -> true);
        environment.failNextPrepare = true;

        assertFalse (manager.handle (manager.activeGeneration (), event));
        assertEquals (1, core.handleCount);
        assertEquals ("", manager.activeBuildId ());
        assertEquals (0, manager.activeGeneration ());
        assertTrue (source.closed);
        assertEquals (List.of (1L), environment.committedGenerations);
        assertEquals (List.of (1L), environment.appliedGenerations);
        assertEquals (List.of (2L), environment.invalidatedGenerations);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("Faulted reloadable core stable")));
        manager.close ();
    }


    @Test
    void coreHandleFailureFaultsInsteadOfLeavingAControlSwallowingCoreActive ()
    {
        final TestEnvironment environment = new TestEnvironment (ShellCapabilities.empty ());
        final RecordingLog log = new RecordingLog ();
        final RuntimeManager manager = new RuntimeManager (environment, log);
        final TestCore core = new TestCore (1);
        final TestSource source = source ("stable", core);
        final CoreEvent event = new ButtonInputEvent (1, 0, new ControlId ("test"), true);

        manager.start ();
        manager.activate ("stable", source, () -> true);
        core.failHandle = true;

        assertFalse (manager.handle (manager.activeGeneration (), event));
        assertEquals ("", manager.activeBuildId ());
        assertEquals (0, manager.activeGeneration ());
        assertTrue (source.closed);
        assertEquals (List.of (2L), environment.invalidatedGenerations);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("broken handle")));
        manager.close ();
    }


    private static TestSource source (final String buildId, final TestCore core)
    {
        return new TestSource (new TestProvider (descriptor (buildId), core));
    }


    private static CoreDescriptor descriptor (final String buildId)
    {
        return new CoreDescriptor (CoreApi.VERSION, buildId, "test.state", 1, ShellCapabilities.empty ());
    }


    private static final class TestSource implements CoreProviderSource
    {
        private final CoreProvider provider;
        private boolean closed;
        private Runnable onClose = () -> {
            // No observer by default.
        };


        private TestSource (final CoreProvider provider)
        {
            this.provider = provider;
        }


        @Override
        public CoreProvider instantiateProvider ()
        {
            return this.provider;
        }


        @Override
        public <T> T invokeWithContext (final Supplier<T> operation)
        {
            return operation.get ();
        }


        @Override
        public void close ()
        {
            this.closed = true;
            this.onClose.run ();
        }
    }


    private static final class TestProvider implements CoreProvider
    {
        private final CoreDescriptor descriptor;
        private final ControllerCore core;
        private int createCount;


        private TestProvider (final CoreDescriptor descriptor, final ControllerCore core)
        {
            this.descriptor = descriptor;
            this.core = core;
        }


        @Override
        public CoreDescriptor descriptor ()
        {
            return this.descriptor;
        }


        @Override
        public ControllerCore create ()
        {
            this.createCount++;
            return this.core;
        }
    }


    private static final class TestCore implements ControllerCore
    {
        private final int checkpointValue;
        private Optional<StateEnvelope> previousState = Optional.empty ();
        private boolean failStart;
        private boolean failCheckpoint;
        private boolean failHandle;
        private int handleCount;


        private TestCore (final int checkpointValue)
        {
            this.checkpointValue = checkpointValue;
        }


        @Override
        public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
        {
            this.previousState = previousState;
            if (this.failStart)
                throw new IllegalStateException ("broken start");
            return CoreResult.empty ();
        }


        @Override
        public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
        {
            this.handleCount++;
            if (this.failHandle)
                throw new IllegalStateException ("broken handle");
            return CoreResult.empty ();
        }


        @Override
        public StateEnvelope checkpoint ()
        {
            if (this.failCheckpoint)
                throw new IllegalStateException ("broken checkpoint");
            return new StateEnvelope ("test.state", 1, new byte []
            {
                (byte) this.checkpointValue
            });
        }

    }


    private static final class TestEnvironment implements CoreRuntimeEnvironment
    {
        private final ShellCapabilities capabilities;
        private final List<Long> committedGenerations = new ArrayList<> ();
        private final List<Long> appliedGenerations = new ArrayList<> ();
        private final List<Long> invalidatedGenerations = new ArrayList<> ();
        private long revision;
        private boolean failNextPrepare;
        private boolean failNextCommit;
        private boolean failNextApply;
        private boolean replacementAllowed = true;
        private LongConsumer onCommit = generation -> {
            // No observer by default.
        };
        private LongConsumer onApply = generation -> {
            // No observer by default.
        };


        private TestEnvironment (final ShellCapabilities capabilities)
        {
            this.capabilities = capabilities;
        }


        @Override
        public boolean canReplaceActiveCore ()
        {
            return this.replacementAllowed;
        }


        @Override
        public ControllerSnapshot snapshot ()
        {
            final long currentRevision = this.revision++;
            return new ControllerSnapshot (currentRevision, currentRevision, this.capabilities, ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
        }


        @Override
        public PreparedCoreResult prepare (final CoreResult result)
        {
            if (this.failNextPrepare)
            {
                this.failNextPrepare = false;
                throw new IllegalStateException ("prepare failed without mutation");
            }
            return new TestPreparedResult (result);
        }


        @Override
        public void commit (final long generation, final PreparedCoreResult result)
        {
            if (this.failNextCommit)
            {
                this.failNextCommit = false;
                throw new IllegalStateException ("commit failed before mutation");
            }
            this.committedGenerations.add (Long.valueOf (generation));
            this.onCommit.accept (generation);
        }


        @Override
        public void apply (final long generation)
        {
            this.appliedGenerations.add (Long.valueOf (generation));
            this.onApply.accept (generation);
            if (this.failNextApply)
            {
                this.failNextApply = false;
                throw new IllegalStateException ("apply failed after commit");
            }
        }


        @Override
        public void invalidate (final long generation)
        {
            this.invalidatedGenerations.add (Long.valueOf (generation));
        }
    }


    private record TestPreparedResult (CoreResult result) implements PreparedCoreResult
    {
    }


    private record CommitObservation (long committedGeneration, String activeBuildId, long activeGeneration, boolean previousSourceClosed)
    {
    }


    private record ApplyObservation (long appliedGeneration, String activeBuildId, long activeGeneration, boolean previousSourceClosed, List<Long> committedGenerations)
    {
        private ApplyObservation
        {
            committedGenerations = List.copyOf (committedGenerations);
        }
    }


    private static final class RecordingLog implements RuntimeLog
    {
        private final List<String> warnings = new ArrayList<> ();


        @Override
        public void info (final String message)
        {
            // Not needed by these assertions.
        }


        @Override
        public void warn (final String message)
        {
            this.warnings.add (message);
        }
    }
}
