// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.StringUtils;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.shell.PushDebugging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Bounded, opt-in mirror of successful Push light transmissions and physical pressed state.
 *
 * <p>The controller thread only copies at most the installed Push button/pad footprint into one
 * coalescing slot. JSON serialization and filesystem writes run on the owned debug worker.</p>
 */
final class PushDebugSurfaceHost implements AutoCloseable
{
    static final String STATE_FILE = "surface-state.json";
    private static final int MAX_INPUT_EVENTS = 32;

    private final Path                         statePath;
    private final ScheduledExecutorService     worker;
    private final Map<String, LightState>       lights = new TreeMap<> ();
    private final Set<String>                   pressed = new LinkedHashSet<> ();
    private final Deque<InputEvent>              inputEvents = new ArrayDeque<> (MAX_INPUT_EVENTS);
    private final AtomicReference<Snapshot>     pending = new AtomicReference<> ();
    private final AtomicBoolean                 drainScheduled = new AtomicBoolean ();
    private final AtomicBoolean                 closed = new AtomicBoolean ();

    private ClockState clock = ClockState.unavailable ();
    private long revision;
    private long inputEventSequence;
    private long publishedClockBeat = Long.MIN_VALUE;
    private boolean semanticFrameActive;
    private boolean semanticFrameChanged;


    static PushDebugSurfaceHost createIfEnabled ()
    {
        if (!PushDebugging.isEnabled ())
            return null;

        return new PushDebugSurfaceHost (
            PushDebugging.directory (),
            PushDebugging.createWorker ("Pull debug surface transport"));
    }


    PushDebugSurfaceHost (final Path directory, final ScheduledExecutorService worker)
    {
        this.statePath = Objects.requireNonNull (directory, "directory").resolve (STATE_FILE);
        this.worker = worker;
        this.publish (true);
    }


    /** Remember one successfully transmitted Push button palette value. */
    void observeButton (final ButtonID button, final int palette, final ColorEx color)
    {
        if (this.closed.get ())
            return;
        final String control = PushControlIds.button (Objects.requireNonNull (button, "button").name ()).value ();
        final LightState state = new LightState (palette, StringUtils.formatColor (Objects.requireNonNull (color, "color")), 0, null, false, null);
        if (!state.equals (this.lights.put (control, state)))
            this.publish (true);
    }


    /** Remember one complete, successfully transmitted Push pad state. */
    void observePadTransmission (final int oneBasedPad, final int palette, final ColorEx color, final int blinkPalette, final ColorEx blinkColor, final boolean fast)
    {
        if (this.closed.get ())
            return;
        final String control = PushControlIds.pad (oneBasedPad).value ();
        final DebugMusicalPulse pulse = this.lights.getOrDefault (control, LightState.off ()).pulse ();
        final LightState state = new LightState (
            palette,
            StringUtils.formatColor (Objects.requireNonNull (color, "color")),
            blinkPalette,
            blinkPalette > 0 ? StringUtils.formatColor (Objects.requireNonNull (blinkColor, "blinkColor")) : null,
            fast,
            pulse);
        if (!state.equals (this.lights.put (control, state)))
            this.publish (true);
    }


    /** Remember the semantic pulse independently of physical output-cache transmissions. */
    void observePadSemantic (final int oneBasedPad, final DebugMusicalPulse musicalPulse)
    {
        if (this.closed.get ())
            return;
        final String control = PushControlIds.pad (oneBasedPad).value ();
        final LightState current = this.lights.getOrDefault (control, LightState.off ());
        final LightState state = new LightState (current.palette (), current.rgb (), current.blinkPalette (), current.blinkRgb (), current.fast (), musicalPulse);
        if (!state.equals (this.lights.put (control, state)))
        {
            if (this.semanticFrameActive)
                this.semanticFrameChanged = true;
            else
                this.publish (true);
        }
    }


    /** Publish one complete grid semantic frame without exposing an old-pad/new-pad gap. */
    void observePadSemanticFrame (final Runnable observations)
    {
        if (this.semanticFrameActive)
            throw new IllegalStateException ("A pad semantic frame is already active");
        this.semanticFrameActive = true;
        this.semanticFrameChanged = false;
        boolean complete = false;
        try
        {
            Objects.requireNonNull (observations, "observations").run ();
            complete = true;
        }
        finally
        {
            this.semanticFrameActive = false;
        }
        if (complete && this.semanticFrameChanged)
            this.publish (true);
    }


