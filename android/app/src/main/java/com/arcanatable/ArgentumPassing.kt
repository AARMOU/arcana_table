package com.arcanatable

/**
 * Pass-button wording and the client-side "Pass to my turn" loop for Argentum games.
 *
 * Argentum already auto-passes steps where nothing meaningful can happen (AutoPassManager), and it
 * tells each seat where passing will take them (`nextStopPoint`: "Resolve", "Pass", "End Turn" or
 * "To <step>"). Arcana only presses the server's own PassPriority action; it never invents one.
 */
object ArgentumPassing {
    /** "To Combat" → "PASS → TO COMBAT"; falls back to "PASS PRIORITY" when Argentum sent nothing. */
    fun passLabel(nextStopPoint: String?): String {
        val stop = nextStopPoint?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return "PASS PRIORITY"
        return if (stop.equals("Pass", ignoreCase = true)) "PASS" else "PASS → ${stop.uppercase()}"
    }

    enum class Step {
        /** Send Argentum's PassPriority now. */
        PASS,
        /** Nothing to do yet (opponent acting, or waiting for the server). */
        WAIT,
        /** Your next turn has started. */
        REACHED_YOUR_TURN,
        /** Stop and hand control back: something needs the player. */
        INTERRUPTED,
    }

    data class Outcome(val step: Step, val reason: String? = null)

    /** Only the engine's explicit phase/step labels qualify. Unknown labels do not invent a stop. */
    internal fun selectedOpponentWindow(
        phaseName: String?,
        stepName: String?,
        stopAtPrecombat: Boolean,
        stopAtEndStep: Boolean,
    ): String? {
        fun normalized(value: String?): String = value.orEmpty().trim().uppercase()
            .replace('_', ' ').replace(Regex("\\s+"), " ")
        val phase = normalized(phaseName)
        val step = normalized(stepName)
        if (stopAtPrecombat && phase in setOf("PRECOMBAT MAIN", "PRECOMBAT MAIN PHASE") &&
            step in setOf("MAIN", "PRECOMBAT MAIN", "MAIN PHASE")) return "Opponent's precombat main phase"
        if (stopAtEndStep && phase in setOf("ENDING", "ENDING PHASE", "END") &&
            step in setOf("END", "END STEP")) return "Opponent's end step"
        return null
    }

    /**
     * One tick of "Pass to my turn". Stops for any decision, for being attacked (a block
     * declaration), for an attack declaration, for an opponent's spell or ability on the stack,
     * and when the player's next turn begins.
     */
    fun next(
        startTurn: Int,
        turnNumber: Int,
        activePlayer: Int,
        localSeat: Int,
        priorityPlayer: Int,
        legalActionTypes: Set<String>,
        hasPendingDecision: Boolean,
        opponentStackItem: String?,
        gameOver: Boolean,
        awaitingUpdate: Boolean,
        currentPhaseName: String? = null,
        currentStepName: String? = null,
        stopAtOpponentPrecombat: Boolean = false,
        stopAtOpponentEndStep: Boolean = false,
    ): Outcome = when {
        gameOver -> Outcome(Step.INTERRUPTED, "The game is over.")
        activePlayer == localSeat && turnNumber > startTurn -> Outcome(Step.REACHED_YOUR_TURN)
        awaitingUpdate -> Outcome(Step.WAIT)
        hasPendingDecision -> Outcome(Step.INTERRUPTED, "Argentum needs a choice from you.")
        "DeclareBlockers" in legalActionTypes -> Outcome(Step.INTERRUPTED, "You're being attacked. Declare blockers.")
        "DeclareAttackers" in legalActionTypes -> Outcome(Step.INTERRUPTED, "You can declare attackers.")
        opponentStackItem != null -> Outcome(Step.INTERRUPTED, "$opponentStackItem is on the stack. You can respond.")
        activePlayer != localSeat && priorityPlayer == localSeat && "PassPriority" in legalActionTypes &&
            selectedOpponentWindow(currentPhaseName, currentStepName, stopAtOpponentPrecombat, stopAtOpponentEndStep) != null ->
                Outcome(Step.INTERRUPTED, selectedOpponentWindow(currentPhaseName, currentStepName, stopAtOpponentPrecombat, stopAtOpponentEndStep))
        "PassPriority" in legalActionTypes && priorityPlayer == localSeat -> Outcome(Step.PASS)
        "PassPriority" in legalActionTypes && priorityPlayer < 0 -> Outcome(Step.PASS)
        else -> Outcome(Step.WAIT)
    }
}
