// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;


/** Owns the ordered lifecycle of one core-selected Note layout and its musical input route. */
final class NotePerformanceHost
{
    private final ISelectedTrackNoteTarget selectedTarget;
    private final Function<DesiredControllerLayout, DesiredControllerLayout> layoutPreparer;
    private final Consumer<DesiredControllerLayout> layoutApplier;
    private final Runnable routeNeutralizer;

    private DesiredNotePerformance desired = DesiredNotePerformance.inactive ();
    private DesiredNoteInputRoute activeRoute = DesiredNoteInputRoute.disabled ();
    private DesiredControllerLayout commandedLayout = DesiredControllerLayout.empty ();
    private BooleanSupplier inputLifecycleIdle = () -> true;
    private boolean quarantinedUntilInputIdle;


    NotePerformanceHost (final ISelectedTrackNoteTarget selectedTarget, final Function<DesiredControllerLayout, DesiredControllerLayout> layoutPreparer, final Consumer<DesiredControllerLayout> layoutApplier, final Runnable routeNeutralizer)
    {
        this.selectedTarget = Objects.requireNonNull (selectedTarget, "selectedTarget");
        this.layoutPreparer = Objects.requireNonNull (layoutPreparer, "layoutPreparer");
        this.layoutApplier = Objects.requireNonNull (layoutApplier, "layoutApplier");
        this.routeNeutralizer = Objects.requireNonNull (routeNeutralizer, "routeNeutralizer");
    }


    void setInputLifecycleIdle (final BooleanSupplier idle)
    {
        this.inputLifecycleIdle = Objects.requireNonNull (idle, "idle");
    }


    DesiredNotePerformance prepare (final DesiredNotePerformance performance)
    {
        final DesiredNotePerformance requested = Objects.requireNonNull (performance, "performance");
        return new DesiredNotePerformance (this.layoutPreparer.apply (requested.layout ()), requested.inputRoute ());
    }


    void apply (final DesiredNotePerformance performance)
    {
        this.desired = Objects.requireNonNull (performance, "performance");
        this.reconcile (true);
    }


    void refresh ()
    {
        this.reconcile (false);
    }


    void invalidate ()
    {
        this.desired = DesiredNotePerformance.inactive ();
        this.applyLayout (DesiredControllerLayout.empty (), true);
        this.quarantinedUntilInputIdle = false;
        this.detach ();
    }


    private void reconcile (final boolean reassertLayout)
    {
        final DesiredNoteInputRoute requested = this.desired.inputRoute ();
        if (this.activeRoute.active () && !this.liveTargetMatches (this.activeRoute))
        {
            this.applyLayout (DesiredControllerLayout.empty (), false);
            this.detach ();
            this.quarantinedUntilInputIdle = !this.inputLifecycleIdle.getAsBoolean ();
        }

        if (requested.active ())
        {
            if (!this.liveTargetMatches (requested))
            {
                this.applyLayout (DesiredControllerLayout.empty (), reassertLayout);
                return;
            }
            if (this.quarantinedUntilInputIdle && !this.inputLifecycleIdle.getAsBoolean ())
            {
                this.applyLayout (DesiredControllerLayout.empty (), reassertLayout);
                return;
            }

            this.quarantinedUntilInputIdle = false;
            final boolean attaching = !requested.equals (this.activeRoute);
            if (attaching)
            {
                this.selectedTarget.setNoteInputRouteActive (true);
                this.activeRoute = requested;
            }
            this.applyLayout (this.desired.layout (), reassertLayout || attaching);
            return;
        }

        // A normal exit relinquishes the musical layout before removing its route. Keep the route
        // only until every physical gesture that could have begun under that layout is complete.
        this.applyLayout (this.desired.layout (), reassertLayout);
        if (this.activeRoute.active ())
        {
            if (!this.inputLifecycleIdle.getAsBoolean ())
                this.quarantinedUntilInputIdle = true;
            else
            {
                this.detach ();
                this.quarantinedUntilInputIdle = false;
            }
        }
        else if (this.quarantinedUntilInputIdle && this.inputLifecycleIdle.getAsBoolean ())
            this.quarantinedUntilInputIdle = false;
    }


    private boolean liveTargetMatches (final DesiredNoteInputRoute route)
    {
        return route.active () && route.targetGeneration () == this.selectedTarget.getGeneration () && route.targetChannelId ().equals (this.selectedTarget.getChannelID ()) && this.selectedTarget.doesExist () && this.selectedTarget.canHoldNotes ();
    }


    private void detach ()
    {
        if (!this.activeRoute.active ())
            return;
        try
        {
            this.routeNeutralizer.run ();
        }
        finally
        {
            this.selectedTarget.setNoteInputRouteActive (false);
            this.activeRoute = DesiredNoteInputRoute.disabled ();
        }
    }


    private void applyLayout (final DesiredControllerLayout layout, final boolean reassert)
    {
        if (!reassert && layout.equals (this.commandedLayout))
            return;
        this.layoutApplier.accept (layout);
        this.commandedLayout = layout;
    }
}
