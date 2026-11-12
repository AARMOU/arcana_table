package com.arcanatable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

internal val LocalStudioScreen=compositionLocalOf<String?>{null}
/** True inside screens Studio never touches (owner D33): no registration, no styles, no palette, no density. */
internal val LocalStudioExcluded=compositionLocalOf{false}
/** The table colours before any Studio palette, for excluded screens. */
internal val LocalStudioBaseColors=compositionLocalOf<TableColors?>{null}

internal object StudioScope {
    /** Owner decision: Home, Decks, Setup, Lobby and Settings (and anything opened from them) are outside Studio. */
    val EXCLUDED=setOf("Home","Setup","Room","Decks","Deck selection","Deck editor","Settings","Diagnostics")
    fun excluded(screen:String?)=screen in EXCLUDED
}

@Composable
internal fun StudioScreen(name:String,content:@Composable ()->Unit) {
    val excluded=LocalStudioExcluded.current || StudioScope.excluded(name)
    if(excluded && !LocalStudioExcluded.current) {
        val base=LocalStudioBaseColors.current ?: LocalTableColors.current
        CompositionLocalProvider(LocalStudioScreen provides name,LocalStudioExcluded provides true,LocalTableColors provides base,content=content)
    } else CompositionLocalProvider(LocalStudioScreen provides name,content=content)
}
