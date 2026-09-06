// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;
import de.mossgrabers.pull.core.api.output.TouchStripMode;

import java.util.Objects;
import java.util.function.Consumer;


/** One replayable output arbitrator beneath every core and frozen legacy touch-strip writer. */
final class TouchStripOutputHost
{
    private final Consumer<DesiredTouchStrip> transmitter;
    private DesiredTouchStrip desired = DesiredTouchStrip.off ();
    private TouchStripMode legacyMode = TouchStripMode.OFF;
    private int legacyPosition;
    private DesiredTouchStrip transmitted;
    private long generation = -1;


    TouchStripOutputHost (final Consumer<DesiredTouchStrip> transmitter)
    {
        this.transmitter = Objects.requireNonNull (transmitter, "transmitter");
    }


    void apply (final DesiredTouchStrip desired, final long generation)
    {
        this.desired = Objects.requireNonNull (desired, "desired");
        if (this.generation != generation)
        {
            this.generation = generation;
            this.transmitted = null;
        }
        this.flush ();
    }


    void legacyMode (final TouchStripMode mode)
    {
        this.legacyMode = Objects.requireNonNull (mode, "mode");
        this.flush ();
    }


    void legacyPosition (final int value)
    {
        if (value < 0 || value > 16383)
            throw new IllegalArgumentException ("touch-strip position must be between 0 and 16383");
        this.legacyPosition = value;
        this.flush ();
    }


    boolean legacyInputEnabled ()
    {
        return !this.desired.owned ();
    }


    void forceFlush ()
    {
        this.transmitted = null;
        this.flush ();
    }


    private void flush ()
    {
        final DesiredTouchStrip output = this.desired.owned () ? this.desired : new DesiredTouchStrip (
            true, this.legacyMode, this.legacyMode == TouchStripMode.OFF ? 0 : this.legacyPosition);
        if (output.equals (this.transmitted))
            return;
        this.transmitter.accept (output);
        this.transmitted = output;
    }
}
