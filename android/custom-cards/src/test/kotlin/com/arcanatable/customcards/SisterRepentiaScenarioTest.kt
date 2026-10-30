package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SisterRepentia
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SisterRepentiaScenarioTest {
    @Test
    fun martyrdomGainsLifeAndDrawsTwoWhenSisterDies() {
        val d = customScenario(SisterRepentia, "Wrath of God")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Sister Repentia")
        val wrath = d.putCardInHand(player, "Wrath of God")
        val handSizeBeforeCast = d.zoneNames(player, Zone.HAND).size
        d.giveMana(player, Color.WHITE, 2)
        d.giveColorlessMana(player, 2)

        assertNull(d.castSpell(player, wrath).error)
        d.resolveAll()

        d.assertLifeTotal(player, 22)
        assertEquals(handSizeBeforeCast + 1, d.zoneNames(player, Zone.HAND).size)
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Sister Repentia"))
    }

    @Test
    fun martyrdomDoesNotTriggerWhenSisterIsExiled() {
        val d = customScenario(SisterRepentia, "Path to Exile")
        val player = d.player1
        val sister = d.putCreatureOnBattlefield(player, "Sister Repentia")
        val path = d.putCardInHand(player, "Path to Exile")
        d.giveMana(player, Color.WHITE)

        assertNull(d.castSpell(player, path, listOf(sister)).error)
        d.resolveAll()

        d.assertLifeTotal(player, 20)
        assertTrue(d.zoneNames(player, Zone.EXILE).contains("Sister Repentia"))
    }

    @Test
    fun miracleCastsSisterForWhiteAndBlackWhenItIsFirstDrawn() {
        val d = customScenario(SisterRepentia)
        val player = d.player1
        d.putCardOnTopOfLibrary(player, "Sister Repentia")
        d.advanceToMiracleDecision()
        d.giveMana(player, Color.WHITE)
        d.giveMana(player, Color.BLACK)

        d.resolveAll(Choices(yes = true))

        assertTrue(d.permanentsNamed(player, "Sister Repentia").isNotEmpty())
        assertFalse(d.zoneNames(player, Zone.HAND).contains("Sister Repentia"))
    }

    @Test
    fun miracleCanBeDeclinedWhenSisterIsFirstDrawn() {
        val d = customScenario(SisterRepentia)
        val player = d.player1
        d.putCardOnTopOfLibrary(player, "Sister Repentia")
        d.advanceToMiracleDecision()

        assertTrue(d.state.pendingDecision is YesNoDecision)
        d.decide(Choices(yes = false))

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Sister Repentia"))
        assertTrue(d.state.stack.isEmpty())
    }

    @Test
    fun miracleDoesNotApplyWhenSisterIsNotTheFirstCardDrawnThisTurn() {
        val d = customScenario(SisterRepentia, "Divination", "Plains")
        val player = d.player1
        d.putCardOnTopOfLibrary(player, "Sister Repentia")
        d.putCardOnTopOfLibrary(player, "Plains")
        val divination = d.putCardInHand(player, "Divination")
        d.giveMana(player, Color.BLUE)
        d.giveMana(player, Color.WHITE)
        d.giveMana(player, Color.BLACK)
        d.giveColorlessMana(player, 2)

        assertNull(d.castSpell(player, divination).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Sister Repentia"))
        assertTrue(d.permanentsNamed(player, "Sister Repentia").isEmpty())
        assertTrue(d.state.stack.isEmpty())
    }

    private fun GameTestDriver.advanceToMiracleDecision() {
        repeat(200) {
            when {
                state.pendingDecision is YesNoDecision -> return
                state.pendingDecision != null -> decide(Choices())
                state.stack.isNotEmpty() -> return
                state.priorityPlayerId != null -> passPriority(state.priorityPlayerId!!)
                else -> error("Game has no pending decision, stack object, or priority")
            }
        }
        error("Miracle did not trigger during the next draw step")
    }
}
