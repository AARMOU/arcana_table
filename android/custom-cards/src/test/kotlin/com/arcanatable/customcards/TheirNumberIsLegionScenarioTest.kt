package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TheirNumberIsLegion
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import org.junit.Assert.*
import org.junit.Test

class TheirNumberIsLegionScenarioTest {
    private fun game() = customScenario(TheirNumberIsLegion, "Sol Ring", "Ornithopter", "Grizzly Bears", "Counterspell")
    private fun GameTestDriver.pay(x: Int) {
        giveMana(player1, Color.BLACK, 4)
        if (x > 0) giveColorlessMana(player1, x)
    }
    private fun GameTestDriver.cast(id: com.wingedsheep.sdk.model.EntityId, x: Int) =
        submit(CastSpell(player1, id, xValue = x, paymentStrategy = PaymentStrategy.FromPool))

    @Test fun handCastCreatesExactTokensThenCountsAllYourArtifacts() {
        val d = game(); val p = d.player1
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putCreatureOnBattlefield(p, "Ornithopter")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val id = d.putCardInHand(p, TheirNumberIsLegion.name)
        val life = d.getLifeTotal(p)
        d.pay(3)
        assertNull(d.cast(id, 3).error)
        d.resolveAll()
        val tokens = d.permanentsNamed(p, "Necron Warrior")
        assertEquals(3, tokens.size)
        tokens.forEach {
            assertTrue(d.state.getEntity(it)?.has<TokenComponent>() == true)
            assertTrue(d.isTapped(it))
            assertEquals(2, d.projected.getPower(it)); assertEquals(2, d.projected.getToughness(it))
            assertTrue(d.projected.hasType(it, "ARTIFACT")); assertTrue(d.projected.isCreature(it))
            assertTrue(d.projected.hasColor(it, Color.BLACK)); assertEquals(1, d.projected.getColors(it).size)
            assertTrue(d.projected.hasSubtype(it, "Necron")); assertTrue(d.projected.hasSubtype(it, "Warrior"))
        }
        assertEquals(life + 5, d.getLifeTotal(p))
        assertTrue(TheirNumberIsLegion.name in d.zoneNames(p, Zone.EXILE))
    }

    @Test fun graveyardCastOffersXAndResolvesWithChosenX() {
        val d = game(); val p = d.player1
        val id = d.putCardInGraveyard(p, TheirNumberIsLegion.name)
        d.pay(2)
        val offered = d.legalActions(p).single { (it.action as? CastSpell)?.cardId == id }
        assertTrue(offered.hasXCost); assertEquals(2, offered.maxAffordableX)
        val life = d.getLifeTotal(p)
        assertNull(d.cast(id, 2).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Necron Warrior").size)
        assertEquals(life + 2, d.getLifeTotal(p))
        assertTrue(TheirNumberIsLegion.name in d.zoneNames(p, Zone.EXILE))
        assertFalse(d.legalActions(p).any { (it.action as? CastSpell)?.cardId == id })
    }

    @Test fun zeroXStillGainsLifeForExistingArtifacts() {
        val d = game(); val p = d.player1
        d.putPermanentOnBattlefield(p, "Sol Ring")
        val id = d.putCardInGraveyard(p, TheirNumberIsLegion.name)
        val life = d.getLifeTotal(p)
        d.pay(0); assertNull(d.cast(id, 0).error); d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Necron Warrior").isEmpty())
        assertEquals(life + 1, d.getLifeTotal(p))
        assertTrue(TheirNumberIsLegion.name in d.zoneNames(p, Zone.EXILE))
    }

    @Test fun cannotUnderpayOrReplaceTheFourBlackPipsWithColorless() {
        for (black in listOf(3, 4)) {
            val d = game(); val p = d.player1
            val id = d.putCardInGraveyard(p, TheirNumberIsLegion.name)
            d.giveMana(p, Color.BLACK, black); d.giveColorlessMana(p, if (black == 3) 8 else 1)
            assertNotNull(d.cast(id, 2).error)
            assertTrue(TheirNumberIsLegion.name in d.zoneNames(p, Zone.GRAVEYARD))
            assertTrue(d.state.stack.isEmpty())
        }
    }

    @Test fun graveyardPermissionDoesNotGrantOpponentTurnTimingOrOthersCards() {
        val d = game(); val p = d.player1
        val theirs = d.putCardInGraveyard(d.player2, TheirNumberIsLegion.name)
        d.pay(2); assertNotNull(d.cast(theirs, 2).error)
        val mine = d.putCardInGraveyard(p, TheirNumberIsLegion.name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriority(d.player2)
        d.pay(2); assertNotNull(d.cast(mine, 2).error)
    }

    @Test fun counteredGraveyardCastStaysInGraveyardAndCanBeCastAgain() {
        val d = game(); val p = d.player1
        val id = d.putCardInGraveyard(p, TheirNumberIsLegion.name)
        val life = d.getLifeTotal(p)
        d.pay(1); assertNull(d.cast(id, 1).error)
        d.passPriority(p)
        val counter = d.putCardInHand(d.player2, "Counterspell")
        d.giveMana(d.player2, Color.BLUE, 2)
        assertNull(d.castSpellWithTargets(d.player2, counter, listOf(ChosenTarget.Spell(id))).error)
        d.resolveAll()
        assertTrue(TheirNumberIsLegion.name in d.zoneNames(p, Zone.GRAVEYARD))
        assertFalse(TheirNumberIsLegion.name in d.zoneNames(p, Zone.EXILE))
        assertEquals(life, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(p, "Necron Warrior").isEmpty())
        d.pay(1); assertNull(d.cast(id, 1).error); d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Necron Warrior").size)
    }
}
