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
import de.mossgrabers.pull.core.api.ControllerHardwareSettingsSnapshot;
import de.mossgrabers.pull.core.api.RibbonSettingsSnapshot;
import de.mossgrabers.pull.core.api.effect.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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
        fixture.configuration.returnMillis = 750;
        fixture.configuration.returnCurve = "Ease-out";
        final var after = fixture.host.snapshot ();
        assertTrue (after.vuMetersEnabled ());
        assertEquals (4, after.mixSendOffset ());
        assertEquals ("PAN", after.globalMixMode ());
        assertEquals (750, after.parameterReturnMillis ());
        assertEquals ("Ease-out", after.parameterReturnCurve ());
    }

    @Test
    void modePreferenceRequiresAnInstalledEntry ()
    {
        final Fixture fixture = new Fixture ();
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "not-a-mode")));
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "SEND8")));
        assertTrue (fixture.configuration.commands.isEmpty ());
    }

    @Test
    void accentSettingsAreObservedOnlyAfterCallbacks ()
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
    }

    @Test
    void hardwareRequestsWaitForObservedPreferencesAndVelocityCurve ()
    {
        final Fixture fixture = new Fixture ();
        final var before = fixture.host.snapshot ();
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS, 37)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.LED_BRIGHTNESS, 83)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.PAD_SENSITIVITY, 2)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.PAD_GAIN, 9)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.PAD_DYNAMICS, 7)));
        assertEquals (List.of ("display:37", "leds:83", "sensitivity:2", "gain:9", "dynamics:7"), fixture.configuration.commands);
        assertEquals (before, fixture.host.snapshot (), "submitting hardware settings cannot publish the requested values or a new curve");

        fixture.configuration.display = 37;
        fixture.configuration.leds = 83;
        fixture.configuration.sensitivity = 2;
        fixture.configuration.gain = 9;
        fixture.configuration.dynamics = 7;
        fixture.curve = IntStream.range (0, 128).map (index -> Math.min (127, index + 1)).toArray ();
        final var observed = fixture.host.snapshot ().hardware ();
        assertEquals (new ControllerHardwareSettingsSnapshot (true, 37, 83, 2, 9, 7, IntStream.range (0, 128).map (index -> Math.min (127, index + 1)).boxed ().toList ()), observed);

        fixture.curve[0] = 45;
        assertEquals (1, observed.velocityCurve ().get (0), "later hardware table changes cannot mutate a published observation");
        fixture.configuration.sensitivity = 3;
        assertEquals (45, fixture.host.snapshot ().hardware ().velocityCurve ().get (0));
    }

    @Test
    void unchangedPreferencesDoNotResampleTheHardwareCurve ()
    {
        final Fixture fixture = new Fixture ();
        final var initial = fixture.host.snapshot ().hardware ();
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.PAD_GAIN, 8)));
        fixture.host.snapshot ();
        assertEquals (initial, fixture.host.snapshot ().hardware ());
        assertEquals (1, fixture.curveSamples, "unchanged observations and submitted writes must reuse the immutable hardware table");

        fixture.configuration.gain = 8;
        assertEquals (8, fixture.host.snapshot ().hardware ().gain ());
        fixture.host.snapshot ();
        assertEquals (2, fixture.curveSamples, "a changed observed preference refreshes the hardware table once");

        fixture.configuration.display = 255;
        assertEquals (ControllerHardwareSettingsSnapshot.empty (), fixture.host.snapshot ().hardware ());
        assertEquals (2, fixture.curveSamples, "unavailable preferences must not sample the hardware table");
        fixture.configuration.display = 100;
        fixture.host.snapshot ();
        assertEquals (3, fixture.curveSamples, "availability recovery must refresh rather than revive a retired cached table");
    }

    @Test
    void hardwareWaitsForBrightnessObserversBeforeSamplingItsVelocityCurve ()
    {
        final Fixture fixture = new Fixture ();
        fixture.configuration.display = 255;
        fixture.configuration.leds = 127;
        fixture.curveAvailable = false;
        assertEquals (ControllerHardwareSettingsSnapshot.empty (), fixture.host.snapshot ().hardware ());
        fixture.configuration.display = 100;
        assertEquals (ControllerHardwareSettingsSnapshot.empty (), fixture.host.snapshot ().hardware (), "one brightness callback does not establish the complete settings tuple");
        fixture.configuration.leds = 100;
        fixture.curveAvailable = true;
        assertTrue (fixture.host.snapshot ().hardware ().available ());
    }

    @Test
    void ribbonSettingsArePublishedOnlyAfterObservedReadback ()
    {
        final Fixture fixture = new Fixture ();
        final var before = fixture.host.snapshot ();
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.RIBBON_FUNCTION, 4)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.RIBBON_CC, 74)));
        fixture.host.apply (fixture.host.prepare (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.RIBBON_NOTE_REPEAT, 2)));
        assertEquals (List.of ("ribbon-function:4", "ribbon-cc:74", "ribbon-repeat:2"), fixture.configuration.commands);
        assertEquals (before, fixture.host.snapshot (), "submitting strip preferences does not advance their observed values");

        fixture.configuration.ribbonFunction = 4;
        fixture.configuration.ribbonCc = 74;
        fixture.configuration.ribbonRepeat = 2;
        assertEquals (new RibbonSettingsSnapshot (true, 4, 74, 2), fixture.host.snapshot ().ribbon ());
        assertEquals (1, fixture.curveSamples, "strip preferences do not resample unrelated hardware calibration");
    }

    private static final class Fixture
    {
        private String channel = "pinned-model-cursor";
        private boolean exists = true;
        private int offset;
        private int[] curve = IntStream.range (0, 128).map (index -> 64).toArray ();
        private boolean curveAvailable = true;
        private int curveSamples;
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
            final de.mossgrabers.framework.daw.ITransport transport = proxy (de.mossgrabers.framework.daw.ITransport.class, (method, args) -> "getQuartersPerMeasure".equals (method) ? 4 : null);
            final IModel model = proxy (IModel.class, (method, args) -> switch (method) { case "getCursorTrack" -> cursor; case "getProject" -> project; case "getTransport" -> transport; default -> null; });
            final ModeManager modes = new ModeManager ();
            modes.register (Modes.VOLUME, proxy (IMode.class, (method, args) -> null));
            modes.register (Modes.PAN, proxy (IMode.class, (method, args) -> null));
            this.host = new ControllerSettingsHost (this.configuration, model, modes, () -> {
                assertTrue (this.curveAvailable, "hardware velocity data must not be sampled before preferences are available");
                this.curveSamples++;
                return this.curve;
            });
        }
    }

    private static final class Configuration extends PushConfiguration
    {
        private final List<String> commands = new ArrayList<> ();
        private boolean vu;
        private boolean accent;
        private int velocity = 127;
        private int offset;
        private int returnMillis;
        private String returnCurve = "Linear";
        private Modes mode = Modes.VOLUME;
        private int display = 100;
        private int leds = 100;
        private int sensitivity = 5;
        private int gain = 5;
        private int dynamics = 5;
        private int ribbonFunction;
        private int ribbonCc = 1;
        private int ribbonRepeat = 1;
        private Configuration () { super (proxy (IHost.class, (method, args) -> null), new TwosComplementValueChanger (1024, 10), List.of ()); }
        @Override public String getParameterReturnCurve () { return this.returnCurve; }
        @Override public int getParameterReturnMillis () { return this.returnMillis; }
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
        @Override public int getDisplayBrightness () { return this.display; }
        @Override public int getLedBrightness () { return this.leds; }
        @Override public int getPadSensitivityPush2 () { return this.sensitivity; }
        @Override public int getPadGainPush2 () { return this.gain; }
        @Override public int getPadDynamicsPush2 () { return this.dynamics; }
        @Override public void setDisplayBrightness (final int value) { this.commands.add ("display:" + value); }
        @Override public void setLEDBrightness (final int value) { this.commands.add ("leds:" + value); }
        @Override public void setPadSensitivityPush2 (final int value) { this.commands.add ("sensitivity:" + value); }
        @Override public void setPadGainPush2 (final int value) { this.commands.add ("gain:" + value); }
        @Override public void setPadDynamicsPush2 (final int value) { this.commands.add ("dynamics:" + value); }
        @Override public int getRibbonMode () { return this.ribbonFunction; }
        @Override public int getRibbonModeCCVal () { return this.ribbonCc; }
        @Override public int getRibbonNoteRepeat () { return this.ribbonRepeat; }
        @Override public void setRibbonMode (final int value) { this.commands.add ("ribbon-function:" + value); }
        @Override public void setRibbonModeCC (final int value) { this.commands.add ("ribbon-cc:" + value); }
        @Override public void setRibbonNoteRepeat (final int value) { this.commands.add ("ribbon-repeat:" + value); }
    }
}
