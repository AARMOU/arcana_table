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

    /**
     * In auto-pass mode, only stop for an actual target on you or an owned/controlled card.
     * Unknown targets fail closed: guessing would silently miss a chance to respond.
     * Untargeted spells/abilities, including opponents' own-board effects, are passed over.
     */
    fun interruptingStackItem(
        stack: List<ArgentumStackItem>,
        targets: Map<String, ArgentumTargetRef>,
        localPlayerId: String?,
    ): String? {
        if (localPlayerId.isNullOrBlank()) return stack.firstOrNull()?.name
        return stack.firstOrNull { item ->
            (item.targetNames.isNotEmpty() && item.targetIds.isEmpty()) ||
                item.targetIds.any { id ->
                    id == localPlayerId || targets[id]?.let { target ->
                        target.ownerId == localPlayerId || target.controllerId == localPlayerId
                    } ?: true
                }
        }?.name
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

    /**
     * One tick of auto-pass. Stop for required decisions/combat declarations, a stack
     * item targeting you or your cards, game over, or your next turn. Skip non-threatening
     * opponent spells and abilities. Only send Argentum's real legal PassPriority action.
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
    ): Outcome = when {
        gameOver -> Outcome(Step.INTERRUPTED, "The game is over.")
        activePlayer == localSeat && turnNumber > startTurn -> Outcome(Step.REACHED_YOUR_TURN)
        awaitingUpdate -> Outcome(Step.WAIT)
        hasPendingDecision -> Outcome(Step.INTERRUPTED, "Argentum needs a choice from you.")
        "DeclareBlockers" in legalActionTypes -> Outcome(Step.INTERRUPTED, "You're being attacked. Declare blockers.")
        "DeclareAttackers" in legalActionTypes -> Outcome(Step.INTERRUPTED, "You can declare attackers.")
        opponentStackItem != null -> Outcome(Step.INTERRUPTED, "$opponentStackItem targets you or your cards. You can respond.")
        "PassPriority" in legalActionTypes && priorityPlayer == localSeat -> Outcome(Step.PASS)
        "PassPriority" in legalActionTypes && priorityPlayer < 0 -> Outcome(Step.PASS)
        else -> Outcome(Step.WAIT)
    }
}
