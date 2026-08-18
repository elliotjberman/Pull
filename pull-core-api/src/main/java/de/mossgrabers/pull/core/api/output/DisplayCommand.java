// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import java.util.Objects;


/** One hardware-independent primitive in a reloadable controller display scene. */
public sealed interface DisplayCommand permits DisplayCommand.PushClip, DisplayCommand.PopClip, DisplayCommand.Rectangle, DisplayCommand.RoundedRectangle, DisplayCommand.Circle, DisplayCommand.DottedArc, DisplayCommand.TextAt, DisplayCommand.TextBox, DisplayCommand.Icon
{
    double MAX_ABSOLUTE_ANGLE = 360000.0;

    /** Begin one non-nested rectangular clip scope. */
    record PushClip (double x, double y, double width, double height) implements DisplayCommand
    {
        /** Validate the bounded clip. */
        public PushClip
        {
            requirePosition (x, "x");
            requirePosition (y, "y");
            requireSize (width, "width");
            requireSize (height, "height");
        }
    }


    /** End the active rectangular clip scope. */
    record PopClip () implements DisplayCommand
    {
    }


    /** Filled axis-aligned rectangle. */
    record Rectangle (double x, double y, double width, double height, RgbColor color) implements DisplayCommand
    {
        /** Validate the bounded primitive. */
        public Rectangle
        {
            requirePosition (x, "x");
            requirePosition (y, "y");
            requireSize (width, "width");
            requireSize (height, "height");
            color = Objects.requireNonNull (color, "color");
        }
    }


    /** Filled rounded rectangle. */
    record RoundedRectangle (double x, double y, double width, double height, double radius, RgbColor color) implements DisplayCommand
    {
        /** Validate the bounded primitive. */
        public RoundedRectangle
        {
            requirePosition (x, "x");
            requirePosition (y, "y");
            requireSize (width, "width");
            requireSize (height, "height");
            requireSize (radius, "radius");
            color = Objects.requireNonNull (color, "color");
        }
    }


    /** Filled circle. */
    record Circle (double centerX, double centerY, double radius, RgbColor color) implements DisplayCommand
    {
        /** Validate the bounded primitive. */
        public Circle
        {
            requirePosition (centerX, "centerX");
            requirePosition (centerY, "centerY");
            requireSize (radius, "radius");
            color = Objects.requireNonNull (color, "color");
        }
    }


    /** Evenly sampled circular arc made from filled dots. */
    record DottedArc (double centerX, double centerY, double radius, double startDegrees, double sweepDegrees, int steps, double dotRadius, RgbColor color) implements DisplayCommand
    {
        /** Validate the bounded primitive. */
        public DottedArc
        {
            requirePosition (centerX, "centerX");
            requirePosition (centerY, "centerY");
            requireSize (radius, "radius");
            requireFinite (startDegrees, "startDegrees");
            requireFinite (sweepDegrees, "sweepDegrees");
            if (Math.abs (startDegrees) > MAX_ABSOLUTE_ANGLE || Math.abs (sweepDegrees) > MAX_ABSOLUTE_ANGLE)
                throw new IllegalArgumentException ("arc angles must be between -360000 and 360000 degrees");
            if (steps < 2 || steps > 512)
                throw new IllegalArgumentException ("steps must be between 2 and 512");
            requireSize (dotRadius, "dotRadius");
            color = Objects.requireNonNull (color, "color");
        }
    }


    /** Text positioned by its baseline. */
    record TextAt (String text, double x, double baselineY, RgbColor color, double fontSize) implements DisplayCommand
    {
        /** Validate and normalize text. */
        public TextAt
        {
            text = requireText (text);
            requirePosition (x, "x");
            requirePosition (baselineY, "baselineY");
            color = Objects.requireNonNull (color, "color");
            requireFontSize (fontSize, "fontSize");
        }
    }


    /** Text clipped and optionally fitted into a rectangle. */
    record TextBox (String text, double x, double y, double width, double height, DisplayTextAlignment alignment, RgbColor color, double maximumFontSize, double minimumFontSize, DisplayTextFit fit) implements DisplayCommand
    {
        /** Validate and normalize text. */
        public TextBox
        {
            text = requireText (text);
            requirePosition (x, "x");
            requirePosition (y, "y");
            requireSize (width, "width");
            requireSize (height, "height");
            alignment = Objects.requireNonNull (alignment, "alignment");
            color = Objects.requireNonNull (color, "color");
            requireFontSize (maximumFontSize, "maximumFontSize");
            requireFontSize (minimumFontSize, "minimumFontSize");
            if (minimumFontSize > maximumFontSize)
                throw new IllegalArgumentException ("minimumFontSize must not exceed maximumFontSize");
            fit = Objects.requireNonNull (fit, "fit");
        }
    }


    /** Masked stable icon centered inside a rectangle. */
    record Icon (DisplayIcon icon, double x, double y, double width, double height, RgbColor color) implements DisplayCommand
    {
        /** Validate the bounded primitive. */
        public Icon
        {
            icon = Objects.requireNonNull (icon, "icon");
            requirePosition (x, "x");
            requirePosition (y, "y");
            requireSize (width, "width");
            requireSize (height, "height");
            color = Objects.requireNonNull (color, "color");
        }
    }


    private static String requireText (final String text)
    {
        final String value = Objects.requireNonNullElse (text, "");
        if (value.length () > 1024)
            throw new IllegalArgumentException ("display text must not exceed 1024 UTF-16 code units");
        return value;
    }


    private static void requirePosition (final double value, final String name)
    {
        requireFinite (value, name);
        if (value < -8192 || value > 8192)
            throw new IllegalArgumentException (name + " must be between -8192 and 8192");
    }


    private static void requireSize (final double value, final String name)
    {
        requireFinite (value, name);
        if (value < 0 || value > 4096)
            throw new IllegalArgumentException (name + " must be between 0 and 4096");
    }


    private static void requireFontSize (final double value, final String name)
    {
        requireFinite (value, name);
        if (value <= 0 || value > 512)
            throw new IllegalArgumentException (name + " must be greater than zero and at most 512");
    }


    private static void requireFinite (final double value, final String name)
    {
        if (!Double.isFinite (value))
            throw new IllegalArgumentException (name + " must be finite");
    }
}
