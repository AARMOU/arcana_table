package com.arcanatable

import java.util.Locale

/** Import zones are deliberately separate from the game's single-commander deck model. */
data class DeckImportDiagnostic(val line: Int, val original: String, val message: String) {
    override fun toString(): String = "Line $line: $message — $original"
}

data class ParsedDeckList(
    val main: List<DeckEntry>,
    val commanders: List<DeckEntry>,
    val companion: List<DeckEntry>,
    val sideboard: List<DeckEntry>,
    val excluded: List<DeckEntry>,
    val deckName: String?,
    val diagnostics: List<DeckImportDiagnostic>,
    val partnerPairEligible: Boolean? = null,
) {
    /** Compatibility API: ordinary callers receive playable main and commander cards only. */
    val playableEntries: List<DeckEntry> get() = commanders + main

    /**
     * The Android game/persistence path currently supports only one commander, and no companion.
     * Never convert these zones into a game deck until full command-zone support is available.
     */
    val gameBlockers: List<String> get() = buildList {
        if (commanders.sumOf { it.count } > 1) add("Two commanders require game and save support; both were preserved in the import preview.")
        if (companion.isNotEmpty()) add("Companion is outside the starting deck and cannot yet be used in games.")
        if (diagnostics.isNotEmpty()) add("Correct the import lines reported with diagnostics before playing.")
    }
}

private enum class ImportZone { MAIN, COMMANDER, COMPANION, SIDEBOARD, EXCLUDED }
private data class ImportRow(val entry: DeckEntry, val zone: ImportZone, val line: Int)

private val importerCategoryNames = setOf(
    "creatures", "instants", "sorceries", "artifacts", "enchantments", "lands",
    "planeswalkers", "battles", "other", "others", "other spells",
    "tokens", "noncreatures", "ramp", "removal", "draw", "interaction",
    "mana base", "mana", "protection", "utility", "board wipes"
)
private val importerSections = mapOf(
    "deck" to ImportZone.MAIN, "main" to ImportZone.MAIN,
    "mainboard" to ImportZone.MAIN, "main deck" to ImportZone.MAIN,
    "commander" to ImportZone.COMMANDER, "commanders" to ImportZone.COMMANDER,
    "companion" to ImportZone.COMPANION, "companions" to ImportZone.COMPANION,
    "sideboard" to ImportZone.SIDEBOARD, "sideboard cards" to ImportZone.SIDEBOARD,
    "maybeboard" to ImportZone.EXCLUDED, "considering" to ImportZone.EXCLUDED,
    "excluded" to ImportZone.EXCLUDED, "maybe" to ImportZone.EXCLUDED
)

private fun parseImporterCsv(line: String): List<String> {
    val output = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        when (line[i]) {
            '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') { cell.append('"'); i++ } else quoted = !quoted
            ',' -> if (!quoted) { output += cell.toString().trim(); cell.clear() } else cell.append(',')
            else -> cell.append(line[i])
        }
        i++
    }
    output += cell.toString().trim()
    return output
}

private fun printingFrom(text: String): Triple<String, String?, String?> {
    val paren = Regex("""^(.*?)\s+\(([A-Za-z0-9]{2,6})\)\s+#?(\d+[A-Za-z]?)$""").matchEntire(text)
    if (paren != null) return Triple(paren.groupValues[1].trim(), paren.groupValues[2], paren.groupValues[3])
    val bracket = Regex("""^(.*?)\s+\[([A-Za-z0-9]{2,6})\]\s+#?(\d+[A-Za-z]?)$""").matchEntire(text)
    if (bracket != null) return Triple(bracket.groupValues[1].trim(), bracket.groupValues[2], bracket.groupValues[3])
    val deckstats = Regex("""^(.*?)\s+\[([A-Za-z0-9]{2,6})#(\d+[A-Za-z]?)\]$""").matchEntire(text)
    if (deckstats != null) return Triple(deckstats.groupValues[1].trim(), deckstats.groupValues[2], deckstats.groupValues[3])
    val setOnly = Regex("""^(.*?)\s+\(([A-Za-z0-9]{2,6})\)$""").matchEntire(text)
    if (setOnly != null) return Triple(setOnly.groupValues[1].trim(), setOnly.groupValues[2], null)
    return Triple(text, null, null)
}

