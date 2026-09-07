// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackSnapshot;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.ui.page.GlobalMixerPagePresentation;
import de.mossgrabers.pull.core.ui.page.MacroPagePresentation;
import de.mossgrabers.pull.core.ui.page.MasterPagePresentation;
import de.mossgrabers.pull.core.ui.page.TrackMixerPagePresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Projects subscribed host values into immutable page data. Renderers never resolve host targets. */
final class MixerPageProjections
{
    private static final double PARAMETER_UPPER_BOUND = 1024.0;

    private MixerPageProjections () { }

    static MacroPagePresentation macros (final ControllerSnapshot snapshot)
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> parameters = ParameterAlignment.targets (snapshot);
        final List<MacroPagePresentation.Control> controls = new ArrayList<> (8);
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final ParameterTargetSnapshot target = parameters.get (ParameterSlot.projectRemote (index));
            if (target == null || target.name ().isBlank ()) continue;
            final String displayed = target.displayedValue ();
            final MacroPagePresentation.Widget widget = "On".equalsIgnoreCase (displayed.trim ()) ? MacroPagePresentation.Widget.TOGGLE_ON : "Off".equalsIgnoreCase (displayed.trim ()) ? MacroPagePresentation.Widget.TOGGLE_OFF : MacroPagePresentation.Widget.RING;
            controls.add (new MacroPagePresentation.Control (index, target.name (), displayed,
                ratio ((target.modulatedValue () == -1 ? target.value () : target.modulatedValue ()) / PARAMETER_UPPER_BOUND), widget, touched (snapshot, target)));
        }
        return new MacroPagePresentation (controls);
    }

    static TrackMixerPagePresentation track (final ControllerSnapshot snapshot, final TrackMixerPageState page, final boolean normalProfile)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final boolean io = page.inputOutputSelected ();
        final boolean left = !io && page.sendOffset () > 0;
        final boolean right = !io && !left && TrackMixerControlsView.hasAdditionalSends (snapshot);
        final List<TrackMixerPagePresentation.Metadata> metadata = new ArrayList<> (2);
        final List<MixerControlSnapshot> controls = new ArrayList<> (8);
        if (selected.exists () && io)
        {
            metadata.add (new TrackMixerPagePresentation.Metadata (0, "Track Type", trackType (selected), selected.activated ()));
            metadata.add (new TrackMixerPagePresentation.Metadata (1, "Monitor", switch (selected.monitorMode ()) { case AUTO -> "Auto"; case ON -> "On"; case OFF -> "Off"; }, selected.activated ()));
        }
        else if (selected.exists ())
        {
            final Map<ParameterSlot, ParameterTargetSnapshot> parameters = ParameterAlignment.targets (snapshot);
            final CurrentTrackSnapshot metered = normalProfile ? snapshot.bridge ().currentTrackBank ().tracks ().stream ().filter (track -> track.track ().exists () && track.track ().channelId ().equals (selected.channelId ())).findFirst ().orElse (CurrentTrackSnapshot.empty ()) : CurrentTrackSnapshot.empty ();
            final double range = normalProfile && snapshot.bridge ().encoderConfiguration ().available () ? snapshot.bridge ().encoderConfiguration ().valueUpperBound () - 1.0 : PARAMETER_UPPER_BOUND;
            for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            {
                final int send = page.sendOffset () + index - 2;
                final ParameterSlot slot = index == 0 ? ParameterSlot.SELECTED_TRACK_VOLUME : index == 1 ? ParameterSlot.SELECTED_TRACK_PAN : send < ParameterSlot.BANK_SIZE ? ParameterSlot.selectedTrackSend (send) : null;
                final ParameterTargetSnapshot parameter = slot == null ? null : parameters.get (slot);
                if (parameter == null) continue;
                final MixerControlKind kind = index == 0 ? MixerControlKind.VOLUME : index == 1 ? MixerControlKind.PAN : MixerControlKind.KNOB;
                final String label = kind == MixerControlKind.KNOB ? parameter.name ().isBlank () ? "Send " + (send + 1) : parameter.name () : "";
                controls.add (new MixerControlSnapshot (index, kind, label,
                    index == 0 ? selected.volume () : index == 1 ? selected.pan () : ratio (parameter.value () / range),
                    parameter.modulatedValue () == -1 ? -1 : ratio (parameter.modulatedValue () / range),
                    normalProfile && index == 1 ? formatPan (parameter.value () / range) : parameter.displayedValue (),
                    MixerControlRole.HOST_COLORED, selected.activated () && parameter.enabled ().orElse (Boolean.TRUE).booleanValue (),
                    touched (snapshot, parameter), Optional.of (selected.color ()), index == 0 ? metered.vuLeft () : 0, index == 0 ? metered.vuRight () : 0));
            }
        }
        return new TrackMixerPagePresentation (io, left, right, normalProfile && selected.exists () ? Optional.of (selected.color ()) : Optional.empty (), metadata, controls);
    }

    static MasterPagePresentation master (final ControllerSnapshot snapshot)
    {
        final MasterSnapshot master = snapshot.bridge ().master ();
        final Map<ParameterSlot, ParameterTargetSnapshot> parameters = ParameterAlignment.targets (snapshot);
        final List<MixerControlSnapshot> controls = new ArrayList<> (4);
        final List<ParameterSlot> slots = List.of (ParameterSlot.MASTER_MIX_VOLUME, ParameterSlot.MASTER_MIX_PAN, ParameterSlot.CUE_VOLUME, ParameterSlot.CUE_MIX);
        final List<String> labels = List.of ("Volume", "Pan", "Cue Volume", "Cue Mix");
        for (int index = 0; index < slots.size (); index++)
        {
            final ParameterTargetSnapshot parameter = parameters.get (slots.get (index));
            if (parameter == null) continue;
            controls.add (new MixerControlSnapshot (index, index == 0 ? MixerControlKind.VOLUME : index == 1 ? MixerControlKind.PAN : MixerControlKind.KNOB, labels.get (index),
                ratio (parameter.value () / PARAMETER_UPPER_BOUND), parameter.modulatedValue () == -1 ? -1 : ratio (parameter.modulatedValue () / PARAMETER_UPPER_BOUND),
                parameter.displayedValue (), MixerControlRole.HOST_COLORED, index >= 2 || master.trackActive (), touched (snapshot, parameter), Optional.of (master.trackColor ()),
                index == 0 ? ratio (master.vuLeft () / PARAMETER_UPPER_BOUND) : 0, index == 0 ? ratio (master.vuRight () / PARAMETER_UPPER_BOUND) : 0));
        }
        return new MasterPagePresentation (controls, new MasterPagePresentation.TrackFooter (master.trackName (), master.cursorPinned () ? DisplayIcon.PIN : DisplayIcon.MASTER,
            master.trackColor (), master.trackSelected (), master.trackActive ()), master.engineActive (), master.projectName (), master.canPrevious (), master.canNext (), master.projectDirty ());
    }

    static GlobalMixerPagePresentation global (final ControllerSnapshot snapshot, final GlobalMixerControlsView.Role role, final int sendIndex, final List<GlobalMixerMenu.Entry> menu)
    {
        final List<GlobalMixerPagePresentation.Control> controls = new ArrayList<> (8);
        if (snapshot.bridge ().encoderConfiguration ().available ())
        {
            final double upper = snapshot.bridge ().encoderConfiguration ().valueUpperBound ();
            final List<CurrentTrackSnapshot> tracks = snapshot.bridge ().currentTrackBank ().tracks ();
            for (int index = 0; index < tracks.size (); index++)
            {
                final CurrentTrackSnapshot track = tracks.get (index);
                final ParameterTargetSnapshot parameter = GlobalMixerControlsView.alignedTarget (snapshot, role, sendIndex, index);
                if (!track.track ().exists () || parameter == null || role == GlobalMixerControlsView.Role.SEND && parameter.name ().isBlank ()) continue;
                final boolean active = track.track ().activated () && (role != GlobalMixerControlsView.Role.SEND || parameter.enabled ().orElse (Boolean.FALSE).booleanValue ());
                final GlobalMixerPagePresentation.Widget widget;
                final String displayed;
                if (role == GlobalMixerControlsView.Role.VOLUME)
                {
                    widget = GlobalMixerPagePresentation.Widget.VOLUME;
                    displayed = parameter.displayedValue ();
                }
                else if (role == GlobalMixerControlsView.Role.PAN)
                {
                    widget = GlobalMixerPagePresentation.Widget.PAN;
                    displayed = formatPan (parameter.value () / (upper - 1));
                }
                else
                {
                    // Preserve the inherited name-selected Send widget until a deliberate UI change.
                    widget = parameter.name ().contains ("Volume") ? GlobalMixerPagePresentation.Widget.SEND_VOLUME : "Pan".equals (parameter.name ()) || parameter.name ().contains ("Panning") ? GlobalMixerPagePresentation.Widget.PAN : GlobalMixerPagePresentation.Widget.RING;
                    displayed = parameter.displayedValue ().substring (0, Math.min (8, parameter.displayedValue ().length ()));
                }
                controls.add (new GlobalMixerPagePresentation.Control (index, widget, displayed,
                    ratio ((parameter.modulatedValue () == -1 ? parameter.value () : parameter.modulatedValue ()) / upper), active, track.track ().color (),
                    snapshot.bridge ().controllerSettings ().vuMetersEnabled () ? track.vuLeft () * (upper - 1) / upper : 0,
                    snapshot.bridge ().controllerSettings ().vuMetersEnabled () ? track.vuRight () * (upper - 1) / upper : 0));
            }
        }
        return new GlobalMixerPagePresentation (menu.stream ().map (entry -> new GlobalMixerPagePresentation.MenuItem (entry.text (), entry.selected (), entry.arrow ())).toList (), controls);
    }

    private static boolean touched (final ControllerSnapshot snapshot, final ParameterTargetSnapshot target) { return snapshot.bridge ().parameters ().touchLeases ().contains (target.target ()); }
    private static double ratio (final double value) { return Math.max (0, Math.min (1, value)); }

    private static String formatPan (final double normalized)
    {
        final double bipolar = 2 * normalized - 1;
        final int amount = (int) Math.round (100 * Math.abs (bipolar));
        return amount == 0 ? "C" : (bipolar < 0 ? "L " : "R ") + amount;
    }

    private static String trackType (final SelectedTrackSnapshot track)
    {
        if (track.canHoldNotes () && track.canHoldAudio ()) return "Hybrid";
        if (track.canHoldNotes ()) return "Instrument";
        if (track.canHoldAudio ()) return "Audio";
        return track.group () ? "Group" : "Track";
    }
}
