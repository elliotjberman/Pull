// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Closed-loop contracts for local Push debugger navigation. */
class PushDebugNavigationHostTest
{
    @TempDir
    Path debugDirectory;


    @Test
    void projectMacrosWaitsForLaterAuthoritativeObservation () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("project-request", "project-macros");

        host.tick ();

        assertEquals (List.of ("SHIFT:DOWN", "SESSION:DOWN", "SESSION:UP", "SHIFT:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()), "gesture submission is not completion");

        surface.observe ("WORKSPACE", "WORKSPACE", true);
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "one observed sample is not yet stable");
        host.tick ();

        assertEquals (List.of ("project-request", "READY", "project-macros", "WORKSPACE", "WORKSPACE", "true", ""), this.status ());
    }


    @Test
    void mixExitsCoreWorkspaceThenUsesTheStableTrackBinding () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("WORKSPACE", "WORKSPACE", true);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("mix-request", "mix");

        host.tick ();
        assertEquals (List.of ("NOTE:DOWN", "NOTE:UP"), surface.events);

        surface.observe ("PLAY", "WORKSPACE", false);
        host.tick ();
        host.tick ();
        host.tick ();
        assertEquals (List.of ("NOTE:DOWN", "NOTE:UP", "TRACK:DOWN", "TRACK:UP"), surface.events);

        surface.observe ("PLAY", "TRACK", false);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("mix-request", "READY", "mix", "PLAY", "TRACK", "false", ""), this.status ());
    }


    @Test
    void heldPhysicalNavigationButtonIsNeverReleasedByDebugger () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        surface.pressed.add (ButtonID.SHIFT);
        this.request ("held-request", "project-macros");

        host.tick ();
        assertTrue (surface.events.isEmpty ());

        surface.pressed.clear ();
        host.tick ();
        assertEquals (List.of ("SHIFT:DOWN", "SESSION:DOWN", "SESSION:UP", "SHIFT:UP"), surface.events);
    }


    @Test
    void masterUsesThePermanentMasterBindingAndObservedMode () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("master-request", "master");

        host.tick ();
        assertEquals (List.of ("MASTERTRACK:DOWN", "MASTERTRACK:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()));

        surface.observe ("PLAY", "MASTER", false);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("master-request", "READY", "master", "PLAY", "MASTER", "false", ""), this.status ());
    }


    @Test
    void sessionRequiresTheWorkspaceToBeReleased () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("WORKSPACE", "WORKSPACE", true);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("session-request", "session");

        host.tick ();
        assertEquals (List.of ("SESSION:DOWN", "SESSION:UP"), surface.events);

        surface.observe ("SESSION", "TRACK", false);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("session-request", "READY", "session", "SESSION", "TRACK", "false", ""), this.status ());
    }


    @Test
    void unsupportedTargetFailsWithoutTouchingController () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("bad-request", "record");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        final List<String> status = this.status ();
        assertEquals ("bad-request", status.get (0));
        assertEquals ("FAILED", status.get (1));
        assertTrue (status.get (6).contains ("unsupported target"));
    }


    @Test
    void mixWaitsForEveryDrivenControlAndModifierToBeReleased () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("WORKSPACE", "WORKSPACE", true);
        final PushDebugNavigationHost host = this.host (surface);
        surface.pressed.add (ButtonID.NOTE);
        this.request ("held-mix", "mix");

        host.tick ();
        assertTrue (surface.events.isEmpty ());

        surface.pressed.clear ();
        surface.pressed.add (ButtonID.SHIFT);
        host.tick ();
        assertTrue (surface.events.isEmpty ());

        surface.pressed.clear ();
        host.tick ();
        assertEquals (List.of ("NOTE:DOWN", "NOTE:UP"), surface.events);
    }


    @Test
    void routerQuiescenceGatesGestureSubmission () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (false);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("idle-request", "master");

        host.tick ();
        assertTrue (surface.events.isEmpty ());

        admission.idle = true;
        host.tick ();
        assertEquals (List.of ("MASTERTRACK:DOWN", "MASTERTRACK:UP"), surface.events);
    }


    @Test
    void submittedGestureIsNotBlindlyRetried () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("one-shot", "master");

        for (int tick = 0; tick < 12; tick++)
            host.tick ();

        assertEquals (List.of ("MASTERTRACK:DOWN", "MASTERTRACK:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()), "the request remains pending for authoritative state");
    }


    @Test
    void sessionDoesNotAcceptTheMasterDisplayAsReady () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "MASTER", true);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("master-session", "session");

        host.tick ();
        surface.observe ("SESSION", "MASTER", false);
        host.tick ();
        host.tick ();

        assertFalse (Files.exists (this.statusPath ()), "Master mode still owns the display");
    }


    @Test
    void failedDownStillSubmitsTheMatchingRelease () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        surface.failOnDown = true;
        this.request ("failed-down", "master");

        host.tick ();

        assertEquals (List.of ("MASTERTRACK:DOWN", "MASTERTRACK:UP"), surface.events);
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void shutdownFailsPendingNavigation () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("closing-navigation", "master");
        host.tick ();

        host.close ();

        final List<String> status = this.status ();
        assertEquals ("closing-navigation", status.get (0));
        assertEquals ("FAILED", status.get (1));
        assertTrue (status.get (6).contains ("closing"));
    }


    private PushDebugNavigationHost host (final FakeNavigationSurface surface)
    {
        return new PushDebugNavigationHost (this.debugDirectory, surface);
    }


    private void request (final String requestID, final String target) throws IOException
    {
        Files.writeString (this.debugDirectory.resolve (PushDebugNavigationHost.REQUEST_FILE), requestID + "\t" + target + "\n");
    }


    private Path statusPath ()
    {
        return this.debugDirectory.resolve (PushDebugNavigationHost.STATUS_FILE);
    }


    private List<String> status () throws IOException
    {
        final String content = Files.readString (this.statusPath ());
        return List.of (content.substring (0, content.length () - 1).split ("\t", -1));
    }


    private static final class FakeNavigationSurface implements PushDebugNavigationHost.NavigationSurface
    {
        private final List<String>  events = new ArrayList<> ();
        private final Set<ButtonID> pressed = EnumSet.noneOf (ButtonID.class);
        private PushDebugNavigationHost.ObservedNavigation observed;


        private FakeNavigationSurface (final String viewID, final String modeID, final boolean workspaceActive)
        {
            this.observe (viewID, modeID, workspaceActive);
        }


        private void observe (final String viewID, final String modeID, final boolean workspaceActive)
        {
            this.observed = new PushDebugNavigationHost.ObservedNavigation (viewID, modeID, workspaceActive);
        }


        @Override
        public PushDebugNavigationHost.ObservedNavigation observe ()
        {
            return this.observed;
        }


        @Override
        public boolean isPressed (final ButtonID button)
        {
            return this.pressed.contains (button);
        }


        @Override
        public void trigger (final ButtonID button, final ButtonEvent event)
        {
            this.events.add (button + ":" + event);
            if (this.failOnDown && event == ButtonEvent.DOWN)
                throw new IllegalStateException ("synthetic down failed");
        }


        private boolean failOnDown;
    }


    private static final class FakeAdmission implements PushDebugNavigationHost.GestureAdmission
    {
        private boolean idle;


        private FakeAdmission (final boolean idle)
        {
            this.idle = idle;
        }


        @Override
        public boolean isIdle ()
        {
            return this.idle;
        }


        @Override
        public boolean trySubmit (final Runnable gesture)
        {
            if (!this.idle)
                return false;
            gesture.run ();
            return true;
        }
    }
}
