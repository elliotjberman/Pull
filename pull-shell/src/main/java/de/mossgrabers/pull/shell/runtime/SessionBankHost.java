// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.bitwig.framework.daw.data.SlotImpl;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.bank.IBank;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.BankNavigationSnapshot;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionClipWindowSnapshot;
import de.mossgrabers.pull.core.api.SessionLocation;
import de.mossgrabers.pull.core.api.SessionSceneSnapshot;
import de.mossgrabers.pull.core.api.SessionSlotSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackType;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.SessionActionEffect;
import de.mossgrabers.pull.core.api.effect.CopySessionClipEffect;
import de.mossgrabers.pull.core.api.effect.CreateSessionClipEffect;
import de.mossgrabers.pull.core.api.effect.SetSessionBankPositionEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionTrackEffect;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;


/** Bounded authoritative state and generation-fenced effects for the active Session bank. */
final class SessionBankHost
{
    private final SessionBankRegistry registry;
    private final Supplier<String> projectIdentity;
    private final Consumer<String> cleanupDiagnostic;
    private final Map<SessionLocation, Boolean> outstandingLaunches = new LinkedHashMap<> ();

    private SessionBankSnapshot snapshot = SessionBankSnapshot.empty ();
    private TargetIdentity identity;
    private long generation;
    private PendingPosition pendingPosition;


    SessionBankHost (final SessionBankRegistry registry, final Supplier<String> projectIdentity, final Consumer<String> cleanupDiagnostic)
    {
        this.registry = Objects.requireNonNull (registry, "registry");
        this.projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        this.cleanupDiagnostic = Objects.requireNonNull (cleanupDiagnostic, "cleanupDiagnostic");
    }


    /** Sample the active eight-column Session window. */
    boolean refresh ()
    {
        return this.refresh (false);
    }


    /** Sample clips/scenes only when their separate subscription is requested. */
    boolean refresh (final boolean clipsRequested)
    {
        final TargetIdentity currentIdentity = this.captureIdentity ();
        if (this.pendingPosition != null && (!this.pendingPosition.projectIdentity ().equals (currentIdentity.projectIdentity ()) || !this.pendingPosition.shape ().equals (currentIdentity.shape ())))
            this.pendingPosition = null;
        if (!currentIdentity.equals (this.identity))
        {
            this.releaseOutstanding ();
            this.identity = currentIdentity;
            this.generation++;
        }

        final SessionBankSnapshot refreshed = this.captureSnapshot (currentIdentity, clipsRequested);
        if (refreshed.equals (this.snapshot))
            return false;
        this.snapshot = refreshed;
        return true;
    }


    SessionBankSnapshot snapshot ()
    {
        return this.snapshot;
    }


    PreparedLauncherAction prepare (final SessionActionEffect effect)
    {
        Objects.requireNonNull (effect, "effect");
        return new PreparedLauncherAction (effect, this.locationIsCurrent (effect.target (), !effect.isRelease ()));
    }


    PreparedCopy prepare (final CopySessionClipEffect effect)
    {
        Objects.requireNonNull (effect, "effect");
        return new PreparedCopy (effect, this.locationIsCurrent (effect.source (), true) && this.locationIsCurrent (effect.target (), true));
    }


    PreparedCreate prepare (final CreateSessionClipEffect effect)
    {
        Objects.requireNonNull (effect, "effect");
        return new PreparedCreate (effect, this.locationIsCurrent (effect.target (), true));
    }


    PreparedPosition prepare (final SetSessionBankPositionEffect effect)
    {
        Objects.requireNonNull (effect, "effect");
        return new PreparedPosition (effect, this.positionIsCurrent (effect));
    }


