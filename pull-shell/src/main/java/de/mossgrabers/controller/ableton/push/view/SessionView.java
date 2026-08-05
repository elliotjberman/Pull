// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.command.trigger.SelectSessionViewCommand;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.grid.LightInfo;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.Pair;
import de.mossgrabers.framework.view.AbstractSessionView;
import de.mossgrabers.framework.view.TransposeView;
import de.mossgrabers.pull.core.api.SessionBankShape;


/**
 * The Session view.
 *
 * @author Jürgen Moßgraber
 */
public class SessionView extends AbstractSessionView<PushControlSurface, PushConfiguration> implements TransposeView
{
    /** Full Push Session grid bank. */
    public static final SessionBankShape SESSION_BANK_SHAPE = new SessionBankShape (8, 8);

    private final int yOffset;


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     */
    public SessionView (final PushControlSurface surface, final IModel model)
    {
        this ("Session", surface, model, 8, 0);
    }


    /**
     * Constructor for a fixed Session-grid region.
     *
     * @param name The view name
     * @param surface The surface
     * @param model The model
     * @param rows Number of Session rows
     * @param yOffset Display-grid row offset
     */
    protected SessionView (final String name, final PushControlSurface surface, final IModel model, final int rows, final int yOffset)
    {
        super (name, surface, model, rows, 8, true);
        this.yOffset = yOffset;

        final int redLo = PushColorManager.PUSH2_COLOR2_RED_LO;
        final int rose = PushColorManager.PUSH2_COLOR2_ROSE;
        final int black = PushColorManager.PUSH2_COLOR2_BLACK;
        final int white = PushColorManager.PUSH2_COLOR2_WHITE;
        final int green = PushColorManager.PUSH2_COLOR2_GREEN;
        final int amber = PushColorManager.PUSH2_COLOR2_AMBER;
        final int grey = PushColorManager.PUSH2_COLOR2_GREY_LO;
        final LightInfo isRecording = new LightInfo (rose, rose, false);
        final LightInfo isRecordingQueued = new LightInfo (rose, black, true);
        final LightInfo isPlaying = new LightInfo (green, green, false);
        final LightInfo isPlayingQueued = new LightInfo (green, green, true);
        final LightInfo isStopQueued = new LightInfo (green, green, true);
        final LightInfo hasContent = new LightInfo (amber, white, false);
        final LightInfo noContent = new LightInfo (black, -1, false);
        final LightInfo recArmed = new LightInfo (redLo, -1, false);
        final LightInfo isMuted = new LightInfo (grey, -1, false);
        this.setColors (isRecording, isRecordingQueued, isPlaying, isPlayingQueued, isStopQueued, hasContent, noContent, recArmed, isMuted);

        this.birdColorHasContent = new LightInfo (amber, -1, false);
        this.birdColorSelected = isPlaying;
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        final SessionBankShape shape = this.getSessionBankShape ();
        if (shape.isPresent ())
            this.surface.getSessionBankRegistry ().activate (shape);
        super.onActivate ();
    }


    /**
     * Get the Session bank shape declared by this view.
     *
     * @return Session bank shape
     */
    protected SessionBankShape getSessionBankShape ()
    {
        return SESSION_BANK_SHAPE;
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNote (final int note, final int velocity)
    {
        if (velocity == 0)
            ((SelectSessionViewCommand) this.surface.getButton (ButtonID.SESSION).getCommand ()).setTemporary ();

        // Birds-eye-view navigation
        if (this.isBirdsEyeActive ())
        {
            if (velocity == 0)
                return;

            final Pair<Integer, Integer> pad = this.getPad (note);
            if (pad != null)
                this.onGridNoteBirdsEyeView (pad.getKey ().intValue (), pad.getValue ().intValue (), this.yOffset);
            return;
        }

        super.onGridNote (note, velocity);
    }


    /** {@inheritDoc} */
    @Override
    protected int getYOffset ()
    {
        return this.yOffset;
    }


    /** {@inheritDoc} */
    @Override
    protected boolean handleButtonCombinations (final ITrack track, final ISlot slot)
    {
        if (this.isButtonCombination (ButtonID.SELECT))
        {
            if (slot.doesExist ())
                slot.select ();
            return true;
        }

        return super.handleButtonCombinations (track, slot);
    }


    /** {@inheritDoc} */
    @Override
    public boolean isBirdsEyeActive ()
    {
        return this.surface.isShiftPressed () && this.surface.isSelectPressed ();
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        final int scene = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        if (scene < 0 || scene >= 8)
            return AbstractFeatureGroup.BUTTON_COLOR_OFF;

        final ISceneBank sceneBank = this.model.getSceneBank ();
        final IScene s = sceneBank.getItem (scene);
        if (s.doesExist ())
            return s.isSelected () ? AbstractSessionView.COLOR_SELECTED_SCENE : AbstractSessionView.COLOR_SCENE;
        return AbstractSessionView.COLOR_SCENE_OFF;
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveDown (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            this.model.getCurrentTrackBank ().getSceneBank ().selectNextPage ();
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveUp (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            this.model.getCurrentTrackBank ().getSceneBank ().selectPreviousPage ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveUpButtonOn ()
    {
        return this.model.getCurrentTrackBank ().getSceneBank ().canScrollPageForwards ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveDownButtonOn ()
    {
        return this.model.getCurrentTrackBank ().getSceneBank ().canScrollPageBackwards ();
    }


    /** {@inheritDoc} */
    @Override
    public void resetOctave ()
    {
        // Currently, not used
    }


    /** {@inheritDoc} */
    @Override
    public void onButton (final ButtonID buttonID, final ButtonEvent event, final int velocity)
    {
        super.onButton (buttonID, event, velocity);

        if (ButtonID.isSceneButton (buttonID) && event == ButtonEvent.UP)
            ((SelectSessionViewCommand) this.surface.getButton (ButtonID.SESSION).getCommand ()).setTemporary ();
    }
}
