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
        "CombatResolutionDecision", "SplitPilesDecision", "ChooseReplacementDecision", "BudgetModalDecision",
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
        return when {
            info.optJSONObject("action") == null -> "Argentum sent an action without a payload."
            info.optBoolean("requiresTargets") && info.optJSONArray("validTargets")?.length() == 0 &&
                (info.optJSONArray("targetRequirements")?.length() ?: 0) == 0 -> "Argentum did not list any targets for this action."
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

    /**
     * True for a mana ability ("{T}: Add {G}"). Argentum pays costs itself by tapping sources
     * automatically, so Arcana never offers these as manual actions: activating one by hand at
     * best does nothing useful and, on a tapped permanent, is rejected with INVALID_ACTION.
     */
    fun isManaAbility(info: JSONObject): Boolean = info.optBoolean("isManaAbility")

    /**
     * The legal actions Arcana shows as choices: drops the "nothing to do" placeholder and every
     * mana ability. Order is preserved.
     */
    fun visibleChoices(choices: List<JSONObject>): List<JSONObject> =
        choices.filterNot { it.optBoolean("__none") || (isManaAbility(it) && !isSacrificeMana(it)) }

    /**
     * A mana ability that sacrifices its source (a Treasure). Auto-pay never uses these, so they stay
     * in the card menu as "Sacrifice for mana".
     */
    fun isSacrificeMana(info: JSONObject): Boolean =
        isManaAbility(info) && info.optString("description").contains("Sacrifice", ignoreCase = true)

    /**
     * Mana abilities you can use by hand to float mana ("Add {G}"), shown at the bottom of a card's
     * actions. Sacrifice-for-mana is already in the main list.
     */
    fun floatManaChoices(choices: List<JSONObject>): List<JSONObject> =
        choices.filter { isManaAbility(it) && !isSacrificeMana(it) && it.optBoolean("isAffordable", true) }

    /** Preserve the extra cost in the UI: sacrificing Food is not sacrificing a Treasure. */
    fun menuLabel(info: JSONObject): String {
        val description = info.optString("description")
        return when {
            !isSacrificeMana(info) -> description
            description.contains("Sacrifice a Food", ignoreCase = true) -> "Tap Goose · sacrifice Food for mana"
            description.contains("Sacrifice this", ignoreCase = true) -> "Sacrifice this source for mana"
            else -> "Sacrifice for mana"
        }
    }
}

/** One target slot of a legal action (LegalActionTargetInfo), or the single implied slot. */
data class ArgentumTargetSlot(
    val index: Int,
    val description: String,
    val min: Int,
    val max: Int,
    val valid: List<String>,
    val byPrefix: Map<String, List<String>>,
    val exclusiveGroups: List<List<String>>,
    /** For a cost choice (index < 0): the AdditionalCostPayment field the picks go into. */
    val costField: String? = null,
)

/**
 * Everything Arcana must collect before submitting a legal action: X, targets (any number of
 * slots), divided damage and a mana colour. Built from the server's LegalActionInfo; the result
 * is the server's own action object with only those player choices filled in.
 */
