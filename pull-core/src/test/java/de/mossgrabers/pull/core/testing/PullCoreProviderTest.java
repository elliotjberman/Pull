// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Discovery, compatibility, and lifecycle tests for the production core provider.
 */
class PullCoreProviderTest
{
    @Test
    void serviceLoaderFindsExactlyOneProductionProvider () throws IOException
    {
        final List<CoreProvider> providers = ServiceLoader.load (CoreProvider.class).stream ().map (ServiceLoader.Provider::get).toList ();

        assertEquals (1, providers.size ());
        assertEquals (PullCoreProvider.class, providers.getFirst ().getClass ());
        assertEquals (CoreApi.VERSION, providers.getFirst ().descriptor ().apiVersion ());
        assertEquals (embeddedBuildId (), providers.getFirst ().descriptor ().buildId ());
    }


    @Test
    void declaresEveryShellCapabilityUsedByTheCore ()
    {
        final Map<String, Integer> required = new PullCoreProvider ().descriptor ().requiredCapabilities ().versions ();

        assertEquals (Map.ofEntries (
            Map.entry (CoreCapabilities.INPUT_DRUM_FILL, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.BINDING_CLIP_TARGET, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (4)),
            Map.entry (CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (6)),
            Map.entry (CoreCapabilities.OUTPUT_CONTROLLER_MAPPING, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.OUTPUT_CONTROLLER_STATE, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_NOTE_VIEW_PREFERENCE, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.OUTPUT_NOTE_REPEAT, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.INPUT_CONTROLLER, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.ROUTING_CONTROLLER_INPUT, Integer.valueOf (4)),
            Map.entry (CoreCapabilities.SNAPSHOT_CONTROLLER_BRIDGE, Integer.valueOf (10)),
            Map.entry (CoreCapabilities.SUBSCRIPTION_CONTROLLER_BRIDGE, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_TRANSPORT, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_SELECTED_TRACK, Integer.valueOf (3)),
            Map.entry (CoreCapabilities.EFFECT_SESSION_BANK, Integer.valueOf (3)),
            Map.entry (CoreCapabilities.EFFECT_CONTROLLER_BUTTON_CONSUMPTION, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.EFFECT_DRUM_PAD, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_NOTE_INPUT_MIDI, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.SNAPSHOT_PARAMETER_TARGETS, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.EFFECT_PARAMETER_TARGET, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.SNAPSHOT_CONTROLLER_MAPPING_FEEDBACK, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SNAPSHOT_MASTER, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_MASTER, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.OUTPUT_CONTROLLER_DISPLAY, Integer.valueOf (4)),
            Map.entry (CoreCapabilities.OUTPUT_PAD_GRID_OVERLAY, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.OUTPUT_DISPLAY_OVERLAY, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.RENDER_MIXER_CONTROLS, Integer.valueOf (1))), required);
    }


    @Test
    void createsIndependentCoreInstances ()
    {
        final PullCoreProvider provider = new PullCoreProvider ();

        assertNotSame (provider.create (), provider.create ());
    }


    @Test
    void stressFitsMixerTextAndRemovesPositiveNumericSignsWithoutAHost () throws IOException
    {
        final ControllerCore core = new PullCoreProvider ().create ();
        final RgbColor accent = new RgbColor (10, 80, 140);
        final MixerControlsDisplay display = core.renderMixerControls (new MixerControlsSnapshot (List.of (
            hostControl (0, MixerControlKind.VOLUME, "", 0.5, "+3.0 dB", accent, 0.25, 0.5),
            hostControl (1, MixerControlKind.PAN, "", 0.75, "23 R", accent, 0, 0),
            projectMacro (2, "Very Long Project Macro Name", 0.6, "-123.456 dB"),
            projectMacro (3, "Positive Decimal Decibels", 0.4, "+123.456 dB"),
            projectMacro (4, "Very Long Frequency Parameter", 0.7, "+12345.678 kHz"),
            projectMacro (5, "Very Long Millisecond Value", 0.3, "-9876.543 ms"),
            projectMacro (6, "Fine Tune Hundredths", 0.8, "+100.000 ct"),
            projectMacro (7, "Boolean Macro With Long Name", 1, "On"))));
        final List<DisplayCommand> commands = display.controls ().stream ().flatMap (control -> control.scene ().commands ().stream ()).toList ();
        final List<DisplayCommand> volumeCommands = display.controls ().get (0).scene ().commands ();
        final List<DisplayCommand> wideKnobCommands = display.controls ().get (2).scene ().commands ();
        final Set<String> fittedText = commands.stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).collect (Collectors.toSet ());
        final Set<String> unitText = commands.stream ().filter (DisplayCommand.TextAt.class::isInstance).map (DisplayCommand.TextAt.class::cast).map (DisplayCommand.TextAt::text).collect (Collectors.toSet ());

