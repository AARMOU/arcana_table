package com.arcanatable

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Studio's own log (owner, Nov 7: "I want it separate from the arcana debug logs").
 *
 * One small text file used only by Studio. It records what happened in the editor: opening and leaving, each edit
 * (what changed, from → to), Apply and what it saved, red advice at Apply, and errors. Nothing from Arcana's debug,
 * crash or game logs is written here, and nothing written here goes to them. It is capped so it never grows large,
 * and the owner shares or clears it from Studio's ⋮ menu.
 */
internal object StudioLog {
    /** Keep at most this many characters; past it the oldest part is dropped. Small enough to share as text. */
    const val CAP = 60_000
    private var file: File? = null
    private val lock = Any()
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(dir: File) { synchronized(lock) { file = File(dir, "studio/studio-log.txt").also { it.parentFile?.mkdirs() } } }

    fun add(event: String) {
        val f = synchronized(lock) { file } ?: return
        runCatching {
            synchronized(lock) {
                f.appendText("${clock.format(Date())}  ${event.replace('\n', ' ').take(600)}\n")
                if (f.length() > CAP + 8_000) {
                    val text = f.readText()
                    val keep = text.takeLast(CAP)
                    f.writeText("…older entries removed…\n" + keep.substringAfter('\n'))
                }
            }
        }
    }

    fun text(): String = synchronized(lock) { file?.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() } }.orEmpty()

    fun clear() { synchronized(lock) { runCatching { file?.writeText("") } } }

    /** "width 156→164, height 40→48" for the fields that changed between two styles. */
    fun diff(before: StudioControlStyle, after: StudioControlStyle): String {
        fun f(v: Float?) = v?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else "%.1f".format(Locale.US, it) } ?: "native"
        return buildList {
            if (before.dx != after.dx || before.dy != after.dy) add("position ${f(before.dx)},${f(before.dy)}→${f(after.dx)},${f(after.dy)}")
            if (before.width != after.width) add("width ${f(before.width)}→${f(after.width)}")
            if (before.height != after.height) add("height ${f(before.height)}→${f(after.height)}")
            if (before.visible != after.visible) add(if (after.visible) "shown" else "hidden")
            if (before.label != after.label) add("label \"${before.label ?: "native"}\"→\"${after.label ?: "native"}\"")
            if (before.binding != after.binding) add("action ${before.binding ?: "native"}→${after.binding ?: "native"}")
            if (before.fill != after.fill || before.ink != after.ink) add("colours ${before.fill ?: "-"}/${before.ink ?: "-"}→${after.fill ?: "-"}/${after.ink ?: "-"}")
            if (before.font != after.font) add("text size ${f(before.font)}→${f(after.font)}")
            if (before.radius != after.radius) add("corners ${f(before.radius)}→${f(after.radius)}")
            if (before.layer != after.layer) add("layer ${f(before.layer)}→${f(after.layer)}")
            if (before.icon != after.icon) add("icon ${before.icon ?: "-"}→${after.icon ?: "-"}")
        }.joinToString(", ").ifEmpty { "no change" }
    }

    /** What changed in the hand design, element by element. */
    fun handDiff(before: StudioLayout?, after: StudioLayout): String {
        if (before == null) return "hand design set (${after.elements.size} items)"
        val old = before.elements.associateBy { it.id }
        val lines = after.elements.mapNotNull { e ->
            val o = old[e.id] ?: return@mapNotNull "${e.label}: added"
            buildList {
                if (o.x != e.x || o.y != e.y) add("position ${o.x.toInt()},${o.y.toInt()}→${e.x.toInt()},${e.y.toInt()}")
                if (o.w != e.w || o.h != e.h) add("size ${o.w.toInt()}×${o.h.toInt()}→${e.w.toInt()}×${e.h.toInt()}")
                if (o.visible != e.visible) add(if (e.visible) "shown" else "hidden")
                if (o.label != e.label) add("label \"${o.label}\"→\"${e.label}\"")
                if (o.color != e.color || o.textColor != e.textColor || o.fs != e.fs || o.r != e.r || o.icon != e.icon) add("style")
            }.takeIf { it.isNotEmpty() }?.let { "${e.label}: ${it.joinToString(", ")}" }
        } + (old.keys - after.elements.map { it.id }.toSet()).map { "${old[it]!!.label}: removed" }
        return if (lines.isEmpty()) "hand: no change" else "hand: " + lines.joinToString("; ")
    }
}
