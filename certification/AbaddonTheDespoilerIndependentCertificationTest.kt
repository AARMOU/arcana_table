package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AbaddonTheDespoiler
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AbaddonTheDespoilerIndependentCertificationTest {
    @Test
    fun requiresTwoGenericBlueBlackAndRedAndEntersAsAFiveFiveWithTrample() {
        val d = customScenario(AbaddonTheDespoiler)
        val player = d.player1
        val abaddon = d.putCardInHand(player, "Abaddon the Despoiler")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLUE)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.castSpell(player, abaddon).error)
        assertTrue(d.getHand(player).contains(abaddon))

        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, abaddon).error)
        d.resolveAll()

        val permanent = d.findPermanent(player, "Abaddon the Despoiler")!!
        assertEquals(5, d.projected.getPower(permanent))
        assertEquals(5, d.projected.getToughness(permanent))
        assertTrue(d.projected.hasKeyword(permanent, Keyword.TRAMPLE))
    }

    @Test
    fun trampleAssignsExcessCombatDamageToTheOpponent() {
        val d = customScenario(AbaddonTheDespoiler, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val abaddon = d.putCreatureOnBattlefield(player, "Abaddon the Despoiler")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(abaddon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(abaddon), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(abaddon))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(17, d.getLifeTotal(opponent))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Grizzly Bears"))
    }

    @Test
    fun aHandSpellWithManaValueEqualToLifeLostGetsCascade() {
        val d = customScenario(AbaddonTheDespoiler, "Shock", "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Abaddon the Despoiler")
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(opponent)).error)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(opponent))

        d.putCardOnTopOfLibrary(player, "Llanowar Elves")
        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, bears).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(yes = true, pick = listOf("Llanowar Elves")))

        assertTrue(d.permanentsNamed(player, "Llanowar Elves").isNotEmpty())
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
    }

    @Test
    fun noOpponentLifeLossThisTurnMeansNoCascade() {
        val d = customScenario(AbaddonTheDespoiler, "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Abaddon the Despoiler")
        d.putCardOnTopOfLibrary(player, "Llanowar Elves")
        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, bears).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertTrue(d.permanentsNamed(player, "Llanowar Elves").isEmpty())
        assertTrue(d.zoneNames(player, Zone.LIBRARY).contains("Llanowar Elves"))
    }

    @Test
    fun aHandSpellAboveTheLifeLossManaValueDoesNotGetCascade() {
        val d = customScenario(AbaddonTheDespoiler, "Shock", "Divination", "Llanowar Elves")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Abaddon the Despoiler")
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(d.player2)).error)
        d.resolveAll()

        d.putCardOnTopOfLibrary(player, "Llanowar Elves")
        val divination = d.putCardInHand(player, "Divination")
        d.giveMana(player, Color.BLUE)
        d.giveColorlessMana(player, 2)
        assertNull(d.castSpell(player, divination).error)

        assertEquals(1, d.stackSize)
    }

    @Test
    fun aSpellCastFromTheGraveyardDoesNotGetCascade() {
        val d = customScenario(AbaddonTheDespoiler, "Shock", "Think Twice", "Llanowar Elves")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Abaddon the Despoiler")
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(opponent)).error)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(opponent))

        val thinkTwice = d.putCardInGraveyard(player, "Think Twice")
        d.giveMana(player, Color.BLUE)
        d.giveColorlessMana(player, 2)
        assertNull(d.castSpell(player, thinkTwice).error)
        assertEquals(1, d.stackSize)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Think Twice"))
    }

    @Test
    fun aHandSpellCastDuringAnOpponentsTurnDoesNotGetCascade() {
        val d = customScenario(AbaddonTheDespoiler, "Sign in Blood", "Shock", "Llanowar Elves")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Abaddon the Despoiler")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val signInBlood = d.putCardInHand(opponent, "Sign in Blood")
        d.giveMana(opponent, Color.BLACK)
        d.giveMana(opponent, Color.BLACK)
        assertNull(d.castSpell(opponent, signInBlood, listOf(opponent)).error)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(opponent))
        assertNull(d.passPriority(opponent).error)

        d.putCardOnTopOfLibrary(player, "Llanowar Elves")
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(opponent)).error)

        assertEquals(1, d.stackSize)
    }
}
