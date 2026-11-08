package com.arcanatable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Owner, Nov 7: opacity on every editable item, hold-for-slider (one Undo per drag), GIF table backgrounds. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34])
class StudioOpacitySliderGifTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun prefs()=context.getSharedPreferences("osg-${System.nanoTime()}",Context.MODE_PRIVATE)
    private val a="native:HomeScreen.OutlinedButton.1/10"

    @Test fun opacitySavesLoadsAndStaysInRange() {
        val s=StudioControlStyle(alpha=0.4f)
        assertEquals(0.4f,StudioControlStyle.parse(s.json()).alpha!!,0.001f)
        assertNull("solid is not stored",StudioControlStyle.parse(StudioControlStyle().json()).alpha)
        assertTrue(StudioControlStyle(alpha=0.1f).problems().isNotEmpty())
        assertEquals(0.9f,StudioOpacity.step(null,-1)!!,0.001f)
        assertNull("back to solid",StudioOpacity.step(0.9f,1))
        assertEquals("never below 20%",0.2f,StudioOpacity.step(0.2f,-1)!!,0.001f)
        val hand=StudioLayout.reference().let{l->l.copy(elements=l.elements.map{if(it.id=="undo")it.copy(alpha=0.5f) else it})}
        assertEquals(0.5f,StudioLayout.parse(hand.json()).elements.single{it.id=="undo"}.alpha,0.001f)
        assertTrue(hand.copy(elements=hand.elements.map{if(it.id=="undo")it.copy(alpha=0.05f) else it}).problems().isNotEmpty())
        assertTrue(StudioLog.diff(StudioControlStyle(),s).contains("opacity solid→40%"))
    }

    @Test fun aSliderDragIsOneUndoStepAndOneLogLine() {
        val runtime=StudioControlsRuntime(prefs(),"portrait");runtime.begin()
        runtime.beginGesture()
        (1..20).forEach{runtime.edit(a,StudioControlStyle(width=100f+it))}
        runtime.endGesture()
        assertEquals(120f,runtime.style(a).width!!,0f)
        runtime.undo()
        assertNull("one Undo takes back the whole drag",runtime.style(a).width)
        assertFalse(runtime.canUndo)
    }

    @Test fun aGifIsKeptAsAGifAndAPictureStillBecomesAJpg() {
        // A 1×1 GIF.
        val gif=android.util.Base64.decode("R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==",0)
        val f=File(context.cacheDir,"t.gif").apply{writeBytes(gif)}
        val name=StudioBackdrop.import(context,android.net.Uri.fromFile(f))
        assertTrue(name,name.endsWith(".gif") && StudioBackdrop.validName(name))
        assertTrue(StudioBackdrop.isGif(name))
        assertArrayEquals("stored unchanged so it still moves",gif,StudioBackdrop.file(context,name)!!.readBytes())
        assertNotNull("first frame for the colour match",StudioBackdrop.samplePixels(context,name))
        val project=StudioControlProject(backdrop=name)
        assertEquals(name,StudioControlProject.parse(project.json()).backdrop)
        // A normal picture is still shrunk into a JPG.
        val png=File(context.cacheDir,"t.png");android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
            .compress(android.graphics.Bitmap.CompressFormat.PNG,100,png.outputStream())
        assertTrue(StudioBackdrop.import(context,android.net.Uri.fromFile(png)).endsWith(".jpg"))
    }
}
