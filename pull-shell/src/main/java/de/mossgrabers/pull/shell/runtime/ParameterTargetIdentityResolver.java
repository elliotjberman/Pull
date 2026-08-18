// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.bitwig.framework.daw.data.ParameterImpl;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IBank;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.AbstractParameterWrapper;
import de.mossgrabers.framework.parameter.IParameter;

import java.util.Objects;
import java.util.Optional;


/**
 * Resolves the host object currently addressed by one stable parameter wrapper. Cursor remote
 * controls reuse their Java wrapper while Bitwig changes the selected owner or page, so wrapper
 * identity alone is deliberately insufficient here.
 */
final class ParameterTargetIdentityResolver
{
    private final PushControlSurface surface;
    private final IModel model;


    ParameterTargetIdentityResolver (final PushControlSurface surface, final IModel model)
    {
        this.surface = Objects.requireNonNull (surface, "surface");
        this.model = Objects.requireNonNull (model, "model");
    }


    /** Resolve the semantic host identity currently behind one parameter wrapper. */
    TargetIdentity resolve (final int controlIndex, final IParameter parameter)
    {
        Objects.requireNonNull (parameter, "parameter");

        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        if (this.surface.getModeManager ().getActiveID () == Modes.TRACK)
            return this.selectedTrackMixParameter (controlIndex, cursorTrack, parameter);

        TargetIdentity identity = this.remoteParameter ("project-remote", this.model.getProject ().getName (), this.model.getProject ().getParameterBank (), parameter);
        if (identity != null)
            return identity;

        identity = this.remoteParameter ("track-remote", cursorTrack.getChannelID (), cursorTrack.getParameterBank (), parameter);
        if (identity != null)
            return identity;

        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        identity = this.remoteParameter ("device-remote", cursorDevice.getID (), cursorDevice.getParameterBank (), parameter);
        if (identity != null)
            return identity;

        identity = channelParameter (cursorTrack, parameter);
        if (identity != null)
            return identity;
        identity = channelBankParameter (this.model.getCurrentTrackBank (), parameter);
        if (identity != null)
            return identity;
        identity = channelBankParameter (cursorDevice.getLayerBank (), parameter);
        if (identity != null)
            return identity;
        identity = channelBankParameter (cursorDevice.getDrumPadBank (), parameter);
        if (identity != null)
            return identity;

        // An unclassified Bitwig proxy may be movable. Excluding it is safer than claiming that
        // its stable Java wrapper is an exact actuator lease.
        if (parameter instanceof ParameterImpl)
            return null;

        final Object activeMode = this.surface.getModeManager ().getActiveID ();
        return new TargetIdentity (
            "stable-wrapper",
            activeMode == null ? "" : activeMode.toString (),
            parameter.getPosition (),
            controlIndex,
            parameter.getName ());
    }


    private TargetIdentity selectedTrackMixParameter (final int controlIndex, final ICursorTrack track, final IParameter parameter)
    {
        if (track == null || !track.doesExist () || Objects.requireNonNullElse (track.getChannelID (), "").isBlank ())
            return null;

        // TrackMode binds from the current bank while selected-track state is observed through a
        // private selection-following cursor. The two proxies are deliberately different. Match
        // the physical binding against its actual bank owner, then fence that owner to the cursor
        // by stable channel identity before exposing it to core.
        final ITrackBank tracks = this.model.getCurrentTrackBank ();
        final Optional<ITrack> selected = tracks == null ? Optional.empty () : tracks.getSelectedItem ();
        if (selected.isEmpty ())
            return null;
        final ITrack boundTrack = selected.get ();
        if (!boundTrack.doesExist () || !Objects.equals (boundTrack.getChannelID (), track.getChannelID ()))
            return null;

        final IParameter unwrapped = unwrap (parameter);
        if (controlIndex == 0 && boundTrack.getVolumeParameter () == unwrapped)
            return channelIdentity (boundTrack, "volume", 0, unwrapped);
        if (controlIndex == 1 && boundTrack.getPanParameter () == unwrapped)
            return channelIdentity (boundTrack, "pan", 0, unwrapped);
        if (controlIndex < 2)
            return null;

        final int sendIndex = this.surface.getConfiguration ().getTrackMixSendOffset () + controlIndex - 2;
        final IBank<? extends IParameter> sends = boundTrack.getSendBank ();
        if (sends == null || sendIndex < 0 || sendIndex >= sends.getPageSize () || sends.getItem (sendIndex) != unwrapped)
            return null;
        return channelIdentity (boundTrack, "send", sendIndex + sends.getScrollPosition (), unwrapped);
    }


