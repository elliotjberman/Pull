// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.List;
import java.util.Objects;


/**
 * Optional launcher observations for at most eight tracks and eight scenes. Slots are track-major.
 * The empty value incurs no clip/scene sampling. Alignment checks proxy locations only; it does
 * not promise clip identity or completion of a submitted launch.
 */
public record SessionClipWindowSnapshot (SessionBankShape shape, boolean aligned, List<SessionSlotSnapshot> slots, List<SessionSceneSnapshot> scenes, BankNavigationSnapshot trackNavigation, BankNavigationSnapshot sceneNavigation)
{
    public SessionClipWindowSnapshot
    {
        shape = Objects.requireNonNull (shape, "shape");
        slots = List.copyOf (Objects.requireNonNull (slots, "slots"));
        scenes = List.copyOf (Objects.requireNonNull (scenes, "scenes"));
        trackNavigation = Objects.requireNonNull (trackNavigation, "trackNavigation");
        sceneNavigation = Objects.requireNonNull (sceneNavigation, "sceneNavigation");
        if (shape.tracks () > 8 || shape.scenes () > 8 || slots.size () != shape.tracks () * shape.scenes () || scenes.size () != shape.scenes ())
            throw new IllegalArgumentException ("Session clip observations must match a bounded shape of at most 8x8");
        if (!shape.isPresent () && (aligned || !trackNavigation.equals (BankNavigationSnapshot.empty ()) || !sceneNavigation.equals (BankNavigationSnapshot.empty ())))
            throw new IllegalArgumentException ("An unsubscribed Session clip window cannot contain state");
    }


    public SessionSlotSnapshot slot (final int trackIndex, final int sceneIndex)
    {
        Objects.checkIndex (trackIndex, this.shape.tracks ());
        Objects.checkIndex (sceneIndex, this.shape.scenes ());
        return this.slots.get (trackIndex * this.shape.scenes () + sceneIndex);
    }


    public static SessionClipWindowSnapshot empty ()
    {
        return new SessionClipWindowSnapshot (SessionBankShape.empty (), false, List.of (), List.of (), BankNavigationSnapshot.empty (), BankNavigationSnapshot.empty ());
    }
}
