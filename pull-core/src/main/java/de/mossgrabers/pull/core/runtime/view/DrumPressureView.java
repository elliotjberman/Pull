// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;


/**
 * Reloadable pressure policy for the playable drum block in VS Live.
 */
public final class DrumPressureView implements ControllerView
{
    private static final int PLAY_COLUMNS = 4;
    private static final int PLAY_ROWS = 4;
    private static final int GRID_COLUMNS = 8;
    private static final int MIDI_POLY_PRESSURE = 0xA0;
    private static final int MIDI_CC = 0xB0;
    private static final int MIDI_CHANNEL_PRESSURE = 0xD0;
    private static final Set<SurfaceClaim> CLAIMS = Set.of (
        new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.GRID_CHANNEL_PRESSURE, SurfaceClaim.Kind.OBSERVE_INPUT));
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.CONTROLLER_LAYOUT);

    private final BooleanSupplier workspaceActive;


    /**
     * Constructor.
     *
     * @param workspaceActive Whether VS Live currently owns the composed surface
     */
    public DrumPressureView (final BooleanSupplier workspaceActive)
    {
        this.workspaceActive = Objects.requireNonNull (workspaceActive, "workspaceActive");
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "drum-pressure";
    }


    /** {@inheritDoc} */
    @Override
    public Set<SurfaceClaim> claims ()
    {
        return CLAIMS;
    }


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!this.workspaceActive.getAsBoolean () || !(event instanceof final ControllerInputEvent input))
            return List.of ();

        final ControllerLayoutSnapshot layout = snapshot.bridge ().layout ();
        if (!layout.drumLayoutActive () || !layout.drumControllerEngaged ())
            return List.of ();

        if (input.kind () == InputKind.POLY_PRESSURE)
            return this.polyPressureEffects (input, layout);
        if (input.kind () == InputKind.CHANNEL_PRESSURE)
            return this.channelPressureEffects ((int) input.value (), snapshot, layout);
        return List.of ();
    }


    private List<CoreEffect> polyPressureEffects (final ControllerInputEvent input, final ControllerLayoutSnapshot layout)
    {
        final int padIndex = playPadIndex (input.controlId ());
        if (padIndex < 0)
            return List.of ();
        return pressureEffects (layout.gridPressure (), layout.drumBaseMidiNote () + padIndex, (int) input.value ());
    }


    private List<CoreEffect> channelPressureEffects (final int value, final ControllerSnapshot snapshot, final ControllerLayoutSnapshot layout)
    {
        if (layout.gridPressure ().mode () != GridPressureConfiguration.Mode.POLY_AFTERTOUCH)
            return pressureEffects (layout.gridPressure (), -1, value);

        final List<CoreEffect> effects = new ArrayList<> ();
        for (int padIndex = 0; padIndex < PLAY_COLUMNS * PLAY_ROWS; padIndex++)
        {
            if (snapshot.pressedControls ().contains (playPadControl (padIndex)))
                effects.addAll (pressureEffects (layout.gridPressure (), layout.drumBaseMidiNote () + padIndex, value));
        }
        return List.copyOf (effects);
    }


    private static List<CoreEffect> pressureEffects (final GridPressureConfiguration configuration, final int midiNote, final int value)
    {
        return switch (configuration.mode ())
        {
            case OFF -> List.of ();
            case POLY_AFTERTOUCH -> midiNote < 0 || midiNote > 127 ? List.of () : List.of (new SendNoteInputMidiEffect (MIDI_POLY_PRESSURE, midiNote, value));
            case CHANNEL_AFTERTOUCH -> List.of (new SendNoteInputMidiEffect (MIDI_CHANNEL_PRESSURE, value, 0));
            case CONTROL_CHANGE -> List.of (new SendNoteInputMidiEffect (MIDI_CC, configuration.controller (), value));
        };
    }


    private static int playPadIndex (final ControlId control)
    {
        for (int padIndex = 0; padIndex < PLAY_COLUMNS * PLAY_ROWS; padIndex++)
        {
            if (playPadControl (padIndex).equals (control))
                return padIndex;
        }
        return -1;
    }


    private static ControlId playPadControl (final int padIndex)
    {
        final int row = padIndex / PLAY_COLUMNS;
        final int column = padIndex % PLAY_COLUMNS;
        return PushControlIds.pad (row * GRID_COLUMNS + column + 1);
    }
}
