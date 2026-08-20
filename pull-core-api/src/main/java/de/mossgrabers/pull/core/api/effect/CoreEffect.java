// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * A validated, one-shot action requested from the stable shell.
 */
public sealed interface CoreEffect permits AdjustParameterValueEffect, CancelTimerEffect, ConsumeControllerButtonEffect, NavigateProjectEffect, PressClipTargetEffect, ProjectFileActionEffect, ReleaseClipTargetsEffect, ResetParameterEffect, ScheduleTimerEffect, SelectDrumPadEffect, SelectSessionTrackEffect, SelectedTrackActionEffect, SendNoteInputMidiEffect, SetDrumPadBooleanEffect, SetDrumPadValueEffect, SetNoteViewPreferenceEffect, SetParameterValueEffect, SetProjectEngineEffect, SetProjectTransportStateEffect, SetSelectedTrackBooleanEffect, SetSelectedTrackMonitorEffect, SetSelectedTrackValueEffect, SetTransportStateEffect, SetTransportValueEffect, StopSessionBankEffect, StopSessionTrackEffect
{
    // Marker interface for API-owned effects
}
