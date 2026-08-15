// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipTimelineSnapshot;
import de.mossgrabers.pull.core.api.ClipTimelineTarget;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetClipTimelineRangeEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputPhase;

import java.util.List;
import java.util.Objects;


/** Shared, checkpointed Clip Timeline interaction state across normal and Master workspaces. */
public final class ClipTimelineState
{
    private static final int [] PAD_RESOLUTIONS =
    {
        1,
        4,
        16
    };

    private int resolution;
    private Gesture gesture;


    /** Constructor with a restored resolution index. */
    public ClipTimelineState (final int resolution)
    {
        this.setResolution (resolution);
    }


    /** Get the selected resolution index. */
    public int resolution ()
    {
        return this.resolution;
    }


    /** Get the selected number of pads per measure. */
    public int padsPerMeasure ()
    {
        return PAD_RESOLUTIONS[this.resolution];
    }


    /** Select one of the three bounded pad resolutions. */
    public void setResolution (final int resolution)
    {
        if (resolution < 0 || resolution >= PAD_RESOLUTIONS.length)
            throw new IllegalArgumentException ("Clip Timeline resolution must be between 0 and 2");
        this.resolution = resolution;
    }


    /** Cancel a frozen gesture when authoritative target identity changes. */
    public void reconcile (final ControllerSnapshot snapshot)
    {
        final Gesture active = this.gesture;
        if (active != null && !snapshot.bridge ().clipTimeline ().target ().filter (active.target ()::equals).isPresent ())
            this.gesture = null;
    }


    /** Handle one physical pad edge mapped to the top-left-origin timeline. */
    public List<CoreEffect> handlePad (final int pad, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (pad < 0 || pad >= 64)
            throw new IllegalArgumentException ("timeline pad must be between 0 and 63");
        Objects.requireNonNull (input, "input");
        Objects.requireNonNull (snapshot, "snapshot");

        if (input.phase () == InputPhase.BEGIN)
        {
            if (this.gesture == null)
            {
                final ClipTimelineSnapshot timeline = snapshot.bridge ().clipTimeline ();
                final TransportSnapshot transport = snapshot.bridge ().transport ();
                if (!timeline.available () || !transport.available ())
                    return List.of ();
                final double quartersPerPad = quartersPerPad (transport, this.padsPerMeasure ());
                if (!Double.isFinite (quartersPerPad) || quartersPerPad <= 0)
                    return List.of ();
                final int selectablePads = Math.min (64, (int) Math.ceil (timeline.selectableEnd () / quartersPerPad));
                if (pad >= selectablePads)
                    return List.of ();
                this.gesture = new Gesture (timeline.target ().orElseThrow (), pad, quartersPerPad, selectablePads, false);
            }
            return List.of ();
        }

        if (input.phase () != InputPhase.END || this.gesture == null)
            return List.of ();

        final Gesture active = this.gesture;
        if (pad >= active.selectablePads ())
            return List.of ();
        if (!snapshot.bridge ().clipTimeline ().target ().filter (active.target ()::equals).isPresent ())
        {
            this.gesture = null;
            return List.of ();
        }

        final boolean secondPad = active.firstPad () != pad;
        final boolean hadSecond = active.hasSecond ();
        if (secondPad)
            this.gesture = active.withSecond ();

        final List<CoreEffect> effects;
        if (secondPad || !hadSecond)
        {
            final int first = Math.min (active.firstPad (), pad);
            final int end = Math.max (active.firstPad (), pad) + 1;
            effects = List.of (new SetClipTimelineRangeEffect (active.target (), first * active.quartersPerPad (), (end - first) * active.quartersPerPad ()));
        }
        else
            effects = List.of ();

        if (active.firstPad () == pad)
            this.gesture = null;
        return effects;
    }


    /** Convert a time signature and bounded resolution into quarters per pad. */
    public static double quartersPerPad (final TransportSnapshot transport, final int padsPerMeasure)
    {
        final TransportSnapshot checked = Objects.requireNonNull (transport, "transport");
        if (!checked.available () || padsPerMeasure <= 0)
            return 0;
        return 4.0 * checked.numerator () / checked.denominator () / padsPerMeasure;
    }


    private record Gesture (ClipTimelineTarget target, int firstPad, double quartersPerPad, int selectablePads, boolean hasSecond)
    {
        private Gesture withSecond ()
        {
            return new Gesture (this.target, this.firstPad, this.quartersPerPad, this.selectablePads, true);
        }
    }
}
