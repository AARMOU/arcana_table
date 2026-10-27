package com.arcanatable

/** Initial production scope: premade decks and a single offline AI opponent.
 * Debug builds retain experimental import and multiplayer testing.
 */
internal object ReleaseFeatures {
    val deckImportEnabled: Boolean get() = true // Owner: next TEST release-look
    val multiplayerPreviewEnabled: Boolean get() = true // Owner: next TEST release-look
    fun aiCount(requested: Int, debug: Boolean = BuildConfig.DEBUG): Int =
        requested.coerceIn(1, 3)
    fun availableDecks(decks: List<DeckChoice>, debug: Boolean = BuildConfig.DEBUG): List<DeckChoice> =
        decks // User-imported decks available; Argentum still validates game support
}
