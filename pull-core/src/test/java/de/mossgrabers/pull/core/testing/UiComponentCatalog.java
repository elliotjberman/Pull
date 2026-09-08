// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.PreRoll;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.page.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.ResponseCurve;
import de.mossgrabers.pull.core.ui.component.Toggle;
import de.mossgrabers.pull.core.ui.PageStyle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** Generate a static review catalog from production renderers, with no host, input or effect path. */
public final class UiComponentCatalog
{
    private static final RgbColor BLUE = new RgbColor (62, 160, 255);
    private static final RgbColor ORANGE = new RgbColor (255, 145, 35);
    private static final RgbColor GREEN = new RgbColor (60, 205, 125);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final Map<RgbColor, String> COMPONENT_COLORS = Map.of (BLUE, "var(--component-color," + DisplaySceneSvg.color (BLUE) + ")", new RgbColor (31, 80, 128), "color-mix(in srgb, var(--component-color), black 50%)");
    private static final List<Boolean> ALTERNATING = List.of (true, false, true, false, false, true, false, true);

    private UiComponentCatalog () { }

    /** Arguments are the output directory and repository root, supplied by tools/ui-component-catalog. */
    public static void main (final String[] args) throws IOException
    {
        if (args.length != 2) throw new IllegalArgumentException ("Expected output directory and repository root");
        final Path output = Path.of (args[0]).toAbsolutePath ();
        final Path root = Path.of (args[1]);
        final Path icons = root.resolve ("pull-shell/src/main/resources/images");
        final Path hardware = root.resolve ("tools/push-debug-surface-app");
        final Path app = root.resolve ("tools/ui-component-catalog-app");
        final List<Component> components = components ();
        final List<Example> examples = examples ();
        validateUniqueArtifacts (components, examples);
        final CatalogTypography typography = CatalogTypography.load ();
        final String displayFont = "<style>" + typography.displayCss () + "</style>";
        Files.createDirectories (output);
        final StringBuilder stories = new StringBuilder ();
        int specimens = 0;
        for (final Component component: components)
        {
            story (stories, component.id (), "components", componentGroup (component), component.title (), component.description ());
            stories.append ("<div class=\"story-meta\"><span>").append (component.variants ().size ()).append (component.variants ().size () == 1 ? " variant" : " variants").append (" · Native logical pixels</span></div><div class=\"specimens\">");
            for (final Variant variant: component.variants ())
            {
                final String file = component.id () + "-" + variant.id () + ".svg";
                final String svg = DisplaySceneSvg.render (variant.display (), icons, typography, component.id () + "-" + variant.id () + "-", component.colors ());
                Files.writeString (output.resolve (file), svg);
                stories.append ("<figure><div class=\"specimen-display\">")
                    .append (svg.replace (displayFont, "").replace ("<svg ", "<svg role=\"img\" aria-label=\"" + DisplaySceneSvg.escape (component.title () + " · " + variant.title ()) + "\" "))
                    .append ("</div><figcaption><span>").append (DisplaySceneSvg.escape (variant.title ())).append ("</span><a href=\"").append (file)
                    .append ("\" download=\"").append (file).append ("\" aria-label=\"Export ").append (DisplaySceneSvg.escape (component.title () + " " + variant.title ()))
                    .append (" SVG\">SVG ↓</a></figcaption>");
                variant.light ().ifPresent (color -> stories.append ("<div class=\"choice-light\"><svg role=\"img\" aria-label=\"Hardware button light\" data-row=\"2\" data-colors=\"")
                    .append (DisplaySceneSvg.color (color)).append ("\"></svg>Button light <span>").append (DisplaySceneSvg.color (color)).append ("</span></div>"));
                stories.append ("</figure>");
                specimens++;
            }
            stories.append ("</div>");
            source (stories, component.renderer (), "Components are shown at their native size. SVG exports include the selected color and embedded display font.");
            stories.append ("</article>");
        }
        for (final Example example: examples)
        {
            final String file = example.id () + ".svg";
            final ControllerDisplayScene display = example.visuals ().display ();
            Files.writeString (output.resolve (file), DisplaySceneSvg.render (display, icons, typography));
            story (stories, example.id (), "views", example.group (), example.title (), example.description ());
            stories.append ("<div class=\"story-meta\"><span>").append (display.width ()).append (" × ").append (display.height ())
                .append (" logical pixels</span><a href=\"").append (file).append ("\" download=\"").append (file).append ("\">Export display SVG ↓</a></div><div class=\"surface\">");
            lights (stories, example.visuals (), 2);
            stories.append ("<div class=\"screen-area\"><div class=\"surface-label\">Display</div><div class=\"screen\">");
            image (stories, file, example.title (), display);
            stories.append ("</div></div>");
            lights (stories, example.visuals (), 1);
            stories.append ("</div><div class=\"hardware-key\"><span>Dashed: no light state from this view</span><span>Black: button off</span></div>");
            source (stories, example.renderer (), "The screen and hardware buttons use the same presentation assets as the Push debugger.");
            stories.append ("</article>");
        }
        final String html = Files.readString (app.resolve ("index.html"))
            .replace ("{{TYPOGRAPHY_CSS}}", typography.catalogCss ())
            .replace ("{{HARDWARE_CSS}}", Files.readString (hardware.resolve ("push-hardware.css")))
            .replace ("{{APP_CSS}}", Files.readString (app.resolve ("catalog.css")))
            .replace ("{{COMPONENT_COUNT}}", Integer.toString (components.size ()))
            .replace ("{{VIEW_COUNT}}", Integer.toString (examples.size ()))
            .replace ("{{DEFAULT_COLOR}}", DisplaySceneSvg.color (BLUE))
            .replace ("{{NAVIGATION}}", navigation (components, examples))
            .replace ("{{STORIES}}", stories.toString ())
            .replace ("{{DISPLAY_FONT}}", displayFont)
            .replace ("{{HARDWARE_JS}}", Files.readString (hardware.resolve ("push-hardware.js")))
            .replace ("{{APP_JS}}", Files.readString (app.resolve ("catalog.js")));
        Files.writeString (output.resolve ("index.html"), html);
        System.out.println ("UI component catalog: " + output.resolve ("index.html"));
        System.out.println (components.size () + " components (" + specimens + " isolated variants) and " + examples.size () + " view previews generated from production renderer output.");
    }

