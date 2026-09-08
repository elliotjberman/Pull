// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.*;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceBrowserMode;
import de.mossgrabers.controller.ableton.push.mode.track.AddTrackMode;
import de.mossgrabers.framework.daw.GrooveParameterID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IBrowserColumnItem;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.scale.Scale;
import de.mossgrabers.framework.scale.ScaleLayout;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.pull.core.api.OptionPageState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Read-only capture of installed, bounded proxies and existing legacy local selections. */
public final class PushOptionPageObserver
{
    private PushOptionPageObserver () { }
    public static OptionPageState capture (final PushControlSurface surface, final IModel model)
    {
        final var mode = surface.getModeManager ().getActive ();
        if (mode instanceof ScalesMode)
        {
            final var scales = model.getScales ();
            return new OptionPageState.Scales (Arrays.asList (Scale.getNames ()), scales.getScale ().ordinal (), Scales.BASES, scales.getScaleOffsetIndex (), scales.isChromatic (), text (scales.getRangeText ()));
        }
        if (mode instanceof ScaleLayoutMode) return new OptionPageState.ScaleLayout (Arrays.asList (ScaleLayout.getNames ()), model.getScales ().getScaleLayout ().ordinal ());
        if (mode instanceof FixedMode) return new OptionPageState.FixedLength (surface.getConfiguration ().getNewClipLength ());
        if (mode instanceof final AddTrackMode add)
        {
            final List<String> names = new ArrayList<> (7);
            for (int index = 0; index < 7; index++) names.add (add.getObservedShortcut (index).map (metadata -> text (metadata.name ())).orElse (""));
            return new OptionPageState.AddTrack (add.getObservedAddMode ().name (), names);
        }
        if (mode instanceof final DeviceBrowserMode browserMode) return browser (model, browserMode);
        if (mode instanceof final NoteRepeatMode repeatMode) return repeat (surface, model, repeatMode);
        return OptionPageState.empty ();
    }

    private static OptionPageState browser (final IModel model, final DeviceBrowserMode mode)
    {
        final var browser = model.getBrowser ();
        if (!browser.isActive ()) return OptionPageState.empty ();
        final List<OptionPageState.BrowserColumn> columns = new ArrayList<> (7);
        for (int index = 0; index < Math.min (7, browser.getFilterColumnCount ()); index++)
        {
            final var column = browser.getFilterColumn (index);
            columns.add (new OptionPageState.BrowserColumn (column.doesExist (), text (column.getName ()), column.doesCursorExist (), text (column.getCursorName ()), text (column.getWildcard ())));
        }
        final int selection = mode.getObservedSelectionMode ();
        final int selectedColumn = mode.getObservedFilterColumn ();
        final IBrowserColumnItem[] items = selection == 1 ? browser.getResultColumnItems () :
            selection == 2 && selectedColumn >= 0 && selectedColumn < browser.getFilterColumnCount () ? browser.getFilterColumn (selectedColumn).getItems () : new IBrowserColumnItem[0];
        final List<OptionPageState.BrowserItem> observed = new ArrayList<> (Math.min (48, items.length));
        for (int index = 0; index < Math.min (48, items.length); index++)
            observed.add (new OptionPageState.BrowserItem (items[index].doesExist (), text (items[index].getName ()), items[index].isSelected (), items[index].getHitCount ()));
        return new OptionPageState.Browser (true, selection, selectedColumn, text (browser.getInfoText ()), text (browser.getSelectedResult ()), text (browser.getSelectedContentType ()), browser.isPreviewEnabled (), columns, observed);
    }

    private static OptionPageState repeat (final PushControlSurface surface, final IModel model, final NoteRepeatMode mode)
    {
        final var noteInput = surface.getMidiInput ().getDefaultNoteInput ();
        if (noteInput == null) return OptionPageState.empty ();
        final var repeat = noteInput.getNoteRepeat ();
        if (repeat == null) return OptionPageState.empty ();
        final var configuration = surface.getConfiguration ();
        return new OptionPageState.NoteRepeat (true, repeat.getPeriod (), repeat.getNoteLength (), repeat.isLatchActive (), repeat.usePressure (), repeat.isFreeRunning (), repeat.isShuffle (),
            configuration.getArpeggiatorModes ().stream ().map (ArpeggiatorMode::getName).toList (), configuration.lookupArpeggiatorModeIndex (repeat.getMode ()), text (repeat.getMode ().getName ()), repeat.getOctaves (), mode.isKnobTouched (5), mode.isKnobTouched (6),
            parameter (model.getGroove ().getParameter (GrooveParameterID.SHUFFLE_AMOUNT), model, mode.isKnobTouched (7)), parameter (model.getGroove ().getParameter (GrooveParameterID.ENABLED), model, false));
    }

    private static OptionPageState.Parameter parameter (final IParameter value, final IModel model, final boolean touched)
    { return new OptionPageState.Parameter (text (value.getName ()), value.getValue (), model.getValueChanger ().getUpperBound (), text (value.getDisplayedValue ()), touched); }

    private static String text (final String value)
    {
        if (value == null) return "";
        if (value.length () <= 1024) return value;
        return value.substring (0, Character.isHighSurrogate (value.charAt (1023)) ? 1023 : 1024);
    }
}
