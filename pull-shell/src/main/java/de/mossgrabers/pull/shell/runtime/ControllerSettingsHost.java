// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.ControllerHardwareSettingsSnapshot;
import de.mossgrabers.pull.core.api.ControllerSettingsSnapshot;
import de.mossgrabers.pull.core.api.SessionSettingsSnapshot;
import de.mossgrabers.pull.core.api.CursorSendBankSnapshot;
import de.mossgrabers.pull.core.api.RibbonSettingsSnapshot;
import de.mossgrabers.pull.core.api.effect.SetControllerBooleanSettingEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerModeSettingEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/** Mechanical access to installed preferences and the existing eight-send model cursor window. */
final class ControllerSettingsHost
{
    private final PushConfiguration configuration;
    private final IModel model;
    private final ModeManager modes;
    private final Supplier<int[]> velocityCurve;
    private ControllerHardwareSettingsSnapshot hardware = ControllerHardwareSettingsSnapshot.empty ();
    private CursorIdentity cursorIdentity;
    private long generation;

    ControllerSettingsHost (final PushConfiguration configuration, final IModel model, final ModeManager modes, final Supplier<int[]> velocityCurve)
    {
        this.configuration = Objects.requireNonNull (configuration, "configuration");
        this.model = Objects.requireNonNull (model, "model");
        this.modes = Objects.requireNonNull (modes, "modes");
        this.velocityCurve = Objects.requireNonNull (velocityCurve, "velocityCurve");
    }

    ControllerSettingsSnapshot snapshot ()
    {
        final SessionSettingsSnapshot session = new SessionSettingsSnapshot (true, this.configuration.isSelectClipOnLaunch (), this.configuration.getActionForRecArmedPad (), this.configuration.getNewClipLenghthInBeats (this.model.getTransport ().getQuartersPerMeasure ()), this.configuration.isDrawRecordStripe ());
        final RibbonSettingsSnapshot ribbon = new RibbonSettingsSnapshot (true, this.configuration.getRibbonMode (), this.configuration.getRibbonModeCCVal (), this.configuration.getRibbonNoteRepeat ());
        return new ControllerSettingsSnapshot (true, this.configuration.isEnableVUMeters (), this.configuration.getGlobalMixMode ().name (), this.configuration.getMixSendOffset (), this.cursorSends (), this.configuration.isAccentActive (), this.configuration.getFixedAccentValue (), session, this.hardwareSettings (), ribbon, this.configuration.getParameterReturnMillis (), this.configuration.getParameterReturnCurve ());
    }

    private ControllerHardwareSettingsSnapshot hardwareSettings ()
    {
        final int display = this.configuration.getDisplayBrightness ();
        final int leds = this.configuration.getLedBrightness ();
        final int sensitivity = this.configuration.getPadSensitivityPush2 ();
        final int gain = this.configuration.getPadGainPush2 ();
        final int dynamics = this.configuration.getPadDynamicsPush2 ();
        // Legacy pre-observer brightness values use transport units, outside the settings range.
        if (display < 0 || display > 100 || leds < 0 || leds > 100 || sensitivity < 0 || sensitivity > 10 || gain < 0 || gain > 10 || dynamics < 0 || dynamics > 10)
        {
            if (this.hardware.available ()) this.hardware = ControllerHardwareSettingsSnapshot.empty ();
            return this.hardware;
        }
        if (!this.hardware.available () || this.hardware.displayBrightness () != display || this.hardware.ledBrightness () != leds || this.hardware.sensitivity () != sensitivity || this.hardware.gain () != gain || this.hardware.dynamics () != dynamics)
            this.hardware = new ControllerHardwareSettingsSnapshot (true, display, leds, sensitivity, gain, dynamics, Arrays.stream (this.velocityCurve.get ()).boxed ().toList ());
        return this.hardware;
    }

