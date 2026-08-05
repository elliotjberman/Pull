// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;


/**
 * Single seam for controller-originated parameter mutations.
 */
@FunctionalInterface
public interface ParameterMutationGateway
{
    /**
     * Submit one resolved mutation.
     *
     * @param request Mutation request
     */
    void mutate (ParameterMutationRequest request);
}
