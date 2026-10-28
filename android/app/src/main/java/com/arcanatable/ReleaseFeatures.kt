package com.arcanatable

/** Build 134 release scope: deck importing and four-seat multiplayer are enabled.
 * The AI opponent count is bounded separately from WAN multiplayer seat admission.
 */
internal object ReleaseFeatures {
    val deckImportEnabled: Boolean get() = true
    val multiplayerPreviewEnabled: Boolean get() = true
    fun aiCount(requested: Int, debug: Boolean = BuildConfig.DEBUG): Int =
        requested.coerceIn(1, 3)
    fun availableDecks(decks: List<DeckChoice>, debug: Boolean = BuildConfig.DEBUG): List<DeckChoice> =
        decks
}
