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
    val binding: String = id, val color: String? = null, val textColor: String? = null,
    val icon: String = "None",
)

internal data class StudioLayout(val elements: List<StudioElement>, val snap: Int = 8,
    val theme: Map<String,String> = emptyMap(),
    val canvasWidth: Float = 412f, val canvasHeight: Float = 928f, val originY: Float = 344f) {
    /**
     * Owner feedback note 8: a saved hand design is never thrown out in a game. Small buttons grow to a tappable size
     * when drawn ([StudioHandCanvas]) instead of the whole design falling back to the native hand with a warning.
     * Only a viewport that can't be drawn at all is reported.
     */
    fun viewportProblems(widthDp: Float, fontScale: Float = 1f, heightDp: Float = 928f): List<String> = buildList {
        val scale=widthDp/canvasWidth
        if(!scale.isFinite() || scale<=0 || !fontScale.isFinite() || !heightDp.isFinite()) add("Invalid viewport.")
    }
    fun colors(base: TableColors): TableColors = theme.entries.fold(base) { colors,(key,value) ->
        parseTableColorHex(value)?.let {colors.withColorRole(if(key=="gold")"counter" else key,it)} ?: colors
    }
    fun duplicateElement(id:String):StudioLayout {
        val e=elements.first{it.id==id}
        val next=elements.filter{it.kind=="card"}.size
        val copy=if(e.kind=="card")e.copy(id="slot"+java.util.UUID.randomUUID().toString().take(8),binding="card$next",label="Card ${next+1}",
            y=(elements.maxOfOrNull{it.y+it.h} ?: originY)+8f)
            else e.copy(id="copy"+java.util.UUID.randomUUID().toString().take(8),y=e.y+e.h+8f)
        return this.copy(elements=elements+copy)
    }
    fun removeElement(id:String):StudioLayout {
        val remaining=elements.filterNot{it.id==id}
        val order=remaining.filter{it.kind=="card"}.sortedBy{it.binding.removePrefix("card").toIntOrNull() ?: 0}
        return copy(elements=remaining.map {e->if(e.kind=="card")e.copy(binding="card${order.indexOf(e)}") else e})
    }
    fun json(): String = JSONObject().put("version", 2).put("screen", "hand")
        .put("canvas",JSONObject().put("width",canvasWidth).put("height",canvasHeight).put("originY",originY))
        .put("snap", snap).put("theme",JSONObject(theme)).put("layouts", JSONObject().put("hand", JSONArray().apply {
            elements.forEach { e -> put(JSONObject().put("id", e.id).put("kind", e.kind).put("label", e.label)
                .put("x", e.x).put("y", e.y).put("w", e.w).put("h", e.h).put("fs", e.fs).put("r", e.r)
                .put("locked", e.locked).put("strong", e.strong).put("visible", e.visible)
                .put("binding", e.binding).put("icon",e.icon).apply {
                    e.color?.let { put("color", it) }; e.textColor?.let {put("textColor",it)}
                }) }
        })).toString()

    /**
     * Hard problems only: a design that is malformed, or that would lock the player out (no Pass, no hand). Owner note 14:
     * sizes, overlaps, hidden counters/buttons and items past the edge are [advice], never a reason to refuse Apply.
     */
    fun problems(): List<String> = buildList {
        if (elements.size !in 1..64) add("Use at most 64 elements.")
        if (elements.map { it.id }.distinct().size != elements.size) add("Element IDs must be unique.")
        if (snap !in 1..32) add("Snap must be 1–32.")
        if(!canvasWidth.isFinite() || !canvasHeight.isFinite() || !originY.isFinite() ||
            canvasWidth !in 240f..2000f || canvasHeight !in 240f..4000f || originY<0 || originY>canvasHeight-96)
            add("Invalid canvas dimensions or hand origin.")
        if (theme.keys.any { it !in themeRoles } || theme.values.any { parseTableColorHex(it)==null }) add("Invalid theme color roles.")
        elements.forEach { e ->
            if (e.id.isBlank() || e.id.length > 64 || e.label.length > 80) add("Invalid element identity or label.")
            if (e.kind !in setOf("button", "metric", "card")) add("Unsupported element kind: ${e.kind}")
            if (listOf(e.x,e.y,e.w,e.h,e.fs,e.r).any { !it.isFinite() } || e.w <= 0 || e.h <= 0 || e.w > 4000 || e.h > 4000 ||
                kotlin.math.abs(e.x) > 4000 || kotlin.math.abs(e.y) > 8000 || e.fs !in 8f..32f || e.r !in 0f..64f)
                add("${e.id}: invalid geometry.")
            if (e.kind == "card") {
                if (e.binding.removePrefix("card").toIntOrNull() !in 0..31 || !e.binding.startsWith("card")) add("${e.id}: invalid hand card slot.")
            } else if (e.binding !in bindings) add("${e.id}: unknown action binding.")
            if (e.color != null && parseTableColorHex(e.color) == null) add("${e.id}: invalid color.")
            if (e.textColor != null && parseTableColorHex(e.textColor) == null) add("${e.id}: invalid text color.")
            if(e.icon !in icons) add("${e.id}: unknown icon.")
        }
        // Lock-outs (owner: Pass and the way back can't be hidden). Without a visible card slot the hand can't be played.
        if (elements.none { it.kind != "card" && it.binding == "pass" && it.visible }) add("Keep Pass visible.")
        if (elements.none { it.kind == "card" && it.binding == "card0" && it.visible }) add("Keep the first hand card slot visible.")
        val slots=elements.filter {it.kind=="card" && it.visible}.mapNotNull {it.binding.removePrefix("card").toIntOrNull()}.sorted()
        if(slots != (0 until slots.size).toList()) add("Hand card slots must be unique and consecutive from card0.")
    }

    /** Soft advice per element id (owner note 14): shown as red marking in the editor, never blocks Apply. */
    fun advice(): Map<String,String> = buildMap {
        val visible = elements.filter { it.visible }
        visible.forEach { e ->
            if (e.kind != "card" && (e.w < 44 || e.h < 44)) put(e.id, "Hard to tap: smaller than 44.")
            if (e.x < 0 || e.y < originY || e.x + e.w > canvasWidth) put(e.id, "Partly outside the hand.")
        }
        visible.forEachIndexed { i, a -> visible.drop(i+1).forEach { b ->
            if (a.x < b.x+b.w && a.x+a.w > b.x && a.y < b.y+b.h && a.y+a.h > b.y) { putIfAbsent(a.id, "Overlaps ${b.label}."); putIfAbsent(b.id, "Overlaps ${a.label}.") }
        } }
    }

    companion object {
        val themeRoles=listOf("background","panel","raised","accent","text","muted","gold")
        val icons=mapOf("None" to "", "Play" to "▶", "Undo" to "↶", "Stack" to "▤", "Library" to "▥", "Zone" to "◇", "Menu" to "⋮")
        val bindings = listOf("pass", "undo", "actions", "cast", "metric0", "metric1", "metric2", "metric3")
        fun parse(text: String): StudioLayout {
            require(text.length in 1..250_000) { "Project is empty or too large." }
            val obj = JSONObject(text)
            val version=obj.getInt("version")
            require(version in 1..2 && obj.optString("screen", "hand") == "hand") { "Unsupported Studio version/screen." }
            val canvas=if(version==2) obj.getJSONObject("canvas") else null
            val array = obj.getJSONObject("layouts").getJSONArray("hand")
            require(array.length() <= 64) { "Too many elements." }
            return StudioLayout((0 until array.length()).map { i ->
                val e = array.getJSONObject(i)
                StudioElement(e.getString("id"), e.getString("kind"), e.optString("label"),
                    e.getDouble("x").toFloat(), e.getDouble("y").toFloat(), e.getDouble("w").toFloat(), e.getDouble("h").toFloat(),
                    e.optDouble("fs",11.0).toFloat(), e.optDouble("r",10.0).toFloat(), e.optBoolean("locked"),
                    e.optBoolean("strong"), e.optBoolean("visible",true), e.optString("binding",e.getString("id")),
                    e.optString("color").takeIf { it.isNotEmpty() },e.optString("textColor").takeIf {it.isNotEmpty()},e.optString("icon","None"))
            }, obj.optInt("snap",8), obj.optJSONObject("theme")?.let { theme ->
                themeRoles.filter {theme.has(it)}.associateWith {theme.getString(it)}
            }.orEmpty(),canvas?.getDouble("width")?.toFloat() ?: 412f,
                canvas?.getDouble("height")?.toFloat() ?: 928f,canvas?.getDouble("originY")?.toFloat() ?: 344f
            ).also { require(it.problems().isEmpty()) { it.problems().joinToString("\n") } }
        }
        fun responsive(width: Float,height: Float,density: String="Balanced"): StudioLayout {
            val w=width.coerceIn(240f,2000f)
            val h=height.coerceIn(240f,4000f)
            val origin=if(w>h) 64f else (h*.32f).coerceAtLeast(64f)
            val columns=if(w<380) 2 else 4
            val cell=(w-32f-(columns-1)*8f)/columns
            val rowHeight=if(density=="Detailed")64f else 48f
            val ids=bindings
            val labels=listOf("PASS","UNDO","ACTIONS","CAST","STACK","LIBRARY","GRAVEYARD","EXILE")
            val rows=(ids.size+columns-1)/columns
            val cardWidth=when(density){"Minimal"->72f;"Detailed"->120f;else->92f}.coerceAtMost(w-32)
            val cardColumns=((w-32+8)/(cardWidth+8)).toInt().coerceAtLeast(1)
            val slots=if(density=="Minimal")cardColumns else 7
            return StudioLayout(ids.mapIndexed {i,id ->
                StudioElement(id,if(i<4)"button" else "metric",labels[i],16f+(i%columns)*(cell+8),origin+(i/columns)*(rowHeight+8),cell,rowHeight,strong=i==0)
            }+(0 until slots).map {i->StudioElement("card$i","card","Card ${i+1}",16f+(i%cardColumns)*(cardWidth+8),
                origin+rows*(rowHeight+8)+(i/cardColumns)*(cardWidth*132/92+8),cardWidth,cardWidth*132/92,r=5f)},
                canvasWidth=w,canvasHeight=h,originY=origin)
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

/** One saved Studio layout for every orientation and fold state (owner: "one layout that adapts automatically"). */
internal const val STUDIO_LAYOUT_KEY = "portrait"

internal class StudioStore(internal val prefs: SharedPreferences, internal val orientation: String,
    internal val profile: String = "Default") {
    init {require(orientation in listOf("portrait","landscape"));require(profile.length in 1..40 && profile.all {it.isLetterOrDigit() || it in " _-"})}
    private val key = "studio_native_v1_hand_$orientation"+if(profile=="Default")"" else "_$profile"
    fun enabled() = prefs.getBoolean("${key}_enabled",false)
    fun load(): StudioLayout? = listOf(key,"${key}_backup").firstNotNullOfOrNull { k ->
        prefs.getString(k,null)?.let { runCatching { StudioLayout.parse(it) }.getOrNull() }
    }
    fun draft(): StudioLayout? = prefs.getString("${key}_draft",null)?.let { runCatching { StudioLayout.parse(it) }.getOrNull() }
    /** Owner feedback bug 19: drafts always save (only malformed data, which can't be reloaded, is skipped). */
    fun saveDraft(layout: StudioLayout) { if (layout.problems().isEmpty()) prefs.edit().putString("${key}_draft",layout.json()).apply() }
    fun clearDraft() { prefs.edit().remove("${key}_draft").apply() }
    fun apply(layout: StudioLayout) {
        check(stage(prefs.edit(),layout).putString("studio_active_v1_$orientation",profile).commit()) { "Could not save layout." }
    }
    fun stage(edit:SharedPreferences.Editor,layout:StudioLayout):SharedPreferences.Editor {
        require(layout.problems().isEmpty())
        load()?.let { edit.putString("${key}_backup",it.json()) }
        return edit.putString(key,layout.json()).putString("${key}_draft",layout.json()).putBoolean("${key}_enabled",true)
    }
    fun enable(value: Boolean) { prefs.edit().putBoolean("${key}_enabled",value).apply() }
    fun reset() { prefs.edit().remove(key).remove("${key}_backup").remove("${key}_draft").putBoolean("${key}_enabled",false).apply() }
    fun copySavedTo(target:StudioStore,draftOverride:StudioLayout?=null) {
        val edit=prefs.edit()
        load()?.let{edit.putString(target.key,it.json())}
        (draftOverride ?: draft() ?: load())?.let{edit.putString("${target.key}_draft",it.json())}
        edit.putBoolean("${target.key}_enabled",enabled()).commit()
    }
}

internal class StudioProfiles(private val prefs: SharedPreferences,val orientation: String) {
    private val namesKey="studio_profiles_v1_$orientation"
    private val activeKey="studio_active_v1_$orientation"
    fun names(): List<String> = (listOf("Default","Minimal","Balanced","Detailed")+
        prefs.getStringSet(namesKey,emptySet()).orEmpty()).distinct().sorted()
    fun activeName()=prefs.getString(activeKey,"Default").takeIf {it in names()} ?: "Default"
    fun activeStore()=StudioStore(prefs,orientation,activeName())
    fun activate(name: String) {require(name in names());prefs.edit().putString(activeKey,name).apply()}
    fun stageActive(edit:SharedPreferences.Editor,name:String):SharedPreferences.Editor {require(name in names());return edit.putString(activeKey,name)}
    fun create(name: String,layout: StudioLayout): StudioStore {
        require(name !in names()) {"A profile with this name already exists."}
        require(names().size<32){"Use at most 32 profiles per orientation."}
        val store=StudioStore(prefs,orientation,name)
        require(layout.problems().isEmpty())
        prefs.edit().putStringSet(namesKey,names().toSet()+name).commit()
        store.saveDraft(layout)
        return store
    }
    fun copyProfile(from:String,name:String,handDraft:StudioLayout?=null):StudioStore {
        require(name !in names() && names().size<32){"Choose a new profile name; at most 32 profiles are supported."}
        require(handDraft==null || handDraft.problems().isEmpty()){"Fix the hand draft before copying it."}
        val target=StudioStore(prefs,orientation,name)
        prefs.edit().putStringSet(namesKey,names().toSet()+name).commit()
        StudioStore(prefs,orientation,from).copySavedTo(target,handDraft)
        StudioControlStore(prefs,orientation,name).apply(StudioControlStore(prefs,orientation,from).load())
        return target
    }
    fun resetAll() {
        val edit=prefs.edit()
        prefs.all.keys.filter {it.startsWith("studio_native_") || it.startsWith("studio_profiles_") || it.startsWith("studio_active_") || it.startsWith("studio_controls_")}.forEach(edit::remove)
        edit.commit()
    }
}
