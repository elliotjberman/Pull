// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Arranger;
import com.bitwig.extension.controller.api.Mixer;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.StringValue;
import de.mossgrabers.bitwig.framework.daw.ApplicationImpl;
import de.mossgrabers.bitwig.framework.daw.ArrangerImpl;
import de.mossgrabers.bitwig.framework.daw.MixerImpl;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IApplication;
import de.mossgrabers.framework.daw.IArranger;
import de.mossgrabers.framework.daw.IMixer;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.pull.core.api.ApplicationUiSnapshot;
import de.mossgrabers.pull.core.api.ArrangerUiSnapshot;
import de.mossgrabers.pull.core.api.MixerUiSnapshot;
import de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect;
import de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect;
import de.mossgrabers.pull.core.api.effect.SetArrangerBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetMixerBooleanEffect;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real framework wrappers over asynchronous native-property fakes. */
class ApplicationUiHostTest
{
    @Test
    void allThirteenAbsoluteWritesWaitForNativeReadback ()
    {
        final Fixture f = new Fixture ();
        final var before = f.host.refresh (true);
        for (final var property: SetArrangerBooleanEffect.Property.values ())
            f.host.apply (f.host.prepare (new SetArrangerBooleanEffect (before.context (), property, true)));
        for (final var property: SetMixerBooleanEffect.Property.values ())
            f.host.apply (f.host.prepare (new SetMixerBooleanEffect (before.context (), property, true)));
        assertEquals (List.of (
            "arranger.isClipLauncherVisible:true", "arranger.isIoSectionVisible:true", "arranger.areCueMarkersVisible:true", "arranger.isTimelineVisible:true", "arranger.areEffectTracksVisible:true", "arranger.isPlaybackFollowEnabled:true", "arranger.hasDoubleRowTrackHeight:true",
            "mixer.isClipLauncherSectionVisible:true", "mixer.isIoSectionVisible:true", "mixer.isCrossFadeSectionVisible:true", "mixer.isDeviceSectionVisible:true", "mixer.isMeterSectionVisible:true", "mixer.isSendSectionVisible:true"), f.writes);
        assertEquals (before, f.host.refresh (true), "void setters only submit requests");
        f.advanceHost ();
        final var after = f.host.refresh (true);
        assertEquals (new ArrangerUiSnapshot (true, true, true, true, true, true, true), after.arranger ());
        assertEquals (new MixerUiSnapshot (true, true, true, true, true, true), after.mixer ());
        assertEquals (before.context (), after.context (), "option values do not change the project/layout identity");
        f.host.apply (f.host.prepare (new SetArrangerBooleanEffect (after.context (), SetArrangerBooleanEffect.Property.TIMELINE_VISIBLE, true)));
        f.advanceHost ();
        assertTrue (f.host.refresh (true).arranger ().timelineVisible (), "an absolute true request is not a toggle");
    }

    @Test
    void subscriptionsGateSamplingWhileInstalledNativeValuesRemainCurrent ()
    {
        final Fixture f = new Fixture ();
        assertEquals (13, f.values.size ());
        assertTrue (f.values.values ().stream ().allMatch (value -> value.interested && value.subscribed));
        assertEquals (ApplicationUiSnapshot.empty (), f.host.refresh (false));
        assertEquals (0, f.reads);
        f.values.get ("arranger.areCueMarkersVisible").observed = true;
        assertTrue (f.host.refresh (true).arranger ().cueMarkersVisible ());
        final int reads = f.reads;
        f.host.refresh (false);
        f.host.refresh (false);
        assertEquals (reads, f.reads, "an unrequested domain does no per-field or identity sampling");
        f.values.get ("arranger.areCueMarkersVisible").observed = false;
        assertFalse (f.host.refresh (true).arranger ().cueMarkersVisible ());
        assertTrue (f.values.values ().stream ().allMatch (value -> value.subscribed));
    }