data class ArgentumActionPlan(
    val info: JSONObject,
    val slots: List<ArgentumTargetSlot>,
    val xRange: IntRange?,
    val distributeTotal: Int?,
    val distributeMin: Int,
    val manaColors: List<String>,
) {
    val description: String get() = info.optString("description", "Argentum action")
    val needsInput: Boolean get() = slots.isNotEmpty() || xRange != null || distributeTotal != null || manaColors.isNotEmpty()

    /** Legal choices for a slot given earlier picks (server prefix table first, else the plain list). */
    fun legalFor(slot: ArgentumTargetSlot, picks: Map<Int, List<String>>): List<String> {
        val earlier = slots.filter { it.index in 0 until slot.index }.flatMap { picks[it.index].orEmpty() }
        val key = earlier.joinToString(",")
        val base = slot.byPrefix[key] ?: slot.valid
        val taken = picks[slot.index].orEmpty()
        // Exclusive groups: once one member is picked, the rest of that group is unavailable.
        return base.filter { id -> id in taken || slot.exclusiveGroups.none { group -> id in group && group.any { it in taken } } }
    }

    fun chosenTargets(picks: Map<Int, List<String>>): List<String> = slots.filter { it.costField == null }.flatMap { picks[it.index].orEmpty() }

    fun problem(picks: Map<Int, List<String>>, distribution: Map<String, Int>, color: String?): String? {
        for (slot in slots) {
            val count = picks[slot.index].orEmpty().size
            if (count < slot.min) return "Choose ${if (slot.min == slot.max) slot.min else "at least ${slot.min}"} for “${slot.description}”."
            if (count > slot.max) return "Too many choices for “${slot.description}”."
        }
        distributeTotal?.let { total ->
            val targets = chosenTargets(picks)
            if (targets.isEmpty()) return "Choose targets before dividing damage."
            if (targets.sumOf { distribution[it] ?: 0 } != total) return "Divide exactly $total among the targets."
            if (targets.any { (distribution[it] ?: 0) < distributeMin }) return "Each target needs at least $distributeMin."
        }
        if (manaColors.isNotEmpty() && color == null) return "Choose a mana colour."
        return null
    }

    /** The server's action with the player's choices filled in. [ref] turns a target id into ChosenTarget JSON. */
    fun build(picks: Map<Int, List<String>>, x: Int?, distribution: Map<String, Int>, color: String?, ref: (String) -> JSONObject?): JSONObject {
        val action = JSONObject(info.optJSONObject("action")?.toString() ?: "{}")
        val targetSlots = slots.filter { it.costField == null }
        if (targetSlots.isNotEmpty()) {
            val targets = JSONArray()
            targetSlots.forEach { slot -> picks[slot.index].orEmpty().forEach { id -> ref(id)?.let(targets::put) } }
            action.put("targets", targets)
            if (targetSlots.size > 1) action.put("targetGroupCounts", JSONArray(targetSlots.map { picks[it.index].orEmpty().size }))
        }
        // Costs like "Return an Elf you control" or "Sacrifice a creature": the chosen permanents.
        val costSlots = slots.filter { it.costField != null }
        if (costSlots.isNotEmpty()) {
            val key = if (action.optString("type") == "CastSpell") "additionalCostPayment" else "costPayment"
            val payment = action.optJSONObject(key) ?: JSONObject()
            costSlots.forEach { slot -> payment.put(slot.costField!!, JSONArray(picks[slot.index].orEmpty())) }
            action.put(key, payment)
        }
        if (x != null && xRange != null) action.put("xValue", x)
        if (distributeTotal != null) action.put("damageDistribution", JSONObject().apply { chosenTargets(picks).forEach { put(it, distribution[it] ?: 0) } })
        if (color != null && manaColors.isNotEmpty()) action.put("manaColorChoice", color)
        return action
    }

    companion object {
        private fun JSONArray?.ids(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() && it != "null" }

        fun from(info: JSONObject): ArgentumActionPlan {
            val requirements = info.optJSONArray("targetRequirements")
            val slots = if (requirements != null && requirements.length() > 0) {
                (0 until requirements.length()).mapNotNull { requirements.optJSONObject(it) }.map { r ->
                    val prefix = r.optJSONObject("validTargetsByPrefix")
                    ArgentumTargetSlot(
                        index = r.optInt("index"), description = r.optString("description", "Choose target"),
                        min = r.optInt("minTargets", 1), max = r.optInt("maxTargets", 1), valid = r.optJSONArray("validTargets").ids(),
                        byPrefix = prefix?.keys()?.asSequence()?.associateWith { prefix.optJSONArray(it).ids() }.orEmpty(),
                        exclusiveGroups = r.optJSONArray("exclusiveGroups")?.let { g -> (0 until g.length()).map { g.optJSONArray(it).ids() } }.orEmpty(),
                    )
                }.sortedBy { it.index }
            } else if (info.optBoolean("requiresTargets")) {
                val count = info.optInt("targetCount", 1)
                listOf(ArgentumTargetSlot(0, info.optString("targetDescription").takeIf { it.isNotBlank() && it != "null" } ?: "Choose target",
                    info.optInt("minTargets", count), count, info.optJSONArray("validTargets").ids(), emptyMap(), emptyList()))
            } else emptyList()
            return ArgentumActionPlan(
                info = info,
                slots = costSlots(info) + slots,
                xRange = ArgentumActions.xRange(info),
                distributeTotal = if (info.optBoolean("requiresDamageDistribution")) info.optInt("totalDamageToDistribute").takeIf { it > 0 } else null,
                distributeMin = info.optInt("minDamagePerTarget", 1),
                manaColors = if (info.optBoolean("requiresManaColorChoice")) info.optJSONArray("availableManaColors").ids().ifEmpty { listOf("WHITE", "BLUE", "BLACK", "RED", "GREEN") } else emptyList(),
            )
        }

        /** Choices an additional cost needs (permanents to sacrifice, return, tap; cards to discard or exile). */
        fun costSlots(info: JSONObject): List<ArgentumTargetSlot> {
            val cost = info.optJSONObject("additionalCostInfo") ?: return emptyList()
            val label = cost.optString("description").takeIf { it.isNotBlank() && it != "null" }
            fun slot(index: Int, what: String, validKey: String, min: Int, max: Int, field: String): ArgentumTargetSlot? {
                val valid = cost.optJSONArray(validKey).ids()
                if (valid.isEmpty() || max <= 0) return null
                return ArgentumTargetSlot(index, "Cost: ${label ?: what}", min.coerceAtMost(valid.size), max.coerceAtMost(valid.size), valid, emptyMap(), emptyList(), costField = field)
            }
            return listOfNotNull(
                slot(-5, "sacrifice", "validSacrificeTargets", cost.optInt("sacrificeCount", 1), cost.optInt("sacrificeCount", 1), "sacrificedPermanents"),
                slot(-4, "return to hand", "validBounceTargets", cost.optInt("bounceCount"), cost.optInt("bounceCount"), "bouncedPermanents"),
                slot(-3, "discard", "validDiscardTargets", cost.optInt("discardCount"), cost.optInt("discardCount"), "discardedCards"),
                slot(-2, "exile", "validExileTargets", cost.optInt("exileMinCount"), cost.optInt("exileMaxCount"), "exiledCards"),
                slot(-1, "tap", "validTapTargets", cost.optInt("tapCount"), cost.optInt("tapCount"), "tappedPermanents"),
            )
        }

        /** ChosenTarget JSON (StackComponents.kt) for a target reference from the masked state. */
        fun chosenTarget(ref: ArgentumTargetRef): JSONObject = when (ref.type) {
            "Player" -> JSONObject().put("type", "Player").put("playerId", ref.id)
            "Permanent" -> JSONObject().put("type", "Permanent").put("entityId", ref.id)
            "Spell" -> JSONObject().put("type", "Spell").put("spellEntityId", ref.id)
            else -> JSONObject().put("type", "Card").put("cardId", ref.id).put("ownerId", ref.ownerId).put("zone", ref.zone)
        }
    }
}

