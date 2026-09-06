// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Value-only core page identity, with an optional alias for frozen legacy queries. */
public record ControllerPageRef (Kind kind, String id, String legacyAlias)
{
    public enum Kind { CORE, LEGACY, NONE }
    private static final ControllerPageRef NONE = new ControllerPageRef (Kind.NONE, "", "");
    public ControllerPageRef
    {
        kind = Objects.requireNonNull (kind, "kind");
        id = Objects.requireNonNull (id, "id");
        legacyAlias = Objects.requireNonNull (legacyAlias, "legacyAlias");
        if (id.length () > 128 || legacyAlias.length () > 128 || id.chars ().anyMatch (Character::isISOControl) || legacyAlias.chars ().anyMatch (Character::isISOControl))
            throw new IllegalArgumentException ("Page IDs must be bounded printable values");
        if (kind == Kind.NONE ? !id.isEmpty () || !legacyAlias.isEmpty () : id.isBlank ())
            throw new IllegalArgumentException ("Page identity does not match its kind");
        if (kind == Kind.LEGACY && !id.equals (legacyAlias))
            throw new IllegalArgumentException ("Legacy pages use their installed mode ID as alias");
    }
    public static ControllerPageRef none () { return NONE; }
    public static ControllerPageRef core (final String id) { return core (id, ""); }
    public static ControllerPageRef core (final String id, final String alias) { return new ControllerPageRef (Kind.CORE, id, alias); }
    public static ControllerPageRef legacy (final String id) { return new ControllerPageRef (Kind.LEGACY, id, id); }
    public boolean isPresent () { return this.kind != Kind.NONE; }
}
