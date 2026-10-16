// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.nio.ByteBuffer;

/** Frozen CPU path from PushUsbDisplay at fd562c842a2079a0cf72026f91dd5c5b48f1714e. */
final class LegacyFrameEncoder
{
    static void encode(ByteBuffer imageBuffer, byte[] bytes)
    {
        int counter = 0;
        final int padding = (bytes.length - 160 * 960 * 2) / 160;
        for (int y = 0; y < 160; y++)
        {
            for (int x = 0; x < 960; x++)
            {
                final int blue = imageBuffer.get();
                final int green = imageBuffer.get();
                final int red = imageBuffer.get();
                imageBuffer.get();
                final int calibrated = PushColorCalibration.toDisplayRGB(red, green, blue);
                final int pixel = pixel(calibrated >> 16 & 0xFF, calibrated >> 8 & 0xFF, calibrated & 0xFF);
                bytes[counter] = (byte) (pixel & 0xFF);
                bytes[counter + 1] = (byte) ((pixel & 0xFF00) >> 8);
                counter += 2;
            }
            for (int x = 0; x < padding; x++) bytes[counter++] = 0;
        }
        for (int pos = 0; pos < bytes.length; pos += 4)
        {
            bytes[pos] ^= 0xE7;
            bytes[pos + 1] ^= 0xF3;
            bytes[pos + 2] ^= 0xE7;
            bytes[pos + 3] ^= 0xFF;
        }
        imageBuffer.rewind();
    }

    static void copy(byte[] bytes, ByteBuffer destination)
    {
        destination.clear();
        for (int i = 0; i < bytes.length; i++) destination.put(bytes[i]);
    }

    private static int pixel(int red, int green, int blue)
    {
        int pixel = (blue & 0xF8) >> 3;
        pixel <<= 6;
        pixel += (green & 0xFC) >> 2;
        pixel <<= 5;
        pixel += (red & 0xF8) >> 3;
        return pixel;
    }
}
