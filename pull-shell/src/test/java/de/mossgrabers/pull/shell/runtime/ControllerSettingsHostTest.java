// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.effect.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static de.mossgrabers.pull.shell.runtime.CurrentTrackParameterBankHostTest.proxy;
import static org.junit.jupiter.api.Assertions.*;

class ControllerSettingsHostTest
{
    @Test
    void cursorMetadataIsBoundedAndTracksModelCursorIdentityRatherThanSelectedTrack ()
    {
        final Fixture fixture = new Fixture ();
        final var initial = fixture.host.snapshot ();
        assertEquals ("pinned-model-cursor", initial.cursorSends ().channelId ());
        assertEquals (8, initial.cursorSends ().sends ().size ());
        assertEquals ("Send 6", initial.cursorSends ().sends ().get (5).name ());
        fixture.offset = 8;
        final var scrolled = fixture.host.snapshot ();
        assertTrue (scrolled.cursorSends ().generation () > initial.cursorSends ().generation ());
        assertEquals (8, scrolled.cursorSends ().offset ());
        fixture.channel = "next-cursor";
        assertTrue (fixture.host.snapshot ().cursorSends ().generation () > scrolled.cursorSends ().generation ());
        fixture.exists = false;
        assertTrue (fixture.host.snapshot ().cursorSends ().sends ().isEmpty ());
    }

    @Test
    void preferencesAreAbsoluteRequestsAndSnapshotReadsObservedValues ()
    {
        final Fixture fixture = new Fixture ();
        final var before = fixture.host.snapshot ();
        fixture.host.apply (fixture.host.prepare (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.VU_METERS, true)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 4)));
        fixture.host.apply (fixture.host.prepare (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "PAN")));
        assertEquals (List.of ("vu:true", "offset:4", "mode:PAN"), fixture.configuration.commands);
        assertEquals (before, fixture.host.snapshot (), "fake request submission does not advance observed preference values");
        fixture.configuration.vu = true;
        fixture.configuration.offset = 4;
        fixture.configuration.mode = Modes.PAN;
        final var after = fixture.host.snapshot ();
        assertTrue (after.vuMetersEnabled ());
        assertEquals (4, after.mixSendOffset ());
        assertEquals ("PAN", after.globalMixMode ());
    }

    @Test
    void modePreferenceRequiresAnInstalledEntryAndIntegerValuesRemainBounded ()
    {
        final Fixture fixture = new Fixture ();
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "not-a-mode")));
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "SEND8")));
        assertThrows (IllegalArgumentException.class, () -> new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 5));
        assertTrue (fixture.configuration.commands.isEmpty ());
    }

    @Test
    void accentSettingsAreObservedOnlyAfterCallbacksAndKeepMidiVelocityBounds ()
    {
        final Fixture fixture = new Fixture ();
        final var before = fixture.host.snapshot ();
        fixture.host.apply (fixture.host.prepare (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.ACCENT_ENABLED, true)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 73)));
        assertEquals (List.of ("accent:true", "velocity:73"), fixture.configuration.commands);
        assertEquals (before, fixture.host.snapshot (), "submitting a setting request cannot confirm its own read-back");
        fixture.configuration.accent = true;
        fixture.configuration.velocity = 73;
        assertTrue (fixture.host.snapshot ().accentEnabled ());
        assertEquals (73, fixture.host.snapshot ().accentVelocity ());
        assertThrows (IllegalArgumentException.class, () -> new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 0));
        assertThrows (IllegalArgumentException.class, () -> new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 128));
    }

    private static final class Fixture
    {
        private String channel = "pinned-model-cursor";
        private boolean exists = true;
        private int offset;
        private final Configuration configuration = new Configuration ();
        private final ControllerSettingsHost host;
        private Fixture ()
        {
            final List<ISend> sends = new ArrayList<> ();
            for (int index = 0; index < 12; index++)
            {
                final int slot = index;
                sends.add (proxy (ISend.class, (method, args) -> switch (method) { case "doesExist" -> true; case "getName" -> "Send " + (slot + 1); default -> null; }));
            }
            final ISendBank bank = proxy (ISendBank.class, (method, args) -> switch (method) { case "getPageSize" -> 12; case "getScrollPosition" -> this.offset; case "getItem" -> sends.get ((Integer) args[0]); default -> null; });
            final ICursorTrack cursor = proxy (ICursorTrack.class, (method, args) -> switch (method) { case "doesExist" -> this.exists; case "getChannelID" -> this.channel; case "getSendBank" -> bank; default -> null; });
            final IProject project = proxy (IProject.class, (method, args) -> "getIdentity".equals (method) ? "project" : null);
            final IModel model = proxy (IModel.class, (method, args) -> switch (method) { case "getCursorTrack" -> cursor; case "getProject" -> project; default -> null; });
            final ModeManager modes = new ModeManager ();
            modes.register (Modes.VOLUME, proxy (IMode.class, (method, args) -> null));
            modes.register (Modes.PAN, proxy (IMode.class, (method, args) -> null));
            this.host = new ControllerSettingsHost (this.configuration, model, modes);
        }
    }

    private static final class Configuration extends PushConfiguration
    {
        private final List<String> commands = new ArrayList<> ();
        private boolean vu;
        private boolean accent;
        private int velocity = 127;
        private int offset;
        private Modes mode = Modes.VOLUME;
        private Configuration () { super (proxy (IHost.class, (method, args) -> null), new TwosComplementValueChanger (1024, 10), List.of ()); }
        @Override public boolean isAccentActive () { return this.accent; }
        @Override public int getFixedAccentValue () { return this.velocity; }
        @Override public void setAccentEnabled (final boolean enabled) { this.commands.add ("accent:" + enabled); }
        @Override public void setFixedAccentValue (final int velocity) { this.commands.add ("velocity:" + velocity); }
        @Override public boolean isEnableVUMeters () { return this.vu; }
        @Override public int getMixSendOffset () { return this.offset; }
        @Override public Modes getGlobalMixMode () { return this.mode; }
        @Override public void setVUMetersEnabled (final boolean value) { this.commands.add ("vu:" + value); }
        @Override public void setMixSendOffset (final int value) { this.commands.add ("offset:" + value); }
        @Override public void setGlobalMixMode (final Modes value) { this.commands.add ("mode:" + value); }
    }
}
