package com.arcanatable

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Saved design data never contains engine actions or card identities. */
internal data class StudioElement(
    val id: String, val kind: String, val label: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val fs: Float = 11f, val r: Float = 10f, val locked: Boolean = false,
    val strong: Boolean = false, val visible: Boolean = true,
    val binding: String = id, val color: String? = null,
)

internal data class StudioLayout(val elements: List<StudioElement>, val snap: Int = 8,
    val theme: Map<String,String> = emptyMap()) {
    fun viewportProblems(widthDp: Float, fontScale: Float = 1f, heightDp: Float = 928f): List<String> = buildList {
        val scale=widthDp/412f
        if(!scale.isFinite() || scale<=0) add("Invalid viewport.")
        if(elements.any {it.visible && it.kind!="card" && (it.w*scale<44f || it.h*scale<44f)})
            add("This design needs larger touch targets at this width; native hand retained.")
        if(elements.any {it.visible && it.kind!="card" && it.fs*fontScale*1.2f>it.h})
            add("Text size exceeds this design; native hand retained.")
        if(344f*scale+44f*scale>heightDp-24f)
            add("The reference hand origin does not fit this orientation; native hand retained.")
    }
    fun colors(base: TableColors): TableColors = theme.entries.fold(base) { colors,(key,value) ->
        parseTableColorHex(value)?.let {colors.withColorRole(if(key=="gold")"counter" else key,it)} ?: colors
    }
    fun json(): String = JSONObject().put("version", 1).put("screen", "hand")
        .put("snap", snap).put("theme",JSONObject(theme)).put("layouts", JSONObject().put("hand", JSONArray().apply {
            elements.forEach { e -> put(JSONObject().put("id", e.id).put("kind", e.kind).put("label", e.label)
                .put("x", e.x).put("y", e.y).put("w", e.w).put("h", e.h).put("fs", e.fs).put("r", e.r)
                .put("locked", e.locked).put("strong", e.strong).put("visible", e.visible)
                .put("binding", e.binding).apply { e.color?.let { put("color", it) } }) }
        })).toString()

    fun problems(): List<String> = buildList {
        if (elements.size !in 8..64) add("Use 8–64 elements.")
        if (elements.map { it.id }.distinct().size != elements.size) add("Element IDs must be unique.")
        if (snap !in 1..32) add("Snap must be 1–32.")
        if (theme.keys.any { it !in themeRoles } || theme.values.any { parseTableColorHex(it)==null }) add("Invalid theme color roles.")
        elements.forEach { e ->
            if (e.id.isBlank() || e.id.length > 64 || e.label.length > 80) add("Invalid element identity or label.")
            if (e.kind !in setOf("button", "metric", "card")) add("Unsupported element kind: ${e.kind}")
            if (listOf(e.x,e.y,e.w,e.h,e.fs,e.r).any { !it.isFinite() } || e.x < 0 || e.y < 344 ||
                e.w < 24 || e.h < 24 || e.x + e.w > 412 || e.y + e.h > 4000 || e.fs !in 8f..32f || e.r !in 0f..64f)
                add("${e.id}: geometry is outside the supported hand canvas.")
            if (e.kind == "card") {
                if (e.binding.removePrefix("card").toIntOrNull() !in 0..31 || !e.binding.startsWith("card")) add("${e.id}: invalid hand card slot.")
            } else if (e.binding !in bindings) add("${e.id}: unknown action binding.")
            if (e.color != null && parseTableColorHex(e.color) == null) add("${e.id}: invalid color.")
        }
        bindings.forEach { b -> if (elements.none { it.kind != "card" && it.binding == b && it.visible }) add("Keep $b reachable.") }
        if (elements.none { it.kind == "card" && it.binding == "card0" && it.visible }) add("Keep the first hand card slot visible.")
        val slots=elements.filter {it.kind=="card" && it.visible}.mapNotNull {it.binding.removePrefix("card").toIntOrNull()}.sorted()
        if(slots != (0 until slots.size).toList()) add("Hand card slots must be unique and consecutive from card0.")
        val visible = elements.filter { it.visible }
        visible.forEachIndexed { i, a -> visible.drop(i+1).forEach { b ->
            if (a.x < b.x+b.w && a.x+a.w > b.x && a.y < b.y+b.h && a.y+a.h > b.y) add("${a.id} overlaps ${b.id}.")
        } }
    }

    companion object {
        val themeRoles=listOf("background","panel","raised","accent","text","muted","gold")
        val bindings = listOf("pass", "undo", "actions", "cast", "metric0", "metric1", "metric2", "metric3")
        fun parse(text: String): StudioLayout {
            require(text.length in 1..250_000) { "Project is empty or too large." }
            val obj = JSONObject(text)
            require(obj.getInt("version") == 1 && obj.optString("screen", "hand") == "hand") { "Unsupported Studio version/screen." }
            val array = obj.getJSONObject("layouts").getJSONArray("hand")
            require(array.length() <= 64) { "Too many elements." }
            return StudioLayout((0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                StudioElement(e.getString("id"), e.getString("kind"), e.optString("label"),
                    e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.getDouble("w").toFloat(), e.getDouble("h").toFloat(),
                    e.optDouble("fs",11.0).toFloat(), e.optDouble("r",10.0).toFloat(), e.optBoolean("locked"),
                    e.optBoolean("strong"), e.optBoolean("visible",true), e.optString("binding",e.getString("id")),
                    e.optString("color").takeIf { it.isNotEmpty() })
            }, obj.optInt("snap",8), obj.optJSONObject("theme")?.let { theme ->
                themeRoles.filter {theme.has(it)}.associateWith {theme.getString(it)}
            }.orEmpty()).also { require(it.problems().isEmpty()) { it.problems().joinToString("\n") } }
        }
        fun reference() = StudioLayout(listOf(
            StudioElement("pass","button","PASS",8f,344f,145f,44f,strong=true),
            StudioElement("undo","button","UNDO",160f,344f,63f,44f),
            StudioElement("actions","button","ACTIONS",232f,344f,78f,44f),
            StudioElement("cast","button","CAST",320f,344f,71f,44f),
        ) + (0..3).map { i -> StudioElement("metric$i","metric",listOf("STACK","LIBRARY","GRAVEYARD","EXILE")[i],16f+i*96,400f,90f,55f,12f) }
            + (0..6).map { i -> StudioElement("card$i","card","Card ${i+1}",16f+(i%4)*96,464f+(i/4)*136,92f,132f,r=5f) })
    }
}

