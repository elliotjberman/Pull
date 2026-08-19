// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.api.output.RgbColor;


/** Deterministic host-free evidence renderer for the mixer text stress fixture. */
final class MixerDisplayStressImage
{
    static
    {
        System.setProperty ("java.awt.headless", "true");
    }


    private static final int DISPLAY_WIDTH = 960;
    private static final int DISPLAY_HEIGHT = MixerControlDisplay.DISPLAY_HEIGHT;
    private static final Color BACKGROUND = Color.BLACK;
    private static final Color GUIDE = new Color (0, 78, 92);


    private MixerDisplayStressImage ()
    {
        // Utility class.
    }


    static BufferedImage write (final MixerControlsDisplay display, final Path output) throws IOException
    {
        final BufferedImage image = render (display);
        Files.createDirectories (output.toAbsolutePath ().getParent ());
        if (!ImageIO.write (image, "png", output.toFile ()))
            throw new IOException ("PNG encoder is unavailable");
        return image;
    }


    private static BufferedImage render (final MixerControlsDisplay display)
    {
        final BufferedImage image = new BufferedImage (DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics ();
        try
        {
            graphics.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint (RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor (BACKGROUND);
            graphics.fillRect (0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

            for (final MixerControlDisplay control: display.controls ())
            {
                final Graphics2D cell = (Graphics2D) graphics.create (control.column () * MixerControlDisplay.WIDTH, MixerControlDisplay.TOP, MixerControlDisplay.WIDTH, MixerControlDisplay.HEIGHT);
                try
                {
                    for (final DisplayCommand command: control.scene ().commands ())
                        draw (cell, command);
                }
                finally
                {
                    cell.dispose ();
                }
            }

            drawGuides (graphics);
        }
        finally
        {
            graphics.dispose ();
        }
        return image;
    }


    private static void draw (final Graphics2D graphics, final DisplayCommand command)
    {
        switch (command)
        {
            case final DisplayCommand.Rectangle rectangle -> {
                graphics.setColor (color (rectangle.color ()));
                graphics.fill (new Rectangle2D.Double (rectangle.x (), rectangle.y (), rectangle.width (), rectangle.height ()));
            }
            case final DisplayCommand.DottedArc arc -> {
                graphics.setColor (color (arc.color ()));
                for (int index = 0; index <= arc.steps (); index++)
                {
                    final double angle = Math.toRadians (arc.startDegrees () + arc.sweepDegrees () * ((double) index / arc.steps ()));
                    final double centerX = arc.centerX () + Math.cos (angle) * arc.radius ();
                    final double centerY = arc.centerY () - Math.sin (angle) * arc.radius ();
                    graphics.fill (new Ellipse2D.Double (centerX - arc.dotRadius (), centerY - arc.dotRadius (), 2 * arc.dotRadius (), 2 * arc.dotRadius ()));
                }
            }
            case final DisplayCommand.TextAt text -> drawTextAt (graphics, text);
            case final DisplayCommand.TextBox text -> drawTextBox (graphics, text);
            default -> throw new IllegalArgumentException ("Mixer stress proof received an unsupported command: " + command.getClass ().getSimpleName ());
        }
    }


    private static void drawTextAt (final Graphics2D graphics, final DisplayCommand.TextAt text)
    {
        graphics.setColor (color (text.color ()));
        graphics.setFont (font (text.fontSize ()));
        graphics.drawString (text.text (), (float) text.x (), (float) text.baselineY ());
    }


    private static void drawTextBox (final Graphics2D graphics, final DisplayCommand.TextBox text)
    {
        String fittedText = text.text ();
        double fontSize = text.maximumFontSize ();
        if (text.fit () != DisplayTextFit.CLIP)
        {
            while (fontSize > text.minimumFontSize () && textWidth (graphics, fittedText, fontSize) > text.width ())
                fontSize = Math.max (text.minimumFontSize (), fontSize - 0.25);
            if (text.fit () == DisplayTextFit.SHRINK_ELLIPSIS && textWidth (graphics, fittedText, fontSize) > text.width ())
                fittedText = ellipsize (graphics, fittedText, text.width (), fontSize);
        }

        final Graphics2D clipped = (Graphics2D) graphics.create ();
        try
        {
            clipped.clip (new Rectangle2D.Double (text.x (), text.y (), text.width (), text.height ()));
            clipped.setColor (color (text.color ()));
            clipped.setFont (font (fontSize));
            final FontMetrics metrics = clipped.getFontMetrics ();
            final double width = metrics.getStringBounds (fittedText, clipped).getWidth ();
            final double left = text.alignment () == DisplayTextAlignment.CENTER ? text.x () + (text.width () - width) / 2.0 : text.x ();
            final double baseline = text.y () + (text.height () - metrics.getHeight ()) / 2.0 + metrics.getAscent ();
            clipped.drawString (fittedText, (float) left, (float) baseline);
        }
        finally
        {
            clipped.dispose ();
        }
    }


    private static double textWidth (final Graphics2D graphics, final String text, final double fontSize)
    {
        return graphics.getFontMetrics (font (fontSize)).getStringBounds (text, graphics).getWidth ();
    }


    private static String ellipsize (final Graphics2D graphics, final String text, final double width, final double fontSize)
    {
        for (int length = text.length () - 1; length > 0; length--)
        {
            final String candidate = text.substring (0, length).stripTrailing () + "...";
            if (textWidth (graphics, candidate, fontSize) <= width)
                return candidate;
        }
        return "";
    }


    private static Font font (final double size)
    {
        return new Font (Font.SANS_SERIF, Font.PLAIN, 1).deriveFont ((float) size);
    }


    private static Color color (final RgbColor color)
    {
        return new Color (color.red (), color.green (), color.blue ());
    }


    private static void drawGuides (final Graphics2D graphics)
    {
        graphics.setColor (GUIDE);
        graphics.setStroke (new BasicStroke (1));
        graphics.drawLine (0, MixerControlDisplay.TOP, DISPLAY_WIDTH - 1, MixerControlDisplay.TOP);
        graphics.drawLine (0, MixerControlDisplay.TOP + MixerControlDisplay.HEIGHT, DISPLAY_WIDTH - 1, MixerControlDisplay.TOP + MixerControlDisplay.HEIGHT);
        for (int column = 1; column < 8; column++)
            graphics.drawLine (column * MixerControlDisplay.WIDTH, 0, column * MixerControlDisplay.WIDTH, DISPLAY_HEIGHT - 1);
    }
}
