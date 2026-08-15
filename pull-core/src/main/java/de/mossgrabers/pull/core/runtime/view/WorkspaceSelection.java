// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;


/**
 * Shared core-owned selection of one compiled controller workspace.
 */
public final class WorkspaceSelection
{
    /** Physical workspace-selection gestures whose temporary lifetime spans workspace changes. */
    public enum Gesture
    {
        NOTE,
        SESSION
    }

    /** Available compiled workspaces. */
    public enum Id
    {
        /** Established controller behavior without a stable layout override. */
        DEFAULT,
        /** Project macros, upper Session grid, and lower Drum Controller. */
        VS_LIVE
    }

    /** Explicit stable destination currently awaiting controller-layout acknowledgement. */
    public enum Destination
    {
        /** No destination handoff is pending. */
        NONE,
        /** Track/Mix page with the full Session view. */
        SESSION,
        /** Track/Mix page around the stable preferred Note view. */
        NOTE
    }

    private Id          active;
    private Destination pendingDestination;
    private long        pendingAfterLayoutGeneration = -1;
    private boolean     sessionDestinationAcknowledged;
    private long        requestSequence;
    private final Map<Gesture, HeldSelection> heldSelections = new EnumMap<> (Gesture.class);
    private PreferredNoteViewRequest preferredNoteViewRequest;


    /**
     * Constructor.
     *
     * @param active Initial workspace
     */
    public WorkspaceSelection (final Id active)
    {
        this (active, Destination.NONE);
    }


    /**
     * Constructor with a replayed destination handoff.
     *
     * @param active Initial workspace
     * @param pendingDestination Destination still awaiting layout read-back
     */
    public WorkspaceSelection (final Id active, final Destination pendingDestination)
    {
        this.active = Objects.requireNonNull (active, "active");
        this.pendingDestination = Objects.requireNonNull (pendingDestination, "pendingDestination");
    }


    /**
     * Get the active workspace.
     *
     * @return Workspace ID
     */
    public Id active ()
    {
        return this.active;
    }


    /**
     * Get the sequence of explicit workspace-selection requests.
     *
     * @return Request sequence
     */
    public long requestSequence ()
    {
        return this.requestSequence;
    }


    /** Get the stable destination still awaiting authoritative layout read-back. */
    public Destination pendingDestination ()
    {
        return this.pendingDestination;
    }


    /**
     * Select a workspace.
     *
     * @param workspace Workspace ID
     */
    public void select (final Id workspace)
    {
        this.select (workspace, Destination.NONE);
    }


    /**
     * Select a workspace and an optional stable destination handoff.
     *
     * @param workspace Workspace ID
     * @param destination Stable destination to realize and acknowledge
     */
    public void select (final Id workspace, final Destination destination)
    {
        this.select (workspace, destination, -1);
    }


    private void select (final Id workspace, final Destination destination, final long afterLayoutGeneration)
    {
        this.active = Objects.requireNonNull (workspace, "workspace");
        this.pendingDestination = Objects.requireNonNull (destination, "destination");
        this.pendingAfterLayoutGeneration = destination == Destination.NONE ? -1 : afterLayoutGeneration;
        this.sessionDestinationAcknowledged = false;
        this.requestSequence++;
    }


    /** Begin one selection gesture while retaining the destination it may temporarily replace. */
    public boolean beginGesture (final Gesture gesture, final Id workspace, final Destination destination, final ControllerLayoutSnapshot layout, final boolean switched)
    {
        final Gesture checkedGesture = Objects.requireNonNull (gesture, "gesture");
        final HeldSelection previous = this.heldSelections.putIfAbsent (checkedGesture, new HeldSelection (this.active, this.currentDestination (layout), switched, false));
        if (previous != null)
            return false;
        this.select (workspace, switched ? destination : Destination.NONE, layout.generation ());
        return true;
    }


    /** Mark a held selection as temporary, either from a long press or Session interaction. */
    public void makeTemporary (final Gesture gesture)
    {
        this.heldSelections.computeIfPresent (Objects.requireNonNull (gesture, "gesture"), (ignored, held) -> held.withTemporary ());
    }


