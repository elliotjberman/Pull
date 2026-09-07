// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** One Lato source for offline measurement, SVG text and the catalog UI. No installed font fallback. */
final class CatalogTypography
{
    static final String FAMILY = "Lato";
    private final Font regular;
    private final String regularFace;
    private final String semiboldFace;

    private CatalogTypography (final Path directory) throws IOException
    {
        final byte[] regularBytes = Files.readAllBytes (directory.resolve ("Lato-Regular.ttf"));
        final byte[] semiboldBytes = Files.readAllBytes (directory.resolve ("Lato-Semibold.ttf"));
        this.regular = readFont (regularBytes, "Lato-Regular");
        readFont (semiboldBytes, "Lato-Semibold");
        this.regularFace = face (regularBytes, 400);
        this.semiboldFace = face (semiboldBytes, 600);
    }

    static CatalogTypography load () throws IOException
    {
        final String configured = System.getenv ("PULL_UI_FONT_DIR");
        if (configured != null && !configured.isBlank ()) return new CatalogTypography (Path.of (configured));
        final List<Path> candidates = new ArrayList<> (List.of (
            Path.of ("/Applications/Bitwig Studio.app/Contents/Resources/fonts"),
            Path.of ("/opt/bitwig-studio/resources/fonts"),
            Path.of ("/usr/share/bitwig-studio/resources/fonts")));
        final String programFiles = System.getenv ("ProgramFiles");
        if (programFiles != null) candidates.add (Path.of (programFiles, "Bitwig Studio", "resources", "fonts"));
        for (final Path directory: candidates)
            if (Files.isRegularFile (directory.resolve ("Lato-Regular.ttf")) && Files.isRegularFile (directory.resolve ("Lato-Semibold.ttf"))) return new CatalogTypography (directory);
        throw new IOException ("Lato fonts not found. Set PULL_UI_FONT_DIR to a directory containing Lato-Regular.ttf and Lato-Semibold.ttf. Bitwig need not be running.");
    }

    Font regular (final double size) { return this.regular.deriveFont ((float) size); }
    String displayCss () { return this.regularFace; }
    String catalogCss () { return this.regularFace + this.semiboldFace; }

    private static Font readFont (final byte[] bytes, final String expectedFace) throws IOException
    {
        try
        {
            final Font font = Font.createFont (Font.TRUETYPE_FONT, new ByteArrayInputStream (bytes));
            if (!expectedFace.equals (font.getPSName ())) throw new IOException ("Expected " + expectedFace + " font data, found " + font.getFontName (Locale.ROOT));
            return font;
        }
        catch (final FontFormatException ex) { throw new IOException ("Invalid Lato font data", ex); }
    }

    private static String face (final byte[] bytes, final int weight)
    {
        return "@font-face{font-family:'" + FAMILY + "';font-style:normal;font-weight:" + weight + ";src:url(data:font/ttf;base64," + Base64.getEncoder ().encodeToString (bytes) + ") format('truetype')}\n";
    }
}
