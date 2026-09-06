// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionClipWindowSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SessionClipWindowHostTest
{
    @Test
    void trackOnlySubscriptionNeverReadsSlotOrSceneItems ()
    {
        final Fixture fixture = new Fixture (8);
        fixture.host.refresh (false);
        assertEquals (0, fixture.slotReads);
        assertEquals (0, fixture.sceneReads);
        assertEquals (SessionClipWindowSnapshot.empty (), fixture.host.snapshot ().clips ());

        fixture.host.refresh (true);
        assertEquals (64, fixture.slotReads);
        assertEquals (8, fixture.sceneReads);

        assertTrue (fixture.host.refresh (false));
        assertEquals (64, fixture.slotReads);
        assertEquals (8, fixture.sceneReads);
        assertEquals (SessionClipWindowSnapshot.empty (), fixture.host.snapshot ().clips ());
    }


    @ParameterizedTest
    @ValueSource (ints = { 4, 8 })
    void publishesTheExactBoundedWindowAndNavigation (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.host.refresh (true);
        final SessionClipWindowSnapshot clips = fixture.host.snapshot ().clips ();
        assertEquals (new SessionBankShape (8, rows), clips.shape ());
        assertEquals (8 * rows, clips.slots ().size ());
        assertEquals (rows, clips.scenes ().size ());
        assertTrue (clips.aligned ());
        assertEquals (fixture.sceneOffset + rows - 1, clips.slot (7, rows - 1).scenePosition ());
        assertEquals ("7:" + (rows - 1), clips.slot (7, rows - 1).name ());
        assertTrue (clips.scenes ().getFirst ().selected ());
        assertEquals (32, clips.trackNavigation ().itemCount ());
        assertTrue (clips.trackNavigation ().previousPage ());
        assertFalse (clips.sceneNavigation ().nextPage ());
        assertThrows (IndexOutOfBoundsException.class, () -> clips.slot (8, 0));
        assertThrows (IndexOutOfBoundsException.class, () -> clips.slot (0, rows));
        assertThrows (UnsupportedOperationException.class, () -> clips.slots ().clear ());
    }


    @Test
    void commandSubmissionDoesNotBecomePlaybackUntilLaterHostAdvancement ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final long generation = fixture.host.snapshot ().generation ();
        fixture.slots[0][0].launch (true, false);
        assertEquals (1, fixture.launchRequests);
        assertFalse (fixture.host.refresh (true));
        assertFalse (fixture.host.snapshot ().clips ().slot (0, 0).playing ());

        fixture.advanceHost ();
        assertTrue (fixture.host.refresh (true));
        assertTrue (fixture.host.snapshot ().clips ().slot (0, 0).playing ());
        assertEquals (generation, fixture.host.snapshot ().generation ());
    }


    @Test
    void mixedProxyLocationsRemainUnalignedUntilReadBackCatchesUp ()
    {
        final Fixture fixture = new Fixture (4);
        fixture.host.refresh (true);
        final long generation = fixture.host.snapshot ().generation ();
        fixture.sceneOffset++;
        fixture.host.refresh (true);
        assertEquals (generation + 1, fixture.host.snapshot ().generation ());
        assertFalse (fixture.host.snapshot ().clips ().aligned ());
        assertEquals (5, fixture.host.snapshot ().clips ().slot (0, 0).scenePosition ());

        fixture.proxySceneOffset = fixture.sceneOffset;
        fixture.host.refresh (true);
        assertTrue (fixture.host.snapshot ().clips ().aligned ());
        assertEquals (6, fixture.host.snapshot ().clips ().slot (0, 0).scenePosition ());
    }


    private static final class Fixture
    {
        private int sceneOffset = 5;
        private int proxySceneOffset = 5;
        private int slotReads;
        private int sceneReads;
        private int launchRequests;
        private boolean playing;
        private final ISlot[][] slots;
        private final SessionBankHost host;


        private Fixture (final int rows)
        {
            final SessionBankShape shape = new SessionBankShape (8, rows);
            this.slots = new ISlot[8][rows];
            final ITrack[] tracks = new ITrack[8];
            final IScene[] scenes = new IScene[rows];
            for (int row = 0; row < rows; row++)
            {
                final int sceneIndex = row;
                scenes[row] = proxy (IScene.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "doesExist" -> true;
                    case "getPosition" -> this.proxySceneOffset + sceneIndex;
                    case "getName" -> "Scene " + sceneIndex;
                    case "isSelected" -> sceneIndex == 0;
                    case "getColor" -> ColorEx.RED;
                    default -> defaultValue (method.getReturnType ());
                });
            }
            for (int column = 0; column < 8; column++)
            {
                final int trackIndex = column;
                for (int row = 0; row < rows; row++)
                {
                    final int sceneIndex = row;
                    this.slots[column][row] = proxy (ISlot.class, (ignored, method, args) -> switch (method.getName ())
                    {
                        case "doesExist", "hasContent" -> true;
                        case "getPosition" -> this.proxySceneOffset + sceneIndex;
                        case "getName" -> trackIndex + ":" + sceneIndex;
                        case "getColor" -> ColorEx.BLUE;
                        case "isPlaying" -> trackIndex == 0 && sceneIndex == 0 && this.playing;
                        case "launch" -> {
                            this.launchRequests++;
                            yield null;
                        }
                        default -> defaultValue (method.getReturnType ());
                    });
                }
                final ISlotBank slotBank = proxy (ISlotBank.class, (ignored, method, args) -> {
                    if ("getItem".equals (method.getName ()))
                    {
                        this.slotReads++;
                        return this.slots[trackIndex][(Integer) args[0]];
                    }
                    return defaultValue (method.getReturnType ());
                });
                tracks[column] = proxy (ITrack.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "doesExist", "isActivated" -> true;
                    case "getChannelID" -> "track-" + trackIndex;
                    case "getPosition" -> 3 + trackIndex;
                    case "getName" -> "Track " + trackIndex;
                    case "getType" -> ChannelType.INSTRUMENT;
                    case "getColor" -> ColorEx.BLUE;
                    case "getSlotBank" -> slotBank;
                    default -> defaultValue (method.getReturnType ());
                });
            }
            final ISceneBank sceneBank = proxy (ISceneBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> {
                    this.sceneReads++;
                    yield scenes[(Integer) args[0]];
                }
                case "getScrollPosition" -> this.sceneOffset;
                case "getItemCount" -> 40;
                case "canScrollBackwards", "canScrollForwards", "canScrollPageBackwards" -> true;
                default -> defaultValue (method.getReturnType ());
            });
            final ITrackBank bank = proxy (ITrackBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> tracks[(Integer) args[0]];
                case "getScrollPosition" -> 3;
                case "getItemCount" -> 32;
                case "getSceneBank" -> sceneBank;
                case "canScrollBackwards", "canScrollForwards", "canScrollPageBackwards", "canScrollPageForwards" -> true;
                default -> defaultValue (method.getReturnType ());
            });
            final IModel model = proxy (IModel.class, (ignored, method, args) -> "getTrackBank".equals (method.getName ()) ? bank : defaultValue (method.getReturnType ()));
            this.host = new SessionBankHost (new SessionBankRegistry (model, Set.of (shape), shape));
        }


        private void advanceHost ()
        {
            this.playing = this.launchRequests > 0;
        }
    }


    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?>[] { type }, handler));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == double.class)
            return 0.0;
        return null;
    }
}
