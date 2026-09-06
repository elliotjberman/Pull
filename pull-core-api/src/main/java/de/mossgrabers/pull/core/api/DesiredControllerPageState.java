// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete replayable UI page state. All selection/history policy belongs to the core. */
public record DesiredControllerPageState (long revision, ControllerPageRef selected, ControllerPageRef previous, Optional<ControllerTemporaryPage> temporary, long acknowledgedRequestSequence, Set<ParameterSlot> parameterIndications)
{
    private static final DesiredControllerPageState EMPTY = new DesiredControllerPageState (0, ControllerPageRef.none (), ControllerPageRef.none (), Optional.empty (), 0);
    public DesiredControllerPageState
    {
        selected = Objects.requireNonNull (selected, "selected");
        previous = Objects.requireNonNull (previous, "previous");
        temporary = Objects.requireNonNull (temporary, "temporary");
        parameterIndications = Set.copyOf (Objects.requireNonNull (parameterIndications, "parameterIndications"));
        if (parameterIndications.size () > ParameterSlot.INSTALLED_TARGET_CAPACITY) throw new IllegalArgumentException ("Parameter indications exceed the installed canopy");
        if (revision < 0 || acknowledgedRequestSequence < 0) throw new IllegalArgumentException ("Page revision and request acknowledgement must be nonnegative");
    }
    public DesiredControllerPageState (final long revision, final ControllerPageRef selected, final ControllerPageRef previous, final Optional<ControllerTemporaryPage> temporary, final long acknowledgedRequestSequence)
    {
        this (revision, selected, previous, temporary, acknowledgedRequestSequence, Set.of ());
    }
    public static DesiredControllerPageState empty () { return EMPTY; }
    public ControllerPageRef effectivePage () { return this.temporary.map (ControllerTemporaryPage::page).orElse (this.selected); }
    public long temporaryToken () { return this.temporary.map (ControllerTemporaryPage::token).orElse (Long.valueOf (0)).longValue (); }
}
