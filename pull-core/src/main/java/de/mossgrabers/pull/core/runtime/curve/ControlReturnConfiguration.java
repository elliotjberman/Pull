// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.curve;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.events.Event;

/** YAML syntax and schema validation only. No files, host objects or return lifecycle. */
public final class ControlReturnConfiguration
{
    private ControlReturnConfiguration () {}

    /** Missing/empty configuration leaves Custom using the built-in linear curve. */
    public static ReturnCurve parse (final String yaml)
    {
        if (yaml.isBlank ()) return time -> time;
        try
        {
            final var settings = LoadSettings.builder ().setLabel ("config.yaml").setCodePointLimit (16384)
                .setMaxAliasesForCollections (0).setAllowDuplicateKeys (false).build ();
            int depth = 0;
            for (final Event event: new Parse (settings).parseString (yaml))
            {
                if (event.getEventId () == Event.ID.Alias)
                    throw new IllegalArgumentException ("YAML aliases are not supported");
                if (event.getEventId () == Event.ID.MappingStart || event.getEventId () == Event.ID.SequenceStart)
                    if (++depth > 8) throw new IllegalArgumentException ("YAML nesting exceeds 8 levels");
                if (event.getEventId () == Event.ID.MappingEnd || event.getEventId () == Event.ID.SequenceEnd) depth--;
            }
            final Object loaded = new Load (settings).loadFromString (yaml);
            if (loaded == null) return parse ("");
            final Map<?, ?> root = mapping (loaded, Set.of ("control_return"));
            if (root.isEmpty ()) return parse ("");
            final Map<?, ?> control = mapping (root.get ("control_return"), Set.of ("curve"));
            final Map<?, ?> curve = mapping (control.get ("curve"), Set.of ("interpolation", "keyframes"));
            final Object interpolation = curve.get ("interpolation");
            if (!"linear".equals (interpolation) && !"smooth".equals (interpolation))
                throw new IllegalArgumentException ("interpolation must be linear or smooth");
            if (!(curve.get ("keyframes") instanceof final List<?> points))
                throw new IllegalArgumentException ("keyframes must be a list of [time, remaining] pairs");
            final List<KeyframeCurve.Key> keys = new ArrayList<> ();
            for (final Object point: points)
            {
                if (!(point instanceof final List<?> pair) || pair.size () != 2 || !(pair.get (0) instanceof Number) || !(pair.get (1) instanceof Number))
                    throw new IllegalArgumentException ("each keyframe must be [time, remaining] numbers");
                keys.add (new KeyframeCurve.Key (((Number) pair.get (0)).doubleValue (), ((Number) pair.get (1)).doubleValue ()));
            }
            return new KeyframeCurve (keys, "smooth".equals (interpolation));
        }
        catch (final RuntimeException failure)
        {
            throw new IllegalArgumentException ("Invalid control return configuration: " + failure.getMessage (), failure);
        }
    }

    private static Map<?, ?> mapping (final Object value, final Set<String> allowed)
    {
        if (!(value instanceof final Map<?, ?> map)) throw new IllegalArgumentException ("expected mapping with keys " + allowed);
        if (!allowed.containsAll (map.keySet ())) throw new IllegalArgumentException ("unknown keys; expected " + allowed);
        return map;
    }
}
