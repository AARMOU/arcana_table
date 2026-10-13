package com.arcanatable

internal data class AutomaticTokenSpec(
    val count: Int,
    val name: String,
    val type: String,
    val power: String? = null,
    val toughness: String? = null
)

internal data class AutomaticResolutionEffects(
    val lifeGain: Int = 0,
    val lifeLoss: Int = 0,
    val cardsDrawn: Int = 0,
    val tokens: List<AutomaticTokenSpec> = emptyList(),
    val requiresManualReview: Boolean = false
) {
    val isEmpty: Boolean get() = lifeGain == 0 && lifeLoss == 0 && cardsDrawn == 0 && tokens.isEmpty()
}

internal data class CreatureEntryLifeEffect(
    val amount: Int,
    val anotherOnly: Boolean,
    val controllerFilter: String?
)

internal data class RecognizedTriggeredAbility(
    val event: String,
    val actorFilter: String,
    val conditionText: String,
    val effectText: String,
    val anotherOnly: Boolean = false,
    val controllerFilter: String? = null
)

internal data class TargetedAutomaticEffect(
    val targetRule: String,
    val effectText: String
)

internal data class UntapStepResult(
    val remainsTapped: Boolean,
    val counters: Map<String, Int>,
    val removedStunCounter: Boolean
)

/** Only spells and commanders change zones when their stack item resolves. */
internal fun stackResolutionMovesCardToZone(kind: String): Boolean =
    kind.equals("SPELL", ignoreCase = true) || kind.equals("COMMANDER", ignoreCase = true)

/** Applies the normal untap-step rule for stun counters to one permanent. */
internal fun resolveUntapStep(tapped: Boolean, counters: Map<String, Int>): UntapStepResult {
    if (!tapped) return UntapStepResult(remainsTapped = false, counters = counters, removedStunCounter = false)
    val stun = counters.entries.firstOrNull { it.key.equals("Stun", ignoreCase = true) && it.value > 0 }
        ?: return UntapStepResult(remainsTapped = false, counters = counters, removedStunCounter = false)
    val nextCounters = counters.toMutableMap()
    if (stun.value == 1) nextCounters.remove(stun.key) else nextCounters[stun.key] = stun.value - 1
    return UntapStepResult(remainsTapped = true, counters = nextCounters, removedStunCounter = true)
}

private val automaticNumberWords = mapOf(
    "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
    "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
)

private fun parseAutomaticCount(raw: String): Int? =
    raw.toIntOrNull() ?: automaticNumberWords[raw.lowercase()]

/**
 * Extracts only unconditional, fixed-size effects from a resolving spell.
 * Anything involving targets, choices, conditions, costs, or variable amounts
 * stays manual so the app does not guess at a rules decision.
 */
