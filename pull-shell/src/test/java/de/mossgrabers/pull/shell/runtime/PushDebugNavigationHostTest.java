// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.RgbColor;

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

        assertEquals (List.of ("project-request", "READY", "project-macros", "WORKSPACE", "WORKSPACE", "true", "false", "false", "-1", "-", "0", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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

        assertEquals (List.of ("mix-request", "READY", "mix", "PLAY", "TRACK", "false", "false", "false", "-1", "-", "0", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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

        assertEquals (List.of ("master-request", "READY", "master", "PLAY", "MASTER", "false", "false", "false", "-1", "-", "0", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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
        assertEquals (List.of ("satisfied-request", "READY", "client-label", "PLAY", "MASTER", "false", "false", "false", "-1", "-", "0", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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
        assertEquals (List.of ("play-request", "READY", "play", "PLAY", "MASTER", "true", "false", "false", "-1", "-", "0", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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
        assertEquals ("track-5", this.status ().get (9));
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
        assertEquals ("top-level-drum", this.status ().get (9));
        assertEquals ("10", this.status ().get (10));
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
        assertTrue (this.status ().getLast ().contains ("requires an exact track-id"));
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

        assertEquals (List.of ("repeat-request", "READY", "drum-roll", "DRUM_PAD", "TRACK", "false", "true", "false", "0", "track-0", "1", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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

        assertEquals (List.of ("session-request", "READY", "session", "SESSION", "TRACK", "false", "false", "false", "-1", "-", "0", "false", "OFF", "true", "false", "false", "-", "-", "false", "-", "-", "NONE", ""), this.status ());
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
        assertTrue (status.getLast ().contains ("unsupported navigation gesture"));
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
    void padProbeClosesInputDesiredResolvedAndTransmittedLoopBeforeRelease () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        surface.observe ("DRUM_PAD", "TRACK", false, 0, "drums", 7, true);
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("pad-proof", "pad-29", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false,track-id=drums,repeat=true");

        host.tick ();
        assertEquals (List.of ("PAD29:DOWN:100"), surface.events);
        assertTrue (admission.debugInputActive);
        assertTrue (surface.padObservationActive);

        surface.publishPadOutput (2, new RgbColor (12, 34, 56), 42, 44, true, 1, 2);
        host.tick ();
        assertEquals (List.of ("PAD29:DOWN:100"), surface.events, "an unrelated resolved color is not correlation");
        surface.publishPadOutput (2, new RgbColor (12, 34, 56), 43, 44, true, 3, 4);
        host.tick ();
        assertEquals (List.of ("PAD29:DOWN:100"), surface.events, "one resolved sample is not proof");
        host.tick ();
        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertTrue (admission.debugInputActive, "UP submission retains the generation fence until later application");

        admission.debugRouteIdle = false;
        surface.appliedRevision = 3;
        host.tick ();
        assertTrue (admission.debugInputActive);
        admission.debugRouteIdle = true;
        host.tick ();
        host.tick ();
        host.tick ();

        final List<String> status = this.fullStatus ();
        assertEquals ("READY", status.get (1));
        assertEquals ("OUTPUT", status.get (22));
        assertEquals ("PAD29", status.get (23));
        assertEquals ("push.pad.29", status.get (24));
        assertEquals ("64", status.get (25));
        assertEquals ("100", status.get (26));
        assertEquals ("EXCLUSIVE", status.get (27));
        assertEquals ("true", status.get (28));
        assertEquals ("true", status.get (29));
        assertEquals ("true", status.get (30));
        assertEquals ("0C2238", status.get (31));
        assertEquals ("43:44:true", status.get (32));
        assertEquals ("base=0:64:43;blink=14:64:44", status.get (33));
        assertFalse (admission.debugInputActive);
        assertFalse (surface.padObservationActive);
    }


    @Test
    void padProbeRejectsAnythingButAnActiveExclusiveCoreRoute () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (true);
        admission.padRoute = InputRouteMode.OBSERVE;
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("unsafe-pad", "no-route", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertFalse (surface.padObservationActive);
        assertEquals ("FAILED", this.status ().get (1));
        assertTrue (this.status ().getLast ().contains ("EXCLUSIVE"));
        assertEquals ("OBSERVE", this.fullStatus ().get (27));
    }


    @Test
    void padProbeRejectsAnInactiveMappingLeaseBeforeDown () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        surface.mappingDesired = false;
        final FakeAdmission admission = new FakeAdmission (true);
        admission.padMappingActive = false;
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("unmapped-pad", "outside-drum", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();

        final List<String> status = this.fullStatus ();
        assertTrue (surface.events.isEmpty ());
        assertEquals ("FAILED", status.get (1));
        assertEquals ("EXCLUSIVE", status.get (27));
        assertEquals ("false", status.get (28));
        assertEquals ("false", status.get (29));
        assertEquals ("true", status.get (30));
        assertTrue (status.getLast ().contains ("mapping lease"));
    }


    @Test
    void padProbeWaitsForAppliedMappingAndFencesItWhileHeld () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (true);
        admission.padMappingActive = false;
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("mapping-transition", "pad-29", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();
        assertTrue (surface.events.isEmpty (), "desired mapping is not applied mapping");
        assertFalse (Files.exists (this.statusPath ()), "pending activation is not terminal failure");

        admission.padMappingActive = true;
        host.tick ();
        assertEquals (List.of ("PAD29:DOWN:100"), surface.events);
        admission.padMappingActive = false;
        host.tick ();

        final List<String> status = this.fullStatus ();
        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertEquals ("FAILED", status.get (1));
        assertEquals ("true", status.get (28));
        assertEquals ("false", status.get (29));
        assertEquals ("true", status.get (30));
        assertTrue (status.getLast ().contains ("activation changed"));
    }


    @Test
    void padProbeWaitsForSubscribedMappedFeedbackAndAcceptsFalse () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        surface.mappedOn = null;
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("mapped-feedback", "pad-29", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();
        assertTrue (surface.events.isEmpty ());
        assertFalse (Files.exists (this.statusPath ()), "an unavailable subscribed snapshot is not false read-back");

        surface.mappedOn = Boolean.FALSE;
        host.tick ();
        assertEquals (List.of ("PAD29:DOWN:100"), surface.events);
        host.close ();
        assertEquals ("false", this.fullStatus ().get (30));
    }


    @Test
    void padProbeFencesMappedFeedbackAvailabilityWhileHeld () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("lost-mapped-feedback", "pad-29", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();
        surface.mappedOn = null;
        host.tick ();

        final List<String> status = this.fullStatus ();
        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertEquals ("FAILED", status.get (1));
        assertEquals ("-", status.get (30));
        assertTrue (status.getLast ().contains ("mapped feedback became unavailable"));
    }


    @Test
    void padProbeContextLossSubmitsExactlyOneUpAndEndsObservation () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("moving-pad", "context-loss", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();
        surface.observe ("SESSION", "TRACK", false);
        host.tick ();

        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertFalse (admission.debugInputActive);
        assertFalse (surface.padObservationActive);
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void synchronousCoreInvalidationDuringDownStillSubmitsUp () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        surface.onPadDown = () -> host.cancelActiveProbe ("core invalidated during DOWN");
        this.request ("faulting-pad", "core-fault", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");

        host.tick ();
        host.tick ();

        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertFalse (admission.debugInputActive);
        assertFalse (surface.padObservationActive);
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void padProbeNeverTreatsAnUnappliedUpAsReleaseAcknowledgement () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        surface.applyPadRelease = false;
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("unapplied-up", "release-failure", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");
        host.tick ();
        surface.publishPadOutput (2, new RgbColor (12, 34, 56), 43, 0, false, 1, 0);

        host.tick ();
        host.tick ();

        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertEquals ("FAILED", this.status ().get (1));
        assertTrue (this.status ().getLast ().contains ("UP did not produce"));
        assertFalse (admission.debugInputActive);
        assertFalse (surface.padObservationActive);
    }


    @Test
    void closingHeldPadProbeAlwaysSubmitsUp () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final FakeAdmission admission = new FakeAdmission (true);
        final PushDebugNavigationHost host = new PushDebugNavigationHost (this.debugDirectory, surface, admission);
        this.request ("closing-pad", "shutdown", "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false");
        host.tick ();

        host.close ();

        assertEquals (List.of ("PAD29:DOWN:100", "PAD29:UP:0"), surface.events);
        assertFalse (admission.debugInputActive);
        assertFalse (surface.padObservationActive);
        assertEquals ("FAILED", this.status ().get (1));
    }


    @Test
    void padProbeRequiresExactControllerContext () throws IOException
    {
        final FakeNavigationSurface surface = new FakeNavigationSurface ("DRUM_PAD", "TRACK", false);
        final PushDebugNavigationHost host = this.host (surface);
        this.request ("unsafe-pad-context", "missing-context", "PAD_OUTPUT_29_100/view=DRUM_PAD");

        host.tick ();

        assertTrue (surface.events.isEmpty ());
        assertEquals ("FAILED", this.status ().get (1));
        assertTrue (this.status ().getLast ().contains ("exact view, mode, and workspace"));
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
        assertTrue (status.getLast ().contains ("closing"));
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
        final List<String> fields = this.fullStatus ();
        final List<String> navigation = new ArrayList<> (fields.subList (0, 22));
        navigation.add (fields.getLast ());
        return List.copyOf (navigation);
    }


    private List<String> fullStatus () throws IOException
    {
        final String content = Files.readString (this.statusPath ());
        return List.of (content.substring (0, content.length () - 1).split ("\t", -1));
    }


    private static final class FakeNavigationSurface implements PushDebugNavigationHost.NavigationSurface
    {
        private final List<String>  events = new ArrayList<> ();
        private final Set<ButtonID> pressed = EnumSet.noneOf (ButtonID.class);
        private PushDebugNavigationHost.ObservedNavigation observed;
        private long coreGeneration = 3;
        private long appliedRevision = 1;
        private boolean mappingDesired = true;
        private Boolean mappedOn = Boolean.TRUE;
        private ControlId padControl;
        private RgbColor padDesiredColor;
        private PushControlSurface.DebugPadOutput padOutput;
        private boolean padObservationActive;
        private boolean applyPadRelease = true;
        private Runnable onPadDown = () -> {
            // No synchronous invalidation by default.
        };
        private boolean failObservation;


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


        private void observe (
            final String viewID,
            final String modeID,
            final boolean workspaceActive,
            final int selectedTrackPosition,
            final String selectedTrackID,
            final long selectedTrackGeneration,
            final boolean noteRepeatActive,
            final boolean noteLatchActive,
            final boolean selectedTrackArmed,
            final SelectedTrackMonitorMode selectedTrackMonitorMode)
        {
            final ControllerBridge.NotePerformanceState notePerformance = notePerformance (viewID, selectedTrackID, selectedTrackGeneration);
            this.observed = new PushDebugNavigationHost.ObservedNavigation (
                viewID, modeID, workspaceActive, selectedTrackPosition, selectedTrackID,
                selectedTrackGeneration, true, noteRepeatActive, noteLatchActive, selectedTrackArmed,
                selectedTrackMonitorMode, notePerformance);
        }


        private static ControllerBridge.NotePerformanceState notePerformance (final String viewID, final String trackID, final long generation)
        {
            if (trackID.isEmpty ())
                return ControllerBridge.NotePerformanceState.unavailable ();
            if (!Set.of ("PLAY", "DRUM_PAD").contains (viewID))
                return new ControllerBridge.NotePerformanceState (true, DesiredNotePerformance.inactive (), DesiredNoteInputRoute.disabled (), DesiredControllerLayout.empty ());

            final DesiredControllerLayout layout = DesiredControllerLayout.note (ControllerNoteView.valueOf (viewID));
            final DesiredNoteInputRoute route = DesiredNoteInputRoute.selectedTrack (generation, trackID);
            return new ControllerBridge.NotePerformanceState (true, new DesiredNotePerformance (layout, route), route, layout);
        }


        @Override
        public PushDebugNavigationHost.ObservedNavigation observe ()
        {
            if (this.failObservation)
                throw new IllegalStateException ("synthetic observation failure");
            return this.observed;
        }


        private void notePerformance (final ControllerBridge.NotePerformanceState notePerformance)
        {
            final PushDebugNavigationHost.ObservedNavigation current = this.observed;
            this.observed = new PushDebugNavigationHost.ObservedNavigation (
                current.viewID (), current.modeID (), current.workspaceActive (), current.selectedTrackPosition (),
                current.selectedTrackID (), current.selectedTrackGeneration (), current.selectedTrackCanHoldNotes (),
                current.noteRepeatActive (), current.noteLatchActive (), current.selectedTrackArmed (),
                current.selectedTrackMonitorMode (), notePerformance);
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


        @Override
        public void trigger (final ButtonID button, final ButtonEvent event, final double velocity)
        {
            this.events.add (button + ":" + event + ":" + Math.round (velocity * 127));
            if (this.failOnDown && event == ButtonEvent.DOWN)
                throw new IllegalStateException ("synthetic down failed");
            if (event == ButtonEvent.DOWN && ButtonID.isInRange (button, ButtonID.PAD1, 64))
                this.onPadDown.run ();
            if (this.applyPadRelease && event == ButtonEvent.UP && ButtonID.isInRange (button, ButtonID.PAD1, 64))
                this.appliedRevision++;
        }


        @Override
        public PushDebugNavigationHost.ObservedCoreLight coreLight (final ControlId control)
        {
            return new PushDebugNavigationHost.ObservedCoreLight (
                this.coreGeneration, this.appliedRevision,
                control.equals (this.padControl) ? this.padDesiredColor : null,
                this.mappingDesired,
                this.mappedOn);
        }


        @Override
        public void beginPadOutputObservation (final int oneBasedPad)
        {
            assertFalse (this.padObservationActive);
            this.padObservationActive = true;
            this.padControl = PushControlIds.pad (oneBasedPad);
            this.padOutput = new PushControlSurface.DebugPadOutput (
                oneBasedPad, 36 + oneBasedPad - 1, 0, 0, false,
                new PushControlSurface.DebugPadTransmission (0, -1, -1, -1),
                new PushControlSurface.DebugPadTransmission (0, -1, -1, -1));
        }


        @Override
        public PushControlSurface.DebugPadOutput padOutput (final int oneBasedPad)
        {
            assertTrue (this.padObservationActive);
            assertEquals (oneBasedPad, this.padOutput.oneBasedPad ());
            return this.padOutput;
        }


        @Override
        public int resolvePadColor (final RgbColor color)
        {
            return 43;
        }


        @Override
        public void endPadOutputObservation (final int oneBasedPad)
        {
            this.padObservationActive = false;
        }


        private void publishPadOutput (final long appliedRevision, final RgbColor desired, final int color, final int blinkColor, final boolean fast, final long baseRevision, final long blinkRevision)
        {
            this.appliedRevision = appliedRevision;
            this.padDesiredColor = desired;
            final int midiNote = this.padOutput.midiNote ();
            this.padOutput = new PushControlSurface.DebugPadOutput (
                this.padOutput.oneBasedPad (), midiNote, color, blinkColor, fast,
                new PushControlSurface.DebugPadTransmission (baseRevision, 0, midiNote, color),
                blinkRevision == 0 ? new PushControlSurface.DebugPadTransmission (0, -1, -1, -1) : new PushControlSurface.DebugPadTransmission (blinkRevision, fast ? 14 : 10, midiNote, blinkColor));
        }


        private boolean failOnDown;
    }


    private static final class FakeAdmission implements PushDebugNavigationHost.GestureAdmission
    {
        private boolean idle;
        private boolean debugInputActive;
        private InputRouteMode padRoute = InputRouteMode.EXCLUSIVE;
        private boolean padMappingActive = true;
        private boolean debugRouteIdle = true;


        private FakeAdmission (final boolean idle)
        {
            this.idle = idle;
        }


        @Override
        public boolean isIdle ()
        {
            return this.idle && !this.debugInputActive;
        }


        @Override
        public boolean trySubmit (final Runnable gesture)
        {
            if (!this.isIdle ())
                return false;
            gesture.run ();
            return true;
        }


        @Override
        public boolean tryBeginDebugInput (final Runnable noteOn)
        {
            if (!this.isIdle ())
                return false;
            this.debugInputActive = true;
            try
            {
                noteOn.run ();
                return true;
            }
            catch (final RuntimeException ex)
            {
                this.debugInputActive = false;
                throw ex;
            }
        }


        @Override
        public void endDebugInput (final Runnable noteOff)
        {
            assertTrue (this.debugInputActive);
            noteOff.run ();
        }


        @Override
        public void completeDebugInput ()
        {
            this.debugInputActive = false;
        }


        @Override
        public InputRouteMode debugPadRoute (final ControlId control)
        {
            return this.padRoute;
        }


        @Override
        public boolean debugPadMappingActive (final ControlId control)
        {
            return this.padMappingActive;
        }


        @Override
        public boolean debugInputRouteIdle ()
        {
            return this.debugRouteIdle;
        }
    }
}
