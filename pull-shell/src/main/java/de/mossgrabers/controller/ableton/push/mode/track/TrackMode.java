// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.parameterprovider.PushTrackParameterProvider;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;
import de.mossgrabers.framework.graphics.canvas.component.MixerControlsComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.parameterprovider.special.EmptyParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.Pair;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.shell.runtime.MixerMeterLevels;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;


/**
 * Mode for editing a track parameters.
 *
 * @author Jürgen Moßgraber
 */
public class TrackMode extends AbstractTrackMode
{
    private final PushTrackParameterProvider  mixParameterProvider;
    private final EmptyParameterProvider      inputOutputParameterProvider = new EmptyParameterProvider (8);
    private final ReloadableControllerRuntime reloadableRuntime;
    private boolean                           inputOutputSelected;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     * @param reloadableRuntime The reloadable controller runtime
     */
    public TrackMode (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime reloadableRuntime)
    {
        super ("Track", surface, model);

        this.reloadableRuntime = Objects.requireNonNull (reloadableRuntime, "reloadableRuntime");
        this.mixParameterProvider = new PushTrackParameterProvider (model, surface.getConfiguration ());
        this.setParameterProvider (this.mixParameterProvider);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;

        if (index == 0)
        {
            this.selectInputOutputPage (false);
            return;
        }
        if (index == 1)
        {
            this.selectInputOutputPage (true);
            return;
        }
        if (this.inputOutputSelected)
            return;

        final boolean hasAdditionalSends = this.hasAdditionalTrackSends ();
        final int sendOffset = hasAdditionalSends ? this.configuration.getTrackMixSendOffset () : 0;
        if (!hasAdditionalSends)
            this.configuration.setTrackMixSendOffset (0);
        if (index == 6 && sendOffset > 0)
        {
            this.configuration.setTrackMixSendOffset (0);
            this.bindControls ();
        }
        else if (index == 7 && sendOffset == 0 && hasAdditionalSends)
        {
            this.configuration.setTrackMixSendOffset (4);
            this.bindControls ();
        }
    }


    private void selectInputOutputPage (final boolean isSelected)
    {
        if (this.inputOutputSelected == isSelected)
            return;

        if (this.isActive)
            this.defaultParameterProvider.removeParametersObserver (this);

        this.inputOutputSelected = isSelected;
        this.setParameterProvider (isSelected ? this.inputOutputParameterProvider : this.mixParameterProvider);

        if (this.isActive)
        {
            this.defaultParameterProvider.addParametersObserver (this);
            this.bindControls ();
        }
    }
    private boolean hasAdditionalTrackSends ()
    {
        final ISendBank sendBank = this.model.getCursorTrack ().getSendBank ();
        return sendBank.getPageSize () > 6 && sendBank.getItem (6).doesExist ();
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        super.onKnobTouch (index, isTouched);

        if (isTouched && this.surface.isShiftPressed () && this.surface.isSelectPressed () && this.getParameterProvider ().get (index) instanceof final ISend send)
        {
            this.surface.setTriggerConsumed (ButtonID.SELECT);
            send.toggleEnabled ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final ITrack track = this.model.getCurrentTrackBank ().getItem (index);
            if (!track.doesExist () || !track.isActivated ())
                return this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK);
            if (track.isRecArm ())
                return this.colorManager.getColorIndex (PushColorManager.PUSH_RED_HI);
            return this.colorManager.getColorIndex (track.getColor ());
        }

        index = this.isButtonRow (1, buttonID);
        if (index < 0)
            return super.getButtonColor (buttonID);

