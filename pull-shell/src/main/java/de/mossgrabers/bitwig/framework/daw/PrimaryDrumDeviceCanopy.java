// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.daw;

import java.util.List;
import java.util.Objects;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayer;
import com.bitwig.extension.controller.api.DeviceMatcher;
import com.bitwig.extension.controller.api.PinnableCursorDevice;


/**
 * Creates the fixed proxy canopy used to resolve a track's primary compatible drum device.
 *
 * <p>A device-bank matcher filters one device chain; it does not recurse. This canopy therefore
 * covers exactly four candidates: a native Drum Machine match in the track device chain, the
 * semantic first instrument itself when it reports drum pads, and native matches in layer zero and
 * the cursor slot of that instrument. It intentionally does not search additional layers, parallel
 * branches or arbitrary nesting depth.</p>
 */
public final class PrimaryDrumDeviceCanopy
{
    /** Number of fixed drum candidates in the canopy. */
    public static final int NUM_CANDIDATES = 4;


    private PrimaryDrumDeviceCanopy ()
    {
        // Utility class
    }


    /**
     * Create the bounded drum candidates for a selection-following track cursor.
     *
     * @param host The Bitwig controller host
     * @param track The track cursor
     * @param cursorID Stable ID for the private first-instrument cursor
     * @param cursorName Display name for that cursor
     * @param numSends Number of nested sends exposed by the cursor
     * @return Direct, semantic first-instrument, first-layer and cursor-slot drum candidates, in
     *         that order
     */
    public static List<Candidate> create (final ControllerHost host, final CursorTrack track, final String cursorID, final String cursorName, final int numSends)
    {
        final ControllerHost checkedHost = Objects.requireNonNull (host, "host");
        final CursorTrack checkedTrack = Objects.requireNonNull (track, "track");
        final DeviceMatcher drumMatcher = checkedHost.createBitwigDeviceMatcher (ModelImpl.INSTRUMENT_DRUM_MACHINE);

        final Device directDrum = createMatchedDevice (checkedTrack.createDeviceBank (1), drumMatcher);
        final PinnableCursorDevice primaryInstrument = checkedTrack.createCursorDevice (cursorID, cursorName, numSends, CursorDeviceFollowMode.FIRST_INSTRUMENT);

        final DeviceLayer firstLayer = primaryInstrument.createLayerBank (1).getItemAt (0);
        final Device layerDrum = createMatchedDevice (firstLayer.createDeviceBank (1), drumMatcher);
        final Device slotDrum = createMatchedDevice (primaryInstrument.getCursorSlot ().createDeviceBank (1), drumMatcher);

        // Only the track-chain candidate is rooted in the send-capable cursor track. Bitwig's
        // nested FIRST_INSTRUMENT/layer/slot proxies expose their child channels with a send-bank
        // capacity of zero. Passing the track's send count through for those paths makes
        // DrumDeviceImpl eagerly call Channel.sendBank() on a proxy which has no send bank.
        return List.of (
            new Candidate (directDrum, Path.TRACK_CHAIN, numSends),
            new Candidate (primaryInstrument, Path.FIRST_INSTRUMENT, 0),
            new Candidate (layerDrum, Path.FIRST_LAYER, 0),
            new Candidate (slotDrum, Path.CURSOR_SLOT, 0));
    }


    private static Device createMatchedDevice (final DeviceBank bank, final DeviceMatcher matcher)
    {
        bank.setDeviceMatcher (matcher);
        return bank.getItemAt (0);
    }


    /**
     * One fixed candidate and the proxy path that produced it.
     *
     * @param device Candidate device proxy
     * @param path Fixed path used to reach the proxy
     * @param numLayerSends Send-bank capacity exposed by layers of this proxy
     */
    public record Candidate (Device device, Path path, int numLayerSends)
    {
        /**
         * Validate the candidate.
         */
        public Candidate
        {
            device = Objects.requireNonNull (device, "device");
            path = Objects.requireNonNull (path, "path");
            if (numLayerSends < 0)
                throw new IllegalArgumentException ("numLayerSends must not be negative");
        }
    }


    /**
     * Fixed path through the selected track's device topology.
     */
    public enum Path
    {
        /** Matched device in the selected track's top-level chain. */
        TRACK_CHAIN,

        /** Bitwig's semantic first-instrument cursor. */
        FIRST_INSTRUMENT,

        /** Matched device in layer zero of the first instrument. */
        FIRST_LAYER,

        /** Matched device in the first instrument's cursor slot. */
        CURSOR_SLOT
    }
}
