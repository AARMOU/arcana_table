package com.arcanatable

internal data class AutomaticTokenSpec(
    val count: Int,
    val name: String,
    val type: String,
    val power: String? = null,
    val toughness: String? = null,
    val colors: List<String> = emptyList(),
    val keywords: List<String> = emptyList()
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

/** Validates known prerequisites before an automatic effect mutates any state. */
internal fun canApplyAutomaticEffects(librarySize: Int, effects: AutomaticResolutionEffects): Boolean =
    !effects.requiresManualReview && effects.cardsDrawn <= librarySize.coerceAtLeast(0)

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
    val controllerFilter: String? = null,
    /** The object that must cause the event, when Oracle wording names one. */
    val subjectFilter: String? = null,
    /** Turn structure trigger such as UPKEEP, BEGINNING_COMBAT, or END_STEP. */
    val phaseFilter: String? = null
)

/** A normalized action observed by the table. Unknown actions remain manual. */
internal data class AssistanceEvent(
    val kind: String,
    val actorIndex: Int? = null,
    val subjectType: String? = null,
    val subjectName: String? = null,
    val phase: String? = null,
    val targetIndex: Int? = null,
    val amount: Int? = null,
    val sourceCardId: Int? = null,
    val subjectCardId: Int? = null
)

internal data class TargetedAutomaticEffect(
    val targetRule: String,
    val effectText: String
)

data class TargetCountRange(val minimum: Int, val maximum: Int)

/**
 * Estimates the number of target choices from card text. This deliberately
 * does not attempt to decide whether any particular object is legal; players
 * confirm legality using the card's actual wording in the picker.
 */
internal fun parseTargetCountRange(text: String?): TargetCountRange? {
    val oracle = text.orEmpty()
    if (!Regex("\\btarget\\b", RegexOption.IGNORE_CASE).containsMatchIn(oracle)) return null
    val numberWords = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
    val group = Regex("(?i)(?:(up to|any number of)\\s+)?(?:(one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s+)?target\\b")
    var minimum = 0
    var maximum = 0
    group.findAll(oracle).forEach { match ->
        val qualifier = match.groupValues[1].lowercase()
        val amount = match.groupValues[2].toIntOrNull() ?: numberWords[match.groupValues[2].lowercase()] ?: 1
        if (qualifier.isBlank()) minimum += amount
        maximum += if (qualifier == "any number of") 99 else amount
    }
    return TargetCountRange(minimum.coerceAtMost(99), maximum.coerceAtMost(99))
}

data class StackTarget(
    val key: String,
    val label: String,
    val category: String,
    val ownerId: String? = null
)

/** Target classes parsed from ordinary Oracle target phrases; restrictions remain player-verified. */
internal data class TargetCategoryHints(val categories: Set<String>, val description: String)

internal fun parseTargetCategoryHints(text: String?): TargetCategoryHints? {
    val oracle = text.orEmpty()
    if (!Regex("(?i)\\btarget\\b|\\bany target\\b").containsMatchIn(oracle)) return null
    val categories = linkedSetOf<String>()
    if (Regex("(?i)\\bany target\\b").containsMatchIn(oracle)) {
        categories += setOf("PLAYER", "CREATURE", "PLANESWALKER", "BATTLE")
    }
    val normalizedGroups = Regex(
        "(?i)\\btarget\\s+(?:[a-z-]+\\s+){0,3}(player|opponent|creature|artifact|enchantment|land|planeswalker|battle|permanent|spell|ability|card)\\b"
    ).findAll(oracle).map { it.groupValues[1].uppercase() }.toList()
    // Include alternatives in phrases such as "target creature or planeswalker".
    val alternatives = Regex("(?i)\\btarget\\b[^.]*").findAll(oracle).flatMap { match ->
        Regex("(?i)\\b(player|opponent|creature|artifact|enchantment|land|planeswalker|battle|permanent|spell|ability|card)\\b")
            .findAll(match.value.substringAfter(Regex("(?i)\\btarget\\b").find(match.value)?.value.orEmpty()))
            .map { it.groupValues[1].uppercase() }
    }.toList()
    (normalizedGroups + alternatives).forEach { target ->
        when (target) {
            "PLAYER", "OPPONENT" -> categories += "PLAYER"
            "PERMANENT" -> categories += setOf("CREATURE", "ARTIFACT", "ENCHANTMENT", "LAND", "PLANESWALKER", "BATTLE")
            "SPELL" -> categories += "STACK · SPELL"
            "ABILITY" -> categories += "STACK · ABILITY"
            "CARD" -> {
                val nearby = oracle.lowercase().substringAfter("target card").substringBefore(".").substringBefore(" to ")
                if ("graveyard" in nearby) categories += "GRAVEYARD"
                if ("exile" in nearby) categories += "EXILE"
                if ("hand" in nearby) categories += "HAND"
                if ("library" in nearby) categories += "LIBRARY"
            }
            else -> categories += target
        }
    }
    if (categories.isEmpty()) return null
    return TargetCategoryHints(categories, categories.joinToString(" or ") { it.lowercase().replaceFirstChar { first -> first.uppercase() } })
}

