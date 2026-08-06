// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import de.mossgrabers.controller.ableton.push.controller.Push2LcdReference;


/**
 * Push 2 palette inspector, LED preview and temporary pad-calibration utility.
 */
public final class Push2ColorTest
{
    private static final byte [] PUSH2_SYSEX_HEADER =
    {
        (byte) 0xF0,
        0x00,
        0x21,
        0x1D,
        0x01,
        0x01
    };

    private static final int     PALETTE_REPLY_LENGTH    = 17;
    private static final int     SET_PALETTE_ENTRY       = 0x03;
    private static final int     GET_PALETTE_ENTRY       = 0x04;
    private static final int     REAPPLY_PALETTE         = 0x05;
    private static final int     GET_LED_BRIGHTNESS      = 0x07;
    private static final int     GET_LED_WHITE_BALANCE   = 0x15;
    private static final int     FIRST_PAD_NOTE          = 36;
    private static final int     PAD_COUNT               = 64;
    private static final int     FIRST_LOWER_BUTTON      = 20;
    private static final int     LOWER_BUTTON_COUNT      = 8;
    private static final int     CALIBRATION_FIRST_INDEX = 64;
    private static final int     COLOR_GROUP_COUNT       = 11;
    private static final int     DEFAULT_TIMEOUT_MS      = 1000;
    private static final int     DEFAULT_RETRIES         = 3;
    private static final int     BRIDGE_READY_TIMEOUT_MS = 5000;
    private static final double  TUNE_TINT_PER_PIXEL     = 0.001;
    private static final double  TUNE_LIGHT_PER_PIXEL    = 0.002;
    private static final double  TUNE_FINE_SCALE         = 0.2;
    private static final double  TUNE_MAX_TINT           = 0.12;
    private static final double  TUNE_MAX_LIGHT          = 0.20;
    private static final boolean TRACE_MIDI              = Boolean.getBoolean ("push2.color.trace");
    private static final Pattern BACKUP_ENTRY_PATTERN    = Pattern.compile ("\\{\\s*\"index\"\\s*:\\s*(\\d+)\\s*,\\s*\"red\"\\s*:\\s*(\\d+)\\s*,\\s*\"green\"\\s*:\\s*(\\d+)\\s*,\\s*\"blue\"\\s*:\\s*(\\d+)\\s*,\\s*\"white\"\\s*:\\s*(\\d+)\\s*\\}");
    private static final Pattern BACKUP_SCHEMA_PATTERN   = Pattern.compile ("\"schemaVersion\"\\s*:\\s*(\\d+)");
    private static final Pattern BACKUP_KIND_PATTERN     = Pattern.compile ("\"kind\"\\s*:\\s*\"push2-temporary-palette-backup\"");
    private static final Pattern BACKUP_FIRST_PATTERN    = Pattern.compile ("\"firstIndex\"\\s*:\\s*(\\d+)");
    private static final Pattern BACKUP_COUNT_PATTERN    = Pattern.compile ("\"count\"\\s*:\\s*(\\d+)");
    private static final Pattern BACKUP_HASH_PATTERN     = Pattern.compile ("\"entriesSha256\"\\s*:\\s*\"([0-9a-f]{64})\"");
    private static final Pattern BANK_SCHEMA_PATTERN     = Pattern.compile ("\"schemaVersion\"\\s*:\\s*(\\d+)");
    private static final Pattern BANK_ID_PATTERN         = Pattern.compile ("\"id\"\\s*:\\s*\"([A-Za-z0-9][A-Za-z0-9._-]*)\"");
    private static final Pattern BANK_MODE_PATTERN       = Pattern.compile ("\"mode\"\\s*:\\s*\"([a-z-]+)\"");
    private static final Pattern BANK_ROTATION_PATTERN   = Pattern.compile ("\"rowRotation\"\\s*:\\s*(-?\\d+)");
    private static final Pattern BANK_OFFSET_PATTERN     = Pattern.compile ("\"rowOffset\"\\s*:\\s*(-?\\d+)");
    private static final Pattern BANK_LABELS_PATTERN     = Pattern.compile ("\"candidateLabels\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern BANK_TARGET_PATTERN     = Pattern.compile ("\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"targetRgb\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"\\s*,\\s*\"candidateRgb\"\\s*:\\s*\\[(.*?)]\\s*}", Pattern.DOTALL);
    private static final Pattern BANK_DIRECT_TARGET_PATTERN = Pattern.compile ("\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"targetRgb\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"\\s*,\\s*\"baseProgrammedRgb\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"\\s*}", Pattern.DOTALL);
    private static final Pattern JSON_STRING_PATTERN     = Pattern.compile ("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern RGB_STRING_PATTERN      = Pattern.compile ("\"(#[0-9A-Fa-f]{6})\"");

    private static final List<CalibrationTarget> DEFAULT_CALIBRATION_TARGETS = List.of (
        new CalibrationTarget ("Muted rose", new Color (0xC3748A)),
        new CalibrationTarget ("Steel blue", new Color (0x2A629F)),
        new CalibrationTarget ("Indigo", new Color (0x3C3766)),
        new CalibrationTarget ("Magenta", new Color (0xCD4594)),
        new CalibrationTarget ("Pale coral", new Color (0xFCA9A2)),
        new CalibrationTarget ("Green", new Color (0x009D47)),
        new CalibrationTarget ("Lavender", new Color (0x848AE0)),
        new CalibrationTarget ("Pale amber", new Color (0xFFD293)));


    private Push2ColorTest ()
    {
        // Utility class.
    }


    /**
     * Entry point.
     *
     * @param args Command-line arguments
     */
    public static void main (final String [] args)
    {
        try
        {
            final Options options = Options.parse (args);
            switch (options.command ())
            {
                case LIST:
                    listMidiDevices ();
                    break;

                case DUMP:
                    dumpPalette (options);
                    break;

                case BANK:
                    showBank (options);
                    break;

                case SHOW:
                    showIndex (options);
                    break;

                case COMPARE:
                    compareTarget (options);
                    break;

                case CALIBRATE:
                    calibratePads (options);
                    break;

                case CHECK_BANK:
                    checkCalibrationBank (options.path ());
                    break;

                case CHECK_BACKUP:
                    checkBackup (options.path ());
                    break;

                case RESTORE:
                    restoreBackup (options);
                    break;

                case HELP:
                    printUsage ();
                    break;
            }
        }
        catch (final UsageException ex)
        {
            System.err.println ("Error: " + ex.getMessage ());
            System.err.println ();
            printUsage ();
            System.exit (2);
        }
        catch (final InterruptedException ex)
        {
            Thread.currentThread ().interrupt ();
            System.err.println ("Interrupted.");
            System.exit (130);
        }
        catch (final Exception ex)
        {
            System.err.println ("Error: " + ex.getMessage ());
            System.exit (1);
        }
    }


    private static void checkCalibrationBank (final Path path) throws IOException
    {
        final CalibrationBank bank = loadCalibrationBank (path);
        if (bank.mode () == CalibrationMode.DIRECT_TUNE)
            System.out.printf (Locale.ROOT, "Calibration bank %s is valid: direct tune, 8 targets, one repeated base RGB per column.%n", bank.id ());
        else
            System.out.printf (Locale.ROOT, "Calibration bank %s is valid: candidate select, 8 targets, 8 candidates, row rotation %d, row offset %d.%n", bank.id (), Integer.valueOf (bank.rowRotation ()), Integer.valueOf (bank.rowOffset ()));
        for (int column = 0; column < bank.targets ().size (); column++)
        {
            final CalibrationTarget target = bank.targets ().get (column);
            if (bank.mode () == CalibrationMode.DIRECT_TUNE)
                System.out.printf (Locale.ROOT, "  Column %d: %s target %s, base programmed %s%n", Integer.valueOf (column + 1), target.name (), toHex (target.color ()), toHex (target.directBase ()));
            else
                System.out.printf (Locale.ROOT, "  Column %d: %s %s%n", Integer.valueOf (column + 1), target.name (), toHex (target.color ()));
        }
    }


    private static void checkBackup (final Path path) throws IOException
    {
        final List<PaletteEntry> entries = readBackupEntries (path);
        System.out.println ("Backup is valid: " + path.toAbsolutePath ());
        System.out.println ("  " + entries.size () + " ordered palette entries, indices 64-127.");
    }


    private static void listMidiDevices () throws MidiUnavailableException
    {
        final MidiDevice.Info [] infos = MidiSystem.getMidiDeviceInfo ();
        if (infos.length == 0)
        {
            System.out.println ("No MIDI devices found.");
            return;
        }

        System.out.println ("#  Directions  Name | Vendor | Description | Version");
        for (int index = 0; index < infos.length; index++)
        {
            final MidiDevice device = MidiSystem.getMidiDevice (infos[index]);
            final boolean canInput = device.getMaxTransmitters () != 0;
            final boolean canOutput = device.getMaxReceivers () != 0;
            final String directions = (canInput ? "IN" : "--") + "/" + (canOutput ? "OUT" : "---");
            final MidiDevice.Info info = infos[index];
            System.out.printf (Locale.ROOT, "%-2d %-10s  %s | %s | %s | %s%n", Integer.valueOf (index), directions, info.getName (), info.getVendor (), info.getDescription (), info.getVersion ());
        }

        System.out.println ();
        System.out.println ("IN means the device can send replies to this tool; OUT means the tool can send to it.");
    }


    private static void dumpPalette (final Options options) throws Exception
    {
        final List<PaletteEntry> entries;
        try (MidiConnection connection = MidiConnection.open (options.inputSubstring (), options.outputSubstring ()))
        {
            entries = queryRange (connection, 0, 128, options.timeoutMillis (), options.retries (), true);
        }

        writeCsv (options.path (), entries);
        System.out.println ("Wrote 128 RGBW entries to " + options.path ().toAbsolutePath ());
    }


    private static void showBank (final Options options) throws Exception
    {
        final int base = options.number ();
        final List<PaletteEntry> entries;
        try (MidiConnection connection = MidiConnection.open (options.inputSubstring (), options.outputSubstring ()))
        {
            System.out.println ("Reading 64 palette entries...");
            entries = queryRange (connection, base, PAD_COUNT, options.timeoutMillis (), options.retries (), false);
            lightPadBank (connection.output (), base);
        }

        System.out.println ("Displayed palette indices " + base + "-" + (base + PAD_COUNT - 1) + " on the pads; the MIDI port is now released.");
        if (options.noWindow ())
            return;
        System.out.println ("Opening the palette window. Close that window to exit the command.");
        showGridWindow ("Push 2 palette " + base + "-" + (base + PAD_COUNT - 1), entries);
    }


