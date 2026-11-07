package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * S0/S1–S4 coverage audit on the real MainActivity, out of a game and inside a real Argentum game (FakeArgentumServer
 * recorded payloads). For every screen reachable from the Studio screen picker: open it through the picker, confirm its
 * native controls are registered in the window on top, select one through the editor, restyle it through the inspector,
 * then Apply once and confirm every edit persisted and survives an activity restart. Writes a coverage matrix to
 * build/studio-coverage.md. Robolectric/JVM evidence only — not a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
class StudioCoverageAuditTest {
    @get:Rule val rule=createEmptyComposeRule()
    private val fake=FakeArgentumServer()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun prefs()=context.getSharedPreferences("arcana_options",Context.MODE_PRIVATE)
    private data class Row(val screen:String,val window:String,val actions:Int,val data:Int,val selected:String?,val edited:Boolean,val id:String?,var persisted:Boolean=false)
    private val rows=mutableListOf<Row>()

    @Before fun setUp() {
        fake.start()
        var id=3000
        fun card(name:String)=JSONObject().put("id",id++).put("name",name).put("type","CARD").put("colorArgb",0xFF547D9A.toInt())
            .put("tapped",false).put("token",false).put("faceDown",false).put("keywords",JSONArray()).put("counters",JSONObject())
        val library=JSONArray().apply{repeat(99){put(card("Forest"))}}
        fun seat(name:String,sid:String,computer:Boolean,lib:JSONArray,cmd:JSONObject?)=JSONObject()
            .put("name",name).put("id",sid).put("commander",cmd?.getString("name") ?: JSONObject.NULL).put("commanderCard",cmd ?: JSONObject.NULL)
            .put("computer",computer).put("deckLoaded",lib.length()>0).put("life",40)
            .put("library",lib).put("hand",JSONArray()).put("battlefield",JSONArray()).put("graveyard",JSONArray()).put("exile",JSONArray()).put("revealed",JSONArray())
        context.getSharedPreferences("arcana-table",Context.MODE_PRIVATE).edit()
            .putString("players",JSONArray().put(seat("TESTER","s0",false,library,card("Bello, Bard of the Brambles"))).put(seat("AI","s1",true,JSONArray(),null)).toString()).commit()
        context.getSharedPreferences("arcana_tutorial",Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2",setOf("HOME","TABLE","PLAYER","HAND","MENU_MAIN","MENU_TOOLS","MENU_GAME","MENU_DECKS","LOBBY","OPTIONS","DECK_IMPORT","STACK","LOG")).commit()
        context.getSharedPreferences("argentum_session",Context.MODE_PRIVATE).edit().putString(ArgentumEndpoints.PREF_ORIGIN,fake.origin).commit()
        StudioProfiles(prefs(),"portrait").resetAll()
    }
    @After fun tearDown(){fake.shutdown();StudioProfiles(prefs(),"portrait").resetAll()}

    private fun runtime(s:ActivityScenario<MainActivity>):StudioControlsRuntime{var r:StudioControlsRuntime?=null;s.onActivity{r=it.tableController!!.studioControlsRuntime};return r!!}
    private fun mainWindow(s:ActivityScenario<MainActivity>):Int{var w=0;s.onActivity{w=System.identityHashCode(it.window.decorView.rootView)};return w}
    private fun last(tag:String)=rule.onAllNodesWithTag(tag).onLast()

    private fun openStudio(s:ActivityScenario<MainActivity>) {
        s.onActivity{it.tableController!!.studioControlsRuntime!!.enableCatalog()}
        UiTestSupport.settle(rule)
        rule.onNodeWithTag("studio-root-escape").performClick();UiTestSupport.settle(rule)
        rule.onNodeWithTag("studio-editor").assertExists()
    }

    /** Opens [screen] through the real picker, then selects and restyles one native control in the window on top. */
    private fun audit(s:ActivityScenario<MainActivity>,screen:String,tag:String) {
        last("studio-screen-picker").performClick();UiTestSupport.settle(rule)
        last("studio-screen-$screen").performScrollTo().performClick();UiTestSupport.settle(rule,2_500)
        val r=runtime(s);val main=mainWindow(s)
        val live=r.controls.values.filter{it.bounds.width>0f && it.window!=0}
        val dialog=live.any{it.window!=main}
        val window=if(dialog)live.first{it.window!=main}.window else main
        val mine=live.filter{it.window==window}
        var height=0f;s.onActivity{height=it.window.decorView.height.toFloat()}
        val target=mine.filter{it.isAction || it.kind=="data"}.filter{it.bounds.center.y in height*0.15f..height*0.75f}
            .minByOrNull{it.bounds.width*it.bounds.height} ?: mine.firstOrNull()
        var selected:String?=null;var edited=false
        if(target!=null) {
            if(!dialog) rule.onNodeWithTag("studio-canvas").performTouchInput{click(target.bounds.center)}
            else last("studio-window-canvas").performTouchInput{click(target.bounds.center)} // the dialog window's own canvas
            UiTestSupport.settle(rule)
            selected=r.selected
            if(selected!=null) {
                last("studio-tab-Style").performClick();UiTestSupport.settle(rule)
                last("studio-fill-Gold").performClick();UiTestSupport.settle(rule)
                edited=r.style(selected).fill==tableColorHex(darkTableColors.counter)
            }
        }
        // Direct drag inside a dialog window moves the real control (one undo step), then Undo restores it.
        if(dialog && selected!=null && screen=="Settings") {
            val before=r.style(selected).dy
            last("studio-window-canvas").performTouchInput{down(r.controls[selected]!!.bounds.center);repeat(8){moveBy(androidx.compose.ui.geometry.Offset(0f,6f))};up()}
            UiTestSupport.settle(rule)
            assertTrue("dialog drag moved ${r.controls[selected]?.title}",r.style(selected).dy>before)
            last("studio-undo").performClick();UiTestSupport.settle(rule)
            assertEquals(before,r.style(selected).dy,0.01f)
        }
        s.onActivity{UiTestSupport.screenshot(it,"studio-audit-$tag-$screen".replace(' ','-'))}
        rows+=Row(screen,if(dialog)"dialog" else "main",mine.count{it.isAction},mine.count{!it.isAction},selected?.let{r.controls[it]?.title?.take(40)},edited,selected)
    }

    private fun applyAndVerify(s:ActivityScenario<MainActivity>,tag:String) {
        val r=runtime(s)
        val problems=r.problems()
        val ids=rows.filter{it.edited}.map{it to it.id!!}
        last("studio-apply").performClick();UiTestSupport.settle(rule)
        assertTrue("Apply blocked: $problems",!runtime(s).editing)
        val saved=StudioControlStore(prefs(),"portrait",runtime(s).profile).load()
        ids.forEach{(row,id)->row.persisted=saved.controls[id]?.fill==tableColorHex(darkTableColors.counter)}
        s.recreate();UiTestSupport.settle(rule)
        val after=runtime(s)
        ids.forEach{(row,id)->if(after.style(id).fill!=tableColorHex(darkTableColors.counter))row.persisted=false}
        writeMatrix(tag)
    }

    private fun writeMatrix(tag:String) {
        val text=buildString {
            appendLine("| Screen ($tag) | Window | Actions | Live info/labels | Selected in editor | Restyled | Applied + survives restart |")
            appendLine("|---|---|---|---|---|---|---|")
            rows.forEach{appendLine("| ${it.screen} | ${it.window} | ${it.actions} | ${it.data} | ${it.selected ?: "—"} | ${if(it.edited)"✅" else "❌"} | ${if(it.persisted)"✅" else "❌"} |")}
        }
        println(text)
        File("build/studio-coverage-$tag.md").writeText(text)
    }

    @Test fun outOfGameScreens() {
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            UiTestSupport.settle(rule)
            if(rule.onAllNodesWithText("NOT NOW").fetchSemanticsNodes().isNotEmpty()){rule.onNodeWithText("NOT NOW").performClick();UiTestSupport.settle(rule)}
            openStudio(s)
            listOf("Home","Setup","Lobby","Decks","Settings").forEach{audit(s,it,"menu")}
            last("studio-screen-picker").performClick();UiTestSupport.settle(rule)
            last("studio-screen-Home").performClick();UiTestSupport.settle(rule)
            applyAndVerify(s,"menu")
            rows.forEach{assertTrue("${it.screen}: ${it}",it.actions+it.data>0 && it.edited && it.persisted)}
        } finally {s.close()}
    }

