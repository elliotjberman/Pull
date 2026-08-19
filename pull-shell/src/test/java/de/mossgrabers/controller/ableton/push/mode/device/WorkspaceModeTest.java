// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;


/** Tests for the mechanical Project Macro display adapter. */
class WorkspaceModeTest
{
    @Test
    void passesUntruncatedProjectMacroStressTextToTheCoreRenderer ()
    {
        final IParameter present = parameter (true, "Very Long Project Macro Name", "+123.456 dB", 512, -1);
        final IParameter absent = parameter (false, "", "", 0, -1);
        final IParameterBank bank = proxy (IParameterBank.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getPageSize" -> Integer.valueOf (8);
            case "getItem" -> ((Integer) arguments[0]).intValue () == 0 ? present : absent;
            default -> relaxedValue (method.getReturnType ());
        });
        final IValueChanger valueChanger = proxy (IValueChanger.class, (proxy, method, arguments) -> "toNormalizedValue".equals (method.getName ()) ? Double.valueOf (((Number) arguments[0]).doubleValue () / 1023.0) : relaxedValue (method.getReturnType ()));

        final MixerControlsSnapshot snapshot = WorkspaceMode.projectControls (bank, valueChanger, true, index -> index == 0);

        assertEquals (1, snapshot.controls ().size ());
        final MixerControlSnapshot control = snapshot.controls ().get (0);
        assertEquals ("Very Long Project Macro Name", control.label ());
        assertEquals ("+123.456 dB", control.displayedValue ());
        assertEquals (-1, control.modulatedValue ());
        assertEquals (MixerControlRole.PROJECT_MACRO, control.role ());
        assertTrue (control.enabled ());
        assertTrue (control.touched ());
        assertTrue (control.hostAccentColor ().isEmpty ());
    }


    @Test
    void leavesTheProjectMacroBodyBlankWhenTheFacetIsAbsent ()
    {
        assertEquals (MixerControlsSnapshot.empty (), WorkspaceMode.projectControls (null, null, false, null));
    }


    private static IParameter parameter (final boolean exists, final String name, final String displayedValue, final int value, final int modulatedValue)
    {
        return proxy (IParameter.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.valueOf (exists);
            case "getName" -> name;
            case "getDisplayedValue" -> displayedValue;
            case "getValue" -> Integer.valueOf (value);
            case "getModulatedValue" -> Integer.valueOf (modulatedValue);
            default -> relaxedValue (method.getReturnType ());
        });
    }


    @SuppressWarnings ("unchecked")
    private static <T> T proxy (final Class<T> type, final InvocationHandler invocation)
    {
        return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, invocation);
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == double.class)
            return Double.valueOf (0);
        if (type == int.class)
            return Integer.valueOf (0);
        return null;
    }
}
