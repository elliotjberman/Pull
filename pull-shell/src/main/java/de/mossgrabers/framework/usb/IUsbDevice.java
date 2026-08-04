// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.usb;


/**
 * Interface to an USB device.
 *
 * @author Jürgen Moßgraber
 */
public interface IUsbDevice
{
    /**
     * Create an end-point for the USB device.
     *
     * @param interfaceIndex The index of the registered interface
     * @param endpointIndex The index of the registered end-point
     * @return The end-point
     * @throws UsbException Could not lookup or open the end-point
     */
    IUsbEndpoint getEndpoint (final int interfaceIndex, final int endpointIndex) throws UsbException;


    /**
     * Release the USB device.
     */
    void release ();
}
