// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.ui.page.GlobalMixerPageRenderer;
import de.mossgrabers.pull.core.ui.page.PageVisuals;


import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.AcquireParameterTouchEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterEnabledEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Eight current-bank Volume/Pan/Send encoders, touches, global mixer menu, and parameter display. */
public final class GlobalMixerControlsView implements ControllerView
{
    public enum Role { VOLUME, PAN, SEND }
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final Set<ControllerActionBinding> ACTIONS = IntStream.range (0, 8).mapToObj (index -> new ControllerActionBinding (upper (index), InputKind.BUTTON, ControllerActionId.SELECT_PARAMETER_PAGE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS))).collect (Collectors.toUnmodifiableSet ());
    private final Role role;
    private final int sendIndex;
    private final ParameterBankId bank;
    private final List<AuthoritativeBooleanToggle<ParameterTargetRef>> enabled = IntStream.range (0, 8).mapToObj (ignored -> new AuthoritativeBooleanToggle<ParameterTargetRef> ()).toList ();
    private final Map<ControlId, ParameterSlot> bindings;
    private final ViewProfile profile;
    private final PageNavigation pages;

    public GlobalMixerControlsView (final Role role, final PageNavigation pages)
    {
        this (role, -1, pages);
    }

    public static GlobalMixerControlsView send (final int sendIndex, final PageNavigation pages)
    {
        return new GlobalMixerControlsView (Role.SEND, sendIndex, pages);
    }

    private GlobalMixerControlsView (final Role role, final int sendIndex, final PageNavigation pages)
    {
        this.role = Objects.requireNonNull (role, "role");
        this.pages = Objects.requireNonNull (pages, "pages");
        this.sendIndex = sendIndex;
        this.bank = role == Role.SEND ? ParameterBankId.trackSend (sendIndex) : role == Role.VOLUME ? ParameterBankId.TRACK_VOLUME : ParameterBankId.TRACK_PAN;
        final Map<ControlId, ParameterSlot> slots = new LinkedHashMap<> ();
        for (int index = 0; index < 8; index++) slots.put (PushControlIds.continuous ("KNOB" + (index + 1)), this.slot (index));
        this.bindings = Map.copyOf (slots);
        this.profile = ViewProfile.fixed ("current-track-" + this.roleId ().toLowerCase (java.util.Locale.ROOT), Set.of (
            new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.SELECT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.DELETE_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    }

    @Override public String id () { return "global-mixer-" + this.roleId ().toLowerCase (java.util.Locale.ROOT); }
    private String roleId () { return this.role == Role.SEND ? "SEND" + (this.sendIndex + 1) : this.role.name (); }
    @Override public ViewProfile profile () { return this.profile; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.AUTOMATION, BridgeSubscription.CURRENT_TRACK_BANK, BridgeSubscription.ENCODER_CONFIGURATION, BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.CONTROLLER_LAYOUT); }
    @Override public Set<ParameterBankId> parameterBanks () { return Set.of (this.bank); }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return this.bindings; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot)
    {
        return this.bindings.entrySet ().stream ().filter (entry -> this.alignedTarget (snapshot, entry.getValue ().index ()) != null).collect (Collectors.toUnmodifiableMap (Map.Entry::getKey, Map.Entry::getValue));
    }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.enabled.stream ().anyMatch (AuthoritativeBooleanToggle::pending)); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.reconcile (snapshot); }
    @Override public void reconcile (final ControllerSnapshot snapshot)
    {
    }
    @Override public void deactivate () { this.enabled.forEach (AuthoritativeBooleanToggle::clear); }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        this.advanceEnabled (snapshot, effects);
        if (event instanceof final ControllerInputEvent input)
        {
            final ParameterSlot slot = this.bindings.get (input.controlId ());
            if (slot == null) return List.copyOf (effects);
            final ParameterTargetSnapshot target = this.alignedTarget (snapshot, slot.index ());
            if (input.kind () == InputKind.RELATIVE && target != null)
                effects.addAll (TrackEncoderResponse.adjust (this.role == Role.VOLUME ? TrackEncoderResponse.Role.VOLUME : this.role == Role.PAN ? TrackEncoderResponse.Role.PAN : TrackEncoderResponse.Role.RANGED, target, input.value (), snapshot.pressedControls ().contains (SHIFT), snapshot.bridge ().encoderConfiguration ()));
            else if (input.kind () == InputKind.TOUCH)
            {
                if (input.phase () == InputPhase.BEGIN && target == null && snapshot.bridge ().parameters ().slots ().containsKey (slot)) return List.copyOf (effects);
                final boolean begin = input.phase () == InputPhase.BEGIN;
                if (begin && this.role == Role.SEND && snapshot.pressedControls ().containsAll (Set.of (SHIFT, SELECT)))
                {
                    // Preserve inherited cumulative ordering: Delete reset, touch, then enabled.
                    if (target != null) effects.add (new AcquireParameterTouchEffect (input.controlId (), target.target ()));
                    effects.add (new ConsumeControllerButtonEffect (SELECT));
                    if (target != null && target.enabled ().isPresent ())
                        effects.addAll (this.enabled.get (slot.index ()).update (target.target (), target.enabled ().orElseThrow ().booleanValue (), snapshot.monotonicTimeNanos (), true, (key, enabled) -> new SetParameterEnabledEffect (key, enabled.booleanValue ())));
                }
            }
        }
        else effects.addAll (GlobalMixerMenu.normalize (snapshot.bridge ().controllerSettings ()));
        return List.copyOf (effects);
    }

    private void advanceEnabled (final ControllerSnapshot snapshot, final List<CoreEffect> effects)
    {
        if (this.role != Role.SEND) return;
        for (int index = 0; index < 8; index++)
        {
            final ParameterTargetSnapshot target = this.alignedTarget (snapshot, index);
            if (target == null || target.enabled ().isEmpty ()) this.enabled.get (index).clear ();
            else effects.addAll (this.enabled.get (index).update (target.target (), target.enabled ().orElseThrow ().booleanValue (), snapshot.monotonicTimeNanos (), false, (key, enabled) -> new SetParameterEnabledEffect (key, enabled.booleanValue ())));
        }
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final int column = IntStream.range (0, 8).filter (index -> upper (index).equals (input.controlId ())).findFirst ().orElse (-1);
        final PageNavigation.Origin origin = this.pages.origin ();
        final GlobalMixerMenu.Selection selection = GlobalMixerMenu.select (column, snapshot.bridge ().controllerSettings ());
        return ResolvedControllerAction.of (binding.intent (), () -> {
            if (!this.pages.matches (origin)) return List.of ();
            if (!selection.destination ().isEmpty ())
                this.pages.select (origin, this.pages.resolve (selection.destination ()));
            return selection.effects ();
        });
    }

    @Override
    public de.mossgrabers.pull.core.view.InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.TOUCH)
        {
            final ParameterSlot slot = this.bindings.get (control);
            if (slot != null && snapshot.bridge ().parameters ().slots ().containsKey (slot) && this.alignedTarget (snapshot, slot.index ()) == null) return null;
        }
        return ControllerView.super.inputTarget (control, kind, snapshot);
    }


    @Override
    public Set<ControlId> parameterTouchControls (final ControllerSnapshot snapshot)
    {
        return SurfaceArea.ENCODER_TOUCHES.controls ();
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final List<GlobalMixerMenu.Entry> menu = GlobalMixerMenu.entries (snapshot.bridge ().controllerSettings (), this.pages.legacyAlias ());
        final PageVisuals visuals = GlobalMixerPageRenderer.render (MixerPageProjections.global (snapshot, this.role, this.sendIndex, menu));
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredNotePerformance.inactive (), DesiredNoteRepeat.unowned (), DesiredControllerMappings.empty ());
    }


    private ParameterSlot slot (final int index) { return new ParameterSlot (this.bank, index); }
    private static ControlId upper (final int index) { return PushControlIds.button ("ROW2_" + (index + 1)); }

    private ParameterTargetSnapshot alignedTarget (final ControllerSnapshot snapshot, final int index) { return alignedTarget (snapshot, this.role, this.sendIndex, index); }

    static ParameterTargetSnapshot alignedTarget (final ControllerSnapshot snapshot, final Role role, final int sendIndex, final int index)
    {
        final List<CurrentTrackSnapshot> tracks = snapshot.bridge ().currentTrackBank ().tracks ();
        if (index >= tracks.size () || !tracks.get (index).track ().exists ()) return null;
        final ParameterSlot slot = role == Role.SEND ? ParameterSlot.trackSend (sendIndex, index) : role == Role.VOLUME ? ParameterSlot.trackVolume (index) : ParameterSlot.trackPan (index);
        final ParameterTargetSnapshot target = snapshot.bridge ().parameters ().slots ().get (slot);
        if (target == null) return null;
        return ParameterAlignment.matches (target, role == Role.VOLUME ? "channel-volume" : role == Role.PAN ? "channel-pan" : "channel-send", tracks.get (index).track ().channelId ()) ? target : null;
    }
}
