package com.arcanatable

import java.util.Locale

/**
 * Quick filters for searching your library in a game: the engine's tutor choices (Argentum's
 * SearchLibraryDecision) and the manual table's library browser. They only narrow and arrange
 * what's shown; the engine still decides which cards are legal picks. Pure Kotlin for tests.
 */
enum class LibraryChip(val label: String) {
    BASIC_LAND("Basic land"), LAND("Land"), CREATURE("Creature"), ARTIFACT("Artifact"), ENCHANTMENT("Enchantment"),
    INSTANT("Instant"), SORCERY("Sorcery"), PLANESWALKER("Planeswalker"), LEGENDARY("Legendary");

    fun matches(typeLine: String): Boolean {
        val t = typeLine.substringBefore(" // ").lowercase(Locale.ROOT)
        return when (this) {
            BASIC_LAND -> "basic" in t && "land" in t
            LAND -> "land" in t
            else -> name.lowercase(Locale.ROOT) in t
        }
    }

    companion object {
        /** Chips worth showing for these cards, with how many each matches. A chip matching all of them adds nothing. */
        fun present(typeLines: List<String>): List<Pair<LibraryChip, Int>> = entries
            .map { chip -> chip to typeLines.count(chip::matches) }
            .filter { (_, n) -> n > 0 && n < typeLines.size }
    }
}

enum class LibrarySort(val label: String) { TYPE("By type"), NAME("A–Z"), MANA_VALUE("Mana value") }

/** The heading a card sits under when grouped by type, in the order players usually scan. */
fun libraryGroupOf(typeLine: String): String {
    val t = typeLine.substringBefore(" // ").lowercase(Locale.ROOT)
    return when {
        t.isBlank() -> "Other"
        "basic" in t && "land" in t -> "Basic lands"
        "land" in t -> "Lands"
        "creature" in t -> "Creatures"
        "planeswalker" in t -> "Planeswalkers"
        "battle" in t -> "Battles"
        "instant" in t -> "Instants"
        "sorcery" in t -> "Sorceries"
        "artifact" in t -> "Artifacts"
        "enchantment" in t -> "Enchantments"
        else -> "Other"
    }
}

val LIBRARY_GROUP_ORDER = listOf("Basic lands", "Lands", "Creatures", "Planeswalkers", "Battles", "Instants", "Sorceries", "Artifacts", "Enchantments", "Other")

/**
 * Applies a chip and sort to [items]. Cards in [keep] (already selected) always stay, so a chip
 * can never hide a choice you've made. Returns the items with their group heading.
 */
fun <T> arrangeLibrary(
    items: List<T>,
    chip: LibraryChip?,
    sort: LibrarySort,
    keep: (T) -> Boolean,
    name: (T) -> String,
    typeLine: (T) -> String,
    manaValue: (T) -> Double?,
): List<Pair<String, T>> {
    val shown = items.filter { chip == null || chip.matches(typeLine(it)) || keep(it) }
    val byName = compareBy<T> { name(it).lowercase(Locale.ROOT) }
    return when (sort) {
        LibrarySort.TYPE -> shown.sortedWith(compareBy<T> { LIBRARY_GROUP_ORDER.indexOf(libraryGroupOf(typeLine(it))) }.thenBy { manaValue(it) ?: 0.0 }.then(byName))
            .map { libraryGroupOf(typeLine(it)) to it }
        LibrarySort.NAME -> shown.sortedWith(byName).map { "Library" to it }
        LibrarySort.MANA_VALUE -> shown.sortedWith(compareBy<T> { manaValue(it) ?: 99.0 }.then(byName))
            .map { (manaValue(it)?.let { mv -> "Mana value ${fmtNumber(mv)}" } ?: "Other") to it }
    }
}
