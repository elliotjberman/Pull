// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


/** Validates and combines fixed local display regions into one complete Push viewport. */
final class DisplayRegionComposition
{
    private static final int WIDTH = 960;
    private static final int HEIGHT = 160;
    private static final int FOOTER_HEIGHT = 17;
    private static final int PARAMETER_HEIGHT = HEIGHT - FOOTER_HEIGHT;
    private static final Set<SurfaceArea> COMPLETE_REGIONS = Set.of (SurfaceArea.DISPLAY_PARAMETERS, SurfaceArea.DISPLAY_BOTTOM_STRIP);


    private DisplayRegionComposition ()
    {
        // Utility class.
    }


    /** Compose one complete display only when all installed fixed regions are present. */
    static ControllerDisplayScene compose (final Map<SurfaceArea, ControllerDisplayScene> regions)
    {
        if (!regions.keySet ().equals (COMPLETE_REGIONS))
            throw new IllegalStateException ("composed controller display must own every fixed display region");

        final ArrayList<DisplayCommand> commands = new ArrayList<> ();
        append (commands, regions.get (SurfaceArea.DISPLAY_PARAMETERS), WIDTH, PARAMETER_HEIGHT, 0);
        append (commands, regions.get (SurfaceArea.DISPLAY_BOTTOM_STRIP), WIDTH, FOOTER_HEIGHT, PARAMETER_HEIGHT);
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }


    private static void append (final ArrayList<DisplayCommand> target, final ControllerDisplayScene scene, final int width, final int height, final double offsetY)
    {
        if (scene == null || !scene.isPresent () || scene.width () != width || scene.height () != height)
            throw new IllegalStateException ("display-region scene has the wrong fixed viewport");
        target.add (new DisplayCommand.PushClip (0, offsetY, width, height));
        for (final DisplayCommand command: scene.commands ())
        {
            requireContained (command, width, height);
            target.add (translate (command, offsetY));
        }
        target.add (new DisplayCommand.PopClip ());
    }


    private static void requireContained (final DisplayCommand command, final double width, final double height)
    {
        switch (command)
        {
            case final DisplayCommand.PushClip ignored -> throw new IllegalStateException ("display-region clip scopes are compiler-owned");
            case final DisplayCommand.PopClip ignored -> throw new IllegalStateException ("display-region clip scopes are compiler-owned");
            case final DisplayCommand.Rectangle rectangle -> requireBox (rectangle.x (), rectangle.y (), rectangle.width (), rectangle.height (), width, height);
            case final DisplayCommand.RoundedRectangle rectangle -> requireBox (rectangle.x (), rectangle.y (), rectangle.width (), rectangle.height (), width, height);
            case final DisplayCommand.Circle circle -> requireBox (circle.centerX () - circle.radius (), circle.centerY () - circle.radius (), 2 * circle.radius (), 2 * circle.radius (), width, height);
            case final DisplayCommand.DottedArc arc -> {
                final double radius = arc.radius () + arc.dotRadius ();
                requireBox (arc.centerX () - radius, arc.centerY () - radius, 2 * radius, 2 * radius, width, height);
            }
            case final DisplayCommand.TextAt text -> requirePoint (text.x (), text.baselineY (), width, height);
            case final DisplayCommand.TextBox text -> requireBox (text.x (), text.y (), text.width (), text.height (), width, height);
            case final DisplayCommand.Icon icon -> requireBox (icon.x (), icon.y (), icon.width (), icon.height (), width, height);
        }
    }


    private static DisplayCommand translate (final DisplayCommand command, final double offsetY)
    {
        return switch (command)
        {
            case final DisplayCommand.PushClip clip -> new DisplayCommand.PushClip (clip.x (), clip.y () + offsetY, clip.width (), clip.height ());
            case final DisplayCommand.PopClip ignored -> new DisplayCommand.PopClip ();
            case final DisplayCommand.Rectangle rectangle -> new DisplayCommand.Rectangle (rectangle.x (), rectangle.y () + offsetY, rectangle.width (), rectangle.height (), rectangle.color ());
            case final DisplayCommand.RoundedRectangle rectangle -> new DisplayCommand.RoundedRectangle (rectangle.x (), rectangle.y () + offsetY, rectangle.width (), rectangle.height (), rectangle.radius (), rectangle.color ());
            case final DisplayCommand.Circle circle -> new DisplayCommand.Circle (circle.centerX (), circle.centerY () + offsetY, circle.radius (), circle.color ());
            case final DisplayCommand.DottedArc arc -> new DisplayCommand.DottedArc (arc.centerX (), arc.centerY () + offsetY, arc.radius (), arc.startDegrees (), arc.sweepDegrees (), arc.steps (), arc.dotRadius (), arc.color ());
            case final DisplayCommand.TextAt text -> new DisplayCommand.TextAt (text.text (), text.x (), text.baselineY () + offsetY, text.color (), text.fontSize ());
            case final DisplayCommand.TextBox text -> new DisplayCommand.TextBox (text.text (), text.x (), text.y () + offsetY, text.width (), text.height (), text.alignment (), text.color (), text.maximumFontSize (), text.minimumFontSize (), text.fit ());
            case final DisplayCommand.Icon icon -> new DisplayCommand.Icon (icon.icon (), icon.x (), icon.y () + offsetY, icon.width (), icon.height (), icon.color ());
        };
    }


    private static void requirePoint (final double x, final double y, final double width, final double height)
    {
        if (x < 0 || y < 0 || x > width || y > height)
            throw new IllegalStateException ("display-region text anchor exceeds its owned viewport");
    }


    private static void requireBox (final double x, final double y, final double boxWidth, final double boxHeight, final double width, final double height)
    {
        if (x < 0 || y < 0 || x + boxWidth > width || y + boxHeight > height)
            throw new IllegalStateException ("display-region command exceeds its owned viewport");
    }
}
