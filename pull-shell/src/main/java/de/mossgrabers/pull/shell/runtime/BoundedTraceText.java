// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.DesiredInputRoutes;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;


/** Worker-side structural text which never allocates an unbounded value string. */
final class BoundedTraceText
{
    private static final int MAX_DEPTH = 64;

    private final StringBuilder text;
    private final int limit;
    private boolean truncated;


    BoundedTraceText (final int limit)
    {
        if (limit < 0)
            throw new IllegalArgumentException ("limit must not be negative");
        this.limit = limit;
        this.text = new StringBuilder (Math.min (limit, 64_000));
    }


    BoundedTraceText append (final String value)
    {
        for (int index = 0; index < value.length () && !this.truncated; index++)
            this.append (value.charAt (index));
        return this;
    }


    BoundedTraceText append (final long value)
    {
        return this.append (Long.toString (value));
    }


    BoundedTraceText append (final char value)
    {
        if (this.text.length () < this.limit)
            this.text.append (value);
        else
            this.truncated = true;
        return this;
    }


    BoundedTraceText appendValue (final Object value)
    {
        this.appendValue (value, 0);
        return this;
    }


    boolean truncated ()
    {
        return this.truncated;
    }


    @Override
    public String toString ()
    {
        return this.text.toString ();
    }


    private void appendValue (final Object value, final int depth)
    {
        if (this.truncated)
            return;
        if (value == null)
        {
            this.append ("null");
            return;
        }
        if (depth >= MAX_DEPTH)
        {
            this.append ("<depth-limit>");
            return;
        }
        if (value instanceof final CharSequence characters)
        {
            this.appendSanitized (characters);
            return;
        }
        if (value instanceof final Character character)
        {
            this.appendSanitized (character.charValue ());
            return;
        }
        if (value instanceof final Enum<?> enumeration)
        {
            this.append (enumeration.name ());
            return;
        }
        if (value instanceof Number || value instanceof Boolean)
        {
            this.append (value.toString ());
            return;
        }
        if (value instanceof final Optional<?> optional)
        {
            this.append ("Optional[");
            optional.ifPresent (item -> this.appendValue (item, depth + 1));
            this.append (']');
            return;
        }
        if (value instanceof final Map<?, ?> map)
        {
            this.appendMap (map, depth);
            return;
        }
        if (value instanceof final DesiredInputRoutes routes)
        {
            this.append ("DesiredInputRoutes[routes=");
            this.appendValue (routes.routes (), depth + 1);
            this.append (']');
            return;
        }
        if (value instanceof final Iterable<?> items)
        {
            this.appendIterable (items, depth);
            return;
        }

        final Class<?> type = value.getClass ();
        if (type.isArray ())
        {
            this.appendArray (value, depth);
            return;
        }
        if (type.isRecord () && type.getName ().startsWith ("de.mossgrabers.pull.core.api."))
        {
            this.appendRecord (value, type, depth);
            return;
        }
        this.append ('<').append (type.getName ()).append ('>');
    }


    private void appendRecord (final Object value, final Class<?> type, final int depth)
    {
        this.append (type.getSimpleName ()).append ('[');
        final RecordComponent [] components = type.getRecordComponents ();
        for (int index = 0; index < components.length && !this.truncated; index++)
        {
            if (index > 0)
                this.append (", ");
            final RecordComponent component = components[index];
            this.append (component.getName ()).append ('=');
            try
            {
                this.appendValue (component.getAccessor ().invoke (value), depth + 1);
            }
            catch (final ReflectiveOperationException | RuntimeException ignored)
            {
                this.append ("<unavailable>");
            }
        }
        this.append (']');
    }


    private void appendMap (final Map<?, ?> map, final int depth)
    {
        this.append ('{');
        boolean first = true;
        for (final Map.Entry<?, ?> entry: map.entrySet ())
        {
            if (this.truncated)
                break;
            if (!first)
                this.append (", ");
            first = false;
            this.appendValue (entry.getKey (), depth + 1);
            this.append ('=');
            this.appendValue (entry.getValue (), depth + 1);
        }
        this.append ('}');
    }


    private void appendIterable (final Iterable<?> items, final int depth)
    {
        this.append ('[');
        boolean first = true;
        for (final Object item: items)
        {
            if (this.truncated)
                break;
            if (!first)
                this.append (", ");
            first = false;
            this.appendValue (item, depth + 1);
        }
        this.append (']');
    }


    private void appendArray (final Object array, final int depth)
    {
        this.append ('[');
        final int length = Array.getLength (array);
        for (int index = 0; index < length && !this.truncated; index++)
        {
            if (index > 0)
                this.append (", ");
            this.appendValue (Array.get (array, index), depth + 1);
        }
        this.append (']');
    }


    private void appendSanitized (final CharSequence value)
    {
        for (int index = 0; index < value.length () && !this.truncated; index++)
            this.appendSanitized (value.charAt (index));
    }


    private void appendSanitized (final char value)
    {
        this.append (value == '\t' || value == '\r' || value == '\n' ? ' ' : value);
    }
}
