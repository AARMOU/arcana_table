package com.arcanatable

/**
 * Pure rules for the stacked combat screens (CombatStackBoard.kt): which creatures share a stack,
 * how a count picked on a stack becomes per-creature declarations, block legality hints, the
 * damage preview and the "suggest blocks" helper. Argentum stays the authority: everything here
 * only shapes what the player is offered, and every declaration is still validated by the engine.
 */

/** One stack of interchangeable creatures; [rep] (the first id) names it on screen and in tests. */
data class CombatStack(val rep: String, val ids: List<String>) {
    val size: Int get() = ids.size
}

/** Printed power for previews; unknown or `*` counts as 0. */
internal fun combatPower(card: GameCard?): Int = card?.power?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

private fun combatToughness(card: GameCard?): Int =
    ((card?.toughness?.trim()?.toIntOrNull() ?: 0) - (card?.damage ?: 0)).coerceAtLeast(0)

/** Engine keywords in one spelling: "FIRST_STRIKE", "First strike" → "first strike". */
internal fun normalizedKeywords(card: GameCard?): Set<String> =
    card?.keywords.orEmpty().map { it.lowercase().replace('_', ' ').trim() }.toSet()

private fun GameCard.has(keyword: String) = keyword in normalizedKeywords(this)

/**
 * Creatures share a stack when nothing about them differs on the battlefield: name, power and
 * toughness, keywords, counters and marked damage. A commander or a face-down creature always
 * stands alone.
 */
internal fun stackKey(piece: CombatPiece): String {
    val card = piece.card ?: return "name:${piece.name}"
    if (card.isCommander || card.isFaceDown) return "solo:${piece.id}"
    return listOf(piece.name, combatStats(card), card.counters.filterValues { it > 0 }.toSortedMap().toString(), card.damage.toString())
        .joinToString("|")
}

/** Stacks in first-seen order, so the screen keeps the battlefield's order. */
internal fun stacksOf(ids: List<String>, piece: (String) -> CombatPiece, extraKey: (String) -> String = { "" }): List<CombatStack> =
    ids.groupBy { stackKey(piece(it)) + "#" + extraKey(it) }.values.map { CombatStack(it.first(), it) }

// ---------------------------------------------------------------- attacking

/** How many members of a stack attack each target. */
internal fun stackAllocation(selection: Map<String, String>, ids: List<String>): Map<String, Int> =
    ids.mapNotNull { selection[it] }.groupingBy { it }.eachCount()

/**
 * Re-declares one stack from target → count. Creatures that must attack are used first, a
 * creature is only sent at a target Argentum allows for it, and a creature that must attack and
 * was not placed still attacks its first legal target. Other stacks are left as they were.
 */
internal fun allocateStack(
    selection: Map<String, String>,
    ids: List<String>,
    counts: Map<String, Int>,
    optionFor: Map<String, ArgentumAttackOption>,
): Map<String, String> {
    val result = (selection - ids.toSet()).toMutableMap()
    val free = ids.sortedByDescending { optionFor[it]?.mandatory == true }.toMutableList()
    counts.forEach { (target, wanted) ->
        repeat(wanted.coerceAtLeast(0)) {
            val pick = free.firstOrNull { target in optionFor[it]?.targets.orEmpty() } ?: return@repeat
            free.remove(pick)
            result[pick] = target
        }
    }
    free.filter { optionFor[it]?.mandatory == true }.forEach { id -> optionFor[id]?.targets?.firstOrNull()?.let { result[id] = it } }
    return result
}

/** The target a stack's − / + buttons work on: where most of it attacks, else [preferred] when legal. */
internal fun stackTarget(selection: Map<String, String>, stack: CombatStack, optionFor: Map<String, ArgentumAttackOption>, preferred: String?): String? {
    val legal = optionFor[stack.rep]?.targets.orEmpty()
    return stackAllocation(selection, stack.ids).maxByOrNull { it.value }?.key
        ?: preferred?.takeIf { it in legal }
        ?: legal.firstOrNull()
}