private fun isCommanderCandidate(card: SupportedCard?): Boolean =
    card != null && (
        ("legendary" in card.typeLine.lowercase(Locale.ROOT) && "creature" in card.typeLine.lowercase(Locale.ROOT)) ||
        card.oracleText.orEmpty().contains("can be your commander", ignoreCase = true)
    )

/**
 * Conservative pair eligibility; an unknown catalog entry is NOT approval.
 * This does not assert comprehensive Commander format legality.
 */
private fun hasLegalCommanderPair(names: List<String>, catalog: SupportedCardCatalog): Boolean {
    if (names.size != 2 || names[0].equals(names[1], true)) return false
    val a = catalog.find(names[0])
    val b = catalog.find(names[1])
    if (a == null) return false
    val aText = a.oracleText.orEmpty().lowercase(Locale.ROOT)
    val bText = b?.oracleText.orEmpty().lowercase(Locale.ROOT).orEmpty()
    val bType = b?.typeLine.orEmpty().lowercase(Locale.ROOT)
    if (aText.contains("choose a background") &&
        bType.contains("legendary") && bType.contains("background")) return true
    val aType = a.typeLine.lowercase(Locale.ROOT)
    if (bText.contains("choose a background") &&
        aType.contains("legendary") && aType.contains("background")) return true
    if (!isCommanderCandidate(a) || !isCommanderCandidate(b)) return false
    val partner = Regex("""(?m)^partner(?:\s*\([^\n]*\))?\s*$""", RegexOption.IGNORE_CASE)
    if (partner.containsMatchIn(aText) && partner.containsMatchIn(bText)) return true
    if (aText.contains("friends forever") && bText.contains("friends forever")) return true
    val aPartnerWith = "partner with " + names[1].lowercase(Locale.ROOT)
    val bPartnerWith = "partner with " + names[0].lowercase(Locale.ROOT)
    return aText.contains(aPartnerWith) && bText.contains(bPartnerWith)
}

/**
 * Parse rather than discard separate zones, keeping source line diagnostics.
 * Optional catalog enables conservative MTGO/Moxfield implicit commander detection.
 * No arbitrary four-copy cap is applied.
 */
