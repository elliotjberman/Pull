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
    interface Host
    {
        void sendSysex (int [] parameters);

        void sendSysex (String parameters);

        void scheduleTask (Runnable task, long delay);

        void println (String message);

        void errorln (String message);
    }


    private static final int          MAX_VERIFICATION_ATTEMPTS = 3;
    private static final int          VERIFICATION_TIMEOUT_MS   = 1000;

    private final Host                 host;
    private final ColorPaletteEntry [] entries                  = new ColorPaletteEntry [128];
    private final Object               updateLock               = new Object ();

    private boolean                    uploadStarted;
    private boolean                    verificationRequestOutstanding;
    private int                        verificationIndex;
    private int                        verificationAttempt;
    private int                        correctedEntryCount;
    private int                        failedEntryCount;
    private long                       verificationToken;
    private long                       updateStartedAt;


    /**
     * Constructor.
     *
     * @param surface The surface
     */
    public ColorPalette (final PushControlSurface surface)
    {
        this ((Host) surface);
    }


    ColorPalette (final Host host)
    {
        this.host = host;

        for (int i = 0; i < this.entries.length; i++)
            this.entries[i] = new ColorPaletteEntry (i, PushColorManager.getPaletteColorRGB (i), PushPaletteData.WHITE_VALUES[i]);
    }


    /**
     * Upload the complete known RGB plus white-only palette, then verify it in the background. Push
     * palette Set messages have no reply, so reading every slot before writing only delays the
     * first correct render. Ableton Live likewise sends its complete table followed by Reapply.
     */
    public void updatePalette ()
    {
        final long uploadStartedAt;
        synchronized (this.updateLock)
        {
            if (this.uploadStarted)
                return;

            this.uploadStarted = true;
            this.updateStartedAt = System.nanoTime ();
            uploadStartedAt = this.updateStartedAt;

            for (final ColorPaletteEntry entry: this.entries)
                this.host.sendSysex (entry.createUpdateMessage ());
            this.host.sendSysex ("05");
        }

        final long uploadMilliseconds = elapsedMilliseconds (uploadStartedAt);
        this.host.println ("Push RGBW color palette queued in " + uploadMilliseconds + " ms; verifying in background.");
        this.host.scheduleTask (this::requestVerification, 0);
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

        boolean verificationComplete = false;
        synchronized (this.updateLock)
        {
            if (!this.uploadStarted || !this.verificationRequestOutstanding || this.verificationIndex >= this.entries.length || data[7] != this.verificationIndex)
                return;

            this.verificationRequestOutstanding = false;
            this.verificationToken++;
            final ColorPaletteEntry entry = this.entries[this.verificationIndex];
            if (entry.matches (data))
                this.advanceVerification ();
            else if (this.verificationAttempt < MAX_VERIFICATION_ATTEMPTS)
            {
                this.correctedEntryCount++;
                this.host.sendSysex (entry.createUpdateMessage ());
                this.host.sendSysex ("05");
            }
            else
            {
                this.failCurrentEntry ("writing");
                this.advanceVerification ();
            }

            verificationComplete = this.verificationIndex >= this.entries.length;
        }

        if (verificationComplete)
            this.finishVerification ();
        else
            this.host.scheduleTask (this::requestVerification, 0);
    }


    private void requestVerification ()
    {
        final int entryIndex;
        final long token;
        synchronized (this.updateLock)
        {
            if (!this.uploadStarted || this.verificationRequestOutstanding || this.verificationIndex >= this.entries.length)
                return;

            entryIndex = this.verificationIndex;
            this.verificationAttempt++;
            this.verificationRequestOutstanding = true;
            token = ++this.verificationToken;
            this.host.sendSysex (new int []
            {
                0x04,
                entryIndex
            });
        }

        this.host.scheduleTask (() -> this.handleVerificationTimeout (entryIndex, token), VERIFICATION_TIMEOUT_MS);
    }


    private void handleVerificationTimeout (final int entryIndex, final long token)
    {
        boolean verificationComplete = false;
        synchronized (this.updateLock)
        {
            if (!this.verificationRequestOutstanding || entryIndex != this.verificationIndex || token != this.verificationToken)
                return;

            this.verificationRequestOutstanding = false;
            this.verificationToken++;
            if (this.verificationAttempt >= MAX_VERIFICATION_ATTEMPTS)
            {
                this.failCurrentEntry ("reading");
                this.advanceVerification ();
                verificationComplete = this.verificationIndex >= this.entries.length;
            }
        }

        if (verificationComplete)
            this.finishVerification ();
        else
            this.host.scheduleTask (this::requestVerification, 0);
    }


    private void advanceVerification ()
    {
        this.verificationIndex++;
        this.verificationAttempt = 0;
        this.verificationRequestOutstanding = false;
    }


    private void failCurrentEntry (final String operation)
    {
        this.failedEntryCount++;
        this.host.errorln ("Failed " + operation + " color palette entry #" + this.verificationIndex + ".");
    }


    private void finishVerification ()
    {
        final int failed;
        final int corrected;
        final long startedAt;
        synchronized (this.updateLock)
        {
            failed = this.failedEntryCount;
            corrected = this.correctedEntryCount;
            startedAt = this.updateStartedAt;
        }

        final String result = failed == 0 ? "verified" : "finished with " + failed + " failed entries";
        this.host.println ("Push RGBW color palette " + result + " in " + elapsedMilliseconds (startedAt) + " ms (" + corrected + " corrective writes).");
    }


    private static long elapsedMilliseconds (final long startedAt)
    {
        return (System.nanoTime () - startedAt) / 1_000_000L;
    }
}
