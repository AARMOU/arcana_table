package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Lictor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class LictorIndependentCertificationTest {
    @Test
    fun threeGenericAndGreenCastLictorAsAThreeThreeTyranid() {
        val d = customScenario(Lictor)
        val player = d.player1
        val lictor = d.putCardInHand(player, "Lictor")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.GREEN)

        assertNull(d.castSpell(player, lictor).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Lictor").contains(lictor))
        assertTrue(d.projected.isCreature(lictor))
        assertTrue(d.projected.hasSubtype(lictor, "TYRANID"))
        assertEquals(3, d.projected.getPower(lictor))
        assertEquals(3, d.projected.getToughness(lictor))
        assertTrue(d.projected.hasColor(lictor, Color.GREEN))
    }

    @Test
    fun flashLictorOnAnOpponentsTurnCreatesTheExactTyranidWarriorToken() {
        val d = customScenario(Lictor, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        val bear = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN)
        d.giveColorlessMana(opponent, 1)
        assertNull(d.castSpell(opponent, bear).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").contains(bear))

        assertNull(d.passPriority(opponent).error)
        val lictor = d.putCardInHand(player, "Lictor")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, lictor).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Lictor").contains(lictor))
        val tokens = d.getPermanents(player).filter {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        }
        assertEquals(1, tokens.size)
        val token = tokens.single()
        assertTrue(d.projected.isCreature(token))
        assertTrue(d.projected.hasColor(token, Color.GREEN))
        assertTrue(d.projected.hasSubtype(token, "TYRANID"))
        assertTrue(d.projected.hasSubtype(token, "WARRIOR"))
        assertEquals(3, d.projected.getPower(token))
        assertEquals(3, d.projected.getToughness(token))
        assertTrue(d.projected.hasKeyword(token, Keyword.TRAMPLE))
    }

    @Test
    fun lictorsOwnEntryDoesNotSatisfyItsOpponentCreatureCondition() {
        val d = customScenario(Lictor)
        val player = d.player1
        val lictor = d.putCardInHand(player, "Lictor")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, lictor).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Lictor").contains(lictor))
        assertTrue(d.getPermanents(player).none {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        })
    }

    @Test
    fun aCreatureEnteringUnderYourControlDoesNotSatisfyTheCondition() {
        val d = customScenario(Lictor, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, bear).error)
        d.resolveAll()

        val lictor = d.putCardInHand(player, "Lictor")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 3)
        assertNull(d.castSpell(player, lictor).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").contains(bear))
        assertTrue(d.permanentsNamed(player, "Lictor").contains(lictor))
        assertTrue(d.getPermanents(player).none {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        })
    }

    @Test
    fun fourColorlessManaCannotPayTheGreenInLictorsCost() {
        val d = customScenario(Lictor)
        val player = d.player1
        val lictor = d.putCardInHand(player, "Lictor")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, lictor).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Lictor"))
        assertTrue(d.permanentsNamed(player, "Lictor").isEmpty())
    }

    @Test
    fun twoGenericAndGreenManaCannotPayTheFourManaCost() {
        val d = customScenario(Lictor)
        val player = d.player1
        val lictor = d.putCardInHand(player, "Lictor")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.GREEN)

        assertNotNull(d.castSpell(player, lictor).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Lictor"))
        assertTrue(d.permanentsNamed(player, "Lictor").isEmpty())
    }
}
