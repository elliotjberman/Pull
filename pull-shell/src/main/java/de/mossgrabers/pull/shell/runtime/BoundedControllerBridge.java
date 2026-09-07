// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;


import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.trigger.clip.NewClipAction;
import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.CurrentTrackBankSnapshot;
import de.mossgrabers.pull.core.api.effect.CurrentTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SetCurrentTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.NavigateTrackParentEffect;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.INoteRepeat;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.daw.midi.MidiShortCallback;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.BrowserSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerHardwareSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.DesiredControllerState;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterTouches;
import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.TransportSettingsSnapshot;
import de.mossgrabers.pull.core.api.effect.SetTransportSettingEffect;
import de.mossgrabers.pull.core.api.effect.SetPreRollEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationModeEffect;
import de.mossgrabers.pull.core.api.effect.ResetAutomationOverridesEffect;
import de.mossgrabers.pull.core.api.EncoderConfigurationSnapshot;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import de.mossgrabers.pull.core.api.effect.SetDrumBankPositionEffect;
import de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.DrumPadSnapshot;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.NoteRepeatMode;
import de.mossgrabers.pull.core.api.NoteRepeatSnapshot;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.TrackMonitorMode;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.effect.SetControllerMappingStorageEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.AcquireParameterTouchEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterEnabledEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterNormalizedValueEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.DrumPadBoolean;
import de.mossgrabers.pull.core.api.effect.DrumPadValue;
import de.mossgrabers.pull.core.api.effect.SelectDrumPadEffect;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SelectedTrackValue;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.effect.SetDrumPadBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetDrumPadValueEffect;
import de.mossgrabers.pull.core.api.effect.SetNoteViewPreferenceEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackMonitorEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackValueEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportValueEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.effect.TransportValue;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;


/**
 * Permanent bounded state/effect canopy shared with reloadable controller cores.
 *
 * <p>The private selection-following target is authoritative. The framework drum model is exposed
 * only while its stable channel identity matches that target, so Track Pin cannot redirect core
 * rendering or effects to another track.</p>
 */
final class BoundedControllerBridge implements ControllerBridge
{
    static final int DRUM_PAD_CAPACITY = 16;

    private static final long TRANSPORT_POSITION_SAMPLE_NANOS = 50_000_000L;
    private static final long DRUM_SAMPLE_NANOS = 33_000_000L;
    private static final Map<ControlId, ButtonID> CONSUMABLE_BUTTONS = consumableButtons ();

    private final IModel model;
    private final ITransport transport;
    private final ISelectedTrackNoteTarget selectedTarget;
    private final MidiShortCallback noteInputMidiSender;
    private final PushControlSurface surface;
    private final IValueChanger valueChanger;
    private final RuntimeLog log;
    private final NewClipAction newClipAction;
    private final ParameterTargetHost parameterTargets;
    private final AutomationHost automation;
    private final TransportSettingsHost transportSettings;
    private final MasterCommandHost masterCommands;
    private final ControllerStateHost controllerState;
    private final ControllerMappingHost controllerMappings;
    private final SessionBankHost sessionBank;
    private final CurrentTrackBankHost currentTrackBank;
    private final ControllerSettingsHost controllerSettings;
    private final ApplicationUiHost applicationUi;
    private final Map<MidiStateKey, MidiState> noteInputMidiState = new HashMap<> ();

    private BrowserSnapshot browser = BrowserSnapshot.empty ();
    private ControllerHardwareSnapshot controllerHardware = ControllerHardwareSnapshot.empty ();
    private ControllerBridgeSnapshot snapshot = ControllerBridgeSnapshot.empty ();
    private ControllerLayoutSnapshot sampledLayout = ControllerLayoutSnapshot.empty ();
    private long layoutGeneration;
    private DrumContextSnapshot drumSnapshot = DrumContextSnapshot.empty ();
    private String drumIdentity = "";
    private long drumGeneration;
    private long lastDrumSampleNanos = Long.MIN_VALUE;
    private long lastTransportPositionSampleNanos = Long.MIN_VALUE;
    private double sampledTransportPosition;
    private long sampledSelectedGeneration = -1;
    private long observedSelectedGeneration = -1;
    private long activeCoreGeneration;
    private Runnable inputLifecycleCleanup = () -> {
        // No debugger input is active unless the optional debugger installs one.
    };
    private Runnable debugNoteInputCleanup = () -> {
        // Browser MIDI exists only when the optional debugger is installed.
    };
    private DesiredNoteRepeat desiredNoteRepeat = DesiredNoteRepeat.unowned ();
    private NoteRepeatLease noteRepeatLease;
    private boolean noteRepeatActiveReleasePending;
    private PendingNoteRepeatToggle pendingNoteRepeatToggle;


    /** Production and test seam for the fixed mapped-light observation host. */
    BoundedControllerBridge (final IModel model, final ISelectedTrackNoteTarget selectedTarget, final MidiShortCallback noteInputMidiSender, final PushControlSurface surface, final IValueChanger valueChanger, final RuntimeLog log, final ControllerMappingHost controllerMappings)
    {
        this (model, selectedTarget, noteInputMidiSender, surface, valueChanger, log, controllerMappings, null);
    }


    BoundedControllerBridge (final IModel model, final ISelectedTrackNoteTarget selectedTarget, final MidiShortCallback noteInputMidiSender, final PushControlSurface surface, final IValueChanger valueChanger, final RuntimeLog log, final ControllerMappingHost controllerMappings, final AutomationHost automation)
    {
        this (model, selectedTarget, noteInputMidiSender, surface, valueChanger, log, controllerMappings, automation, null);
    }


    BoundedControllerBridge (final IModel model, final ISelectedTrackNoteTarget selectedTarget, final MidiShortCallback noteInputMidiSender, final PushControlSurface surface, final IValueChanger valueChanger, final RuntimeLog log, final ControllerMappingHost controllerMappings, final AutomationHost automation, final TransportSettingsHost transportSettings)
    {
        this.automation = automation;
        this.transportSettings = transportSettings;
        this.model = Objects.requireNonNull (model, "model");
        this.transport = Objects.requireNonNull (model.getTransport (), "transport");
        this.selectedTarget = Objects.requireNonNull (selectedTarget, "selectedTarget");
        this.noteInputMidiSender = Objects.requireNonNull (noteInputMidiSender, "noteInputMidiSender");
        this.surface = Objects.requireNonNull (surface, "surface");
        this.valueChanger = Objects.requireNonNull (valueChanger, "valueChanger");
        this.newClipAction = new NewClipAction (model);
        this.log = Objects.requireNonNull (log, "log");
        this.parameterTargets = new ParameterTargetHost (surface, model, selectedTarget, this.log);
        this.masterCommands = new MasterCommandHost (model, log);
        this.controllerState = new ControllerStateHost (selectedTarget, surface.getControllerWorkspaceHost (), this::resetNoteInputMidiState);
        this.controllerMappings = controllerMappings;
        this.sessionBank = new SessionBankHost (surface.getSessionBankRegistry (), model.getProject ()::getIdentity, this.log::warn);
        surface.getHost ().setProjectStructureMutationGuard (this.sessionBank::invalidate);
        this.currentTrackBank = new CurrentTrackBankHost (model, surface.getSessionBankRegistry ().getBanks ());
        this.controllerSettings = new ControllerSettingsHost (surface.getConfiguration (), model, surface.getModeManager ());
        this.applicationUi = new ApplicationUiHost (model);
        final var nativeBrowser = model.getBrowser ();
        if (nativeBrowser != null)
        {
            // The framework observer does not replay the current value to a new subscriber.
            this.browser = new BrowserSnapshot (1, nativeBrowser.isActive ());
            nativeBrowser.addActiveObserver (active -> {
                if (this.browser.active () != active.booleanValue ())
                    this.browser = new BrowserSnapshot (Math.incrementExact (this.browser.generation ()), active.booleanValue ());
            });
        }
    }


