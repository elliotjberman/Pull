// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.InputRoute;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputEvent;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** End-to-end asynchronous snapback lifecycle across the child-core and stable shell boundary. */
class ParameterSnapbackIntegrationTest
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId PAGE_RIGHT = PushControlIds.button ("PAGE_RIGHT");


    @Test
    void waitsForAuthoritativeRestoreBeforeStableNavigationOrCoreReplacement (@TempDir final Path temporaryDirectory) throws Exception
    {
        final AsyncParameterBridge bridge = new AsyncParameterBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (new EmptyClipHost (), bridge, NoOpLog.INSTANCE, new IncrementingClock ());
        environment.setInputRouteValidator (ignored -> true);
        environment.setControllerActionValidator (ignored -> true);

        final RuntimeManager manager = new RuntimeManager (environment, NoOpLog.INSTANCE);
        final PhysicalInputRouter<ControlId> inputs = inputRouter (environment, manager);
        environment.setDeferredInputRelease (inputs::releaseDeferredStableDispatches);
        environment.setInputLifecycleIdle (inputs::isIdle);

        manager.start ();
        final Path coreJar = createCoreJar (temporaryDirectory.resolve ("pull-core.jar"));
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("unpublished", new CoreJarLoader ().load (coreJar), () -> true).state ());
        assertEquals (DesiredParameterBanks.empty (), bridge.lastAppliedBanks);
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (environment, NoOpLog.INSTANCE, event -> manager.handle (manager.activeGeneration (), event));
        runtime.start ();

        route (inputs, SHIFT, InputPhase.BEGIN, () -> {
            // Shift's stable observer remains active during migration.
        });
        runtime.tick ();
        assertEquals (new DesiredParameterBanks (Set.of (ParameterBankId.ACTIVE, ParameterBankId.GLOBAL)), bridge.lastAppliedBanks);

        final AtomicInteger staleMutation = new AtomicInteger ();
        bridge.resolveMutations = false;
        bridge.requireResolvedMutation = true;
        runtime.handleParameterMutation (ContinuousID.KNOB1, bridge.control, staleMutation::incrementAndGet);
        assertEquals (0, staleMutation.get ());
        bridge.resolveMutations = true;

        runtime.handleParameterMutation (ContinuousID.KNOB1, bridge.control, () -> bridge.submit (40));
        assertEquals (100, bridge.authoritativeValue);
        assertEquals (40, bridge.submittedValue);
        bridge.advanceHost ();
        runtime.tick ();

        final AtomicInteger stableNavigation = new AtomicInteger ();
        route (inputs, PAGE_RIGHT, InputPhase.BEGIN, stableNavigation::incrementAndGet);
        route (inputs, PAGE_RIGHT, InputPhase.END, () -> {
            // Stable button release has no navigation side effect.
        });
        assertEquals (0, stableNavigation.get ());
        assertFalse (manager.canReplaceActiveCore ());
        final Path replacementJar = createCoreJar (temporaryDirectory.resolve ("replacement-core.jar"));
        assertEquals (ActivationResult.State.BLOCKED, manager.activate ("unpublished", new CoreJarLoader ().load (replacementJar), () -> true).state ());
        assertEquals (1, manager.activeGeneration ());

        runtime.tick ();
        runtime.tick ();
        assertEquals (40, bridge.authoritativeValue);
        assertEquals (100, bridge.submittedValue);
        assertEquals (0, stableNavigation.get ());

        runtime.tick ();
        assertEquals (0, stableNavigation.get ());
        bridge.advanceHost ();
        runtime.tick ();
        assertEquals (0, stableNavigation.get ());
        runtime.tick ();

        assertEquals (100, bridge.authoritativeValue);
        assertEquals (1, stableNavigation.get ());
        assertEquals (0, inputs.deferredStableDispatchCount ());
        assertFalse (manager.canReplaceActiveCore ());
        route (inputs, SHIFT, InputPhase.END, () -> {
            // Shift's stable observer remains active during migration.
        });
        assertTrue (manager.canReplaceActiveCore ());
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("unpublished", new CoreJarLoader ().load (replacementJar), () -> true).state ());
        assertEquals (2, manager.activeGeneration ());

        runtime.close ();
        manager.close ();
    }


    private static PhysicalInputRouter<ControlId> inputRouter (final ControllerRuntimeEnvironment environment, final RuntimeManager manager)
    {
        final PhysicalControlRegistry<ControlId> registry = PhysicalControlRegistry.<ControlId>builder (2)
            .register (SHIFT, InputKind.BUTTON)
            .register (PAGE_RIGHT, InputKind.BUTTON)
            .build ();
        return new PhysicalInputRouter<> (
            registry,
            (control, kind) -> toShellRoute (environment.desiredInputRoutes ().modeOrNull (control, de.mossgrabers.pull.core.api.event.InputKind.valueOf (kind.name ()))),
            event -> deliver (environment, manager, event),
            (control, kind, stableAction) -> environment.blocksStableAction (control, de.mossgrabers.pull.core.api.event.InputKind.valueOf (kind.name ()), stableAction),
            System::nanoTime,
            manager::activeGeneration);
    }


    private static InputRoute toShellRoute (final InputRouteMode mode)
    {
        return mode == null ? InputRoute.NONE : InputRoute.valueOf (mode.name ());
    }


    private static void deliver (final ControllerRuntimeEnvironment environment, final RuntimeManager manager, final PhysicalInputEvent<ControlId> event)
    {
        final CoreEvent input = event.stableAction ().<CoreEvent>map (environment::controllerAction).orElseGet ( () -> environment.controllerInput (
            event.control (),
            de.mossgrabers.pull.core.api.event.InputKind.valueOf (event.kind ().name ()),
            de.mossgrabers.pull.core.api.event.InputPhase.valueOf (event.phase ().name ()),
            event.value ()));
        manager.handle (event.ownerGeneration (), input);
    }


    private static void route (final PhysicalInputRouter<ControlId> inputs, final ControlId control, final InputPhase phase, final Runnable stableCommand)
    {
        final ControllerActionIntent stableAction = PAGE_RIGHT.equals (control) && phase == InputPhase.BEGIN ? new ControllerActionIntent (ControllerActionId.SELECT_PARAMETER_PAGE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)) : null;
        inputs.route (control, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127, stableAction, stableCommand);
    }


    private static Path createCoreJar (final Path destination) throws IOException
    {
        final Path classes = Path.of ("..", "pull-core", "target", "classes").toAbsolutePath ().normalize ();
        if (!Files.isDirectory (classes))
            throw new IOException ("Compiled pull-core classes are unavailable: " + classes);

        try (JarOutputStream jar = new JarOutputStream (Files.newOutputStream (destination)))
        {
            final List<Path> files;
            try (var walk = Files.walk (classes))
            {
                files = walk.filter (Files::isRegularFile).sorted ().toList ();
            }
            for (final Path file: files)
            {
                jar.putNextEntry (new JarEntry (classes.relativize (file).toString ().replace ('\\', '/')));
                Files.copy (file, jar);
                jar.closeEntry ();
            }
        }
        return destination;
    }


    private static final class AsyncParameterBridge implements ControllerBridge
    {
        private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "integration-target", 1);

        private final IHwContinuousControl control = proxyControl ();
        private ControllerBridgeSnapshot snapshot = ControllerBridgeSnapshot.empty ();
        private Map<ParameterTargetRef, ParameterLease> retained = Map.of ();
        private DesiredParameterBanks lastAppliedBanks = DesiredParameterBanks.empty ();
        private double authoritativeValue = 100;
        private Double submittedValue;
        private boolean resolveMutations = true;
        private boolean requireResolvedMutation;


        @Override
        public boolean refresh (final long monotonicTimeNanos, final DesiredBridgeSubscriptions subscriptions, final DesiredParameterBanks parameterBanks)
        {
            final boolean requested = subscriptions.includes (BridgeSubscription.PARAMETERS) || !this.retained.isEmpty ();
            final ParameterBridgeSnapshot parameters;
            if (requested)
            {
                final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
                this.retained.forEach ( (target, lease) -> baselines.put (target, ((Lease) lease).baseline));
                parameters = new ParameterBridgeSnapshot (
                    Map.of (ParameterSlot.active (0), this.targetSnapshot ()),
                    baselines);
            }
            else
                parameters = ParameterBridgeSnapshot.empty ();

            final ControllerBridgeSnapshot refreshed = new ControllerBridgeSnapshot (
                this.snapshot.transport (),
                this.snapshot.selectedTrack (),
                this.snapshot.layout (),
                this.snapshot.drum (),
                parameters);
            if (refreshed.equals (this.snapshot))
                return false;
            this.snapshot = refreshed;
            return true;
        }


        @Override
        public void activateCoreGeneration (final long generation)
        {
            // The fake has no generation-specific mutable MIDI state.
        }


        @Override
        public void invalidate ()
        {
            this.retained = Map.of ();
        }


        @Override
        public ControllerBridge.TargetedParameter resolveParameterMutation (final IHwContinuousControl requestedControl)
        {
            return this.resolveMutations && requestedControl == this.control ? new ControllerBridge.TargetedParameter (this.targetSnapshot ()) : null;
        }


        @Override
        public boolean requiresResolvedParameterMutation (final IHwContinuousControl requestedControl)
        {
            return this.requireResolvedMutation && requestedControl == this.control;
        }


        @Override
        public Map<ParameterTargetRef, ParameterLease> prepareParameterLeases (final DesiredParameterInteraction desired, final DesiredParameterBanks parameterBanks)
        {
            final Map<ParameterTargetRef, ParameterLease> prepared = new LinkedHashMap<> ();
            desired.baselines ().forEach ( (target, baseline) -> {
                if (!TARGET.equals (target))
                    throw new IllegalArgumentException ("Unknown fake parameter target");
                prepared.put (target, new Lease (baseline.doubleValue ()));
            });
            return Map.copyOf (prepared);
        }


        @Override
        public boolean applyParameterLeases (final Map<ParameterTargetRef, ParameterLease> prepared, final DesiredParameterBanks parameterBanks)
        {
            this.retained = Map.copyOf (prepared);
            this.lastAppliedBanks = parameterBanks;
            final DesiredBridgeSubscriptions subscriptions = parameterBanks.banks ().isEmpty () && prepared.isEmpty () ? DesiredBridgeSubscriptions.empty () : new DesiredBridgeSubscriptions (java.util.Set.of (BridgeSubscription.PARAMETERS));
            return this.refresh (0, subscriptions, parameterBanks);
        }


        @Override
        public boolean retainsParameterTarget (final ParameterTargetRef target)
        {
            return this.retained.containsKey (target);
        }


        @Override
        public de.mossgrabers.pull.core.api.DesiredControllerState prepareControllerState (final de.mossgrabers.pull.core.api.DesiredControllerState state)
        {
            return state;
        }


        @Override
        public void applyControllerState (final de.mossgrabers.pull.core.api.DesiredControllerState state)
        {
            // The integration scenario remains in the default workspace.
        }


        @Override
        public ControllerBridgeSnapshot snapshot ()
        {
            return this.snapshot;
        }


        @Override
        public PreparedAction prepare (final CoreEffect effect, final Map<ParameterTargetRef, ParameterLease> parameterLeases)
        {
            if (!(effect instanceof final SetParameterValueEffect set) || !parameterLeases.containsKey (set.target ()))
                return null;
            return new SetValue (set.value ());
        }


        @Override
        public void apply (final PreparedAction action)
        {
            this.submittedValue = Double.valueOf (((SetValue) action).value);
        }


        private ParameterTargetSnapshot targetSnapshot ()
        {
            return new ParameterTargetSnapshot (TARGET, this.authoritativeValue, 0.001);
        }


        private void submit (final double value)
        {
            this.submittedValue = Double.valueOf (value);
        }


        private void advanceHost ()
        {
            if (this.submittedValue == null)
                throw new IllegalStateException ("No submitted parameter value to acknowledge");
            this.authoritativeValue = this.submittedValue.doubleValue ();
            this.submittedValue = null;
        }


        private static IHwContinuousControl proxyControl ()
        {
            return (IHwContinuousControl) Proxy.newProxyInstance (
                IHwContinuousControl.class.getClassLoader (),
                new Class<?> []
                {
                    IHwContinuousControl.class
                },
                (proxy, method, arguments) -> switch (method.getName ())
                {
                    case "equals" -> Boolean.valueOf (proxy == arguments[0]);
                    case "hashCode" -> Integer.valueOf (System.identityHashCode (proxy));
                    case "toString" -> "integration-parameter-control";
                    default -> throw new AssertionError ("Unexpected fake hardware call: " + method.getName ());
                });
        }


        private record Lease (double baseline) implements ParameterLease
        {
        }


        private record SetValue (double value) implements PreparedAction
        {
        }
    }


    private static final class EmptyClipHost implements DrumFillClipHost
    {
        @Override
        public boolean refresh ()
        {
            return false;
        }


        @Override
        public ClipCatalogSnapshot clipCatalog ()
        {
            return ClipCatalogSnapshot.empty ();
        }


        @Override
        public void setDesiredBindings (final long catalogGeneration, final Map<ControlId, ClipTargetId> bindings)
        {
            // No clips are present.
        }


        @Override
        public Map<ControlId, ClipTargetId> armedClipTargets ()
        {
            return Map.of ();
        }


        @Override
        public LaunchTarget prepare (final ControlId owner, final long catalogGeneration, final ClipTargetId targetId)
        {
            throw new UnsupportedOperationException ("No clips are present");
        }
    }


    private static final class IncrementingClock implements java.util.function.LongSupplier
    {
        private long value;


        @Override
        public long getAsLong ()
        {
            return this.value++;
        }
    }


    private enum NoOpLog implements RuntimeLog
    {
        INSTANCE;


        @Override
        public void info (final String message)
        {
            // Intentionally empty.
        }


        @Override
        public void warn (final String message)
        {
            // Intentionally empty.
        }
    }
}
