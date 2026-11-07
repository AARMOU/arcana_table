package com.arcanatable.ui
import androidx.test.core.app.ActivityScenario
import com.arcanatable.MainActivity
object J12Mode {
    const val name="baseline"
    fun applyEdit(s: ActivityScenario<MainActivity>, pump: ()->Unit) { pump() }
}
