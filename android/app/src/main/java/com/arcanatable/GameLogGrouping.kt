package com.arcanatable

/** One compact view of consecutive identical engine events, preserving the original log. */
internal data class CollapsedGameLogRow(
    val firstIndex: Int,
    val count: Int,
    val description: String,
    val isYours: Boolean?,
    val isTurnMarker: Boolean,
    val groupingKey: String?,
)

/** Only engine events with complete semantic fingerprints may be collapsed.
 * Plain sandbox strings cannot prove two separate events have the same source/target,
 * so they remain individual entries. Input order and original entries are untouched.
 */
internal fun collapsedGameLogRows(
    entries: List<String>,
    ownership: List<Boolean?>?,
    turnMarkers: List<Boolean>?,
    groupingKeys: List<String?>?,
): List<CollapsedGameLogRow> {
    val result = ArrayList<CollapsedGameLogRow>()
    entries.forEachIndexed { index, description ->
        val yours = ownership?.getOrNull(index)
        val marker = turnMarkers?.getOrNull(index) == true
        val key = groupingKeys?.getOrNull(index)
        val preceding = result.lastOrNull()
        if (preceding != null && !marker && !preceding.isTurnMarker && key != null &&
            preceding.groupingKey == key && preceding.description == description &&
            preceding.isYours == yours && preceding.firstIndex + preceding.count == index
        ) {
            result[result.lastIndex] = preceding.copy(count = preceding.count + 1)
        } else result += CollapsedGameLogRow(index, 1, description, yours, marker, key)
    }
    return result
}
