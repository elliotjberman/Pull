// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;

import java.util.Objects;
import java.util.Optional;


/**
 * One controller-originated numeric mutation after target resolution.
 *
 * @param target Exact target when the mutation is snapback-eligible
 * @param mutation Established mutation callback
 * @param persistence Persistence policy
 */
public record ParameterMutationRequest (Optional<ParameterMutationTarget> target, Runnable mutation, PersistencePolicy persistence)
{
    /** Persistence policy for a submitted mutation. */
    public enum PersistencePolicy
    {
        /** Mutation remains applied. */
        PERSISTENT,
        /** Mutation is restored when the active trigger session ends. */
        SNAPBACK_ELIGIBLE
    }


    /**
     * Validate the request.
     */
    public ParameterMutationRequest
    {
        target = Objects.requireNonNull (target, "target");
        mutation = Objects.requireNonNull (mutation, "mutation");
        persistence = Objects.requireNonNull (persistence, "persistence");
        if (persistence == PersistencePolicy.SNAPBACK_ELIGIBLE && target.isEmpty ())
            throw new IllegalArgumentException ("Snapback-eligible mutations require an exact target");
    }


    /**
     * Create a persistent mutation.
     *
     * @param mutation Mutation callback
     * @return Request
     */
    public static ParameterMutationRequest persistent (final Runnable mutation)
    {
        return new ParameterMutationRequest (Optional.empty (), mutation, PersistencePolicy.PERSISTENT);
    }


    /**
     * Create a snapback-eligible mutation.
     *
     * @param target Exact target
     * @param mutation Mutation callback
     * @return Request
     */
    public static ParameterMutationRequest snapback (final ParameterMutationTarget target, final Runnable mutation)
    {
        return new ParameterMutationRequest (Optional.of (Objects.requireNonNull (target, "target")), mutation, PersistencePolicy.SNAPBACK_ELIGIBLE);
    }
}