    @Test
    void everyLayoutAndUnobservedPanelCommandForwardsToItsNativePrimitive ()
    {
        for (final var layout: SetApplicationLayoutEffect.Layout.values ())
        {
            final Fixture f = new Fixture ();
            final var before = f.host.refresh (true);
            f.host.apply (f.host.prepare (new SetApplicationLayoutEffect (before.context (), layout)));
            assertEquals (List.of ("layout:" + layout), f.writes);
            assertEquals (before, f.host.refresh (true));
            f.advanceHost ();
            assertEquals (layout.name (), f.host.refresh (true).panelLayout ());
        }
        final Fixture f = new Fixture ();
        final var before = f.host.refresh (true);
        for (final var panel: ToggleApplicationPanelEffect.Panel.values ())
            f.host.apply (f.host.prepare (new ToggleApplicationPanelEffect (before.context (), panel)));
        assertEquals (List.of ("toggleNoteEditor", "toggleAutomationEditor", "toggleDevices", "toggleMixer", "toggleInspector", "toggleFullScreen"), f.writes);
        assertEquals (before, f.host.refresh (true), "unobservable visibility must not become invented readback");
    }

    @Test
    void projectLayoutAndProxyChangesFailClosedAtPreparationAndApplication ()
    {
        for (int change = 0; change < 5; change++)
        {
            final Fixture f = new Fixture ();
            final var origin = f.host.refresh (true).context ();
            final var effect = new ToggleApplicationPanelEffect (origin, ToggleApplicationPanelEffect.Panel.NOTE_EDITOR);
            final var prepared = f.host.prepare (effect);
            switch (change)
            {
                case 0 -> f.projectId = "other";
                case 1 -> f.panelLayout = "MIX";
                case 2 -> f.application = relaxed (IApplication.class);
                case 3 -> f.arranger = relaxed (IArranger.class);
                case 4 -> f.mixer = relaxed (IMixer.class);
            }
            assertThrows (IllegalArgumentException.class, () -> f.host.prepare (effect));
            f.host.apply (prepared);
            assertTrue (f.writes.isEmpty (), "wrong target case " + change);
        }
    }

