// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import java.util.List;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import java.util.Objects;

/** Immutable identities for input admission. Values and display names are never target identities. */
public sealed interface InputTarget
{
    /** A controller-local interaction whose view identity is fenced separately by the router. */
    record Local (ControlId control) implements InputTarget
    {
        public Local { Objects.requireNonNull (control, "control"); }
    }

    /** The shell's exact parameter incarnation, shared across controls and views. */
    record Parameter (ParameterTargetRef reference) implements InputTarget
    {
        public Parameter { Objects.requireNonNull (reference, "reference"); }
    }

    /** Exact clip identity; its acquired launch owner is retired by authoritative shell state. */
    record Clip (ClipTargetId reference) implements InputTarget
    {
        public Clip { Objects.requireNonNull (reference, "reference"); }
    }

    /** One interaction depends on all these independently observed contexts. */
    record Composite (List<InputTarget> targets) implements InputTarget
    {
        public Composite
        {
            targets = List.copyOf (targets);
            if (targets.isEmpty ()) throw new IllegalArgumentException ("Composite target cannot be empty");
        }
    }

    /** Exact bounded Session bank, including its unavailable observation. */
    record SessionBank (ControlId control, long generation, SessionBankShape shape) implements InputTarget
    {
        public SessionBank
        {
            Objects.requireNonNull (control, "control");
            Objects.requireNonNull (shape, "shape");
            if (generation < 0) throw new IllegalArgumentException ("Negative Session generation");
        }
    }

    /** Frozen native drum-note address and pressure interpretation. */
    record DrumPad (ControlId control, long selectedGeneration, String channelId, long drumGeneration, String deviceId, int midiNote, GridPressureConfiguration pressure) implements InputTarget
    {
        public DrumPad
        {
            Objects.requireNonNull (control, "control");
            Objects.requireNonNull (pressure, "pressure");
            if (selectedGeneration < 0 || drumGeneration < 0 || midiNote < 0 || midiNote > 127 || Objects.requireNonNull (channelId, "channelId").isBlank () || Objects.requireNonNull (deviceId, "deviceId").isBlank ())
                throw new IllegalArgumentException ("Invalid drum-pad target");
        }
    }

    /** Selected note target and applicability; preference changes do not replace the target. */
    record Note (ControlId control, long selectedGeneration, String selectedChannel, long noteGeneration, String noteChannel, int position, boolean drumApplicable) implements InputTarget
    {
        public Note
        {
            Objects.requireNonNull (control, "control");
            Objects.requireNonNull (selectedChannel, "selectedChannel");
            Objects.requireNonNull (noteChannel, "noteChannel");
            if (selectedGeneration < 0 || noteGeneration < 0 || position < -1)
                throw new IllegalArgumentException ("Invalid selected note target");
        }
    }

    /** An independent control bound to a host-owned context with an identity/generation; an empty owner denotes an observed empty slot. */
    record Context (ControlId control, String scope, String owner, long generation) implements InputTarget
    {
        public Context
        {
            Objects.requireNonNull (control, "control");
            Objects.requireNonNull (owner, "owner");
            if (Objects.requireNonNull (scope, "scope").isBlank () || generation < 0)
                throw new IllegalArgumentException ("Context requires a scope, owner, and nonnegative generation");
        }
    }
}
