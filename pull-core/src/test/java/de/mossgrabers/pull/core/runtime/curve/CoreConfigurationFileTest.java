package de.mossgrabers.pull.core.runtime.curve;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CoreConfigurationFileTest
{
    @Test
    void transportsOnlyBoundedUtf8AndDistinguishesMissingFromUnreadable (@TempDir final Path directory) throws Exception
    {
        final Path path = directory.resolve ("config.yaml");
        assertEquals ("", CoreConfigurationFile.read (path));
        Files.writeString (path, "# spring → baseline\nx: 1\n");
        assertEquals ("# spring → baseline\nx: 1\n", CoreConfigurationFile.read (path));
        Files.writeString (path, "x".repeat (16385));
        assertThrows (IOException.class, () -> CoreConfigurationFile.read (path));
        Files.write (path, new byte [] {(byte) 0xC3, 0x28});
        assertThrows (IOException.class, () -> CoreConfigurationFile.read (path));
        assertThrows (IOException.class, () -> CoreConfigurationFile.read (directory));
    }
}
