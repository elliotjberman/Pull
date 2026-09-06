// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Real core page replacements must preserve the original physical interaction's receiver. */
class CorePageGestureCaptureTest
{
    private static final ControlId NOTE_EDITOR = PushControlIds.button ("ROW1_4");

    @Test
    void frameReleaseReturnsToItsOriginalViewWithoutRestoringTheOldPage ()
    {
        final FakeCoreHost host = host ();
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "FRAME");
        host.controllerButton (NOTE_EDITOR, true);
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "TRACK");
        host.controllerTick ();
        assertEquals ("TRACK", page (host));
        assertTrue (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.APPLICATION_UI), "the held Frame receiver still needs authoritative context");
        assertTrue (panelEffects (host).isEmpty (), "a BEGIN is not an applied release action");

        host.controllerButton (NOTE_EDITOR, false);
        assertEquals (List.of (new ToggleApplicationPanelEffect (ui (1, "project-a").context (), ToggleApplicationPanelEffect.Panel.NOTE_EDITOR)), panelEffects (host));
        assertEquals ("TRACK", page (host), "handling the old release cannot reactivate its old display");
        assertTrue (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.APPLICATION_UI), "the release result still needs its emitted effect's declared dependency");
        host.controllerTick ();
        assertFalse (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.APPLICATION_UI), "the next result retires the completed receiver's subscription");
        host.controllerButton (NOTE_EDITOR, false);
        host.controllerButton (NOTE_EDITOR, true);
        host.controllerButton (NOTE_EDITOR, false);
        assertEquals (1, panelEffects (host).size (), "neither duplicate release nor a new Track press may invoke old Frame behavior");
    }

    @Test
    void retainingTheOriginalViewDoesNotBypassItsHostTargetFence ()
    {
        final FakeCoreHost host = host ();
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "FRAME");
        host.controllerButton (NOTE_EDITOR, true);
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "TRACK");
        host.bridge (bridge (ui (2, "project-b")));
        host.controllerButton (NOTE_EDITOR, false);
        assertTrue (panelEffects (host).isEmpty (), "a captured receiver cannot apply an old project's action to a new project");
        assertEquals ("TRACK", page (host));
    }

    private static FakeCoreHost host ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge (ui (1, "project-a")));
        host.start (Optional.empty ());
        return host;
    }

    private static List<ToggleApplicationPanelEffect> panelEffects (final FakeCoreHost host)
    {
        return host.effects ().executionOrder ().stream ().filter (ToggleApplicationPanelEffect.class::isInstance).map (ToggleApplicationPanelEffect.class::cast).toList ();
    }

    private static String page (final FakeCoreHost host) { return host.effects ().desiredControllerPage ().effectivePage ().legacyAlias (); }
    private static ApplicationUiSnapshot ui (final long generation, final String project)
    {
        return new ApplicationUiSnapshot (generation, project, "ARRANGE", ArrangerUiSnapshot.empty (), MixerUiSnapshot.empty ());
    }
    private static ControllerBridgeSnapshot bridge (final ApplicationUiSnapshot ui)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), ui, e.controllerPages (), e.browser ());
    }
}
