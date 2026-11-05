package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Exterminatus
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ExterminatusIndependentCertificationTest {
    @Test
    fun opponentsLoseIndestructibleBeforeAllNonlandPermanentsAreDestroyed() {
        val d = customScenario(
            Exterminatus,
            "Grizzly Bears",
            "Sol Ring",
            "Darksteel Myr",
            "Darksteel Citadel",
            "Forest",
            "Phyrexian Arena",
            "Jace Beleren",
        )
        val p = d.player1
        val ownBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ownRing = d.putPermanentOnBattlefield(p, "Sol Ring")
        val ownMyr = d.putCreatureOnBattlefield(p, "Darksteel Myr")
        val ownForest = d.putLandOnBattlefield(p, "Forest")
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val opposingRing = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val opposingMyr = d.putCreatureOnBattlefield(d.player2, "Darksteel Myr")
        val opposingArena = d.putPermanentOnBattlefield(d.player2, "Phyrexian Arena")
        val opposingWalker = d.putPermanentOnBattlefield(d.player2, "Jace Beleren")
        val opposingCitadel = d.putLandOnBattlefield(d.player2, "Darksteel Citadel")
        val opposingForest = d.putLandOnBattlefield(d.player2, "Forest")
        val spell = d.putCardInHand(p, "Exterminatus")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertEquals(setOf(ownMyr, ownForest), d.getPermanents(p).toSet())
        assertEquals(setOf(opposingCitadel, opposingForest), d.getPermanents(d.player2).toSet())
        assertTrue(d.projected.hasKeyword(ownMyr, Keyword.INDESTRUCTIBLE))
        assertTrue(d.projected.hasKeyword(opposingCitadel, Keyword.INDESTRUCTIBLE))
        assertEquals(
            listOf("Exterminatus", "Grizzly Bears", "Sol Ring"),
            d.zoneNames(p, Zone.GRAVEYARD).sorted(),
        )
        assertEquals(
            listOf("Darksteel Myr", "Grizzly Bears", "Jace Beleren", "Phyrexian Arena", "Sol Ring"),
            d.zoneNames(d.player2, Zone.GRAVEYARD).sorted(),
        )
        assertFalse(d.getPermanents(p).any { it in setOf(ownBear, ownRing) })
        assertFalse(
            d.getPermanents(d.player2)
                .any { it in setOf(opposingBear, opposingRing, opposingMyr, opposingArena, opposingWalker) },
        )
    }

    @Test
    fun lossOfIndestructibleEndsAtTheEndOfTurn() {
        val d = customScenario(Exterminatus, "Darksteel Myr")
        val p = d.player1
        val opponent = d.player2
        val spell = d.putCardInHand(p, "Exterminatus")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        val myr = d.putCreatureOnBattlefield(opponent, "Darksteel Myr")
        assertFalse(d.projected.hasKeyword(myr, Keyword.INDESTRUCTIBLE))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertTrue(d.projected.hasKeyword(myr, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun fourGenericManaCannotPayTheFiveGenericManaCost() {
        val d = customScenario(Exterminatus, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Exterminatus")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(bear))
    }

    @Test
    fun whiteManaIsRequiredToCastExterminatus() {
        val d = customScenario(Exterminatus, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Exterminatus")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(bear))
    }

    @Test
    fun blackManaIsRequiredToCastExterminatus() {
        val d = customScenario(Exterminatus, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Exterminatus")
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(bear))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(Exterminatus, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Exterminatus")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(bear))
    }
}
