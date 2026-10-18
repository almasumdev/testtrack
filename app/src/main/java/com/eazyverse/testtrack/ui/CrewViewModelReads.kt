package com.eazyverse.testtrack.ui

/*
 * What the Crew tab reads off its view model: counts and lists worked out from the crews it holds.
 * Kept beside the view model rather than in it, as plain reads over its state, so the class itself
 * stays the part that talks to the server.
 */

/** What the pill for a tab counts, or zero where the tab has nothing outstanding. */
fun CrewViewModel.badge(phase: CrewPhase) =
    when (phase) {
        CrewPhase.RUN -> tonight.size
        CrewPhase.LOBBY -> sentBack.size + toAnswer
        CrewPhase.OPEN -> 0
        CrewPhase.RECORD -> 0
    }

/** The crew being looked at. Only ever one of yours. */
val CrewViewModel.current: Crew?
    get() = opened?.let { id -> crews.firstOrNull { it.id == id } }

/** Whether the open crew is one you already have an app in. */
val CrewViewModel.inOpen: Boolean
    get() = open?.let { o -> crews.any { it.id == o.id && !it.out } } == true

/**
 * Everything owed tonight, across every running crew.
 *
 * One column rather than one per crew. Which crew an app belongs to is not the reason anybody
 * opens it, and a member with two runs going has one evening, not two.
 */
val CrewViewModel.tonight: List<CrewApp>
    get() = tonightIn(crews)

/** Everything settled tonight, for the count under the list. */
val CrewViewModel.doneTonight: Int
    get() = doneTonightIn(crews)

/** Apps waiting on members that this account may answer for and has not. */
val CrewViewModel.toAnswer: Int
    get() = crews.filter { it.mayVote }.sumOf { c -> c.seats.count { it.state == SeatState.CHECKING && !it.mine && it.myVote == null } }

/** Your apps sent back before a run, to fix and put in again. */
val CrewViewModel.sentBack: List<CrewSeat>
    get() = sentBackIn(crews)

/** How many runs are going, which is what the nightly figure is made of. */
val CrewViewModel.running: Int
    get() = crews.count { it.phase == CrewPhase.RUN }