/** One more (or one fewer, [delta] = -1) of this stack attacks; the count never passes the stack's size. */
internal fun stepStack(
    selection: Map<String, String>, stack: CombatStack, delta: Int,
    optionFor: Map<String, ArgentumAttackOption>, preferred: String?,
): Map<String, String> {
    val counts = stackAllocation(selection, stack.ids).toMutableMap()
    val target = stackTarget(selection, stack, optionFor, preferred) ?: return selection
    if (delta > 0) {
        if (counts.values.sum() >= stack.size) return selection
        counts[target] = (counts[target] ?: 0) + 1
    } else {
        val from = target.takeIf { (counts[it] ?: 0) > 0 } ?: counts.maxByOrNull { it.value }?.key ?: return selection
        counts[from] = (counts[from] ?: 0) - 1
    }
    return allocateStack(selection, stack.ids, counts, optionFor)
}

/**
 * Remembers the last attack per player by stack (name, stats, counters) and target, so "Same as
 * last turn" can rebuild it on new tokens of the same kind. Lives for the app session only.
 */
internal object CombatMemory {
    private val lastAttack = mutableMapOf<String, Map<String, Map<String, Int>>>()
    private val sent = mutableMapOf<String, Map<String, Map<String, Int>>>()

    /** Holds an attack just sent; it becomes "last turn" only once [commit] sees it accepted. */
    fun remember(playerId: String, stacks: List<CombatStack>, selection: Map<String, String>, piece: (String) -> CombatPiece) {
        val attack = stacks.associate { stackKey(piece(it.rep)) to stackAllocation(selection, it.ids) }.filterValues { it.isNotEmpty() }
        if (attack.isEmpty()) sent.remove(playerId) else sent[playerId] = attack
    }

    /** Argentum accepted the attack (the player's creatures are attacking): keep it for next turn. */
    fun commit(playerId: String) {
        sent.remove(playerId)?.let { lastAttack[playerId] = it }
    }

    fun forget() { lastAttack.clear(); sent.clear() }

    /** Last turn's attack applied to these stacks, or null when nothing from it fits. */
    fun replay(
        playerId: String, stacks: List<CombatStack>, base: Map<String, String>,
        optionFor: Map<String, ArgentumAttackOption>, piece: (String) -> CombatPiece,
    ): Map<String, String>? {
        val previous = lastAttack[playerId] ?: return null
        var next = base
        var matched = false
        stacks.forEach { stack ->
            val counts = previous[stackKey(piece(stack.rep))] ?: return@forEach
            val legal = optionFor[stack.rep]?.targets.orEmpty()
            val usable = counts.filterKeys { it in legal }
            if (usable.isEmpty()) return@forEach
            matched = true
            next = allocateStack(next, stack.ids, usable, optionFor)
        }
        return next.takeIf { matched }
    }
}

/** Damage each target would take if nothing blocks: double strike counts twice. */
internal fun attackPreview(selection: Map<String, String>, piece: (String) -> CombatPiece): Map<String, Int> =
    selection.entries.groupBy({ it.value }) { (attacker, _) ->
        val card = piece(attacker).card
        combatPower(card) * if (card?.has("double strike") == true) 2 else 1
    }.mapValues { it.value.sum() }

/** Commander damage a target would take from [selection]'s commanders if unblocked, by commander name. */
internal fun commanderPreview(selection: Map<String, String>, target: String, piece: (String) -> CombatPiece): Map<String, Int> =
    selection.filterValues { it == target }.keys.mapNotNull { id ->
        val p = piece(id)
        val card = p.card?.takeIf { it.isCommander } ?: return@mapNotNull null
        p.name to combatPower(card) * if (card.has("double strike")) 2 else 1
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }

// ---------------------------------------------------------------- blocking

