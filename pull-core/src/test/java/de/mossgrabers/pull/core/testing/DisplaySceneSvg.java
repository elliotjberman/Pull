// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Offline command replay. Geometry, icons and Lato font data are real; host rasterization is approximate. */
final class DisplaySceneSvg
{
    private static final FontRenderContext METRICS = new FontRenderContext (new AffineTransform (), true, true);
    private final Path icons;
    private final CatalogTypography typography;
    private final String idPrefix;
    private final Map<RgbColor, String> colorBindings;
    private final StringBuilder svg = new StringBuilder ();
    private int nextId;
    private boolean clipped;

    private DisplaySceneSvg (final Path icons, final CatalogTypography typography, final String idPrefix, final Map<RgbColor, String> colorBindings)
    {
        this.icons = icons;
        this.typography = typography;
        this.idPrefix = idPrefix;
        this.colorBindings = Map.copyOf (colorBindings);
    }

    static String render (final ControllerDisplayScene scene, final Path icons, final CatalogTypography typography) throws IOException
    {
        return render (scene, icons, typography, "", Map.of ());
    }

    static String render (final ControllerDisplayScene scene, final Path icons, final CatalogTypography typography, final String idPrefix, final Map<RgbColor, String> colorBindings) throws IOException
    {
        final DisplaySceneSvg renderer = new DisplaySceneSvg (icons, typography, idPrefix, colorBindings);
        renderer.svg.append ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append (scene.width ())
            .append ("\" height=\"").append (scene.height ()).append ("\" viewBox=\"0 0 ").append (scene.width ())
            .append (' ').append (scene.height ()).append ("\" font-family=\"").append (CatalogTypography.FAMILY).append ("\" font-weight=\"400\">\n<style>").append (typography.displayCss ()).append ("</style>\n");
        for (final DisplayCommand command: scene.commands ()) renderer.draw (command);
        if (renderer.clipped) throw new IllegalArgumentException ("Unclosed display clip");
        return renderer.svg.append ("</svg>\n").toString ();
    }

    private void draw (final DisplayCommand command) throws IOException
    {
        switch (command)
        {
            case final DisplayCommand.PushClip clip -> {
                if (this.clipped) throw new IllegalArgumentException ("Nested display clip");
                this.openClip (clip.x (), clip.y (), clip.width (), clip.height ());
                this.clipped = true;
            }
            case final DisplayCommand.PopClip ignored -> {
                if (!this.clipped) throw new IllegalArgumentException ("Display clip underflow");
                this.svg.append ("</g>\n");
                this.clipped = false;
            }
            case final DisplayCommand.Rectangle r -> this.rect (r.x (), r.y (), r.width (), r.height (), 0, r.color ());
            case final DisplayCommand.RoundedRectangle r -> this.rect (r.x (), r.y (), r.width (), r.height (), r.radius (), r.color ());
            case final DisplayCommand.Circle c -> this.circle (c.centerX (), c.centerY (), c.radius (), c.color ());
            case final DisplayCommand.Line line -> this.svg.append (String.format (Locale.ROOT,
                "<line x1=\"%.3f\" y1=\"%.3f\" x2=\"%.3f\" y2=\"%.3f\" stroke=\"%s\" stroke-width=\"%.3f\"/>\n",
                line.x1 (), line.y1 (), line.x2 (), line.y2 (), this.paint (line.color ()), line.width ()));
            case final DisplayCommand.DottedArc arc -> {
                for (int step = 0; step <= arc.steps (); step++)
                {
                    final double angle = Math.toRadians (arc.startDegrees () + arc.sweepDegrees () * step / arc.steps ());
                    this.circle (arc.centerX () + Math.cos (angle) * arc.radius (), arc.centerY () - Math.sin (angle) * arc.radius (), arc.dotRadius (), arc.color ());
                }
            }
            case final DisplayCommand.TextAt text -> this.text (text.text (), text.x (), text.baselineY (), text.fontSize (), text.color ());
            case final DisplayCommand.TextBox text -> this.textBox (text);
            case final DisplayCommand.Icon icon -> this.icon (icon);
        }
    }

    private void rect (final double x, final double y, final double width, final double height, final double radius, final RgbColor color)
    {
        this.svg.append (String.format (Locale.ROOT, "<rect x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\" rx=\"%.3f\" fill=\"%s\"/>\n", x, y, width, height, radius, this.paint (color)));
    }

    private void circle (final double x, final double y, final double radius, final RgbColor color)
    {
        this.svg.append (String.format (Locale.ROOT, "<circle cx=\"%.3f\" cy=\"%.3f\" r=\"%.3f\" fill=\"%s\"/>\n", x, y, radius, this.paint (color)));
    }

    private void openClip (final double x, final double y, final double width, final double height)
    {
        final String id = escape (this.idPrefix + "c" + this.nextId++);
        this.svg.append (String.format (Locale.ROOT, "<defs><clipPath id=\"%s\"><rect x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\"/></clipPath></defs><g clip-path=\"url(#%s)\">\n", id, x, y, width, height, id));
    }

