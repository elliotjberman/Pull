// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.curve;

import java.util.List;

/** Immutable, bounded keyframes with shape-preserving cubic Hermite interpolation. */
public final class KeyframeCurve implements ReturnCurve
{
    public record Key (double time, double remaining) {}
    private final List<Key> keys;
    private final boolean smooth;
    private final double [] slopes;

    public KeyframeCurve (final List<Key> keys, final boolean smooth)
    {
        this.keys = List.copyOf (keys);
        this.smooth = smooth;
        final int count = keys.size ();
        if (count < 2 || count > 32)
            throw new IllegalArgumentException ("keyframes must contain 2–32 points");
        double previous = -1;
        for (final Key key: keys)
        {
            if (!Double.isFinite (key.time ()) || key.time () < 0 || key.time () > 1 || key.time () - previous < 0.000001)
                throw new IllegalArgumentException ("keyframe times must increase from 0 to 1, at least 0.000001 apart");
            if (!Double.isFinite (key.remaining ()) || Math.abs (key.remaining ()) > 4)
                throw new IllegalArgumentException ("keyframe remaining displacement must be finite and within -4..4");
            previous = key.time ();
        }
        if (keys.getFirst ().time () != 0 || keys.getFirst ().remaining () != 1 || keys.getLast ().time () != 1 || keys.getLast ().remaining () != 0)
            throw new IllegalArgumentException ("keyframes must start at [0, 1] and finish at [1, 0]");
        this.slopes = new double[count];
        // Flat endpoint tangents give smooth departure/arrival. Interior weighted harmonic
        // means preserve each segment's shape; extrema have zero slope, so bounces stay bounded.
        for (int i = 1; i < count - 1; i++)
        {
            final double leftWidth = keys.get (i).time () - keys.get (i - 1).time ();
            final double rightWidth = keys.get (i + 1).time () - keys.get (i).time ();
            final double left = (keys.get (i).remaining () - keys.get (i - 1).remaining ()) / leftWidth;
            final double right = (keys.get (i + 1).remaining () - keys.get (i).remaining ()) / rightWidth;
            if (left != 0 && right != 0 && Math.signum (left) == Math.signum (right))
            {
                final double w1 = 2 * rightWidth + leftWidth;
                final double w2 = rightWidth + 2 * leftWidth;
                this.slopes[i] = (w1 + w2) / (w1 / left + w2 / right);
            }
        }
    }

    @Override
    public double apply (final double time)
    {
        if (time <= 0) return 0;
        if (time >= 1) return 1;
        int i = 0;
        while (time > this.keys.get (i + 1).time ()) i++;
        final Key left = this.keys.get (i);
        final Key right = this.keys.get (i + 1);
        final double width = right.time () - left.time ();
        final double t = (time - left.time ()) / width;
        if (!this.smooth)
            return 1 - (left.remaining () + (right.remaining () - left.remaining ()) * t);
        final double t2 = t * t;
        final double t3 = t2 * t;
        return 1 - ((2 * t3 - 3 * t2 + 1) * left.remaining () + (t3 - 2 * t2 + t) * width * this.slopes[i]
            + (-2 * t3 + 3 * t2) * right.remaining () + (t3 - t2) * width * this.slopes[i + 1]);
    }
}
