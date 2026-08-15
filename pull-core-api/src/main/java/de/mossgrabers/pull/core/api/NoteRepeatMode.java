// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Portable arpeggiator modes supported by the stable note-repeat engine. */
public enum NoteRepeatMode
{
    ALL,
    UP,
    UP_DOWN,
    UP_THEN_DOWN,
    DOWN,
    DOWN_UP,
    DOWN_THEN_UP,
    FLOW,
    RANDOM,
    CONVERGE_UP,
    CONVERGE_DOWN,
    DIVERGE_UP,
    DIVERGE_DOWN,
    THUMB_UP,
    THUMB_DOWN,
    PINKY_UP,
    PINKY_DOWN
}
