// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import java.util.Objects;

/** Latest deferred global page intent and its exact core-owned temporary return token. */
final class ControllerPageTransitions
{
    private final PageNavigation navigation;
    private Request current;

    ControllerPageTransitions () { this (PageNavigation.defaults ()); }
    ControllerPageTransitions (final PageNavigation navigation) { this.navigation = Objects.requireNonNull (navigation, "navigation"); }
    PageNavigation.Origin origin () { return this.navigation.origin (); }
    String visibleAlias () { return this.navigation.legacyAlias (); }
    Request temporary (final PageNavigation.Origin origin, final String page) { return this.replace (new Request (origin, Operation.TEMPORARY, page)); }
    Request select (final PageNavigation.Origin origin, final String page) { return this.replace (new Request (origin, Operation.SELECT, page)); }
    Request restore (final PageNavigation.Origin origin) { return this.replace (new Request (origin, Operation.RESTORE, "")); }

    /** Transfer an inherited return flag only while its original temporary owner remains current. */
    Request adopt (final Request previous)
    {
        this.observe ();
        if (!this.current (previous) || previous.operation != Operation.TEMPORARY) return null;
        final Request next = new Request (previous.origin, previous.operation, previous.page);
        next.submitted = previous.submitted;
        next.temporaryToken = previous.temporaryToken;
        return this.replace (next);
    }

    private Request replace (final Request next)
    {
        this.current = next;
        return next;
    }

    void observe ()
    {
        final Request request = this.current;
        if (request != null && request.submitted && request.temporaryToken != this.navigation.state ().temporaryToken ()) this.cancel (request);
    }

    /** Called only after semantic admission. Controller-local state needs no synthetic host ack. */
    void advance (final Request request)
    {
        this.observe ();
        if (!this.current (request)) return;
        if (!request.submitted)
        {
            request.submitted = true;
            switch (request.operation)
            {
                case SELECT -> this.navigation.select (request.origin, this.navigation.resolve (request.page));
                case RESTORE -> this.navigation.restore (request.origin);
                case TEMPORARY -> request.temporaryToken = this.navigation.temporary (request.origin, this.navigation.resolve (request.page));
            }
            if (request.operation != Operation.TEMPORARY || request.temporaryToken == 0) this.cancel (request);
        }
        if (this.current (request) && request.released && request.restoreOnRelease)
        {
            this.navigation.releaseTemporary (request.temporaryToken);
            this.cancel (request);
        }
    }

    void release (final Request request, final boolean restore)
    {
        if (!this.current (request)) return;
        request.released = true;
        request.restoreOnRelease = restore;
    }
    boolean complete (final Request request) { return !this.current (request) || request.submitted && request.released && !request.restoreOnRelease; }
    void cancel (final Request request)
    {
        if (request == null) return;
        if (this.current == request) this.current = null;
    }
    private boolean current (final Request request) { return request != null && request == this.current; }
    private enum Operation { SELECT, TEMPORARY, RESTORE }
    static final class Request
    {
        private final PageNavigation.Origin origin;
        private final Operation operation;
        private final String page;
        private boolean submitted;
        private long temporaryToken;
        private boolean released;
        private boolean restoreOnRelease;
        private Request (final PageNavigation.Origin origin, final Operation operation, final String page) { this.origin = origin; this.operation = operation; this.page = page; }
    }
}
