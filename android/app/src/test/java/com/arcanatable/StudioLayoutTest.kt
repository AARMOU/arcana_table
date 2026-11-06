package com.arcanatable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class StudioLayoutTest {
    private val ref=StudioLayout.reference()
    private fun prefs()=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("studio-test-${System.nanoTime()}",Context.MODE_PRIVATE)
    private fun reject(layout:StudioLayout) { assertTrue(layout.problems().isNotEmpty());assertTrue(runCatching{StudioLayout.parse(layout.json())}.isFailure) }

    @Test fun ownerExportParsesWithExactReferenceCoordinatesAndStableBindings() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val fixture=context.assets.open("studio/owner-hand.json").bufferedReader().use{it.readText()}
        val parsed=StudioLayout.parse(fixture)
        assertEquals(15,parsed.elements.size)
        assertEquals(listOf(8f,344f,145f,44f),parsed.elements.first().let{listOf(it.x,it.y,it.w,it.h)})
        assertEquals(600f,parsed.elements.last().y)
        assertEquals(parsed,StudioLayout.parse(parsed.json()))
    }
    @Test fun duplicateIdsAndMissingCriticalBindingCannotApply() {
        reject(ref.copy(elements=ref.elements+ref.elements.first()))
        reject(ref.copy(elements=ref.elements.filterNot{it.id=="cast"}))
        reject(ref.copy(elements=ref.elements.map{if(it.id=="undo")it.copy(visible=false) else it}))
    }
    @Test fun unknownOrCrossScreenBindingsCannotGainGameAuthority() {
        reject(ref.copy(elements=ref.elements.map{if(it.id=="pass")it.copy(binding="concede") else it}))
        reject(ref.copy(elements=ref.elements.map{if(it.id=="card0")it.copy(binding="opponent.hand.0") else it}))
    }
    @Test fun overlapOffCanvasAndNonfiniteGeometryRejected() {
        reject(ref.copy(elements=ref.elements.map{if(it.id=="undo")it.copy(x=8f) else it}))
        reject(ref.copy(elements=ref.elements.map{if(it.id=="cast")it.copy(w=100f) else it}))
        assertTrue(ref.copy(elements=ref.elements.map{it.copy(x=Float.NaN)}).problems().isNotEmpty())
    }
    @Test fun unsupportedVersionsAndEmptyProjectsFailClosed() {
        assertTrue(runCatching{StudioLayout.parse(JSONObject(ref.json()).put("version",2).toString())}.isFailure)
        assertTrue(runCatching{StudioLayout.parse("{}")}.isFailure)
        assertTrue(runCatching{StudioLayout.parse("x".repeat(250001))}.isFailure)
    }
    @Test fun applyPersistsAndOrientationIsIsolated() {
        val prefs=prefs();val port=StudioStore(prefs,"portrait");port.apply(ref)
        assertTrue(StudioStore(prefs,"portrait").enabled());assertEquals(ref,StudioStore(prefs,"portrait").load())
        assertNull(StudioStore(prefs,"landscape").load());assertFalse(StudioStore(prefs,"landscape").enabled())
    }
    @Test fun invalidApplyDoesNotDamageSavedDesign() {
        val store=StudioStore(prefs(),"portrait");store.apply(ref)
        assertTrue(runCatching{store.apply(ref.copy(elements=emptyList()))}.isFailure)
        assertEquals(ref,store.load())
    }
    @Test fun disableRetainsDesignAndResetRestoresNative() {
        val store=StudioStore(prefs(),"portrait");store.apply(ref);store.enable(false)
        assertFalse(store.enabled());assertEquals(ref,store.load())
        store.reset();assertNull(store.load());assertNull(store.draft());assertFalse(store.enabled())
    }
    @Test fun malformedCurrentUsesLastKnownGoodBackup() {
        val prefs=prefs();val store=StudioStore(prefs,"portrait");store.apply(ref);store.apply(ref.copy(snap=4))
        prefs.edit().putString("studio_native_v1_hand_portrait","broken").commit()
        assertEquals(ref,StudioStore(prefs,"portrait").load())
    }
    @Test fun draftAndUndoRedoNeverChangeAppliedLayout() {
        val store=StudioStore(prefs(),"portrait");store.apply(ref)
        val history=StudioHistory(ref);history.change(ref.copy(snap=4));store.saveDraft(history.current)
        assertEquals(ref,store.load());assertEquals(4,store.draft()!!.snap)
        history.undo();assertEquals(ref,history.current);history.redo();assertEquals(4,history.current.snap)
        history.undo();history.change(ref.copy(snap=2));assertFalse(history.canRedo)
    }
    @Test fun unsupportedViewportAndLargeTextAreFlaggedWithoutMovingSavedElements() {
        assertTrue(ref.viewportProblems(412f).isEmpty())
        assertTrue(ref.viewportProblems(344f).isNotEmpty())
        assertTrue(ref.viewportProblems(412f,4f).isNotEmpty())
        assertTrue(ref.viewportProblems(892f,1f,412f).isNotEmpty())
        assertEquals(8f,ref.elements.first().x)
    }
    @Test fun handSlotGapsCannotHideActualCards() {
        reject(ref.copy(elements=ref.elements.filterNot{it.id=="card2"}))
        reject(ref.copy(elements=ref.elements.map{if(it.id=="card6")it.copy(binding="card0") else it}))
    }
    @Test fun customLabelsNeverDisguisePrimaryActionOrFreezeLiveCounts() {
        val labels=mapOf("pass" to "DECLARE BLOCKERS","metric1" to "LIBRARY\n83","actions" to "ACTIONS · 2")
        assertEquals("DECLARE BLOCKERS",studioLiveLabel(ref.elements.first(),labels))
        assertEquals("LIBRARY\n83",studioLiveLabel(ref.elements.first{it.id=="metric1"}.copy(label="LIBRARY|92"),labels))
        assertEquals("Choices · 2",studioLiveLabel(ref.elements.first{it.id=="actions"}.copy(label="Choices · 4"),labels))
    }
}
