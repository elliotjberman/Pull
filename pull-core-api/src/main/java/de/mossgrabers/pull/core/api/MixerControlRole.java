// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Identifies the authoritative control domain without prescribing its visual treatment. */
public enum MixerControlRole
{
    /** A control whose accent is supplied by authoritative host state. */
    HOST_COLORED,

    /** A Bitwig project remote-control parameter. */
    PROJECT_MACRO
}
