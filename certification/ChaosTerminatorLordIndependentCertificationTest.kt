package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ChaosTerminatorLord
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ChaosTerminatorLordIndependentCertificationTest {
    @Test
    fun requiresThreeGenericAndOneRedAndEntersAsAThreeThree() {
        val missingGeneric = customScenario(ChaosTerminatorLord)
        val unpaid = missingGeneric.putCardInHand(missingGeneric.player1, "Chaos Terminator Lord")
        missingGeneric.giveColorlessMana(missingGeneric.player1, 2)
        missingGeneric.giveMana(missingGeneric.player1, Color.RED)
        assertNotNull(missingGeneric.castSpell(missingGeneric.player1, unpaid).error)

        missingGeneric.giveColorlessMana(missingGeneric.player1, 1)
        assertNull(missingGeneric.castSpell(missingGeneric.player1, unpaid).error)
        missingGeneric.resolveAll()
        val lord = missingGeneric.findPermanent(missingGeneric.player1, "Chaos Terminator Lord")!!
        assertEquals(3, missingGeneric.projected.getPower(lord))
        assertEquals(3, missingGeneric.projected.getToughness(lord))

        val missingRed = customScenario(ChaosTerminatorLord)
        val card = missingRed.putCardInHand(missingRed.player1, "Chaos Terminator Lord")
        missingRed.giveColorlessMana(missingRed.player1, 3)
        assertNotNull(missingRed.castSpell(missingRed.player1, card).error)
    }

    @Test
    fun atTheBeginningOfYourCombatAnotherCreatureYouControlGainsDoubleStrikeUntilEndOfTurn() {
        val d = customScenario(ChaosTerminatorLord, "Grizzly Bears")
        val lord = d.putCreatureOnBattlefield(d.player1, "Chaos Terminator Lord")
        val ally = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val opponentCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        assertFalse(d.projected.hasKeyword(ally, Keyword.DOUBLE_STRIKE))
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll(Choices(targets = listOf(ally)))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(ally, Keyword.DOUBLE_STRIKE))
        assertFalse("the source is not another creature", d.projected.hasKeyword(lord, Keyword.DOUBLE_STRIKE))
        assertFalse("opponents' creatures are not controlled by the source's controller",
            d.projected.hasKeyword(opponentCreature, Keyword.DOUBLE_STRIKE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertFalse("double strike lasts only until end of turn",
            d.projected.hasKeyword(ally, Keyword.DOUBLE_STRIKE))
    }

    @Test
    fun doesNotGrantDoubleStrikeWhenThereIsNoOtherCreatureYouControl() {
        val d = customScenario(ChaosTerminatorLord, "Grizzly Bears")
        val lord = d.putCreatureOnBattlefield(d.player1, "Chaos Terminator Lord")
        val opponentCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertFalse(d.projected.hasKeyword(lord, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(opponentCreature, Keyword.DOUBLE_STRIKE))
    }

    @Test
    fun doesNotTriggerDuringAnOpponentsCombat() {
        val d = customScenario(ChaosTerminatorLord, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.putCreatureOnBattlefield(d.player1, "Chaos Terminator Lord")
        val ally = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)

        assertFalse(d.projected.hasKeyword(ally, Keyword.DOUBLE_STRIKE))
    }
}
