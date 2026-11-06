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
class StudioControlsTest {
    private val a="native:HomeScreen.OutlinedButton.1/10"
    private val b="native:HomeScreen.OutlinedButton.1/11"
    private fun prefs()=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("controls-${System.nanoTime()}",0)
    @Test fun resetUsesPersistedLogicalScreenAndDensityDoesNotLeakToOtherScreens() {
        val prefs=prefs();val runtime=StudioControlsRuntime(prefs,"portrait")
        runtime.record(StudioLiveControl(a,"Opening hand","action","Draw",true),{})
        runtime.record(StudioLiveControl(b,"Settings","action","Theme",true),{})
        runtime.begin();runtime.edit(a,StudioControlStyle(dx=8f));runtime.edit(b,StudioControlStyle(dx=16f))
        runtime.density("Opening hand","Minimal")
        assertFalse(runtime.style("native:HomeScreen.Text.1/20","Opening hand","data").visible)
        assertTrue(runtime.style("native:HomeScreen.Text.1/21","Settings","data").visible)
        runtime.apply()
        val reopened=StudioControlsRuntime(prefs,"portrait");reopened.begin()
        assertEquals("Opening hand",reopened.draft.owners[a])
        reopened.resetScreen("Opening hand")
        assertFalse(reopened.draft.controls.containsKey(a));assertTrue(reopened.draft.controls.containsKey(b))
        assertEquals("Balanced",reopened.draft.screenDensity["Opening hand"])
        reopened.undo();assertTrue(reopened.draft.controls.containsKey(a))
    }
    @Test fun appMenuEscapeCannotBeHiddenWithoutReplacement() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        val menu="native:TableMainLayout.IconButton.2/10"
        runtime.record(StudioLiveControl(menu,"Table","icon-action","App menu",true),{})
        runtime.begin(menu);runtime.edit(menu,StudioControlStyle(visible=false))
        assertTrue(runtime.problems().any{it.contains("required action")})
        runtime.duplicate(menu,1f);assertTrue(runtime.problems().isEmpty())
    }
    @Test fun informationBindingsRejectActionsAndOtherWindows() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        val info="native:HomeScreen.Text.1/10";val other="native:HomeScreen.Text.2/10"
        runtime.record(StudioLiveControl(info,"Home","data","Life",true,window=1),null)
        runtime.record(StudioLiveControl(other,"Home","data","Private dialog",true,window=2),null)
        runtime.record(StudioLiveControl(a,"Home","action","Play",true,window=1),{})
        runtime.begin(info);runtime.duplicate(info,1f)
        val copy=runtime.draft.copies.single()
        runtime.record(StudioLiveControl(copy.id,"Home","data","Life",true,window=1,protectedLabel=true),null)
        runtime.edit(copy.id,copy.style.copy(binding=other));assertTrue(runtime.problems().isNotEmpty())
        runtime.edit(copy.id,copy.style.copy(binding=a));assertTrue(runtime.problems().isNotEmpty())
        runtime.edit(copy.id,copy.style.copy(binding=info));assertTrue(runtime.problems().isEmpty())
        assertFalse(runtime.available(copy.id));assertFalse(runtime.invoke(copy.id))
    }
    @Test fun onlyAdmittedNativeControlIdsCanBeImported() {
        val project=StudioControlProject(mapOf(a to StudioControlStyle(dx=12f,font=18f,ink="#FFFFFF")))
        assertEquals(project,StudioControlProject.parse(project.json()))
        assertTrue(runCatching{StudioControlProject.parse(StudioControlProject(mapOf("native:execute/arbitrary" to StudioControlStyle())).json())}.isFailure)
        assertTrue(runCatching{StudioControlProject.parse(JSONObject(project.json()).put("version",99).toString())}.isFailure)
    }
    @Test fun nonfiniteOrUnsafeDimensionsAndUnknownIconsFailValidation() {
        assertTrue(StudioControlStyle(dx=Float.NaN).problems().isNotEmpty())
        assertTrue(StudioControlStyle(width=20f).problems().isNotEmpty())
        assertTrue(StudioControlStyle(icon="network-fetch").problems().isNotEmpty())
        assertTrue(StudioControlStyle(fill="javascript:run()").problems().isNotEmpty())
    }
    @Test fun nativeCallbacksMustBeCurrentAndAvailable() {
        val runtime=StudioControlsRuntime(prefs(),"portrait");var calls=0
        runtime.record(StudioLiveControl(a,"Home","action","Play",true)){calls++}
        assertTrue(runtime.invoke(a));assertEquals(1,calls)
        runtime.record(StudioLiveControl(a,"Home","action","Play",false)){calls++}
        assertFalse(runtime.invoke(a));assertEquals(1,calls)
        runtime.remove(a);assertFalse(runtime.invoke(a))
    }
    @Test fun wrongScreenAndUnavailableRebindingsCannotApply() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        runtime.record(StudioLiveControl(a,"Home","action","Play",true)){}
        runtime.record(StudioLiveControl(b,"Game","action","Concede",true)){}
        runtime.begin(a);runtime.edit(a,StudioControlStyle(binding=b))
        assertTrue(runtime.problems().isNotEmpty());assertTrue(runCatching{runtime.apply()}.isFailure)
        runtime.record(StudioLiveControl(b,"Home","action","Import",false)){}
        assertTrue(runtime.problems().isNotEmpty())
    }
    @Test fun requiredActionsAndDynamicDataRemainTruthful() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        runtime.record(StudioLiveControl(a,"Table","action","PASS → BLOCKERS",true)){}
        runtime.begin(a);runtime.edit(a,StudioControlStyle(visible=false));assertTrue(runtime.problems().isNotEmpty())
        runtime.edit(a,StudioControlStyle());runtime.record(StudioLiveControl(b,"Table","data","Life 40",true,protectedLabel=true),null)
        runtime.edit(b,StudioControlStyle(label="Life 500"));assertTrue(runtime.problems().isNotEmpty())
    }
    @Test fun draftsUndoCancelAndProfileSelectionNeverRewriteAppliedProject() {
        val p=prefs();val runtime=StudioControlsRuntime(p,"portrait")
        runtime.begin();runtime.edit(a,StudioControlStyle(dx=20f));runtime.undo();assertTrue(runtime.draft.controls.isEmpty())
        runtime.redo();assertEquals(20f,runtime.draft.controls[a]!!.dx)
        runtime.apply();assertEquals(20f,StudioControlStore(p,"portrait","Default").load().controls[a]!!.dx)
        runtime.begin();runtime.edit(a,StudioControlStyle(dx=40f));runtime.cancel()
        assertEquals(20f,runtime.style(a).dx)
        runtime.begin();runtime.chooseProfile("Minimal");runtime.cancel();assertEquals("Default",runtime.profile)
        assertEquals(20f,runtime.style(a).dx)
        assertTrue(StudioControlStore(p,"landscape","Default").load().controls.isEmpty())
    }
    @Test fun corruptCurrentProjectUsesBackupAndResetLeavesOtherData() {
        val p=prefs();p.edit().putString("saved_game","preserved").commit()
        val store=StudioControlStore(p,"portrait","Default")
        val first=StudioControlProject(mapOf(a to StudioControlStyle(dx=8f)))
        store.apply(first);store.apply(first.copy(controls=mapOf(a to StudioControlStyle(dx=16f))))
        p.edit().putString("studio_controls_v1_portrait_Default","broken").commit()
        assertEquals(first,store.load());store.reset();assertTrue(store.load().controls.isEmpty())
        assertEquals("preserved",p.getString("saved_game",null))
    }
    @Test fun repeatedCompositionBasesAreClaimedUniquelyAndReleased() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        val first=Any();val second=Any()
        val x=runtime.claim(a,first);val y=runtime.claim(a,second)
        assertNotEquals(x,y);assertEquals(x,runtime.claim(a,first))
        runtime.release(a,first);assertEquals(x,runtime.claim(a,Any()))
    }
    @Test fun duplicateProjectsContainBindingsNotLiveTitlesAndRejectUnknownSources() {
        val copy=StudioControlCopy("copy:example",a,StudioControlStyle(dx=24f,dy=120f,width=100f,height=48f))
        val p=StudioControlProject(copies=listOf(copy),density="Minimal")
        assertEquals(p,StudioControlProject.parse(p.json()))
        assertTrue(runCatching{StudioControlProject.parse(p.copy(copies=listOf(copy,copy)).json())}.isFailure)
        assertTrue(runCatching{StudioControlProject.parse(p.copy(copies=listOf(copy.copy(source="native:unknown/action"))).json())}.isFailure)
    }
    @Test fun requiredOriginalCanBeHiddenOnlyWhenItsReplacementRemainsAvailableInTheDesign() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        runtime.record(StudioLiveControl(a,"Home","action","PASS priority",true)){}
        runtime.begin(a);runtime.duplicate(a,1f);runtime.edit(a,StudioControlStyle(visible=false))
        assertTrue(runtime.problems().isEmpty())
        val id=runtime.draft.copies.single().id
        runtime.edit(id,runtime.style(id).copy(visible=false));assertTrue(runtime.problems().isNotEmpty())
    }
    @Test fun sameScreenStillCannotRebindAcrossNativeWindows() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        runtime.record(StudioLiveControl(a,"Home","action","Play",true,window=1)){}
        runtime.record(StudioLiveControl(b,"Home","action","Leave modal",true,window=2)){}
        runtime.begin(a);runtime.edit(a,StudioControlStyle(binding=b));assertTrue(runtime.problems().isNotEmpty())
        assertFalse(runtime.invokeFrom(a,b))
    }
    @Test fun bundleImportUndoAndApplyTreatHandAndControlsAsOneProject() {
        val p=prefs();val hand=StudioStore(p,"portrait");hand.apply(StudioLayout.reference())
        val runtime=StudioControlsRuntime(p,"portrait");runtime.begin()
        val changed=StudioLayout.reference().copy(snap=4)
        val controls=StudioControlProject(mapOf(a to StudioControlStyle(dx=8f)),theme=mapOf("accent" to "#112233"))
        val bundle=JSONObject().put("version",3).put("renderer","arcana-native").put("orientation","portrait")
            .put("hand",JSONObject(changed.json())).put("controls",JSONObject(controls.json())).toString()
        runtime.importProject(bundle)
        assertEquals(8,hand.load()!!.snap);assertTrue(runtime.store().load().controls.isEmpty())
        runtime.undo();runtime.apply();assertEquals(8,hand.load()!!.snap)
        runtime.begin();runtime.importProject(bundle);runtime.apply()
        assertEquals(4,hand.load()!!.snap);assertEquals(controls,runtime.store().load())
        assertEquals("arcana-native",JSONObject(runtime.exportProject()).getString("renderer"))
    }
    @Test fun importedHandDraftNeverLeaksIntoAnotherProfileAndCopyIncludesIt() {
        val p=prefs();val original=StudioLayout.reference();StudioStore(p,"portrait").apply(original)
        val runtime=StudioControlsRuntime(p,"portrait");runtime.begin()
        val changed=original.copy(snap=4)
        val bundle=JSONObject(runtime.exportProject()).put("hand",JSONObject(changed.json())).toString()
        runtime.importProject(bundle);runtime.chooseProfile("Balanced");runtime.apply()
        assertNull(StudioStore(p,"portrait","Balanced").load())
        assertEquals(8,StudioStore(p,"portrait","Default").load()!!.snap)
        runtime.begin();runtime.chooseProfile("Default");runtime.copyProfile("Copied draft");runtime.apply()
        assertEquals(4,StudioStore(p,"portrait","Copied draft").load()!!.snap)
        assertEquals(8,StudioStore(p,"portrait","Default").load()!!.snap)
        runtime.begin();runtime.edit(a,StudioControlStyle(width=1f))
        assertTrue(runCatching{runtime.copyProfile("Invalid copy")}.isFailure)
        assertFalse("Invalid copy" in StudioProfiles(p,"portrait").names())
    }
    @Test fun bundleRejectsWrongOrientationAndMalformedHandBeforeMutatingDraft() {
        val runtime=StudioControlsRuntime(prefs(),"portrait");runtime.begin()
        val bundle=JSONObject(runtime.exportProject()).put("orientation","landscape")
        assertTrue(runCatching{runtime.importProject(bundle.toString())}.isFailure)
        bundle.put("orientation","portrait").put("hand",JSONObject("{}"))
        assertTrue(runCatching{runtime.importProject(bundle.toString())}.isFailure)
        assertTrue(runtime.draft.controls.isEmpty())
    }
    @Test fun paletteBelongsToProjectAndUnknownRolesCannotBeImported() {
        val theme=StudioControlProject(theme=mapOf("background" to "#010203","gold" to "#FFEEDD"))
        assertEquals(theme,StudioControlProject.parse(theme.json()))
        assertEquals("#010203",tableColorHex(theme.colors(darkTableColors).background))
        assertTrue(runCatching{StudioControlProject.parse(theme.copy(theme=mapOf("secret" to "#FFFFFF")).json())}.isFailure)
    }
    @Test fun defaultGameplayDoesNotBuildAnEditingRegistry() {
        val runtime=StudioControlsRuntime(prefs(),"portrait")
        assertFalse(runtime.active);runtime.begin();assertTrue(runtime.active)
        runtime.cancel();assertFalse(runtime.active)
    }
}
