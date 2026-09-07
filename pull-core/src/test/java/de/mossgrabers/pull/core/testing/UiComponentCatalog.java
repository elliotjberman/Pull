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
import de.mossgrabers.pull.core.ui.component.Toggle;
import de.mossgrabers.pull.core.ui.PageStyle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Generate a static review catalog from production renderers, with no host, input or effect path. */
public final class UiComponentCatalog
{
    private static final RgbColor BLUE = new RgbColor (62, 160, 255);
    private static final RgbColor ORANGE = new RgbColor (255, 145, 35);
    private static final RgbColor GREEN = new RgbColor (60, 205, 125);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final Map<RgbColor, String> COMPONENT_COLORS = Map.of (BLUE, "var(--component-color," + DisplaySceneSvg.color (BLUE) + ")");
    private static final List<Boolean> ALTERNATING = List.of (true, false, true, false, false, true, false, true);

    private UiComponentCatalog () { }

    /** Arguments are the output directory and repository root, supplied by tools/ui-component-catalog. */
    public static void main (final String[] args) throws IOException
    {
        if (args.length != 2) throw new IllegalArgumentException ("Expected output directory and repository root");
        final Path output = Path.of (args[0]).toAbsolutePath ();
        final Path icons = Path.of (args[1]).resolve ("pull-shell/src/main/resources/images");
        Files.createDirectories (output);
        final CatalogTypography typography = CatalogTypography.load ();
        final String displayFont = "<style>" + typography.displayCss () + "</style>";
        final StringBuilder html = new StringBuilder (HEADER.replace ("/* typography */", typography.catalogCss ()).replace ("$FONT_FAMILY", CatalogTypography.FAMILY));
        final List<Component> components = components ();
        final List<Example> examples = examples ();
        html.append ("<nav class=\"sections\" aria-label=\"Catalog sections\"><a href=\"#components\">Components <span>").append (components.size ())
            .append ("</span></a><a href=\"#views\">Views <span>").append (examples.size ()).append ("</span></a></nav><main>");
        html.append ("<section id=\"components\" aria-labelledby=\"components-title\"><div class=\"section-heading\"><small>Building blocks</small><h2 id=\"components-title\">Components</h2>")
            .append ("<p>Individual controls at their native size. Compare states and values with your own color, using the same drawing components as the views below.</p></div>")
            .append (COLOR_CONTROLS.replace ("$DEFAULT_COLOR", DisplaySceneSvg.color (BLUE)));
        int specimens = 0;
        for (final Component component: components)
        {
            html.append ("<article id=\"").append (component.id ()).append ("\"><h3>").append (component.title ()).append ("</h3><p>")
                .append (component.description ()).append ("</p><div class=\"specimens\">");
            for (final Variant variant: component.variants ())
            {
                final String file = component.id () + "-" + variant.id () + ".svg";
                final String svg = DisplaySceneSvg.render (variant.display (), icons, typography, component.id () + "-" + variant.id () + "-", component.colors ());
                Files.writeString (output.resolve (file), svg);
                html.append ("<figure><div class=\"specimen-display\">");
                html.append (svg.replace (displayFont, "").replace ("<svg ", "<svg role=\"img\" aria-label=\"" + DisplaySceneSvg.escape (component.title () + " · " + variant.title ()) + "\" "));
                html.append ("</div><figcaption><span>").append (variant.title ()).append ("</span><a href=\"").append (file).append ("\" aria-label=\"Open ")
                    .append (component.title ()).append (' ').append (variant.title ()).append (" SVG\">SVG ↗</a></figcaption>");
                variant.light ().ifPresent (color -> html.append ("<div class=\"choice-light\"><i style=\"--light:").append (DisplaySceneSvg.color (color))
                    .append ("\"></i>Light color <span>").append (DisplaySceneSvg.color (color)).append ("</span></div>"));
                html.append ("</figure>");
                specimens++;
            }
            html.append ("</div><footer><code>").append (component.renderer ()).append ("</code> · Native logical pixels; no scaling</footer></article>");
        }
        html.append ("</section><section id=\"views\" aria-labelledby=\"views-title\"><div class=\"section-heading\"><small>In context</small><h2 id=\"views-title\">Views</h2>")
            .append ("<p>Known pages and shared view regions, assembled by their production renderers. Each preview uses supplied values and shows the row lights it owns.</p></div>")
            .append ("<nav class=\"view-filters\" aria-label=\"View families\"><button aria-pressed=\"true\" data-filter=\"All\">All views</button>");
        examples.stream ().map (Example::group).distinct ().forEach (group -> html.append ("<button aria-pressed=\"false\" data-filter=\"")
            .append (group).append ("\">").append (group).append ("</button>"));
        html.append ("</nav>");
        for (final Example example: examples)
        {
            final String file = example.id () + ".svg";
            Files.writeString (output.resolve (file), DisplaySceneSvg.render (example.visuals ().display (), icons, typography));
            html.append ("<article id=\"").append (example.id ()).append ("\" data-group=\"").append (example.group ()).append ("\"><header><div><small>")
                .append (example.group ()).append ("</small><h3>").append (DisplaySceneSvg.escape (example.title ())).append ("</h3></div><a href=\"")
                .append (file).append ("\">Open SVG ↗</a></header><p>").append (DisplaySceneSvg.escape (example.description ())).append ("</p><div class=\"surface\">");
            lights (html, example.visuals (), 2);
            image (html, file, example.title (), example.visuals ().display ());
            lights (html, example.visuals (), 1);
            html.append ("</div><footer><code>").append (example.renderer ()).append ("</code> · ")
                .append (example.visuals ().display ().width ()).append (" × ").append (example.visuals ().display ().height ())
                .append (" logical pixels</footer></article>\n");
        }
        Files.writeString (output.resolve ("index.html"), html.append ("</section></main><template id=\"display-font\">").append (displayFont).append ("</template>").append (FOOTER).toString ());
        System.out.println ("UI component catalog: " + output.resolve ("index.html"));
        System.out.println (components.size () + " components (" + specimens + " isolated variants) and " + examples.size () + " view previews generated from production renderer output.");
    }

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
        final List<String> families = List.of ("Master", "Track mix", "Global mixer", "Macros", "Settings", "Frame", "Info", "Accent", "Track footer");
        result.sort (Comparator.comparingInt (example -> {
            final int index = families.indexOf (example.group ());
            return index < 0 ? families.size () : index;
        }));
        return result;
    }

    private static List<Component> components ()
    {
        final ChoiceCell.Style choiceStyle = new ChoiceCell.Style (104, 28, 4, 2, 14, 10, new RgbColor (30, 30, 30));
        final Component choices = new Component ("component-choice", "Choice cell", "Availability and selection produce one bounded label and a matching light color. Unavailable and empty choices remain blank.", "ChoiceCell", List.of (
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
        return List.of (choices, toggles, rings, parameters);
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
        html.append ("<div class=\"lights\" aria-label=\"ROW").append (row).append (" lights\">");
        for (int column = 1; column <= 8; column++)
        {
            final RgbColor color = visuals.lights ().get (PushControlIds.button ("ROW" + row + "_" + column));
            html.append ("<span class=\"").append (color == null ? "unowned" : "owned").append ("\" style=\"--light:")
                .append (color == null ? "transparent" : DisplaySceneSvg.color (color)).append ("\" title=\"ROW").append (row).append ('_').append (column)
                .append (": ").append (color == null ? "unowned" : DisplaySceneSvg.color (color)).append ("\"><i></i></span>");
        }
        html.append ("</div>");
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

    private static final String HEADER = """
        <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Pull · UI library</title><style>
        /* typography */
        :root{color-scheme:dark;font:15px/1.5 "$FONT_FAMILY",sans-serif;font-synthesis:none;background:#101216;color:#e7eaf0}
        *{box-sizing:border-box}body{max-width:1100px;margin:0 auto;padding:44px 30px 80px}h1{font-size:36px;letter-spacing:-1px;margin:4px 0 12px}
        h1,h2,h3,strong{font-weight:600}h2{font-size:28px;margin:2px 0}h3{font-size:20px;margin:2px 0}p{color:#aeb6c3;max-width:860px;margin:8px 0 18px}
        small{color:#82bfff;font-size:11px;text-transform:uppercase;letter-spacing:1.5px}.intro{padding-bottom:8px}.intro p{max-width:850px}
        .notes{font-size:13px;color:#818e9e}.sections,.view-filters{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0 32px}.sections{border-bottom:1px solid #303641;padding-bottom:26px}
        .sections a{font:inherit;padding:9px 16px;border:1px solid #3c5069;border-radius:7px;background:#202c3a}.sections span{color:#8babc9;margin-left:12px}
        .section-heading{margin:32px 0 24px}section{scroll-margin-top:24px}#views{padding-top:12px;border-top:1px solid #303641;margin-top:44px}
        button{border:1px solid #343c4a;border-radius:7px;background:#1a2029;color:#c2ccd9;padding:8px 14px;cursor:pointer;font:inherit}
        button[aria-pressed=true]{background:#bddcff;color:#101216;border-color:#bddcff}article{margin:0 0 28px;padding:24px;background:#181c23;border:1px solid #2a303a;border-radius:12px;scroll-margin-top:20px}
        article header{display:flex;justify-content:space-between;align-items:center;gap:20px}a{color:#a4d0ff;text-decoration:none;white-space:nowrap;font-size:13px}a:hover{text-decoration:underline}
        .surface{background:#07080b;padding:12px;border-radius:7px;overflow:auto}.surface img{display:block;max-width:100%;height:auto;margin:8px auto}
        .lights{display:grid;grid-template-columns:repeat(8,1fr);gap:2px;max-width:960px;margin:0 auto}.lights span{display:block;padding:0 10px}
        .lights i,.choice-light i{display:block;height:5px;background:var(--light);border:1px solid #3a404a;border-radius:2px}.lights .unowned i{border-style:dashed;opacity:.4}
        .specimens{display:grid;grid-template-columns:repeat(auto-fill,minmax(138px,1fr));gap:12px}.specimens figure{margin:0;min-width:0}
        .specimen-display{height:96px;display:flex;align-items:center;justify-content:center;background:#000;border:1px solid #2a303a;border-radius:7px}
        .specimen-display svg{display:block;flex:none}.specimens figcaption{display:flex;justify-content:space-between;gap:5px;margin-top:8px;font-size:12px}
        .specimens figcaption a{font-size:11px;color:#8599af}.choice-light{display:flex;gap:6px;align-items:center;color:#818e9e;font-size:10px;margin-top:5px}
        .choice-light i{width:18px;height:6px}.choice-light span{margin-left:auto}article footer{color:#747f90;font-size:12px;margin-top:16px}
        code{font:inherit;color:#9aa9bd}body>footer{color:#818e9e;font-size:13px}[hidden]{display:none!important}
        .component-controls{display:flex;align-items:center;flex-wrap:wrap;gap:10px;position:sticky;top:12px;z-index:1;padding:12px 16px;margin-bottom:24px;background:#202630;border:1px solid #3b4655;border-radius:9px;box-shadow:0 6px 20px #0006}
        .component-controls label{font-weight:600}.component-controls input{font:inherit;border:1px solid #526074;border-radius:5px;background:#101216;color:inherit;height:36px}
        .component-controls input[type=color]{width:42px;padding:3px;cursor:pointer}.component-controls input[type=text]{width:100px;padding:5px 9px;font-variant-numeric:tabular-nums}
        .component-controls button{padding:5px 12px}.component-controls p{margin:0;font-size:12px}.component-controls [aria-invalid=true]{border-color:#ff9b9b}
        #color-error{color:#ffb1b1;flex-basis:100%}:focus-visible{outline:2px solid #a4d0ff;outline-offset:3px}
        @media(max-width:700px){body{padding:24px 12px}article{padding:15px}.surface{padding:6px}article header{align-items:start}h1{font-size:30px}.lights span{padding:0 4px}}
        </style></head><body><div class="intro"><small>Pull / Reloadable UI</small><h1>UI library</h1>
        <p>Explore the individual building blocks, then preview the views built from them. Everything here uses production drawing commands with supplied values. No Bitwig or Push connection is needed.</p>
        <p class="notes">All UI text uses Lato. Display previews are measured with the same Lato Regular font data embedded in each SVG; catalog headings use Lato Semibold. Host rasterization can still differ; verify final typography on Push.</p>
        <p class="notes">View previews: top swatches are ROW2, bottom swatches ROW1. Dashed swatches are unowned; solid black is owned and off.</p></div>
        """;

    private static final String COLOR_CONTROLS = """
        <div class="component-controls" role="group" aria-label="Component preview controls">
        <label for="component-color">Component color</label><input id="component-color" type="color" value="$DEFAULT_COLOR">
        <input id="component-color-hex" type="text" value="$DEFAULT_COLOR" aria-label="Hex color" aria-describedby="color-error" spellcheck="false" maxlength="7">
        <button id="reset-color" type="button">Reset</button><p>Toggle, ring and parameter value</p>
        <p id="color-error" role="status" hidden>Use six hex digits, for example #3ea0ff.</p></div>
        """;

    private static final String FOOTER = """
        <footer>Regenerate with <code>tools/ui-component-catalog</code>. Fixture source: <code>pull-core/src/test/java/de/mossgrabers/pull/core/testing/UiComponentCatalog.java</code>.<br>Offline visual review does not prove host acknowledgements, interaction routing, or live hardware output.</footer>
        <script>
        const components = document.querySelector('#components');
        const picker = document.querySelector('#component-color');
        const hex = document.querySelector('#component-color-hex');
        const error = document.querySelector('#color-error');
        const defaultColor = picker.value;
        const exportFont = new Blob([document.querySelector('#display-font').innerHTML]);
        const exports = Array.from(components.querySelectorAll('figure'), figure => ({
          svg: figure.querySelector('svg').cloneNode(true), link: figure.querySelector('a'), url: null
        }));
        function applyColor(color) {
          picker.value = color;
          components.style.setProperty('--component-color', color);
          hex.removeAttribute('aria-invalid');
          error.hidden = true;
          // Keep native open/save-link actions in sync, sharing the font bytes across exports.
          for (const specimen of exports) {
            specimen.svg.style.setProperty('--component-color', color);
            const source = new XMLSerializer().serializeToString(specimen.svg);
            const end = source.lastIndexOf('</svg>');
            const previous = specimen.url;
            specimen.url = URL.createObjectURL(new Blob([source.slice(0, end), exportFont, source.slice(end)], {type: 'image/svg+xml'}));
            specimen.link.href = specimen.url;
            if (previous) URL.revokeObjectURL(previous);
          }
        }
        picker.addEventListener('input', () => { applyColor(picker.value); hex.value = picker.value; });
        hex.addEventListener('input', () => {
          const value = hex.value.trim().replace(/^#/, '');
          if (/^[0-9a-f]{6}$/i.test(value)) applyColor('#' + value.toLowerCase());
        });
        hex.addEventListener('blur', () => {
          const valid = /^#?[0-9a-f]{6}$/i.test(hex.value.trim());
          hex.setAttribute('aria-invalid', String(!valid));
          error.hidden = valid;
          if (valid) hex.value = picker.value;
        });
        document.querySelector('#reset-color').addEventListener('click', () => { applyColor(defaultColor); hex.value = defaultColor; });
        for (const button of document.querySelectorAll('[data-filter]')) button.addEventListener('click', () => {
          for (const other of document.querySelectorAll('[data-filter]')) other.setAttribute('aria-pressed', String(other === button));
          for (const example of document.querySelectorAll('#views article')) example.hidden = button.dataset.filter !== 'All' && example.dataset.group !== button.dataset.filter;
        });
        </script></body></html>
        """;
}
