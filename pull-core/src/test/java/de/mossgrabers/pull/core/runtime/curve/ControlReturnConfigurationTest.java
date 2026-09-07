package de.mossgrabers.pull.core.runtime.curve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ControlReturnConfigurationTest
{
    private static String yaml (final String interpolation, final String points)
    {
        return "control_return:\n  curve:\n    interpolation: " + interpolation + "\n    keyframes: " + points;
    }

    @Test
    void springCrossesBaselineAndSettlesWithoutExtraOvershoot ()
    {
        final ReturnCurve curve = ControlReturnConfiguration.parse (yaml ("smooth", "[[0, 1], [0.35, -0.25], [0.6, 0.1], [0.8, -0.03], [1, 0]]"));
        final double [] times = {0, .35, .6, .8, 1};
        final double [] values = {0, 1.25, .9, 1.03, 1};
        for (int i = 0; i < times.length; i++) assertEquals (values[i], curve.apply (times[i]), 1e-12);
        for (int i = 0; i < times.length - 1; i++)
            for (int sample = 0; sample <= 100; sample++)
            {
                final double value = curve.apply (times[i] + (times[i + 1] - times[i]) * sample / 100);
                assertTrue (value >= Math.min (values[i], values[i + 1]) - 1e-12);
                assertTrue (value <= Math.max (values[i], values[i + 1]) + 1e-12);
            }
        assertEquals (1, curve.apply (2));
    }

    @Test
    void smoothUnevenKeysPreserveMonotonicSegmentsAndLinearRemainsLinear ()
    {
        final String points = "[[0, 1], [0.1, 0.95], [0.8, 0.2], [1, 0]]";
        final ReturnCurve smooth = ControlReturnConfiguration.parse (yaml ("smooth", points));
        double previous = 0;
        for (int i = 0; i <= 1000; i++)
        {
            final double value = smooth.apply (i / 1000.0);
            assertTrue (value >= previous && value <= 1);
            previous = value;
        }
        final ReturnCurve linear = ControlReturnConfiguration.parse (yaml ("linear", "[[0, 1], [1, 0]]"));
        assertEquals (.25, linear.apply (.25));
        assertEquals (.5, ControlReturnConfiguration.parse ("").apply (.5));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[[0, 1], [0.5, 0], [0.5, -1], [1, 0]]", "[[0, 1], [0.8, 0], [0.2, 0], [1, 0]]",
        "[[0, 0], [1, 0]]", "[[0, 1], [1, 1]]", "[[0, 1], [1, .nan]]",
        "[[0, 1], [0.5, 5], [1, 0]]", "[[0, 1, 2], [1, 0]]", "[[0, 1]]",
        "[[0, 1], [0.00000000000001, 0.5], [1, 0]]", "[[0, 1], [1, '0']]"
    })
    void rejectsInvalidKeyframes (final String points)
    {
        assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (yaml ("smooth", points)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong: 1", "control_return: {}", "control_return: {curve: {interpolation: math}}",
        "---\n{}\n---\n{}", "x: !java/object {}"})
    void rejectsMalformedOrUnsupportedDocuments (final String yaml)
    {
        assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (yaml));
    }

    @Test
    void boundsInputAndNestingAndKeyCount ()
    {
        assertTrue (assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (yaml ("smooth", "[".repeat (20) + "0" + "]".repeat (20)))).getMessage ().contains ("YAML nesting exceeds 8 levels"));
        final String valid = yaml ("linear", "[[0, 1], [1, 0]]");
        assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (valid + "\n# " + "a".repeat (17000)));
        assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (valid.replace ("interpolation: linear", "interpolation: smooth\n    interpolation: linear")));
        assertTrue (assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (yaml ("linear", "[[&zero 0, &one 1], [*one, *zero]]"))).getMessage ().contains ("YAML aliases are not supported"));
        final String points = java.util.stream.IntStream.rangeClosed (0, 32).mapToObj (i -> "[" + i / 32.0 + ", " + (1 - i / 32.0) + "]").collect (java.util.stream.Collectors.joining (",", "[", "]"));
        assertThrows (IllegalArgumentException.class, () -> ControlReturnConfiguration.parse (yaml ("smooth", points)));
    }
}
