package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShrineOfTheForsakenGods
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ShrineOfTheForsakenGodsScenarioTest {
    @Test
    fun sevenLandsUnlockTheDoubleColorlessAbilityAndItCanPayForColorlessSpell() {
        val d = customScenario(ShrineOfTheForsakenGods, "Ulamog's Crusher")
        val p = d.player1
        val shrine = d.putPermanentOnBattlefield(p, "Shrine of the Forsaken Gods")
        repeat(6) { d.putPermanentOnBattlefield(p, "Plains") }
        assertNull(d.activate(p, shrine, index = 1).error)
        d.resolveAll()
        d.giveColorlessMana(p, 6)
        val card = d.putCardInHand(p, "Ulamog's Crusher")
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Ulamog's Crusher").size)
    }

    @Test
    fun fewerThanSevenLandsCannotActivateDoubleColorlessAbility() {
        val d = customScenario(ShrineOfTheForsakenGods)
        val p = d.player1
        val shrine = d.putPermanentOnBattlefield(p, "Shrine of the Forsaken Gods")
        repeat(5) { d.putPermanentOnBattlefield(p, "Plains") }
        assertNotNull(d.activate(p, shrine, index = 1).error)
        assertNull(d.activate(p, shrine, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
    }

    private fun shrine(d: GameTestDriver): EntityId {
        val shrine = d.putPermanentOnBattlefield(d.player1, "Shrine of the Forsaken Gods")
        repeat(6) { d.putPermanentOnBattlefield(d.player1, "Plains") }
        assertNull(d.activate(d.player1, shrine, index = 1).error)
        assertTrue(d.state.stack.isEmpty())
        assertEquals(2, d.manaPool(d.player1).restrictedMana.size)
        return shrine
    }

    @Test fun coloredSpellCannotSpendRestrictedManaEvenForItsGenericPart() {
        val d = customScenario(ShrineOfTheForsakenGods, "Grizzly Bears")
        shrine(d)
        d.giveMana(d.player1, Color.GREEN)
        val bear = d.putCardInHand(d.player1, "Grizzly Bears")
        val before = d.manaPool(d.player1)
        assertNotNull(d.castSpell(d.player1, bear).error)
        assertEquals(before, d.manaPool(d.player1))
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bear).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Grizzly Bears"))
        assertEquals(2, d.manaPool(d.player1).restrictedMana.size)
    }

    @Test fun devoidSpellCanSpendRestrictedManaDespiteItsPrintedColoredManaCost() {
        val d = customScenario(ShrineOfTheForsakenGods, "Eldrazi Displacer")
        shrine(d)
        d.giveMana(d.player1, Color.WHITE)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Eldrazi Displacer")).error)
        assertTrue(d.manaPool(d.player1).restrictedMana.isEmpty())
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Eldrazi Displacer"))
    }

    @Test fun restrictedManaCannotPayForAnAbilityOfAColorlessSource() {
        val d = customScenario(ShrineOfTheForsakenGods, "Eldrazi Displacer", "Grizzly Bears")
        shrine(d)
        // Prevent the engine auto-tapper from funding the ability with unrelated Plains.
        d.permanentsNamed(d.player1, "Plains").forEach(d::tapPermanent)
        val source = d.putCreatureOnBattlefield(d.player1, "Eldrazi Displacer")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val ability = d.cardRegistry.requireCard("Eldrazi Displacer").script.activatedAbilities.single()
        val action = ActivateAbility(d.player1, source, ability.id,
            targets = listOf(ChosenTarget.Permanent(target)), paymentStrategy = PaymentStrategy.FromPool)
        d.giveColorlessMana(d.player1, 1)
        val before = d.manaPool(d.player1)
        val attempt = d.submit(action)
        assertNotNull("Outcome=${attempt.outcome}; pending=${d.pendingDecision}; pool=${d.manaPool(d.player1)}", attempt.error)
        assertEquals(before, d.manaPool(d.player1))
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.submit(action).error)
        assertEquals(2, d.manaPool(d.player1).restrictedMana.size)
        assertEquals(0, d.manaPool(d.player1).colorless)
        d.resolveAll()
        assertTrue(d.isTapped(d.findPermanent(d.player2, "Grizzly Bears")!!))
    }

}
