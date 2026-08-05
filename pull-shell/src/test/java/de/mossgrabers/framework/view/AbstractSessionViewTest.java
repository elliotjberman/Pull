// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.view;

import de.mossgrabers.framework.utils.Pair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class AbstractSessionViewTest
{
    @Test
    void mapsTheFullPushGridWithoutChangingLegacyCoordinates ()
    {
        assertPad (AbstractSessionView.mapPad (36, 36, 8, 8, 8, 0), 0, 7);
        assertPad (AbstractSessionView.mapPad (99, 36, 8, 8, 8, 0), 7, 0);
    }


    @Test
    void mapsOnlyTheUpperFourRowsIntoFourSessionScenes ()
    {
        assertNull (AbstractSessionView.mapPad (36, 36, 8, 8, 4, 0));
        assertNull (AbstractSessionView.mapPad (67, 36, 8, 8, 4, 0));
        assertPad (AbstractSessionView.mapPad (68, 36, 8, 8, 4, 0), 0, 3);
        assertPad (AbstractSessionView.mapPad (99, 36, 8, 8, 4, 0), 7, 0);
        assertNull (AbstractSessionView.mapPad (100, 36, 8, 8, 4, 0));
    }


    private static void assertPad (final Pair<Integer, Integer> pad, final int column, final int row)
    {
        assertEquals (column, pad.getKey ().intValue ());
        assertEquals (row, pad.getValue ().intValue ());
    }
}
