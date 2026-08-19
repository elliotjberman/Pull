// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntPredicate;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.graphics.canvas.component.MixerControlsComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;


/**
 * Stable display and encoder-touch adapter for fixed workspace facets.
 */
public final class WorkspaceMode extends BaseMode<IParameter> implements WorkspaceFacetAdapter
{
    private final ReloadableControllerRuntime reloadableRuntime;


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     * @param reloadableRuntime The reloadable controller runtime
     */
    public WorkspaceMode (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime reloadableRuntime)
    {
        super ("Workspace", surface, model, model.getProject ().getParameterBank ());

        this.reloadableRuntime = Objects.requireNonNull (reloadableRuntime, "reloadableRuntime");
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        this.reconcileWorkspaceFacets ();
        super.onActivate ();
    }


    /** {@inheritDoc} */
    @Override
    public void reconcileWorkspaceFacets ()
    {
        // Touch read-back remains a mechanical stable fact. Rendering and relative mutation are
        // core-owned, so this mode deliberately never binds its project bank to the encoders.
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        if (!this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS))
            return;

        this.setTouchedKnob (index, isTouched);
        final IParameter parameter = this.bank.getItem (index);
        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            parameter.resetValue ();
        }
        parameter.touchValue (isTouched);
        this.checkStopAutomationOnKnobRelease (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (this.hasFacet (ControllerViewFacet.TRACK_SELECTION_STRIP) && event == ButtonEvent.UP)
            this.model.getCurrentTrackBank ().getItem (index).select ();
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0 && this.hasFacet (ControllerViewFacet.TRACK_SELECTION_STRIP))
        {
            final ITrack track = this.model.getCurrentTrackBank ().getItem (index);
            if (!track.doesExist () || !track.isActivated ())
                return PushColorManager.PUSH2_COLOR_BLACK;
            if (track.isRecArm ())
                return PushColorManager.PUSH2_COLOR_RED_HI;
            return this.colorManager.getColorIndex (DAWColor.getColorID (track.getColor ()));
        }

        index = this.isButtonRow (1, buttonID);
        if (index == 0 && this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS))
            return PushColorManager.PUSH2_COLOR2_WHITE;
        return PushColorManager.PUSH2_COLOR_BLACK;
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final boolean showParameters = this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS);
        final boolean showTracks = this.hasFacet (ControllerViewFacet.TRACK_SELECTION_STRIP);
        final IValueChanger valueChanger = this.model.getValueChanger ();
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        final List<MenuData> menus = new ArrayList<> (8);
        final List<TrackData> tracks = new ArrayList<> (8);

        for (int index = 0; index < this.bank.getPageSize (); index++)
        {
            final ITrack track = trackBank.getItem (index);
            final boolean trackExists = showTracks && track.doesExist ();
            menus.add (new MenuData (showParameters && index == 0 ? "Project" : "", showParameters && index == 0));
            tracks.add (new TrackData (trackExists ? track.getName (16) : "", track.getType (), track.getColor (), trackExists && track.isSelected (), trackExists && track.isActivated (), false));
        }

        final TrackMixerComponent stableFrame = new TrackMixerComponent (menus, List.of (), tracks, 0, 0);
        final MixerControlsSnapshot projectControls = projectControls (this.bank, valueChanger, showParameters, this::isKnobTouched);
        final MixerControlsComponent reloadableControls = new MixerControlsComponent (this.reloadableRuntime.renderMixerControls (projectControls));
        display.addElement (info -> {
            // Stable retains only the inherited Project menu and track footer. Parameter copy,
            // typography, geometry, units, colors, and shapes come from the reloadable core.
            stableFrame.draw (info);
            reloadableControls.draw (info);
        });
    }


    static MixerControlsSnapshot projectControls (final IBank<IParameter> bank, final IValueChanger valueChanger, final boolean enabled, final IntPredicate touched)
    {
        if (!enabled)
            return MixerControlsSnapshot.empty ();

        final List<MixerControlSnapshot> controls = new ArrayList<> (8);
        for (int index = 0; index < bank.getPageSize (); index++)
        {
            final IParameter parameter = bank.getItem (index);
            if (!parameter.doesExist ())
                continue;
            final int modulatedValue = parameter.getModulatedValue ();
            controls.add (new MixerControlSnapshot (
                index,
                MixerControlKind.KNOB,
                parameter.getName (),
                valueChanger.toNormalizedValue (parameter.getValue ()),
                modulatedValue < 0 ? -1 : valueChanger.toNormalizedValue (modulatedValue),
                parameter.getDisplayedValue (),
                MixerControlRole.PROJECT_MACRO,
                true,
                touched.test (index),
                Optional.empty (),
                0,
                0));
        }
        return new MixerControlsSnapshot (controls);
    }


    private boolean hasFacet (final ControllerViewFacet facet)
    {
        return this.surface.getControllerWorkspaceHost ().hasFacet (facet);
    }
}