internal fun targetCandidateLikelyMatches(candidate: StackTarget, hints: TargetCategoryHints): Boolean {
    val category = candidate.category.uppercase()
    return hints.categories.any { target ->
        when (target) {
            "PLAYER" -> category == "PLAYER"
            "STACK · SPELL" -> category.startsWith("STACK · SPELL")
            "STACK · ABILITY" -> category.startsWith("STACK · ABILITY")
            "GRAVEYARD", "EXILE", "HAND", "LIBRARY" -> category.startsWith(target)
            else -> category.startsWith("BATTLEFIELD") && category.contains(target)
        }
    }
}

/** Builds the visible game objects that can be reviewed as possible targets. */
internal fun buildTargetCandidates(
    players: List<TablePlayer>,
    stack: List<StackAction>,
    sourceStackItemId: String,
    actingPlayerId: String
): List<StackTarget> = buildList {
    players.forEach { player ->
        if (!player.isEliminated) add(StackTarget("player:${player.id}", player.name, "PLAYER", player.id))
        player.battlefield.filterNot { it.isFaceDown }.forEach { card ->
            add(StackTarget("battlefield:${player.id}:${card.id}", card.name, "BATTLEFIELD · ${card.type}", player.id))
        }
        player.graveyard.forEach { card -> add(StackTarget("graveyard:${player.id}:${card.id}", card.name, "GRAVEYARD", player.id)) }
        player.exile.forEach { card -> add(StackTarget("exile:${player.id}:${card.id}", card.name, "EXILE", player.id)) }
        player.revealed.filterNot { it.isFaceDown }.forEach { card -> add(StackTarget("revealed:${player.id}:${card.id}", card.name, "REVEALED", player.id)) }
        if (player.id == actingPlayerId || player.handRevealedToAll) {
            player.hand.filterNot { it.isFaceDown }.forEach { card -> add(StackTarget("hand:${player.id}:${card.id}", card.name, "HAND", player.id)) }
        }
        player.commanderCard?.let { card ->
            val alreadyListed = (player.library + player.hand + player.battlefield + player.graveyard + player.exile + player.revealed).any { it.id == card.id }
            if (!alreadyListed) add(StackTarget("command:${player.id}:${card.id}", card.name, "COMMAND ZONE", player.id))
        }
    }
    stack.filterNot { it.id == sourceStackItemId }.forEach { item ->
        val ownerName = players.firstOrNull { it.id == item.controllerId }?.name ?: "Unknown player"
        val isAbility = item.kind.contains("ABILITY", ignoreCase = true) || item.kind == "TRIGGER"
        val label = if (item.card?.isFaceDown == true) "Face-down ${if (isAbility) "ability" else "spell"} · $ownerName" else "${item.title} · $ownerName"
        add(StackTarget("stack:${item.id}", label, if (isAbility) "STACK · ABILITY" else "STACK · SPELL", item.controllerId))
    }
}

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
    val tokenPattern = Regex("(?i)^create\\s+$amountPattern\\s+(.+?)\\s+tokens?(?:\\s+with\\s+(.+))?$")
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
            val statLike = Regex("^(?:\\d+|\\*|x)/(?:\\d+|\\*|x)\\s+", RegexOption.IGNORE_CASE).containsMatchIn(description)
            val suffix = token.groupValues[3]
            val supportedKeywords = listOf("Flying", "First strike", "Double strike", "Deathtouch", "Defender", "Haste", "Hexproof", "Indestructible", "Lifelink", "Menace", "Reach", "Trample", "Vigilance", "Ward")
            val unexplainedSuffix = supportedKeywords.fold(suffix.lowercase()) { remaining, keyword ->
                remaining.replace(Regex("(?<![a-z])${Regex.escape(keyword.lowercase())}(?![a-z])(?:\\s+\\{[^}]+})?"), " ")
            }.replace(Regex("\\b(and|or|with)\\b|[{},.;]"), " ").trim()
            if ((!hasCreature && !isArtifact) || (statLike && stats == null) ||
                Regex("(?i)\\b(tapped|attacking|blocking|counter|counters|copy|copies|enchanted|equipped)\\b").containsMatchIn(description) ||
                unexplainedSuffix.isNotBlank()) return AutomaticResolutionEffects(requiresManualReview = true)
            val colorWords = mapOf("white" to "W", "blue" to "U", "black" to "B", "red" to "R", "green" to "G")
            val colors = description.split(Regex("[^A-Za-z]+"))
                .mapNotNull { colorWords[it.lowercase()] }
                .distinct()
            val keywordNames = listOf("Flying", "First strike", "Double strike", "Deathtouch", "Defender", "Haste", "Hexproof", "Indestructible", "Lifelink", "Menace", "Reach", "Trample", "Vigilance", "Ward")
            val fullText = "$description $suffix"
            val keywords = keywordNames.filter { Regex("(?i)(?<![A-Za-z])${Regex.escape(it)}(?![A-Za-z])").containsMatchIn(fullText) }
            val name = description
                .replace(Regex("^\\d+/\\d+\\s+"), "")
                .split(Regex("\\s+"))
                .filterNot { it.lowercase() in setOf("white", "blue", "black", "red", "green", "colorless", "and", "creature", "artifact", "enchantment", "land", "token", "tokens") }
                .joinToString(" ")
                .trim()
                .ifBlank { if (isArtifact) "Artifact" else "Creature" }
            val type = when {
                hasCreature -> "Creature Token"
                isArtifact -> "Artifact Token — $name"
                else -> "Token"
            }
            tokens += AutomaticTokenSpec(count, name, type, stats?.groupValues?.get(1), stats?.groupValues?.get(2), colors, keywords)
            continue
        }
        return AutomaticResolutionEffects(requiresManualReview = true)
    }
    return AutomaticResolutionEffects(lifeGain, lifeLoss, cardsDrawn, tokens)
}

