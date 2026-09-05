// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.pull.core.api.DesiredNoteInputTranslation;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;


/** Mechanical exclusive arbitration of the permanent note input's two translation tables. */
public final class NoteInputTranslationArbiter
{
    private final Consumer<int[]> keyWriter;
    private final Consumer<int[]> velocityWriter;
    private int[] legacyKeys = silentKeys ();
    private int[] legacyVelocities = IntStream.range (0, 128).toArray ();
    private DesiredNoteInputTranslation applied = DesiredNoteInputTranslation.unowned ();


    public NoteInputTranslationArbiter (final Consumer<int[]> keyWriter, final Consumer<int[]> velocityWriter)
    {
        this.keyWriter = Objects.requireNonNull (keyWriter, "keyWriter");
        this.velocityWriter = Objects.requireNonNull (velocityWriter, "velocityWriter");
    }


    public void setLegacyKeys (final int[] table)
    {
        this.legacyKeys = Objects.requireNonNull (table, "table").clone ();
        if (!this.applied.owned ())
            this.keyWriter.accept (this.legacyKeys.clone ());
    }


    public void setLegacyVelocities (final int[] table)
    {
        this.legacyVelocities = Objects.requireNonNull (table, "table").clone ();
        if (!this.applied.owned ())
            this.velocityWriter.accept (this.legacyVelocities.clone ());
    }


    public void apply (final DesiredNoteInputTranslation requested)
    {
        final DesiredNoteInputTranslation next = Objects.requireNonNull (requested, "requested");
        if (next.equals (this.applied))
            return;
        // Take ownership before transmission so a failed write cannot revive a legacy writer.
        this.applied = DesiredNoteInputTranslation.silent ();
        try
        {
            this.keyWriter.accept (next.owned () ? next.keyTranslation ().stream ().mapToInt (Integer::intValue).toArray () : this.legacyKeys.clone ());
            this.velocityWriter.accept (next.owned () ? next.velocityTranslation ().stream ().mapToInt (Integer::intValue).toArray () : this.legacyVelocities.clone ());
            this.applied = next;
        }
        catch (final RuntimeException failure)
        {
            try
            {
                this.keyWriter.accept (silentKeys ());
            }
            catch (final RuntimeException cleanupFailure)
            {
                failure.addSuppressed (cleanupFailure);
            }
            throw failure;
        }
    }


    public DesiredNoteInputTranslation snapshot ()
    {
        return this.applied;
    }


    private static int[] silentKeys ()
    {
        final int[] table = new int[128];
        Arrays.fill (table, -1);
        return table;
    }
}