internal fun parseAutomaticResolutionEffects(oracleText: String?): AutomaticResolutionEffects {
    val text = oracleText?.trim().orEmpty()
    if (text.isBlank()) return AutomaticResolutionEffects()
    val amountPattern = "(\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten)"
    val gainPattern = Regex("(?i)^(?:you\\s+)?gain\\s+$amountPattern\\s+life$")
    val lossPattern = Regex("(?i)^(?:you\\s+)?lose\\s+$amountPattern\\s+life$")
    val drawPattern = Regex("(?i)^(?:you\\s+)?draw\\s+$amountPattern\\s+cards?$")
    val tokenPattern = Regex("(?i)^create\\s+$amountPattern\\s+([^.;]+?)\\s+tokens?$")
    var lifeGain = 0
    var lifeLoss = 0
    var cardsDrawn = 0
    val tokens = mutableListOf<AutomaticTokenSpec>()
    val statements = text.split(Regex("(?<=\\.)\\s+|\\n+")).map { it.trim().trimEnd('.') }.filter { it.isNotBlank() }
    for (statement in statements) {
        val match = gainPattern.matchEntire(statement)
        if (match != null) {
            lifeGain += parseAutomaticCount(match.groupValues[1]) ?: return AutomaticResolutionEffects(requiresManualReview = true)
            continue
        }
        val loss = lossPattern.matchEntire(statement)
        if (loss != null) {
            lifeLoss += parseAutomaticCount(loss.groupValues[1]) ?: return AutomaticResolutionEffects(requiresManualReview = true)
            continue
        }
        val draw = drawPattern.matchEntire(statement)
        if (draw != null) {
            cardsDrawn += parseAutomaticCount(draw.groupValues[1]) ?: return AutomaticResolutionEffects(requiresManualReview = true)
            if (cardsDrawn > 20) return AutomaticResolutionEffects(requiresManualReview = true)
            continue
        }
        val token = tokenPattern.matchEntire(statement)
        if (token != null) {
            val count = parseAutomaticCount(token.groupValues[1]) ?: return AutomaticResolutionEffects(requiresManualReview = true)
            if (count > 20) return AutomaticResolutionEffects(requiresManualReview = true)
            val description = token.groupValues[2].trim()
            val stats = Regex("^(\\d+)/(\\d+)\\s+").find(description)
            val hasCreature = description.contains("creature", ignoreCase = true)
            val isArtifact = description.contains("artifact", ignoreCase = true) ||
                description.substringBefore(" ").lowercase() in setOf("treasure", "food", "clue", "blood", "map", "powerstone", "incubator")
            val name = description
                .replace(Regex("^\\d+/\\d+\\s+"), "")
                .split(Regex("\\s+"))
                .filterNot { it.lowercase() in setOf("white", "blue", "black", "red", "green", "colorless", "and", "creature", "artifact", "enchantment", "land") }
                .joinToString(" ")
                .trim()
                .ifBlank { if (isArtifact) "Artifact" else "Creature" }
            val type = when {
                hasCreature -> "Creature Token"
                isArtifact -> "Artifact Token — $name"
                else -> "Token"
            }
            tokens += AutomaticTokenSpec(count, name, type, stats?.groupValues?.get(1), stats?.groupValues?.get(2))
            continue
        }
        return AutomaticResolutionEffects(requiresManualReview = true)
    }
    return AutomaticResolutionEffects(lifeGain, lifeLoss, cardsDrawn, tokens)
}

/** Recognizes trigger clauses for events that the table currently reports: casts, draws, and creature entry. */
internal fun parseRecognizedTriggeredAbilities(oracleText: String?): List<RecognizedTriggeredAbility> {
    val sentences = oracleText.orEmpty().lineSequence()
        .flatMap { line -> line.split(Regex("(?<=\\.)\\s+" )).asSequence() }
        .map { it.trim().trimEnd('.') }
        .filter { it.isNotBlank() }
    return sentences.mapNotNull { sentence ->
        val comma = sentence.indexOf(',')
        if (comma < 0) return@mapNotNull null
        val condition = sentence.substringBefore(',').trim()
        val effect = sentence.substring(comma + 1).trim()
        if (effect.isBlank()) return@mapNotNull null

        val cast = Regex("(?i)^whenever\\s+(you|a player|an opponent)\\s+casts?\\s+.+\\bspell$").matchEntire(condition)
        if (cast != null) return@mapNotNull RecognizedTriggeredAbility(
            event = "CAST",
            actorFilter = when (cast.groupValues[1].lowercase()) { "you" -> "YOU"; "an opponent" -> "OPPONENT"; else -> "ANY" },
            conditionText = condition,
            effectText = effect
        )

        val draw = Regex("(?i)^whenever\\s+(you|a player|an opponent)\\s+draws?\\s+(?:a|one)\\s+card$").matchEntire(condition)
        if (draw != null) return@mapNotNull RecognizedTriggeredAbility(
            event = "DRAW",
            actorFilter = when (draw.groupValues[1].lowercase()) { "you" -> "YOU"; "an opponent" -> "OPPONENT"; else -> "ANY" },
            conditionText = condition,
            effectText = effect
        )

        val creatureEntry = Regex("(?i)^whenever\\s+(another\\s+)?creature\\s+enters(?:\\s+the battlefield)?(?:\\s+(under your control|under an opponent's control))?$").matchEntire(condition)
        if (creatureEntry != null) return@mapNotNull RecognizedTriggeredAbility(
            event = "CREATURE_ETB",
            actorFilter = when (creatureEntry.groupValues[2].lowercase()) {
                "under your control" -> "YOU"
                "under an opponent's control" -> "OPPONENT"
                else -> "ANY"
            },
            conditionText = condition,
            effectText = effect,
            anotherOnly = creatureEntry.groupValues[1].isNotBlank(),
            controllerFilter = creatureEntry.groupValues[2].takeIf { it.isNotBlank() }?.lowercase()
        )
        null
    }.toList()
}

