// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSettingsSnapshot;
import de.mossgrabers.pull.core.api.CursorSendBankSnapshot;
import de.mossgrabers.pull.core.api.SessionSettingsSnapshot;
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.junit.jupiter.api.Assertions.assertNull;


/** End-to-end asynchronous snapback lifecycle across the child-core and stable shell boundary. */
class ParameterSnapbackIntegrationTest
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId PAGE_RIGHT = PushControlIds.button ("PAGE_RIGHT");


    @org.junit.jupiter.api.io.TempDir
    Path configurationDirectory;
    private String previousConfigurationPath;

    @org.junit.jupiter.api.BeforeEach
    void isolateConfiguration ()
    {
        this.previousConfigurationPath = System.getProperty ("pull.core.config.file");
        System.setProperty ("pull.core.config.file", this.configurationDirectory.resolve ("config.yaml").toString ());
    }

    @org.junit.jupiter.api.AfterEach
    void restoreConfigurationPath ()
    {
        if (this.previousConfigurationPath == null) System.clearProperty ("pull.core.config.file");
        else System.setProperty ("pull.core.config.file", this.previousConfigurationPath);
    }

    @ParameterizedTest
    @CsvSource({"0, Linear", "1000, Linear", "0, Ease-out", "1000, Ease-out"})
    void waitsForAuthoritativeRestoreBeforeStableNavigationOrCoreReplacement (final int duration, final String curve, @TempDir final Path temporaryDirectory) throws Exception
    {
        final AsyncParameterBridge bridge = new AsyncParameterBridge ();
        bridge.returnMillis = duration;
        bridge.returnCurve = curve;
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (new EmptyClipHost (), bridge, NoOpLog.INSTANCE, new IncrementingClock ());
        environment.setInputRouteValidator (ignored -> true);
        environment.setControllerActionValidator (ignored -> true);
        environment.setPhysicalLightOwnerValidator (ignored -> true);

        final RuntimeManager manager = new RuntimeManager (environment, NoOpLog.INSTANCE);
        final PhysicalInputRouter<ControlId> inputs = inputRouter (environment, manager);
        environment.setDeferredInputRelease (inputs::releaseDeferredStableDispatches);
        environment.setInputLifecycleIdle (inputs::isIdle);

        manager.start ();
        final Path coreJar = createCoreJar (temporaryDirectory.resolve ("pull-core.jar"));
        final var activated = manager.activate ("unpublished", new CoreJarLoader ().load (coreJar), () -> true);
        assertEquals (ActivationResult.State.ACTIVE, activated.state (), activated.message ());
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


    @ParameterizedTest
    @CsvSource({"Linear, 55, 70", "Ease-out, 74.6875, 92.5", "Custom, 115, 94"})
    void returnUsesElapsedTimeAndLaterReadbackBeforeReplacement (final String curve, final double quarterValue, final double halfValue, @TempDir final Path temporaryDirectory) throws Exception
    {
        final AsyncParameterBridge bridge = new AsyncParameterBridge ();
        bridge.returnMillis = 1000;
        bridge.returnCurve = curve;
        final IncrementingClock clock = new IncrementingClock ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (new EmptyClipHost (), bridge, NoOpLog.INSTANCE, clock);
        environment.setInputRouteValidator (ignored -> true);
        environment.setControllerActionValidator (ignored -> true);
        environment.setPhysicalLightOwnerValidator (ignored -> true);
        final RuntimeManager manager = new RuntimeManager (environment, NoOpLog.INSTANCE);
        final PhysicalInputRouter<ControlId> inputs = inputRouter (environment, manager);
        environment.setInputLifecycleIdle (inputs::isIdle);
        manager.start ();
        final Path coreJar = createCoreJar (temporaryDirectory.resolve ("pull-core.jar"));
        final Path configuration = this.configurationDirectory.resolve ("config.yaml");
        Files.writeString (configuration, "control_return:\n  curve:\n    interpolation: smooth\n    keyframes: [[0, 1], [0.25, -0.25], [0.5, 0.1], [0.8, -0.03], [1, 0]]\n");
        final CoreJarLoader configuredLoader = new CoreJarLoader ();
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("unpublished", configuredLoader.load (coreJar), () -> true).state ());
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (environment, NoOpLog.INSTANCE, event -> manager.handle (manager.activeGeneration (), event));
        runtime.start ();
        route (inputs, SHIFT, InputPhase.BEGIN, () -> {});
        runtime.tick ();
        runtime.handleParameterMutation (ContinuousID.KNOB1, bridge.control, () -> bridge.submit (40));
        bridge.advanceHost ();
        runtime.tick ();
        route (inputs, SHIFT, InputPhase.END, () -> {});
        runtime.tick ();
        runtime.tick ();
        assertNull (bridge.submittedValue, "settlement must precede the ramp");
        assertTrue (environment.ticksRequested ());
        assertFalse (manager.canReplaceActiveCore (), "released Shift does not end the return");
        assertEquals (ActivationResult.State.BLOCKED, manager.activate ("unpublished", new CoreJarLoader ().load (coreJar), () -> true).state ());

        clock.value += 250_000_000;
        runtime.tick ();
        assertEquals (quarterValue, bridge.submittedValue, 0.001);
        assertEquals (40, bridge.authoritativeValue, "submitted interpolation is not observed host state");
        bridge.advanceHost ();
        bridge.returnMillis = 0; // Setting changes affect the next release, not this trajectory.
        bridge.returnCurve = "Linear".equals (curve) ? "Ease-out" : "Linear";
        clock.value += 250_000_000;
        runtime.tick ();
        assertEquals (halfValue, bridge.submittedValue, 0.001);
        route (inputs, SHIFT, InputPhase.BEGIN, () -> {});
        runtime.handleParameterMutation (ContinuousID.KNOB1, bridge.control, () -> bridge.submit (5));
        assertEquals (halfValue, bridge.submittedValue, 0.001, "repress cannot mutate a restoring target");
        route (inputs, SHIFT, InputPhase.END, () -> {});
        bridge.advanceHost ();
        clock.value += 900_000_000; // A delayed tick goes straight to the endpoint.
        runtime.tick ();
        assertEquals (100, bridge.submittedValue);
        assertEquals (halfValue, bridge.authoritativeValue, 0.001);
        assertFalse (manager.canReplaceActiveCore ());
        bridge.advanceHost ();
        runtime.tick ();
        assertFalse (manager.canReplaceActiveCore (), "one baseline observation is not confirmation");
        runtime.tick ();
        assertTrue (manager.canReplaceActiveCore ());
        assertEquals (ActivationResult.State.ACTIVE, manager.activate ("unpublished", new CoreJarLoader ().load (coreJar), () -> true).state ());
        final long activeGeneration = manager.activeGeneration ();
        Files.writeString (configuration, "control_return: {curve: broken}");
        final ActivationResult rejected = manager.activate ("unpublished", configuredLoader.load (coreJar), () -> true);
        assertEquals (ActivationResult.State.ACTIVE, rejected.state (), "invalid YAML activates the Linear fallback");
        assertTrue (manager.activeGeneration () > activeGeneration);
        if ("Custom".equals (curve))
        {
            // Keep malformed YAML: this full routed return must use the Linear fallback.
            bridge.returnCurve = "Custom";
            bridge.returnMillis = 1000;
            route (inputs, SHIFT, InputPhase.BEGIN, () -> {});
            runtime.tick ();
            runtime.handleParameterMutation (ContinuousID.KNOB1, bridge.control, () -> bridge.submit (40));
            bridge.advanceHost ();
            runtime.tick ();
            route (inputs, SHIFT, InputPhase.END, () -> {});
            runtime.tick ();
            runtime.tick ();
            clock.value += 500_000_000;
            runtime.tick ();
            assertEquals (70, bridge.submittedValue, 0.001, "same JAR reload falls back to Linear for invalid YAML without restarting the host");
            bridge.advanceHost ();
            clock.value += 500_000_000;
            runtime.tick ();
            bridge.advanceHost ();
            runtime.tick ();
            runtime.tick ();
            assertEquals (100, bridge.authoritativeValue);
            assertTrue (manager.canReplaceActiveCore ());
        }
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
            try (var dependencies = new java.util.jar.JarFile (Path.of ("..", "pull-core", "target", "pull-core.jar").toFile ()))
            {
                for (final var entry: dependencies.stream ().filter (entry -> entry.getName ().startsWith ("org/snakeyaml/") && !entry.isDirectory ()).toList ())
                {
                    jar.putNextEntry (new JarEntry (entry.getName ()));
                    try (var input = dependencies.getInputStream (entry)) { input.transferTo (jar); }
                    jar.closeEntry ();
                }
            }
        }
        return destination;
    }


    private static final class AsyncParameterBridge implements ControllerBridge
    {
        private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "integration-target", 1);

        private final IHwContinuousControl control = proxyControl ();
        private ControllerBridgeSnapshot snapshot = new ControllerBridgeSnapshot (
            de.mossgrabers.pull.core.api.TransportSnapshot.empty (), de.mossgrabers.pull.core.api.SelectedTrackSnapshot.empty (),
            new de.mossgrabers.pull.core.api.ControllerLayoutSnapshot (1, "PLAY", "DEVICE_PARAMS", false, false, 0, de.mossgrabers.pull.core.api.GridPressureConfiguration.OFF),
            de.mossgrabers.pull.core.api.DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty ());
        private Map<ParameterTargetRef, ParameterLease> retained = Map.of ();
        private DesiredParameterBanks lastAppliedBanks = DesiredParameterBanks.empty ();
        private double authoritativeValue = 100;
        private Double submittedValue;
        private boolean resolveMutations = true;
        private boolean requireResolvedMutation;
        private int returnMillis;
        private String returnCurve = "Linear";
        private DesiredBridgeSubscriptions lastSubscriptions = DesiredBridgeSubscriptions.empty ();


        @Override
        public boolean refresh (final long monotonicTimeNanos, final DesiredBridgeSubscriptions subscriptions, final DesiredParameterBanks parameterBanks)
        {
            this.lastSubscriptions = subscriptions;
            final boolean requested = subscriptions.includes (BridgeSubscription.PARAMETERS) || !this.retained.isEmpty ();
            final ParameterBridgeSnapshot parameters;
            if (requested)
            {
                final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
                this.retained.forEach ( (target, lease) -> baselines.put (target, ((Lease) lease).baseline));
                parameters = new ParameterBridgeSnapshot (
                    Map.of (ParameterSlot.active (0), this.targetSnapshot ()),
                    baselines, java.util.Set.of ());
            }
            else
                parameters = ParameterBridgeSnapshot.empty ();

            final ControllerBridgeSnapshot refreshed = new ControllerBridgeSnapshot (
                this.snapshot.transport (),
                this.snapshot.selectedTrack (), this.snapshot.sessionBank (),
                this.snapshot.layout (), this.snapshot.noteView (), this.snapshot.noteRepeat (),
                this.snapshot.drum (), parameters, this.snapshot.controllerMappingFeedback (),
                this.snapshot.master (), this.snapshot.project (), this.snapshot.automation (),
                this.snapshot.encoderConfiguration (), this.snapshot.currentTrackBank (), this.snapshot.transportSettings (),
                subscriptions.includes (BridgeSubscription.CONTROLLER_SETTINGS) ? new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), false, 127, SessionSettingsSnapshot.empty (), this.returnMillis, this.returnCurve) : ControllerSettingsSnapshot.empty ());
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
            return this.refresh (0, this.lastSubscriptions, parameterBanks);
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
            if (effect instanceof final de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect notification)
                return new Notification (notification.text ());
            if (!(effect instanceof final SetParameterValueEffect set) || !parameterLeases.containsKey (set.target ()))
                return null;
            return new SetValue (set.value ());
        }


        @Override
        public void apply (final PreparedAction action)
        {
            if (action instanceof Notification) return;
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


        private record Notification (String text) implements PreparedAction {}

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
