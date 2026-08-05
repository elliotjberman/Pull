// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.SessionBankShape;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SessionBankRegistryTest
{
    @Test
    void switchesDeclaredBanksAndPreservesViewportOffsets ()
    {
        final SessionBankShape fullShape = new SessionBankShape (8, 8);
        final SessionBankShape halfShape = new SessionBankShape (8, 4);
        final BankProbe full = new BankProbe (8, 8);
        final BankProbe half = new BankProbe (8, 4);
        final ModelProbe model = new ModelProbe (Map.of (fullShape, full.bank, halfShape, half.bank));
        final SessionBankRegistry registry = new SessionBankRegistry (model.model, Set.of (fullShape, halfShape), fullShape);

        assertTrue (full.indicated);
        assertSame (full.bank, model.currentMainBanks.getLast ());

        full.trackPosition = 16;
        full.scenePosition = 24;
        registry.activate (halfShape);

        assertFalse (full.indicated);
        assertTrue (half.indicated);
        assertEquals (16, half.trackPosition);
        assertEquals (24, half.scenePosition);
        assertSame (half.bank, model.currentMainBanks.getLast ());

        half.trackPosition = 32;
        half.scenePosition = 12;
        registry.restoreDefault ();

        assertTrue (full.indicated);
        assertFalse (half.indicated);
        assertEquals (32, full.trackPosition);
        assertEquals (12, full.scenePosition);
    }


    @Test
    void rejectsShapesOutsideTheInstalledCanopy ()
    {
        final SessionBankShape fullShape = new SessionBankShape (8, 8);
        final BankProbe full = new BankProbe (8, 8);
        final ModelProbe model = new ModelProbe (Map.of (fullShape, full.bank));
        final SessionBankRegistry registry = new SessionBankRegistry (model.model, Set.of (fullShape), fullShape);

        assertThrows (IllegalArgumentException.class, () -> registry.activate (new SessionBankShape (8, 4)));
    }


    private static final class ModelProbe
    {
        private final List<ITrackBank> currentMainBanks = new ArrayList<> ();
        private final IModel model;


        private ModelProbe (final Map<SessionBankShape, ITrackBank> banks)
        {
            this.model = (IModel) Proxy.newProxyInstance (IModel.class.getClassLoader (), new Class<?> []
            {
                IModel.class
            }, (proxy, method, args) -> {
                if (method.getName ().equals ("getTrackBank") && args != null && args.length == 2)
                    return banks.get (new SessionBankShape (((Integer) args[0]).intValue (), ((Integer) args[1]).intValue ()));
                if (method.getName ().equals ("setCurrentMainTrackBank"))
                {
                    this.currentMainBanks.add ((ITrackBank) args[0]);
                    return null;
                }
                return defaultValue (method.getReturnType ());
            });
        }
    }


    private static final class BankProbe
    {
        private final ITrackBank bank;
        private final ISceneBank scenes;
        private int trackPosition;
        private int scenePosition;
        private boolean indicated;


        private BankProbe (final int tracks, final int sceneCount)
        {
            this.scenes = (ISceneBank) Proxy.newProxyInstance (ISceneBank.class.getClassLoader (), new Class<?> []
            {
                ISceneBank.class
            }, (proxy, method, args) -> {
                if (method.getName ().equals ("getPageSize"))
                    return Integer.valueOf (sceneCount);
                if (method.getName ().equals ("getScrollPosition"))
                    return Integer.valueOf (this.scenePosition);
                if (method.getName ().equals ("scrollTo"))
                {
                    this.scenePosition = ((Integer) args[0]).intValue ();
                    return null;
                }
                return defaultValue (method.getReturnType ());
            });
            this.bank = (ITrackBank) Proxy.newProxyInstance (ITrackBank.class.getClassLoader (), new Class<?> []
            {
                ITrackBank.class
            }, (proxy, method, args) -> {
                switch (method.getName ())
                {
                    case "getPageSize":
                        return Integer.valueOf (tracks);
                    case "getScrollPosition":
                        return Integer.valueOf (this.trackPosition);
                    case "getSceneBank":
                        return this.scenes;
                    case "scrollTo":
                        this.trackPosition = ((Integer) args[0]).intValue ();
                        return null;
                    case "setIndication":
                        this.indicated = ((Boolean) args[0]).booleanValue ();
                        return null;
                    default:
                        return defaultValue (method.getReturnType ());
                }
            });
        }
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive ())
            return null;
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == int.class)
            return Integer.valueOf (0);
        if (type == long.class)
            return Long.valueOf (0);
        return null;
    }
}
