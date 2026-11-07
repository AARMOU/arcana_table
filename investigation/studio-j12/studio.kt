package com.arcanatable.ui
import androidx.test.core.app.ActivityScenario
import com.arcanatable.*
object J12Mode {
    val unedited = System.getenv("J12_UNEDITED") == "1"
    val name get() = if (unedited) "studio-unedited" else "studio-applied"
    fun applyEdit(s: ActivityScenario<MainActivity>, pump: ()->Unit) {
        if (unedited) { pump(); return }
        s.onActivity { it.tableController!!.studioControlsRuntime!!.enableCatalog() }
        repeat(6) { pump() }
        s.onActivity { a ->
            val r=a.tableController!!.studioControlsRuntime!!
            val control=r.controls.values.first { it.screen=="Table" && it.kind=="data" }
            r.begin(control.id)
            r.change(r.draft.copy(controls=r.draft.controls + (control.id to StudioControlStyle(fill="#203040"))))
            r.apply()
            check(!r.editing && r.project().controls.size==1)
        }
        repeat(6) { pump() }
    }
}
