package com.arcanatable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Owner notes 16/17: a picture of the owner's own as the table background. The picture is copied (shrunk) into the
 * app's private storage, so it stays on the phone and keeps working if the original is moved or deleted. Studio
 * builds only, like the rest of Studio.
 */
internal object StudioBackdrop {
    private val NAME = Regex("^bg-[A-Za-z0-9-]{1,60}\\.(jpg|gif)$")
    /** Owner, Nov 7: GIFs play behind the table. Kept as the original file (up to [MAX_GIF_BYTES]) so they still move. */
    const val MAX_GIF_BYTES = 25L * 1024 * 1024
    fun isGif(name: String) = name.endsWith(".gif")
    fun isGifHeader(head: ByteArray) = String(head, Charsets.US_ASCII).startsWith("GIF8")
    const val MAX_SIDE = 2048
    val FITS = listOf("Fill", "Fit")
    fun validName(name: String) = NAME.matches(name)
    private fun dir(context: Context) = File(context.filesDir, "studio-backdrops").apply { mkdirs() }
    private val cache = mutableMapOf<String, ImageBitmap>()

    /** Copies the picked picture in, shrunk to at most [MAX_SIDE] pixels a side. Returns its stored name. */
    fun import(context: Context, uri: Uri): String {
        val head = context.contentResolver.openInputStream(uri)?.use { s -> ByteArray(6).also { s.read(it) } } ?: error("Can't open that picture.")
        if (isGifHeader(head)) return importGif(context, uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Owner's screenshot, Nov 7: every pick said "Can't open that picture." A bounds-only decode always returns null
        // (it fills [bounds] instead), so the null check fired every time. Only a missing stream means it can't open.
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); Unit } ?: error("Can't open that picture.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file isn't a picture Studio can read." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("That file isn't a picture Studio can read.")
        val name = "bg-" + java.util.UUID.randomUUID().toString() + ".jpg"
        File(dir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache[name] = bitmap.asImageBitmap()
        StudioLog.add("Background picture added (${bitmap.width}×${bitmap.height})")
        return name
    }

    private fun importGif(context: Context, uri: Uri): String {
        val name = "bg-" + java.util.UUID.randomUUID().toString() + ".gif"
        val file = File(dir(context), name)
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                total += n
                if (total > MAX_GIF_BYTES) { out.close(); file.delete(); error("That GIF is too big (over 25 MB). Try a shorter or smaller one.") }
                out.write(buffer, 0, n)
            }
        } } ?: error("Can't open that picture.")
        val first = BitmapFactory.decodeFile(file.path)
        if (first == null) { file.delete(); error("That file isn't a picture Studio can read.") }
        StudioLog.add("Background GIF added (${first.width}×${first.height}, ${file.length() / 1024} KB)")
        return name
    }

    fun file(context: Context, name: String): File? = if (validName(name)) File(dir(context), name).takeIf { it.isFile } else null

    fun load(context: Context, name: String): ImageBitmap? {
        if (!validName(name)) return null
        cache[name]?.let { return it }
        val file = File(dir(context), name)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { cache[name] = it }
    }

    /** A small copy's pixels, for [StudioPictureColors]. */
    fun samplePixels(context: Context, name: String): IntArray? {
        if (!validName(name)) return null
        val file = File(dir(context), name)
        val full = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 16 }) ?: return null
        val small = Bitmap.createScaledBitmap(full, 48, 48, true)
        return IntArray(48 * 48).also { small.getPixels(it, 0, 48, 0, 0, 48, 48) }
    }

    /** Removes stored pictures no layout uses any more. */
    fun prune(context: Context, keep: Set<String>) {
        dir(context).listFiles()?.filter { it.name !in keep }?.forEach { cache.remove(it.name); it.delete() }
    }
}

/**
 * Owner note 17: "Match picture" builds a table palette from the background picture; "Shuffle" picks another mix from
 * the same picture. Pure (plain ARGB ints in, "#RRGGBB" roles out) so it can be tested off the phone. Text always stays
 * light and readable over the dark table; the table itself stays dark.
 */
internal object StudioPictureColors {
    private class Swatch(val r: Float, val g: Float, val b: Float, val count: Int) {
        val maxC get() = max(r, max(g, b)); val minC get() = min(r, min(g, b))
        val sat get() = if (maxC <= 0f) 0f else (maxC - minC) / maxC
        val hue: Float get() {
            val d = maxC - minC; if (d <= 0f) return 0f
            val h = when (maxC) { r -> ((g - b) / d).mod(6f); g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
            return h * 60f
        }
    }

