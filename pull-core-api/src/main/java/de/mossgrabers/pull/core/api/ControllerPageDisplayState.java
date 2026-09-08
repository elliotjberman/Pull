// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** Bounded observations for pages whose input handlers are still frozen in the shell. */
public sealed interface ControllerPageDisplayState permits DevicePageState, OptionPageState, EditingPageState, ControllerPageDisplayState.Empty
{
    record Empty () implements ControllerPageDisplayState { }
}
