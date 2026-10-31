package com.arcanatable

import com.arcanatable.engine.costs.CounterRemovalCost
import com.arcanatable.engine.EmbeddedEngineTrial
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.engineSerializersModule
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Iron Spider, Stark Upgrade: "{2}, Remove two +1/+1 counters from among artifacts you control:
 * Draw a card." Argentum offers the ability but reports the counter total as 0, so Arcana sent
 * no counter choice and the engine rejected the activation. The engine side is proven in
 * :custom-cards IronSpiderScenarioTest; these cover Arcana's half.
 */
class IronSpiderCounterRemovalTest {
    private val spider = "Iron Spider, Stark Upgrade"

    private fun spiderDefinition() =
        File("src/main/assets/argentum/all-cards.jsonl").bufferedReader().useLines { lines ->
            EmbeddedEngineTrial.loadCards(lines.filter { it.contains("\"name\":\"$spider\"") }).single()
        }

    @Test fun requirementComesFromTheAbilitysOwnCost() {
        val abilities = spiderDefinition().script.activatedAbilities
        assertNull("the tap ability removes no counters", CounterRemovalCost.requirement(abilities[0].cost))
        assertEquals(CounterRemovalCost.Requirement(2, "+1/+1"), CounterRemovalCost.requirement(abilities[1].cost))
    }

    /** The legal action as the offline host now sends it (total filled in). */
    private fun legalAction(total: Int = 2, spiderCounters: Int = 0, thopterCounters: Int = 2) = JSONObject()
        .put("description", "{2}, Remove two +1/+1 counters from among artifacts you control: Draw a card.")
        .put("isAffordable", true)
        .put("action", JSONObject().put("type", "ActivateAbility").put("playerId", "p1")
            .put("sourceId", "spider").put("abilityId", "$spider:2"))
        .put("additionalCostInfo", JSONObject().put("description", "Remove +1/+1 counters from creatures you control")
            .put("costType", "RemoveCounters").put("distributedCounterRemovalTotal", total)
            .put("counterRemovalCreatures", JSONArray()
                .put(source("spider", spiderCounters))
                .put(source("thopter", thopterCounters))))

    private fun source(id: String, n: Int) = JSONObject().put("entityId", id).put("name", id)
        .put("availableCounters", n)
        .put("availableCountersByType", if (n > 0) JSONObject().put("+1/+1", n) else JSONObject())

    /** A legal action listing arbitrary per-type counters ([availableCounters] = all of them). */
    private fun mixed(total: Int, vararg sources: Pair<String, Map<String, Int>>) = legalAction(total).apply {
        getJSONObject("additionalCostInfo").put("counterRemovalCreatures", JSONArray(sources.map { (id, byType) ->
            JSONObject().put("entityId", id).put("name", id).put("availableCounters", byType.values.sum())
                .put("availableCountersByType", JSONObject(byType))
        }))
    }

    @Test fun planAsksWhichArtifactsGiveUpCountersAndStartsComplete() {
        val plan = ArgentumActionPlan.from(legalAction())
        assertTrue("the composer opens for this cost", plan.needsInput)
        val cr = plan.counterRemoval!!
        assertEquals(2, cr.total)
        assertEquals("only permanents with counters are offered", mapOf("thopter" to 2), cr.sources)
        val start = cr.initial()
        assertEquals(mapOf("thopter" to 2), start)
        assertNull("CONFIRM works straight away", plan.problem(emptyMap(), emptyMap(), null, start))
    }

    @Test fun splitMustTotalExactlyAndStayWithinEachPermanent() {
        val plan = ArgentumActionPlan.from(legalAction(spiderCounters = 1, thopterCounters = 1))
        assertEquals(mapOf("spider" to 1, "thopter" to 1), plan.counterRemoval!!.initial())
        assertNotNull(plan.problem(emptyMap(), emptyMap(), null, mapOf("spider" to 1)))
        assertNotNull(plan.problem(emptyMap(), emptyMap(), null, mapOf("thopter" to 2)))
        assertNull(plan.problem(emptyMap(), emptyMap(), null, mapOf("spider" to 1, "thopter" to 1)))
    }

