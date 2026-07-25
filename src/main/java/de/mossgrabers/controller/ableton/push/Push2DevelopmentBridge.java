// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;


/**
 * Optional filesystem bridge used by the local Push 2 development tools.
 */
final class Push2DevelopmentBridge
{
    private static final Path             DEVELOPMENT_DIRECTORY = Path.of (System.getProperty ("java.io.tmpdir"), "pull-push2-dev");
    private static final Path             ENABLE_MARKER_PATH    = DEVELOPMENT_DIRECTORY.resolve ("enabled");
    private static final Path             CAPTURE_MARKER_PATH   = DEVELOPMENT_DIRECTORY.resolve ("capture");
    private static final Path             READY_MARKER_PATH     = DEVELOPMENT_DIRECTORY.resolve ("ready");
    private static final Path             BRIGHTNESS_PATH       = DEVELOPMENT_DIRECTORY.resolve ("brightness");
    private static final Path             BRIGHTNESS_APPLIED    = DEVELOPMENT_DIRECTORY.resolve ("brightness-applied");
    private static final Path             MODE_APPLIED_PATH     = DEVELOPMENT_DIRECTORY.resolve ("mode-applied");
    private static final long             POLL_DELAY            = 500;
    private static final Map<Modes, Path> MODE_MARKER_PATHS     = Map.of (
        Modes.TRACK, DEVELOPMENT_DIRECTORY.resolve ("mode-track"),
        Modes.VOLUME, DEVELOPMENT_DIRECTORY.resolve ("mode-volume"),
        Modes.PAN, DEVELOPMENT_DIRECTORY.resolve ("mode-pan"),
        Modes.USER, DEVELOPMENT_DIRECTORY.resolve ("mode-user"),
        Modes.DEVICE_PARAMS, DEVELOPMENT_DIRECTORY.resolve ("mode-device-params"));

    private final IHost                   host;
    private final IModel                  model;
    private final PushConfiguration       configuration;
    private final PushControlSurface      surface;
    private final Map<Modes, Long>        modeMarkerTimestamps = new EnumMap<> (Modes.class);

    private long                          captureMarkerTimestamp;
    private boolean                       isRunning;


    Push2DevelopmentBridge (final IHost host, final IModel model, final PushConfiguration configuration, final PushControlSurface surface)
    {
        this.host = host;
        this.model = model;
        this.configuration = configuration;
        this.surface = surface;
    }


    void start ()
    {
        if (!Files.exists (ENABLE_MARKER_PATH))
            return;

        this.captureMarkerTimestamp = getMarkerTimestamp (CAPTURE_MARKER_PATH);
        for (final Map.Entry<Modes, Path> entry: MODE_MARKER_PATHS.entrySet ())
            this.modeMarkerTimestamps.put (entry.getKey (), Long.valueOf (getMarkerTimestamp (entry.getValue ())));

        this.isRunning = true;
        this.writeMarker (READY_MARKER_PATH);
        this.host.scheduleTask (this::checkMarkers, POLL_DELAY);
    }


    void stop ()
    {
        this.isRunning = false;
    }


    private void checkMarkers ()
    {
        if (!this.isRunning)
            return;

        this.checkCaptureMarker ();
        this.checkModeMarkers ();
        this.host.scheduleTask (this::checkMarkers, POLL_DELAY);
    }


    private void checkCaptureMarker ()
    {
        final long captureTimestamp = getMarkerTimestamp (CAPTURE_MARKER_PATH);
        if (captureTimestamp < 0 || captureTimestamp == this.captureMarkerTimestamp)
            return;

        this.captureMarkerTimestamp = captureTimestamp;
        this.applyRequestedBrightness ();
        this.surface.getGraphicsDisplay ().saveDebugImage ();
    }


    private void applyRequestedBrightness ()
    {
        if (!Files.exists (BRIGHTNESS_PATH))
            return;

        try
        {
            final String [] values = Files.readString (BRIGHTNESS_PATH).trim ().split ("\\s+");
            this.configuration.setDisplayBrightness (Integer.parseInt (values[0]));
            this.configuration.setLEDBrightness (Integer.parseInt (values[1]));
            Files.deleteIfExists (BRIGHTNESS_PATH);
            this.writeMarker (BRIGHTNESS_APPLIED);
        }
        catch (final IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException ex)
        {
            this.host.error ("Could not apply Push 2 development brightness.", ex);
        }
    }


    private void checkModeMarkers ()
    {
        for (final Map.Entry<Modes, Path> entry: MODE_MARKER_PATHS.entrySet ())
        {
            final long timestamp = getMarkerTimestamp (entry.getValue ());
            if (timestamp < 0 || timestamp == this.modeMarkerTimestamps.get (entry.getKey ()).longValue ())
                continue;

            this.modeMarkerTimestamps.put (entry.getKey (), Long.valueOf (timestamp));
            this.applyRequestedMode (entry.getKey ());
        }
    }


    private void applyRequestedMode (final Modes mode)
    {
        try
        {
            final ModeManager modeManager = this.surface.getModeManager ();
            if (modeManager.get (mode) == null)
            {
                this.host.error ("Push 2 development mode is not registered: " + mode);
                return;
            }

            modeManager.setActive (mode);
            if (mode == Modes.TRACK)
            {
                final ITrackBank trackBank = this.model.getCurrentTrackBank ();
                if (trackBank.getSelectedItem ().isEmpty () && trackBank.getItem (0).doesExist ())
                    trackBank.getItem (0).select ();
            }

            this.writeMarker (MODE_APPLIED_PATH);
            this.host.scheduleTask ( () -> this.surface.getGraphicsDisplay ().saveDebugImage (), 250);
        }
        catch (final RuntimeException ex)
        {
            this.host.error ("Could not apply Push 2 development mode.", ex);
        }
    }


    private static long getMarkerTimestamp (final Path path)
    {
        try
        {
            return Files.exists (path) ? Files.getLastModifiedTime (path).toMillis () : -1;
        }
        catch (final IOException ex)
        {
            return -1;
        }
    }


    private void writeMarker (final Path path)
    {
        try
        {
            Files.createDirectories (DEVELOPMENT_DIRECTORY);
            Files.writeString (path, System.currentTimeMillis () + " " + System.nanoTime () + System.lineSeparator ());
        }
        catch (final IOException ex)
        {
            this.host.error ("Could not write Push 2 development marker: " + path, ex);
        }
    }
}