    /** Observe the current pressed subset of the fixed installed Push button footprint. */
    void observePressed (final Map<ButtonID, IHwButton> buttons)
    {
        if (this.closed.get ())
            return;

        final Set<String> next = new LinkedHashSet<> ();
        for (final Map.Entry<ButtonID, IHwButton> entry: Objects.requireNonNull (buttons, "buttons").entrySet ())
        {
            if (!entry.getValue ().isPressed ())
                continue;
            next.add (controlId (entry.getKey ()));
        }
        this.observePressedControls (next);
    }


    /** Observe Bitwig's transport clock without publishing more than once per beat. */
    void observeClock (final boolean playing, final double tempo, final double positionBeats, final long sampledAtMillis)
    {
        if (this.closed.get () || !Double.isFinite (tempo) || tempo <= 0 || !Double.isFinite (positionBeats) || positionBeats < 0 || sampledAtMillis < 0)
            return;

        final ClockState previous = this.clock;
        final ClockState next = new ClockState (true, playing, tempo, positionBeats, sampledAtMillis);
        final long beat = (long) Math.floor (positionBeats);
        this.clock = next;
        if (previous.available () && previous.playing () == playing && Double.compare (previous.tempo (), tempo) == 0 && beat == this.publishedClockBeat)
            return;
        this.publishedClockBeat = beat;
        this.publish (true);
    }


    /** Retain one successful debugger injection even when adjacent samples share one tick. */
    void observeDebugInput (final ControlId control, final InputKind kind, final InputPhase phase, final long value)
    {
        if (this.closed.get ())
            return;
        if (this.inputEvents.size () == MAX_INPUT_EVENTS)
            this.inputEvents.removeFirst ();
        this.inputEvents.addLast (new InputEvent (
            ++this.inputEventSequence,
            Objects.requireNonNull (control, "control").value (),
            Objects.requireNonNull (kind, "kind").name (),
            Objects.requireNonNull (phase, "phase").name (),
            value));
        this.publish (true);
    }


    /** Deterministic pressed-state seam for tests. */
    void observePressedControls (final Collection<String> controls)
    {
        if (this.closed.get ())
            return;
        final Set<String> next = new LinkedHashSet<> (Objects.requireNonNull (controls, "controls"));
        if (next.equals (this.pressed))
            return;
        this.pressed.clear ();
        this.pressed.addAll (next);
        this.publish (true);
    }


    /** Deterministic filesystem drain for tests. */
    void pollForTest ()
    {
        if (this.worker != null)
            throw new IllegalStateException ("Production surface transport drains itself");
        this.pollFilesSafely ();
    }


    private void publish (final boolean connected)
    {
        this.pending.set (new Snapshot (
            ++this.revision,
            connected,
            this.clock,
            new TreeMap<> (this.lights),
            Set.copyOf (this.pressed),
            new ArrayList<> (this.inputEvents)));
        this.requestDrain ();
    }


    private void requestDrain ()
    {
        if (this.worker == null || !this.drainScheduled.compareAndSet (false, true))
            return;
        this.worker.execute ( () -> {
            try
            {
                this.pollFilesSafely ();
            }
            finally
            {
                this.drainScheduled.set (false);
                if (this.pending.get () != null)
                    this.requestDrain ();
            }
        });
    }


    private void pollFilesSafely ()
    {
        final Snapshot snapshot = this.pending.getAndSet (null);
        if (snapshot == null)
            return;
        try
        {
            Files.createDirectories (this.statePath.getParent ());
            final Path temporary = this.statePath.resolveSibling (STATE_FILE + ".tmp");
            Files.writeString (temporary, serialize (snapshot));
            PushDebugging.replaceAtomically (temporary, this.statePath);
        }
        catch (final IOException | RuntimeException ignored)
        {
            // A local visualization failure cannot affect controller output.
        }
    }


