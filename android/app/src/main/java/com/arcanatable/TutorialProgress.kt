package com.arcanatable

/**
 * Where a tutorial lesson is and how it moves on. Pure (no Android or Compose state) so the
 * same logic that runs in the app is what the engine walkthrough tests drive.
 *
 * Gated steps in a practice lesson only advance on the matching gesture or Argentum event.
 * A real game can get ahead of the text, though: the learner can cast before tapping a land for
 * mana, or Argentum can skip a combat step nobody can use. So while the lesson WAITS on an
 * event, a later game milestone that makes that wait pointless moves it on to the step after
 * the milestone, instead of leaving the learner stuck with no NEXT button. Explanations the
 * learner is reading are never skipped: milestones that happen meanwhile complete their step
 * when the learner reaches it, and a priority pass never counts for a later pass prompt.
 */
internal class TutorialRun(val lesson: ArcanaLesson, val practice: Boolean, startIndex: Int = 0) {
    var index: Int = startIndex.coerceIn(0, (lesson.steps.size - 1).coerceAtLeast(0))
        private set
    var finished: Boolean = false
        private set
    private val milestones = TutorialMilestones()

    val step: ArcanaLessonStep? get() = if (finished) null else lesson.steps.getOrNull(index)

    /** NEXT on the coach card, or the last step's button. */
    fun next() = moveTo(index + 1)

    /** A gesture ("tap"/"hold" + target) or an Argentum event ("event" + name). Returns true if the step changed. */
    fun signal(kind: String, key: String): Boolean {
        if (finished) return false
        if (practice && kind == "event") milestones.observe(key)
        val current = step ?: return false
        if (TutorialStepGate.accepts(current, kind, key)) { moveTo(index + 1); return true }
        if (!practice || kind != "event" || current.actionKind != "event" || key !in CATCH_UP_EVENTS) return false
        val ahead = catchUpIndex(key) ?: return false
        moveTo(ahead + 1)
        return true
    }

    /** The later step [event] satisfies, if nothing but explanations and gestures stand between. */
    private fun catchUpIndex(event: String): Int? {
        for (j in index + 1..lesson.steps.lastIndex) {
            val s = lesson.steps[j]
            if (s.actionKind == "event") return j.takeIf { s.event == event }
        }
        return null
    }

    private companion object {
        /** Game progress that can't be undone, so it can stand in for an earlier wait. */
        val CATCH_UP_EVENTS = setOf("SpellCast", "SpellResolved", "AttackersDeclared", "CombatDamageDealt", "TurnAdvanced")
    }

    private fun moveTo(target: Int) {
        if (target > lesson.steps.lastIndex) { finished = true; return }
        index = target
        // Combat and stack resolution can finish while an explanation is open.
        if (practice && milestones.completes(step)) moveTo(index + 1)
    }
}

/**
 * Turns two consecutive Argentum snapshots into the tutorial events they show happened.
 * [step] is read before each check because an event can move the lesson on.
 */
internal class TutorialEventDetector {
    var startTurn = 0
    /** Set when Arcana sends PassPriority / Keep Hand; consumed by the next snapshot. */
    var passPending = false
    var keepPending = false

    fun reset() { startTurn = 0; passPending = false; keepPending = false }

    fun detect(previous: ArgentumTableSnapshot?, current: ArgentumTableSnapshot, step: () -> ArcanaLessonStep?, emit: (String) -> Unit) {
        if (startTurn == 0) startTurn = current.turnNumber
        val old = previous?.players?.getOrNull(previous.viewingPlayer)
        val now = current.players.getOrNull(current.viewingPlayer) ?: return
        // The pass you just made comes first, then what it caused (a resolution, a new step).
        if (passPending && previous != null && current.interactionEpoch != previous.interactionEpoch) {
            passPending = false
            emit("PriorityPassed")
        }
        if (previous != null && old != null) {
            fun isLand(c: GameCard) = "land" in c.type.lowercase()
            val oldIds = (old.battlefield + old.graveyard + old.exile).map { it.id }.toSet()
            val oldLands = old.battlefield.filter(::isLand).map { it.id }.toSet()
            if (now.hand.count(::isLand) < old.hand.count(::isLand) && now.battlefield.any { isLand(it) && it.id !in oldLands })
                emit("LandPlayed")
            if (now.manaPool.values.sum() > old.manaPool.values.sum()) emit("ManaAdded")
            // One of your nonland cards left your hand for the stack or straight to where it lands.
            val handNames = old.hand.filterNot(::isLand).map { it.name }.distinct()
            if (handNames.any { name ->
                    old.hand.count { it.name == name } > now.hand.count { it.name == name } &&
                        (current.stack.any { it.name == name } || (now.battlefield + now.graveyard + now.exile).any { it.name == name && it.id !in oldIds })
                }) emit("SpellCast")
            // Something on the stack resolved, or a spell you cast arrived on the battlefield.
            val stackShrank = previous.stack.isNotEmpty() && current.stack.size < previous.stack.size
            val arrived = now.battlefield.any { c -> !isLand(c) && c.id !in oldIds && previous.stack.any { it.name == c.name } }
            if (stackShrank || arrived) emit("SpellResolved")
            val yourTurn = previous.activePlayer == previous.viewingPlayer
            if (previous.stepKey == "DECLARE_ATTACKERS" && current.stepKey != previous.stepKey) emit("AttackersDeclared")
            // Argentum moves straight past combat when you have no creature that can attack.
            else if (yourTurn && previous.stepKey in BEFORE_ATTACKS && (current.stepKey in AFTER_ATTACKS || current.turnNumber > previous.turnNumber))
                emit("AttackersDeclared")
            val oldOpp = previous.players.getOrNull(1 - previous.viewingPlayer)
            val newOpp = current.players.getOrNull(1 - current.viewingPlayer)
            if (oldOpp != null && newOpp != null && newOpp.life < oldOpp.life) emit("CombatDamageDealt")
        }
        // Your turn ended: a later turn number, or the opponent became the active player.
        if (current.turnNumber > startTurn || (previous != null && current.activePlayer != current.viewingPlayer && previous.activePlayer == previous.viewingPlayer))
            emit("TurnAdvanced")
        if (keepPending) {
            keepPending = false
            emit("HandKept")
        }
    }

    companion object {
        private val BEFORE_ATTACKS = setOf("UNTAP", "UPKEEP", "DRAW", "PRECOMBAT_MAIN", "BEGIN_COMBAT")
        private val AFTER_ATTACKS = setOf("END_COMBAT", "POSTCOMBAT_MAIN", "END", "CLEANUP")
    }
}
