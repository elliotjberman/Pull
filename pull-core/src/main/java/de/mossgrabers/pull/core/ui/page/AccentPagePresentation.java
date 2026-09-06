// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

/** Observed fixed velocity and physical touch brightness; pending parameter writes are excluded. */
public record AccentPagePresentation (boolean available, int velocity, double position, boolean touched) { }