fun parseDeckListDetailed(raw: String, catalog: SupportedCardCatalog? = null): ParsedDeckList {
    val rows = mutableListOf<ImportRow>()
    val errors = mutableListOf<DeckImportDiagnostic>()
    var zone = ImportZone.MAIN
    var deckName: String? = null
    var lastBlankLine = 0
    var csvColumns: Map<String, Int>? = null
    val lines = raw.lines()

    fun append(nameRaw: String, count: Int?, set: String?, collector: String?, selected: ImportZone, line: Int, original: String) {
        val name = nameRaw.trim().trim('"', '\'').trim()
        if (count == null || count !in 1..999 || name.isEmpty() || name.matches(Regex("""\d+"""))) {
            errors += DeckImportDiagnostic(line, original, "Invalid card name or quantity")
            return
        }
        if (name.contains(':') && !name.contains(" // ")) {
            errors += DeckImportDiagnostic(line, original, "Unexpected metadata or malformed card name")
            return
        }
        rows += ImportRow(DeckEntry(name, count, set?.trim()?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT),
            collector?.trim()?.removePrefix("#")?.takeIf(String::isNotBlank), selected == ImportZone.COMMANDER), selected, line)
    }

    lines.forEachIndexed { index, source ->
        val number = index + 1
        var line = source.trim().removePrefix("\uFEFF")
        if (line.isEmpty()) { lastBlankLine = number; return@forEachIndexed }
        if (line.startsWith("//") || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
        val heading = line.replace(Regex("""^(?:[xX]\s*)?\d+\s*[xX]?\s+"""), "")
            .trim().trim('[', ']', ':', ' ')
            .replace(Regex("""\s*\(\d+\)$"""), "").trim().lowercase(Locale.ROOT)
        importerSections[heading]?.let { zone = it; return@forEachIndexed }
        if (heading in importerCategoryNames) { zone = ImportZone.MAIN; return@forEachIndexed }
        if (line.equals("About", true)) return@forEachIndexed
        if (line.startsWith("Name ", true)) { deckName = line.substringAfter(' ').trim().ifBlank { null }; return@forEachIndexed }
        if (Regex("""^(format|created|updated|author|description|source|deckstats)\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(line)) return@forEachIndexed
        if (line.matches(Regex("""^\[[^\]]+]$""")) && !line.matches(Regex("""^\[[A-Za-z0-9]{2,6}#\d+[a-zA-Z]?\]$"""))) {
            // Archidekt arbitrary category header, not a card. Mark next rows as main.
            zone = ImportZone.MAIN
            return@forEachIndexed
        }
        if (line.startsWith("SB:", true)) { line = line.substringAfter(':').trim(); zone = ImportZone.SIDEBOARD }

        var explicitZone: ImportZone? = null
        if (line.startsWith("Commander:", true)) {
            explicitZone = ImportZone.COMMANDER
            line = line.substringAfter(':').trim()
        }
        if (line.contains("*CMDR*", true)) { explicitZone = ImportZone.COMMANDER; line = line.replace(Regex("""\*CMDR\*""", RegexOption.IGNORE_CASE), "").trim() }
        if (line.contains(Regex("""\[Commander(?:\{[^]]*})?]""", RegexOption.IGNORE_CASE))) {
            explicitZone = ImportZone.COMMANDER
            line = line.replace(Regex("""\[Commander(?:\{[^]]*})?]""", RegexOption.IGNORE_CASE), "").trim()
        }
        if (line.contains("{noDeck}", true)) {
            explicitZone = ImportZone.EXCLUDED
            line = line.replace(Regex("""\{noDeck}""", RegexOption.IGNORE_CASE), "").trim()
        }
        line = line.replace(Regex("""\*[FA]\*""", RegexOption.IGNORE_CASE), "").trim()
        line = line.replace(Regex("""\s+#(?!\d+\s*$)[\w-]+(?:\s+#[\w-]+)*\s*$"""), "").trim()
        line = line.replace(Regex("""\s+\[[A-Za-z][^\]#]{3,}\]\s*$"""), "").trim()

        val cells = parseImporterCsv(line)
        val lowerCells = cells.map { it.lowercase(Locale.ROOT).trim() }
        if (lowerCells.any { it in setOf("name", "card name") } &&
            lowerCells.any { it in setOf("count", "quantity", "amount") }) {
            csvColumns = lowerCells.withIndex().associate { it.value to it.index }
            return@forEachIndexed
        }
        val mapping = csvColumns
        if (mapping != null) {
            fun field(vararg names: String): String? = names.firstNotNullOfOrNull { mapping[it]?.let(cells::getOrNull) }
            val section = field("zone", "board", "section", "type").orEmpty().lowercase(Locale.ROOT)
            val csvZone = when {
                section.contains("commander") -> ImportZone.COMMANDER
                section.contains("companion") -> ImportZone.COMPANION
                section.contains("side") -> ImportZone.SIDEBOARD
                section.contains("maybe") || section.contains("exclude") -> ImportZone.EXCLUDED
                else -> ImportZone.MAIN
            }
            append(field("name", "card name").orEmpty(), field("count", "quantity", "amount")?.toIntOrNull(),
                field("set", "set code", "edition"), field("collector number", "collector_number", "number", "card number"),
                explicitZone ?: csvZone, number, source)
            return@forEachIndexed
        }

        val quantity = Regex("""^(?:[xX]\s*)?(\d+)\s*[xX]?\s+(.+)$""").matchEntire(line)
        var count = quantity?.groupValues?.get(1)?.toIntOrNull() ?: 1
        var cardPart = quantity?.groupValues?.get(2)?.trim() ?: line
        if (quantity == null && cells.size > 1 && cells[0].toIntOrNull() != null) {
            count = cells[0].toIntOrNull() ?: 0
            cardPart = cells[1].trim()
        }
        if (line.matches(Regex("""^\d+\s*$""")) || line.startsWith("http:", true) ||
            line.startsWith("https:", true) || line.startsWith("{")) {
            errors += DeckImportDiagnostic(number, source, "Unrecognized import line")
            return@forEachIndexed
        }
        val (name, set, collector) = printingFrom(cardPart)
        append(name, count, set, collector, explicitZone ?: zone, number, source)
    }

    // A 99+1 (or 98+2) Commander-shaped list is necessary, not sufficient.
    // Never infer from an ordinary 60+15 constructed deck, or without catalog proof.
    if (catalog != null && rows.none { it.zone == ImportZone.COMMANDER }) {
        val mainCount = rows.filter { it.zone == ImportZone.MAIN }.sumOf { it.entry.count }
        val side = rows.filter { it.zone == ImportZone.SIDEBOARD }
        val finalBlock = rows.filter { it.zone == ImportZone.MAIN && it.line > lastBlankLine }
        val potential = when {
            side.isNotEmpty() && side.sumOf { it.entry.count } in 1..2 &&
                mainCount + side.sumOf { it.entry.count } == 100 -> side
            finalBlock.isNotEmpty() && finalBlock.sumOf { it.entry.count } in 1..2 &&
                mainCount == 100 && finalBlock.size < rows.count { it.zone == ImportZone.MAIN } -> finalBlock
            else -> emptyList()
        }
        val inferred = potential.flatMap { row -> List(row.entry.count) { row.entry.name } }
        if (inferred.size == 1 && isCommanderCandidate(catalog.find(inferred.single())) ||
            inferred.size == 2 && hasLegalCommanderPair(inferred, catalog)) {
            val positions = potential.toSet()
            for (i in rows.indices) {
                if (rows[i] in positions) rows[i] = rows[i].copy(
                    entry = rows[i].entry.copy(commander = true), zone = ImportZone.COMMANDER)
            }
        }
    }
    fun grouped(selected: ImportZone): List<DeckEntry> {
        data class Key(val name: String, val set: String?, val collector: String?, val commander: Boolean)
        val values = linkedMapOf<Key, Int>()
        rows.filter { it.zone == selected }.forEach { row ->
            val e = row.entry
            val k = Key(e.name, e.setCode, e.collectorNumber, selected == ImportZone.COMMANDER)
            values[k] = (values[k] ?: 0) + e.count
        }
        return values.map { (k, quantity) -> DeckEntry(k.name, quantity, k.set, k.collector, k.commander) }
    }
    val leaders = grouped(ImportZone.COMMANDER)
    val eligibility = if (leaders.sumOf { it.count } == 2 && catalog != null)
        hasLegalCommanderPair(leaders.flatMap { e -> List(e.count) { e.name } }, catalog)
    else null
    return ParsedDeckList(
        main = grouped(ImportZone.MAIN),
        commanders = leaders, companion = grouped(ImportZone.COMPANION),
        sideboard = grouped(ImportZone.SIDEBOARD), excluded = grouped(ImportZone.EXCLUDED),
        deckName = deckName, diagnostics = errors, partnerPairEligible = eligibility,
    )
}

/** Bundled definitions supply offline card identity and metadata without claiming a particular printing. */
fun offlineImportMatches(entries: List<DeckEntry>, catalog: SupportedCardCatalog?): Map<DeckEntry, ScryfallCard> =
    if (catalog == null) emptyMap()
    else entries.mapNotNull { entry ->
        catalog.find(entry.name)?.toScryfallCard()?.let { entry to it }
    }.toMap()
