// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.event.InputKind;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Exact physical-input coverage tests for the fixed Push surface vocabulary. */
class SurfaceAreaTest
{
    private static final Map<SurfaceArea, String> DEDICATED_BUTTONS = Map.ofEntries (
        Map.entry (SurfaceArea.NEW_BUTTON, "NEW"),
        Map.entry (SurfaceArea.FIXED_LENGTH_BUTTON, "FIXED_LENGTH"),
        Map.entry (SurfaceArea.DUPLICATE_BUTTON, "DUPLICATE"),
        Map.entry (SurfaceArea.QUANTIZE_BUTTON, "QUANTIZE"),
        Map.entry (SurfaceArea.DELETE_MODIFIER, "DELETE"),
        Map.entry (SurfaceArea.DOUBLE_BUTTON, "DOUBLE"),
        Map.entry (SurfaceArea.UNDO_BUTTON, "UNDO"),
        Map.entry (SurfaceArea.AUTOMATION_BUTTON, "AUTOMATION"),
        Map.entry (SurfaceArea.MIX_BUTTON, "TRACK"),
        Map.entry (SurfaceArea.DEVICE_BUTTON, "DEVICE"),
        Map.entry (SurfaceArea.CLIP_BUTTON, "CLIP"),
        Map.entry (SurfaceArea.TAP_TEMPO_BUTTON, "TAP_TEMPO"),
        Map.entry (SurfaceArea.METRONOME_BUTTON, "METRONOME"),
        Map.entry (SurfaceArea.MASTER_BUTTON, "MASTERTRACK"),
        Map.entry (SurfaceArea.MUTE_BUTTON, "MUTE"),
        Map.entry (SurfaceArea.SOLO_BUTTON, "SOLO"),
        Map.entry (SurfaceArea.SCALES_BUTTON, "SCALES"),
        Map.entry (SurfaceArea.ACCENT_BUTTON, "ACCENT"),
        Map.entry (SurfaceArea.ADD_DEVICE_BUTTON, "ADD_EFFECT"),
        Map.entry (SurfaceArea.ADD_TRACK_BUTTON, "ADD_TRACK"),
        Map.entry (SurfaceArea.STOP_CLIP_BUTTON, "STOP_CLIP"),
        Map.entry (SurfaceArea.REPEAT_BUTTON, "REPEAT"),
        Map.entry (SurfaceArea.SETUP_BUTTON, "SETUP"),
        Map.entry (SurfaceArea.CONVERT_BUTTON, "CONVERT"),
        Map.entry (SurfaceArea.USER_BUTTON, "USER"),
        Map.entry (SurfaceArea.BROWSE_BUTTON, "BROWSE"));


    @Test
    void namesEveryPreviouslyUncoveredInstalledInputAddress ()
    {
        final Set<InputAddress> addresses = new LinkedHashSet<> ();
        DEDICATED_BUTTONS.forEach ( (area, name) -> {
            final ControlId control = PushControlIds.button (name);
            assertEquals (Set.of (control), area.controls ());
            assertEquals (Set.of (InputKind.BUTTON), area.inputKinds ());
            addresses.add (new InputAddress (control, InputKind.BUTTON));
        });

        assertArea (SurfaceArea.FOOTSWITCH_2, PushControlIds.button ("FOOTSWITCH2"), Set.of (InputKind.PEDAL));
        assertArea (SurfaceArea.SUSTAIN_PEDAL, PushControlIds.SUSTAIN_PEDAL, Set.of (InputKind.PEDAL));
        assertArea (SurfaceArea.PLAY_POSITION_ENCODER, PushControlIds.continuous ("PLAY_POSITION"), Set.of (InputKind.RELATIVE, InputKind.TOUCH));
        addresses.add (new InputAddress (PushControlIds.button ("FOOTSWITCH2"), InputKind.PEDAL));
        addresses.add (new InputAddress (PushControlIds.SUSTAIN_PEDAL, InputKind.PEDAL));
        addresses.add (new InputAddress (PushControlIds.continuous ("PLAY_POSITION"), InputKind.RELATIVE));
        addresses.add (new InputAddress (PushControlIds.continuous ("PLAY_POSITION"), InputKind.TOUCH));

        assertEquals (30, addresses.size ());
        addresses.forEach (address -> assertTrue (SurfaceArea.coversInput (address.control (), address.kind ()), address.toString ()));
        assertFalse (SurfaceArea.coversInput (PushControlIds.button ("FOOTSWITCH2"), InputKind.BUTTON));
        assertFalse (SurfaceArea.coversInput (PushControlIds.SUSTAIN_PEDAL, InputKind.BUTTON));
        assertFalse (SurfaceArea.coversInput (PushControlIds.continuous ("PLAY_POSITION"), InputKind.ABSOLUTE));
    }