/** Extracts simple effects aimed at one player so the UI can ask who the target is before applying them. */
internal fun parseTargetedAutomaticEffect(effectText: String?): TargetedAutomaticEffect? {
    val match = Regex("(?i)^target\\s+(player|opponent)\\s+(gains?|loses?|draws?|creates?)\\s+(.+?)\\.?$")
        .matchEntire(effectText.orEmpty().trim()) ?: return null
    val verb = when (match.groupValues[2].lowercase()) {
        "gain", "gains" -> "gain"
        "lose", "loses" -> "lose"
        "draw", "draws" -> "draw"
        "create", "creates" -> "create"
        else -> return null
    }
    val normalized = "$verb ${match.groupValues[3].trim().trimEnd('.')}"
    val parsed = parseAutomaticResolutionEffects(normalized)
    if (parsed.isEmpty || parsed.requiresManualReview) return null
    return TargetedAutomaticEffect(
        targetRule = match.groupValues[1].uppercase(),
        effectText = normalized
    )
}

internal fun parseTargetPlayerRule(effectText: String?): String? =
    Regex("(?i)^target\\s+(player|opponent)\\b").find(effectText.orEmpty().trim())?.groupValues?.get(1)?.uppercase()

/** Finds simple fixed-life triggers for creature entries, such as Soul Warden. */
internal fun parseCreatureEntryLifeEffects(oracleText: String?): List<CreatureEntryLifeEffect> {
    val pattern = Regex(
        "(?i)whenever\\s+(another\\s+)?creature\\s+enters\\s+(?:the battlefield\\s+)?" +
            "(under your control|under an opponent's control)?\\s*,\\s*you gain\\s+(\\d+)\\s+life\\."
    )
    return pattern.findAll(oracleText.orEmpty()).mapNotNull { match ->
        val amount = match.groupValues[3].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        CreatureEntryLifeEffect(
            amount = amount,
            anotherOnly = match.groupValues[1].isNotBlank(),
            controllerFilter = match.groupValues[2].takeIf { it.isNotBlank() }?.lowercase()
        )
    }.toList()
}

/** A single mana ability's production choice, keyed by mana symbol. */
internal data class ManaProductionOption(val pool: Map<String, Int>) {
    val label: String get() = pool.entries.flatMap { (symbol, count) -> List(count) { "{$symbol}" } }.joinToString(" + ")
}

/** Extracts ordinary tap-for-mana abilities from Oracle text, plus basic-land defaults. */
internal fun manaProductionPools(name: String, type: String, oracleText: String?): List<Map<String, Int>> {
    val tapAbilities = oracleText.orEmpty().lines().mapNotNull { line ->
        val ability = line.split(":", limit = 2)
        if (ability.size != 2 || !ability[1].contains("add", ignoreCase = true)) return@mapNotNull null
        val cost = ability[0].trim()
        if (!cost.equals("{T}", ignoreCase = true) && !cost.equals("Tap this permanent", ignoreCase = true)) return@mapNotNull null
        Regex("(?i)add\\s+([^\\n.]+)").find(ability[1])?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }
    val parsed = tapAbilities.flatMap { addText ->
        val anyType = Regex("(?i)(one|a) mana of any type").containsMatchIn(addText)
        val anyColor = Regex("(?i)(one|a) mana of any color").containsMatchIn(addText)
        when {
            anyType -> listOf("W", "U", "B", "R", "G", "C").map { mapOf(it to 1) }
            anyColor -> listOf("W", "U", "B", "R", "G").map { mapOf(it to 1) }
            else -> addText.split(Regex("(?i)\\s+or\\s+")).mapNotNull { option ->
                val symbols = Regex("\\{([WUBRGC])\\}", RegexOption.IGNORE_CASE).findAll(option)
                    .map { it.groupValues[1].uppercase() }.toList()
                symbols.takeIf { it.isNotEmpty() }?.groupingBy { it }?.eachCount()
            }
        }
    }.distinctBy { it.toSortedMap().toString() }
    if (parsed.isNotEmpty()) return parsed

    val basicType = when {
        type.contains("plains", true) || name.equals("Plains", true) -> "W"
        type.contains("island", true) || name.equals("Island", true) -> "U"
        type.contains("swamp", true) || name.equals("Swamp", true) -> "B"
        type.contains("mountain", true) || name.equals("Mountain", true) -> "R"
        type.contains("forest", true) || name.equals("Forest", true) -> "G"
        else -> null
    }
    val knownBasicLandName = name in setOf("Plains", "Island", "Swamp", "Mountain", "Forest") ||
        name.startsWith("Snow-Covered ", ignoreCase = true)
    return if (basicType != null && (type.contains("land", true) || knownBasicLandName)) listOf(mapOf(basicType to 1)) else emptyList()
}