    private static IParameter unwrap (final IParameter parameter)
    {
        IParameter unwrapped = parameter;
        while (unwrapped instanceof final AbstractParameterWrapper wrapper)
            unwrapped = wrapper.getWrappedParameter ();
        return unwrapped;
    }


    /** Resolve one slot in a remote-control bank. */
    TargetIdentity remote (final String domain, final String ownerId, final IParameterBank bank, final int index)
    {
        if (bank == null || index < 0 || index >= bank.getPageSize ())
            return null;
        return this.remoteParameter (domain, ownerId, bank, bank.getItem (index));
    }


    /** Resolve one channel parameter by its stable channel identity and semantic role. */
    TargetIdentity channel (final IChannel channel, final IParameter parameter)
    {
        return channelParameter (channel, parameter);
    }


    private TargetIdentity remoteParameter (final String domain, final String ownerId, final IParameterBank bank, final IParameter parameter)
    {
        if (bank == null)
            return null;
        for (int index = 0; index < bank.getPageSize (); index++)
        {
            if (bank.getItem (index) != parameter)
                continue;
            final String checkedOwner = Objects.requireNonNullElse (ownerId, "");
            if (checkedOwner.isBlank ())
                return null;
            return new TargetIdentity (domain, checkedOwner, bank.getPageBank ().getSelectedItemPosition (), index, parameter.getName ());
        }
        return null;
    }


    private static TargetIdentity channelBankParameter (final IBank<? extends IChannel> bank, final IParameter parameter)
    {
        if (bank == null)
            return null;
        for (int index = 0; index < bank.getPageSize (); index++)
        {
            final TargetIdentity identity = channelParameter (bank.getItem (index), parameter);
            if (identity != null)
                return identity;
        }
        return null;
    }


    private static TargetIdentity channelParameter (final IChannel channel, final IParameter parameter)
    {
        if (channel == null || !channel.doesExist () || Objects.requireNonNullElse (channel.getChannelID (), "").isBlank ())
            return null;
        if (channel.getVolumeParameter () == parameter)
            return channelIdentity (channel, "volume", 0, parameter);
        if (channel.getPanParameter () == parameter)
            return channelIdentity (channel, "pan", 0, parameter);
        if (channel instanceof final ITrack track && track.getCrossfadeParameter () == parameter)
            return channelIdentity (channel, "crossfade", 0, parameter);

        final IBank<? extends IParameter> sends = channel.getSendBank ();
        if (sends != null)
        {
            for (int index = 0; index < sends.getPageSize (); index++)
            {
                if (sends.getItem (index) == parameter)
                    return channelIdentity (channel, "send", index + sends.getScrollPosition (), parameter);
            }
        }
        return null;
    }


    private static TargetIdentity channelIdentity (final IChannel channel, final String role, final int index, final IParameter parameter)
    {
        return new TargetIdentity ("channel-" + role, channel.getChannelID (), 0, index, parameter.getName ());
    }


    record TargetIdentity (String domain, String ownerId, int page, int index, String parameterName)
    {
        TargetIdentity
        {
            domain = Objects.requireNonNull (domain, "domain");
            ownerId = Objects.requireNonNull (ownerId, "ownerId");
            parameterName = Objects.requireNonNullElse (parameterName, "");
        }
    }
}
