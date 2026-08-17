// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewFacet;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Session clip control across either the full grid or the upper composite grid. */
public final class SessionView implements ControllerView
{
    /** Upper scene-launch facet identifier. */
    public static final String SCENE_LAUNCH = "scene-launch";

    private static final RgbColor STOP_AVAILABLE = new RgbColor (25, 0, 0);
    private static final RgbColor STOP_HELD = new RgbColor (255, 0, 0);
    private static final ControlId STOP_CLIP = PushControlIds.button ("STOP_CLIP");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.SESSION_BANK);

    private final ViewProfile profile;
    private boolean stopGestureConsumed;


    private SessionView (final ViewProfile profile)
    {
        this.profile = profile;
    }


    /** Create the inherited complete eight-by-eight Session view. */
    public static SessionView full ()
    {
        return new SessionView (fullProfile ());
    }


    /** Create the upper four-row Session view used in a composite. */
    public static SessionView upper (final boolean sceneLaunchEnabled)
    {
        return new SessionView (upperProfile (sceneLaunchEnabled));
    }


    @Override
    public String id ()
    {
        return "session-" + this.profile.id ();
    }


    @Override
    public ViewProfile profile ()
    {
        return this.profile;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        // The release snapshot no longer contains Stop Clip, so clearing here would erase a
        // Stop-plus-pad consumption immediately before the END edge is handled. Every new BEGIN
        // resets the gesture explicitly.
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final RgbColor color = snapshot.pressedControls ().contains (STOP_CLIP) ? STOP_HELD : STOP_AVAILABLE;
        return new ViewOutput (Map.of (STOP_CLIP, color), Map.of ());
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.BUTTON && input.kind () != InputKind.PAD)
            return List.of ();
        if (STOP_CLIP.equals (input.controlId ()))
            return this.handleStopButton (input, snapshot);
        if (input.phase () == InputPhase.BEGIN && snapshot.pressedControls ().contains (STOP_CLIP) && !SHIFT.equals (input.controlId ()) && !SELECT.equals (input.controlId ()))
            this.stopGestureConsumed = true;
        return List.of ();
    }


    private List<CoreEffect> handleStopButton (final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (input.phase () == InputPhase.BEGIN)
        {
            this.stopGestureConsumed = false;
            return List.of ();
        }
        if (input.phase () != InputPhase.END)
            return List.of ();
        if (this.stopGestureConsumed)
        {
            this.stopGestureConsumed = false;
            return List.of ();
        }

        if (snapshot.pressedControls ().contains (SHIFT) || snapshot.pressedControls ().contains (SELECT))
        {
            final SessionBankSnapshot bank = snapshot.bridge ().sessionBank ();
            if (!bank.shape ().isPresent ())
                return List.of ();
            final StopSessionBankEffect stopAll = new StopSessionBankEffect (bank.generation (), bank.shape (), true);
            return snapshot.pressedControls ().contains (SELECT) ? List.of (new ConsumeControllerButtonEffect (SELECT), stopAll) : List.of (stopAll);
        }

        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        if (!selected.exists ())
            return List.of ();
        return List.of (new SelectedTrackActionEffect (selected.generation (), selected.channelId (), SelectedTrackAction.STOP_IMMEDIATELY));
    }


    private static ViewProfile fullProfile ()
    {
        final Set<SurfaceClaim> claims = commonClaims (true);
        addStable (claims, SurfaceArea.GRID_UPPER);
        addStable (claims, SurfaceArea.GRID_LOWER);
        addStable (claims, SurfaceArea.SCENE_KEYS_UPPER);
        addStable (claims, SurfaceArea.SCENE_KEYS_LOWER);
        addStable (claims, SurfaceArea.NAVIGATION_ARROWS);
        addStable (claims, SurfaceArea.NAVIGATION_PAGE);
        return ViewProfile.fixed ("full", claims, Set.of (ControllerViewFacet.SESSION_GRID_FULL));
    }


    private static ViewProfile upperProfile (final boolean sceneLaunchEnabled)
    {
        final Set<SurfaceClaim> claims = commonClaims (false);
        addStable (claims, SurfaceArea.GRID_UPPER);
        final ViewFacet scenes = new ViewFacet (
            SCENE_LAUNCH,
            Set.of (
                new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
                new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
            Set.of (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER));
        return new ViewProfile (
            "upper",
            claims,
            Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER),
            Map.of (SCENE_LAUNCH, scenes),
            sceneLaunchEnabled ? Set.of (SCENE_LAUNCH) : Set.of ());
    }


    private static Set<SurfaceClaim> commonClaims (final boolean full)
    {
        final Set<SurfaceClaim> claims = new LinkedHashSet<> ();
        claims.add (new SurfaceClaim (SurfaceArea.STOP_CLIP_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT));
        claims.add (new SurfaceClaim (SurfaceArea.STOP_CLIP_BUTTON, SurfaceClaim.Kind.OUTPUT));
        claims.add (new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT));
        claims.add (new SurfaceClaim (SurfaceArea.SELECT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT));
        claims.add (new SurfaceClaim (SurfaceArea.GRID_UPPER_PAD_EDGES, SurfaceClaim.Kind.OBSERVE_INPUT));
        if (full)
            claims.add (new SurfaceClaim (SurfaceArea.GRID_LOWER_PAD_EDGES, SurfaceClaim.Kind.OBSERVE_INPUT));
        claims.add (new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OBSERVE_INPUT));
        claims.add (new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OBSERVE_INPUT));
        return claims;
    }


    private static void addStable (final Set<SurfaceClaim> claims, final SurfaceArea area)
    {
        claims.add (new SurfaceClaim (area, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT));
        claims.add (new SurfaceClaim (area, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT));
    }
}
