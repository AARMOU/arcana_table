package com.arcanatable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

internal val LocalStudioScreen=compositionLocalOf<String?>{null}

@Composable
internal fun StudioScreen(name:String,content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalStudioScreen provides name,content=content)
}
