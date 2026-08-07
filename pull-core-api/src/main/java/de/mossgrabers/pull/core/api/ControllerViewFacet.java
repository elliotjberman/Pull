// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/**
 * Fixed controller-view facets currently implemented by the stable Push 2 shell.
 *
 * <p>The core may compose these facets but cannot remap them to different hardware.</p>
 */
public enum ControllerViewFacet
{
    /** Eight project-remote encoders, touches, and parameter display cells. */
    PROJECT_MACRO_CONTROLS,
    /** Inherited per-track Mix page across encoders, display, and soft keys. */
    TRACK_MIXER_PAGE,
    /** Track names and selection on the lower display/button strip. */
    TRACK_SELECTION_STRIP,
    /** Session-oriented arrow and page navigation. */
    SESSION_NAVIGATION,
    /** Eight tracks by four scenes on the upper pad-grid half. */
    SESSION_CLIP_GRID_UPPER,
    /** Complete inherited eight-track by eight-scene Session view. */
    SESSION_GRID_FULL,
    /** Four scene buttons aligned to the upper session grid. */
    SESSION_SCENE_KEYS_UPPER,
    /** Existing drum performance, rate, and fill controls on the lower grid half. */
    DRUM_CONTROLLER_LOWER,
    /** Raw drum pitch bend on the touch strip. */
    DRUM_PITCH_BEND,
    /** Master-mode display, soft keys, and encoder-touch adapter. */
    MASTER_CONTROLS
}
