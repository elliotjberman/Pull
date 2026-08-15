// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
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
import de.mossgrabers.pull.core.view.ViewFacet;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Complete lower-half Drum Controller behavior with a fixed optional pitch-bend facet.
 */
public final class DrumControllerView implements ControllerView
{
    /** Pitch-bend facet identifier. */
    public static final String PITCH_BEND = "pitch-bend";

    private static final int PLAY_COLUMNS = 4;
    private static final int PLAY_ROWS = 4;
    private static final int GRID_COLUMNS = 8;
    private static final int MIDI_POLY_PRESSURE = 0xA0;
    private static final int MIDI_CC = 0xB0;
    private static final int MIDI_CHANNEL_PRESSURE = 0xD0;
    private static final ViewFacet PITCH_BEND_FACET = new ViewFacet (
        PITCH_BEND,
        Set.of (
            new SurfaceClaim (SurfaceArea.TOUCH_STRIP, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.TOUCH_STRIP, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.DRUM_PITCH_BEND));

    private final DrumFillView fillView = new DrumFillView ();
    private final ViewProfile profile;


    /**
     * Constructor.
     *
     * @param pitchBendEnabled Whether the fixed touch-strip facet is selected
     */
    public DrumControllerView (final boolean pitchBendEnabled)
    {
        this.profile = new ViewProfile (
            "lower",
            requiredClaims (this.fillView),
            requiredControllerFacets (this.fillView),
            Map.of (PITCH_BEND, PITCH_BEND_FACET),
            pitchBendEnabled ? Set.of (PITCH_BEND) : Set.of ());

    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "drum-controller";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return this.profile;
    }


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> (this.fillView.bridgeSubscriptions ());
        subscriptions.addAll (Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.SELECTED_TRACK, BridgeSubscription.NOTE_VIEW));
        return Set.copyOf (subscriptions);
    }


    /** {@inheritDoc} */
    @Override
    public void start (final ControllerSnapshot snapshot)
    {
        this.fillView.start (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.fillView.reconcile (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> (this.fillView.handle (event, snapshot));
        effects.addAll (this.pressureEffects (event, snapshot));
        return List.copyOf (effects);
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ViewOutput fill = this.fillView.render (snapshot);
        final ResolvedNoteViewer resolved = ResolvedNoteViewer.resolveCompositeDrum (snapshot);
        return new ViewOutput (
            fill.lights (),
            fill.clipBindings (),
            fill.display (),
            fill.padGridOverlay (),
            fill.displayOverlay (),
            new DesiredNotePerformance (DesiredControllerLayout.empty (), resolved.noteInputRoute ()),
            fill.noteRepeat ());
    }


    private List<CoreEffect> pressureEffects (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input))
            return List.of ();

        final ControllerLayoutSnapshot layout = snapshot.bridge ().layout ();
        if (!layout.drumLayoutActive () || !layout.drumControllerEngaged ())
            return List.of ();

        if (input.kind () == InputKind.POLY_PRESSURE)
        {
            final int padIndex = playPadIndex (input.controlId ());
            if (padIndex < 0)
                return List.of ();
            return pressureEffects (layout.gridPressure (), layout.drumBaseMidiNote () + padIndex, (int) input.value ());
        }
        if (input.kind () == InputKind.CHANNEL_PRESSURE)
            return channelPressureEffects ((int) input.value (), snapshot, layout);
        return List.of ();
    }


    private static List<CoreEffect> channelPressureEffects (final int value, final ControllerSnapshot snapshot, final ControllerLayoutSnapshot layout)
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


    private static Set<SurfaceClaim> requiredClaims (final DrumFillView fillView)
    {
        final Set<SurfaceClaim> claims = new LinkedHashSet<> (fillView.claims ());
        claims.addAll (Set.of (
            new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT),
            new SurfaceClaim (SurfaceArea.GRID_CHANNEL_PRESSURE, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_OCTAVE, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_OCTAVE, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)));
        return Set.copyOf (claims);
    }


    private static Set<ControllerViewFacet> requiredControllerFacets (final DrumFillView fillView)
    {
        final Set<ControllerViewFacet> facets = new LinkedHashSet<> (fillView.profile ().controllerFacets ());
        facets.add (ControllerViewFacet.DRUM_CONTROLLER_LOWER);
        return Set.copyOf (facets);
    }
}