/**
 * Reads the common rules-text shapes used by triggered abilities. This is a
 * deliberately conservative recognizer: it labels the event and leaves effect
 * choices and rules legality to the assistance flow instead of simulating text.
 */
internal fun parseRecognizedTriggeredAbilities(oracleText: String?): List<RecognizedTriggeredAbility> {
    val sentences = oracleText.orEmpty().lineSequence()
        .flatMap { line -> line.split(Regex("(?<=\\.)\\s+" )).asSequence() }
        .map { it.trim().trimEnd('.') }
        .filter { it.isNotBlank() }
    return sentences.mapNotNull { sentence ->
        val comma = sentence.indexOf(',')
        if (comma < 0) return@mapNotNull null
        val rawCondition = sentence.substringBefore(',').trim()
        // Oracle often prefixes a trigger with a named ability label, e.g.
        // "Landfall — Whenever a land ...". Parse the actual event clause.
        val condition = rawCondition.substringAfter("—", rawCondition).trim()
        val effect = sentence.substring(comma + 1).trim()
        if (effect.isBlank()) return@mapNotNull null

        fun actor(raw: String): String = when (raw.lowercase()) {
            "you", "your" -> "YOU"
            "an opponent", "opponent's", "an opponent's", "each opponent's" -> "OPPONENT"
            "a player", "each player", "a player's", "each player's" -> "ANY"
            else -> "ANY"
        }

        val cast = Regex("(?i)^(whenever|when)\\s+(you|a player|an opponent)\\s+casts?\\s+(.+)$").matchEntire(condition)
        if (cast != null && (cast.groupValues[3].contains("spell", true) || cast.groupValues[3].contains("cast", true))) {
            return@mapNotNull RecognizedTriggeredAbility("CAST", actor(cast.groupValues[2]), condition, effect,
                subjectFilter = cast.groupValues[3].takeIf { it.isNotBlank() })
        }

        val draw = Regex("(?i)^(whenever|when)\\s+(you|a player|an opponent)\\s+draws?\\s+(.+)$").matchEntire(condition)
        if (draw != null && Regex("(?i)(a|one|another|second|third|fourth|first)\\s+card|cards").containsMatchIn(draw.groupValues[3])) {
            return@mapNotNull RecognizedTriggeredAbility("DRAW", actor(draw.groupValues[2]), condition, effect,
                subjectFilter = draw.groupValues[3])
        }

        val enters = Regex("(?i)^(whenever|when)\\s+(?:(another|a|an)\\s+)?(creature|artifact|enchantment|permanent|land|planeswalker|battle|token)\\s+enters(?:\\s+the battlefield)?(?:\\s+(under your control|under an opponent's control))?$")
            .matchEntire(condition)
        if (enters != null) {
            val objectType = enters.groupValues[3].uppercase()
            val event = when (objectType) {
                "CREATURE" -> "CREATURE_ETB"
                "LAND" -> "LAND_ETB"
                else -> "PERMANENT_ETB"
            }
            val controller = enters.groupValues[4].takeIf { it.isNotBlank() }?.lowercase()
            return@mapNotNull RecognizedTriggeredAbility(event, when (controller) {
                "under your control" -> "YOU"
                "under an opponent's control" -> "OPPONENT"
                else -> "ANY"
            }, condition, effect, anotherOnly = enters.groupValues[2].equals("another", true),
                controllerFilter = controller, subjectFilter = objectType)
        }
        val thisEnters = Regex("(?i)^(whenever|when)\\s+this\\s+(creature|artifact|enchantment|permanent|land|planeswalker|battle)\\s+enters(?:\\s+the battlefield)?$")
            .matchEntire(condition)
        if (thisEnters != null) {
            val objectType = thisEnters.groupValues[2].uppercase()
            val event = when (objectType) {
                "CREATURE" -> "CREATURE_ETB"
                "LAND" -> "LAND_ETB"
                else -> "PERMANENT_ETB"
            }
            return@mapNotNull RecognizedTriggeredAbility(event, "YOU", condition, effect, subjectFilter = "this $objectType")
        }

        val dies = Regex("(?i)^(whenever|when)\\s+(?:(another|a|an)\\s+)?(creature|artifact|permanent|token|planeswalker)(?:\\s+(you control|an opponent controls))?\\s+dies$").matchEntire(condition)
        if (dies != null) {
            val controller = dies.groupValues[4].takeIf { it.isNotBlank() }?.lowercase()
            return@mapNotNull RecognizedTriggeredAbility("DIES", when (controller) {
                "you control" -> "YOU"
                "an opponent controls" -> "OPPONENT"
                else -> "ANY"
            }, condition, effect, anotherOnly = dies.groupValues[2].equals("another", true), controllerFilter = controller,
                subjectFilter = dies.groupValues[3].uppercase())
        }
        val thisDies = Regex("(?i)^(whenever|when)\\s+this\\s+(creature|artifact|permanent|token|planeswalker)\\s+dies$").matchEntire(condition)
        if (thisDies != null) return@mapNotNull RecognizedTriggeredAbility(
            "DIES", "YOU", condition, effect, subjectFilter = "this ${thisDies.groupValues[2]}"
        )

        val leaves = Regex("(?i)^(whenever|when)\\s+(?:(another|a|an)\\s+)?(creature|artifact|permanent|land|token|planeswalker|enchantment)(?:\\s+(you control|an opponent controls))?\\s+leaves the battlefield$").matchEntire(condition)
        if (leaves != null) {
            val controller = leaves.groupValues[4].takeIf { it.isNotBlank() }?.lowercase()
            return@mapNotNull RecognizedTriggeredAbility("LEAVES_BATTLEFIELD", when (controller) {
                "you control" -> "YOU"
                "an opponent controls" -> "OPPONENT"
                else -> "ANY"
            }, condition, effect, anotherOnly = leaves.groupValues[2].equals("another", true), controllerFilter = controller,
                subjectFilter = leaves.groupValues[3].uppercase())
        }
        val thisLeaves = Regex("(?i)^(whenever|when)\\s+this\\s+(creature|artifact|permanent|land|token|planeswalker|enchantment)\\s+leaves the battlefield$").matchEntire(condition)
        if (thisLeaves != null) return@mapNotNull RecognizedTriggeredAbility(
            "LEAVES_BATTLEFIELD", "YOU", condition, effect, subjectFilter = "this ${thisLeaves.groupValues[2]}"
        )

        val attacks = Regex("(?i)^(whenever|when)\\s+(.+?)\\s+attacks?(?:\\s+alone)?(?:\\s+you)?$").matchEntire(condition)
        if (attacks != null) {
            val subject = attacks.groupValues[2]
            val actorFilter = when {
                subject.equals("you", true) || subject.contains("you control", true) -> "YOU"
                subject.contains("opponent", true) -> "OPPONENT"
                else -> "ANY"
            }
            return@mapNotNull RecognizedTriggeredAbility("ATTACKS", actorFilter, condition, effect,
                anotherOnly = subject.contains("another", true), subjectFilter = subject)
        }

        val blocks = Regex("(?i)^(whenever|when)\\s+(.+?)\\s+blocks?(?:\\s+(.+))?$").matchEntire(condition)
        if (blocks != null) {
            val subject = blocks.groupValues[2]
            val actorFilter = when {
                subject.equals("you", true) || subject.contains("you control", true) -> "YOU"
                subject.contains("opponent", true) -> "OPPONENT"
                else -> "ANY"
            }
            return@mapNotNull RecognizedTriggeredAbility("BLOCKS", actorFilter, condition, effect,
                anotherOnly = subject.contains("another", true), subjectFilter = subject)
        }

        val damage = Regex("(?i)^(whenever|when)\\s+(.+?)\\s+(deals?|is dealt)\\s+(combat )?damage(?:\\s+to\\s+(.+))?$").matchEntire(condition)
        if (damage != null) {
            val subject = damage.groupValues[2]
            val actorFilter = when {
                subject.contains("you control", true) -> "YOU"
                subject.contains("opponent controls", true) || subject.contains("opponent's", true) -> "OPPONENT"
                else -> "ANY"
            }
            return@mapNotNull RecognizedTriggeredAbility(if (damage.groupValues[4].isBlank()) "DAMAGE" else "COMBAT_DAMAGE", actorFilter, condition, effect,
                subjectFilter = subject)
        }

        val phase = Regex("(?i)^at the beginning of (your|each player's|an opponent's|each opponent's) (upkeep|draw step|(?:precombat |postcombat |first )?main phase|combat(?: phase)?(?: on your turn)?|end combat|end step|cleanup)$")
            .matchEntire(condition)
        if (phase != null) {
            val owner = actor(phase.groupValues[1])
            val phaseName = when (phase.groupValues[2].lowercase()) {
                "upkeep" -> "UPKEEP"
                "draw step" -> "DRAW"
                "precombat main phase", "first main phase" -> "PRECOMBAT_MAIN"
                "postcombat main phase" -> "POSTCOMBAT_MAIN"
                "main phase" -> "PRECOMBAT_MAIN"
                "combat", "combat phase", "combat on your turn", "combat phase on your turn" -> "BEGIN_COMBAT"
                "end combat" -> "END_COMBAT"
                "end step" -> "END_STEP"
                else -> "CLEANUP"
            }
            return@mapNotNull RecognizedTriggeredAbility("PHASE", owner, condition, effect, phaseFilter = phaseName)
        }
        if (Regex("(?i)^at the beginning of (your|each player's|an opponent's|each opponent's) turn$").matches(condition)) {
            val who = condition.substringAfter("beginning of ").substringBefore(" turn")
            return@mapNotNull RecognizedTriggeredAbility("PHASE", actor(who), condition, effect, phaseFilter = "BEGIN_TURN")
        }

        val life = Regex("(?i)^(whenever|when)\\s+(you|a player|an opponent)\\s+(gains?|loses?)\\s+life$").matchEntire(condition)
        if (life != null) return@mapNotNull RecognizedTriggeredAbility(if (life.groupValues[3].startsWith("gain", true)) "LIFE_GAIN" else "LIFE_LOSS", actor(life.groupValues[2]), condition, effect)

        val landfall = Regex("(?i)^landfall$").matchEntire(condition)
        if (landfall != null) return@mapNotNull RecognizedTriggeredAbility("LAND_ETB", "YOU", condition, effect, subjectFilter = "LAND")

        val genericTap = Regex("(?i)^(whenever|when)\\s+(this|a|an|another|one or more)\\s+(creature|permanent|artifact|land)(?:\\s+(you control|an opponent controls))?\\s+becomes? (tapped|untapped)$").matchEntire(condition)
        if (genericTap != null) return@mapNotNull RecognizedTriggeredAbility(if (genericTap.groupValues[5].equals("tapped", true)) "BECOMES_TAPPED" else "BECOMES_UNTAPPED", when (genericTap.groupValues[4].lowercase()) {
            "you control" -> "YOU"
            "an opponent controls" -> "OPPONENT"
            else -> "ANY"
        }, condition, effect,
            anotherOnly = genericTap.groupValues[2].equals("another", true), controllerFilter = genericTap.groupValues[4].takeIf { it.isNotBlank() }?.lowercase(),
            subjectFilter = if (genericTap.groupValues[2].equals("this", true)) "this ${genericTap.groupValues[3]}" else genericTap.groupValues[3].uppercase())

        val sacrifice = Regex("(?i)^(whenever|when)\\s+(you|a player|an opponent)\\s+sacrifices?\\s+(.+)$").matchEntire(condition)
        if (sacrifice != null) return@mapNotNull RecognizedTriggeredAbility("SACRIFICE", actor(sacrifice.groupValues[2]), condition, effect,
            subjectFilter = sacrifice.groupValues[3])

        val counters = Regex("(?i)^(whenever|when)\\s+(one or more|a|another)\\s+(.+?)\\s+counters?\\s+(?:are|is)\\s+put on\\s+(.+)$").matchEntire(condition)
        if (counters != null) return@mapNotNull RecognizedTriggeredAbility("COUNTER_ADDED", "ANY", condition, effect,
            anotherOnly = counters.groupValues[2].equals("another", true), subjectFilter = counters.groupValues[4])

        val discards = Regex("(?i)^(whenever|when)\\s+(you|a player|an opponent)\\s+discards?\\s+(.+)$").matchEntire(condition)
        if (discards != null) return@mapNotNull RecognizedTriggeredAbility("DISCARD", actor(discards.groupValues[2]), condition, effect,
            subjectFilter = discards.groupValues[3])
        null
    }.toList()
}

