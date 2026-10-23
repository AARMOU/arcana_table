package com.arcanatable

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackIdentityRegressionTest {
    private val original = GameCard(
        id = 1, name = "Sample Copy", type = "Creature — Construct",
        color = Color.Gray, isToken = true, power = "1", toughness = "1",
        oracleText = "Vigilance", manaCost = "{1}", manaValue = 1.0,
    )

    @Test fun oneThousandIndistinguishableTokensCanStillStack() {
        val tokens = (1..1000).map { original.copy(id = it) }
        val stacks = stackIdentical(tokens)
        assertEquals(1, stacks.size)
        assertEquals(1000, stacks.single().count)
        assertEquals(1000, stacks.single().cards.map { it.id }.distinct().size)
    }

    @Test fun differingPrintedAndVisualFactsCannotCollapseIntoOneStack() {
        val variants = listOf(
            original.copy(id = 2, type = "Artifact Creature — Construct"),
            original.copy(id = 3, color = Color.Red),
            original.copy(id = 4, oracleText = "Trample"),
            original.copy(id = 5, manaCost = "{2}"),
            original.copy(id = 6, manaValue = 2.0),
            original.copy(id = 7, artCropUrl = "other-art"),
            original.copy(id = 8, importIssue = "NEEDS REVIEW"),
            original.copy(id = 9, counters = mapOf("+1/+1" to 1)),
            original.copy(id = 10, combatRole = "ATTACKING"),
        )
        variants.forEach { variant ->
            val stacks = stackIdentical(listOf(original, variant))
            assertEquals("Different rules/state must stay separate for ${variant.id}", 2, stacks.size)
        }
    }

    @Test fun commandersAndFaceDownCardsRemainIndividuallyAddressable() {
        val a = original.copy(id = 11, isCommander = true)
        val b = original.copy(id = 12, isCommander = true)
        val hiddenA = original.copy(id = 13, isFaceDown = true)
        val hiddenB = original.copy(id = 14, isFaceDown = true)
        val stacks = stackIdentical(listOf(a,b,hiddenA,hiddenB))
        assertEquals(4, stacks.size)
        assertTrue(stacks.all { it.count == 1 })
    }
}
