package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BituminousBlast
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class BituminousBlastIndependentCertificationTest {
    private fun GameTestDriver.giveBlastMana(player: EntityId) {
        giveColorlessMana(player, 3)
        giveMana(player, Color.BLACK)
        giveMana(player, Color.RED)
    }

    @Test
    fun cascadeSkipsLandsAndManaValueFiveCardsAndCastsTheFirstLowerNonlandForFree() {
        val d = customScenario(
            BituminousBlast,
            "Forest",
            "Serra Angel",
            "Grizzly Bears",
            "Island",
            "Colossal Dreadmaw",
        )
        val player = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        val spell = d.putCardInHand(player, "Bituminous Blast")
        d.putCardOnTopOfLibrary(player, "Island")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Serra Angel")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveBlastMana(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        assertEquals(2, d.stackSize)
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertEquals(
            mapOf(target to 4),
            d.events.filterIsInstance<DamageDealtEvent>()
                .filter { it.sourceId == spell }
                .associate { it.targetId to it.amount },
        )
        assertTrue(d.getPermanents(d.player2).contains(target))

        assertEquals(0, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).black)
        assertEquals(0, d.manaPool(player).red)
        val library = d.zoneNames(player, Zone.LIBRARY)
        assertEquals(43, library.size)
        assertEquals("Island", library.first())
        assertEquals(40, library.count { it == "Plains" })
        assertEquals(setOf("Forest", "Serra Angel"), library.takeLast(2).toSet())
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
    }

    @Test
    fun decliningTheCascadeCastReturnsTheFoundCardToTheBottom() {
        val d = customScenario(BituminousBlast, "Grizzly Bears", "Colossal Dreadmaw")
        val player = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        val spell = d.putCardInHand(player, "Bituminous Blast")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.giveBlastMana(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isEmpty())
        assertEquals("Grizzly Bears", d.zoneNames(player, Zone.LIBRARY).last())
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
        assertEquals(4, d.events.filterIsInstance<DamageDealtEvent>()
            .single { it.sourceId == spell }.amount)
    }

    @Test
    fun cascadeDoesNotCastALandOrANonlandWithManaValueFiveOrMore() {
        val d = customScenario(
            BituminousBlast,
            "Forest",
            "Serra Angel",
            "Island",
            "Colossal Dreadmaw",
        )
        val player = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        val spell = d.putCardInHand(player, "Bituminous Blast")
        d.putCardOnTopOfLibrary(player, "Island")
        d.putCardOnTopOfLibrary(player, "Serra Angel")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveBlastMana(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Serra Angel").isEmpty())
        assertTrue(d.zoneNames(player, Zone.LIBRARY).containsAll(listOf("Forest", "Serra Angel", "Island")))
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
        assertEquals(4, d.events.filterIsInstance<DamageDealtEvent>()
            .single { it.sourceId == spell }.amount)
    }

    @Test
    fun blackAndRedAndThreeGenericManaAreAllRequiredToCast() {
        val missingBlack = customScenario(BituminousBlast, "Grizzly Bears")
        val blacklessSpell = missingBlack.putCardInHand(missingBlack.player1, "Bituminous Blast")
        val blacklessTarget = missingBlack.putCreatureOnBattlefield(missingBlack.player2, "Grizzly Bears")
        missingBlack.putCardOnTopOfLibrary(missingBlack.player1, "Grizzly Bears")
        missingBlack.giveColorlessMana(missingBlack.player1, 3)
        missingBlack.giveMana(missingBlack.player1, Color.RED)
        assertNotNull(missingBlack.castSpell(missingBlack.player1, blacklessSpell, listOf(blacklessTarget)).error)
        assertTrue(missingBlack.getHand(missingBlack.player1).contains(blacklessSpell))
        assertEquals(0, missingBlack.stackSize)
        assertTrue(missingBlack.zoneNames(missingBlack.player1, Zone.LIBRARY).contains("Grizzly Bears"))

        val missingRed = customScenario(BituminousBlast, "Grizzly Bears")
        val redlessSpell = missingRed.putCardInHand(missingRed.player1, "Bituminous Blast")
        val redlessTarget = missingRed.putCreatureOnBattlefield(missingRed.player2, "Grizzly Bears")
        missingRed.putCardOnTopOfLibrary(missingRed.player1, "Grizzly Bears")
        missingRed.giveColorlessMana(missingRed.player1, 3)
        missingRed.giveMana(missingRed.player1, Color.BLACK)
        assertNotNull(missingRed.castSpell(missingRed.player1, redlessSpell, listOf(redlessTarget)).error)
        assertTrue(missingRed.getHand(missingRed.player1).contains(redlessSpell))
        assertEquals(0, missingRed.stackSize)
        assertTrue(missingRed.zoneNames(missingRed.player1, Zone.LIBRARY).contains("Grizzly Bears"))

        val missingGeneric = customScenario(BituminousBlast, "Grizzly Bears")
        val genericlessSpell = missingGeneric.putCardInHand(missingGeneric.player1, "Bituminous Blast")
        val genericlessTarget = missingGeneric.putCreatureOnBattlefield(missingGeneric.player2, "Grizzly Bears")
        missingGeneric.putCardOnTopOfLibrary(missingGeneric.player1, "Grizzly Bears")
        missingGeneric.giveColorlessMana(missingGeneric.player1, 2)
        missingGeneric.giveMana(missingGeneric.player1, Color.BLACK)
        missingGeneric.giveMana(missingGeneric.player1, Color.RED)
        assertNotNull(missingGeneric.castSpell(missingGeneric.player1, genericlessSpell, listOf(genericlessTarget)).error)
        assertTrue(missingGeneric.getHand(missingGeneric.player1).contains(genericlessSpell))
        assertEquals(0, missingGeneric.stackSize)
        assertTrue(missingGeneric.zoneNames(missingGeneric.player1, Zone.LIBRARY).contains("Grizzly Bears"))
    }

    @Test
    fun aCreatureTargetIsRequired() {
        val noTarget = customScenario(BituminousBlast)
        val player = noTarget.player1
        val spell = noTarget.putCardInHand(player, "Bituminous Blast")
        noTarget.giveBlastMana(player)

        assertNotNull(noTarget.castSpell(player, spell).error)
        assertTrue(noTarget.getHand(player).contains(spell))
        assertEquals(0, noTarget.stackSize)

        val playerTarget = customScenario(BituminousBlast)
        val spellWithPlayerTarget = playerTarget.putCardInHand(playerTarget.player1, "Bituminous Blast")
        playerTarget.giveBlastMana(playerTarget.player1)

        assertNotNull(playerTarget.castSpell(playerTarget.player1, spellWithPlayerTarget, listOf(playerTarget.player2)).error)
        assertTrue(playerTarget.getHand(playerTarget.player1).contains(spellWithPlayerTarget))
        assertEquals(0, playerTarget.stackSize)
    }

    @Test
    fun anInstantCanBeCastDuringTheOpponentsTurnAndCascadeCastsACreatureThen() {
        val d = customScenario(BituminousBlast, "Grizzly Bears", "Colossal Dreadmaw")
        val player = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Colossal Dreadmaw")
        val spell = d.putCardInHand(player, "Bituminous Blast")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        d.giveBlastMana(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertEquals(4, d.events.filterIsInstance<DamageDealtEvent>()
            .single { it.sourceId == spell }.amount)
        assertTrue(d.getPermanents(opponent).contains(target))
    }
}
