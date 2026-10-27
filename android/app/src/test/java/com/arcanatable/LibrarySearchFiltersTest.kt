package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-game library search: type chips, grouping and sorting never hide a card already chosen. */
class LibrarySearchFiltersTest {
    private data class C(val id: Int, val name: String, val type: String, val mv: Double?)
    private val items = listOf(
        C(1, "Forest", "Basic Land — Forest", 0.0), C(2, "Breeding Pool", "Land — Forest Island", 0.0),
        C(3, "Llanowar Elves", "Creature — Elf Druid", 1.0), C(4, "Sol Ring", "Artifact", 1.0),
        C(5, "Cultivate", "Sorcery", 3.0), C(6, "Krenko, Mob Boss", "Legendary Creature — Goblin Warrior", 4.0), C(7, "Hidden card", "", null),
    )

    private fun arrange(chip: LibraryChip?, sort: LibrarySort, keep: Set<Int> = emptySet()) =
        arrangeLibrary(items, chip, sort, { it.id in keep }, { it.name }, { it.type }, { it.mv })

    @Test fun chipsOnlyForTypesPresentAndNotUniversal() {
        val chips = LibraryChip.present(items.map { it.type }).toMap()
        assertEquals(1, chips[LibraryChip.BASIC_LAND])
        assertEquals(2, chips[LibraryChip.LAND])
        assertEquals(2, chips[LibraryChip.CREATURE])
        assertTrue(LibraryChip.INSTANT !in chips)
        assertTrue("a chip matching every card adds nothing", LibraryChip.present(listOf("Basic Land — Forest", "Basic Land — Island")).none { it.first == LibraryChip.BASIC_LAND })
    }

    @Test fun groupsByTypeInScanOrder() {
        val groups = arrange(null, LibrarySort.TYPE).map { it.first }.distinct()
        assertEquals(listOf("Basic lands", "Lands", "Creatures", "Sorceries", "Artifacts", "Other"), groups)
    }

    @Test fun chipFiltersButKeepsSelection() {
        val lands = arrange(LibraryChip.LAND, LibrarySort.NAME).map { it.second.name }
        assertEquals(listOf("Breeding Pool", "Forest"), lands)
        val creaturesKeepingSolRing = arrange(LibraryChip.CREATURE, LibrarySort.MANA_VALUE, keep = setOf(4)).map { it.second.name }
        assertEquals(listOf("Llanowar Elves", "Sol Ring", "Krenko, Mob Boss"), creaturesKeepingSolRing)
    }

    @Test fun manaValueSortPutsUnknownLast() {
        assertEquals("Hidden card", arrange(null, LibrarySort.MANA_VALUE).last().second.name)
    }
}