/** Pure helpers for rules that are shared by the UI and can be regression-tested without Android. */
internal fun spendGenericMana(pool: Map<String, Int>, amount: Int): Map<String, Int>? {
    if (amount < 0 || pool.values.sum() < amount) return null
    var remaining = amount
    val next = pool.toMutableMap()
    listOf("C", "W", "U", "B", "R", "G").forEach { color ->
        if (remaining > 0) {
            val used = minOf(remaining, next[color] ?: 0)
            if (used > 0) {
                remaining -= used
                val left = (next[color] ?: 0) - used
                if (left == 0) next.remove(color) else next[color] = left
            }
        }
    }
    return next.takeIf { remaining == 0 }
}

internal fun supportsSimpleManaCost(cost: String?): Boolean {
    return cost.isNullOrBlank() || simpleManaSymbols(cost) != null
}

/** Returns a printed numeric creature stat when known; variable values such as `*` stay unknown. */
internal fun printedCreatureStat(value: String?): Int? = value?.trim()?.toIntOrNull()

internal data class CombatCreature(
    val id: Int,
    val power: Int?,
    val toughness: Int?,
    val keywords: Set<String> = emptySet(),
    val oracleText: String = ""
) {
    fun hasKeyword(keyword: String): Boolean = keywords.any { it.equals(keyword, ignoreCase = true) } ||
        Regex("(?<![A-Za-z])${Regex.escape(keyword)}(?![A-Za-z])", RegexOption.IGNORE_CASE).containsMatchIn(oracleText)
}

/** A cautious single- or double-block recommendation; it never invents stats for `*` creatures. */
internal fun chooseAiBlockers(
    attacker: CombatCreature,
    availableBlockers: List<CombatCreature>,
    defenderLife: Int,
    alreadyAssigned: Set<Int> = emptySet()
): Set<Int> {
    val attackPower = attacker.power?.coerceAtLeast(0) ?: return emptySet()
    if (attackPower == 0 || attacker.hasKeyword("unblockable") || attacker.oracleText.contains("can't be blocked", ignoreCase = true)) return emptySet()
    val legal = availableBlockers.filter { blocker ->
        blocker.id !in alreadyAssigned && blocker.power != null && blocker.toughness != null &&
            (!attacker.hasKeyword("Flying") || blocker.hasKeyword("Flying") || blocker.hasKeyword("Reach"))
    }
    if (legal.isEmpty()) return emptySet()

    fun killsAttacker(blocker: CombatCreature) = blocker.power!!.let { power ->
        power > 0 && (blocker.hasKeyword("Deathtouch") || power >= (attacker.toughness ?: Int.MAX_VALUE))
    }
    fun survives(blocker: CombatCreature) = attacker.hasKeyword("Deathtouch") || attackPower < blocker.toughness!! ||
        (blocker.hasKeyword("First strike") || blocker.hasKeyword("Double strike")) && killsAttacker(blocker)

    val needsTwo = attacker.hasKeyword("Menace") || attacker.oracleText.contains("can't be blocked except by two or more", ignoreCase = true)
    val winningGroups = if (needsTwo) {
        legal.indices.flatMap { first -> (first + 1 until legal.size).map { second -> listOf(legal[first], legal[second]) } }
            .filter { group -> group.sumOf { it.power!!.coerceAtLeast(0) } >= (attacker.toughness ?: Int.MAX_VALUE) || group.any(::killsAttacker) }
            .filter { group -> group.any(::survives) }
    } else {
        legal.filter { killsAttacker(it) && survives(it) }.map(::listOf)
    }
    if (winningGroups.isNotEmpty()) {
        return winningGroups.minByOrNull { group ->
            val lost = group.count { !survives(it) }
            val strength = group.sumOf { it.power!!.coerceAtLeast(0) }
            lost * 1000 + strength
        }!!.map { it.id }.toSet()
    }

    // Trade only when the attacker is a meaningful threat; chump-block only lethal non-trample damage.
    val trade = legal.filter { blocker -> killsAttacker(blocker) && !survives(blocker) &&
        (defenderLife <= 12 || attackPower >= (blocker.power ?: Int.MAX_VALUE) * 2)
    }.minByOrNull { it.power ?: Int.MAX_VALUE }
    if (trade != null) return setOf(trade.id)
    val lethalWithoutTrample = defenderLife in 1..attackPower && !attacker.hasKeyword("Trample")
    if (lethalWithoutTrample && !needsTwo) return setOf(legal.minByOrNull { it.power ?: Int.MAX_VALUE }!!.id)
    return emptySet()
}

