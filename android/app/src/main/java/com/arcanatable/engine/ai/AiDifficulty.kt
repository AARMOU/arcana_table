package com.arcanatable.engine.ai

import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.budget.SearchAllowances

/**
 * How hard one AI seat plays. Chosen per seat before an offline game.
 *
 * - [EASY]: the plain engine AI with almost no look-ahead. Beatable while learning.
 * - [NORMAL]: exactly the mobile AI that shipped before difficulty existed (Build 123 limits).
 * - [HARD]: Argentum's own server AI profile ([AiProfile.LIVE]: card intent, set advisors, holding
 *   removal and counters, combat tricks after blocks, race-clock attacks, rollouts) on a phone budget.
 * - [BRUTAL]: the same strategy with server-sized search: more targets simulated, deeper attack
 *   search, more rollouts and more thinking time.
 *
 * No level sees hidden cards. Every level searches only what the engine's public rules state allows.
 */
enum class AiDifficulty(
    val label: String,
    val blurb: String,
    /** Search time for an ordinary decision (ms). */
    val normalMillis: Long,
    /** Search time for attacks and blocks (ms). */
    val combatMillis: Long,
    /** Search time when the battlefield is crowded (>= [CROWDED_BATTLEFIELD] permanents). */
    val crowdedMillis: Long,
    /** This seat's share of the thinking time for one host burst (until a person must act). */
    val burstMillis: Long,
    /**
     * Chance that the AI hesitates on a play it chose: holds a spell or ability for later (passes
     * instead) or skips a planned attack. It never makes an illegal play, only a slower one. Search
     * depth alone barely changes the plain AI, so this is what makes Easy beatable.
     */
    val hesitateChance: Double = 0.0,
) {
    EASY("Easy", "Holds back and misses attacks. Good for learning.", 250, 600, 150, 600, hesitateChance = 0.4),
    NORMAL("Normal", "The standard AI.", 250, 600, 150, 600),
    HARD("Hard", "Argentum's full strategy. Plays tight.", 800, 1_600, 300, 3_000),
    BRUTAL("Brutal", "Hard, searching deeper. Slower.", 1_200, 2_400, 450, 5_000);

    /** Search work allowed for one decision at this level. */
    fun allowances(combat: Boolean, crowded: Boolean): SearchAllowances = when (this) {
        // Easy searches exactly like Normal; it is weaker only through [hesitateChance].
        EASY, NORMAL -> if (crowded) SearchAllowances.TRIVIAL_ALLOWANCES else SearchAllowances.forMillis(if (combat) combatMillis else normalMillis).copy(
            targetCandidates = 2,
            refineTargetsBySimulation = false,
            blockSimulations = 4,
            attackSearchIterations = 1,
            rolloutPlayouts = 0,
        )
        HARD -> if (crowded) SearchAllowances.forMillis(crowdedMillis).copy(rolloutPlayouts = 0, blockSimulations = 4)
        else SearchAllowances.forMillis(if (combat) combatMillis else normalMillis).copy(
            targetCandidates = 4,
            refineTargetsBySimulation = combat,
            blockSimulations = 10,
            attackSearchIterations = if (combat) 3 else 2,
            rolloutPlayouts = if (combat) 6 else 4,
        )
        // Duels showed 24 rollouts and 6 attack passes reaching 17 s on one decision without
        // beating Hard, so Brutal stays within about 1.5x Hard's work.
        BRUTAL -> if (crowded) SearchAllowances.forMillis(crowdedMillis).copy(rolloutPlayouts = 1, blockSimulations = 6)
        else SearchAllowances.forMillis(if (combat) combatMillis else normalMillis).copy(
            targetCandidates = 6,
            refineTargetsBySimulation = true,
            blockSimulations = 12,
            attackSearchIterations = if (combat) 4 else 2,
            rolloutPlayouts = if (combat) 9 else 6,
        )
    }

    /** The engine strategy profile this level plays with (its budget policy is filled in later). */
    internal fun baseProfile(): AiProfile = when (this) {
        EASY, NORMAL -> AiProfile.CURRENT
        HARD, BRUTAL -> AiProfile.LIVE
    }

    companion object {
        val DEFAULT = NORMAL
        const val CROWDED_BATTLEFIELD = 96

        /** Stored name back to a level; unknown or missing values are [DEFAULT]. */
        fun fromName(name: String?): AiDifficulty = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT

        /** Slider position (0 = Easy) to a level, clamped. */
        fun fromIndex(index: Int): AiDifficulty = entries[index.coerceIn(0, entries.size - 1)]
    }
}
