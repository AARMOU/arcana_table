package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.AiDifficulty
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

/** Per-seat AI difficulty: Normal stays the shipped mobile AI; each level above searches more. */
class AiDifficultyTest {
    @Test
    fun normalIsExactlyThePreDifficultyMobileAi() {
        assertSame(OfflineAiPolicy.profile, OfflineAiPolicy.profileFor(AiDifficulty.NORMAL))
        assertEquals("arcana-mobile-b123", OfflineAiPolicy.profileFor(AiDifficulty.NORMAL).id)
        val d = scenario("Grizzly Bears")
        val budget = OfflineAiPolicy.budgetForDecision(d.state, d.player1)
        assertEquals(250L, budget.millis)
        with(budget.allowances) {
            assertEquals(2, targetCandidates)
            assertFalse(refineTargetsBySimulation)
            assertEquals(4, blockSimulations)
            assertEquals(1, attackSearchIterations)
            assertEquals(0, rolloutPlayouts)
        }
        // The object and the Normal level policy give the same budget.
        val level = OfflineAiPolicy.policyFor(AiDifficulty.NORMAL).budgetForDecision(d.state, d.player1)
        assertEquals(budget.millis, level.millis)
        assertEquals(budget.allowances, level.allowances)
    }

    @Test
    fun eachLevelSearchesAtLeastAsMuchAsTheOneBelow() {
        // Easy searches exactly like Normal (it is weaker by hesitating, not by searching less).
        for (combat in listOf(false, true)) for (crowded in listOf(false, true))
            assertEquals(AiDifficulty.NORMAL.allowances(combat, crowded), AiDifficulty.EASY.allowances(combat, crowded))
        for (combat in listOf(false, true)) {
            listOf(AiDifficulty.NORMAL, AiDifficulty.HARD, AiDifficulty.BRUTAL).zipWithNext().forEach { (lower, higher) ->
                val a = lower.allowances(combat, crowded = false)
                val b = higher.allowances(combat, crowded = false)
                val what = "$lower -> $higher (combat=$combat)"
                assertTrue(what, (if (combat) higher.combatMillis else higher.normalMillis) > (if (combat) lower.combatMillis else lower.normalMillis))
                assertTrue(what, b.targetCandidates >= a.targetCandidates)
                assertTrue(what, b.blockSimulations >= a.blockSimulations)
                assertTrue(what, b.attackSearchIterations >= a.attackSearchIterations)
                assertTrue(what, b.rolloutPlayouts >= a.rolloutPlayouts)
                assertTrue(what, higher.burstMillis > lower.burstMillis)
            }
        }
        assertTrue(AiDifficulty.BRUTAL.allowances(combat = true, crowded = false).refineTargetsBySimulation)
    }

    @Test
    fun hardAndBrutalPlayArgentumsServerStrategy() {
        for (level in listOf(AiDifficulty.HARD, AiDifficulty.BRUTAL)) {
            val profile = OfflineAiPolicy.profileFor(level)
            assertEquals("arcana-mobile-${level.name.lowercase()}", profile.id)
            assertTrue(profile.useMeaningfulFilter)
            assertTrue(profile.useCardIntent)
            assertTrue(profile.holdRemovalForBetterTargets)
            assertTrue(profile.combatTricksWaitForBlocks)
            assertTrue(profile.advisorModules.isNotEmpty())
            assertSame(OfflineAiPolicy.policyFor(level), profile.budgetPolicy)
        }
        assertFalse(OfflineAiPolicy.profileFor(AiDifficulty.EASY).useCardIntent)
        assertSame(OfflineAiPolicy.policyFor(AiDifficulty.EASY), OfflineAiPolicy.profileFor(AiDifficulty.EASY).budgetPolicy)
    }

    @Test
    fun onlyEasyHesitatesAndBrutalStaysWithinHalfAgainHardsWork() {
        assertTrue(AiDifficulty.EASY.hesitateChance > 0.0)
        listOf(AiDifficulty.NORMAL, AiDifficulty.HARD, AiDifficulty.BRUTAL).forEach { assertEquals(0.0, it.hesitateChance, 0.0) }
        for (combat in listOf(false, true)) {
            val hard = AiDifficulty.HARD.allowances(combat, crowded = false)
            val brutal = AiDifficulty.BRUTAL.allowances(combat, crowded = false)
            assertTrue(brutal.rolloutPlayouts * 2 <= hard.rolloutPlayouts * 3)
            assertTrue(brutal.attackSearchIterations * 2 <= hard.attackSearchIterations * 3)
        }
        assertTrue(AiDifficulty.BRUTAL.combatMillis * 2 <= AiDifficulty.HARD.combatMillis * 3)
    }

    @Test
    fun crowdedBoardsStayBoundedAtEveryLevel() {
        for (level in AiDifficulty.entries) {
            assertTrue(level.crowdedMillis <= level.normalMillis)
            assertTrue(level.crowdedMillis <= 600L)
        }
    }

