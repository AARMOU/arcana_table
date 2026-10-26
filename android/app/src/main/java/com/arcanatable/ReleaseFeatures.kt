package com.arcanatable

/** Initial production scope: premade decks and a single offline AI opponent.
 * Debug builds retain experimental import and multiplayer testing.
 */
internal object ReleaseFeatures {
    val deckImportEnabled: Boolean get() = BuildConfig.DEBUG
    val multiplayerPreviewEnabled: Boolean get() = BuildConfig.DEBUG
    fun aiCount(requested: Int, debug: Boolean = BuildConfig.DEBUG): Int =
        requested.coerceIn(1, if (debug) 3 else 1)
    fun availableDecks(decks: List<DeckChoice>, debug: Boolean = BuildConfig.DEBUG): List<DeckChoice> =
        if (debug) decks else decks.filter { it.builtIn }
}
