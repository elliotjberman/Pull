// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.framework.graphics.canvas.component;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.graphics.IGraphicsInfo;

/** Frozen startup message geometry; an empty message also fills an unused piano-roll column. */
public record MessageComponent (String text) implements IComponent
{
    @Override
    public void draw (final IGraphicsInfo info)
    {
        if (this.text == null || this.text.isEmpty ())
            return;
        final double menuHeight = 2 * info.getDimensions ().getMenuHeight ();
        final double headerHeight = (info.getBounds ().height () - 2 * menuHeight) / 2;
        ColorEx textColor = info.getConfiguration ().getColorText ();
        if (textColor.equals (info.getConfiguration ().getColorBorder ()))
            textColor = ColorEx.calcContrastColor (textColor);
        info.getContext ().drawTextInHeight (this.text, info.getBounds ().left (), menuHeight, headerHeight, textColor, headerHeight / 2.0);
    }
}