    void apply (final PreparedLauncherAction prepared)
    {
        final SessionActionEffect effect = prepared.effect ();
        final SessionLocation target = effect.target ();
        final SessionActionEffect.Action action = effect.action ();
        if (effect.isRelease ())
        {
            final Boolean acquired = this.outstandingLaunches.get (target);
            if (acquired == null || acquired.booleanValue () != (action == SessionActionEffect.Action.RELEASE_ALT))
                return;
            this.outstandingLaunches.remove (target);
        }
        if (!prepared.valid () || !this.locationIsCurrent (target, false))
        {
            if (effect.isRelease ())
                this.cleanupDiagnostic.accept ("Session release unavailable: " + target + ", " + action);
            return;
        }
        final int row = target.scenePosition () - this.snapshot.sceneOffset ();
        final boolean alternate = action == SessionActionEffect.Action.LAUNCH_ALT || action == SessionActionEffect.Action.RELEASE_ALT;
        if (target.isScene ())
        {
            final IScene scene = this.registry.getActiveBank ().getSceneBank ().getItem (row);
            switch (action)
            {
                case LAUNCH, LAUNCH_ALT -> scene.launch (true, alternate);
                case RELEASE, RELEASE_ALT -> scene.launch (false, alternate);
                case SELECT -> scene.select ();
                case REMOVE -> scene.remove ();
                case DUPLICATE -> scene.duplicate ();
                default -> throw new IllegalArgumentException ("Operation requires a Session slot");
            }
        }
        else
        {
            final ISlot slot = this.slot (target);
            switch (action)
            {
                case LAUNCH, LAUNCH_ALT -> slot.launch (true, alternate);
                case RELEASE, RELEASE_ALT -> slot.launch (false, alternate);
                case SELECT -> slot.select ();
                case REMOVE -> slot.remove ();
                case DUPLICATE -> throw new IllegalArgumentException ("Operation requires a Session scene");
                case START_RECORDING -> slot.startRecording ();
                case BROWSE -> ((SlotImpl) slot).getSlot ().replaceInsertionPoint ().browse ();
            }
        }
        if (action == SessionActionEffect.Action.LAUNCH || action == SessionActionEffect.Action.LAUNCH_ALT)
            this.outstandingLaunches.put (target, Boolean.valueOf (alternate));
    }


    void apply (final PreparedCopy prepared)
    {
        final CopySessionClipEffect effect = prepared.effect ();
        if (prepared.valid () && this.locationIsCurrent (effect.source (), false) && this.locationIsCurrent (effect.target (), false))
        {
            final ISlot source = this.slot (effect.source ());
            if (source.hasContent ())
                this.slot (effect.target ()).paste (source);
        }
    }


    void apply (final PreparedCreate prepared)
    {
        final CreateSessionClipEffect effect = prepared.effect ();
        if (prepared.valid () && this.locationIsCurrent (effect.target (), false) && !this.slot (effect.target ()).hasContent ())
            this.registry.getActiveBank ().getItem (effect.target ().trackIndex ()).createClip (effect.target ().scenePosition () - this.snapshot.sceneOffset (), effect.lengthBeats ());
    }


    void apply (final PreparedPosition prepared)
    {
        final SetSessionBankPositionEffect effect = prepared.effect ();
        if (!prepared.valid () || !this.positionIsCurrent (effect))
            return;
        final ITrackBank bank = this.registry.getActiveBank ();
        final PendingPosition pending = new PendingPosition (this.identity.projectIdentity (), effect.shape (), effect.trackPosition () < 0 ? this.identity.trackOffset () : effect.trackPosition (), effect.scenePosition () < 0 ? this.identity.sceneOffset () : effect.scenePosition ());
        try
        {
            if (effect.trackPosition () >= 0)
                bank.scrollTo (effect.trackPosition (), false);
            if (effect.scenePosition () >= 0)
                bank.getSceneBank ().scrollTo (effect.scenePosition (), false);
        }
        finally
        {
            this.pendingPosition = pending;
            this.generation++;
            this.snapshot = SessionBankSnapshot.empty ();
        }
    }


    /** Submit exact releases before rebinding; a void API return is not playback acknowledgement. */
    void releaseOutstanding ()
    {
        final Map<SessionLocation, Boolean> releases = new LinkedHashMap<> (this.outstandingLaunches);
        RuntimeException failure = null;
        for (final Map.Entry<SessionLocation, Boolean> entry: releases.entrySet ())
        {
            final SessionActionEffect effect = new SessionActionEffect (entry.getKey (), entry.getValue ().booleanValue () ? SessionActionEffect.Action.RELEASE_ALT : SessionActionEffect.Action.RELEASE);
            try
            {
                this.apply (new PreparedLauncherAction (effect, true));
            }
            catch (final RuntimeException exception)
            {
                if (failure == null)
                    failure = exception;
                else
                    failure.addSuppressed (exception);
            }
        }
        if (failure != null)
            throw failure;
    }


    /** Retire abandoned host requests even if best-effort release submission fails. */
    void invalidate ()
    {
        try
        {
            this.releaseOutstanding ();
        }
        finally
        {
            this.pendingPosition = null;
            this.identity = null;
            this.generation++;
            this.snapshot = SessionBankSnapshot.empty ();
        }
    }


