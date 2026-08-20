// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.SessionBankShape;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Stable bounded canopy of eagerly installed Session banks.
 */
public final class SessionBankRegistry
{
    private final IModel model;
    private final Map<SessionBankShape, ITrackBank> banks;
    private final SessionBankShape defaultShape;

    private ITrackBank activeBank;
    private SessionBankShape activeShape;


    /**
     * Resolve all declared banks from the initialized model.
     *
     * @param model DAW model
     * @param declaredShapes Complete bounded set of installed shapes
     * @param defaultShape Shape used outside a core workspace
     */
    public SessionBankRegistry (final IModel model, final Set<SessionBankShape> declaredShapes, final SessionBankShape defaultShape)
    {
        this.model = Objects.requireNonNull (model, "model");
        this.defaultShape = Objects.requireNonNull (defaultShape, "defaultShape");
        if (!defaultShape.isPresent ())
            throw new IllegalArgumentException ("Default Session bank shape must be present");

        final Map<SessionBankShape, ITrackBank> resolved = new LinkedHashMap<> ();
        for (final SessionBankShape shape: Set.copyOf (Objects.requireNonNull (declaredShapes, "declaredShapes")))
        {
            if (!shape.isPresent ())
                throw new IllegalArgumentException ("Declared Session bank shapes must be present");
            resolved.put (shape, model.getTrackBank (shape.tracks (), shape.scenes ()));
        }
        this.banks = Map.copyOf (resolved);
        this.activeBank = this.requireBank (defaultShape);
        this.activeShape = defaultShape;
        this.model.setCurrentMainTrackBank (this.activeBank);
        this.activeBank.setIndication (true);
    }


    /**
     * Activate a declared shape, preserving the visible track and scene offsets.
     *
     * @param shape Shape requested by the active view
     */
    public void activate (final SessionBankShape shape)
    {
        final ITrackBank nextBank = this.requireBank (shape);
        if (nextBank == this.activeBank)
        {
            this.activeShape = shape;
            this.model.setCurrentMainTrackBank (nextBank);
            return;
        }

        final int trackPosition = this.activeBank.getScrollPosition ();
        if (trackPosition >= 0)
            nextBank.scrollTo (trackPosition, false);
        final ISceneBank activeScenes = this.activeBank.getSceneBank ();
        final int scenePosition = activeScenes.getScrollPosition ();
        if (scenePosition >= 0)
            nextBank.getSceneBank ().scrollTo (scenePosition, false);

        this.activeBank.setIndication (false);
        nextBank.setIndication (true);
        this.activeBank = nextBank;
        this.activeShape = shape;
        this.model.setCurrentMainTrackBank (nextBank);
    }


    /**
     * Restore the default Session bank.
     */
    public void restoreDefault ()
    {
        this.activate (this.defaultShape);
    }


    /**
     * Require that a shape is part of the installed canopy.
     *
     * @param shape Requested shape
     */
    public void requireDeclared (final SessionBankShape shape)
    {
        this.requireBank (shape);
    }


    /**
     * Get every eagerly installed Session bank.
     *
     * @return Installed banks
     */
    public Collection<ITrackBank> getBanks ()
    {
        return this.banks.values ();
    }


    /** Get the currently selected bounded Session bank. */
    public ITrackBank getActiveBank ()
    {
        return this.activeBank;
    }


    /** Get the shape identifying the active bounded Session bank. */
    public SessionBankShape getActiveShape ()
    {
        return this.activeShape;
    }


    private ITrackBank requireBank (final SessionBankShape shape)
    {
        final SessionBankShape requestedShape = Objects.requireNonNull (shape, "shape");
        final ITrackBank bank = this.banks.get (requestedShape);
        if (bank == null)
            throw new IllegalArgumentException ("Session bank " + requestedShape.tracks () + "x" + requestedShape.scenes () + " is outside the installed canopy");
        return bank;
    }
}
