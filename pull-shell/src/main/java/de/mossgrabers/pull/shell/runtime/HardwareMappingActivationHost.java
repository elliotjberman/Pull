// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/** Gates each original Bitwig-learnable action while raw MIDI preserves ordinary dispatch. */
final class HardwareMappingActivationHost
{
    private final Map<ControlId, IHwButton> mappingButtons;
    private final Predicate<ControlId> lifecycleIdle;
    private final Map<ControlId, LaneState> states = new LinkedHashMap<> ();


    HardwareMappingActivationHost (final Map<ControlId, IHwButton> mappingButtons, final Predicate<ControlId> lifecycleIdle)
    {
        this.mappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        this.lifecycleIdle = Objects.requireNonNull (lifecycleIdle, "lifecycleIdle");
        this.mappingButtons.forEach ( (control, button) -> {
            button.unbindRelease ();
            this.states.put (control, LaneState.MAPPING);
        });
    }


    /** Replace the complete desired mapping lane set. */
    void request (final Set<ControlId> controls)
    {
        final Set<ControlId> requested = Set.copyOf (Objects.requireNonNull (controls, "controls"));
        if (!this.mappingButtons.keySet ().containsAll (requested))
            throw new IllegalArgumentException ("Requested hardware mapping is not installed");

        for (final ControlId control: this.mappingButtons.keySet ())
        {
            final boolean mappingDesired = requested.contains (control);
            switch (this.states.get (control))
            {
                case MAPPING:
                    if (!mappingDesired)
                        this.leaveMapping (control);
                    break;
                case DISPATCH:
                    if (mappingDesired)
                        this.leaveDispatch (control);
                    break;
                case RELEASING_MAPPING, RELEASING_DISPATCH:
                    this.finishRelease (control, mappingDesired);
                    break;
            }
        }
    }


    /** Get original Bitwig-learnable actions currently admitting new note-on presses. */
    Set<ControlId> activeMappings ()
    {
        return this.states.entrySet ().stream ()
            .filter (entry -> entry.getValue () == LaneState.MAPPING)
            .map (Map.Entry::getKey)
            .collect (java.util.stream.Collectors.toUnmodifiableSet ());
    }


    /** Whether raw MIDI must complete ordinary dispatch without exposing another HardwareButton. */
    boolean dispatchRaw (final ControlId control, final ButtonEvent event, final double velocity)
    {
        final LaneState state = this.states.get (Objects.requireNonNull (control, "control"));
        if (state == null)
            throw new IllegalArgumentException ("Hardware mapping is not installed");
        final boolean dispatch = switch (state)
        {
            case DISPATCH -> event == ButtonEvent.DOWN || !this.lifecycleIdle.test (control);
            case MAPPING, RELEASING_MAPPING, RELEASING_DISPATCH -> event == ButtonEvent.UP && !this.lifecycleIdle.test (control);
        };
        if (dispatch)
            this.mappingButtons.get (control).trigger (event, velocity);
        return dispatch;
    }


    private void leaveMapping (final ControlId control)
    {
        this.mappingButtons.get (control).unbindPress ();
        if (this.lifecycleIdle.test (control))
        {
            this.states.put (control, LaneState.DISPATCH);
            return;
        }

        this.states.put (control, LaneState.RELEASING_MAPPING);
    }


    private void leaveDispatch (final ControlId control)
    {
        if (this.lifecycleIdle.test (control))
        {
            this.activateMapping (control);
            this.states.put (control, LaneState.MAPPING);
            return;
        }

        this.states.put (control, LaneState.RELEASING_DISPATCH);
    }


    private void finishRelease (final ControlId control, final boolean mappingDesired)
    {
        if (!this.lifecycleIdle.test (control))
            return;

        if (mappingDesired)
            this.activateMapping (control);
        this.states.put (control, mappingDesired ? LaneState.MAPPING : LaneState.DISPATCH);
    }


    private void activateMapping (final ControlId control)
    {
        final IHwButton button = this.mappingButtons.get (control);
        button.rebind ();
        button.unbindRelease ();
    }


    private enum LaneState
    {
        MAPPING,
        DISPATCH,
        RELEASING_MAPPING,
        RELEASING_DISPATCH
    }
}
