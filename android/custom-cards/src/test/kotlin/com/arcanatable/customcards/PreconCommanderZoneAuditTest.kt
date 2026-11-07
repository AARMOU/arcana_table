package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Commander-zone audit for the six Warhammer 40K / Eldrazi precon face commanders that are in the
 * card pool today (Inquisitor Greyfax, Szarekh). The other four (The Swarmlord, Abaddon the Despoiler,
 * Zhulodok, Ulalek) have no definition yet and are tracked in #81.
 *
 * Each commander must start in the command zone, be castable from it, go back to the command zone
 * when its owner chooses that after it dies, and cost {2} more for each previous cast (CR 903.8).
 */
class PreconCommanderZoneAuditTest {

    private data class Cmdr(val name: String, val generic: Int, val colored: List<Color>)

    private val commanders = listOf(
        Cmdr("Inquisitor Greyfax", 1, listOf(Color.WHITE, Color.BLUE, Color.BLACK)),
        Cmdr("Szarekh, the Silent King", 1, listOf(Color.BLACK, Color.BLACK, Color.BLACK)),
    )

    private fun GameTestDriver.pay(player: EntityId, c: Cmdr, extraGeneric: Int = 0) {
        if (c.generic + extraGeneric > 0) giveColorlessMana(player, c.generic + extraGeneric)
        c.colored.forEach { giveMana(player, it) }
    }

    private fun GameTestDriver.commandZone(player: EntityId) = state.getZone(player, Zone.COMMAND)

    private fun GameTestDriver.canCastFromCommandZone(player: EntityId, card: EntityId) =
        legalActions(player).any { (it.action as? CastSpell)?.cardId == card }

    /** Kills the commander and answers the owner's command-zone choice with "yes". */
    private fun GameTestDriver.killAndReturn(player: EntityId, commander: EntityId) {
        moveToGraveyard(commander)
        var guard = 0
        while (guard++ < 10) {
            val decision = pendingDecision
            if (decision is YesNoDecision) {
                assertEquals("The commander's owner makes the command-zone choice", player, decision.playerId)
                submitYesNo(player, true)
                break
            }
            if (decision != null) autoResolveDecision() else passPriority(state.priorityPlayerId ?: break)
        }
    }

    private fun audit(c: Cmdr) {
        val d = CardPool.driver()
        val seats = d.initMultiplayer(
            decks = List(2) { Deck.of("Plains" to 40) },
            format = Format.Commander(),
            commanders = listOf(c.name, commanders.first { it != c }.name),
        )
        val owner = seats[0]
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val inZone = d.commandZone(owner).singleOrNull { d.getCardName(it) == c.name }
        assertNotNull("${c.name} starts in the command zone (zone=${d.zoneNames(owner, Zone.COMMAND)})", inZone)
        assertEquals(40, d.state.getZone(owner, Zone.LIBRARY).size + d.getHand(owner).size)
        assertEquals("Commander format starting life", 40, d.getLifeTotal(owner))

        // First cast: printed cost.
        d.pay(owner, c)
        assertTrue("${c.name} is castable from the command zone", d.canCastFromCommandZone(owner, inZone!!))
        assertNull("${c.name} first cast", d.castSpell(owner, inZone).error)
        d.resolveAll()
        val onBattlefield = d.findPermanent(owner, c.name)
        assertNotNull("${c.name} resolves onto the battlefield", onBattlefield)

        // Dies, owner returns it to the command zone.
        d.killAndReturn(owner, onBattlefield!!)
        val back = d.commandZone(owner).singleOrNull { d.getCardName(it) == c.name }
        assertNotNull("${c.name} returns to the command zone (graveyard=${d.zoneNames(owner, Zone.GRAVEYARD)})", back)
        assertFalse(d.zoneNames(owner, Zone.GRAVEYARD).contains(c.name))

        // Second cast: printed cost is no longer enough, printed cost + {2} is.
        if (d.state.step != Step.PRECOMBAT_MAIN && d.state.step != Step.POSTCOMBAT_MAIN) d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.pay(owner, c)
        assertFalse("${c.name} second cast must not be legal without commander tax", d.canCastFromCommandZone(owner, back!!))
        d.giveColorlessMana(owner, 2)
        assertTrue("${c.name} second cast is legal with {2} tax", d.canCastFromCommandZone(owner, back))
        assertNull("${c.name} second cast with tax", d.castSpell(owner, back).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(owner, c.name))
    }

    @Test fun inquisitorGreyfaxCommanderZoneCastReturnAndTax() = audit(commanders[0])

    @Test fun szarekhCommanderZoneCastReturnAndTax() = audit(commanders[1])
}
