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

internal fun damageThrough(attacker: String, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): Int {
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

// ---------------------------------------------------------------- outcome previews
//
// Labels for the combat screens, never rules decisions: Argentum validates and resolves combat.
// The model is deliberately small and honest. It covers vanilla combat with first strike, double
// strike (separate damage steps; a creature that dies in the first step deals no regular damage),
// deathtouch, indestructible and marked damage. Anything it doesn't model (infect, protection,
// prevention, block triggers, a blocker splitting damage, an attacker dividing damage between
// several blockers) comes back as "unclear" with the reason, instead of a confident wrong answer.

/** What a declared block is likely to do, from the defender's side. */
enum class BlockOutcome(val label: String) {
    UNBLOCKED("Unblocked"), TRADE("Trade"), ATTACKER_DIES("Kills it"), CHUMP("Chump block"), HELD_OFF("Held off"), UNCLEAR("Unclear"),
}

/** null [attackerDies] / [blockersDying] = can't be predicted here; [reason] says why. */
internal data class BlockResult(val outcome: BlockOutcome, val attackerDies: Boolean?, val blockersDying: List<String>?, val reason: String? = null)

private val BlockTrigger = Regex("whenever[^.]*\\b(blocks|becomes blocked|is blocked)\\b")
private val AttackTrigger = Regex("whenever[^.]*\\battacks\\b")
private val UnmodelledKeywords = listOf("infect", "wither", "toxic", "protection", "banding", "flanking", "bushido", "rampage")

/** Why this preview can't model [card] in combat, or null when it can. */
internal fun unmodelledCombat(card: GameCard?): String? {
    if (card == null) return "unknown card"
    val keywords = normalizedKeywords(card)
    UnmodelledKeywords.firstOrNull { k -> keywords.any { it.startsWith(k) } }?.let { return it }
    val text = card.oracleText.orEmpty().lowercase()
    return when {
        "infect" in text || "poison" in text -> "infect"
        "wither" in text -> "wither"
        "protection from" in text -> "protection"
        "prevent" in text -> "damage prevention"
        "regenerate" in text -> "regeneration"
        BlockTrigger.containsMatchIn(text) -> "block trigger"
        else -> null
    }
}

/** A creature's damage in a combat preview: marked damage, deathtouch, indestructible. */
private class Fighter(card: GameCard) {
    val power = combatPower(card)
    private val toughness = card.toughness?.trim()?.toIntOrNull() ?: 0
    private var marked = card.damage
    private var deathtouched = false
    val deathtouch = card.has("deathtouch")
    val indestructible = card.has("indestructible")
    /** Deals damage in the first-strike step / the regular step. */
    val first = card.has("first strike") || card.has("double strike")
    val regular = !card.has("first strike") || card.has("double strike")
    val dead: Boolean get() = !indestructible && (marked >= toughness || deathtouched)
    /** Damage this creature needs to be destroyed by [from]; null when it can't be (indestructible). */
    fun lethalFrom(from: Fighter): Int? = if (indestructible) null else if (from.deathtouch) 1 else (toughness - marked).coerceAtLeast(1)
    fun hitBy(from: Fighter) {
        if (from.power <= 0) return
        marked += from.power
        if (from.deathtouch) deathtouched = true
    }
}

/** One attacker against one blocker, with first-strike and regular damage steps: (attacker dies, blocker dies). */
private fun duel(attacker: GameCard, blocker: GameCard): Pair<Boolean, Boolean> {
    val a = Fighter(attacker)
    val b = Fighter(blocker)
    if (a.first || b.first) {
        if (a.first) b.hitBy(a)
        if (b.first) a.hitBy(b)
    }
    val aAlive = !a.dead
    val bAlive = !b.dead
    if (aAlive && a.regular) b.hitBy(a)
    if (bAlive && b.regular) a.hitBy(b)
    return a.dead to b.dead
}

private fun blockResultOf(attackerDies: Boolean, dying: List<String>): BlockResult = BlockResult(
    when {
        attackerDies && dying.isNotEmpty() -> BlockOutcome.TRADE
        attackerDies -> BlockOutcome.ATTACKER_DIES
        dying.isNotEmpty() -> BlockOutcome.CHUMP
        else -> BlockOutcome.HELD_OFF
    }, attackerDies, dying,
)

private fun unclear(reason: String, attackerDies: Boolean? = null, dying: List<String>? = null) =
    BlockResult(BlockOutcome.UNCLEAR, attackerDies, dying, reason)

/**
 * The likely result of the blocks on [attacker] in [selection]. One blocker: an exact two-step
 * simulation. Several blockers (attacker without first strike): whether the attacker dies is
 * certain, but its controller chooses how to divide its damage, so blocker losses are only
 * certain when it can't kill any of them or dies before dealing damage.
 */
internal fun blockResult(attacker: String, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): BlockResult {
    val blockerIds = selection.filterValues { attacker in it }.keys.toList()
    if (blockerIds.isEmpty()) return BlockResult(BlockOutcome.UNBLOCKED, false, emptyList())
    val a = piece(attacker).card ?: return unclear("unknown card")
    val cards = blockerIds.map { piece(it).card ?: return unclear("unknown card") }
    (listOf(a) + cards).firstNotNullOfOrNull(::unmodelledCombat)?.let { return unclear(it) }
    if (blockerIds.any { selection[it].orEmpty().size > 1 }) return unclear("a blocker splits its damage")
    if (blockerIds.size == 1) {
        val (attackerDies, blockerDies) = duel(a, cards.single())
        return blockResultOf(attackerDies, if (blockerDies) blockerIds else emptyList())
    }
    val att = Fighter(a)
    if (att.first) return unclear("first strike against several blockers")
    val blockers = cards.map(::Fighter)
    blockers.filter { it.first }.forEach { att.hitBy(it) }
    val diesBeforeDealing = att.dead
    blockers.filter { it.regular }.forEach { att.hitBy(it) }
    val attackerDies = att.dead
    if (diesBeforeDealing) return blockResultOf(attackerDies, emptyList())
    val canKillOne = blockers.any { b -> b.lethalFrom(att)?.let { it <= att.power } == true }
    return if (!canKillOne) blockResultOf(attackerDies, emptyList())
    else unclear("attacker chooses how to split damage", attackerDies, null)
}

/** "1 Trade · 2 Unblocked" for a stack of identical attackers, each judged on its own blocks. */
internal fun stackOutcomeSummary(ids: List<String>, selection: Map<String, List<String>>, piece: (String) -> CombatPiece): String =
    ids.map { blockResult(it, selection, piece).outcome }.groupingBy { it }.eachCount()
        .entries.sortedBy { it.key.ordinal }.joinToString(" · ") { (outcome, n) -> "$n ${outcome.label}" }

/** A short warning (or all-clear) for an attacker; [unclear] = this preview can't say. */
internal data class AttackRisk(val text: String, val warn: Boolean, val unclear: Boolean = false)

/**
 * What could happen to [attacker] if one of [blockers] (the defending player's untapped
 * creatures) blocks it alone, with first-strike steps. Blockers that evasion obviously stops are
 * left out; [blockProblem] is a hint, not full legality. Says "unclear" rather than guessing.
 */
internal fun attackRisk(attacker: GameCard?, blockers: List<GameCard>): AttackRisk? {
    if (attacker == null) return null
    unmodelledCombat(attacker)?.let { return AttackRisk("Outcome unclear: $it", false, unclear = true) }
    if (AttackTrigger.containsMatchIn(attacker.oracleText.orEmpty().lowercase())) return AttackRisk("Outcome unclear: attack trigger", false, unclear = true)
    if (combatPower(attacker) == 0) return AttackRisk("0 power: deals no damage", true)
    val able = blockers.filter { !it.tapped && blockProblem(attacker, it) == null }
    if (able.isEmpty()) return AttackRisk("Nothing they have can block it", false)
    if (attacker.has("menace")) return if (able.size < 2) AttackRisk("Menace: they can't block it", false)
        else AttackRisk("Menace: only a double block can stop it", false, unclear = true)
    val unknown = able.firstOrNull { unmodelledCombat(it) != null }
    val results = able.filter { unmodelledCombat(it) == null }.map { it to duel(attacker, it) }
    val dies = results.firstOrNull { (_, r) -> r.first && !r.second }?.first
    val trade = results.firstOrNull { (_, r) -> r.first && r.second }?.first
    return when {
        dies != null -> AttackRisk("Could die to ${dies.name}", true)
        trade != null -> AttackRisk("Could trade with ${trade.name}", true)
        unknown != null -> AttackRisk("Unclear against ${unknown.name} (${unmodelledCombat(unknown)})", false, unclear = true)
        else -> AttackRisk("Survives any one blocker", false)
    }
}

/**
 * Life after unblocked damage, or null when that damage might not be plain life loss (infect,
 * toxic, prevention, unknown cards): the screen then shows only "N potential damage".
 */
internal fun lifeAfterUnblocked(life: Int?, attackers: List<GameCard?>, damage: Int): Int? {
    if (life == null || damage <= 0) return null
    val notLifeLoss = setOf("unknown card", "infect", "toxic", "damage prevention")
    val plain = attackers.all { c -> c != null && unmodelledCombat(c) !in notLifeLoss }
    return if (plain) life - damage else null
}

/** "2 × Goblin Token, Llanowar Elves" for a summary line; "None" when empty. */
internal fun namesWithCounts(names: List<String>): String =
    if (names.isEmpty()) "None"
    else names.groupingBy { it }.eachCount().entries.joinToString { (name, n) -> if (n > 1) "$n × $name" else name }
