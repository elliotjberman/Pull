// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

/**
 * The physical shape of a controller input. The kind also fixes how the stable shell buffers the
 * input before handing it to a reloadable consumer.
 */
public enum InputKind
{
    /** A momentary or latching button. */
    BUTTON (Delivery.EDGE),
    /** A playable grid pad edge. */
    PAD (Delivery.EDGE),
    /** A relative encoder value. */
    RELATIVE (Delivery.RELATIVE_SUM),
    /** An absolute continuous value, such as the ribbon position. */
    ABSOLUTE (Delivery.ABSOLUTE_LATEST),
    /** The touch edge associated with a continuous control. */
    TOUCH (Delivery.EDGE),
    /** Per-pad pressure. */
    POLY_PRESSURE (Delivery.ABSOLUTE_LATEST),
    /** Channel pressure. */
    CHANNEL_PRESSURE (Delivery.ABSOLUTE_LATEST),
    /** A pedal press or release. */
    PEDAL (Delivery.EDGE);

    private final Delivery delivery;


    InputKind (final Delivery delivery)
    {
        this.delivery = delivery;
    }


    /**
     * Returns true for inputs which form a BEGIN-to-END gesture.
     *
     * @return True if this is an edge input
     */
    public boolean isEdge ()
    {
        return this.delivery == Delivery.EDGE;
    }


    /**
     * Returns true if adjacent values are summed before delivery.
     *
     * @return True for relative input
     */
    public boolean sumsRelativeValues ()
    {
        return this.delivery == Delivery.RELATIVE_SUM;
    }


    /**
     * Returns true if only the latest value is retained before delivery.
     *
     * @return True for absolute input
     */
    public boolean keepsLatestValue ()
    {
        return this.delivery == Delivery.ABSOLUTE_LATEST;
    }


    private enum Delivery
    {
        EDGE,
        RELATIVE_SUM,
        ABSOLUTE_LATEST
    }
}
