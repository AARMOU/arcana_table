package com.arcanatable

import org.json.JSONObject

/**
 * Phones playing together, and a saved game being resumed, must use the same Arcana build: each build
 * carries its own rules engine, and two different engines do not understand each other's game updates
 * (B142 moved to a newer Argentum, 2026-11-02). Older builds sent no build number at all, so a missing
 * number counts as "an older build".
 *
 * Messages are written for players, not developers.
 */
internal object BuildCompatibility {
    /** Added to a guest's lanHello, the host's lanLobby and an offline checkpoint. */
    const val FIELD = "arcanaBuild"

    val current: Int get() = BuildConfig.VERSION_CODE

    private fun other(packet: JSONObject): Int? = packet.optInt(FIELD, -1).takeIf { it > 0 }

    private fun describe(build: Int?): String = if (build == null) "an older build" else "Build $build"

    /** Host side: why this guest can't join, or null when their build matches. */
    fun guestProblem(hello: JSONObject): String? {
        val theirs = other(hello)
        return if (theirs == current) null else
            "Your Arcana is ${describe(theirs)} and the host's is Build $current. " +
                "Both phones need the same Arcana build to play together."
    }

    /** Guest side: why this host's room can't be played, or null when the builds match. */
    fun hostProblem(lobby: JSONObject): String? {
        val theirs = other(lobby)
        return if (theirs == current) null else
            "The host's Arcana is ${describe(theirs)} and yours is Build $current. " +
                "Both phones need the same Arcana build to play together."
    }

    /** A saved offline game that this build's engine could not read. */
    fun savedGameProblem(checkpoint: JSONObject): String {
        val theirs = other(checkpoint)
        val made = if (theirs == null || theirs != current) "by ${describe(theirs)} of Arcana, which used a different rules engine"
            else "by this build, but it could not be read"
        return "This game was saved $made, so it can't continue here. Start a new game; " +
            "the old save is kept until you save a new one."
    }
}
