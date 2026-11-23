package com.eazyverse.testtrack.domain

/**
 * Lets one thing run at a time, such as Android's screen-share ask. A second try while the first is
 * still waiting for its answer is turned away, so two asks never race and cancel each other.
 */
class OneAtATime {
    private var running = false

    /** True when the caller may go ahead; false while an earlier one has not finished. */
    fun begin(): Boolean {
        if (running) return false
        running = true
        return true
    }

    /** The answer came back: the next one may begin. */
    fun end() {
        running = false
    }
}
