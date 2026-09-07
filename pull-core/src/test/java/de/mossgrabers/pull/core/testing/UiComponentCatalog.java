// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.PreRoll;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.page.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.Toggle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Generate a static review catalog from production renderers, with no host, input or effect path. */
public final class UiComponentCatalog
{
    private static final RgbColor BLUE = new RgbColor (62, 160, 255);
    private static final RgbColor ORANGE = new RgbColor (255, 145, 35);
    private static final RgbColor GREEN = new RgbColor (60, 205, 125);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final List<Boolean> ALTERNATING = List.of (true, false, true, false, false, true, false, true);

    private UiComponentCatalog () { }

    /** Arguments are the output directory and repository root, supplied by tools/ui-component-catalog. */
    public static void main (final String[] args) throws IOException
    {
        if (args.length != 2) throw new IllegalArgumentException ("Expected output directory and repository root");
        final Path output = Path.of (args[0]).toAbsolutePath ();
        final Path icons = Path.of (args[1]).resolve ("pull-shell/src/main/resources/images");
        Files.createDirectories (output);
        final StringBuilder html = new StringBuilder (HEADER);
        final List<Example> examples = examples ();
        for (final Example example: examples)
        {
            final String file = example.id () + ".svg";
            Files.writeString (output.resolve (file), DisplaySceneSvg.render (example.visuals ().display (), icons));
            html.append ("<article id=\"").append (example.id ()).append ("\" data-group=\"").append (example.group ()).append ("\"><header><div><small>")
                .append (example.group ()).append ("</small><h2>").append (DisplaySceneSvg.escape (example.title ())).append ("</h2></div><a href=\"")
                .append (file).append ("\">Open SVG ↗</a></header><p>").append (DisplaySceneSvg.escape (example.description ())).append ("</p><div class=\"surface\">");
            lights (html, example.visuals (), 2);
            html.append ("<img width=\"").append (example.visuals ().display ().width ()).append ("\" height=\"").append (example.visuals ().display ().height ())
                .append ("\" src=\"").append (file).append ("\" alt=\"").append (DisplaySceneSvg.escape (example.title ())).append ("\">");
            lights (html, example.visuals (), 1);
            html.append ("</div><footer><code>").append (example.renderer ()).append ("</code> · ")
                .append (example.visuals ().display ().width ()).append (" × ").append (example.visuals ().display ().height ())
                .append (" logical pixels</footer></article>\n");
        }
        Files.writeString (output.resolve ("index.html"), html.append (FOOTER).toString ());
        System.out.println ("UI component catalog: " + output.resolve ("index.html"));
        System.out.println (examples.size () + " examples generated from production renderer output.");
    }

