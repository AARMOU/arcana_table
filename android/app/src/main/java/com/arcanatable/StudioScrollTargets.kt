package com.arcanatable

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView

/**
 * Owner note 5 (Nov 6/7): scroll with two fingers while Studio is open, instead of switching the toolbar to Scroll.
 * Studio's canvas sits over the screen and takes every touch, so the scrolling rows and lists register here while
 * editing, and a two-finger drag on the canvas scrolls the one under the fingers. Studio builds only.
 */
internal object StudioScrollTargets {
    internal class Target(val state:ScrollableState,val horizontal:Boolean,
        /** True when the state takes a finger drag (a raw `scrollable` state) rather than a scroll amount. */
        val dragDelta:Boolean) {
        var bounds=Rect.Zero
        var window=0
    }
    internal val targets=mutableMapOf<Any,Target>()

    /** The innermost horizontal and vertical lists under [point] in [window]. */
    fun under(point:Offset,window:Int):Pair<Target?,Target?> {
        val here=targets.values.filter{it.window==window && it.bounds.width>0f && it.bounds.contains(point)}
        fun smallest(h:Boolean)=here.filter{it.horizontal==h}.minByOrNull{it.bounds.width*it.bounds.height}
        return smallest(true) to smallest(false)
    }

    /** Scrolls the lists under the fingers by a finger movement of [drag]. Returns whether anything moved. */
    fun scroll(sideways:Target?,upDown:Target?,drag:Offset):Boolean {
        var moved=false
        sideways?.let{t->moved=t.state.dispatchRawDelta(if(t.dragDelta)drag.x else -drag.x)!=0f || moved}
        upDown?.let{t->moved=t.state.dispatchRawDelta(if(t.dragDelta)drag.y else -drag.y)!=0f || moved}
        return moved
    }
}

/** Registers a scrolling row or list for two-finger scrolling in Studio (see [StudioScrollTargets]). */
@Composable
internal fun Modifier.studioScrollTarget(state:ScrollableState,horizontal:Boolean,dragDelta:Boolean=false):Modifier {
    if(!BuildConfig.STUDIO_ENABLED)return this
    val token=remember{Any()}
    val window=System.identityHashCode(LocalView.current.rootView)
    val target=remember(state,horizontal,dragDelta){StudioScrollTargets.Target(state,horizontal,dragDelta)}
    DisposableEffect(token,target){StudioScrollTargets.targets[token]=target;onDispose{StudioScrollTargets.targets.remove(token)}}
    return this.onGloballyPositioned{
        // Only kept up to date while Studio is open (owner note 32: nothing extra is measured in play).
        if(StudioEditorRequests.shellActive){target.bounds=it.boundsInRoot();target.window=window}
    }
}
