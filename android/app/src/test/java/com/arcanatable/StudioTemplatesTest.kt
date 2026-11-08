package com.arcanatable

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The example templates in docs/studio-ux/templates import cleanly and match [StudioTemplates]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class StudioTemplatesTest {
    private val dir=listOf(File("../../docs/studio-ux/templates"),File("docs/studio-ux/templates")).first{it.isDirectory}

    @Test fun everyTemplateImportsAndApplies() {
        StudioTemplates.all.forEach {(name,text)->
            // Compared as Studio data, not text: Android's JSON writer orders keys differently from desktop org.json.
            fun parts(t:String)=JSONObject(t).let{StudioControlProject.parse(it.getJSONObject("controls").toString()) to StudioLayout.parse(it.getJSONObject("hand").toString())}
            assertEquals("$name is out of date: rerun StudioTemplates.main",parts(text),parts(File(dir,name).readText()))
            val prefs=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("tpl-${System.nanoTime()}",0)
            val runtime=StudioControlsRuntime(prefs,STUDIO_LAYOUT_KEY);runtime.begin()
            runtime.importProject(text)
            assertTrue("$name: ${runtime.problems()}",runtime.problems().isEmpty())
            assertEquals(JSONObject(text).getJSONObject("controls").getJSONObject("theme").length(),runtime.draft.theme.size)
            assertNotNull(runtime.handDraft())
            runtime.apply()
        }
    }
}
