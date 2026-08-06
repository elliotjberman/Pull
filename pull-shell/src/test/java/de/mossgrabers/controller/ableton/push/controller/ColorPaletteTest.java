// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;


class ColorPaletteTest
{
    @Test
    void uploadsBeforeStartingBackgroundVerification ()
    {
        final FakeHost host = new FakeHost ();
        final ColorPalette palette = new ColorPalette (host);

        palette.updatePalette ();

        assertEquals (128, host.countMessages (0x03));
        assertEquals (List.of ("05"), host.commands);
        assertEquals (List.of ("Push colors syncing"), host.notifications);

        host.runScheduled (0);
        assertEquals (1, host.countMessages (0x04));
        assertArrayEquals (new int []
        {
            0x04,
            0
        }, host.messages.get (host.messages.size () - 1));

        for (int index = 0; index < 128; index++)
        {
            if (index > 0)
                host.runScheduled (0);
            palette.handleColorPaletteMessage (responseForUpdate (host.messages.get (index)));
        }
        assertEquals (List.of ("Push colors syncing", "Push colors ready"), host.notifications);
    }


    @Test
    void ignoresDuplicateResponsesBetweenVerificationRequests ()
    {
        final FakeHost host = new FakeHost ();
        final ColorPalette palette = new ColorPalette (host);
        palette.updatePalette ();
        host.runScheduled (0);

        final int [] mismatch = responseForIndex (0);
        mismatch[8] = 1;
        palette.handleColorPaletteMessage (mismatch);

        final int messageCount = host.messages.size ();
        final int commandCount = host.commands.size ();
        final int taskCount = host.tasks.size ();
        palette.handleColorPaletteMessage (mismatch);

        assertEquals (messageCount, host.messages.size ());
        assertEquals (commandCount, host.commands.size ());
        assertEquals (taskCount, host.tasks.size ());

        host.runScheduled (0);
        assertEquals (2, host.countMessages (0x04));
    }


    @Test
    void ignoresALateResponseAfterItsRequestTimesOut ()
    {
        final FakeHost host = new FakeHost ();
        final ColorPalette palette = new ColorPalette (host);
        palette.updatePalette ();
        host.runScheduled (0);
        host.runScheduled (1000);

        final int messageCount = host.messages.size ();
        final int taskCount = host.tasks.size ();
        palette.handleColorPaletteMessage (responseForIndex (0));

        assertEquals (messageCount, host.messages.size ());
        assertEquals (taskCount, host.tasks.size ());

        host.runScheduled (0);
        assertEquals (2, host.countMessages (0x04));
    }


    private static int [] responseForIndex (final int index)
    {
        final int [] response = new int [17];
        response[6] = 0x04;
        response[7] = index;
        return response;
    }


    private static int [] responseForUpdate (final int [] update)
    {
        final int [] response = responseForIndex (update[1]);
        System.arraycopy (update, 2, response, 8, 8);
        return response;
    }


    private static final class FakeHost implements ColorPalette.Host
    {
        private final List<int []>       messages      = new ArrayList<> ();
        private final List<String>       commands      = new ArrayList<> ();
        private final List<ScheduledTask> tasks         = new ArrayList<> ();
        private final List<String>       notifications = new ArrayList<> ();


        @Override
        public void sendSysex (final int [] parameters)
        {
            this.messages.add (parameters.clone ());
        }


        @Override
        public void sendSysex (final String parameters)
        {
            this.commands.add (parameters);
        }


        @Override
        public void scheduleTask (final Runnable task, final long delay)
        {
            this.tasks.add (new ScheduledTask (task, delay));
        }


        @Override
        public void println (final String message)
        {
            // Not relevant to these protocol tests
        }


        @Override
        public void errorln (final String message)
        {
            // Not relevant to these protocol tests
        }


        @Override
        public void notifyPaletteStatus (final String message)
        {
            this.notifications.add (message);
        }


        private long countMessages (final int command)
        {
            return this.messages.stream ().filter (message -> message[0] == command).count ();
        }


        private void runScheduled (final long delay)
        {
            for (int i = 0; i < this.tasks.size (); i++)
            {
                if (this.tasks.get (i).delay () == delay)
                {
                    this.tasks.remove (i).task ().run ();
                    return;
                }
            }
            throw new AssertionError ("No task scheduled with delay " + delay);
        }
    }


    private record ScheduledTask (Runnable task, long delay)
    {
    }
}
