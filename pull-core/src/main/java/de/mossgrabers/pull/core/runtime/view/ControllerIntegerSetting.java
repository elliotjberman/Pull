// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect;
import java.util.List;

/** One bounded preference write, followed by its latest intent after authoritative acknowledgement. */
final class ControllerIntegerSetting
{
    private final SetControllerIntegerSettingEffect.Setting setting;
    private final PageNavigation navigation;
    private PageNavigation.Origin origin;
    private Integer requested;
    private int desired;
    private int before;
    private long submittedAt;
    private long submittedRevision;

    ControllerIntegerSetting (final SetControllerIntegerSettingEffect.Setting setting, final PageNavigation navigation) { this.setting = setting; this.navigation = navigation; }
    boolean pending () { return this.requested != null && this.navigation.matches (this.origin); }
    int intended (final int observed) { return this.pending () ? this.desired : observed; }
    void clear () { this.requested = null; }

    List<CoreEffect> observe (final ControllerSnapshot snapshot, final int observed)
    {
        if (!this.pending ()) { this.clear (); return List.of (); }
        if (snapshot.revision () > this.submittedRevision && observed == this.requested.intValue ())
        {
            this.clear ();
            return this.desired == observed ? List.of () : List.of (this.submit (snapshot, observed));
        }
        if (observed != this.before || snapshot.monotonicTimeNanos () - this.submittedAt >= 5_000_000_000L) this.clear ();
        return List.of ();
    }

    List<CoreEffect> request (final ControllerSnapshot snapshot, final int observed, final int desired)
    {
        this.desired = desired;
        return !this.pending () && desired != observed ? List.of (this.submit (snapshot, observed)) : List.of ();
    }

    private CoreEffect submit (final ControllerSnapshot snapshot, final int observed)
    {
        this.origin = this.navigation.origin ();
        this.requested = Integer.valueOf (this.desired);
        this.before = observed;
        this.submittedAt = snapshot.monotonicTimeNanos ();
        this.submittedRevision = snapshot.revision ();
        return new SetControllerIntegerSettingEffect (this.setting, this.desired);
    }
}
