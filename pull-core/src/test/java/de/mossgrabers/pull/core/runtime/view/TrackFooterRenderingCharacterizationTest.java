// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ViewOutput;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Geometry and lights captured before extracting typed footer presentations. */
class TrackFooterRenderingCharacterizationTest
{
    @Test void currentBankFooter () throws Exception { assertEquals ("e85692a585735e7270c2c5a9b7ce92058e4fc3b1b40aceecd80c9060f16d2cf0", fingerprint (true)); }
    @Test void sessionStripFooter () throws Exception { assertEquals ("0e98d1cb53e7b43f7f75229ec90c13f9e7a03c11ef95c3ba7309f98b66a6744b", fingerprint (false)); }

    private static String fingerprint (final boolean current) throws Exception
    {
        final MessageDigest digest = MessageDigest.getInstance ("SHA-256");
        for (int sample = 0; sample < 64; sample++)
        {
            final ViewOutput output = current ? new CurrentTrackFooterView ().render (snapshot (sample)) : new TrackSelectionStripView ().render (snapshot (sample));
            digest.update (output.display ().toString ().getBytes (StandardCharsets.UTF_8));
            output.lights ().entrySet ().stream ().sorted (Comparator.comparing (entry -> entry.getKey ().value ())).forEach (entry -> digest.update (entry.toString ().getBytes (StandardCharsets.UTF_8)));
        }
        return HexFormat.of ().formatHex (digest.digest ());
    }

    private static ControllerSnapshot snapshot (final int sample)
    {
        final List<SessionTrackSnapshot> tracks = new ArrayList<> (8);
        final List<CurrentTrackSnapshot> current = new ArrayList<> (8);
        for (int index = 0; index < 8; index++)
        {
            final boolean exists = (sample & 1) == 0 || index % 2 == 0;
            final SessionTrackType type = SessionTrackType.values ()[(sample + index) % SessionTrackType.values ().length];
            final RgbColor color = index % 2 == 0 ? new RgbColor (240, 250, 200) : new RgbColor (0, 40, 100);
            final SessionTrackSnapshot track = exists ? new SessionTrackSnapshot ("track-" + index, index, index == 7 ? "" : "Track name long enough " + index, true, index == sample % 8, (sample & 2) == 0, false, false, (sample & 4) == 0, false, type, color) : SessionTrackSnapshot.empty ();
            tracks.add (track);
            current.add (new CurrentTrackSnapshot (track, exists && (sample & 8) == 0, 0, 0));
        }
        final var empty = ControllerBridgeSnapshot.empty ();
        final int columns = (sample & 16) == 0 ? 8 : 4;
        final var session = new SessionBankSnapshot (1, new SessionBankShape (columns, 8), 0, 0, tracks.subList (0, columns));
        final var bank = new CurrentTrackBankSnapshot (1, "main", 0, current, "track-0", (sample & 32) == 0, 1, true);
        final var bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), session, empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), empty.parameters (), empty.controllerMappingFeedback (), empty.master (), empty.project (), empty.automation (), empty.encoderConfiguration (), bank, empty.transportSettings (), empty.controllerSettings (), empty.applicationUi ());
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }
}
