// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import java.util.Objects;

/** Explicit address supplied by a named resource owner, never inferred from a wrapper. */
record ParameterIdentity (String domain, String ownerId, int page, int index, String parameterName)
{
    ParameterIdentity
    {
        Objects.requireNonNull (domain, "domain");
        Objects.requireNonNull (ownerId, "ownerId");
        parameterName = Objects.requireNonNullElse (parameterName, "");
    }
}
