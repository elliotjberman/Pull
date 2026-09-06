/**
 * Core-owned presentation values, rendering and styling for controller pages.
 *
 * <p>Feature views resolve host identities and project subscribed state into immutable presentation
 * records. Renderers accept only those values and produce display primitives and light values;
 * they do not resolve parameters, navigate pages, submit effects or mutate controller state.
 * Family styles share typography, geometry and palettes without introducing a configurable UI schema.
 *
 * <p>The shared mixer primitive also accepts the existing value-only MixerControlSnapshot DTO used
 * by frozen legacy data adapters. That DTO carries normalized display values, never live actuators.
 */
package de.mossgrabers.pull.core.ui.page;
