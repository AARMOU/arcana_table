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
        assertTrue(runCatching{StudioLayout.parse(JSONObject(ref.json()).put("version",999).toString())}.isFailure)
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
    @Test fun versionOneImportsMigrateWithoutChangingOwnerCoordinates() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val original=JSONObject(context.assets.open("studio/owner-hand.json").bufferedReader().use {it.readText()})
        val migrated=StudioLayout.parse(original.toString())
        assertEquals(2,JSONObject(migrated.json()).getInt("version"))
        assertEquals(412f,migrated.canvasWidth);assertEquals(928f,migrated.canvasHeight);assertEquals(344f,migrated.originY)
        assertEquals(original.getJSONObject("layouts").getJSONArray("hand").getJSONObject(0).getDouble("x").toFloat(),migrated.elements[0].x)
    }
    @Test fun allDensityProfilesFitCoverUnfoldedAndLandscapeGeometry() {
        for((w,h) in listOf(344f to 882f,412f to 928f,673f to 841f,892f to 412f)) {
            for(density in listOf("Minimal","Balanced","Detailed")) {
                val layout=StudioLayout.responsive(w,h,density)
                assertEquals("$w/$h/$density: ${layout.problems()}",emptyList<String>(),layout.problems())
                assertTrue(layout.viewportProblems(w,1f,h).isEmpty())
                assertEquals(layout,StudioLayout.parse(layout.json()))
            }
        }
    }
    @Test fun namedCopyDraftDoesNotChangeActiveProfileUntilApply() {
        val p=prefs();val profiles=StudioProfiles(p,"portrait")
        StudioStore(p,"portrait").apply(ref)
        val copy=profiles.create("My Hand",ref.copy(snap=4))
        assertEquals("Default",profiles.activeName());assertNull(copy.load());assertEquals(4,copy.draft()!!.snap)
        copy.apply(copy.draft()!!)
        assertEquals("My Hand",profiles.activeName());assertEquals(4,profiles.activeStore().load()!!.snap)
        assertEquals(ref,StudioStore(p,"portrait").load())
        assertEquals("Default",StudioProfiles(p,"landscape").activeName())
    }
    @Test fun resettingAllProfilesPreservesUnrelatedGameAndThemePreferences() {
        val p=prefs();p.edit().putString("saved_game","keep").putBoolean("dark_mode",true).commit()
        val profiles=StudioProfiles(p,"portrait");profiles.create("Custom",ref).apply(ref)
        StudioStore(p,"landscape").apply(StudioLayout.responsive(892f,412f))
        profiles.resetAll()
        assertNull(StudioStore(p,"portrait","Custom").load());assertNull(StudioStore(p,"landscape").load())
        assertEquals("keep",p.getString("saved_game",null));assertTrue(p.getBoolean("dark_mode",false))
    }
    @Test fun iconsAndTextColorsRoundTripAndUnknownIconsFailClosed() {
        val layout=ref.copy(elements=ref.elements.map{if(it.id=="cast")it.copy(icon="Play",textColor="#FFFFFF")else it})
        assertEquals(layout,StudioLayout.parse(layout.json()))
        reject(layout.copy(elements=layout.elements.map{it.copy(icon="unbound")}))
    }
    @Test fun duplicateAndRemoveCardSlotsKeepEveryLiveIndexReachable() {
        val duplicate=ref.duplicateElement("card2")
        assertTrue(duplicate.problems().isEmpty())
        assertEquals("card7",duplicate.elements.last().binding)
        val removed=duplicate.removeElement("card2")
        assertTrue(removed.problems().isEmpty())
        assertEquals((0..6).toList(),removed.elements.filter{it.kind=="card"}.map{it.binding.removePrefix("card").toInt()}.sorted())
    }
}
