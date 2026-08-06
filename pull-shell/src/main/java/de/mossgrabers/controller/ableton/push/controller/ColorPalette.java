// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

/**
 * The color palette of the Push 2/3.
 *
 * @author Jürgen Moßgraber
 */
public class ColorPalette
{
    private final PushControlSurface   surface;
    private final ColorPaletteEntry [] entries          = new ColorPaletteEntry [128];
    private final Object               updateLock       = new Object ();
    private boolean                    entriesHasUpdate = false;
    private int                        failedEntryCount;


    /**
     * Constructor.
     *
     * @param surface The surface
     */
    public ColorPalette (final PushControlSurface surface)
    {
        this.surface = surface;

        for (int i = 0; i < this.entries.length; i++)
            this.entries[i] = new ColorPaletteEntry (i, PushColorManager.getPaletteColorRGB (i));
    }


    /**
     * Checks all entries in the current pad color palette of the Push 2/3. Sends updates if
     * necessary.
     */
    public void updatePalette ()
    {
        synchronized (this.updateLock)
        {
            final int entryIndex = this.findNextEntry ();
            // All done?
            if (entryIndex < 0)
            {
                this.surface.scheduleTask (this::finishUpdate, this.entriesHasUpdate ? 1000 : 0);
                return;
            }

            switch (this.entries[entryIndex].getState ())
            {
                case READ:
                    this.sendColorEntryRequest (entryIndex);
                    break;

                case READ_REQUESTED:
                    // 2nd attempt after 1 second...
                    if (System.currentTimeMillis () - this.entries[entryIndex].getSendTimestamp () > 1000L)
                        this.sendColorEntryRequest (entryIndex);
                    break;

                case WRITE:
                    if (this.entries[entryIndex].incWriteRetries ())
                        this.surface.sendSysex (this.entries[entryIndex].createUpdateMessage ());
                    else
                    {
                        this.failedEntryCount++;
                        this.surface.errorln ("Failed writing color palette entry #" + entryIndex + ".");
                    }
                    break;

                default:
                    return;
            }
        }

        this.surface.scheduleTask (this::updatePalette, 10);
    }


    private void finishUpdate ()
    {
        final boolean hasUpdates;
        final int failed;
        synchronized (this.updateLock)
        {
            hasUpdates = this.entriesHasUpdate;
            failed = this.failedEntryCount;
        }

        if (hasUpdates)
            this.surface.sendSysex ("05");

        final String status = failed == 0 ? "ready" : "finished with " + failed + " failed entries";
        this.surface.println ("Push color palette is " + status + ".");
        this.surface.getDisplay ().notify (failed == 0 ? "Push colors ready" : "Push color sync incomplete");
    }


    /**
     * Handle a color palette system exclusive message.
     *
     * @param data The message data
     */
    public void handleColorPaletteMessage (final int [] data)
    {
        if (!ColorPaletteEntry.isValid (data))
            return;

        synchronized (this.updateLock)
        {
            final int index = data[7];
            final ColorPaletteEntry entry = this.entries[index];

            // Retried requests can produce a stale response after this entry has already completed
            // or exhausted its retries. Never let such a response reopen a terminal entry.
            if (entry.getState () == ColorPaletteEntry.State.DONE)
                return;

            // Is an update of the color palette entry necessary?
            if (!entry.requiresUpdate (data))
            {
                entry.setDone ();
                return;
            }

            this.entriesHasUpdate = true;
            entry.setWrite ();
        }
    }


    /**
     * Get the next palette entry which needs to be updated.
     *
     * @return The index of the entry or -1 if no further updates are required
     */
    private int findNextEntry ()
    {
        for (int i = 0; i < this.entries.length; i++)
        {
            if (this.entries[i].getState () != ColorPaletteEntry.State.DONE)
                return i;
        }

        return -1;
    }


    /**
     * Send a request to the Push 2/3 to send the values of an entry of the current color palette.
     *
     * @param entryIndex The index of the entry 0-127
     */
    private void sendColorEntryRequest (final int entryIndex)
    {
        if (!this.entries[entryIndex].incReadRetries ())
        {
            this.failedEntryCount++;
            this.surface.errorln ("Failed reading color palette entry #" + entryIndex + ".");
            return;
        }

        this.surface.sendSysex (new int []
        {
            0x04,
            entryIndex
        });
    }
}
