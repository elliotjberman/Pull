// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IMemoryBlock;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.IEncoder;
import de.mossgrabers.framework.graphics.IRenderer;
import de.mossgrabers.framework.usb.IUsbCallback;
import de.mossgrabers.framework.usb.IUsbDevice;
import de.mossgrabers.framework.usb.IUsbEndpoint;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Wire-level compatibility, including calibration, padding, cached rows and actual USB submission. */
class PushFrameEncoderTest
{
    private static final int PIXELS = 960 * 160;
    private static final int WIRE_BYTES = 327680;

    @Test
    void allRgbColorsMatchTheScalarWireContract ()
    {
        final PushFrameEncoder encoder = new PushFrameEncoder ();
        final ByteBuffer input = ByteBuffer.allocateDirect (PIXELS * 4).order (ByteOrder.LITTLE_ENDIAN);
        for (int start = 0; start < 1 << 24; start += PIXELS)
        {
            input.clear ();
            for (int i = 0; i < PIXELS; i++)
                input.putInt ((start + i & 0xFFFFFF) | (i & 255) << 24);
            input.flip ();
            final byte [] expected = referenceWire (input);
            encoder.encode (input);
            assertArrayEquals (expected, output (encoder), "RGB block " + start);
        }
    }

    @Test
    void cachedRowsFollowChangesAtEveryFrameBoundaryAndPreservePadding ()
    {
        final PushFrameEncoder encoder = new PushFrameEncoder ();
        final ByteBuffer input = ByteBuffer.allocate (PIXELS * 4).order (ByteOrder.LITTLE_ENDIAN);
        final Random random = new Random (42);
        random.nextBytes (input.array ());
        for (final int index : new int [] {0, 59, 60, 119, 120, 959, 960, PIXELS - 1, 0})
        {
            for (int repeat = 0; repeat < 2; repeat++)
            {
                final byte [] expected = referenceWire (input);
                encoder.encode (input);
                assertArrayEquals (expected, output (encoder));
            }
            input.putInt (index * 4, input.getInt (index * 4) ^ 0x00FFFFFF);
        }
        encoder.encode (input);
        assertArrayEquals (referenceWire (input), output (encoder));
    }

    @Test
    void nativeAndHeapReadOnlyViewsPreserveByteOrderAndInputRewind ()
    {
        for (final boolean direct : new boolean [] {false, true})
        {
            for (final ByteOrder order : new ByteOrder [] {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN})
            {
                final ByteBuffer storage = direct ? ByteBuffer.allocateDirect (PIXELS * 4 + 12) : ByteBuffer.allocate (PIXELS * 4 + 12);
                storage.position (8);
                for (int i = 0; i < PIXELS; i++)
                    storage.put ((byte) i).put ((byte) (i >> 8)).put ((byte) (i >> 16)).put ((byte) 255);
                final ByteBuffer input = storage.asReadOnlyBuffer ().order (order);
                input.position (8);
                final byte [] expected = referenceWire (input);
                final PushFrameEncoder encoder = new PushFrameEncoder ();
                encoder.encode (input);
                assertEquals (0, input.position ());
                assertEquals (order, input.order ());
                final ByteBuffer destination = ByteBuffer.allocate (WIRE_BYTES + 8).order (order);
                encoder.copyTo (destination);
                assertEquals (WIRE_BYTES, destination.position ());
                assertEquals (order, destination.order ());
                destination.flip ();
                final byte [] actual = new byte [WIRE_BYTES];
                destination.get (actual);
                assertArrayEquals (expected, actual);
            }
        }
    }

    @Test
    void localizedEditsCrossCellRowAndDisplayEdgesWithoutChangingNeighbors ()
    {
        final PushFrameEncoder encoder = new PushFrameEncoder ();
        final ByteBuffer input = ByteBuffer.allocate (PIXELS * 4).order (ByteOrder.LITTLE_ENDIAN);
        new Random (84).nextBytes (input.array ());
        encoder.encode (input);
        for (final int [] rectangle : new int [][] {{59, 0, 2, 2}, {119, 79, 5, 9}, {959, 159, 1, 1}, {360, 40, 120, 80}})
        {
            for (int y = rectangle[1]; y < rectangle[1] + rectangle[3]; y++)
                for (int x = rectangle[0]; x < rectangle[0] + rectangle[2]; x++)
                    input.putInt ((y * 960 + x) * 4, (x << 16) ^ (y << 8) ^ 0xA5030201);
            encoder.encode (input);
            assertArrayEquals (referenceWire (input), output (encoder));
            encoder.encode (input); // Whole-frame reuse must preserve the complete wire image.
            assertArrayEquals (referenceWire (input), output (encoder));
        }
    }