    /** End a selection gesture and restore its prior destination only when it became temporary. */
    public void endGesture (final Gesture gesture, final ControllerLayoutSnapshot layout)
    {
        final HeldSelection held = this.heldSelections.remove (Objects.requireNonNull (gesture, "gesture"));
        if (held != null && held.switched () && held.temporary ())
            this.select (held.workspace (), held.destination (), Objects.requireNonNull (layout, "layout").generation ());
        else if (gesture == Gesture.SESSION && this.pendingDestination == Destination.SESSION && this.sessionDestinationAcknowledged)
        {
            this.pendingDestination = Destination.NONE;
            this.pendingAfterLayoutGeneration = -1;
            this.sessionDestinationAcknowledged = false;
        }
    }


    /** Keep a requested per-track preference active until stable read-back acknowledges it. */
    public void requestPreferredNoteView (final NoteViewSnapshot target, final ControllerNoteView view)
    {
        final NoteViewSnapshot checked = Objects.requireNonNull (target, "target");
        final ControllerNoteView preferred = Objects.requireNonNull (view, "view");
        if (!preferred.isPresent () || checked.targetChannelId ().isBlank () || checked.trackPosition () < 0)
            throw new IllegalArgumentException ("preferred note view requires an existing selected target");
        this.preferredNoteViewRequest = new PreferredNoteViewRequest (checked.targetGeneration (), checked.targetChannelId (), checked.trackPosition (), preferred);
    }


    /** Resolve authoritative preference plus an exact still-unacknowledged request. */
    public ControllerNoteView preferredNoteView (final NoteViewSnapshot target)
    {
        final NoteViewSnapshot checked = Objects.requireNonNull (target, "target");
        return this.preferredNoteViewRequest != null && this.preferredNoteViewRequest.matches (checked) ? this.preferredNoteViewRequest.view () : checked.preferredView ();
    }


    /**
     * Retire a destination request only after the stable controller reports the requested layout.
     *
     * @param layout Authoritative stable layout read-back
     */
    public void observe (final ControllerLayoutSnapshot layout)
    {
        final ControllerLayoutSnapshot observed = Objects.requireNonNull (layout, "layout");
        final boolean trackPage = "TRACK".equals (observed.modeId ());
        if (observed.generation () <= this.pendingAfterLayoutGeneration)
            return;
        if (this.pendingDestination == Destination.SESSION && trackPage && "SESSION".equals (observed.viewId ()))
        {
            if (this.heldSelections.containsKey (Gesture.SESSION))
            {
                this.sessionDestinationAcknowledged = true;
                return;
            }
            this.pendingDestination = Destination.NONE;
        }
        else if (this.pendingDestination == Destination.NOTE && trackPage && ControllerNoteView.fromStableId (observed.viewId ()).isPresent ())
            this.pendingDestination = Destination.NONE;
        if (this.pendingDestination == Destination.NONE)
            this.pendingAfterLayoutGeneration = -1;
    }


    /** Retire a preference request only after the stable preference map reports it. */
    public void observe (final NoteViewSnapshot noteView)
    {
        final NoteViewSnapshot observed = Objects.requireNonNull (noteView, "noteView");
        if (this.preferredNoteViewRequest != null && this.preferredNoteViewRequest.matches (observed) && this.preferredNoteViewRequest.view () == observed.preferredView ())
            this.preferredNoteViewRequest = null;
    }


    private Destination currentDestination (final ControllerLayoutSnapshot layout)
    {
        if (this.pendingDestination != Destination.NONE)
            return this.pendingDestination;
        final ControllerLayoutSnapshot observed = Objects.requireNonNull (layout, "layout");
        if (this.active != Id.DEFAULT || !"TRACK".equals (observed.modeId ()))
            return Destination.NONE;
        if ("SESSION".equals (observed.viewId ()))
            return Destination.SESSION;
        return ControllerNoteView.fromStableId (observed.viewId ()).isPresent () ? Destination.NOTE : Destination.NONE;
    }


    private record HeldSelection (Id workspace, Destination destination, boolean switched, boolean temporary)
    {
        private HeldSelection withTemporary ()
        {
            return new HeldSelection (this.workspace, this.destination, this.switched, true);
        }
    }


    private record PreferredNoteViewRequest (long generation, String channelId, int position, ControllerNoteView view)
    {
        private boolean matches (final NoteViewSnapshot snapshot)
        {
            return this.generation == snapshot.targetGeneration () && this.position == snapshot.trackPosition () && this.channelId.equals (snapshot.targetChannelId ());
        }
    }
}