    private static void showIndex (final Options options) throws Exception
    {
        final int index = options.number ();
        final PaletteEntry entry;
        try (MidiConnection connection = MidiConnection.open (options.inputSubstring (), options.outputSubstring ()))
        {
            System.out.println ("Reading palette entry " + index + "...");
            entry = queryPaletteEntry (connection, index, options.timeoutMillis (), options.retries ());
            lightAllPadsAndLowerButtons (connection.output (), index);
        }

        final List<PaletteEntry> entries = new ArrayList<> (PAD_COUNT);
        for (int pad = 0; pad < PAD_COUNT; pad++)
            entries.add (entry);

        System.out.printf (Locale.ROOT, "Displayed index %d (RGB %d,%d,%d; white %d) on all pads and lower display buttons CC20-27; the MIDI port is now released.%n", Integer.valueOf (index), Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ()), Integer.valueOf (entry.white ()));
        if (options.noWindow ())
            return;
        System.out.println ("Opening the palette window. Close that window to exit the command.");
        showGridWindow ("Push 2 palette index " + index, entries);
    }


    private static void compareTarget (final Options options) throws Exception
    {
        final Color target = options.targetColor ();
        final List<PaletteEntry> programmedPalette;
        final List<MatchCandidate> candidates;
        try (MidiConnection connection = MidiConnection.open (options.inputSubstring (), options.outputSubstring ()))
        {
            System.out.println ("Reading all 128 palette entries...");
            programmedPalette = queryRange (connection, 0, 128, options.timeoutMillis (), options.retries (), true);
            candidates = createMatchCandidates (target, programmedPalette);
            lightCandidateColumns (connection.output (), candidates);
        }

        System.out.printf (Locale.ROOT, "Target #%02X%02X%02X; each candidate is repeated down one pad column and mirrored on lower display buttons CC20-27.%n", Integer.valueOf (target.getRed ()), Integer.valueOf (target.getGreen ()), Integer.valueOf (target.getBlue ()));
        for (int column = 0; column < candidates.size (); column++)
        {
            final MatchCandidate candidate = candidates.get (column);
            System.out.printf (Locale.ROOT, "Column %d: index %d, logical %s, programmed %s, %s%n", Integer.valueOf (column + 1), Integer.valueOf (candidate.index ()), toHex (candidate.logical ()), toHex (candidate.programmed ()), candidate.sourcesLabel ());
        }
        System.out.println ("The MIDI port is now released. Judge the physical pads/buttons against the target window, not the simulated candidate RGB.");
        if (options.noWindow ())
            return;
        System.out.println ("Opening the comparison window. Close that window to exit the command.");
        showMatchWindow (target, candidates);
    }


    private static void calibratePads (final Options options) throws Exception
    {
        if (GraphicsEnvironment.isHeadless ())
            throw new IOException ("The calibrate command needs a graphical desktop.");
        final CalibrationBank bank = options.path () == null ? createDefaultCalibrationBank () : loadCalibrationBank (options.path ());

        final Path recoveryMarker = recoveryMarkerPath ();
        if (Files.exists (recoveryMarker))
        {
            final String backup = Files.readString (recoveryMarker, StandardCharsets.UTF_8).trim ();
            throw new IOException ("A previous calibration still requires restoration. Run:" + System.lineSeparator () + "  ./tools/push2-color-test.sh restore \"" + backup + "\" --input \"" + options.inputSubstring () + "\" --output \"" + options.outputSubstring () + "\"");
        }

        System.out.println ("CALIBRATION MODE TEMPORARILY REWRITES PALETTE ENTRIES 64-127.");
        System.out.println ("Fully quit Bitwig Studio and Ableton Live. Keep Push externally powered and at your normal LED brightness.");
        System.out.println ("White balance and brightness are read only; this command never writes either setting.");

        try (MidiConnection connection = MidiConnection.open (options.inputSubstring (), options.outputSubstring ()))
        {
            requireLivePort (connection);

            System.out.println ("Reading Push LED brightness and all 11 white-balance groups...");
            final DeviceSettings settings = queryDeviceSettings (connection, options.timeoutMillis (), options.retries ());

            System.out.println ("Reading the 64 palette entries that will be used temporarily...");
            final List<PaletteEntry> originals = queryRange (connection, CALIBRATION_FIRST_INDEX, PAD_COUNT, options.timeoutMillis (), options.retries (), true);
            final Path backupPath = createCalibrationPath ("backup");
            writeBackupJson (backupPath, connection, settings, originals);
            System.out.println ("Recovery backup written before any palette mutation:");
            System.out.println ("  " + backupPath.toAbsolutePath ());

            final List<CalibrationCell> cells = createCalibrationCells (bank, originals);
            final TemporaryPaletteSession session = new TemporaryPaletteSession (connection, originals, recoveryMarker, backupPath, options.inputSubstring (), options.outputSubstring (), options.timeoutMillis (), options.retries ());
            final Thread shutdownHook = new Thread (session::restoreFromShutdown, "push2-palette-restore");
            Runtime.getRuntime ().addShutdownHook (shutdownHook);

            try (Push2LcdReference lcdReference = Push2LcdReference.open (bank.targets ().stream ().map (CalibrationTarget::color).toList ()))
            {
                if (!lcdReference.isActive ())
                    System.out.println ("Using the desktop target swatches because the Push 2 LCD reference is unavailable.");

                CalibrationWindow window = null;
                Exception primaryFailure = null;
                try
                {
                    session.program (cells.stream ().map (CalibrationCell::programmed).toList ());
                    lightPadBank (connection.output (), CALIBRATION_FIRST_INDEX);

                    final Path resultsPath = createResultsPath (backupPath);
                    window = CalibrationWindow.open (bank, cells, resultsPath, settings, backupPath, session::updateColumn);
                    final CalibrationWindow activeWindow = window;
                    connection.input ().setPadListener (activeWindow::handlePadNote);

                    if (bank.mode () == CalibrationMode.DIRECT_TUNE)
                        System.out.println ("The direct-tune bank is active. Drag each target swatch to tune its repeated pad column, then press any pad in that column to confirm.");
                    else
                        System.out.println ("The physical 8x8 calibration bank is active. Press the closest-looking pad in each column.");
                    System.out.println ("Close the calibration window or click Finish & Restore when done.");
                    window.awaitFinish ();
                    connection.input ().setPadListener (null);
                    window.writeResults ();
                    System.out.println ("Selections written to " + resultsPath.toAbsolutePath ());
                }
                catch (final Exception ex)
                {
                    primaryFailure = ex;
                    throw ex;
                }
                finally
                {
                    connection.input ().setPadListener (null);
                    if (window != null)
                        window.showRestoring ();

                    try
                    {
                        session.restore ();
                        if (window != null)
                            window.closeAfterRestore (true, null);
                    }
                    catch (final Exception restoreFailure)
                    {
                        System.err.println ("Automatic palette restoration could not be verified: " + restoreFailure.getMessage ());
                        System.err.println ("Run this recovery command after fixing the MIDI connection:");
                        System.err.println ("  " + recoveryCommand (backupPath, options.inputSubstring (), options.outputSubstring ()));
                        if (window != null)
                            window.closeAfterRestore (false, restoreFailure.getMessage ());
                        if (primaryFailure != null)
                            primaryFailure.addSuppressed (restoreFailure);
                        else
                            throw restoreFailure;
                    }
                    finally
                    {
                        try
                        {
                            // A failed normal restore already exhausted bounded retries. Its
                            // persistent marker and printed recovery command deliberately become
                            // the fallback.
                            Runtime.getRuntime ().removeShutdownHook (shutdownHook);
                        }
                        catch (final IllegalStateException ignored)
                        {
                            // JVM shutdown is already in progress; the idempotent hook may be
                            // running.
                        }
                    }
                }
            }
        }
    }


    private static void restoreBackup (final Options options) throws Exception
    {
        final Path backupPath = options.path ().toAbsolutePath ();
        final List<PaletteEntry> entries = readBackupEntries (backupPath);
        validateCalibrationEntries (entries);

        System.out.println ("Restoring and verifying palette entries 64-127 from:");
        System.out.println ("  " + backupPath);
        try (MidiConnection connection = MidiConnection.open (options.inputSubstring (), options.outputSubstring ()))
        {
            requireLivePort (connection);
            final TemporaryPaletteSession session = new TemporaryPaletteSession (connection, entries, recoveryMarkerPath (), backupPath, options.inputSubstring (), options.outputSubstring (), options.timeoutMillis (), options.retries ());
            session.armRecovery ();
            final Thread shutdownHook = new Thread (session::restoreFromShutdown, "push2-palette-recovery");
            Runtime.getRuntime ().addShutdownHook (shutdownHook);
            try
            {
                session.restore ();
            }
            catch (final Exception ex)
            {
                System.err.println ("Recovery remains required. Retry with:");
                System.err.println ("  " + recoveryCommand (backupPath, options.inputSubstring (), options.outputSubstring ()));
                throw ex;
            }
            finally
            {
                try
                {
                    Runtime.getRuntime ().removeShutdownHook (shutdownHook);
                }
                catch (final IllegalStateException ignored)
                {
                    // JVM shutdown is already in progress.
                }
            }
        }

        System.out.println ("Palette restoration verified.");
    }


    private static void requireLivePort (final MidiConnection connection) throws UsageException
    {
        if (!connection.isLivePort ())
            throw new UsageException ("Calibration and restore require the Push Live Port; the User Port can ignore pad LEDs and pad input while Push is in Live mode.");
    }


    private static DeviceSettings queryDeviceSettings (final MidiConnection connection, final int timeoutMillis, final int retries) throws Exception
    {
        final int brightness = queryLedBrightness (connection, timeoutMillis, retries);
        final List<Integer> whiteBalance = new ArrayList<> (COLOR_GROUP_COUNT);
        for (int group = 0; group < COLOR_GROUP_COUNT; group++)
            whiteBalance.add (Integer.valueOf (queryWhiteBalance (connection, group, timeoutMillis, retries)));

        System.out.printf (Locale.ROOT, "LED brightness: %d; pad white balance R/G/B: %d/%d/%d%n", Integer.valueOf (brightness), whiteBalance.get (3), whiteBalance.get (4), whiteBalance.get (5));
        return new DeviceSettings (brightness, List.copyOf (whiteBalance));
    }


    private static int queryLedBrightness (final MidiConnection connection, final int timeoutMillis, final int retries) throws Exception
    {
        connection.input ().discardBrightnessReplies ();
        for (int attempt = 1; attempt <= retries; attempt++)
        {
            sendSysexCommand (connection.output (), GET_LED_BRIGHTNESS);
            final Integer reply = connection.input ().pollBrightness (timeoutMillis, TimeUnit.MILLISECONDS);
            if (reply != null)
                return reply.intValue ();
        }
        throw new IOException ("Timed out reading LED brightness.");
    }


    private static int queryWhiteBalance (final MidiConnection connection, final int group, final int timeoutMillis, final int retries) throws Exception
    {
        connection.input ().discardWhiteBalanceReplies ();
        for (int attempt = 1; attempt <= retries; attempt++)
        {
            sendSysexCommand (connection.output (), GET_LED_WHITE_BALANCE, group);
            final long deadline = System.nanoTime () + TimeUnit.MILLISECONDS.toNanos (timeoutMillis);
            while (true)
            {
                final long remaining = deadline - System.nanoTime ();
                if (remaining <= 0)
                    break;
                final WhiteBalanceReply reply = connection.input ().pollWhiteBalance (remaining, TimeUnit.NANOSECONDS);
                if (reply == null)
                    break;
                if (reply.group () == group)
                    return reply.value ();
            }
        }
        throw new IOException ("Timed out reading white-balance group " + group + ".");
    }


    private static List<PaletteEntry> queryRange (final MidiConnection connection, final int firstIndex, final int count, final int timeoutMillis, final int retries, final boolean showProgress) throws InterruptedException, InvalidMidiDataException, IOException
    {
        final List<PaletteEntry> entries = new ArrayList<> (count);
        for (int offset = 0; offset < count; offset++)
        {
            final int index = firstIndex + offset;
            entries.add (queryPaletteEntry (connection, index, timeoutMillis, retries));
            if (showProgress && ((offset + 1) % 16 == 0 || offset + 1 == count))
                System.out.println ("Read " + (offset + 1) + "/" + count + " palette entries.");
        }
        return entries;
    }


    private static PaletteEntry queryPaletteEntry (final MidiConnection connection, final int index, final int timeoutMillis, final int retries) throws InvalidMidiDataException, InterruptedException, IOException
    {
        connection.input ().discardPaletteReplies ();
        for (int attempt = 1; attempt <= retries; attempt++)
        {
            sendPaletteQuery (connection.output (), index);

            final long deadline = System.nanoTime () + TimeUnit.MILLISECONDS.toNanos (timeoutMillis);
            while (true)
            {
                final long remaining = deadline - System.nanoTime ();
                if (remaining <= 0)
                    break;

                final PaletteEntry reply = connection.input ().pollPalette (remaining, TimeUnit.NANOSECONDS);
                if (reply == null)
                    break;
                if (reply.index () == index)
                    return reply;
            }
        }

        throw new PaletteTimeoutException ("Timed out reading palette entry " + index + " after " + retries + " attempts. Close Bitwig/Live and verify the selected MIDI ports.");
    }


    private static void sendPaletteQuery (final Receiver output, final int index) throws InvalidMidiDataException
    {
        sendSysexCommand (output, GET_PALETTE_ENTRY, index);
    }


    private static void sendPaletteUpdate (final Receiver output, final PaletteEntry entry) throws InvalidMidiDataException
    {
        sendSysexCommand (output, SET_PALETTE_ENTRY,
            entry.index (),
            entry.red () & 0x7F,
            entry.red () >> 7,
            entry.green () & 0x7F,
            entry.green () >> 7,
            entry.blue () & 0x7F,
            entry.blue () >> 7,
            entry.white () & 0x7F,
            entry.white () >> 7);
    }


    private static void sendPaletteReapply (final Receiver output) throws InvalidMidiDataException
    {
        sendSysexCommand (output, REAPPLY_PALETTE);
    }


    private static void sendSysexCommand (final Receiver output, final int command, final int... arguments) throws InvalidMidiDataException
    {
        final byte [] message = new byte [PUSH2_SYSEX_HEADER.length + 1 + arguments.length + 1];
        System.arraycopy (PUSH2_SYSEX_HEADER, 0, message, 0, PUSH2_SYSEX_HEADER.length);
        message[PUSH2_SYSEX_HEADER.length] = (byte) command;
        for (int index = 0; index < arguments.length; index++)
        {
            if (arguments[index] < 0 || arguments[index] > 0x7F)
                throw new InvalidMidiDataException ("SysEx argument is outside the 7-bit range: " + arguments[index]);
            message[PUSH2_SYSEX_HEADER.length + 1 + index] = (byte) arguments[index];
        }
        message[message.length - 1] = (byte) 0xF7;
        if (TRACE_MIDI)
            System.out.println ("MIDI OUT: " + formatMidiBytes (message));
        output.send (new SysexMessage (message, message.length), -1);
    }


    private static void writeAndVerify (final MidiConnection connection, final PaletteEntry expected, final int timeoutMillis, final int retries) throws Exception
    {
        PaletteEntry lastRead = null;
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= retries; attempt++)
        {
            sendPaletteUpdate (connection.output (), expected);
            try
            {
                lastRead = queryPaletteEntry (connection, expected.index (), timeoutMillis, 1);
                if (expected.equals (lastRead))
                    return;
                lastFailure = new IOException ("Palette entry " + expected.index () + " read back as " + formatRgbw (lastRead) + " instead of " + formatRgbw (expected) + ".");
            }
            catch (final IOException ex)
            {
                lastFailure = ex;
            }
        }

        if (lastFailure != null)
            throw lastFailure;
        throw new IOException ("Could not verify palette entry " + expected.index () + (lastRead == null ? "." : ": " + formatRgbw (lastRead)));
    }


    private static void restoreEntries (final MidiConnection connection, final List<PaletteEntry> entries, final int timeoutMillis, final int retries) throws Exception
    {
        List<PaletteEntry> remaining = new ArrayList<> (entries);
        final Map<Integer, String> failures = new LinkedHashMap<> ();
        int consecutiveTimeouts = 0;
        for (int pass = 1; pass <= retries && !remaining.isEmpty (); pass++)
        {
            final List<PaletteEntry> failedThisPass = new ArrayList<> ();
            for (int index = 0; index < remaining.size (); index++)
            {
                final PaletteEntry entry = remaining.get (index);
                try
                {
                    writeAndVerify (connection, entry, timeoutMillis, 1);
                    failures.remove (Integer.valueOf (entry.index ()));
                    consecutiveTimeouts = 0;
                }
                catch (final Exception ex)
                {
                    failedThisPass.add (entry);
                    failures.put (Integer.valueOf (entry.index ()), ex.getMessage ());
                    if (ex instanceof PaletteTimeoutException)
                    {
                        consecutiveTimeouts++;
                        if (consecutiveTimeouts >= 3)
                        {
                            try
                            {
                                sendPaletteReapply (connection.output ());
                            }
                            catch (final Exception reapplyFailure)
                            {
                                ex.addSuppressed (reapplyFailure);
                            }
                            throw new IOException ("Stopped restoration after three consecutive palette-read timeouts. The recovery marker remains active.", ex);
                        }
                    }
                    else
                        consecutiveTimeouts = 0;
                }
                if ((index + 1) % 8 == 0 || index + 1 == remaining.size ())
                    System.out.printf (Locale.ROOT, "Restore pass %d: checked %d/%d entries; %d still need retry.%n", Integer.valueOf (pass), Integer.valueOf (index + 1), Integer.valueOf (remaining.size ()), Integer.valueOf (failedThisPass.size ()));
            }
            remaining = failedThisPass;
        }

        sendPaletteReapply (connection.output ());
        if (!remaining.isEmpty ())
        {
            final StringBuilder message = new StringBuilder ("Could not restore and verify palette entries:");
            for (final PaletteEntry entry: remaining)
                message.append (System.lineSeparator ()).append ("  ").append (entry.index ()).append (" expected ").append (formatRgbw (entry)).append (": ").append (failures.get (Integer.valueOf (entry.index ())));
            throw new IOException (message.toString ());
        }
    }


    private static void confirmEntries (final MidiConnection connection, final List<PaletteEntry> entries, final int timeoutMillis, final int retries) throws Exception
    {
        final List<Integer> mismatches = new ArrayList<> ();
        for (final PaletteEntry expected: entries)
        {
            final PaletteEntry actual = queryPaletteEntry (connection, expected.index (), timeoutMillis, retries);
            if (!expected.equals (actual))
                mismatches.add (Integer.valueOf (expected.index ()));
        }
        if (!mismatches.isEmpty ())
            throw new IOException ("Post-restore confirmation failed for palette indices " + mismatches + ".");
    }


    private static void lightPadBank (final Receiver output, final int base) throws InvalidMidiDataException
    {
        for (int offset = 0; offset < PAD_COUNT; offset++)
            sendShortMessage (output, ShortMessage.NOTE_ON, FIRST_PAD_NOTE + offset, base + offset);
    }


    private static void clearPads (final Receiver output) throws InvalidMidiDataException
    {
        for (int offset = 0; offset < PAD_COUNT; offset++)
            sendShortMessage (output, ShortMessage.NOTE_ON, FIRST_PAD_NOTE + offset, 0);
    }


    private static void lightAllPadsAndLowerButtons (final Receiver output, final int index) throws InvalidMidiDataException
    {
        for (int offset = 0; offset < PAD_COUNT; offset++)
            sendShortMessage (output, ShortMessage.NOTE_ON, FIRST_PAD_NOTE + offset, index);
        for (int offset = 0; offset < LOWER_BUTTON_COUNT; offset++)
            sendShortMessage (output, ShortMessage.CONTROL_CHANGE, FIRST_LOWER_BUTTON + offset, index);
    }


    private static void lightCandidateColumns (final Receiver output, final List<MatchCandidate> candidates) throws InvalidMidiDataException
    {
        for (int column = 0; column < candidates.size (); column++)
        {
            final int colorIndex = candidates.get (column).index ();
            for (int row = 0; row < 8; row++)
                sendShortMessage (output, ShortMessage.NOTE_ON, FIRST_PAD_NOTE + row * 8 + column, colorIndex);
            sendShortMessage (output, ShortMessage.CONTROL_CHANGE, FIRST_LOWER_BUTTON + column, colorIndex);
        }
    }


    private static void sendShortMessage (final Receiver output, final int command, final int data1, final int data2) throws InvalidMidiDataException
    {
        output.send (new ShortMessage (command, 0, data1, data2), -1);
    }


    private static List<MatchCandidate> createMatchCandidates (final Color target, final List<PaletteEntry> programmedPalette) throws IOException
    {
        final LogicalPalette logicalPalette = loadLogicalPalette (target);
        final List<PaletteEntry> logicalRanking = rankPalette (target, logicalPalette.entries ());
        final List<PaletteEntry> programmedRanking = rankPalette (target, programmedPalette);
        final Map<Integer, EnumSet<CandidateSource>> selected = new LinkedHashMap<> ();

        addCandidate (selected, logicalPalette.currentIndex (), CandidateSource.CURRENT);
        for (int rank = 0; rank < 4; rank++)
            addCandidate (selected, logicalRanking.get (rank).index (), CandidateSource.LOGICAL);
        for (int rank = 0; rank < 4; rank++)
            addCandidate (selected, programmedRanking.get (rank).index (), CandidateSource.PROGRAMMED);

        for (int rank = 4; selected.size () < 8; rank++)
        {
            addCandidate (selected, logicalRanking.get (rank).index (), CandidateSource.LOGICAL);
            if (selected.size () < 8)
                addCandidate (selected, programmedRanking.get (rank).index (), CandidateSource.PROGRAMMED);
        }

        final List<MatchCandidate> candidates = new ArrayList<> (8);
        for (final Map.Entry<Integer, EnumSet<CandidateSource>> entry: selected.entrySet ())
        {
            if (candidates.size () == 8)
                break;
            final int index = entry.getKey ().intValue ();
            candidates.add (new MatchCandidate (index, logicalPalette.entries ().get (index), programmedPalette.get (index), entry.getValue ()));
        }
        return candidates;
    }


    private static void addCandidate (final Map<Integer, EnumSet<CandidateSource>> selected, final int index, final CandidateSource source)
    {
        selected.computeIfAbsent (Integer.valueOf (index), key -> EnumSet.noneOf (CandidateSource.class)).add (source);
    }


    private static List<PaletteEntry> rankPalette (final Color target, final List<PaletteEntry> palette)
    {
        final double [] targetOKLab = toOKLab (target.getRed (), target.getGreen (), target.getBlue ());
        final List<PaletteEntry> ranking = new ArrayList<> (palette);
        ranking.sort (Comparator.<PaletteEntry> comparingDouble (entry -> colorDistance (targetOKLab, entry)).thenComparingInt (PaletteEntry::index));
        return ranking;
    }


    private static double colorDistance (final double [] targetOKLab, final PaletteEntry entry)
    {
        final double [] candidate = toOKLab (entry.red (), entry.green (), entry.blue ());
        final double deltaL = targetOKLab[0] - candidate[0];
        final double deltaA = targetOKLab[1] - candidate[1];
        final double deltaB = targetOKLab[2] - candidate[2];
        return deltaL * deltaL + deltaA * deltaA + deltaB * deltaB;
    }


    private static double [] toOKLab (final int redValue, final int greenValue, final int blueValue)
    {
        final double red = linearize (redValue);
        final double green = linearize (greenValue);
        final double blue = linearize (blueValue);

        final double l = Math.cbrt (0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        final double m = Math.cbrt (0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        final double s = Math.cbrt (0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);

        return new double []
        {
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }


    private static double linearize (final int component)
    {
        final double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow ((value + 0.055) / 1.055, 2.4);
    }


    private static AdjustedPaletteEntry applyTune (final PaletteEntry base, final ColumnAdjustment adjustment)
    {
        if (adjustment.isZero ())
            return new AdjustedPaletteEntry (base, 1.0);

        final double [] baseLab = toOKLab (base.red (), base.green (), base.blue ());
        final double requestedLightness = Math.max (0.0, Math.min (1.0, baseLab[0] + adjustment.lightness ()));
        final double requestedA = baseLab[1] + adjustment.a ();
        final double requestedB = baseLab[2] + adjustment.b ();
        double scale = 1.0;
        double [] linear = fromOKLab (requestedLightness, requestedA, requestedB);
        if (!isInGamut (linear))
        {
            double lower = 0.0;
            double upper = 1.0;
            for (int iteration = 0; iteration < 24; iteration++)
            {
                final double candidate = (lower + upper) / 2.0;
                final double [] candidateLinear = fromOKLab (requestedLightness, candidate * requestedA, candidate * requestedB);
                if (isInGamut (candidateLinear))
                    lower = candidate;
                else
                    upper = candidate;
            }
            scale = lower;
            linear = fromOKLab (requestedLightness, scale * requestedA, scale * requestedB);
        }

        final PaletteEntry adjusted = new PaletteEntry (base.index (), delinearize (linear[0]), delinearize (linear[1]), delinearize (linear[2]), base.white ());
        return new AdjustedPaletteEntry (adjusted, scale);
    }


    private static double [] fromOKLab (final double lightness, final double a, final double b)
    {
        final double lRoot = lightness + 0.3963377774 * a + 0.2158037573 * b;
        final double mRoot = lightness - 0.1055613458 * a - 0.0638541728 * b;
        final double sRoot = lightness - 0.0894841775 * a - 1.2914855480 * b;
        final double l = lRoot * lRoot * lRoot;
        final double m = mRoot * mRoot * mRoot;
        final double s = sRoot * sRoot * sRoot;
        return new double []
        {
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        };
    }


    private static boolean isInGamut (final double [] linear)
    {
        for (final double component: linear)
        {
            if (component < -2.0e-7 || component > 1.0 + 2.0e-7)
                return false;
        }
        return true;
    }


    private static int delinearize (final double component)
    {
        final double value = Math.max (0.0, Math.min (1.0, component));
        final double encoded = value <= 0.0031308 ? 12.92 * value : 1.055 * Math.pow (value, 1.0 / 2.4) - 0.055;
        return clampColor ((int) Math.round (255.0 * encoded));
    }


    private static List<CalibrationModel> createCalibrationModels ()
    {
        return List.of (
            createPowerModel ("A", 1.00),
            createPowerModel ("B", 1.25),
            createPowerModel ("C", 1.50),
            createPowerModel ("D", 1.75),
            createPowerModel ("E", 2.00),
            createPowerModel ("F", 2.25),
            createPowerModel ("G", 2.50),
            createPowerModel ("H", 2.75));
    }


    private static CalibrationBank createDefaultCalibrationBank ()
    {
        return new CalibrationBank ("power-gamma-fixed-v1", null, CalibrationMode.CANDIDATE_SELECT, 0, 0, createCalibrationModels (), DEFAULT_CALIBRATION_TARGETS);
    }


    private static CalibrationBank loadCalibrationBank (final Path path) throws IOException
    {
        final String json = Files.readString (path, StandardCharsets.UTF_8);
        validateJsonSyntax (json, path);
        final int schemaVersion = Integer.parseInt (requirePatternGroup (BANK_SCHEMA_PATTERN, json, "bank schemaVersion", path));
        if (schemaVersion != 1 && schemaVersion != 2)
            throw new IOException (path + ": unsupported schemaVersion " + schemaVersion + ".");
        final String id = requirePatternGroup (BANK_ID_PATTERN, json, "bank id", path);
        final String sourceSha256 = sha256Hex (json.getBytes (StandardCharsets.UTF_8));

        if (schemaVersion == 2)
            return loadDirectTuneBank (path, json, id, sourceSha256);

        if (containsJsonKey (json, "mode"))
            throw new IOException (path + ": schemaVersion 1 is the candidate-select format and must not declare mode; use schemaVersion 2 for direct-tune banks.");
        final int rowRotation = Integer.parseInt (requirePatternGroup (BANK_ROTATION_PATTERN, json, "rowRotation", path));
        final int rowOffset = Integer.parseInt (requirePatternGroup (BANK_OFFSET_PATTERN, json, "rowOffset", path));

        final Matcher labelsSection = BANK_LABELS_PATTERN.matcher (json);
        if (!labelsSection.find ())
            throw new IOException (path + ": candidateLabels must contain eight strings.");
        final List<String> labels = new ArrayList<> (8);
        final Matcher labelMatcher = JSON_STRING_PATTERN.matcher (labelsSection.group (1));
        while (labelMatcher.find ())
            labels.add (unescapeJsonString (labelMatcher.group (1), path));
        if (labels.size () != 8)
            throw new IOException (path + ": candidateLabels must contain exactly eight strings.");

        final List<CalibrationModel> models = new ArrayList<> (8);
        for (int index = 0; index < labels.size (); index++)
        {
            final String modelId = Character.toString ((char) ('A' + index));
            models.add (new CalibrationModel (modelId, labels.get (index), "Explicit programmed RGB from calibration bank " + id, null));
        }

        final List<CalibrationTarget> targets = new ArrayList<> (8);
        final Matcher targetMatcher = BANK_TARGET_PATTERN.matcher (json);
        while (targetMatcher.find ())
        {
            final String name = unescapeJsonString (targetMatcher.group (1), path);
            final Color targetColor = parseBankColor (targetMatcher.group (2), path);
            final List<Color> candidates = new ArrayList<> (8);
            final Matcher candidateMatcher = RGB_STRING_PATTERN.matcher (targetMatcher.group (3));
            while (candidateMatcher.find ())
                candidates.add (parseBankColor (candidateMatcher.group (1), path));
            if (candidates.size () != 8)
                throw new IOException (path + ": target " + name + " must contain exactly eight candidateRgb values.");
            if (new HashSet<> (candidates).size () != candidates.size ())
                throw new IOException (path + ": target " + name + " must contain eight distinct candidateRgb values.");
            targets.add (new CalibrationTarget (name, targetColor, List.copyOf (candidates), null));
        }
        if (targets.size () != 8)
            throw new IOException (path + ": targets must contain exactly eight objects in name/targetRgb/candidateRgb order.");

        return new CalibrationBank (id, sourceSha256, CalibrationMode.CANDIDATE_SELECT, Math.floorMod (rowRotation, 8), Math.floorMod (rowOffset, 8), List.copyOf (models), List.copyOf (targets));
    }


    private static CalibrationBank loadDirectTuneBank (final Path path, final String json, final String id, final String sourceSha256) throws IOException
    {
        final String mode = requirePatternGroup (BANK_MODE_PATTERN, json, "mode", path);
        if (!CalibrationMode.DIRECT_TUNE.jsonValue ().equals (mode))
            throw new IOException (path + ": schemaVersion 2 requires mode \"" + CalibrationMode.DIRECT_TUNE.jsonValue () + "\".");

        for (final String forbiddenKey: List.of ("rowRotation", "rowOffset", "candidateLabels", "candidateRgb"))
        {
            if (containsJsonKey (json, forbiddenKey))
                throw new IOException (path + ": direct-tune banks must not declare " + forbiddenKey + ".");
        }

        final List<CalibrationTarget> targets = new ArrayList<> (8);
        final Matcher targetMatcher = BANK_DIRECT_TARGET_PATTERN.matcher (json);
        while (targetMatcher.find ())
        {
            final String name = unescapeJsonString (targetMatcher.group (1), path);
            final Color targetColor = parseBankColor (targetMatcher.group (2), path);
            final Color directBase = parseBankColor (targetMatcher.group (3), path);
            targets.add (new CalibrationTarget (name, targetColor, List.of (), directBase));
        }
        if (targets.size () != 8)
            throw new IOException (path + ": direct-tune targets must contain exactly eight objects in name/targetRgb/baseProgrammedRgb order.");

        return new CalibrationBank (id, sourceSha256, CalibrationMode.DIRECT_TUNE, 0, 0, List.of (), List.copyOf (targets));
    }


    private static String requirePatternGroup (final Pattern pattern, final String input, final String label, final Path path) throws IOException
    {
        final Matcher matcher = pattern.matcher (input);
        if (!matcher.find ())
            throw new IOException (path + ": missing or invalid " + label + ".");
        return matcher.group (1);
    }


    private static boolean containsJsonKey (final String json, final String key)
    {
        return Pattern.compile ("\"" + Pattern.quote (key) + "\"\\s*:").matcher (json).find ();
    }


    private static void validateJsonSyntax (final String json, final Path path) throws IOException
    {
        try
        {
            new JsonSyntaxValidator (json).validate ();
        }
        catch (final IllegalArgumentException ex)
        {
            throw new IOException (path + ": invalid JSON: " + ex.getMessage (), ex);
        }
    }


    private static Color parseBankColor (final String value, final Path path) throws IOException
    {
        try
        {
            return new Color (Integer.parseInt (value.substring (1), 16));
        }
        catch (final RuntimeException ex)
        {
            throw new IOException (path + ": invalid RGB value " + value + ".", ex);
        }
    }


    private static String unescapeJsonString (final String value, final Path path) throws IOException
    {
        final StringBuilder result = new StringBuilder (value.length ());
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            if (character != '\\')
            {
                result.append (character);
                continue;
            }
            if (++index >= value.length ())
                throw new IOException (path + ": incomplete JSON string escape.");
            final char escaped = value.charAt (index);
            switch (escaped)
            {
                case '"', '\\', '/' -> result.append (escaped);
                case 'b' -> result.append ('\b');
                case 'f' -> result.append ('\f');
                case 'n' -> result.append ('\n');
                case 'r' -> result.append ('\r');
                case 't' -> result.append ('\t');
                case 'u' -> {
                    if (index + 4 >= value.length ())
                        throw new IOException (path + ": incomplete JSON unicode escape.");
                    try
                    {
                        result.append ((char) Integer.parseInt (value.substring (index + 1, index + 5), 16));
                    }
                    catch (final NumberFormatException ex)
                    {
                        throw new IOException (path + ": invalid JSON unicode escape.", ex);
                    }
                    index += 4;
                }
                default -> throw new IOException (path + ": invalid JSON string escape \\" + escaped + ".");
            }
        }
        return result.toString ();
    }


    private static CalibrationModel createPowerModel (final String id, final double gamma)
    {
        return new CalibrationModel (id, String.format (Locale.ROOT, "γ %.2f", Double.valueOf (gamma)), String.format (Locale.ROOT, "per-channel y = x^%.2f; gain 1.00", Double.valueOf (gamma)), color -> applyPower (color, gamma));
    }


    private static Color applyPower (final Color color, final double gamma)
    {
        return new Color (
            powerChannel (color.getRed (), gamma),
            powerChannel (color.getGreen (), gamma),
            powerChannel (color.getBlue (), gamma));
    }


    private static int powerChannel (final int channel, final double gamma)
    {
        return clampColor ((int) Math.round (255.0 * Math.pow (channel / 255.0, gamma)));
    }


    private static Color applyBalancedV2 (final Color color)
    {
        final int red = color.getRed ();
        final int green = color.getGreen ();
        final int blue = color.getBlue ();
        final int maximum = Math.max (red, Math.max (green, blue));
        final int minimum = Math.min (red, Math.min (green, blue));
        if (maximum - minimum < 8)
            return color;

        final int luminance = calculateLuminance (red, green, blue);
        final int highlight = Math.max (0, luminance - 128);
        final int saturation = 432 - highlight * (432 - 352) / 127;
        final int brightnessHighlight = Math.min (highlight, 64);
        final int brightness = 192 + brightnessHighlight * (232 - 192) / 64;
        return new Color (
            calibrateLegacyChannel (red, luminance, saturation, brightness),
            calibrateLegacyChannel (green, luminance, saturation, brightness),
            calibrateLegacyChannel (blue, luminance, saturation, brightness));
    }


    private static int calculateLuminance (final int red, final int green, final int blue)
    {
        return (54 * red + 183 * green + 19 * blue) >> 8;
    }


    private static int calibrateLegacyChannel (final int channel, final int luminance, final int saturation, final int brightness)
    {
        final int saturated = luminance + (channel - luminance) * saturation / 256;
        return clampColor (saturated * brightness / 256);
    }


    private static int clampColor (final int value)
    {
        return Math.max (0, Math.min (255, value));
    }


    private static List<CalibrationCell> createCalibrationCells (final CalibrationBank bank, final List<PaletteEntry> originals) throws IOException
    {
        if (bank.targets ().size () != 8)
            throw new IOException ("Calibration requires exactly eight targets.");
        if (bank.mode () == CalibrationMode.CANDIDATE_SELECT && bank.models ().size () != 8)
            throw new IOException ("Candidate-select calibration requires exactly eight models.");
        if (bank.mode () == CalibrationMode.DIRECT_TUNE && !bank.models ().isEmpty ())
            throw new IOException ("Direct-tune calibration does not use candidate models.");
        validateCalibrationEntries (originals);

        final List<CalibrationCell> cells = new ArrayList<> (PAD_COUNT);
        for (int row = 0; row < 8; row++)
        {
            for (int column = 0; column < 8; column++)
            {
                final CalibrationTarget target = bank.targets ().get (column);
                final int candidateIndex;
                final CalibrationModel model;
                final Color output;
                if (bank.mode () == CalibrationMode.DIRECT_TUNE)
                {
                    candidateIndex = -1;
                    model = null;
                    output = target.directBase ();
                }
                else
                {
                    candidateIndex = Math.floorMod (row + column * bank.rowRotation () + bank.rowOffset (), 8);
                    model = bank.models ().get (candidateIndex);
                    output = target.getCandidate (candidateIndex, model);
                }
                final int offset = row * 8 + column;
                final int paletteIndex = CALIBRATION_FIRST_INDEX + offset;
                final PaletteEntry programmed = new PaletteEntry (paletteIndex, output.getRed (), output.getGreen (), output.getBlue (), originals.get (offset).white ());
                cells.add (new CalibrationCell (row, column, candidateIndex, model, target, programmed));
            }
        }
        return List.copyOf (cells);
    }


    private static void validateCalibrationEntries (final List<PaletteEntry> entries) throws IOException
    {
        if (entries.size () != PAD_COUNT)
            throw new IOException ("Expected 64 calibration palette entries, found " + entries.size () + ".");
        for (int offset = 0; offset < entries.size (); offset++)
        {
            final PaletteEntry entry = entries.get (offset);
            final int expectedIndex = CALIBRATION_FIRST_INDEX + offset;
            if (entry.index () != expectedIndex)
                throw new IOException ("Expected palette index " + expectedIndex + " at offset " + offset + ", found " + entry.index () + ".");
            if (!isByte (entry.red ()) || !isByte (entry.green ()) || !isByte (entry.blue ()) || !isByte (entry.white ()))
                throw new IOException ("Palette entry " + entry.index () + " contains a value outside 0-255.");
        }
    }


    private static boolean isByte (final int value)
    {
        return value >= 0 && value <= 255;
    }


    private static LogicalPalette loadLogicalPalette (final Color target) throws IOException
    {
        try
        {
            final Class<?> colorClass = Class.forName ("de.mossgrabers.framework.controller.color.ColorEx");
            final Class<?> managerClass = Class.forName ("de.mossgrabers.controller.ableton.push.controller.PushColorManager");
            final Object manager = managerClass.getConstructor ().newInstance ();
            final Object targetColor = colorClass.getMethod ("fromRGB", Integer.TYPE, Integer.TYPE, Integer.TYPE).invoke (null, Integer.valueOf (target.getRed ()), Integer.valueOf (target.getGreen ()), Integer.valueOf (target.getBlue ()));
            final int currentIndex = ((Integer) managerClass.getMethod ("getColorIndex", colorClass).invoke (manager, targetColor)).intValue ();

            final List<PaletteEntry> entries = new ArrayList<> (128);
            for (int index = 0; index < 128; index++)
            {
                final Object color = managerClass.getMethod ("getPaletteColor", Integer.TYPE).invoke (null, Integer.valueOf (index));
                final int [] rgb = (int []) colorClass.getMethod ("toIntRGB255").invoke (color);
                entries.add (new PaletteEntry (index, rgb[0], rgb[1], rgb[2], 0));
            }
            return new LogicalPalette (entries, currentIndex);
        }
        catch (final ReflectiveOperationException | LinkageError ex)
        {
            throw new IOException ("The compare command needs compiled controller classes. Run mvn -DskipTests compile first.", ex);
        }
    }


    private static String toHex (final PaletteEntry entry)
    {
        return String.format (Locale.ROOT, "#%02X%02X%02X", Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ()));
    }


    private static String toHex (final Color color)
    {
        return String.format (Locale.ROOT, "#%02X%02X%02X", Integer.valueOf (color.getRed ()), Integer.valueOf (color.getGreen ()), Integer.valueOf (color.getBlue ()));
    }


    private static String formatMidiBytes (final byte [] data)
    {
        final StringBuilder result = new StringBuilder (data.length * 3);
        for (final byte value: data)
        {
            if (!result.isEmpty ())
                result.append (' ');
            result.append (String.format (Locale.ROOT, "%02X", Integer.valueOf (Byte.toUnsignedInt (value))));
        }
        return result.toString ();
    }


    private static String formatRgbw (final PaletteEntry entry)
    {
        return String.format (Locale.ROOT, "RGB %d,%d,%d + white-only %d", Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ()), Integer.valueOf (entry.white ()));
    }


    private static Path calibrationDirectory () throws IOException
    {
        final String override = System.getProperty ("push2.colorTest.dataDir");
        final Path directory = override == null || override.isBlank () ? Path.of (System.getProperty ("user.home"), ".drivenbymoss", "push2-color-calibration") : Path.of (override);
        Files.createDirectories (directory);
        return directory;
    }


    private static Path createCalibrationPath (final String kind) throws IOException
    {
        return Files.createTempFile (calibrationDirectory (), "push2-" + kind + "-", ".json");
    }


    private static Path createResultsPath (final Path backupPath) throws IOException
    {
        final String filename = backupPath.getFileName ().toString ();
        final String resultName = filename.replaceFirst ("backup", "results");
        final Path result = backupPath.resolveSibling (resultName);
        if (!result.equals (backupPath))
            return result;
        return createCalibrationPath ("results");
    }


    private static Path recoveryMarkerPath () throws IOException
    {
        return calibrationDirectory ().resolve ("restore-required.txt");
    }


    private static void writeBackupJson (final Path path, final MidiConnection connection, final DeviceSettings settings, final List<PaletteEntry> entries) throws IOException
    {
        validateCalibrationEntries (entries);
        final StringBuilder json = new StringBuilder (8192);
        json.append ("{\n");
        json.append ("  \"schemaVersion\": 2,\n");
        json.append ("  \"kind\": \"push2-temporary-palette-backup\",\n");
        json.append ("  \"createdAt\": \"").append (jsonEscape (Instant.now ().toString ())).append ("\",\n");
        json.append ("  \"inputDevice\": \"").append (jsonEscape (connection.inputDescription ())).append ("\",\n");
        json.append ("  \"outputDevice\": \"").append (jsonEscape (connection.outputDescription ())).append ("\",\n");
        json.append ("  \"firstIndex\": ").append (CALIBRATION_FIRST_INDEX).append (",\n");
        json.append ("  \"count\": ").append (PAD_COUNT).append (",\n");
        json.append ("  \"ledBrightness\": ").append (settings.brightness ()).append (",\n");
        json.append ("  \"whiteBalance\": [");
        for (int group = 0; group < settings.whiteBalance ().size (); group++)
        {
            if (group > 0)
                json.append (", ");
            json.append (settings.whiteBalance ().get (group));
        }
        json.append ("],\n");
        json.append ("  \"entriesSha256\": \"").append (paletteEntriesSha256 (entries)).append ("\",\n");
        json.append ("  \"entries\": [\n");
        for (int index = 0; index < entries.size (); index++)
        {
            final PaletteEntry entry = entries.get (index);
            json.append ("    {\"index\": ").append (entry.index ()).append (", \"red\": ").append (entry.red ()).append (", \"green\": ").append (entry.green ()).append (", \"blue\": ").append (entry.blue ()).append (", \"white\": ").append (entry.white ()).append ("}");
            json.append (index + 1 == entries.size () ? "\n" : ",\n");
        }
        json.append ("  ]\n");
        json.append ("}\n");
        writeAtomically (path, json.toString ());
    }


    private static List<PaletteEntry> readBackupEntries (final Path path) throws IOException
    {
        if (!Files.isRegularFile (path))
            throw new IOException ("Backup file does not exist: " + path);
        final String json = Files.readString (path, StandardCharsets.UTF_8);
        validateJsonSyntax (json, path);
        final int schemaVersion = Integer.parseInt (requirePatternGroup (BACKUP_SCHEMA_PATTERN, json, "backup schemaVersion", path));
        if (schemaVersion != 1 && schemaVersion != 2)
            throw new IOException (path + ": unsupported backup schemaVersion " + schemaVersion + ".");
        if (!BACKUP_KIND_PATTERN.matcher (json).find ())
            throw new IOException ("Not a Push 2 temporary palette backup: " + path);
        final int firstIndex = Integer.parseInt (requirePatternGroup (BACKUP_FIRST_PATTERN, json, "backup firstIndex", path));
        final int count = Integer.parseInt (requirePatternGroup (BACKUP_COUNT_PATTERN, json, "backup count", path));
        if (firstIndex != CALIBRATION_FIRST_INDEX || count != PAD_COUNT)
            throw new IOException (path + ": backup must cover palette entries 64-127.");

        final List<PaletteEntry> entries = new ArrayList<> (PAD_COUNT);
        final Matcher matcher = BACKUP_ENTRY_PATTERN.matcher (json);
        while (matcher.find ())
        {
            entries.add (new PaletteEntry (
                Integer.parseInt (matcher.group (1)),
                Integer.parseInt (matcher.group (2)),
                Integer.parseInt (matcher.group (3)),
                Integer.parseInt (matcher.group (4)),
                Integer.parseInt (matcher.group (5))));
        }
        validateCalibrationEntries (entries);
        if (schemaVersion >= 2)
        {
            final String expectedHash = requirePatternGroup (BACKUP_HASH_PATTERN, json, "backup entriesSha256", path);
            final String actualHash = paletteEntriesSha256 (entries);
            if (!actualHash.equals (expectedHash))
                throw new IOException (path + ": palette-entry checksum does not match.");
        }
        return List.copyOf (entries);
    }


    private static String paletteEntriesSha256 (final List<PaletteEntry> entries) throws IOException
    {
        final StringBuilder canonical = new StringBuilder (entries.size () * 24);
        for (final PaletteEntry entry: entries)
            canonical.append (entry.index ()).append (',').append (entry.red ()).append (',').append (entry.green ()).append (',').append (entry.blue ()).append (',').append (entry.white ()).append ('\n');
        return sha256Hex (canonical.toString ().getBytes (StandardCharsets.UTF_8));
    }


    private static String sha256Hex (final byte [] data) throws IOException
    {
        try
        {
            final byte [] digest = MessageDigest.getInstance ("SHA-256").digest (data);
            return HexFormat.of ().formatHex (digest);
        }
        catch (final NoSuchAlgorithmException ex)
        {
            throw new IOException ("SHA-256 is unavailable.", ex);
        }
    }


    private static void writeAtomically (final Path path, final String content) throws IOException
    {
        final Path absolute = path.toAbsolutePath ();
        final Path parent = absolute.getParent ();
        if (parent == null)
            throw new IOException ("Output path has no parent directory: " + absolute);
        Files.createDirectories (parent);

        final Path temporary = Files.createTempFile (parent, absolute.getFileName ().toString (), ".tmp");
        boolean moved = false;
        try (FileChannel channel = FileChannel.open (temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            final ByteBuffer data = StandardCharsets.UTF_8.encode (content);
            while (data.hasRemaining ())
                channel.write (data);
            channel.force (true);
        }
        try
        {
            try
            {
                Files.move (temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (final java.nio.file.AtomicMoveNotSupportedException ex)
            {
                Files.move (temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            forceDirectory (parent);
        }
        finally
        {
            if (!moved)
                Files.deleteIfExists (temporary);
        }
    }


    private static void clearRecoveryMarkerIfMatching (final Path backupPath) throws IOException
    {
        final Path marker = recoveryMarkerPath ();
        if (!Files.exists (marker))
            return;
        final String markedBackup = Files.readString (marker, StandardCharsets.UTF_8).trim ();
        if (Path.of (markedBackup).toAbsolutePath ().normalize ().equals (backupPath.toAbsolutePath ().normalize ()))
        {
            Files.deleteIfExists (marker);
            forceDirectory (marker.getParent ());
        }
    }


    private static void ensureRecoveryMarker (final Path marker, final Path backupPath) throws IOException
    {
        final Path absoluteBackup = backupPath.toAbsolutePath ().normalize ();
        if (Files.exists (marker))
        {
            final String markedBackup = Files.readString (marker, StandardCharsets.UTF_8).trim ();
            if (!Path.of (markedBackup).toAbsolutePath ().normalize ().equals (absoluteBackup))
                throw new IOException ("Another palette backup still requires recovery: " + markedBackup);
            return;
        }
        writeAtomically (marker, absoluteBackup + System.lineSeparator ());
    }


    private static void forceDirectory (final Path directory) throws IOException
    {
        try (FileChannel channel = FileChannel.open (directory, StandardOpenOption.READ))
        {
            channel.force (true);
        }
        catch (final UnsupportedOperationException ex)
        {
            // Some non-POSIX file systems do not expose directory channels.
        }
    }


    private static String recoveryCommand (final Path backupPath, final String input, final String output)
    {
        return "./tools/push2-color-test.sh restore " + shellQuote (backupPath.toAbsolutePath ().toString ()) + " --input " + shellQuote (input) + " --output " + shellQuote (output);
    }


    private static String shellQuote (final String value)
    {
        return "'" + value.replace ("'", "'\"'\"'") + "'";
    }


    private static String jsonEscape (final String value)
    {
        final StringBuilder escaped = new StringBuilder (value.length () + 16);
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            switch (character)
            {
                case '\\':
                    escaped.append ("\\\\");
                    break;
                case '"':
                    escaped.append ("\\\"");
                    break;
                case '\n':
                    escaped.append ("\\n");
                    break;
                case '\r':
                    escaped.append ("\\r");
                    break;
                case '\t':
                    escaped.append ("\\t");
                    break;
                default:
                    if (character < 0x20)
                        escaped.append (String.format (Locale.ROOT, "\\u%04X", Integer.valueOf (character)));
                    else
                        escaped.append (character);
                    break;
            }
        }
        return escaped.toString ();
    }


    private static void writeCsv (final Path path, final List<PaletteEntry> entries) throws IOException
    {
        final Path absolutePath = path.toAbsolutePath ();
        if (Files.exists (absolutePath))
            throw new IOException ("Refusing to overwrite existing file: " + absolutePath);

        final Path parent = absolutePath.getParent ();
        if (parent != null && !Files.isDirectory (parent))
            throw new IOException ("Output directory does not exist: " + parent);

        try (BufferedWriter writer = Files.newBufferedWriter (absolutePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            writer.write ("index,red,green,blue,white,hex");
            writer.newLine ();
            for (final PaletteEntry entry: entries)
            {
                writer.write (String.format (Locale.ROOT, "%d,%d,%d,%d,%d,#%02X%02X%02X", Integer.valueOf (entry.index ()), Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ()), Integer.valueOf (entry.white ()), Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ())));
                writer.newLine ();
            }
        }
    }


    private static void showGridWindow (final String title, final List<PaletteEntry> entries) throws Exception
    {
        if (GraphicsEnvironment.isHeadless ())
            throw new IOException ("Cannot create the Swing grid in a headless environment.");

        final CountDownLatch closed = new CountDownLatch (1);
        SwingUtilities.invokeAndWait ( () -> {
            final JFrame frame = new JFrame (title);
            frame.setDefaultCloseOperation (WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener (new WindowAdapter ()
            {
                @Override
                public void windowClosed (final WindowEvent event)
                {
                    closed.countDown ();
                }
            });

            final JLabel note = new JLabel ("Reported palette RGB; physical appearance also includes Push white balance and brightness.", SwingConstants.CENTER);
            note.setBorder (BorderFactory.createEmptyBorder (8, 8, 8, 8));
            frame.add (note, BorderLayout.NORTH);

            final JPanel grid = new JPanel (new GridLayout (8, 8, 3, 3));
            grid.setBackground (Color.DARK_GRAY);
            grid.setBorder (BorderFactory.createEmptyBorder (3, 3, 3, 3));

            // Physical note 36 is bottom-left. Build Swing rows from physical top to bottom.
            for (int physicalRow = 7; physicalRow >= 0; physicalRow--)
            {
                for (int column = 0; column < 8; column++)
                {
                    final PaletteEntry entry = entries.get (physicalRow * 8 + column);
                    grid.add (createColorCell (entry));
                }
            }

            frame.add (grid, BorderLayout.CENTER);
            frame.setMinimumSize (new Dimension (840, 700));
            frame.pack ();
            frame.setLocationByPlatform (true);
            frame.setVisible (true);
            frame.toFront ();
            frame.requestFocus ();
        });

        closed.await ();
    }


    private static void showMatchWindow (final Color target, final List<MatchCandidate> candidates) throws Exception
    {
        if (GraphicsEnvironment.isHeadless ())
            throw new IOException ("Cannot create the Swing comparison in a headless environment.");

        final CountDownLatch closed = new CountDownLatch (1);
        SwingUtilities.invokeAndWait ( () -> {
            final JFrame frame = new JFrame (String.format (Locale.ROOT, "Push 2 target #%02X%02X%02X", Integer.valueOf (target.getRed ()), Integer.valueOf (target.getGreen ()), Integer.valueOf (target.getBlue ())));
            frame.setDefaultCloseOperation (WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener (new WindowAdapter ()
            {
                @Override
                public void windowClosed (final WindowEvent event)
                {
                    closed.countDown ();
                }
            });

            final JPanel targetPanel = new JPanel (new BorderLayout ());
            targetPanel.setBackground (target);
            targetPanel.setBorder (BorderFactory.createEmptyBorder (18, 8, 18, 8));
            final JLabel targetLabel = new JLabel (String.format (Locale.ROOT, "TARGET  #%02X%02X%02X  RGB %d,%d,%d", Integer.valueOf (target.getRed ()), Integer.valueOf (target.getGreen ()), Integer.valueOf (target.getBlue ()), Integer.valueOf (target.getRed ()), Integer.valueOf (target.getGreen ()), Integer.valueOf (target.getBlue ())), SwingConstants.CENTER);
            targetLabel.setFont (targetLabel.getFont ().deriveFont (Font.BOLD, 18.0f));
            targetLabel.setForeground (contrastColor (target));
            targetPanel.add (targetLabel, BorderLayout.CENTER);

            final JPanel north = new JPanel (new BorderLayout ());
            north.add (targetPanel, BorderLayout.CENTER);
            final JLabel note = new JLabel ("Columns 1-8 are mirrored on every pad row and CC20-27. CURRENT is the installed mapper; judge physical LEDs against TARGET.", SwingConstants.CENTER);
            note.setBorder (BorderFactory.createEmptyBorder (8, 8, 8, 8));
            north.add (note, BorderLayout.SOUTH);
            frame.add (north, BorderLayout.NORTH);

            final JPanel candidateGrid = new JPanel (new GridLayout (1, 8, 4, 4));
            candidateGrid.setBackground (Color.DARK_GRAY);
            candidateGrid.setBorder (BorderFactory.createEmptyBorder (4, 4, 4, 4));
            for (int column = 0; column < candidates.size (); column++)
                candidateGrid.add (createMatchCell (column, candidates.get (column)));
            frame.add (candidateGrid, BorderLayout.CENTER);

            frame.setMinimumSize (new Dimension (1200, 400));
            frame.pack ();
            frame.setLocationByPlatform (true);
            frame.setVisible (true);
            frame.toFront ();
            frame.requestFocus ();
        });

        closed.await ();
    }


    private static JPanel createMatchCell (final int column, final MatchCandidate candidate)
    {
        final PaletteEntry programmed = candidate.programmed ();
        final Color background = new Color (programmed.red (), programmed.green (), programmed.blue ());
        final JLabel label = new JLabel (String.format (Locale.ROOT, "<html><center><b>Column %d — index %d</b><br>%s<br><br>Logical %s<br>Programmed %s<br>W %d</center></html>", Integer.valueOf (column + 1), Integer.valueOf (candidate.index ()), candidate.sourcesLabel (), toHex (candidate.logical ()), toHex (programmed), Integer.valueOf (programmed.white ())), SwingConstants.CENTER);
        label.setForeground (contrastColor (background));
        label.setFont (label.getFont ().deriveFont (Font.PLAIN, 12.0f));

        final JPanel cell = new JPanel (new BorderLayout ());
        cell.setBackground (background);
        cell.setBorder (BorderFactory.createLineBorder (candidate.sources ().contains (CandidateSource.CURRENT) ? Color.MAGENTA : Color.GRAY, candidate.sources ().contains (CandidateSource.CURRENT) ? 4 : 1));
        cell.add (label, BorderLayout.CENTER);
        return cell;
    }


    private static JPanel createColorCell (final PaletteEntry entry)
    {
        final Color background = new Color (entry.red (), entry.green (), entry.blue ());

        final JLabel label = new JLabel (String.format (Locale.ROOT, "<html><center><b>%d</b><br>#%02X%02X%02X<br>RGB %d,%d,%d<br>W %d</center></html>", Integer.valueOf (entry.index ()), Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ()), Integer.valueOf (entry.red ()), Integer.valueOf (entry.green ()), Integer.valueOf (entry.blue ()), Integer.valueOf (entry.white ())), SwingConstants.CENTER);
        label.setForeground (contrastColor (background));
        label.setFont (label.getFont ().deriveFont (Font.PLAIN, 11.0f));

        final JPanel cell = new JPanel (new BorderLayout ());
        cell.setBackground (background);
        cell.setBorder (BorderFactory.createLineBorder (Color.GRAY));
        cell.add (label, BorderLayout.CENTER);
        return cell;
    }


    private static Color contrastColor (final Color background)
    {
        final int luminance = (54 * background.getRed () + 183 * background.getGreen () + 19 * background.getBlue ()) >> 8;
        return luminance >= 140 ? Color.BLACK : Color.WHITE;
    }


    private static void printUsage ()
    {
        System.out.println ("Usage:");
        System.out.println ("  push2-color-test.sh list");
        System.out.println ("  push2-color-test.sh dump CSV --input NAME --output NAME [--timeout-ms N] [--retries N]");
        System.out.println ("  push2-color-test.sh bank 0|64 --input NAME --output NAME [--no-window] [--timeout-ms N] [--retries N]");
        System.out.println ("  push2-color-test.sh show INDEX --input NAME --output NAME [--no-window] [--timeout-ms N] [--retries N]");
        System.out.println ("  push2-color-test.sh compare '#RRGGBB' --input NAME --output NAME [--no-window] [--timeout-ms N] [--retries N]");
        System.out.println ("  push2-color-test.sh calibrate [BANK.json] --input NAME --output NAME [--timeout-ms N] [--retries N]");
        System.out.println ("  push2-color-test.sh check-bank BANK.json");
        System.out.println ("  push2-color-test.sh check-backup BACKUP.json");
        System.out.println ("  push2-color-test.sh restore BACKUP.json --input NAME --output NAME [--timeout-ms N] [--retries N]");
        System.out.println ();
        System.out.println ("NAME is a case-insensitive substring of a MIDI device name/description.");
        System.out.println ("Run list first, and quote names containing spaces.");
        System.out.println ();
        System.out.println ("Only calibrate and restore write palette entries. They require the Push Live Port.");
    }


    private enum Command
    {
        LIST,
        DUMP,
        BANK,
        SHOW,
        COMPARE,
        CALIBRATE,
        CHECK_BANK,
        CHECK_BACKUP,
        RESTORE,
        HELP
    }


    private record Options (Command command, String inputSubstring, String outputSubstring, Path path, int number, Color targetColor, int timeoutMillis, int retries, boolean noWindow)
    {
        private static Options parse (final String [] args) throws UsageException
        {
            if (args.length == 0)
                return new Options (Command.HELP, null, null, null, -1, null, DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES, false);

            final Command command = switch (args[0].toLowerCase (Locale.ROOT))
            {
                case "list" -> Command.LIST;
                case "dump" -> Command.DUMP;
                case "bank" -> Command.BANK;
                case "show" -> Command.SHOW;
                case "compare" -> Command.COMPARE;
                case "calibrate" -> Command.CALIBRATE;
                case "check-bank" -> Command.CHECK_BANK;
                case "check-backup" -> Command.CHECK_BACKUP;
                case "restore" -> Command.RESTORE;
                case "help", "--help", "-h" -> Command.HELP;
                default -> throw new UsageException ("Unknown command: " + args[0]);
            };

            String input = null;
            String output = null;
            int timeout = DEFAULT_TIMEOUT_MS;
            int retries = DEFAULT_RETRIES;
            boolean noWindow = false;
            final List<String> positional = new ArrayList<> ();
            for (int index = 1; index < args.length; index++)
            {
                switch (args[index])
                {
                    case "--input":
                        input = requireOptionValue (args, ++index, "--input");
                        break;

                    case "--output":
                        output = requireOptionValue (args, ++index, "--output");
                        break;

                    case "--timeout-ms":
                        timeout = parsePositiveInteger (requireOptionValue (args, ++index, "--timeout-ms"), "--timeout-ms");
                        break;

                    case "--retries":
                        retries = parsePositiveInteger (requireOptionValue (args, ++index, "--retries"), "--retries");
                        break;

                    case "--no-window":
                        noWindow = true;
                        break;

                    default:
                        if (args[index].startsWith ("--"))
                            throw new UsageException ("Unknown option: " + args[index]);
                        positional.add (args[index]);
                        break;
                }
            }

            if (command == Command.LIST || command == Command.HELP)
            {
                if (!positional.isEmpty () || input != null || output != null || noWindow)
                    throw new UsageException (command.name ().toLowerCase (Locale.ROOT) + " does not accept device or positional arguments");
                return new Options (command, null, null, null, -1, null, timeout, retries, false);
            }

            if (command == Command.CHECK_BANK || command == Command.CHECK_BACKUP)
            {
                requirePositionalCount (positional, command, 1);
                if (input != null || output != null || noWindow)
                    throw new UsageException (command.name ().toLowerCase (Locale.ROOT).replace ('_', '-') + " does not accept device or window options");
                return new Options (command, null, null, Path.of (positional.get (0)), -1, null, timeout, retries, false);
            }

            if (input == null || input.isBlank ())
                throw new UsageException ("--input NAME is required");
            if (output == null || output.isBlank ())
                throw new UsageException ("--output NAME is required");

            return switch (command)
            {
                case DUMP -> {
                    requirePositionalCount (positional, command, 1);
                    if (noWindow)
                        throw new UsageException ("dump does not accept --no-window");
                    yield new Options (command, input, output, Path.of (positional.get (0)), -1, null, timeout, retries, false);
                }
                case BANK -> {
                    requirePositionalCount (positional, command, 1);
                    final int base = parseInteger (positional.get (0), "bank base");
                    if (base != 0 && base != 64)
                        throw new UsageException ("bank base must be 0 or 64");
                    yield new Options (command, input, output, null, base, null, timeout, retries, noWindow);
                }
                case SHOW -> {
                    requirePositionalCount (positional, command, 1);
                    final int paletteIndex = parseInteger (positional.get (0), "palette index");
                    if (paletteIndex < 0 || paletteIndex > 127)
                        throw new UsageException ("palette index must be between 0 and 127");
                    yield new Options (command, input, output, null, paletteIndex, null, timeout, retries, noWindow);
                }
                case COMPARE -> {
                    requirePositionalCount (positional, command, 1);
                    yield new Options (command, input, output, null, -1, parseColor (positional.get (0)), timeout, retries, noWindow);
                }
                case CALIBRATE -> {
                    if (positional.size () > 1)
                        throw new UsageException ("calibrate accepts at most one optional BANK.json positional argument");
                    if (noWindow)
                        throw new UsageException ("calibrate does not accept --no-window");
                    yield new Options (command, input, output, positional.isEmpty () ? null : Path.of (positional.get (0)), -1, null, timeout, retries, false);
                }
                case RESTORE -> {
                    requirePositionalCount (positional, command, 1);
                    if (noWindow)
                        throw new UsageException ("restore does not accept --no-window");
                    yield new Options (command, input, output, Path.of (positional.get (0)), -1, null, timeout, retries, false);
                }
                default -> throw new UsageException ("Unsupported command");
            };
        }


        private static void requirePositionalCount (final List<String> positional, final Command command, final int count) throws UsageException
        {
            if (positional.size () != count)
                throw new UsageException (command.name ().toLowerCase (Locale.ROOT) + " requires " + (count == 0 ? "no positional arguments" : "exactly " + count + " positional argument" + (count == 1 ? "" : "s")));
        }


        private static String requireOptionValue (final String [] args, final int index, final String option) throws UsageException
        {
            if (index >= args.length)
                throw new UsageException (option + " requires a value");
            return args[index];
        }


        private static int parsePositiveInteger (final String value, final String label) throws UsageException
        {
            final int number = parseInteger (value, label);
            if (number <= 0)
                throw new UsageException (label + " must be greater than zero");
            return number;
        }


        private static int parseInteger (final String value, final String label) throws UsageException
        {
            try
            {
                return Integer.parseInt (value);
            }
            catch (final NumberFormatException ex)
            {
                throw new UsageException (label + " must be an integer: " + value);
            }
        }


        private static Color parseColor (final String value) throws UsageException
        {
            final String hex = value.startsWith ("#") ? value.substring (1) : value;
            if (!hex.matches ("[0-9A-Fa-f]{6}"))
                throw new UsageException ("target color must use #RRGGBB format: " + value);
            return new Color (Integer.parseInt (hex, 16));
        }
    }


    private enum CandidateSource
    {
        CURRENT,
        LOGICAL,
        PROGRAMMED
    }


    private record LogicalPalette (List<PaletteEntry> entries, int currentIndex)
    {
        // Data record.
    }


    private record MatchCandidate (int index, PaletteEntry logical, PaletteEntry programmed, EnumSet<CandidateSource> sources)
    {
        private String sourcesLabel ()
        {
            return String.join (" + ", this.sources.stream ().map (CandidateSource::name).toList ());
        }
    }


    @FunctionalInterface
    private interface CalibrationTransform
    {
        Color apply (Color color);
    }


    @FunctionalInterface
    private interface CalibrationColumnUpdater
    {
        void update (int column, List<PaletteEntry> entries) throws Exception;
    }


    private record CalibrationTarget (String name, Color color, List<Color> candidates, Color directBase)
    {
        CalibrationTarget (final String name, final Color color)
        {
            this (name, color, List.of (), null);
        }


        private Color getCandidate (final int candidateIndex, final CalibrationModel model)
        {
            if (this.directBase != null)
                throw new IllegalStateException ("A direct-tune target has no candidates.");
            return this.candidates.isEmpty () ? model.transform ().apply (this.color) : this.candidates.get (candidateIndex);
        }
    }


    private record CalibrationModel (String id, String name, String detail, CalibrationTransform transform)
    {
        // Data record.
    }


    private record CalibrationBank (String id, String sourceSha256, CalibrationMode mode, int rowRotation, int rowOffset, List<CalibrationModel> models, List<CalibrationTarget> targets)
    {
        // Data record.
    }


    private record CalibrationCell (int row, int column, int candidateIndex, CalibrationModel model, CalibrationTarget target, PaletteEntry programmed)
    {
        // Data record.
    }


    private enum CalibrationMode
    {
        CANDIDATE_SELECT ("candidate-select"),
        DIRECT_TUNE ("direct-tune");

        private final String jsonValue;


        CalibrationMode (final String jsonValue)
        {
            this.jsonValue = jsonValue;
        }


        String jsonValue ()
        {
            return this.jsonValue;
        }
    }


    private record ColumnAdjustment (double lightness, double a, double b)
    {
        private static final ColumnAdjustment ZERO = new ColumnAdjustment (0.0, 0.0, 0.0);


        private ColumnAdjustment bounded ()
        {
            return new ColumnAdjustment (
                Math.max (-TUNE_MAX_LIGHT, Math.min (TUNE_MAX_LIGHT, this.lightness)),
                Math.max (-TUNE_MAX_TINT, Math.min (TUNE_MAX_TINT, this.a)),
                Math.max (-TUNE_MAX_TINT, Math.min (TUNE_MAX_TINT, this.b)));
        }


        private boolean isZero ()
        {
            return Math.abs (this.lightness) < 1.0e-9 && Math.abs (this.a) < 1.0e-9 && Math.abs (this.b) < 1.0e-9;
        }
    }


    private record AdjustedPaletteEntry (PaletteEntry entry, double scale)
    {
        // Data record.
    }


    private record TuneAction (int column, ColumnAdjustment before, ColumnAdjustment after)
    {
        // Data record.
    }


    private record DeviceSettings (int brightness, List<Integer> whiteBalance)
    {
        // Data record.
    }


    private record WhiteBalanceReply (int group, int value)
    {
        // Data record.
    }


    private record PaletteEntry (int index, int red, int green, int blue, int white)
    {
        private static PaletteEntry fromMessage (final byte [] data)
        {
            return new PaletteEntry (unsigned (data[7]), decode8Bit (data[8], data[9]), decode8Bit (data[10], data[11]), decode8Bit (data[12], data[13]), decode8Bit (data[14], data[15]));
        }


        private static int decode8Bit (final byte lower, final byte upper)
        {
            return unsigned (lower) + (unsigned (upper) << 7);
        }


        private static int unsigned (final byte value)
        {
            return Byte.toUnsignedInt (value);
        }
    }


    private static final class TemporaryPaletteSession
    {
        private final MidiConnection    connection;
        private final List<PaletteEntry> originals;
        private final Path              recoveryMarker;
        private final Path              backupPath;
        private final String            inputSelector;
        private final String            outputSelector;
        private final int               timeoutMillis;
        private final int               retries;
        private volatile boolean        stopRequested;
        private boolean                 dirty;
        private boolean                 restored;


        TemporaryPaletteSession (final MidiConnection connection, final List<PaletteEntry> originals, final Path recoveryMarker, final Path backupPath, final String inputSelector, final String outputSelector, final int timeoutMillis, final int retries)
        {
            this.connection = connection;
            this.originals = List.copyOf (originals);
            this.recoveryMarker = recoveryMarker;
            this.backupPath = backupPath.toAbsolutePath ();
            this.inputSelector = inputSelector;
            this.outputSelector = outputSelector;
            this.timeoutMillis = timeoutMillis;
            this.retries = retries;
        }


        synchronized void program (final List<PaletteEntry> candidates) throws Exception
        {
            if (this.dirty)
                throw new IllegalStateException ("Temporary palette session is already programmed.");
            validateCalibrationEntries (candidates);

            this.armRecovery ();
            System.out.println ("Programming and verifying 64 temporary palette entries...");
            for (int index = 0; index < candidates.size (); index++)
            {
                if (this.stopRequested)
                    throw new InterruptedException ("Shutdown requested during temporary palette programming.");
                writeAndVerify (this.connection, candidates.get (index), this.timeoutMillis, this.retries);
                if ((index + 1) % 8 == 0)
                    System.out.println ("Programmed " + (index + 1) + "/64 entries.");
            }
            sendPaletteReapply (this.connection.output ());
        }


        synchronized void updateColumn (final int column, final List<PaletteEntry> candidates) throws Exception
        {
            if (!this.dirty || this.restored)
                throw new IllegalStateException ("Temporary palette session is not accepting live updates.");
            if (column < 0 || column >= 8 || candidates.size () != 8)
                throw new IOException ("A live calibration update must contain one complete pad column.");

            for (int row = 0; row < 8; row++)
            {
                if (this.stopRequested)
                    throw new InterruptedException ("Shutdown requested during live palette update.");
                final int offset = row * 8 + column;
                final PaletteEntry entry = candidates.get (row);
                final PaletteEntry original = this.originals.get (offset);
                final int expectedIndex = CALIBRATION_FIRST_INDEX + offset;
                if (entry.index () != expectedIndex || entry.white () != original.white () || !isByte (entry.red ()) || !isByte (entry.green ()) || !isByte (entry.blue ()))
                    throw new IOException ("Invalid live calibration entry for row " + row + ", column " + column + ".");
                sendPaletteUpdate (this.connection.output (), entry);
            }
            sendPaletteReapply (this.connection.output ());

            for (final PaletteEntry expected: candidates)
            {
                if (this.stopRequested)
                    throw new InterruptedException ("Shutdown requested during live palette verification.");
                final int liveTimeout = Math.min (this.timeoutMillis, 250);
                final PaletteEntry actual = queryPaletteEntry (this.connection, expected.index (), liveTimeout, 1);
                if (!expected.equals (actual))
                    writeAndVerify (this.connection, expected, liveTimeout, 1);
            }
            sendPaletteReapply (this.connection.output ());
        }


        synchronized void armRecovery () throws IOException
        {
            if (this.dirty)
                return;
            ensureRecoveryMarker (this.recoveryMarker, this.backupPath);
            this.dirty = true;
        }


        synchronized void restore () throws Exception
        {
            this.restoreWithLimits (this.timeoutMillis, this.retries);
        }


        void restoreFromShutdown ()
        {
            this.stopRequested = true;
            synchronized (this)
            {
                try
                {
                    this.restoreWithLimits (Math.min (this.timeoutMillis, 250), 1);
                }
                catch (final Exception ex)
                {
                    System.err.println ("Emergency palette restoration could not be verified: " + ex.getMessage ());
                    System.err.println ("Retry with:");
                    System.err.println ("  " + recoveryCommand (this.backupPath, this.inputSelector, this.outputSelector));
                }
            }
        }


        private void restoreWithLimits (final int timeout, final int attempts) throws Exception
        {
            if (this.restored || !this.dirty)
                return;

            System.out.println ("Restoring and verifying the original Push palette...");
            restoreEntries (this.connection, this.originals, timeout, attempts);
            clearPads (this.connection.output ());
            confirmEntries (this.connection, this.originals, timeout, attempts);
            clearRecoveryMarkerIfMatching (this.backupPath);
            this.dirty = false;
            this.restored = true;
            System.out.println ("Original palette restoration verified.");
        }
    }


    private static final class CalibrationWindow
    {
        private final CalibrationBank          bank;
        private final List<CalibrationModel>   models;
        private final List<CalibrationTarget>  targets;
        private final List<CalibrationCell>    cells;
        private final Path                     resultsPath;
        private final DeviceSettings           settings;
        private final Path                     backupPath;
        private final CalibrationColumnUpdater columnUpdater;
        private final Instant                  createdAt          = Instant.now ();
        private final CountDownLatch           finished           = new CountDownLatch (1);
        private final Object                   tuneLock           = new Object ();
        private final int []                   selectedRows       =
        {
            -1, -1, -1, -1, -1, -1, -1, -1
        };
        private final PaletteEntry []          desiredEntries     = new PaletteEntry [PAD_COUNT];
        private final PaletteEntry []          appliedEntries     = new PaletteEntry [PAD_COUNT];
        private final double []                desiredChromaScales = new double [PAD_COUNT];
        private final double []                appliedChromaScales = new double [PAD_COUNT];
        private final ColumnAdjustment []      desiredAdjustments = new ColumnAdjustment [8];
        private final ColumnAdjustment []      appliedAdjustments = new ColumnAdjustment [8];
        private final long []                  tuneRevisions      = new long [8];
        private final long []                  appliedRevisions   = new long [8];
        private final boolean []               updateQueued       = new boolean [8];
        private final Deque<TuneAction>         tuneHistory        = new ArrayDeque<> ();
        private final JPanel []                targetCells        = new JPanel [8];
        private final JLabel []                tuneLabels         = new JLabel [8];
        private final Timer []                 tuneTimers         = new Timer [8];
        private final ExecutorService          tuneExecutor;
        private final JFrame                   frame;
        private final JLabel                   status;
        private final JSlider                  lightnessSlider;
        private final JLabel                   lightnessValueLabel;
        private final JButton                  undoTuneButton;
        private final JButton                  resetTuneButton;
        private final JButton                  finishButton;
        private final JButton                  cancelButton;
        private volatile Exception             tuningFailure;
        private int                            activeColumn       = -1;
        private boolean                        updatingLightnessControl;
        private ColumnAdjustment               lightnessOriginAdjustment;
        private int                            lightnessOriginColumn = -1;
        private boolean                        finishing;


        private CalibrationWindow (final CalibrationBank bank, final List<CalibrationCell> cells, final Path resultsPath, final DeviceSettings settings, final Path backupPath, final CalibrationColumnUpdater columnUpdater)
        {
            this.bank = bank;
            this.models = bank.models ();
            this.targets = bank.targets ();
            this.cells = List.copyOf (cells);
            this.resultsPath = resultsPath;
            this.settings = settings;
            this.backupPath = backupPath;
            this.columnUpdater = columnUpdater;
            this.tuneExecutor = Executors.newSingleThreadExecutor (task -> {
                final Thread thread = new Thread (task, "push2-column-tune");
                thread.setDaemon (true);
                return thread;
            });
            for (int index = 0; index < this.cells.size (); index++)
            {
                final PaletteEntry programmed = this.cells.get (index).programmed ();
                this.desiredEntries[index] = programmed;
                this.appliedEntries[index] = programmed;
                this.desiredChromaScales[index] = 1.0;
                this.appliedChromaScales[index] = 1.0;
            }
            for (int column = 0; column < 8; column++)
            {
                this.desiredAdjustments[column] = ColumnAdjustment.ZERO;
                this.appliedAdjustments[column] = ColumnAdjustment.ZERO;
                final int tunedColumn = column;
                this.tuneTimers[column] = new Timer (110, event -> this.queueColumnUpdate (tunedColumn));
                this.tuneTimers[column].setRepeats (false);
            }

            this.frame = new JFrame ("Push 2 pad calibration — " + bank.id ());
            this.frame.setDefaultCloseOperation (WindowConstants.DO_NOTHING_ON_CLOSE);
            this.frame.addWindowListener (new WindowAdapter ()
            {
                @Override
                public void windowClosing (final WindowEvent event)
                {
                    CalibrationWindow.this.requestFinish (false);
                }
            });

            final JPanel north = new JPanel (new GridLayout (3, 1));
            final String instructionText = bank.mode () == CalibrationMode.DIRECT_TUNE ? "All eight pads in each column repeat one color. Drag its fixed target swatch until the physical column matches, then press any pad in that column to confirm." : "Compare each physical pad column with its fixed target swatch. Candidate previews stay hidden.";
            final JLabel instructions = new JLabel (instructionText, SwingConstants.CENTER);
            instructions.setFont (instructions.getFont ().deriveFont (Font.BOLD, 15.0f));
            instructions.setBorder (BorderFactory.createEmptyBorder (8, 8, 4, 8));
            north.add (instructions);
            final JLabel tuningInstructions = new JLabel ("Drag a swatch: left/right = green↔magenta, up/down = yellow↔blue · use the visible slider for brightness · Option = fine · double-click = reset", SwingConstants.CENTER);
            tuningInstructions.setBorder (BorderFactory.createEmptyBorder (2, 8, 2, 8));
            north.add (tuningInstructions);
            final String settingsText = bank.mode () == CalibrationMode.DIRECT_TUNE ? String.format (Locale.ROOT, "Bank %s · direct tune · Push LED brightness %d · pad WB R/G/B %d/%d/%d", bank.id (), Integer.valueOf (settings.brightness ()), settings.whiteBalance ().get (3), settings.whiteBalance ().get (4), settings.whiteBalance ().get (5)) : String.format (Locale.ROOT, "Bank %s · candidate select · Push LED brightness %d · pad WB R/G/B %d/%d/%d · row rotation %d · offset %d", bank.id (), Integer.valueOf (settings.brightness ()), settings.whiteBalance ().get (3), settings.whiteBalance ().get (4), settings.whiteBalance ().get (5), Integer.valueOf (bank.rowRotation ()), Integer.valueOf (bank.rowOffset ()));
            final JLabel settingsLabel = new JLabel (settingsText, SwingConstants.CENTER);
            settingsLabel.setBorder (BorderFactory.createEmptyBorder (4, 8, 8, 8));
            north.add (settingsLabel);
            this.frame.add (north, BorderLayout.NORTH);

            final String targetOrder = String.join ("  ·  ", targets.stream ().map (CalibrationTarget::name).toList ());
            final JLabel columns = new JLabel ("Push LCD columns, left → right:  " + targetOrder, SwingConstants.CENTER);
            columns.setBorder (BorderFactory.createEmptyBorder (16, 12, 16, 12));
            this.frame.add (columns, BorderLayout.CENTER);

            final JPanel south = new JPanel (new BorderLayout ());
            final JPanel targetStrip = new JPanel (new GridLayout (1, 8, 4, 0));
            targetStrip.setBackground (Color.DARK_GRAY);
            targetStrip.setBorder (BorderFactory.createEmptyBorder (4, 4, 4, 4));
            for (int column = 0; column < targets.size (); column++)
            {
                final CalibrationTarget target = targets.get (column);
                final Color background = target.color ();
                final JLabel label = new JLabel (String.format (Locale.ROOT, "<html><center><b>%d · %s</b><br>%s</center></html>", Integer.valueOf (column + 1), target.name (), toHex (background)), SwingConstants.CENTER);
                label.setForeground (contrastColor (background));
                final JLabel tuneLabel = new JLabel (this.tuneLabelText (column), SwingConstants.CENTER);
                tuneLabel.setForeground (contrastColor (background));
                tuneLabel.setFont (tuneLabel.getFont ().deriveFont (Font.PLAIN, 10.0f));
                final JPanel cell = new JPanel (new BorderLayout ());
                cell.setBackground (background);
                cell.setBorder (BorderFactory.createLineBorder (Color.GRAY));
                cell.add (label, BorderLayout.CENTER);
                cell.add (tuneLabel, BorderLayout.SOUTH);
                cell.setCursor (Cursor.getPredefinedCursor (Cursor.CROSSHAIR_CURSOR));
                final String tooltip = bank.mode () == CalibrationMode.DIRECT_TUNE ? "Drag to tune the one RGB repeated on all eight physical pads in this column; the displayed target never changes." : "Drag to tune only the physical Push column; the displayed target color never changes.";
                cell.setToolTipText (tooltip);
                label.setToolTipText (tooltip);
                tuneLabel.setToolTipText (tooltip);
                final MouseAdapter tuneMouse = this.createTuneMouseAdapter (column);
                cell.addMouseListener (tuneMouse);
                cell.addMouseMotionListener (tuneMouse);
                label.addMouseListener (tuneMouse);
                label.addMouseMotionListener (tuneMouse);
                tuneLabel.addMouseListener (tuneMouse);
                tuneLabel.addMouseMotionListener (tuneMouse);
                this.targetCells[column] = cell;
                this.tuneLabels[column] = tuneLabel;
                targetStrip.add (cell);
            }
            targetStrip.setPreferredSize (new Dimension (0, 124));
            south.add (targetStrip, BorderLayout.NORTH);

            final JPanel footer = new JPanel (new BorderLayout ());
            this.status = new JLabel (bank.mode () == CalibrationMode.DIRECT_TUNE ? "0/8 confirmed. Tune a column, wait for verification, then press any pad in it." : "0/8 selected. You may reselect any column before finishing.", SwingConstants.CENTER);
            this.status.setBorder (BorderFactory.createEmptyBorder (8, 8, 8, 8));
            footer.add (this.status, BorderLayout.CENTER);
            final JPanel lightnessControls = new JPanel (new BorderLayout (4, 0));
            lightnessControls.setBorder (BorderFactory.createEmptyBorder (2, 8, 2, 8));
            this.lightnessValueLabel = new JLabel ("Lightness ΔL 0.000", SwingConstants.CENTER);
            lightnessControls.add (this.lightnessValueLabel, BorderLayout.NORTH);
            this.lightnessSlider = new JSlider (-100, 100, 0);
            this.lightnessSlider.setEnabled (false);
            this.lightnessSlider.setMajorTickSpacing (50);
            this.lightnessSlider.setPaintTicks (true);
            this.lightnessSlider.setPaintLabels (true);
            final Hashtable<Integer, JLabel> lightnessLabels = new Hashtable<> ();
            lightnessLabels.put (Integer.valueOf (-100), new JLabel ("Darker"));
            lightnessLabels.put (Integer.valueOf (0), new JLabel ("Neutral"));
            lightnessLabels.put (Integer.valueOf (100), new JLabel ("Brighter"));
            this.lightnessSlider.setLabelTable (lightnessLabels);
            this.lightnessSlider.setPreferredSize (new Dimension (310, 54));
            this.lightnessSlider.addChangeListener (event -> this.changeLightness ());
            lightnessControls.add (this.lightnessSlider, BorderLayout.CENTER);
            footer.add (lightnessControls, BorderLayout.WEST);
            final JPanel actions = new JPanel ();
            this.undoTuneButton = new JButton ("Undo Tune");
            this.undoTuneButton.setEnabled (false);
            this.undoTuneButton.addActionListener (this::undoTune);
            actions.add (this.undoTuneButton);
            this.resetTuneButton = new JButton ("Reset Column");
            this.resetTuneButton.setEnabled (false);
            this.resetTuneButton.addActionListener (this::resetTune);
            actions.add (this.resetTuneButton);
            this.cancelButton = new JButton ("Cancel & Restore");
            this.cancelButton.addActionListener (this::cancel);
            actions.add (this.cancelButton);
            this.finishButton = new JButton ("Finish & Restore");
            this.finishButton.setEnabled (false);
            this.finishButton.addActionListener (this::finish);
            actions.add (this.finishButton);
            footer.add (actions, BorderLayout.EAST);
            south.add (footer, BorderLayout.CENTER);
            this.frame.add (south, BorderLayout.SOUTH);

            this.frame.setMinimumSize (new Dimension (1180, 390));
            this.frame.pack ();
            this.frame.setLocationByPlatform (true);
            this.frame.setVisible (true);
            this.frame.toFront ();
            this.frame.requestFocus ();
        }


        static CalibrationWindow open (final CalibrationBank bank, final List<CalibrationCell> cells, final Path resultsPath, final DeviceSettings settings, final Path backupPath, final CalibrationColumnUpdater columnUpdater) throws Exception
        {
            final CalibrationWindow [] holder = new CalibrationWindow [1];
            SwingUtilities.invokeAndWait ( () -> holder[0] = new CalibrationWindow (bank, cells, resultsPath, settings, backupPath, columnUpdater));
            return holder[0];
        }


        void handlePadNote (final int note)
        {
            final int offset = note - FIRST_PAD_NOTE;
            if (offset < 0 || offset >= PAD_COUNT)
                return;
            final int row = offset / 8;
            final int column = offset % 8;
            final long observedRevision;
            synchronized (this.tuneLock)
            {
                observedRevision = this.appliedRevisions[column];
            }
            SwingUtilities.invokeLater ( () -> this.select (row, column, observedRevision));
        }


        void awaitFinish () throws InterruptedException
        {
            this.finished.await ();
        }


        synchronized void writeResults () throws IOException
        {
            final int [] selections;
            synchronized (this.selectedRows)
            {
                selections = this.selectedRows.clone ();
            }
            final PaletteEntry [] entries;
            final double [] chromaScales;
            final ColumnAdjustment [] adjustments;
            synchronized (this.tuneLock)
            {
                entries = this.appliedEntries.clone ();
                chromaScales = this.appliedChromaScales.clone ();
                adjustments = this.appliedAdjustments.clone ();
            }

            final StringBuilder json = new StringBuilder (16384);
            json.append ("{\n");
            json.append ("  \"schemaVersion\": 3,\n");
            json.append ("  \"kind\": \"push2-pad-calibration-results\",\n");
            json.append ("  \"mode\": \"").append (this.bank.mode ().jsonValue ()).append ("\",\n");
            json.append ("  \"bankId\": \"").append (jsonEscape (this.bank.id ())).append ("\",\n");
            if (this.bank.sourceSha256 () == null)
                json.append ("  \"bankSha256\": null,\n");
            else
                json.append ("  \"bankSha256\": \"").append (this.bank.sourceSha256 ()).append ("\",\n");
            if (this.bank.mode () == CalibrationMode.CANDIDATE_SELECT)
            {
                json.append ("  \"rowRotation\": ").append (this.bank.rowRotation ()).append (",\n");
                json.append ("  \"rowOffset\": ").append (this.bank.rowOffset ()).append (",\n");
            }
            json.append ("  \"createdAt\": \"").append (jsonEscape (this.createdAt.toString ())).append ("\",\n");
            json.append ("  \"updatedAt\": \"").append (jsonEscape (Instant.now ().toString ())).append ("\",\n");
            json.append ("  \"backupPath\": \"").append (jsonEscape (this.backupPath.toAbsolutePath ().toString ())).append ("\",\n");
            json.append ("  \"ledBrightness\": ").append (this.settings.brightness ()).append (",\n");
            json.append ("  \"padWhiteBalance\": [").append (this.settings.whiteBalance ().get (3)).append (", ").append (this.settings.whiteBalance ().get (4)).append (", ").append (this.settings.whiteBalance ().get (5)).append ("],\n");
            json.append ("  \"tuning\": {\n");
            json.append ("    \"model\": \"oklab-offset-v1\",\n");
            json.append ("    \"application\": \"").append (this.bank.mode () == CalibrationMode.DIRECT_TUNE ? "one immutable repeated base RGB plus one shared per-column OKLab offset" : "immutable base candidate plus one shared per-column OKLab offset").append ("\",\n");
            json.append ("    \"gamut\": \"preserve requested OKLab lightness and hue; binary-search OKLCh chroma\",\n");
            json.append ("    \"tintPerPixel\": ").append (formatTuneValue (TUNE_TINT_PER_PIXEL)).append (",\n");
            json.append ("    \"lightnessPerPixel\": ").append (formatTuneValue (TUNE_LIGHT_PER_PIXEL)).append (",\n");
            json.append ("    \"fineScale\": ").append (formatTuneValue (TUNE_FINE_SCALE)).append (",\n");
            json.append ("    \"maxTint\": ").append (formatTuneValue (TUNE_MAX_TINT)).append (",\n");
            json.append ("    \"maxLightness\": ").append (formatTuneValue (TUNE_MAX_LIGHT)).append (",\n");
            json.append ("    \"columns\": [\n");
            for (int column = 0; column < adjustments.length; column++)
            {
                final ColumnAdjustment adjustment = adjustments[column];
                json.append ("      {\"column\": ").append (column).append (", \"deltaL\": ").append (formatTuneValue (adjustment.lightness ())).append (", \"deltaA\": ").append (formatTuneValue (adjustment.a ())).append (", \"deltaB\": ").append (formatTuneValue (adjustment.b ())).append ("}");
                json.append (column + 1 == adjustments.length ? "\n" : ",\n");
            }
            json.append ("    ]\n");
            json.append ("  },\n");
            if (this.bank.mode () == CalibrationMode.CANDIDATE_SELECT)
            {
                json.append ("  \"models\": [\n");
                for (int index = 0; index < this.models.size (); index++)
                {
                    final CalibrationModel model = this.models.get (index);
                    json.append ("    {\"candidateIndex\": ").append (index).append (", \"id\": \"").append (jsonEscape (model.id ())).append ("\", \"name\": \"").append (jsonEscape (model.name ())).append ("\", \"detail\": \"").append (jsonEscape (model.detail ())).append ("\"}");
                    json.append (index + 1 == this.models.size () ? "\n" : ",\n");
                }
                json.append ("  ],\n");
            }
            json.append ("  \"targets\": [\n");
            for (int column = 0; column < this.targets.size (); column++)
            {
                final CalibrationTarget target = this.targets.get (column);
                json.append ("    {\"column\": ").append (column).append (", \"name\": \"").append (jsonEscape (target.name ())).append ("\", \"rgb\": \"").append (toHex (target.color ())).append ("\"}");
                json.append (column + 1 == this.targets.size () ? "\n" : ",\n");
            }
            json.append ("  ],\n");
            json.append ("  \"cells\": [\n");
            for (int index = 0; index < this.cells.size (); index++)
            {
                final CalibrationCell cell = this.cells.get (index);
                final PaletteEntry base = cell.programmed ();
                final PaletteEntry entry = entries[index];
                json.append ("    {\"row\": ").append (cell.row ()).append (", \"column\": ").append (cell.column ());
                if (this.bank.mode () == CalibrationMode.CANDIDATE_SELECT)
                    json.append (", \"candidateIndex\": ").append (cell.candidateIndex ()).append (", \"modelId\": \"").append (jsonEscape (cell.model ().id ())).append ("\"");
                json.append (", \"paletteIndex\": ").append (entry.index ()).append (", \"baseProgrammedRgb\": \"").append (toHex (base)).append ("\", \"").append (this.bank.mode () == CalibrationMode.DIRECT_TUNE ? "finalProgrammedRgb" : "programmedRgb").append ("\": \"").append (toHex (entry)).append ("\", \"chromaScale\": ").append (formatTuneValue (chromaScales[index])).append (", \"white\": ").append (entry.white ()).append ("}");
                json.append (index + 1 == this.cells.size () ? "\n" : ",\n");
            }
            json.append ("  ],\n");
            if (this.bank.mode () == CalibrationMode.DIRECT_TUNE)
            {
                json.append ("  \"columns\": [\n");
                for (int column = 0; column < selections.length; column++)
                {
                    final int index = column;
                    final CalibrationTarget target = this.targets.get (column);
                    final ColumnAdjustment adjustment = adjustments[column];
                    json.append ("    {\"column\": ").append (column).append (", \"name\": \"").append (jsonEscape (target.name ())).append ("\", \"targetRgb\": \"").append (toHex (target.color ())).append ("\", \"baseProgrammedRgb\": \"").append (toHex (this.cells.get (index).programmed ())).append ("\", \"finalProgrammedRgb\": \"").append (toHex (entries[index])).append ("\", \"deltaL\": ").append (formatTuneValue (adjustment.lightness ())).append (", \"deltaA\": ").append (formatTuneValue (adjustment.a ())).append (", \"deltaB\": ").append (formatTuneValue (adjustment.b ())).append (", \"chromaScale\": ").append (formatTuneValue (chromaScales[index])).append (", \"complete\": ").append (selections[column] >= 0).append (", \"confirmedByPhysicalRow\": ");
                    if (selections[column] < 0)
                        json.append ("null");
                    else
                        json.append (selections[column]);
                    json.append ("}");
                    json.append (column + 1 == selections.length ? "\n" : ",\n");
                }
                json.append ("  ]\n");
            }
            else
            {
                json.append ("  \"selections\": [\n");
                for (int column = 0; column < selections.length; column++)
                {
                    final int row = selections[column];
                    json.append ("    {\"column\": ").append (column).append (", \"selectedRow\": ").append (row);
                    if (row >= 0)
                    {
                        final int index = row * 8 + column;
                        final CalibrationCell cell = this.cells.get (index);
                        final PaletteEntry entry = entries[index];
                        json.append (", \"candidateIndex\": ").append (cell.candidateIndex ()).append (", \"modelId\": \"").append (jsonEscape (cell.model ().id ())).append ("\", \"paletteIndex\": ").append (entry.index ()).append (", \"baseProgrammedRgb\": \"").append (toHex (cell.programmed ())).append ("\", \"programmedRgb\": \"").append (toHex (entry)).append ("\", \"chromaScale\": ").append (formatTuneValue (chromaScales[index]));
                    }
                    json.append ("}");
                    json.append (column + 1 == selections.length ? "\n" : ",\n");
                }
                json.append ("  ]\n");
            }
            json.append ("}\n");
            writeAtomically (this.resultsPath, json.toString ());
        }


        void showRestoring ()
        {
            SwingUtilities.invokeLater ( () -> {
                this.finishing = true;
                this.undoTuneButton.setEnabled (false);
                this.resetTuneButton.setEnabled (false);
                this.lightnessSlider.setEnabled (false);
                this.finishButton.setEnabled (false);
                this.cancelButton.setEnabled (false);
                this.status.setText ("Restoring and read-back-verifying all 64 original palette entries…");
            });
        }


        void closeAfterRestore (final boolean success, final String failure)
        {
            try
            {
                SwingUtilities.invokeAndWait ( () -> {
                    if (!success)
                        JOptionPane.showMessageDialog (this.frame, "Automatic restoration could not be verified.\n" + failure + "\n\nUse the recovery command printed in the terminal.", "Push palette restoration failed", JOptionPane.ERROR_MESSAGE);
                    this.frame.dispose ();
                });
            }
            catch (final Exception ex)
            {
                System.err.println ("Could not close calibration window cleanly: " + ex.getMessage ());
            }
        }


        private void select (final int row, final int column, final long observedRevision)
        {
            if (this.finishing)
                return;
            if (this.tuningFailure != null)
            {
                this.status.setText ("Live tuning failed; use Cancel & Restore. " + this.tuningFailure.getMessage ());
                return;
            }
            if (this.isColumnPending (column) || !this.isAppliedRevision (column, observedRevision))
            {
                this.status.setText (this.targets.get (column).name () + " changed while that press was being handled; " + (this.bank.mode () == CalibrationMode.DIRECT_TUNE ? "press any pad in its column again." : "press its closest pad again."));
                return;
            }
            this.setActiveColumn (column);
            synchronized (this.selectedRows)
            {
                this.selectedRows[column] = row;
                this.refreshFinishButton ();
            }

            final CalibrationCell selected = this.cells.get (row * 8 + column);
            final PaletteEntry entry;
            synchronized (this.tuneLock)
            {
                entry = this.appliedEntries[row * 8 + column];
            }
            final String physicalRow = Character.toString ((char) ('A' + row));
            if (this.bank.mode () == CalibrationMode.DIRECT_TUNE)
            {
                this.status.setText (countSelected (this.selectedRows) + "/8 confirmed · " + selected.target ().name () + " · repeated programmed RGB " + toHex (entry) + " · confirmed from physical row " + physicalRow);
                System.out.println (selected.target ().name () + ": confirmed direct-tuned column from physical row " + physicalRow + ", repeated programmed " + toHex (entry));
            }
            else
            {
                this.status.setText (countSelected (this.selectedRows) + "/8 selected · " + selected.target ().name () + " → candidate " + selected.model ().id () + " " + selected.model ().name () + " · physical row " + physicalRow + " · " + toHex (entry));
                System.out.println (selected.target ().name () + ": selected physical row " + physicalRow + ", candidate " + selected.model ().id () + " (" + selected.model ().name () + "), programmed " + toHex (entry));
            }
            try
            {
                this.writeResults ();
            }
            catch (final IOException ex)
            {
                this.status.setText ("Could not save the latest selection: " + ex.getMessage ());
                System.err.println (this.status.getText ());
            }
        }


        private void finish (final ActionEvent event)
        {
            this.requestFinish (true);
        }


        private void cancel (final ActionEvent event)
        {
            this.requestFinish (false);
        }


        private void requestFinish (final boolean requireComplete)
        {
            if (this.finishing)
                return;
            if (requireComplete && this.tuningFailure != null)
                return;
            synchronized (this.selectedRows)
            {
                if (requireComplete && !allSelected (this.selectedRows))
                    return;
            }
            this.finishing = true;
            this.undoTuneButton.setEnabled (false);
            this.resetTuneButton.setEnabled (false);
            this.lightnessSlider.setEnabled (false);
            this.finishButton.setEnabled (false);
            this.cancelButton.setEnabled (false);
            this.stopTuneTimers ();
            this.status.setText ("Finishing pending palette verification, then preparing to restore…");
            this.tuneExecutor.execute (this.finished::countDown);
            this.tuneExecutor.shutdown ();
        }


        private MouseAdapter createTuneMouseAdapter (final int column)
        {
            return new MouseAdapter ()
            {
                private int              originX;
                private int              originY;
                private ColumnAdjustment originAdjustment;
                private boolean          dragging;


                @Override
                public void mousePressed (final MouseEvent event)
                {
                    if (CalibrationWindow.this.finishing || CalibrationWindow.this.tuningFailure != null)
                        return;
                    CalibrationWindow.this.setActiveColumn (column);
                    this.originX = event.getX ();
                    this.originY = event.getY ();
                    synchronized (CalibrationWindow.this.tuneLock)
                    {
                        this.originAdjustment = CalibrationWindow.this.desiredAdjustments[column];
                    }
                    this.dragging = true;
                }


                @Override
                public void mouseDragged (final MouseEvent event)
                {
                    if (!this.dragging)
                        return;
                    this.applyDrag (event);
                }


                @Override
                public void mouseReleased (final MouseEvent event)
                {
                    if (!this.dragging)
                        return;
                    this.applyDrag (event);
                    final ColumnAdjustment after;
                    synchronized (CalibrationWindow.this.tuneLock)
                    {
                        after = CalibrationWindow.this.desiredAdjustments[column];
                    }
                    if (!this.originAdjustment.equals (after))
                    {
                        CalibrationWindow.this.tuneHistory.push (new TuneAction (column, this.originAdjustment, after));
                        CalibrationWindow.this.flushColumnUpdate (column);
                        CalibrationWindow.this.updateTuneControls ();
                    }
                    this.dragging = false;
                }


                @Override
                public void mouseClicked (final MouseEvent event)
                {
                    if (event.getClickCount () == 2)
                        CalibrationWindow.this.resetColumn (column);
                }


                private void applyDrag (final MouseEvent event)
                {
                    final double fine = (event.getModifiersEx () & InputEvent.ALT_DOWN_MASK) == 0 ? 1.0 : TUNE_FINE_SCALE;
                    final int deltaX = event.getX () - this.originX;
                    final int deltaY = event.getY () - this.originY;
                    final ColumnAdjustment requested;
                    if ((event.getModifiersEx () & InputEvent.SHIFT_DOWN_MASK) != 0)
                    {
                        requested = new ColumnAdjustment (
                            this.originAdjustment.lightness () - deltaY * TUNE_LIGHT_PER_PIXEL * fine,
                            this.originAdjustment.a (),
                            this.originAdjustment.b ()).bounded ();
                    }
                    else
                    {
                        requested = new ColumnAdjustment (
                            this.originAdjustment.lightness (),
                            this.originAdjustment.a () + deltaX * TUNE_TINT_PER_PIXEL * fine,
                            this.originAdjustment.b () - deltaY * TUNE_TINT_PER_PIXEL * fine).bounded ();
                    }
                    CalibrationWindow.this.setDesiredAdjustment (column, requested);
                }
            };
        }


        private void setDesiredAdjustment (final int column, final ColumnAdjustment adjustment)
        {
            if (this.finishing || this.tuningFailure != null)
                return;
            synchronized (this.tuneLock)
            {
                if (this.desiredAdjustments[column].equals (adjustment))
                    return;
                this.desiredAdjustments[column] = adjustment;
                this.rebuildDesiredColumn (column);
                this.tuneRevisions[column]++;
            }
            synchronized (this.selectedRows)
            {
                this.selectedRows[column] = -1;
            }
            this.setActiveColumn (column);
            this.updateTuneLabel (column);
            this.refreshFinishButton ();
            this.status.setText (this.targets.get (column).name () + " tuning queued; " + (this.bank.mode () == CalibrationMode.DIRECT_TUNE ? "confirm any pad in its column after verification." : "reselect its closest pad after verification."));
            if (!this.tuneTimers[column].isRunning ())
                this.tuneTimers[column].start ();
        }


        private void rebuildDesiredColumn (final int column)
        {
            final ColumnAdjustment adjustment = this.desiredAdjustments[column];
            for (int row = 0; row < 8; row++)
            {
                final int index = row * 8 + column;
                final AdjustedPaletteEntry adjusted = applyTune (this.cells.get (index).programmed (), adjustment);
                this.desiredEntries[index] = adjusted.entry ();
                this.desiredChromaScales[index] = adjusted.scale ();
            }
        }


        private void queueColumnUpdate (final int column)
        {
            if (this.finishing || this.tuningFailure != null)
                return;
            synchronized (this.tuneLock)
            {
                if (this.updateQueued[column])
                    return;
                this.updateQueued[column] = true;
            }
            this.tuneExecutor.execute ( () -> this.runColumnUpdates (column));
        }


        private void flushColumnUpdate (final int column)
        {
            this.tuneTimers[column].stop ();
            this.queueColumnUpdate (column);
        }


        private void stopTuneTimers ()
        {
            for (final Timer timer: this.tuneTimers)
            {
                if (timer != null)
                    timer.stop ();
            }
        }


        private void runColumnUpdates (final int column)
        {
            while (true)
            {
                final long revision;
                final List<PaletteEntry> entries;
                final double [] scales = new double [8];
                final ColumnAdjustment adjustment;
                synchronized (this.tuneLock)
                {
                    revision = this.tuneRevisions[column];
                    adjustment = this.desiredAdjustments[column];
                    final List<PaletteEntry> snapshot = new ArrayList<> (8);
                    for (int row = 0; row < 8; row++)
                    {
                        final int index = row * 8 + column;
                        snapshot.add (this.desiredEntries[index]);
                        scales[row] = this.desiredChromaScales[index];
                    }
                    entries = List.copyOf (snapshot);
                }

                try
                {
                    this.columnUpdater.update (column, entries);
                }
                catch (final Exception ex)
                {
                    synchronized (this.tuneLock)
                    {
                        this.updateQueued[column] = false;
                    }
                    this.tuningFailure = ex;
                    SwingUtilities.invokeLater ( () -> {
                        this.stopTuneTimers ();
                        this.finishButton.setEnabled (false);
                        this.undoTuneButton.setEnabled (false);
                        this.resetTuneButton.setEnabled (false);
                        this.lightnessSlider.setEnabled (false);
                        this.status.setText ("Live tuning failed and the visible column may be partial; use Cancel & Restore. " + ex.getMessage ());
                    });
                    return;
                }

                final boolean current;
                synchronized (this.tuneLock)
                {
                    for (int row = 0; row < 8; row++)
                    {
                        final int index = row * 8 + column;
                        this.appliedEntries[index] = entries.get (row);
                        this.appliedChromaScales[index] = scales[row];
                    }
                    this.appliedAdjustments[column] = adjustment;
                    this.appliedRevisions[column] = revision;
                    current = revision == this.tuneRevisions[column];
                    if (current)
                        this.updateQueued[column] = false;
                }

                try
                {
                    this.writeResults ();
                }
                catch (final IOException ex)
                {
                    this.tuningFailure = ex;
                    SwingUtilities.invokeLater ( () -> {
                        this.stopTuneTimers ();
                        this.finishButton.setEnabled (false);
                        this.undoTuneButton.setEnabled (false);
                        this.resetTuneButton.setEnabled (false);
                        this.lightnessSlider.setEnabled (false);
                        this.status.setText ("The tuned palette was verified but its result could not be saved; use Cancel & Restore. " + ex.getMessage ());
                    });
                    return;
                }

                if (!current)
                    continue;

                final int limited = countGamutLimited (scales);
                SwingUtilities.invokeLater ( () -> {
                    if (this.finishing || this.isColumnPending (column) || !this.isAppliedRevision (column, revision))
                        return;
                    this.updateTuneLabel (column);
                    this.refreshFinishButton ();
                    final String limitedText = limited == 0 ? "." : " · gamut-limited " + limited + (this.bank.mode () == CalibrationMode.DIRECT_TUNE ? "/8 repeated entries." : "/8 candidates.");
                    this.status.setText (this.targets.get (column).name () + " applied and verified" + limitedText + (this.bank.mode () == CalibrationMode.DIRECT_TUNE ? " Press any pad in its column to confirm." : " Select its closest physical pad."));
                });
                return;
            }
        }


        private boolean isColumnPending (final int column)
        {
            synchronized (this.tuneLock)
            {
                return this.updateQueued[column] || !this.desiredAdjustments[column].equals (this.appliedAdjustments[column]);
            }
        }


        private boolean isAppliedRevision (final int column, final long revision)
        {
            synchronized (this.tuneLock)
            {
                return this.appliedRevisions[column] == revision;
            }
        }


        private boolean anyColumnPending ()
        {
            synchronized (this.tuneLock)
            {
                for (int column = 0; column < 8; column++)
                {
                    if (this.updateQueued[column] || !this.desiredAdjustments[column].equals (this.appliedAdjustments[column]))
                        return true;
                }
            }
            return false;
        }


        private void undoTune (final ActionEvent event)
        {
            if (this.tuneHistory.isEmpty () || this.finishing || this.tuningFailure != null)
                return;
            final TuneAction action = this.tuneHistory.pop ();
            this.setDesiredAdjustment (action.column (), action.before ());
            this.flushColumnUpdate (action.column ());
            this.updateTuneControls ();
        }


        private void resetTune (final ActionEvent event)
        {
            if (this.activeColumn >= 0)
                this.resetColumn (this.activeColumn);
        }


        private void resetColumn (final int column)
        {
            if (this.finishing || this.tuningFailure != null)
                return;
            final ColumnAdjustment before;
            synchronized (this.tuneLock)
            {
                before = this.desiredAdjustments[column];
            }
            if (before.isZero ())
                return;
            this.tuneHistory.push (new TuneAction (column, before, ColumnAdjustment.ZERO));
            this.setDesiredAdjustment (column, ColumnAdjustment.ZERO);
            this.flushColumnUpdate (column);
            this.updateTuneControls ();
        }


        private void setActiveColumn (final int column)
        {
            if (this.activeColumn != column)
            {
                this.lightnessOriginAdjustment = null;
                this.lightnessOriginColumn = -1;
            }
            this.activeColumn = column;
            for (int index = 0; index < this.targetCells.length; index++)
            {
                final JPanel cell = this.targetCells[index];
                if (cell != null)
                    cell.setBorder (BorderFactory.createLineBorder (index == column ? Color.CYAN : Color.GRAY, index == column ? 3 : 1));
            }
            this.updateTuneControls ();
        }


        private void updateTuneControls ()
        {
            this.undoTuneButton.setEnabled (!this.finishing && this.tuningFailure == null && !this.tuneHistory.isEmpty ());
            boolean canReset = false;
            if (this.activeColumn >= 0)
            {
                synchronized (this.tuneLock)
                {
                    canReset = !this.desiredAdjustments[this.activeColumn].isZero ();
                }
            }
            this.resetTuneButton.setEnabled (!this.finishing && this.tuningFailure == null && canReset);
            this.updateLightnessControl ();
        }


        private void changeLightness ()
        {
            if (this.updatingLightnessControl || this.activeColumn < 0 || this.finishing || this.tuningFailure != null)
                return;

            final int column = this.activeColumn;
            final ColumnAdjustment before;
            synchronized (this.tuneLock)
            {
                before = this.desiredAdjustments[column];
            }
            if (this.lightnessOriginAdjustment == null || this.lightnessOriginColumn != column)
            {
                this.lightnessOriginAdjustment = before;
                this.lightnessOriginColumn = column;
            }

            final ColumnAdjustment requested = new ColumnAdjustment (this.lightnessSlider.getValue () * TUNE_LIGHT_PER_PIXEL, before.a (), before.b ()).bounded ();
            if (!before.equals (requested))
                this.setDesiredAdjustment (column, requested);

            if (this.lightnessSlider.getValueIsAdjusting ())
                return;

            final ColumnAdjustment after;
            synchronized (this.tuneLock)
            {
                after = this.desiredAdjustments[column];
            }
            if (!this.lightnessOriginAdjustment.equals (after))
            {
                this.tuneHistory.push (new TuneAction (column, this.lightnessOriginAdjustment, after));
                this.flushColumnUpdate (column);
            }
            this.lightnessOriginAdjustment = null;
            this.lightnessOriginColumn = -1;
            this.updateTuneControls ();
        }


        private void updateLightnessControl ()
        {
            final boolean enabled = !this.finishing && this.tuningFailure == null && this.activeColumn >= 0;
            final double lightness;
            if (this.activeColumn < 0)
                lightness = 0.0;
            else
            {
                synchronized (this.tuneLock)
                {
                    lightness = this.desiredAdjustments[this.activeColumn].lightness ();
                }
            }

            this.updatingLightnessControl = true;
            try
            {
                this.lightnessSlider.setValue ((int) Math.round (lightness / TUNE_LIGHT_PER_PIXEL));
                this.lightnessSlider.setEnabled (enabled);
                this.lightnessValueLabel.setText (this.activeColumn < 0 ? "Select a column for lightness" : String.format (Locale.ROOT, "%s · Lightness ΔL %+.3f", this.targets.get (this.activeColumn).name (), Double.valueOf (lightness)));
            }
            finally
            {
                this.updatingLightnessControl = false;
            }
        }


        private void updateTuneLabel (final int column)
        {
            this.tuneLabels[column].setText (this.tuneLabelText (column));
        }


        private String tuneLabelText (final int column)
        {
            final ColumnAdjustment adjustment;
            final int limited;
            synchronized (this.tuneLock)
            {
                adjustment = this.desiredAdjustments[column];
                final double [] scales = new double [8];
                for (int row = 0; row < 8; row++)
                    scales[row] = this.desiredChromaScales[row * 8 + column];
                limited = countGamutLimited (scales);
            }
            if (adjustment.isZero ())
                return "<html><center>drag to tune<br>ΔL 0 · Δa 0 · Δb 0</center></html>";
            return String.format (Locale.ROOT, "<html><center>ΔL %+.3f · Δa %+.3f · Δb %+.3f%s</center></html>", Double.valueOf (adjustment.lightness ()), Double.valueOf (adjustment.a ()), Double.valueOf (adjustment.b ()), limited == 0 ? "" : "<br>gamut-limited " + limited + "/8");
        }


        private void refreshFinishButton ()
        {
            boolean complete;
            synchronized (this.selectedRows)
            {
                complete = allSelected (this.selectedRows);
            }
            this.finishButton.setEnabled (!this.finishing && this.tuningFailure == null && complete && !this.anyColumnPending ());
        }


        private static int countGamutLimited (final double [] scales)
        {
            int count = 0;
            for (final double scale: scales)
            {
                if (scale < 0.999999)
                    count++;
            }
            return count;
        }


        private static String formatTuneValue (final double value)
        {
            return String.format (Locale.ROOT, "%.6f", Double.valueOf (Math.abs (value) < 0.0000005 ? 0.0 : value));
        }


        private static boolean allSelected (final int [] selections)
        {
            for (final int row: selections)
            {
                if (row < 0)
                    return false;
            }
            return true;
        }


        private static int countSelected (final int [] selections)
        {
            int count = 0;
            for (final int row: selections)
            {
                if (row >= 0)
                    count++;
            }
            return count;
        }
    }


    private static final class PushInputReceiver implements Receiver
    {
        private final BlockingQueue<PaletteEntry>      paletteReplies      = new LinkedBlockingQueue<> ();
        private final BlockingQueue<Integer>           brightnessReplies   = new LinkedBlockingQueue<> ();
        private final BlockingQueue<WhiteBalanceReply> whiteBalanceReplies = new LinkedBlockingQueue<> ();
        private final boolean []                       heldPads             = new boolean [128];
        private volatile IntConsumer                   padListener;
        private volatile boolean                       isClosed;


        @Override
        public void send (final MidiMessage message, final long timestamp)
        {
            if (this.isClosed)
                return;

            if (TRACE_MIDI)
                System.out.println ("MIDI IN:  " + message.getClass ().getSimpleName () + " " + formatMidiBytes (message.getMessage ()));

            if (message instanceof ShortMessage shortMessage)
            {
                this.handleShortMessage (shortMessage);
                return;
            }
            if (!(message instanceof SysexMessage))
                return;

            final byte [] data = message.getMessage ();
            if (!hasPushHeader (data))
                return;

            final int command = Byte.toUnsignedInt (data[6]);
            if (command == GET_PALETTE_ENTRY && isPaletteReply (data))
                this.paletteReplies.offer (PaletteEntry.fromMessage (data));
            else if (command == GET_LED_BRIGHTNESS && data.length == 9)
                this.brightnessReplies.offer (Integer.valueOf (Byte.toUnsignedInt (data[7])));
            else if (command == GET_LED_WHITE_BALANCE && data.length == 11 && Byte.toUnsignedInt (data[9]) <= 0x0F)
                this.whiteBalanceReplies.offer (new WhiteBalanceReply (Byte.toUnsignedInt (data[7]), Byte.toUnsignedInt (data[8]) + (Byte.toUnsignedInt (data[9]) << 7)));
        }


        void acceptNativeMessage (final byte [] data)
        {
            if (data.length == 0)
                return;
            try
            {
                if (Byte.toUnsignedInt (data[0]) == SysexMessage.SYSTEM_EXCLUSIVE)
                {
                    this.send (new SysexMessage (data, data.length), -1);
                    return;
                }

                if (data.length > 3)
                    throw new InvalidMidiDataException ("Short MIDI message is longer than three bytes");
                final int data1 = data.length > 1 ? Byte.toUnsignedInt (data[1]) : 0;
                final int data2 = data.length > 2 ? Byte.toUnsignedInt (data[2]) : 0;
                this.send (new ShortMessage (Byte.toUnsignedInt (data[0]), data1, data2), -1);
            }
            catch (final InvalidMidiDataException ex)
            {
                if (TRACE_MIDI)
                    System.err.println ("Ignored invalid native MIDI message " + formatMidiBytes (data) + ": " + ex.getMessage ());
            }
        }


        PaletteEntry pollPalette (final long timeout, final TimeUnit unit) throws InterruptedException
        {
            return this.paletteReplies.poll (timeout, unit);
        }


        void discardPaletteReplies ()
        {
            this.paletteReplies.clear ();
        }


        Integer pollBrightness (final long timeout, final TimeUnit unit) throws InterruptedException
        {
            return this.brightnessReplies.poll (timeout, unit);
        }


        void discardBrightnessReplies ()
        {
            this.brightnessReplies.clear ();
        }


        WhiteBalanceReply pollWhiteBalance (final long timeout, final TimeUnit unit) throws InterruptedException
        {
            return this.whiteBalanceReplies.poll (timeout, unit);
        }


        void discardWhiteBalanceReplies ()
        {
            this.whiteBalanceReplies.clear ();
        }


        void setPadListener (final IntConsumer listener)
        {
            synchronized (this.heldPads)
            {
                for (int note = FIRST_PAD_NOTE; note < FIRST_PAD_NOTE + PAD_COUNT; note++)
                    this.heldPads[note] = false;
                this.padListener = listener;
            }
        }


        @Override
        public void close ()
        {
            this.isClosed = true;
            this.padListener = null;
            this.paletteReplies.clear ();
            this.brightnessReplies.clear ();
            this.whiteBalanceReplies.clear ();
        }


        private void handleShortMessage (final ShortMessage message)
        {
            final int note = message.getData1 ();
            if (note < FIRST_PAD_NOTE || note >= FIRST_PAD_NOTE + PAD_COUNT)
                return;
            synchronized (this.heldPads)
            {
                if (message.getCommand () == ShortMessage.NOTE_OFF || message.getCommand () == ShortMessage.NOTE_ON && message.getData2 () == 0)
                {
                    this.heldPads[note] = false;
                    return;
                }
                final IntConsumer listener = this.padListener;
                if (listener == null || message.getCommand () != ShortMessage.NOTE_ON || this.heldPads[note])
                    return;
                this.heldPads[note] = true;
                listener.accept (note);
            }
        }


        private static boolean isPaletteReply (final byte [] data)
        {
            if (data.length != PALETTE_REPLY_LENGTH)
                return false;
            return Byte.toUnsignedInt (data[6]) == GET_PALETTE_ENTRY && Byte.toUnsignedInt (data[9]) <= 1 && Byte.toUnsignedInt (data[11]) <= 1 && Byte.toUnsignedInt (data[13]) <= 1 && Byte.toUnsignedInt (data[15]) <= 1;
        }


        private static boolean hasPushHeader (final byte [] data)
        {
            if (data.length < PUSH2_SYSEX_HEADER.length + 2 || Byte.toUnsignedInt (data[data.length - 1]) != 0xF7)
                return false;
            for (int index = 0; index < PUSH2_SYSEX_HEADER.length; index++)
            {
                if (data[index] != PUSH2_SYSEX_HEADER[index])
                    return false;
            }
            return true;
        }
    }


    private static final class MidiConnection implements AutoCloseable
    {
        private final MidiDevice        inputDevice;
        private final MidiDevice        outputDevice;
        private final Transmitter       transmitter;
        private final Receiver          output;
        private final PushInputReceiver input;
        private final CoreMidiBridge     coreMidiBridge;
        private final String             inputDescription;
        private final String             outputDescription;


        private MidiConnection (final MidiDevice inputDevice, final MidiDevice outputDevice, final Transmitter transmitter, final Receiver output, final PushInputReceiver input, final CoreMidiBridge coreMidiBridge, final String inputDescription, final String outputDescription)
        {
            this.inputDevice = inputDevice;
            this.outputDevice = outputDevice;
            this.transmitter = transmitter;
            this.output = output;
            this.input = input;
            this.coreMidiBridge = coreMidiBridge;
            this.inputDescription = inputDescription;
            this.outputDescription = outputDescription;
        }


        static MidiConnection open (final String inputSubstring, final String outputSubstring) throws MidiUnavailableException, UsageException
        {
            final String bridgePath = System.getProperty ("push2.coremidi.bridge", "");
            if (!bridgePath.isBlank ())
                return openCoreMidiBridge (Path.of (bridgePath), inputSubstring, outputSubstring);

            final MidiDevice inputDevice = findDevice (inputSubstring, true);
            final MidiDevice outputDevice = findDevice (outputSubstring, false);

            Transmitter transmitter = null;
            Receiver output = null;
            PushInputReceiver input = null;
            try
            {
                System.out.println ("Opening MIDI input:  " + describe (inputDevice.getDeviceInfo ()));
                inputDevice.open ();
                if (outputDevice != inputDevice)
                {
                    System.out.println ("Opening MIDI output: " + describe (outputDevice.getDeviceInfo ()));
                    outputDevice.open ();
                }

                input = new PushInputReceiver ();
                transmitter = inputDevice.getTransmitter ();
                transmitter.setReceiver (input);
                output = outputDevice.getReceiver ();

                System.out.println ("MIDI input:  " + describe (inputDevice.getDeviceInfo ()));
                System.out.println ("MIDI output: " + describe (outputDevice.getDeviceInfo ()));
                if (normalize (outputDevice.getDeviceInfo ()).contains ("user port"))
                    System.out.println ("Warning: Push may ignore pad/button LED messages on the User Port while it is in Live mode. This tool does not change MIDI mode.");

                return new MidiConnection (inputDevice, outputDevice, transmitter, output, input, null, describe (inputDevice.getDeviceInfo ()), describe (outputDevice.getDeviceInfo ()));
            }
            catch (final MidiUnavailableException ex)
            {
                if (transmitter != null)
                    transmitter.close ();
                if (output != null)
                    output.close ();
                if (input != null)
                    input.close ();
                inputDevice.close ();
                if (outputDevice != inputDevice)
                    outputDevice.close ();
                throw new MidiUnavailableException ("Could not open the selected Push MIDI port. Fully quit Bitwig and Live, then try again. " + ex.getMessage ());
            }
        }


        Receiver output ()
        {
            return this.output;
        }


        PushInputReceiver input ()
        {
            return this.input;
        }


        boolean isLivePort ()
        {
            return normalize (this.inputDescription).contains ("live port") && normalize (this.outputDescription).contains ("live port");
        }


        String inputDescription ()
        {
            return this.inputDescription;
        }


        String outputDescription ()
        {
            return this.outputDescription;
        }


        @Override
        public void close ()
        {
            if (this.coreMidiBridge != null)
            {
                this.coreMidiBridge.close ();
                this.input.close ();
                return;
            }

            this.transmitter.close ();
            this.output.close ();
            this.input.close ();
            this.inputDevice.close ();
            if (this.outputDevice != this.inputDevice)
                this.outputDevice.close ();
        }


        private static MidiConnection openCoreMidiBridge (final Path bridgePath, final String inputSubstring, final String outputSubstring) throws MidiUnavailableException
        {
            final PushInputReceiver input = new PushInputReceiver ();
            try
            {
                System.out.println ("Opening native CoreMIDI input/output bridge...");
                final CoreMidiBridge bridge = CoreMidiBridge.open (bridgePath, inputSubstring, outputSubstring, input);
                System.out.println ("MIDI input:  " + bridge.inputDescription ());
                System.out.println ("MIDI output: " + bridge.outputDescription ());
                if (normalize (bridge.outputDescription ()).contains ("user port"))
                    System.out.println ("Warning: Push may ignore pad/button LED messages on the User Port while it is in Live mode. This tool does not change MIDI mode.");
                return new MidiConnection (null, null, null, bridge, input, bridge, bridge.inputDescription (), bridge.outputDescription ());
            }
            catch (final IOException ex)
            {
                input.close ();
                final MidiUnavailableException failure = new MidiUnavailableException ("Could not open the native Push CoreMIDI bridge. Fully quit Bitwig and Live, then try again. " + ex.getMessage ());
                failure.initCause (ex);
                throw failure;
            }
        }


        private static MidiDevice findDevice (final String substring, final boolean input) throws MidiUnavailableException, UsageException
        {
            final String needle = substring.toLowerCase (Locale.ROOT);
            final List<MidiDevice> matches = new ArrayList<> ();
            for (final MidiDevice.Info info: MidiSystem.getMidiDeviceInfo ())
            {
                final MidiDevice device = MidiSystem.getMidiDevice (info);
                final boolean supportsDirection = input ? device.getMaxTransmitters () != 0 : device.getMaxReceivers () != 0;
                if (supportsDirection && normalize (info).contains (needle))
                    matches.add (device);
            }

            final String direction = input ? "input" : "output";
            if (matches.isEmpty ())
                throw new UsageException ("No MIDI " + direction + " matches \"" + substring + "\". Run the list command.");
            if (matches.size () > 1)
            {
                final StringBuilder message = new StringBuilder ("MIDI ").append (direction).append (" substring \"").append (substring).append ("\" is ambiguous:");
                for (final MidiDevice match: matches)
                    message.append (System.lineSeparator ()).append ("  ").append (describe (match.getDeviceInfo ()));
                throw new UsageException (message.toString ());
            }
            return matches.get (0);
        }


        private static String normalize (final MidiDevice.Info info)
        {
            return (info.getName () + " " + info.getVendor () + " " + info.getDescription () + " " + info.getVersion ()).toLowerCase (Locale.ROOT);
        }


        private static String normalize (final String text)
        {
            return text.toLowerCase (Locale.ROOT);
        }


        private static String describe (final MidiDevice.Info info)
        {
            return info.getName () + " | " + info.getVendor () + " | " + info.getDescription ();
        }
    }


    private static final class CoreMidiBridge implements Receiver
    {
        private final Process           process;
        private final BufferedReader    reader;
        private final BufferedWriter    writer;
        private final PushInputReceiver input;
        private final String            inputDescription;
        private final String            outputDescription;
        private final Thread            readerThread;
        private volatile IOException    failure;
        private volatile boolean        isClosed;


        private CoreMidiBridge (final Process process, final BufferedReader reader, final BufferedWriter writer, final PushInputReceiver input, final String inputDescription, final String outputDescription)
        {
            this.process = process;
            this.reader = reader;
            this.writer = writer;
            this.input = input;
            this.inputDescription = inputDescription;
            this.outputDescription = outputDescription;
            this.readerThread = new Thread (this::readMessages, "Push 2 CoreMIDI bridge reader");
            this.readerThread.setDaemon (true);
            this.readerThread.start ();
        }


        static CoreMidiBridge open (final Path executable, final String inputSelector, final String outputSelector, final PushInputReceiver input) throws IOException
        {
            if (!Files.isExecutable (executable))
                throw new IOException ("Native bridge is missing or not executable: " + executable);

            final Process process = new ProcessBuilder (executable.toString (), inputSelector, outputSelector).redirectErrorStream (true).start ();
            final BufferedReader reader = new BufferedReader (new InputStreamReader (process.getInputStream (), StandardCharsets.UTF_8));
            final BufferedWriter writer = new BufferedWriter (new OutputStreamWriter (process.getOutputStream (), StandardCharsets.UTF_8));
            final String ready;
            try
            {
                final CompletableFuture<String> readyFuture = CompletableFuture.supplyAsync ( () -> {
                    try
                    {
                        return reader.readLine ();
                    }
                    catch (final IOException ex)
                    {
                        throw new CompletionException (ex);
                    }
                });
                ready = readyFuture.get (BRIDGE_READY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            catch (final TimeoutException ex)
            {
                stopStartingBridge (process, reader, writer);
                throw new IOException ("Native bridge did not become ready within " + BRIDGE_READY_TIMEOUT_MS + " ms.", ex);
            }
            catch (final ExecutionException ex)
            {
                stopStartingBridge (process, reader, writer);
                final Throwable cause = ex.getCause ();
                throw cause instanceof IOException ioException ? ioException : new IOException ("Native bridge failed during startup.", cause);
            }
            catch (final InterruptedException ex)
            {
                Thread.currentThread ().interrupt ();
                stopStartingBridge (process, reader, writer);
                throw new IOException ("Interrupted while opening the native bridge.", ex);
            }
            if (ready == null)
            {
                stopStartingBridge (process, reader, writer);
                throw new IOException ("Native bridge exited before opening the MIDI ports.");
            }
            if (ready.startsWith ("ERROR\t"))
            {
                stopStartingBridge (process, reader, writer);
                throw new IOException (ready.substring ("ERROR\t".length ()));
            }

            final String [] fields = ready.split ("\\t", -1);
            if (fields.length != 3 || !"READY".equals (fields[0]))
            {
                stopStartingBridge (process, reader, writer);
                throw new IOException ("Unexpected native bridge response: " + ready);
            }
            return new CoreMidiBridge (process, reader, writer, input, fields[1], fields[2]);
        }


        private static void stopStartingBridge (final Process process, final BufferedReader reader, final BufferedWriter writer)
        {
            try
            {
                writer.close ();
            }
            catch (final IOException ignored)
            {
                // The process will be terminated below.
            }

            process.destroy ();
            try
            {
                if (!process.waitFor (250, TimeUnit.MILLISECONDS))
                {
                    process.destroyForcibly ();
                    process.waitFor (250, TimeUnit.MILLISECONDS);
                }
            }
            catch (final InterruptedException ex)
            {
                Thread.currentThread ().interrupt ();
                process.destroyForcibly ();
            }

            try
            {
                reader.close ();
            }
            catch (final IOException ignored)
            {
                // The process has already been terminated.
            }
        }


        String inputDescription ()
        {
            return this.inputDescription;
        }


        String outputDescription ()
        {
            return this.outputDescription;
        }


        @Override
        public void send (final MidiMessage message, final long timestamp)
        {
            if (this.isClosed)
                throw new IllegalStateException ("Native CoreMIDI bridge is closed.");
            final IOException currentFailure = this.failure;
            if (currentFailure != null)
                throw new IllegalStateException ("Native CoreMIDI bridge failed: " + currentFailure.getMessage (), currentFailure);

            final String bytes = formatMidiBytes (message.getMessage ());
            if (TRACE_MIDI && !(message instanceof SysexMessage))
                System.out.println ("MIDI OUT: " + bytes);
            synchronized (this.writer)
            {
                try
                {
                    this.writer.write ("SEND\t");
                    this.writer.write (bytes);
                    this.writer.newLine ();
                    this.writer.flush ();
                }
                catch (final IOException ex)
                {
                    this.failure = ex;
                    throw new IllegalStateException ("Could not send MIDI through the native CoreMIDI bridge.", ex);
                }
            }
        }


        @Override
        public void close ()
        {
            if (this.isClosed)
                return;
            this.isClosed = true;

            synchronized (this.writer)
            {
                try
                {
                    this.writer.write ("QUIT");
                    this.writer.newLine ();
                    this.writer.flush ();
                    this.writer.close ();
                }
                catch (final IOException ex)
                {
                    if (TRACE_MIDI)
                        System.err.println ("Could not close native CoreMIDI bridge input cleanly: " + ex.getMessage ());
                }
            }

            try
            {
                if (!this.process.waitFor (2, TimeUnit.SECONDS))
                {
                    this.process.destroy ();
                    if (!this.process.waitFor (500, TimeUnit.MILLISECONDS))
                        this.process.destroyForcibly ();
                }
                this.readerThread.join (500);
            }
            catch (final InterruptedException ex)
            {
                Thread.currentThread ().interrupt ();
                this.process.destroyForcibly ();
            }
            try
            {
                this.reader.close ();
            }
            catch (final IOException ex)
            {
                if (TRACE_MIDI)
                    System.err.println ("Could not close native CoreMIDI bridge output cleanly: " + ex.getMessage ());
            }
        }


        private void readMessages ()
        {
            try
            {
                String line;
                while ((line = this.reader.readLine ()) != null)
                {
                    if (line.startsWith ("MIDI\t"))
                    {
                        final byte [] message = parseMidiBytes (line.substring ("MIDI\t".length ()));
                        this.input.acceptNativeMessage (message);
                    }
                    else if (line.startsWith ("ERROR\t"))
                        this.failure = new IOException (line.substring ("ERROR\t".length ()));
                    else if (TRACE_MIDI)
                        System.err.println ("Native CoreMIDI bridge: " + line);
                }
                if (!this.isClosed && this.failure == null)
                    this.failure = new IOException ("Native bridge exited unexpectedly.");
            }
            catch (final IOException | IllegalArgumentException ex)
            {
                if (!this.isClosed)
                    this.failure = ex instanceof IOException ioException ? ioException : new IOException (ex.getMessage (), ex);
            }
        }


        private static byte [] parseMidiBytes (final String text)
        {
            final String trimmed = text.trim ();
            if (trimmed.isEmpty ())
                throw new IllegalArgumentException ("Native bridge returned an empty MIDI message.");
            final String [] tokens = trimmed.split ("\\s+");
            final byte [] data = new byte [tokens.length];
            for (int index = 0; index < tokens.length; index++)
            {
                if (!tokens[index].matches ("[0-9A-Fa-f]{2}"))
                    throw new IllegalArgumentException ("Native bridge returned invalid MIDI data: " + text);
                data[index] = (byte) Integer.parseInt (tokens[index], 16);
            }
            return data;
        }
    }


    private static final class JsonSyntaxValidator
    {
        private final String json;
        private int          position;


        JsonSyntaxValidator (final String json)
        {
            this.json = json;
        }


        void validate ()
        {
            this.skipWhitespace ();
            this.parseValue ();
            this.skipWhitespace ();
            if (this.position != this.json.length ())
                this.fail ("unexpected trailing content");
        }


        private void parseValue ()
        {
            this.skipWhitespace ();
            if (this.position >= this.json.length ())
                this.fail ("expected a value");

            switch (this.json.charAt (this.position))
            {
                case '{':
                    this.parseObject ();
                    break;
                case '[':
                    this.parseArray ();
                    break;
                case '"':
                    this.parseString ();
                    break;
                case 't':
                    this.parseLiteral ("true");
                    break;
                case 'f':
                    this.parseLiteral ("false");
                    break;
                case 'n':
                    this.parseLiteral ("null");
                    break;
                default:
                    this.parseNumber ();
                    break;
            }
        }


        private void parseObject ()
        {
            this.expect ('{');
            this.skipWhitespace ();
            if (this.take ('}'))
                return;

            while (true)
            {
                this.skipWhitespace ();
                this.parseString ();
                this.skipWhitespace ();
                this.expect (':');
                this.parseValue ();
                this.skipWhitespace ();
                if (this.take ('}'))
                    return;
                this.expect (',');
            }
        }


        private void parseArray ()
        {
            this.expect ('[');
            this.skipWhitespace ();
            if (this.take (']'))
                return;

            while (true)
            {
                this.parseValue ();
                this.skipWhitespace ();
                if (this.take (']'))
                    return;
                this.expect (',');
            }
        }


        private void parseString ()
        {
            this.expect ('"');
            while (this.position < this.json.length ())
            {
                final char character = this.json.charAt (this.position++);
                if (character == '"')
                    return;
                if (character < 0x20)
                    this.fail ("unescaped control character in string");
                if (character != '\\')
                    continue;
                if (this.position >= this.json.length ())
                    this.fail ("unterminated string escape");

                final char escape = this.json.charAt (this.position++);
                if ("\"\\/bfnrt".indexOf (escape) >= 0)
                    continue;
                if (escape != 'u')
                    this.fail ("invalid string escape");
                for (int digit = 0; digit < 4; digit++)
                {
                    if (this.position >= this.json.length () || Character.digit (this.json.charAt (this.position++), 16) < 0)
                        this.fail ("invalid Unicode escape");
                }
            }
            this.fail ("unterminated string");
        }


        private void parseNumber ()
        {
            this.take ('-');
            if (this.take ('0'))
            {
                if (this.hasDigit ())
                    this.fail ("leading zero in number");
            }
            else
            {
                if (!this.hasDigit () || this.json.charAt (this.position) == '0')
                    this.fail ("invalid number");
                this.consumeDigits ();
            }

            if (this.take ('.'))
            {
                if (!this.hasDigit ())
                    this.fail ("missing fraction digits");
                this.consumeDigits ();
            }
            if (this.take ('e') || this.take ('E'))
            {
                if (!this.take ('+'))
                    this.take ('-');
                if (!this.hasDigit ())
                    this.fail ("missing exponent digits");
                this.consumeDigits ();
            }
        }


        private void parseLiteral (final String literal)
        {
            if (!this.json.startsWith (literal, this.position))
                this.fail ("invalid value");
            this.position += literal.length ();
        }


        private void consumeDigits ()
        {
            while (this.hasDigit ())
                this.position++;
        }


        private boolean hasDigit ()
        {
            if (this.position >= this.json.length ())
                return false;
            final char character = this.json.charAt (this.position);
            return character >= '0' && character <= '9';
        }


        private void skipWhitespace ()
        {
            while (this.position < this.json.length ())
            {
                final char character = this.json.charAt (this.position);
                if (character != ' ' && character != '\n' && character != '\r' && character != '\t')
                    return;
                this.position++;
            }
        }


        private boolean take (final char expected)
        {
            if (this.position >= this.json.length () || this.json.charAt (this.position) != expected)
                return false;
            this.position++;
            return true;
        }


        private void expect (final char expected)
        {
            if (!this.take (expected))
                this.fail ("expected '" + expected + "'");
        }


        private void fail (final String message)
        {
            throw new IllegalArgumentException ("at character " + this.position + ": " + message);
        }
    }


    private static final class UsageException extends Exception
    {
        private static final long serialVersionUID = 1L;


        UsageException (final String message)
        {
            super (message);
        }
    }


    private static final class PaletteTimeoutException extends IOException
    {
        private static final long serialVersionUID = 1L;


        PaletteTimeoutException (final String message)
        {
            super (message);
        }
    }
}
