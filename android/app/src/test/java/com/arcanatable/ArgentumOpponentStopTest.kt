package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentumOpponentStopTest {
    private fun outcome(
        phase: String?, step: String?,
        precombat: Boolean = false, endStep: Boolean = false,
        localPriority: Boolean = true, localTurn: Boolean = false,
        decision: Boolean = false, waiting: Boolean = false,
        stack: String? = null,
        legal: Set<String> = setOf("PassPriority"),
    ) = ArgentumPassing.next(
        startTurn = 2, turnNumber = 3, activePlayer = if (localTurn) 0 else 1,
        localSeat = 0, priorityPlayer = if (localPriority) 0 else 1,
        legalActionTypes = legal, hasPendingDecision = decision,
        opponentStackItem = stack, gameOver = false, awaitingUpdate = waiting,
        currentPhaseName = phase, currentStepName = step,
        stopAtOpponentPrecombat = precombat, stopAtOpponentEndStep = endStep,
    )

    @Test fun defaultSettingsPreservePriorAutoPassing() {
        assertEquals(ArgentumPassing.Step.PASS, outcome("ENDING", "END").step)
        assertEquals(ArgentumPassing.Step.PASS, outcome("PRECOMBAT_MAIN", "MAIN").step)
    }

    @Test fun enabledEndStepStopsOnlyAtRealOfferedPriorityWindow() {
        val actual = outcome("ENDING", "END_STEP", endStep = true)
        assertEquals(ArgentumPassing.Step.INTERRUPTED, actual.step)
        assertEquals("Opponent's end step", actual.reason)
        assertEquals(ArgentumPassing.Step.WAIT, outcome("ENDING", "END", endStep = true, localPriority = false).step)
        assertEquals(ArgentumPassing.Step.PASS, outcome("ENDING", "END", endStep = true, localTurn = true).step)
        assertEquals(ArgentumPassing.Step.WAIT, outcome("ENDING", "END", endStep = true, legal = emptySet()).step)
        assertEquals(ArgentumPassing.Step.PASS, outcome("ENDING", "CLEANUP", endStep = true).step)
    }

    @Test fun precombatOptionOnlyStopsOnMatchingPhaseAndStep() {
        assertEquals(ArgentumPassing.Step.INTERRUPTED, outcome("PRECOMBAT_MAIN", "MAIN", precombat = true).step)
        assertEquals(ArgentumPassing.Step.PASS, outcome("POSTCOMBAT_MAIN", "MAIN", precombat = true).step)
        assertEquals(ArgentumPassing.Step.PASS, outcome("COMBAT", "DECLARE_ATTACKERS", precombat = true).step)
        assertEquals(ArgentumPassing.Step.PASS, outcome(null, null, precombat = true, endStep = true).step)
    }

    @Test fun mandatoryChoicesAndStackInteractionsStillTakePrecedence() {
        assertEquals("Argentum needs a choice from you.", outcome("ENDING", "END", endStep = true, decision = true).reason)
        assertEquals(ArgentumPassing.Step.WAIT, outcome("ENDING", "END", endStep = true, waiting = true).step)
        assertTrue(outcome("ENDING", "END", endStep = true, stack = "Removal").reason!!.contains("stack"))
        // Existing safety rule: the player's next turn always ends the pass loop.
        assertEquals(ArgentumPassing.Step.REACHED_YOUR_TURN,
            ArgentumPassing.next(
                startTurn = 2, turnNumber = 4, activePlayer = 0, localSeat = 0,
                priorityPlayer = 0, legalActionTypes = setOf("DeclareBlockers"),
                hasPendingDecision = false, opponentStackItem = null, gameOver = false, awaitingUpdate = false,
                currentPhaseName = "ENDING", currentStepName = "END",
                stopAtOpponentPrecombat = true, stopAtOpponentEndStep = true,
            ).step)
    }

    @Test fun selectedWindowLabelsDoNotGuessFromUnrecognizedPhases() {
        assertEquals(null, ArgentumPassing.selectedOpponentWindow("MYSTERY", "END", false, true))
        assertEquals(null, ArgentumPassing.selectedOpponentWindow("COMBAT", "MAIN", true, false))
        assertEquals("Opponent's end step", ArgentumPassing.selectedOpponentWindow("ENDING", "END", false, true))
    }
}
