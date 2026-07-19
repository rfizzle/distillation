package com.rfizzle.distillation.compat.viewer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tier 1 — the viewer-refresh debounce cadence. The queue holds static state, so every test clears
 * it afterwards rather than leaking a pending request into the next one.
 */
class ViewerRefreshQueueTest {

    @AfterEach
    void clearPendingRequest() {
        ViewerRefreshQueue.clear();
    }

    @Test
    void twoRequestsInOneTickFlushOnce() {
        // The join burst: the config sync and the discovery sync both land before the tick ends.
        ViewerRefreshQueue.request();
        ViewerRefreshQueue.request();

        assertTrue(ViewerRefreshQueue.drain(), "the tick after a burst owes one rebuild");
        assertFalse(ViewerRefreshQueue.drain(), "the burst must not owe a second rebuild");
    }

    @Test
    void requestAfterFlushDrainsAgain() {
        ViewerRefreshQueue.request();
        ViewerRefreshQueue.drain();

        // A discovery made later in the session still invalidates the viewers.
        ViewerRefreshQueue.request();

        assertTrue(ViewerRefreshQueue.drain(), "a request after a flush starts a fresh cycle");
    }

    @Test
    void idleTickDrainsNothing() {
        assertFalse(ViewerRefreshQueue.drain(), "an idle tick owes no rebuild");
    }

    @Test
    void clearDropsPendingRequest() {
        ViewerRefreshQueue.request();

        ViewerRefreshQueue.clear();

        assertFalse(ViewerRefreshQueue.drain(), "a request pending at disconnect must not survive it");
    }
}
