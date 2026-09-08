// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Common bounded state installed in the stable controller bridge.
 *
 * @param transport Transport state
 * @param selectedTrack Private selection-following track state
 * @param sessionBank Active bounded Session bank and visible tracks
 * @param layout Visible layout and reconciled applicability state
 * @param noteView Selected-target-fenced note-view preference
 * @param noteRepeat Live note-repeat read-back and drum-roll setting
 * @param drum Selected-track drum window
 * @param parameters Current parameter slots and retained actuators
 * @param controllerMappingFeedback Bitwig target presence and value keyed by semantic mapping endpoint
 * @param master Current project and Master-page state
 * @param project Lightweight current-project state
 * @param automation Unified Automation Write state
 * @param encoderConfiguration Raw installed encoder calibration and preferences
 * @param currentTrackBank Current main/effect bank, independent of Session topology
 * @param controllerHardware Raw identity read-back for the attached controller surface
 */
public record ControllerBridgeSnapshot (TransportSnapshot transport, SelectedTrackSnapshot selectedTrack, SessionBankSnapshot sessionBank, ControllerLayoutSnapshot layout, NoteViewSnapshot noteView, NoteRepeatSnapshot noteRepeat, DrumContextSnapshot drum, ParameterBridgeSnapshot parameters, ControllerMappingFeedbackSnapshot controllerMappingFeedback, MasterSnapshot master, ProjectSnapshot project, AutomationSnapshot automation, EncoderConfigurationSnapshot encoderConfiguration, CurrentTrackBankSnapshot currentTrackBank, TransportSettingsSnapshot transportSettings, ControllerSettingsSnapshot controllerSettings, ApplicationUiSnapshot applicationUi, LegacyControllerPageRequests controllerPages, BrowserSnapshot browser, ControllerHardwareSnapshot controllerHardware, ControllerPageDisplaySnapshot pageDisplay)
{
    private static final ControllerBridgeSnapshot EMPTY = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), SessionBankSnapshot.empty (), ControllerLayoutSnapshot.empty (), NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (), ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty ());


    /**
     * Validate the bridge snapshot.
     */
    public ControllerBridgeSnapshot
    {
        transport = Objects.requireNonNull (transport, "transport");
        selectedTrack = Objects.requireNonNull (selectedTrack, "selectedTrack");
        sessionBank = Objects.requireNonNull (sessionBank, "sessionBank");
        layout = Objects.requireNonNull (layout, "layout");
        noteView = Objects.requireNonNull (noteView, "noteView");
        noteRepeat = Objects.requireNonNull (noteRepeat, "noteRepeat");
        drum = Objects.requireNonNull (drum, "drum");
        parameters = Objects.requireNonNull (parameters, "parameters");
        controllerMappingFeedback = Objects.requireNonNull (controllerMappingFeedback, "controllerMappingFeedback");
        master = Objects.requireNonNull (master, "master");
        project = Objects.requireNonNull (project, "project");
        automation = Objects.requireNonNull (automation, "automation");
        encoderConfiguration = Objects.requireNonNull (encoderConfiguration, "encoderConfiguration");
        currentTrackBank = Objects.requireNonNull (currentTrackBank, "currentTrackBank");
        transportSettings = Objects.requireNonNull (transportSettings, "transportSettings");
        controllerSettings = Objects.requireNonNull (controllerSettings, "controllerSettings");
        applicationUi = Objects.requireNonNull (applicationUi, "applicationUi");
        controllerPages = Objects.requireNonNull (controllerPages, "controllerPages");
        browser = Objects.requireNonNull (browser, "browser");
        controllerHardware = Objects.requireNonNull (controllerHardware, "controllerHardware");
        pageDisplay = Objects.requireNonNull (pageDisplay, "pageDisplay");
    }


    /** Compatibility constructor without page display observations. */
    public ControllerBridgeSnapshot (TransportSnapshot transport, SelectedTrackSnapshot selectedTrack, SessionBankSnapshot sessionBank, ControllerLayoutSnapshot layout, NoteViewSnapshot noteView, NoteRepeatSnapshot noteRepeat, DrumContextSnapshot drum, ParameterBridgeSnapshot parameters, ControllerMappingFeedbackSnapshot controllerMappingFeedback, MasterSnapshot master, ProjectSnapshot project, AutomationSnapshot automation, EncoderConfigurationSnapshot encoderConfiguration, CurrentTrackBankSnapshot currentTrackBank, TransportSettingsSnapshot transportSettings, ControllerSettingsSnapshot controllerSettings, ApplicationUiSnapshot applicationUi, LegacyControllerPageRequests controllerPages, BrowserSnapshot browser, ControllerHardwareSnapshot controllerHardware)
    {
        this (transport, selectedTrack, sessionBank, layout, noteView, noteRepeat, drum, parameters, controllerMappingFeedback, master, project, automation, encoderConfiguration, currentTrackBank, transportSettings, controllerSettings, applicationUi, controllerPages, browser, controllerHardware, ControllerPageDisplaySnapshot.empty ());
    }


    /** Compatibility constructor without native application UI state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final SessionBankSnapshot sessionBank, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final ControllerMappingFeedbackSnapshot controllerMappingFeedback, final MasterSnapshot master, final ProjectSnapshot project, final AutomationSnapshot automation, final EncoderConfigurationSnapshot encoderConfiguration, final CurrentTrackBankSnapshot currentTrackBank, final TransportSettingsSnapshot transportSettings, final ControllerSettingsSnapshot controllerSettings)
    {
        this (transport, selectedTrack, sessionBank, layout, noteView, noteRepeat, drum, parameters, controllerMappingFeedback, master, project, automation, encoderConfiguration, currentTrackBank, transportSettings, controllerSettings, ApplicationUiSnapshot.empty (), LegacyControllerPageRequests.empty (), BrowserSnapshot.empty (), ControllerHardwareSnapshot.empty ());
    }


    /** Compatibility constructor without extended transport settings. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final SessionBankSnapshot sessionBank, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final ControllerMappingFeedbackSnapshot controllerMappingFeedback, final MasterSnapshot master, final ProjectSnapshot project, final AutomationSnapshot automation, final EncoderConfigurationSnapshot encoderConfiguration, final CurrentTrackBankSnapshot currentTrackBank)
    {
        this (transport, selectedTrack, sessionBank, layout, noteView, noteRepeat, drum, parameters, controllerMappingFeedback, master, project, automation, encoderConfiguration, currentTrackBank, TransportSettingsSnapshot.empty (), ControllerSettingsSnapshot.empty ());
    }


    /** Compatibility constructor without encoder calibration. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final SessionBankSnapshot sessionBank, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final ControllerMappingFeedbackSnapshot controllerMappingFeedback, final MasterSnapshot master, final ProjectSnapshot project, final AutomationSnapshot automation)
    {
        this (transport, selectedTrack, sessionBank, layout, noteView, noteRepeat, drum, parameters, controllerMappingFeedback, master, project, automation, EncoderConfigurationSnapshot.empty (), CurrentTrackBankSnapshot.empty ());
    }


    /** Compatibility constructor without automation state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final SessionBankSnapshot sessionBank, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final ControllerMappingFeedbackSnapshot controllerMappingFeedback, final MasterSnapshot master, final ProjectSnapshot project)
    {
        this (transport, selectedTrack, sessionBank, layout, noteView, noteRepeat, drum, parameters, controllerMappingFeedback, master, project, AutomationSnapshot.empty ());
    }


    /** Compatibility constructor for snapshots without active Session-bank state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final ControllerMappingFeedbackSnapshot controllerMappingFeedback, final MasterSnapshot master, final ProjectSnapshot project)
    {
        this (transport, selectedTrack, SessionBankSnapshot.empty (), layout, noteView, noteRepeat, drum, parameters, controllerMappingFeedback, master, project);
    }


    /** Compatibility constructor for snapshots without controller-mapping feedback. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final SessionBankSnapshot sessionBank, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final MasterSnapshot master, final ProjectSnapshot project)
    {
        this (transport, selectedTrack, sessionBank, layout, noteView, noteRepeat, drum, parameters, ControllerMappingFeedbackSnapshot.empty (), master, project);
    }


    /** Compatibility constructor for snapshots without Session-bank or controller-mapping state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final NoteViewSnapshot noteView, final NoteRepeatSnapshot noteRepeat, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final MasterSnapshot master, final ProjectSnapshot project)
    {
        this (transport, selectedTrack, SessionBankSnapshot.empty (), layout, noteView, noteRepeat, drum, parameters, ControllerMappingFeedbackSnapshot.empty (), master, project);
    }


    /** Compatibility constructor for snapshots without note-controller state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters, final MasterSnapshot master, final ProjectSnapshot project)
    {
        this (transport, selectedTrack, SessionBankSnapshot.empty (), layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), drum, parameters, ControllerMappingFeedbackSnapshot.empty (), master, project);
    }


    /** Compatibility constructor for snapshots without Master state. */
    public ControllerBridgeSnapshot (final TransportSnapshot transport, final SelectedTrackSnapshot selectedTrack, final ControllerLayoutSnapshot layout, final DrumContextSnapshot drum, final ParameterBridgeSnapshot parameters)
    {
        this (transport, selectedTrack, SessionBankSnapshot.empty (), layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), drum, parameters, ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty ());
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
