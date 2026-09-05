// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwAbsoluteControl;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingTarget;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.PushControlIds;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** Permanent semantic absolute-value mapping endpoints and their authoritative target feedback. */
final class ControllerMappingHost
{
    private static final int PAD_COUNT = 64;
    private static final Set<ControlId> PHYSICAL_PAD_CONTROLS = physicalPadControls ();

    private final Map<ControlId, IHwButton> physicalButtons;
    private final Map<ControllerMappingId, IHwAbsoluteControl> mappingControls;
    private final FeedbackState feedback;
    private final ControllerMappingStorageHost storage;


    ControllerMappingHost (final PushControlSurface surface, final ControllerMappingStorageHost storage)
    {
        this (Objects.requireNonNull (surface, "surface").getSurfaceFactory (), surface.getSurfaceID (), physicalButtons (surface), Objects.requireNonNull (storage, "storage"));
    }


    /** Test seam for installing the bounded endpoint inventory. */
    ControllerMappingHost (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControlId, IHwButton> physicalButtons)
    {
        this (factory, surfaceID, physicalButtons, null);
    }


    /** Test seam for installing endpoint resources and raw document storage together. */
    ControllerMappingHost (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControlId, IHwButton> physicalButtons, final ControllerMappingStorageHost storage)
    {
        final IHwSurfaceFactory checkedFactory = Objects.requireNonNull (factory, "factory");
        this.physicalButtons = Map.copyOf (Objects.requireNonNull (physicalButtons, "physicalButtons"));
        if (!this.physicalButtons.keySet ().equals (PHYSICAL_PAD_CONTROLS))
            throw new IllegalArgumentException ("controller mapping host requires the complete 64-pad physical grid");

        this.storage = storage;
        this.feedback = new FeedbackState ();
        final Map<ControllerMappingId, IHwAbsoluteControl> controls = new LinkedHashMap<> ();
        // Retain the original identities so persisted mappings remain recognizable, but no core
        // track context leases them. New banks never inherit these historical global targets.
        for (int slot = 0; slot < CoreControllerMappings.DRUM_CONTROL_PADS.size (); slot++)
        {
            final int number = slot + 1;
            final ControllerMappingId mappingId = CoreControllerMappings.DRUM_CONTROL_PADS.get (slot);
            this.install (checkedFactory, surfaceID, controls, mappingId,
                "CONTROLLER_MAPPING_DRUM_CONTROL_VALUE_" + number,
                "Drum Controller Toggle " + number);
        }
        for (int bank = 0; bank < CoreControllerMappings.TRACK_BANK_COUNT; bank++)
            for (int slot = 0; slot < CoreControllerMappings.trackBank (bank).size (); slot++)
                this.install (checkedFactory, surfaceID, controls, CoreControllerMappings.trackBank (bank).get (slot),
                    "CONTROLLER_MAPPING_TRACK_" + (bank + 1) + "_CONTROL_VALUE_" + (slot + 1),
                    "Track " + (bank + 1) + " Toggle " + (slot + 1));
        this.mappingControls = Map.copyOf (controls);

        // Physical pads remain the sole ordinary-command dispatch objects, but none expose native
        // MIDI matchers or Bitwig-learnable identities. The permanent raw ingress drives them.
        this.physicalButtons.values ().forEach (IHwButton::unbind);
    }


    Map<ControlId, IHwButton> physicalButtons ()
    {
        return this.physicalButtons;
    }


    Map<ControllerMappingId, IHwAbsoluteControl> mappingControls ()
    {
        return this.mappingControls;
    }


    ControllerMappingFeedbackSnapshot snapshot ()
    {
        return this.feedback.snapshot (this.storage == null ? ControllerMappingStorageSnapshot.empty () : this.storage.snapshot ());
    }


    ControllerMappingStorageHost storage ()
    {
        return Objects.requireNonNull (this.storage, "controller mapping storage is not installed");
    }


    private void install (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControllerMappingId, IHwAbsoluteControl> controls,
                         final ControllerMappingId mappingId, final String hardwareId, final String label)
    {
        final IHwAbsoluteControl mappingControl = Objects.requireNonNull (factory.createAbsoluteKnob (surfaceID, hardwareId, label), "semantic mapping control");
        mappingControl.disableTakeOver ();
        factory.installMappedAbsoluteFeedback (mappingControl,
            (hasTarget, value) -> this.feedback.accept (mappingId, new ControllerMappingTarget (hasTarget.booleanValue (), value.doubleValue ())));
        controls.put (mappingId, mappingControl);
    }


    private static Map<ControlId, IHwButton> physicalButtons (final PushControlSurface surface)
    {
        final Map<ControlId, IHwButton> physicalButtons = new LinkedHashMap<> ();
        for (int index = 0; index < PAD_COUNT; index++)
        {
            final IHwButton button = Objects.requireNonNull (surface.getButton (ButtonID.get (ButtonID.PAD1, index)), "physical pad");
            physicalButtons.put (PushControlIds.pad (index + 1), button);
        }
        return physicalButtons;
    }


    private static Set<ControlId> physicalPadControls ()
    {
        final Set<ControlId> controls = new LinkedHashSet<> (PAD_COUNT);
        for (int index = 1; index <= PAD_COUNT; index++)
            controls.add (PushControlIds.pad (index));
        return Set.copyOf (controls);
    }


    private static final class FeedbackState
    {
        private final Map<ControllerMappingId, ControllerMappingTarget> targets = new LinkedHashMap<> ();
        private ControllerMappingFeedbackSnapshot snapshot = ControllerMappingFeedbackSnapshot.empty ();
        private boolean dirty;


        private synchronized void accept (final ControllerMappingId mappingId, final ControllerMappingTarget target)
        {
            if (target.equals (this.targets.get (mappingId)))
                return;
            this.targets.put (mappingId, target);
            this.dirty = true;
        }


        private synchronized ControllerMappingFeedbackSnapshot snapshot (final ControllerMappingStorageSnapshot storage)
        {
            if (this.dirty || !storage.equals (this.snapshot.storage ()))
            {
                this.snapshot = new ControllerMappingFeedbackSnapshot (!this.targets.isEmpty (), this.targets, storage);
                this.dirty = false;
            }
            return this.snapshot;
        }
    }
}
