package com.arcanatable

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

// Read-only, offline tutorial library. Engine-backed action coaching will be a separate
// integration: do not claim that clicking Next performs an Argentum action.
internal data class ArcanaLessonStep(
    val id: String,
    val screen: String,
    val target: String?,
    val event: String?,
    val title: String,
    val body: String,
    val hint: String,
    val actionKind: String
)
internal data class ArcanaLesson(
    val id: String,
    val title: String,
    val section: String,
    val description: String,
    val minutes: Int,
    val steps: List<ArcanaLessonStep>
)

internal fun loadTutorialLessons(context: Context): List<ArcanaLesson> = runCatching {
    val json = context.assets.open("tutorials/catalog.json").bufferedReader().use { it.readText() }
    val chapters = JSONObject(json).getJSONArray("chapters")
    (0 until chapters.length()).map { index ->
        val chapter = chapters.getJSONObject(index)
        val stepArray = chapter.getJSONArray("steps")
        ArcanaLesson(
            id = chapter.getString("id"),
            title = chapter.getString("title"),
            section = chapter.getString("section"),
            description = chapter.getString("description"),
            minutes = chapter.getInt("minutes"),
            steps = (0 until stepArray.length()).map { n ->
                val step = stepArray.getJSONObject(n)
                ArcanaLessonStep(
                    id = step.getString("id"),
                    screen = step.optString("screen"),
                    target = step.optString("target").takeUnless { it.isBlank() || it == "null" },
                    event = step.getJSONObject("advance").optString("event").takeUnless { it.isBlank() },
                    title = step.getString("title"),
                    body = step.getString("body"),
                    hint = step.optString("hint"),
                    actionKind = step.getJSONObject("advance").getString("kind")
                )
            }
        )
    }
}.getOrDefault(emptyList())

private val TutorialFelt = Color(0xFF101B18)
private val TutorialPanel = Color(0xFF1B2A25)
private val TutorialText = Color(0xFFF0EEE6)
private val TutorialMuted = Color(0xFFA5B6AD)
private val TutorialGold = Color(0xFFE6B65C)

