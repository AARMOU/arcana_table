package com.arcanatable

import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.engineSerializersModule
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * The Lunar Whale, Crew 1 (#108). Argentum offers CrewVehicle with crewCreatures empty and the
 * eligible creatures in tapForPowerCreatures. Arcana didn't show Crew on the Vehicle (it matched
 * actions by cardId/sourceId, not vehicleId) and, from the general actions list, sent the empty
 * crew, which the engine rejects. The engine side is proven in :custom-cards LunarWhaleCrewScenarioTest.
 */
class LunarWhaleCrewTest {
    /** Shape copied from the real engine's legal action (see hostJsonCarriesTheFieldsArcanaReads). */
    private fun crewAction(vararg creatures: Triple<String, Int, Boolean>, required: Int = 1) = JSONObject()
        .put("actionType", "CrewVehicle").put("description", "Crew The Lunar Whale").put("isAffordable", true)
        .put("action", JSONObject().put("type", "CrewVehicle").put("playerId", "p1").put("vehicleId", "whale").put("crewCreatures", JSONArray()))
        .put("tapForPower", true).put("tapForPowerRequired", required)
        .put("tapForPowerCreatures", JSONArray(creatures.map { (id, power, canAttack) ->
            JSONObject().put("entityId", id).put("name", id).put("power", power).put("canAttack", canAttack)
        }))

    private val board = crewAction(Triple("elf", 1, false), Triple("bear", 2, false), Triple("thopter", 0, false))

    @Test fun crewIsListedOnTheVehicleItself() {
        val other = JSONObject().put("action", JSONObject().put("type", "ActivateAbility").put("sourceId", "elf"))
        assertEquals(listOf(board), ArgentumActions.forCardServerId(listOf(board, other), "whale"))
        assertTrue("not on the crew members", ArgentumActions.forCardServerId(listOf(board), "elf").isEmpty())
    }

    @Test fun composerOpensAndStartsWithTheLightestSufficientCrew() {
        val plan = ArgentumActionPlan.from(board)
        assertTrue("Crew needs a choice", plan.needsInput)
        val crew = plan.crew!!
        assertEquals(1, crew.required)
        assertEquals(mapOf("elf" to 1, "bear" to 2, "thopter" to 0), crew.power)
        assertEquals("one summoning-sick 1-power creature is enough", listOf("elf"), crew.initial())
        assertNull("CONFIRM works straight away", plan.problem(emptyMap(), emptyMap(), null, crewPicks = crew.initial()))
    }

    @Test fun startingChoiceAvoidsTappingCreaturesThatCouldAttack() {
        val plan = ArgentumActionPlan.from(crewAction(Triple("bear", 2, true), Triple("elf", 1, false)))
        assertEquals(listOf("elf"), plan.crew!!.initial())
        val onlyAttackers = ArgentumActionPlan.from(crewAction(Triple("bear", 2, true), Triple("cat", 1, true)))
        assertEquals("weakest attacker that's enough", listOf("cat"), onlyAttackers.crew!!.initial())
        val crew3 = ArgentumActionPlan.from(crewAction(Triple("a", 1, false), Triple("b", 2, false), Triple("c", 2, true), required = 3))
        assertEquals("several creatures reach Crew 3", 3, crew3.crew!!.total(crew3.crew!!.initial()))
    }

    @Test fun insufficientPowerAndUnlistedCreaturesAreRefused() {
        val plan = ArgentumActionPlan.from(board)
        assertNotNull("nothing chosen", plan.problem(emptyMap(), emptyMap(), null, crewPicks = emptyList()))
        assertNotNull("0 power isn't enough", plan.problem(emptyMap(), emptyMap(), null, crewPicks = listOf("thopter")))
        assertNotNull("opponent's creature isn't offered", plan.problem(emptyMap(), emptyMap(), null, crewPicks = listOf("oppBear")))
        assertNull("several together", plan.problem(emptyMap(), emptyMap(), null, crewPicks = listOf("thopter", "elf")))
    }

    @Test fun builtActionIsTheEnginesCrewVehicleWithTheChosenCrew() {
        val plan = ArgentumActionPlan.from(board)
        val built = plan.build(emptyMap(), null, emptyMap(), null, crewPicks = listOf("thopter", "elf")) { null }
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true; classDiscriminator = "type"; serializersModule = engineSerializersModule }
        val action = json.decodeFromString(GameAction.serializer(), built.toString()) as CrewVehicle
        assertEquals("whale", action.vehicleId.value)
        assertEquals(listOf("thopter", "elf"), action.crewCreatures.map { it.value })
    }

    @Test fun otherActionsAreUnaffected() {
        assertNull(ArgentumActionPlan.from(JSONObject().put("action", JSONObject().put("type", "ActivateAbility").put("sourceId", "x"))).crew)
        assertNull("no eligible creatures listed", ArgentumActionPlan.from(crewAction()).crew)
        // Saddle uses the same shape (saddleCreatures) and is not handled here.
        assertNull(ArgentumActionPlan.from(board.put("action", JSONObject().put("type", "SaddleMount").put("mountId", "m"))).crew)
    }
}
