// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.SettableStringValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;


/**
 * Persists causal ownership of native Drum Pitch helpers in Bitwig's document state.
 *
 * <p>A native device insertion has no script-owned name or identifier. The stable shell therefore
 * records the track UUID and the subscribed helper/Drum Machine positions only after it observes
 * the exact topology transition caused by its own insertion request. The document value is
 * authoritative read-back: {@link #save(Ownership)} only submits a request.</p>
 */
interface DrumPitchOwnershipStore
{
    int CONFIGURATION_VERSION = 1;
    int MAX_RECORDS = 32;


    Snapshot snapshot ();


    void save (Ownership ownership);


    /** One immutable subscribed store sample. */
    record Snapshot (boolean loaded, boolean valid, List<Ownership> ownerships)
    {
        public Snapshot
        {
            ownerships = List.copyOf (Objects.requireNonNull (ownerships, "ownerships"));
            if (ownerships.size () > MAX_RECORDS)
                throw new IllegalArgumentException ("ownership registry exceeds its bounded capacity");
        }


        Optional<Ownership> forTrack (final String trackId)
        {
            Ownership match = null;
            for (final Ownership ownership: this.ownerships)
            {
                if (!ownership.trackId ().equals (trackId))
                    continue;
                if (match != null)
                    return Optional.empty ();
                match = ownership;
            }
            return Optional.ofNullable (match);
        }


        static Snapshot loading ()
        {
            return new Snapshot (false, true, List.of ());
        }


        static Snapshot loadedEmpty ()
        {
            return new Snapshot (true, true, List.of ());
        }
    }


    /** One durable managed-helper identity. */
    record Ownership (String trackId, int helperPosition, int drumPosition, int configurationVersion)
    {
        public Ownership
        {
            trackId = normalizeTrackId (trackId);
            if (helperPosition < 0 || drumPosition <= helperPosition)
                throw new IllegalArgumentException ("managed helper must precede its Drum Machine");
            if (configurationVersion != CONFIGURATION_VERSION)
                throw new IllegalArgumentException ("unsupported Drum Pitch configuration version");
        }
    }


    /** Document-state-backed API-21 implementation. */
    final class Document implements DrumPitchOwnershipStore
    {
        private static final String EMPTY = "v1";
        private static final String LABEL = "Managed Drum Pitch Helpers";
        private static final String CATEGORY = "Pull (internal)";
        private static final int MAX_CHARACTERS = 4096;

        private final Value value;
        private String observed = EMPTY;
        private String pending;
        private boolean loaded;


        Document (final ControllerHost host)
        {
            this (new BitwigValue (Objects.requireNonNull (host, "host").getDocumentState ().getStringSetting (LABEL, CATEGORY, MAX_CHARACTERS, EMPTY)));
        }


        Document (final Value value)
        {
            this.value = Objects.requireNonNull (value, "value");
            this.value.addObserver (text ->
            {
                this.observed = text == null ? "" : text;
                this.pending = null;
                this.loaded = true;
            });
        }


        /** {@inheritDoc} */
        @Override
        public Snapshot snapshot ()
        {
            return this.loaded ? decode (this.observed) : Snapshot.loading ();
        }


        /** {@inheritDoc} */
        @Override
        public void save (final Ownership ownership)
        {
            final Ownership requested = Objects.requireNonNull (ownership, "ownership");
            final Snapshot current = this.snapshot ();
            if (!current.loaded () || !current.valid ())
                throw new IllegalStateException ("Drum Pitch ownership registry is not available");

            final Map<String, Ownership> updated = new LinkedHashMap<> ();
            for (final Ownership existing: current.ownerships ())
                updated.put (existing.trackId (), existing);
            if (!updated.containsKey (requested.trackId ()) && updated.size () >= MAX_RECORDS)
                throw new IllegalStateException ("Drum Pitch ownership registry is full");
            updated.put (requested.trackId (), requested);
            final String encoded = encode (updated.values ());
            if (encoded.equals (this.pending))
                return;
            if (this.pending != null)
                throw new IllegalStateException ("another Drum Pitch ownership write is awaiting read-back");

            this.pending = encoded;
            try
            {
                this.value.set (encoded); // Request only; the observer acknowledges it.
            }
            catch (final RuntimeException failure)
            {
                this.pending = null;
                throw failure;
            }
        }


