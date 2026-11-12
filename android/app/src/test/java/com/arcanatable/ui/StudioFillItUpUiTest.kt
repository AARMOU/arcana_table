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
import androidx.compose.ui.geometry.Offset

/**
 * Owner note 29 ("Fill it up") and note 5 (two-finger scroll) on the real app: the busy example table fills four seats
 * while editing, a two-finger drag scrolls the battlefield under the fingers without editing anything, and switching
 * Fill it up off puts the real players back without ever saving the example.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
class StudioFillItUpUiTest {
    @get:Rule val rule=createEmptyComposeRule()
    private val fake=FakeArgentumServer()
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun prefs()=context.getSharedPreferences("arcana_options",Context.MODE_PRIVATE)

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

    private fun startGameInStudio(s:ActivityScenario<MainActivity>) {
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule,s)
        UiTestSupport.awaitReal(rule,30_000,"opening hand"){rule.onAllNodesWithText("KEEP ",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithText("KEEP ",substring=true)[0].performClick()
        UiTestSupport.awaitReal(rule,30_000,"Argentum table"){rule.onAllNodes(hasTestTag("phase-tracker"),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        UiTestSupport.settle(rule,1_000)
        s.onActivity{it.tableController!!.nativeStudioOpen=true};UiTestSupport.settle(rule)
    }

    @Test fun fillItUpThenTwoFingerScrollTheBattlefield() {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            startGameInStudio(s)
            var controller:TableController?=null;s.onActivity{controller=it.tableController}
            val c=controller!!
            val realCount=c.players.size
            val saved=context.getSharedPreferences("arcana-table",Context.MODE_PRIVATE).getString("players",null)
            s.onActivity{c.studioControlsRuntime!!.fillItUp=true};UiTestSupport.settle(rule)
            assertEquals("four busy seats",4,c.players.size)
            assertTrue(c.players.all{it.battlefield.size==18})
            assertEquals("the example is never saved",saved,context.getSharedPreferences("arcana-table",Context.MODE_PRIVATE).getString("players",null))
            rule.onNodeWithTag("studio-tab-screen-Player").performScrollTo().performClick();UiTestSupport.settle(rule,2_500)
            val board=StudioScrollTargets.targets.values.filter{!it.horizontal && it.bounds.width>0f && it.state.canScrollForward}
                .maxByOrNull{it.bounds.width*it.bounds.height}
            assertNotNull("the busy battlefield can scroll",board)
            val before=c.studioControlsRuntime!!.draft
            val centre=board!!.bounds.center
            rule.onNodeWithTag("studio-canvas").performTouchInput {
                down(0,Offset(centre.x-60f,centre.y+200f));down(1,Offset(centre.x+60f,centre.y+200f))
                repeat(10){moveBy(0,Offset(0f,-40f));moveBy(1,Offset(0f,-40f))}
                up(0);up(1)
            }
            UiTestSupport.settle(rule)
            assertTrue("two fingers scrolled the battlefield",board.state.canScrollBackward)
            assertEquals("and edited nothing",before,c.studioControlsRuntime!!.draft)
            s.onActivity{c.studioControlsRuntime!!.fillItUp=false};UiTestSupport.settle(rule)
            assertEquals("the real players are back",realCount,c.players.size)
            assertNull(c.studioRealPlayers)
            s.onActivity{UiTestSupport.screenshot(it,"studio-fill-it-up-off")}
        } finally {s.close()}
    }
}
