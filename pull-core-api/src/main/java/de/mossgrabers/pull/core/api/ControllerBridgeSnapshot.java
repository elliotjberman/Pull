// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Common bounded state installed in the stable controller bridge.
 *
 * @param transport Transport state
 * @param selectedTrack Private selection-following track state
 * @param layout Visible layout and reconciled applicability state
 * @param noteView Selected-target-fenced note-view preference
 * @param noteRepeat Live note-repeat read-back and drum-roll setting
 * @param drum Selected-track drum window
 * @param parameters Current parameter slots and retained actuators
 * @param master Current project and Master-page state
 * @param project Lightweight current-project state
 */
public record ControllerBridgeSnapshot (TransportSnapshot transport, SelectedTrackSnapshot selectedTrack, ControllerLayoutSnapshot layout, NoteViewSnapshot noteView, NoteRepeatSnapshot noteRepeat, DrumContextSnapshot drum, ParameterBridgeSnapshot parameters, MasterSnapshot master, ProjectSnapshot project)
{
    private static final ControllerBridgeSnapshot EMPTY = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), ControllerLayoutSnapshot.empty (), NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty ());


    /**
     * Validate the bridge snapshot.
     */
    public ControllerBridgeSnapshot
    {
        transport = Objects.requireNonNull (transport, "transport");
        selectedTrack = Objects.requireNonNull (selectedTrack, "selectedTrack");
        layout = Objects.requireNonNull (layout, "layout");
        noteView = Objects.requireNonNull (noteView, "noteView");
        noteRepeat = Objects.requireNonNull (noteRepeat, "noteRepeat");
        drum = Objects.requireNonNull (drum, "drum");
        parameters = Objects.requireNonNull (parameters, "parameters");
        master = Objects.requireNonNull (master, "master");
        project = Objects.requireNonNull (project, "project");
    }


    /** Compatibility constructor for snapshots without note-controller state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final MasterSnapshot master, final ProjectSnapshot project)
    {
        this (transport, selectedTrack, layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), drum, parameters, master, project);
    }


    /** Compatibility constructor for snapshots without lightweight project state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final MasterSnapshot master)
    {
        this (transport, selectedTrack, layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), drum, parameters, master, ProjectSnapshot.empty ());
    }


    /** Compatibility constructor for snapshots without Master state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters)
    {
        this (transport, selectedTrack, layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), drum, parameters, MasterSnapshot.empty (), ProjectSnapshot.empty ());
    }


    /**
     * Get unavailable common bridge state.
     *
     * @return Empty bridge state
     */
    public static ControllerBridgeSnapshot empty ()
    {
        return EMPTY;
    }
}
