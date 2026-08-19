// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackType;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SessionBankHostTest
{
    @Test
    void publishesAuthoritativeTrackStateWithoutChangingTargetGeneration ()
    {
        final Fixture fixture = new Fixture ();

        assertTrue (fixture.host.refresh ());
        final SessionBankSnapshot initial = fixture.host.snapshot ();
        assertEquals (1, initial.generation ());
        assertEquals ("track-1", initial.tracks ().getFirst ().channelId ());
        assertEquals ("Track 1", initial.tracks ().getFirst ().name ());
        assertEquals (SessionTrackType.INSTRUMENT, initial.tracks ().getFirst ().type ());
        assertEquals (ColorEx.RED.getRed (), initial.tracks ().getFirst ().color ().red () / 255.0, 0.01);

        fixture.first.muted = true;
        assertTrue (fixture.host.refresh ());

        assertEquals (initial.generation (), fixture.host.snapshot ().generation ());
        assertTrue (fixture.host.snapshot ().tracks ().getFirst ().muted ());
    }


    @Test
    void selectionAppliesOnlyToTheExactPreparedVisibleTrack ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh ();
        final SessionBankSnapshot initial = fixture.host.snapshot ();
        final SelectSessionTrackEffect effect = new SelectSessionTrackEffect (initial.generation (), initial.shape (), 0, "track-1");
        final SessionBankHost.PreparedSelection prepared = fixture.host.prepare (effect);

        fixture.host.apply (prepared);
        assertEquals (1, fixture.first.selections);

        fixture.first.channelId = "replacement";
        fixture.host.apply (prepared);
        assertEquals (1, fixture.first.selections);

        fixture.host.refresh ();
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (effect));
    }


    @Test
    void rejectsAStopPreparedForAFormerVisibleBankIdentity ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh ();
        final SessionBankSnapshot initial = fixture.host.snapshot ();
        final StopSessionBankEffect effect = new StopSessionBankEffect (initial.generation (), initial.shape (), true);
        final SessionBankHost.PreparedStop prepared = fixture.host.prepare (effect);

        fixture.first.channelId = "replacement";
        fixture.host.apply (prepared);
        assertTrue (fixture.stopAlternatives.isEmpty ());

        fixture.host.refresh ();
        assertEquals (initial.generation () + 1, fixture.host.snapshot ().generation ());
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (effect));

        final SessionBankSnapshot replacement = fixture.host.snapshot ();
        fixture.host.apply (fixture.host.prepare (new StopSessionBankEffect (replacement.generation (), replacement.shape (), true)));
        assertEquals (List.of (Boolean.TRUE), fixture.stopAlternatives);
    }


    private static final class Fixture
    {
        private static final SessionBankShape SHAPE = new SessionBankShape (8, 8);

        private final TrackProbe first = new TrackProbe ("track-1", 0);
        private final List<Boolean> stopAlternatives = new ArrayList<> ();
        private final ITrackBank bank;
        private final SessionBankHost host;


        private Fixture ()
        {
            final List<TrackProbe> tracks = new ArrayList<> ();
            tracks.add (this.first);
            for (int index = 1; index < SHAPE.tracks (); index++)
                tracks.add (new TrackProbe ("", -1));
            final ISceneBank scenes = proxy (ISceneBank.class, (ignored, method, arguments) -> "getScrollPosition".equals (method.getName ()) ? Integer.valueOf (5) : defaultValue (method.getReturnType ()));
            this.bank = proxy (ITrackBank.class, (ignored, method, arguments) -> switch (method.getName ())
            {
                case "getItem" -> tracks.get (((Integer) arguments[0]).intValue ()).track;
                case "getScrollPosition" -> Integer.valueOf (3);
                case "getSceneBank" -> scenes;
                case "stop" -> {
                    this.stopAlternatives.add ((Boolean) arguments[0]);
                    yield null;
                }
                default -> defaultValue (method.getReturnType ());
            });
            final IModel model = proxy (IModel.class, (ignored, method, arguments) -> "getTrackBank".equals (method.getName ()) ? this.bank : defaultValue (method.getReturnType ()));
            this.host = new SessionBankHost (new SessionBankRegistry (model, Set.of (SHAPE), SHAPE));
        }
    }


    private static final class TrackProbe
    {
        private String channelId;
        private final int position;
        private boolean muted;
        private int selections;
        private final ITrack track;


        private TrackProbe (final String channelId, final int position)
        {
            this.channelId = channelId;
            this.position = position;
            this.track = proxy (ITrack.class, (ignored, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.valueOf (!this.channelId.isEmpty ());
                case "getChannelID" -> this.channelId;
                case "getPosition" -> Integer.valueOf (this.position);
                case "getName" -> "Track " + (this.position + 1);
                case "getType" -> ChannelType.INSTRUMENT;
                case "isSelected", "isActivated" -> Boolean.TRUE;
                case "isMute" -> Boolean.valueOf (this.muted);
                case "isRecArm", "isSolo", "isPlaying" -> Boolean.FALSE;
                case "getColor" -> ColorEx.RED;
                case "select" -> {
                    this.selections++;
                    yield null;
                }
                default -> defaultValue (method.getReturnType ());
            });
        }
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, handler));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive () || void.class.equals (type))
            return null;
        if (boolean.class.equals (type))
            return Boolean.FALSE;
        if (int.class.equals (type))
            return Integer.valueOf (0);
        if (long.class.equals (type))
            return Long.valueOf (0);
        if (double.class.equals (type))
            return Double.valueOf (0);
        return null;
    }
}