    @Test
    void grayscaleFastPathHandlesBothSidesOfTheChromaThreshold ()
    {
        final PushFrameEncoder encoder = new PushFrameEncoder ();
        final ByteBuffer input = ByteBuffer.allocate (PIXELS * 4);
        for (final int difference : new int [] {0, 7, 8, 255, 7, 0})
        {
            input.clear ();
            for (int i = 0; i < PIXELS; i++)
            {
                final int value = i % (256 - difference);
                input.put ((byte) value).put ((byte) (value + difference)).put ((byte) value).put ((byte) i);
            }
            input.flip ();
            encoder.encode (input);
            assertArrayEquals (referenceWire (input), output (encoder));
        }
    }

    @Test
    void displaySubmitsExactHeaderAndFrameThroughItsRealWorker () throws Exception
    {
        final LinkedBlockingQueue<byte []> transmitted = new LinkedBlockingQueue<> ();
        final IUsbEndpoint endpoint = new IUsbEndpoint ()
        {
            @Override
            public void send (final IMemoryBlock block, final int timeout)
            {
                final ByteBuffer buffer = block.createByteBuffer ();
                final byte [] bytes = new byte [buffer.remaining ()];
                buffer.get (bytes);
                transmitted.add (bytes);
            }

            @Override
            public void sendAsync (final IMemoryBlock block, final IUsbCallback callback, final int timeout)
            {
                fail ("Display output uses the synchronous endpoint on its own worker");
            }
        };
        final IUsbDevice device = new IUsbDevice ()
        {
            @Override
            public IUsbEndpoint getEndpoint (final int interfaceIndex, final int endpointIndex) { return endpoint; }

            @Override
            public void release () { }
        };
        final IHost host = (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (), new Class<?> [] {IHost.class}, (proxy, method, args) -> {
            if ("getUsbDevice".equals (method.getName ())) return device;
            if ("createMemoryBlock".equals (method.getName ()))
            {
                final ByteBuffer storage = ByteBuffer.allocateDirect ((int) args[0]);
                return (IMemoryBlock) storage::duplicate;
            }
            if ("error".equals (method.getName ())) throw new AssertionError (args[0]);
            return null;
        });
        final ByteBuffer pixels = ByteBuffer.allocateDirect (PIXELS * 4).order (ByteOrder.LITTLE_ENDIAN);
        final IBitmap bitmap = new IBitmap ()
        {
            @Override
            public void render (final boolean antialias, final IRenderer renderer) { fail ("No rendering expected"); }

            @Override
            public void encode (final IEncoder encoder) { encoder.encode (pixels.duplicate (), 960, 160); }
        };
        final PushUsbDisplay display = new PushUsbDisplay (host);
        try
        {
            for (final int color : new int [] {0xFF800000, 0xFF0000FF, 0xFF0000FF})
            {
                pixels.putInt (0, color);
                display.send (bitmap);
                assertArrayEquals (new byte [] {(byte) 255, (byte) 204, (byte) 170, (byte) 136, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, transmitted.poll (2, TimeUnit.SECONDS));
                assertArrayEquals (referenceWire (pixels), transmitted.poll (2, TimeUnit.SECONDS));
            }
        }
        finally { display.shutdown (); }
    }

    private static byte [] output (final PushFrameEncoder encoder)
    {
        final ByteBuffer destination = ByteBuffer.allocate (WIRE_BYTES);
        encoder.copyTo (destination);
        return destination.array ();
    }

    /** Deliberately scalar specification of the pre-existing calibrated BGR565 wire format. */
    private static byte [] referenceWire (final ByteBuffer original)
    {
        final ByteBuffer input = original.duplicate ();
        final byte [] bytes = new byte [WIRE_BYTES];
        for (int y = 0; y < 160; y++)
        {
            for (int x = 0; x < 960; x++)
            {
                final int blue = input.get ();
                final int green = input.get ();
                final int red = input.get ();
                input.get ();
                final int calibrated = PushColorCalibration.toDisplayRGB (red, green, blue);
                final int pixel = (calibrated >>> 19 & 31) | (calibrated >>> 5 & 0x7E0) | (calibrated << 8 & 0xF800);
                final int position = (y * 1024 + x) * 2;
                bytes[position] = (byte) pixel;
                bytes[position + 1] = (byte) (pixel >> 8);
            }
        }
        final int [] pattern = {0xE7, 0xF3, 0xE7, 0xFF};
        for (int i = 0; i < bytes.length; i++) bytes[i] ^= (byte) pattern[i & 3];
        return bytes;
    }
}
