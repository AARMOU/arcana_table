package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * The Lunar Whale (upstream Argentum card): {3}{U} legendary artifact — Vehicle, flying, 3/5,
 * Crew 1. Issue #108: Crew and Vehicle attack legality, on the real rules engine.
 *
 * Argentum offers Crew as its own action, CrewVehicle(player, vehicle, crewCreatures = []), with
 * the eligible creatures and the power required in tapForPowerCreatures / tapForPowerRequired.
 * The client has to fill crewCreatures. Arcana sent it empty.
 */
class LunarWhaleCrewScenarioTest {
    private val whaleName = "The Lunar Whale"

    private class Board(val d: GameTestDriver, val whale: EntityId, val elf: EntityId, val bear: EntityId, val thopter: EntityId, val oppBear: EntityId)

    /** Whale, Llanowar Elves (1), Grizzly Bears (2), Ornithopter (0); opponent's Bears. All entered this turn. */
    private fun board(): Board {
        val d = scenario(whaleName, "Llanowar Elves", "Grizzly Bears", "Ornithopter")
        val p = d.player1
        return Board(d,
            whale = d.putPermanentOnBattlefield(p, whaleName),
            elf = d.putCreatureOnBattlefield(p, "Llanowar Elves"),
            bear = d.putCreatureOnBattlefield(p, "Grizzly Bears"),
            thopter = d.putCreatureOnBattlefield(p, "Ornithopter"),
            oppBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears"))
    }

    private fun Board.crewInfo() = LegalActionEnricher(d.services.manaSolver, d.cardRegistry)
        .enrich(d.legalActions(d.player1), d.state, d.player1)
        .singleOrNull { (it.action as? CrewVehicle)?.vehicleId == whale }

    private fun Board.crew(crew: List<EntityId>) = d.submit(CrewVehicle(d.player1, whale, crew))

    @Test fun engineOffersCrewWithEligibleCreaturesAndRequiredPower() {
        val b = board()
        val info = b.crewInfo()
        assertNotNull("Crew is offered", info)
        assertTrue(info!!.isAffordable)
        assertEquals("the client must choose the crew", emptyList<EntityId>(), (info.action as CrewVehicle).crewCreatures)
        assertTrue(info.tapForPower)
        assertEquals(1, info.tapForPowerRequired)
        val offered = info.tapForPowerCreatures!!.associate { it.entityId to it.power }
        assertEquals("own untapped creatures, summoning-sick ones included; not the opponent's",
            mapOf(b.elf to 1, b.bear to 2, b.thopter to 0), offered)
    }

    @Test fun emptyCrewIsRejectedWhichIsWhatArcanaSent() {
        val b = board()
        assertNotNull("no crew chosen", b.crew(emptyList()).error)
        assertFalse(b.d.projected.isCreature(b.whale))
    }

    @Test fun summoningSickOnePowerCreatureCrewsTheWhale() {
        val b = board()
        assertNull(b.crew(listOf(b.elf)).error)
        b.d.resolveAll()
        assertTrue(b.d.isTapped(b.elf))
        assertTrue(b.d.projected.isCreature(b.whale))
        assertEquals(3, b.d.projected.getPower(b.whale))
        assertEquals(5, b.d.projected.getToughness(b.whale))
        assertTrue(b.d.projected.hasKeyword(b.whale, Keyword.FLYING))
    }

    @Test fun severalCreaturesCanCrewTogether() {
        val b = board()
        assertNull(b.crew(listOf(b.thopter, b.elf)).error)
        b.d.resolveAll()
        assertTrue(b.d.isTapped(b.thopter) && b.d.isTapped(b.elf))
        assertTrue(b.d.projected.isCreature(b.whale))
    }

    @Test fun zeroPowerAloneIsNotEnough() {
        val b = board()
        assertNotNull(b.crew(listOf(b.thopter)).error)
        assertFalse(b.d.isTapped(b.thopter))
        assertFalse(b.d.projected.isCreature(b.whale))
    }

    @Test fun tappedCreatureCannotCrewAndIsNotOffered() {
        val b = board()
        b.d.tapPermanent(b.bear)
        assertFalse(b.crewInfo()!!.tapForPowerCreatures!!.any { it.entityId == b.bear })
        assertNotNull(b.crew(listOf(b.bear)).error)
    }

    @Test fun opponentsCreatureCannotCrew() {
        val b = board()
        assertNotNull(b.crew(listOf(b.oppBear)).error)
        assertFalse(b.d.isTapped(b.oppBear))
    }

    @Test fun whaleCastThisTurnCannotAttackAfterCrewing() {
        // Cast for real (the direct-placement helper only marks creatures summoning sick).
        val d = scenario(whaleName, "Llanowar Elves")
        val p = d.player1
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val card = d.putCardInHand(p, whaleName)
        d.giveMana(p, Color.BLUE, 4)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val whale = d.permanentsNamed(p, whaleName).single()
        assertNull(d.submit(CrewVehicle(p, whale, listOf(elf))).error)
        d.resolveAll()
        assertTrue(d.projected.isCreature(whale))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull("summoning-sick Vehicle can't attack", d.declareAttackers(p, listOf(whale), d.player2).error)
    }

    @Test fun whaleControlledSinceTurnStartAttacksOnceCrewed() {
        val b = board()
        b.d.removeSummoningSickness(b.whale)
        assertNull(b.crew(listOf(b.bear)).error)
        b.d.resolveAll()
        b.d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(b.d.declareAttackers(b.d.player1, listOf(b.whale), b.d.player2).error)
        assertTrue("attacking taps it (no vigilance)", b.d.isTapped(b.whale))
    }

    @Test fun uncrewedWhaleCannotAttack() {
        val b = board()
        b.d.removeSummoningSickness(b.whale)
        b.d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNotNull(b.d.declareAttackers(b.d.player1, listOf(b.whale), b.d.player2).error)
    }

    @Test fun crewLastsUntilEndOfTurn() {
        val b = board()
        assertNull(b.crew(listOf(b.elf)).error)
        b.d.resolveAll()
        assertTrue(b.d.projected.isCreature(b.whale))
        b.d.passPriorityUntil(Step.UPKEEP, b.d.player2)
        assertFalse("no longer a creature next turn", b.d.projected.isCreature(b.whale))
    }

    @Test fun hostJsonCarriesTheFieldsArcanaReads() {
        // Encoded with LocalArgentumHost's Json settings: the contract ArgentumCrew.from() parses.
        val b = board()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true; classDiscriminator = "type"; serializersModule = com.wingedsheep.engine.core.engineSerializersModule }
        val o = json.parseToJsonElement(json.encodeToString(com.wingedsheep.engine.view.LegalActionInfo.serializer(), b.crewInfo()!!)).let { it as kotlinx.serialization.json.JsonObject }
        fun str(e: kotlinx.serialization.json.JsonElement?) = (e as kotlinx.serialization.json.JsonPrimitive).content
        val action = o["action"] as kotlinx.serialization.json.JsonObject
        assertEquals("CrewVehicle", str(action["type"]))
        assertEquals(b.whale.value, str(action["vehicleId"]))
        assertEquals("true", str(o["tapForPower"]))
        assertEquals("1", str(o["tapForPowerRequired"]))
        val creatures = (o["tapForPowerCreatures"] as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
        assertEquals(setOf(b.elf.value, b.bear.value, b.thopter.value), creatures.map { str(it["entityId"]) }.toSet())
        assertTrue(creatures.all { it.containsKey("power") && it.containsKey("canAttack") })
    }
}
