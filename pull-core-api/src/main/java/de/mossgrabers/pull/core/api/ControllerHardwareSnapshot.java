// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Raw identity read-back for the one attached controller surface.
 *
 * <p>Generation is the revision of the latest complete tuple observed during subscribed sampling.
 * It survives core replacement and subscription gaps, and resets with extension reconstruction.
 * It is neither an actuator identity nor proof of physical connection: changes between samples
 * and hardware disconnects without a new identity response are not observable.</p>
 *
 * @param generation Positive observed tuple revision, or zero when unavailable or unrequested
 * @param firmwareMajor Raw firmware major version, or -1 when unavailable
 * @param firmwareMinor Raw firmware minor version, or -1 when unavailable
 * @param firmwareBuild Raw firmware build number, or -1 when unavailable
 * @param boardRevision Raw board revision, or -1 when unavailable
 * @param serialNumber Raw signed 32-bit serial field; its sign does not indicate availability
 */
public record ControllerHardwareSnapshot (long generation, int firmwareMajor, int firmwareMinor, int firmwareBuild, int boardRevision, int serialNumber)
{
    private static final ControllerHardwareSnapshot EMPTY = new ControllerHardwareSnapshot (0, -1, -1, -1, -1, -1);


    public ControllerHardwareSnapshot
    {
        if (generation < 0 || generation == 0 && (firmwareMajor != -1 || firmwareMinor != -1 || firmwareBuild != -1 || boardRevision != -1 || serialNumber != -1) || generation > 0 && (firmwareMajor < 0 || firmwareMinor < 0 || firmwareBuild < 0 || boardRevision < 0))
            throw new IllegalArgumentException ("Hardware identity requires a complete observed tuple");
    }


    /** Whether the shell has observed a complete identity response. */
    public boolean available () { return this.generation > 0; }


    /** Unrequested or not yet observed hardware identity. */
    public static ControllerHardwareSnapshot empty () { return EMPTY; }
}
