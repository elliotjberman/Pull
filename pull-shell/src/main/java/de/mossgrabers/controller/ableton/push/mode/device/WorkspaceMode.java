// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Stable layout adapter for the fully core-owned project-macro facet.
 */
public final class WorkspaceMode extends BaseMode<IParameter> implements WorkspaceFacetAdapter
{
    private static final IComponent BLANK_DISPLAY = info -> {
        final var bounds = info.getBounds ();
        info.getContext ().fillRectangle (bounds.left (), bounds.top (), bounds.width (), bounds.height (), ColorEx.BLACK);
    };

    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     */
    public WorkspaceMode (final PushControlSurface surface, final IModel model)
    {
        super ("Workspace", surface, model, model.getProject ().getParameterBank ());
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        this.reconcileWorkspaceFacets ();
        super.onActivate ();
    }


    /** {@inheritDoc} */
    @Override
    public void reconcileWorkspaceFacets ()
    {
        // Touch, relative mutation, rendering, track selection, and
        // controller feedback are core-owned, so this mode never binds its project bank.
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        // Project Macro touch, Delete reset, and automation-release policy are core-owned.
        // The permanent mode dispatcher remains available to unrelated inherited page profiles.
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        // Track selection is core-exclusive. Keep the inherited BaseMode row action inert.
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        return 0;
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        // The generic display base plane projects the composed core scene. Missing core output
        // stays blank instead of reviving the migrated stable page.
        display.addElement (BLANK_DISPLAY);
    }


}
