// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Parent-owned description of one immutable development candidate.
 *
 * @param apiVersion The exact shell API version
 * @param shellFingerprint Exact parent-loaded shell/API source fingerprint expected by the candidate
 * @param buildId The immutable build identifier
 * @param jarName The candidate JAR basename
 * @param sha256 The lowercase SHA-256 digest
 */
record CoreCandidateManifest (int apiVersion, String shellFingerprint, String buildId, String jarName, String sha256)
{
    private static final int SHA_256_LENGTH = 64;


    CoreCandidateManifest
    {
        if (apiVersion <= 0)
            throw new IllegalArgumentException ("apiVersion must be positive");
        if (shellFingerprint == null || !shellFingerprint.matches ("[0-9a-f]{40}|[0-9a-f]{64}"))
            throw new IllegalArgumentException ("shellFingerprint must contain 40 or 64 lowercase hexadecimal characters");
        if (!CoreArtifactMetadata.VALID_BUILD_ID.test (buildId))
            throw new IllegalArgumentException ("Invalid core buildId");
        if ("unpublished".equals (buildId))
            throw new IllegalArgumentException ("The reserved buildId 'unpublished' cannot be published");

        final String expectedJarName = "pull-core-" + buildId + ".jar";
        if (!expectedJarName.equals (jarName))
            throw new IllegalArgumentException ("jar must be " + expectedJarName);
        if (sha256 == null || sha256.length () != SHA_256_LENGTH || !sha256.matches ("[0-9a-f]{64}"))
            throw new IllegalArgumentException ("sha256 must contain 64 lowercase hexadecimal characters");
    }


    static CoreCandidateManifest parse (final byte [] content) throws IOException
    {
        final Properties properties = new Properties ();
        try (ByteArrayInputStream input = new ByteArrayInputStream (content))
        {
            properties.load (input);
        }

        CoreArtifactMetadata.requireExact (properties, "formatVersion", "1");
        return new CoreCandidateManifest (
            CoreArtifactMetadata.parsePositiveInteger (properties, "apiVersion"),
            CoreArtifactMetadata.require (properties, "shellFingerprint"),
            CoreArtifactMetadata.require (properties, "buildId"),
            CoreArtifactMetadata.require (properties, "jar"),
            CoreArtifactMetadata.require (properties, "sha256"));
    }


    Path resolveJar (final Path root)
    {
        final Path resolved = root.resolve (this.jarName).toAbsolutePath ().normalize ();
        if (!root.toAbsolutePath ().normalize ().equals (resolved.getParent ()))
            throw new IllegalArgumentException ("Candidate JAR must be directly inside the reload directory");
        return resolved;
    }
}
