package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekSpyder
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class CanoptekSpyderIndependentCertificationTest {
    @Test
    fun castingRequiresFiveGenericManaAndEntersAsAFourFourFlyingArtifactCreature() {
        val d = customScenario(CanoptekSpyder)
        val player = d.player1
        val spyder = d.putCardInHand(player, "Canoptek Spyder")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, spyder).error)
        assertTrue(d.getHand(player).contains(spyder))

        d.giveColorlessMana(player, 1)
        val handBeforeCast = d.getHandSize(player)
        assertNull(d.castSpell(player, spyder).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(spyder))
        assertEquals(4, d.projected.getPower(spyder))
        assertEquals(4, d.projected.getToughness(spyder))
        assertTrue(d.projected.hasType(spyder, "ARTIFACT"))
        assertTrue(d.projected.hasType(spyder, "CREATURE"))
        assertTrue(d.projected.hasSubtype(spyder, "SPIDER"))
        assertTrue(d.projected.hasKeyword(spyder, Keyword.FLYING))
        assertEquals("Entering without another Spyder does not draw a card", handBeforeCast - 1,
            d.getHandSize(player))
    }

    @Test
    fun flyingSpyderCannotBeBlockedByAGroundCreature() {
        val d = customScenario(CanoptekSpyder, "Grizzly Bears")
        val player = d.player1
        val spyder = d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(spyder)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(spyder), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(spyder))).error)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(16, d.getLifeTotal(d.player2))
        assertTrue(d.getPermanents(player).contains(spyder))
        assertTrue(d.getPermanents(d.player2).contains(blocker))
    }

    @Test
    fun anotherControlledNontokenArtifactCreatureEnteringDrawsExactlyOneCard() {
        val d = customScenario(CanoptekSpyder, "Ornithopter")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val ornithopter = d.putCardInHand(player, "Ornithopter")
        val handBeforeCast = d.getHandSize(player)

        assertNull(d.castSpell(player, ornithopter).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Ornithopter").contains(ornithopter))
        assertEquals("Casting Ornithopter costs one card and the trigger draws one",
            handBeforeCast, d.getHandSize(player))
    }

    @Test
    fun aSecondSpyderTriggersTheFirstButDoesNotTriggerItsOwnAbility() {
        val d = customScenario(CanoptekSpyder)
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val secondSpyder = d.putCardInHand(player, "Canoptek Spyder")
        d.giveColorlessMana(player, 5)
        val handBeforeCast = d.getHandSize(player)

        assertNull(d.castSpell(player, secondSpyder).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(secondSpyder))
        assertEquals("Only the Spyder already on the battlefield sees another creature enter",
            handBeforeCast, d.getHandSize(player))
    }

    @Test
    fun aControlledVehicleTriggersEvenWhenItIsNotACreature() {
        val d = customScenario(CanoptekSpyder, "Smuggler's Copter")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val copter = d.putCardInHand(player, "Smuggler's Copter")
        d.giveColorlessMana(player, 2)
        val handBeforeCast = d.getHandSize(player)

        assertNull(d.castSpell(player, copter).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(copter))
        assertTrue(d.projected.hasType(copter, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(copter, "VEHICLE"))
        assertFalse(d.projected.isCreature(copter))
        assertEquals("Casting the Vehicle costs one card and the trigger draws one",
            handBeforeCast, d.getHandSize(player))
    }

    @Test
    fun aControlledNontokenNonartifactCreatureDoesNotTrigger() {
        val d = customScenario(CanoptekSpyder, "Grizzly Bears")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        val handBeforeCast = d.getHandSize(player)

        assertNull(d.castSpell(player, bears).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(bears))
        assertEquals("A nonartifact creature does not trigger Fabricator Claw Array",
            handBeforeCast - 1, d.getHandSize(player))
    }

    @Test
    fun anOpponentsNontokenArtifactCreatureDoesNotTrigger() {
        val d = customScenario(CanoptekSpyder, "Ornithopter")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val handBeforeCast = d.getHandSize(player)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val ornithopter = d.putCardInHand(opponent, "Ornithopter")

        assertNull(d.castSpell(opponent, ornithopter).error)
        d.resolveAll()

        assertTrue(d.getPermanents(opponent).contains(ornithopter))
        assertEquals("Only artifact creatures and Vehicles you control trigger the ability",
            handBeforeCast, d.getHandSize(player))
    }

    @Test
    fun controlledArtifactCreatureTokensDoNotTrigger() {
        val d = customScenario(CanoptekSpyder, "Servo Exhibition")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val exhibition = d.putCardInHand(player, "Servo Exhibition")
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 1)
        val handBeforeCast = d.getHandSize(player)

        assertNull(d.castSpell(player, exhibition).error)
        d.resolveAll()

        val servos = d.permanentsNamed(player, "Servo")
        assertEquals(2, servos.size)
        servos.forEach { servo ->
            assertTrue(d.projected.hasType(servo, "ARTIFACT"))
            assertTrue(d.projected.hasType(servo, "CREATURE"))
        }
        assertEquals("Artifact creature tokens are excluded from the trigger",
            handBeforeCast - 1, d.getHandSize(player))
    }
}