/**
 * Why [blocker] cannot block [attacker], from the evasion keywords Argentum reports; null when
 * nothing obvious stops it. This only greys out choices: Argentum still checks the declaration.
 */
internal fun blockProblem(attacker: GameCard?, blocker: GameCard?): String? {
    if (attacker == null || blocker == null) return null
    val text = blocker.oracleText.orEmpty().lowercase()
    val handlesFlying = blocker.has("flying") || blocker.has("reach") ||
        "can block creatures with flying" in text || "as though it had reach" in text || "as though it had flying" in text
    return when {
        attacker.has("flying") && !handlesFlying -> "can't block flying"
        attacker.has("shadow") && !blocker.has("shadow") -> "can't block shadow"
        blocker.has("shadow") && !attacker.has("shadow") -> "shadow blocks only shadow"
        attacker.has("horsemanship") && !blocker.has("horsemanship") -> "can't block horsemanship"
        else -> null
    }
}

/** Attackers needing two or more blockers (menace) that have exactly one. */
internal fun menaceShort(selection: Map<String, List<String>>, piece: (String) -> CombatPiece): List<String> =
    selection.values.flatten().groupingBy { it }.eachCount().filter { (attacker, count) ->
        count == 1 && piece(attacker).card?.has("menace") == true
    }.keys.toList()

/** Blockers assigned to each attacker. */
internal fun blockersOn(selection: Map<String, List<String>>): Map<String, Int> =
    selection.values.flatten().groupingBy { it }.eachCount()

/** How many members of [blockerStack] block anything in [attackerStack]. */
internal fun stackBlocking(selection: Map<String, List<String>>, blockerStack: CombatStack, attackerStack: CombatStack): Int =
    blockerStack.ids.count { b -> selection[b].orEmpty().any { it in attackerStack.ids } }

/**
 * One more blocker from [blockerStack] on [attackerStack]: an unused member blocks the attacker
 * in that stack with the fewest blockers so far, so "2 Soldiers on 3 Goblins" blocks two Goblins.
 */
internal fun addBlock(
    selection: Map<String, List<String>>, blockerStack: CombatStack, attackerStack: CombatStack,
    maxBlocks: Map<String, Int>, piece: (String) -> CombatPiece,
): Map<String, List<String>> {
    val counts = blockersOn(selection)
    val blocker = blockerStack.ids.firstOrNull { selection[it].isNullOrEmpty() }
        ?: blockerStack.ids.firstOrNull { b -> (maxBlocks[b] ?: 1) > selection[b].orEmpty().size && selection[b].orEmpty().none { it in attackerStack.ids } }
        ?: return selection
    val attacker = attackerStack.ids
        .filter { it !in selection[blocker].orEmpty() && blockProblem(piece(it).card, piece(blocker).card) == null }
        .minByOrNull { counts[it] ?: 0 } ?: return selection
    val cap = maxBlocks[blocker] ?: 1
    val next = if (cap > 1) (selection[blocker].orEmpty() + attacker).take(cap) else listOf(attacker)
    return selection + (blocker to next)
}

/** One fewer blocker from [blockerStack] on [attackerStack]; assignments the engine requires stay. */
internal fun removeBlock(
    selection: Map<String, List<String>>, blockerStack: CombatStack, attackerStack: CombatStack,
    mandatory: Map<String, List<String>>,
): Map<String, List<String>> {
    val blocker = blockerStack.ids.lastOrNull { b ->
        selection[b].orEmpty().any { it in attackerStack.ids && it !in mandatory[b].orEmpty() }
    } ?: return selection
    val drop = selection[blocker].orEmpty().last { it in attackerStack.ids && it !in mandatory[blocker].orEmpty() }
    val next = selection[blocker].orEmpty() - drop
    return if (next.isEmpty()) selection - blocker else selection + (blocker to next)
}

