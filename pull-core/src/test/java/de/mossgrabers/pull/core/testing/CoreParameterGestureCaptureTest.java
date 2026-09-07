// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production routing: binding loss cancels, and exact touch-lease retirement is a later shell fact. */
class CoreParameterGestureCaptureTest
{
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "project-a-macro", 1);

    @Test
    void leavingMacroPageCancelsImmediatelyAndOldReleaseNeverActsOnReplacement ()
    {
        final Fixture f = new Fixture ();
        f.page ("WORKSPACE");
        f.touch (true);
        assertEquals (Map.of (KNOB, TARGET), f.result.desiredParameterTouches ().targets ());
        f.leases = Set.of (TARGET); // A later shell observation, not the emitted request.
        f.tick ();
        f.page ("DEVICE_PARAMS");
        assertEquals (ControllerPageRef.Kind.LEGACY, f.result.desiredControllerState ().page ().effectivePage ().kind ());
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), f.result.effects ());
        assertTrue (f.automation.writingEnabled (), "submitting cleanup is not host read-back");
        assertNull (f.result.desiredInputRoutes ().modeOrNull (KNOB, InputKind.TOUCH));
        f.page ("WORKSPACE");
        f.turn ();
        assertTrue (f.result.effects ().isEmpty (), "returning to the old page cannot revive its held motion");
        f.touch (false);
        assertTrue (f.result.effects ().isEmpty (), "cancelled END does not repeat cleanup or release behavior");
        f.leases = Set.of ();
        f.tick ();
        f.touch (true);
        assertEquals (Map.of (KNOB, TARGET), f.result.desiredParameterTouches ().targets ());
    }

    @Test
    void targetReplacementCancelsMotionAndTargetReuseWaitsForActualLeaseRetirement ()
    {
        final Fixture f = new Fixture ();
        f.page ("WORKSPACE");
        f.touch (true);
        f.leases = Set.of (TARGET);
        f.tick ();
        f.target = new ParameterTargetRef (ParameterTargetKind.LIVE, "replacement-macro", 2);
        f.tick ();
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        f.turn ();
        assertTrue (f.result.effects ().isEmpty (), "a retained proxy slot is not permission to write its new target");
        f.target = TARGET;
        f.tick ();
        f.touch (false);
        f.touch (true);
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty (), "old host touch lease still owns this target");
        f.leases = Set.of ();
        f.tick ();
        f.turn ();
        assertTrue (f.result.effects ().isEmpty (), "a rejected press also needs a fresh physical gesture");
        f.touch (false);
        f.touch (true);
        f.turn ();
        assertEquals (List.of (new AdjustParameterValueEffect (TARGET, 10)), f.result.effects ());
    }

    @Test
    void projectChangeNeverStopsAutomationInTheReplacementProject ()
    {
        final Fixture f = new Fixture ();
        f.page ("WORKSPACE");
        f.touch (true);
        f.automation = new AutomationSnapshot ("project-b", true, true);
        f.target = new ParameterTargetRef (ParameterTargetKind.LIVE, "project-b-macro", 2);
        f.tick ();
        assertTrue (f.result.effects ().isEmpty ());
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        f.touch (false);
        assertTrue (f.result.effects ().isEmpty ());
    }

    private static final class Fixture
    {
        private final ControllerCore core = new PullCoreProvider ().create ();
        private CoreResult result;
        private AutomationSnapshot automation = new AutomationSnapshot ("project-a", true, true);
        private ParameterTargetRef target = TARGET;
        private boolean touched;
        private Set<ParameterTargetRef> leases = Set.of ();
        private long sequence;
        private LegacyControllerPageRequests requests = LegacyControllerPageRequests.empty ();
        Fixture () { this.result = this.core.start (this.snapshot (), Optional.empty ()); }
        void touch (final boolean down)
        {
            this.touched = down;
            this.sequence++;
            this.result = this.core.handle (new ControllerInputEvent (this.sequence, this.sequence, KNOB, InputKind.TOUCH, down ? InputPhase.BEGIN : InputPhase.END, down ? 127 : 0), this.snapshot ());
        }
        void turn ()
        {
            this.sequence++;
            this.result = this.core.handle (new ControllerInputEvent (this.sequence, this.sequence, KNOB, InputKind.RELATIVE, InputPhase.UPDATE, 1), this.snapshot ());
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
            final var parameters = new ParameterBridgeSnapshot (Map.of (ParameterSlot.projectRemote (0), new ParameterTargetSnapshot (this.target, "Cutoff", 64, 64, "64", 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot ("project-remote", this.automation.projectIdentity (), 0, 0))), Map.of (), this.leases);
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), parameters, e.controllerMappingFeedback (), e.master (), e.project (), this.automation, e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), this.requests, e.browser ());
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), this.touched ? Set.of (KNOB) : Set.of ());
        }
    }
}
