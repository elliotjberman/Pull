// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.parameter.IParameter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Retained device/page capability; opaque owner keys identify leases, never device UUIDs. */
interface RetainedDeviceParameters
{
    RetainedDeviceParameters UNAVAILABLE = new RetainedDeviceParameters ()
    {
        @Override public void requestDevicePage (final boolean active, final Set<String> cleanupOwners) { }
        @Override public DevicePage devicePage () { return null; }
    };

    void requestDevicePage (boolean active, Set<String> cleanupOwners);
    DevicePage devicePage ();

    record DevicePage (String owner, long generation, int page, List<IParameter> parameters, java.util.Map<String, RetainedTrackParameters.TrackMix> channels,
                       BooleanSupplier current, BooleanSupplier addressable)
    {
        public DevicePage
        {
            Objects.requireNonNull (owner, "owner");
            if (owner.isBlank () || generation < 1 || page < -1)
                throw new IllegalArgumentException ("A ready device page needs an opaque owner, generation and page");
            parameters = List.copyOf (parameters);
            channels = java.util.Map.copyOf (channels);
            if (parameters.size () != 8) throw new IllegalArgumentException ("Eight remote slots are required");
            Objects.requireNonNull (current, "current");
            Objects.requireNonNull (addressable, "addressable");
        }
    }
}