    @Test fun inGameScreens() {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            UiTestSupport.settle(rule)
            UiTestSupport.playWithArgentum(rule,s)
            UiTestSupport.awaitReal(rule,30_000,"opening hand"){rule.onAllNodesWithText("KEEP ",substring=true).fetchSemanticsNodes().isNotEmpty()}
            rule.onAllNodesWithText("KEEP ",substring=true)[0].performClick()
            UiTestSupport.awaitReal(rule,30_000,"Argentum table"){rule.onAllNodes(hasTestTag("phase-tracker"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            UiTestSupport.settle(rule,1_000)
            openStudio(s)
            listOf("Table","Player","Stack","Zones","Game log","Settings","Decks").forEach{audit(s,it,"game")}
            // Hand: the shell's own hand screen, with the player's real hand.
            last("studio-screen-picker").performClick();UiTestSupport.settle(rule)
            last("studio-screen-Hand").performScrollTo().performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-workspace").assertIsDisplayed()
            s.onActivity{UiTestSupport.screenshot(it,"studio-audit-game-Hand")}
            last("studio-screen-picker").performClick();UiTestSupport.settle(rule)
            last("studio-screen-Table").performClick();UiTestSupport.settle(rule)
            applyAndVerify(s,"game")
            rows.forEach{assertTrue("${it.screen}: ${it}",it.actions+it.data>0 && it.edited && it.persisted)}
        } finally {s.close()}
    }
}
