// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.usb;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.usb.IUsbDevice;
import de.mossgrabers.framework.usb.IUsbEndpoint;
import de.mossgrabers.framework.usb.UsbException;

import com.bitwig.extension.controller.api.UsbDevice;


/**
 * Implementation for an USB device.
 *
 * @author Jürgen Moßgraber
 */
public class UsbDeviceImpl implements IUsbDevice
{
    private final UsbDevice      usbDevice;
    private final IHost          host;


    /**
     * Constructor.
     *
     * @param host The host for logging
     * @param usbDevice The Bitwig USB device
     */
    public UsbDeviceImpl (final IHost host, final UsbDevice usbDevice)
    {
        this.host = host;
        this.usbDevice = usbDevice;
    }


    /** {@inheritDoc} */
    @Override
    public IUsbEndpoint getEndpoint (final int interfaceIndex, final int endpointIndex) throws UsbException
    {
        try
        {
            return new UsbEndpointImpl (this.host, this.usbDevice.iface (interfaceIndex).pipe (endpointIndex));
        }
        catch (final RuntimeException ex)
        {
            throw new UsbException ("Could not lookup or open the endpoint.", ex);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void release ()
    {
        // This is automatically handled by the Bitwig framework
    }
}
