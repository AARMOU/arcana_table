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

    @Test fun onlyUnknownDecisionTypesNeedHandling() {
        assertNotNull(ArgentumDecisions.needsHandlingReason(JSONObject().put("type", "SomeFutureDecision")))
        // Every decision type in Argentum's rules engine today has a native control.
        for (type in listOf("ChooseTargetsDecision", "SelectCardsDecision", "YesNoDecision", "BatchYesNoDecision", "ChooseModeDecision",
            "ChooseColorDecision", "ChooseNumberDecision", "DistributeDecision", "OrderObjectsDecision", "SplitPilesDecision",
            "ChooseOptionDecision", "ChooseReplacementDecision", "AssignDamageDecision", "SearchLibraryDecision", "ReorderLibraryDecision",
            "SelectManaSourcesDecision", "BudgetModalDecision", "CombatResolutionDecision", "PlayCardDecision")) {
            assertNull(type, ArgentumDecisions.needsHandlingReason(JSONObject().put("type", type)))
        }
    }

    @Test fun multiTargetActionsBuildArgentumShape() {
        val info = JSONObject().put("description", "Cast Arc Lightning")
            .put("action", JSONObject().put("type", "CastSpell").put("cardId", "spell").put("playerId", "me"))
            .put("requiresTargets", true).put("requiresDamageDistribution", true).put("totalDamageToDistribute", 3).put("minDamagePerTarget", 1)
            .put("targetRequirements", JSONArray()
                .put(JSONObject().put("index", 0).put("description", "Any target").put("minTargets", 1).put("maxTargets", 3).put("validTargets", JSONArray(listOf("opp", "bear", "elf")))))
        val plan = ArgentumActionPlan.from(info)
        assertTrue(plan.needsInput)
        assertNull(ArgentumActions.needsHandlingReason(info))
        val picks = mapOf(0 to listOf("opp", "bear"))
        assertNotNull("must divide exactly 3", plan.problem(picks, mapOf("opp" to 1, "bear" to 1), null))
        assertNull(plan.problem(picks, mapOf("opp" to 2, "bear" to 1), null))
        val refs = mapOf(
            "opp" to ArgentumTargetRef("opp", "Opponent", "Player"),
            "bear" to ArgentumTargetRef("bear", "Bear", "Permanent", "opp", "Battlefield"))
        val action = plan.build(picks, null, mapOf("opp" to 2, "bear" to 1), null) { refs[it]?.let(ArgentumActionPlan::chosenTarget) }
        assertEquals("Player", action.getJSONArray("targets").getJSONObject(0).getString("type"))
        assertEquals("bear", action.getJSONArray("targets").getJSONObject(1).getString("entityId"))
        assertEquals(2, action.getJSONObject("damageDistribution").getInt("opp"))
        assertFalse("single slot sends no group counts", action.has("targetGroupCounts"))
    }

    @Test fun twoSlotActionsSendGroupCountsAndRespectPrefixes() {
        val info = JSONObject().put("action", JSONObject().put("type", "CastSpell").put("cardId", "s").put("playerId", "me")).put("requiresTargets", true)
            .put("targetRequirements", JSONArray()
                .put(JSONObject().put("index", 0).put("description", "Target creature you control").put("minTargets", 1).put("maxTargets", 1).put("validTargets", JSONArray(listOf("a", "b"))))
                .put(JSONObject().put("index", 1).put("description", "Another target creature").put("minTargets", 1).put("maxTargets", 1)
                    .put("validTargets", JSONArray(listOf("a", "b", "c"))).put("validTargetsByPrefix", JSONObject().put("a", JSONArray(listOf("b", "c"))).put("b", JSONArray(listOf("a", "c"))))))
        val plan = ArgentumActionPlan.from(info)
        assertEquals(listOf("b", "c"), plan.legalFor(plan.slots[1], mapOf(0 to listOf("a"))))
        val action = plan.build(mapOf(0 to listOf("a"), 1 to listOf("c")), null, emptyMap(), null) { JSONObject().put("type", "Permanent").put("entityId", it) }
        assertEquals(listOf(1, 1), (0 until 2).map { action.getJSONArray("targetGroupCounts").getInt(it) })
    }

    @Test fun manaColourAndXAreFilledIn() {
        val info = JSONObject().put("action", JSONObject().put("type", "ActivateAbility").put("sourceId", "lotus"))
            .put("requiresManaColorChoice", true).put("availableManaColors", JSONArray(listOf("RED", "GREEN")))
        val plan = ArgentumActionPlan.from(info)
        assertNotNull(plan.problem(emptyMap(), emptyMap(), null))
        assertEquals("GREEN", plan.build(emptyMap(), null, emptyMap(), "GREEN") { null }.getString("manaColorChoice"))
        val x = ArgentumActionPlan.from(JSONObject().put("action", JSONObject().put("type", "CastSpell")).put("hasXCost", true).put("maxAffordableX", 5))
        assertEquals(5, x.build(emptyMap(), 5, emptyMap(), null) { null }.getInt("xValue"))
    }

    @Test fun cardTargetsKeepArgentumZoneSpelling() {
        val json = ArgentumActionPlan.chosenTarget(ArgentumTargetRef("c1", "Grizzly Bears", "Card", "me", "Graveyard"))
        assertEquals("Graveyard", json.getString("zone"))
        assertEquals("Card", json.getString("type"))
    }

    @Test fun pileSplitReplacementAndBudgetMirrorValidators() {
        val split = ArgentumPileSplit(JSONObject().put("type", "SplitPilesDecision").put("cards", JSONArray(listOf("a", "b", "c"))).put("numberOfPiles", 2)
            .put("suggestedPiles", JSONArray().put(JSONArray(listOf("a"))).put(JSONArray(listOf("b")))))
        val start = split.initial()
        assertEquals(0, start["c"])
        assertNull(split.problem(start))
        val piles = split.response(start).getJSONArray("piles")
        assertEquals(2, piles.length()); assertEquals(2, piles.getJSONArray(0).length())

        val replacement = ArgentumReplacementChoice(JSONObject().put("fromOptions", JSONArray(listOf("Draw", "Damage")))
            .put("toOptions", JSONArray(listOf("Skip", "Double"))).put("allowedToByFrom", JSONArray().put(JSONArray(listOf(0))).put(JSONArray(listOf(1)))))
        assertEquals(listOf(1), replacement.allowedTo(1))
        assertEquals(1, replacement.response(1, 1).getInt("toIndex"))

        val budget = ArgentumBudgetModes(JSONObject().put("budget", 3).put("modes", JSONArray()
            .put(JSONObject().put("cost", 1).put("description", "Draw")).put(JSONObject().put("cost", 2).put("description", "Hit"))))
        val counts = mapOf(0 to 1, 1 to 1)
        assertEquals(3, budget.spent(counts))
        assertEquals(listOf(0, 1), budget.response(counts).getJSONArray("selectedModeIndices").let { a -> (0 until a.length()).map { a.getInt(it) } })
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

    // ---- Combat damage board (rules-engine core/CombatResolution.kt) ----

    private fun tramplerBlocked(): JSONObject {
        fun edge(id: String, src: String, tgt: String, dir: String, amount: Int, max: Int, lethal: Int, drain: Boolean) = JSONObject()
            .put("id", id).put("sourceId", src).put("targetId", tgt).put("direction", dir).put("amount", amount)
            .put("maximum", max).put("lethal", lethal).put("orderConstrained", true).put("isTrampleDrain", drain).put("editableBy", "me")
        return JSONObject().put("type", "CombatResolutionDecision").put("id", "d1").put("playerId", "me")
            .put("prompt", "Assign Wildsear, Scouring Maw's 6 combat damage").put("firstStrike", false)
            .put("attackers", JSONArray().put(JSONObject().put("id", "wild").put("name", "Wildsear, Scouring Maw").put("power", 6).put("hasTrample", true)))
            .put("blockers", JSONArray().put(JSONObject().put("id", "wall").put("name", "Wall").put("power", 0).put("toughness", 3)))
            .put("defenders", JSONArray().put(JSONObject().put("id", "opp").put("name", "Opponent").put("kind", "PLAYER")))
            .put("edges", JSONArray()
                .put(edge("wild->wall", "wild", "wall", "ATTACKER_TO_BLOCKER", 3, 6, 3, false))
                .put(edge("wild->opp", "wild", "opp", "ATTACKER_TO_PLAYER", 3, 6, 1, true)))
    }

    @Test fun combatResolutionIsNativeAndDefaultsAreAccepted() {
        val decision = tramplerBlocked()
        assertNull(ArgentumDecisions.needsHandlingReason(decision))
        val board = requireNotNull(ArgentumCombatResolution.from(decision))
        val defaults = board.defaults("me")
        assertEquals(mapOf("wild->wall" to 3, "wild->opp" to 3), defaults)
        assertNull(board.problem(defaults))
        val response = board.response("me", defaults)
        assertEquals("CombatResolutionResponse", response.getString("type"))
        assertEquals(2, response.getJSONArray("edges").length())
        assertEquals("wild->wall", response.getJSONArray("edges").getJSONObject(0).getString("edgeId"))
    }

    @Test fun combatResolutionMirrorsArgentumValidation() {
        val board = requireNotNull(ArgentumCombatResolution.from(tramplerBlocked()))
        // Trample: blocker below lethal while damage tramples over.
        assertNotNull(board.problem(mapOf("wild->wall" to 2, "wild->opp" to 4)))
        // Over budget: 4 + 3 > 6.
        assertNotNull(board.problem(mapOf("wild->wall" to 4, "wild->opp" to 3)))
        // All on the blocker is legal; unassigned remainder is allowed.
        assertNull(board.problem(mapOf("wild->wall" to 6, "wild->opp" to 0)))
        assertEquals(1, board.remaining("wild", mapOf("wild->wall" to 3, "wild->opp" to 2)))
        // Only edges this player owns are submitted.
        assertEquals(0, board.response("someone-else", emptyMap()).getJSONArray("edges").length())
    }
}
