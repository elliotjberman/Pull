// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.TimeoutOptimizer;


/**
 * Abstract implementation of a proxy to a button on a hardware controller.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractHwButton extends AbstractHwInputControl implements IHwButton
{
    private static final int               BUTTON_STATE_INTERVAL = 500;

    private final TimeoutOptimizer         optimizer;

    protected TriggerCommand               command;
    protected IHwLight                     light;

    private ButtonEventArbitrator           eventArbitrator;
    private ButtonEvent                    physicalState;
    private ButtonEvent                    legacyState;
    private boolean                        isConsumed;
    private int                            pressedVelocity       = 0;
    private int                            physicalPressedVelocity = 0;
    private int                            scheduleCounter       = 0;
    private final Object                   buttonStateLock       = new Object ();

    private final List<ButtonEventHandler> downEventHandlers     = new ArrayList<> ();
    private final List<ButtonEventHandler> upEventHandlers       = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param host The host
     * @param label The label of the button
     */
    protected AbstractHwButton (final IHost host, final String label)
    {
        this (host, label, new TimeoutOptimizer (host, BUTTON_STATE_INTERVAL));
    }


    /**
     * Constructor with a shared timeout optimizer.
     *
     * @param host The host
     * @param label The label of the button
     * @param optimizer The optimizer shared by buttons on the same surface
     */
    protected AbstractHwButton (final IHost host, final String label, final TimeoutOptimizer optimizer)
    {
        super (host, label);

        this.optimizer = optimizer;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isBound ()
    {
        return this.command != null;
    }


    /** {@inheritDoc} */
    @Override
    public final void installEventArbitrator (final ButtonEventArbitrator arbitrator)
    {
        if (this.command == null)
            throw new IllegalStateException ("Cannot arbitrate an unbound button");
        if (arbitrator == null)
            throw new IllegalArgumentException ("arbitrator must not be null");
        if (this.eventArbitrator != null)
            throw new IllegalStateException ("A button event arbitrator is already installed");
        this.eventArbitrator = arbitrator;
    }


    /** {@inheritDoc} */
    @Override
    public void clearState ()
    {
        synchronized (this.buttonStateLock)
        {
            this.physicalState = null;
            this.legacyState = null;
        }
    }


    /**
     * Handle a button press.
     *
     * @param value The pressure value in the range of [0..1]
     */
    protected void handleButtonPressed (final double value)
    {
        // This is necessary since it is called as well for an release event!
        if (value == 0)
            return;

        synchronized (this.buttonStateLock)
        {
            this.physicalState = ButtonEvent.DOWN;
            this.scheduleCounter++;
            this.host.scheduleTask (this::checkButtonState, this.optimizer.getTimeout ());
        }

        final int velocity = (int) (value * 127.0);
        this.physicalPressedVelocity = velocity;
        this.arbitrate (ButtonEvent.DOWN, velocity, () -> this.dispatchLegacyPressed (velocity));
    }


    /**
     * Handle a button release.
     */
    protected void handleButtonRelease ()
    {
        if (!this.isBound ())
            return;

        synchronized (this.buttonStateLock)
        {
            this.physicalState = ButtonEvent.UP;
        }

        this.arbitrate (ButtonEvent.UP, 0, this::dispatchLegacyRelease);
    }


    /** {@inheritDoc} */
    @Override
    public void addLight (final IHwLight light)
    {
        this.light = light;
    }


    /** {@inheritDoc} */
    @Override
    public IHwLight getLight ()
    {
        return this.light;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isPressed ()
    {
        synchronized (this.buttonStateLock)
        {
            return this.legacyState == ButtonEvent.DOWN || this.legacyState == ButtonEvent.LONG;
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean isLongPressed ()
    {
        synchronized (this.buttonStateLock)
        {
            return this.legacyState == ButtonEvent.LONG;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void addEventHandler (final ButtonEvent event, final ButtonEventHandler eventHandler)
    {
        if (event == ButtonEvent.DOWN)
            this.downEventHandlers.add (eventHandler);
        else if (event == ButtonEvent.UP)
            this.upEventHandlers.add (eventHandler);
    }


    /** {@inheritDoc} */
    @Override
    public void removeEventHandler (final ButtonEvent event, final ButtonEventHandler eventHandler)
    {
        if (event == ButtonEvent.DOWN)
            this.downEventHandlers.remove (eventHandler);
        else if (event == ButtonEvent.UP)
            this.upEventHandlers.remove (eventHandler);
    }


    /** {@inheritDoc} */
    @Override
    public void setConsumed ()
    {
        this.isConsumed = true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isConsumed ()
    {
        return this.isConsumed;
    }


    /** {@inheritDoc} */
    @Override
    public void trigger ()
    {
        this.trigger (ButtonEvent.DOWN);
        this.trigger (ButtonEvent.UP);
    }


    /** {@inheritDoc} */
    @Override
    public void trigger (final ButtonEvent event)
    {
        this.trigger (event, 1.0);
    }


    /** {@inheritDoc} */
    @Override
    public void trigger (final ButtonEvent event, final double velocity)
    {
        if (event == ButtonEvent.DOWN)
            this.handleButtonPressed (velocity);
        else if (event == ButtonEvent.UP)
            this.handleButtonRelease ();
    }


    /** {@inheritDoc} */
    @Override
    public int getPressedVelocity ()
    {
        return this.pressedVelocity;
    }


    /** {@inheritDoc} */
    @Override
    public TriggerCommand getCommand ()
    {
        return this.command;
    }


    /**
     * If the state of the given button is still down, the state is set to long and an event gets
     * fired.
     */
    private void checkButtonState ()
    {
        synchronized (this.buttonStateLock)
        {
            this.scheduleCounter--;
            // This prevents that LONG is accidently fired if one quickly switches between 2 buttons
            // and the old scheduler was still running
            if (this.scheduleCounter > 0)
                return;

            if (this.physicalState != ButtonEvent.DOWN && this.physicalState != ButtonEvent.LONG)
                return;
            this.physicalState = ButtonEvent.LONG;
        }

        this.arbitrate (ButtonEvent.LONG, this.physicalPressedVelocity, this::dispatchLegacyLongPress);
    }


    private void arbitrate (final ButtonEvent event, final int velocity, final Runnable legacyDispatch)
    {
        if (this.eventArbitrator == null)
            legacyDispatch.run ();
        else
            this.eventArbitrator.arbitrate (event, velocity, legacyDispatch);
    }


    private void dispatchLegacyPressed (final int velocity)
    {
        synchronized (this.buttonStateLock)
        {
            this.legacyState = ButtonEvent.DOWN;
            this.isConsumed = false;
        }

        this.pressedVelocity = velocity;
        if (this.command != null)
            this.command.execute (ButtonEvent.DOWN, velocity);

        this.downEventHandlers.forEach (handler -> handler.handle (ButtonEvent.DOWN));
    }


    private void dispatchLegacyRelease ()
    {
        synchronized (this.buttonStateLock)
        {
            this.legacyState = ButtonEvent.UP;
        }

        if (this.command != null && !this.isConsumed)
            this.command.execute (ButtonEvent.UP, 0);

        this.upEventHandlers.forEach (handler -> handler.handle (ButtonEvent.UP));
    }


    private void dispatchLegacyLongPress ()
    {
        synchronized (this.buttonStateLock)
        {
            this.legacyState = ButtonEvent.LONG;
        }

        if (this.command != null)
            this.command.execute (ButtonEvent.LONG, this.pressedVelocity);
    }
}
