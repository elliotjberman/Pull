// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Entry point supplied by a reloadable controller-core JAR.
 */
public interface CoreProvider
{
    /**
     * Describe the core before creating its runtime instance.
     *
     * @return The core descriptor
     */
    CoreDescriptor descriptor ();


    /**
     * Create a fresh runtime instance.
     *
     * @return A new controller core
     */
    ControllerCore create ();
}
