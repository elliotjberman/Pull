// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.SettableBooleanValue;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;

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


    static AutomationHost create (final ControllerHost host, final Supplier<String> projectIdentity, final BooleanSupplier stopOnRelease)
    {
        // API 25 retains the historical arranger name for Bitwig 6's unified Automation Write.
        // The separate launcher methods are deprecated and must not be used here.
        final SettableBooleanValue writing = host.createTransport ().isArrangerAutomationWriteEnabled ();
        writing.markInterested ();
        return new AutomationHost (projectIdentity, writing::get, writing::set, stopOnRelease);
    }


    AutomationHost (final Supplier<String> projectIdentity, final BooleanSupplier writing, final Consumer<Boolean> write, final BooleanSupplier stopOnRelease)
    {
        this.projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        this.writing = Objects.requireNonNull (writing, "writing");
        this.write = Objects.requireNonNull (write, "write");
        this.stopOnRelease = Objects.requireNonNull (stopOnRelease, "stopOnRelease");
    }


    AutomationSnapshot snapshot ()
    {
        final String identity = this.identity ();
        return identity.isBlank () ? AutomationSnapshot.empty () : new AutomationSnapshot (identity, this.writing.getAsBoolean (), this.stopOnRelease.getAsBoolean ());
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
}