    /**
     * Refresh bounded read-back. Transport position and the 64-pad window are rate limited; edge
     * events still receive the most recently observed authoritative values.
     *
     * @param monotonicTimeNanos Shell monotonic time
     * @param subscriptions State domains requested by the active core
     * @return True when the public bridge snapshot changed
     */
    @Override
    public boolean refresh (final long monotonicTimeNanos, final DesiredBridgeSubscriptions subscriptions, final DesiredParameterBanks parameterBanks)
    {
        final DesiredBridgeSubscriptions requested = Objects.requireNonNull (subscriptions, "subscriptions");
        this.reconcileNoteRepeatLease ();
        this.controllerState.refresh ();
        final long selectedGeneration = this.selectedTarget.getGeneration ();
        if (this.observedSelectedGeneration >= 0 && selectedGeneration != this.observedSelectedGeneration)
        {
            this.resetNoteInputMidiState ();
            this.parameterTargets.releaseTouches ();
        }
        this.observedSelectedGeneration = selectedGeneration;

        final boolean selectedRequested = requested.includes (BridgeSubscription.SELECTED_TRACK);
        final boolean drumRequested = requested.includes (BridgeSubscription.DRUM_PADS);
        final boolean noteViewRequested = requested.includes (BridgeSubscription.NOTE_VIEW);
        final SelectedTrackNoteTargetSnapshot selectedState = selectedRequested || drumRequested || noteViewRequested ? this.selectedTarget.snapshot () : null;
        final SelectedTrackSnapshot selected = selectedRequested ? toApiSnapshot (selectedState) : SelectedTrackSnapshot.empty ();
        final boolean sessionClipsRequested = requested.includes (BridgeSubscription.SESSION_CLIPS);
        final boolean sessionBankRequested = requested.includes (BridgeSubscription.SESSION_BANK) || sessionClipsRequested;
        if (sessionBankRequested)
            this.sessionBank.refresh (sessionClipsRequested);
        final SessionBankSnapshot sessionBankState = sessionBankRequested ? this.sessionBank.snapshot () : SessionBankSnapshot.empty ();
        final boolean currentTrackBankRequested = requested.includes (BridgeSubscription.CURRENT_TRACK_BANK);
        if (currentTrackBankRequested)
            this.currentTrackBank.refresh ();
        final CurrentTrackBankSnapshot currentTrackBankState = currentTrackBankRequested ? this.currentTrackBank.snapshot () : CurrentTrackBankSnapshot.empty ();

        final TransportSnapshot transportState;
        if (requested.includes (BridgeSubscription.TRANSPORT))
            transportState = this.captureTransport (monotonicTimeNanos);
        else
        {
            transportState = TransportSnapshot.empty ();
            this.lastTransportPositionSampleNanos = Long.MIN_VALUE;
        }

        final ControllerLayoutSnapshot layout = requested.includes (BridgeSubscription.CONTROLLER_LAYOUT) ? this.captureLayout () : ControllerLayoutSnapshot.empty ();
        final NoteViewSnapshot noteView = noteViewRequested ? this.captureNoteView (selectedState) : NoteViewSnapshot.empty ();
        final NoteRepeatSnapshot noteRepeat = requested.includes (BridgeSubscription.NOTE_REPEAT) ? this.captureNoteRepeat () : NoteRepeatSnapshot.empty ();

        if (drumRequested && (selectedState.generation () != this.sampledSelectedGeneration || elapsedAtLeast (monotonicTimeNanos, this.lastDrumSampleNanos, DRUM_SAMPLE_NANOS)))
        {
            this.drumSnapshot = this.captureDrum (selectedState);
            this.lastDrumSampleNanos = monotonicTimeNanos;
            this.sampledSelectedGeneration = selectedState.generation ();
        }
        else if (!drumRequested)
        {
            this.drumSnapshot = DrumContextSnapshot.empty ();
            this.lastDrumSampleNanos = Long.MIN_VALUE;
            this.sampledSelectedGeneration = -1;
            this.drumIdentity = "";
        }

        final boolean parametersRequested = requested.includes (BridgeSubscription.PARAMETERS);
        final DesiredParameterBanks requestedParameterBanks = parametersRequested ? Objects.requireNonNull (parameterBanks, "parameterBanks") : DesiredParameterBanks.empty ();
        this.parameterTargets.refresh (requestedParameterBanks);
        final ParameterBridgeSnapshot parameters = parametersRequested ? this.parameterTargets.snapshot () : ParameterBridgeSnapshot.empty ();
        final ControllerMappingFeedbackSnapshot controllerMappingFeedback = requested.includes (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK) && this.controllerMappings != null ? this.controllerMappings.snapshot () : ControllerMappingFeedbackSnapshot.empty ();
        final boolean masterRequested = requested.includes (BridgeSubscription.MASTER);
        final boolean projectRequested = requested.includes (BridgeSubscription.PROJECT);
        this.masterCommands.refresh (masterRequested, projectRequested);
        final MasterSnapshot master = masterRequested ? this.masterCommands.snapshot () : MasterSnapshot.empty ();
        final ProjectSnapshot project = projectRequested ? this.masterCommands.projectSnapshot () : ProjectSnapshot.empty ();
        final ControllerBridgeSnapshot refreshed = new ControllerBridgeSnapshot (transportState, selected, sessionBankState, layout, noteView, noteRepeat, this.drumSnapshot, parameters, controllerMappingFeedback, master, project, requested.includes (BridgeSubscription.AUTOMATION) && this.automation != null ? this.automation.snapshot () : AutomationSnapshot.empty (), requested.includes (BridgeSubscription.ENCODER_CONFIGURATION) ? new EncoderConfigurationSnapshot (true, this.valueChanger.getUpperBound (), this.valueChanger.getStepSize (), this.surface.getConfiguration ().getKnobSensitivityDefault (), this.surface.getConfiguration ().getKnobSensitivitySlow ()) : EncoderConfigurationSnapshot.empty (), currentTrackBankState, requested.includes (BridgeSubscription.TRANSPORT_SETTINGS) && this.transportSettings != null ? this.transportSettings.snapshot () : TransportSettingsSnapshot.empty (), requested.includes (BridgeSubscription.CONTROLLER_SETTINGS) ? this.controllerSettings.snapshot () : de.mossgrabers.pull.core.api.ControllerSettingsSnapshot.empty (), this.applicationUi.refresh (requested.includes (BridgeSubscription.APPLICATION_UI)), this.surface.getModeManager ().requests (requested.includes (BridgeSubscription.CONTROLLER_PAGES)), requested.includes (BridgeSubscription.BROWSER) ? this.browser : BrowserSnapshot.empty (), requested.includes (BridgeSubscription.CONTROLLER_HARDWARE) ? this.captureControllerHardware () : ControllerHardwareSnapshot.empty ());
        if (refreshed.equals (this.snapshot))
            return false;

        this.snapshot = refreshed;
        return true;
    }


    /** Sample the existing identity-response fields; no additional MIDI or host observer is installed. */
    private ControllerHardwareSnapshot captureControllerHardware ()
    {
        final int major = this.surface.getMajorVersion ();
        final int minor = this.surface.getMinorVersion ();
        final int build = this.surface.getBuildNumber ();
        final int board = this.surface.getBoardRevision ();
        final int serial = this.surface.getSerialNumber ();
        if (major < 0 || minor < 0 || build < 0 || board < 0)
            return ControllerHardwareSnapshot.empty ();
        final ControllerHardwareSnapshot previous = this.controllerHardware;
        if (!previous.available () || major != previous.firmwareMajor () || minor != previous.firmwareMinor () || build != previous.firmwareBuild () || board != previous.boardRevision () || serial != previous.serialNumber ())
            this.controllerHardware = new ControllerHardwareSnapshot (Math.incrementExact (previous.generation ()), major, minor, build, board, serial);
        return this.controllerHardware;
    }