    @Test
    void givesEveryDedicatedControlADistinctPhysicalAtom ()
    {
        for (final SurfaceArea button: DEDICATED_BUTTONS.keySet ())
        {
            for (final SurfaceArea other: SurfaceArea.values ())
            {
                if (button != other)
                    assertFalse (button.overlaps (other), button + " / " + other);
            }
        }
        assertFalse (SurfaceArea.SUSTAIN_PEDAL.overlaps (SurfaceArea.FOOTSWITCH_2));
    }


    @Test
    void keepsPhysicalPadClaimsSeparateFromVirtualMappingEndpoints ()
    {
        final List<ControlId> physicalMappingPads = IntStream.rangeClosed (29, 32).mapToObj (PushControlIds::pad).toList ();
        assertEquals (physicalMappingPads, CoreControls.DRUM_CONTROL_PADS);
        assertEquals (Set.copyOf (physicalMappingPads), SurfaceArea.DRUM_CONTROL_PADS.controls ());
        assertEquals (Set.of (InputKind.PAD), SurfaceArea.DRUM_CONTROL_PADS.inputKinds ());

        final Set<String> physicalIds = physicalMappingPads.stream ().map (ControlId::value).collect (Collectors.toUnmodifiableSet ());
        assertTrue (CoreControllerMappings.DRUM_CONTROL_PADS.stream ().noneMatch (mapping -> physicalIds.contains (mapping.value ())));
        for (int index = 1; index <= 64; index++)
        {
            final ControlId physicalPad = PushControlIds.pad (index);
            assertTrue (SurfaceArea.coversInput (physicalPad, InputKind.PAD));
            assertTrue (SurfaceArea.coversInput (physicalPad, InputKind.POLY_PRESSURE));
        }
    }


    @Test
    void separatesSemanticFillActionsFromTheirPhysicalLights ()
    {
        final List<ControlId> physicalFillLights = List.of (
            PushControlIds.pad (13),
            PushControlIds.pad (14),
            PushControlIds.pad (15),
            PushControlIds.pad (16),
            PushControlIds.pad (21),
            PushControlIds.pad (22),
            PushControlIds.pad (23),
            PushControlIds.pad (24));

        assertEquals (Set.copyOf (CoreControls.DRUM_FILLS), SurfaceArea.DRUM_FILL_PADS.controls ());
        assertEquals (Set.of (InputKind.PAD, InputKind.POLY_PRESSURE), SurfaceArea.DRUM_FILL_PADS.inputKinds ());
        assertEquals (Set.copyOf (physicalFillLights), SurfaceArea.DRUM_FILL_LIGHTS.controls ());
        assertEquals (Set.of (), SurfaceArea.DRUM_FILL_LIGHTS.inputKinds ());
        assertTrue (SurfaceArea.DRUM_FILL_PADS.overlaps (SurfaceArea.DRUM_FILL_LIGHTS));
    }


    private static void assertArea (final SurfaceArea area, final ControlId control, final Set<InputKind> kinds)
    {
        assertEquals (Set.of (control), area.controls ());
        assertEquals (kinds, area.inputKinds ());
    }


    private record InputAddress (ControlId control, InputKind kind)
    {}
}
