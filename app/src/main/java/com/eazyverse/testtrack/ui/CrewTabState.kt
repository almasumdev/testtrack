package com.eazyverse.testtrack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Which of the Crews tab's four tabs shows, and how it got there.
 *
 * Each new visit opens on Open. Once the first answer after it is in, a night with apps owed
 * moves to Run a moment later, the way a tap would; nothing owed stays on Open. A reload never
 * moves the tab, and a touch on the tab, or leaving it, calls off a move still waiting.
 *
 * Kept apart from [CrewViewModel], which passes its loads and taps through.
 */
class CrewTabState(
    private val scope: CoroutineScope,
) {
    /** Which tab is showing. */
    var tab by mutableStateOf(CrewPhase.OPEN)
        private set

    /** Whether [tab] is worth showing yet: set on arrival. Until then the screen shows one plain placeholder. */
    var known by mutableStateOf(false)
        private set

    /**
     * The last tab change moves like a tap (it fades through): a tap itself, or the slide to Run
     * after arriving. Only a placeholder giving way to a tab cross-fades instead.
     */
    var byHand = false
        private set

    /** Set on arrival: the first answer after it may slide the tab to Run, once. */
    private var slideAsked = false

    /** The slide to Run, waiting its moment. */
    private var slide: Job? = null

    /** The visit last arrived at, so coming back to it from a page on top is not a new one. */
    private var visit: String? = null

    /** The tab is on screen; a tab asked for while it is not is where the next arrival opens. */
    private var showing = false
    private var asked: CrewPhase? = null

    /**
     * A visit to the tab. The same [visit] come back to keeps its tab, a tab asked for on the way in
     * ([show]) wins, and [fixed] (the screen gallery's tab put in by hand) is left alone.
     */
    fun arrive(
        visit: String,
        fixed: Boolean = false,
    ) {
        showing = true
        val asked = asked.also { this.asked = null }
        if (visit == this.visit) return
        this.visit = visit
        if (fixed) return
        hold()
        slideAsked = asked == null
        byHand = false
        known = true
        tab = asked ?: CrewPhase.OPEN
    }

    /** The tab is off screen: a slide still waiting does not come. */
    fun leave() {
        showing = false
        hold()
    }

    /** A touch anywhere on the tab, or leaving it: the slide to Run waiting its moment does not come. */
    fun hold() {
        slideAsked = false
        slide?.cancel()
        slide = null
    }

    /** A tab picked by hand, or asked for by another page on the way to this one. */
    fun show(phase: CrewPhase) {
        hold()
        if (!showing) asked = phase
        byHand = true
        known = true
        tab = phase
    }

    /** The screen gallery's tab, put in directly. */
    internal fun preview(phase: CrewPhase) {
        hold()
        known = true
        tab = phase
    }

    /**
     * The newest answer is in. Only the first after arriving, and only from Open, may move the tab:
     * moving it under somebody who has started reading is worse than staying put.
     */
    fun answered(crews: List<Crew>) {
        if (!slideAsked) return
        slideAsked = false
        if (tab != CrewPhase.OPEN || !slidesToRun(crews)) return
        slide?.cancel()
        slide =
            scope.launch {
                delay(SLIDE_TO_RUN_MS)
                byHand = true
                tab = CrewPhase.RUN
            }
    }

    /** The newest load is over, answered or not: it has had its say on the tab. */
    fun loaded() {
        slideAsked = false
    }

    private companion object {
        /** How long Open shows before a night with apps owed slides to Run. */
        const val SLIDE_TO_RUN_MS = 400L
    }
}

/** Whether Crews moves from Open to Run on arrival: an app owed tonight in a running crew, a volunteer's too. */
internal fun slidesToRun(crews: List<Crew>): Boolean = tonightIn(crews).isNotEmpty()
