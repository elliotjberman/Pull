// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/** Bounded authoritative state and generation-fenced effects for the active Session bank. */
final class SessionBankHost
{
    private final SessionBankRegistry registry;

    private SessionBankSnapshot snapshot = SessionBankSnapshot.empty ();
    private TargetIdentity identity;
    private long generation;


    SessionBankHost (final SessionBankRegistry registry)
    {
        this.registry = Objects.requireNonNull (registry, "registry");
    }


    /** Sample the active eight-column Session window. */
    boolean refresh ()
    {
        final TargetIdentity currentIdentity = this.captureIdentity ();
        if (!currentIdentity.equals (this.identity))
        {
            this.identity = currentIdentity;
            this.generation++;
        }

        final SessionBankSnapshot refreshed = this.captureSnapshot (currentIdentity);
        if (refreshed.equals (this.snapshot))
            return false;
        this.snapshot = refreshed;
        return true;
    }


    SessionBankSnapshot snapshot ()
    {
        return this.snapshot;
    }


    PreparedStop prepare (final StopSessionBankEffect effect)
    {
        final StopSessionBankEffect request = Objects.requireNonNull (effect, "effect");
        if (request.targetGeneration () != this.snapshot.generation () || !request.shape ().equals (this.snapshot.shape ()))
            throw new IllegalArgumentException ("Session-bank action target is stale");
        return new PreparedStop (request.targetGeneration (), request.shape (), request.alternative ());
    }


    void apply (final PreparedStop action)
    {
        final PreparedStop request = Objects.requireNonNull (action, "action");
        final TargetIdentity live = this.captureIdentity ();
        if (request.generation () != this.generation || !request.shape ().equals (live.shape ()) || !live.equals (this.identity))
            return;
        this.registry.getActiveBank ().stop (request.alternative ());
    }


    private SessionBankSnapshot captureSnapshot (final TargetIdentity currentIdentity)
    {
        final ITrackBank bank = this.registry.getActiveBank ();
        final List<SessionTrackSnapshot> tracks = new ArrayList<> (currentIdentity.shape ().tracks ());
        for (int index = 0; index < currentIdentity.shape ().tracks (); index++)
        {
            final ITrack track = bank.getItem (index);
            if (!track.doesExist ())
            {
                tracks.add (SessionTrackSnapshot.empty ());
                continue;
            }
            tracks.add (new SessionTrackSnapshot (
                track.getChannelID (),
                track.getPosition (),
                true,
                track.isSelected (),
                track.isActivated (),
                track.isRecArm (),
                track.isMute (),
                track.isSolo (),
                track.isPlaying (),
                toRgb (track.getColor ())));
        }
        return new SessionBankSnapshot (this.generation, currentIdentity.shape (), currentIdentity.trackOffset (), currentIdentity.sceneOffset (), tracks);
    }


    private TargetIdentity captureIdentity ()
    {
        final SessionBankShape shape = this.registry.getActiveShape ();
        final ITrackBank bank = this.registry.getActiveBank ();
        final List<String> channelIds = new ArrayList<> (shape.tracks ());
        for (int index = 0; index < shape.tracks (); index++)
        {
            final ITrack track = bank.getItem (index);
            channelIds.add (track.doesExist () ? track.getChannelID () : "");
        }
        return new TargetIdentity (shape, Math.max (0, bank.getScrollPosition ()), Math.max (0, bank.getSceneBank ().getScrollPosition ()), channelIds);
    }


    private static RgbColor toRgb (final ColorEx color)
    {
        final ColorEx checked = Objects.requireNonNullElse (color, ColorEx.BLACK);
        return new RgbColor ((int) Math.round (255 * checked.getRed ()), (int) Math.round (255 * checked.getGreen ()), (int) Math.round (255 * checked.getBlue ()));
    }


    record PreparedStop (long generation, SessionBankShape shape, boolean alternative) implements ControllerBridge.PreparedAction
    {
        PreparedStop
        {
            shape = Objects.requireNonNull (shape, "shape");
        }
    }


    private record TargetIdentity (SessionBankShape shape, int trackOffset, int sceneOffset, List<String> channelIds)
    {
        private TargetIdentity
        {
            shape = Objects.requireNonNull (shape, "shape");
            channelIds = List.copyOf (Objects.requireNonNull (channelIds, "channelIds"));
        }
    }
}
