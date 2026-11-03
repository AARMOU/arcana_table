package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NecronMonolith
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Necron Monolith (40K #161): Flying, indestructible; Eternity Gate — whenever it attacks, mill three,
 * then one 2/2 black Necron Warrior artifact creature token per creature card milled this way; Crew 4.
 * No official rulings are published (Scryfall/Gatherer, checked 2026-11-03): CR 701.17a (mill),
 * CR 702.122a (crew: total power 4 or more), CR 702.12b (indestructible).
 */
class NecronMonolithScenarioTest {

    private val monolith = "Necron Monolith"

    private fun game() = customScenario(NecronMonolith, "Grizzly Bears", "Hill Giant", "Shatter")

    private fun GameTestDriver.crew(player: EntityId, vehicle: EntityId, crew: List<EntityId>) {
        assertNull(submit(CrewVehicle(player, vehicle, crew)).error)
        resolveAll()
    }

    /** A crewed Monolith attacking player 2, with its Eternity Gate trigger resolved. */
    private fun GameTestDriver.crewAndAttack(p: EntityId): EntityId {
        val mono = putPermanentOnBattlefield(p, monolith)
        removeSummoningSickness(mono)
        crew(p, mono, listOf(putCreatureOnBattlefield(p, "Grizzly Bears"), putCreatureOnBattlefield(p, "Grizzly Bears")))
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(p, listOf(mono), player2).error)
        resolveAll()
        return mono
    }

    private fun GameTestDriver.warriors(p: EntityId) = permanentsNamed(p, "Necron Warrior")

    @Test
    fun crewFourNeedsTotalPowerFour() {
        val d = game()
        val p = d.player1
        val mono = d.putPermanentOnBattlefield(p, monolith)
        val giant = d.putCreatureOnBattlefield(p, "Hill Giant")
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val bears2 = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertFalse(d.projected.isCreature(mono))

        assertNotNull("total power 3", d.submit(CrewVehicle(p, mono, listOf(giant))).error)
        assertFalse(d.isTapped(giant))
        assertFalse(d.projected.isCreature(mono))

        d.crew(p, mono, listOf(bears, bears2))
        assertTrue(d.isTapped(bears) && d.isTapped(bears2))
        assertTrue(d.projected.isCreature(mono))
        assertTrue(d.projected.hasType(mono, "ARTIFACT"))
        assertEquals(7, d.projected.getPower(mono))
        assertEquals(7, d.projected.getToughness(mono))
    }

    @Test
    fun cannotAttackUncrewed() {
        val d = game()
        val p = d.player1
        val mono = d.putPermanentOnBattlefield(p, monolith)
        d.removeSummoningSickness(mono)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(d.declareAttackers(p, listOf(mono), d.player2).error)
        assertTrue(d.warriors(p).isEmpty())
    }

    @Test
    fun attackingMillsThreeAndMakesATokenPerCreatureCardMilled() {
        val d = game()
        val p = d.player1
        d.putCardInGraveyard(p, "Hill Giant") // already there: not milled this way, doesn't count
        d.putCardOnTopOfLibrary(p, "Plains")
        d.putCardOnTopOfLibrary(p, "Hill Giant")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val librarySize = d.state.getZone(p, Zone.LIBRARY).size

        d.crewAndAttack(p)

        assertEquals(librarySize - 3, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(listOf("Hill Giant", "Grizzly Bears", "Hill Giant", "Plains").sorted(), d.zoneNames(p, Zone.GRAVEYARD).sorted())
        val tokens = d.warriors(p)
        assertEquals("two creature cards milled", 2, tokens.size)
        for (t in tokens) {
            assertEquals(2, d.projected.getPower(t))
            assertEquals(2, d.projected.getToughness(t))
            assertEquals("black only", listOf(Color.BLACK), Color.values().filter { d.projected.hasColor(t, it) })
            assertTrue(d.projected.isCreature(t))
            assertTrue(d.projected.hasType(t, "ARTIFACT"))
            assertTrue(d.projected.hasSubtype(t, "Necron") && d.projected.hasSubtype(t, "Warrior"))
            assertFalse("not entered tapped", d.isTapped(t))
        }
    }

    @Test
    fun noCreatureCardMilledMakesNoTokensEvenWithCreaturesAlreadyInTheGraveyard() {
        val d = game()
        val p = d.player1
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(p, "Hill Giant")
        val librarySize = d.state.getZone(p, Zone.LIBRARY).size

        d.crewAndAttack(p)

        assertEquals(librarySize - 3, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(3, d.zoneNames(p, Zone.GRAVEYARD).count { it == "Plains" })
        assertTrue(d.warriors(p).isEmpty())
    }

    @Test
    fun flyingAndIndestructibleAgainstDestroy() {
        val d = game()
        val p = d.player1
        val mono = d.putPermanentOnBattlefield(p, monolith)
        assertTrue(d.projected.hasKeyword(mono, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(mono, Keyword.INDESTRUCTIBLE))

        val shatter = d.putCardInHand(p, "Shatter")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, shatter, listOf(mono)).error)
        d.resolveAll()
        assertTrue("destroy doesn't remove it", mono in d.getPermanents(p))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Shatter"))
    }
}
