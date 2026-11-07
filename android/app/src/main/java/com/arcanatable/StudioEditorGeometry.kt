package com.arcanatable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    /** Comfortable touch target. Below it a control is marked red (owner note 14); it is no longer a hard stop. */
    const val MIN_DP=44f
    /** The smallest a resize can go, so a control can never vanish under a fingertip. */
    const val FLOOR_DP=16f

    /** True while a drag would leave the selection too small, overlapping a sibling, or off the screen (red live). */
    fun dragTrouble(frame:Rect,selected:StudioLiveControl,siblings:List<Rect>,screen:androidx.compose.ui.geometry.Size,minPx:Float):Boolean {
        if(!selected.isAction)return false
        if(frame.width<minPx-1 || frame.height<minPx-1)return true
        if(frame.left< -1 || frame.top< -1 || frame.right>screen.width+1 || frame.bottom>screen.height+1)return true
        return siblings.any{b->frame.overlaps(b) && !(frame.contains(b.topLeft) && frame.contains(b.bottomRight)) &&
            !(b.contains(frame.topLeft) && b.contains(frame.bottomRight))}
    }
    const val GRID_DP=4f

    /** The always-mounted table screen; every other screen in the main window is drawn over it. */
    const val BASE_SCREEN="Table"
    /** Screens that cover the whole table while shown, so table controls behind them cannot be seen or touched. */
    val FULL_SCREENS=setOf("Home","Setup","Decks","Deck selection","Deck editor","Lobby","Room","Settings")

    /**
     * Picks what the finger is on. Overlay screens beat the table underneath them; a full-screen page hides the table
     * entirely. Within a layer the deepest (smallest) visible control wins, so text inside a container is reachable.
     */
    fun hitTest(controls:Collection<StudioLiveControl>,point:Offset,window:Int,current:String?=null,visible:(String)->Boolean):StudioLiveControl? {
        val live=controls.filter {it.window==window && it.bounds.width>0f && it.bounds.height>0f && visible(it.id)}
        val covered=live.any{it.screen in FULL_SCREENS}
        val under=live.filter {it.bounds.contains(point) && !(covered && it.screen==BASE_SCREEN)}
        val deepest=under.minWithOrNull(compareBy<StudioLiveControl>{if(it.screen==BASE_SCREEN)1 else 0}.thenBy{it.bounds.width*it.bounds.height}.thenBy{if(it.isAction)0 else 1})
            ?: return null
        // Owner, Nov 7: "It drags the picture off the chip." A picture, name or number inside a grouped item (a player
        // chip, a log entry) selects the whole group first, so a drag moves the chip in one piece. Tapping the group
        // again goes inside to the part. Buttons are always picked directly.
        val group=under.filter{it.kind=="container-action" && it.id!=deepest.id && it.screen==deepest.screen}
            .minByOrNull{it.bounds.width*it.bounds.height} ?: return deepest
        // Something inside a big panel (the battlefield, the hand tray) is picked directly; inside a small group such as
        // a chip (at most ~12× its own area) the group comes first.
        val area={c:StudioLiveControl->c.bounds.width*c.bounds.height}
        if(area(group)>12f*area(deepest))return deepest
        return if(current==group.id)deepest else group
    }

    /** The smallest other control that fully encloses [child] in the same window. */
    fun parentOf(controls:Collection<StudioLiveControl>,child:StudioLiveControl):StudioLiveControl? =
        controls.filter {it.id!=child.id && it.window==child.window && it.bounds.width>0f &&
            it.bounds.left<=child.bounds.left+0.5f && it.bounds.top<=child.bounds.top+0.5f &&
            it.bounds.right>=child.bounds.right-0.5f && it.bounds.bottom>=child.bounds.bottom-0.5f &&
            it.bounds.width*it.bounds.height>child.bounds.width*child.bounds.height}
            .minByOrNull {it.bounds.width*it.bounds.height}

    /** Handle positions (centers) for a frame, in the same coordinate space as [frame]. */
    fun handlePoints(frame:Rect,compact:Boolean=false):Map<StudioHandle,Offset> = if(compact) mapOf(
        StudioHandle.TopLeft to frame.topLeft,StudioHandle.TopRight to frame.topRight,StudioHandle.BottomRight to frame.bottomRight,StudioHandle.BottomLeft to frame.bottomLeft)
        else mapOf(
        StudioHandle.TopLeft to frame.topLeft,StudioHandle.Top to Offset(frame.center.x,frame.top),StudioHandle.TopRight to frame.topRight,
        StudioHandle.Right to Offset(frame.right,frame.center.y),StudioHandle.BottomRight to frame.bottomRight,
        StudioHandle.Bottom to Offset(frame.center.x,frame.bottom),StudioHandle.BottomLeft to frame.bottomLeft,StudioHandle.Left to Offset(frame.left,frame.center.y))

    /** Small frames show only corner handles, so the handles never bury the control being edited. */
    fun compact(frame:Rect,reach:Float)=frame.width<reach*2.5f || frame.height<reach*2.5f

    /**
     * Which handle a touch at [point] grabs. The interior (inset so small 44dp controls still move) moves the control;
     * the frame band and corner targets resize it; [reach] is the touch radius in px.
     */
    fun handleAt(frame:Rect,point:Offset,reach:Float):StudioHandle? {
        val inset=minOf(reach*0.5f,frame.width/4f,frame.height/4f)
        if(frame.deflate(inset).contains(point))return StudioHandle.Move
        val nearest=handlePoints(frame,compact(frame,reach)).minByOrNull {(_,p)->(p-point).getDistance()}
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
            val nw=snap((if(handle.left)w-dragDp.x else w+dragDp.x),grid).coerceIn(FLOOR_DP,2000f)
            next=next.copy(width=nw,dx=if(handle.left)snap(style.dx+(w-nw),false).coerceIn(-2000f,2000f) else next.dx)
        }
        if(handle.top || handle.bottom) {
            val nh=snap((if(handle.top)h-dragDp.y else h+dragDp.y),grid).coerceIn(FLOOR_DP,4000f)
            next=next.copy(height=nh,dy=if(handle.top)snap(style.dy+(h-nh),false).coerceIn(-4000f,4000f) else next.dy)
        }
        return next
    }

    /**
     * Absolute-layout variant used by the hand canvas (design units, not dp offsets). Returns x,y,w,h after a gesture,
     * snapped to [grid] (1 = free), never smaller than [min], and kept inside the canvas left/right and below [originY].
     */
    fun commitAbsolute(x:Float,y:Float,w:Float,h:Float,handle:StudioHandle,dx:Float,dy:Float,grid:Int,min:Float,canvasWidth:Float,originY:Float):FloatArray {
        // Snap the finger travel, not the absolute position: designs with off-grid coordinates (responsive or
        // imported) keep their alignment, and a vertical drag never nudges x.
        fun s(v:Float)=if(grid<=1)v.roundToInt().toFloat() else (v/grid).roundToInt()*grid.toFloat()
        val sx=s(dx);val sy=s(dy)
        if(handle==StudioHandle.Move)return floatArrayOf((x+sx).coerceIn(0f,(canvasWidth-w).coerceAtLeast(0f)),(y+sy).coerceAtLeast(originY),w,h)
        var l=x;var t=y;var r=x+w;var b=y+h
        if(handle.left)l=(l+sx).coerceIn(0f,r-min)
        if(handle.right)r=(r+sx).coerceIn(l+min,canvasWidth)
        if(handle.top)t=(t+sy).coerceIn(originY,b-min)
        if(handle.bottom)b=(b+sy).coerceAtLeast(t+min)
        return floatArrayOf(l,t,r-l,b-t)
    }

    /** Arrow-key style nudge used by the inspector; keeps the result inside the persisted bounds. */
    fun nudge(style:StudioControlStyle,dxDp:Float,dyDp:Float)=
        style.copy(dx=(style.dx+dxDp).coerceIn(-2000f,2000f),dy=(style.dy+dyDp).coerceIn(-4000f,4000f))

    fun resize(style:StudioControlStyle,widthDp:Float,heightDp:Float,dw:Float,dh:Float)=style.copy(
        width=if(dw==0f)style.width else ((style.width ?: widthDp)+dw).coerceIn(FLOOR_DP,2000f),
        height=if(dh==0f)style.height else ((style.height ?: heightDp)+dh).coerceIn(FLOOR_DP,4000f))

    fun isMeaningfulDrag(drag:Offset,slop:Float)=drag.getDistance()>slop

    // ---- Multi-select and Align (owner, Nov 7: "tap and hold similar elements and modify multiple objects at once",
    // "a way to align the selected buttons ... multiple ways", "It needs to be simple"). ----

    /** The piece of code a control comes from; controls that share it are "the same kind of thing" (all player chips). */
    fun siteOf(id:String)=id.substringAfter("native:").substringBefore("/")

    /**
     * Applies the edit made on the lead control ([lead] → [next]) to another selected control: moves and size changes
     * are added as amounts (every item moves 4dp, grows 4dp), everything else that changed is copied. The label and
     * what the button does are never copied, since they belong to each item.
     */
    fun groupEdit(lead:StudioControlStyle,next:StudioControlStyle,member:StudioControlStyle,leadW:Float,leadH:Float,memW:Float,memH:Float):StudioControlStyle {
        fun size(old:Float?,new:Float?,own:Float?,leadNative:Float,memNative:Float,max:Float)=when {
            new==old->own
            new==null->null
            else->((own ?: memNative)+(new-(old ?: leadNative))).coerceIn(FLOOR_DP,max)
        }
        fun <T> pick(a:T,b:T,own:T)=if(a!=b)b else own
        return member.copy(dx=(member.dx+next.dx-lead.dx).coerceIn(-2000f,2000f),dy=(member.dy+next.dy-lead.dy).coerceIn(-4000f,4000f),
            width=size(lead.width,next.width,member.width,leadW,memW,2000f),height=size(lead.height,next.height,member.height,leadH,memH,4000f),
            font=pick(lead.font,next.font,member.font),radius=pick(lead.radius,next.radius,member.radius),fill=pick(lead.fill,next.fill,member.fill),
            ink=pick(lead.ink,next.ink,member.ink),type=pick(lead.type,next.type,member.type),visible=pick(lead.visible,next.visible,member.visible),icon=pick(lead.icon,next.icon,member.icon),
            layer=pick(lead.layer,next.layer,member.layer))
    }

    /** One way to line items up. Labels are what the owner sees. */
    enum class Align(val label:String){Left("Left edges"),Top("Top edges"),Centre("Centres"),Row("In a row"),Column("In a column"),Size("Same size")}

    /** What one item needs: a move in dp and, for [Align.Size], a new size in dp. */
    data class AlignMove(val ddx:Float,val ddy:Float,val width:Float?=null,val height:Float?=null)

    /**
     * Lines up [rects] (screen pixels) against the [lead] item, the one picked first. Row and Column keep the order the
     * items are in on screen and spread them evenly between the first and last; if they overlap, they get an 8dp gap.
     */
    fun align(mode:Align,rects:Map<String,Rect>,lead:String,density:Float):Map<String,AlignMove> {
        val anchor=rects[lead] ?: return emptyMap()
        val d=density.coerceAtLeast(0.1f)
        return when(mode) {
            Align.Left->rects.mapValues{(_,r)->AlignMove((anchor.left-r.left)/d,0f)}
            Align.Top->rects.mapValues{(_,r)->AlignMove(0f,(anchor.top-r.top)/d)}
            Align.Centre->rects.mapValues{(_,r)->AlignMove((anchor.center.x-r.center.x)/d,(anchor.center.y-r.center.y)/d)}
            Align.Size->rects.mapValues{(_,_)->AlignMove(0f,0f,anchor.width/d,anchor.height/d)}
            Align.Row,Align.Column-> {
                val across=mode==Align.Row
                val order=rects.entries.sortedBy{if(across)it.value.left else it.value.top}
                val start=if(across)order.first().value.left else order.first().value.top
                val end=if(across)order.last().value.right else order.last().value.bottom
                val total=order.sumOf{(if(across)it.value.width else it.value.height).toDouble()}.toFloat()
                val gap=if(order.size<2)0f else ((end-start-total)/(order.size-1)).let{if(it<0f)8f*d else it}
                var at=start
                order.associate {(id,r)->
                    val move=if(across)AlignMove((at-r.left)/d,(anchor.top-r.top)/d) else AlignMove((anchor.left-r.left)/d,(at-r.top)/d)
                    at+=(if(across)r.width else r.height)+gap
                    id to move
                }
            }
        }
    }
}
