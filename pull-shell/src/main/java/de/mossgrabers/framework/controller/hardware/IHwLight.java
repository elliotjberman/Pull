// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;

import de.mossgrabers.framework.controller.color.ColorEx;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Interface for a proxy to a light / LED on a hardware controller.
 *
 * @author Jürgen Moßgraber
 */
public interface IHwLight extends IHwControl
{
    /**
     * Install the one permanent observer for host manual-mapping feedback on this light.
     *
     * <p>An empty value means the host does not currently assign mapped feedback. The observer only
     * captures authoritative host color state; normal light suppliers continue owning physical
     * output.</p>
     *
     * @param observer Mapped feedback observer
     */
    void installMappedColorObserver (Consumer<Optional<ColorEx>> observer);


    /**
     * Switch off the light.
     */
    void turnOff ();


    /**
     * Clear the light cache state.
     */
    void forceFlush ();
}
