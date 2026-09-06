// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.ui.page.*;
import de.mossgrabers.pull.core.api.output.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Scene fingerprints from 9f0d575 preserve formatting, owner rejection and widget geometry. */
class MixerPageRenderingCharacterizationTest
{
    @Test void macro () throws Exception { assertEquals ("cc7d28856077e0354a9862d8c2996b9f67833b0b1c938923c5ad61fe99183b3b", fingerprint ("macro")); }
    @Test void track () throws Exception { assertEquals ("64e96bf06081c4e4a9f05557a26693dc239cff0df3dc324970be8549ae0a8034", fingerprint ("track")); }
    @Test void master () throws Exception { assertEquals ("1964dd3d343fce69a38234a0de6b9b899e2d20996261824fffc261c4dbe3ac9e", fingerprint ("master")); }
    @Test void global () throws Exception { assertEquals ("fae559940c7f89baf51111140dd5b1466b81b0533171dd1da42143044e3c784e", fingerprint ("global")); }

    @Test
    void projectionsExcludeDisagreeingOwnersBeforeValuesReachRendering ()
    {
        final ControllerSnapshot wrongOwner = snapshot (3);
        assertTrue (MixerPageProjections.macros (wrongOwner).controls ().isEmpty ());
        assertTrue (MixerPageProjections.master (wrongOwner).controls ().isEmpty ());
        assertTrue (MixerPageProjections.track (wrongOwner, new TrackMixerPageState (), true).controls ().isEmpty ());
        assertTrue (MixerPageProjections.global (wrongOwner, GlobalMixerControlsView.Role.VOLUME, 0,
            GlobalMixerMenu.entries (wrongOwner.bridge ().controllerSettings (), "VOLUME")).controls ().isEmpty ());
        final ControllerSnapshot aligned = snapshot (7);
        assertFalse (MixerPageProjections.macros (aligned).controls ().isEmpty ());
        assertFalse (MixerPageProjections.master (aligned).controls ().isEmpty ());
        assertFalse (MixerPageProjections.track (aligned, new TrackMixerPageState (), true).controls ().isEmpty ());
        assertFalse (MixerPageProjections.global (aligned, GlobalMixerControlsView.Role.VOLUME, 0,
            GlobalMixerMenu.entries (aligned.bridge ().controllerSettings (), "VOLUME")).controls ().isEmpty ());
    }

    @Test
    void projectionNeedsLaterHostValueToChangeTheDisplay ()
    {
        final ControllerSnapshot observed = snapshot (7);
        final MacroPagePresentation first = MixerPageProjections.macros (observed);
        final ParameterTargetSnapshot parameter = observed.bridge ().parameters ().slots ().get (ParameterSlot.projectRemote (0));
        final ProjectMacroControlsView view = new ProjectMacroControlsView ();
        view.start (observed);
        final ControllerDisplayScene before = view.render (observed).display ();
        final var requests = view.handle (new ControllerInputEvent (1, 1, PushControlIds.continuous ("KNOB1"), InputKind.RELATIVE, InputPhase.UPDATE, 1), observed);
        assertEquals (List.of (new AdjustParameterValueEffect (parameter.target (), 10)), requests);
        assertEquals (before, view.render (observed).display ());
        assertEquals (first, MixerPageProjections.macros (observed));
        assertNotEquals (before, view.render (snapshot (15)).display ());
        assertEquals (0, first.controls ().getFirst ().value ());
        assertEquals (0.25, MixerPageProjections.macros (snapshot (15)).controls ().getFirst ().value ());
    }

    private static String fingerprint (final String family) throws Exception
    {
        final MessageDigest digest = MessageDigest.getInstance ("SHA-256");
        for (int sample = 0; sample < 64; sample++)
        {
            final ControllerSnapshot snapshot = snapshot (sample);
            final List<ControllerDisplayScene> scenes = new ArrayList<> ();
            switch (family)
            {
                case "macro" -> scenes.add (MacroPageRenderer.render (MixerPageProjections.macros (snapshot)));
                case "master" -> scenes.add (MasterPageRenderer.render (MixerPageProjections.master (snapshot)).display ());
                case "track" -> {
                    for (final boolean normal: List.of (false, true))
                        for (final boolean io: List.of (false, true))
                            for (final int offset: List.of (0, 4))
                                scenes.add (TrackMixerPageRenderer.render (MixerPageProjections.track (snapshot, new TrackMixerPageState (io, offset), normal)).display ());
                }
                case "global" -> {
                    for (final GlobalMixerControlsView.Role role: GlobalMixerControlsView.Role.values ())
                        for (int send = 0; send < (role == GlobalMixerControlsView.Role.SEND ? 8 : 1); send++)
                        {
                            final String mode = role == GlobalMixerControlsView.Role.SEND ? "SEND" + (send + 1) : role.name ();
                            scenes.add (GlobalMixerPageRenderer.render (MixerPageProjections.global (snapshot, role, send, GlobalMixerMenu.entries (snapshot.bridge ().controllerSettings (), mode))).display ());
                        }
                }
                default -> throw new IllegalArgumentException (family);
            }
            for (final ControllerDisplayScene scene: scenes)
                digest.update (scene.toString ().getBytes (StandardCharsets.UTF_8));
        }
        return HexFormat.of ().formatHex (digest.digest ());
    }

