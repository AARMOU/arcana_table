package com.arcanatable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which part of the selection frame a gesture grabbed. */
internal enum class StudioHandle(val left:Boolean=false,val top:Boolean=false,val right:Boolean=false,val bottom:Boolean=false) {
    Move,
    Left(left=true),Top(top=true),Right(right=true),Bottom(bottom=true),
    TopLeft(left=true,top=true),TopRight(top=true,right=true),BottomLeft(left=true,bottom=true),BottomRight(right=true,bottom=true);
    val resizes get()=this!=Move
}

/**
 * Pure geometry for the direct-manipulation canvas. Everything here works on the live native bounds that
 * [StudioControlsRuntime] records, so the editor never draws a mock of a screen: it frames the real control.
 */
internal object StudioEditorGeometry {
    /** Matches [StudioControlStyle.problems]: every sized control keeps a 44dp touch target. */
    const val MIN_DP=44f
    const val GRID_DP=4f

    /** The always-mounted table screen; every other screen in the main window is drawn over it. */
    const val BASE_SCREEN="Table"
    /** Screens that cover the whole table while shown, so table controls behind them cannot be seen or touched. */
    val FULL_SCREENS=setOf("Home","Setup","Decks","Deck selection","Deck editor","Lobby","Room","Settings")

    /**
     * Picks what the finger is on. Overlay screens beat the table underneath them; a full-screen page hides the table
     * entirely. Within a layer the deepest (smallest) visible control wins, so text inside a container is reachable.
     */
    fun hitTest(controls:Collection<StudioLiveControl>,point:Offset,window:Int,visible:(String)->Boolean):StudioLiveControl? {
        val live=controls.filter {it.window==window && it.bounds.width>0f && it.bounds.height>0f && visible(it.id)}
        val covered=live.any{it.screen in FULL_SCREENS}
        return live.filter {it.bounds.contains(point) && !(covered && it.screen==BASE_SCREEN)}
            .minWithOrNull(compareBy<StudioLiveControl>{if(it.screen==BASE_SCREEN)1 else 0}.thenBy{it.bounds.width*it.bounds.height}.thenBy{if(it.isAction)0 else 1})
    }

    /** The smallest other control that fully encloses [child] in the same window. */
    fun parentOf(controls:Collection<StudioLiveControl>,child:StudioLiveControl):StudioLiveControl? =
        controls.filter {it.id!=child.id && it.window==child.window && it.bounds.width>0f &&
            it.bounds.left<=child.bounds.left+0.5f && it.bounds.top<=child.bounds.top+0.5f &&
            it.bounds.right>=child.bounds.right-0.5f && it.bounds.bottom>=child.bounds.bottom-0.5f &&
            it.bounds.width*it.bounds.height>child.bounds.width*child.bounds.height}
            .minByOrNull {it.bounds.width*it.bounds.height}

    /** Handle positions (centers) for a frame, in the same coordinate space as [frame]. */
    fun handlePoints(frame:Rect):Map<StudioHandle,Offset> = mapOf(
        StudioHandle.TopLeft to frame.topLeft,StudioHandle.Top to Offset(frame.center.x,frame.top),StudioHandle.TopRight to frame.topRight,
        StudioHandle.Right to Offset(frame.right,frame.center.y),StudioHandle.BottomRight to frame.bottomRight,
        StudioHandle.Bottom to Offset(frame.center.x,frame.bottom),StudioHandle.BottomLeft to frame.bottomLeft,StudioHandle.Left to Offset(frame.left,frame.center.y))

    /**
     * Which handle a touch at [point] grabs. The interior (inset so small 44dp controls still move) moves the control;
     * the frame band and corner targets resize it; [reach] is the touch radius in px.
     */
    fun handleAt(frame:Rect,point:Offset,reach:Float):StudioHandle? {
        val inset=minOf(reach*0.5f,frame.width/4f,frame.height/4f)
        if(frame.deflate(inset).contains(point))return StudioHandle.Move
        val nearest=handlePoints(frame).minByOrNull {(_,p)->(p-point).getDistance()}
        if(nearest!=null && (nearest.value-point).getDistance()<=reach)return nearest.key
        return if(frame.contains(point))StudioHandle.Move else null
    }

    /** Live preview of the frame while a finger is down; never smaller than the minimum target. */
    fun preview(frame:Rect,handle:StudioHandle,drag:Offset,minPx:Float):Rect {
        if(handle==StudioHandle.Move)return frame.translate(drag)
        var l=frame.left;var t=frame.top;var r=frame.right;var b=frame.bottom
        if(handle.left)l=minOf(l+drag.x,r-minPx)
        if(handle.right)r=maxOf(r+drag.x,l+minPx)
        if(handle.top)t=minOf(t+drag.y,b-minPx)
        if(handle.bottom)b=maxOf(b+drag.y,t+minPx)
        return Rect(l,t,r,b)
    }

    private fun snap(value:Float,grid:Boolean)=if(grid)(value/GRID_DP).roundToInt()*GRID_DP else value.roundToInt().toFloat()

    /**
     * Converts a finished gesture into the persisted style. [widthDp]/[heightDp] are the control's current rendered size,
     * [dragDp] the finger travel in dp. Left/top handles keep the opposite edge anchored by shifting the offset.
     */
    fun commit(style:StudioControlStyle,handle:StudioHandle,widthDp:Float,heightDp:Float,dragDp:Offset,grid:Boolean=true):StudioControlStyle {
        if(handle==StudioHandle.Move)return style.copy(
            dx=snap(style.dx+dragDp.x,grid).coerceIn(-2000f,2000f),dy=snap(style.dy+dragDp.y,grid).coerceIn(-4000f,4000f))
        val w=style.width ?: widthDp;val h=style.height ?: heightDp
        var next=style
        if(handle.left || handle.right) {
            val nw=snap((if(handle.left)w-dragDp.x else w+dragDp.x),grid).coerceIn(MIN_DP,2000f)
            next=next.copy(width=nw,dx=if(handle.left)snap(style.dx+(w-nw),false).coerceIn(-2000f,2000f) else next.dx)
        }
        if(handle.top || handle.bottom) {
            val nh=snap((if(handle.top)h-dragDp.y else h+dragDp.y),grid).coerceIn(MIN_DP,4000f)
            next=next.copy(height=nh,dy=if(handle.top)snap(style.dy+(h-nh),false).coerceIn(-4000f,4000f) else next.dy)
        }
        return next
    }

    /** Arrow-key style nudge used by the inspector; keeps the result inside the persisted bounds. */
    fun nudge(style:StudioControlStyle,dxDp:Float,dyDp:Float)=
        style.copy(dx=(style.dx+dxDp).coerceIn(-2000f,2000f),dy=(style.dy+dyDp).coerceIn(-4000f,4000f))

    fun resize(style:StudioControlStyle,widthDp:Float,heightDp:Float,dw:Float,dh:Float)=style.copy(
        width=if(dw==0f)style.width else ((style.width ?: widthDp)+dw).coerceIn(MIN_DP,2000f),
        height=if(dh==0f)style.height else ((style.height ?: heightDp)+dh).coerceIn(MIN_DP,4000f))

    fun isMeaningfulDrag(drag:Offset,slop:Float)=abs(drag.x)>slop || abs(drag.y)>slop
}