@Composable
internal fun TutorialHubDialog(
    onDismiss: () -> Unit,
    onPractice: (String) -> Unit = {},
    onCoach: (String, Int) -> Unit = { _, _ -> },
    practiceEnabled: Boolean = false
) {
    val context = LocalContext.current
    val chapters = remember(context) { loadTutorialLessons(context) }
    val prefs = remember(context) { context.getSharedPreferences("arcana_tutorials", Context.MODE_PRIVATE) }
    val readIds = remember(prefs) {
        mutableStateListOf<String>().apply { addAll(prefs.getStringSet("read_chapters", emptySet()).orEmpty()) }
    }
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var chapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var stepIndex by rememberSaveable { mutableStateOf(0) }
    var showGlossary by rememberSaveable { mutableStateOf(false) }
    val current = chapters.firstOrNull { it.id == chapterId }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(18.dp), color = TutorialFelt
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ARCANA  /  LEARN", color = TutorialGold, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (current != null || showGlossary) { chapterId = null; showGlossary = false }
                        else onDismiss()
                    }) { Text(if (current != null || showGlossary) "BACK" else "CLOSE", color = TutorialText) }
                }

                if (showGlossary) {
                    Text("Magic glossary", color = TutorialText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val entries = remember(context) {
                        runCatching {
                            val raw = context.assets.open("tutorials/glossary.json").bufferedReader().use { it.readText() }
                            val obj = JSONObject(raw)
                            obj.keys().asSequence().map { it to obj.getString(it) }.sortedBy { it.first }.toList()
                        }.getOrDefault(emptyList())
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(entries) { entry ->
                            Column(Modifier.fillMaxWidth().background(TutorialPanel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text(entry.first, color = TutorialGold, fontWeight = FontWeight.Bold)
                                Text(entry.second, color = TutorialText, fontSize = 14.sp)
                            }
                        }
                    }
                } else if (current == null) {
                    Text("Learn to play", color = TutorialText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("20 short chapters, real-control walkthroughs, and engine-backed practice games. Your saved game is never overwritten.",
                        color = TutorialMuted, fontSize = 13.sp)

                    val intro = chapters.firstOrNull { it.id == "first-turn" }
                    if (intro != null) {
                        Column(
                            Modifier.fillMaxWidth().background(TutorialPanel, RoundedCornerShape(14.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("START HERE", color = TutorialGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Your first turn", color = TutorialText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Play a real, isolated Argentum practice turn. You can also read every step without leaving your game.",
                                color = TutorialMuted, fontSize = 12.sp)
                            Button(
                                onClick = { if (practiceEnabled) onPractice(intro.id) else { chapterId = intro.id; stepIndex = 0 } },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text(if (practiceEnabled) "PLAY THE FIRST TURN" else "READ FIRST TURN") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(readIds.size.toString() + " / " + chapters.size.toString() + " read",
                            color = TutorialMuted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { showGlossary = true }) { Text("GLOSSARY", color = TutorialGold) }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Find a topic") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("All", "Learn Magic", "App Controls").forEach { candidate ->
                            TextButton(
                                onClick = { filter = candidate },
                                modifier = Modifier.background(
                                    if (filter == candidate) TutorialPanel else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                            ) {
                                Text(candidate, color = if (filter == candidate) TutorialGold else TutorialMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    val visible = chapters.filter {
                        (filter == "All" || it.section == filter || (filter == "All" && it.section == "Quick Start")) &&
                            (query.isBlank() || it.title.contains(query, true) || it.description.contains(query, true))
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)) {
                        items(visible, key = { it.id }) { lesson ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(TutorialPanel, RoundedCornerShape(12.dp))
                                    .clickable { chapterId = lesson.id; stepIndex = 0 }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(lesson.title, color = TutorialText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(lesson.description, color = TutorialMuted, fontSize = 12.sp)
                                    Text(lesson.section + " · " + lesson.minutes + " min",
                                        color = TutorialGold, fontSize = 11.sp)
                                }
                                Text(if (lesson.id in readIds) "READ" else "›",
                                    color = TutorialGold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                } else {
                    Text(current.section.uppercase() + "  /  " + current.minutes + " MIN",
                        color = TutorialGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(current.title, color = TutorialText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("STEP " + (stepIndex + 1).toString() + " OF " + current.steps.size.toString(),
                            color = TutorialMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onCoach(current.id, stepIndex) }) {
                            Text("SHOW ON SCREEN", color = TutorialGold, fontSize = 11.sp)
                        }
                        if (practiceEnabled && current.id in TutorialPracticeScenarios.supported) {
                            TextButton(onClick = { onPractice(current.id) }) {
                                Text("PRACTICE", color = TutorialGold, fontSize = 11.sp)
                            }
                        }
                    }
                    val step = current.steps.getOrNull(stepIndex)
                    if (step != null) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth()
                                .background(TutorialPanel, RoundedCornerShape(14.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(step.title, color = TutorialText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(step.body, color = TutorialText, fontSize = 16.sp, lineHeight = 23.sp)
                            if (step.hint.isNotBlank()) {
                                Text("TRY: " + step.hint, color = TutorialGold, fontSize = 12.sp)
                            }
                            if (step.actionKind != "next") {
                                Text("Use SHOW ON SCREEN to highlight the control in Arcana, or PRACTICE for supported engine-backed drills.",
                                    color = TutorialMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                            enabled = stepIndex > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("PREVIOUS") }
                        Button(
                            onClick = {
                                if (stepIndex < current.steps.lastIndex) stepIndex++
                                else {
                                    if (current.id !in readIds) readIds.add(current.id)
                                    prefs.edit().putStringSet("read_chapters", readIds.toSet()).apply()
                                    chapterId = null
                                    stepIndex = 0
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text(if (stepIndex == current.steps.lastIndex) "FINISH READING" else "NEXT") }
                    }
                }
            }
        }
    }
}
