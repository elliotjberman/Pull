// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.IApplication;
import de.mossgrabers.framework.daw.IArranger;
import de.mossgrabers.framework.daw.IMixer;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.pull.core.api.ApplicationUiContext;
import de.mossgrabers.pull.core.api.ApplicationUiSnapshot;
import de.mossgrabers.pull.core.api.ArrangerUiSnapshot;
import de.mossgrabers.pull.core.api.MixerUiSnapshot;
import de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect;
import de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect;
import de.mossgrabers.pull.core.api.effect.SetArrangerBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetMixerBooleanEffect;

import java.util.Objects;

/**
 * Bounded native UI state from the existing eager Application/Arranger/Mixer proxies. Native
 * values remain interested; the requested domain controls all DTO sampling. No view policy lives here.
 */
final class ApplicationUiHost
{
    private final IModel model;
    private final IApplication application;
    private final IArranger arranger;
    private final IMixer mixer;
    private Identity identity;
    private long generation;
    private ApplicationUiSnapshot snapshot = ApplicationUiSnapshot.empty ();

    ApplicationUiHost (final IModel model)
    {
        this.model = Objects.requireNonNull (model, "model");
        this.application = model.getApplication ();
        this.arranger = model.getArranger ();
        this.mixer = model.getMixer ();
        // Frame's old activation-owned subscriptions are deleted with its inert adapter cutover.
        // These thirteen cheap native values stay current even when DTO sampling is unrequested.
        if (this.arranger != null) this.arranger.enableObservers (true);
        if (this.mixer != null) this.mixer.enableObservers (true);
    }

    ApplicationUiSnapshot refresh (final boolean requested)
    {
        if (!requested)
        {
            this.identity = null;
            this.snapshot = ApplicationUiSnapshot.empty ();
            return this.snapshot;
        }
        final Identity live = this.liveIdentity ();
        if (!Objects.equals (live, this.identity))
        {
            this.identity = live;
            this.generation++;
        }
        if (live == null)
            this.snapshot = ApplicationUiSnapshot.empty ();
        else
            this.snapshot = new ApplicationUiSnapshot (this.generation, live.projectId (), live.panelLayout (),
                new ArrangerUiSnapshot (this.arranger.isClipLauncherVisible (), this.arranger.isIoSectionVisible (), this.arranger.areCueMarkersVisible (), this.arranger.isTimelineVisible (), this.arranger.areEffectTracksVisible (), this.arranger.isPlaybackFollowEnabled (), this.arranger.hasDoubleRowTrackHeight ()),
                new MixerUiSnapshot (this.mixer.isClipLauncherSectionVisible (), this.mixer.isIoSectionVisible (), this.mixer.isCrossFadeSectionVisible (), this.mixer.isDeviceSectionVisible (), this.mixer.isMeterSectionVisible (), this.mixer.isSendSectionVisible ()));
        return this.snapshot;
    }

    PreparedLayout prepare (final SetApplicationLayoutEffect effect)
    {
        this.requireOrigin (effect.context ());
        return new PreparedLayout (effect);
    }

    PreparedPanel prepare (final ToggleApplicationPanelEffect effect)
    {
        this.requireOrigin (effect.context ());
        return new PreparedPanel (effect);
    }

    PreparedArranger prepare (final SetArrangerBooleanEffect effect)
    {
        this.requireOrigin (effect.context ());
        return new PreparedArranger (effect);
    }

    PreparedMixer prepare (final SetMixerBooleanEffect effect)
    {
        this.requireOrigin (effect.context ());
        return new PreparedMixer (effect);
    }

    void apply (final PreparedLayout action)
    {
        final var effect = action.effect ();
        if (this.isCurrent (effect.context ()))
            this.application.setPanelLayout (effect.layout ().name ());
    }

    void apply (final PreparedPanel action)
    {
        final var effect = action.effect ();
        if (!this.isCurrent (effect.context ()))
            return;
        switch (effect.panel ())
        {
            case NOTE_EDITOR -> this.application.toggleNoteEditor ();
            case AUTOMATION_EDITOR -> this.application.toggleAutomationEditor ();
            case DEVICES -> this.application.toggleDevices ();
            case MIXER -> this.application.toggleMixer ();
            case INSPECTOR -> this.application.toggleInspector ();
            case FULLSCREEN -> this.application.toggleFullScreen ();
        }
    }

    void apply (final PreparedArranger action)
    {
        final var effect = action.effect ();
        if (!this.isCurrent (effect.context ()))
            return;
        switch (effect.property ())
        {
            case CLIP_LAUNCHER_VISIBLE -> this.arranger.setClipLauncherVisible (effect.enabled ());
            case IO_SECTION_VISIBLE -> this.arranger.setIoSectionVisible (effect.enabled ());
            case CUE_MARKERS_VISIBLE -> this.arranger.setCueMarkersVisible (effect.enabled ());
            case TIMELINE_VISIBLE -> this.arranger.setTimelineVisible (effect.enabled ());
            case EFFECT_TRACKS_VISIBLE -> this.arranger.setEffectTracksVisible (effect.enabled ());
            case PLAYBACK_FOLLOW_ENABLED -> this.arranger.setPlaybackFollowEnabled (effect.enabled ());
            case DOUBLE_ROW_TRACK_HEIGHT -> this.arranger.setDoubleRowTrackHeight (effect.enabled ());
        }
    }

    void apply (final PreparedMixer action)
    {
        final var effect = action.effect ();
        if (!this.isCurrent (effect.context ()))
            return;
        switch (effect.property ())
        {
            case CLIP_LAUNCHER_VISIBLE -> this.mixer.setClipLauncherSectionVisible (effect.enabled ());
            case IO_SECTION_VISIBLE -> this.mixer.setIoSectionVisible (effect.enabled ());
            case CROSS_FADE_VISIBLE -> this.mixer.setCrossFadeSectionVisible (effect.enabled ());
            case DEVICE_SECTION_VISIBLE -> this.mixer.setDeviceSectionVisible (effect.enabled ());
            case METER_SECTION_VISIBLE -> this.mixer.setMeterSectionVisible (effect.enabled ());
            case SEND_SECTION_VISIBLE -> this.mixer.setSendSectionVisible (effect.enabled ());
        }
    }

    private void requireOrigin (final ApplicationUiContext context)
    {
        if (!this.isCurrent (context))
            throw new IllegalArgumentException ("application UI request origin is stale or unavailable");
    }

    private boolean isCurrent (final ApplicationUiContext context)
    {
        final Identity live = this.liveIdentity ();
        return this.snapshot.available () && this.snapshot.context ().equals (context) && live != null &&
            context.projectId ().equals (live.projectId ()) && context.panelLayout ().equals (live.panelLayout ());
    }

    private Identity liveIdentity ()
    {
        if (this.application == null || this.arranger == null || this.mixer == null || this.model.getApplication () != this.application || this.model.getArranger () != this.arranger || this.model.getMixer () != this.mixer || this.model.getProject () == null)
            return null;
        final String project = this.model.getProject ().getIdentity ();
        final String layout = this.application.getPanelLayout ();
        return project == null || project.isBlank () || layout == null || layout.length () > 128 ? null : new Identity (project, layout);
    }

    private record Identity (String projectId, String panelLayout) { }
    record PreparedLayout (SetApplicationLayoutEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedPanel (ToggleApplicationPanelEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedArranger (SetArrangerBooleanEffect effect) implements ControllerBridge.PreparedAction { }
    record PreparedMixer (SetMixerBooleanEffect effect) implements ControllerBridge.PreparedAction { }
}
