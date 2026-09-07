// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.curve;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Bounded configuration I/O, performed once while creating a replacement core. */
public final class CoreConfigurationFile
{
    public static Path defaultPath ()
    {
        return Path.of (System.getProperty ("pull.core.config.file", Path.of (System.getProperty ("user.home"), ".drivenbymoss", "pull", "config.yaml").toString ()));
    }

    public static String read (final Path path) throws IOException
    {
        try
        {
            if (!Files.readAttributes (path, java.nio.file.attribute.BasicFileAttributes.class).isRegularFile ())
                throw new IOException ("Core configuration must be a regular file: " + path);
        }
        catch (final NoSuchFileException missing) { return ""; }
        try (var input = Files.newInputStream (path))
        {
            final byte [] bytes = input.readNBytes (16385);
            if (bytes.length > 16384) throw new IOException ("Core configuration exceeds 16 KiB: " + path);
            return StandardCharsets.UTF_8.newDecoder ().decode (ByteBuffer.wrap (bytes)).toString ();
        }
        catch (final NoSuchFileException missing)
        {
            return "";
        }
    }
}