    private fun hex(r: Float, g: Float, b: Float) = String.format(Locale.US, "#%02X%02X%02X",
        (r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt())
    private fun mix(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>, t: Float) =
        Triple(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, a.third + (b.third - a.third) * t)
    private fun Triple<Float, Float, Float>.hex() = hex(first, second, third)
    private fun Swatch.rgb() = Triple(r, g, b)
    /** Brightens a colour (keeping its hue) until its strongest channel reaches [atLeast]. */
    private fun bright(c: Triple<Float, Float, Float>, atLeast: Float): Triple<Float, Float, Float> {
        val m = max(c.first, max(c.second, c.third)); if (m >= atLeast) return c
        if (m <= 0.01f) return Triple(atLeast, atLeast, atLeast)
        val k = atLeast / m; return Triple(c.first * k, c.second * k, c.third * k)
    }

    fun theme(pixels: IntArray, shuffle: Int = 0): Map<String, String> {
        require(pixels.isNotEmpty()) { "Empty picture." }
        val buckets = HashMap<Int, IntArray>()
        for (p in pixels) {
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val acc = buckets.getOrPut(((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)) { IntArray(4) }
            acc[0] += r; acc[1] += g; acc[2] += b; acc[3]++
        }
        val swatches = buckets.values.map { Swatch(it[0] / (255f * it[3]), it[1] / (255f * it[3]), it[2] / (255f * it[3]), it[3]) }
            .sortedByDescending { it.count }
        val turn = abs(shuffle)
        val base = swatches[turn % min(3, swatches.size)].rgb()
        // The table stays dark: the picture's main colour, darkened, keeping a hint of its hue.
        val background = mix(base, Triple(0f, 0f, 0f), 0.82f)
        val vivid = swatches.filter { it.sat > 0.3f && it.maxC > 0.3f }.sortedByDescending { it.count * it.sat }
        val accent = vivid.getOrNull(turn % max(1, vivid.size))?.rgb()?.let { bright(it, 0.75f) } ?: bright(mix(base, Triple(1f, 1f, 1f), 0.4f), 0.75f)
        val accentHue = vivid.getOrNull(turn % max(1, vivid.size))?.hue
        val goldSwatch = vivid.firstOrNull { s -> accentHue == null || abs(((s.hue - accentHue + 540f) % 360f) - 180f) > 40f }
        val gold = goldSwatch?.rgb()?.let { bright(it, 0.85f) } ?: Triple(1f, 0.82f, 0.4f)
        val text = Triple(0.95f, 0.92f, 0.87f)
        return mapOf(
            "background" to background.hex(),
            "panel" to mix(background, Triple(1f, 1f, 1f), 0.06f).hex(),
            "raised" to mix(background, Triple(1f, 1f, 1f), 0.12f).hex(),
            "accent" to accent.hex(),
            "text" to text.hex(),
            "muted" to mix(text, background, 0.45f).hex(),
            "gold" to gold.hex(),
        )
    }
}

/** Draws the background picture (and its dimming) behind the table, when one is set. */
@androidx.compose.runtime.Composable
internal fun StudioBackdropLayer() {
    val project = LocalStudioControls.current?.project() ?: return
    val name = project.backdrop ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    if (StudioBackdrop.isGif(name) && android.os.Build.VERSION.SDK_INT >= 28) StudioGifBackdrop(context, name, project.backdropFit == "Fit")
    else {
        // A GIF on Android 8 shows its first frame (moving pictures need Android 9).
        val image = androidx.compose.runtime.remember(name) { StudioBackdrop.load(context, name) } ?: return
        androidx.compose.foundation.Image(image, null, androidx.compose.ui.Modifier.fillMaxSize(),
            contentScale = if (project.backdropFit == "Fit") androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop)
    }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = project.backdropDim.coerceIn(0f, 0.9f))))
}

/** True while a background picture shows, so the table's own solid colour steps aside. */
@androidx.compose.runtime.Composable
internal fun studioBackdropShown() = LocalStudioControls.current?.project()?.backdrop != null

/** Plays a GIF background (Android 9+), scaled down to at most [StudioBackdrop.MAX_SIDE] a side to keep memory low. */
@androidx.annotation.RequiresApi(28)
@androidx.compose.runtime.Composable
private fun StudioGifBackdrop(context: Context, name: String, fit: Boolean) {
    val drawable = androidx.compose.runtime.remember(name) {
        StudioBackdrop.file(context, name)?.let { f -> runCatching {
            android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(f)) { decoder, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > StudioBackdrop.MAX_SIDE) {
                    val k = StudioBackdrop.MAX_SIDE.toFloat() / side
                    decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                }
            }
        }.getOrNull() }
    } ?: return
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { android.widget.ImageView(it) },
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        update = { view ->
            view.scaleType = if (fit) android.widget.ImageView.ScaleType.FIT_CENTER else android.widget.ImageView.ScaleType.CENTER_CROP
            if (view.drawable !== drawable) view.setImageDrawable(drawable)
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.let { if (!it.isRunning) it.start() }
        },
        onRelease = { view -> (view.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.stop(); view.setImageDrawable(null) },
    )
}
