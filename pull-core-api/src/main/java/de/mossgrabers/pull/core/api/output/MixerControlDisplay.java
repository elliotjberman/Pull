// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.ParameterSlot;

import java.util.Objects;


/** One column-local mixer-control scene confined to the stable-owned parameter body. */
public record MixerControlDisplay (int column, MixerControlKind kind, ControllerDisplayScene scene)
{
    /** Logical cell width in display pixels. */
    public static final int WIDTH = 120;
    /** Logical height of the complete controller display. */
    public static final int DISPLAY_HEIGHT = 160;
    /** Stable top edge below the soft-button menu. */
    public static final int TOP = 17;
    /** Logical cell height ending before the track footer. */
    public static final int HEIGHT = 126;

    /** Validate one bounded column-local scene. */
    public MixerControlDisplay
    {
        if (column < 0 || column >= ParameterSlot.BANK_SIZE)
            throw new IllegalArgumentException ("column must be between 0 and 7");
        kind = Objects.requireNonNull (kind, "kind");
        scene = Objects.requireNonNull (scene, "scene");
        if (!scene.isPresent () || scene.width () != WIDTH || scene.height () != HEIGHT)
            throw new IllegalArgumentException ("mixer control scenes must use the 120x126 body viewport");
        scene.commands ().forEach (MixerControlDisplay::requireContained);
    }


    private static void requireContained (final DisplayCommand command)
    {
        switch (command)
        {
            case final DisplayCommand.Rectangle rectangle -> requireBox (rectangle.x (), rectangle.y (), rectangle.width (), rectangle.height ());
            case final DisplayCommand.TextAt text -> requirePoint (text.x (), text.baselineY ());
            case final DisplayCommand.DottedArc arc -> requireBox (arc.centerX () - arc.radius () - arc.dotRadius (), arc.centerY () - arc.radius () - arc.dotRadius (), 2 * (arc.radius () + arc.dotRadius ()), 2 * (arc.radius () + arc.dotRadius ()));
            default -> throw new IllegalArgumentException ("mixer control scenes support only contained mixer-control primitives");
        }
    }


    private static void requirePoint (final double x, final double y)
    {
        if (x < 0 || y < 0 || x > WIDTH || y > HEIGHT)
            throw new IllegalArgumentException ("mixer control text anchor exceeds its owned column body");
    }
    private static void requireBox (final double x, final double y, final double width, final double height)
    {
        if (x < 0 || y < 0 || x + width > WIDTH || y + height > HEIGHT)
            throw new IllegalArgumentException ("mixer control command exceeds its owned column body");
    }
}
