package com.arcanatable.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Shared helpers for Robolectric UI tests. Screenshots go to app/build/screenshots. */
object UiTestSupport {
    private val dir: File by lazy {
        File(System.getProperty("arcana.screenshots") ?: "build/screenshots").apply { mkdirs() }
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