/** Editor history excludes applied game state; cancel never rewrites applied preferences. */
internal class StudioHistory(initial: StudioLayout) {
    var current = initial; private set
    private val back = ArrayDeque<StudioLayout>()
    private val forward = ArrayDeque<StudioLayout>()
    val canUndo get() = back.isNotEmpty()
    val canRedo get() = forward.isNotEmpty()
    fun change(next: StudioLayout) { if (next != current) { back.addLast(current); if (back.size > 100) back.removeFirst(); current=next; forward.clear() } }
    fun undo() { if (canUndo) { forward.addLast(current); current=back.removeLast() } }
    fun redo() { if (canRedo) { back.addLast(current); current=forward.removeLast() } }
}

internal class StudioStore(private val prefs: SharedPreferences, private val orientation: String) {
    private val key = "studio_native_v1_hand_$orientation"
    fun enabled() = prefs.getBoolean("${key}_enabled",false)
    fun load(): StudioLayout? = listOf(key,"${key}_backup").firstNotNullOfOrNull { k ->
        prefs.getString(k,null)?.let { runCatching { StudioLayout.parse(it) }.getOrNull() }
    }
    fun draft(): StudioLayout? = prefs.getString("${key}_draft",null)?.let { runCatching { StudioLayout.parse(it) }.getOrNull() }
    fun saveDraft(layout: StudioLayout) { if (layout.problems().isEmpty()) prefs.edit().putString("${key}_draft",layout.json()).apply() }
    fun apply(layout: StudioLayout) {
        require(layout.problems().isEmpty())
        val edit = prefs.edit()
        load()?.let { edit.putString("${key}_backup",it.json()) }
        check(edit.putString(key,layout.json()).putString("${key}_draft",layout.json()).putBoolean("${key}_enabled",true).commit()) { "Could not save layout." }
    }
    fun enable(value: Boolean) { prefs.edit().putBoolean("${key}_enabled",value).apply() }
    fun reset() { prefs.edit().remove(key).remove("${key}_backup").remove("${key}_draft").putBoolean("${key}_enabled",false).apply() }
}
