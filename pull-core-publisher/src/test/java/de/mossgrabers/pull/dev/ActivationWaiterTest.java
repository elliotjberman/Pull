// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact activation acknowledgement tests.
 */
class ActivationWaiterTest
{
    @TempDir
    Path publicationDirectory;


    @Test
    void acceptsOnlyExactRequestedAndActiveBuild () throws Exception
    {
        writeStatus ("active", "build-a", "build-a", "activated");

        final ReloadStatus status = new ActivationWaiter ().await (this.publicationDirectory, "build-a", Duration.ZERO);

        assertEquals ("build-a", status.activeBuildId ());
    }


    @Test
    void ignoresStaleAcknowledgement () throws Exception
    {
        writeStatus ("active", "older-build", "older-build", "activated");

        final IllegalStateException failure = assertThrows (IllegalStateException.class, () -> new ActivationWaiter ().await (this.publicationDirectory, "requested-build", Duration.ZERO));

        assertTrue (failure.getMessage ().contains ("Timed out"));
    }


    @Test
    void reportsCandidateFailureAndStillActiveBuild () throws Exception
    {
        writeStatus ("failed", "broken-build", "good-build", "provider threw");

        final IllegalStateException failure = assertThrows (IllegalStateException.class, () -> new ActivationWaiter ().await (this.publicationDirectory, "broken-build", Duration.ZERO));

        assertTrue (failure.getMessage ().contains ("provider threw"));
        assertTrue (failure.getMessage ().contains ("good-build"));
    }


    @Test
    void classifiesShellApiRestartRequirement () throws Exception
    {
        writeStatus ("restartRequired", "new-api", "old-core", "requires API 2, shell provides 1");

        final IllegalStateException failure = assertThrows (IllegalStateException.class, () -> new ActivationWaiter ().await (this.publicationDirectory, "new-api", Duration.ZERO));

        assertTrue (failure.getMessage ().contains ("shell/API rebuild and Bitwig restart"));
        assertTrue (failure.getMessage ().contains ("requires API 2"));
    }


    @Test
    void rejectsInconsistentActiveAcknowledgement () throws Exception
    {
        writeStatus ("active", "requested", "different", "activated");

        final IllegalStateException failure = assertThrows (IllegalStateException.class, () -> new ActivationWaiter ().await (this.publicationDirectory, "requested", Duration.ZERO));

        assertTrue (failure.getMessage ().contains ("activeBuildId"));
    }


    private void writeStatus (final String state, final String requestedBuildId, final String activeBuildId, final String message) throws IOException
    {
        final String contents = "formatVersion=1\n" +
            "state=" + state + "\n" +
            "requestedBuildId=" + requestedBuildId + "\n" +
            "activeBuildId=" + activeBuildId + "\n" +
            "message=" + message + "\n";
        Files.writeString (this.publicationDirectory.resolve (ReloadStatus.FILE_NAME), contents, StandardCharsets.UTF_8);
    }
}
