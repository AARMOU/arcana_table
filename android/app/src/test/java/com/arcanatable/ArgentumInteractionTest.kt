package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentumInteractionTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(requireNotNull(javaClass.classLoader!!.getResource("argentum/$name")).readText())

    // ---- Real payloads captured from magic.wingedsheep.com (Commander vs Argentum AI) ----

    @Test fun liveStateUpdateProjectsOntoArcanaTable() {
        val message = fixture("state-main-phase-with-actions.json")
        val snapshot = ArgentumTableSnapshot.parse(message.getJSONObject("state"), message.getJSONArray("legalActions"), message.optString("interactionEpoch"))
        assertEquals(2, snapshot.players.size)
        assertEquals(5, snapshot.legalActions.size)
        assertNotNull(snapshot.interactionEpoch)
        val me = snapshot.players[snapshot.viewingPlayer]
        // Own hand is visible with real names; the opponent's hand stays hidden.
        assertTrue(me.hand.isNotEmpty())
        assertTrue(me.hand.none { it.name == "Hidden card" })
        val opponent = snapshot.players[1 - snapshot.viewingPlayer]
        assertTrue(opponent.hand.all { it.name == "Hidden card" })
        // Commander starts in the command zone.
        assertEquals("Bello, Bard of the Brambles", me.commanderName)
    }

    @Test fun liveLegalActionsAreAllPresentable() {
        val message = fixture("state-main-phase-with-actions.json")
        val actions = message.getJSONArray("legalActions")
        for (i in 0 until actions.length()) {
            assertNull(actions.getJSONObject(i).optString("description"), ArgentumActions.needsHandlingReason(actions.getJSONObject(i)))
        }
    }

    @Test fun livePlayLandActionTargetsACardInTheProjectedHand() {
        val message = fixture("state-main-phase-with-actions.json")
        val snapshot = ArgentumTableSnapshot.parse(message.getJSONObject("state"), message.getJSONArray("legalActions"), message.optString("interactionEpoch"))
        val playLand = snapshot.legalActions.first { it.getJSONObject("action").optString("type") == "PlayLand" }
        val serverCardId = playLand.getJSONObject("action").optString("cardId")
        val displayId = snapshot.serverCardIds.entries.first { it.value == serverCardId }.key
        assertTrue(snapshot.players[snapshot.viewingPlayer].hand.any { it.id == displayId })
    }

    @Test fun liveCleanupDiscardDecisionIsNative() {
        val decision = fixture("state-cleanup-discard-decision.json").getJSONObject("pendingDecision")
        assertEquals("SelectCardsDecision", decision.optString("type"))
        assertNull(ArgentumDecisions.needsHandlingReason(decision))
        val response = ArgentumDecisions.cardsSelectedResponse(listOf(decision.getJSONArray("options").getString(0)))
        assertEquals("CardsSelectedResponse", response.getString("type"))
        assertEquals(1, response.getJSONArray("selectedCards").length())
    }

    // ---- Needs-handling classification ----

    @Test fun unknownDecisionTypesNeedHandlingInsteadOfBlocking() {
        for (type in listOf("SplitPilesDecision", "ChooseReplacementDecision", "BudgetModalDecision", "SomeFutureDecision")) {
            val reason = ArgentumDecisions.needsHandlingReason(JSONObject().put("type", type))
            assertNotNull(type, reason)
        }
        for (type in ArgentumDecisions.NATIVE_TYPES + ArgentumDecisions.PLAY_CARD) {
            assertNull(type, ArgentumDecisions.needsHandlingReason(JSONObject().put("type", type)))
        }
    }

    @Test fun multiTargetActionsNeedHandling() {
        val info = JSONObject().put("action", JSONObject().put("type", "CastSpell"))
            .put("requiresTargets", true).put("targetCount", 2).put("validTargets", JSONArray(listOf("a", "b")))
        assertNotNull(ArgentumActions.needsHandlingReason(info))
        val single = JSONObject().put("action", JSONObject().put("type", "CastSpell"))
            .put("requiresTargets", true).put("validTargets", JSONArray(listOf("a")))
        assertNull(ArgentumActions.needsHandlingReason(single))
    }

    @Test fun xCostRangeAndValue() {
        val info = JSONObject().put("hasXCost", true).put("minX", 1).put("maxAffordableX", 4)
        assertEquals(1..4, ArgentumActions.xRange(info))
        assertNull(ArgentumActions.xRange(JSONObject()))
        assertEquals(3, ArgentumActions.withX(JSONObject().put("type", "CastSpell"), 3).getInt("xValue"))
    }

    // ---- Combat (shapes from rules-engine CombatEnumerator) ----

    @Test fun attackPlanUsesOnlyServerListedAttackersAndTargets() {
        val info = JSONObject()
            .put("actionType", "DeclareAttackers")
            .put("action", JSONObject().put("type", "DeclareAttackers").put("playerId", "me").put("attackers", JSONObject()))
            .put("validAttackers", JSONArray(listOf("bear", "elf")))
            .put("mandatoryAttackers", JSONArray(listOf("elf")))
            .put("validAttackTargets", JSONArray(listOf("opponent", "walker")))
            .put("attackTargetsByAttacker", JSONObject().put("bear", JSONArray(listOf("opponent"))))
        val plan = requireNotNull(ArgentumAttackPlan.from(info))
        assertEquals(listOf("opponent"), plan.options.first { it.attackerId == "bear" }.targets)
        assertEquals(listOf("elf"), plan.missingMandatory(mapOf("bear" to "opponent")))
        val action = plan.buildAction(mapOf("bear" to "walker", "elf" to "opponent", "intruder" to "opponent"))
        val attackers = action.getJSONObject("attackers")
        assertFalse("bear may not attack the walker", attackers.has("bear"))
        assertEquals("opponent", attackers.getString("elf"))
        assertFalse(attackers.has("intruder"))
        assertEquals("DeclareAttackers", action.getString("type"))
        assertEquals("me", action.getString("playerId"))
    }

    @Test fun noAttackIsAnEmptyDeclaration() {
        val info = JSONObject().put("action", JSONObject().put("type", "DeclareAttackers").put("playerId", "me").put("attackers", JSONObject()))
            .put("validAttackers", JSONArray(listOf("bear"))).put("validAttackTargets", JSONArray(listOf("opp")))
        val action = requireNotNull(ArgentumAttackPlan.from(info)).buildAction(emptyMap())
        assertEquals(0, action.getJSONObject("attackers").length())
    }

    @Test fun blockPlanReadsAttackersFromStateAndRespectsMaxBlocks() {
        val state = JSONObject().put("cards", JSONObject()
            .put("a1", JSONObject().put("isAttacking", true))
            .put("a2", JSONObject().put("isAttacking", true))
            .put("x", JSONObject().put("isAttacking", false)))
        val info = JSONObject().put("action", JSONObject().put("type", "DeclareBlockers").put("playerId", "me").put("blockers", JSONObject()))
            .put("validBlockers", JSONArray(listOf("wall", "bird")))
            .put("blockerMaxBlockCounts", JSONObject().put("wall", 2))
        val plan = requireNotNull(ArgentumBlockPlan.from(info, state))
        assertEquals(setOf("a1", "a2"), plan.attackers.toSet())
        val blocks = plan.buildAction(mapOf("wall" to listOf("a1", "a2"), "bird" to listOf("a1", "a2"), "ghost" to listOf("a1"))).getJSONObject("blockers")
        assertEquals(2, blocks.getJSONArray("wall").length())
        assertEquals(1, blocks.getJSONArray("bird").length())
        assertFalse(blocks.has("ghost"))
    }

    // ---- Decision responses mirror Argentum's DecisionResponse types ----

    @Test fun responseShapesMatchArgentumTypes() {
        assertEquals("ModesChosenResponse", ArgentumDecisions.modesResponse(listOf(0, 2)).getString("type"))
        assertEquals("OrderedResponse", ArgentumDecisions.orderedResponse(listOf("b", "a")).getString("type"))
        val autoPay = ArgentumDecisions.autoPayManaResponse()
        assertEquals("ManaSourcesSelectedResponse", autoPay.getString("type")); assertTrue(autoPay.getBoolean("autoPay"))
        assertEquals("CancelDecisionResponse", ArgentumDecisions.cancelResponse().getString("type"))
        assertEquals(3, ArgentumDecisions.damageAssignmentResponse(mapOf("x" to 3)).getJSONObject("assignments").getInt("x"))
    }

    @Test fun damageAndDistributionValidation() {
        val damage = JSONObject().put("availablePower", 4)
        assertTrue(ArgentumDecisions.damageAssignmentComplete(damage, mapOf("a" to 3, "b" to 1)))
        assertFalse(ArgentumDecisions.damageAssignmentComplete(damage, mapOf("a" to 3)))
        val split = JSONObject().put("totalAmount", 3).put("minPerTarget", 1).put("maxPerTarget", JSONObject().put("a", 2))
        assertTrue(ArgentumDecisions.distributionComplete(split, mapOf("a" to 2, "b" to 1)))
        assertFalse(ArgentumDecisions.distributionComplete(split, mapOf("a" to 3, "b" to 0)))
    }

    @Test fun orderingItemsReadEitherField() {
        assertEquals(listOf("x", "y"), ArgentumDecisions.orderingItems(JSONObject().put("objects", JSONArray(listOf("x", "y")))))
        assertEquals(listOf("p"), ArgentumDecisions.orderingItems(JSONObject().put("cards", JSONArray(listOf("p")))))
    }
}
