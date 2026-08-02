// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;


/**
 * Materializes the branded, neutral Bend preset needed by Bitwig's preset-file insertion path.
 */
final class BundledDrumPitchPreset
{
    static final String PRESET_NAME = "Pull Drum Pitch Helper v1";
    static final String PRESET_CREATOR = "DrivenByMoss Pull";

    private static final String RESOURCE = "/PullDrumPitchHelperV1.bwpreset.b64";
    private static final String EXPECTED_SHA_256 = "124bb8391667d6fd4b626a9dc54b7f6a18553430b9ef27cb006ba5b0add8c289";
    private static final String ASSET_DIRECTORY = "assets/drum-pitch-helper-v1";
    private static final String PRESET_FILE = PRESET_NAME + ".bwpreset";


    private BundledDrumPitchPreset ()
    {
        // Utility class.
    }


    /**
     * Decode and publish the exact bundled preset at a stable filesystem path.
     *
     * @param paths Stable Pull runtime paths
     * @return Absolute preset path accepted by {@code InsertionPoint.insertFile}
     */
    static Path materialize (final RuntimePaths paths)
    {
        Objects.requireNonNull (paths, "paths");
        final byte [] preset = readPreset ();
        if (!EXPECTED_SHA_256.equals (sha256 (preset)))
            throw new IllegalStateException ("Bundled Drum Pitch preset failed its integrity check");

        final Path directory = paths.root ().resolve (ASSET_DIRECTORY);
        final Path target = directory.resolve (PRESET_FILE);
        Path temporary = null;
        try
        {
            Files.createDirectories (directory);
            final boolean current = Files.isRegularFile (target) && Files.size (target) == preset.length && EXPECTED_SHA_256.equals (sha256 (Files.readAllBytes (target)));
            if (current)
                return target;

            temporary = Files.createTempFile (directory, ".pull-drum-pitch-", ".tmp");
            try (FileOutputStream output = new FileOutputStream (temporary.toFile ()))
            {
                output.write (preset);
                output.getChannel ().force (true);
            }
            try
            {
                Files.move (temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (final AtomicMoveNotSupportedException ignored)
            {
                Files.move (temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;

            final boolean published = Files.isRegularFile (target) && Files.size (target) == preset.length && EXPECTED_SHA_256.equals (sha256 (Files.readAllBytes (target)));
            if (!published)
                throw new IllegalStateException ("Published Drum Pitch preset failed its integrity check");
            return target;
        }
        catch (final IOException failure)
        {
            throw new IllegalStateException ("Could not publish the bundled Drum Pitch preset", failure);
        }
        finally
        {
            if (temporary != null)
            {
                try
                {
                    Files.deleteIfExists (temporary);
                }
                catch (final IOException ignored)
                {
                    // Best-effort cleanup after a failed publication.
                }
            }
        }
    }


    private static byte [] readPreset ()
    {
        try (InputStream input = BundledDrumPitchPreset.class.getResourceAsStream (RESOURCE))
        {
            if (input == null)
                throw new IllegalStateException ("Bundled Drum Pitch preset is missing");
            return Base64.getMimeDecoder ().decode (input.readAllBytes ());
        }
        catch (final IOException | IllegalArgumentException failure)
        {
            throw new IllegalStateException ("Bundled Drum Pitch preset could not be decoded", failure);
        }
    }


    private static String sha256 (final byte [] bytes)
    {
        try
        {
            return HexFormat.of ().formatHex (MessageDigest.getInstance ("SHA-256").digest (bytes));
        }
        catch (final NoSuchAlgorithmException failure)
        {
            throw new IllegalStateException ("SHA-256 is unavailable", failure);
        }
    }
}
