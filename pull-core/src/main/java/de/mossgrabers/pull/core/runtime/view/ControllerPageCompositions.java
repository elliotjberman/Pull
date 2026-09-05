// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/** Finite page replacements over the exact retained grid and musical-input composition. */
public final class ControllerPageCompositions
{
    /** A page-independent controller surface, containing the original retained view instances. */
    public record Background (SessionBankShape sessionBank, List<ControllerView> views, boolean noteController, boolean rawPitchBend)
    {
        public Background
        {
            sessionBank = Objects.requireNonNull (sessionBank, "sessionBank");
            views = List.copyOf (Objects.requireNonNull (views, "views"));
        }
    }

    private final Map<CompiledWorkspace, Background> backgrounds = new IdentityHashMap<> ();
    private final Map<String, Map<Background, CompiledWorkspace>> pages = new HashMap<> ();


    public void register (final CompiledWorkspace workspace, final Background background)
    {
        this.backgrounds.put (Objects.requireNonNull (workspace, "workspace"), Objects.requireNonNull (background, "background"));
    }


    public void registerAlias (final CompiledWorkspace workspace, final CompiledWorkspace source)
    {
        this.register (workspace, Objects.requireNonNull (this.backgrounds.get (source), "registered source composition"));
    }


    /** Compile every declared page/background pair once; no runtime claims or callback remapping. */
    public void compile (final ControllerLevelViews controllerViews, final Map<String, List<ControllerView>> pageViews)
    {
        if (!this.pages.isEmpty ())
            throw new IllegalStateException ("Page compositions are already compiled");
        for (final var page: pageViews.entrySet ())
        {
            final Map<Background, CompiledWorkspace> compositions = new HashMap<> ();
            for (final Background background: this.backgrounds.values ())
            {
                if (compositions.containsKey (background))
                    continue;
                final List<ControllerView> views = new ArrayList<> (page.getValue ());
                views.addAll (background.views ());
                final List<ControllerView> composed = background.rawPitchBend () ? controllerViews.composeWithRawPitchBend (views, background.noteController ()) : background.noteController () ? controllerViews.compose (views) : controllerViews.composeWithoutNoteController (views);
                final CompiledWorkspace workspace = CompiledWorkspace.compile (page.getKey (), background.sessionBank (), composed);
                if (!page.getKey ().equals (workspace.desiredControllerWorkspace ().installedModeId ()))
                    throw new IllegalArgumentException ("Page must declare its registered adapter: " + page.getKey ());
                compositions.put (background, workspace);
            }
            this.pages.put (page.getKey (), Map.copyOf (compositions));
        }
    }


    public boolean contains (final String modeId)
    {
        return this.pages.containsKey (modeId);
    }


    public CompiledWorkspace select (final String modeId, final CompiledWorkspace background)
    {
        return Objects.requireNonNull (this.pages.get (modeId).get (this.backgrounds.get (background)), "Declared page/background composition");
    }
}