    /**
     * Reconcile stateful MIDI when a different child-core generation takes ownership.
     *
     * @param generation Active runtime generation
     */
    @Override
    public void activateCoreGeneration (final long generation)
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        if (this.activeCoreGeneration != 0 && generation != this.activeCoreGeneration)
        {
            this.sessionBank.invalidate ();
            this.inputLifecycleCleanup.run ();
            this.resetNoteInputMidiState ();
            this.parameterTargets.releaseTouches ();
        }
        this.activeCoreGeneration = generation;
        this.controllerState.activateCoreGeneration (generation);
        if (generation > 0) this.surface.getModeManager ().activateConsumer (generation);
    }


    /**
     * Release parent-owned stateful MIDI during terminal invalidation.
     */
    @Override
    public void invalidate ()
    {
        this.sessionBank.invalidate ();
        this.inputLifecycleCleanup.run ();
        this.resetNoteInputMidiState ();
        this.surface.getModeManager ().invalidate ();
        this.controllerState.invalidate ();
        this.applyNoteRepeat (DesiredNoteRepeat.unowned ());
        this.parameterTargets.invalidate ();
    }


    @Override
    public boolean canReplaceActiveCore ()
    {
        final var pages = this.surface.getModeManager ();
        // Input flush can acknowledge a request after this tick's host snapshot was sampled.
        // Keep the candidate pending until its bootstrap snapshot contains that retired prefix.
        return pages.canReplaceCore () && this.snapshot.controllerPages ().retiredSequence () == pages.requests (false).retiredSequence ();
    }


    @Override
    public void setInputLifecycleCleanup (final Runnable cleanup, final Runnable neutralizeNoteInput)
    {
        this.inputLifecycleCleanup = Objects.requireNonNull (cleanup, "cleanup");
        this.debugNoteInputCleanup = Objects.requireNonNull (neutralizeNoteInput, "neutralizeNoteInput");
    }


    @Override
    public void abandonActiveCore ()
    {
        try
        {
            this.sessionBank.invalidate ();
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Session launch quarantine cleanup failed: " + failure.getMessage ());
        }
        try
        {
            this.inputLifecycleCleanup.run ();
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Debug input quarantine cleanup failed: " + failure.getMessage ());
        }
        try
        {
            this.surface.getModeManager ().invalidate ();
            this.controllerState.invalidate ();
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Note-input route quarantine cleanup failed: " + failure.getMessage ());
        }
        try
        {
            this.resetNoteInputMidiState ();
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Note-input MIDI quarantine cleanup failed: " + failure.getMessage ());
        }
        try
        {
            this.parameterTargets.invalidate ();
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Parameter quarantine cleanup failed: " + failure.getMessage ());
        }
        try
        {
            this.applyNoteRepeat (DesiredNoteRepeat.unowned ());
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Note-repeat quarantine cleanup failed: " + failure.getMessage ());
        }
    }


    @Override
    public ControllerBridge.TargetedParameter resolveParameterMutation (final de.mossgrabers.framework.controller.hardware.IHwContinuousControl control)
    {
        return this.parameterTargets.resolveMutation (control);
    }


    @Override
    public boolean requiresResolvedParameterMutation (final de.mossgrabers.framework.controller.hardware.IHwContinuousControl control)
    {
        return this.parameterTargets.requiresResolvedMutation (control);
    }


    @Override
    public Map<ControlId, ControllerBridge.ParameterTouchLease> prepareParameterTouches (final DesiredParameterTouches touches, final DesiredParameterBanks banks)
    {
        return this.parameterTargets.prepareTouches (touches, banks);
    }


    @Override
    public void releaseParameterTouches (final Map<ControlId, ControllerBridge.ParameterTouchLease> touches)
    {
        this.parameterTargets.releaseTouchesExcept (touches);
    }


    @Override
    public void acquireParameterTouches (final Map<ControlId, ControllerBridge.ParameterTouchLease> touches)
    {
        this.parameterTargets.acquireTouches (touches);
    }


    @Override
    public Map<ParameterTargetRef, ControllerBridge.ParameterLease> prepareParameterLeases (final DesiredParameterInteraction desired, final DesiredParameterBanks parameterBanks)
    {
        return Map.copyOf (this.parameterTargets.prepareLeases (desired, parameterBanks));
    }


    @Override
    public boolean applyParameterLeases (final Map<ParameterTargetRef, ControllerBridge.ParameterLease> prepared, final DesiredParameterBanks parameterBanks)
    {
        if (!this.parameterTargets.applyLeases (retainedTargets (prepared), Objects.requireNonNull (parameterBanks, "parameterBanks")))
            return false;
        this.snapshot = new ControllerBridgeSnapshot (
            this.snapshot.transport (),
            this.snapshot.selectedTrack (),
            this.snapshot.sessionBank (),
            this.snapshot.layout (),
            this.snapshot.noteView (),
            this.snapshot.noteRepeat (),
            this.snapshot.drum (),
            this.parameterTargets.snapshot (),
            this.snapshot.controllerMappingFeedback (),
            this.snapshot.master (),
            this.snapshot.project (),
            this.snapshot.automation (),
            this.snapshot.encoderConfiguration (),
            this.snapshot.currentTrackBank (),
            this.snapshot.transportSettings (),
            this.snapshot.controllerSettings (),
            this.snapshot.applicationUi (),
            this.snapshot.controllerPages (),
            this.snapshot.browser (),
            this.snapshot.controllerHardware ());
        return true;
    }


    @Override
    public boolean retainsParameterTarget (final ParameterTargetRef target)
    {
        return this.parameterTargets.retains (target);
    }


    @Override
    public void setNoteInputLifecycleIdle (final BooleanSupplier idle)
    {
        this.controllerState.setInputLifecycleIdle (idle);
    }


    @Override
    public DesiredControllerState prepareControllerState (final DesiredControllerState state)
    {
        this.surface.getModeManager ().prepare (state.page ());
        return this.controllerState.prepare (state);
    }


    @Override
    public void applyControllerState (final DesiredControllerState state)
    {
        this.parameterTargets.releaseIndicationsExcept (state.page ().parameterIndications ());
        this.surface.getModeManager ().apply (state.page ());
        this.controllerState.apply (state);
        this.parameterTargets.applyIndications (state.page ().parameterIndications ());
    }


    @Override
    public NotePerformanceState notePerformanceState ()
    {
        return this.controllerState.state ();
    }


    @Override
    public DesiredNoteRepeat prepareNoteRepeat (final DesiredNoteRepeat noteRepeat)
    {
        return Objects.requireNonNull (noteRepeat, "noteRepeat");
    }


    @Override
    public void applyNoteRepeat (final DesiredNoteRepeat noteRepeat)
    {
        final DesiredNoteRepeat next = Objects.requireNonNull (noteRepeat, "noteRepeat");
        final INoteRepeat engine = this.noteRepeatEngine ();
        // Complete desired ownership supplies the lifecycle edge: owned acquires the lease;
        // unowned releases it only after the terminal inactive state is read back from Bitwig.
        if (next.owned () && this.noteRepeatLease == null && engine != null)
            this.noteRepeatLease = new NoteRepeatLease (engine.isLatchActive (), engine.isFreeRunning (), engine.usePressure (), engine.isShuffle ());
        this.desiredNoteRepeat = next;
        this.reconcileNoteRepeatLease ();
    }


    /**
     * Get the latest immutable bridge snapshot.
     *
     * @return Bridge state
     */
    @Override
    public ControllerBridgeSnapshot snapshot ()
    {
        return this.snapshot;
    }


    /**
     * Translate and validate a bridge effect into parent-owned primitive intent.
     *
     * @param effect Candidate effect
     * @return Prepared action, or {@code null} when the effect belongs to another shell domain
     */
    @Override
    public ControllerBridge.PreparedAction prepare (final CoreEffect effect, final Map<ParameterTargetRef, ControllerBridge.ParameterLease> parameterLeases)
    {
        if (effect instanceof final SetControllerMappingStorageEffect storage)
            return new PreparedControllerMappingStorage (storage);
        Objects.requireNonNull (effect, "effect");
        final ControllerBridge.PreparedAction masterAction = this.masterCommands.prepare (effect);
        if (masterAction != null)
            return masterAction;
        if (effect instanceof final SetTransportStateEffect setState)
            return new PreparedTransportState (setState.state (), setState.enabled ());
        if (effect instanceof final SetParameterValueEffect setParameter)
            return new PreparedParameterSet (this.parameterTargets.prepare (setParameter, retainedTargets (parameterLeases)));
        if (effect instanceof final AdjustParameterValueEffect adjustParameter)
            return new PreparedParameterAdjust (this.parameterTargets.prepare (adjustParameter));
        if (effect instanceof final ResetParameterEffect resetParameter)
            return new PreparedParameterReset (this.parameterTargets.prepare (resetParameter));
        if (effect instanceof final SetParameterNormalizedValueEffect normalized)
            return new PreparedParameterNormalized (this.parameterTargets.prepare (normalized));
        if (effect instanceof final SetParameterEnabledEffect enabled)
            return new PreparedParameterEnabled (this.parameterTargets.prepare (enabled));
        if (effect instanceof final AcquireParameterTouchEffect touch)
            return new PreparedParameterTouch (this.parameterTargets.prepare (touch));
        if (effect instanceof final SetTransportValueEffect setValue)
        {
            if (setValue.value () == TransportValue.TEMPO && (setValue.amount () < this.transport.getMinimumTempo () || setValue.amount () > this.transport.getMaximumTempo ()))
                throw new IllegalArgumentException ("Requested tempo is outside the host range");
            return new PreparedTransportValue (setValue.value (), setValue.amount ());
        }
        if (effect instanceof final SetSelectedTrackBooleanEffect setBoolean)
        {
            this.requireSelectedTarget (setBoolean.targetGeneration (), setBoolean.channelId ());
            return new PreparedSelectedBoolean (setBoolean.targetGeneration (), setBoolean.channelId (), setBoolean.property (), setBoolean.enabled ());
        }
        if (effect instanceof final SetSelectedTrackMonitorEffect setMonitor)
        {
            this.requireSelectedTarget (setMonitor.targetGeneration (), setMonitor.channelId ());
            return new PreparedSelectedMonitor (setMonitor.targetGeneration (), setMonitor.channelId (), setMonitor.mode ());
        }
        if (effect instanceof final SetSelectedTrackValueEffect setValue)
        {
            this.requireSelectedTarget (setValue.targetGeneration (), setValue.channelId ());
            return new PreparedSelectedValue (setValue.targetGeneration (), setValue.channelId (), setValue.value (), setValue.normalizedValue ());
        }
        if (effect instanceof final SelectedTrackActionEffect action)
        {
            this.requireSelectedTarget (action.targetGeneration (), action.channelId ());
            return new PreparedSelectedAction (action.targetGeneration (), action.channelId (), action.action ());
        }
        if (effect instanceof final CurrentTrackActionEffect action)
            return this.currentTrackBank.prepare (action);
        if (effect instanceof final SetCurrentTrackBooleanEffect action)
            return this.currentTrackBank.prepare (action);
        if (effect instanceof final NavigateTrackParentEffect action)
            return this.currentTrackBank.prepare (action);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect action)
            return this.currentTrackBank.prepare (action);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetControllerBooleanSettingEffect setting)
            return this.controllerSettings.prepare (setting);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect setting)
            return this.controllerSettings.prepare (setting);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetControllerModeSettingEffect setting)
            return this.controllerSettings.prepare (setting);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect ui)
            return this.applicationUi.prepare (ui);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect ui)
            return this.applicationUi.prepare (ui);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetArrangerBooleanEffect ui)
            return this.applicationUi.prepare (ui);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetMixerBooleanEffect ui)
            return this.applicationUi.prepare (ui);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SessionActionEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.CopySessionClipEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.CreateSessionClipEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final de.mossgrabers.pull.core.api.effect.SetSessionBankPositionEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final StopSessionBankEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final SelectSessionTrackEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final StopSessionTrackEffect action)
            return this.sessionBank.prepare (action);
        if (effect instanceof final SetAutomationWriteEffect write)
        {
            if (this.automation == null || !this.snapshot.automation ().available ())
                throw new IllegalArgumentException ("Automation Write capability is unavailable or not subscribed");
            return this.automation.prepare (write);
        }
        if (effect instanceof final SetAutomationModeEffect mode)
        {
            if (this.automation == null || !this.snapshot.automation ().available ()) throw new IllegalArgumentException ("Automation canopy unavailable or not subscribed");
            return this.automation.prepare (mode);
        }
        if (effect instanceof final ResetAutomationOverridesEffect reset)
        {
            if (this.automation == null || !this.snapshot.automation ().available ()) throw new IllegalArgumentException ("Automation canopy unavailable or not subscribed");
            return this.automation.prepare (reset);
        }
        if (effect instanceof final SetTransportSettingEffect setting)
        {
            if (this.transportSettings == null || !this.snapshot.transportSettings ().available ()) throw new IllegalArgumentException ("Transport settings canopy unavailable or not subscribed");
            return this.transportSettings.prepare (setting);
        }
        if (effect instanceof final SetPreRollEffect preRoll)
        {
            if (this.transportSettings == null || !this.snapshot.transportSettings ().available ()) throw new IllegalArgumentException ("Transport settings canopy unavailable or not subscribed");
            return this.transportSettings.prepare (preRoll);
        }
        if (effect instanceof final ConsumeControllerButtonEffect consumption)
        {
            final ButtonID button = CONSUMABLE_BUTTONS.get (consumption.controlId ());
            if (button == null)
                throw new IllegalArgumentException ("Controller-button consumption is not installed for " + consumption.controlId ().value ());
            return new PreparedControllerButtonConsumption (button);
        }
        if (effect instanceof final SendNoteInputMidiEffect midi)
            return new PreparedNoteInputMidi (midi.status (), midi.data1 (), midi.data2 ());
        if (effect instanceof final ShowHostNotificationEffect notification)
            return new PreparedHostNotification (notification.text ());
        if (effect instanceof final SetDrumBankPositionEffect position)
        {
            final DrumContextSnapshot drum = this.snapshot.drum ();
            if (!drum.available () || !drum.modelAligned () || drum.generation () != position.contextGeneration () || !drum.targetChannelId ().equals (position.targetChannelId ()) || drum.deviceId ().isBlank ())
                throw new IllegalArgumentException ("Drum bank position targets a stale window");
            return new PreparedDrumBankPosition (position, drum.targetGeneration (), drum.deviceId (), drum.baseMidiNote ());
        }
        if (effect instanceof final SetNoteViewPreferenceEffect preference)
        {
            this.requireSelectedTarget (preference.targetGeneration (), preference.channelId ());
            if (this.snapshot.selectedTrack ().position () != preference.trackPosition ())
                throw new IllegalArgumentException ("Note-view preference targets a stale track position");
            return new PreparedNoteViewPreference (preference.targetGeneration (), preference.channelId (), preference.trackPosition (), preference.view ());
        }
        if (effect instanceof final SetDrumPadBooleanEffect setPad)
        {
            final DrumPadSnapshot pad = this.requireDrumPad (setPad.contextGeneration (), setPad.targetChannelId (), setPad.padIndex ());
            final DrumContextSnapshot drum = this.snapshot.drum ();
            return new PreparedDrumBoolean (setPad.contextGeneration (), setPad.targetChannelId (), drum.deviceId (), drum.baseMidiNote (), setPad.padIndex (), pad.channelId (), setPad.property (), setPad.enabled ());
        }
        if (effect instanceof final SetDrumPadValueEffect setPad)
        {
            final DrumPadSnapshot pad = this.requireDrumPad (setPad.contextGeneration (), setPad.targetChannelId (), setPad.padIndex ());
            final DrumContextSnapshot drum = this.snapshot.drum ();
            return new PreparedDrumValue (setPad.contextGeneration (), setPad.targetChannelId (), drum.deviceId (), drum.baseMidiNote (), setPad.padIndex (), pad.channelId (), setPad.value (), setPad.normalizedValue ());
        }
        if (effect instanceof final SelectDrumPadEffect selectPad)
        {
            final DrumPadSnapshot pad = this.requireDrumPad (selectPad.contextGeneration (), selectPad.targetChannelId (), selectPad.padIndex ());
            final DrumContextSnapshot drum = this.snapshot.drum ();
            return new PreparedDrumSelection (selectPad.contextGeneration (), selectPad.targetChannelId (), drum.deviceId (), drum.baseMidiNote (), selectPad.padIndex (), pad.channelId ());
        }
        return null;
    }


    ControllerBridge.PreparedAction prepare (final CoreEffect effect)
    {
        return this.prepare (effect, Map.of ());
    }


    private static Map<ParameterTargetRef, ParameterTargetHost.RetainedTarget> retainedTargets (final Map<ParameterTargetRef, ControllerBridge.ParameterLease> leases)
    {
        final Map<ParameterTargetRef, ParameterTargetHost.RetainedTarget> retained = new LinkedHashMap<> ();
        Objects.requireNonNull (leases, "leases").forEach ( (target, lease) -> {
            if (!(lease instanceof final ParameterTargetHost.RetainedTarget retainedTarget))
                throw new IllegalArgumentException ("Parameter lease belongs to another controller bridge");
            retained.put (target, retainedTarget);
        });
        return Map.copyOf (retained);
    }


    /**
     * Apply one prepared action after the runtime generation has committed.
     *
     * @param action Parent-owned action
     */
    @Override
    public void apply (final ControllerBridge.PreparedAction action)
    {
        if (action instanceof final PreparedControllerMappingStorage storage)
        {
            if (this.controllerMappingContextMatches (storage.effect ().context ()))
                this.controllerMappings.storage ().compareAndSet (storage.effect ());
            return;
        }
        Objects.requireNonNull (action, "action");
        if (this.masterCommands.applyIfOwned (action))
            return;
        if (action instanceof final AutomationHost.PreparedWrite write)
            this.automation.apply (write);
        else if (action instanceof final AutomationHost.PreparedMode mode)
            this.automation.apply (mode);
        else if (action instanceof final AutomationHost.PreparedReset reset)
            this.automation.apply (reset);
        else if (action instanceof final TransportSettingsHost.PreparedBoolean setting)
            this.transportSettings.apply (setting);
        else if (action instanceof final TransportSettingsHost.PreparedPreRoll preRoll)
            this.transportSettings.apply (preRoll);
        else if (action instanceof final ControllerSettingsHost.PreparedBoolean setting)
            this.controllerSettings.apply (setting);
        else if (action instanceof final ControllerSettingsHost.PreparedInteger setting)
            this.controllerSettings.apply (setting);
        else if (action instanceof final ControllerSettingsHost.PreparedMode setting)
            this.controllerSettings.apply (setting);
        else if (action instanceof final ApplicationUiHost.PreparedLayout ui)
            this.applicationUi.apply (ui);
        else if (action instanceof final ApplicationUiHost.PreparedPanel ui)
            this.applicationUi.apply (ui);
        else if (action instanceof final ApplicationUiHost.PreparedArranger ui)
            this.applicationUi.apply (ui);
        else if (action instanceof final ApplicationUiHost.PreparedMixer ui)
            this.applicationUi.apply (ui);
        else if (action instanceof final PreparedTransportState state)
            this.masterCommands.applyTransportState (state.state (), state.enabled ());
        else if (action instanceof final PreparedParameterSet parameter)
            this.parameterTargets.apply (parameter.action ());
        else if (action instanceof final PreparedParameterAdjust parameter)
            this.parameterTargets.apply (parameter.action ());
        else if (action instanceof final PreparedParameterReset parameter)
            this.parameterTargets.apply (parameter.action ());
        else if (action instanceof final PreparedParameterNormalized parameter)
            this.parameterTargets.apply (parameter.action ());
        else if (action instanceof final PreparedParameterEnabled parameter)
            this.parameterTargets.apply (parameter.action ());
        else if (action instanceof final PreparedParameterTouch parameter)
            this.parameterTargets.apply (parameter.action ());
        else if (action instanceof final PreparedTransportValue value)
            this.applyTransportValue (value);
        else if (action instanceof final PreparedSelectedBoolean state)
            this.applySelectedBoolean (state);
        else if (action instanceof final PreparedSelectedMonitor monitor)
            this.applySelectedMonitor (monitor);
        else if (action instanceof final PreparedSelectedValue value)
            this.applySelectedValue (value);
        else if (action instanceof final PreparedSelectedAction selectedAction)
            this.applySelectedAction (selectedAction);
        else if (action instanceof final CurrentTrackBankHost.PreparedTrackAction trackAction)
            this.currentTrackBank.apply (trackAction);
        else if (action instanceof final CurrentTrackBankHost.PreparedBoolean trackBoolean)
            this.currentTrackBank.apply (trackBoolean);
        else if (action instanceof final CurrentTrackBankHost.PreparedParent parent)
            this.currentTrackBank.apply (parent);
        else if (action instanceof final CurrentTrackBankHost.PreparedNavigation navigation)
            this.currentTrackBank.apply (navigation);
        else if (action instanceof final SessionBankHost.PreparedLauncherAction sessionAction)
            this.sessionBank.apply (sessionAction);
        else if (action instanceof final SessionBankHost.PreparedCopy sessionAction)
            this.sessionBank.apply (sessionAction);
        else if (action instanceof final SessionBankHost.PreparedCreate sessionAction)
            this.sessionBank.apply (sessionAction);
        else if (action instanceof final SessionBankHost.PreparedPosition sessionAction)
            this.sessionBank.apply (sessionAction);
        else if (action instanceof final SessionBankHost.PreparedStop sessionAction)
            this.sessionBank.apply (sessionAction);
        else if (action instanceof final SessionBankHost.PreparedSelection sessionSelection)
            this.sessionBank.apply (sessionSelection);
        else if (action instanceof final SessionBankHost.PreparedTrackStop sessionTrackStop)
            this.sessionBank.apply (sessionTrackStop);
        else if (action instanceof final PreparedControllerButtonConsumption consumption)
            this.surface.setTriggerConsumed (consumption.button ());
        else if (action instanceof final PreparedNoteInputMidi midi)
            this.applyNoteInputMidi (midi);
        else if (action instanceof final PreparedNoteViewPreference preference)
            this.applyNoteViewPreference (preference);
        else if (action instanceof final PreparedDrumBoolean state)
            this.applyDrumBoolean (state);
        else if (action instanceof final PreparedDrumValue value)
            this.applyDrumValue (value);
        else if (action instanceof final PreparedDrumSelection selection)
            this.applyDrumSelection (selection);
        else if (action instanceof final PreparedDrumBankPosition position)
            this.applyDrumBankPosition (position);
        else if (action instanceof final PreparedHostNotification notification)
            this.model.getHost ().showNotification (notification.text ());
    }


    private TransportSnapshot captureTransport (final long now)
    {
        final boolean samplePosition = !this.transport.isPlaying () || elapsedAtLeast (now, this.lastTransportPositionSampleNanos, TRANSPORT_POSITION_SAMPLE_NANOS);
        if (samplePosition)
        {
            this.sampledTransportPosition = Math.max (0, this.transport.getPosition ());
            this.lastTransportPositionSampleNanos = now;
        }

        final double tempo = Math.max (0, this.transport.getTempo ());
        final int numerator = Math.max (0, this.transport.getNumerator ());
        final int denominator = Math.max (0, this.transport.getDenominator ());
        final boolean available = tempo > 0 && numerator > 0 && denominator > 0;
        return new TransportSnapshot (
            available,
            this.model.getApplication ().isEngineActive (),
            this.transport.isPlaying (),
            this.transport.isRecording (),
            this.transport.isArrangerOverdub (),
            this.transport.isLauncherOverdub (),
            this.transport.isLoop (),
            this.transport.isMetronomeOn (),
            this.transport.isFillModeActive (),
            tempo,
            this.sampledTransportPosition,
            numerator,
            denominator);
    }


    private ControllerLayoutSnapshot captureLayout ()
    {
        final Object activeView = this.surface.getViewManager ().getActiveID ();
        final Object activeMode = this.surface.getModeManager ().getActiveID ();
        final Object underlyingMode = this.surface.getModeManager ().getActiveIDIgnoreTemporary ();
        final Object previousMode = this.surface.getModeManager ().getPreviousID ();
        final ControllerLayoutSnapshot captured = new ControllerLayoutSnapshot (
            this.layoutGeneration,
            activeView == null ? "" : activeView.toString (),
            activeMode == null ? "" : activeMode.toString (),
            this.surface.isDrumPadLayoutActive (),
            this.surface.isDrumControllerActive (),
            this.model.getScales ().getDrumOffset (),
            this.captureGridPressureConfiguration (),
            this.surface.getAppliedNoteTranslation (),
            underlyingMode == null ? "" : underlyingMode.toString (),
            previousMode == null ? "" : previousMode.toString (),
            this.surface.getModeManager ().isTemporary ());
        if (sameLayout (captured, this.sampledLayout))
            return this.sampledLayout;
        this.sampledLayout = new ControllerLayoutSnapshot (
            ++this.layoutGeneration,
            captured.viewId (),
            captured.modeId (),
            captured.drumLayoutActive (),
            captured.drumControllerEngaged (),
            captured.drumBaseMidiNote (),
            captured.gridPressure (),
            captured.appliedNoteTranslation (),
            captured.activeModeId (),
            captured.previousModeId (),
            captured.temporaryMode ());
        return this.sampledLayout;
    }


    private static boolean sameLayout (final ControllerLayoutSnapshot first, final ControllerLayoutSnapshot second)
    {
        return first.viewId ().equals (second.viewId ()) && first.modeId ().equals (second.modeId ()) && first.activeModeId ().equals (second.activeModeId ()) && first.previousModeId ().equals (second.previousModeId ()) && first.temporaryMode () == second.temporaryMode () && first.drumLayoutActive () == second.drumLayoutActive () && first.drumControllerEngaged () == second.drumControllerEngaged () && first.drumBaseMidiNote () == second.drumBaseMidiNote () && first.gridPressure ().equals (second.gridPressure ()) && first.appliedNoteTranslation ().equals (second.appliedNoteTranslation ());
    }


    @Override
    public boolean supportsPageInput (final de.mossgrabers.pull.core.api.DesiredControllerPageState page, final ControlId control, final de.mossgrabers.pull.core.api.event.InputKind kind)
    {
        return page.effectivePage ().kind () == de.mossgrabers.pull.core.api.ControllerPageRef.Kind.CORE && de.mossgrabers.controller.ableton.push.mode.CorePageMode.containsInput (control, kind);
    }

    @Override
    public boolean supportsPageLight (final de.mossgrabers.pull.core.api.DesiredControllerPageState page, final ControlId control)
    {
        return page.effectivePage ().kind () == de.mossgrabers.pull.core.api.ControllerPageRef.Kind.CORE && de.mossgrabers.controller.ableton.push.mode.CorePageMode.containsLight (control);
    }


    private NoteViewSnapshot captureNoteView (final SelectedTrackNoteTargetSnapshot selected)
    {
        if (selected == null || !selected.exists () || selected.trackID ().isBlank ())
            return NoteViewSnapshot.empty ();
        final de.mossgrabers.framework.view.Views preferred = this.surface.getViewManager ().getPreferredView (selected.position ());
        final ControllerNoteView preferredView = preferred == null ? ControllerNoteView.NONE : ControllerNoteView.fromStableId (preferred.name ());
        return new NoteViewSnapshot (selected.generation (), selected.trackID (), selected.position (), preferredView, this.surface.isDrumControllerApplicable ());
    }


    private NoteRepeatSnapshot captureNoteRepeat ()
    {
        final INoteRepeat engine = this.noteRepeatEngine ();
        if (engine == null)
            return new NoteRepeatSnapshot (false, this.surface.getConfiguration ().isDrumControllerRollEnabled (), false, NoteRepeatMode.ALL, 0, 0.25, 0.5, false, false, false, false);
        return new NoteRepeatSnapshot (
            true,
            this.surface.getConfiguration ().isDrumControllerRollEnabled (),
            engine.isActive (),
            NoteRepeatMode.valueOf (engine.getMode ().name ()),
            engine.getOctaves (),
            positiveOrDefault (engine.getPeriod (), 0.25),
            positiveOrDefault (engine.getNoteLength (), 0.5),
            engine.isLatchActive (),
            engine.isFreeRunning (),
            engine.usePressure (),
            engine.isShuffle ());
    }


    private INoteRepeat noteRepeatEngine ()
    {
        final de.mossgrabers.framework.daw.midi.INoteInput input = this.surface.getMidiInput ().getDefaultNoteInput ();
        return input == null ? null : input.getNoteRepeat ();
    }


    private void reconcileNoteRepeatLease ()
    {
        final de.mossgrabers.controller.ableton.push.PushConfiguration configuration = this.surface.getConfiguration ();
        if (this.noteRepeatActiveReleasePending && !configuration.isNoteRepeatActive ())
            this.noteRepeatActiveReleasePending = false;
        if (!this.desiredNoteRepeat.owned () && this.noteRepeatLease == null)
            return;
        final boolean activeSettingReleased = this.desiredNoteRepeat.owned () || this.releaseNoteRepeatActiveSetting (configuration);
        final INoteRepeat engine = this.noteRepeatEngine ();
        if (engine == null)
            return;
        final DesiredNoteRepeat target;
        if (this.desiredNoteRepeat.owned ())
            target = this.desiredNoteRepeat;
        else
        {
            final NoteRepeatLease lease = this.noteRepeatLease;
            if (lease == null)
                return;
            target = this.releasedNoteRepeat (lease);
        }

        setIfDifferent (engine.getMode (), ArpeggiatorMode.valueOf (target.mode ().name ()), engine::setMode);
        if (engine.getOctaves () != target.octaves ())
            engine.setOctaves (target.octaves ());
        if (!close (engine.getPeriod (), target.period ()))
            engine.setPeriod (target.period ());
        if (!close (engine.getNoteLength (), target.noteLength ()))
            engine.setNoteLength (target.noteLength ());
        if (engine.isLatchActive () != target.latchActive ())
            engine.setLatchActive (target.latchActive ());
        if (engine.isActive () != target.active ())
            engine.setActive (target.active ());

        if (!this.reconcileToggle (engine, target))
            return;
        if (!this.desiredNoteRepeat.owned () && activeSettingReleased && matches (engine, target))
        {
            this.noteRepeatLease = null;
            this.pendingNoteRepeatToggle = null;
        }
    }


    private boolean releaseNoteRepeatActiveSetting (final de.mossgrabers.controller.ableton.push.PushConfiguration configuration)
    {
        if (!configuration.isNoteRepeatActive ())
            return true;
        if (!this.noteRepeatActiveReleasePending)
        {
            configuration.setNoteRepeatActive (false);
            this.noteRepeatActiveReleasePending = true;
        }
        return false;
    }


    private boolean reconcileToggle (final INoteRepeat engine, final DesiredNoteRepeat target)
    {
        final PendingNoteRepeatToggle pending = this.pendingNoteRepeatToggle;
        if (pending != null)
        {
            if (pending.kind ().read (engine) != pending.expected ())
                return false;
            this.pendingNoteRepeatToggle = null;
        }
        for (final NoteRepeatToggle kind: NoteRepeatToggle.values ())
        {
            final boolean expected = kind.expected (target);
            if (kind.read (engine) == expected)
                continue;
            kind.toggle (engine);
            this.pendingNoteRepeatToggle = new PendingNoteRepeatToggle (kind, expected);
            return false;
        }
        return true;
    }


    private DesiredNoteRepeat releasedNoteRepeat (final NoteRepeatLease lease)
    {
        final de.mossgrabers.controller.ableton.push.PushConfiguration configuration = this.surface.getConfiguration ();
        return new DesiredNoteRepeat (
            true,
            false,
            NoteRepeatMode.valueOf (configuration.getNoteRepeatMode ().name ()),
            configuration.getNoteRepeatOctave (),
            configuration.getNoteRepeatPeriod ().getValue (),
            configuration.getNoteRepeatLength ().getValue (),
            lease.latchActive (),
            lease.freeRunning (),
            lease.usePressure (),
            lease.shuffle ());
    }


    private static boolean matches (final INoteRepeat engine, final DesiredNoteRepeat target)
    {
        return engine.isActive () == target.active () && engine.getMode ().name ().equals (target.mode ().name ()) && engine.getOctaves () == target.octaves () && close (engine.getPeriod (), target.period ()) && close (engine.getNoteLength (), target.noteLength ()) && engine.isLatchActive () == target.latchActive () && engine.isFreeRunning () == target.freeRunning () && engine.usePressure () == target.usePressure () && engine.isShuffle () == target.shuffle ();
    }


    private static void setIfDifferent (final ArpeggiatorMode actual, final ArpeggiatorMode desired, final java.util.function.Consumer<ArpeggiatorMode> setter)
    {
        if (actual != desired)
            setter.accept (desired);
    }


    private static double positiveOrDefault (final double value, final double fallback)
    {
        return Double.isFinite (value) && value > 0 ? value : fallback;
    }


    private static boolean close (final double left, final double right)
    {
        return Math.abs (left - right) < 0.000001;
    }


    private GridPressureConfiguration captureGridPressureConfiguration ()
    {
        final int conversion = this.surface.getConfiguration ().getConvertAftertouch ();
        return switch (conversion)
        {
            case AbstractConfiguration.AFTERTOUCH_CONVERT_OFF -> GridPressureConfiguration.OFF;
            case AbstractConfiguration.AFTERTOUCH_CONVERT_POLY -> GridPressureConfiguration.POLY;
            case AbstractConfiguration.AFTERTOUCH_CONVERT_CHANNEL -> GridPressureConfiguration.CHANNEL;
            default -> GridPressureConfiguration.controlChange (conversion);
        };
    }


    private DrumContextSnapshot captureDrum (final SelectedTrackNoteTargetSnapshot selected)
    {
        final String targetID = selected.trackID ();
        final String modelTrackID = this.model.getCursorTrack ().getChannelID ();
        final boolean aligned = selected.exists () && !targetID.isBlank () && this.model.getCursorTrack ().doesExist () && targetID.equals (modelTrackID);
        final IDrumDevice drum = this.model.getDrumDevice ();
        final boolean available = aligned && this.selectedTarget.hasDrumDevice () && drum.doesExist () && drum.hasDrumPads ();
        if (!available)
        {
            this.updateDrumGeneration (targetID + "|" + selected.generation () + "|" + aligned + "|unavailable");
            return new DrumContextSnapshot (this.drumGeneration, selected.generation (), targetID, "", false, aligned, 0, List.of ());
        }

        final IDrumPadBank bank = drum.getDrumPadBank ();
        final int baseNote = clampMidiNote (bank.getScrollPosition ());
        final String drumDeviceID = valueOrEmpty (drum.getID ());
        if (drumDeviceID.isBlank ())
        {
            this.updateDrumGeneration (targetID + "|" + selected.generation () + "|unresolved-device");
            return new DrumContextSnapshot (this.drumGeneration, selected.generation (), targetID, "", false, true, baseNote, List.of ());
        }
        final List<DrumPadSnapshot> pads = new ArrayList<> (DRUM_PAD_CAPACITY);
        final StringBuilder identity = new StringBuilder (targetID).append ('|').append (selected.generation ()).append ('|').append (drumDeviceID).append ('|').append (baseNote);
        final int capacity = Math.min (DRUM_PAD_CAPACITY, bank.getPageSize ());
        for (int index = 0; index < capacity && baseNote + index <= 127; index++)
        {
            final int midiNote = baseNote + index;
            final IDrumPad pad = bank.getItem (index);
            final String padChannelID = valueOrEmpty (pad.getChannelID ());
            identity.append ('|').append (padChannelID);
            pads.add (new DrumPadSnapshot (
                index,
                midiNote,
                padChannelID,
                pad.doesExist (),
                pad.getName (),
                toRgb (pad.getColor ()),
                pad.isActivated (),
                pad.hasDevices (),
                pad.isSelected (),
                pad.isMute (),
                pad.isSolo (),
                this.valueChanger.toNormalizedValue (pad.getVolume ()),
                this.valueChanger.toNormalizedValue (pad.getPan ()),
                this.selectedTarget.getPlayingVelocity (midiNote)));
        }
        this.updateDrumGeneration (identity.toString ());
        return new DrumContextSnapshot (this.drumGeneration, selected.generation (), targetID, drumDeviceID, true, true, baseNote, pads);
    }


    private void updateDrumGeneration (final String identity)
    {
        if (identity.equals (this.drumIdentity))
            return;
        this.drumIdentity = identity;
        this.drumGeneration = Math.incrementExact (this.drumGeneration);
    }


    private void requireSelectedTarget (final long generation, final String channelID)
    {
        final SelectedTrackSnapshot selected = this.snapshot.selectedTrack ();
        if (!selected.exists () || selected.generation () != generation || !selected.channelId ().equals (channelID))
            throw new IllegalArgumentException ("Selected-track effect targets stale state");
    }


    private DrumPadSnapshot requireDrumPad (final long generation, final String targetID, final int padIndex)
    {
        final DrumContextSnapshot drum = this.snapshot.drum ();
        if (!drum.available () || !drum.modelAligned () || drum.generation () != generation || !drum.targetChannelId ().equals (targetID))
            throw new IllegalArgumentException ("Drum-pad effect targets a stale window");
        if (padIndex < 0 || padIndex >= drum.pads ().size () || !drum.pads ().get (padIndex).exists ())
            throw new IllegalArgumentException ("Drum-pad effect targets an unavailable pad");
        final DrumPadSnapshot pad = drum.pads ().get (padIndex);
        if (drum.deviceId ().isBlank () || pad.channelId ().isBlank ())
            throw new IllegalArgumentException ("Drum-pad effect targets unresolved host identity");
        return pad;
    }


    private boolean selectedTargetIsCurrent (final long generation, final String channelID)
    {
        return this.selectedTarget.doesExist () && this.selectedTarget.getGeneration () == generation && channelID.equals (this.selectedTarget.getChannelID ());
    }


    @Override
    public boolean controllerMappingContextMatches (final ControllerMappingContext context)
    {
        return context.active () && this.controllerMappings != null &&
            this.selectedTargetIsCurrent (context.targetGeneration (), context.channelId ()) &&
            this.controllerMappings.storage ().matches (context);
    }


    private IDrumPad currentDrumPad (final long generation, final String targetID, final String deviceID, final int baseMidiNote, final int padIndex, final String padChannelID)
    {
        final DrumContextSnapshot drum = this.snapshot.drum ();
        if (!this.selectedTargetIsCurrent (drum.targetGeneration (), targetID) || !this.model.getCursorTrack ().doesExist () || !targetID.equals (this.model.getCursorTrack ().getChannelID ()) || !drum.available () || drum.generation () != generation || !targetID.equals (drum.targetChannelId ()) || padIndex < 0 || padIndex >= drum.pads ().size ())
            return null;
        final IDrumDevice device = this.model.getDrumDevice ();
        if (!device.doesExist () || !device.hasDrumPads () || !deviceID.equals (device.getID ()))
            return null;
        final IDrumPadBank bank = device.getDrumPadBank ();
        if (clampMidiNote (bank.getScrollPosition ()) != baseMidiNote)
            return null;
        final IDrumPad pad = bank.getItem (padIndex);
        return pad.doesExist () && padChannelID.equals (pad.getChannelID ()) ? pad : null;
    }


    private void applyTransportValue (final PreparedTransportValue request)
    {
        if (request.value () == TransportValue.TEMPO)
            this.transport.setTempo (request.amount ());
        else
            this.transport.setPosition (request.amount ());
    }


    private void applySelectedBoolean (final PreparedSelectedBoolean request)
    {
        if (!this.selectedTargetIsCurrent (request.generation (), request.channelID ()))
            return;
        switch (request.property ())
        {
            case ACTIVATED -> this.selectedTarget.setActivated (request.enabled ());
            case GROUP_EXPANDED -> this.selectedTarget.setGroupExpanded (request.enabled ());
            case RECORD_ARMED -> this.selectedTarget.setArmed (request.enabled ());
            case MUTED -> this.selectedTarget.setMuted (request.enabled ());
            case SOLOED -> this.selectedTarget.setSoloed (request.enabled ());
        }
    }


    private void applySelectedMonitor (final PreparedSelectedMonitor request)
    {
        if (this.selectedTargetIsCurrent (request.generation (), request.channelID ()))
            this.selectedTarget.setMonitorMode (SelectedTrackMonitorMode.valueOf (request.mode ().name ()));
    }


    private void applySelectedValue (final PreparedSelectedValue request)
    {
        if (!this.selectedTargetIsCurrent (request.generation (), request.channelID ()))
            return;
        if (request.value () == SelectedTrackValue.VOLUME)
            this.selectedTarget.setVolume (request.amount ());
        else
            this.selectedTarget.setPan (request.amount ());
    }


    private void applySelectedAction (final PreparedSelectedAction request)
    {
        if (!this.selectedTargetIsCurrent (request.generation (), request.channelID ()))
            return;
        switch (request.action ())
        {
            case STOP -> this.selectedTarget.stop ();
            case STOP_IMMEDIATELY -> this.selectedTarget.stopImmediately ();
            case RETURN_TO_ARRANGEMENT -> this.selectedTarget.returnToArrangement ();
            case CREATE_NEW_CLIP ->
            {
                // Clip creation uses the framework cursor. Fail closed while Track Pin or any other
                // cursor state points it away from the private selection-following target.
                if (this.model.getCursorTrack ().doesExist () && request.channelID ().equals (this.model.getCursorTrack ().getChannelID ()))
                {
                    final int lengthInBeats = this.surface.getConfiguration ().getNewClipLenghthInBeats (this.model.getTransport ().getQuartersPerMeasure ());
                    this.newClipAction.execute (lengthInBeats, true);
                }
            }
        }
    }


    private void applyNoteInputMidi (final PreparedNoteInputMidi request)
    {
        this.noteInputMidiSender.handleMidi (request.status (), request.data1 (), request.data2 ());
        this.rememberNoteInputMidiState (request.status (), request.data1 (), request.data2 ());
    }


    private void applyNoteViewPreference (final PreparedNoteViewPreference request)
    {
        final SelectedTrackNoteTargetSnapshot selected = this.selectedTarget.snapshot ();
        if (!this.selectedTargetIsCurrent (request.generation (), request.channelID ()) || selected.position () != request.position ())
            return;
        this.surface.getViewManager ().setPreferredView (request.position (), de.mossgrabers.framework.view.Views.valueOf (request.view ().name ()));
    }


    private void rememberNoteInputMidiState (final int status, final int data1, final int data2)
    {
        final int command = status & 0xF0;
        final MidiStateKey key = new MidiStateKey (status, command == 0xA0 || command == 0xB0 ? data1 : 0);
        final MidiState state = switch (command)
        {
            case 0xA0 -> new MidiState (data1, data2, data1, 0);
            case 0xB0 -> new MidiState (data1, data2, data1, 0);
            case 0xD0 -> new MidiState (data1, data2, 0, 0);
            case 0xE0 -> new MidiState (data1, data2, 0, 64);
            default -> throw new IllegalArgumentException ("Unsupported stateful note-input MIDI status");
        };
        if (state.isNeutral ())
            this.noteInputMidiState.remove (key);
        else
            this.noteInputMidiState.put (key, state);
    }


    private void resetNoteInputMidiState ()
    {
        // A target or note-route change retires receivers and musical state, not physical holds.
        this.surface.cancelGridGestures ();
        this.debugNoteInputCleanup.run ();
        if (this.noteInputMidiState.isEmpty ())
            return;

        try
        {
            for (final Map.Entry<MidiStateKey, MidiState> entry: List.copyOf (this.noteInputMidiState.entrySet ()))
            {
                final MidiState state = entry.getValue ();
                try
                {
                    this.noteInputMidiSender.handleMidi (entry.getKey ().status (), state.neutralData1 (), state.neutralData2 ());
                }
                catch (final RuntimeException failure)
                {
                    this.log.warn ("Neutralizing note-input MIDI state failed: " + failure.getMessage ());
                }
            }
        }
        finally
        {
            this.noteInputMidiState.clear ();
        }
    }


    private void applyDrumBoolean (final PreparedDrumBoolean request)
    {
        final IDrumPad pad = this.currentDrumPad (request.generation (), request.targetID (), request.deviceID (), request.baseMidiNote (), request.padIndex (), request.padChannelID ());
        if (pad == null)
            return;
        if (request.property () == DrumPadBoolean.ACTIVATED)
            pad.setIsActivated (request.enabled ());
        else if (request.property () == DrumPadBoolean.MUTED)
            pad.setMute (request.enabled ());
        else
            pad.setSolo (request.enabled ());
    }


    private void applyDrumBankPosition (final PreparedDrumBankPosition request)
    {
        final SetDrumBankPositionEffect effect = request.effect ();
        final String target = effect.targetChannelId ();
        if (!this.selectedTargetIsCurrent (request.targetGeneration (), target) || !this.model.getCursorTrack ().doesExist () || !target.equals (this.model.getCursorTrack ().getChannelID ()))
            return;
        final IDrumDevice device = this.model.getDrumDevice ();
        if (!device.doesExist () || !device.hasDrumPads () || !request.deviceId ().equals (device.getID ()))
            return;
        final IDrumPadBank bank = device.getDrumPadBank ();
        if (bank.getScrollPosition () != request.previousBase ())
            return;
        final var view = this.surface.getViewManager ().getActive ();
        if (view != null)
            view.getKeyManager ().clearPressedKeys ();
        this.model.getScales ().setDrumOffset (effect.baseMidiNote ());
        bank.scrollTo (effect.baseMidiNote (), effect.adjustPage ());
    }


    private void applyDrumSelection (final PreparedDrumSelection request)
    {
        final IDrumPad pad = this.currentDrumPad (request.generation (), request.targetID (), request.deviceID (), request.baseMidiNote (), request.padIndex (), request.padChannelID ());
        if (pad != null)
            pad.select ();
    }


    private void applyDrumValue (final PreparedDrumValue request)
    {
        final IDrumPad pad = this.currentDrumPad (request.generation (), request.targetID (), request.deviceID (), request.baseMidiNote (), request.padIndex (), request.padChannelID ());
        if (pad == null)
            return;
        if (request.value () == DrumPadValue.VOLUME)
            pad.getVolumeParameter ().setNormalizedValue (request.amount ());
        else
            pad.getPanParameter ().setNormalizedValue (request.amount ());
    }


    private static SelectedTrackSnapshot toApiSnapshot (final SelectedTrackNoteTargetSnapshot selected)
    {
        return new SelectedTrackSnapshot (
            selected.generation (),
            selected.trackID (),
            selected.name (),
            selected.exists () ? Math.max (0, selected.position ()) : -1,
            selected.trackType (),
            selected.exists (),
            selected.group (),
            selected.groupExpanded (),
            selected.canHoldNotes (),
            selected.canHoldAudio (),
            selected.activated (),
            selected.armed (),
            TrackMonitorMode.valueOf (selected.monitorMode ().name ()),
            selected.muted (),
            selected.soloed (),
            selected.mutedBySolo (),
            selected.clipPlaying (),
            selected.volume (),
            selected.pan (),
            toRgb (selected.colorRed (), selected.colorGreen (), selected.colorBlue ()));
    }


    private static RgbColor toRgb (final ColorEx color)
    {
        if (color == null)
            return new RgbColor (0, 0, 0);
        return toRgb (color.getRed (), color.getGreen (), color.getBlue ());
    }


    private static RgbColor toRgb (final double red, final double green, final double blue)
    {
        return new RgbColor (toRgbChannel (red), toRgbChannel (green), toRgbChannel (blue));
    }


    private static int toRgbChannel (final double value)
    {
        if (!Double.isFinite (value))
            return 0;
        return (int) Math.round (Math.max (0, Math.min (1, value)) * 255.0);
    }


    private static boolean elapsedAtLeast (final long now, final long previous, final long interval)
    {
        return previous == Long.MIN_VALUE || now - previous >= interval;
    }


    private static int clampMidiNote (final int note)
    {
        return Math.max (0, Math.min (127, note));
    }


    private static String valueOrEmpty (final String value)
    {
        return value == null ? "" : value;
    }


    private enum NoteRepeatToggle
    {
        FREE_RUNNING,
        USE_PRESSURE,
        SHUFFLE;


        boolean read (final INoteRepeat engine)
        {
            return switch (this)
            {
                case FREE_RUNNING -> engine.isFreeRunning ();
                case USE_PRESSURE -> engine.usePressure ();
                case SHUFFLE -> engine.isShuffle ();
            };
        }


        boolean expected (final DesiredNoteRepeat desired)
        {
            return switch (this)
            {
                case FREE_RUNNING -> desired.freeRunning ();
                case USE_PRESSURE -> desired.usePressure ();
                case SHUFFLE -> desired.shuffle ();
            };
        }


        void toggle (final INoteRepeat engine)
        {
            switch (this)
            {
                case FREE_RUNNING -> engine.toggleIsFreeRunning ();
                case USE_PRESSURE -> engine.toggleUsePressure ();
                case SHUFFLE -> engine.toggleShuffle ();
            }
        }
    }


    private record NoteRepeatLease (boolean latchActive, boolean freeRunning, boolean usePressure, boolean shuffle)
    {
    }


    private record PendingNoteRepeatToggle (NoteRepeatToggle kind, boolean expected)
    {
    }


    private record PreparedTransportState (TransportState state, boolean enabled) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedTransportValue (TransportValue value, double amount) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedParameterSet (ParameterTargetHost.PreparedSet action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedParameterAdjust (ParameterTargetHost.PreparedAdjust action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedParameterNormalized (ParameterTargetHost.PreparedNormalized action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedParameterEnabled (ParameterTargetHost.PreparedEnabled action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedParameterTouch (ParameterTargetHost.PreparedTouch action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedParameterReset (ParameterTargetHost.PreparedReset action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedControllerMappingStorage (SetControllerMappingStorageEffect effect) implements ControllerBridge.PreparedAction
    {
        // Immutable request; live selection and storage are checked at application time.
    }


    private record PreparedSelectedBoolean (long generation, String channelID, SelectedTrackBoolean property, boolean enabled) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedSelectedMonitor (long generation, String channelID, TrackMonitorMode mode) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedSelectedValue (long generation, String channelID, SelectedTrackValue value, double amount) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedSelectedAction (long generation, String channelID, SelectedTrackAction action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedControllerButtonConsumption (ButtonID button) implements ControllerBridge.PreparedAction
    {
    }


    private static Map<ControlId, ButtonID> consumableButtons ()
    {
        final Map<ControlId, ButtonID> buttons = new LinkedHashMap<> ();
        buttons.put (PushControlIds.button (ButtonID.SELECT.name ()), ButtonID.SELECT);
        buttons.put (PushControlIds.button (ButtonID.BROWSE.name ()), ButtonID.BROWSE);
        buttons.put (PushControlIds.button (ButtonID.STOP_CLIP.name ()), ButtonID.STOP_CLIP);
        buttons.put (PushControlIds.button (ButtonID.DELETE.name ()), ButtonID.DELETE);
        buttons.put (PushControlIds.button (ButtonID.DUPLICATE.name ()), ButtonID.DUPLICATE);
        buttons.put (PushControlIds.button (ButtonID.RECORD.name ()), ButtonID.RECORD);
        for (int index = 0; index < 8; index++)
        {
            final ButtonID button = ButtonID.get (ButtonID.ROW1_1, index);
            buttons.put (PushControlIds.button (button.name ()), button);
        }
        return Map.copyOf (buttons);
    }


    private record PreparedNoteInputMidi (int status, int data1, int data2) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedNoteViewPreference (long generation, String channelID, int position, ControllerNoteView view) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedDrumBoolean (long generation, String targetID, String deviceID, int baseMidiNote, int padIndex, String padChannelID, DrumPadBoolean property, boolean enabled) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedDrumBankPosition (SetDrumBankPositionEffect effect, long targetGeneration, String deviceId, int previousBase) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedHostNotification (String text) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedDrumSelection (long generation, String targetID, String deviceID, int baseMidiNote, int padIndex, String padChannelID) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedDrumValue (long generation, String targetID, String deviceID, int baseMidiNote, int padIndex, String padChannelID, DrumPadValue value, double amount) implements ControllerBridge.PreparedAction
    {
    }


    private record MidiStateKey (int status, int controller)
    {
    }


    private record MidiState (int data1, int data2, int neutralData1, int neutralData2)
    {
        private boolean isNeutral ()
        {
            return this.data1 == this.neutralData1 && this.data2 == this.neutralData2;
        }
    }

}
