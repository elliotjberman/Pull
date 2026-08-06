// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Fixed parameter banks installed by the stable shell. */
public enum ParameterBankId
{
    /** Compatibility window currently bound by an inherited stable mode. */
    ACTIVE,
    /** Eight project remote controls. */
    PROJECT_REMOTE,
    /** Current page of eight selected-device remote controls. */
    SELECTED_DEVICE_REMOTE,
    /** Eight visible-track volume parameters. */
    TRACK_VOLUME,
    /** Eight visible-track pan parameters. */
    TRACK_PAN,
    /** Tempo and master-volume parameters. */
    GLOBAL;

    /** Number of installed bank identities. */
    public static final int BANK_CAPACITY = values ().length;
}
