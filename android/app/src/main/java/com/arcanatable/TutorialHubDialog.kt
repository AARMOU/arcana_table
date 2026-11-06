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
import androidx.compose.ui.platform.testTag
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
    parseTutorialLessons(context.assets.open("tutorials/catalog.json").bufferedReader().use { it.readText() })
}.getOrDefault(emptyList())

/** The bundled catalog as lessons; shared with the tests that walk each lesson. */
internal fun parseTutorialLessons(json: String): List<ArcanaLesson> = run {
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
}

private val TutorialFelt = Color(0xFF101B18)
private val TutorialPanel = Color(0xFF1B2A25)
private val TutorialText = Color(0xFFF0EEE6)
private val TutorialMuted = Color(0xFFA5B6AD)
private val TutorialGold = Color(0xFFE6B65C)

@Composable
internal fun TutorialHubDialog(
    onDismiss: () -> Unit,
    /** Run the lesson in the app from this step (a practice board is set up when it needs one). */
    onStart: (String, Int) -> Unit = { _, _ -> },
    /** A game is in progress: table lessons use it instead of a practice board. */
    gameRunning: Boolean = false,
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
    var reading by rememberSaveable { mutableStateOf(false) }
    var showGlossary by rememberSaveable { mutableStateOf(false) }
    val current = chapters.firstOrNull { it.id == chapterId }

    StudioDialog("Tutorial Hub Dialog 1", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(18.dp), color = TutorialFelt
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("TutorialHubDialog.Text.1", "Dialogs and overlays", false, "ARCANA  /  LEARN", color = TutorialGold, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f))
                    StudioTextButton("TutorialHubDialog.TextButton.1", "Dialogs and overlays", onClick = {
                        if (reading) reading = false
                        else if (current != null || showGlossary) { chapterId = null; showGlossary = false }
                        else onDismiss()
                    }) { StudioText("TutorialHubDialog.Text.2", "Dialogs and overlays", true, if (current != null || showGlossary) "BACK" else "CLOSE", color = TutorialText) }
                }

                if (showGlossary) {
                    StudioText("TutorialHubDialog.Text.3", "Dialogs and overlays", false, "Magic glossary", color = TutorialText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                                StudioText("TutorialHubDialog.Text.4", "Dialogs and overlays", true, entry.first, color = TutorialGold, fontWeight = FontWeight.Bold)
                                StudioText("TutorialHubDialog.Text.5", "Dialogs and overlays", true, entry.second, color = TutorialText, fontSize = 14.sp)
                            }
                        }
                    }
                } else if (current == null) {
                    StudioText("TutorialHubDialog.Text.6", "Dialogs and overlays", false, "Learn to play", color = TutorialText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    StudioText("TutorialHubDialog.Text.7", "Dialogs and overlays", false, "20 short chapters, real-control walkthroughs, and engine-backed practice games. Your saved game is never overwritten.",
                        color = TutorialMuted, fontSize = 13.sp)

                    val intro = chapters.firstOrNull { it.id == "first-turn" }
                    if (intro != null) {
                        Column(
                            Modifier.fillMaxWidth().background(TutorialPanel, RoundedCornerShape(14.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            StudioText("TutorialHubDialog.Text.8", "Dialogs and overlays", false, "START HERE", color = TutorialGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            StudioText("TutorialHubDialog.Text.9", "Dialogs and overlays", false, "Your first turn", color = TutorialText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            StudioText("TutorialHubDialog.Text.10", "Dialogs and overlays", true, if (gameRunning) "A game is in progress, so this follows along on it. Leave the game to play the practice turn instead."
                                else "Play a real practice turn against a passive opponent. Your saved game is never touched.",
                                color = TutorialMuted, fontSize = 12.sp)
                            StudioButton("TutorialHubDialog.Button.1", "Dialogs and overlays",
                                onClick = { onStart(intro.id, 0) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("tutorial-start-first-turn")
                            ) { StudioText("TutorialHubDialog.Text.11", "Dialogs and overlays", true, if (gameRunning) "FOLLOW ON MY GAME" else "PLAY THE FIRST TURN") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudioText("TutorialHubDialog.Text.12", "Dialogs and overlays", true, readIds.size.toString() + " / " + chapters.size.toString() + " read",
                            color = TutorialMuted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                        StudioTextButton("TutorialHubDialog.TextButton.2", "Dialogs and overlays", onClick = { showGlossary = true }) { StudioText("TutorialHubDialog.Text.13", "Dialogs and overlays", false, "GLOSSARY", color = TutorialGold) }
                    }
                    StudioOutlinedTextField("TutorialHubDialog.OutlinedTextField.1", "Dialogs and overlays",
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), label = { StudioText("TutorialHubDialog.Text.14", "Dialogs and overlays", false, "Find a topic") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("All", "Learn Magic", "App Controls").forEach { candidate ->
                            StudioTextButton("TutorialHubDialog.TextButton.3", "Dialogs and overlays",
                                onClick = { filter = candidate },
                                modifier = Modifier.background(
                                    if (filter == candidate) TutorialPanel else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                            ) {
                                StudioText("TutorialHubDialog.Text.15", "Dialogs and overlays", true, candidate, color = if (filter == candidate) TutorialGold else TutorialMuted, fontSize = 12.sp)
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
                                    .clickable { chapterId = lesson.id; stepIndex = 0; reading = false }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    StudioText("TutorialHubDialog.Text.16", "Dialogs and overlays", true, lesson.title, color = TutorialText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    StudioText("TutorialHubDialog.Text.17", "Dialogs and overlays", true, lesson.description, color = TutorialMuted, fontSize = 12.sp)
                                    StudioText("TutorialHubDialog.Text.18", "Dialogs and overlays", true, lesson.section + " · " + lesson.minutes + " min",
                                        color = TutorialGold, fontSize = 11.sp)
                                }
                                StudioText("TutorialHubDialog.Text.19", "Dialogs and overlays", true, if (lesson.id in readIds) "READ" else "›",
                                    color = TutorialGold, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                } else if (!reading) {
                    // Chapter overview: one clear way in, plus a text-only option.
                    val drill = current.id in TutorialPracticeScenarios.supported
                    val table = current.steps.any { it.screen in setOf("board", "stack", "menu", "mulligan") }
                    StudioText("TutorialHubDialog.Text.20", "Dialogs and overlays", true, current.section.uppercase() + "  /  " + current.minutes + " MIN  /  " + current.steps.size + " STEPS",
                        color = TutorialGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    StudioText("TutorialHubDialog.Text.21", "Dialogs and overlays", true, current.title, color = TutorialText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    StudioText("TutorialHubDialog.Text.22", "Dialogs and overlays", true, current.description, color = TutorialText, fontSize = 15.sp, lineHeight = 21.sp)
                    StudioText("TutorialHubDialog.Text.23", "Dialogs and overlays", true, when {
                        drill && !gameRunning -> "You'll play it in a practice game. Each step waits for you to do it on the real table. Your saved game is never touched."
                        table && !gameRunning -> "Opens a practice Commander board so every control is there to see. Your saved game is never touched."
                        table -> "Runs on your current game. Nothing is played for you."
                        else -> "Highlights each control on the real screen."
                    }, color = TutorialMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(current.steps.size) { n ->
                            StudioText("TutorialHubDialog.Text.24", "Dialogs and overlays", true, "${n + 1}.  " + current.steps[n].title, color = TutorialMuted, fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth().clickable { stepIndex = n; reading = true }.padding(vertical = 5.dp))
                        }
                    }
                    StudioButton("TutorialHubDialog.Button.2", "Dialogs and overlays", onClick = { onStart(current.id, 0) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("tutorial-start")) {
                        StudioText("TutorialHubDialog.Text.25", "Dialogs and overlays", false, "START LESSON", fontWeight = FontWeight.Black)
                    }
                    StudioOutlinedButton("TutorialHubDialog.OutlinedButton.1", "Dialogs and overlays", onClick = { stepIndex = 0; reading = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        StudioText("TutorialHubDialog.Text.26", "Dialogs and overlays", false, "READ IT HERE INSTEAD")
                    }
                } else {
                    StudioText("TutorialHubDialog.Text.27", "Dialogs and overlays", true, current.section.uppercase() + "  /  " + current.minutes + " MIN",
                        color = TutorialGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    StudioText("TutorialHubDialog.Text.28", "Dialogs and overlays", true, current.title, color = TutorialText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudioText("TutorialHubDialog.Text.29", "Dialogs and overlays", false, "STEP " + (stepIndex + 1).toString() + " OF " + current.steps.size.toString(),
                            color = TutorialMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        StudioTextButton("TutorialHubDialog.TextButton.4", "Dialogs and overlays", onClick = { onStart(current.id, if (current.id in TutorialPracticeScenarios.supported) 0 else stepIndex) }) {
                            StudioText("TutorialHubDialog.Text.30", "Dialogs and overlays", false, "SHOW ON SCREEN", color = TutorialGold, fontSize = 11.sp)
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
                            StudioText("TutorialHubDialog.Text.31", "Dialogs and overlays", true, step.title, color = TutorialText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            StudioText("TutorialHubDialog.Text.32", "Dialogs and overlays", true, step.body, color = TutorialText, fontSize = 16.sp, lineHeight = 23.sp)
                            if (step.hint.isNotBlank()) {
                                StudioText("TutorialHubDialog.Text.33", "Dialogs and overlays", false, "TRY: " + step.hint, color = TutorialGold, fontSize = 12.sp)
                            }
                            if (step.actionKind != "next") {
                                StudioText("TutorialHubDialog.Text.34", "Dialogs and overlays", false, "SHOW ON SCREEN runs this step in the app so you can do it yourself.",
                                    color = TutorialMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioOutlinedButton("TutorialHubDialog.OutlinedButton.2", "Dialogs and overlays",
                            onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                            enabled = stepIndex > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { StudioText("TutorialHubDialog.Text.35", "Dialogs and overlays", false, "PREVIOUS") }
                        StudioButton("TutorialHubDialog.Button.3", "Dialogs and overlays",
                            onClick = {
                                if (stepIndex < current.steps.lastIndex) stepIndex++
                                else {
                                    if (current.id !in readIds) readIds.add(current.id)
                                    prefs.edit().putStringSet("read_chapters", readIds.toSet()).apply()
                                    chapterId = null
                                    reading = false
                                    stepIndex = 0
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { StudioText("TutorialHubDialog.Text.36", "Dialogs and overlays", true, if (stepIndex == current.steps.lastIndex) "FINISH READING" else "NEXT") }
                    }
                }
            }
        }
    }
}
