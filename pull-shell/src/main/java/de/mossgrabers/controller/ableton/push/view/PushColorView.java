// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.view.ColorView;

/** Preserves existing color input while core owns the complete palette light output. */
public final class PushColorView extends ColorView<PushControlSurface, PushConfiguration>
{
    public PushColorView (final PushControlSurface surface, final IModel model) { super (surface, model); }
    @Override public void drawGrid () { }
}
