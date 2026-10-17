package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject

/*
 * Adapter helpers between Argentum's protocol payloads and Arcana's dialogs.
 *
 * Nothing here decides legality. Every list of choices comes from the server (legal actions,
 * pending decisions, valid attackers/blockers), and every outbound message mirrors an Argentum
 * type found in its source:
 *   rules-engine/.../core/PendingDecision.kt   (decisions and responses)
 *   rules-engine/.../core/GameAction.kt        (DeclareAttackers / DeclareBlockers)
 *   rules-engine/.../view/LegalActionPresentation.kt (LegalActionInfo fields)
 * The helpers only build JSON and classify what Arcana can or cannot present.
 */

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() && it != "null" }

private fun JSONObject?.stringListMap(): Map<String, List<String>> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optJSONArray(it).strings() }
}

private fun JSONObject?.intMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optInt(it) }
}

object ArgentumDecisions {
    /** Decision types Arcana renders with its own controls. */
    val NATIVE_TYPES = setOf(
        "YesNoDecision", "BatchYesNoDecision", "ChooseOptionDecision", "ChooseNumberDecision",
        "ChooseColorDecision", "SelectCardsDecision", "ChooseTargetsDecision",
        "ChooseModeDecision", "SearchLibraryDecision", "OrderObjectsDecision", "ReorderLibraryDecision",
        "SelectManaSourcesDecision", "AssignDamageDecision", "DistributeDecision",
        "CombatResolutionDecision",
    )

    /** Handled outside the decision dialog (card-tap legal actions). */
    const val PLAY_CARD = "PlayCardDecision"

    /**
     * Returns a player-facing reason when Arcana has no control for this decision, or null when
     * it can be presented. The caller must show a "needs handling" state; never auto-answer.
     */
    fun needsHandlingReason(decision: JSONObject): String? {
        val type = decision.optString("type")
        if (type == PLAY_CARD || type in NATIVE_TYPES) return null
        val friendly = when (type) {
            "SplitPilesDecision" -> "splitting cards into piles"
            "ChooseReplacementDecision" -> "choosing how a replacement effect applies"
            "BudgetModalDecision" -> "spending a mode budget"
            "" -> "an unnamed decision"
            else -> type.removeSuffix("Decision").replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
        }
        return "Argentum asked for $friendly, which Arcana does not have a control for yet."
    }

    fun canCancel(decision: JSONObject): Boolean = decision.optBoolean("canCancel", false)

    /** Wraps a response with the decision id; MainActivity adds SubmitDecision + playerId. */
    fun cancelResponse(): JSONObject = JSONObject().put("type", "CancelDecisionResponse")

    fun modesResponse(selected: List<Int>): JSONObject =
        JSONObject().put("type", "ModesChosenResponse").put("selectedModes", JSONArray(selected))

    fun cardsSelectedResponse(selected: Collection<String>): JSONObject =
        JSONObject().put("type", "CardsSelectedResponse").put("selectedCards", JSONArray(selected.toList()))

    fun orderedResponse(ordered: List<String>): JSONObject =
        JSONObject().put("type", "OrderedResponse").put("orderedObjects", JSONArray(ordered))

    /** Argentum computes the payment itself when autoPay is true. */
    fun autoPayManaResponse(): JSONObject = JSONObject().put("type", "ManaSourcesSelectedResponse")
        .put("selectedSources", JSONArray()).put("autoPay", true)

    fun declineManaResponse(): JSONObject = JSONObject().put("type", "ManaSourcesSelectedResponse")
        .put("selectedSources", JSONArray()).put("autoPay", false)

    fun damageAssignmentResponse(assignments: Map<String, Int>): JSONObject =
        JSONObject().put("type", "DamageAssignmentResponse").put("assignments", JSONObject(assignments))

    fun distributionResponse(distribution: Map<String, Int>): JSONObject =
        JSONObject().put("type", "DistributionResponse").put("distribution", JSONObject(distribution))