/** Checks a recognized trigger against the table event without inventing legality. */
internal fun matchesAssistanceEvent(
    ability: RecognizedTriggeredAbility,
    event: AssistanceEvent,
    controllerIndex: Int
): Boolean {
    val eventMatches = when (ability.event) {
        "PERMANENT_ETB" -> event.kind in setOf("PERMANENT_ETB", "CREATURE_ETB", "LAND_ETB")
        "CREATURE_ETB" -> event.kind == "CREATURE_ETB"
        "LAND_ETB" -> event.kind == "LAND_ETB"
        "PHASE" -> event.kind == "PHASE" && (ability.phaseFilter == null || ability.phaseFilter == event.phase)
        else -> ability.event == event.kind
    }
    if (!eventMatches) return false
    val actorMatches = when (ability.actorFilter) {
        "YOU" -> controllerIndex == event.actorIndex
        "OPPONENT" -> event.actorIndex != null && controllerIndex != event.actorIndex
        else -> true
    }
    if (!actorMatches) return false
    if (ability.event == "ATTACKS" && ability.conditionText.contains("attacks you", true) && controllerIndex != event.targetIndex) return false
    if (ability.event in setOf("DAMAGE", "COMBAT_DAMAGE")) {
        val condition = ability.conditionText
        if (condition.contains("damage to you", true) && controllerIndex != event.targetIndex) return false
        if (condition.contains("damage to an opponent", true) && controllerIndex == event.targetIndex) return false
    }
    if (ability.controllerFilter == "under your control" && controllerIndex != event.actorIndex) return false
    if (ability.controllerFilter == "under an opponent's control" && controllerIndex == event.actorIndex) return false
    if (ability.anotherOnly && event.sourceCardId != null && event.sourceCardId == event.subjectCardId) return false
    val subject = ability.subjectFilter?.lowercase()
        ?.replace(Regex("^(?:a|an|another|one or more)\\s+"), "")
        .orEmpty()
    if (subject.startsWith("this ") && event.subjectCardId != null && event.subjectCardId != event.sourceCardId) return false
    val subjectType = event.subjectType?.lowercase().orEmpty()
    if (subject.isNotBlank() && subjectType.isNotBlank()) {
        val typeMatches = when {
            "permanent" in subject -> true
            "creature" in subject -> "creature" in subjectType
            "land" in subject -> "land" in subjectType
            "artifact" in subject -> "artifact" in subjectType
            "enchantment" in subject -> "enchantment" in subjectType
            "planeswalker" in subject -> "planeswalker" in subjectType
            "battle" in subject -> "battle" in subjectType
            "noncreature" in subject -> "creature" !in subjectType
            "spell" in subject -> "spell" in categoryForAssistanceEvent(event.kind)
            "token" in subject -> event.subjectName?.contains("token", true) == true
            else -> true
        }
        if (!typeMatches) return false
    }
    return true
}

