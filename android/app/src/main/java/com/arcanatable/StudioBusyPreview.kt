package com.arcanatable

import androidx.compose.ui.graphics.Color

/**
 * Owner note 29, "Fill it up": a crowded table to edit against, so a layout that looks fine on an empty board isn't
 * cut off or crowded in a real late game. Long names, big numbers, a full battlefield and four seats.
 *
 * Only for Studio's editing view: nothing here is sent to the engine or saved (the caller keeps the real players in
 * [TableController.studioRealPlayers] and puts them back when Studio stops filling). No real hidden information is
 * shown; every card is an example.
 */
internal object StudioBusyPreview {
    private val names = listOf(
        "Bello, Bard of the Brambles" to "Legendary Creature",
        "Elesh Norn, Grand Cenobite of the Whispering Halls" to "Legendary Creature",
        "Llanowar Elves" to "Creature",
        "Sol Ring" to "Artifact",
        "Arcane Signet" to "Artifact",
        "Command Tower" to "Land",
        "Forest" to "Land",
        "Island" to "Land",
        "Rhythm of the Wild" to "Enchantment",
        "Grazing Gladehart of the Verdant Expanse" to "Creature",
        "Swords to Plowshares" to "Instant",
        "Elspeth, Sun's Champion" to "Planeswalker",
    )
    private val colors = listOf(0xFF4E7A44, 0xFFC9B98A, 0xFF4E7A44, 0xFF8A8F96, 0xFF8A8F96, 0xFF6E6252, 0xFF4E7A44, 0xFF3F6A93,
        0xFFA2483A, 0xFF4E7A44, 0xFFC9B98A, 0xFFC9B98A)
    private const val FIRST_ID = 990_100
    private val seatNames = listOf("Grand Archmage Valentina", "Opponent With A Very Long Name", "The Third Opponent", "Fourth Seat")

    private fun card(i: Int, seat: Int): GameCard {
        val (name, type) = names[i % names.size]
        val creature = type.contains("Creature")
        return GameCard(FIRST_ID + seat * 100 + i, name, type, Color(colors[i % colors.size]), tapped = i % 4 == 1,
            counters = if (i % 5 == 0 && creature) mapOf("+1/+1" to 12) else emptyMap(),
            power = if (creature) "${3 + i % 9}" else null, toughness = if (creature) "${3 + i % 7}" else null)
    }

    /** The busy table: every seat gets 18 permanents, a full hand and graveyard, big life totals; up to four seats. */
    fun fill(real: List<TablePlayer>, localSeat: Int): List<TablePlayer> {
        val seats = real.ifEmpty { listOf(TablePlayer(seatNames[0])) }.let { it + (it.size until 4).map { n -> TablePlayer(seatNames[n], isComputer = true) } }
        return seats.take(4).mapIndexed { seat, p ->
            p.copy(
                name = if (seat == localSeat) p.name else seatNames.getOrElse(seat) { p.name },
                life = if (seat == localSeat) 40 else 140 - seat * 37,
                battlefield = (0 until 18).map { card(it, seat) },
                hand = (0 until 9).map { card(it + 20, seat) },
                graveyard = (0 until 30).map { card(it + 40, seat) },
                exile = (0 until 6).map { card(it + 80, seat) },
                manaPool = mapOf("W" to 3, "U" to 2, "G" to 12),
                commanderCard = p.commanderCard ?: card(1, seat).copy(isCommander = true),
                commanderDamageReceived = if (seat == localSeat) seats.filterIndexed { i, _ -> i != localSeat }.take(2).associate { it.id to 18 } else emptyMap(),
                playerCounters = mapOf("Poison" to 7, "Energy" to 99),
            )
        }
    }
}