    private static void story (final StringBuilder html, final String id, final String kind, final String group, final String title, final String description)
    {
        html.append ("<article class=\"story\" id=\"").append (id).append ("\" data-kind=\"").append (kind).append ("\" data-group=\"")
            .append (DisplaySceneSvg.escape (group)).append ("\" data-title=\"").append (DisplaySceneSvg.escape (title)).append ("\" hidden><p class=\"story-description\">")
            .append (DisplaySceneSvg.escape (description)).append ("</p>");
    }

    private static void source (final StringBuilder html, final String renderer, final String note)
    {
        html.append ("<details class=\"source-details\"><summary>Source and preview details</summary><p><code>")
            .append (DisplaySceneSvg.escape (renderer)).append ("</code><br>").append (DisplaySceneSvg.escape (note)).append ("</p></details>");
    }

    private static String navigation (final List<Component> components, final List<Example> examples)
    {
        final Map<String, List<NavigationItem>> groups = new LinkedHashMap<> ();
        for (final Component component: components)
        {
            final String group = componentGroup (component);
            groups.computeIfAbsent ("components/" + group, ignored -> new ArrayList<> ()).add (new NavigationItem (component.id (), "components", group, component.title (), component.description ()));
        }
        for (final Example example: examples)
            groups.computeIfAbsent ("views/" + example.group (), ignored -> new ArrayList<> ()).add (new NavigationItem (example.id (), "views", example.group (), example.title (), example.description ()));
        final StringBuilder html = new StringBuilder ();
        for (final List<NavigationItem> group: groups.values ())
        {
            html.append ("<section class=\"nav-group\"><h2>").append (DisplaySceneSvg.escape (group.getFirst ().group ())).append ("</h2>");
            for (final NavigationItem item: group)
                html.append ("<a href=\"#").append (item.id ()).append ("\" data-story-link=\"").append (item.id ()).append ("\" data-kind=\"").append (item.kind ())
                    .append ("\" data-search=\"").append (DisplaySceneSvg.escape ((item.title () + " " + item.group () + " " + item.description ()).toLowerCase (java.util.Locale.ROOT)))
                    .append ("\">").append (DisplaySceneSvg.escape (item.title ())).append ("</a>");
            html.append ("</section>");
        }
        return html.toString ();
    }