private fun categoryForAssistanceEvent(eventKind: String): String = when {
    eventKind.contains("SPELL") || eventKind == "CAST" -> "spell"
    else -> eventKind.lowercase()
}

/**
 * A lightweight explanation used for logs and diagnostics. A value below 1.0
 * means one or more clauses need player handling; it is not a rules-accuracy
 * claim and must never be treated as permission to apply a partial effect.
 */
internal data class AssistanceAssessment(
    val recognizedClauses: Int,
    val manualClauses: Int,
    val triggerClauses: Int,
    val unsupportedClauses: List<String>
) {
    val recognizedRatio: Double get() = if (recognizedClauses + manualClauses == 0) 1.0
        else recognizedClauses.toDouble() / (recognizedClauses + manualClauses)
    val hasPlayerHandledClauses: Boolean get() = manualClauses > 0
}

/** Classifies the text coverage before resolving; unknown text is always manual. */
internal fun assessOracleText(oracleText: String?): AssistanceAssessment {
    val text = oracleText.orEmpty().trim()
    if (text.isBlank()) return AssistanceAssessment(0, 0, 0, emptyList())
    val clauses = text.lineSequence()
        .flatMap { line -> line.split(Regex("(?<=\\.)\\s+|\\n+|(?<=:)\\s+(?=[A-Z])")).asSequence() }
        .map { it.trim().trimEnd('.', ' ') }
        .filter { it.isNotBlank() && !it.startsWith("//") }
        .toList()
    val recognized = mutableListOf<String>()
    val unsupported = mutableListOf<String>()
    clauses.forEach { clause ->
        val lowered = clause.lowercase()
        val simple = Regex("(?i)^(?:you\\s+)?(?:gain|lose)\\s+(?:\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten)\\s+life$").matches(clause) ||
            Regex("(?i)^(?:you\\s+)?draw\\s+(?:\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten)\\s+cards?$").matches(clause) ||
            Regex("(?i)^create\\s+(?:\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten)\\s+.+\\s+tokens?$" ).matches(clause)
        val trigger = parseRecognizedTriggeredAbilities(clause).isNotEmpty() ||
            Regex("(?i)^(?:whenever|when|at the beginning of)\\b").containsMatchIn(clause)
        val keyword = Regex("(?i)^(?:flying|first strike|double strike|deathtouch|defender|haste|hexproof|indestructible|lifelink|menace|reach|trample|vigilance|ward)(?:\\s+.+)?$").matches(clause)
        when {
            simple || trigger || keyword -> recognized += clause
            // Static abilities are surfaced as understood text rather than executed.
            lowered.startsWith("as this enters") || lowered.startsWith("you may") || lowered.startsWith("you can't") ||
                lowered.startsWith("you cannot") || lowered.startsWith("players can't") || lowered.startsWith("spells can't") -> recognized += clause
            else -> unsupported += clause
        }
    }
    val triggerCount = clauses.count { Regex("(?i)^(?:whenever|when|at the beginning of)\\b").containsMatchIn(it) }
    return AssistanceAssessment(recognized.size, unsupported.size, triggerCount, unsupported)
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

internal data class StackPriorityAdvance(
    val nextPlayer: Int,
    val passedPlayers: Set<String>,
    val returnedToOrigin: Boolean
)

/** Advances one pass around the table and ends the response cycle when priority returns to the stack actor. */
internal fun advanceStackPriority(
    currentPlayer: Int,
    originPlayer: Int,
    passedPlayers: Set<String>,
    playerIds: List<String>,
    turnOrder: List<Int>,
    eligiblePlayers: Set<Int>
): StackPriorityAdvance {
    if (playerIds.isEmpty()) return StackPriorityAdvance(0, passedPlayers, false)
    val current = currentPlayer.coerceIn(playerIds.indices)
    val validEligible = eligiblePlayers.filter { it in playerIds.indices }.toSet()
    val next = nextTurnIndex(current, turnOrder, playerIds.size, validEligible)
    val id = playerIds[current]
    val passed = if (id.isBlank()) passedPlayers else passedPlayers + id
    val origin = originPlayer.takeIf { it in validEligible }
    val responders = validEligible.filter { it != origin }.map { playerIds[it] }
    val returned = origin != null && next == origin && responders.all { it in passed }
    return StackPriorityAdvance(next, passed, returned)
}

internal fun isLastEligibleTurnInRound(currentIndex: Int, turnOrder: List<Int>, playerCount: Int, eligiblePlayers: Set<Int>): Boolean {
    if (playerCount <= 0) return true
    val validOrder = turnOrder.takeIf { it.size == playerCount && it.toSet() == (0 until playerCount).toSet() }
        ?: (0 until playerCount).toList()
    val turnIndex = validOrder.indexOf(currentIndex)
    return turnIndex < 0 || validOrder.drop(turnIndex + 1).none { it in eligiblePlayers }
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
    val completedRound = isLastEligibleTurnInRound(activePlayer, turnOrder, playerCount, eligiblePlayers)
    return TurnProgression(
        0,
        nextTurnIndex(activePlayer, turnOrder, playerCount, eligiblePlayers),
        turnNumber + if (completedRound) 1 else 0,
        true
    )
}
