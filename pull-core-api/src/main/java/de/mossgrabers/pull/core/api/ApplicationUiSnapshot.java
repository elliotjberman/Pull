// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Requested read-back from the installed Application, Arranger, and Mixer proxies. */
public record ApplicationUiSnapshot (long generation, String projectId, String panelLayout, ArrangerUiSnapshot arranger, MixerUiSnapshot mixer)
{
    public ApplicationUiSnapshot
    {
        projectId = Objects.requireNonNull (projectId, "projectId");
        panelLayout = Objects.requireNonNull (panelLayout, "panelLayout");
        arranger = Objects.requireNonNull (arranger, "arranger");
        mixer = Objects.requireNonNull (mixer, "mixer");
        if (generation < 0 || panelLayout.length () > 128 || generation > 0 && projectId.isBlank ())
            throw new IllegalArgumentException ("invalid application UI observation");
        if (generation == 0 && (!projectId.isEmpty () || !panelLayout.isEmpty () || !arranger.equals (ArrangerUiSnapshot.empty ()) || !mixer.equals (MixerUiSnapshot.empty ())))
            throw new IllegalArgumentException ("unavailable application UI cannot contain observed state");
    }

    public boolean available () { return this.generation > 0; }

    public ApplicationUiContext context ()
    {
        return new ApplicationUiContext (this.generation, this.projectId, this.panelLayout);
    }

    public static ApplicationUiSnapshot empty ()
    {
        return new ApplicationUiSnapshot (0, "", "", ArrangerUiSnapshot.empty (), MixerUiSnapshot.empty ());
    }
}
