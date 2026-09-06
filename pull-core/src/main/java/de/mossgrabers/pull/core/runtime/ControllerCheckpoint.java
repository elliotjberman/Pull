// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.ControllerTemporaryPage;
import de.mossgrabers.pull.core.api.DesiredControllerPageState;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Bounded value-only checkpoint. Views, renderers and executable continuations are never saved. */
record ControllerCheckpoint (WorkspaceSelection.Id workspace, WorkspaceSelection.Destination selectedDestination, WorkspaceSelection.Destination pendingDestination, String engineOwnerIdentity, boolean engineOwnerPlaying, boolean inputOutputSelected, int sendOffset, Optional<DesiredControllerPageState> page)
{
    static ControllerCheckpoint empty ()
    {
        return new ControllerCheckpoint (WorkspaceSelection.Id.DEFAULT, WorkspaceSelection.Destination.NONE, WorkspaceSelection.Destination.NONE, "", false, false, 0, Optional.empty ());
    }

    StateEnvelope encode ()
    {
        try
        {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream ();
            final DataOutputStream out = new DataOutputStream (bytes);
            out.writeByte (this.workspace.ordinal ());
            out.writeByte (this.selectedDestination.ordinal ());
            out.writeByte (this.pendingDestination.ordinal ());
            out.writeUTF (this.engineOwnerIdentity);
            out.writeBoolean (this.engineOwnerPlaying);
            out.writeBoolean (this.inputOutputSelected);
            out.writeByte (this.sendOffset);
            final DesiredControllerPageState navigation = this.page.orElseThrow ();
            out.writeLong (navigation.revision ());
            writePage (out, navigation.selected ());
            writePage (out, navigation.previous ());
            out.writeLong (navigation.temporaryToken ());
            if (navigation.temporary ().isPresent ()) writePage (out, navigation.temporary ().get ().page ());
            out.writeLong (navigation.acknowledgedRequestSequence ());
            return new StateEnvelope (PullCoreProvider.STATE_SCHEMA, PullCoreProvider.STATE_SCHEMA_VERSION, bytes.toByteArray ());
        }
        catch (final IOException failure) { throw new IllegalStateException ("Cannot encode in-memory checkpoint", failure); }
    }

    static ControllerCheckpoint decode (final Optional<StateEnvelope> previous)
    {
        if (previous.isEmpty ()) return empty ();
        final StateEnvelope envelope = previous.get ();
        if (!PullCoreProvider.STATE_SCHEMA.equals (envelope.schema ()) || envelope.version () != PullCoreProvider.STATE_SCHEMA_VERSION || envelope.payload ().length > 8192) return empty ();
        try
        {
            final DataInputStream in = new DataInputStream (new ByteArrayInputStream (envelope.payload ()));
            final var workspace = WorkspaceSelection.Id.values ()[in.readUnsignedByte ()];
            final var selected = WorkspaceSelection.Destination.values ()[in.readUnsignedByte ()];
            final var pending = WorkspaceSelection.Destination.values ()[in.readUnsignedByte ()];
            final String owner = in.readUTF ();
            final boolean playing = in.readBoolean ();
            final boolean inputOutput = in.readBoolean ();
            final int send = in.readUnsignedByte ();
            if (owner.length () > 1024 || send != 0 && send != 4 || pending != WorkspaceSelection.Destination.NONE && pending != selected) return empty ();
            final long revision = in.readLong ();
            final ControllerPageRef page = readPage (in);
            final ControllerPageRef previousPage = readPage (in);
            final long token = in.readLong ();
            final Optional<ControllerTemporaryPage> temporary = token == 0 ? Optional.empty () : Optional.of (new ControllerTemporaryPage (token, readPage (in)));
            final DesiredControllerPageState navigation = new DesiredControllerPageState (revision, page, previousPage, temporary, in.readLong ());
            if (in.available () != 0 || !page.isPresent ()) return empty ();
            return new ControllerCheckpoint (workspace, selected, pending, owner, playing, inputOutput, send, Optional.of (navigation));
        }
        catch (final IOException | IllegalArgumentException | IndexOutOfBoundsException invalid) { return empty (); }
    }

    private static void writePage (final DataOutputStream out, final ControllerPageRef page) throws IOException
    {
        out.writeByte (page.kind ().ordinal ());
        out.writeUTF (page.id ());
        out.writeUTF (page.legacyAlias ());
    }

    private static ControllerPageRef readPage (final DataInputStream in) throws IOException
    {
        return new ControllerPageRef (ControllerPageRef.Kind.values ()[in.readUnsignedByte ()], in.readUTF (), in.readUTF ());
    }
}
