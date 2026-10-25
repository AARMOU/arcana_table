package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginTheIneffable
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

/**
 * #153 / #150: Ugin's {2} reduction applies to colorless spells (including
 * Devoid) but never erases a printed colored mana symbol, never reduces a
 * genuinely colored spell, and disappears when Ugin leaves the battlefield.
 *
 * All payments are real CastSpell actions against the Android-consumer engine;
 * no client-only castability flags or fabricated mana-ability responses.
 */
class UginCostReductionDevoidScenarioTest {
    private fun GameTestDriver.poolColorless() =
        state.getEntity(player1)!!.get<ManaPoolComponent>()!!.colorless

    /**
     * The battlefield setup helper bypasses zone-entry processing and does not
     * initialize planeswalker loyalty. Seed printed loyalty explicitly so a
     * later real CastSpell action cannot correctly remove a zero-loyalty Ugin
     * via state-based actions and masquerade as a legend/controller bug.
     */
    private fun GameTestDriver.seedPrintedLoyalty(id: com.wingedsheep.sdk.model.EntityId) {
        replaceState(state.updateEntity(id) { entity ->
            val current = entity.get<CountersComponent>() ?: CountersComponent()
            entity.with(current.withCounters(CounterType.LOYALTY, 4))
        })
        assertEquals("Ugin must start with four loyalty counters", 4,
            state.getEntity(id)!!.get<CountersComponent>()!!.getCount(CounterType.LOYALTY))
    }

    @Test fun devoidSpellKeepsWhiteRequirementButGetsTwoGenericDiscount() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val probe = card("Ugin Devoid Colored-Cost Probe") {
            manaCost = "{2}{W}"
            colorIdentity = "W"
            typeLine = "Creature — Eldrazi"
            power = 2
            toughness = 2
            keywords(Keyword.DEVOID)
        }
        d.registerCard(probe)
        assertTrue("Devoid is colorless despite the printed white symbol",
            d.cardRegistry.requireCard(probe.name).colors.isEmpty())
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        d.seedPrintedLoyalty(ugin)
        val spell = d.putCardInHand(p, probe.name)
        d.giveColorlessMana(p, 2)

        assertEquals(p, d.priorityPlayer)
        assertNotNull("Ugin cannot pay the printed {W} requirement",
            d.castSpell(p, spell).error)
        assertEquals("A rejected cast must not consume mana", 2, d.poolColorless())

        d.giveMana(p, Color.WHITE)
        assertNull("The {2} generic portion is reduced to zero for Devoid",
            d.castSpell(p, spell).error)
        assertEquals("The two unused colorless mana must remain in the pool",
            2, d.poolColorless())
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))
        assertEquals("Ugin must survive casting with four loyalty", ugin,
            d.findPermanent(p, "Ugin, the Ineffable"))
    }

    @Test fun printedColoredSpellStillRequiresItsTwoGenericMana() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val probe = card("Ugin Colored Generic-Cost Probe") {
            manaCost = "{2}{G}"
            colorIdentity = "G"
            typeLine = "Creature — Beast"
            power = 2
            toughness = 2
        }
        d.registerCard(probe)
        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        d.seedPrintedLoyalty(ugin)
        val spell = d.putCardInHand(p, probe.name)
        d.giveMana(p, Color.GREEN)

        assertEquals(p, d.priorityPlayer)
        assertNotNull("A colored spell must not receive Ugin's {2} reduction",
            d.castSpell(p, spell).error)
        d.giveColorlessMana(p, 2)
        assertNull("Full {2}{G} payment should still cast the colored spell",
            d.castSpell(p, spell).error)
        assertEquals(0, d.poolColorless())
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))
        assertEquals("Ugin must survive casting with four loyalty", ugin,
            d.findPermanent(p, "Ugin, the Ineffable"))
    }

    @Test fun exactTwoGenericDiscountEndsWhenUginLeaves() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val probe = card("Ugin Three-Generic-Cost Probe") {
            manaCost = "{3}"
            typeLine = "Artifact"
        }
        d.registerCard(probe)
        val first = d.putCardInHand(p, probe.name)
        d.giveColorlessMana(p, 1)
        assertNotNull("Without Ugin, one mana cannot pay {3}",
            d.castSpell(p, first).error)

        val ugin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        d.seedPrintedLoyalty(ugin)
        assertNull("With Ugin, {3} becomes {1}, not {0}",
            d.castSpell(p, first).error)
        assertEquals("The remaining {1} is actually paid", 0, d.poolColorless())
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))

        d.moveToGraveyard(ugin)
        assertNull(d.findPermanent(p, "Ugin, the Ineffable"))
        val second = d.putCardInHand(p, probe.name)
        d.giveColorlessMana(p, 1)
        assertEquals(p, d.priorityPlayer)
        assertNotNull("Cost reduction must cease immediately when Ugin leaves",
            d.castSpell(p, second).error)
        assertEquals(1, d.poolColorless())
        d.giveColorlessMana(p, 2)
        assertNull("The unreduced {3} remains payable in full",
            d.castSpell(p, second).error)
        assertEquals(0, d.poolColorless())
    }

    @Test fun opponentsUginCannotDiscountMySpellOrStackWithMyUgin() {
        val d = customScenario(UginTheIneffable)
        val p = d.player1
        val opponent = d.player2
        val probe = card("Ugin Controller-Scope Colorless Probe") {
            manaCost = "{3}"
            typeLine = "Artifact"
        }
        d.registerCard(probe)

        // Two Ugins controlled by different players are legal. Only the Ugin
        // controlled by the player casting the spell may reduce its cost.
        val opposingUgin = d.putPermanentOnBattlefield(opponent, "Ugin, the Ineffable")
        d.seedPrintedLoyalty(opposingUgin)
        assertEquals("Opponent Ugin must exist before any spell is cast",
            opposingUgin, d.findPermanent(opponent, "Ugin, the Ineffable"))
        val spell = d.putCardInHand(p, probe.name)
        d.giveColorlessMana(p, 1)

        assertEquals("Rejection must occur while the caster has priority", p, d.priorityPlayer)
        assertNotNull("Opponent's Ugin must not reduce our {3} spell to {1}",
            d.castSpell(p, spell).error)
        assertEquals("Failed payment cannot consume the one available mana",
            1, d.poolColorless())

        val ownUgin = d.putPermanentOnBattlefield(p, "Ugin, the Ineffable")
        d.seedPrintedLoyalty(ownUgin)
        assertEquals("Our Ugin must exist after setup", ownUgin,
            d.findPermanent(p, "Ugin, the Ineffable"))
        assertEquals("The legend rule is controller-scoped; opponent Ugin must survive setup",
            opposingUgin, d.findPermanent(opponent, "Ugin, the Ineffable"))
        assertNull("Our own Ugin now reduces the same {3} spell to exactly {1}",
            d.castSpell(p, spell).error)
        assertEquals("Opponent's Ugin must not stack an extra reduction",
            0, d.poolColorless())
        assertEquals("Opponent Ugin must survive the cast before resolution",
            opposingUgin, d.findPermanent(opponent, "Ugin, the Ineffable"))
        d.resolveAll()
        assertNotNull(d.findPermanent(p, probe.name))
        assertEquals("An unrelated artifact resolving must not remove opponent Ugin",
            opposingUgin, d.findPermanent(opponent, "Ugin, the Ineffable"))
        assertEquals(1, d.permanentsNamed(opponent, "Ugin, the Ineffable").size)
    }
}