/** SplitPilesDecision helper; mirrors DecisionValidators.validateSplitPiles. */
data class ArgentumPileSplit(val decision: JSONObject) {
    val cards: List<String> = decision.optJSONArray("cards")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    val piles: Int = decision.optInt("numberOfPiles", 2)
    val allowUnassigned: Boolean = decision.optBoolean("allowUnassigned")
    val labels: List<String> = (0 until piles).map { i -> decision.optJSONArray("pileLabels")?.optString(i)?.takeIf { it.isNotBlank() } ?: "Pile ${i + 1}" }

    /** card -> pile index (or -1 unassigned), starting from the server's suggestion. */
    fun initial(): Map<String, Int> {
        val suggested = decision.optJSONArray("suggestedPiles")
        val start = mutableMapOf<String, Int>()
        if (suggested != null) for (p in 0 until suggested.length()) suggested.optJSONArray(p)?.let { pile -> for (i in 0 until pile.length()) start[pile.optString(i)] = p }
        cards.forEach { if (it !in start) start[it] = if (allowUnassigned) -1 else 0 }
        return start
    }

    fun allowedPiles(card: String): List<Int> = (0 until piles).filter { p ->
        val options = decision.optJSONObject("pileOptions")?.optJSONArray(p.toString()) ?: return@filter true
        (0 until options.length()).any { options.optString(it) == card }
    }

    fun problem(assign: Map<String, Int>): String? {
        if (!allowUnassigned && cards.any { (assign[it] ?: -1) < 0 }) return "Put every card in a pile."
        if (cards.any { c -> assign[c]?.let { it >= 0 && it !in allowedPiles(c) } == true }) return "A card is in a pile it can't go in."
        if (decision.has("requiredAssignments") && !decision.isNull("requiredAssignments")) {
            val need = decision.optInt("requiredAssignments")
            if (cards.count { (assign[it] ?: -1) >= 0 } != need) return "Assign exactly $need card(s)."
        }
        return null
    }

    fun response(assign: Map<String, Int>): JSONObject = JSONObject().put("type", "PilesSplitResponse")
        .put("piles", JSONArray((0 until piles).map { p -> JSONArray(cards.filter { assign[it] == p }) }))
}

/** ChooseReplacementDecision helper; mirrors DecisionValidators.validateReplacement. */
data class ArgentumReplacementChoice(val decision: JSONObject) {
    val from: List<String> = decision.optJSONArray("fromOptions")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    val to: List<String> = decision.optJSONArray("toOptions")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
    fun allowedTo(fromIndex: Int): List<Int> = decision.optJSONArray("allowedToByFrom")?.optJSONArray(fromIndex)
        ?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: to.indices.toList()
    fun defaultFrom(): Int = decision.optInt("defaultFromIndex", 0).coerceIn(0, (from.size - 1).coerceAtLeast(0))
    fun response(fromIndex: Int, toIndex: Int): JSONObject = JSONObject().put("type", "ReplacementChosenResponse").put("fromIndex", fromIndex).put("toIndex", toIndex)
}

/** BudgetModalDecision helper; modes may repeat as long as the total cost fits the budget. */
data class ArgentumBudgetModes(val decision: JSONObject) {
    val budget: Int = decision.optInt("budget")
    val modes: List<Pair<Int, String>> = decision.optJSONArray("modes")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.optInt("cost") to it.optString("description") } }.orEmpty()
    fun spent(counts: Map<Int, Int>): Int = counts.entries.sumOf { (i, n) -> (modes.getOrNull(i)?.first ?: 0) * n }
    fun response(counts: Map<Int, Int>): JSONObject = JSONObject().put("type", "BudgetModalResponse")
        .put("selectedModeIndices", JSONArray(counts.entries.sortedBy { it.key }.flatMap { (i, n) -> List(n) { i } }))
}
