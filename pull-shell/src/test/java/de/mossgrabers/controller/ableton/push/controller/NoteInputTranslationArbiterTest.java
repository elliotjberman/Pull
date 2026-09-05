// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.pull.core.api.DesiredNoteInputTranslation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class NoteInputTranslationArbiterTest
{
    @Test
    void ownedTablesBlockLateLegacyWritesAndRestoreTheLatestLegacyTablesOnRelease ()
    {
        final List<int[]> keys = new ArrayList<> ();
        final List<int[]> velocities = new ArrayList<> ();
        final NoteInputTranslationArbiter arbiter = new NoteInputTranslationArbiter (keys::add, velocities::add);
        final int[] legacyKeys = IntStream.range (0, 128).toArray ();
        final int[] legacyVelocities = IntStream.range (0, 128).toArray ();
        arbiter.setLegacyKeys (legacyKeys);
        arbiter.setLegacyVelocities (legacyVelocities);
        final DesiredNoteInputTranslation core = translation (48);
        arbiter.apply (core);
        arbiter.apply (core);
        assertEquals (2, keys.size ());
        assertEquals (2, velocities.size ());
        assertEquals (core, arbiter.snapshot ());

        legacyKeys[36] = 60;
        legacyVelocities[100] = 127;
        arbiter.setLegacyKeys (legacyKeys);
        arbiter.setLegacyVelocities (legacyVelocities);
        assertEquals (2, keys.size ());
        assertEquals (2, velocities.size ());
        legacyKeys[36] = 61;
        legacyVelocities[100] = 126;

        arbiter.apply (DesiredNoteInputTranslation.unowned ());
        assertEquals (60, keys.getLast ()[36]);
        assertEquals (127, velocities.getLast ()[100]);
        assertEquals (DesiredNoteInputTranslation.unowned (), arbiter.snapshot ());
    }


    @Test
    void partialTransmissionFailureKeepsOwnershipAndAttemptsToSilenceKeys ()
    {
        final List<int[]> keys = new ArrayList<> ();
        final NoteInputTranslationArbiter arbiter = new NoteInputTranslationArbiter (keys::add, ignored -> {
            throw new IllegalStateException ("transport unavailable");
        });
        assertThrows (IllegalStateException.class, () -> arbiter.apply (translation (48)));
        assertEquals (DesiredNoteInputTranslation.silent (), arbiter.snapshot ());
        assertEquals (2, keys.size ());
        assertTrue (Arrays.stream (keys.getLast ()).allMatch (note -> note == -1));
        arbiter.setLegacyKeys (IntStream.range (0, 128).toArray ());
        assertEquals (2, keys.size ());
    }


    @Test
    void silentOwnershipIsDifferentFromRelinquishingToLegacy ()
    {
        final List<int[]> keys = new ArrayList<> ();
        final NoteInputTranslationArbiter arbiter = new NoteInputTranslationArbiter (keys::add, ignored -> { });
        final int[] legacy = IntStream.range (0, 128).toArray ();
        arbiter.setLegacyKeys (legacy);
        arbiter.apply (DesiredNoteInputTranslation.silent ());
        assertTrue (Arrays.stream (keys.getLast ()).allMatch (note -> note == -1));
        arbiter.apply (DesiredNoteInputTranslation.unowned ());
        assertArrayEquals (legacy, keys.getLast ());
    }


    private static DesiredNoteInputTranslation translation (final int note)
    {
        final List<Integer> keys = new ArrayList<> (DesiredNoteInputTranslation.silent ().keyTranslation ());
        keys.set (36, Integer.valueOf (note));
        return new DesiredNoteInputTranslation (true, keys, IntStream.range (0, 128).boxed ().toList ());
    }
}