    private static String componentGroup (final Component component)
    {
        return switch (component.id ())
        {
            case "component-choice", "component-toggle", "component-option-column" -> "Selection";
            case "component-list", "component-color-palette" -> "Content";
            default -> "Meters and parameters";
        };
    }

    /** Check the complete plan before writing any artifact; component variants and views share a directory. */
    private static void validateUniqueArtifacts (final List<Component> components, final List<Example> examples)
    {
        final Set<String> ids = new HashSet<> (Set.of ("library-navigation", "story-navigation", "story-search", "main-panel", "story-group", "story-title", "story-permalink", "story-workspace", "color-controls", "component-color", "component-color-hex", "reset-color", "color-error", "stories", "display-font", "no-results", "nav-toggle", "nav-backdrop"));
        final Set<String> files = new HashSet<> (Set.of ("index.html"));
        for (final Component component: components)
        {
            unique (ids, component.id (), "story ID");
            for (final Variant variant: component.variants ()) unique (files, component.id () + "-" + variant.id () + ".svg", "artifact filename");
        }
        for (final Example example: examples)
        {
            unique (ids, example.id (), "story ID");
            unique (files, example.id () + ".svg", "artifact filename");
        }
    }

    private static void unique (final Set<String> values, final String value, final String kind)
    {
        if (!values.add (value)) throw new IllegalStateException ("Duplicate catalog " + kind + ": " + value);
    }

    private record NavigationItem (String id, String kind, String group, String title, String description) { }

    private static void image (final StringBuilder html, final String file, final String title, final ControllerDisplayScene display)
    {
        html.append ("<img width=\"").append (display.width ()).append ("\" height=\"").append (display.height ())
            .append ("\" src=\"").append (file).append ("\" alt=\"").append (DisplaySceneSvg.escape (title)).append ("\">");
    }

