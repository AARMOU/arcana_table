package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekSpyder
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class CanoptekSpyderIndependentCertificationTest {
    @Test
    fun fiveGenericManaCastsTheFourFourFlyingSpyderAndItsOwnEntryDoesNotDraw() {
        val d = customScenario(CanoptekSpyder)
        val player = d.player1
        val forest = d.putCardOnTopOfLibrary(player, "Forest")
        val spyder = d.putCardInHand(player, "Canoptek Spyder")
        val handBeforeCast = d.getHandSize(player)

        d.giveColorlessMana(player, 4)
        assertNotNull(d.castSpell(player, spyder).error)
        assertTrue(d.getHand(player).contains(spyder))
        assertEquals(4, d.manaPool(player).colorless)

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, spyder).error)
        assertEquals(handBeforeCast - 1, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Canoptek Spyder").contains(spyder))
        assertTrue(d.projected.hasType(spyder, "ARTIFACT"))
        assertTrue(d.projected.isCreature(spyder))
        assertTrue(d.projected.hasSubtype(spyder, "SPIDER"))
        assertEquals(4, d.projected.getPower(spyder))
        assertEquals(4, d.projected.getToughness(spyder))
        assertTrue(d.projected.hasKeyword(spyder, Keyword.FLYING))
        assertEquals("Its own entry is not another artifact creature entering",
            handBeforeCast - 1, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))
    }

    @Test
    fun flyingPreventsAGroundCreatureFromBlockingAndTheSpyderDealsFourCombatDamage() {
        val d = customScenario(CanoptekSpyder, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val spyder = d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(spyder)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(spyder, Keyword.FLYING))
        assertNull(d.declareAttackers(player, listOf(spyder), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(opponent, mapOf(bear to listOf(spyder))).error)
        assertNull(d.declareBlockers(opponent, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(16, d.getLifeTotal(opponent))
        assertTrue(d.permanentsNamed(player, "Canoptek Spyder").contains(spyder))
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").contains(bear))
    }

    @Test
    fun aControlledNontokenArtifactCreatureEnteringDrawsExactlyOneCardAndItsTokensDoNot() {
        val d = customScenario(CanoptekSpyder, "Myr Battlesphere")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val forest = d.putCardOnTopOfLibrary(player, "Forest")
        val battlesphere = d.putCardInHand(player, "Myr Battlesphere")
        val handBeforeCast = d.getHandSize(player)
        d.giveColorlessMana(player, 7)

        assertNull(d.castSpell(player, battlesphere).error)
        assertEquals(handBeforeCast - 1, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))
        d.resolveAll()

        val permanent = d.permanentsNamed(player, "Myr Battlesphere").single()
        assertTrue(d.projected.hasType(permanent, "ARTIFACT"))
        assertTrue(d.projected.isCreature(permanent))
        assertEquals("The trigger draws after the artifact creature enters, exactly once",
            handBeforeCast, d.getHandSize(player))
        assertTrue(d.getHand(player).contains(forest))

        val tokens = d.getPermanents(player).filter {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        }
        assertEquals(4, tokens.size)
        tokens.forEach {
            assertTrue(d.projected.hasType(it, "ARTIFACT"))
            assertTrue(d.projected.isCreature(it))
        }
    }

    @Test
    fun aControlledVehicleEnteringDrawsOneCardWhileItIsNotACreature() {
        val d = customScenario(CanoptekSpyder, "Smuggler's Copter")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val forest = d.putCardOnTopOfLibrary(player, "Forest")
        val copter = d.putCardInHand(player, "Smuggler's Copter")
        val handBeforeCast = d.getHandSize(player)
        d.giveColorlessMana(player, 2)

        assertNull(d.castSpell(player, copter).error)
        assertEquals(handBeforeCast - 1, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))
        d.resolveAll()

        val vehicle = d.permanentsNamed(player, "Smuggler's Copter").single()
        assertTrue(d.projected.hasSubtype(vehicle, "VEHICLE"))
        assertFalse(d.projected.isCreature(vehicle))
        assertEquals(handBeforeCast, d.getHandSize(player))
        assertTrue(d.getHand(player).contains(forest))
    }

    @Test
    fun aNonartifactCreatureAndAnArtifactThatIsNotACreatureOrVehicleDoNotTrigger() {
        val d = customScenario(CanoptekSpyder, "Grizzly Bears", "Sol Ring")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val forest = d.putCardOnTopOfLibrary(player, "Forest")

        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        val handBeforeBears = d.getHandSize(player)
        assertNull(d.castSpell(player, bears).error)
        d.resolveAll()
        val bearPermanent = d.permanentsNamed(player, "Grizzly Bears").single()
        assertTrue(d.projected.isCreature(bearPermanent))
        assertFalse(d.projected.hasType(bearPermanent, "ARTIFACT"))
        assertEquals(handBeforeBears - 1, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))

        val ring = d.putCardInHand(player, "Sol Ring")
        d.giveColorlessMana(player, 1)
        val handBeforeRing = d.getHandSize(player)
        assertNull(d.castSpell(player, ring).error)
        d.resolveAll()
        val ringPermanent = d.permanentsNamed(player, "Sol Ring").single()
        assertTrue(d.projected.hasType(ringPermanent, "ARTIFACT"))
        assertFalse(d.projected.isCreature(ringPermanent))
        assertFalse(d.projected.hasSubtype(ringPermanent, "VEHICLE"))
        assertEquals(handBeforeRing - 1, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))
    }

    @Test
    fun anArtifactCreatureEnteringUnderAnOpponentsControlDoesNotDrawForTheSpyder() {
        val d = customScenario(CanoptekSpyder, "Ornithopter")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Canoptek Spyder")
        val forest = d.putCardOnTopOfLibrary(player, "Forest")
        val handBefore = d.getHandSize(player)

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val ornithopter = d.putCardInHand(opponent, "Ornithopter")
        assertNull(d.castSpell(opponent, ornithopter).error)
        d.resolveAll()

        val permanent = d.permanentsNamed(opponent, "Ornithopter").single()
        assertTrue(d.projected.hasType(permanent, "ARTIFACT"))
        assertTrue(d.projected.isCreature(permanent))
        assertEquals(handBefore, d.getHandSize(player))
        assertFalse(d.getHand(player).contains(forest))
    }
}