    /** Item ids for ordering decisions (OrderObjects uses "objects", ReorderLibrary uses "cards"). */
    fun orderingItems(decision: JSONObject): List<String> =
        decision.optJSONArray("objects").strings().ifEmpty { decision.optJSONArray("cards").strings() }

    /** Display name for an id using the decision's own card info, falling back to the table. */
    fun cardName(decision: JSONObject, id: String, fallback: (String) -> String?): String {
        val info = decision.optJSONObject("cardInfo") ?: decision.optJSONObject("cards")
        return info?.optJSONObject(id)?.optString("name")?.takeIf(String::isNotBlank)
            ?: fallback(id) ?: "Card ${id.takeLast(6)}"
    }

    /** Server-provided default damage assignment, used as the starting point for the editor. */
    fun defaultDamageAssignments(decision: JSONObject): Map<String, Int> {
        val defaults = decision.optJSONObject("defaultAssignments").intMap()
        if (defaults.isNotEmpty()) return defaults
        return decision.optJSONObject("minimumAssignments").intMap()
    }

    /** Validity check mirroring Argentum's own rule so the CONFIRM button can be disabled early. */
    fun damageAssignmentComplete(decision: JSONObject, assignments: Map<String, Int>): Boolean {
        val power = decision.optInt("availablePower", 0)
        return assignments.values.all { it >= 0 } && assignments.values.sum() == power
    }

    fun distributionComplete(decision: JSONObject, distribution: Map<String, Int>): Boolean {
        val total = decision.optInt("totalAmount", 0)
        val min = decision.optInt("minPerTarget", 0)
        val caps = decision.optJSONObject("maxPerTarget").intMap()
        val sum = distribution.values.sum()
        val sumOk = if (decision.optBoolean("allowPartial", false)) sum in 0..total else sum == total
        return sumOk && distribution.all { (id, value) -> value >= min && (caps[id]?.let { value <= it } ?: true) }
    }
}

/** One damage edge of Argentum's combat-resolution board (rules-engine core/CombatResolution.kt). */
data class ArgentumDamageEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val direction: String,
    val defaultAmount: Int,
    val maximum: Int,
    val lethal: Int,
    val isTrampleDrain: Boolean,
    val editableBy: String,
)

/**
 * Parsed `CombatResolutionDecision`: every combat-damage assignment the engine wants confirmed,
 * pre-filled with Argentum's default amounts. Validation mirrors Argentum's own
 * DecisionValidators.validateCombatResolution so CONFIRM is only enabled for answers it accepts.
 */