        static Snapshot decode (final String encoded)
        {
            if (encoded == null || encoded.isBlank ())
                return new Snapshot (true, false, List.of ());
            final String [] sections = encoded.split ("\\|", -1);
            if (sections.length < 1 || !EMPTY.equals (sections[0]) || sections.length - 1 > MAX_RECORDS)
                return new Snapshot (true, false, List.of ());

            final Map<String, Ownership> ownerships = new LinkedHashMap<> ();
            try
            {
                for (int index = 1; index < sections.length; index++)
                {
                    if (sections[index].isEmpty ())
                        throw new IllegalArgumentException ("empty ownership record");
                    final String [] fields = sections[index].split (",", -1);
                    if (fields.length != 4)
                        throw new IllegalArgumentException ("invalid ownership record");
                    final Ownership ownership = new Ownership (
                        fields[0],
                        Integer.parseInt (fields[1]),
                        Integer.parseInt (fields[2]),
                        Integer.parseInt (fields[3]));
                    if (ownerships.putIfAbsent (ownership.trackId (), ownership) != null)
                        throw new IllegalArgumentException ("duplicate ownership record");
                }
            }
            catch (final IllegalArgumentException failure)
            {
                return new Snapshot (true, false, List.of ());
            }
            return new Snapshot (true, true, List.copyOf (ownerships.values ()));
        }


        static String encode (final Iterable<Ownership> ownerships)
        {
            final List<Ownership> sorted = new ArrayList<> ();
            ownerships.forEach (sorted::add);
            if (sorted.size () > MAX_RECORDS)
                throw new IllegalArgumentException ("ownership registry exceeds its bounded capacity");
            sorted.sort (Comparator.comparing (Ownership::trackId));

            final StringBuilder result = new StringBuilder (EMPTY);
            final Map<String, Ownership> unique = new LinkedHashMap<> ();
            for (final Ownership ownership: sorted)
            {
                final Ownership checked = Objects.requireNonNull (ownership, "ownership");
                if (unique.putIfAbsent (checked.trackId (), checked) != null)
                    throw new IllegalArgumentException ("duplicate ownership record");
                result.append ('|')
                    .append (checked.trackId ())
                    .append (',').append (checked.helperPosition ())
                    .append (',').append (checked.drumPosition ())
                    .append (',').append (checked.configurationVersion ());
            }
            if (result.length () > MAX_CHARACTERS)
                throw new IllegalArgumentException ("encoded ownership registry is too large");
            return result.toString ();
        }


        /** Minimal observer/request seam for deterministic document-state tests. */
        interface Value
        {
            void addObserver (Consumer<String> observer);


            void set (String text);
        }


        private record BitwigValue (SettableStringValue value) implements Value
        {
            private BitwigValue
            {
                value = Objects.requireNonNull (value, "value");
                value.markInterested ();
            }


            /** {@inheritDoc} */
            @Override
            public void addObserver (final Consumer<String> observer)
            {
                this.value.addValueObserver (observer::accept);
            }


            /** {@inheritDoc} */
            @Override
            public void set (final String text)
            {
                this.value.set (text);
            }
        }
    }


    /** Loaded no-persistence seam for legacy branded-helper tests. */
    enum Empty implements DrumPitchOwnershipStore
    {
        INSTANCE;


        /** {@inheritDoc} */
        @Override
        public Snapshot snapshot ()
        {
            return Snapshot.loadedEmpty ();
        }


        /** {@inheritDoc} */
        @Override
        public void save (final Ownership ownership)
        {
            throw new IllegalStateException ("Ownership persistence is unavailable in this test seam");
        }
    }


    private static String normalizeTrackId (final String trackId)
    {
        return UUID.fromString (Objects.requireNonNull (trackId, "trackId")).toString ();
    }
}
