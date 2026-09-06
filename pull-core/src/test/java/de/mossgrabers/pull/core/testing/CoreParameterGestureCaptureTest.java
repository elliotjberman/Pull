// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The production core must retain one exact touch across a core-to-legacy page replacement. */
class CoreParameterGestureCaptureTest
{
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "project-a-macro", 1);

    @Test
    void hiddenMacroTouchRetainsItsTargetAndEndsAutomationWithoutRevivingItsPage ()
    {
        final Fixture f = new Fixture ();
        f.page ("WORKSPACE");
        f.touch (true);
        assertEquals (Map.of (KNOB, TARGET), f.result.desiredParameterTouches ().targets ());
        f.page ("DEVICE_PARAMS");
        assertEquals (ControllerPageRef.Kind.LEGACY, f.result.desiredControllerState ().page ().effectivePage ().kind ());
        assertEquals (Map.of (KNOB, TARGET), f.result.desiredParameterTouches ().targets ());
        assertTrue (f.result.desiredParameterBanks ().includes (ParameterBankId.PROJECT_REMOTE));
        assertNull (f.result.desiredInputRoutes ().modeOrNull (KNOB, InputKind.TOUCH), "the old touch is retained without claiming fresh legacy-page input");
        f.touch (false);
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), f.result.effects ());
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        assertEquals ("DEVICE_PARAMS", f.result.desiredControllerState ().page ().effectivePage ().id ());
        assertTrue (f.automation.writingEnabled (), "a requested stop is not authoritative read-back");
        f.touch (false);
        assertTrue (f.result.effects ().isEmpty (), "orphan END is inert");
        f.page ("WORKSPACE");
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty (), "returning to the page cannot invent a new touch");
    }

    @Test
    void hiddenTouchDropsAReboundTargetAndDoesNotStopWritingInAnotherProject ()
    {
        for (final String project: List.of ("project-a", "project-b"))
        {
            final Fixture f = new Fixture ();
            f.page ("WORKSPACE");
            f.touch (true);
            f.page ("DEVICE_PARAMS");
            f.target = new ParameterTargetRef (ParameterTargetKind.LIVE, "replacement-macro", 2);
            f.automation = new AutomationSnapshot (project, true, true);
            f.tick ();
            assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
            f.target = TARGET;
            f.tick ();
            assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty (), "returning an old target cannot reacquire a held touch");
            f.touch (false);
            assertEquals (project.equals ("project-a") ? List.of (new SetAutomationWriteEffect (project, false)) : List.of (), f.result.effects ());
        }
    }

    private static final class Fixture
    {
        private final ControllerCore core = new PullCoreProvider ().create ();
        private CoreResult result;
        private AutomationSnapshot automation = new AutomationSnapshot ("project-a", true, true);
        private ParameterTargetRef target = TARGET;
        private boolean touched;
        private long sequence;
        private LegacyControllerPageRequests requests = LegacyControllerPageRequests.empty ();
        Fixture () { this.result = this.core.start (this.snapshot (), Optional.empty ()); }
        void touch (final boolean down)
        {
            this.touched = down;
            this.sequence++;
            this.result = this.core.handle (new ControllerInputEvent (this.sequence, this.sequence, KNOB, InputKind.TOUCH, down ? InputPhase.BEGIN : InputPhase.END, down ? 127 : 0), this.snapshot ());
        }
        void page (final String alias)
        {
            final var page = this.result.desiredControllerState ().page ();
            final long request = page.acknowledgedRequestSequence () + 1;
            this.requests = new LegacyControllerPageRequests (page.acknowledgedRequestSequence (), List.of (new LegacyControllerPageRequest (request, page.revision (), page.temporaryToken (), LegacyControllerPageRequest.Operation.SELECT, alias)));
            this.tick ();
            this.requests = new LegacyControllerPageRequests (this.result.desiredControllerState ().page ().acknowledgedRequestSequence (), List.of ());
        }
        void tick () { this.sequence++; this.result = this.core.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()); }
        ControllerSnapshot snapshot ()
        {
            final var e = ControllerBridgeSnapshot.empty ();
            final var parameters = new ParameterBridgeSnapshot (Map.of (ParameterSlot.projectRemote (0), new ParameterTargetSnapshot (this.target, "Cutoff", 64, 64, "64", 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot ("project-remote", this.automation.projectIdentity (), 0, 0))), Map.of ());
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), parameters, e.controllerMappingFeedback (), e.master (), e.project (), this.automation, e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), this.requests, e.browser ());
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), this.touched ? Set.of (KNOB) : Set.of ());
        }
    }
}