        this.updateTrackMenus ();
        final Pair<String, Boolean> menuItem = this.menu.get (index);
        return menuItem.getValue ().booleanValue () || "<".equals (menuItem.getKey ()) || ">".equals (menuItem.getKey ()) ? PushColorManager.PUSH2_COLOR2_WHITE : PushColorManager.PUSH2_COLOR_BLACK;
    }


    private void updateTrackMenus ()
    {
        for (int i = 0; i < 8; i++)
            this.menu.get (i).set (" ", Boolean.FALSE);

        this.menu.get (0).set ("Mix", Boolean.valueOf (!this.inputOutputSelected));
        this.menu.get (1).set ("Input & Output", Boolean.valueOf (this.inputOutputSelected));

        if (this.inputOutputSelected)
            return;

        final boolean hasAdditionalSends = this.hasAdditionalTrackSends ();
        final int sendOffset = hasAdditionalSends ? this.configuration.getTrackMixSendOffset () : 0;
        if (!hasAdditionalSends)
            this.configuration.setTrackMixSendOffset (0);
        if (sendOffset > 0)
            this.menu.get (6).set ("<", Boolean.FALSE);
        else if (hasAdditionalSends)
            this.menu.get (7).set (">", Boolean.FALSE);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final ITrackBank tb = this.model.getCurrentTrackBank ();
        this.updateTrackMenus ();

        final List<MenuData> menus = new ArrayList<> (8);
        final List<ParameterData> parameters = new ArrayList<> (8);
        final List<TrackData> tracks = new ArrayList<> (8);
        final List<MixerControlSnapshot> mixerControls = new ArrayList<> (8);
        final IValueChanger valueChanger = this.model.getValueChanger ();
        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        for (int i = 0; i < 8; i++)
        {
            final Pair<String, Boolean> menuItem = this.menu.get (i);
            menus.add (new MenuData (menuItem.getKey ().trim (), menuItem.getValue ().booleanValue ()));
            parameters.add (new ParameterData ("", -1, -1, "", false));

            final ITrack t = tb.getItem (i);
            tracks.add (new TrackData (t.doesExist () ? t.getName (12) : "", this.updateType (t), t.getColor (), t.isSelected (), t.isActivated (), t.isSelected () && cursorTrack.isPinned ()));
        }

        if (cursorTrack.doesExist ())
        {
            final ITrack track = cursorTrack;
            final boolean isActive = track.isActivated ();
            if (this.inputOutputSelected)
            {
                parameters.set (0, new ParameterData ("Track Type", -1, -1, this.getTrackTypeName (track), isActive));
                parameters.set (1, new ParameterData ("Monitor", -1, -1, this.getMonitorName (track), isActive));
            }
            else
            {
                final RgbColor accent = toRgb (track.getColor ());
                final MixerMeterLevels meterLevels = MixerMeterLevels.capture (track);
                mixerControls.add (new MixerControlSnapshot (0, MixerControlKind.VOLUME, "", valueChanger.toNormalizedValue (track.getVolume ()), normalizedModulated (valueChanger, track.getModulatedVolume ()), track.getVolumeStr (8), MixerControlRole.HOST_COLORED, isActive, false, Optional.of (accent), meterLevels.normalizedLeft (valueChanger), meterLevels.normalizedRight (valueChanger)));
                mixerControls.add (new MixerControlSnapshot (1, MixerControlKind.PAN, "", valueChanger.toNormalizedValue (track.getPan ()), normalizedModulated (valueChanger, track.getModulatedPan ()), this.formatPanValue (track.getPan ()), MixerControlRole.HOST_COLORED, isActive, false, Optional.of (accent), 0, 0));

                final ISendBank sendBank = track.getSendBank ();
                final int sendOffset = this.configuration.getTrackMixSendOffset ();
                for (int i = 0; i < 6; i++)
                {
                    final int sendIndex = sendOffset + i;
                    if (sendIndex >= sendBank.getPageSize ())
                        break;

                    final ISend send = sendBank.getItem (sendIndex);
                    if (send.doesExist ())
                        mixerControls.add (new MixerControlSnapshot (i + 2, MixerControlKind.KNOB, send.getName (), valueChanger.toNormalizedValue (send.getValue ()), normalizedModulated (valueChanger, send.getModulatedValue ()), send.getDisplayedValue (8), MixerControlRole.HOST_COLORED, isActive && send.isEnabled (), false, Optional.of (accent), 0, 0));
                }

            }
        }

        final IComponent stableMixerFrame = new TrackMixerComponent (menus, parameters, tracks, 0, 0);
        final MixerControlsComponent reloadableControls = new MixerControlsComponent (this.reloadableRuntime.renderMixerControls (new MixerControlsSnapshot (mixerControls)));
        display.addElement (info -> {
            // Stable retains only menu/footer mechanics. All eight Mix control cells are blank
            // when no core scene is active; there is no stable semantic fallback.
            stableMixerFrame.draw (info);
            reloadableControls.draw (info);
        });
    }


    private static double normalizedModulated (final IValueChanger valueChanger, final int value)
    {
        return value < 0 ? -1 : valueChanger.toNormalizedValue (value);
    }


    private static RgbColor toRgb (final ColorEx color)
    {
        return new RgbColor ((int) Math.round (255 * color.getRed ()), (int) Math.round (255 * color.getGreen ()), (int) Math.round (255 * color.getBlue ()));
    }


    private String getTrackTypeName (final ITrack track)
    {
        if (track.canHoldNotes () && track.canHoldAudioData ())
            return "Hybrid";
        if (track.canHoldNotes ())
            return "Instrument";
        if (track.canHoldAudioData ())
            return "Audio";
        return track.isGroup () ? "Group" : "Track";
    }


    private String getMonitorName (final ITrack track)
    {
        if (track.isAutoMonitor ())
            return "Auto";
        return track.isMonitor () ? "On" : "Off";
    }
}
