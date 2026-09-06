// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ParameterSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable definition of an upper controller page. Its fixed views own data projection, input
 * behavior and presentation; their claims define its footprint. Grid views and navigation state
 * are separate. Definitions retain view instances so an unchanged view keeps its gesture state.
 *
 * @param id Semantic identity, independent of any legacy mode alias
 * @param views Fixed page views, including separately composed display regions and button rows
 * @param navigation Declared arrow behavior used only when the grid does not own the arrows
 * @param parameterIndications Exact named parameters to highlight in the DAW while this page is open
 */
public record Page (PageId id, List<ControllerView> views, Optional<ControllerView> navigation, Set<ParameterSlot> parameterIndications)
{
    public Page
    {
        id = Objects.requireNonNull (id, "id");
        views = List.copyOf (Objects.requireNonNull (views, "views"));
        navigation = Objects.requireNonNull (navigation, "navigation");
        parameterIndications = Set.copyOf (Objects.requireNonNull (parameterIndications, "parameterIndications"));
        if (views.isEmpty () || views.size () > 32)
            throw new IllegalArgumentException ("A page requires between one and 32 fixed views");
        if (views.stream ().map (ControllerView::id).distinct ().count () != views.size ())
            throw new IllegalArgumentException ("A page cannot contain duplicate view identities");
        if (parameterIndications.size () > ParameterSlot.BANK_SIZE)
            throw new IllegalArgumentException ("Page parameter indications exceed the fixed encoder footprint");
    }

    public Page (final PageId id, final List<ControllerView> views, final ControllerView navigation)
    {
        this (id, views, Optional.of (Objects.requireNonNull (navigation, "navigation")), Set.of ());
    }
}
