// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.Page;
import de.mossgrabers.pull.core.view.PageId;
import de.mossgrabers.pull.core.view.SurfaceArea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compiles typed pages over retained grids without consulting a stable mode registry. */
public final class ControllerPageCompositions
{
    /** A fixed grid/musical profile. All page variants reuse these exact view instances. */
    public record Background (String name, SessionBankShape sessionBank, List<ControllerView> views, boolean noteController, boolean rawPitchBend, List<ControllerView> legacyPageViews)
    {
        public Background
        {
            name = Objects.requireNonNull (name, "name");
            sessionBank = Objects.requireNonNull (sessionBank, "sessionBank");
            views = List.copyOf (Objects.requireNonNull (views, "views"));
            legacyPageViews = List.copyOf (Objects.requireNonNull (legacyPageViews, "legacyPageViews"));
        }
    }

    private final Map<Background, Map<PageId, Entry>> pages = new LinkedHashMap<> ();
    private final Map<Background, CompiledWorkspace> legacy = new LinkedHashMap<> ();

    /** Declare all page variants of a background once, validating every physical composition. */
    public void register (final ControllerLevelViews controllerViews, final Background background, final List<Page> definitions)
    {
        Objects.requireNonNull (controllerViews, "controllerViews");
        Objects.requireNonNull (background, "background");
        if (this.pages.containsKey (background))
            throw new IllegalStateException ("Background is already compiled: " + background.name ());
        final Map<PageId, Entry> compiled = new LinkedHashMap<> ();
        final boolean gridOwnsArrows = background.views ().stream ().flatMap (view -> view.claims ().stream ()).anyMatch (claim -> claim.area () == SurfaceArea.NAVIGATION_ARROWS);
        for (final Page page: List.copyOf (definitions))
        {
            final List<ControllerView> selectedViews = new ArrayList<> (page.views ());
            if (!gridOwnsArrows)
                page.navigation ().ifPresent (selectedViews::add);
            final CompiledWorkspace workspace = compile (controllerViews, background, page.id ().value (), selectedViews);
            if (compiled.putIfAbsent (page.id (), new Entry (page, workspace)) != null)
                throw new IllegalArgumentException ("Duplicate page definition: " + page.id ());
        }
        this.pages.put (background, Map.copyOf (compiled));
        this.legacy.put (background, compile (controllerViews, background, "legacy", background.legacyPageViews ()));
    }

    public CompiledWorkspace select (final PageId page, final Background background)
    {
        return this.entry (page, background).workspace ();
    }

    public Page definition (final PageId page, final Background background)
    {
        return this.entry (page, background).definition ();
    }

    public CompiledWorkspace legacy (final Background background)
    {
        return Objects.requireNonNull (this.legacy.get (background), "Declared background");
    }

    private Entry entry (final PageId page, final Background background)
    {
        final Map<PageId, Entry> available = Objects.requireNonNull (this.pages.get (background), "Declared background");
        return Objects.requireNonNull (available.get (page), "Declared page " + page.value () + " for " + background.name ());
    }

    private static CompiledWorkspace compile (final ControllerLevelViews controllerViews, final Background background, final String page, final List<ControllerView> pageViews)
    {
        final List<ControllerView> views = new ArrayList<> (pageViews);
        views.addAll (background.views ());
        final List<ControllerView> composed = background.rawPitchBend () ? controllerViews.composeWithRawPitchBend (views, background.noteController ()) : background.noteController () ? controllerViews.compose (views) : controllerViews.composeWithoutNoteController (views);
        return CompiledWorkspace.compile (background.name () + " / " + page, background.sessionBank (), composed);
    }

    private record Entry (Page definition, CompiledWorkspace workspace) { }
}
