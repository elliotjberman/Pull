// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller;

/**
 * Interface to setting up a controller.
 *
 * @author Jürgen Moßgraber
 */
public interface IControllerSetup
{
    /**
     * Initialize all required functionality for the controller.
     */
    void init ();


    /**
     * Startup the controller.
     */
    void startup ();


    /**
     * Execute necessary shutdown functions.
     */
    void exit ();


    /**
     * Update cycle. Use e.g. for display updates.
     */
    void flush ();


}