    private boolean bankIsCurrent (final long generation, final SessionBankShape shape)
    {
        final ITrackBank bank = this.registry.getActiveBank ();
        return this.pendingPosition == null && this.identity != null && !this.identity.projectIdentity ().isBlank () && generation == this.generation && shape.equals (this.snapshot.shape ()) && bank.getScrollPosition () >= 0 && bank.getSceneBank ().getScrollPosition () >= 0 && this.captureIdentity ().equals (this.identity);
    }


    private boolean positionIsCurrent (final SetSessionBankPositionEffect effect)
    {
        final ITrackBank bank = this.registry.getActiveBank ();
        return effect.trackPosition () < bank.getItemCount () && effect.scenePosition () < bank.getSceneBank ().getItemCount () && this.bankIsCurrent (effect.generation (), effect.shape ());
    }


    private boolean locationIsCurrent (final SessionLocation target, final boolean requireObservedWindow)
    {
        if (!this.bankIsCurrent (target.generation (), target.shape ()) || !target.projectIdentity ().equals (this.identity.projectIdentity ()) || requireObservedWindow && !this.snapshot.clips ().aligned ())
            return false;
        final int row = target.scenePosition () - this.snapshot.sceneOffset ();
        if (row < 0 || row >= target.shape ().scenes ())
            return false;
        final ITrackBank bank = this.registry.getActiveBank ();
        if (target.isScene ())
        {
            final IScene scene = bank.getSceneBank ().getItem (row);
            return scene.doesExist () && scene.getPosition () == target.scenePosition ();
        }
        final ITrack track = bank.getItem (target.trackIndex ());
        if (!track.doesExist () || !target.channelId ().equals (track.getChannelID ()) || track.getPosition () != this.snapshot.trackOffset () + target.trackIndex ())
            return false;
        final ISlotBank slots = track.getSlotBank ();
        if (slots.getScrollPosition () != this.snapshot.sceneOffset ())
            return false;
        final ISlot slot = slots.getItem (row);
        return slot.getPosition () == target.scenePosition () || !slot.doesExist () && slot.getPosition () < 0;
    }


    private ISlot slot (final SessionLocation target)
    {
        return this.registry.getActiveBank ().getItem (target.trackIndex ()).getSlotBank ().getItem (target.scenePosition () - this.snapshot.sceneOffset ());
    }


    PreparedStop prepare (final StopSessionBankEffect effect)
    {
        final StopSessionBankEffect request = Objects.requireNonNull (effect, "effect");
        if (request.targetGeneration () != this.snapshot.generation () || !request.shape ().equals (this.snapshot.shape ()))
            throw new IllegalArgumentException ("Session-bank action target is stale");
        return new PreparedStop (request.targetGeneration (), request.shape (), request.alternative ());
    }


    PreparedSelection prepare (final SelectSessionTrackEffect effect)
    {
        final SelectSessionTrackEffect request = Objects.requireNonNull (effect, "effect");
        if (request.targetGeneration () != this.snapshot.generation () || !request.shape ().equals (this.snapshot.shape ()))
            throw new IllegalArgumentException ("Session-bank action target is stale");
        final SessionTrackSnapshot track = this.snapshot.tracks ().get (request.trackIndex ());
        if (!track.exists () || !track.channelId ().equals (request.channelId ()))
            throw new IllegalArgumentException ("Session track selection target is stale");
        return new PreparedSelection (request.targetGeneration (), request.shape (), request.trackIndex (), request.channelId ());
    }


    PreparedTrackStop prepare (final StopSessionTrackEffect effect)
    {
        final StopSessionTrackEffect request = Objects.requireNonNull (effect, "effect");
        this.requireTrack (request.targetGeneration (), request.shape (), request.trackIndex (), request.channelId (), "Session track stop target is stale");
        return new PreparedTrackStop (request.targetGeneration (), request.shape (), request.trackIndex (), request.channelId (), request.alternative ());
    }


    void apply (final PreparedStop action)
    {
        final PreparedStop request = Objects.requireNonNull (action, "action");
        final TargetIdentity live = this.captureIdentity ();
        if (request.generation () != this.generation || !request.shape ().equals (live.shape ()) || !live.equals (this.identity))
            return;
        this.registry.getActiveBank ().stop (request.alternative ());
    }


