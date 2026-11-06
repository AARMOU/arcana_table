package com.arcanatable

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class StudioEditorGeometryTest {
    private fun c(id:String,r:Rect,kind:String="action",window:Int=1)=StudioLiveControl(id,"Home",kind,id,true,r,window)

    @Test fun hitTestPicksTheDeepestVisibleControlInThisWindowOnly() {
        val group=c("native:group",Rect(0f,0f,400f,200f),"container-action")
        val label=c("native:label",Rect(10f,10f,110f,60f),"data")
        val dialog=c("native:dialog",Rect(0f,0f,50f,50f),window=2)
        val hidden=c("native:hidden",Rect(12f,12f,40f,40f))
        val all=listOf(group,label,dialog,hidden)
        assertEquals("native:label",StudioEditorGeometry.hitTest(all,Offset(20f,20f),1){it!="native:hidden"}?.id)
        assertEquals("native:group",StudioEditorGeometry.hitTest(all,Offset(300f,150f),1){true}?.id)
        assertNull(StudioEditorGeometry.hitTest(all,Offset(500f,500f),1){true})
        assertEquals("native:group",StudioEditorGeometry.parentOf(all,label)?.id)
        assertNull(StudioEditorGeometry.parentOf(all,group))
    }

    @Test fun overlayScreensBeatTheTableAndFullScreenPagesHideIt() {
        fun s(id:String,screen:String,r:Rect)=StudioLiveControl(id,screen,"action",id,true,r,1)
        val board=s("native:board","Table",Rect(0f,0f,400f,400f))
        val stackButton=s("native:stack","Stack",Rect(0f,0f,400f,400f).deflate(1f))
        val tiny=s("native:tiny","Table",Rect(10f,10f,20f,20f))
        assertEquals("an overlay beats a smaller table control under it","native:stack",StudioEditorGeometry.hitTest(listOf(board,stackButton,tiny),Offset(15f,15f),1){true}?.id)
        assertEquals("native:tiny",StudioEditorGeometry.hitTest(listOf(board,tiny),Offset(15f,15f),1){true}?.id)
        val home=s("native:play","Home",Rect(0f,500f,400f,560f))
        assertNull("Home covers the table, so table controls behind it are not selectable",StudioEditorGeometry.hitTest(listOf(board,tiny,home),Offset(15f,15f),1){true})
        assertEquals("native:play",StudioEditorGeometry.hitTest(listOf(board,home),Offset(20f,520f),1){true}?.id)
    }

    @Test fun smallControlsStillMoveFromTheirCentreAndCornersResize() {
        val frame=Rect(100f,100f,188f,188f) // 44dp at 2x
        assertEquals(StudioHandle.Move,StudioEditorGeometry.handleAt(frame,frame.center,48f))
        assertEquals(StudioHandle.BottomRight,StudioEditorGeometry.handleAt(frame,Offset(190f,190f),48f))
        assertEquals(StudioHandle.TopLeft,StudioEditorGeometry.handleAt(frame,Offset(95f,96f),48f))
        assertEquals(StudioHandle.Left,StudioEditorGeometry.handleAt(Rect(0f,0f,400f,400f),Offset(2f,200f),48f))
        assertNull(StudioEditorGeometry.handleAt(frame,Offset(400f,400f),48f))
    }

    @Test fun commitSnapsMovesAndAnchorsOppositeEdges() {
        val moved=StudioEditorGeometry.commit(StudioControlStyle(dx=2f),StudioHandle.Move,100f,50f,Offset(9f,-7f))
        assertEquals(12f,moved.dx,0f);assertEquals(-8f,moved.dy,0f)
        val left=StudioEditorGeometry.commit(StudioControlStyle(),StudioHandle.Left,100f,50f,Offset(-20f,0f))
        assertEquals(120f,left.width!!,0f);assertEquals(-20f,left.dx,0f);assertNull(left.height)
        val top=StudioEditorGeometry.commit(StudioControlStyle(),StudioHandle.TopRight,100f,50f,Offset(10f,30f))
        assertEquals("never below the 44dp minimum",44f,top.height!!,0f);assertEquals(6f,top.dy,0f);assertEquals(112f,top.width!!,0f)
        assertTrue(StudioEditorGeometry.commit(StudioControlStyle(),StudioHandle.BottomRight,100f,50f,Offset(-500f,-500f)).problems().isEmpty())
    }

    @Test fun absoluteHandCommitsSnapTravelAndStayOnTheCanvas() {
        val moved=StudioEditorGeometry.commitAbsolute(113f,297f,63f,44f,StudioHandle.Move,3f,37f,8,24f,412f,296f)
        assertEquals(113f,moved[0],0f);assertEquals(337f,moved[1],0f)
        val clamped=StudioEditorGeometry.commitAbsolute(380f,300f,30f,30f,StudioHandle.Move,100f,-200f,8,24f,412f,296f)
        assertEquals(382f,clamped[0],0f);assertEquals("never above the hand origin",296f,clamped[1],0f)
        val left=StudioEditorGeometry.commitAbsolute(100f,300f,60f,44f,StudioHandle.Left,-17f,0f,8,24f,412f,296f)
        assertEquals(84f,left[0],0f);assertEquals(76f,left[2],0f)
        val tiny=StudioEditorGeometry.commitAbsolute(100f,300f,60f,44f,StudioHandle.BottomRight,-200f,-200f,1,24f,412f,296f)
        assertEquals(24f,tiny[2],0f);assertEquals(24f,tiny[3],0f)
    }

    @Test fun previewNeverInvertsTheFrame() {
        val p=StudioEditorGeometry.preview(Rect(0f,0f,100f,100f),StudioHandle.Right,Offset(-300f,0f),88f)
        assertEquals(88f,p.width,0f)
        assertEquals(Rect(10f,5f,110f,105f),StudioEditorGeometry.preview(Rect(0f,0f,100f,100f),StudioHandle.Move,Offset(10f,5f),88f))
    }
}
