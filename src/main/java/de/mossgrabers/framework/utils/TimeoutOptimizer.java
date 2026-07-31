// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.utils;

import java.util.concurrent.TimeUnit;

import de.mossgrabers.framework.daw.IHost;


/**
 * Helper class to optimize a timeout interval depending on the computer/OS for using it with the
 * IHost.scheduleTask method.
 *
 * @author Jürgen Moßgraber
 */
public class TimeoutOptimizer
{
    private static final int RUNS = 30;

    private final IHost      host;
    private final int        requestedDelay;
    private volatile int     delay;
    private long             startValue;
    private int              iterations;
    private long             diff;


    /**
     * Constructor.
     *
     * @param host The host instance
     * @param delay The delay in milliseconds
     */
    public TimeoutOptimizer (final IHost host, final int delay)
    {
        this.host = host;
        this.requestedDelay = delay;
        this.delay = delay;

        this.host.scheduleTask (this::measure, delay);
    }


    /**
     * Executes the measurement for several times and calculates the average after a number of runs.
     */
    private void measure ()
    {
        final long endValue = System.nanoTime ();

        // The first callback can be held until extension initialization returns. Use it only as
        // the start of the calibration window so startup work cannot skew every button timeout.
        if (this.startValue == 0)
        {
            this.startValue = endValue;
            this.host.scheduleTask (this::measure, this.delay);
            return;
        }

        this.diff += endValue - this.startValue;
        this.iterations++;

        if (this.iterations < RUNS)
        {
            this.startValue = endValue;
            this.host.scheduleTask (this::measure, this.delay);
        }
        else
        {
            final long effective = this.diff / RUNS;
            if (effective <= 0)
                return;

            final long requestedNanos = TimeUnit.MILLISECONDS.toNanos (this.requestedDelay);
            final long optimizedDelay = Math.round (this.requestedDelay * (double) requestedNanos / effective);
            this.delay = (int) Math.max (1, Math.min (Integer.MAX_VALUE, optimizedDelay));
        }
    }


    /**
     * Get the measured and calculated timeout which should be used to achieve the delay given in
     * the constructor.
     *
     * @return The timeout
     */
    public int getTimeout ()
    {
        return this.delay;
    }
}
