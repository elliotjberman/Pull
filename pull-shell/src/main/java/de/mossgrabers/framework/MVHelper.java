// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework;

import java.util.Optional;
import java.util.function.Supplier;

import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.IControlSurface;
import de.mossgrabers.framework.controller.display.IDisplay;
import de.mossgrabers.framework.daw.GrooveParameterID;
import de.mossgrabers.framework.daw.IGroove;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.featuregroup.IMode;


/**
 * Shared display notifications used by modes and commands.
 *
 * @param <C> The type of the configuration
 * @param <S> The type of the control surface
 *
 * @author Jürgen Moßgraber
 */
public class MVHelper<S extends IControlSurface<C>, C extends Configuration>
{
    private static final String NONE          = "None";
    private static final int    DISPLAY_DELAY = 200;

    private final ITransport    transport;
    private final IGroove       groove;
    private final S             surface;
    private final IDisplay      display;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public MVHelper (final IModel model, final S surface)
    {
        this.surface = surface;
        this.display = surface == null ? null : surface.getDisplay ();
        this.transport = model == null ? null : model.getTransport ();
        this.groove = model == null ? null : model.getGroove ();
    }


    /**
     * Display the name of the selected item in the current mode.
     *
     * @param mode The mode
     */
    public void notifySelectedItem (final IMode mode)
    {
        this.delayDisplay ( () -> {

            final Optional<String> selectedItemName = mode.getSelectedItemName ();
            return selectedItemName.orElse (NONE);

        });
    }


    /**
     * Display the current tempo.
     */
    public void notifyTempo ()
    {
        this.delayDisplay ( () -> "Tempo: " + this.transport.formatTempo (this.transport.getTempo ()));
    }


    /**
     * Display the current shuffle amount.
     */
    public void notifyShuffle ()
    {
        this.delayDisplay ( () -> "Shuffle: " + this.groove.getParameter (GrooveParameterID.SHUFFLE_AMOUNT).getDisplayedValue ());
    }


    /**
     * Display the current edit page of the note clip.
     *
     * @param clip The clip
     */
    public void notifyEditPage (final INoteClip clip)
    {
        if (clip != null && clip.doesExist ())
            this.delayDisplay ( () -> "Edit page: " + (clip.getEditPage () + 1));
    }


    /**
     * Notify a text after 200ms.
     *
     * @param supplier The supplier to provide the text
     */
    public void delayDisplay (final Supplier<String> supplier)
    {
        this.surface.scheduleTask ( () -> {

            final String message = supplier.get ();
            if (message != null && !message.isBlank ())
                this.display.notify (message);

        }, DISPLAY_DELAY);
    }
}
