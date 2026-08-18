// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import java.util.List;
import java.util.Objects;


/** Complete replayable vector scene for one controller graphics display. */
public record ControllerDisplayScene (int width, int height, List<DisplayCommand> commands)
{
    private static final int MAX_COMMANDS    = 2048;
    private static final int MAX_RENDER_WORK = 8192;
    private static final ControllerDisplayScene EMPTY = new ControllerDisplayScene (0, 0, List.of ());


    /** Validate and copy the bounded command buffer. */
    public ControllerDisplayScene
    {
        commands = List.copyOf (Objects.requireNonNull (commands, "commands"));
        if (commands.isEmpty ())
        {
            if (width != 0 || height != 0)
                throw new IllegalArgumentException ("An empty display scene must have a zero-sized viewport");
        }
        else if (width <= 0 || height <= 0 || width > 4096 || height > 4096)
            throw new IllegalArgumentException ("Display scene viewport must be between 1 and 4096 pixels");
        if (commands.size () > MAX_COMMANDS)
            throw new IllegalArgumentException ("Display scene exceeds its 2048-command capacity");

        long renderWork = 0;
        int clipDepth = 0;
        for (final DisplayCommand command: commands)
        {
            if (command instanceof DisplayCommand.PushClip)
            {
                if (clipDepth != 0)
                    throw new IllegalArgumentException ("display clip scopes must not nest");
                clipDepth = 1;
            }
            else if (command instanceof DisplayCommand.PopClip)
            {
                if (clipDepth == 0)
                    throw new IllegalArgumentException ("display clip scope is unbalanced");
                clipDepth = 0;
            }
            renderWork += renderCost (command);
            if (renderWork > MAX_RENDER_WORK)
                throw new IllegalArgumentException ("Display scene exceeds its bounded render-work capacity");
        }
        if (clipDepth != 0)
            throw new IllegalArgumentException ("display clip scope is unbalanced");
    }


    /** Get no scene override. */
    public static ControllerDisplayScene empty ()
    {
        return EMPTY;
    }


    /** Test whether this scene should replace stable display composition. */
    public boolean isPresent ()
    {
        return !this.commands.isEmpty ();
    }


    private static long renderCost (final DisplayCommand command)
    {
        if (command instanceof final DisplayCommand.DottedArc arc)
            return arc.steps () + 1L;
        if (command instanceof final DisplayCommand.TextAt text)
            return 1L + text.text ().codePointCount (0, text.text ().length ()) / 16L;
        if (!(command instanceof final DisplayCommand.TextBox text))
            return 1;

        final long codePoints = text.text ().codePointCount (0, text.text ().length ());
        final long textMeasureCost = 1 + codePoints / 16;
        if (text.fit () == DisplayTextFit.CLIP)
            return textMeasureCost;

        final long fontTrials = 2 + (long) Math.ceil (text.maximumFontSize () - text.minimumFontSize ());
        if (text.fit () == DisplayTextFit.SHRINK)
            return textMeasureCost * fontTrials;

        // Ellipsis probes every successively shorter code-point prefix. Account for both the
        // number and total length of those measurements before the stable renderer accepts it.
        final long prefixCodePoints = codePoints * (codePoints + 1) / 2;
        return fontTrials * (codePoints + 2 + prefixCodePoints / 16);
    }
}
