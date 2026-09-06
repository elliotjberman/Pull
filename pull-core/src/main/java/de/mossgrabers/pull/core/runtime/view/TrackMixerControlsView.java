// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.ui.page.TrackMixerPageRenderer;
import de.mossgrabers.pull.core.ui.page.PageVisuals;


import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.AcquireParameterTouchEffect;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterEnabledEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Core-owned Track subpages, encoder semantics, upper menus, and parameter display. */
public final class TrackMixerControlsView implements ControllerView
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = createParameterBindings (0);
    private static final ViewProfile PROFILE = ViewProfile.fixed ("selected-track-mix", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (
        pageAction (0), pageAction (1), pageAction (6), pageAction (7));

    private final TrackMixerPageState page;
    private final ParameterTouchSession touchSession;
    private final ParameterTouchControls touches;
    private final boolean normalResponse;
    private final List<AuthoritativeBooleanToggle<ParameterTargetRef>> sendEnabled = java.util.stream.IntStream.range (0, ParameterSlot.BANK_SIZE).mapToObj (ignored -> new AuthoritativeBooleanToggle<ParameterTargetRef> ()).toList ();
    private long pageRevision;

    public TrackMixerControlsView ()
    {
        this (new TrackMixerPageState (), new ParameterTouchSession (), false);
    }

    public TrackMixerControlsView (final TrackMixerPageState page, final ParameterTouchSession touchSession, final boolean normalResponse)
    {
        this.page = Objects.requireNonNull (page, "page");
        this.touchSession = Objects.requireNonNull (touchSession, "touchSession");
        this.touches = new ParameterTouchControls (touchSession);
        this.normalResponse = normalResponse;
    }

    @Override public String id () { return "track-mixer-controls"; }


    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.SELECTED_TRACK, BridgeSubscription.AUTOMATION, BridgeSubscription.ENCODER_CONFIGURATION, BridgeSubscription.CURRENT_TRACK_BANK);
    }
    @Override public Set<ParameterBankId> parameterBanks () { return Set.of (ParameterBankId.SELECTED_TRACK, ParameterBankId.SELECTED_TRACK_SENDS); }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return PARAMETER_BINDINGS; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot)
    {
        return this.page.inputOutputSelected () ? Map.of () : ParameterAlignment.bindings (snapshot, createParameterBindings (this.page.sendOffset ()));
    }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.sendEnabled.stream ().anyMatch (AuthoritativeBooleanToggle::pending)); }

    @Override
    public void start (final ControllerSnapshot snapshot)
    {
        this.deactivate ();
        this.pageRevision = this.page.revision ();
        this.reconcile (snapshot);
    }

    @Override
    public void deactivate ()
    {
        this.touches.clear ();
        this.sendEnabled.forEach (AuthoritativeBooleanToggle::clear);
    }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        // Wait for an aligned named bank before reconciling restored send-page state.
        if (ParameterAlignment.target (snapshot, ParameterSlot.SELECTED_TRACK_VOLUME) != null && !hasAdditionalSends (snapshot))
            this.page.selectSendOffset (0);
        if (this.pageRevision != this.page.revision ())
        {
            this.deactivate ();
            this.pageRevision = this.page.revision ();
        }
        this.touches.reconcile (snapshot);
        this.touches.retainTargets (ParameterAlignment.references (snapshot));
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            if (ParameterAlignment.target (snapshot, ParameterSlot.selectedTrackSend (index)) == null)
                this.sendEnabled.get (index).clear ();
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        this.advanceEnabled (snapshot, effects);
        if (!(event instanceof final ControllerInputEvent input))
            return List.copyOf (effects);
        final ParameterSlot slot = this.page.inputOutputSelected () ? null : createParameterBindings (this.page.sendOffset ()).get (input.controlId ());
        final ParameterTargetSnapshot target = ParameterAlignment.target (snapshot, slot);
        if (input.kind () == InputKind.RELATIVE)
        {
            if (target != null)
                effects.addAll (this.normalResponse ? TrackEncoderResponse.adjust (slot, target, input.value (), snapshot.pressedControls ().contains (SHIFT), snapshot.bridge ().encoderConfiguration ()) : List.of (new AdjustParameterValueEffect (target.target (), input.value () * 10)));
        }
        else if (input.kind () == InputKind.TOUCH)
        {
            if (input.phase () == InputPhase.BEGIN && ParameterAlignment.contradicts (snapshot, slot))
                return List.copyOf (effects);
            final boolean begin = input.phase () == InputPhase.BEGIN && !this.touchSession.contains (input.controlId ());
            effects.addAll (this.touches.handle (input, target, snapshot));
            if (begin && snapshot.pressedControls ().containsAll (Set.of (SHIFT, SELECT)) && slot != null && slot.bank () == ParameterBankId.SELECTED_TRACK_SENDS)
            {
                if (target != null && target.enabled ().isPresent ())
                {
                    // Explicit acquisition preserves reset -> touch -> send enabled ordering.
                    effects.add (new AcquireParameterTouchEffect (input.controlId (), target.target ()));
                    effects.add (new ConsumeControllerButtonEffect (SELECT));
                    effects.addAll (this.sendEnabled.get (slot.index ()).update (target.target (), target.enabled ().orElseThrow ().booleanValue (), snapshot.monotonicTimeNanos (), true, (key, enabled) -> new SetParameterEnabledEffect (key, enabled.booleanValue ())));
                }
                else if (snapshot.bridge ().currentTrackBank ().tracks ().stream ().anyMatch (track -> track.track ().exists () && track.track ().selected () && track.track ().channelId ().equals (snapshot.bridge ().selectedTrack ().channelId ())))
                    effects.add (new ConsumeControllerButtonEffect (SELECT));
            }
        }
        return List.copyOf (effects);
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final int column = upperColumn (input.controlId ());
        final boolean canLeft = !this.page.inputOutputSelected () && this.page.sendOffset () > 0;
        final boolean canRight = !this.page.inputOutputSelected () && this.page.sendOffset () == 0 && hasAdditionalSends (snapshot);
        return ResolvedControllerAction.of (binding.intent (), () -> {
            if (column == 0 || column == 1)
                this.page.selectInputOutput (column == 1);
            else if (column == 6 && canLeft)
                this.page.selectSendOffset (0);
            else if (column == 7 && canRight)
                this.page.selectSendOffset (4);
            this.reconcile (snapshot);
            return List.of ();
        });
    }

    @Override
    public de.mossgrabers.pull.core.api.DesiredParameterTouches parameterTouches (final ControllerSnapshot snapshot)
    {
        return this.touches.desired ();
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final PageVisuals visuals = TrackMixerPageRenderer.render (MixerPageProjections.track (snapshot, this.page, this.normalResponse));
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredNotePerformance.inactive (), DesiredNoteRepeat.unowned (), DesiredControllerMappings.empty ());
    }


    private void advanceEnabled (final ControllerSnapshot snapshot, final List<CoreEffect> effects)
    {
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final ParameterTargetSnapshot target = ParameterAlignment.target (snapshot, ParameterSlot.selectedTrackSend (index));
            final AuthoritativeBooleanToggle<ParameterTargetRef> lane = this.sendEnabled.get (index);
            if (target == null || target.enabled ().isEmpty ())
                lane.clear ();
            else
                effects.addAll (lane.update (target.target (), target.enabled ().orElseThrow ().booleanValue (), snapshot.monotonicTimeNanos (), false, (key, enabled) -> new SetParameterEnabledEffect (key, enabled.booleanValue ())));
        }
    }

    static boolean hasAdditionalSends (final ControllerSnapshot snapshot)
    {
        return ParameterAlignment.target (snapshot, ParameterSlot.selectedTrackSend (6)) != null;
    }

    private static Map<ControlId, ParameterSlot> createParameterBindings (final int offset)
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        bindings.put (PushControlIds.continuous ("KNOB1"), ParameterSlot.SELECTED_TRACK_VOLUME);
        bindings.put (PushControlIds.continuous ("KNOB2"), ParameterSlot.SELECTED_TRACK_PAN);
        for (int index = 0; index < 6 && offset + index < ParameterSlot.BANK_SIZE; index++)
            bindings.put (PushControlIds.continuous ("KNOB" + (index + 3)), ParameterSlot.selectedTrackSend (offset + index));
        return Map.copyOf (bindings);
    }

    private static ControlId upper (final int index) { return PushControlIds.button ("ROW2_" + (index + 1)); }
    private static int upperColumn (final ControlId control)
    {
        for (int index = 0; index < 8; index++)
            if (upper (index).equals (control)) return index;
        return -1;
    }
    private static ControllerActionBinding pageAction (final int index)
    {
        return new ControllerActionBinding (upper (index), InputKind.BUTTON, ControllerActionId.SELECT_PARAMETER_PAGE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS));
    }
}