/** Parses only ordinary numeric, colored, and colorless symbols without regex engine quirks. */
internal fun simpleManaSymbols(cost: String?): List<String>? {
    if (cost.isNullOrBlank()) return emptyList()
    val symbols = mutableListOf<String>()
    var cursor = 0
    while (cursor < cost.length) {
        if (cost[cursor] != '{') return null
        val closing = cost.indexOf('}', startIndex = cursor + 1)
        if (closing <= cursor + 1) return null
        val symbol = cost.substring(cursor + 1, closing).uppercase()
        val isColor = symbol in setOf("W", "U", "B", "R", "G", "C")
        val isGeneric = symbol.all { it in '0'..'9' } && symbol.toIntOrNull() != null
        if (!isColor && !isGeneric) return null
        symbols += symbol
        cursor = closing + 1
    }
    return symbols.takeIf { it.isNotEmpty() }
}

/** Pays standard colored/colorless symbols first, then generic mana from any remaining color. */
internal fun paySimpleManaCost(pool: Map<String, Int>, cost: String?, extraGeneric: Int = 0): Map<String, Int>? {
    val symbols = simpleManaSymbols(cost) ?: return null
    var next = pool.toMutableMap()
    for (symbol in symbols.filter { it in listOf("W", "U", "B", "R", "G", "C") }) {
        val available = next[symbol] ?: 0
        if (available <= 0) return null
        if (available == 1) next.remove(symbol) else next[symbol] = available - 1
    }
    val generic = symbols.filter { symbol -> symbol.all { it in '0'..'9' } }.sumOf { it.toIntOrNull() ?: 0 } + extraGeneric.coerceAtLeast(0)
    return spendGenericMana(next, generic)
}

internal fun nextTurnIndex(currentIndex: Int, turnOrder: List<Int>, playerCount: Int, eligiblePlayers: Set<Int> = (0 until playerCount).toSet()): Int {
    if (playerCount <= 0) return 0
    val validOrder = turnOrder.takeIf { it.size == playerCount && it.toSet() == (0 until playerCount).toSet() }
        ?: (0 until playerCount).toList()
    val eligibleOrder = validOrder.filter { it in eligiblePlayers }
    if (eligibleOrder.isEmpty()) return currentIndex.coerceIn(0, playerCount - 1)
    val orderIndex = eligibleOrder.indexOf(currentIndex)
    return eligibleOrder[if (orderIndex < 0) 0 else (orderIndex + 1) % eligibleOrder.size]
}

internal data class TurnProgression(
    val phaseIndex: Int,
    val activePlayer: Int,
    val turnNumber: Int,
    val startedNewTurn: Boolean
)

internal fun advanceTurnProgression(
    phaseIndex: Int,
    lastPhaseIndex: Int,
    activePlayer: Int,
    turnNumber: Int,
    turnOrder: List<Int>,
    playerCount: Int,
    eligiblePlayers: Set<Int> = (0 until playerCount).toSet()
): TurnProgression {
    if (phaseIndex < lastPhaseIndex) return TurnProgression(phaseIndex + 1, activePlayer, turnNumber, false)
    return TurnProgression(0, nextTurnIndex(activePlayer, turnOrder, playerCount, eligiblePlayers), turnNumber + 1, true)
}
