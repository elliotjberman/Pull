// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Setting;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Consumer;


/**
 * Bounded exact clip/transport phase anchors retained across core, selection, and shell restarts.
 *
 * <p>The cache is not a playback-state source. A caller may use an entry only after the current
 * project, exact launcher target, clip geometry, launcher playing state, and transport playing
 * state are all read back again from Bitwig. Observed stop and transport-discontinuity edges remove
 * an entry. The global hidden preference avoids dirtying the user's project.</p>
 */
final class ClipPlaybackPhaseStore
{
    private static final String FORMAT = "v1";
    private static final String SETTING_LABEL = "Clip playback phase anchors";
    private static final String SETTING_CATEGORY = "Pull Internal";
    private static final int MAX_ENTRIES = 16;
    private static final int MAX_SERIALIZED_CHARACTERS = 8192;
    private static final double EPSILON = 1.0e-6;

    private final LinkedHashMap<Key, Phase> phases = new LinkedHashMap<> (MAX_ENTRIES, 0.75f, true);
    private Consumer<String> persistence = ignored -> {
        // Unit tests and disconnected runtimes keep the same bounded in-memory behavior.
    };
    private boolean persistenceInstalled;
    private String transportProjectIdentity = "";
    private boolean hasTransportSample;
    private boolean transportPlaying;
    private double transportPosition;


    /** Attach the preference resource during Bitwig's driver-initialization callback. */
    void installPersistence (final ControllerHost host)
    {
        if (this.persistenceInstalled)
            throw new IllegalStateException ("Clip playback phase persistence is already installed");
        final SettableStringValue setting = Objects.requireNonNull (host, "host").getPreferences ().getStringSetting (SETTING_LABEL, SETTING_CATEGORY, MAX_SERIALIZED_CHARACTERS, FORMAT);
        setting.markInterested ();
        if (setting instanceof final Setting hiddenSetting)
            hiddenSetting.hide ();

        this.persistence = setting::set;
        setting.addValueObserver (this::replaceFromSerialized);
        this.persistenceInstalled = true;
    }


    /** Restore an exact phase only when every durable target and geometry fence still agrees. */
    OptionalDouble restore (final String projectIdentity, final String targetIdentity, final double transportPosition, final double playStart, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        final Phase phase = this.phases.get (new Key (projectIdentity, targetIdentity));
        if (phase == null || !phase.sameGeometry (playStart, loopStart, loopLength, loopEnabled))
            return OptionalDouble.empty ();

        double position = transportPosition - phase.transportOffset;
        if (loopEnabled && position >= loopStart + loopLength)
            position = loopStart + positiveRemainder (position - loopStart, loopLength);
        if (!Double.isFinite (position) || position < playStart - EPSILON)
            return OptionalDouble.empty ();
        return OptionalDouble.of (Math.max (playStart, position));
    }


    /** Retain the exact offset established from authoritative launch or previously retained phase. */
    void remember (final String projectIdentity, final String targetIdentity, final double transportPosition, final double clipPosition, final double playStart, final double loopStart, final double loopLength, final boolean loopEnabled)
    {
        if (projectIdentity == null || projectIdentity.isBlank () || targetIdentity == null || targetIdentity.isBlank () || !Double.isFinite (transportPosition) || !Double.isFinite (clipPosition))
            return;
        final Key key = new Key (projectIdentity, targetIdentity);
        final Phase replacement = new Phase (playStart, loopStart, loopLength, loopEnabled, transportPosition - clipPosition);
        if (!replacement.valid ())
            return;
        final Phase previous = this.phases.get (key);
        if (replacement.nearlyEquals (previous))
            return;
        this.phases.put (key, replacement);
        this.trim ();
        this.persistence.accept (this.serialized ());
    }


    /** Invalidate one exact target after authoritative stop or clock discontinuity. */
    void invalidate (final String projectIdentity, final String targetIdentity)
    {
        if (this.phases.remove (new Key (projectIdentity, targetIdentity)) != null)
            this.persistence.accept (this.serialized ());
    }


    /** Keep retained offsets coherent across authoritative arranger-loop wraps. */
    void observeTransport (final String projectIdentity, final boolean playing, final double position, final boolean loopEnabled, final double loopStart, final double loopEnd)
    {
        if (projectIdentity == null || projectIdentity.isBlank () || !Double.isFinite (position))
            return;
        if (!projectIdentity.equals (this.transportProjectIdentity))
        {
            this.transportProjectIdentity = projectIdentity;
            this.hasTransportSample = false;
        }

        if (this.hasTransportSample && playing && this.transportPlaying && position < this.transportPosition - EPSILON)
        {
            final boolean loopWrap = loopEnabled && Double.isFinite (loopStart) && Double.isFinite (loopEnd) && loopEnd > loopStart && this.transportPosition >= loopEnd - 1 && this.transportPosition <= loopEnd + EPSILON && position >= loopStart - EPSILON && position <= loopStart + 1;
            if (loopWrap)
                this.shiftProjectOffsets (projectIdentity, -(loopEnd - loopStart));
            else
                this.invalidateProject (projectIdentity);
        }
        this.hasTransportSample = true;
        this.transportPlaying = playing;
        this.transportPosition = position;
    }


