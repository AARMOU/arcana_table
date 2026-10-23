package com.arcanatable

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured, searchable diagnostic log. Pure Kotlin (no Android types) so it can be unit tested
 * on the JVM. The UI owns presentation; MainActivity owns persistence through [serialize].
 *
 * Privacy model:
 *  - Every message passes through [DiagnosticRedactor] before it is stored, so credentials,
 *    tokens, server addresses and IPs never reach disk.
 *  - Entries flagged [DiagnosticEntry.private] (e.g. own hidden-card names) are visible in the
 *    on-device viewer but replaced with a placeholder in [exportText].
 */
enum class DiagnosticCategory(val label: String) {
    CONNECTION("Connection"),
    ENGINE("Engine"),
    ACTION("Player action"),
    UNSUPPORTED("Needs handling"),
    ERROR("Error"),
    APP("App");

    companion object {
        fun parse(value: String): DiagnosticCategory = entries.firstOrNull { it.name == value } ?: APP
    }
}

data class DiagnosticEntry(
    val sequence: Int,
    val timeMillis: Long,
    val category: DiagnosticCategory,
    val message: String,
    val private: Boolean = false,
) {
    fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timeMillis))

    /** Line shown in the in-app viewer (private details included; never leaves the device). */
    fun displayLine(): String = "#${sequence.toString().padStart(5, '0')}  ${timestamp()}  [${category.label}]  $message"
}

object DiagnosticRedactor {
    private val url = Regex("(?i)\\b(?:https?|wss?)://[^\\s\"'<>]+")
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val ipv4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b")
    private val ipv6 = Regex("(?i)\\b(?:[0-9a-f]{1,4}:){3,7}[0-9a-f]{1,4}\\b")
    // Bare host names such as game.example.com. Requires a letter-only TLD so "1.2" or "v0.15" survive.
    private val host = Regex("(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:com|net|org|io|dev|app|gg|co|uk|de|nl|eu|xyz|cloud|workers|local|lan|internal)\\b(?::\\d+)?")
    private val secretPair = Regex("(?i)\\b(password|passwd|token|authToken|secret|api[_-]?key|session|cookie|authorization)(\\s*[:=]\\s*|\"\\s*:\\s*\")([^\\s\",}]+)")
    private val roomCode = Regex("(?i)\\b((?:room|join|lobby)\\s*(?:code|id)\\s*[:=]?\\s*)([a-z0-9-]{4,})")
    private val uuid = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b")
    private val longOpaque = Regex("\\b[A-Za-z0-9_\\-]{32,}={0,2}\\b")

    fun redact(text: String): String = text
        .replace(url, "<server>")
        .replace(email, "<email>")
        .replace(secretPair) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
        .replace(roomCode) { "${it.groupValues[1]}<redacted>" }
        .replace(ipv6, "<ip>")
        .replace(ipv4, "<ip>")
        .replace(host, "<server>")
        .replace(uuid, "<id>")
        .replace(longOpaque, "<id>")
}

class DiagnosticLog(private val capacity: Int = 800, private val clock: () -> Long = System::currentTimeMillis) {
    private val items = ArrayDeque<DiagnosticEntry>()
    private var nextSequence = 1

    val entries: List<DiagnosticEntry> get() = items.toList()

    fun record(category: DiagnosticCategory, message: String, private: Boolean = false): DiagnosticEntry {
        val entry = DiagnosticEntry(nextSequence++, clock(), category, DiagnosticRedactor.redact(message.trim()).take(600), private)
        items.addLast(entry)
        while (items.size > capacity) items.removeFirst()
        return entry
    }

    /** Case-insensitive search over message and category label, optionally restricted to categories. */
    fun search(query: String, categories: Set<DiagnosticCategory> = emptySet()): List<DiagnosticEntry> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        return items.filter { entry ->
            (categories.isEmpty() || entry.category in categories) &&
                terms.all { term -> entry.message.lowercase().contains(term) || entry.category.label.lowercase().contains(term) }
        }
    }

    fun clear() { items.clear() }

    /** Text for files leaving the device: redacted again (defence in depth) and private entries masked. */
    fun exportText(entriesToExport: List<DiagnosticEntry> = entries): String = entriesToExport.joinToString("\n") { entry ->
        val body = if (entry.private) "[private game detail omitted]" else DiagnosticRedactor.redact(entry.message)
        "#${entry.sequence.toString().padStart(5, '0')}  ${entry.timestamp()}  [${entry.category.label}]  $body"
    }

    /** Compact persistence format: one tab-separated entry per line. Messages never contain tabs/newlines. */
    fun serialize(): String = items.joinToString("\n") { e ->
        listOf(e.sequence, e.timeMillis, e.category.name, if (e.private) 1 else 0, e.message.replace('\t', ' ').replace('\n', ' ')).joinToString("\t")
    }

    fun restore(serialized: String) {
        items.clear()
        serialized.lineSequence().filter(String::isNotBlank).forEach { line ->
            val parts = line.split('\t', limit = 5)
            val entry = if (parts.size == 5 && parts[0].toIntOrNull() != null) {
                DiagnosticEntry(parts[0].toInt(), parts[1].toLongOrNull() ?: 0L, DiagnosticCategory.parse(parts[2]), DiagnosticRedactor.redact(parts[4]), parts[3] == "1")
            } else {
                // Legacy "#00012  2026-10-16 12:00:00.000  message" lines from earlier builds.
                DiagnosticEntry(nextSequence, 0L, DiagnosticCategory.APP, DiagnosticRedactor.redact(line.replace(Regex("^#\\d+\\s+\\S+\\s+\\S+\\s+"), "")))
            }
            items.addLast(entry)
            nextSequence = maxOf(nextSequence, entry.sequence + 1)
        }
        while (items.size > capacity) items.removeFirst()
    }

    companion object {
        /** Best-effort category for legacy free-text events recorded through recordDiagnostic(String). */
        fun categorize(message: String): DiagnosticCategory {
            val m = message.lowercase()
            return when {
                "needs handling" in m || "unsupported" in m || "cannot present" in m -> DiagnosticCategory.UNSUPPORTED
                "error" in m || "failed" in m || "could not" in m || "crash" in m || "rejected" in m -> DiagnosticCategory.ERROR
                "connection" in m || "connect" in m || "socket" in m || "reconnect" in m || "network" in m -> DiagnosticCategory.CONNECTION
                "argentum" in m && ("state" in m || "decision" in m || "game" in m || "lobby" in m) -> DiagnosticCategory.ENGINE
                "sent" in m || "submitted" in m || "tapped" in m || "chose" in m || "played" in m -> DiagnosticCategory.ACTION
                else -> DiagnosticCategory.APP
            }
        }
    }
}
