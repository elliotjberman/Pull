// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.*;
import de.mossgrabers.controller.ableton.push.mode.track.ClipMode;
import de.mossgrabers.framework.daw.*;
import de.mossgrabers.framework.daw.clip.*;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Bounded active-page read-back; submitted note edits remain separate from host observations. */
final class PushEditingPageObserver
{
    EditingPageState capture (final PushControlSurface surface, final IModel model)
    {
        final var mode = surface.getModeManager ().getActive ();
        if (mode instanceof final ClipMode clipMode) return this.clip (surface, model, clipMode);
        if (mode instanceof final NoteMode noteMode) return note (surface, model, noteMode);
        if (mode instanceof QuantizeMode)
        {
            final var track = model.getCursorTrack ();
            return new EditingPageState.Quantize (track.doesExist (), track.getRecordQuantizationGrid ().ordinal (), track.isRecordQuantizationNoteLength (), surface.getConfiguration ().getQuantizeAmount ());
        }
        if (mode instanceof final GrooveMode grooveMode)
        {
            final var groove = model.getGroove ();
            final List<EditingPageState.Parameter> parameters = new ArrayList<> ();
            for (final GrooveParameterID id: List.of (GrooveParameterID.SHUFFLE_AMOUNT, GrooveParameterID.SHUFFLE_RATE, GrooveParameterID.ACCENT_AMOUNT, GrooveParameterID.ACCENT_PHASE, GrooveParameterID.ACCENT_RATE))
            {
                final IParameter parameter = groove.getParameter (id);
                final int index = switch (id) { case SHUFFLE_AMOUNT -> 2; case SHUFFLE_RATE -> 3; case ACCENT_AMOUNT -> 5; case ACCENT_PHASE -> 6; default -> 7; };
                parameters.add (new EditingPageState.Parameter (parameter != null && parameter.doesExist (), parameter == null ? "" : text (parameter.getName ()), parameter == null ? 0 : normalized (model, parameter.getValue ()), parameter == null ? "" : text (parameter.getDisplayedValue ()), grooveMode.isKnobTouched (index)));
            }
            final IParameter enabled = groove.getParameter (GrooveParameterID.ENABLED);
            return new EditingPageState.Groove (enabled != null && enabled.getValue () > 0, parameters);
        }
        return EditingPageState.empty ();
    }

    private EditingPageState clip (final PushControlSurface surface, final IModel model, final ClipMode mode)
    {
        final IClip clip = mode.getObservedClip ();
        final List<EditingPageState.Track> tracks = new ArrayList<> ();
        if (!mode.isDisplayingMidiNotes ())
            for (int index = 0; index < 8; index++)
            {
                final var track = model.getCurrentTrackBank ().getItem (index);
                tracks.add (new EditingPageState.Track (track.doesExist (), text (track.getName ()), track.getType ().name (), SessionBankHost.toRgb (track.getColor ()), track.isSelected (), track.isActivated (), track.isGroupExpanded (), track.isSelected () && model.getCursorTrack ().isPinned ()));
            }
        return new EditingPageState.Clip (clip.doesExist (), clip instanceof final INoteClip notes && notes.isPinned (), clip.getPlayStart (), clip.getPlayEnd (), clip.getLoopStart (), clip.getLoopLength (), clip.isLoopEnabled (), clip.isShuffleEnabled (), clip.getAccent (), model.getTransport ().getQuartersPerMeasure (), tracks, touched (mode), mode.isDisplayingMidiNotes ());
    }

    private static EditingPageState note (final PushControlSurface surface, final IModel model, final NoteMode mode)
    {
        final var editor = mode.getNoteEditor ();
        final var positions = editor.getNotes ();
        if (positions.isEmpty () || editor.getClip () == null)
            return new EditingPageState.Note (false, mode.getObservedPage (), 0, 0, 0, model.getTransport ().getQuartersPerMeasure (), 96, surface.isShiftPressed (), touched (mode), EditingPageState.NoteData.empty ());
        final var position = positions.get (0);
        final var clip = editor.getClip ();
        final IStepInfo step = clip.getObservedStep (position);
        return new EditingPageState.Note (step.getState () != StepState.OFF, mode.getObservedPage (), positions.size (), position.getStep (), position.getNote (), model.getTransport ().getQuartersPerMeasure (), clip.getStepTransposeRange (), surface.isShiftPressed (), touched (mode),
            new EditingPageState.NoteData (step.getDuration (), step.isMuted (), step.getVelocity (), step.getVelocitySpread (), step.getReleaseVelocity (), step.isChanceEnabled (), step.getChance (), step.isOccurrenceEnabled (), step.getOccurrence ().name (), step.isRecurrenceEnabled (), step.getRecurrenceLength (), step.getRecurrenceMask (), step.getGain (), step.getPan (), step.getTranspose (), step.getTimbre (), step.getPressure (), step.isRepeatEnabled (), step.getRepeatCount (), step.getRepeatCurve (), step.getRepeatVelocityCurve (), step.getRepeatVelocityEnd ()));
    }

    private static List<Boolean> touched (final BaseMode<?> mode) { return IntStream.range (0, 8).mapToObj (mode::isKnobTouched).toList (); }
    private static double normalized (final IModel model, final double value) { return Math.max (0, Math.min (1, model.getValueChanger ().toNormalizedValue (value))); }
    private static String text (final String value) { return value == null ? "" : value.substring (0, Math.min (1024, value.length ())); }
}