    @Test
    fun storedNamesAndSliderPositionsMapToLevels() {
        assertEquals(AiDifficulty.NORMAL, AiDifficulty.fromName(null))
        assertEquals(AiDifficulty.NORMAL, AiDifficulty.fromName("nonsense"))
        assertEquals(AiDifficulty.BRUTAL, AiDifficulty.fromName("brutal"))
        assertEquals(AiDifficulty.EASY, AiDifficulty.fromIndex(-4))
        assertEquals(AiDifficulty.BRUTAL, AiDifficulty.fromIndex(99))
        AiDifficulty.entries.forEach { assertEquals(it, AiDifficulty.fromIndex(it.ordinal)) }
    }

    @Test
    fun thinkingTimeGrowsWithTheSeatsButNormalTablesKeepTheirBudget() {
        assertEquals(600L, OfflineAiPolicy.burstMillisFor(listOf(AiDifficulty.NORMAL, AiDifficulty.NORMAL, AiDifficulty.NORMAL)))
        assertEquals(600L, OfflineAiPolicy.burstMillisFor(listOf(AiDifficulty.EASY)))
        assertEquals(AiDifficulty.HARD.burstMillis + AiDifficulty.BRUTAL.burstMillis,
            OfflineAiPolicy.burstMillisFor(listOf(AiDifficulty.HARD, AiDifficulty.BRUTAL)))
    }

    @Test
    fun aLevelsBudgetNeverOutlastsTheSharedBurst() {
        val d = scenario("Grizzly Bears")
        OfflineAiPolicy.withThinkingBurst(searchMillis = 40) {
            assertEquals(40L, OfflineAiPolicy.policyFor(AiDifficulty.BRUTAL).budgetForDecision(d.state, d.player1).millis)
        }
        assertEquals(AiDifficulty.BRUTAL.normalMillis, OfflineAiPolicy.policyFor(AiDifficulty.BRUTAL).budgetForDecision(d.state, d.player1).millis)
    }

    @Test(timeout = 120000)
    fun everyLevelMakesALegalPlayInARealEnginePosition() {
        for (level in AiDifficulty.entries) {
            // The AI (player 1) holds Murder with mana up; the opponent has a Shivan Dragon.
            val d = scenario("Shivan Dragon", "Murder")
            d.putCreatureOnBattlefield(d.player2, "Shivan Dragon")
            d.putCardInHand(d.player1, "Murder")
            d.giveMana(d.player1, Color.BLACK, 3)
            val action = OfflineAiPolicy.withThinkingBurst(OfflineAiPolicy.burstMillisFor(listOf(level))) {
                OfflineAiPolicy.think { OfflineAiPolicy.create(d.cardRegistry, d.player1, level).chooseAction(d.state) }
            }
            assertNull("$level chose $action", d.submit(action).error)
        }
    }

    @Test(timeout = 120000)
    fun easyHesitatingStillOnlyMakesLegalPlays() {
        repeat(12) { seed ->
            val d = scenario("Shivan Dragon", "Murder", "Grizzly Bears")
            d.putCreatureOnBattlefield(d.player2, "Shivan Dragon")
            d.putCardInHand(d.player1, "Murder")
            d.putCardInHand(d.player1, "Grizzly Bears")
            d.giveMana(d.player1, Color.BLACK, 3)
            d.giveMana(d.player1, Color.GREEN, 2)
            val bot = OfflineAiPolicy.create(d.cardRegistry, d.player1, AiDifficulty.EASY)
            val action = OfflineAiPolicy.chooseAction(bot, AiDifficulty.EASY, d.state, kotlin.random.Random(seed)) { d.legalActions(d.player1) }
            assertNull("seed $seed chose $action", d.submit(action).error)
        }
    }

    @Test(timeout = 120000)
    fun hardStillSavesTheDragonWithSpellskite() {
        for (level in listOf(AiDifficulty.HARD, AiDifficulty.BRUTAL)) {
            val d = scenario("Spellskite", "Shivan Dragon", "Murder")
            val skite = d.putCreatureOnBattlefield(d.player2, "Spellskite")
            val dragon = d.putCreatureOnBattlefield(d.player2, "Shivan Dragon")
            d.putLandOnBattlefield(d.player2, "Island")
            val murder = d.putCardInHand(d.player1, "Murder")
            d.giveMana(d.player1, Color.BLACK, 3)
            assertNull(d.castSpell(d.player1, murder, listOf(dragon)).error)
            assertNull(d.submit(PassPriority(d.player1)).error)
            val action = OfflineAiPolicy.create(d.cardRegistry, d.player2, level).chooseAction(d.state)
            assertTrue("$level should use Spellskite to save Shivan Dragon, got $action", action is ActivateAbility)
            assertNull(d.submit(action).error)
            d.resolveAll()
            assertTrue(dragon in d.state.getBattlefield())
            assertFalse(skite in d.state.getBattlefield())
        }
    }
}