    void apply (final PreparedSelection action)
    {
        final PreparedSelection request = Objects.requireNonNull (action, "action");
        final TargetIdentity live = this.captureIdentity ();
        if (request.generation () != this.generation || !request.shape ().equals (live.shape ()) || !live.equals (this.identity))
            return;
        final ITrack track = this.registry.getActiveBank ().getItem (request.trackIndex ());
        if (track.doesExist () && request.channelId ().equals (track.getChannelID ()))
            track.select ();
    }


    void apply (final PreparedTrackStop action)
    {
        final PreparedTrackStop request = Objects.requireNonNull (action, "action");
        final TargetIdentity live = this.captureIdentity ();
        if (request.generation () != this.generation || !request.shape ().equals (live.shape ()) || !live.equals (this.identity))
            return;
        final ITrack track = this.registry.getActiveBank ().getItem (request.trackIndex ());
        if (track.doesExist () && request.channelId ().equals (track.getChannelID ()))
            track.stop (request.alternative ());
    }


    private SessionTrackSnapshot requireTrack (final long generation, final SessionBankShape shape, final int trackIndex, final String channelId, final String staleMessage)
    {
        if (generation != this.snapshot.generation () || !shape.equals (this.snapshot.shape ()))
            throw new IllegalArgumentException ("Session-bank action target is stale");
        final SessionTrackSnapshot track = this.snapshot.tracks ().get (trackIndex);
        if (!track.exists () || !track.channelId ().equals (channelId))
            throw new IllegalArgumentException (staleMessage);
        return track;
    }


