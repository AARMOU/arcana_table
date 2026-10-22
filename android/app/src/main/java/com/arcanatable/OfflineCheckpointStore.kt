package com.arcanatable

import android.content.Context
import android.util.AtomicFile
import com.arcanatable.engine.LocalSeat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A private, atomic full-engine checkpoint. Never persist a masked ClientGameState as
 * a resumable game: it has no hidden cards, priority history, or suspended continuations.
 * Save/restore is restricted to the local AI host; network rooms retain server authority.
 */
internal object OfflineCheckpointStore {
    private const val FILE_NAME = "argentum-offline-checkpoint-v1.json"
    const val FORMAT = 1
    private fun file(context: Context): AtomicFile =
        AtomicFile(File(context.filesDir, FILE_NAME))

    fun available(context: Context): Boolean = file(context).baseFile.isFile

    fun save(context: Context, data: String): Boolean {
        val payload = JSONObject(data)
        require(payload.optInt("schema") == FORMAT && payload.optString("mode") == "local-ai") {
            "Unsupported offline checkpoint format"
        }
        require(payload.has("gameState") && (payload.optJSONArray("seats")?.length() ?: 0) in 2..4) {
            "Incomplete offline game checkpoint"
        }
        val atomic = file(context)
        val out = atomic.startWrite()
        return try {
            out.write(data.toByteArray(Charsets.UTF_8))
            out.fd.sync()
            atomic.finishWrite(out)
            true
        } catch (error: Throwable) {
            atomic.failWrite(out)
            throw error
        }
    }

    fun load(context: Context): JSONObject? {
        val atomic = file(context)
        if (!atomic.baseFile.isFile) return null
        val data = atomic.openRead().use { it.reader(Charsets.UTF_8).readText() }
        return JSONObject(data).also {
            require(it.optInt("schema") == FORMAT && it.optString("mode") == "local-ai")
            require(it.optJSONObject("gameState") != null)
            require((it.optJSONArray("seats")?.length() ?: 0) in 2..4)
        }
    }

    fun clear(context: Context) { file(context).delete() }

    fun seatToJson(seat: LocalSeat): JSONObject = JSONObject()
        .put("name", seat.name)
        .put("deck", JSONArray(seat.deck))
        .put("commander", seat.commander ?: JSONObject.NULL)

    fun seatFromJson(json: JSONObject): LocalSeat {
        val deck = json.getJSONArray("deck")
        return LocalSeat(json.getString("name"),
            (0 until deck.length()).map { deck.getString(it) },
            json.optString("commander").takeUnless { it.isBlank() || it == "null" })
    }

    fun savedSeats(record: JSONObject): List<LocalSeat> {
        val array = record.getJSONArray("seats")
        require(array.length() in 2..4)
        return (0 until array.length()).map { seatFromJson(array.getJSONObject(it)) }
    }
}
