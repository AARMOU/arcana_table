package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.view.ClientStateTransformer
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

/**
 * Real pinned-Argentum regressions for The Lunar Whale's abilities beyond Crew (#108/#51).
 * CardPool loads the shipped Argentum definition, not a mock or a rewritten custom card.
 * The display contract is the engine's per-player ClientStateTransformer, not a raw GameState.
 */
class LunarWhaleTopLibraryScenarioTest {
    private val whale = "The Lunar Whale"

    private fun board() = scenario(whale, "Grizzly Bears", "Frogmite", "Opt", "Divination")

    /** Crew and actually declare Whale attacking; the ability is not enabled by mere crewing. */
    private fun attackedBoard(): GameTestDriver {
        val d = board()
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val vehicle = d.putPermanentOnBattlefield(p, whale)
        d.removeSummoningSickness(vehicle)
        assertNull(d.submit(com.wingedsheep.engine.core.CrewVehicle(p, vehicle, listOf(bear))).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(vehicle), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        return d
    }

    @Test fun ownerSeesOnlyTopCardThroughMaskedProjectionWithoutPriority() {
        val d = board()
        val p = d.player1
        val top = d.putCardOnTopOfLibrary(p, "Frogmite")
        val below = d.putCardOnTopOfLibrary(p, "Forest")
        val transformer = ClientStateTransformer(d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator)

        // The permission is supplied by the engine's LookAtTopOfLibrary static ability.
        d.putPermanentOnBattlefield(p, whale)
        assertNull(d.passPriority(p).error)
        assertEquals("Look at any time must not depend on priority", d.player2, d.priorityPlayer)

        val owner = transformer.transform(d.state, p)
        val ownLibrary = owner.zones.single { it.zoneId.ownerId == p && it.zoneId.zoneType == Zone.LIBRARY }
        assertEquals(listOf(0), ownLibrary.positions)
        assertEquals(listOf(below), ownLibrary.cardIds)
        assertTrue(owner.cards.containsKey(below))
        assertFalse("even the owner may not view the second card", owner.cards.containsKey(top))

        val opponent = transformer.transform(d.state, d.player2)
        assertFalse("the opponent must not receive the private top card id", opponent.cards.containsKey(below))
        assertFalse("the opponent must not receive the private top card id", opponent.cards.containsKey(top))
        val opponentLibrary = opponent.zones.single { it.zoneId.ownerId == p && it.zoneId.zoneType == Zone.LIBRARY }
        assertTrue(opponentLibrary.cardIds.isEmpty())
    }

    @Test fun noWhaleMeansNoPrivateTopAccess() {
        val d = board()
        val top = d.putCardOnTopOfLibrary(d.player1, "Frogmite")
        val owner = ClientStateTransformer(d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator).transform(d.state, d.player1)
        assertFalse(owner.cards.containsKey(top))
    }

    @Test fun neitherCrewNorUnattackedWhaleGivesLandPermission() {
        val d = board()
        val p = d.player1
        d.putPermanentOnBattlefield(p, whale)
        val top = d.putCardOnTopOfLibrary(p, "Forest")
        assertFalse(d.legalActions(p).any { (it.action as? PlayLand)?.cardId == top })
        assertNotNull(d.playLand(p, top).error)
    }

    @Test fun attackingWhaleAllowsLandFromTopWithNormalLandDrop() {
        val d = attackedBoard()
        val p = d.player1
        val first = d.putCardOnTopOfLibrary(p, "Forest")
        assertTrue("post-attack top land must be legal", d.legalActions(p).any { (it.action as? PlayLand)?.cardId == first })
        assertNull(d.playLand(p, first).error)
        assertTrue(d.permanentsNamed(p, "Forest").isNotEmpty())

        val second = d.putCardOnTopOfLibrary(p, "Island")
        assertFalse("one land per turn still applies", d.legalActions(p).any { (it.action as? PlayLand)?.cardId == second })
        assertNotNull(d.playLand(p, second).error)
    }

    @Test fun attackingWhaleAllowsPaidSpellFromTop() {
        val d = attackedBoard()
        val p = d.player1
        val card = d.putCardOnTopOfLibrary(p, "Frogmite")
        d.giveMana(p, Color.BLUE, 4)
        assertTrue("the real legal-action list offers the top card", d.legalActions(p).any { (it.action as? CastSpell)?.cardId == card })
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Frogmite").isNotEmpty())
    }

    @Test fun topSpellMustStillHaveEnoughMana() {
        val d = attackedBoard()
        val p = d.player1
        val card = d.putCardOnTopOfLibrary(p, "Frogmite")
        assertNotNull("library access is not a free-cast permission", d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Frogmite"))
    }

    @Test fun sorceryTimingStillAppliesAfterAttack() {
        val d = attackedBoard()
        val p = d.player1
        val card = d.putCardOnTopOfLibrary(p, "Divination")
        d.giveMana(p, Color.BLUE, 3)
        // The postcombat main phase permits a sorcery with an empty stack.
        assertNull("a sorcery in your postcombat main phase is legal", d.castSpell(p, card).error)
        d.resolveAll()
        val next = d.putCardOnTopOfLibrary(p, "Divination")
        d.giveMana(p, Color.BLUE, 3)
        assertNull(d.passPriority(p).error)
        assertEquals(d.player2, d.priorityPlayer)
        assertFalse("the player without priority has no legal sorcery action",
            d.legalActions(p).any { (it.action as? CastSpell)?.cardId == next })
        assertNotNull("the handler also enforces priority", d.castSpell(p, next).error)
    }

    @Test fun onlyActualTopCardCanBeCast() {
        val d = attackedBoard()
        val p = d.player1
        val buried = d.putCardOnTopOfLibrary(p, "Frogmite")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.giveMana(p, Color.BLUE, 4)
        assertFalse(d.legalActions(p).any { (it.action as? CastSpell)?.cardId == buried })
        assertNotNull(d.castSpell(p, buried).error)
    }
}
