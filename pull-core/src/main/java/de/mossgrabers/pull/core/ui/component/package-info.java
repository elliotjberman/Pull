/**
 * Reusable Push UI components: immutable content and pure drawing, independent of feature views.
 *
 * <p>Pages supply local positions and styles, then map component feedback to their claimed outputs.
 * Components do not look up host state, identify targets, bind physical controls, navigate, acquire
 * touches or submit effects. The workspace compiler retains ownership of display clip scopes.
 * See docs/ui-component-library.md for the component inventory and offline visual catalog.
 */
package de.mossgrabers.pull.core.ui.component;
