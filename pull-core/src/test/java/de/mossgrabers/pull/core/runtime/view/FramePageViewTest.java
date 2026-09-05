// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FramePageViewTest
{
    @Test
    void arrangerOptionsSubmitAbsoluteValuesAndRenderOnlyLaterReadback ()
    {
        for (int index = 0; index < 7; index++)
        {
            final Fixture fixture = new Fixture ("ARRANGE");
            assertTrue (fixture.edge (true, index, InputPhase.BEGIN).effects ().isEmpty ());
            final CoreResult release = fixture.edge (true, index, InputPhase.END);
            assertEquals (List.of (new SetArrangerBooleanEffect (fixture.ui ().context (), SetArrangerBooleanEffect.Property.values ()[index], true)), release.effects ());
            assertEquals (new RgbColor (30, 30, 30), release.desiredOutput ().lights ().get (key (true, index)));
            fixture.arranger[index] = true;
            assertEquals (new RgbColor (255, 255, 255), fixture.tick ().desiredOutput ().lights ().get (key (true, index)));
        }
    }

    @Test
    void mixerHasSixObservedOptionsAnInertSeventhAndFullscreenEighth ()
    {
        for (int index = 0; index < 6; index++)
        {
            final Fixture fixture = new Fixture ("MIX");
            assertEquals (List.of (new SetMixerBooleanEffect (fixture.ui ().context (), SetMixerBooleanEffect.Property.values ()[index], true)), fixture.click (true, index).effects ());
        }
        final Fixture fixture = new Fixture ("MIX");
        assertTrue (fixture.click (true, 6).effects ().isEmpty ());
        assertEquals (new RgbColor (0, 0, 0), fixture.tick ().desiredOutput ().lights ().get (key (true, 6)));
        assertEquals (List.of (new ToggleApplicationPanelEffect (fixture.ui ().context (), ToggleApplicationPanelEffect.Panel.FULLSCREEN)), fixture.click (true, 7).effects ());
        assertEquals (new RgbColor (30, 30, 30), fixture.tick ().desiredOutput ().lights ().get (key (true, 7)));
    }

    @Test
    void lowerLayoutAndPanelChoicesPreserveTheirRowsAndStaticPanelFeedback ()
    {
        final List<SetApplicationLayoutEffect.Layout> layouts = List.of (SetApplicationLayoutEffect.Layout.ARRANGE, SetApplicationLayoutEffect.Layout.MIX, SetApplicationLayoutEffect.Layout.EDIT);
        final List<ToggleApplicationPanelEffect.Panel> panels = List.of (ToggleApplicationPanelEffect.Panel.NOTE_EDITOR, ToggleApplicationPanelEffect.Panel.AUTOMATION_EDITOR, ToggleApplicationPanelEffect.Panel.DEVICES, ToggleApplicationPanelEffect.Panel.MIXER, ToggleApplicationPanelEffect.Panel.INSPECTOR);
        final Fixture fixture = new Fixture ("ARRANGE");
        for (int index = 0; index < 3; index++)
            assertEquals (List.of (new SetApplicationLayoutEffect (fixture.ui ().context (), layouts.get (index))), fixture.click (false, index).effects ());
        for (int index = 3; index < 8; index++)
        {
            final CoreResult output = fixture.click (false, index);
            assertEquals (List.of (new ToggleApplicationPanelEffect (fixture.ui ().context (), panels.get (index - 3))), output.effects ());
            assertEquals (new RgbColor (30, 30, 30), output.desiredOutput ().lights ().get (key (false, index)));
        }
        assertEquals (new RgbColor (255, 255, 255), fixture.tick ().desiredOutput ().lights ().get (key (false, 0)));
        assertEquals (new RgbColor (30, 30, 30), fixture.tick ().desiredOutput ().lights ().get (key (false, 2)));
        fixture.layout = "EDIT";
        fixture.generation++;
        assertEquals (new RgbColor (255, 255, 255), fixture.tick ().desiredOutput ().lights ().get (key (false, 2)));
    }

    @Test
    void rapidVisibilityTogglesSerializeAcrossReadbackAndContextChangesRetireIntent ()
    {
        final Fixture fixture = new Fixture ("ARRANGE");
        assertEquals (1, fixture.click (true, 0).effects ().size ());
        assertTrue (fixture.click (true, 0).effects ().isEmpty ());
        assertTrue (fixture.tick ().effects ().isEmpty ());
        fixture.arranger[0] = true;
        assertEquals (List.of (new SetArrangerBooleanEffect (fixture.ui ().context (), SetArrangerBooleanEffect.Property.CLIP_LAUNCHER_VISIBLE, false)), fixture.tick ().effects ());
        fixture.click (true, 0);
        fixture.layout = "MIX";
        fixture.generation++;
        assertTrue (fixture.tick ().effects ().isEmpty ());
        assertFalse (fixture.tick ().executionRequirements ().ticksRequested ());
    }

    @Test
    void releaseCannotActOnAChangedProjectOrPanelAndAnOrphanReleaseIsInert ()
    {
        final Fixture fixture = new Fixture ("ARRANGE");
        assertTrue (fixture.edge (true, 1, InputPhase.END).effects ().isEmpty ());
        fixture.edge (true, 1, InputPhase.BEGIN);
        fixture.layout = "MIX";
        fixture.generation++;
        assertTrue (fixture.edge (true, 1, InputPhase.END).effects ().isEmpty ());
        fixture.edge (false, 4, InputPhase.BEGIN);
        fixture.project = "project-b";
        fixture.generation++;
        assertTrue (fixture.edge (false, 4, InputPhase.END).effects ().isEmpty ());
        fixture.edge (false, 0, InputPhase.BEGIN);
        fixture.view.deactivate ();
        assertTrue (fixture.edge (false, 0, InputPhase.END).effects ().isEmpty ());
    }

    @Test
    void editUnknownAndUnavailableLayoutsNeverInventUpperOptions ()
    {
        for (final String layout: List.of ("EDIT", "PLAY", "UNKNOWN"))
        {
            final Fixture fixture = new Fixture (layout);
            for (int index = 0; index < 8; index++)
            {
                assertTrue (fixture.click (true, index).effects ().isEmpty ());
                assertEquals (new RgbColor (0, 0, 0), fixture.tick ().desiredOutput ().lights ().get (key (true, index)));
            }
        }
        final Fixture unavailable = new Fixture ("ARRANGE");
        unavailable.available = false;
        final CoreResult result = unavailable.tick ();
        assertTrue (result.desiredOutput ().lights ().values ().stream ().allMatch (color -> color.equals (new RgbColor (0, 0, 0))));
        assertTrue (result.desiredOutput ().display ().commands ().stream ().noneMatch (DisplayCommand.TextBox.class::isInstance));
    }

    @Test
    void encoderTurnsTouchesAndLongsAreInertAndPageClaimsCoverBothRowsAndDisplay ()
    {
        final Fixture fixture = new Fixture ("ARRANGE");
        for (int index = 1; index <= 8; index++)
        {
            final ControlId knob = PushControlIds.continuous ("KNOB" + index);
            for (final InputKind kind: List.of (InputKind.RELATIVE, InputKind.TOUCH))
                assertTrue (fixture.workspace.handle (new ControllerInputEvent (1, 1, knob, kind, kind == InputKind.TOUCH ? InputPhase.BEGIN : InputPhase.UPDATE, 10), fixture.snapshot ()).effects ().isEmpty ());
        }
        assertTrue (fixture.edge (false, 0, InputPhase.LONG).effects ().isEmpty ());
        final CoreResult rendered = fixture.tick ();
        assertEquals ("FRAME", rendered.desiredControllerState ().workspace ().installedModeId ());
        assertEquals (16, rendered.desiredOutput ().lights ().size ());
        assertEquals (960, rendered.desiredOutput ().display ().width ());
        assertEquals (160, rendered.desiredOutput ().display ().height ());
        assertTrue (rendered.desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && text.text ().equals ("Inspector")));
    }

    private static ControlId key (final boolean upper, final int index) { return PushControlIds.button ((upper ? "ROW2_" : "ROW1_") + (index + 1)); }

    private static final class Fixture
    {
        private final FramePageView view = new FramePageView ();
        private final CompiledWorkspace workspace = CompiledWorkspace.compile ("Frame", List.of (this.view));
        private String project = "project-a";
        private String layout;
        private long generation = 1;
        private long sequence;
        private boolean available = true;
        private final boolean [] arranger = new boolean [7];
        private final boolean [] mixer = new boolean [6];
        private Fixture (final String layout) { this.layout = layout; this.workspace.start (this.snapshot ()); }
        private CoreResult edge (final boolean upper, final int index, final InputPhase phase)
        {
            this.sequence++;
            return this.workspace.handle (new ControllerInputEvent (this.sequence, this.sequence, key (upper, index), InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ());
        }
        private CoreResult click (final boolean upper, final int index) { this.edge (upper, index, InputPhase.BEGIN); return this.edge (upper, index, InputPhase.END); }
        private CoreResult tick () { this.sequence++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()); }
        private ApplicationUiSnapshot ui ()
        {
            return !this.available ? ApplicationUiSnapshot.empty () : new ApplicationUiSnapshot (this.generation, this.project, this.layout,
                new ArrangerUiSnapshot (this.arranger[0], this.arranger[1], this.arranger[2], this.arranger[3], this.arranger[4], this.arranger[5], this.arranger[6]),
                new MixerUiSnapshot (this.mixer[0], this.mixer[1], this.mixer[2], this.mixer[3], this.mixer[4], this.mixer[5]));
        }
        private ControllerSnapshot snapshot ()
        {
            final var e = ControllerBridgeSnapshot.empty ();
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), this.ui ());
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
        }
    }
}