    @Test fun builtActionCarriesTheDistributionTheEngineExpects() {
        val plan = ArgentumActionPlan.from(legalAction(spiderCounters = 1, thopterCounters = 3))
        val built = plan.build(emptyMap(), null, emptyMap(), null, mapOf("spider" to 1, "thopter" to 1)) { null }
        val removals = built.getJSONObject("costPayment").getJSONArray("distributedCounterRemovals")
        assertEquals(2, removals.length())
        // Decode exactly as LocalArgentumHost does on submitAction.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true; classDiscriminator = "type"; serializersModule = engineSerializersModule }
        val action = json.decodeFromString(GameAction.serializer(), built.toString()) as ActivateAbility
        val decoded = action.costPayment!!.distributedCounterRemovals.associate { it.entityId.value to it.count }
        assertEquals(mapOf("spider" to 1, "thopter" to 1), decoded)
        assertTrue(action.costPayment!!.distributedCounterRemovals.all { it.counterType == "+1/+1" })
    }

    @Test fun withoutATotalNothingChanges() {
        // A server that still reports 0 (or too few counters) keeps the old behaviour.
        assertNull(ArgentumActionPlan.from(legalAction(total = 0)).counterRemoval)
        assertNull(ArgentumActionPlan.from(legalAction(thopterCounters = 1)).counterRemoval)
    }

    // Mixed counter types (GPT review on #104). The host narrows the listing to "+1/+1"; the plan
    // must also use only that type's count and refuse anything ambiguous.

    @Test fun onePlusOneBesideOtherCountersCannotPayTwo() {
        // As the host sends it: only "+1/+1" listed, though the permanent has 4 counters in all.
        val narrowed = mixed(2, "thopter" to mapOf("+1/+1" to 1)).apply {
            getJSONObject("additionalCostInfo").getJSONArray("counterRemovalCreatures").getJSONObject(0).put("availableCounters", 4)
        }
        assertNull("capacity is the +1/+1 count, not all counters", ArgentumActionPlan.from(narrowed).counterRemoval)
        // Unnarrowed listing with another type: ambiguous, fail closed.
        assertNull(ArgentumActionPlan.from(mixed(2, "thopter" to mapOf("+1/+1" to 1, "charge" to 3))).counterRemoval)
    }

    @Test fun twoPlusOneOnAMixedArtifactSendsExactlyTwoPlusOne() {
        val info = mixed(2, "thopter" to mapOf("+1/+1" to 2)).apply {
            getJSONObject("additionalCostInfo").getJSONArray("counterRemovalCreatures").getJSONObject(0).put("availableCounters", 5)
        }
        val plan = ArgentumActionPlan.from(info)
        val cr = plan.counterRemoval!!
        assertEquals(mapOf("thopter" to 2), cr.sources)
        assertNotNull("can't take three", cr.problem(mapOf("thopter" to 3)))
        val removals = plan.build(emptyMap(), null, emptyMap(), null, cr.initial()) { null }
            .getJSONObject("costPayment").getJSONArray("distributedCounterRemovals")
        assertEquals(1, removals.length())
        assertEquals("+1/+1", removals.getJSONObject(0).getString("counterType"))
        assertEquals(2, removals.getJSONObject(0).getInt("count"))
    }

    @Test fun oneAndOneAcrossTwoArtifactsOneOfThemMixed() {
        val info = mixed(2, "thopter" to mapOf("+1/+1" to 1), "ring" to mapOf("+1/+1" to 1)).apply {
            getJSONObject("additionalCostInfo").getJSONArray("counterRemovalCreatures").getJSONObject(0).put("availableCounters", 4)
        }
        val plan = ArgentumActionPlan.from(info)
        val split = plan.counterRemoval!!.initial()
        assertEquals(mapOf("thopter" to 1, "ring" to 1), split)
        val removals = plan.build(emptyMap(), null, emptyMap(), null, split) { null }
            .getJSONObject("costPayment").getJSONArray("distributedCounterRemovals")
        assertEquals(listOf("thopter" to 1, "ring" to 1), (0 until removals.length()).map {
            removals.getJSONObject(it).let { r -> assertEquals("+1/+1", r.getString("counterType")); r.getString("entityId") to r.getInt("count") }
        })
    }

    @Test fun differentTypesAcrossPermanentsFailClosed() {
        assertNull(ArgentumActionPlan.from(mixed(2, "thopter" to mapOf("+1/+1" to 1), "ring" to mapOf("charge" to 1))).counterRemoval)
    }
}