    private SessionBankSnapshot captureSnapshot (final TargetIdentity currentIdentity, final boolean clipsRequested)
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
                track.getName (16),
                true,
                track.isSelected (),
                track.isActivated (),
                track.isRecArm (),
                track.isMute (),
                track.isSolo (),
                track.isPlaying (),
                toTrackType (track.getType ()),
                toRgb (track.getColor ())));
        }
        final SessionClipWindowSnapshot clips = clipsRequested ? this.captureClips (currentIdentity, tracks) : SessionClipWindowSnapshot.empty ();
        return new SessionBankSnapshot (this.generation, currentIdentity.shape (), currentIdentity.trackOffset (), currentIdentity.sceneOffset (), tracks, clips);
    }


    private SessionClipWindowSnapshot captureClips (final TargetIdentity currentIdentity, final List<SessionTrackSnapshot> tracks)
    {
        final ITrackBank bank = this.registry.getActiveBank ();
        final ISceneBank sceneBank = bank.getSceneBank ();
        final SessionBankShape shape = currentIdentity.shape ();
        final List<SessionSceneSnapshot> scenes = new ArrayList<> (shape.scenes ());
        boolean aligned = bank.getScrollPosition () >= 0 && sceneBank.getScrollPosition () >= 0;
        for (int row = 0; row < shape.scenes (); row++)
        {
            final IScene scene = sceneBank.getItem (row);
            if (!scene.doesExist ())
            {
                scenes.add (SessionSceneSnapshot.empty ());
                continue;
            }
            final int position = scene.getPosition ();
            aligned &= position == currentIdentity.sceneOffset () + row;
            if (position < 0)
                scenes.add (SessionSceneSnapshot.empty ());
            else
                scenes.add (new SessionSceneSnapshot (true, position, scene.getName (128), scene.isSelected (), toRgb (scene.getColor ())));
        }
        final List<SessionSlotSnapshot> slots = new ArrayList<> (shape.tracks () * shape.scenes ());
        for (int column = 0; column < shape.tracks (); column++)
        {
            final SessionTrackSnapshot track = tracks.get (column);
            final ISlotBank slotBank = track.exists () ? bank.getItem (column).getSlotBank () : null;
            if (track.exists ())
                aligned &= track.position () == currentIdentity.trackOffset () + column && slotBank.getScrollPosition () == currentIdentity.sceneOffset ();
            for (int row = 0; row < shape.scenes (); row++)
            {
                final ISlot slot = slotBank == null ? null : slotBank.getItem (row);
                if (slot == null || !slot.doesExist ())
                {
                    slots.add (SessionSlotSnapshot.empty ());
                    continue;
                }
                final int position = slot.getPosition ();
                aligned &= position == currentIdentity.sceneOffset () + row;
                if (position < 0)
                    slots.add (SessionSlotSnapshot.empty ());
                else
                    slots.add (new SessionSlotSnapshot (true, position, slot.getName (128), slot.hasContent (), slot.isSelected (), slot.isMuted (), slot.isPlaying (), slot.isRecording (), slot.isPlayingQueued (), slot.isRecordingQueued (), slot.isStopQueued (), toRgb (slot.getColor ())));
            }
        }
        if (this.pendingPosition != null)
        {
            if (aligned && this.pendingPosition.trackOffset () == currentIdentity.trackOffset () && this.pendingPosition.sceneOffset () == currentIdentity.sceneOffset ())
                this.pendingPosition = null;
            else
                aligned = false;
        }
        return new SessionClipWindowSnapshot (shape, aligned, slots, scenes, captureNavigation (bank), captureNavigation (sceneBank));
    }


    private static BankNavigationSnapshot captureNavigation (final IBank<?> bank)
    {
        return new BankNavigationSnapshot (Math.max (0, bank.getItemCount ()), bank.canScrollBackwards (), bank.canScrollForwards (), bank.canScrollPageBackwards (), bank.canScrollPageForwards ());
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
        return new TargetIdentity (Objects.requireNonNullElse (this.projectIdentity.get (), ""), shape, Math.max (0, bank.getScrollPosition ()), Math.max (0, bank.getSceneBank ().getScrollPosition ()), channelIds);
    }


    static RgbColor toRgb (final ColorEx color)
    {
        final ColorEx checked = Objects.requireNonNullElse (color, ColorEx.BLACK);
        return new RgbColor ((int) Math.round (255 * checked.getRed ()), (int) Math.round (255 * checked.getGreen ()), (int) Math.round (255 * checked.getBlue ()));
    }


    static SessionTrackType toTrackType (final de.mossgrabers.framework.daw.resource.ChannelType type)
    {
        return switch (Objects.requireNonNullElse (type, de.mossgrabers.framework.daw.resource.ChannelType.UNKNOWN))
        {
            case UNKNOWN -> SessionTrackType.UNKNOWN;
            case AUDIO -> SessionTrackType.AUDIO;
            case INSTRUMENT -> SessionTrackType.INSTRUMENT;
            case HYBRID -> SessionTrackType.HYBRID;
            case GROUP -> SessionTrackType.GROUP;
            case GROUP_OPEN -> SessionTrackType.GROUP_OPEN;
            case EFFECT -> SessionTrackType.EFFECT;
            case MASTER -> SessionTrackType.MASTER;
            case LAYER -> SessionTrackType.LAYER;
            case CUE -> SessionTrackType.CUE;
        };
    }


    record PreparedStop (long generation, SessionBankShape shape, boolean alternative) implements ControllerBridge.PreparedAction
    {
        PreparedStop
        {
            shape = Objects.requireNonNull (shape, "shape");
        }
    }


    record PreparedSelection (long generation, SessionBankShape shape, int trackIndex, String channelId) implements ControllerBridge.PreparedAction
    {
        PreparedSelection
        {
            shape = Objects.requireNonNull (shape, "shape");
            channelId = Objects.requireNonNull (channelId, "channelId");
        }
    }


    record PreparedTrackStop (long generation, SessionBankShape shape, int trackIndex, String channelId, boolean alternative) implements ControllerBridge.PreparedAction
    {
        PreparedTrackStop
        {
            shape = Objects.requireNonNull (shape, "shape");
            channelId = Objects.requireNonNull (channelId, "channelId");
        }
    }


    record PreparedLauncherAction (SessionActionEffect effect, boolean valid) implements ControllerBridge.PreparedAction { }

    record PreparedCopy (CopySessionClipEffect effect, boolean valid) implements ControllerBridge.PreparedAction { }

    record PreparedCreate (CreateSessionClipEffect effect, boolean valid) implements ControllerBridge.PreparedAction { }

    record PreparedPosition (SetSessionBankPositionEffect effect, boolean valid) implements ControllerBridge.PreparedAction { }

    private record PendingPosition (String projectIdentity, SessionBankShape shape, int trackOffset, int sceneOffset) { }


    private record TargetIdentity (String projectIdentity, SessionBankShape shape, int trackOffset, int sceneOffset, List<String> channelIds)
    {
        private TargetIdentity
        {
            shape = Objects.requireNonNull (shape, "shape");
            channelIds = List.copyOf (Objects.requireNonNull (channelIds, "channelIds"));
        }
    }
}
