package com.arcanatable.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ActivityScenario
import com.arcanatable.MainActivity
import com.arcanatable.startArgentumPractice
import java.io.File

/** Shared helpers for Robolectric UI tests. Screenshots go to app/build/screenshots. */
object UiTestSupport {
    private val dir: File by lazy {
        File(System.getProperty("arcana.screenshots") ?: "build/screenshots").apply { mkdirs() }
    }

    /**
     * Exercise the real server startup/validation path against the test server. Server play has
     * been parked since Build 113, so clicking its disabled menu starts an unrelated offline game.
     * Keep the activity's existing composition and touch-input owner, and use its current
     * controller. Production keeps the disabled menu.
     */
    fun playWithArgentum(rule: ComposeTestRule, scenario: ActivityScenario<MainActivity>) {
        if (rule.onAllNodesWithTag("quick-ui-tips").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("NOT NOW").performClick()
            settle(rule, 300)
        }
        scenario.onActivity { activity ->
            checkNotNull(activity.tableController).startArgentumPractice(vsAi = true)
        }
    }

    /** True while the home screen's PLAY button is showing. */
    fun homeShown(rule: ComposeTestRule): Boolean =
        rule.onAllNodesWithTag("home-play", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Invoke the real accessible action, including tabs whose tag is on a non-clickable wrapper. */
    fun clickTag(rule: ComposeTestRule, tag: String) {
        val taggedAction = hasTestTag(tag) and hasClickAction()
        val target = if (rule.onAllNodes(taggedAction).fetchSemanticsNodes().isNotEmpty()) taggedAction
            else hasAnyAncestor(hasTestTag(tag)) and hasClickAction()
        rule.onNode(target).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** The app runs an endless 1 s game-clock loop, so tests drive the Compose clock manually. */
    fun settle(rule: ComposeTestRule, ms: Long = 1_500) {
        rule.mainClock.autoAdvance = false
        var left = ms
        while (left > 0) {
            rule.mainClock.advanceTimeBy(100)
            shadowOf(Looper.getMainLooper()).idle()
            left -= 100
        }
    }

    /** Draws every app window (activity plus dialogs and popups, in z-order) into a PNG. */
    fun screenshot(activity: android.app.Activity, name: String): File {
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val views = runCatching {
            val global = Class.forName("android.view.WindowManagerGlobal")
            val instance = global.getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>).toList()
        }.getOrDefault(listOf(root))
        views.filter { it.isAttachedToWindow && it.width > 0 && it.height > 0 }.forEach { view ->
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            val params = view.layoutParams as? android.view.WindowManager.LayoutParams
            if (params != null && (params.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                canvas.drawColor(android.graphics.Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
            }
            canvas.save(); canvas.translate(location[0].toFloat(), location[1].toFloat()); view.draw(canvas); canvas.restore()
        }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /**
     * Waits in real time for network-driven UI (OkHttp threads post to the main looper), pumping
     * Robolectric's paused main looper and Compose between checks.
     */
    fun awaitReal(rule: ComposeTestRule, timeoutMs: Long, what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.mainClock.autoAdvance = false
            // Advance Robolectric's looper clock with real time so postDelayed work (reconnect backoff) runs.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250))
            rule.mainClock.advanceTimeBy(250)
            shadowOf(Looper.getMainLooper()).idle()
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(250)
        }
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $what")
    }
}