    private static List<Example> examples ()
    {
        final List<Example> result = new ArrayList<> ();
        components (result);
        result.add (new Example ("automation-read", "Choices", "Automation · Read", "Read is selected; every option and its row light comes from the same observed state.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, false, AutomationWriteMode.LATCH))));
        result.add (new Example ("automation-touch", "Choices", "Automation · Touch", "Writing enabled with Touch selected.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, true, AutomationWriteMode.TOUCH))));
        result.add (new Example ("automation-unknown", "Choices", "Automation · unknown host mode", "No known option is selected while the host reports an unknown writing mode.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, true, AutomationWriteMode.UNKNOWN))));
        result.add (new Example ("metronome", "Choices", "Metronome · pre-roll and volume", "Two-bar pre-roll, during-pre-roll enabled, and an observed volume with modulation.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Metronome (true, PreRoll.TWO_BARS, true, Optional.of (new SettingsPagePresentation.Volume (0.75, 0.9, "−6.00 dB"))))));
        result.add (new Example ("metronome-no-volume", "Choices", "Metronome · unavailable volume", "The page is available but its volume control is absent; no parameter value is invented.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Metronome (true, PreRoll.NONE, false, Optional.empty ()))));
        result.add (new Example ("settings-unavailable", "Choices", "Settings · unavailable", "Unavailable host state clears the page and both owned button rows.", "SettingsPageRenderer", SettingsPageRenderer.render (new SettingsPagePresentation.Automation (false, true, AutomationWriteMode.WRITE))));
        for (final FramePagePresentation.Layout layout: List.of (FramePagePresentation.Layout.ARRANGE, FramePagePresentation.Layout.MIX, FramePagePresentation.Layout.EDIT))
            result.add (new Example ("frame-" + layout.name ().toLowerCase (java.util.Locale.ROOT), "Choices", "Frame · " + layout, "Selected and unselected options, grouped headings, and layout-specific unavailable cells.", "FramePageRenderer", FramePageRenderer.render (new FramePagePresentation (true, layout, ALTERNATING))));
        result.add (new Example ("frame-unavailable", "Choices", "Frame · unavailable", "Even selected presentation flags cannot invent options when the host state is unavailable.", "FramePageRenderer", FramePageRenderer.render (new FramePagePresentation (false, FramePagePresentation.Layout.ARRANGE, Collections.nCopies (8, true)))));

        result.add (new Example ("macros-stress", "Parameters", "Project macros · text and touch", "Normal, touched, minimum and maximum rings; on/off toggles; long labels and values; an absent eighth control.", "MacroPageRenderer", MacroPageRenderer.render (new MacroPagePresentation (List.of (
            macro (0, "Cutoff", "1.25 kHz", 0.45, MacroPagePresentation.Widget.RING, false),
            macro (1, "Cutoff", "1.25 kHz", 0.45, MacroPagePresentation.Widget.RING, true),
            macro (2, "Very long modulation destination name", "123456.789 Hz", 1, MacroPagePresentation.Widget.RING, false),
            macro (3, "Minimum", "0.00 %", 0, MacroPagePresentation.Widget.RING, false),
            macro (4, "Enabled", "On", 1, MacroPagePresentation.Widget.TOGGLE_ON, true),
            macro (5, "Disabled", "Off", 0, MacroPagePresentation.Widget.TOGGLE_OFF, false),
            macro (6, "Unicode ♯ / ∞", "−∞ dB", 0, MacroPagePresentation.Widget.RING, false))))));
        result.add (new Example ("macros-empty", "Parameters", "Project macros · unavailable bank", "No available controls means an empty owned page.", "MacroPageRenderer", MacroPageRenderer.render (new MacroPagePresentation (List.of ()))));
        result.add (new Example ("accent-minimum", "Parameters", "Accent · minimum", "Observed velocity 1, untouched.", "AccentPageRenderer", AccentPageRenderer.render (new AccentPagePresentation (true, 1, 0, false))));
        result.add (new Example ("accent-maximum-touch", "Parameters", "Accent · maximum and touched", "Observed velocity 127 at the upper endpoint, with physical touch brightness.", "AccentPageRenderer", AccentPageRenderer.render (new AccentPagePresentation (true, 127, 1, true))));
        result.add (new Example ("accent-unavailable", "Parameters", "Accent · unavailable", "Touch and a retained numerical value cannot override unavailable state.", "AccentPageRenderer", AccentPageRenderer.render (new AccentPagePresentation (false, 127, 1, true))));

        final List<MixerControlSnapshot> mixer = List.of (
            control (0, MixerControlKind.VOLUME, "Volume", "−6.00 dB", 0.75, true, false, BLUE),
            control (1, MixerControlKind.PAN, "Pan", "50.0 L", 0.25, true, true, BLUE),
            control (2, MixerControlKind.KNOB, "A very long send destination", "123456.789 ms", 1, true, false, ORANGE),
            control (3, MixerControlKind.KNOB, "Unavailable", "", 0, false, false, GREEN),
            control (4, MixerControlKind.KNOB, "Send minimum", "−∞ dB", 0, true, false, GREEN),
            control (5, MixerControlKind.KNOB, "Send maximum", "+12.00 dB", 1, true, true, ORANGE));
        result.add (new Example ("track-mixer", "Mixer", "Track mixer · parameter stress", "Volume, pan, long send names, missing value, minimum, maximum, touched, and inactive controls.", "TrackMixerPageRenderer", TrackMixerPageRenderer.render (new TrackMixerPagePresentation (false, false, true, Optional.of (BLUE), List.of (), mixer))));
        result.add (new Example ("mixer-text-stress", "Mixer", "Mixer · provider text regression", "The provider test's exact observed values: long labels, signed numbers, separate units, pan direction and stereo meters. Reviewed through the same catalog as every other component.", "TrackMixerPageRenderer / MixerTextStressFixture", TrackMixerPageRenderer.render (new TrackMixerPagePresentation (false, false, false, Optional.of (MixerTextStressFixture.ACCENT), List.of (), MixerTextStressFixture.snapshot ().controls ()))));
        result.add (new Example ("track-io", "Mixer", "Track mixer · input and output", "Long metadata names expose the current layout; inactive output and missing controls remain visible for review.", "TrackMixerPageRenderer", TrackMixerPageRenderer.render (new TrackMixerPagePresentation (true, true, false, Optional.of (ORANGE), List.of (
            new TrackMixerPagePresentation.Metadata (0, "Input", "A very long hardware input name", true),
            new TrackMixerPagePresentation.Metadata (1, "Output", "Unavailable", false)), mixer.subList (2, 6)))));
        final List<GlobalMixerPagePresentation.MenuItem> menu = List.of (
            new GlobalMixerPagePresentation.MenuItem ("Volume", true, false), new GlobalMixerPagePresentation.MenuItem ("Pan", false, false),
            new GlobalMixerPagePresentation.MenuItem ("Sends", false, false), new GlobalMixerPagePresentation.MenuItem ("Very long menu label", false, false),
            new GlobalMixerPagePresentation.MenuItem ("", false, false), new GlobalMixerPagePresentation.MenuItem ("", false, false),
            new GlobalMixerPagePresentation.MenuItem ("<", false, true), new GlobalMixerPagePresentation.MenuItem (">", false, true));
        result.add (new Example ("global-mixer", "Mixer", "Global mixer · shape family", "Volume and stereo VU, left/center/right pan, send meters and rings, with active and inactive colors.", "GlobalMixerPageRenderer", GlobalMixerPageRenderer.render (new GlobalMixerPagePresentation (menu, List.of (
            global (0, GlobalMixerPagePresentation.Widget.VOLUME, "−6.00 dB", 0.75, true, BLUE),
            global (1, GlobalMixerPagePresentation.Widget.VOLUME, "−∞ dB", 0, false, BLUE),
            global (2, GlobalMixerPagePresentation.Widget.PAN, "100 L", 0, true, GREEN),
            global (3, GlobalMixerPagePresentation.Widget.PAN, "Center", 0.5, true, GREEN),
            global (4, GlobalMixerPagePresentation.Widget.PAN, "100 R", 1, true, GREEN),
            global (5, GlobalMixerPagePresentation.Widget.SEND_VOLUME, "+12.0 dB", 1, true, ORANGE),
            global (6, GlobalMixerPagePresentation.Widget.RING, "50 %", 0.5, true, BLUE),
            global (7, GlobalMixerPagePresentation.Widget.RING, "0 %", 0, false, ORANGE))))));

        result.add (new Example ("master", "Composition", "Master · project and audio engine", "Four parameters, selected Master footer, engine enabled, long project name, unavailable previous project, and dirty save light.", "MasterPageRenderer", MasterPageRenderer.render (new MasterPagePresentation (mixer.subList (0, 4), new MasterPagePresentation.TrackFooter ("Master", DisplayIcon.MASTER, BLUE, true, true), true, "A very long project name with revision 12345", false, true, true))));
        result.add (new Example ("master-empty", "Composition", "Master · engine off and empty project", "Missing parameter controls, inactive track, empty project name, and no project navigation.", "MasterPageRenderer", MasterPageRenderer.render (new MasterPagePresentation (List.of (), new MasterPagePresentation.TrackFooter ("Master", DisplayIcon.MASTER, BLUE, false, false), false, "", false, false, false))));
        result.add (new Example ("footers", "Composition", "Track footer · selection, icons and availability", "Selected, armed, inactive, grouped and pinned cells use real icon assets. The eighth cell is absent.", "TrackFooterRenderer", TrackFooterRenderer.render (new TrackFooterPresentation (List.of (
            footer (0, "Audio", DisplayIcon.AUDIO_TRACK, BLUE, true, true, false),
            footer (1, "Very long instrument track name", DisplayIcon.INSTRUMENT_TRACK, GREEN, false, true, true),
            footer (2, "Hybrid", DisplayIcon.HYBRID_TRACK, ORANGE, false, true, false),
            footer (3, "Inactive", DisplayIcon.RETURN_TRACK, BLUE, false, false, false),
            footer (4, "Open group", DisplayIcon.GROUP_TRACK_OPEN, WHITE, true, true, false),
            footer (5, "Group", DisplayIcon.GROUP_TRACK, ORANGE, false, true, false),
            footer (6, "Pinned", DisplayIcon.PIN, GREEN, false, true, false))))));
        return result;
    }

    private static void components (final List<Example> result)
    {
        final List<DisplayCommand> choices = background ();
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final ChoiceCell.Style choiceStyle = new ChoiceCell.Style (104, 28, 4, 2, 14, 10, new RgbColor (30, 30, 30));
        final List<ChoiceCell> cells = List.of (new ChoiceCell ("Read", true, false), new ChoiceCell ("Touch", true, true),
            new ChoiceCell ("Very long option label", true, false), new ChoiceCell ("Very long selected label", true, true),
            new ChoiceCell ("Unavailable", false, true), new ChoiceCell ("", true, true));
        for (int column = 0; column < cells.size (); column++)
        {
            final ChoiceCell cell = cells.get (column);
            cell.append (choices, column * 120 + 8, 70, choiceStyle);
            lights.put (PushControlIds.button ("ROW1_" + (column + 1)), cell.lightColor ());
        }
        result.add (new Example ("component-choice", "Components", "ChoiceCell · one value, display and light", "Normal, selected, long, selected-long, unavailable, and empty choices. The final two cells intentionally render blank and off.", "ChoiceCell", new PageVisuals (lights, new ControllerDisplayScene (960, 160, choices))));

        final List<DisplayCommand> toggles = background ();
        for (int column = 0; column < 4; column++)
            Toggle.append (toggles, column * 120 + 8, 90, column % 2 == 0, column < 2 ? MacroPageStyle.METER_ON : MacroPageStyle.brightness (MacroPageStyle.METER_ON, false));
        result.add (new Example ("component-toggle", "Components", "Toggle · on, off and supplied colors", "On and off in bright and dim colors. The consuming page resolves the state and color; the toggle only draws.", "Toggle", new PageVisuals (Map.of (), new ControllerDisplayScene (960, 160, toggles))));

        final List<DisplayCommand> parameters = background ();
        final List<ParameterValue.Content> values = List.of (new ParameterValue.Content ("0.00", "%"), new ParameterValue.Content ("50.0", "%"),
            new ParameterValue.Content ("100", "%"), new ParameterValue.Content ("1.25", "kHz"), new ParameterValue.Content ("123456.789", "Hz"),
            new ParameterValue.Content ("A very long textual value", ""), new ParameterValue.Content ("−∞", "VeryLongUnit"), new ParameterValue.Content ("", ""));
        for (int column = 0; column < values.size (); column++)
        {
            final RgbColor color = column == 3 ? MacroPageStyle.METER_ON : MacroPageStyle.brightness (MacroPageStyle.METER_ON, false);
            ParameterValue.append (parameters, values.get (column), column * 120 + 8, MacroPageStyle.VALUE_TOP, color, MacroPageStyle.VALUE);
            RingMeter.append (parameters, column * 120 + 33, MacroPageStyle.RING_CENTER_Y, column == 0 ? 0 : column == 2 ? 1 : 0.5, MacroPageStyle.METER_OFF, color, MacroPageStyle.RING);
        }
        result.add (new Example ("component-parameter", "Components", "ParameterValue + RingMeter · limits and text", "Minimum, midpoint, maximum, bright color, long numeric/textual values and unit, Unicode, and an absent displayed value. Geometry comes from the shared macro style.", "ParameterValue / RingMeter", new PageVisuals (Map.of (), new ControllerDisplayScene (960, 160, parameters))));
    }

    private static List<DisplayCommand> background ()
    { return new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0)))); }

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

    private static final String HEADER = """
        <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Pull · UI component catalog</title><style>
        :root{color-scheme:dark;font:15px/1.5 system-ui,sans-serif;background:#101216;color:#e7eaf0}
        *{box-sizing:border-box}body{max-width:1100px;margin:0 auto;padding:44px 30px 80px}h1{font-size:36px;letter-spacing:-1px;margin:4px 0 12px}
        h2{font-size:20px;margin:2px 0}p{color:#aeb6c3;max-width:860px;margin:8px 0 18px}small{color:#82bfff;font-size:11px;text-transform:uppercase;letter-spacing:1.5px}
        .intro{padding-bottom:22px}.intro p{max-width:850px}nav{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0 32px}
        button{border:1px solid #343c4a;border-radius:7px;background:#1a2029;color:#c2ccd9;padding:8px 14px;cursor:pointer;font:inherit}
        button[aria-pressed=true]{background:#bddcff;color:#101216;border-color:#bddcff}article{margin:0 0 28px;padding:24px;background:#181c23;border:1px solid #2a303a;border-radius:12px}
        article header{display:flex;justify-content:space-between;align-items:center;gap:20px}a{color:#a4d0ff;text-decoration:none;white-space:nowrap;font-size:13px}
        .surface{background:#07080b;padding:12px;border-radius:7px;overflow:auto}.surface img{display:block;max-width:100%;height:auto;margin:8px auto}
        .lights{display:grid;grid-template-columns:repeat(8,1fr);gap:2px;max-width:960px;margin:0 auto}.lights span{display:block;padding:0 10px}
        .lights i{display:block;height:5px;background:var(--light);border:1px solid #3a404a;border-radius:2px}.lights .unowned i{border-style:dashed;opacity:.4}
        article footer{color:#747f90;font-size:12px;margin-top:13px}code{color:#9aa9bd}body>footer{color:#818e9e;font-size:13px}[hidden]{display:none!important}
        @media(max-width:700px){body{padding:24px 12px}article{padding:15px}.surface{padding:6px}article header{align-items:start}h1{font-size:30px}.lights span{padding:0 4px}}
        </style><body><div class="intro"><small>Pull / Reloadable UI</small><h1>Component catalog</h1>
        <p>Actual production drawing commands, collected in one place to review shared controls and the pages built from them. Each example pairs its display with the row lights emitted by the same renderer.</p>
        <p>Top swatches are ROW2, bottom swatches ROW1. Dashed swatches are unowned; solid black is owned and off. Scenes retain their real logical dimensions. These fixtures use supplied read-back values and never contact Bitwig.</p>
        <p>Geometry and icon assets come from the repository. Text fitting uses local Sans Serif metrics, so glyphs, baseline and rasterization can differ from Bitwig; verify final typography on Push.</p></div>
        <nav aria-label="Example groups"><button aria-pressed="true" data-filter="All">All</button><button aria-pressed="false" data-filter="Components">Components</button><button aria-pressed="false" data-filter="Choices">Choices</button><button aria-pressed="false" data-filter="Parameters">Parameters</button><button aria-pressed="false" data-filter="Mixer">Mixer</button><button aria-pressed="false" data-filter="Composition">Composition</button></nav>
        """;

    private static final String FOOTER = """
        <footer>Regenerate with <code>tools/ui-component-catalog</code>. Fixture source: <code>pull-core/src/test/java/de/mossgrabers/pull/core/testing/UiComponentCatalog.java</code>.<br>Offline visual review does not prove host acknowledgements, interaction routing, or live hardware output.</footer>
        <script>for(const button of document.querySelectorAll('[data-filter]'))button.addEventListener('click',()=>{for(const other of document.querySelectorAll('[data-filter]'))other.setAttribute('aria-pressed',String(other===button));for(const example of document.querySelectorAll('article'))example.hidden=button.dataset.filter!=='All'&&example.dataset.group!==button.dataset.filter;});</script></body></html>
        """;
}
