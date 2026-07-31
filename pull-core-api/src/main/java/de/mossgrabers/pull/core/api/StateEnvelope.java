// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * Opaque, value-semantic checkpoint bytes owned by a core implementation.
 */
public final class StateEnvelope
{
    private final String schema;
    private final int version;
    private final byte [] payload;


    /**
     * Constructor.
     *
     * @param schema The schema identifier
     * @param version The schema version
     * @param payload The opaque payload
     */
    public StateEnvelope (final String schema, final int version, final byte [] payload)
    {
        this.schema = Objects.requireNonNull (schema, "schema");
        if (schema.isBlank ())
            throw new IllegalArgumentException ("schema must not be blank");
        if (version <= 0)
            throw new IllegalArgumentException ("version must be positive");

        this.version = version;
        this.payload = Objects.requireNonNull (payload, "payload").clone ();
    }


    /**
     * Get the schema identifier.
     *
     * @return The schema identifier
     */
    public String schema ()
    {
        return this.schema;
    }


    /**
     * Get the schema version.
     *
     * @return The schema version
     */
    public int version ()
    {
        return this.version;
    }


    /**
     * Get a defensive copy of the payload.
     *
     * @return The payload
     */
    public byte [] payload ()
    {
        return this.payload.clone ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object object)
    {
        if (this == object)
            return true;
        if (!(object instanceof final StateEnvelope other))
            return false;
        return this.version == other.version && this.schema.equals (other.schema) && Arrays.equals (this.payload, other.payload);
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        int result = Objects.hash (this.schema, Integer.valueOf (this.version));
        result = 31 * result + Arrays.hashCode (this.payload);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "StateEnvelope[schema=" + this.schema + ", version=" + this.version + ", payloadSize=" + this.payload.length + "]";
    }
}