data class ArgentumCombatResolution(
    val decision: JSONObject,
    val edges: List<ArgentumDamageEdge>,
    val names: Map<String, String>,
    val firstStrike: Boolean,
) {
    /** Edges the given player may change (banding can hand some to the opponent). */
    fun editableBy(playerId: String): List<ArgentumDamageEdge> = edges.filter { it.editableBy == playerId }

    fun defaults(playerId: String): Map<String, Int> = editableBy(playerId).associate { it.id to it.defaultAmount }

    private fun amountOf(edge: ArgentumDamageEdge, chosen: Map<String, Int>) = chosen[edge.id] ?: edge.defaultAmount

    /** Null when Argentum would accept these amounts; otherwise a player-facing reason. */
    fun problem(chosen: Map<String, Int>): String? {
        for (edge in edges) {
            val amount = amountOf(edge, chosen)
            if (amount < 0) return "Damage can't be negative."
            if (amount > edge.maximum) return "${names[edge.sourceId] ?: "A creature"} can assign at most ${edge.maximum} to ${names[edge.targetId] ?: "that target"}."
        }
        edges.groupBy { it.sourceId }.forEach { (source, sourceEdges) ->
            val total = sourceEdges.sumOf { amountOf(it, chosen) }
            val power = sourceEdges.maxOf { it.maximum }
            if (total > power) return "${names[source] ?: "A creature"} assigns $total but only has $power damage."
        }
        val toBlocker = mutableMapOf<String, Int>()
        edges.filter { it.direction == "ATTACKER_TO_BLOCKER" }.forEach { toBlocker.merge(it.targetId, amountOf(it, chosen), Int::plus) }
        for (drain in edges.filter { it.isTrampleDrain && amountOf(it, chosen) > 0 }) {
            val short = edges.filter { it.sourceId == drain.sourceId && it.direction == "ATTACKER_TO_BLOCKER" }
                .firstOrNull { (toBlocker[it.targetId] ?: 0) < it.lethal }
            if (short != null) return "Trample: assign lethal damage to ${names[short.targetId] ?: "each blocker"} (${short.lethal}) before damaging ${names[drain.targetId] ?: "the player"}."
        }
        return null
    }

    /** Remaining damage a source can still assign, for the editor's + buttons. */
    fun remaining(sourceId: String, chosen: Map<String, Int>): Int {
        val sourceEdges = edges.filter { it.sourceId == sourceId }
        return sourceEdges.maxOf { it.maximum } - sourceEdges.sumOf { amountOf(it, chosen) }
    }

    /** CombatResolutionResponse with only the edges this player owns. */
    fun response(playerId: String, chosen: Map<String, Int>): JSONObject {
        val list = JSONArray()
        editableBy(playerId).forEach { edge -> list.put(JSONObject().put("edgeId", edge.id).put("amount", amountOf(edge, chosen))) }
        return JSONObject().put("type", "CombatResolutionResponse").put("edges", list)
    }

    companion object {
        fun from(decision: JSONObject): ArgentumCombatResolution? {
            if (decision.optString("type") != "CombatResolutionDecision") return null
            val names = mutableMapOf<String, String>()
            listOf("attackers", "blockers", "defenders").forEach { key ->
                val list = decision.optJSONArray(key) ?: return@forEach
                for (i in 0 until list.length()) list.optJSONObject(i)?.let { names[it.optString("id")] = it.optString("name") }
            }
            val raw = decision.optJSONArray("edges") ?: JSONArray()
            val edges = (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }.map { e ->
                ArgentumDamageEdge(
                    id = e.optString("id"), sourceId = e.optString("sourceId"), targetId = e.optString("targetId"),
                    direction = e.optString("direction"), defaultAmount = e.optInt("amount"), maximum = e.optInt("maximum"),
                    lethal = e.optInt("lethal", 1), isTrampleDrain = e.optBoolean("isTrampleDrain"), editableBy = e.optString("editableBy"),
                )
            }
            return ArgentumCombatResolution(decision, edges, names, decision.optBoolean("firstStrike"))
        }
    }
}

/** One creature Argentum allows to attack, with the defenders it may attack. */
data class ArgentumAttackOption(val attackerId: String, val targets: List<String>, val mandatory: Boolean)

/** Parsed `DeclareAttackers` legal action. Selections are validated against server lists only. */
data class ArgentumAttackPlan(
    val legalAction: JSONObject,
    val options: List<ArgentumAttackOption>,
) {
    /** Builds the DeclareAttackers action, starting from the server's action object. */
    fun buildAction(selection: Map<String, String>): JSONObject {
        val action = JSONObject(legalAction.optJSONObject("action")?.toString() ?: "{}")
        val attackers = JSONObject()
        selection.forEach { (attacker, target) ->
            val option = options.firstOrNull { it.attackerId == attacker } ?: return@forEach
            if (target in option.targets) attackers.put(attacker, target)
        }
        return action.put("attackers", attackers)
    }

    fun missingMandatory(selection: Map<String, String>): List<String> =
        options.filter { it.mandatory && it.attackerId !in selection }.map { it.attackerId }

    companion object {
        fun from(info: JSONObject): ArgentumAttackPlan? {
            if (info.optJSONObject("action")?.optString("type") != "DeclareAttackers") return null
            val valid = info.optJSONArray("validAttackers").strings()
            val mandatory = info.optJSONArray("mandatoryAttackers").strings().toSet()
            val byAttacker = info.optJSONObject("attackTargetsByAttacker").stringListMap()
            val generic = info.optJSONArray("validAttackTargets").strings()
            val options = valid.map { id -> ArgentumAttackOption(id, byAttacker[id]?.takeIf { it.isNotEmpty() } ?: generic, id in mandatory) }
                .filter { it.targets.isNotEmpty() }
            return ArgentumAttackPlan(info, options)
        }
    }
}