    @Test
    void changedAndUnrequestedOriginsInvalidatePriorRequests ()
    {
        final Fixture f = new Fixture ();
        final var first = f.host.refresh (true);
        final var effect = new SetMixerBooleanEffect (first.context (), SetMixerBooleanEffect.Property.SEND_SECTION_VISIBLE, true);
        final var prepared = f.host.prepare (effect);
        f.host.refresh (false);
        f.host.apply (prepared);
        assertTrue (f.writes.isEmpty ());
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (effect));
        assertTrue (f.host.refresh (true).generation () > first.generation (), "resubscribing cannot revive an old request context");
        f.host.apply (prepared);
        assertTrue (f.writes.isEmpty ());
        f.panelLayout = "CUSTOM";
        final var changed = f.host.refresh (true);
        assertEquals ("CUSTOM", changed.panelLayout (), "unrecognized native layouts remain authoritative raw values");
        assertTrue (changed.generation () > first.generation ());
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (effect));
        f.projectId = "";
        assertEquals (ApplicationUiSnapshot.empty (), f.host.refresh (true));
    }

    @Test
    void opaqueEditsDrainResourcesBeforeNativeSubmissionButUiChangesDoNot ()
    {
        final Fixture f = new Fixture ();
        f.frameworkHost.setProjectStructureMutationGuard (() -> f.writes.add ("release"));
        final List<Runnable> edits = List.of (f.application::duplicate, f.application::deleteSelection,
            f.application::undo, f.application::redo, f.application::addAudioTrack,
            f.application::addEffectTrack, f.application::addInstrumentTrack, () -> f.application.invokeAction ("opaque"));
        final List<String> commands = List.of ("duplicate", "remove", "undo", "redo", "createAudioTrack",
            "createEffectTrack", "createInstrumentTrack", "invoke");
        for (int index = 0; index < edits.size (); index++)
        {
            f.writes.clear ();
            edits.get (index).run ();
            assertEquals (List.of ("release", commands.get (index)), f.writes);
        }
        f.writes.clear ();
        f.application.setPanelLayout ("MIX");
        assertEquals (List.of ("layout:MIX"), f.writes, "a presentation change preserves launcher holds");
        f.application.invokeAction ("missing");
        assertEquals (List.of ("layout:MIX"), f.writes, "an unavailable action has no mutation to guard");

        final Fixture failed = new Fixture ();
        failed.frameworkHost.setProjectStructureMutationGuard (() -> { throw new IllegalStateException ("cleanup failed"); });
        assertThrows (IllegalStateException.class, failed.application::deleteSelection);
        assertTrue (failed.writes.isEmpty (), "failed cleanup must prevent the destructive submission");
    }

    private static final class Fixture
    {
        final Map<String, NativeBoolean> values = new LinkedHashMap<> ();
        final List<String> writes = new ArrayList<> ();
        final List<Runnable> pending = new ArrayList<> ();
        String projectId = "project";
        String panelLayout = "ARRANGE";
        int reads;
        final Arranger nativeArranger = this.nativePanel (Arranger.class, "arranger");
        final Mixer nativeMixer = this.nativePanel (Mixer.class, "mixer");
        final StringValue nativeLayout = proxy (StringValue.class, (ignored, method, args) -> {
            if (method.getName ().equals ("get")) { this.reads++; return this.panelLayout; }
            return defaultValue (method.getReturnType ());
        });
        final de.mossgrabers.bitwig.framework.daw.HostImpl frameworkHost = new de.mossgrabers.bitwig.framework.daw.HostImpl (null);
        final Application nativeApplication = proxy (Application.class, (ignored, method, args) -> {
            if (List.of ("duplicate", "remove", "undo", "redo", "createAudioTrack", "createEffectTrack", "createInstrumentTrack").contains (method.getName ()))
            { this.writes.add (method.getName ()); return null; }
            if (method.getName ().equals ("getAction"))
                return "missing".equals (args[0]) ? null : proxy (com.bitwig.extension.controller.api.Action.class, (action, actionMethod, arguments) -> {
                    if (actionMethod.getName ().equals ("invoke")) this.writes.add ("invoke");
                    return defaultValue (actionMethod.getReturnType ());
                });
            if (method.getName ().equals ("panelLayout")) return this.nativeLayout;
            if (method.getName ().equals ("setPanelLayout"))
            {
                final String layout = (String) args[0];
                this.writes.add ("layout:" + layout);
                this.pending.add (() -> this.panelLayout = layout);
                return null;
            }
            if (method.getName ().startsWith ("toggle")) { this.writes.add (method.getName ()); return null; }
            return defaultValue (method.getReturnType ());
        });
        IApplication application = new ApplicationImpl (this.frameworkHost, this.nativeApplication, this.nativeArranger, new TwosComplementValueChanger (128, 1));
        IArranger arranger = new ArrangerImpl (this.nativeArranger);
        IMixer mixer = new MixerImpl (this.nativeMixer);
        final IProject project = proxy (IProject.class, (ignored, method, args) -> {
            if (method.getName ().equals ("getIdentity")) { this.reads++; return this.projectId; }
            return defaultValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (ignored, method, args) -> switch (method.getName ())
        {
            case "getApplication" -> this.application;
            case "getArranger" -> this.arranger;
            case "getMixer" -> this.mixer;
            case "getProject" -> this.project;
            default -> defaultValue (method.getReturnType ());
        });
        final ApplicationUiHost host = new ApplicationUiHost (this.model);

        <T> T nativePanel (final Class<T> type, final String domain)
        {
            return proxy (type, (ignored, method, args) -> method.getReturnType () == SettableBooleanValue.class ?
                this.values.computeIfAbsent (domain + "." + method.getName (), NativeBoolean::new).proxy : defaultValue (method.getReturnType ()));
        }

        void advanceHost () { List.copyOf (this.pending).forEach (Runnable::run); this.pending.clear (); }

        final class NativeBoolean
        {
            boolean observed;
            boolean interested;
            boolean subscribed;
            final SettableBooleanValue proxy;
            NativeBoolean (final String name)
            {
                this.proxy = ApplicationUiHostTest.proxy (SettableBooleanValue.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "markInterested" -> { this.interested = true; yield null; }
                    case "subscribe" -> { this.subscribed = true; yield null; }
                    case "unsubscribe" -> { this.subscribed = false; yield null; }
                    case "get" -> { Fixture.this.reads++; yield this.observed; }
                    case "set" -> { final boolean value = (Boolean) args[0]; Fixture.this.writes.add (name + ":" + value); Fixture.this.pending.add (() -> this.observed = value); yield null; }
                    case "toggle" -> throw new AssertionError ("observed state setters must be absolute");
                    default -> defaultValue (method.getReturnType ());
                });
            }
        }
    }

    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?>[] { type }, handler));
    }

    private static <T> T relaxed (final Class<T> type) { return proxy (type, (ignored, method, args) -> defaultValue (method.getReturnType ())); }

    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == double.class) return 0.0;
        if (type == long.class) return 0L;
        if (type.isInterface ()) return relaxed (type);
        return null;
    }
}