    private static List<Example> examples ()
    {
        final List<Example> result = new ArrayList<> ();
        result.add (new Example ("automation-read", "Settings", "Automation · Read", "Read is selected; every option and its row light comes from the same observed state.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, false, AutomationWriteMode.LATCH))));
        result.add (new Example ("automation-touch", "Settings", "Automation · Touch", "Writing enabled with Touch selected.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, true, AutomationWriteMode.TOUCH))));
        result.add (new Example ("automation-unknown", "Settings", "Automation · unknown host mode", "No known option is selected while the host reports an unknown writing mode.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, true, AutomationWriteMode.UNKNOWN))));
        result.add (new Example ("metronome", "Settings", "Metronome · pre-roll and volume", "Two-bar pre-roll, during-pre-roll enabled, and an observed volume with modulation.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Metronome (true, PreRoll.TWO_BARS, true, Optional.of (new SettingsPagePresentation.Volume (0.75, 0.9, "−6.00 dB"))))));
        result.add (new Example ("metronome-no-volume", "Settings", "Metronome · unavailable volume", "The page is available but its volume control is absent; no parameter value is invented.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Metronome (true, PreRoll.NONE, false, Optional.empty ()))));
        result.add (new Example ("settings-unavailable", "Settings", "Settings · unavailable", "Unavailable host state clears the page and both owned button rows.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (false, true, AutomationWriteMode.WRITE))));
        for (final FramePagePresentation.Layout layout: List.of (FramePagePresentation.Layout.ARRANGE, FramePagePresentation.Layout.MIX, FramePagePresentation.Layout.EDIT))
            result.add (new Example ("frame-" + layout.name ().toLowerCase (java.util.Locale.ROOT), "Frame", "Frame · " + layout, "Selected and unselected options, grouped headings, and layout-specific unavailable cells.", "FramePageRenderer", FramePageRenderer.render (new FramePagePresentation (true, layout, ALTERNATING))));
        result.add (new Example ("frame-unavailable", "Frame", "Frame · unavailable", "Even selected presentation flags cannot invent options when the host state is unavailable.", "FramePageRenderer", FramePageRenderer.render (new FramePagePresentation (false, FramePagePresentation.Layout.ARRANGE, Collections.nCopies (8, true)))));
        result.add (new Example ("info-known", "Info", "Info · observed hardware identity", "Configuration navigation uses shared choice cells. Firmware, board revision and serial number occupy aligned, bounded spans.", "InfoPageRenderer", InfoPageRenderer.render (new InfoPagePresentation (true, "1.0 Build 71", "1", "123456789"))));
        result.add (new Example ("info-maximum", "Info", "Info · identity value limits", "Firmware and board limits plus the longest signed serial in the observed transport range remain inside their fields without manual spacing or overlap.", "InfoPageRenderer", InfoPageRenderer.render (new InfoPagePresentation (true, "127.127 Build 16383", "127", "-2147483648"))));
        result.add (new Example ("info-unavailable", "Info", "Info · awaiting hardware identity", "Unknown hardware shows an explicit waiting state. Info remains selected and Setup remains available; lower-row lights stay off.", "InfoPageRenderer", InfoPageRenderer.render (new InfoPagePresentation (false, "", "", ""))));

        result.add (new Example ("setup-known", "Setup", "Setup · brightness and pad response", "Display and LED brightness, three pad settings, and supplied velocity samples. Display and Gain are touched; the observed values remain authoritative.", "SetupPageRenderer", SetupPageRenderer.render (new SetupPagePresentation (true, 75, 60, 5, 5, 5, Set.of (1, 5), IntStream.range (0, 128).map (value -> Math.max (1, value)).boxed ().toList ()))));
        result.add (new Example ("setup-limits", "Setup", "Setup · setting limits", "Zero and maximum settings retain their physical columns. The curve contains supplied endpoint samples; it is not recomputed from these fixture settings.", "SetupPageRenderer", SetupPageRenderer.render (new SetupPagePresentation (true, 0, 100, 0, 10, 10, Set.of (2, 4, 6), IntStream.range (0, 128).map (value -> value < 64 ? 1 : 127).boxed ().toList ()))));
        result.add (new Example ("setup-unavailable", "Setup", "Setup · awaiting settings", "Unavailable read-back shows a waiting state while Info/Setup navigation remains available. Retained touch flags cannot invent parameter values or a curve.", "SetupPageRenderer", SetupPageRenderer.render (new SetupPagePresentation (false, -1, -1, -1, -1, -1, Set.of (1, 4), List.of ()))));

        result.add (new Example ("ribbon-known", "Ribbon", "Ribbon · CC and repeat settings", "CC 1 is displayed as the editable value; Modulation remains an unselected quick action. The observed CC function and Period repeat role determine their own selection and lights.", "RibbonPageRenderer", RibbonPageRenderer.render (new RibbonPagePresentation (true, 1, 1, 1))));
        result.add (new Example ("ribbon-limits", "Ribbon", "Ribbon · maximum CC and final choices", "CC 127, Last Touched and Length. The numeric cell is selected on screen with its physical light off; the final two lower cells remain blank and off.", "RibbonPageRenderer", RibbonPageRenderer.render (new RibbonPagePresentation (true, 5, 127, 2))));
        result.add (new Example ("ribbon-unavailable", "Ribbon", "Ribbon · unavailable settings", "Retained function, CC and repeat values cannot render choices or lights without available read-back.", "RibbonPageRenderer", RibbonPageRenderer.render (new RibbonPagePresentation (false, 5, 127, 2))));

        result.add (new Example ("macros-stress", "Macros", "Project macros · text and touch", "Normal, touched, minimum and maximum rings; on/off toggles; long labels and values; an absent eighth control.", "MacroPageRenderer", MacroPageRenderer.render (new MacroPagePresentation (List.of (
            macro (0, "Cutoff", "1.25 kHz", 0.45, MacroPagePresentation.Widget.RING, false),
            macro (1, "Cutoff", "1.25 kHz", 0.45, MacroPagePresentation.Widget.RING, true),
            macro (2, "Very long modulation destination name", "123456.789 Hz", 1, MacroPagePresentation.Widget.RING, false),
            macro (3, "Minimum", "0.00 %", 0, MacroPagePresentation.Widget.RING, false),
            macro (4, "Enabled", "On", 1, MacroPagePresentation.Widget.TOGGLE_ON, true),
            macro (5, "Disabled", "Off", 0, MacroPagePresentation.Widget.TOGGLE_OFF, false),
            macro (6, "Unicode ♯ / ∞", "−∞ dB", 0, MacroPagePresentation.Widget.RING, false))))));
        result.add (new Example ("macros-empty", "Macros", "Project macros · unavailable bank", "No available controls means an empty owned page.", "MacroPageRenderer", MacroPageRenderer.render (new MacroPagePresentation (List.of ()))));
        result.add (new Example ("accent-minimum", "Accent", "Accent · minimum", "Observed velocity 1, untouched.", "AccentPageRenderer", AccentPageRenderer.render (new AccentPagePresentation (true, 1, 0, false))));
        result.add (new Example ("accent-maximum-touch", "Accent", "Accent · maximum and touched", "Observed velocity 127 at the upper endpoint, with physical touch brightness.", "AccentPageRenderer", AccentPageRenderer.render (new AccentPagePresentation (true, 127, 1, true))));
        result.add (new Example ("accent-unavailable", "Accent", "Accent · unavailable", "Touch and a retained numerical value cannot override unavailable state.", "AccentPageRenderer", AccentPageRenderer.render (new AccentPagePresentation (false, 127, 1, true))));

        final List<MixerControlSnapshot> mixer = List.of (
            control (0, MixerControlKind.VOLUME, "Volume", "−6.00 dB", 0.75, true, false, BLUE),
            control (1, MixerControlKind.PAN, "Pan", "50.0 L", 0.25, true, true, BLUE),
            control (2, MixerControlKind.KNOB, "A very long send destination", "123456.789 ms", 1, true, false, ORANGE),
            control (3, MixerControlKind.KNOB, "Unavailable", "", 0, false, false, GREEN),
            control (4, MixerControlKind.KNOB, "Send minimum", "−∞ dB", 0, true, false, GREEN),
            control (5, MixerControlKind.KNOB, "Send maximum", "+12.00 dB", 1, true, true, ORANGE));
        result.add (new Example ("track-mixer", "Track mix", "Track mixer · parameter stress", "Volume, pan, long send names, missing value, minimum, maximum, touched, and inactive controls.", "TrackMixerPageRenderer", TrackMixerPageRenderer.render (new TrackMixerPagePresentation (false, false, true, Optional.of (BLUE), List.of (), mixer))));
        result.add (new Example ("mixer-text-stress", "Track mix", "Mixer · provider text regression", "The provider test's exact observed values: long labels, signed numbers, separate units, pan direction and stereo meters. Reviewed through the same catalog as every other component.", "TrackMixerPageRenderer / MixerTextStressFixture", TrackMixerPageRenderer.render (new TrackMixerPagePresentation (false, false, false, Optional.of (MixerTextStressFixture.ACCENT), List.of (), MixerTextStressFixture.snapshot ().controls ()))));
        result.add (new Example ("track-io", "Track mix", "Track mixer · input and output", "Long metadata names expose the current layout; inactive output and missing controls remain visible for review.", "TrackMixerPageRenderer", TrackMixerPageRenderer.render (new TrackMixerPagePresentation (true, true, false, Optional.of (ORANGE), List.of (
            new TrackMixerPagePresentation.Metadata (0, "Input", "A very long hardware input name", true),
            new TrackMixerPagePresentation.Metadata (1, "Output", "Unavailable", false)), mixer.subList (2, 6)))));
        final List<GlobalMixerPagePresentation.MenuItem> menu = List.of (
            new GlobalMixerPagePresentation.MenuItem ("Volume", true, false), new GlobalMixerPagePresentation.MenuItem ("Pan", false, false),
            new GlobalMixerPagePresentation.MenuItem ("Sends", false, false), new GlobalMixerPagePresentation.MenuItem ("Very long menu label", false, false),
            new GlobalMixerPagePresentation.MenuItem ("", false, false), new GlobalMixerPagePresentation.MenuItem ("", false, false),
            new GlobalMixerPagePresentation.MenuItem ("<", false, true), new GlobalMixerPagePresentation.MenuItem (">", false, true));
        result.add (new Example ("global-mixer", "Global mixer", "Global mixer · shape family", "Volume and stereo VU, left/center/right pan, send meters and rings, with active and inactive colors.", "GlobalMixerPageRenderer", GlobalMixerPageRenderer.render (new GlobalMixerPagePresentation (menu, List.of (
            global (0, GlobalMixerPagePresentation.Widget.VOLUME, "−6.00 dB", 0.75, true, BLUE),
            global (1, GlobalMixerPagePresentation.Widget.VOLUME, "−∞ dB", 0, false, BLUE),
            global (2, GlobalMixerPagePresentation.Widget.PAN, "100 L", 0, true, GREEN),
            global (3, GlobalMixerPagePresentation.Widget.PAN, "Center", 0.5, true, GREEN),
            global (4, GlobalMixerPagePresentation.Widget.PAN, "100 R", 1, true, GREEN),
            global (5, GlobalMixerPagePresentation.Widget.SEND_VOLUME, "+12.0 dB", 1, true, ORANGE),
            global (6, GlobalMixerPagePresentation.Widget.RING, "50 %", 0.5, true, BLUE),
            global (7, GlobalMixerPagePresentation.Widget.RING, "0 %", 0, false, ORANGE))))));

        result.add (new Example ("master", "Master", "Master · project and audio engine", "Four parameters, selected Master footer, engine enabled, long project name, unavailable previous project, and dirty save light.", "MasterPageRenderer", MasterPageRenderer.render (new MasterPagePresentation (mixer.subList (0, 4), new MasterPagePresentation.TrackFooter ("Master", DisplayIcon.MASTER, BLUE, true, true), true, "A very long project name with revision 12345", false, true, true))));
        result.add (new Example ("master-empty", "Master", "Master · engine off and empty project", "Missing parameter controls, inactive track, empty project name, and no project navigation.", "MasterPageRenderer", MasterPageRenderer.render (new MasterPagePresentation (List.of (), new MasterPagePresentation.TrackFooter ("Master", DisplayIcon.MASTER, BLUE, false, false), false, "", false, false, false))));
        result.add (new Example ("footers", "Track footer", "Track footer · selection, icons and availability", "Selected, armed, inactive, grouped and pinned cells use real icon assets. The eighth cell is absent.", "TrackFooterRenderer", TrackFooterRenderer.render (new TrackFooterPresentation (List.of (
            footer (0, "Audio", DisplayIcon.AUDIO_TRACK, BLUE, true, true, false),
            footer (1, "Very long instrument track name", DisplayIcon.INSTRUMENT_TRACK, GREEN, false, true, true),
            footer (2, "Hybrid", DisplayIcon.HYBRID_TRACK, ORANGE, false, true, false),
            footer (3, "Inactive", DisplayIcon.RETURN_TRACK, BLUE, false, false, false),
            footer (4, "Open group", DisplayIcon.GROUP_TRACK_OPEN, WHITE, true, true, false),
            footer (5, "Group", DisplayIcon.GROUP_TRACK, ORANGE, false, true, false),
            footer (6, "Pinned", DisplayIcon.PIN, GREEN, false, true, false))))));
        for (final var example: ListAndOptionCatalogFixtures.examples ()) result.add (new Example (example.id (), example.family (), example.title (), example.description (), "OptionPageRenderer", example.scene ()));
        for (final var example: DevicePageGallery.examples ()) result.add (new Example (example.id (), "Devices", example.title (), example.description (), "DevicePageRenderer", example.display ()));
        for (final var example: EditingPageGallery.examples ()) result.add (new Example (example.id (), "Editing", example.title (), "Production components driven by observed editing state. Frozen physical lights are not part of this display preview.", "EditingPageRenderer", example.display ()));
        for (final var example: UiLibraryCompletionFixtures.views ()) result.add (new Example (example.id (), "Playback and mixer", example.title (), example.description (), "Shared UI renderers", example.visuals ()));
        final List<String> families = List.of ("Master", "Track mix", "Global mixer", "Macros", "Settings", "Frame", "Info", "Setup", "Ribbon", "Accent", "Track footer");
        result.sort (Comparator.comparingInt (example -> {
            final int index = families.indexOf (example.group ());
            return index < 0 ? families.size () : index;
        }));
        return result;
    }

    private static List<Component> components ()
    {
        final ChoiceCell.Style choiceStyle = new ChoiceCell.Style (104, 28, 6, 2, 14, 10);
        final Component choices = new Component ("component-choice", "Choice cell", "Left-aligned text with a 3 px full-height marker. Selected choices are white; unselected choices are dimmed. Unavailable and empty choices remain blank.", "ChoiceCell", List.of (
            choice ("normal", "Available", new ChoiceCell ("Read", true, false), choiceStyle),
            choice ("selected", "Selected", new ChoiceCell ("Touch", true, true), choiceStyle),
            choice ("long", "Long label", new ChoiceCell ("Very long option label", true, false), choiceStyle),
            choice ("selected-long", "Selected · long label", new ChoiceCell ("Very long selected label", true, true), choiceStyle),
            choice ("unavailable", "Unavailable", new ChoiceCell ("Unavailable", false, true), choiceStyle),
            choice ("empty", "Empty label", new ChoiceCell ("", true, true), choiceStyle)));
        final Component toggles = new Component ("component-toggle", "Toggle", "One on/off shape with a supplied color. The consuming view determines state and brightness.", "Toggle", List.of (
            toggle ("on", "On", true, BLUE), toggle ("off", "Off", false, BLUE)), COMPONENT_COLORS);
        final Component rings = new Component ("component-ring", "Ring meter", "A value and separate track/value colors, drawn with the shared macro-family geometry. Each ring stands on its own.", "RingMeter / MacroPageStyle.RING", List.of (
            ring ("minimum", "Minimum · 0%", 0, BLUE), ring ("midpoint", "Midpoint · 50%", 0.5, BLUE), ring ("maximum", "Maximum · 100%", 1, BLUE)), COMPONENT_COLORS);
        final Component parameters = new Component ("component-parameter", "Parameter value", "Value and unit fields are fitted independently. These samples isolate typography from the meter, using the shared macro-family style.", "ParameterValue / MacroPageStyle.VALUE", List.of (
            parameter ("minimum", "Minimum", "0.00", "%", BLUE), parameter ("maximum", "Maximum", "100", "%", BLUE),
            parameter ("frequency", "Value + unit", "1.25", "kHz", BLUE), parameter ("long-number", "Long number", "123456.789", "Hz", BLUE),
            parameter ("long-text", "Text value", "A very long textual value", "", BLUE), parameter ("long-unit", "Long unit", "−∞", "VeryLongUnit", BLUE),
            parameter ("unicode", "Unicode", "−∞", "dB", BLUE), parameter ("empty", "Absent value", "", "", BLUE)), COMPONENT_COLORS);
        final Component curves = new Component ("component-curve", "Response curve", "Supplied samples joined inside a bounded area. Empty data draws nothing; the shared color input changes the curve stroke.", "ResponseCurve", List.of (
            curve ("linear", "Linear", IntStream.range (0, 128).mapToObj (value -> value / 127.0).toList ()),
            curve ("minimum", "Minimum", List.of (0.0, 0.0)), curve ("maximum", "Maximum", List.of (1.0, 1.0)),
            curve ("step", "Endpoint step", List.of (0.0, 0.0, 1.0, 1.0)), curve ("empty", "Unavailable", List.of ())), COMPONENT_COLORS);
        final List<Component> result = new ArrayList<> (List.of (choices, toggles, rings, parameters, curves));
        for (final var example: ListAndOptionCatalogFixtures.components ())
        {
            final String renderer = switch (example.id ())
            {
                case "component-list" -> "TextList";
                case "component-option-column" -> "OptionColumn";
                case "component-color-palette" -> "ColorPaletteRenderer";
                default -> throw new IllegalStateException ("Unknown component fixture: " + example.id ());
            };
            result.add (new Component (example.id (), example.title (), example.description (), renderer, List.of (new Variant ("default", "Default", example.scene ())), COMPONENT_COLORS));
        }
        for (final var component: UiLibraryCompletionFixtures.components ())
            result.add (new Component (component.id (), component.title (), component.description (), component.renderer (),
                component.variants ().stream ().map (variant -> new Variant (variant.id (), variant.title (), variant.display ())).toList (), COMPONENT_COLORS));
        return result;
    }

    private static Variant choice (final String id, final String title, final ChoiceCell cell, final ChoiceCell.Style style)
    {
        final int width = (int) style.width () + 16;
        final int height = (int) style.height () + 16;
        final List<DisplayCommand> commands = background (width, height);
        cell.append (commands, 8, 8, style);
        return new Variant (id, title, new ControllerDisplayScene (width, height, commands), Optional.of (cell.lightColor ()));
    }

    private static Variant toggle (final String id, final String title, final boolean on, final RgbColor color)
    {
        final int width = (int) PageStyle.TOGGLE_WIDTH + 16;
        final int height = (int) PageStyle.TOGGLE_HEIGHT + 16;
        final List<DisplayCommand> commands = background (width, height);
        Toggle.append (commands, 8, height / 2.0, on, color);
        return new Variant (id, title, new ControllerDisplayScene (width, height, commands));
    }

    private static Variant ring (final String id, final String title, final double value, final RgbColor color)
    {
        final int size = (int) Math.ceil (2 * (MacroPageStyle.RING.radius () + MacroPageStyle.RING.dotRadius ())) + 16;
        final List<DisplayCommand> commands = background (size, size);
        RingMeter.append (commands, size / 2.0, size / 2.0, value, MacroPageStyle.METER_OFF, color, MacroPageStyle.RING);
        return new Variant (id, title, new ControllerDisplayScene (size, size, commands));
    }

    private static Variant parameter (final String id, final String title, final String value, final String unit, final RgbColor color)
    {
        final int width = (int) MacroPageStyle.VALUE.width () + 16;
        final int height = (int) MacroPageStyle.VALUE.height () + 16;
        final List<DisplayCommand> commands = background (width, height);
        ParameterValue.append (commands, new ParameterValue.Content (value, unit), 8, 8, color, MacroPageStyle.VALUE);
        return new Variant (id, title, new ControllerDisplayScene (width, height, commands));
    }

    private static Variant curve (final String id, final String title, final List<Double> samples)
    {
        final ResponseCurve.Style style = new ResponseCurve.Style (90, 34, 1);
        final int width = (int) style.width () + 16;
        final int height = (int) style.height () + 16;
        final List<DisplayCommand> commands = background (width, height);
        ResponseCurve.append (commands, samples, 8, 8, BLUE, style);
        return new Variant (id, title, new ControllerDisplayScene (width, height, commands));
    }

    private static List<DisplayCommand> background (final int width, final int height)
    { return new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, width, height, new RgbColor (0, 0, 0)))); }

    private static MacroPagePresentation.Control macro (final int column, final String label, final String value, final double position, final MacroPagePresentation.Widget widget, final boolean touched)
    { return new MacroPagePresentation.Control (column, label, value, position, widget, touched); }

    private static MixerControlSnapshot control (final int column, final MixerControlKind kind, final String label, final String value, final double position, final boolean enabled, final boolean touched, final RgbColor color)
    { return new MixerControlSnapshot (column, kind, label, position, -1, value, MixerControlRole.HOST_COLORED, enabled, touched, Optional.of (color), 0.65, 0.9); }

    private static GlobalMixerPagePresentation.Control global (final int column, final GlobalMixerPagePresentation.Widget widget, final String value, final double position, final boolean active, final RgbColor color)
    { return new GlobalMixerPagePresentation.Control (column, widget, value, position, active, color, 0.65, 0.9); }

    private static TrackFooterPresentation.Cell footer (final int column, final String name, final DisplayIcon icon, final RgbColor color, final boolean selected, final boolean active, final boolean armed)
    { return new TrackFooterPresentation.Cell (column, name, icon, color, selected, active, armed); }

    private static void lights (final StringBuilder html, final PageVisuals visuals, final int row)
    {
        final String label = row == 2 ? "Upper" : "Lower";
        html.append ("<div class=\"hardware-row\"><div class=\"surface-label\">").append (label)
            .append (" hardware buttons</div><svg class=\"lights\" role=\"img\" aria-label=\"").append (label)
            .append (" hardware button lights\" data-row=\"").append (row).append ("\" data-colors=\"");
        for (int column = 1; column <= 8; column++)
        {
            final RgbColor color = visuals.lights ().get (PushControlIds.button ("ROW" + row + "_" + column));
            if (column > 1) html.append (',');
            if (color != null) html.append (DisplaySceneSvg.color (color));
        }
        html.append ("\"></svg></div>");
    }

    private record Example (String id, String group, String title, String description, String renderer, PageVisuals visuals)
    {
        private Example (final String id, final String group, final String title, final String description, final String renderer, final ControllerDisplayScene display)
        { this (id, group, title, description, renderer, new PageVisuals (Map.of (), display)); }
    }

    private record Component (String id, String title, String description, String renderer, List<Variant> variants, Map<RgbColor, String> colors)
    {
        private Component (final String id, final String title, final String description, final String renderer, final List<Variant> variants)
        { this (id, title, description, renderer, variants, Map.of ()); }
    }

    private record Variant (String id, String title, ControllerDisplayScene display, Optional<RgbColor> light)
    {
        private Variant (final String id, final String title, final ControllerDisplayScene display)
        { this (id, title, display, Optional.empty ()); }
    }

}
