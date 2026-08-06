// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Optional, standalone Push 2 LCD reference used by the pad color calibration tool.
 *
 * <p>The USB dependency is accessed reflectively so the pad test remains usable when Bitwig's
 * usb4java runtime is unavailable. When active, this object owns display interface 0 until
 * {@link #close()} sends a black frame and releases it.</p>
 */
public final class Push2LcdReference implements AutoCloseable
{
    private static final int     WIDTH              = 960;
    private static final int     HEIGHT             = 160;
    private static final int     COLUMN_COUNT       = 8;
    private static final int     COLUMN_WIDTH       = WIDTH / COLUMN_COUNT;
    private static final int     DATA_SIZE          = 20 * 0x4000;
    private static final int     INTERFACE_NUMBER   = 0;
    private static final byte    ENDPOINT_ADDRESS   = 0x01;
    private static final short   VENDOR_ID          = (short) 0x2982;
    private static final short   PRODUCT_ID         = 0x1967;
    private static final long    TRANSFER_TIMEOUT_MS = 1000;
    // The Ableton display manual says Push repeats the last complete frame but turns black after
    // two seconds without another one. A static reference needs no 60 fps animation; 10 fps leaves
    // a wide timeout margin while using only about 3.3 MB/s of USB payload.
    private static final long    REFRESH_PERIOD_MS  = 100;

    private static final byte [] DISPLAY_HEADER     =
    {
        (byte) 0xFF,
        (byte) 0xCC,
        (byte) 0xAA,
        (byte) 0x88,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0
    };

    private final Object usbLock = new Object ();
    private final ByteBuffer header = directBuffer (DISPLAY_HEADER);
    private Object                   context;
    private Object                   handle;
    private Class<?>                 libUsb;
    private ByteBuffer               referenceFrame;
    private ByteBuffer               blackFrame;
    private ScheduledExecutorService refreshExecutor;
    private boolean                  initialized;
    private boolean                  claimed;
    private volatile boolean         referenceVisible;
    private volatile boolean         closed;
    private boolean                  refreshFailureReported;
    private Thread                   shutdownHook;


    private Push2LcdReference ()
    {
        // Use open.
    }


    /**
     * Try to show eight exact source colors as eight full-height, 120-pixel-wide strips.
     *
     * <p>Failure is deliberately non-fatal: a diagnostic is printed and an inactive, safely
     * closeable instance is returned.</p>
     *
     * @param colors The eight source RGB colors, ordered left to right like the pad columns
     * @return The display lease; close it when calibration ends
     */
    public static Push2LcdReference open (final List<Color> colors)
    {
        Objects.requireNonNull (colors, "colors");
        if (colors.size () != COLUMN_COUNT)
            throw new IllegalArgumentException ("Push 2 LCD reference needs exactly eight colors.");
        for (final Color color: colors)
            Objects.requireNonNull (color, "colors must not contain null");

        final Push2LcdReference reference = new Push2LcdReference ();
        try
        {
            if (isBitwigRunning ())
                throw new IllegalStateException ("Bitwig Studio is running; quit it before claiming the Push 2 display.");

            reference.referenceFrame = encodeFrame (colors);
            reference.blackFrame = encodeFrame (java.util.Collections.nCopies (COLUMN_COUNT, Color.BLACK));
            synchronized (reference.usbLock)
            {
                reference.openUsb ();
                reference.sendEncodedFrame (reference.referenceFrame);
            }
            reference.referenceVisible = true;
            reference.startRefresh ();
            reference.shutdownHook = new Thread (reference::close, "push2-lcd-reference-close");
            Runtime.getRuntime ().addShutdownHook (reference.shutdownHook);
            System.out.println ("Push 2 LCD reference active (8 exact targets through the controller display calibration; refreshing at 10 fps).");
        }
        catch (final Throwable ex)
        {
            reference.close ();
            System.err.println ("Push 2 LCD reference unavailable; continuing with pad calibration: " + describe (ex));
        }
        return reference;
    }


    /**
     * Returns whether the target strip was successfully sent and the display is currently owned.
     *
     * @return True if the reference is active
     */
    public boolean isActive ()
    {
        return this.referenceVisible && !this.closed;
    }


    /**
     * Clear the reference and release the Push 2 display interface.
     */
    @Override
    public synchronized void close ()
    {
        if (this.closed)
            return;

        this.closed = true;
        if (this.shutdownHook != null)
        {
            try
            {
                Runtime.getRuntime ().removeShutdownHook (this.shutdownHook);
            }
            catch (final IllegalStateException ignored)
            {
                // The hook is already executing as part of JVM shutdown.
            }
            this.shutdownHook = null;
        }

        this.stopRefresh ();

        synchronized (this.usbLock)
        {
            if (this.referenceVisible && this.blackFrame != null)
            {
                try
                {
                    this.sendEncodedFrame (this.blackFrame);
                }
                catch (final Throwable ex)
                {
                    System.err.println ("Could not clear the Push 2 LCD reference: " + describe (ex));
                }
            }

            this.referenceVisible = false;
            this.releaseUsb ();
        }

        this.referenceFrame = null;
        this.blackFrame = null;
    }


    private void openUsb () throws ReflectiveOperationException
    {
        this.libUsb = Class.forName ("org.usb4java.LibUsb");
        final Class<?> contextClass = Class.forName ("org.usb4java.Context");
        final Class<?> handleClass = Class.forName ("org.usb4java.DeviceHandle");

        this.context = contextClass.getConstructor ().newInstance ();
        checkStatus ("initialize usb4java", invokeInt ("init", new Class<?> []
        {
            contextClass
        }, this.context));
        this.initialized = true;

        this.handle = method ("openDeviceWithVidPid", contextClass, short.class, short.class).invoke (null, this.context, Short.valueOf (VENDOR_ID), Short.valueOf (PRODUCT_ID));
        if (this.handle == null)
            throw new IllegalStateException (String.format (Locale.ROOT, "Push 2 USB device %04X:%04X was not found.", Integer.valueOf (VENDOR_ID & 0xFFFF), Integer.valueOf (PRODUCT_ID & 0xFFFF)));

        checkStatus ("claim Push 2 display interface", invokeInt ("claimInterface", new Class<?> []
        {
            handleClass,
            int.class
        }, this.handle, Integer.valueOf (INTERFACE_NUMBER)));
        this.claimed = true;
    }


    private void startRefresh ()
    {
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor (task -> {
            final Thread thread = new Thread (task, "push2-lcd-reference-refresh");
            thread.setDaemon (true);
            return thread;
        });
        this.refreshExecutor.scheduleAtFixedRate (this::refresh, REFRESH_PERIOD_MS, REFRESH_PERIOD_MS, TimeUnit.MILLISECONDS);
    }


    private void refresh ()
    {
        try
        {
            synchronized (this.usbLock)
            {
                if (this.closed || !this.referenceVisible || this.referenceFrame == null)
                    return;
                this.sendEncodedFrame (this.referenceFrame);
            }

            if (this.refreshFailureReported)
            {
                this.refreshFailureReported = false;
                System.out.println ("Push 2 LCD reference refresh recovered.");
            }
        }
        catch (final Throwable ex)
        {
            if (!this.closed && !this.refreshFailureReported)
            {
                this.refreshFailureReported = true;
                System.err.println ("Push 2 LCD reference refresh failed; continuing to retry: " + describe (ex));
            }
        }
    }


    private void stopRefresh ()
    {
        final ScheduledExecutorService executor = this.refreshExecutor;
        this.refreshExecutor = null;
        if (executor == null)
            return;

        executor.shutdownNow ();
        try
        {
            if (!executor.awaitTermination (TRANSFER_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS))
                System.err.println ("Push 2 LCD refresh thread did not stop within the USB transfer timeout.");
        }
        catch (final InterruptedException ex)
        {
            Thread.currentThread ().interrupt ();
        }
    }


    private void sendEncodedFrame (final ByteBuffer frame) throws ReflectiveOperationException
    {
        if (!this.claimed || this.handle == null)
            throw new IllegalStateException ("Push 2 display interface is not claimed.");

        this.bulkTransfer (duplicateForTransfer (this.header), "display header");
        this.bulkTransfer (duplicateForTransfer (frame), "display pixels");
    }


    private void bulkTransfer (final ByteBuffer data, final String description) throws ReflectiveOperationException
    {
        final Class<?> handleClass = Class.forName ("org.usb4java.DeviceHandle");
        final IntBuffer transferred = ByteBuffer.allocateDirect (Integer.BYTES).order (ByteOrder.nativeOrder ()).asIntBuffer ();
        final int expected = data.remaining ();
        final int status = invokeInt ("bulkTransfer", new Class<?> []
        {
            handleClass,
            byte.class,
            ByteBuffer.class,
            IntBuffer.class,
            long.class
        }, this.handle, Byte.valueOf (ENDPOINT_ADDRESS), data, transferred, Long.valueOf (TRANSFER_TIMEOUT_MS));
        checkStatus ("send Push 2 " + description, status);

        final int actual = transferred.get (0);
        if (actual != expected)
            throw new IllegalStateException ("Short Push 2 " + description + " transfer: " + actual + " of " + expected + " bytes.");
    }


    private void releaseUsb ()
    {
        if (this.libUsb == null)
            return;

        if (this.claimed && this.handle != null)
        {
            try
            {
                final Class<?> handleClass = Class.forName ("org.usb4java.DeviceHandle");
                final int status = invokeInt ("releaseInterface", new Class<?> []
                {
                    handleClass,
                    int.class
                }, this.handle, Integer.valueOf (INTERFACE_NUMBER));
                if (status != 0)
                    System.err.println ("Could not release the Push 2 display interface: " + this.errorName (status));
            }
            catch (final ReflectiveOperationException ex)
            {
                System.err.println ("Could not release the Push 2 display interface: " + describe (ex));
            }
        }
        this.claimed = false;

        if (this.handle != null)
        {
            try
            {
                method ("close", Class.forName ("org.usb4java.DeviceHandle")).invoke (null, this.handle);
            }
            catch (final ReflectiveOperationException ex)
            {
                System.err.println ("Could not close the Push 2 USB handle: " + describe (ex));
            }
            this.handle = null;
        }

        if (this.initialized && this.context != null)
        {
            try
            {
                method ("exit", Class.forName ("org.usb4java.Context")).invoke (null, this.context);
            }
            catch (final ReflectiveOperationException ex)
            {
                System.err.println ("Could not shut down usb4java: " + describe (ex));
            }
        }
        this.initialized = false;
        this.context = null;
        this.libUsb = null;
    }


    private int invokeInt (final String name, final Class<?> [] parameterTypes, final Object... arguments) throws ReflectiveOperationException
    {
        return ((Integer) method (name, parameterTypes).invoke (null, arguments)).intValue ();
    }


    private Method method (final String name, final Class<?>... parameterTypes) throws NoSuchMethodException
    {
        return this.libUsb.getMethod (name, parameterTypes);
    }


    private void checkStatus (final String action, final int status) throws ReflectiveOperationException
    {
        if (status != 0)
            throw new IllegalStateException ("Could not " + action + ": " + this.errorName (status));
    }


    private String errorName (final int status) throws ReflectiveOperationException
    {
        return String.valueOf (method ("errorName", int.class).invoke (null, Integer.valueOf (status))) + " (" + status + ')';
    }


    private static ByteBuffer encodeFrame (final List<Color> colors)
    {
        final ByteBuffer data = ByteBuffer.allocateDirect (DATA_SIZE);
        final int padding = (DATA_SIZE - WIDTH * HEIGHT * 2) / HEIGHT;

        for (int y = 0; y < HEIGHT; y++)
        {
            for (int x = 0; x < WIDTH; x++)
            {
                final Color source = colors.get (x / COLUMN_WIDTH);
                final int calibrated = PushColorCalibration.toDisplayRGB (source.getRed (), source.getGreen (), source.getBlue ());
                final int pixel = pixelFromRGB (calibrated >> 16 & 0xFF, calibrated >> 8 & 0xFF, calibrated & 0xFF);
                data.put ((byte) (pixel & 0xFF));
                data.put ((byte) (pixel >> 8 & 0xFF));
            }
            for (int i = 0; i < padding; i++)
                data.put ((byte) 0);
        }

        for (int position = 0; position < DATA_SIZE; position += 4)
        {
            data.put (position, (byte) (data.get (position) ^ 0xE7));
            data.put (position + 1, (byte) (data.get (position + 1) ^ 0xF3));
            data.put (position + 2, (byte) (data.get (position + 2) ^ 0xE7));
            data.put (position + 3, (byte) (data.get (position + 3) ^ 0xFF));
        }
        data.flip ();
        return data;
    }


    private static ByteBuffer directBuffer (final byte [] bytes)
    {
        final ByteBuffer buffer = ByteBuffer.allocateDirect (bytes.length);
        buffer.put (bytes);
        buffer.flip ();
        return buffer;
    }


    private static ByteBuffer duplicateForTransfer (final ByteBuffer source)
    {
        final ByteBuffer duplicate = source.duplicate ();
        duplicate.clear ();
        return duplicate;
    }


    private static int pixelFromRGB (final int red, final int green, final int blue)
    {
        int pixel = (blue & 0xF8) >> 3;
        pixel <<= 6;
        pixel += (green & 0xFC) >> 2;
        pixel <<= 5;
        pixel += (red & 0xF8) >> 3;
        return pixel;
    }


    private static boolean isBitwigRunning ()
    {
        return ProcessHandle.allProcesses ().anyMatch (process -> process.info ().command ().map (command -> {
            final String normalized = command.toLowerCase (Locale.ROOT);
            return normalized.contains ("/bitwig studio.app/") || normalized.endsWith ("/bitwigstudio");
        }).orElse (false));
    }


    private static String describe (final Throwable throwable)
    {
        Throwable cause = throwable;
        while (cause instanceof final InvocationTargetException invocation && invocation.getCause () != null)
            cause = invocation.getCause ();

        final String message = cause.getMessage ();
        return message == null || message.isBlank () ? cause.getClass ().getSimpleName () : message;
    }
}