    private static ControllerSnapshot snapshot (final int sample)
    {
        final boolean exists = bit (sample, 0);
        final boolean active = bit (sample, 1);
        final boolean aligned = bit (sample, 2);
        final RgbColor color = new RgbColor (12, 100, 255);
        final SelectedTrackSnapshot selected = exists ? new SelectedTrackSnapshot (1, "track-0", "Selected Track", 0, "Instrument", true, bit (sample, 3), false, bit (sample, 4), bit (sample, 5), active, false, TrackMonitorMode.values ()[sample % 3], false, false, false, false, 0.5, 0.75, color) : SelectedTrackSnapshot.empty ();
        final MasterSnapshot master = new MasterSnapshot (exists, "project", "Project long enough to exercise clipping", bit (sample, 1), bit (sample, 2), bit (sample, 3), false, bit (sample, 4), "Master Track", color, active, bit (sample, 4), bit (sample, 5), 512, 1023);
        final Map<ParameterSlot, ParameterTargetSnapshot> parameters = new LinkedHashMap<> ();
        final List<CurrentTrackSnapshot> tracks = new ArrayList<> ();
        final List<CursorSendBankSnapshot.Send> sends = new ArrayList<> ();
        for (int index = 0; index < 8; index++)
        {
            final SessionTrackSnapshot track = new SessionTrackSnapshot ("track-" + index, index, "Track " + index, true, index == 0, active, false, false, false, false, SessionTrackType.AUDIO, color);
            tracks.add (new CurrentTrackSnapshot (track, false, index / 8.0, (8 - index) / 8.0));
            sends.add (new CursorSendBankSnapshot.Send (index < (bit (sample, 5) ? 8 : 4), "Send " + (index + 1)));
            parameter (parameters, ParameterSlot.projectRemote (index), "project-remote", aligned ? "project" : "wrong", index, sample);
            if (index < 4) parameter (parameters, new ParameterSlot (ParameterBankId.MASTER, index), "project-master", aligned ? "project" : "wrong", index, sample);
            if (index < 2) parameter (parameters, new ParameterSlot (ParameterBankId.SELECTED_TRACK, index), index == 0 ? "channel-volume" : "channel-pan", aligned ? "track-0" : "wrong", index, sample);
            parameter (parameters, ParameterSlot.selectedTrackSend (index), "channel-send", aligned ? "track-0" : "wrong", index, sample);
            parameter (parameters, ParameterSlot.trackVolume (index), "channel-volume", aligned ? "track-" + index : "wrong", index, sample);
            parameter (parameters, ParameterSlot.trackPan (index), "channel-pan", aligned ? "track-" + index : "wrong", index, sample);
            for (int send = 0; send < 8; send++) parameter (parameters, ParameterSlot.trackSend (send, index), "channel-send", aligned ? "track-" + index : "wrong", index, sample);
        }
        final var empty = ControllerBridgeSnapshot.empty ();
        final ControllerSettingsSnapshot settings = new ControllerSettingsSnapshot (true, bit (sample, 3), "VOLUME", bit (sample, 4) ? 4 : 0, new CursorSendBankSnapshot (1, "cursor", 0, sends));
        final EncoderConfigurationSnapshot encoder = bit (sample, 0) ? new EncoderConfigurationSnapshot (true, 1024, 1, 1, 1) : EncoderConfigurationSnapshot.empty ();
        final var bridge = new ControllerBridgeSnapshot (empty.transport (), selected, empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), new ParameterBridgeSnapshot (parameters, Map.of ()), empty.controllerMappingFeedback (), master, empty.project (), new AutomationSnapshot ("project", false, false), encoder, new CurrentTrackBankSnapshot (1, "main", 0, tracks, "track-0", false, 1, true), empty.transportSettings (), settings, empty.applicationUi ());
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), bit (sample, 5) ? Set.of (PushControlIds.continuous ("KNOB1"), PushControlIds.continuous ("KNOB8")) : Set.of ());
    }

    private static void parameter (final Map<ParameterSlot, ParameterTargetSnapshot> parameters, final ParameterSlot slot, final String domain, final String owner, final int index, final int sample)
    {
        final String name = List.of ("Volume", "Pan", "Panning", "Macro", "", "Long parameter label", "Toggle", "Ring").get (index);
        final String displayed = List.of ("+6.0 dB", "L 100", "100 R", "On", "Off", "123456789 Hz", "  off  ", "0.5 ms").get ((index + sample) % 8);
        final double value = List.of (0.0, 512.0, 1023.0).get (index % 3);
        parameters.put (slot, new ParameterTargetSnapshot (new ParameterTargetRef (ParameterTargetKind.LIVE, slot.toString (), 1), name, value, bit (sample, 3) ? 256 : -1, displayed, 128, 0.5, index % 3 == 0 ? Optional.empty () : Optional.of (bit (sample, 4)), new ParameterTargetIdentitySnapshot (domain, owner, 0, index)));
    }

    private static boolean bit (final int mask, final int index) { return (mask & 1 << index) != 0; }
}
