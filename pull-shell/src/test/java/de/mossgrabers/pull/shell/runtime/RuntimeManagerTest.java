// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertEquals (1, firstCore.stopCount);
        assertTrue (firstSource.closed);
        assertEquals ("build-b", manager.activeBuildId ());
        assertEquals (2, manager.activeGeneration ());
        assertEquals (List.of (1L, 2L), environment.committedGenerations);

        manager.close ();
        assertEquals (1, secondCore.stopCount);
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
        assertEquals (0, stableCore.stopCount);
        assertEquals (1, brokenCore.stopCount);
        assertTrue (brokenSource.closed);
        assertEquals (List.of (1L), environment.committedGenerations);

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
        assertEquals (1, supersededCore.stopCount);
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
        assertEquals (0, stableCore.stopCount);
        assertFalse (stableSource.closed);
        assertEquals (1, candidateCore.stopCount);
        assertTrue (candidateSource.closed);
        assertEquals (List.of (1L), environment.committedGenerations);

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
        private int handleCount;
        private int stopCount;


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


        @Override
        public void stop ()
        {
            this.stopCount++;
        }
    }


    private static final class TestEnvironment implements CoreRuntimeEnvironment
    {
        private final ShellCapabilities capabilities;
        private final List<Long> committedGenerations = new ArrayList<> ();
        private long revision;
        private boolean failNextCommit;


        private TestEnvironment (final ShellCapabilities capabilities)
        {
            this.capabilities = capabilities;
        }


        @Override
        public ControllerSnapshot snapshot ()
        {
            final long currentRevision = this.revision++;
            return new ControllerSnapshot (currentRevision, currentRevision, this.capabilities, Set.of (), Set.of ());
        }


        @Override
        public void validate (final CoreResult result)
        {
            // API constructors have already validated this test result.
        }


        @Override
        public void commit (final long generation, final CoreResult result)
        {
            if (this.failNextCommit)
            {
                this.failNextCommit = false;
                throw new IllegalStateException ("commit failed before mutation");
            }
            this.committedGenerations.add (Long.valueOf (generation));
        }


        @Override
        public void invalidate (final long generation)
        {
            // Nothing scheduled in this fake.
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
