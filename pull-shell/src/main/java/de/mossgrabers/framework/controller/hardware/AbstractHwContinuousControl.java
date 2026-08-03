// Written by Jürgen Moßgraber - mossgrabers.de
// protected c) 2017-2019
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import de.mossgrabers.framework.command.core.ContinuousCommand;
import de.mossgrabers.framework.command.core.PitchbendCommand;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * A control on a hardware controller.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractHwContinuousControl extends AbstractHwInputControl implements IHwContinuousControl
{
    private static final int    BUTTON_STATE_INTERVAL = 500;

    protected ContinuousCommand command;
    protected TriggerCommand    touchCommand;
    protected PitchbendCommand  pitchbendCommand;

    private ContinuousValueArbitrator valueArbitrator;
    private ButtonEventArbitrator     touchEventArbitrator;
    private ButtonEvent               physicalTouchState;
    private ButtonEvent               legacyTouchState;
    private int                       touchScheduleCounter;

    protected IntSupplier       supplier;
    protected IntConsumer       consumer;
    protected int               outputValue           = -1;


    /**
     * Constructor.
     *
     * @param host The host
     * @param label The label of the control
     */
    protected AbstractHwContinuousControl (final IHost host, final String label)
    {
        super (host, label);
    }


    /** {@inheritDoc} */
    @Override
    public void bind (final ContinuousCommand command)
    {
        this.command = command;
    }


    /** {@inheritDoc} */
    @Override
    public void bind (final PitchbendCommand command)
    {
        this.pitchbendCommand = command;
    }


    /** {@inheritDoc} */
    @Override
    public final void installValueArbitrator (final ContinuousValueArbitrator arbitrator)
    {
        if (arbitrator == null)
            throw new IllegalArgumentException ("arbitrator must not be null");
        if (this.valueArbitrator != null)
            throw new IllegalStateException ("A continuous-value arbitrator is already installed");

        this.valueArbitrator = arbitrator;
        try
        {
            this.onValueArbitratorInstalled ();
        }
        catch (final RuntimeException ex)
        {
            this.valueArbitrator = null;
            throw ex;
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean isBound ()
    {
        return this.command != null || this.touchCommand != null || this.pitchbendCommand != null;
    }


    /** {@inheritDoc} */
    @Override
    public ContinuousCommand getCommand ()
    {
        return this.command;
    }


    /** {@inheritDoc} */
    @Override
    public TriggerCommand getTouchCommand ()
    {
        return this.touchCommand;
    }


    /** {@inheritDoc} */
    @Override
    public void replaceTouchCommand (final TriggerCommand command)
    {
        if (this.touchCommand == null)
            throw new IllegalStateException ("Cannot replace the command of a control without a touch binding");
        if (command == null)
            throw new IllegalArgumentException ("command must not be null");
        this.touchCommand = command;
    }


    /** {@inheritDoc} */
    @Override
    public final void installTouchEventArbitrator (final ButtonEventArbitrator arbitrator)
    {
        if (this.touchCommand == null)
            throw new IllegalStateException ("Cannot arbitrate a control without a touch binding");
        if (arbitrator == null)
            throw new IllegalArgumentException ("arbitrator must not be null");
        if (this.touchEventArbitrator != null)
            throw new IllegalStateException ("A touch-event arbitrator is already installed");
        this.touchEventArbitrator = arbitrator;
    }


    /** {@inheritDoc} */
    @Override
    public PitchbendCommand getPitchbendCommand ()
    {
        return this.pitchbendCommand;
    }


    /** {@inheritDoc} */
    @Override
    public void triggerTouch (final boolean isDown)
    {
        if (this.touchCommand == null)
            return;

        final ButtonEvent event = isDown ? ButtonEvent.DOWN : ButtonEvent.UP;
        this.physicalTouchState = event;
        if (isDown)
        {
            this.touchScheduleCounter++;
            this.host.scheduleTask (this::checkButtonState, BUTTON_STATE_INTERVAL);
        }

        final int velocity = isDown ? 127 : 0;
        this.arbitrateTouch (event, velocity, () -> this.dispatchLegacyTouch (event, velocity));
    }


    /** {@inheritDoc} */
    @Override
    public boolean isTouched ()
    {
        return this.legacyTouchState == ButtonEvent.DOWN || this.legacyTouchState == ButtonEvent.LONG;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isLongTouched ()
    {
        return this.legacyTouchState == ButtonEvent.LONG;
    }


    /** {@inheritDoc} */
    @Override
    public void addOutput (final IntSupplier supplier, final IntConsumer consumer)
    {
        this.supplier = supplier;
        this.consumer = consumer;
    }


    /** {@inheritDoc} */
    @Override
    public void forceFlush ()
    {
        this.outputValue = -1;
    }


    /** {@inheritDoc} */
    @Override
    public void turnOff ()
    {
        this.supplier = null;
        this.outputValue = -1;
        if (this.consumer != null)
            this.consumer.accept (0);
    }


    /** {@inheritDoc} */
    @Override
    public void update ()
    {
        if (this.supplier == null)
            return;

        final int value = this.supplier.getAsInt ();
        if (value == this.outputValue)
            return;
        this.outputValue = value;
        this.consumer.accept (this.outputValue);
    }


    /**
     * If the state of the given button is still down, the state is set to long and an event gets
     * fired.
     */
    private void checkButtonState ()
    {
        this.touchScheduleCounter--;
        if (this.touchScheduleCounter > 0)
            return;
        if (this.physicalTouchState != ButtonEvent.DOWN && this.physicalTouchState != ButtonEvent.LONG)
            return;
        this.physicalTouchState = ButtonEvent.LONG;
        this.arbitrateTouch (ButtonEvent.LONG, 127, () -> this.dispatchLegacyTouch (ButtonEvent.LONG, 127));
    }


    /**
     * Called exactly once when value arbitration is installed. Bitwig-backed controls override
     * this to replace any direct parameter/command target with their stable callback.
     */
    protected void onValueArbitratorInstalled ()
    {
        // Optional for non-Bitwig implementations which already dispatch through handleValue.
    }


    /**
     * Test whether value arbitration has been installed.
     *
     * @return True if installed
     */
    protected final boolean hasValueArbitrator ()
    {
        return this.valueArbitrator != null;
    }


    /**
     * Dispatch one decoded value through the optional arbitrator.
     *
     * @param value Decoded control value
     * @param legacyMutation Complete established mutation
     */
    protected final void arbitrateValue (final int value, final Runnable legacyMutation)
    {
        if (this.valueArbitrator == null)
            legacyMutation.run ();
        else
            this.valueArbitrator.arbitrate (value, legacyMutation);
    }


    private void arbitrateTouch (final ButtonEvent event, final int velocity, final Runnable legacyDispatch)
    {
        if (this.touchEventArbitrator == null)
            legacyDispatch.run ();
        else
            this.touchEventArbitrator.arbitrate (event, velocity, legacyDispatch);
    }


    private void dispatchLegacyTouch (final ButtonEvent event, final int velocity)
    {
        this.legacyTouchState = event;
        if (this.touchCommand != null)
            this.touchCommand.execute (event, velocity);
    }
}
