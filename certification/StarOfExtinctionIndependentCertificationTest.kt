package com.arcanatable.customcards

import com.arcanatable.customcards.cards.StarOfExtinction
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class StarOfExtinctionIndependentCertificationTest {
    @Test
    fun destroysTargetLandAndDealsTwentyToEachCreatureAndPlaneswalker() {
        val d = customScenario(
            StarOfExtinction,
            "Jace Beleren",
            "Forest",
            "Grizzly Bears",
            "Serra Angel",
            "Sol Ring",
        )
        val p = d.player1
        val walker = d.putCardInHand(p, "Jace Beleren")
        d.giveMana(p, Color.BLUE, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, walker).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(walker))

        val ownCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val targetLand = d.putLandOnBattlefield(d.player2, "Forest")
        val otherLand = d.putLandOnBattlefield(p, "Forest")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.setLifeTotal(d.player2, 100)
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.RED, 2)

        assertNull(d.castSpell(p, spell, listOf(targetLand)).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertFalse(d.getPermanents(d.player1).contains(ownCreature))
        assertFalse(d.getPermanents(d.player2).contains(opposingCreature))
        assertFalse(d.getPermanents(d.player1).contains(walker))
        assertFalse(d.getPermanents(d.player2).contains(targetLand))
        assertTrue(d.getPermanents(d.player1).contains(otherLand))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertEquals(
            mapOf(ownCreature to 20, opposingCreature to 20, walker to 20),
            d.events.filterIsInstance<DamageDealtEvent>()
                .filter { it.sourceId == spell }
                .associate { it.targetId to it.amount },
        )
    }

    @Test
    fun fourGenericManaCannotPayTheFiveGenericManaCost() {
        val d = customScenario(StarOfExtinction, "Forest")
        val p = d.player1
        val land = d.putLandOnBattlefield(d.player2, "Forest")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.RED, 2)

        assertNotNull(d.castSpell(p, spell, listOf(land)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(land))
    }

    @Test
    fun oneRedManaCannotPayTheDoubleRedCost() {
        val d = customScenario(StarOfExtinction, "Forest")
        val p = d.player1
        val land = d.putLandOnBattlefield(d.player2, "Forest")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)

        assertNotNull(d.castSpell(p, spell, listOf(land)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(land))
    }

    @Test
    fun aLandTargetIsRequired() {
        val d = customScenario(StarOfExtinction, "Forest")
        val p = d.player1
        val land = d.putLandOnBattlefield(d.player2, "Forest")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.RED, 2)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(land))
    }

    @Test
    fun aNonlandPermanentCannotBeTheTarget() {
        val d = customScenario(StarOfExtinction, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.RED, 2)

        assertNotNull(d.castSpell(p, spell, listOf(artifact)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(artifact))
    }

    @Test
    fun aPlayerCannotBeTheTarget() {
        val d = customScenario(StarOfExtinction)
        val p = d.player1
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.RED, 2)

        assertNotNull(d.castSpell(p, spell, listOf(d.player2)).error)
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(StarOfExtinction, "Forest")
        val p = d.player1
        val land = d.putLandOnBattlefield(d.player2, "Forest")
        val spell = d.putCardInHand(p, "Star of Extinction")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.RED, 2)

        assertNotNull(d.castSpell(p, spell, listOf(land)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(land))
    }
}
