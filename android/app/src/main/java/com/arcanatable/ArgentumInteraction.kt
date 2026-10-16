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
