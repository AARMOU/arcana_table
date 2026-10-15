package com.arcanatable

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the parser against Android's on-device ICU regex implementation. */
@RunWith(AndroidJUnit4::class)
class GameRulesAndroidRuntimeTest {
    @Test
    fun foodEtbAndKeywordTokensParseWithoutCrashing() {
        val food = parseAutomaticResolutionEffects("Create a Food token.")
        assertFalse(food.requiresManualReview)
        assertEquals("Food", food.tokens.single().name)

        val soldier = parseAutomaticResolutionEffects(
            "Create a 1/1 white Soldier creature token with flying, vigilance, and ward {2}."
        )
        assertFalse(soldier.requiresManualReview)
        assertEquals(listOf("Flying", "Vigilance", "Ward"), soldier.tokens.single().keywords)
    }

    @Test
    fun gildedGooseEtbMatchesAndItsFoodEffectParsesOnAndroid() {
        val goose = GameCard(
            id = 79,
            name = "Gilded Goose",
            type = "Creature — Bird",
            color = Color.Black,
            oracleText = "When Gilded Goose enters the battlefield, create a Food token."
        )
        val check = GameEventRulesEngine.evaluate(
            GameEvent(
                kind = GameEventKind.CREATURE_ETB,
                actorIndex = 0,
                subject = goose,
                fromZone = "stack",
                toZone = "battlefield"
            ),
            listOf(TriggerSource(goose, controllerIndex = 0, controllerId = "player-0"))
        ).single()

        assertEquals(TriggerCheckStatus.MATCHED, check.status)
        val effects = parseAutomaticResolutionEffects(check.ability?.effectText)
        assertFalse(effects.requiresManualReview)
        assertEquals("Food", effects.tokens.single().name)
        assertEquals("Artifact Token — Food", effects.tokens.single().type)
    }
}
