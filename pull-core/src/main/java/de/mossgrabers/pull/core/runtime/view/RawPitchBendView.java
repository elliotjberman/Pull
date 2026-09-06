// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Fixed touch-strip profiles. Exactly one profile is selected per composition, with one gesture
 * shared across all compositions in a core generation. The continuation profile observes legacy
 * input without interpreting it and can finish a raw gesture acquired before a workspace change.
 */
public final class RawPitchBendView implements ControllerView
{
    private final RawPitchBendGesture gesture;
    private final boolean rawProfile;
    private final ViewProfile profile;


    private RawPitchBendView (final RawPitchBendGesture gesture, final boolean rawProfile)
    {
        this.gesture = Objects.requireNonNull (gesture, "gesture");
        this.rawProfile = rawProfile;
        this.profile = ViewProfile.fixed (
            rawProfile ? "raw" : "legacy-continuation",
            Set.of (
                new SurfaceClaim (SurfaceArea.TOUCH_STRIP, rawProfile ? SurfaceClaim.Kind.EXCLUSIVE_INPUT : SurfaceClaim.Kind.OBSERVE_INPUT),
                new SurfaceClaim (SurfaceArea.TOUCH_STRIP, SurfaceClaim.Kind.OUTPUT)),
            Set.of ());
    }


    /** Claim complete raw pitch-bend input and output. */
    public static RawPitchBendView raw (final RawPitchBendGesture gesture)
    {
        return new RawPitchBendView (gesture, true);
    }


    /** Preserve frozen legacy input and finish only a previously acquired raw gesture. */
    public static RawPitchBendView legacyContinuation (final RawPitchBendGesture gesture)
    {
        return new RawPitchBendView (gesture, false);
    }


    @Override
    public String id ()
    {
        return "touch-strip";
    }


    @Override
    public ViewProfile profile ()
    {
        return this.profile;
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.gesture.reconcile (snapshot);
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        return event instanceof final ControllerInputEvent input ? this.gesture.handle (input, snapshot, this.rawProfile) : List.of ();
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (
            Map.of (), Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (), DesiredNotePerformance.inactive (), DesiredNoteRepeat.unowned (),
            DesiredControllerMappings.empty (), this.gesture.output (this.rawProfile));
    }
}
