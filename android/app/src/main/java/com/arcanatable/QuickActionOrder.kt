package com.arcanatable

/** The shortcuts supported by the Argentum table. Existing saved legacy shortcuts are discarded. */
val argentumQuickActionIds = listOf("GRAVEYARD", "EXILE", "STACK", "LOG", "CAST_COMMANDER", "PASS_TO_TURN", "UNDO", "HAND")

fun normalizeArgentumQuickActionOrder(saved: String?): List<String> {
    val requested = saved.orEmpty().split('|').filter { it in argentumQuickActionIds }.distinct()
    return requested + argentumQuickActionIds.filterNot { it in requested }
}