    private CursorSendBankSnapshot cursorSends ()
    {
        final ICursorTrack cursor = this.model.getCursorTrack ();
        final ISendBank bank = cursor == null ? null : cursor.getSendBank ();
        if (cursor == null || !cursor.doesExist () || cursor.getChannelID () == null || cursor.getChannelID ().isBlank () || bank == null)
        {
            this.cursorIdentity = null;
            return CursorSendBankSnapshot.empty ();
        }
        final CursorIdentity identity = new CursorIdentity (this.model.getProject ().getIdentity (), cursor.getChannelID (), bank.getScrollPosition ());
        if (!identity.equals (this.cursorIdentity))
        {
            this.cursorIdentity = identity;
            this.generation++;
        }
        final ArrayList<CursorSendBankSnapshot.Send> sends = new ArrayList<> (CursorSendBankSnapshot.CAPACITY);
        for (int index = 0; index < Math.min (CursorSendBankSnapshot.CAPACITY, bank.getPageSize ()); index++)
        {
            final ISend send = bank.getItem (index);
            final boolean exists = send != null && send.doesExist ();
            sends.add (new CursorSendBankSnapshot.Send (exists, exists ? Objects.requireNonNullElse (send.getName (), "") : ""));
        }
        return new CursorSendBankSnapshot (this.generation, identity.channel (), identity.offset (), sends);
    }

    PreparedBoolean prepare (final SetControllerBooleanSettingEffect effect) { return new PreparedBoolean (effect); }
    PreparedInteger prepare (final SetControllerIntegerSettingEffect effect) { return new PreparedInteger (effect); }
    PreparedMode prepare (final SetControllerModeSettingEffect effect)
    {
        this.requireInstalledMode (effect.modeId ());
        return new PreparedMode (effect);
    }

    void apply (final PreparedBoolean prepared)
    {
        switch (prepared.effect ().setting ())
        {
            case VU_METERS -> this.configuration.setVUMetersEnabled (prepared.effect ().enabled ());
            case ACCENT_ENABLED -> this.configuration.setAccentEnabled (prepared.effect ().enabled ());
        }
    }

    void apply (final PreparedInteger prepared)
    {
        switch (prepared.effect ().setting ())
        {
            case MIX_SEND_OFFSET -> this.configuration.setMixSendOffset (prepared.effect ().value ());
            case ACCENT_VELOCITY -> this.configuration.setFixedAccentValue (prepared.effect ().value ());
            case DISPLAY_BRIGHTNESS -> this.configuration.setDisplayBrightness (prepared.effect ().value ());
            case LED_BRIGHTNESS -> this.configuration.setLEDBrightness (prepared.effect ().value ());
            case PAD_SENSITIVITY -> this.configuration.setPadSensitivityPush2 (prepared.effect ().value ());
            case PAD_GAIN -> this.configuration.setPadGainPush2 (prepared.effect ().value ());
            case PAD_DYNAMICS -> this.configuration.setPadDynamicsPush2 (prepared.effect ().value ());
            case RIBBON_FUNCTION -> this.configuration.setRibbonMode (prepared.effect ().value ());
            case RIBBON_CC -> this.configuration.setRibbonModeCC (prepared.effect ().value ());
            case RIBBON_NOTE_REPEAT -> this.configuration.setRibbonNoteRepeat (prepared.effect ().value ());
        }
    }

    void apply (final PreparedMode prepared)
    {
        final Modes mode = this.requireInstalledMode (prepared.effect ().modeId ());
        switch (prepared.effect ().setting ()) { case GLOBAL_MIX_MODE -> this.configuration.setGlobalMixMode (mode); }
    }

    private Modes requireInstalledMode (final String id)
    {
        final Modes mode = Modes.valueOf (id);
        if (this.modes.get (mode) == null)
            throw new IllegalArgumentException ("Controller mode preference references an uninstalled mode: " + id);
        return mode;
    }

    record PreparedBoolean (SetControllerBooleanSettingEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedInteger (SetControllerIntegerSettingEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedMode (SetControllerModeSettingEffect effect) implements ControllerBridge.PreparedAction { }
    private record CursorIdentity (String project, String channel, int offset) { }
}
