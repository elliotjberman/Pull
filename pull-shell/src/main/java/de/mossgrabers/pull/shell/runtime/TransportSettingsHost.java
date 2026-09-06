// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import de.mossgrabers.pull.core.api.PreRoll;
import de.mossgrabers.pull.core.api.TransportSettingsSnapshot;
import de.mossgrabers.pull.core.api.effect.SetPreRollEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportSettingEffect;
import de.mossgrabers.pull.core.api.effect.TransportSetting;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Eager project transport properties; mapping and dependent-write policy remain in core. */
final class TransportSettingsHost
{
    private final Supplier<String> projectIdentity;
    private final BooleanSupplier ticks;
    private final Consumer<Boolean> setTicks;
    private final Supplier<PreRoll> preRoll;
    private final Consumer<PreRoll> setPreRoll;
    private final BooleanSupplier metronome;
    private final Consumer<Boolean> setMetronome;

    static TransportSettingsHost create (final ControllerHost host, final Supplier<String> projectIdentity)
    {
        final var transport = host.createTransport ();
        final SettableBooleanValue ticks = transport.isMetronomeTickPlaybackEnabled ();
        final SettableEnumValue preRoll = transport.preRoll ();
        final SettableBooleanValue metronome = transport.isMetronomeAudibleDuringPreRoll ();
        ticks.markInterested ();
        preRoll.markInterested ();
        metronome.markInterested ();
        return new TransportSettingsHost (projectIdentity, ticks::get, ticks::set, () -> fromHost (preRoll.get ()), value -> preRoll.set (toHost (value)), metronome::get, metronome::set);
    }

    TransportSettingsHost (final Supplier<String> projectIdentity, final BooleanSupplier ticks, final Consumer<Boolean> setTicks, final Supplier<PreRoll> preRoll, final Consumer<PreRoll> setPreRoll, final BooleanSupplier metronome, final Consumer<Boolean> setMetronome)
    {
        this.projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        this.ticks = Objects.requireNonNull (ticks, "ticks");
        this.setTicks = Objects.requireNonNull (setTicks, "setTicks");
        this.preRoll = Objects.requireNonNull (preRoll, "preRoll");
        this.setPreRoll = Objects.requireNonNull (setPreRoll, "setPreRoll");
        this.metronome = Objects.requireNonNull (metronome, "metronome");
        this.setMetronome = Objects.requireNonNull (setMetronome, "setMetronome");
    }

    TransportSettingsSnapshot snapshot ()
    {
        final String identity = Objects.requireNonNullElse (this.projectIdentity.get (), "");
        return identity.isBlank () ? TransportSettingsSnapshot.empty () : new TransportSettingsSnapshot (identity, this.ticks.getAsBoolean (), this.preRoll.get (), this.metronome.getAsBoolean ());
    }

    PreparedBoolean prepare (final SetTransportSettingEffect effect)
    {
        this.requireIdentity (effect.projectIdentity ());
        return new PreparedBoolean (effect);
    }

    PreparedPreRoll prepare (final SetPreRollEffect effect)
    {
        this.requireIdentity (effect.projectIdentity ());
        return new PreparedPreRoll (effect);
    }

    void apply (final PreparedBoolean prepared)
    {
        this.requireIdentity (prepared.effect ().projectIdentity ());
        final Consumer<Boolean> setter = prepared.effect ().setting () == TransportSetting.TICK_PLAYBACK ? this.setTicks : this.setMetronome;
        setter.accept (Boolean.valueOf (prepared.effect ().enabled ()));
    }

    void apply (final PreparedPreRoll prepared)
    {
        this.requireIdentity (prepared.effect ().projectIdentity ());
        this.setPreRoll.accept (prepared.effect ().preRoll ());
    }

    private void requireIdentity (final String expected)
    {
        if (!expected.equals (this.projectIdentity.get ()))
            throw new IllegalStateException ("Transport settings project changed before effect application");
    }

    private static PreRoll fromHost (final String value)
    {
        return switch (value)
        {
            case "one_bar" -> PreRoll.ONE_BAR;
            case "two_bars" -> PreRoll.TWO_BARS;
            case "four_bars" -> PreRoll.FOUR_BARS;
            default -> PreRoll.NONE;
        };
    }

    private static String toHost (final PreRoll value)
    {
        return switch (value)
        {
            case NONE -> "none";
            case ONE_BAR -> "one_bar";
            case TWO_BARS -> "two_bars";
            case FOUR_BARS -> "four_bars";
        };
    }

    record PreparedBoolean (SetTransportSettingEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedPreRoll (SetPreRollEffect effect) implements ControllerBridge.PreparedAction { }
}
