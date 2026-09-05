// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;
import de.mossgrabers.pull.core.api.effect.SetControllerMappingStorageEffect;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Setting;

import java.util.Objects;
import java.util.function.Supplier;


/** Bounded document storage transport; its payload and allocation policy belong to the core. */
final class ControllerMappingStorageHost
{
    private final SettableStringValue setting;
    private final Supplier<String> documentId;
    private String observedValue;
    private String currentDocumentId = "";
    private long revision;
    private boolean writePending;
    private ControllerMappingStorageSnapshot snapshot = ControllerMappingStorageSnapshot.empty ();


    ControllerMappingStorageHost (final ControllerHost host, final Supplier<String> documentId)
    {
        this (Objects.requireNonNull (host, "host").getDocumentState (), documentId);
    }


    /** Test seam preserving actual observer delivery separately from command submission. */
    ControllerMappingStorageHost (final DocumentState documentState, final Supplier<String> documentId)
    {
        this.documentId = Objects.requireNonNull (documentId, "documentId");
        this.setting = Objects.requireNonNull (documentState, "documentState").getStringSetting (
            "Controller mapping storage", "Pull internal state", ControllerMappingStorageSnapshot.MAX_LENGTH, "");
        ((Setting) this.setting).hide ();
        this.setting.addValueObserver (this::observe);
    }


    synchronized ControllerMappingStorageSnapshot snapshot ()
    {
        this.refreshDocument ();
        final boolean available = !this.currentDocumentId.isBlank () && this.observedValue != null &&
            this.observedValue.length () <= ControllerMappingStorageSnapshot.MAX_LENGTH &&
            this.observedValue.equals (this.setting.get ());
        if (this.snapshot.available () != available || this.snapshot.revision () != this.revision)
            this.snapshot = available ? new ControllerMappingStorageSnapshot (true, this.revision, this.currentDocumentId, this.observedValue) :
                new ControllerMappingStorageSnapshot (false, this.revision, "", "");
        return this.snapshot;
    }


    synchronized boolean matches (final ControllerMappingContext context)
    {
        final ControllerMappingContext checked = Objects.requireNonNull (context, "context");
        final ControllerMappingStorageSnapshot current = this.snapshot ();
        return checked.active () && current.available () && checked.storageRevision () == current.revision () &&
            checked.documentId ().equals (current.documentId ());
    }


    /** Submit one raw compare-and-set; only a later host observer can acknowledge it. */
    synchronized boolean compareAndSet (final SetControllerMappingStorageEffect effect)
    {
        final SetControllerMappingStorageEffect checked = Objects.requireNonNull (effect, "effect");
        if (!this.matches (checked.context ()) || this.writePending || !checked.expectedValue ().equals (this.observedValue) ||
            !checked.expectedValue ().equals (this.setting.get ()))
            return false;
        if (checked.expectedValue ().equals (checked.value ()))
            return true;
        this.writePending = true;
        try
        {
            this.setting.set (checked.value ());
        }
        catch (final RuntimeException failure)
        {
            this.writePending = false;
            throw failure;
        }
        return true;
    }


    private synchronized void observe (final String value)
    {
        this.observedValue = value;
        this.revision++;
        this.writePending = false;
    }


    private void refreshDocument ()
    {
        final String observedDocument = Objects.requireNonNullElse (this.documentId.get (), "");
        if (!this.currentDocumentId.equals (observedDocument))
        {
            this.currentDocumentId = observedDocument;
            this.revision++;
            this.writePending = false;
        }
    }
}