    private void textBox (final DisplayCommand.TextBox box)
    {
        String value = box.text ();
        double size = box.maximumFontSize ();
        if (box.fit () != DisplayTextFit.CLIP)
        {
            size = fittingSize (value, box);
            if (size < 0 && box.fit () == DisplayTextFit.SHRINK_ELLIPSIS)
            {
                value = ellipsize (value, box.width (), box.minimumFontSize ());
                size = fittingSize (value, box);
            }
            if (size < 0) size = box.minimumFontSize ();
        }
        final double x = box.alignment () == DisplayTextAlignment.CENTER ? box.x () + (box.width () - textWidth (value, size)) / 2 : box.x ();
        final double glyphHeight = font (size).createGlyphVector (METRICS, "T").getVisualBounds ().getHeight ();
        final double baseline = box.y () + (box.height () + glyphHeight) / 2;
        this.openClip (box.x (), box.y (), box.width (), box.height ());
        this.text (value, x, baseline, size, box.color ());
        this.svg.append ("</g>\n");
    }

    private double fittingSize (final String text, final DisplayCommand.TextBox box)
    {
        double fit = -1;
        for (double size = box.minimumFontSize (); size < box.maximumFontSize () + 1; size++)
        {
            if (textWidth (text, size) > box.width ()) break;
            fit = size;
        }
        return fit;
    }

    private String ellipsize (final String text, final double width, final double size)
    {
        for (int count = text.codePointCount (0, text.length ()) - 1; count > 0; count--)
        {
            final String candidate = text.substring (0, text.offsetByCodePoints (0, count)).stripTrailing () + "...";
            if (textWidth (candidate, size) <= width) return candidate;
        }
        return "";
    }

    private void text (final String value, final double x, final double baseline, final double size, final RgbColor color)
    {
        this.svg.append (String.format (Locale.ROOT, "<text x=\"%.3f\" y=\"%.3f\" font-size=\"%.3f\" fill=\"%s\">%s</text>\n", x, baseline, size, this.paint (color), escape (value)));
    }

    private void icon (final DisplayCommand.Icon icon) throws IOException
    {
        final String file = switch (icon.icon ())
        {
            case AUDIO_TRACK -> "track/audio_track.svg";
            case INSTRUMENT_TRACK -> "track/instrument_track.svg";
            case HYBRID_TRACK -> "track/hybrid_track.svg";
            case GROUP_TRACK -> "track/group_track.svg";
            case GROUP_TRACK_OPEN -> "track/group_track_open.svg";
            case RETURN_TRACK -> "track/return_track.svg";
            case MASTER -> "track/master_track.svg";
            case MULTI_LAYER -> "track/multi_layer.svg";
            case PIN -> "pin.svg";
        };
        final byte[] bytes = Files.readAllBytes (this.icons.resolve (file));
        final String source = new String (bytes, java.nio.charset.StandardCharsets.UTF_8);
        final String root = source.substring (source.indexOf ("<svg"), source.indexOf ('>', source.indexOf ("<svg")));
        final double width = dimension (root, "width");
        final double height = dimension (root, "height");
        final String id = escape (this.idPrefix + "i" + this.nextId++);
        this.svg.append (String.format (Locale.ROOT, "<defs><mask id=\"%s\" maskUnits=\"userSpaceOnUse\" style=\"mask-type:alpha\" x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\"><image x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\" href=\"data:image/svg+xml;base64,%s\"/></mask></defs><g mask=\"url(#%s)\">\n", id, icon.x (), icon.y (), icon.width (), icon.height (), icon.x () + (icon.width () - width) / 2, icon.y () + (icon.height () - height) / 2, width, height, Base64.getEncoder ().encodeToString (bytes), id));
        this.rect (icon.x (), icon.y (), icon.width (), icon.height (), 0, icon.color ());
        this.svg.append ("</g>\n");
    }

    private static double dimension (final String root, final String name)
    {
        final var matcher = Pattern.compile ("\\s" + name + "=\"([0-9.]+)(?:px)?\"").matcher (root);
        if (!matcher.find ()) throw new IllegalArgumentException ("Icon has no numeric " + name);
        return Double.parseDouble (matcher.group (1));
    }

    private Font font (final double size) { return this.typography.regular (size); }
    private double textWidth (final String text, final double size) { return font (size).getStringBounds (text, METRICS).getWidth (); }
    private String paint (final RgbColor color) { return escape (this.colorBindings.getOrDefault (color, color (color))); }
    static String color (final RgbColor color) { return String.format (Locale.ROOT, "#%02x%02x%02x", color.red (), color.green (), color.blue ()); }
    static String escape (final String text) { return text.replace ("&", "&amp;").replace ("<", "&lt;").replace (">", "&gt;").replace ("\"", "&quot;"); }
}
