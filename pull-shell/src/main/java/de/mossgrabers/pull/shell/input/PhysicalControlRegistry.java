// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, explicitly bounded registry of physical controller inputs.
 *
 * @param <C> Control key type
 */
public final class PhysicalControlRegistry<C>
{
    private final int capacity;
    private final Map<PhysicalInputAddress<C>, Boolean> inputs;
    private final Map<C, Map<InputKind, PhysicalInputAddress<C>>> addresses;


    private PhysicalControlRegistry (final int capacity, final Map<PhysicalInputAddress<C>, Boolean> inputs)
    {
        this.capacity = capacity;
        this.inputs = Collections.unmodifiableMap (new LinkedHashMap<> (inputs));
        final Map<C, EnumMap<InputKind, PhysicalInputAddress<C>>> indexed = new LinkedHashMap<> ();
        for (final PhysicalInputAddress<C> input: inputs.keySet ())
            indexed.computeIfAbsent (input.control (), ignored -> new EnumMap<> (InputKind.class)).put (input.kind (), input);
        final Map<C, Map<InputKind, PhysicalInputAddress<C>>> copied = new LinkedHashMap<> ();
        indexed.forEach ( (control, kinds) -> copied.put (control, Map.copyOf (kinds)));
        this.addresses = Map.copyOf (copied);
    }


    /**
     * Start a registry builder with a hard maximum size.
     *
     * @param capacity Maximum number of physical inputs in this controller canopy
     * @param <C> Control key type
     * @return The builder
     */
    public static <C> Builder<C> builder (final int capacity)
    {
        return new Builder<> (capacity);
    }


    /**
     * Get the registered kind, rejecting controls outside the fixed canopy.
     *
     * @param control Control key
     * @return Registered input kind
     */
    public PhysicalInputAddress<C> require (final C control, final InputKind kind)
    {
        final C checkedControl = Objects.requireNonNull (control, "control");
        final InputKind checkedKind = Objects.requireNonNull (kind, "kind");
        final Map<InputKind, PhysicalInputAddress<C>> kinds = this.addresses.get (checkedControl);
        final PhysicalInputAddress<C> input = kinds == null ? null : kinds.get (checkedKind);
        if (input == null)
            throw new IllegalArgumentException ("Unregistered physical input: " + new PhysicalInputAddress<> (checkedControl, checkedKind));
        return input;
    }


    /**
     * Test whether a control is registered.
     *
     * @param control Control key
     * @param kind Input kind
     * @return True if registered
     */
    public boolean contains (final C control, final InputKind kind)
    {
        if (control == null || kind == null)
            return false;
        final Map<InputKind, PhysicalInputAddress<C>> kinds = this.addresses.get (control);
        return kinds != null && kinds.containsKey (kind);
    }


    /**
     * Get the immutable registered input-address set in registration order.
     *
     * @return Registered control and kind pairs
     */
    public Set<PhysicalInputAddress<C>> inputs ()
    {
        return this.inputs.keySet ();
    }


    /**
     * Get the current number of registered controls.
     *
     * @return Registry size
     */
    public int size ()
    {
        return this.inputs.size ();
    }


    /**
     * Get the hard registry capacity.
     *
     * @return Maximum size
     */
    public int capacity ()
    {
        return this.capacity;
    }


    /**
     * Builder which becomes immutable at controller initialization.
     *
     * @param <C> Control key type
     */
    public static final class Builder<C>
    {
        private final int capacity;
        private final Map<PhysicalInputAddress<C>, Boolean> inputs = new LinkedHashMap<> ();


        private Builder (final int capacity)
        {
            if (capacity <= 0)
                throw new IllegalArgumentException ("capacity must be positive");
            this.capacity = capacity;
        }


        /**
         * Register one physical control. Duplicate registrations are rejected even when the kind
         * is identical, since they usually mean the hardware seam was installed twice.
         *
         * @param control Control key
         * @param kind Input kind
         * @return This builder
         */
        public Builder<C> register (final C control, final InputKind kind)
        {
            final PhysicalInputAddress<C> input = new PhysicalInputAddress<> (control, kind);
            if (this.inputs.containsKey (input))
                throw new IllegalArgumentException ("Physical input is already registered: " + input);
            if (this.inputs.size () == this.capacity)
                throw new IllegalStateException ("Physical input capacity exceeded: " + this.capacity);
            this.inputs.put (input, Boolean.TRUE);
            return this;
        }


        /**
         * Freeze the registry.
         *
         * @return Immutable registry
         */
        public PhysicalControlRegistry<C> build ()
        {
            return new PhysicalControlRegistry<> (this.capacity, this.inputs);
        }
    }
}
