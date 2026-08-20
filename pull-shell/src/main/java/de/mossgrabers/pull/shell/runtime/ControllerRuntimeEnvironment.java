// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerActions;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredControllerState;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportValueEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerActionEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.ParameterMutationEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;


/**
 * Stable-shell transaction, state, and effect boundary for reloadable controller behavior.
 * Feature-specific actuator owners remain delegated to the bounded controller and clip hosts.
 */
final class ControllerRuntimeEnvironment implements CoreRuntimeEnvironment
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final Set<ControlId> MASTER_ROW_LIGHTS = masterRowLights ();
    private static final Set<ControlId> CORE_BUTTON_LIGHTS = Set.of (
        PushControlIds.button ("PLAY"),
        PushControlIds.button ("RECORD"),
        PushControlIds.button ("STOP_CLIP"),
        PushControlIds.button ("MUTE"),
        PushControlIds.button ("SOLO"));
    private static final ShellCapabilities CAPABILITIES = new ShellCapabilities (Map.ofEntries (
        Map.entry (CoreCapabilities.INPUT_DRUM_FILL, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.BINDING_CLIP_TARGET, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (4)),
        Map.entry (CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (6)),
        Map.entry (CoreCapabilities.OUTPUT_CONTROLLER_MAPPING, Integer.valueOf (2)),
        Map.entry (CoreCapabilities.OUTPUT_CONTROLLER_STATE, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_NOTE_VIEW_PREFERENCE, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.OUTPUT_NOTE_REPEAT, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.INPUT_CONTROLLER, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.ROUTING_CONTROLLER_INPUT, Integer.valueOf (4)),
        Map.entry (CoreCapabilities.SNAPSHOT_CONTROLLER_BRIDGE, Integer.valueOf (10)),
        Map.entry (CoreCapabilities.SUBSCRIPTION_CONTROLLER_BRIDGE, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_TRANSPORT, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_SELECTED_TRACK, Integer.valueOf (3)),
        Map.entry (CoreCapabilities.EFFECT_SESSION_BANK, Integer.valueOf (3)),
        Map.entry (CoreCapabilities.EFFECT_CONTROLLER_BUTTON_CONSUMPTION, Integer.valueOf (2)),
        Map.entry (CoreCapabilities.EFFECT_DRUM_PAD, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_NOTE_INPUT_MIDI, Integer.valueOf (2)),
        Map.entry (CoreCapabilities.SNAPSHOT_PARAMETER_TARGETS, Integer.valueOf (2)),
        Map.entry (CoreCapabilities.EFFECT_PARAMETER_TARGET, Integer.valueOf (2)),
        Map.entry (CoreCapabilities.SNAPSHOT_CONTROLLER_MAPPING_FEEDBACK, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SNAPSHOT_MASTER, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_MASTER, Integer.valueOf (2)),
        Map.entry (CoreCapabilities.OUTPUT_CONTROLLER_DISPLAY, Integer.valueOf (4)),
        Map.entry (CoreCapabilities.OUTPUT_PAD_GRID_OVERLAY, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.OUTPUT_DISPLAY_OVERLAY, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.RENDER_MIXER_CONTROLS, Integer.valueOf (1))));

    private final DrumFillClipHost clipHost;
    private final ControllerBridge controllerBridge;
    private final RuntimeLog log;
    private final LongSupplier clock;
    private final long timeOrigin;
    private final Set<ControlId> pressedControls = new LinkedHashSet<> ();
    private final Set<ControlId> touchedControls = new LinkedHashSet<> ();
    private final FillLaunchSession fillSession = new FillLaunchSession ();

    private ClipCatalogSnapshot clipCatalog;
    private Map<ControlId, ClipTargetId> armedClipTargets;
    private FillSessionView lastObservedSession;
    private CommittedState committedState = CommittedState.initial ();
    private Predicate<de.mossgrabers.pull.core.api.InputRoute> inputRouteValidator = route -> false;
    private Predicate<ControllerActionBinding> controllerActionValidator = action -> false;
    private Predicate<ControlId> physicalLightOwnerValidator = ControllerRuntimeEnvironment::isPreviouslyInstalledLightOwner;
    private Runnable deferredInputRelease = () -> {
        // No controller bridge is installed in isolated environment tests.
    };
    private BooleanSupplier inputLifecycleIdle = () -> true;
    private long pendingSnapshotRevision = -1;
    private long hostSampleRevision;
    private long lastTime;
    private long revision;
    private long eventSequence;
    private long appliedResultRevision;


    /** Get the complete replayable pad-grid overlay. */
    ControllerPadGridOverlay padGridOverlay ()
    {
        return this.committedState.output ().padGridOverlay ();
    }


    /** Get the complete replayable temporary display overlay. */
    ControllerDisplayOverlay displayOverlay ()
    {
        return this.committedState.output ().displayOverlay ();
    }


    /**
     * Constructor using the system monotonic clock.
     *
     * @param clipHost Stable selected-track clip host
     * @param log Stable runtime log
     */
    ControllerRuntimeEnvironment (final DrumFillClipHost clipHost, final RuntimeLog log)
    {
        this (clipHost, log, System::nanoTime);
    }


    /**
     * Constructor with a deterministic clock seam.
     *
     * @param clipHost Stable selected-track clip host
     * @param log Stable runtime log
     * @param clock Monotonic clock
     */
    ControllerRuntimeEnvironment (final DrumFillClipHost clipHost, final RuntimeLog log, final LongSupplier clock)
    {
        this (clipHost, null, log, clock);
    }


    /**
     * Constructor with the permanent common-controller bridge.
     *
     * @param clipHost Stable selected-track clip host
     * @param controllerBridge Bounded common controller bridge
     * @param log Stable runtime log
     * @param clock Monotonic clock
     */
    ControllerRuntimeEnvironment (final DrumFillClipHost clipHost, final ControllerBridge controllerBridge, final RuntimeLog log, final LongSupplier clock)
    {
        this.clipHost = Objects.requireNonNull (clipHost, "clipHost");
        this.controllerBridge = controllerBridge;
        this.log = Objects.requireNonNull (log, "log");
        this.clock = Objects.requireNonNull (clock, "clock");
        this.timeOrigin = clock.getAsLong ();
        this.clipCatalog = Objects.requireNonNull (clipHost.clipCatalog (), "initial clip catalog");
        this.armedClipTargets = copyHostBindings (clipHost.armedClipTargets ());
        this.lastObservedSession = this.fillSession.view ();
    }


    /** {@inheritDoc} */
    @Override
    public ControllerSnapshot snapshot ()
    {
        return this.createSnapshot (this.now ());
    }


    /**
     * Advance the stable scanner, actuator pool, and retained cleanup work.
     *
     * @return True when the immutable public snapshot changed
     */
    boolean refresh ()
    {
        this.clipHost.refresh ();
        this.hostSampleRevision = Math.incrementExact (this.hostSampleRevision);
        this.fillSession.advance (this.hostSampleRevision);
        final ClipCatalogSnapshot refreshedCatalog = Objects.requireNonNull (this.clipHost.clipCatalog (), "refreshed clip catalog");
        final Map<ControlId, ClipTargetId> refreshedArmedTargets = copyHostBindings (this.clipHost.armedClipTargets ());
        if (!refreshedCatalog.equals (this.clipCatalog) || !refreshedArmedTargets.equals (this.armedClipTargets))
        {
            this.clipCatalog = refreshedCatalog;
            this.armedClipTargets = refreshedArmedTargets;
            this.recordSnapshotChange ();
        }
        this.recordSessionChange ();
        if (this.controllerBridge != null && this.controllerBridge.refresh (this.now (), this.committedState.desiredBridgeSubscriptions (), this.committedState.desiredParameterBanks ()))
            this.recordSnapshotChange ();

        return this.hasPendingSnapshotChange ();
    }


    /**
     * Create an event for a previously observed snapshot change.
     *
     * @return The event
     */
    SnapshotChangedEvent snapshotChangedEvent ()
    {
        return new SnapshotChangedEvent (this.nextEventSequence (), this.now ());
    }


    /**
     * Get the latest immutable snapshot revision available for delivery.
     *
     * @return Current snapshot revision
     */
    long snapshotRevision ()
    {
        return this.revision;
    }


    /**
     * Acknowledge that a core accepted a snapshot containing the supplied revision. A newer
     * change raised while that core result was applied remains pending for a later delivery.
     *
     * @param deliveredRevision Latest revision contained in the accepted snapshot
     */
    void acknowledgeSnapshotChange (final long deliveredRevision)
    {
        if (deliveredRevision < 0)
            throw new IllegalArgumentException ("deliveredRevision must not be negative");
        if (this.hasPendingSnapshotChange () && deliveredRevision >= this.pendingSnapshotRevision)
            this.pendingSnapshotRevision = -1;
    }


    /**
     * Update one authoritative drum-fill input and create its normalized event.
     *
     * @param owner Logical fill-pad owner
     * @param pressed True while that physical pad is held
     * @return The event
     */
    ButtonInputEvent setFillPressed (final ControlId owner, final boolean pressed)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        final boolean changed = pressed ? this.pressedControls.add (fillOwner) : this.pressedControls.remove (fillOwner);
        if (changed)
            this.advanceSnapshotRevision ();
        return new ButtonInputEvent (this.nextEventSequence (), this.now (), fillOwner, pressed);
    }


    /**
     * Apply one normalized generic physical-input sample to parent-owned gesture state.
     *
     * @param control Physical control
     * @param kind Input kind
     * @param phase Input phase
     * @param value Normalized kind-specific value
     * @return Core event with the environment's globally monotonic sequence and clock
     */
    ControllerInputEvent controllerInput (final ControlId control, final InputKind kind, final InputPhase phase, final long value)
    {
        final ControlId checkedControl = Objects.requireNonNull (control, "control");
        final InputKind checkedKind = Objects.requireNonNull (kind, "kind");
        final InputPhase checkedPhase = Objects.requireNonNull (phase, "phase");
        final Set<ControlId> state = checkedKind == InputKind.TOUCH ? this.touchedControls : checkedKind == InputKind.BUTTON || checkedKind == InputKind.PAD || checkedKind == InputKind.PEDAL ? this.pressedControls : null;
        if (state != null)
        {
            final boolean changed;
            if (checkedPhase == InputPhase.BEGIN)
                changed = state.add (checkedControl);
            else if (checkedPhase == InputPhase.END)
                changed = state.remove (checkedControl);
            else
                changed = false;
            if (changed)
                this.advanceSnapshotRevision ();
        }
        return new ControllerInputEvent (this.nextEventSequence (), this.now (), checkedControl, checkedKind, checkedPhase, value);
    }


    /** Create a semantic event for one stable-owned command before it executes. */
    ControllerActionEvent controllerAction (final ControllerActionIntent intent)
    {
        return new ControllerActionEvent (this.nextEventSequence (), this.now (), Objects.requireNonNull (intent, "intent"));
    }


    /** Create a normalized pre-mutation event for one current bounded parameter target. */
    ParameterMutationEvent parameterMutation (final ControlId control, final ControllerBridge.TargetedParameter parameter)
    {
        final ControllerBridge.TargetedParameter checkedParameter = Objects.requireNonNull (parameter, "parameter");
        return new ParameterMutationEvent (this.nextEventSequence (), this.now (), Objects.requireNonNull (control, "control"), checkedParameter.target ());
    }


    /** Create one requested controller-cycle event for authoritative reconciliation. */
    ControllerTickEvent controllerTickEvent ()
    {
        return new ControllerTickEvent (this.nextEventSequence (), this.now ());
    }


    /** Test whether the active core currently requests parameter observations. */
    boolean observesParameters ()
    {
        return this.committedState.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS);
    }


    /** Test whether unchanged host state must still advance core time. */
    boolean ticksRequested ()
    {
        return this.committedState.executionRequirements ().ticksRequested ();
    }


    /** Resolve one physical control through the installed bounded parameter window. */
    ControllerBridge.TargetedParameter resolveParameterMutation (final de.mossgrabers.framework.controller.hardware.IHwContinuousControl control)
    {
        return this.controllerBridge == null ? null : this.controllerBridge.resolveParameterMutation (control);
    }


    /** Test whether an unresolved established mutation must fail closed. */
    boolean requiresResolvedParameterMutation (final de.mossgrabers.framework.controller.hardware.IHwContinuousControl control)
    {
        return this.controllerBridge != null && this.controllerBridge.requiresResolvedParameterMutation (control);
    }


    /** Test whether the stable shell currently retains one exact target for this core. */
    boolean retainsParameterTarget (final ParameterTargetRef target)
    {
        return this.controllerBridge != null && this.controllerBridge.retainsParameterTarget (target);
    }


    /** Test whether core is currently accepting a new exact parameter lease. */
    boolean acceptsParameterMutations ()
    {
        return this.committedState.desiredParameterInteraction ().acceptsMutations ();
    }


    /** Test whether one exact retained target's established mutation is currently blocked. */
    boolean blocksParameterMutation (final ParameterTargetRef target)
    {
        return this.committedState.desiredParameterInteraction ().blocksMutation (target);
    }


    /** Test whether a view-declared semantic action is behind the active parameter barrier. */
    boolean blocksStableAction (final ControlId control, final InputKind inputKind, final ControllerActionIntent stableAction)
    {
        Objects.requireNonNull (control, "control");
        Objects.requireNonNull (inputKind, "inputKind");
        final ControllerActionIntent intent;
        if (stableAction != null)
            intent = stableAction;
        else
        {
            final ControllerActionBinding binding = this.committedState.desiredControllerActions ().bindingOrNull (control, inputKind);
            intent = binding == null ? null : binding.intent ();
        }
        return intent != null && this.committedState.desiredParameterInteraction ().blocksAction (intent);
    }


    /** A core replacement may not split an admitted semantic action from its stable dispatch. */
    @Override
    public boolean canReplaceActiveCore ()
    {
        return this.committedState.desiredParameterInteraction ().pendingActionCount () == 0 &&
            this.inputLifecycleIdle.getAsBoolean () &&
            (this.controllerBridge == null || this.controllerBridge.canReplaceActiveCore ());
    }


    /**
     * Test whether one physical fill pad is held.
     *
     * @param owner Logical fill-pad owner
     * @return True when held
     */
    boolean isFillPressed (final ControlId owner)
    {
        return this.pressedControls.contains (requireFillOwner (owner));
    }


    /**
     * Safely release an active or pending fill even when the reloadable core did not handle the
     * physical UP event.
     *
     * @param owner Logical owner to release
     */
    void safetyRelease (final ControlId owner)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        this.fillSession.requestRelease (fillOwner, "Safety release", this.hostSampleRevision);
        this.recordSessionChange ();

        if (this.pressedControls.remove (fillOwner))
            this.advanceSnapshotRevision ();
    }


    /**
     * Get one buffered, hardware-independent fill light color.
     *
     * @param owner Logical fill-pad owner
     * @return Desired color
     */
    RgbColor fillLightColor (final ControlId owner)
    {
        return this.lightColor (requireFillOwner (owner));
    }


    /** Get one replayable controller light color, defaulting to off. */
    RgbColor lightColor (final ControlId owner)
    {
        return this.committedState.output ().lights ().getOrDefault (Objects.requireNonNull (owner, "light owner"), OFF);
    }


    /** Test whether the applied core result explicitly owns one hardware light. */
    boolean ownsLight (final ControlId owner)
    {
        return this.committedState.explicitLightOwners ().contains (Objects.requireNonNull (owner, "light owner"));
    }


    /** Observe whether a successfully applied complete core result explicitly owns one light. */
    DebugLightObservation debugLightObservation (final ControlId owner)
    {
        final ControlId lightOwner = Objects.requireNonNull (owner, "light owner");
        final RgbColor color = this.committedState.explicitLightOwners ().contains (lightOwner) ? this.committedState.output ().lights ().get (lightOwner) : null;
        final var mappingId = this.committedState.output ().controllerMappings ().mappingIdOrNull (lightOwner);
        return new DebugLightObservation (this.committedState.generation (), this.appliedResultRevision, color, mappingId != null, this.debugControllerMappingOn (mappingId));
    }


    private Boolean debugControllerMappingOn (final de.mossgrabers.pull.core.api.ControllerMappingId mappingId)
    {
        if (mappingId == null || this.controllerBridge == null)
            return null;
        final var feedback = this.controllerBridge.snapshot ().controllerMappingFeedback ();
        return feedback.available () && feedback.supports (mappingId) ? Boolean.valueOf (feedback.isOn (mappingId)) : null;
    }


    /** Get the complete replayable controller display. */
    ControllerDisplayScene controllerDisplay ()
    {
        return this.committedState.output ().display ();
    }


    /**
     * Get the core generation that last replaced the complete output buffer.
     *
     * @return The output generation
     */
    long outputGeneration ()
    {
        return this.committedState.generation ();
    }


    /**
     * Get the complete committed generic input routing table.
     *
     * @return Desired input routes
     */
    DesiredInputRoutes desiredInputRoutes ()
    {
        return this.committedState.desiredInputRoutes ();
    }


    /** Get the complete committed projection onto semantic Bitwig mapping actions. */
    DesiredControllerMappings activeControllerMappings ()
    {
        return this.committedState.output ().controllerMappings ();
    }


    /**
     * Install validation against the fixed physical registry before the runtime starts.
     *
     * @param validator True only for registered control-and-kind pairs
     */
    void setInputRouteValidator (final Predicate<de.mossgrabers.pull.core.api.InputRoute> validator)
    {
        if (this.committedState.generation () != 0)
            throw new IllegalStateException ("Input-route validation must be installed before core activation");
        this.inputRouteValidator = Objects.requireNonNull (validator, "validator");
    }


    /** Install validation for view-owned semantic action bindings. */
    void setControllerActionValidator (final Predicate<ControllerActionBinding> validator)
    {
        if (this.committedState.generation () != 0)
            throw new IllegalStateException ("Controller-action validation must be installed before core activation");
        this.controllerActionValidator = Objects.requireNonNull (validator, "validator");
    }


    /** Install validation against the permanent physical Push light registry. */
    void setPhysicalLightOwnerValidator (final Predicate<ControlId> validator)
    {
        this.physicalLightOwnerValidator = Objects.requireNonNull (validator, "validator");
    }


    /** Install the stable-input release barrier before runtime startup. */
    void setDeferredInputRelease (final Runnable release)
    {
        if (this.committedState.generation () != 0)
            throw new IllegalStateException ("Deferred input release must be installed before core activation");
        this.deferredInputRelease = Objects.requireNonNull (release, "release");
    }


    /** Install the complete physical-input lifecycle fence before runtime startup. */
    void setInputLifecycleIdle (final BooleanSupplier idle)
    {
        if (this.committedState.generation () != 0)
            throw new IllegalStateException ("Input lifecycle must be installed before core activation");
        this.inputLifecycleIdle = Objects.requireNonNull (idle, "idle");
    }


    /** Install the narrower musical-input fence used only by Note-route removal. */
    void setNoteInputLifecycleIdle (final BooleanSupplier idle)
    {
        if (this.committedState.generation () != 0)
            throw new IllegalStateException ("Note-input lifecycle must be installed before core activation");
        if (this.controllerBridge != null)
            this.controllerBridge.setNoteInputLifecycleIdle (Objects.requireNonNull (idle, "idle"));
    }


    /** Install cleanup which must run before core-owned routes are invalidated. */
    void setInputLifecycleCleanup (final Runnable cleanup)
    {
        if (this.committedState.generation () != 0)
            throw new IllegalStateException ("Input lifecycle cleanup must be installed before core activation");
        if (this.controllerBridge != null)
            this.controllerBridge.setInputLifecycleCleanup (Objects.requireNonNull (cleanup, "cleanup"));
    }


    /** {@inheritDoc} */
    @Override
    public PreparedCoreResult prepare (final CoreResult result)
    {
        Objects.requireNonNull (result, "result");
        for (final de.mossgrabers.pull.core.api.InputRoute route: result.desiredInputRoutes ().routes ())
        {
            if (!this.inputRouteValidator.test (route))
                throw new IllegalArgumentException ("Core requested an unregistered controller input route");
        }
        for (final ControllerActionBinding action: result.desiredControllerActions ().bindings ())
        {
            if (!this.controllerActionValidator.test (action))
                throw new IllegalArgumentException ("Core requested an unregistered controller action binding");
        }
        final Map<ControlId, ClipTargetId> preparedBindings = prepareBindings (result.desiredClipBindings (), this.clipCatalog);
        final DesiredControllerState preparedControllerState = this.prepareControllerState (result.desiredControllerState ());
        final DesiredNoteRepeat preparedNoteRepeat = this.prepareNoteRepeat (result.desiredNoteRepeat ());
        final DesiredHardwareOutput preparedOutput = prepareOutput (result, preparedControllerState.workspace ());
        final DesiredParameterBanks parameterBanks = result.desiredParameterBanks ();
        final DesiredParameterInteraction parameterInteraction = result.desiredParameterInteraction ();
        final CoreExecutionRequirements executionRequirements = result.executionRequirements ();
        final boolean parametersRequested = result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS);
        final DesiredParameterBanks sampledParameterBanks = parametersRequested ? parameterBanks : DesiredParameterBanks.empty ();
        if (parameterInteraction.interactionId () != 0 && !parametersRequested)
            throw new IllegalArgumentException ("A parameter interaction requires the parameter snapshot subscription");
        if (!parametersRequested && result.effects ().stream ().anyMatch (ControllerRuntimeEnvironment::isParameterEffect))
            throw new IllegalArgumentException ("A parameter effect requires the parameter snapshot subscription");
        if (!result.desiredBridgeSubscriptions ().includes (BridgeSubscription.SESSION_BANK) && result.effects ().stream ().anyMatch (ControllerRuntimeEnvironment::isSessionBankEffect))
            throw new IllegalArgumentException ("A Session-bank effect requires the Session-bank snapshot subscription");
        if (this.controllerBridge == null && (!parameterBanks.banks ().isEmpty () || parameterInteraction.interactionId () != 0))
            throw new IllegalArgumentException ("Core requested parameter state without a controller bridge");
        final Map<ParameterTargetRef, ControllerBridge.ParameterLease> preparedParameterLeases = this.controllerBridge == null ? Map.of () : this.controllerBridge.prepareParameterLeases (parameterInteraction, sampledParameterBanks);
        final List<PreparedAction> preparedActions = this.prepareEffects (result.effects (), preparedBindings, preparedParameterLeases);
        return new PreparedResult (preparedOutput, result.desiredOutput ().lights ().keySet (), result.desiredInputRoutes (), result.desiredBridgeSubscriptions (), preparedControllerState, preparedNoteRepeat, result.desiredControllerActions (), parameterBanks, parameterInteraction, executionRequirements, preparedParameterLeases, this.clipCatalog.generation (), preparedBindings, preparedActions);
    }


    /** {@inheritDoc} */
    @Override
    public void commit (final long generation, final PreparedCoreResult result)
    {
        final PreparedResult prepared = (PreparedResult) result;
        this.committedState = CommittedState.pending (generation, prepared);
    }


    /** {@inheritDoc} */
    @Override
    public void apply (final long generation)
    {
        final CommittedState committed = this.committedState;
        if (generation != committed.generation ())
            return;

        final PreparedResult prepared = committed.pendingResult ();
        if (prepared == null)
            return;
        this.committedState = committed.applied ();

        if (this.controllerBridge != null)
        {
            this.controllerBridge.activateCoreGeneration (generation);
            final DesiredParameterBanks sampledParameterBanks = prepared.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS) ? prepared.desiredParameterBanks () : DesiredParameterBanks.empty ();
            if (this.controllerBridge.applyParameterLeases (prepared.parameterLeases (), sampledParameterBanks))
                this.recordSnapshotChange ();
            this.deferredInputRelease.run ();
            this.controllerBridge.applyControllerState (prepared.desiredControllerState ());
            this.controllerBridge.applyNoteRepeat (prepared.desiredNoteRepeat ());
        }

        this.clipHost.setDesiredBindings (prepared.catalogGeneration (), prepared.desiredClipBindings ());
        boolean acquisitionFailed = false;
        for (final PreparedAction action: prepared.actions ())
        {
            if (action instanceof final PreparedPress press)
            {
                if (!acquisitionFailed)
                    acquisitionFailed = !this.applyPress (press);
            }
            else if (action instanceof final PreparedRelease release)
                this.applyRelease (release);
            else if (action instanceof final PreparedBridgeAction bridgeAction && this.controllerBridge != null)
                this.controllerBridge.apply (bridgeAction.action ());
        }
        this.recordSessionChange ();
        this.appliedResultRevision++;
    }


    /** {@inheritDoc} */
    @Override
    public void invalidate (final long generation)
    {
        this.committedState = CommittedState.invalidated (generation);

        if (this.controllerBridge != null)
        {
            this.controllerBridge.invalidate ();
            this.deferredInputRelease.run ();
        }

        try
        {
            this.clipHost.setDesiredBindings (this.clipCatalog.generation (), Map.of ());
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Clearing fill bindings during invalidation failed: " + sanitize (failure));
        }

        // During a core fault the stable runtime keeps advancing this return. At terminal extension
        // shutdown API 21 offers no post-exit observation window, so the same request is necessarily
        // best effort.
        this.fillSession.invalidate (this.hostSampleRevision);
        this.recordSessionChange ();

        if (!this.pressedControls.isEmpty ())
        {
            this.pressedControls.clear ();
            this.advanceSnapshotRevision ();
        }
        if (!this.touchedControls.isEmpty ())
        {
            this.touchedControls.clear ();
            this.advanceSnapshotRevision ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void quarantine (final long generation)
    {
        if (generation != this.committedState.generation ())
            return;
        this.committedState = this.committedState.quarantined ();
        if (this.controllerBridge != null)
        {
            try
            {
                this.controllerBridge.abandonActiveCore ();
            }
            catch (final RuntimeException failure)
            {
                this.warn ("Controller-bridge quarantine cleanup failed: " + sanitize (failure));
            }
        }
        try
        {
            this.deferredInputRelease.run ();
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Deferred-input quarantine cleanup failed: " + sanitize (failure));
        }

        try
        {
            this.clipHost.setDesiredBindings (this.clipCatalog.generation (), Map.of ());
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Clearing fill bindings during quarantine failed: " + sanitize (failure));
        }
        try
        {
            this.fillSession.invalidate (this.hostSampleRevision);
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Fill-session quarantine cleanup failed: " + sanitize (failure));
        }
        this.recordSessionChange ();
    }


    private DesiredHardwareOutput prepareOutput (final CoreResult result, final DesiredControllerWorkspace workspace)
    {
        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> (offLights ());
        final boolean masterControls = workspace.facets ().contains (ControllerViewFacet.MASTER_CONTROLS);
        for (final Map.Entry<ControlId, RgbColor> light: result.desiredOutput ().lights ().entrySet ())
        {
            final ControlId owner = Objects.requireNonNull (light.getKey (), "light owner");
            if (!CoreControls.DRUM_FILLS.contains (owner) && !this.physicalLightOwnerValidator.test (owner) && !(masterControls && MASTER_ROW_LIGHTS.contains (owner)))
                throw new IllegalArgumentException ("Unsupported controller light owner");
            final RgbColor requested = Objects.requireNonNull (light.getValue (), "light color");
            colors.put (owner, new RgbColor (requested.red (), requested.green (), requested.blue ()));
        }
        final ControllerDisplayScene display = result.desiredOutput ().display ();
        if (display.isPresent () && (display.width () != 960 || display.height () != 160))
            throw new IllegalArgumentException ("Controller display output must use the 960x160 Push viewport");
        final ControllerPadGridOverlay overlay = result.desiredOutput ().padGridOverlay ();
        final ControllerDisplayOverlay displayOverlay = result.desiredOutput ().displayOverlay ();
        final DesiredControllerMappings controllerMappings = result.desiredOutput ().controllerMappings ();
        if (!controllerMappings.bindings ().isEmpty () && !result.desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK))
            throw new IllegalArgumentException ("Active controller mappings require authoritative mapping feedback");
        for (final ControllerMappingBinding binding: controllerMappings.bindings ())
        {
            if (!CoreControls.DRUM_CONTROL_PADS.contains (binding.physicalControl ()))
                throw new IllegalArgumentException ("Unsupported physical controller mapping owner");
            if (!CoreControllerMappings.DRUM_CONTROL_PADS.contains (binding.mappingId ()))
                throw new IllegalArgumentException ("Unsupported semantic controller mapping endpoint");
            if (!result.desiredInputRoutes ().ownsExclusively (binding.physicalControl (), InputKind.PAD))
                throw new IllegalArgumentException ("An active controller mapping requires an exclusive pad route");
            if (!result.desiredOutput ().lights ().containsKey (binding.physicalControl ()))
                throw new IllegalArgumentException ("An active controller mapping requires owned physical light feedback");
        }
        if (displayOverlay.active () && (displayOverlay.scene ().width () != 960 || displayOverlay.scene ().height () != 160))
            throw new IllegalArgumentException ("Controller display overlay must use the 960x160 Push viewport");
        return new DesiredHardwareOutput (colors, display, overlay, displayOverlay, controllerMappings);
    }


    private static Set<ControlId> masterRowLights ()
    {
        final Set<ControlId> controls = new LinkedHashSet<> (16);
        for (int row = 1; row <= 2; row++)
        {
            for (int column = 1; column <= 8; column++)
                controls.add (PushControlIds.button ("ROW" + row + "_" + column));
        }
        return Set.copyOf (controls);
    }


    private DesiredControllerState prepareControllerState (final DesiredControllerState state)
    {
        final DesiredControllerState requested = Objects.requireNonNull (state, "state");
        if (this.controllerBridge != null)
            return this.controllerBridge.prepareControllerState (requested);
        if (!requested.equals (DesiredControllerState.empty ()))
            throw new IllegalArgumentException ("Core requested controller state without a controller bridge");
        return requested;
    }


    ControllerBridge.NotePerformanceState notePerformanceState ()
    {
        return this.controllerBridge == null ? ControllerBridge.NotePerformanceState.unavailable () : this.controllerBridge.notePerformanceState ();
    }


    private DesiredNoteRepeat prepareNoteRepeat (final DesiredNoteRepeat noteRepeat)
    {
        final DesiredNoteRepeat requested = Objects.requireNonNull (noteRepeat, "noteRepeat");
        if (this.controllerBridge != null)
            return this.controllerBridge.prepareNoteRepeat (requested);
        if (requested.owned ())
            throw new IllegalArgumentException ("Core requested note repeat without a controller bridge");
        return requested;
    }


    private Map<ControlId, ClipTargetId> prepareBindings (final Map<ControlId, ClipTargetId> bindings, final ClipCatalogSnapshot clipCatalog)
    {
        final Map<ControlId, ClipTargetId> copy = new LinkedHashMap<> ();
        final Set<ClipTargetId> catalogTargets = new HashSet<> ();
        clipCatalog.clips ().forEach (clip -> catalogTargets.add (clip.targetId ()));
        final Set<ClipTargetId> targets = new HashSet<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: bindings.entrySet ())
        {
            final ControlId owner = requireFillOwner (binding.getKey ());
            final ClipTargetId requested = Objects.requireNonNull (binding.getValue (), "desired clip target");
            final ClipTargetId target = new ClipTargetId (requested.value ());
            if (!catalogTargets.contains (target))
                throw new IllegalArgumentException ("Desired binding targets an unknown catalog clip");
            if (!targets.add (target))
                throw new IllegalArgumentException ("Desired clip targets must be unique");
            copy.put (owner, target);
        }

        for (final Map.Entry<ControlId, ClipTargetId> retained: this.fillSession.targets ().entrySet ())
        {
            for (final Map.Entry<ControlId, ClipTargetId> binding: copy.entrySet ())
            {
                if (!retained.getKey ().equals (binding.getKey ()) && retained.getValue ().equals (binding.getValue ()))
                    throw new IllegalArgumentException ("A retained clip target cannot be rebound to another fill control");
            }
        }
        return Map.copyOf (copy);
    }


    private List<PreparedAction> prepareEffects (final List<CoreEffect> effects, final Map<ControlId, ClipTargetId> desiredBindings, final Map<ParameterTargetRef, ControllerBridge.ParameterLease> parameterLeases)
    {
        final Set<ControlId> owners = new HashSet<> ();
        final Set<BridgeEffectTarget> bridgeTargets = new HashSet<> ();
        final List<PreparedAction> actions = new ArrayList<> (effects.size ());
        for (final CoreEffect effect: effects)
        {
            if (effect instanceof final PressClipTargetEffect press)
            {
                if (!owners.add (requireFillOwner (press.owner ())))
                    throw new IllegalArgumentException ("Core requested multiple clip effects for one owner");
                actions.add (this.preparePress (press, desiredBindings));
            }
            else if (effect instanceof final ReleaseClipTargetsEffect release)
            {
                if (!owners.add (requireFillOwner (release.owner ())))
                    throw new IllegalArgumentException ("Core requested multiple clip effects for one owner");
                actions.add (this.prepareRelease (release));
            }
            else
            {
                final CoreEffect checkedEffect = Objects.requireNonNull (effect, "effect");
                final BridgeEffectTarget target = bridgeEffectTarget (checkedEffect);
                if (target != null && !bridgeTargets.add (target))
                    throw new IllegalArgumentException ("Core requested multiple effects for " + target);
                final ControllerBridge.PreparedAction action = this.controllerBridge == null ? null : this.controllerBridge.prepare (checkedEffect, parameterLeases);
                if (action == null)
                    throw new IllegalArgumentException ("Core requested an unsupported effect " + effect.getClass ().getSimpleName ());
                actions.add (new PreparedBridgeAction (action));
            }
        }
        return List.copyOf (actions);
    }


    private static BridgeEffectTarget bridgeEffectTarget (final CoreEffect effect)
    {
        if (effect instanceof final SetTransportStateEffect state)
            return new BridgeEffectTarget (BridgeEffectDomain.TRANSPORT_STATE, state.state ());
        if (effect instanceof final SetTransportValueEffect value)
            return new BridgeEffectTarget (BridgeEffectDomain.TRANSPORT_VALUE, value.value ());
        if (effect instanceof final SetParameterValueEffect parameter)
            return new BridgeEffectTarget (BridgeEffectDomain.PARAMETER, parameter.target ());
        if (effect instanceof final AdjustParameterValueEffect parameter)
            return new BridgeEffectTarget (BridgeEffectDomain.PARAMETER, parameter.target ());
        if (effect instanceof final ResetParameterEffect parameter)
            return new BridgeEffectTarget (BridgeEffectDomain.PARAMETER, parameter.target ());
        return null;
    }


    private static boolean isParameterEffect (final CoreEffect effect)
    {
        return effect instanceof SetParameterValueEffect || effect instanceof AdjustParameterValueEffect || effect instanceof ResetParameterEffect;
    }


    private static boolean isSessionBankEffect (final CoreEffect effect)
    {
        return effect instanceof StopSessionBankEffect || effect instanceof SelectSessionTrackEffect || effect instanceof StopSessionTrackEffect;
    }


    private PreparedPress preparePress (final PressClipTargetEffect effect, final Map<ControlId, ClipTargetId> desiredBindings)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        if (!this.pressedControls.contains (owner))
            throw new IllegalStateException ("The fill control is not physically held");
        if (effect.catalogGeneration () != this.clipCatalog.generation ())
            throw new IllegalArgumentException ("Clip-catalog generation is stale");

        final ClipTargetId targetId = new ClipTargetId (effect.target ().value ());
        for (final Map.Entry<ControlId, ClipTargetId> retained: this.fillSession.targets ().entrySet ())
        {
            if (!owner.equals (retained.getKey ()) && targetId.equals (retained.getValue ()))
                throw new IllegalStateException ("A retained clip target cannot be acquired by another fill control");
        }

        if (!targetId.equals (desiredBindings.get (owner)))
            throw new IllegalArgumentException ("New clip press must match this result's desired binding");
        if (!targetId.equals (this.armedClipTargets.get (owner)))
            throw new IllegalArgumentException ("Clip target is not armed for this fill control");

        // Preparing a Bitwig proxy here would reserve the replacement while the current fill is
        // still returning. Keep only an opaque intent; resolve the fresh proxy after base playback
        // has been observed again.
        return new PreparedPress (owner, effect.catalogGeneration (), targetId, effect.launchPolicy ());
    }


    private PreparedRelease prepareRelease (final ReleaseClipTargetsEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        return new PreparedRelease (owner);
    }


    private static ControlId requireFillOwner (final ControlId owner)
    {
        Objects.requireNonNull (owner, "fill owner");
        if (!CoreControls.DRUM_FILLS.contains (owner))
            throw new IllegalArgumentException ("Unsupported fill owner");
        return owner;
    }


    private boolean applyPress (final PreparedPress press)
    {
        if (this.clipCatalog.generation () != press.catalogGeneration ())
        {
            this.warn ("Prepared fill press was discarded after the clip catalog changed");
            return false;
        }
        return this.fillSession.requestLaunch (new PendingLaunch (press.owner (), press.catalogGeneration (), press.targetId (), press.launchPolicy ()), this.hostSampleRevision);
    }


    private void applyRelease (final PreparedRelease release)
    {
        this.fillSession.requestRelease (release.owner (), "Fill-session release", this.hostSampleRevision);
    }


    private void advanceSnapshotRevision ()
    {
        this.revision = Math.incrementExact (this.revision);
    }


    private void recordSessionChange ()
    {
        final FillSessionView observed = this.fillSession.view ();
        if (observed.equals (this.lastObservedSession))
            return;

        this.lastObservedSession = observed;
        this.recordSnapshotChange ();
    }


    private void recordSnapshotChange ()
    {
        this.advanceSnapshotRevision ();
        this.pendingSnapshotRevision = this.revision;
    }


    private boolean hasPendingSnapshotChange ()
    {
        return this.pendingSnapshotRevision >= 0;
    }


    private ControllerSnapshot createSnapshot (final long monotonicTimeNanos)
    {
        final FillSessionView session = this.fillSession.view ();
        final ControllerBridgeSnapshot bridge = this.controllerBridge == null ? ControllerBridgeSnapshot.empty () : this.controllerBridge.snapshot ();
        return new ControllerSnapshot (this.revision, monotonicTimeNanos, CAPABILITIES, bridge, this.clipCatalog, this.armedClipTargets, session.targets (), session.activeOwner (), this.pressedControls, this.touchedControls);
    }


    private long nextEventSequence ()
    {
        this.eventSequence = Math.incrementExact (this.eventSequence);
        return this.eventSequence;
    }


    private long now ()
    {
        final long elapsed = Math.max (0, this.clock.getAsLong () - this.timeOrigin);
        this.lastTime = Math.max (this.lastTime, elapsed);
        return this.lastTime;
    }


    private void warn (final String message)
    {
        try
        {
            this.log.warn (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging cannot change stable-shell ownership or cleanup.
        }
    }


    private static Map<ControlId, ClipTargetId> copyHostBindings (final Map<ControlId, ClipTargetId> bindings)
    {
        final Map<ControlId, ClipTargetId> copy = new LinkedHashMap<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: Objects.requireNonNull (bindings, "armed clip targets").entrySet ())
        {
            final ControlId owner = requireFillOwner (binding.getKey ());
            final ClipTargetId target = Objects.requireNonNull (binding.getValue (), "armed clip target");
            copy.put (owner, new ClipTargetId (target.value ()));
        }
        return Map.copyOf (copy);
    }


    private static Map<ControlId, RgbColor> offLights ()
    {
        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> ();
        for (final ControlId owner: CoreControls.DRUM_FILLS)
            colors.put (owner, OFF);
        for (final ControlId owner: CoreControls.DRUM_RATES)
            colors.put (owner, OFF);
        for (final ControlId owner: CoreControls.DRUM_CONTROL_PADS)
            colors.put (owner, OFF);
        for (final ControlId owner: CORE_BUTTON_LIGHTS)
            colors.put (owner, OFF);
        return Map.copyOf (colors);
    }


    private static boolean isPreviouslyInstalledLightOwner (final ControlId owner)
    {
        return CoreControls.DRUM_RATES.contains (owner) || CoreControls.DRUM_CONTROL_PADS.contains (owner) || CORE_BUTTON_LIGHTS.contains (owner);
    }


    private static String sanitize (final Throwable failure)
    {
        final String message = failure.getMessage ();
        return failure.getClass ().getSimpleName () + (message == null || message.isBlank () ? "" : ": " + message);
    }


    private static void rethrowFatal (final Throwable failure)
    {
        if (failure instanceof final VirtualMachineError virtualMachineError)
            throw virtualMachineError;
    }


    /**
     * One active fill above the opaque playback state that Bitwig owned before the gesture. A
     * replacement waits for the active fill to Return completely before it launches, so every
     * fill inherits the same Bitwig-owned base instead of another fill.
     */
    private final class FillLaunchSession
    {
        private LaunchLease active;
        private PendingLaunch pending;
        private boolean returnRequested;
        private boolean activeObservedBusy;
        private long launchAfterSample = -1;
        private DrumFillClipHost.PlaybackState activePlayback = new DrumFillClipHost.PlaybackState (false, false, false);
        private PendingRelease pendingRelease;
        private String pendingOperation = "Fill-session return";


        private Map<ControlId, ClipTargetId> targets ()
        {
            final Map<ControlId, ClipTargetId> targets = new LinkedHashMap<> ();
            if (this.active != null)
                targets.put (this.active.owner (), this.active.targetId ());
            return Map.copyOf (targets);
        }


        private boolean requestLaunch (final PendingLaunch requested, final long sampleRevision)
        {
            this.pending = Objects.requireNonNull (requested, "requested");
            if (this.active == null)
                return sampleRevision <= this.launchAfterSample || this.launchPending (sampleRevision);

            this.requestActiveReturn ("Fill replacement", sampleRevision);
            return true;
        }


        private void requestRelease (final ControlId owner, final String operation, final long sampleRevision)
        {
            if (this.pending != null && owner.equals (this.pending.owner ()))
                this.pending = null;
            if (this.active != null && owner.equals (this.active.owner ()))
                this.requestActiveReturn (operation, sampleRevision);
        }


        private void invalidate (final long sampleRevision)
        {
            this.pending = null;
            if (this.active != null)
            {
                this.returnRequested = true;
                this.pendingOperation = "Runtime invalidation";
                if (this.pendingRelease == null)
                    this.requestActiveRelease (sampleRevision);
            }
        }


        private void requestActiveReturn (final String operation, final long sampleRevision)
        {
            if (this.active == null)
            {
                this.finishReturn ();
                return;
            }

            this.returnRequested = true;
            this.pendingOperation = Objects.requireNonNull (operation, "operation");
            if (this.pendingRelease == null && this.activeObservedBusy)
                this.requestActiveRelease (sampleRevision);
        }


        private void advance (final long sampleRevision)
        {
            if (this.active == null)
            {
                if (this.pending != null && sampleRevision > this.launchAfterSample)
                    this.launchPending (sampleRevision);
                return;
            }

            this.activePlayback = this.active.target ().playbackState ();
            if (isBusy (this.activePlayback))
                this.activeObservedBusy = true;

            if (!this.returnRequested)
                return;

            final PendingRelease release = this.pendingRelease;
            if (release == null)
            {
                // Let Bitwig publish the launch before submitting Return. Sending both commands
                // in one host turn can be coalesced into a permanently idle read-back, which
                // provides no safe acknowledgement for retiring this exact actuator.
                if (!this.activeObservedBusy)
                    return;
                this.requestActiveRelease (sampleRevision);
                return;
            }

            if (release.lease () != this.active)
                throw new IllegalStateException ("The pending fill release no longer owns the active target");
            if (sampleRevision <= release.issuedAfterSample ())
                return;

            // A target starts with a stale false sample until Bitwig publishes the launch. Never
            // mistake that pre-launch value for a completed Return.
            if (!this.activeObservedBusy || isBusy (this.activePlayback))
                return;

            this.retireActive ();
            this.launchAfterSample = sampleRevision;
            this.finishReturn ();
        }


        private void requestActiveRelease (final long sampleRevision)
        {
            final LaunchLease lease = this.active;
            if (lease == null)
            {
                this.finishReturn ();
                return;
            }

            try
            {
                lease.target ().release ();
                this.pendingRelease = new PendingRelease (lease, sampleRevision);
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                ControllerRuntimeEnvironment.this.warn (this.pendingOperation + " failed for " + lease.owner ().value () + ": " + sanitize (failure));
            }
        }


        private boolean launchPending (final long sampleRevision)
        {
            final PendingLaunch requested = this.pending;
            if (requested == null)
                return true;
            if (sampleRevision <= this.launchAfterSample)
                return true;
            if (!ControllerRuntimeEnvironment.this.pressedControls.contains (requested.owner ()))
            {
                this.pending = null;
                return true;
            }
            if (ControllerRuntimeEnvironment.this.clipCatalog.generation () != requested.catalogGeneration () || !requested.targetId ().equals (ControllerRuntimeEnvironment.this.armedClipTargets.get (requested.owner ())))
            {
                this.pending = null;
                ControllerRuntimeEnvironment.this.warn ("Pending fill press was discarded after its catalog binding changed");
                return false;
            }

            final DrumFillClipHost.LaunchTarget target;
            try
            {
                target = Objects.requireNonNull (ControllerRuntimeEnvironment.this.clipHost.prepare (requested.owner (), requested.catalogGeneration (), requested.targetId ()), "prepared launch target");
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                this.pending = null;
                ControllerRuntimeEnvironment.this.warn ("Fill target preparation failed: " + sanitize (failure));
                return false;
            }
            if (!requested.targetId ().equals (target.targetId ()))
            {
                this.pending = null;
                ControllerRuntimeEnvironment.this.warn ("Fill target preparation resolved a different target");
                return false;
            }

            final LaunchLease lease = new LaunchLease (requested.owner (), requested.catalogGeneration (), requested.targetId (), requested.launchPolicy (), target);
            this.pending = null;
            this.active = lease;
            this.returnRequested = false;
            this.activeObservedBusy = false;
            this.activePlayback = new DrumFillClipHost.PlaybackState (false, false, false);
            this.launchAfterSample = -1;
            this.pendingRelease = null;
            try
            {
                target.press (requested.launchPolicy ());
                return true;
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                ControllerRuntimeEnvironment.this.warn ("Fill target press failed: " + sanitize (failure));
                this.requestActiveReturn ("Partial fill acquisition rollback", sampleRevision);
                return false;
            }
        }


        private void retireActive ()
        {
            final LaunchLease lease = this.active;
            if (lease == null)
                return;
            try
            {
                lease.target ().retire ();
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                ControllerRuntimeEnvironment.this.warn ("Fill target retirement failed for " + lease.owner ().value () + ": " + sanitize (failure));
            }
            finally
            {
                this.active = null;
                this.activeObservedBusy = false;
                this.activePlayback = new DrumFillClipHost.PlaybackState (false, false, false);
            }
        }


        private void finishReturn ()
        {
            this.returnRequested = false;
            this.pendingRelease = null;
            this.pendingOperation = "Fill-session return";
        }


        private FillSessionView view ()
        {
            final Optional<ControlId> activeOwner = this.active != null && this.activePlayback.playing () ? Optional.of (this.active.owner ()) : Optional.empty ();
            return new FillSessionView (FillSessionBase.BITWIG_OWNED_CLIP_OR_ARRANGEMENT, this.targets (), activeOwner);
        }


        private static boolean isBusy (final DrumFillClipHost.PlaybackState state)
        {
            return state.playing () || state.playbackQueued () || state.stopQueued ();
        }
    }


    private enum FillSessionBase
    {
        BITWIG_OWNED_CLIP_OR_ARRANGEMENT
    }


    private record FillSessionView (FillSessionBase base, Map<ControlId, ClipTargetId> targets, Optional<ControlId> activeOwner)
    {
        private FillSessionView
        {
            base = Objects.requireNonNull (base, "base");
            targets = Map.copyOf (Objects.requireNonNull (targets, "targets"));
            activeOwner = Objects.requireNonNull (activeOwner, "activeOwner");
        }
    }


    private record PendingRelease (LaunchLease lease, long issuedAfterSample)
    {
        private PendingRelease
        {
            lease = Objects.requireNonNull (lease, "lease");
            if (issuedAfterSample < 0)
                throw new IllegalArgumentException ("issuedAfterSample must not be negative");
        }
    }


    private record PendingLaunch (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy)
    {
        private PendingLaunch
        {
            owner = requireFillOwner (owner);
            if (catalogGeneration < 0)
                throw new IllegalArgumentException ("catalogGeneration must not be negative");
            targetId = Objects.requireNonNull (targetId, "targetId");
            launchPolicy = Objects.requireNonNull (launchPolicy, "launchPolicy");
        }
    }


    private record PreparedResult (DesiredHardwareOutput output, Set<ControlId> explicitLightOwners, DesiredInputRoutes desiredInputRoutes, DesiredBridgeSubscriptions desiredBridgeSubscriptions, DesiredControllerState desiredControllerState, DesiredNoteRepeat desiredNoteRepeat, DesiredControllerActions desiredControllerActions, DesiredParameterBanks desiredParameterBanks, DesiredParameterInteraction desiredParameterInteraction, CoreExecutionRequirements executionRequirements, Map<ParameterTargetRef, ControllerBridge.ParameterLease> parameterLeases, long catalogGeneration, Map<ControlId, ClipTargetId> desiredClipBindings, List<PreparedAction> actions) implements PreparedCoreResult
    {
        private PreparedResult
        {
            output = Objects.requireNonNull (output, "output");
            explicitLightOwners = Set.copyOf (explicitLightOwners);
            desiredInputRoutes = Objects.requireNonNull (desiredInputRoutes, "desiredInputRoutes");
            desiredBridgeSubscriptions = Objects.requireNonNull (desiredBridgeSubscriptions, "desiredBridgeSubscriptions");
            desiredControllerState = Objects.requireNonNull (desiredControllerState, "desiredControllerState");
            desiredNoteRepeat = Objects.requireNonNull (desiredNoteRepeat, "desiredNoteRepeat");
            desiredControllerActions = Objects.requireNonNull (desiredControllerActions, "desiredControllerActions");
            desiredParameterBanks = Objects.requireNonNull (desiredParameterBanks, "desiredParameterBanks");
            desiredParameterInteraction = Objects.requireNonNull (desiredParameterInteraction, "desiredParameterInteraction");
            executionRequirements = Objects.requireNonNull (executionRequirements, "executionRequirements");
            parameterLeases = Map.copyOf (parameterLeases);
            desiredClipBindings = Map.copyOf (desiredClipBindings);
            actions = List.copyOf (actions);
        }
    }


    private record CommittedState (long generation, DesiredHardwareOutput output, Set<ControlId> explicitLightOwners, DesiredInputRoutes desiredInputRoutes, DesiredBridgeSubscriptions desiredBridgeSubscriptions, DesiredControllerState desiredControllerState, DesiredControllerActions desiredControllerActions, DesiredParameterBanks desiredParameterBanks, DesiredParameterInteraction desiredParameterInteraction, CoreExecutionRequirements executionRequirements, PreparedResult pendingResult)
    {
        private CommittedState
        {
            if (generation < 0)
                throw new IllegalArgumentException ("generation must not be negative");
            output = Objects.requireNonNull (output, "output");
            explicitLightOwners = Set.copyOf (explicitLightOwners);
            desiredInputRoutes = Objects.requireNonNull (desiredInputRoutes, "desiredInputRoutes");
            desiredBridgeSubscriptions = Objects.requireNonNull (desiredBridgeSubscriptions, "desiredBridgeSubscriptions");
            desiredControllerState = Objects.requireNonNull (desiredControllerState, "desiredControllerState");
            desiredControllerActions = Objects.requireNonNull (desiredControllerActions, "desiredControllerActions");
            desiredParameterBanks = Objects.requireNonNull (desiredParameterBanks, "desiredParameterBanks");
            desiredParameterInteraction = Objects.requireNonNull (desiredParameterInteraction, "desiredParameterInteraction");
            executionRequirements = Objects.requireNonNull (executionRequirements, "executionRequirements");
        }


        private static CommittedState initial ()
        {
            return invalidated (0);
        }


        private static CommittedState pending (final long generation, final PreparedResult result)
        {
            final PreparedResult prepared = Objects.requireNonNull (result, "result");
            return new CommittedState (generation, prepared.output (), prepared.explicitLightOwners (), prepared.desiredInputRoutes (), prepared.desiredBridgeSubscriptions (), prepared.desiredControllerState (), prepared.desiredControllerActions (), prepared.desiredParameterBanks (), prepared.desiredParameterInteraction (), prepared.executionRequirements (), prepared);
        }


        private static CommittedState invalidated (final long generation)
        {
            return new CommittedState (generation, new DesiredHardwareOutput (offLights ()), Set.of (), DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), DesiredControllerState.empty (), DesiredControllerActions.empty (), DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), CoreExecutionRequirements.empty (), null);
        }


        private CommittedState applied ()
        {
            return new CommittedState (this.generation, this.output, this.explicitLightOwners, this.desiredInputRoutes, this.desiredBridgeSubscriptions, this.desiredControllerState, this.desiredControllerActions, this.desiredParameterBanks, this.desiredParameterInteraction, this.executionRequirements, null);
        }


        private CommittedState quarantined ()
        {
            final Map<ControlId, RgbColor> passiveLights = new LinkedHashMap<> (this.output.lights ());
            for (final ControlId ratePad: CoreControls.DRUM_RATES)
                passiveLights.put (ratePad, OFF);
            for (final ControlId controlPad: CoreControls.DRUM_CONTROL_PADS)
                passiveLights.put (controlPad, OFF);
            final DesiredHardwareOutput passiveOutput = new DesiredHardwareOutput (passiveLights, this.output.display (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredControllerMappings.empty ());
            return new CommittedState (this.generation, passiveOutput, Set.of (), this.desiredInputRoutes, this.desiredBridgeSubscriptions, this.desiredControllerState, this.desiredControllerActions, DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), CoreExecutionRequirements.empty (), null);
        }
    }


    private interface PreparedAction
    {
        // Closed stable-shell action set.
    }


    record DebugLightObservation (long coreGeneration, long appliedRevision, RgbColor color, boolean mappingDesired, Boolean mappedOn)
    {
        boolean present ()
        {
            return this.color != null;
        }
    }


    private record BridgeEffectTarget (BridgeEffectDomain domain, Object identity)
    {
        private BridgeEffectTarget
        {
            domain = Objects.requireNonNull (domain, "domain");
            identity = Objects.requireNonNull (identity, "identity");
        }
    }


    private enum BridgeEffectDomain
    {
        TRANSPORT_STATE,
        TRANSPORT_VALUE,
        PARAMETER
    }


    private record PreparedPress (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy) implements PreparedAction
    {
    }


    private record PreparedRelease (ControlId owner) implements PreparedAction
    {
    }


    private record PreparedBridgeAction (ControllerBridge.PreparedAction action) implements PreparedAction
    {
        private PreparedBridgeAction
        {
            action = Objects.requireNonNull (action, "action");
        }
    }


    private record LaunchLease (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy, DrumFillClipHost.LaunchTarget target)
    {
    }


}
