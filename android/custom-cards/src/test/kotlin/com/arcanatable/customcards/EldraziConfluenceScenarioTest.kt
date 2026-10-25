package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziConfluence
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class EldraziConfluenceScenarioTest {
    private fun setup(): GameTestDriver = customScenario(EldraziConfluence, "Grizzly Bears", "Craw Wurm", "Hill Giant", "Sol Ring")

    private fun GameTestDriver.cast(modes: List<Int>, targets: List<List<EntityId>>) =
        run {
            val c = putCardInHand(player1, "Eldrazi Confluence")
            giveColorlessMana(player1, 4)
            val ordered = targets.map { it.map { id -> ChosenTarget.Permanent(id) } }
            submit(CastSpell(
                playerId = player1, cardId = c, paymentStrategy = PaymentStrategy.FromPool,
                targets = ordered.flatten(), chosenModes = modes, modeTargetsOrdered = ordered,
            ))
        }

    private fun GameTestDriver.scions(p: EntityId) = getCreatures(p).filter { projected.hasSubtype(it, "Scion") }

    @Test fun oneOfEachMode() {
        val d = setup()
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val ring = d.findPermanent(d.player2, "Sol Ring")!!
        assertFalse(d.isTapped(ring))
        assertNull(d.cast(listOf(0, 1, 2), listOf(listOf(wurm), listOf(ring), emptyList())).error)
        d.resolveAll()
        assertEquals(9, d.projected.getPower(wurm)); assertEquals(1, d.projected.getToughness(wurm))
        val back = d.findPermanent(d.player2, "Sol Ring")
        assertNotNull("blinked permanent returns under its owner's control", back)
        assertTrue("returns tapped", d.isTapped(back!!))
        assertEquals(1, d.scions(d.player1).size)
    }

    @Test fun sameModeThreeTimesMakesThreeScionsThatSacrificeForColorless() {
        val d = setup()
        assertNull(d.cast(listOf(2, 2, 2), listOf(emptyList(), emptyList(), emptyList())).error)
        d.resolveAll()
        val scions = d.scions(d.player1)
        assertEquals(3, scions.size)
        scions.forEach { assertTrue(d.state.projectedState.getColors(it).isEmpty()) }
        val sac = d.abilityActions(d.player1, scions.first()).first().action as com.wingedsheep.engine.core.ActivateAbility
        assertNull(d.submit(sac).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(d.player1).colorless)
        assertEquals(2, d.scions(d.player1).size)
    }

    @Test fun repeatedMinusThreeKillsAndOwnCreatureBlinksBackTapped() {
        val d = setup()
        val giant = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        assertNull(d.cast(listOf(0, 0, 1), listOf(listOf(giant), listOf(giant), listOf(bear))).error)
        d.resolveAll()
        assertTrue(d.getCreatures(d.player2).none { d.getCardName(it) == "Hill Giant" })
        val back = d.findPermanent(d.player1, "Grizzly Bears")
        assertNotNull(back); assertTrue(d.isTapped(back!!))
    }

    @Test fun blinkCannotTargetALand() {
        val d = setup()
        val land = d.putLandOnBattlefield(d.player2, "Plains")
        assertNotNull(d.cast(listOf(1, 2, 2), listOf(listOf(land), emptyList(), emptyList())).error)
    }

    @Test fun minusModeCannotTargetANoncreature() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val ring = d.findPermanent(d.player2, "Sol Ring")!!
        assertNotNull(d.cast(listOf(0, 2, 2), listOf(listOf(ring), emptyList(), emptyList())).error)
    }
}
