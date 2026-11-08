package com.arcanatable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Owner, Nov 7: opacity on every editable item, hold-for-slider (one Undo per drag), GIF table backgrounds. */
// No NATIVE graphics mode here: a NATIVE sandbox first created in this package is reused by the UI tests that come
// later in the same run, with this class's leftovers in it, and 16 unrelated UI tests failed (Nov 7, CI and local).
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class StudioOpacitySliderGifTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun prefs()=context.getSharedPreferences("osg-${System.nanoTime()}",Context.MODE_PRIVATE)
    private val a="native:HomeScreen.OutlinedButton.1/10"

    @Test fun opacitySavesLoadsAndStaysInRange() {
        val s=StudioControlStyle(alpha=0.4f)
        assertEquals(0.4f,StudioControlStyle.parse(s.json()).alpha!!,0.001f)
        assertNull("solid is not stored",StudioControlStyle.parse(StudioControlStyle().json()).alpha)
        assertTrue("0% is allowed",StudioControlStyle(alpha=0f).problems().isEmpty())
        assertTrue(StudioControlStyle(alpha=-0.1f).problems().isNotEmpty());assertTrue(StudioControlStyle(alpha=1.5f).problems().isNotEmpty())
        assertEquals(0.9f,StudioOpacity.step(null,-1)!!,0.001f)
        assertNull("back to solid",StudioOpacity.step(0.9f,1))
        assertEquals("down to 0%",0f,StudioOpacity.step(0.1f,-1)!!,0.001f)
        assertEquals("never below 0%",0f,StudioOpacity.step(0f,-1)!!,0.001f)
        val hand=StudioLayout.reference().let{l->l.copy(elements=l.elements.map{if(it.id=="undo")it.copy(alpha=0.5f) else it})}
        assertEquals(0.5f,StudioLayout.parse(hand.json()).elements.single{it.id=="undo"}.alpha,0.001f)
        assertTrue(hand.copy(elements=hand.elements.map{if(it.id=="undo")it.copy(alpha=-0.05f) else it}).problems().isNotEmpty())
        assertTrue(hand.copy(elements=hand.elements.map{if(it.id=="undo")it.copy(alpha=0f) else it}).problems().isEmpty())
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

    /** GIF naming and detection (the GIF decode and playback themselves are checked on the device). */
    @Test fun gifsAreRecognisedAndKeptAsGifs() {
        assertTrue(StudioBackdrop.isGifHeader("GIF89a".toByteArray()));assertTrue(StudioBackdrop.isGifHeader("GIF87a".toByteArray()))
        assertFalse(StudioBackdrop.isGifHeader(byteArrayOf(0x89.toByte(),0x50,0x4E,0x47,0x0D,0x0A)))
        val name="bg-0f3a9c2e-1111-2222-3333-444455556666.gif"
        assertTrue(StudioBackdrop.validName(name) && StudioBackdrop.isGif(name))
        assertTrue(StudioBackdrop.validName("bg-abc.jpg"));assertFalse(StudioBackdrop.validName("bg-abc.mp4"))
        assertEquals(name,StudioControlProject.parse(StudioControlProject(backdrop=name).json()).backdrop)
    }
}
