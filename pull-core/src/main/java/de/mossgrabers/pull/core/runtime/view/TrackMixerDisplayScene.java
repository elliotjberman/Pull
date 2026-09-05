// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;


/** Core-owned 960x143 selected-track Mix region. */
final class TrackMixerDisplayScene
{
    private static final int WIDTH = 960;
    private static final int HEIGHT = 143;
    private static final int COLUMN_WIDTH = WIDTH / ParameterSlot.BANK_SIZE;
    private static final int MENU_HEIGHT = 17;
    private static final double PARAMETER_UPPER_BOUND = 1024.0;

    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor SELECTED_MENU = new RgbColor (190, 190, 190);


    private TrackMixerDisplayScene ()
    {
        // Utility class.
    }


    /** Render the complete Track parameter region from subscribed host state and core subpage. */
    static ControllerDisplayScene render (final ControllerSnapshot snapshot, final TrackMixerPageState page, final boolean normalProfile)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final Map<ParameterSlot, ParameterTargetSnapshot> parameters = ParameterAlignment.targets (snapshot);
        final ArrayList<DisplayCommand> commands = new ArrayList<> (96);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        final RgbColor menuColor = normalProfile && selected.exists () ? selected.color () : SELECTED_MENU;
        drawMenu (commands, 0, "Mix", !page.inputOutputSelected (), menuColor);
        drawMenu (commands, 1, "Input & Output", page.inputOutputSelected (), menuColor);
        if (!page.inputOutputSelected ())
        {
            if (page.sendOffset () > 0)
                drawMenu (commands, 6, "<", false, menuColor);
            else if (TrackMixerControlsView.hasAdditionalSends (snapshot))
                drawMenu (commands, 7, ">", false, menuColor);
        }
        if (!selected.exists ())
            return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
        if (page.inputOutputSelected ())
        {
            metadata (commands, 0, "Track Type", trackType (selected), selected.activated ());
            metadata (commands, 1, "Monitor", switch (selected.monitorMode ()) { case AUTO -> "Auto"; case ON -> "On"; case OFF -> "Off"; }, selected.activated ());
            return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
        }
        final CurrentTrackSnapshot metered = normalProfile ? snapshot.bridge ().currentTrackBank ().tracks ().stream ().filter (track -> track.track ().exists () && track.track ().channelId ().equals (selected.channelId ())).findFirst ().orElse (CurrentTrackSnapshot.empty ()) : CurrentTrackSnapshot.empty ();
        final double range = normalProfile && snapshot.bridge ().encoderConfiguration ().available () ? snapshot.bridge ().encoderConfiguration ().valueUpperBound () - 1.0 : PARAMETER_UPPER_BOUND;
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final int send = page.sendOffset () + index - 2;
            final ParameterSlot slot = index == 0 ? ParameterSlot.SELECTED_TRACK_VOLUME : index == 1 ? ParameterSlot.SELECTED_TRACK_PAN : send < ParameterSlot.BANK_SIZE ? ParameterSlot.selectedTrackSend (send) : null;
            final ParameterTargetSnapshot parameter = slot == null ? null : parameters.get (slot);
            if (parameter == null)
                continue;
            final MixerControlKind kind = index == 0 ? MixerControlKind.VOLUME : index == 1 ? MixerControlKind.PAN : MixerControlKind.KNOB;
            final String label = kind == MixerControlKind.KNOB ? nonBlank (parameter.name (), "Send " + (send + 1)) : "";
            final double value = index == 0 ? selected.volume () : index == 1 ? selected.pan () : ratio (parameter.value (), range);
            final double modulated = parameter.modulatedValue () == -1 ? -1 : ratio (parameter.modulatedValue (), range);
            final String displayed = normalProfile && index == 1 ? formatPan (parameter.value () / range) : parameter.displayedValue ();
            MixerDisplayScene.append (commands, new MixerControlSnapshot (
                index, kind, label, value, modulated, displayed, MixerControlRole.HOST_COLORED,
                selected.activated () && parameter.enabled ().orElse (Boolean.TRUE).booleanValue (),
                snapshot.touchedControls ().contains (PushControlIds.continuous ("KNOB" + (index + 1))), Optional.of (selected.color ()), index == 0 ? metered.vuLeft () : 0, index == 0 ? metered.vuRight () : 0));
        }
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }


    private static void metadata (final ArrayList<DisplayCommand> commands, final int column, final String label, final String value, final boolean active)
    {
        final double left = column * COLUMN_WIDTH + 8;
        final RgbColor color = active ? WHITE : new RgbColor (102, 102, 102);
        commands.add (new DisplayCommand.TextAt (label, left, 34, color, 12.5));
        commands.add (new DisplayCommand.TextAt (value, left, 55, color, 19));
    }


    private static String trackType (final SelectedTrackSnapshot track)
    {
        if (track.canHoldNotes () && track.canHoldAudio ()) return "Hybrid";
        if (track.canHoldNotes ()) return "Instrument";
        if (track.canHoldAudio ()) return "Audio";
        return track.group () ? "Group" : "Track";
    }


    private static String formatPan (final double normalized)
    {
        final double bipolar = 2 * normalized - 1;
        final int amount = (int) Math.round (100 * Math.abs (bipolar));
        return amount == 0 ? "C" : (bipolar < 0 ? "L " : "R ") + amount;
    }


    private static void drawMenu (final ArrayList<DisplayCommand> commands, final int column, final String text, final boolean selected, final RgbColor menuColor)
    {
        final double left = column * COLUMN_WIDTH;
        if (selected)
            commands.add (new DisplayCommand.Rectangle (left, 0, COLUMN_WIDTH - 2, MENU_HEIGHT, menuColor));
        commands.add (new DisplayCommand.TextBox (
            text,
            left + 7,
            0,
            COLUMN_WIDTH - 14,
            MENU_HEIGHT,
            DisplayTextAlignment.LEFT,
            selected ? BLACK : WHITE,
            12,
            10,
            DisplayTextFit.SHRINK_ELLIPSIS));
    }


    private static String nonBlank (final String value, final String fallback)
    {
        return value == null || value.isBlank () ? fallback : value;
    }


    private static double ratio (final double value, final double range)
    {
        return Math.max (0, Math.min (1, value / range));
    }
}