    String serialized ()
    {
        final StringBuilder text = new StringBuilder (FORMAT);
        for (final Map.Entry<Key, Phase> entry: this.phases.entrySet ())
        {
            final Key key = entry.getKey ();
            final Phase phase = entry.getValue ();
            text.append ('\n')
                .append (encode (key.projectIdentity ())).append ('\t')
                .append (encode (key.targetIdentity ())).append ('\t')
                .append (Double.toHexString (phase.playStart)).append ('\t')
                .append (Double.toHexString (phase.loopStart)).append ('\t')
                .append (Double.toHexString (phase.loopLength)).append ('\t')
                .append (phase.loopEnabled ? '1' : '0').append ('\t')
                .append (Double.toHexString (phase.transportOffset));
        }
        return text.toString ();
    }


    void replaceFromSerialized (final String serialized)
    {
        final LinkedHashMap<Key, Phase> parsed = new LinkedHashMap<> (MAX_ENTRIES, 0.75f, true);
        if (serialized != null)
        {
            final String [] lines = serialized.split ("\\n", MAX_ENTRIES + 2);
            if (lines.length > 0 && FORMAT.equals (lines[0]))
            {
                for (int index = 1; index < lines.length && parsed.size () < MAX_ENTRIES; index++)
                {
                    final String [] fields = lines[index].split ("\\t", -1);
                    if (fields.length != 7)
                        continue;
                    try
                    {
                        final String project = decode (fields[0]);
                        final String target = decode (fields[1]);
                        final Phase phase = new Phase (
                            Double.valueOf (fields[2]).doubleValue (),
                            Double.valueOf (fields[3]).doubleValue (),
                            Double.valueOf (fields[4]).doubleValue (),
                            "1".equals (fields[5]),
                            Double.valueOf (fields[6]).doubleValue ());
                        if (!project.isBlank () && !target.isBlank () && phase.valid ())
                            parsed.put (new Key (project, target), phase);
                    }
                    catch (final IllegalArgumentException ignored)
                    {
                        // One malformed bounded entry cannot poison the remaining cache.
                    }
                }
            }
        }
        this.phases.clear ();
        this.phases.putAll (parsed);
    }


    private void trim ()
    {
        while (this.phases.size () > MAX_ENTRIES || this.serialized ().length () > MAX_SERIALIZED_CHARACTERS)
        {
            final Iterator<Key> eldest = this.phases.keySet ().iterator ();
            if (!eldest.hasNext ())
                return;
            eldest.next ();
            eldest.remove ();
        }
    }


    private void shiftProjectOffsets (final String projectIdentity, final double delta)
    {
        boolean changed = false;
        for (final Map.Entry<Key, Phase> entry: this.phases.entrySet ())
        {
            if (!entry.getKey ().projectIdentity ().equals (projectIdentity))
                continue;
            entry.setValue (entry.getValue ().shiftTransportOffset (delta));
            changed = true;
        }
        if (changed)
            this.persistence.accept (this.serialized ());
    }


    private void invalidateProject (final String projectIdentity)
    {
        final boolean changed = this.phases.keySet ().removeIf (key -> key.projectIdentity ().equals (projectIdentity));
        if (changed)
            this.persistence.accept (this.serialized ());
    }


    private static String encode (final String value)
    {
        return Base64.getUrlEncoder ().withoutPadding ().encodeToString (value.getBytes (StandardCharsets.UTF_8));
    }


    private static String decode (final String value)
    {
        return new String (Base64.getUrlDecoder ().decode (value), StandardCharsets.UTF_8);
    }


    private static double positiveRemainder (final double value, final double divisor)
    {
        final double remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }


    private record Key (String projectIdentity, String targetIdentity)
    {
        private Key
        {
            projectIdentity = Objects.requireNonNullElse (projectIdentity, "");
            targetIdentity = Objects.requireNonNullElse (targetIdentity, "");
        }
    }


    private record Phase (double playStart, double loopStart, double loopLength, boolean loopEnabled, double transportOffset)
    {
        private boolean valid ()
        {
            return Double.isFinite (this.playStart) && Double.isFinite (this.loopStart) && Double.isFinite (this.loopLength) && this.loopLength > 0 && Double.isFinite (this.transportOffset);
        }


        private boolean sameGeometry (final double playStart, final double loopStart, final double loopLength, final boolean loopEnabled)
        {
            return this.loopEnabled == loopEnabled && near (this.playStart, playStart) && near (this.loopStart, loopStart) && near (this.loopLength, loopLength);
        }


        private boolean nearlyEquals (final Phase other)
        {
            return other != null && this.sameGeometry (other.playStart, other.loopStart, other.loopLength, other.loopEnabled) && near (this.transportOffset, other.transportOffset);
        }


        private Phase shiftTransportOffset (final double delta)
        {
            return new Phase (this.playStart, this.loopStart, this.loopLength, this.loopEnabled, this.transportOffset + delta);
        }


        private static boolean near (final double first, final double second)
        {
            return Math.abs (first - second) <= EPSILON;
        }
    }
}
