// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.midi.MidiConstants;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Physical routing tests for the permanent shell facade.
 */
class ReloadableControllerRuntimeTest
{
    private static final int [] EXPECTED_FILL_NOTES =
    {
        48,
        49,
        50,
        51,
        56,
        57,
        58,
        59,
        64,
        65,
        66,
        67
    };


    @Test
    void exposesTwelveFillPadsInBottomUpRowMajorOrder ()
    {
        final int [] notes = ReloadableControllerRuntime.fillPadNotes ();
        assertArrayEquals (EXPECTED_FILL_NOTES, notes);
        for (final int note: notes)
            assertTrue (ReloadableControllerRuntime.isFillPad (note));
        assertFalse (ReloadableControllerRuntime.isFillPad (47));
        assertFalse (ReloadableControllerRuntime.isFillPad (52));
        assertFalse (ReloadableControllerRuntime.isFillPad (68));

        notes[0] = 0;
        assertArrayEquals (EXPECTED_FILL_NOTES, ReloadableControllerRuntime.fillPadNotes ());
    }


    @Test
    void routesIndependentFillPadsAndSafetyReleasesExactOwners ()
    {
        final FakeClipHost clipHost = new FakeClipHost (9);
        final DrumFillRuntimeEnvironment environment = createArmedEnvironment (clipHost);
        final List<CoreEvent> events = new ArrayList<> ();
        final long [] coreGeneration =
        {
            1
        };
        final Predicate<CoreEvent> handler = event -> {
            events.add (event);
            if (event instanceof final ButtonInputEvent button && button.pressed ())
            {
                final ClipTargetId targetId = clipHost.targetId (button.controlId ());
                final CoreResult press = new CoreResult (
                    DesiredHardwareOutput.empty (),
                    clipHost.allBindings (),
                    List.of (new PressClipTargetEffect (button.controlId (), clipHost.catalogGeneration (), targetId)));
                final long generation = ++coreGeneration[0];
                environment.commit (generation, environment.prepare (press));
                environment.apply (generation);
            }
            // Deliberately omit release effects. A physical UP must still release only the shell
            // lease owned by that pad.
            return true;
        };
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (environment, NoOpLog.INSTANCE, handler);
        runtime.start ();

        assertFalse (runtime.routeGridEvent (true, ButtonEvent.DOWN, 52));
        assertFalse (runtime.routeGridEvent (false, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[0]));

        for (int index = 0; index < EXPECTED_FILL_NOTES.length; index++)
        {
            final int note = EXPECTED_FILL_NOTES[index];
            final ControlId control = CoreControls.drumFills ().get (index);
            assertTrue (runtime.routeGridEvent (true, ButtonEvent.DOWN, note));
            assertTrue (environment.isFillPressed (control));
            assertEquals (1, clipHost.target (control).pressCount);
        }

        final ControlId firstControl = CoreControls.drumFills ().getFirst ();
        final ControlId secondControl = CoreControls.drumFills ().get (1);
        assertTrue (runtime.routeGridEvent (false, ButtonEvent.LONG, EXPECTED_FILL_NOTES[0]));
        assertTrue (runtime.routeGridEvent (false, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[0]));
        assertEquals (EXPECTED_FILL_NOTES.length, events.size ());

        runtime.routePhysicalMidiRelease (false, MidiConstants.CMD_NOTE_OFF | 1, EXPECTED_FILL_NOTES[0], 0);
        assertTrue (environment.isFillPressed (firstControl));
        assertEquals (0, clipHost.target (firstControl).releaseCount);

        // This callback sits below active-view dispatch and therefore still sees the exact UP when
        // a view switch consumes the command-layer release.
        runtime.routePhysicalMidiRelease (false, MidiConstants.CMD_NOTE_OFF, EXPECTED_FILL_NOTES[0], 0);
        assertFalse (environment.isFillPressed (firstControl));
        assertTrue (environment.isFillPressed (secondControl));
        assertEquals (1, clipHost.target (firstControl).releaseCount);
        assertEquals (0, clipHost.target (secondControl).releaseCount);
        assertEquals (EXPECTED_FILL_NOTES.length + 1, events.size ());
        assertFalse (((ButtonInputEvent) events.getLast ()).pressed ());

        // The command-layer UP produced by the same MIDI message is consumed after it updates its
        // hardware state, so it cannot leak into the newly active view.
        assertTrue (runtime.routeGridEvent (false, ButtonEvent.UP, EXPECTED_FILL_NOTES[0]));
        assertFalse (runtime.routeGridEvent (false, ButtonEvent.UP, EXPECTED_FILL_NOTES[0]));
        runtime.close ();

        for (final ControlId control: CoreControls.drumFills ())
            assertEquals (1, clipHost.target (control).releaseCount);
    }


