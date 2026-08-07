// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import de.mossgrabers.framework.graphics.IBounds;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;

import java.util.List;
import java.util.Objects;


/** Stable compositor which confines reloadable mixer controls below menus and above the footer. */
public final class MixerControlsComponent implements IComponent
{
    private final MixerControlsDisplay display;
    private final List<DisplaySceneComponent> scenes;


    /**
     * Constructor.
     *
     * @param display Structurally contained mixer-control scenes
     */
    public MixerControlsComponent (final MixerControlsDisplay display)
    {
        this.display = Objects.requireNonNull (display, "display");
        this.scenes = display.controls ().stream ().map (MixerControlDisplay::scene).map (DisplaySceneComponent::new).toList ();
    }


    /** {@inheritDoc} */
    @Override
    public void draw (final IGraphicsInfo info)
    {
        final IBounds bounds = info.getBounds ();
        final double columnWidth = bounds.width () / ParameterSlot.BANK_SIZE;
        final double scaleY = bounds.height () / MixerControlDisplay.DISPLAY_HEIGHT;
        final double top = bounds.top () + MixerControlDisplay.TOP * scaleY;
        final double height = MixerControlDisplay.HEIGHT * scaleY;
        for (int index = 0; index < this.scenes.size (); index++)
        {
            final int column = this.display.controls ().get (index).column ();
            final double left = bounds.left () + column * columnWidth;
            info.getContext ().pushClip (left, top, columnWidth, height);
            try
            {
                this.scenes.get (index).draw (info.withBounds (left, top, columnWidth, height));
            }
            finally
            {
                info.getContext ().popClip ();
            }
        }
    }
}
