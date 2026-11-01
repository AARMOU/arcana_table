package com.arcanatable.customcards

// Independent scenarios from the Oracle record:
// "Squad {2} ... This creature can't block. Endurant — Pay 3 life: This creature gains indestructible until end of turn."
import com.arcanatable.customcards.cards.ArcoFlagellant
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class ArcoFlagellantIndependentCertificationTest {
    @Test
    fun castingRequiresTwoGenericAndOneBlackAndEntersAsThreeOneHuman() {
        val d = customScenario(ArcoFlagellant)
        val p = d.player1
        val card = d.putCardInHand(p, "Arco-Flagellant")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Arco-Flagellant"))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Arco-Flagellant").contains(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(1, d.projected.getToughness(card))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertTrue(d.projected.hasSubtype(card, "HUMAN"))
    }

    @Test
    fun squadPaysTwoForEachOfTwoCopiesAndCreatesThemWhenArcoEnters() {
        val d = customScenario(ArcoFlagellant)
        val p = d.player1
        val card = d.putCardInHand(p, "Arco-Flagellant")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 6)

        assertNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = 2,
                    paymentStrategy = PaymentStrategy.FromPool,
                ),
            ).error,
        )
        assertTrue(d.permanentsNamed(p, "Arco-Flagellant").isEmpty())
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)

        d.resolveAll()

        val copies = d.permanentsNamed(p, "Arco-Flagellant")
        assertEquals(3, copies.size)
        assertTrue(copies.contains(card))
        copies.forEach { copy ->
            assertEquals(3, d.projected.getPower(copy))
            assertEquals(1, d.projected.getToughness(copy))
            assertTrue(d.projected.hasSubtype(copy, "HUMAN"))
        }
    }

    @Test
    fun squadMayBeDeclinedEvenWithEnoughExtraMana() {
        val d = customScenario(ArcoFlagellant)
        val p = d.player1
        val card = d.putCardInHand(p, "Arco-Flagellant")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 6)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertEquals(listOf(card), d.permanentsNamed(p, "Arco-Flagellant"))
        assertEquals(4, d.manaPool(p).colorless)
    }

    @Test
    fun squadCannotBeDeclaredWhenItsAdditionalCostCannotBePaid() {
        val d = customScenario(ArcoFlagellant)
        val p = d.player1
        val card = d.putCardInHand(p, "Arco-Flagellant")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 5)

        assertNotNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = 2,
                    paymentStrategy = PaymentStrategy.FromPool,
                ),
            ).error,
        )

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Arco-Flagellant"))
        assertTrue(d.permanentsNamed(p, "Arco-Flagellant").isEmpty())
        assertEquals(1, d.manaPool(p).black)
        assertEquals(5, d.manaPool(p).colorless)
    }

    @Test
    fun arcoFlagellantCannotBlockAndTheUnblockedAttackerDealsCombatDamage() {
        val d = customScenario(ArcoFlagellant, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val arco = d.putCreatureOnBattlefield(p, "Arco-Flagellant")
        val attacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)

        assertNull(d.declareAttackers(opponent, listOf(attacker), p).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(p, mapOf(arco to listOf(attacker))).error)
        assertNull(d.declareBlockers(p, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = opponent)

        assertEquals(18, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(p, "Arco-Flagellant").contains(arco))
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").contains(attacker))
    }

    @Test
    fun endurantPaysThreeLifeAndIndestructibleLastsThroughEndOfTurn() {
        val d = customScenario(ArcoFlagellant, "Murder")
        val p = d.player1
        val opponent = d.player2
        val arco = d.putCreatureOnBattlefield(p, "Arco-Flagellant")
        val murder = d.putCardInHand(opponent, "Murder")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.giveMana(opponent, Color.BLACK, 2)
        d.giveColorlessMana(opponent, 1)

        assertNull(d.castSpell(opponent, murder, listOf(arco)).error)
        assertNull(d.passPriority(opponent).error)
        assertNull(d.activate(p, arco).error)
        assertEquals(17, d.getLifeTotal(p))
        assertFalse(d.projected.hasKeyword(arco, Keyword.INDESTRUCTIBLE))
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(arco, Keyword.INDESTRUCTIBLE))
        assertTrue(d.permanentsNamed(p, "Arco-Flagellant").contains(arco))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Murder"))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        assertFalse(d.projected.hasKeyword(arco, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun endurantCannotBeActivatedWithoutThreeLifeToPay() {
        val d = customScenario(ArcoFlagellant)
        val p = d.player1
        val arco = d.putCreatureOnBattlefield(p, "Arco-Flagellant")
        d.setLifeTotal(p, 2)

        assertNotNull(d.activate(p, arco).error)

        assertEquals(2, d.getLifeTotal(p))
        assertFalse(d.projected.hasKeyword(arco, Keyword.INDESTRUCTIBLE))
    }
}
