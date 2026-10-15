package com.arcanatable

import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
