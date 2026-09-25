// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.bitwig.framework.daw.data.ParameterImpl;
import de.mossgrabers.bitwig.framework.daw.data.SendImpl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.parameter.IParameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Initialization-owned layer/pad windows beneath an exact retained device, never UI-following banks. */
final class RetainedDeviceChannels
{
    private final List<Window> windows;

    RetainedDeviceChannels (final Device device, final DeviceLayerBank layers, final DrumPadBank pads,
                            final IValueChanger changer, final Runnable ownChanged, final Runnable sourceChanged)
    {
        this.windows = List.of (new Window (device.createLayerBank (8), layers, 8, changer, ownChanged, sourceChanged),
            new Window (device.createDrumPadBank (16), pads, 16, changer, ownChanged, sourceChanged));
    }

    void select ()
    {
        for (final Window window: this.windows)
        {
            window.bank.scrollPosition ().set (window.source.scrollPosition ().get ());
            for (int index = 0; index < window.parameters.size (); index++)
                window.bank.getItemAt (index).sendBank ().scrollPosition ().set (window.source.getItemAt (index).sendBank ().scrollPosition ().get ());
        }
    }

    boolean coherent ()
    {
        for (final Window window: this.windows)
        {
            if (window.bank.scrollPosition ().get () != window.source.scrollPosition ().get () || window.bank.itemCount ().get () != window.source.itemCount ().get ())
                return false;
            for (int index = 0; index < window.parameters.size (); index++)
            {
                final Channel child = window.bank.getItemAt (index);
                final Channel source = window.source.getItemAt (index);
                if (child.exists ().get () != source.exists ().get ()) return false;
                if (!child.exists ().get ()) continue;
                if (child.channelId ().get ().isBlank () || !child.channelId ().get ().equals (source.channelId ().get ()) ||
                    !RetainedCursorHost.sameParameter (child.volume (), source.volume ()) || !RetainedCursorHost.sameParameter (child.pan (), source.pan ()) ||
                    child.sendBank ().scrollPosition ().get () != source.sendBank ().scrollPosition ().get ()) return false;
                for (int send = 0; send < 8; send++)
                {
                    final Send left = child.sendBank ().getItemAt (send);
                    final Send right = source.sendBank ().getItemAt (send);
                    if ((left.exists ().get () || right.exists ().get ()) && !RetainedCursorHost.sameParameter (left, right)) return false;
                }
            }
        }
        return true;
    }

    Map<String, RetainedTrackParameters.TrackMix> capture (final long generation)
    {
        final Map<String, RetainedTrackParameters.TrackMix> result = new LinkedHashMap<> ();
        for (final Window window: this.windows)
            for (int index = 0; index < window.parameters.size (); index++)
            {
                final Channel channel = window.bank.getItemAt (index);
                if (!channel.exists ().get () || channel.channelId ().get ().isBlank ()) continue;
                final String id = channel.channelId ().get ();
                final int position = window.bank.scrollPosition ().get ();
                final List<IParameter> parameters = window.parameters.get (index);
                result.putIfAbsent (id, new RetainedTrackParameters.TrackMix (id, generation, parameters.get (0), parameters.get (1), parameters.subList (2, 10),
                    () -> generation, () -> channel.exists ().get () && id.equals (channel.channelId ().get ()) && position == window.bank.scrollPosition ().get ()));
            }
        return Map.copyOf (result);
    }

    private static final class Window
    {
        private final Bank<? extends Channel> bank;
        private final Bank<? extends Channel> source;
        private final List<List<IParameter>> parameters = new ArrayList<> ();

        private Window (final Bank<? extends Channel> bank, final Bank<? extends Channel> source, final int size,
                        final IValueChanger changer, final Runnable ownChanged, final Runnable sourceChanged)
        {
            this.bank = bank;
            this.source = source;
            for (final var entry: List.of (Map.entry (bank, ownChanged), Map.entry (source, sourceChanged)))
            {
                entry.getKey ().scrollPosition ().markInterested ();
                entry.getKey ().scrollPosition ().addValueObserver (ignored -> entry.getValue ().run ());
                entry.getKey ().itemCount ().markInterested ();
                entry.getKey ().itemCount ().addValueObserver (ignored -> entry.getValue ().run ());
                for (int index = 0; index < size; index++) observe (entry.getKey ().getItemAt (index), entry.getValue ());
            }
            for (int index = 0; index < size; index++)
            {
                final Channel channel = bank.getItemAt (index);
                final List<IParameter> roles = new ArrayList<> (10);
                roles.add (new ParameterImpl (changer, channel.volume ()));
                roles.add (new ParameterImpl (changer, channel.pan ()));
                for (int send = 0; send < 8; send++) roles.add (new SendImpl (changer, channel.sendBank (), send));
                this.parameters.add (List.copyOf (roles));
            }
        }

        private static void observe (final Channel channel, final Runnable changed)
        {
            channel.exists ().markInterested ();
            channel.exists ().addValueObserver (ignored -> changed.run ());
            channel.channelId ().markInterested ();
            channel.channelId ().addValueObserver (ignored -> changed.run ());
            RetainedCursorHost.markParameter (channel.volume ());
            RetainedCursorHost.markParameter (channel.pan ());
            channel.sendBank ().scrollPosition ().markInterested ();
            channel.sendBank ().scrollPosition ().addValueObserver (ignored -> changed.run ());
            channel.sendBank ().itemCount ().markInterested ();
            channel.sendBank ().itemCount ().addValueObserver (ignored -> changed.run ());
            for (int index = 0; index < 8; index++)
            {
                final Send send = channel.sendBank ().getItemAt (index);
                RetainedCursorHost.markParameter (send);
                send.exists ().addValueObserver (ignored -> changed.run ());
                send.name ().addValueObserver (ignored -> changed.run ());
            }
        }
    }
}