/** Damage the defender is about to take: unblocked attackers, trample over blockers, double strike twice. */
internal fun incomingDamage(attackers: List<String>, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): Int =
    attackers.sumOf { id -> damageThrough(id, selection, piece) }

private fun damageThrough(attacker: String, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): Int {
    val card = piece(attacker).card
    val power = combatPower(card)
    val strikes = if (card?.has("double strike") == true) 2 else 1
    val blockers = selection.filterValues { attacker in it }.keys
    if (blockers.isEmpty()) return power * strikes
    if (card?.has("trample") != true) return 0
    val soak = if (card.has("deathtouch")) blockers.size else blockers.sumOf { combatToughness(piece(it).card) }
    return (power * strikes - soak).coerceAtLeast(0)
}

/** Commander damage the defender is about to take, by commander name. */
internal fun incomingCommanderDamage(attackers: List<String>, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): Map<String, Int> =
    attackers.mapNotNull { id ->
        val p = piece(id)
        if (p.card?.isCommander != true) null else p.name to damageThrough(id, selection, piece)
    }.filter { it.second > 0 }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }

/**
 * Blocks a careful player would make, using the same rules as the table AI (chooseAiBlockers):
 * biggest attackers first, chump-blocking only when the damage left would be lethal, counting
 * commander damage toward 21. Keeps every assignment the engine requires.
 */
internal fun suggestBlocks(
    attackers: List<String>, blockers: List<String>, life: Int, commanderDamageReceived: Map<String, Int>,
    mandatory: Map<String, List<String>>, piece: (String) -> CombatPiece,
): Map<String, List<String>> {
    val selection = mandatory.filterKeys { it in blockers }.toMutableMap()
    val index = blockers.withIndex().associate { it.value to it.index }
    fun creature(id: String, number: Int): CombatCreature {
        val card = piece(id).card
        return CombatCreature(number, card?.power?.trim()?.toIntOrNull(), card?.let { c -> c.toughness?.trim()?.toIntOrNull()?.minus(c.damage) },
            normalizedKeywords(card), card?.oracleText.orEmpty())
    }
    val ordered = attackers.sortedByDescending { combatPower(piece(it).card) }
    ordered.forEachIndexed { position, attacker ->
        if (selection.values.any { attacker in it }) return@forEachIndexed
        val laterDamage = ordered.drop(position + 1).sumOf { damageThrough(it, selection, piece) }
        val attackerPiece = piece(attacker)
        val commanderLeft = attackerPiece.card?.takeIf { it.isCommander }?.let { 21 - (commanderDamageReceived[attackerPiece.name] ?: 0) }
        val effectiveLife = minOf(life - laterDamage, commanderLeft ?: Int.MAX_VALUE).coerceAtLeast(1)
        val free = blockers.filter { b -> b !in selection && blockProblem(attackerPiece.card, piece(b).card) == null }
        val picked = chooseAiBlockers(creature(attacker, -1), free.map { creature(it, index.getValue(it)) }, effectiveLife)
        picked.forEach { number -> selection[blockers[number]] = listOf(attacker) }
    }
    return selection
}

/**
 * Whose attack this is from the masked state: attacker id → the player, planeswalker or battle
 * it attacks. Missing when the state does not say.
 */
internal fun attackTargetsFrom(state: org.json.JSONObject?, attackers: List<String>): Map<String, String> {
    val cards = state?.optJSONObject("cards") ?: return emptyMap()
    return attackers.mapNotNull { id ->
        val raw = cards.optJSONObject(id)?.opt("attackingTarget") ?: return@mapNotNull null
        val target = when (raw) {
            is org.json.JSONObject -> raw.optString("value").ifBlank { raw.optString("playerId") }.ifBlank { raw.optString("permanentId") }
            is String -> raw
            else -> ""
        }
        target.takeIf { it.isNotBlank() && it != "null" }?.let { id to it }
    }.toMap()
}

