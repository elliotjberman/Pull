// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

/** Observed ribbon function, MIDI CC and repeat role; physical touch has no display meaning. */
public record RibbonPagePresentation (boolean available, int function, int cc, int noteRepeat) { }
