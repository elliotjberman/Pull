// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/**
 * Semantic controller actions which can cross interaction-policy boundaries.
 */
public enum ControllerActionId
{
    /** Select an item which can change the active parameter target. */
    SELECT_PARAMETER_CONTEXT,
    /** Select another active parameter page. */
    SELECT_PARAMETER_PAGE,
    /** Navigate the selected track, device, or other active target. */
    NAVIGATE_SELECTED_TARGET,
    /** Change the stable controller context which supplies active parameters. */
    SWITCH_PARAMETER_CONTEXT,
    /** Enter another compiled core workspace. */
    SWITCH_WORKSPACE,
    /** Select another layout for the current Note viewer. */
    SELECT_NOTE_LAYOUT,
    /** Navigate between open project tabs. */
    NAVIGATE_PROJECT,
    /** Change the current project's audio-engine state. */
    SET_PROJECT_ENGINE,
    /** Invoke a current-project file action. */
    PROJECT_FILE_ACTION
}
