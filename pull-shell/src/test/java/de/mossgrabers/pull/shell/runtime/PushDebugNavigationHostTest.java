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
    private static final String MIX_EXIT_WORKSPACE = "NOTE/workspace=false";
    private static final String MIX                = "TRACK/mode=TRACK,workspace=false";
    private static final String MASTER             = "MASTERTRACK/mode=MASTER|MASTER_TEMP";
    private static final String PROJECT_MACROS     = "SHIFT+SESSION/view=WORKSPACE,mode=WORKSPACE,workspace=true";
    private static final String SESSION            = "SESSION/view=SESSION,mode!=WORKSPACE|MASTER|MASTER_TEMP,workspace=false";

    @TempDir
    Path debugDirectory;


    @Test
    void projectMacrosWaitsForLaterAuthoritativeObservation () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("project-request", "project-macros", PROJECT_MACROS);

        host.tick ();

        assertEquals (List.of ("SHIFT:DOWN", "SESSION:DOWN", "SESSION:UP", "SHIFT:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()), "gesture submission is not completion");

        surface.observe ("WORKSPACE", "WORKSPACE", true);
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "one observed sample is not yet stable");
        host.tick ();

        assertEquals (List.of ("project-request", "READY", "project-macros", "WORKSPACE", "WORKSPACE", "true", "false", "-1", "-", "0", ""), this.status ());
    }


    @Test
    void layoutGesturesUseThePermanentRoutedButtonAndModifierBindings () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("layout-request", "layout", "LAYOUT/view=CHORDS", "SHIFT_LAYOUT/view=SEQUENCER");

        host.tick ();
        assertEquals (List.of ("LAYOUT:DOWN", "LAYOUT:UP"), surface.events);
        surface.observe ("CHORDS", "TRACK", false);
        host.tick ();
        host.tick ();
        host.tick ();
        assertEquals (List.of ("LAYOUT:DOWN", "LAYOUT:UP", "SHIFT:DOWN", "LAYOUT:DOWN", "LAYOUT:UP", "SHIFT:UP"), surface.events);

        surface.observe ("SEQUENCER", "TRACK", false);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("layout-request", "READY", "layout", "SEQUENCER", "TRACK", "false", "false"), this.status ().subList (0, 7));
    }


    @Test
    void mixExitsCoreWorkspaceThenUsesTheStableTrackBinding () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("WORKSPACE", "WORKSPACE", true);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("mix-request", "mix", MIX_EXIT_WORKSPACE, MIX);

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

        assertEquals (List.of ("mix-request", "READY", "mix", "PLAY", "TRACK", "false", "false", "-1", "-", "0", ""), this.status ());
    }


    @Test
    void heldPhysicalNavigationButtonIsNeverReleasedByDebugger () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        surface.pressed.add (ButtonID.SHIFT);
        this.request ("held-request", "project-macros", PROJECT_MACROS);

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
        this.request ("master-request", "master", MASTER);

        host.tick ();
        assertEquals (List.of ("MASTERTRACK:DOWN", "MASTERTRACK:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()));

        surface.observe ("PLAY", "MASTER", false);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("master-request", "READY", "master", "PLAY", "MASTER", "false", "false", "-1", "-", "0", ""), this.status ());
    }


    @Test
    void alreadySatisfiedGenericPlanDoesNotToggleTheView () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "MASTER", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("satisfied-request", "client-label", MASTER);

        host.tick ();
        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertEquals (List.of ("satisfied-request", "READY", "client-label", "PLAY", "MASTER", "false", "false", "-1", "-", "0", ""), this.status ());
    }


    @Test
    void submittedPlayGestureRunsExactlyOnceAndWaitsForQuiescence () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "MASTER", true);
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("play-request", "play", "PLAY/submitted");

        host.tick ();
        assertEquals (List.of ("PLAY:DOWN", "PLAY:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()), "submission is not debugger completion");

        admission.idle = false;
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "the routed gesture must become idle");

        admission.idle = true;
        host.tick ();
        host.tick ();

        assertEquals (List.of ("PLAY:DOWN", "PLAY:UP"), surface.events);
        assertEquals (List.of ("play-request", "READY", "play", "PLAY", "MASTER", "true", "false", "-1", "-", "0", ""), this.status ());
    }


    @Test
    void submittedMasterControlsAreExplicitlyAdmitted () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "MASTER", true);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("next-request", "next", "ROW2_8/submitted");

        host.tick ();
        host.tick ();
        host.tick ();

        assertEquals (List.of ("ROW2_8:DOWN", "ROW2_8:UP"), surface.events);
        assertEquals ("READY", this.status ().get (1));

        Files.delete (this.statusPath ());
        this.request ("engine-request", "engine", "ROW2_5/submitted");
        host.tick ();
        host.tick ();
        host.tick ();

        assertEquals (List.of ("ROW2_8:DOWN", "ROW2_8:UP", "ROW2_5:DOWN", "ROW2_5:UP"), surface.events);
        assertEquals ("READY", this.status ().get (1));
    }


    @Test
    void trackButtonWaitsForAuthoritativeSelectedTrackIdentity () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("SESSION", "TRACK", false);
        surface.observe ("SESSION", "TRACK", false, 4);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("track-request", "track-6", "ROW1_6/track=5,track-id=track-5");

        host.tick ();
        assertEquals (List.of ("ROW1_6:DOWN", "ROW1_6:UP"), surface.events);
        assertFalse (Files.exists (this.statusPath ()), "button submission is not track selection acknowledgement");

        surface.observe ("SESSION", "TRACK", false, 5);
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "one selected-track sample is not yet stable");
        host.tick ();

        assertEquals ("READY", this.status ().get (1));
        assertEquals ("track-5", this.status ().get (8));
    }


    @Test
    void equalLocalPositionsUnderDifferentParentsNeverSatisfyTrackIdentity () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        surface.observe ("PLAY", "TRACK", false, 0, "drydrum-clean-kick", 7, false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("identity-request", "top-level-drum", "ROW1_1/view=DRUM_PAD,track=0,track-id=top-level-drum,repeat=true");

        host.tick ();
        assertEquals (List.of ("ROW1_1:DOWN", "ROW1_1:UP"), surface.events);

        surface.observe ("DRUM_PAD", "TRACK", false, 0, "drydrum-clean-kick", 8, true);
        host.tick ();
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "a child with the same local position is not the requested top-level track");

        surface.observe ("DRUM_PAD", "TRACK", false, 0, "top-level-drum", 9, true);
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "identity acknowledgement still needs two later samples");
        surface.observe ("DRUM_PAD", "TRACK", false, 0, "top-level-drum", 10, true);
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "a changed identity generation restarts stability acknowledgement");
        host.tick ();

        assertEquals ("READY", this.status ().get (1));
        assertEquals ("top-level-drum", this.status ().get (8));
        assertEquals ("10", this.status ().get (9));
    }


    @Test
    void localTrackPositionCannotBeUsedAsGlobalProof () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("SESSION", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("unsafe-position", "track-6", "ROW1_6/track=5");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertEquals ("FAILED", this.status ().get (1));
        assertTrue (this.status ().get (10).contains ("requires an exact track-id"));
    }


    @Test
    void repeatPredicateWaitsForAuthoritativeNoteInputState () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        surface.observe ("PLAY", "TRACK", false, 5, false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("repeat-request", "drum-roll", "ROW1_1/view=DRUM_PAD,track=0,track-id=track-0,repeat=true");

        host.tick ();
        assertEquals (List.of ("ROW1_1:DOWN", "ROW1_1:UP"), surface.events);

        surface.observe ("DRUM_PAD", "TRACK", false, 0, false);
        host.tick ();
        assertFalse (Files.exists (this.statusPath ()), "layout read-back is not repeat read-back");

        surface.observe ("DRUM_PAD", "TRACK", false, 0, true);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("repeat-request", "READY", "drum-roll", "DRUM_PAD", "TRACK", "false", "true", "0", "track-0", "1", ""), this.status ());
    }


    @Test
    void trackButtonRejectsNonTrackMode () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("SESSION", "DEVICE_PARAMS", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("unsafe-track", "track-6", "ROW1_6/track=5,track-id=track-5");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void submittedMasterControlRejectsNonMasterContext () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("SESSION", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("unsafe-row", "next", "ROW2_8/submitted");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void submittedMasterControlRechecksContextInsideAdmission () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("SESSION", "MASTER", true);
        final PushDebugNavigationHost.GestureAdmission admission = new PushDebugNavigationHost.GestureAdmission ()
        {
            @Override
            public boolean isIdle ()
            {
                return true;
            }


            @Override
            public boolean trySubmit (final Runnable gesture)
            {
                surface.observe ("SESSION", "TRACK", false);
                gesture.run ();
                return true;
            }
        };
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("raced-row", "next", "ROW2_8/submitted");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void sessionRequiresTheWorkspaceToBeReleased () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("WORKSPACE", "WORKSPACE", true);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("session-request", "session", MIX_EXIT_WORKSPACE, MIX, SESSION);

        host.tick ();
        assertEquals (List.of ("NOTE:DOWN", "NOTE:UP"), surface.events);

        surface.observe ("PLAY", "WORKSPACE", false);
        host.tick ();
        assertEquals (List.of ("NOTE:DOWN", "NOTE:UP", "TRACK:DOWN", "TRACK:UP"), surface.events);

        surface.observe ("PLAY", "TRACK", false);
        host.tick ();
        assertEquals (List.of ("NOTE:DOWN", "NOTE:UP", "TRACK:DOWN", "TRACK:UP", "SESSION:DOWN", "SESSION:UP"), surface.events);

        surface.observe ("SESSION", "TRACK", false);
        host.tick ();
        host.tick ();

        assertEquals (List.of ("session-request", "READY", "session", "SESSION", "TRACK", "false", "false", "-1", "-", "0", ""), this.status ());
    }


    @Test
    void unsupportedTargetFailsWithoutTouchingController () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("bad-request", "arbitrary-label", "RECORD/*");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        final List<String> status = this.status ();
        assertEquals ("bad-request", status.get (0));
        assertEquals ("FAILED", status.get (1));
        assertTrue (status.get (10).contains ("unsupported navigation gesture"));
    }


    @Test
    void mixWaitsForEveryDrivenControlAndModifierToBeReleased () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("WORKSPACE", "WORKSPACE", true);
        final PushDebugNavigationHost host = this.host (surface);
        surface.pressed.add (ButtonID.NOTE);
        this.request ("held-mix", "mix", MIX_EXIT_WORKSPACE, MIX);

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
        this.request ("idle-request", "master", MASTER);

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
        this.request ("one-shot", "master", MASTER);

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
        this.request ("master-session", "session", MIX_EXIT_WORKSPACE, MIX, SESSION);

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
        this.request ("failed-down", "master", MASTER);

        host.tick ();

        assertEquals (List.of ("MASTERTRACK:DOWN", "MASTERTRACK:UP"), surface.events);
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void shutdownFailsPendingNavigation () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("PLAY", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("closing-navigation", "master", MASTER);
        host.tick ();

        host.close ();

        final List<String> status = this.status ();
        assertEquals ("closing-navigation", status.get (0));
        assertEquals ("FAILED", status.get (1));
        assertTrue (status.get (10).contains ("closing"));
    }


    private PushDebugNavigationHost host (final FakeNavigationSurface surface)
    {
        return new PushDebugNavigationHost (this.debugDirectory, surface);
    }


    private void request (final String requestID, final String label, final String... steps) throws IOException
    {
        Files.writeString (
            this.debugDirectory.resolve (PushDebugNavigationHost.REQUEST_FILE),
            requestID + "\t" + label + "\t" + String.join ("\t", steps) + "\n");
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
            this.observed = new PushDebugNavigationHost.ObservedNavigation (viewID, modeID, workspaceActive, -1, "", 0, false);
        }


        private void observe (final String viewID, final String modeID, final boolean workspaceActive, final int selectedTrackPosition)
        {
            this.observe (viewID, modeID, workspaceActive, selectedTrackPosition, "track-" + selectedTrackPosition, 1, false);
        }


        private void observe (
            final String viewID,
            final String modeID,
            final boolean workspaceActive,
            final int selectedTrackPosition,
            final boolean noteRepeatActive)
        {
            this.observe (viewID, modeID, workspaceActive, selectedTrackPosition, "track-" + selectedTrackPosition, 1, noteRepeatActive);
        }


        private void observe (
            final String viewID,
            final String modeID,
            final boolean workspaceActive,
            final int selectedTrackPosition,
            final String selectedTrackID,
            final long selectedTrackGeneration,
            final boolean noteRepeatActive)
        {
            this.observed = new PushDebugNavigationHost.ObservedNavigation (viewID, modeID, workspaceActive, selectedTrackPosition, selectedTrackID, selectedTrackGeneration, noteRepeatActive);
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
