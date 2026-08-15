// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.framework.controller.display.AbstractGraphicDisplay;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.graphics.DefaultGraphicsDimensions;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.canvas.component.DisplaySceneComponent;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


/**
 * The display of Push 2.
 *
 * @author Jürgen Moßgraber
 */
public class Push2Display extends AbstractGraphicDisplay
{
    private final PushUsbDisplay usbDisplay;
    private final PushDebugCaptureHost debugCapture;
    private boolean              isShutdown = false;


    /**
     * Constructor. 4 rows (0-3) with 4 blocks (0-3). Each block consists of 17 characters or 2
     * cells (0-7).
     *
     * @param host The host
     * @param maxParameterValue The maximum parameter value (upper bound)
     * @param configuration The Push configuration
     * @param displayOverlaySupplier Reloadable complete display-overlay state
     */
    public Push2Display (final IHost host, final int maxParameterValue, final PushConfiguration configuration, final Supplier<ControllerDisplayOverlay> displayOverlaySupplier)
    {
        super (host, configuration, new DefaultGraphicsDimensions (960, 160, maxParameterValue));

        final Supplier<ControllerDisplayOverlay> checkedSupplier = Objects.requireNonNull (displayOverlaySupplier, "displayOverlaySupplier");
        this.setFullScreenOverlaySupplier ( () -> {
            final ControllerDisplayOverlay overlay = Objects.requireNonNull (checkedSupplier.get (), "display overlay");
            return overlay.active () ? new DisplaySceneComponent (overlay.scene ()) : null;
        });
        this.usbDisplay = new PushUsbDisplay (host);
        this.debugCapture = PushDebugCaptureHost.createIfEnabled ();
    }


    /** {@inheritDoc} */
    @Override
    public void notify (final String message)
    {
        if (message == null)
            return;
        this.host.showNotification (message);
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.setMessage (3, "Please start " + this.host.getName () + " to play...");
        this.send ();

        this.isShutdown = true;
        if (this.debugCapture != null)
            this.debugCapture.close ();

        final ExecutorService executor = Executors.newSingleThreadExecutor ();
        executor.execute ( () -> {

            if (this.usbDisplay != null)
                this.usbDisplay.shutdown ();
            super.shutdown ();

        });
        executor.shutdown ();
        try
        {
            executor.awaitTermination (10, TimeUnit.SECONDS);
        }
        catch (final InterruptedException ex)
        {
            this.host.error ("Display shutdown interrupted.", ex);
            Thread.currentThread ().interrupt ();
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void send (final IBitmap image)
    {
        if (this.debugCapture != null)
            this.debugCapture.observeFrame (image);
        if (!this.isShutdown && this.usbDisplay != null)
            this.usbDisplay.send (image);
    }
}
