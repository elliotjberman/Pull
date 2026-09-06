// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableEnumValue;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationModeEffect;
import de.mossgrabers.pull.core.api.effect.ResetAutomationOverridesEffect;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;


/** One eagerly installed current-project Automation Write actuator and authoritative read-back. */
final class AutomationHost
{
    private final Supplier<String> projectIdentity;
    private final BooleanSupplier writing;
    private final Consumer<Boolean> write;
    private final BooleanSupplier stopOnRelease;
    private final Supplier<AutomationWriteMode> mode;
    private final Consumer<AutomationWriteMode> setMode;
    private final Runnable resetOverrides;


    static AutomationHost create (final ControllerHost host, final Supplier<String> projectIdentity, final BooleanSupplier stopOnRelease)
    {
        // API 25 retains the historical arranger name for Bitwig 6's unified Automation Write.
        // The separate launcher methods are deprecated and must not be used here.
        final var transport = host.createTransport ();
        final SettableBooleanValue writing = transport.isArrangerAutomationWriteEnabled ();
        final SettableEnumValue mode = transport.automationWriteMode ();
        writing.markInterested ();
        mode.markInterested ();
        return new AutomationHost (projectIdentity, writing::get, writing::set, stopOnRelease, () -> fromHost (mode.get ()), value -> mode.set (value.name ().toLowerCase (java.util.Locale.ROOT)), transport::resetAutomationOverrides);
    }


    AutomationHost (final Supplier<String> projectIdentity, final BooleanSupplier writing, final Consumer<Boolean> write, final BooleanSupplier stopOnRelease)
    {
        this (projectIdentity, writing, write, stopOnRelease, () -> AutomationWriteMode.UNKNOWN, ignored -> { throw new IllegalStateException ("Automation mode actuator unavailable"); }, () -> { throw new IllegalStateException ("Automation override actuator unavailable"); });
    }


    AutomationHost (final Supplier<String> projectIdentity, final BooleanSupplier writing, final Consumer<Boolean> write, final BooleanSupplier stopOnRelease, final Supplier<AutomationWriteMode> mode, final Consumer<AutomationWriteMode> setMode, final Runnable resetOverrides)
    {
        this.projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        this.writing = Objects.requireNonNull (writing, "writing");
        this.write = Objects.requireNonNull (write, "write");
        this.stopOnRelease = Objects.requireNonNull (stopOnRelease, "stopOnRelease");
        this.mode = Objects.requireNonNull (mode, "mode");
        this.setMode = Objects.requireNonNull (setMode, "setMode");
        this.resetOverrides = Objects.requireNonNull (resetOverrides, "resetOverrides");
    }


    AutomationSnapshot snapshot ()
    {
        final String identity = this.identity ();
        return identity.isBlank () ? AutomationSnapshot.empty () : new AutomationSnapshot (identity, this.writing.getAsBoolean (), this.stopOnRelease.getAsBoolean (), this.mode.get ());
    }


    PreparedWrite prepare (final SetAutomationWriteEffect effect)
    {
        this.requireIdentity (effect.projectIdentity ());
        return new PreparedWrite (effect);
    }


    void apply (final PreparedWrite prepared)
    {
        this.requireIdentity (prepared.effect ().projectIdentity ());
        this.write.accept (Boolean.valueOf (prepared.effect ().enabled ()));
    }


    PreparedMode prepare (final SetAutomationModeEffect effect)
    {
        this.requireIdentity (effect.projectIdentity ());
        return new PreparedMode (effect);
    }


    PreparedReset prepare (final ResetAutomationOverridesEffect effect)
    {
        this.requireIdentity (effect.projectIdentity ());
        return new PreparedReset (effect);
    }


    void apply (final PreparedMode prepared)
    {
        this.requireIdentity (prepared.effect ().projectIdentity ());
        this.setMode.accept (prepared.effect ().mode ());
    }


    void apply (final PreparedReset prepared)
    {
        this.requireIdentity (prepared.effect ().projectIdentity ());
        this.resetOverrides.run ();
    }


    private static AutomationWriteMode fromHost (final String mode)
    {
        return switch (mode)
        {
            case "latch" -> AutomationWriteMode.LATCH;
            case "touch" -> AutomationWriteMode.TOUCH;
            case "write" -> AutomationWriteMode.WRITE;
            default -> AutomationWriteMode.UNKNOWN;
        };
    }


    private void requireIdentity (final String expected)
    {
        if (!expected.equals (this.identity ()))
            throw new IllegalStateException ("Automation Write target changed before effect application");
    }


    private String identity ()
    {
        return Objects.requireNonNullElse (this.projectIdentity.get (), "");
    }


    record PreparedWrite (SetAutomationWriteEffect effect) implements ControllerBridge.PreparedAction
    {
    }

    record PreparedMode (SetAutomationModeEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedReset (ResetAutomationOverridesEffect effect) implements ControllerBridge.PreparedAction { }
}
