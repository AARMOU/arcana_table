package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SelectiveObliteration
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SelectiveObliterationIndependentCertificationTest {
    @Test
    fun eachPlayerChoosesAColorAndOnlyColorlessOrMatchingMonocoloredPermanentsRemain() {
        val d = customScenario(
            SelectiveObliteration,
            "Serra Angel",
            "Boros Guildmage",
            "Sol Ring",
            "Phyrexian Arena",
            "Jace Beleren",
            "Goblin Raider",
            "Grizzly Bears",
        )
        val p = d.player1
        val opponent = d.player2
        val ownAngel = d.putPermanentOnBattlefield(p, "Serra Angel")
        val ownGuildmage = d.putPermanentOnBattlefield(p, "Boros Guildmage")
        val ownRing = d.putPermanentOnBattlefield(p, "Sol Ring")
        val ownArena = d.putPermanentOnBattlefield(p, "Phyrexian Arena")
        val ownJace = d.putPermanentOnBattlefield(p, "Jace Beleren")
        val opposingRaider = d.putPermanentOnBattlefield(opponent, "Goblin Raider")
        val opposingGuildmage = d.putPermanentOnBattlefield(opponent, "Boros Guildmage")
        val opposingRing = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val opposingBear = d.putPermanentOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Selective Obliteration")
        d.giveColorlessMana(p, 5)

        assertNull(d.castSpell(p, spell).error)
        d.resolveWithControllerColors()

        assertEquals(setOf(ownAngel, ownRing), d.getPermanents(p).toSet())
        assertEquals(setOf(opposingRaider, opposingRing), d.getPermanents(opponent).toSet())
        assertEquals(
            listOf("Boros Guildmage", "Jace Beleren", "Phyrexian Arena"),
            d.zoneNames(p, Zone.EXILE).sorted(),
        )
        assertEquals(
            listOf("Boros Guildmage", "Grizzly Bears"),
            d.zoneNames(opponent, Zone.EXILE).sorted(),
        )
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Selective Obliteration"))
        assertFalse(d.getPermanents(p).any { it in setOf(ownGuildmage, ownArena, ownJace) })
        assertFalse(d.getPermanents(opponent).any { it in setOf(opposingGuildmage, opposingBear) })
    }

    @Test
    fun requiresTwoColorlessManaAndThreeAdditionalMana() {
        val d = customScenario(SelectiveObliteration)
        val p = d.player1
        val spell = d.putCardInHand(p, "Selective Obliteration")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE, 4)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(4, d.manaPool(p).white)

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, spell).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(1, d.manaPool(p).white)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Selective Obliteration"))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(SelectiveObliteration, "Serra Angel")
        val p = d.player1
        val opponent = d.player2
        val angel = d.putPermanentOnBattlefield(opponent, "Serra Angel")
        val spell = d.putCardInHand(p, "Selective Obliteration")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.giveColorlessMana(p, 5)

        assertNotNull(d.castSpell(p, spell).error)

        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(opponent).contains(angel))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Selective Obliteration"))
    }

    private fun GameTestDriver.resolveWithControllerColors() {
        var remainingSteps = 60
        while (state.pendingDecision != null || state.stack.isNotEmpty()) {
            check(remainingSteps-- > 0) { "Stack did not settle: ${getStackSpellNames()} decision=${state.pendingDecision}" }
            val decision = state.pendingDecision
            if (decision != null) {
                val color = if (decision.playerId == player1) Color.WHITE else Color.RED
                decide(Choices(color = color))
            } else {
                bothPass()
            }
        }
    }
}
