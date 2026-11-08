package com.arcanatable.ui

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
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
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owner, Nov 7: moment-only screens (combat, opening hand, legal actions) open as "Show me…" samples in Studio, with no
 * real combat, mulligan or action list needed. One test per sample (they used to be one test that hung in CI).
 *
 * Hang guard: if a sample keeps the screen busy for 60 s, a watchdog writes what kept changing to
 * build/screenshots/studio-showme-hang-<sample>.txt, closes the sample (so the test can finish) and the test fails.
 * (The Nov 7 hang was the test, not the app: an animated scroll to an off-screen tab under a frozen test clock.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w412dp-h928dp-port-xhdpi")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StudioShowMeUiTest {
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

    /** Opens [tab] from the screen bar and runs [check], failing (instead of hanging) if the sample never settles. */
    private fun sample(tab:String,check:(ActivityScenario<MainActivity>)->Unit) {
        fake.dealOpeningHand=true
        val s=ActivityScenario.launch(MainActivity::class.java)
        try {
            startGameInStudio(s)
            var controller:TableController?=null;s.onActivity{controller=it.tableController}
            val changed=ConcurrentHashMap<String,Int>()
            val observer=Snapshot.registerApplyObserver{objs,_->objs.forEach{o->val k=o.toString().take(160);changed.merge(k,1,Int::plus)}}
            val done=AtomicBoolean(false);val hung=AtomicBoolean(false)
            val testThread=Thread.currentThread()
            val report=File(System.getProperty("arcana.screenshots") ?: "build/screenshots","studio-showme-hang-${tab.replace(' ','-')}.txt")
            fun write(stage:String)=runCatching {
                report.parentFile?.mkdirs()
                report.appendText(buildString {
                    appendLine("[$stage] Most-changed state while the '$tab' sample was open:")
                    changed.entries.sortedByDescending{it.value}.take(40).forEach{appendLine("${it.value}× ${it.key}")}
                    appendLine("Test thread ${testThread.name} (${testThread.state}):")
                    testThread.stackTrace.take(120).forEach{appendLine("  at $it")}
                    appendLine()
                })
            }
            val dog=Thread {
                fun waitFor(ms:Long):Boolean{val until=System.currentTimeMillis()+ms;while(!done.get() && System.currentTimeMillis()<until)Thread.sleep(250);return done.get()}
                if(waitFor(60_000))return@Thread
                hung.set(true);write("60 s");Snapshot.withMutableSnapshot{controller?.studioPreview=null}
                if(waitFor(20_000))return@Thread
                write("80 s, after closing the sample");testThread.interrupt()
                if(waitFor(20_000))return@Thread
                // Still stuck: stop this test JVM now (CI prints the report) rather than hold CI for 25 minutes.
                write("100 s, still stuck: stopping");Runtime.getRuntime().halt(3)
            }.apply{isDaemon=true;start()}
            try {
                // The cause of the old CI hang: these tabs sit past the screen edge, and performScrollTo's scroll is
                // animated. With the test clock frozen (UiTestSupport.settle) it never finished and was retried forever.
                rule.mainClock.autoAdvance=true
                rule.onNodeWithTag("studio-tab-screen-$tab").performScrollTo().performClick();UiTestSupport.settle(rule)
                if(!hung.get())check(s)
            } catch(t:Throwable) {
                if(hung.get())throw AssertionError("The '$tab' sample kept the screen busy: ${runCatching{report.readText()}.getOrDefault("")}",t)
                throw t
            } finally {done.set(true);dog.join(2_000);observer.dispose()}
            assertFalse("The '$tab' sample kept the screen busy: ${runCatching{report.readText()}.getOrDefault("")}",hung.get())
            s.onActivity{a->a.tableController!!.studioControlsRuntime!!.cancel()};UiTestSupport.settle(rule)
            s.onActivity{assertNull("samples close with Studio",it.tableController!!.studioPreview)}
        } finally {s.close()}
    }

    @Test fun a_actionsSampleOpensWithoutARealActionList()=sample("Actions") {s->
        assertTrue("actions sample shown",rule.onAllNodesWithText("Cast Grizzly Bears",substring=true).fetchSemanticsNodes().isNotEmpty())
        s.onActivity{UiTestSupport.screenshot(it,"studio-showme-actions")}
    }

    @Test fun b_openingHandSampleOpensWithoutAMulligan()=sample("Opening hand") {s->
        assertTrue("opening hand sample shown",rule.onAllNodesWithText("KEEP 7").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodes(hasTestTag("combat-board"),useUnmergedTree=true).assertCountEquals(0)
        s.onActivity{UiTestSupport.screenshot(it,"studio-showme-opening-hand")}
    }

    @Test fun c_combatSampleOpensWithoutRealCombat()=sample("Combat") {s->
        rule.onAllNodes(hasTestTag("combat-board"),useUnmergedTree=true).fetchSemanticsNodes().let{assertTrue("combat sample shown",it.isNotEmpty())}
        s.onActivity{UiTestSupport.screenshot(it,"studio-showme-combat")}
    }
}
