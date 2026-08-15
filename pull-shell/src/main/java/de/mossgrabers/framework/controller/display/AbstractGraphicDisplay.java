// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.hardware.IHwGraphicsDisplay;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.daw.resource.ResourceHandler;
import de.mossgrabers.framework.graphics.Align;
import de.mossgrabers.framework.graphics.DefaultGraphicsInfo;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.IGraphicsConfiguration;
import de.mossgrabers.framework.graphics.IGraphicsDimensions;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.framework.graphics.canvas.component.GraphOverlayComponent;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;
import de.mossgrabers.framework.graphics.canvas.component.LabelComponent.LabelLayout;
import de.mossgrabers.framework.graphics.canvas.component.ListComponent;
import de.mossgrabers.framework.graphics.canvas.component.MidiClipComponent;
import de.mossgrabers.framework.graphics.canvas.component.OptionsComponent;
import de.mossgrabers.framework.graphics.canvas.component.ParameterComponent;
import de.mossgrabers.framework.graphics.display.ModelInfo;
import de.mossgrabers.framework.utils.Pair;


/**
 * A display which uses graphics rather than fixed characters.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractGraphicDisplay implements IGraphicDisplay
{
    /** Timeout for displaying the notification message. */
    private static final int               TIMEOUT                         = 1;
    private final AtomicInteger            counter                         = new AtomicInteger ();
    private final ScheduledExecutorService executor                        = Executors.newSingleThreadScheduledExecutor ();
    private final Object                   counterSync                     = new Object ();

    private final List<IComponent>         columns                         = new ArrayList<> (8);
    private final List<IComponent>         overlays                        = new ArrayList<> ();
    private final AtomicReference<String>  notificationMessage             = new AtomicReference<> ();
    private ModelInfo                      info                            = new ModelInfo (null, Collections.emptyList (), Collections.emptyList ());
    private Supplier<IComponent>           fullScreenOverlaySupplier       = () -> null;

    protected final IHost                  host;
    protected final IGraphicsConfiguration configuration;
    protected final IGraphicsDimensions    dimensions;
    private final IBitmap                  image;

    private IHwGraphicsDisplay             hardwareDisplay;


    /**
     * Constructor.
     *
     * @param host The host
     * @param configuration The configuration
     * @param dimensions The pre-calculated dimensions
     */
    protected AbstractGraphicDisplay (final IHost host, final IGraphicsConfiguration configuration, final IGraphicsDimensions dimensions)
    {
        this.host = host;
        this.configuration = configuration;
        this.dimensions = dimensions;

        ResourceHandler.init (host);

        this.image = host.createBitmap (dimensions.getWidth (), dimensions.getHeight ());
        // Manage notification message display time
        this.executor.scheduleAtFixedRate (this::runHousekeeping, 1, 1, TimeUnit.SECONDS);
    }


    /** {@inheritDoc} */
    @Override
    public void cancelNotification ()
    {
        synchronized (this.counterSync)
        {
            this.counter.set (0);
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean isNotificationActive ()
    {
        synchronized (this.counterSync)
        {
            return this.counter.get () > 0;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.executor.shutdown ();
        try
        {
            if (!this.executor.awaitTermination (5, TimeUnit.SECONDS))
                this.host.error ("Display send executor did not end in 5 seconds.");
        }
        catch (final InterruptedException ex)
        {
            this.host.error ("Display send executor interrupted.", ex);
            Thread.currentThread ().interrupt ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void send ()
    {
        if (this.executor.isShutdown ())
            return;

        try
        {
            final String notification;
            synchronized (this.counterSync)
            {
                notification = this.notificationMessage.get ();
            }

            final IComponent fullScreenOverlay = this.fullScreenOverlaySupplier.get ();
            final List<IComponent> renderedOverlays;
            if (fullScreenOverlay == null)
                renderedOverlays = this.overlays;
            else
            {
                renderedOverlays = new ArrayList<> (this.overlays);
                renderedOverlays.add (fullScreenOverlay);
            }
            final ModelInfo newInfo = new ModelInfo (fullScreenOverlay == null ? notification : null, this.columns, renderedOverlays);

            // Only render image if there is a change in the data
            if (!this.info.equals (newInfo))
            {
                this.info = newInfo;
                this.renderImage ();
            }
        }
        finally
        {
            this.columns.clear ();
            this.overlays.clear ();
        }

        this.send (this.image);
    }


    private void runHousekeeping ()
    {
        this.checkNotificationCounter ();
    }


    /** Install a generic final overlay rendered over the complete display viewport. */
    protected void setFullScreenOverlaySupplier (final Supplier<IComponent> supplier)
    {
        this.fullScreenOverlaySupplier = java.util.Objects.requireNonNull (supplier, "supplier");
    }


    /**
     * Send the buffered image to the graphics display.
     *
     * @param image An image
     */
    protected abstract void send (final IBitmap image);


    /** {@inheritDoc} */
    @Override
    public void setNotificationMessage (final String message)
    {
        synchronized (this.counterSync)
        {
            this.counter.set (TIMEOUT);
            this.notificationMessage.set (message);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void setMidiClipElement (final INoteClip clip, final int quartersPerMeasure, final NotePosition activePosition)
    {
        this.addElement (new MidiClipComponent (clip, quartersPerMeasure, activePosition));
    }


    /** {@inheritDoc} */
    @Override
    public IGraphicDisplay setMessage (final int column, final String text)
    {
        for (int i = 0; i < 8; i++)
            this.addOptionElement (column == i ? text : "", "", false, "", "", false, false);
        return this;
    }


    /** {@inheritDoc} */
    @Override
    public void addEmptyElement ()
    {
        this.addOptionElement ("", "", false, "", "", false, false);
    }


    /** {@inheritDoc} */
    @Override
    public void addEmptyElement (final boolean hasSmallEmptyMenu)
    {
        this.addOptionElement ("", " ", false, "", "", false, true);
    }


    /** {@inheritDoc} */
    @Override
    public void addParameterElement (final String parameterName, final int parameterValue, final String parameterValueStr, final boolean parameterIsActive, final int parameterModulatedValue)
    {
        this.addParameterElement ("", false, "", (ChannelType) null, ColorEx.BLACK, false, parameterName, parameterValue, parameterValueStr, parameterIsActive, parameterModulatedValue);
    }


    /** {@inheritDoc} */
    @Override
    public void addParameterElement (final String topMenu, final boolean isTopMenuOn, final String bottomMenu, final ChannelType type, final ColorEx bottomMenuColor, final boolean isBottomMenuOn, final String parameterName, final int parameterValue, final String parameterValueStr, final boolean parameterIsActive, final int parameterModulatedValue)
    {
        this.addElement (new ParameterComponent (topMenu, isTopMenuOn, bottomMenu, type, bottomMenuColor, isBottomMenuOn, parameterName, parameterValue, parameterModulatedValue, parameterValueStr, parameterIsActive));
    }


    /** {@inheritDoc} */
    @Override
    public void addParameterElementWithPlainMenu (final String topMenu, final boolean isTopMenuOn, final String bottomMenu, final ColorEx bottomMenuColor, final boolean isBottomMenuOn, final String parameterName, final int parameterValue, final String parameterValueStr, final boolean parameterIsActive, final int parameterModulatedValue)
    {
        this.addElement (new ParameterComponent (topMenu, isTopMenuOn, bottomMenu, null, bottomMenuColor, isBottomMenuOn, parameterName, parameterValue, parameterModulatedValue, parameterValueStr, parameterIsActive, LabelLayout.PLAIN));
    }


    /** {@inheritDoc} */
    @Override
    public void addParameterElement (final String topMenu, final boolean isTopMenuOn, final String bottomMenu, final String deviceName, final ColorEx bottomMenuColor, final boolean isBottomMenuOn, final String parameterName, final int parameterValue, final String parameterValueStr, final boolean parameterIsActive, final int parameterModulatedValue)
    {
        this.addElement (new ParameterComponent (topMenu, isTopMenuOn, bottomMenu, deviceName, bottomMenuColor, isBottomMenuOn, parameterName, parameterValue, parameterModulatedValue, parameterValueStr, parameterIsActive));
    }


    /** {@inheritDoc} */
    @Override
    public void addOptionElement (final String headerTopName, final String menuTopName, final boolean isMenuTopSelected, final String headerBottomName, final String menuBottomName, final boolean isMenuBottomSelected, final boolean useSmallTopMenu)
    {
        this.addOptionElement (headerTopName, menuTopName, isMenuTopSelected, null, headerBottomName, menuBottomName, isMenuBottomSelected, null, useSmallTopMenu);
    }


    /** {@inheritDoc} */
    @Override
    public void addOptionElement (final String headerTopName, final String menuTopName, final boolean isMenuTopSelected, final ColorEx menuTopColor, final String headerBottomName, final String menuBottomName, final boolean isMenuBottomSelected, final ColorEx menuBottomColor, final boolean useSmallTopMenu)
    {
        this.addOptionElement (headerTopName, menuTopName, isMenuTopSelected, menuTopColor, headerBottomName, menuBottomName, isMenuBottomSelected, menuBottomColor, useSmallTopMenu, false);
    }


    /** {@inheritDoc} */
    @Override
    public void addOptionElement (final String headerTopName, final String menuTopName, final boolean isMenuTopSelected, final ColorEx menuTopColor, final String headerBottomName, final String menuBottomName, final boolean isMenuBottomSelected, final ColorEx menuBottomColor, final boolean useSmallTopMenu, final boolean isBottomHeaderSelected)
    {
        this.addElement (new OptionsComponent (headerTopName, menuTopName, isMenuTopSelected, menuTopColor, headerBottomName, menuBottomName, isMenuBottomSelected, menuBottomColor, useSmallTopMenu, isBottomHeaderSelected));
    }


    /** {@inheritDoc} */
    @Override
    public void addListElement (final int displaySize, final String [] elements, final int selectedIndex)
    {
        final List<Pair<String, Boolean>> menu = new ArrayList<> ();
        final int startIndex = Math.max (0, Math.min (selectedIndex, elements.length - displaySize));
        for (int i = 0; i < displaySize; i++)
        {
            final int pos = startIndex + i;
            final String itemName = pos < elements.length ? elements[pos] : "";
            menu.add (new Pair<> (itemName, Boolean.valueOf (pos == selectedIndex)));
        }
        this.addElement (new ListComponent (menu));
    }


    /** {@inheritDoc} */
    @Override
    public void addListElement (final String [] items, final boolean [] selected)
    {
        final List<Pair<String, Boolean>> menu = new ArrayList<> ();
        for (int i = 0; i < items.length; i++)
            menu.add (new Pair<> (items[i], Boolean.valueOf (selected[i])));
        this.addElement (new ListComponent (menu));
    }


    /** {@inheritDoc} */
    @Override
    public void addElement (final IComponent component)
    {
        this.columns.add (component);
    }


    /** {@inheritDoc} */
    @Override
    public void addGraphOverlay (final int x, final int y, final int width, final int height, final ColorEx color, final int [] data, final int maxValue)
    {
        this.overlays.add (new GraphOverlayComponent (x, y, width, height, color, data, maxValue));
    }


    /** {@inheritDoc} */
    @Override
    public void setHardwareDisplay (final IHwGraphicsDisplay display)
    {
        this.hardwareDisplay = display;
    }


    /** {@inheritDoc} */
    @Override
    public IHwGraphicsDisplay getHardwareDisplay ()
    {
        return this.hardwareDisplay;
    }


    /** {@inheritDoc} */
    @Override
    public IBitmap getImage ()
    {
        return this.image;
    }


    private void renderImage ()
    {
        this.image.render (this.configuration.isAntialiasEnabled (), gc -> {

            final int width = this.dimensions.getWidth ();
            final int height = this.dimensions.getHeight ();
            final double separatorSize = this.dimensions.getSeparatorSize ();

            // Clear display
            final ColorEx colorBorder = this.configuration.getColorBorder ();
            gc.fillRectangle (0, 0, width, height, colorBorder);

            // Draw the component element
            final List<IComponent> elements = this.info.getComponents ();
            final int size = elements.size ();
            if (size == 0)
                return;
            final int gridWidth = width / size;
            final double paintWidth = gridWidth - separatorSize;
            final double offsetX = separatorSize / 2.0;

            final IGraphicsInfo graphicsInfo = new DefaultGraphicsInfo (gc, this.configuration, this.dimensions);
            for (int i = 0; i < size; i++)
            {
                final IComponent component = elements.get (i);
                if (component != null)
                    component.draw (graphicsInfo.withBounds (i * gridWidth + offsetX, 0, paintWidth, height));
            }

            // Draw overlays
            for (final IComponent overlay: this.info.getOverlays ())
                overlay.draw (graphicsInfo.withBounds (0, 0, width, height));

            // Draw an overlay notification
            final String notification = this.info.getNotification ();
            if (notification == null)
                return;

            final ColorEx colorText = this.configuration.getColorText ();
            gc.drawTextInBounds (notification, 0, 0, width, height, Align.CENTER, colorText, ColorEx.calcContrastColor (colorText), height / 4.0);

        });
    }


    private void checkNotificationCounter ()
    {
        synchronized (this.counterSync)
        {
            int c = this.counter.get ();
            if (c < 0)
                return;

            c = this.counter.decrementAndGet ();
            if (c < 0)
                this.notificationMessage.set (null);
        }
    }


}
