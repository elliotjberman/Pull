// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.display;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.graphics.DefaultGraphicsDimensions;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.IEncoder;
import de.mossgrabers.framework.graphics.IGraphicsConfiguration;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IImage;
import de.mossgrabers.framework.graphics.IRenderer;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** Composition tests for the generic complete-display base and overlay planes. */
class AbstractGraphicDisplayTest
{
    @Test
    void completeBaseReplacesPageColumnsButPreservesTheFinalOverlayPlane ()
    {
        final List<String> draws = new ArrayList<> ();
        final AtomicReference<IComponent> base = new AtomicReference<> (component (draws, "base"));
        final AtomicReference<IComponent> overlay = new AtomicReference<> (component (draws, "overlay"));
        final TestDisplay display = new TestDisplay (host (), configuration ());
        display.installBase (base::get);
        display.installOverlay (overlay::get);

        try
        {
            display.addElement (component (draws, "stable"));
            display.send ();
            assertEquals (List.of ("base", "overlay"), draws);

            draws.clear ();
            base.set (null);
            overlay.set (null);
            display.addElement (component (draws, "stable"));
            display.send ();
            assertEquals (List.of ("stable"), draws);
        }
        finally
        {
            display.shutdown ();
        }
    }


    private static IComponent component (final List<String> draws, final String name)
    {
        return ignored -> draws.add (name);
    }


    private static IHost host ()
    {
        final IImage image = (IImage) Proxy.newProxyInstance (IImage.class.getClassLoader (), new Class<?> []
        {
            IImage.class
        }, (ignored, method, arguments) -> defaultValue (method.getReturnType ()));
        final IGraphicsContext context = (IGraphicsContext) Proxy.newProxyInstance (IGraphicsContext.class.getClassLoader (), new Class<?> []
        {
            IGraphicsContext.class
        }, (ignored, method, arguments) -> defaultValue (method.getReturnType ()));
        final IBitmap bitmap = new IBitmap ()
        {
            @Override
            public void render (final boolean enableAntialias, final IRenderer renderer)
            {
                renderer.render (context);
            }


            @Override
            public void encode (final IEncoder encoder)
            {
                // Not needed by the display test.
            }
        };
        return (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (), new Class<?> []
        {
            IHost.class
        }, (ignored, method, arguments) -> switch (method.getName ())
        {
            case "createBitmap" -> bitmap;
            case "loadSVG" -> image;
            default -> defaultValue (method.getReturnType ());
        });
    }


    private static IGraphicsConfiguration configuration ()
    {
        return (IGraphicsConfiguration) Proxy.newProxyInstance (IGraphicsConfiguration.class.getClassLoader (), new Class<?> []
        {
            IGraphicsConfiguration.class
        }, (ignored, method, arguments) -> method.getReturnType () == ColorEx.class ? ColorEx.BLACK : defaultValue (method.getReturnType ()));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == int.class)
            return Integer.valueOf (0);
        if (type == long.class)
            return Long.valueOf (0);
        if (type == double.class)
            return Double.valueOf (0);
        return null;
    }


    private static final class TestDisplay extends AbstractGraphicDisplay
    {
        private TestDisplay (final IHost host, final IGraphicsConfiguration configuration)
        {
            super (host, configuration, new DefaultGraphicsDimensions (960, 160, 1024));
        }


        private void installBase (final java.util.function.Supplier<IComponent> supplier)
        {
            this.setFullScreenBaseSupplier (supplier);
        }


        private void installOverlay (final java.util.function.Supplier<IComponent> supplier)
        {
            this.setFullScreenOverlaySupplier (supplier);
        }


        @Override
        public void notify (final String message)
        {
            // No host notification in this unit test.
        }


        @Override
        protected void send (final IBitmap image)
        {
            // No hardware in this unit test.
        }
    }
}