    private static String serialize (final Snapshot snapshot)
    {
        final StringBuilder json = new StringBuilder (16_384);
        json.append ("{\"connected\":").append (snapshot.connected ());
        json.append (",\"revision\":").append (snapshot.revision ());
        final ClockState clock = snapshot.clock ();
        json.append (",\"clock\":{\"available\":").append (clock.available ());
        json.append (",\"playing\":").append (clock.playing ());
        json.append (",\"tempo\":").append (clock.tempo ());
        json.append (",\"positionBeats\":").append (clock.positionBeats ());
        json.append (",\"sampledAtMillis\":").append (clock.sampledAtMillis ()).append ('}');
        json.append (",\"lights\":{");
        boolean first = true;
        for (final Map.Entry<String, LightState> entry: snapshot.lights ().entrySet ())
        {
            if (!first)
                json.append (',');
            first = false;
            final LightState light = entry.getValue ();
            appendString (json, entry.getKey ());
            json.append (":{\"rgb\":");
            appendString (json, light.rgb ());
            json.append (",\"palette\":").append (light.palette ());
            json.append (",\"blinkRgb\":");
            if (light.blinkRgb () == null)
                json.append ("null");
            else
                appendString (json, light.blinkRgb ());
            json.append (",\"blinkPalette\":").append (light.blinkPalette ());
            json.append (",\"fast\":").append (light.fast ());
            json.append (",\"pulse\":");
            final DebugMusicalPulse pulse = light.pulse ();
            if (pulse == null)
                json.append ("null");
            else
            {
                json.append ("{\"baseRgb\":");
                appendString (json, StringUtils.formatColor (pulse.baseColor ()));
                json.append (",\"basePalette\":").append (pulse.basePalette ());
                json.append (",\"alternateRgb\":");
                appendString (json, StringUtils.formatColor (pulse.alternateColor ()));
                json.append (",\"alternatePalette\":").append (pulse.alternatePalette ());
                json.append (",\"cycleBeats\":").append (pulse.cycleBeats ());
                json.append (",\"alternatePhaseStartBeats\":").append (pulse.alternatePhaseStartBeats ());
                json.append (",\"transportOffsetBeats\":").append (pulse.transportOffsetBeats ()).append ('}');
            }
            json.append ('}');
        }
        json.append ("},\"pressed\":[");
        first = true;
        for (final String control: snapshot.pressed ().stream ().sorted ().toList ())
        {
            if (!first)
                json.append (',');
            first = false;
            appendString (json, control);
        }
        json.append ("],\"events\":[");
        first = true;
        for (final InputEvent event: snapshot.inputEvents ())
        {
            if (!first)
                json.append (',');
            first = false;
            json.append ("{\"sequence\":").append (event.sequence ()).append (",\"control\":");
            appendString (json, event.control ());
            json.append (",\"kind\":");
            appendString (json, event.kind ());
            json.append (",\"phase\":");
            appendString (json, event.phase ());
            json.append (",\"value\":").append (event.value ()).append ('}');
        }
        return json.append ("]}\n").toString ();
    }


    private static void appendString (final StringBuilder json, final String value)
    {
        json.append ('\"').append (value).append ('\"');
    }


    private static String controlId (final ButtonID button)
    {
        final int padIndex = button.ordinal () - ButtonID.PAD1.ordinal () + 1;
        return padIndex >= 1 && padIndex <= 64 ? PushControlIds.pad (padIndex).value () : PushControlIds.button (button.name ()).value ();
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;
        this.publishClosed ();
        if (this.worker == null)
            this.pollFilesSafely ();
        else
            PushDebugging.shutdownWorker (this.worker, this::pollFilesSafely);
    }


    private void publishClosed ()
    {
        this.pending.set (new Snapshot (
            ++this.revision,
            false,
            this.clock,
            new TreeMap<> (this.lights),
            Set.of (),
            new ArrayList<> (this.inputEvents)));
    }


    record DebugMusicalPulse (int basePalette, ColorEx baseColor, int alternatePalette, ColorEx alternateColor, double cycleBeats, double alternatePhaseStartBeats, double transportOffsetBeats)
    {
        DebugMusicalPulse
        {
            Objects.requireNonNull (baseColor, "baseColor");
            Objects.requireNonNull (alternateColor, "alternateColor");
            if (!Double.isFinite (cycleBeats) || cycleBeats <= 0 || !Double.isFinite (alternatePhaseStartBeats) || alternatePhaseStartBeats <= 0 || alternatePhaseStartBeats >= cycleBeats || !Double.isFinite (transportOffsetBeats))
                throw new IllegalArgumentException ("Debug musical pulse must have a finite cycle, alternate boundary, and offset");
        }
    }


    private record LightState (int palette, String rgb, int blinkPalette, String blinkRgb, boolean fast, DebugMusicalPulse pulse)
    {
        private static LightState off ()
        {
            return new LightState (0, "000000", 0, null, false, null);
        }
    }


    private record InputEvent (long sequence, String control, String kind, String phase, long value)
    {
    }


    private record ClockState (boolean available, boolean playing, double tempo, double positionBeats, long sampledAtMillis)
    {
        private static ClockState unavailable ()
        {
            return new ClockState (false, false, 0, 0, 0);
        }
    }


    private record Snapshot (long revision, boolean connected, ClockState clock, Map<String, LightState> lights, Set<String> pressed, Collection<InputEvent> inputEvents)
    {
    }
}
