// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.resource.ResourceHandler;
import de.mossgrabers.framework.graphics.Align;
import de.mossgrabers.framework.graphics.IBounds;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.framework.graphics.IImage;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Stable interpreter for bounded, reloadable controller display primitives. */
public final class DisplaySceneComponent implements IComponent
{
    private final ControllerDisplayScene scene;


    /**
     * Constructor.
     *
     * @param scene Complete scene to interpret
     */
    public DisplaySceneComponent (final ControllerDisplayScene scene)
    {
        this.scene = Objects.requireNonNull (scene, "scene");
        if (!scene.isPresent ())
            throw new IllegalArgumentException ("Display scene component requires a present scene");
    }


    /** {@inheritDoc} */
    @Override
    public void draw (final IGraphicsInfo info)
    {
        final IGraphicsContext gc = info.getContext ();
        final IBounds bounds = info.getBounds ();
        final double scaleX = bounds.width () / this.scene.width ();
        final double scaleY = bounds.height () / this.scene.height ();
        final double scale = Math.min (scaleX, scaleY);

        for (final DisplayCommand command: this.scene.commands ())
        {
            switch (command)
            {
                case final DisplayCommand.Rectangle rectangle -> gc.fillRectangle (
                    x (bounds, rectangle.x (), scaleX),
                    y (bounds, rectangle.y (), scaleY),
                    rectangle.width () * scaleX,
                    rectangle.height () * scaleY,
                    color (rectangle.color ()));
                case final DisplayCommand.RoundedRectangle rectangle -> gc.fillRoundedRectangle (
                    x (bounds, rectangle.x (), scaleX),
                    y (bounds, rectangle.y (), scaleY),
                    rectangle.width () * scaleX,
                    rectangle.height () * scaleY,
                    rectangle.radius () * scale,
                    color (rectangle.color ()));
                case final DisplayCommand.Circle circle -> gc.fillCircle (
                    x (bounds, circle.centerX (), scaleX),
                    y (bounds, circle.centerY (), scaleY),
                    circle.radius () * scale,
                    color (circle.color ()));
                case final DisplayCommand.DottedArc arc -> drawArc (gc, bounds, scaleX, scaleY, scale, arc);
                case final DisplayCommand.TextAt text -> gc.drawTextAt (
                    text.text (),
                    x (bounds, text.x (), scaleX),
                    y (bounds, text.baselineY (), scaleY),
                    color (text.color ()),
                    text.fontSize () * scale);
                case final DisplayCommand.TextBox text -> drawTextBox (gc, bounds, scaleX, scaleY, scale, text);
                case final DisplayCommand.Icon icon -> drawIcon (gc, bounds, scaleX, scaleY, icon);
            }
        }
    }


    private static void drawArc (final IGraphicsContext gc, final IBounds bounds, final double scaleX, final double scaleY, final double scale, final DisplayCommand.DottedArc arc)
    {
        final ColorEx color = color (arc.color ());
        for (int i = 0; i <= arc.steps (); i++)
        {
            final double angle = Math.toRadians (arc.startDegrees () + arc.sweepDegrees () * ((double) i / arc.steps ()));
            gc.fillCircle (
                x (bounds, arc.centerX () + Math.cos (angle) * arc.radius (), scaleX),
                y (bounds, arc.centerY () - Math.sin (angle) * arc.radius (), scaleY),
                arc.dotRadius () * scale,
                color);
        }
    }


    private static void drawTextBox (final IGraphicsContext gc, final IBounds bounds, final double scaleX, final double scaleY, final double scale, final DisplayCommand.TextBox text)
    {
        final double width = text.width () * scaleX;
        final double maximumFontSize = text.maximumFontSize () * scale;
        final double minimumFontSize = text.minimumFontSize () * scale;
        String fittedText = text.text ();
        double fontSize = maximumFontSize;
        if (text.fit () != DisplayTextFit.CLIP)
        {
            fontSize = gc.calculateFontSize (fittedText, maximumFontSize + 1, width, minimumFontSize);
            if (fontSize < 0 && text.fit () == DisplayTextFit.SHRINK_ELLIPSIS)
            {
                fittedText = ellipsize (gc, fittedText, width, minimumFontSize);
                fontSize = gc.calculateFontSize (fittedText, maximumFontSize + 1, width, minimumFontSize);
            }
            if (fontSize < 0)
                fontSize = minimumFontSize;
        }

        gc.drawTextInBounds (
            fittedText,
            x (bounds, text.x (), scaleX),
            y (bounds, text.y (), scaleY),
            width,
            text.height () * scaleY,
            text.alignment () == DisplayTextAlignment.CENTER ? Align.CENTER : Align.LEFT,
            color (text.color ()),
            fontSize);
    }


    private static String ellipsize (final IGraphicsContext gc, final String text, final double width, final double fontSize)
    {
        final int codePoints = text.codePointCount (0, text.length ());
        for (int length = codePoints - 1; length > 0; length--)
        {
            final int end = text.offsetByCodePoints (0, length);
            final String candidate = text.substring (0, end).stripTrailing () + "...";
            if (gc.calculateFontSize (candidate, fontSize + 1, width, fontSize) >= 0)
                return candidate;
        }
        return "";
    }


    private static void drawIcon (final IGraphicsContext gc, final IBounds bounds, final double scaleX, final double scaleY, final DisplayCommand.Icon icon)
    {
        final IImage image = ResourceHandler.getSVGImage (icon.icon () == DisplayIcon.MASTER ? "track/master_track.svg" : "pin.svg");
        final double boxX = x (bounds, icon.x (), scaleX);
        final double boxY = y (bounds, icon.y (), scaleY);
        final double boxWidth = icon.width () * scaleX;
        final double boxHeight = icon.height () * scaleY;
        gc.maskImage (image, boxX + (boxWidth - image.getWidth ()) / 2.0, boxY + (boxHeight - image.getHeight ()) / 2.0, color (icon.color ()));
    }


    private static double x (final IBounds bounds, final double value, final double scale)
    {
        return bounds.left () + value * scale;
    }


    private static double y (final IBounds bounds, final double value, final double scale)
    {
        return bounds.top () + value * scale;
    }


    private static ColorEx color (final RgbColor color)
    {
        return ColorEx.fromRGB (color.red (), color.green (), color.blue ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object object)
    {
        return this == object || object instanceof final DisplaySceneComponent other && this.scene.equals (other.scene);
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return this.scene.hashCode ();
    }
}