/** What a declared block is likely to do, from the defender's side. */
enum class BlockOutcome(val label: String) {
    UNBLOCKED("Unblocked"), TRADE("Trade"), ATTACKER_DIES("Kills it"), CHUMP("Chump block"), HELD_OFF("Held off"),
}

/** [blockersDying] = blocker ids the attacker's damage would destroy. */
internal data class BlockResult(val outcome: BlockOutcome, val attackerDies: Boolean, val blockersDying: List<String>)

private fun lethalTo(target: GameCard?, from: GameCard?, power: Int): Boolean =
    target != null && power > 0 && !target.has("indestructible") &&
        (from?.has("deathtouch") == true || power >= combatToughness(target))

/**
 * The likely result of the blocks on [attacker] in [selection]: every blocker's power against the
 * attacker, and the attacker's power (twice with double strike) spread over its blockers in order,
 * one point each with deathtouch. First strike and damage-order choices aren't modelled, so this
 * is a label for the screen, never a rules decision; Argentum resolves the real combat.
 */
internal fun blockResult(attacker: String, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): BlockResult {
    val blockers = selection.filterValues { attacker in it }.keys.toList()
    if (blockers.isEmpty()) return BlockResult(BlockOutcome.UNBLOCKED, false, emptyList())
    val a = piece(attacker).card
    val attackerDies = a != null && !a.has("indestructible") && blockers.any { b ->
        val card = piece(b).card
        card?.has("deathtouch") == true && combatPower(card) > 0
    } || lethalTo(a, null, blockers.sumOf { combatPower(piece(it).card) })
    var left = combatPower(a) * (if (a?.has("double strike") == true) 2 else 1)
    val dying = mutableListOf<String>()
    for (b in blockers) {
        val card = piece(b).card ?: continue
        if (card.has("indestructible")) continue
        val need = if (a?.has("deathtouch") == true) 1 else combatToughness(card)
        if (need in 1..left) { dying += b; left -= need }
    }
    val outcome = when {
        attackerDies && dying.isNotEmpty() -> BlockOutcome.TRADE
        attackerDies -> BlockOutcome.ATTACKER_DIES
        dying.isNotEmpty() -> BlockOutcome.CHUMP
        else -> BlockOutcome.HELD_OFF
    }
    return BlockResult(outcome, attackerDies, dying)
}

/** A short warning (or all-clear) for an attacker against the creatures that could block it. */
internal data class AttackRisk(val text: String, val warn: Boolean)

/**
 * What could happen to [attacker] if one of [blockers] blocks it: a trade, death, or nothing
 * worse than a bounce. Blockers that can't legally block it (evasion) are left out. A single-
 * blocker estimate for the screen only.
 */
internal fun attackRisk(attacker: GameCard?, blockers: List<GameCard>): AttackRisk? {
    if (attacker == null) return null
    val power = combatPower(attacker) * (if (attacker.has("double strike")) 2 else 1)
    if (power == 0) return AttackRisk("0 power: deals no damage", true)
    val able = blockers.filter { !it.tapped && blockProblem(attacker, it) == null }
    if (able.isEmpty()) return AttackRisk("Nothing they have can block it", false)
    val killers = able.filter { lethalTo(attacker, it, combatPower(it)) }
    val trade = killers.firstOrNull { lethalTo(it, attacker, power) }
    return when {
        trade != null -> AttackRisk("Could trade with ${trade.name}", true)
        killers.isNotEmpty() -> AttackRisk("Could die to ${killers.first().name}", true)
        else -> AttackRisk("Survives any one blocker", false)
    }
}

/** "2 × Goblin Token, Llanowar Elves" for a summary line; "Nothing" when empty. */
internal fun namesWithCounts(names: List<String>): String =
    if (names.isEmpty()) "Nothing"
    else names.groupingBy { it }.eachCount().entries.joinToString { (name, n) -> if (n > 1) "$n × $name" else name }