    @Test
    void rejectedAuthoritativeReleaseClearsOnlyItsStaleHeldOutput ()
    {
        final FakeClipHost clipHost = new FakeClipHost (9);
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (clipHost, NoOpLog.INSTANCE, () -> 0);
        final ControlId firstControl = CoreControls.drumFills ().getFirst ();
        final ControlId secondControl = CoreControls.drumFills ().get (1);
        final RgbColor firstHeld = new RgbColor (255, 0, 0);
        final RgbColor secondHeld = new RgbColor (127, 0, 0);
        environment.commit (1, environment.prepare (new CoreResult (
            new DesiredHardwareOutput (Map.of (firstControl, firstHeld, secondControl, secondHeld)),
            List.of ())));
        environment.apply (1);
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (
            environment,
            NoOpLog.INSTANCE,
            event -> event instanceof final ButtonInputEvent button && button.pressed ());
        runtime.start ();

        assertTrue (runtime.routeGridEvent (true, ButtonEvent.DOWN, EXPECTED_FILL_NOTES[0]));
        assertEquals (firstHeld, runtime.fillLightColor (EXPECTED_FILL_NOTES[0]));
        assertEquals (secondHeld, runtime.fillLightColor (EXPECTED_FILL_NOTES[1]));
        assertEquals (new RgbColor (0, 0, 0), runtime.fillLightColor (52));

        runtime.routePhysicalMidiRelease (false, MidiConstants.CMD_NOTE_ON, EXPECTED_FILL_NOTES[0], 0);

        assertFalse (environment.isFillPressed (firstControl));
        assertEquals (new RgbColor (0, 0, 0), runtime.fillLightColor (EXPECTED_FILL_NOTES[0]));
        assertEquals (secondHeld, runtime.fillLightColor (EXPECTED_FILL_NOTES[1]));
        runtime.close ();
    }


    private static DrumFillRuntimeEnvironment createArmedEnvironment (final FakeClipHost clipHost)
    {
        final DrumFillRuntimeEnvironment environment = new DrumFillRuntimeEnvironment (clipHost, NoOpLog.INSTANCE, () -> 0);
        final CoreResult bindings = new CoreResult (DesiredHardwareOutput.empty (), clipHost.allBindings (), List.of ());
        environment.commit (1, environment.prepare (bindings));
        environment.apply (1);
        environment.refresh ();
        return environment;
    }


    private static final class FakeClipHost implements DrumFillClipHost
    {
        private final ClipCatalogSnapshot catalog;
        private final Map<ControlId, FakeTarget> targets;
        private Map<ControlId, ClipTargetId> armedBindings = Map.of ();


        private FakeClipHost (final long generation)
        {
            final Map<ControlId, FakeTarget> createdTargets = new LinkedHashMap<> ();
            final List<CatalogClip> clips = new ArrayList<> ();
            for (int index = 0; index < CoreControls.drumFills ().size (); index++)
            {
                final ControlId control = CoreControls.drumFills ().get (index);
                final FakeTarget target = new FakeTarget (index + 1L);
                createdTargets.put (control, target);
                clips.add (new CatalogClip (target.targetId (), "fill " + (index + 1)));
            }
            this.targets = Map.copyOf (createdTargets);
            this.catalog = new ClipCatalogSnapshot (generation, clips);
        }


        @Override
        public boolean refresh ()
        {
            return false;
        }


        @Override
        public ClipCatalogSnapshot clipCatalog ()
        {
            return this.catalog;
        }


        @Override
        public void setDesiredBindings (final long catalogGeneration, final Map<ControlId, ClipTargetId> bindings)
        {
            if (catalogGeneration != this.catalog.generation ())
                throw new IllegalArgumentException ("Unknown catalog generation");
            this.armedBindings = Map.copyOf (bindings);
        }


        @Override
        public Map<ControlId, ClipTargetId> armedClipTargets ()
        {
            return this.armedBindings;
        }


        @Override
        public LaunchTarget prepare (final ControlId owner, final long catalogGeneration, final ClipTargetId targetId)
        {
            if (catalogGeneration != this.catalog.generation () || !targetId.equals (this.armedBindings.get (owner)))
                throw new IllegalArgumentException ("Unknown target");
            final FakeTarget target = this.targets.get (owner);
            if (target == null || !target.targetId ().equals (targetId))
                throw new IllegalArgumentException ("Unknown owner");
            return target;
        }


        private long catalogGeneration ()
        {
            return this.catalog.generation ();
        }


        private Map<ControlId, ClipTargetId> allBindings ()
        {
            final Map<ControlId, ClipTargetId> bindings = new LinkedHashMap<> ();
            for (final Map.Entry<ControlId, FakeTarget> target: this.targets.entrySet ())
                bindings.put (target.getKey (), target.getValue ().targetId ());
            return Map.copyOf (bindings);
        }


        private ClipTargetId targetId (final ControlId control)
        {
            return this.target (control).targetId ();
        }


        private FakeTarget target (final ControlId control)
        {
            return this.targets.get (control);
        }
    }


    private static final class FakeTarget implements DrumFillClipHost.LaunchTarget
    {
        private final ClipTargetId targetId;
        private int pressCount;
        private int releaseCount;


        private FakeTarget (final long value)
        {
            this.targetId = new ClipTargetId (value);
        }


        @Override
        public ClipTargetId targetId ()
        {
            return this.targetId;
        }


        @Override
        public void press ()
        {
            this.pressCount++;
        }


        @Override
        public void release ()
        {
            this.releaseCount++;
        }
    }


    private enum NoOpLog implements RuntimeLog
    {
        INSTANCE;


        @Override
        public void info (final String message)
        {
            // No-op test log.
        }


        @Override
        public void warn (final String message)
        {
            // No-op test log.
        }
    }
}