/** Parsed `DeclareBlockers` legal action. Attackers come from the masked state (isAttacking). */
data class ArgentumBlockPlan(
    val legalAction: JSONObject,
    val blockers: List<String>,
    val attackers: List<String>,
    val maxBlocks: Map<String, Int>,
    val mandatory: Map<String, List<String>>,
) {
    fun buildAction(selection: Map<String, List<String>>): JSONObject {
        val action = JSONObject(legalAction.optJSONObject("action")?.toString() ?: "{}")
        val blocks = JSONObject()
        selection.forEach { (blocker, chosen) ->
            if (blocker !in blockers) return@forEach
            val legal = chosen.filter { it in attackers }.distinct().take(maxBlocks[blocker] ?: 1)
            if (legal.isNotEmpty()) blocks.put(blocker, JSONArray(legal))
        }
        return action.put("blockers", blocks)
    }

    companion object {
        fun from(info: JSONObject, state: JSONObject?): ArgentumBlockPlan? {
            if (info.optJSONObject("action")?.optString("type") != "DeclareBlockers") return null
            val cards = state?.optJSONObject("cards")
            val attackers = cards?.keys()?.asSequence()?.filter { cards.optJSONObject(it)?.optBoolean("isAttacking") == true }?.toList().orEmpty()
            return ArgentumBlockPlan(
                legalAction = info,
                blockers = info.optJSONArray("validBlockers").strings(),
                attackers = attackers,
                maxBlocks = info.optJSONObject("blockerMaxBlockCounts").intMap(),
                mandatory = info.optJSONObject("mandatoryBlockerAssignments").stringListMap(),
            )
        }
    }
}

object ArgentumActions {
    /** Types not tied to a single card; surfaced from the "Argentum actions" list. */
    val PLAYER_LEVEL_TYPES = setOf("DeclareAttackers", "DeclareBlockers", "PassPriority", "Concede")

    /**
     * Returns a reason when a legal action needs input Arcana cannot collect yet. Such actions are
     * shown as "needs handling" and are not submitted. Null means Arcana can submit it, possibly
     * after a single-target or X choice.
     */
    fun needsHandlingReason(info: JSONObject): String? {
        val type = info.optJSONObject("action")?.optString("type").orEmpty()
        if (type == "DeclareAttackers" || type == "DeclareBlockers") return null
        val requirements = info.optJSONArray("targetRequirements")?.length() ?: 0
        return when {
            info.optJSONObject("action") == null -> "Argentum sent an action without a payload."
            info.optBoolean("requiresDamageDistribution") -> "This action divides damage between targets, which Arcana cannot set up yet."
            info.optBoolean("requiresManaColorChoice") -> "This action needs a mana colour choice that Arcana cannot present yet."
            info.optBoolean("requiresTargets") && (requirements > 1 || info.optInt("targetCount", 1) > 1) ->
                "This action needs several targets chosen together, which Arcana cannot present yet."
            info.optBoolean("requiresTargets") && info.optJSONArray("validTargets").strings().isEmpty() ->
                "Argentum did not list any targets for this action."
            else -> null
        }
    }

    /** X range for actions with an {X} cost, or null when the action has none. */
    fun xRange(info: JSONObject): IntRange? {
        if (!info.optBoolean("hasXCost")) return null
        val min = info.optInt("minX", 0)
        val max = if (info.has("maxAffordableX") && !info.isNull("maxAffordableX")) info.optInt("maxAffordableX") else min
        return min..maxOf(min, max)
    }

    fun withX(action: JSONObject, x: Int): JSONObject = JSONObject(action.toString()).put("xValue", x)

    /** The epoch to send with this action: its own if present, else the snapshot's. */
    fun epochFor(info: JSONObject, snapshotEpoch: String?): String? =
        info.optString("interactionEpoch").takeUnless { it.isBlank() || it == "null" } ?: snapshotEpoch
}
