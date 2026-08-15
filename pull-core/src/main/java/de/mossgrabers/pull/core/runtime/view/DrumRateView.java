// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.NoteRepeatMode;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Core-owned momentary rate pads and automatic drum-controller roll lease. */
public final class DrumRateView implements ControllerView
{
    private static final List<ControlId> RATE_PADS = CoreControls.DRUM_RATES;
    private static final double DEFAULT_RATE = 1.0 / 4.0;
    private static final double ROLL_GATE_RATIO = 0.5;
    private static final double [] SINGLE_RATES = {2.0 / 3.0, 1.0 / 3.0, 1.0 / 6.0, 1.0 / 12.0};
    private static final double [] BETWEEN_RATES = {1.0 / 2.0, 1.0 / 4.0, 1.0 / 8.0};
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor AVAILABLE = new RgbColor (110, 82, 5);
    private static final RgbColor HELD = new RgbColor (205, 155, 8);
    private static final RgbColor ACTIVE = new RgbColor (255, 220, 30);
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.NOTE_REPEAT);
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "rates",
        Set.of (
            new SurfaceClaim (SurfaceArea.DRUM_RATE_PADS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.DRUM_RATE_PADS, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());

    private final boolean [] padsDown = new boolean [RATE_PADS.size ()];
    private final long [] pressOrder = new long [RATE_PADS.size ()];
    private long pressCounter;
    private long targetGeneration = -1;


    @Override
    public String id ()
    {
        return "drum-rates";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        final long generation = snapshot.bridge ().selectedTrack ().generation ();
        if (generation != this.targetGeneration || !isEnabled (snapshot))
        {
            this.reset ();
            this.targetGeneration = generation;
            return;
        }
        for (int index = 0; index < RATE_PADS.size (); index++)
        {
            if (!snapshot.pressedControls ().contains (RATE_PADS.get (index)))
            {
                this.padsDown[index] = false;
                this.pressOrder[index] = 0;
            }
        }
    }


    @Override
    public List<de.mossgrabers.pull.core.api.effect.CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.PAD || !isEnabled (snapshot))
            return List.of ();
        final int index = RATE_PADS.indexOf (input.controlId ());
        if (index < 0 || input.phase () != InputPhase.BEGIN && input.phase () != InputPhase.END)
            return List.of ();

        final boolean down = input.phase () == InputPhase.BEGIN;
        if (this.padsDown[index] != down)
        {
            this.padsDown[index] = down;
            this.pressOrder[index] = down ? ++this.pressCounter : 0;
        }
        return List.of ();
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final boolean enabled = isEnabled (snapshot);
        final int primary = enabled ? this.primary () : -1;
        final int secondary = enabled ? this.secondary (primary) : -1;
        final DesiredNoteRepeat desired = enabled ? desiredRepeat (rate (primary, secondary)) : DesiredNoteRepeat.unowned ();
        final boolean acknowledged = enabled && snapshot.bridge ().noteRepeat ().matches (desired);
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int index = 0; index < RATE_PADS.size (); index++)
        {
            final boolean selected = acknowledged && (index == primary || index == secondary);
            lights.put (RATE_PADS.get (index), !enabled ? OFF : selected ? ACTIVE : this.padsDown[index] ? HELD : AVAILABLE);
        }
        return new ViewOutput (lights, Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredControllerLayout.empty (), desired);
    }


    private static boolean isEnabled (final ControllerSnapshot snapshot)
    {
        return ResolvedNoteViewer.resolve (snapshot, false).automaticRollAttached () && snapshot.bridge ().noteRepeat ().available () && snapshot.bridge ().noteRepeat ().drumRollEnabled ();
    }


    private static DesiredNoteRepeat desiredRepeat (final double rate)
    {
        return new DesiredNoteRepeat (true, true, NoteRepeatMode.UP, 0, rate, ROLL_GATE_RATIO, false, false, true, true);
    }


    private int primary ()
    {
        int newest = -1;
        long order = 0;
        for (int index = 0; index < this.padsDown.length; index++)
        {
            if (this.padsDown[index] && this.pressOrder[index] > order)
            {
                newest = index;
                order = this.pressOrder[index];
            }
        }
        return newest;
    }


    private int secondary (final int primary)
    {
        if (primary < 0)
            return -1;
        final int left = primary - 1;
        final int right = primary + 1;
        if (left < 0 || !this.padsDown[left])
            return right < this.padsDown.length && this.padsDown[right] ? right : -1;
        if (right >= this.padsDown.length || !this.padsDown[right])
            return left;
        return this.pressOrder[left] > this.pressOrder[right] ? left : right;
    }


    private static double rate (final int primary, final int secondary)
    {
        if (primary < 0)
            return DEFAULT_RATE;
        if (secondary < 0)
            return SINGLE_RATES[primary];
        return BETWEEN_RATES[Math.min (primary, secondary)];
    }


    private void reset ()
    {
        Arrays.fill (this.padsDown, false);
        Arrays.fill (this.pressOrder, 0);
        this.pressCounter = 0;
    }
}