        assertTrue (commands.contains (new DisplayCommand.Rectangle (63, 83, 6, 2, accent)));
        assertTrue (commands.contains (new DisplayCommand.Rectangle (69, 83, 2, 40, accent)));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.Rectangle rectangle && rectangle.width () == 3 && rectangle.height () == 16 && rectangle.color ().equals (accent)));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Volume".equals (text.text ())));
        assertTrue (volumeCommands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "3.0".equals (text.text ()) && text.width () == 58 && text.maximumFontSize () == 19 && text.fit () == DisplayTextFit.SHRINK));
        assertTrue (volumeCommands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "dB".equals (text.text ())));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Pan".equals (text.text ())));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "23".equals (text.text ())));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "R".equals (text.text ())));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Very Long Project Macro Name".equals (text.text ()) && text.maximumFontSize () == 15 && text.minimumFontSize () == 9 && text.fit () == DisplayTextFit.SHRINK_ELLIPSIS));
        assertTrue (wideKnobCommands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "-123.456".equals (text.text ()) && text.width () == 64 && text.maximumFontSize () == 30 && text.minimumFontSize () == 12 && text.fit () == DisplayTextFit.SHRINK));
        assertTrue (wideKnobCommands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "dB".equals (text.text ())));
        assertTrue (fittedText.containsAll (Set.of ("3.0", "23", "-123.456", "123.456", "12345.678", "-9876.543", "100.000", "On")));
        assertTrue (unitText.containsAll (Set.of ("dB", "R", "kHz", "ms", "ct")));
        assertFalse (commands.stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && text.text ().startsWith ("+")));
        assertTrue (commands.stream ().anyMatch (command -> command instanceof DisplayCommand.DottedArc));

        final BufferedImage proof = MixerDisplayStressImage.write (display, Path.of ("target", "display-text-fit-stress.png"));
        assertEquals (960, proof.getWidth ());
        assertEquals (160, proof.getHeight ());
    }


    @Test
    void coreOwnsProjectMacroAccentAndTouchEmphasis ()
    {
        final ControllerCore core = new PullCoreProvider ().create ();
        final MixerControlsDisplay touched = core.renderMixerControls (new MixerControlsSnapshot (List.of (projectMacro (0, "Macro", 0.5, "On", true))));
        final MixerControlsDisplay untouched = core.renderMixerControls (new MixerControlsSnapshot (List.of (projectMacro (0, "Macro", 0.5, "On", false))));

        assertTrue (touched.controls ().getFirst ().scene ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.DottedArc arc && arc.color ().equals (new RgbColor (132, 214, 255))));
        assertTrue (untouched.controls ().getFirst ().scene ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.DottedArc arc && arc.color ().equals (new RgbColor (80, 80, 80))));
    }


    private static MixerControlSnapshot hostControl (final int column, final MixerControlKind kind, final String label, final double value, final String displayedValue, final RgbColor accent, final double vuLeft, final double vuRight)
    {
        return new MixerControlSnapshot (column, kind, label, value, -1, displayedValue, MixerControlRole.HOST_COLORED, true, false, Optional.of (accent), vuLeft, vuRight);
    }


    private static MixerControlSnapshot projectMacro (final int column, final String label, final double value, final String displayedValue)
    {
        return projectMacro (column, label, value, displayedValue, true);
    }


    private static MixerControlSnapshot projectMacro (final int column, final String label, final double value, final String displayedValue, final boolean touched)
    {
        return new MixerControlSnapshot (column, MixerControlKind.KNOB, label, value, -1, displayedValue, MixerControlRole.PROJECT_MACRO, true, touched, Optional.empty (), 0, 0);
    }


    @Test
    void enforcesStartLifecycleAndKeepsCheckpointFreeOfTargetLeases ()
    {
        final PullCoreProvider provider = new PullCoreProvider ();
        final ControllerCore core = provider.create ();
        final ControllerSnapshot snapshot = snapshot ();
        final ButtonInputEvent event = new ButtonInputEvent (1, 0, new ControlId ("other"), true);

        assertThrows (IllegalStateException.class, core::checkpoint);
        assertThrows (IllegalStateException.class, () -> core.handle (event, snapshot));
        core.start (snapshot, Optional.of (new StateEnvelope (provider.descriptor ().stateSchema (), provider.descriptor ().stateSchemaVersion (), new byte []
        {
            1,
            2,
            3
        })));
        assertThrows (IllegalStateException.class, () -> core.start (snapshot, Optional.empty ()));
        core.handle (event, snapshot);

        final StateEnvelope checkpoint = core.checkpoint ();
        assertEquals (provider.descriptor ().stateSchema (), checkpoint.schema ());
        assertEquals (provider.descriptor ().stateSchemaVersion (), checkpoint.version ());
        assertArrayEquals (new byte []
        {
            0, 0, 0, 0, 0, 0, 0, 0
        }, checkpoint.payload ());

    }


    private static ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
    }


    private static String embeddedBuildId () throws IOException
    {
        final Properties properties = new Properties ();
        try (InputStream input = PullCoreProviderTest.class.getResourceAsStream ("/META-INF/pull-core.properties"))
        {
            properties.load (input);
        }
        return properties.getProperty ("buildId");
    }
}
