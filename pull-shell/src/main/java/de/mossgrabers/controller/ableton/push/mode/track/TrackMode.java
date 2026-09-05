// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;
import de.mossgrabers.framework.parameterprovider.special.EmptyParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;

import java.util.Objects;

/** Stable layout adapter for the complete core-owned Track page. */
public final class TrackMode extends BaseMode<ITrack>
{
    private static final IComponent BLANK_DISPLAY = info -> {
        final var bounds = info.getBounds ();
        info.getContext ().fillRectangle (bounds.left (), bounds.top (), bounds.width (), bounds.height (), ColorEx.BLACK);
    };
    private final ReloadableControllerRuntime reloadableRuntime;

    public TrackMode (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime reloadableRuntime)
    {
        super ("Track", surface, model);
        this.reloadableRuntime = Objects.requireNonNull (reloadableRuntime, "reloadableRuntime");
        // Keep the permanent encoder binding mechanically inert for every Track profile.
        this.setParameterProvider (new EmptyParameterProvider (8));
    }

    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        // Touch, Delete reset, send enabled, and automation-release policy are core-owned.
    }

    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        // Both the current-bank footer and the VS footer are complete core profiles.
    }

    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        // Mix, Input & Output, and send paging are core-owned.
    }

    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        if (this.isButtonRow (0, buttonID) >= 0 || this.isButtonRow (1, buttonID) >= 0)
        {
            final RgbColor color = this.reloadableRuntime.lightColor (PushControlIds.button (buttonID.name ()));
            return this.model.getColorManager ().getColorIndex (ColorEx.fromRGB (color.red (), color.green (), color.blue ()));
        }
        return super.getButtonColor (buttonID);
    }

    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        // The generic display compositor projects core output; missing output remains blank.
        display.addElement (BLANK_DISPLAY);
    }
}
