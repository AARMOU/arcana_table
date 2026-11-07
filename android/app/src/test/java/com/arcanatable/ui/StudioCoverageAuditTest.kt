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

    private fun startGame(s:ActivityScenario<MainActivity>) {
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule,s)
        UiTestSupport.awaitReal(rule,30_000,"opening hand"){rule.onAllNodesWithText("KEEP ",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithText("KEEP ",substring=true)[0].performClick()
        UiTestSupport.awaitReal(rule,30_000,"Argentum table"){rule.onAllNodes(hasTestTag("phase-tracker"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        UiTestSupport.settle(rule,1_000)
    }

    /**
     * Owner, Nov 7: "If I hide all the player chips and phase banner I want to be able to resize the board." Switching
     * pieces off in Show on screen removes their rows, so the table area below gets taller (Goose STUDIO-J13 acceptance).
     */
    @Test fun hidingScreenPiecesGivesTheirSpaceToTheBoard() {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            startGame(s)
            val before=rule.onNodeWithTag("table-pager",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val r=runtime(s)
            s.onActivity{r.begin();r.change(r.draft.copy(hiddenPieces=setOf("phaseBar","playerChips","tableButton","fullTableLabel","manaBar")));r.apply()}
            UiTestSupport.settle(rule,500)
            rule.onAllNodes(hasTestTag("phase-tracker"),useUnmergedTree=true).assertCountEquals(0)
            val after=rule.onNodeWithTag("table-pager",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue("board grew: $before -> $after",after.height>before.height+20f)
            s.onActivity{UiTestSupport.screenshot(it,"studio-show-on-screen-hidden")}
        } finally {s.close()}
    }

    /** Owner, Nov 7: moment-only screens open as samples in Studio, with no real combat, mulligan or action list needed. */
    @Test fun showMeSamplesOpenWithoutTheRealMoment() {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            startGame(s)
            s.onActivity{it.tableController!!.nativeStudioOpen=true};UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-tab-screen-Combat").performScrollTo().performClick();UiTestSupport.settle(rule)
            rule.onAllNodes(hasTestTag("combat-board"),useUnmergedTree=true).fetchSemanticsNodes().let{assertTrue("combat sample shown",it.isNotEmpty())}
            s.onActivity{UiTestSupport.screenshot(it,"studio-showme-combat")}
            rule.onNodeWithTag("studio-tab-screen-Opening hand").performScrollTo().performClick();UiTestSupport.settle(rule)
            assertTrue("opening hand sample shown",rule.onAllNodesWithText("KEEP 7").fetchSemanticsNodes().isNotEmpty())
            rule.onAllNodes(hasTestTag("combat-board"),useUnmergedTree=true).assertCountEquals(0)
            rule.onNodeWithTag("studio-tab-screen-Actions").performScrollTo().performClick();UiTestSupport.settle(rule)
            assertTrue("actions sample shown",rule.onAllNodesWithText("Cast Grizzly Bears",substring=true).fetchSemanticsNodes().isNotEmpty())
            s.onActivity{a->a.tableController!!.studioControlsRuntime!!.cancel()};UiTestSupport.settle(rule)
            assertTrue("samples close with Studio",rule.onAllNodesWithText("Cast Grizzly Bears",substring=true).fetchSemanticsNodes().isEmpty())
        } finally {s.close()}
    }

    /** Opens [screen] from the screen bar, then selects and restyles one control through the editing canvas. */
    private fun audit(s:ActivityScenario<MainActivity>,screen:String) {
        rule.onNodeWithTag("studio-tab-screen-$screen").performScrollTo().performClick();UiTestSupport.settle(rule,2_500)
        val r=runtime(s);val main=mainWindow(s)
        val popup=StudioEditorRequests.popups.lastOrNull()
        assertTrue("pop-ups are drawn in the main window while editing",r.controls.values.none{it.window!=0 && it.window!=main && it.bounds.width>0f})
        val mine=r.controls.values.filter{it.window==main && it.bounds.width>0f && (popup==null || it.screen in StudioEditorRequests.popups.map{p->p.screen})}
        var height=0f;s.onActivity{height=it.window.decorView.height.toFloat()}
        val target=mine.filter{it.isAction || it.kind=="data"}.filter{it.bounds.center.y in height*0.2f..height*0.7f}
            .minByOrNull{it.bounds.width*it.bounds.height} ?: mine.firstOrNull()
        var selected:String?=null;var edited=false
        if(target!=null) {
            rule.onNodeWithTag("studio-canvas").performTouchInput{click(target.bounds.center)};UiTestSupport.settle(rule)
            selected=r.selected
            if(selected!=null) {
                if(popup!=null)assertTrue("only the pop-up's own controls are selectable",r.controls[selected]!!.screen in StudioEditorRequests.popups.map{it.screen})
                rule.onNodeWithTag("studio-tab-Style").performClick();UiTestSupport.settle(rule)
                rule.onNodeWithTag("studio-fill-Gold").performClick();UiTestSupport.settle(rule)
                edited=r.style(selected).fill==tableColorHex(darkTableColors.counter)
            }
        }
        s.onActivity{UiTestSupport.screenshot(it,"studio-audit-game-$screen")}
        rows+=Row(screen,if(popup!=null)"pop-up (in place)" else "main",mine.count{it.isAction},mine.count{!it.isAction},
            selected?.let{r.controls[it]?.title?.take(40)},edited,selected)
        if(popup!=null) {
            rule.onNodeWithTag("studio-popup-back").assertIsDisplayed().performClick();UiTestSupport.settle(rule)
            assertTrue("Back closes the pop-up the normal way",StudioEditorRequests.popups.isEmpty())
            assertTrue("and Studio stays open",runtime(s).editing)
        }
    }

    private fun applyAndVerify(s:ActivityScenario<MainActivity>,tag:String) {
        val r=runtime(s)
        val problems=r.problems()
        val ids=rows.filter{it.edited}.map{it to it.id!!}
        rule.onNodeWithTag("studio-apply").performClick();UiTestSupport.settle(rule)
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
            appendLine("| Screen ($tag) | Drawn in | Actions | Live info/labels | Selected in editor | Restyled | Applied + survives restart |")
            appendLine("|---|---|---|---|---|---|---|")
            rows.forEach{appendLine("| ${it.screen} | ${it.window} | ${it.actions} | ${it.data} | ${it.selected ?: "—"} | ${if(it.edited)"✅" else "❌"} | ${if(it.persisted)"✅" else "❌"} |")}
        }
        println(text)
        File("build/studio-coverage-$tag.md").writeText(text)
    }

    @Test fun inGameScreensEditInPlaceFromTheScreenBar() {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            startGame(s)
            // ⋮ menu: Arcana Studio replaces Adjust UI (test builds).
            // Open the ⋮ menu the way the tutorial audit does (the opening-hand coach can swallow a synthetic tap here).
            s.onActivity{it.tableController!!.topMenuSection=null;it.tableController!!.topMenuExpanded=true};UiTestSupport.settle(rule,300)
            rule.onAllNodesWithText("Adjust UI").assertCountEquals(0)
            rule.onNodeWithTag("menu-open-studio").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-editor").assertIsDisplayed()
            listOf("Table","Player","Stack","Zones","Log").forEach{audit(s,it)}
            rule.onNodeWithTag("studio-tab-screen-Hand").performClick();UiTestSupport.settle(rule)
            rule.onNodeWithTag("studio-hand-drawer").assertIsDisplayed()
            s.onActivity{assertFalse("the in-game drawer stays closed while editing",it.tableController!!.showHand);UiTestSupport.screenshot(it,"studio-audit-game-Hand")}
            rule.onNodeWithTag("studio-tab-screen-Table").performClick();UiTestSupport.settle(rule)
            applyAndVerify(s,"game")
            rows.forEach{assertTrue("${it.screen}: ${it}",it.actions+it.data>0 && it.edited && it.persisted)}
        } finally {s.close()}
    }

    /** Studio never opens during a game with other people, and closes (keeping the draft) if one starts. */
    @Test fun studioIsUnavailableDuringGamesWithOtherPeople() {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            startGame(s)
            s.onActivity{it.tableController!!.argentumVsAiLobby=false};UiTestSupport.settle(rule)
            s.onActivity{it.tableController!!.nativeStudioOpen=true};UiTestSupport.settle(rule)
            rule.onAllNodesWithTag("studio-editor").assertCountEquals(0)
            assertFalse(runtime(s).editing)
            s.onActivity{it.tableController!!.argentumVsAiLobby=true};UiTestSupport.settle(rule)
            s.onActivity{it.tableController!!.nativeStudioOpen=true};UiTestSupport.settle(rule)
            assertTrue(runtime(s).editing)
            s.onActivity{it.tableController!!.argentumVsAiLobby=false};UiTestSupport.settle(rule)
            assertFalse("a live game closes Studio",runtime(s).editing)
        } finally {s.close()}
    }
}
