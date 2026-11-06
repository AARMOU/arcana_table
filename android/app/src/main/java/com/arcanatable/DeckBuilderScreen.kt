@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arcanatable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developer-preview deck builder (BuildConfig.DEBUG only until the owner enables it).
 * Reads and writes the existing saved-deck list; never touches the game's imported table deck.
 */
@Composable
internal fun DeckManagerDialog(
    decks: List<SavedDeck>,
    loadCatalog: suspend () -> SupportedCardCatalog?,
    onDecksChanged: (List<SavedDeck>) -> Unit,
    onPlay: (SavedDeck) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val introPrefs = remember(context) { context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE) }
    var showBuilderIntro by remember { mutableStateOf(!introPrefs.getBoolean("deck_builder_intro_seen_v1", false)) }
    val dismissBuilderIntro = {
        introPrefs.edit().putBoolean("deck_builder_intro_seen_v1", true).apply()
        showBuilderIntro = false
    }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }   // saved name, "" for a new deck
    var importing by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SavedDeck?>(null) }
    var tagging by remember { mutableStateOf<SavedDeck?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var libQuery by rememberSaveable { mutableStateOf("") }
    var libSort by rememberSaveable { mutableStateOf(DeckLibrarySort.RECENT) }
    var libTag by rememberSaveable { mutableStateOf<String?>(null) }
    var recentlyDeleted by remember { mutableStateOf<Pair<SavedDeck, Int>?>(null) }
    // Tiles fill in color, legality and art once the offline card library has loaded.
    var summaryCatalog by remember { mutableStateOf<SupportedCardCatalog?>(null) }
    LaunchedEffect(Unit) { summaryCatalog = runCatching { loadCatalog() }.getOrNull() }
    val summaries by produceState(emptyMap<String, DeckSummary>(), decks, summaryCatalog) {
        value = withContext(Dispatchers.Default) { decks.associate { it.name to summarizeDeck(it, summaryCatalog) } }
    }
    LaunchedEffect(recentlyDeleted) { if (recentlyDeleted != null) { delay(8_000); recentlyDeleted = null } }

    val editTarget = editing
    if (editTarget != null) {
        val source = decks.firstOrNull { it.name == editTarget }
        StudioScreen("Deck editor") { DeckEditorScreen(
            initialName = source?.name ?: SavedDeckOps.uniqueName("New deck", decks),
            initialText = source?.text ?: pendingImport ?: "",
            savedName = source?.name,
            allDecks = decks,
            loadCatalog = loadCatalog,
            onSave = { original, deck ->
                val previous = decks.firstOrNull { it.name == original }
                onDecksChanged(SavedDeckOps.upsert(decks, original, deck.copy(tags = previous?.tags.orEmpty(), updatedAt = System.currentTimeMillis())))
                editing = deck.name
                pendingImport = null
            },
            onClose = { editing = null; pendingImport = null },
        ) }
        return
    }

    val tags = remember(decks) { SavedDeckOps.allTags(decks) }
    // A tag that no deck carries any more (after untagging or deleting) stops filtering.
    val activeTag = libTag?.takeIf { t -> tags.any { it.equals(t, true) } }
    val shown = remember(decks, libQuery, activeTag, libSort, summaries) { SavedDeckOps.visible(decks, libQuery, activeTag, libSort, summaries) }
    val now = remember(decks) { System.currentTimeMillis() }

    StudioDialog("Deck Builder Screen 1", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioIconButton("DeckBuilderScreen.IconButton.1", "Decks", onClick = onDismiss, modifier = Modifier.size(48.dp)) { StudioIcon("DeckBuilderScreen.Icon.1", "Decks", Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftWhite) }
                    Column(Modifier.weight(1f)) {
                        StudioText("DeckBuilderScreen.Text.1", "Decks", false, "DECK BUILDER", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.5.sp)
                        StudioText("DeckBuilderScreen.Text.2", "Decks", true, "Developer preview · ${decks.size} saved deck${if (decks.size == 1) "" else "s"}", color = Muted, fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioButton("DeckBuilderScreen.Button.1", "Decks", onClick = { pendingImport = null; editing = "" }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("deck-new"),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { StudioText("DeckBuilderScreen.Text.3", "Decks", false, "NEW DECK", fontWeight = FontWeight.Bold) }
                    StudioOutlinedButton("DeckBuilderScreen.OutlinedButton.1", "Decks", onClick = { importText = ""; importing = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { StudioText("DeckBuilderScreen.Text.4", "Decks", false, "PASTE A LIST", color = SoftWhite, fontWeight = FontWeight.Bold) }
                }
                if (decks.isNotEmpty()) {
                    StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.1", "Decks", libQuery, { libQuery = it }, Modifier.fillMaxWidth().testTag("deck-library-search"), singleLine = true,
                        placeholder = { StudioText("DeckBuilderScreen.Text.5", "Decks", false, "Search decks, commanders or cards", color = Muted) },
                        trailingIcon = { if (libQuery.isNotEmpty()) StudioIconButton("DeckBuilderScreen.IconButton.2", "Decks", onClick = { libQuery = "" }) { StudioIcon("DeckBuilderScreen.Icon.2", "Decks", Icons.Filled.Close, "Clear deck search", tint = Muted) } })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        var sortMenu by remember { mutableStateOf(false) }
                        Box {
                            StudioFilterChip("DeckBuilderScreen.FilterChip.1", "Decks", true, { sortMenu = true }, label = { StudioText("DeckBuilderScreen.Text.6", "Decks", true, "${libSort.label} ▾") }, modifier = Modifier.testTag("deck-library-sort"))
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                DeckLibrarySort.entries.forEach { s -> StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.1", "Decks", text = { StudioText("DeckBuilderScreen.Text.7", "Decks", true, s.label + if (s == libSort) "  ✓" else "") }, onClick = { libSort = s; sortMenu = false }) }
                            }
                        }
                        if (tags.isNotEmpty()) {
                            StudioFilterChip("DeckBuilderScreen.FilterChip.2", "Decks", activeTag == null, { libTag = null }, label = { StudioText("DeckBuilderScreen.Text.8", "Decks", false, "All") })
                            tags.forEach { t -> StudioFilterChip("DeckBuilderScreen.FilterChip.3", "Decks", activeTag.equals(t, true), { libTag = if (activeTag.equals(t, true)) null else t }, label = { StudioText("DeckBuilderScreen.Text.9", "Decks", true, t) }) }
                        }
                    }
                }
                when {
                    decks.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StudioText("DeckBuilderScreen.Text.10", "Decks", false, "No saved decks yet.\nStart a new deck or paste a list from ManaBox, Moxfield, Archidekt or Arena.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    shown.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StudioText("DeckBuilderScreen.Text.11", "Decks", false, "No decks match.", color = Muted, fontSize = 13.sp)
                    }
                    else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shown, key = { it.name }) { deck ->
                            DeckTile(deck, summaries[deck.name], now, onOpen = { editing = deck.name }) {
                                Box {
                                    StudioIconButton("DeckBuilderScreen.IconButton.3", "Decks", onClick = { menuFor = deck.name }, modifier = Modifier.size(48.dp)) { StudioIcon("DeckBuilderScreen.Icon.3", "Decks", Icons.Filled.MoreVert, "More for ${deck.name}", tint = Muted) }
                                    DropdownMenu(expanded = menuFor == deck.name, onDismissRequest = { menuFor = null }) {
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.2", "Decks", text = { StudioText("DeckBuilderScreen.Text.12", "Decks", false, "Play this deck") }, onClick = { menuFor = null; onPlay(deck) })
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.3", "Decks", text = { StudioText("DeckBuilderScreen.Text.13", "Decks", false, "Rename") }, onClick = { menuFor = null; renaming = deck })
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.4", "Decks", text = { StudioText("DeckBuilderScreen.Text.14", "Decks", false, "Tags…") }, onClick = { menuFor = null; tagging = deck })
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.5", "Decks", text = { StudioText("DeckBuilderScreen.Text.15", "Decks", false, "Duplicate") }, onClick = { menuFor = null; onDecksChanged(SavedDeckOps.duplicate(decks, deck)) })
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.6", "Decks", text = { StudioText("DeckBuilderScreen.Text.16", "Decks", false, "Copy list") }, onClick = { menuFor = null; copyDeckText(context, deck.name, deck.text) })
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.7", "Decks", text = { StudioText("DeckBuilderScreen.Text.17", "Decks", false, "Share list") }, onClick = { menuFor = null; shareDeckText(context, deck.name, deck.text) })
                                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.8", "Decks", text = { StudioText("DeckBuilderScreen.Text.18", "Decks", false, "Delete", color = Color(0xFFE9978B)) }, onClick = {
                                            menuFor = null
                                            recentlyDeleted = deck to decks.indexOf(deck)
                                            onDecksChanged(SavedDeckOps.delete(decks, deck))
                                        })
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                recentlyDeleted?.let { (deck, index) ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(PanelLight).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        StudioText("DeckBuilderScreen.Text.19", "Decks", true, "Deleted ${deck.name}", color = SoftWhite, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        StudioTextButton("DeckBuilderScreen.TextButton.1", "Decks", onClick = { onDecksChanged(SavedDeckOps.restore(decks, deck, index)); recentlyDeleted = null },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("deck-undo-delete")) { StudioText("DeckBuilderScreen.Text.20", "Decks", false, "UNDO", color = Mint, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
    if (showBuilderIntro) StudioAlertDialog("Deck Builder Screen 2",
        onDismissRequest = dismissBuilderIntro,
        title = { StudioText("DeckBuilderScreen.Text.21", "Decks", false, "About the Deck Builder") },
        text = {
            StudioText("DeckBuilderScreen.Text.22", "Decks", false,
                "Arcana's card library is growing. Not every card, commander, or mechanic is available yet.\n\n" +
                    "A card appearing in the builder means it is in this app's library, not that every interaction has been tested. " +
                    "Unsupported cards are flagged rather than silently replaced.\n\n" +
                    "You can create, import, and save decks now. More cards and rules support will arrive in future updates."
            )
        },
        confirmButton = {
            StudioTextButton("DeckBuilderScreen.TextButton.2", "Decks", onClick = dismissBuilderIntro, modifier = Modifier.testTag("deck-builder-intro-continue")) {
                StudioText("DeckBuilderScreen.Text.23", "Decks", false, "GOT IT")
            }
        },
        containerColor = Panel,
        titleContentColor = SoftWhite,
        textContentColor = SoftWhite,
    )
    if (importing) StudioAlertDialog("Deck Builder Screen 3",
        onDismissRequest = { importing = false },
        title = { StudioText("DeckBuilderScreen.Text.24", "Decks", false, "Paste a deck list") },
        text = {
            Column {
                StudioText("DeckBuilderScreen.Text.25", "Decks", false, "Works offline. Cards are matched against this build's library; anything it can't play is flagged in the editor, never swapped for you.", fontSize = 12.sp)
                StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.2", "Decks", importText, { importText = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp).padding(top = 8.dp), placeholder = { StudioText("DeckBuilderScreen.Text.26", "Decks", false, "1 Sol Ring\n1 Command Tower\n…") })
            }
        },
        confirmButton = { StudioTextButton("DeckBuilderScreen.TextButton.3", "Decks", enabled = importText.isNotBlank(), onClick = { pendingImport = importText; importing = false; editing = "" }) { StudioText("DeckBuilderScreen.Text.27", "Decks", false, "OPEN IN EDITOR") } },
        dismissButton = { StudioTextButton("DeckBuilderScreen.TextButton.4", "Decks", onClick = { importing = false }) { StudioText("DeckBuilderScreen.Text.28", "Decks", false, "CANCEL") } },
    )
    renaming?.let { deck ->
        var value by remember(deck) { mutableStateOf(deck.name) }
        StudioAlertDialog("Deck Builder Screen 4", onDismissRequest = { renaming = null }, title = { StudioText("DeckBuilderScreen.Text.29", "Decks", false, "Rename deck") },
            text = { StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.3", "Decks", value, { value = it }, singleLine = true, label = { StudioText("DeckBuilderScreen.Text.30", "Decks", false, "Deck name") }) },
            confirmButton = { StudioTextButton("DeckBuilderScreen.TextButton.5", "Decks", enabled = value.isNotBlank(), onClick = { onDecksChanged(SavedDeckOps.rename(decks, deck, value)); renaming = null }) { StudioText("DeckBuilderScreen.Text.31", "Decks", false, "SAVE") } },
            dismissButton = { StudioTextButton("DeckBuilderScreen.TextButton.6", "Decks", onClick = { renaming = null }) { StudioText("DeckBuilderScreen.Text.32", "Decks", false, "CANCEL") } })
    }
    tagging?.let { deck ->
        var chosen by remember(deck) { mutableStateOf(deck.tags) }
        var newTag by remember(deck) { mutableStateOf("") }
        val options = (tags + chosen).distinctBy { it.lowercase() }
        fun addNew() { newTag.trim().takeIf { it.isNotEmpty() }?.let { t -> if (chosen.none { it.equals(t, true) }) chosen = chosen + t }; newTag = "" }
        StudioAlertDialog("Deck Builder Screen 5", onDismissRequest = { tagging = null }, title = { StudioText("DeckBuilderScreen.Text.33", "Decks", true, "Tags for ${deck.name}") },
            text = {
                Column {
                    if (options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { t ->
                            val on = chosen.any { it.equals(t, true) }
                            StudioFilterChip("DeckBuilderScreen.FilterChip.4", "Decks", on, { chosen = if (on) chosen.filterNot { it.equals(t, true) } else chosen + t }, label = { StudioText("DeckBuilderScreen.Text.34", "Decks", true, t) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.4", "Decks", newTag, { newTag = it.take(24) }, Modifier.weight(1f), singleLine = true, label = { StudioText("DeckBuilderScreen.Text.35", "Decks", false, "New tag") }, placeholder = { StudioText("DeckBuilderScreen.Text.36", "Decks", false, "Casual, Testing…") })
                        StudioTextButton("DeckBuilderScreen.TextButton.7", "Decks", onClick = ::addNew, enabled = newTag.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.37", "Decks", false, "ADD") }
                    }
                }
            },
            confirmButton = { StudioTextButton("DeckBuilderScreen.TextButton.8", "Decks", onClick = { addNew(); onDecksChanged(SavedDeckOps.setTags(decks, deck, chosen)); tagging = null }) { StudioText("DeckBuilderScreen.Text.38", "Decks", false, "SAVE") } },
            dismissButton = { StudioTextButton("DeckBuilderScreen.TextButton.9", "Decks", onClick = { tagging = null }) { StudioText("DeckBuilderScreen.Text.39", "Decks", false, "CANCEL") } })
    }
}

/** One saved deck: commander art, colors, size, whether it's ready to play, tags and last edit. */
@Composable
private fun DeckTile(deck: SavedDeck, summary: DeckSummary?, now: Long, onOpen: () -> Unit, menu: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).studioClickable("DeckBuilderScreen.clickable.1", "Decks", onClick = onOpen).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        .testTag("deck-tile-${deck.name}"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 50.dp, height = 70.dp).clip(RoundedCornerShape(6.dp)).background(PanelLight), contentAlignment = Alignment.Center) {
            val art = summary?.commanderImageUrl
            if (art != null) CardArt(art, Modifier.fillMaxSize(), ContentScale.Crop, fallbackName = summary?.commanders?.firstOrNull())
            else StudioText("DeckBuilderScreen.Text.40", "Decks", true, deck.name.take(1).uppercase(), color = Muted, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            StudioText("DeckBuilderScreen.Text.41", "Decks", true, deck.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val commanders = summary?.commanders ?: listOfNotNull(deck.commander)
            StudioText("DeckBuilderScreen.Text.42", "Decks", true, commanders.joinToString(" + ").ifBlank { "No commander yet" }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                summary?.identity?.let { id ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (id.isEmpty()) ColorDot('C') else "WUBRG".filter { it in id }.forEach { ColorDot(it) }
                    }
                }
                StudioText("DeckBuilderScreen.Text.43", "Decks", true, "${summary?.cards ?: "…"}/100", color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                when (val n = summary?.problems) {
                    null -> StudioText("DeckBuilderScreen.Text.44", "Decks", false, "checking…", color = Muted, fontSize = 11.sp)
                    0 -> StudioText("DeckBuilderScreen.Text.45", "Decks", false, "Ready", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    else -> StudioText("DeckBuilderScreen.Text.46", "Decks", true, "$n to fix", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            val meta = listOfNotNull(deck.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "#$it" }, editedAgo(deck.updatedAt, now))
            if (meta.isNotEmpty()) StudioText("DeckBuilderScreen.Text.47", "Decks", true, meta.joinToString("  ·  "), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        StudioTextButton("DeckBuilderScreen.TextButton.10", "Decks", onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.48", "Decks", false, "EDIT", color = Mint, fontWeight = FontWeight.Bold) }
        menu()
    }
}

@Composable
private fun ColorDot(c: Char) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(pipColor(c)).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape))
}

private enum class EditorTab { LIBRARY, DECK, STATS }

@Composable
private fun DeckEditorScreen(
    initialName: String,
    initialText: String,
    savedName: String?,
    allDecks: List<SavedDeck>,
    loadCatalog: suspend () -> SupportedCardCatalog?,
    onSave: (originalName: String?, SavedDeck) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<SupportedCardCatalog?>(null) }
    var library by remember { mutableStateOf<CardLibrary?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val loaded = loadCatalog()
        if (loaded == null) loadFailed = true
        else { catalog = loaded; library = withContext(Dispatchers.Default) { CardLibrary(loaded) } }
    }
    // Saveable text survives configuration changes; the session (undo) is rebuilt from it.
    var savedText by rememberSaveable { mutableStateOf(initialText) }
    var deckName by rememberSaveable { mutableStateOf(initialName) }
    var persistedName by rememberSaveable { mutableStateOf(savedName) }
    var tab by rememberSaveable { mutableStateOf(EditorTab.LIBRARY) }
    var query by rememberSaveable { mutableStateOf("") }
    var previewName by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf<String?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val lib = library
    val cat = catalog
    if (lib == null || cat == null) {
        StudioDialog("Deck Builder Screen 6", onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Ink) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loadFailed) StudioText("DeckBuilderScreen.Text.49", "Decks", false, "This build's card library couldn't be loaded.", color = CounterGold)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Mint); StudioText("DeckBuilderScreen.Text.50", "Decks", false, "Loading card library…", color = Muted, modifier = Modifier.padding(top = 10.dp)) }
                }
            }
        }
        return
    }
    val session = remember(cat) { DeckEditSession(DeckDraft.fromText(deckName, savedText, cat)) }
    var draft by remember(session) { mutableStateOf(session.draft) }
    var historyTick by remember { mutableIntStateOf(0) }
    fun edit(change: (DeckDraft) -> DeckDraft) { draft = session.apply(change); historyTick++ }

    // Autosave: a short pause after the last change writes the deck. An untouched new deck is never saved.
    LaunchedEffect(draft, deckName) {
        val text = draft.toText()
        if (text == savedText && deckName == persistedName) return@LaunchedEffect
        if (persistedName == null && draft.deckSize == 0 && draft.sideboard.isEmpty()) return@LaunchedEffect
        delay(600)
        val commander = draft.commanders.firstOrNull()
        onSave(persistedName, SavedDeck(deckName, text, commander))
        savedText = text
        persistedName = deckName
    }

    val issues = remember(draft) { validateCommanderDeck(draft, cat) }
    val stats = remember(draft) { deckStats(draft, cat) }
    val identity = stats.identity

    StudioDialog("Deck Builder Screen 7", onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val twoPane = maxWidth >= 640.dp
                val threePane = maxWidth >= 1000.dp
                Column(Modifier.fillMaxSize()) {
                    // Top bar
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudioIconButton("DeckBuilderScreen.IconButton.4", "Decks", onClick = onClose, modifier = Modifier.size(48.dp)) { StudioIcon("DeckBuilderScreen.Icon.4", "Decks", Icons.AutoMirrored.Filled.ArrowBack, "Back to decks", tint = SoftWhite) }
                        Column(Modifier.weight(1f).clickable { renameOpen = true }) {
                            StudioText("DeckBuilderScreen.Text.51", "Decks", true, deckName, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val errs = issues.count { it.level == IssueLevel.ERROR }
                            StudioText("DeckBuilderScreen.Text.52", "Decks", true, "${draft.deckSize}/100 · ${if (errs == 0) "ready" else "$errs to fix"}${if (draft.toText() == savedText) " · saved" else ""}",
                                color = if (errs == 0) Mint else CounterGold, fontSize = 11.sp)
                        }
                        key(historyTick) {
                            StudioIconButton("DeckBuilderScreen.IconButton.5", "Decks", onClick = { draft = session.undo(); historyTick++ }, enabled = session.canUndo, modifier = Modifier.size(48.dp)) { StudioIcon("DeckBuilderScreen.Icon.5", "Decks", Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (session.canUndo) SoftWhite else Muted.copy(alpha = 0.4f)) }
                            StudioIconButton("DeckBuilderScreen.IconButton.6", "Decks", onClick = { draft = session.redo(); historyTick++ }, enabled = session.canRedo, modifier = Modifier.size(48.dp)) { StudioIcon("DeckBuilderScreen.Icon.6", "Decks", Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (session.canRedo) SoftWhite else Muted.copy(alpha = 0.4f)) }
                        }
                        Box {
                            StudioIconButton("DeckBuilderScreen.IconButton.7", "Decks", onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) { StudioIcon("DeckBuilderScreen.Icon.7", "Decks", Icons.Filled.MoreVert, "Deck options", tint = SoftWhite) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.9", "Decks", text = { StudioText("DeckBuilderScreen.Text.53", "Decks", false, "Rename") }, onClick = { menuOpen = false; renameOpen = true })
                                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.10", "Decks", text = { StudioText("DeckBuilderScreen.Text.54", "Decks", false, "Copy list") }, onClick = { menuOpen = false; copyDeckText(context, deckName, draft.toText()) })
                                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.11", "Decks", text = { StudioText("DeckBuilderScreen.Text.55", "Decks", false, "Share list") }, onClick = { menuOpen = false; shareDeckText(context, deckName, draft.toText()) })
                            }
                        }
                    }
                    if (!twoPane) {
                        TabRow(selectedTabIndex = tab.ordinal, containerColor = Panel, contentColor = Mint) {
                            StudioTab("DeckBuilderScreen.Tab.1", "Decks", tab == EditorTab.LIBRARY, { tab = EditorTab.LIBRARY }, text = { StudioText("DeckBuilderScreen.Text.56", "Decks", false, "Library") })
                            StudioTab("DeckBuilderScreen.Tab.2", "Decks", tab == EditorTab.DECK, { tab = EditorTab.DECK }, text = { StudioText("DeckBuilderScreen.Text.57", "Decks", true, "Deck ${draft.deckSize}") })
                            StudioTab("DeckBuilderScreen.Tab.3", "Decks", tab == EditorTab.STATS, { tab = EditorTab.STATS }, text = { StudioText("DeckBuilderScreen.Text.58", "Decks", false, "Stats") })
                        }
                    }
                    val libraryPane: @Composable (Modifier) -> Unit = { m ->
                        LibraryPane(lib, draft, identity, query, { query = it }, m,
                            onAdd = { name -> edit { it.add(name) } },
                            onCommander = { name -> edit { it.setCommander(name) } },
                            onPreview = { previewName = it })
                    }
                    val deckPane: @Composable (Modifier) -> Unit = { m ->
                        DeckPane(draft, cat, issues, stats, showStatsInline = twoPane && !threePane, m,
                            onCount = { name, n -> edit { it.setCount(name, n) } },
                            onCommander = { name -> edit { it.setCommander(name) } },
                            onPartner = { name -> edit { it.setCommander(name, asSecond = true) } },
                            onRemoveCommander = { name -> edit { it.removeCommander(name) } },
                            onSideboard = { name -> edit { it.moveToSideboard(name) } },
                            onMain = { name -> edit { it.moveToMain(name) } },
                            onMaybe = { name -> edit { it.moveToMaybeboard(name) } },
                            onFromMaybe = { name -> edit { it.moveFromMaybeboard(name) } },
                            onRemoveMaybe = { name -> edit { it.removeFromMaybeboard(name) } },
                            onReplace = { replacing = it },
                            onPreview = { previewName = it })
                    }
                    when {
                        threePane -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            libraryPane(Modifier.weight(1.1f))
                            deckPane(Modifier.weight(1f))
                            Column(Modifier.weight(0.7f).verticalScroll(rememberScrollState())) { StatsPanel(stats, issues) }
                        }
                        twoPane -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            libraryPane(Modifier.weight(1f))
                            deckPane(Modifier.weight(1f))
                        }
                        else -> when (tab) {
                            EditorTab.LIBRARY -> libraryPane(Modifier.fillMaxSize().padding(horizontal = 10.dp))
                            EditorTab.DECK -> deckPane(Modifier.fillMaxSize().padding(horizontal = 10.dp))
                            EditorTab.STATS -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) { StatsPanel(stats, issues) }
                        }
                    }
                }
            }
        }
    }

    previewName?.let { name ->
        CardPreviewDialog(name, cat, draft, onAdd = { edit { it.add(name) } }, onCommander = { edit { it.setCommander(name) } },
            onMaybe = { edit { it.addToMaybeboard(name) } }, onDismiss = { previewName = null })
    }
    replacing?.let { name ->
        ReplacementDialog(name, lib, draft, identity, onChoose = { choice -> edit { it.replace(name, choice) }; replacing = null }, onDismiss = { replacing = null })
    }
    if (renameOpen) {
        var value by remember { mutableStateOf(deckName) }
        StudioAlertDialog("Deck Builder Screen 8", onDismissRequest = { renameOpen = false }, title = { StudioText("DeckBuilderScreen.Text.59", "Decks", false, "Rename deck") },
            text = { StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.5", "Decks", value, { value = it }, singleLine = true, label = { StudioText("DeckBuilderScreen.Text.60", "Decks", false, "Deck name") }) },
            confirmButton = { StudioTextButton("DeckBuilderScreen.TextButton.11", "Decks", enabled = value.isNotBlank(), onClick = {
                val others = allDecks.filterNot { it.name == persistedName }
                deckName = if (value.trim().equals(deckName, true)) value.trim() else SavedDeckOps.uniqueName(value.trim(), others)
                renameOpen = false
            }) { StudioText("DeckBuilderScreen.Text.61", "Decks", false, "SAVE") } },
            dismissButton = { StudioTextButton("DeckBuilderScreen.TextButton.12", "Decks", onClick = { renameOpen = false }) { StudioText("DeckBuilderScreen.Text.62", "Decks", false, "CANCEL") } })
    }
}

// ---- Library pane ---------------------------------------------------------------------------

/** Deck-context search terms layered on the FILTERS panel: commanders first, then what fits. */
private fun contextQuery(commandersOnly: Boolean, fitsCommander: Boolean, identity: Set<Char>, hasCommander: Boolean): String = when {
    commandersOnly -> "is:commander"
    fitsCommander && hasCommander -> "id:" + (if (identity.isEmpty()) "c" else "WUBRG".filter { it in identity }.lowercase())
    else -> ""
}

private val QUICK_TYPES = listOf("creature", "instant", "sorcery", "artifact", "enchantment", "planeswalker", "land", "legendary")

/** The quick mana-value chip writes one mana value condition; other stat filters are left alone. */
private fun CardFilters.withQuickMv(max: Int?): CardFilters {
    val others = stats.filterNot { it.stat == StatField.MANA_VALUE }
    return copy(stats = others + listOfNotNull(max?.let { if (it >= 7) StatRange(StatField.MANA_VALUE, StatOp.GTE, 7.0) else StatRange(StatField.MANA_VALUE, StatOp.LTE, it.toDouble()) }))
}

private fun CardFilters.quickMvLabel(): String? = stats.singleOrNull { it.stat == StatField.MANA_VALUE }?.let { s ->
    when (s.op) { StatOp.LTE -> "MV ≤${fmtNumber(s.value)}"; StatOp.GTE -> "MV ${fmtNumber(s.value)}+"; else -> s.label.replace("Mana value", "MV") }
}

@Composable
private fun LibraryPane(
    lib: CardLibrary,
    draft: DeckDraft,
    identity: Set<Char>,
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier,
    onAdd: (String) -> Unit,
    onCommander: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var filters by rememberSaveable(stateSaver = CardFiltersSaver) { mutableStateOf(CardFilters()) }
    var commandersOnly by rememberSaveable { mutableStateOf(draft.commanders.isEmpty()) }
    var fitsCommander by rememberSaveable { mutableStateOf(true) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CardLibrary.Result?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val hasCommander = draft.commanders.isNotEmpty()
    // Start a new deck by showing commanders; once one is chosen, show what fits it instead.
    LaunchedEffect(hasCommander) { if (hasCommander && commandersOnly) { commandersOnly = false; fitsCommander = true } }
    val baseQuery = "$query ${contextQuery(commandersOnly, fitsCommander, identity, hasCommander)}".trim()
    LaunchedEffect(baseQuery, filters) {
        delay(if (query.isBlank()) 0 else 220) // debounce typing
        result = withContext(Dispatchers.Default) { lib.search(baseQuery, filters, limit = 400) }
    }
    // New results start at the top. requestScrollToItem applies on the next layout pass, on the UI
    // thread; scrolling from the search coroutine could measure off the main thread.
    LaunchedEffect(result) { listState.requestScrollToItem(0); gridState.requestScrollToItem(0) }
    Column(modifier) {
        StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.6", "Decks", query, onQuery, Modifier.fillMaxWidth().testTag("library-search"), singleLine = true,
            label = { StudioText("DeckBuilderScreen.Text.63", "Decks", true, "Search ${lib.size} playable cards") },
            placeholder = { StudioText("DeckBuilderScreen.Text.64", "Decks", false, "name, or t:creature o:draw mv<=3") },
            trailingIcon = { if (query.isNotEmpty()) StudioIconButton("DeckBuilderScreen.IconButton.8", "Decks", onClick = { onQuery("") }) { StudioIcon("DeckBuilderScreen.Icon.8", "Decks", Icons.Filled.Close, "Clear search", tint = Muted) } })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val active = filters.activeCount
            StudioFilterChip("DeckBuilderScreen.FilterChip.5", "Decks", active > 0, { sheetOpen = true }, modifier = Modifier.testTag("open-filters"),
                leadingIcon = { StudioIcon("DeckBuilderScreen.Icon.9", "Decks", Icons.Filled.Tune, null, Modifier.size(18.dp)) },
                label = { StudioText("DeckBuilderScreen.Text.65", "Decks", true, if (active > 0) "FILTERS · $active" else "FILTERS", fontWeight = FontWeight.Bold) })
            StudioFilterChip("DeckBuilderScreen.FilterChip.6", "Decks", commandersOnly, { commandersOnly = !commandersOnly }, label = { StudioText("DeckBuilderScreen.Text.66", "Decks", false, "Commanders") })
            if (hasCommander) StudioFilterChip("DeckBuilderScreen.FilterChip.7", "Decks", fitsCommander && !commandersOnly, { fitsCommander = !fitsCommander; commandersOnly = false }, label = { StudioText("DeckBuilderScreen.Text.67", "Decks", true, "Fits ${identityLabel(identity)}") })
            "WUBRG".forEach { c ->
                StudioFilterChip("DeckBuilderScreen.FilterChip.8", "Decks", !filters.colorless && c in filters.colors, { filters = filters.copy(colorless = false, colors = if (c in filters.colors) filters.colors - c else filters.colors + c) },
                    label = { StudioText("DeckBuilderScreen.Text.68", "Decks", true, c.toString(), fontWeight = FontWeight.Bold) })
            }
            StudioFilterChip("DeckBuilderScreen.FilterChip.9", "Decks", filters.colorless, { filters = filters.copy(colorless = !filters.colorless, colors = emptySet()) }, label = { StudioText("DeckBuilderScreen.Text.69", "Decks", false, "C") })
            var typeMenu by remember { mutableStateOf(false) }
            Box {
                StudioFilterChip("DeckBuilderScreen.FilterChip.10", "Decks", filters.typeTerms.isNotEmpty(), { typeMenu = true }, label = { StudioText("DeckBuilderScreen.Text.70", "Decks", true, filters.typeTerms.singleOrNull()?.replaceFirstChar(Char::uppercaseChar) ?: if (filters.typeTerms.isEmpty()) "Type ▾" else "${filters.typeTerms.size} types") })
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.12", "Decks", text = { StudioText("DeckBuilderScreen.Text.71", "Decks", false, "Any type") }, onClick = { filters = filters.copy(typeTerms = emptyList()); typeMenu = false })
                    QUICK_TYPES.forEach { t ->
                        StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.13", "Decks", text = { StudioText("DeckBuilderScreen.Text.72", "Decks", true, t.replaceFirstChar(Char::uppercaseChar)) }, onClick = { filters = filters.copy(typeTerms = listOf(t)); typeMenu = false })
                    }
                }
            }
            var mvMenu by remember { mutableStateOf(false) }
            Box {
                StudioFilterChip("DeckBuilderScreen.FilterChip.11", "Decks", filters.stats.any { it.stat == StatField.MANA_VALUE }, { mvMenu = true }, label = { StudioText("DeckBuilderScreen.Text.73", "Decks", true, filters.quickMvLabel() ?: "Mana value ▾") })
                DropdownMenu(mvMenu, { mvMenu = false }) {
                    StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.14", "Decks", text = { StudioText("DeckBuilderScreen.Text.74", "Decks", false, "Any") }, onClick = { filters = filters.withQuickMv(null); mvMenu = false })
                    (1..6).forEach { n -> StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.15", "Decks", text = { StudioText("DeckBuilderScreen.Text.75", "Decks", true, "$n or less") }, onClick = { filters = filters.withQuickMv(n); mvMenu = false }) }
                    StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.16", "Decks", text = { StudioText("DeckBuilderScreen.Text.76", "Decks", false, "7 or more") }, onClick = { filters = filters.withQuickMv(7); mvMenu = false })
                }
            }
            StudioTextButton("DeckBuilderScreen.TextButton.13", "Decks", onClick = { showHelp = !showHelp }) { StudioText("DeckBuilderScreen.Text.77", "Decks", true, if (showHelp) "HIDE TIPS" else "SEARCH TIPS", color = Mint, fontSize = 11.sp) }
        }
        if (showHelp) StudioText("DeckBuilderScreen.Text.78", "Decks", false,
            "t:creature · o:draw · c:ub (colors) · id:ub (fits commander) · mv<=3 · pow>=4 · k:flying · r:mythic · s:cmm · is:legendary · is:commander · -t:land to exclude. Combine freely, or use FILTERS.",
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        val chips = filters.summaryChips()
        if (chips.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            chips.forEach { (label, without) -> RemovableChip(label) { filters = without } }
            StudioTextButton("DeckBuilderScreen.TextButton.14", "Decks", onClick = { filters = filters.cleared() }, modifier = Modifier.heightIn(min = 40.dp)) { StudioText("DeckBuilderScreen.Text.79", "Decks", false, "CLEAR", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        val r = result
        when {
            r == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Mint) }
            r.cards.isEmpty() -> Column(Modifier.fillMaxWidth().padding(20.dp)) {
                StudioText("DeckBuilderScreen.Text.80", "Decks", false, "No playable cards match.", color = SoftWhite, fontWeight = FontWeight.Bold)
                StudioText("DeckBuilderScreen.Text.81", "Decks", true, if (filters.activeCount > 0 || commandersOnly || fitsCommander) "Try removing a filter. Only cards this build of Arcana can play are listed." else "Only cards this build of Arcana can play are listed.", color = Muted, fontSize = 12.sp)
                r.errors.forEach { StudioText("DeckBuilderScreen.Text.82", "Decks", true, it, color = CounterGold, fontSize = 12.sp) }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StudioText("DeckBuilderScreen.Text.83", "Decks", true, buildString {
                        append(if (r.total > r.cards.size) "Showing ${r.cards.size} of ${r.total}" else "${r.total} card${if (r.total == 1) "" else "s"}")
                        if (r.fuzzy) append(" · closest names (check spelling)")
                    }, color = if (r.fuzzy) CounterGold else Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        StudioTextButton("DeckBuilderScreen.TextButton.15", "Decks", onClick = { sortMenu = true }, modifier = Modifier.heightIn(min = 44.dp).testTag("results-sort")) {
                            StudioText("DeckBuilderScreen.Text.84", "Decks", true, "${filters.sort.label} ${if (filters.descending) "↓" else "↑"}", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            CardSort.entries.forEach { s ->
                                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.17", "Decks", text = { StudioText("DeckBuilderScreen.Text.85", "Decks", true, s.label + if (s == filters.sort) "  ✓" else "") }, onClick = { filters = filters.copy(sort = s); sortMenu = false })
                            }
                            HorizontalDivider()
                            StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.18", "Decks", text = { StudioText("DeckBuilderScreen.Text.86", "Decks", true, if (filters.descending) "↑ Ascending" else "↓ Descending") }, onClick = { filters = filters.copy(descending = !filters.descending); sortMenu = false })
                        }
                    }
                    StudioIconButton("DeckBuilderScreen.IconButton.9", "Decks", onClick = { grid = !grid }, modifier = Modifier.size(44.dp).testTag("results-view")) {
                        StudioIcon("DeckBuilderScreen.Icon.10", "Decks", if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView, if (grid) "Show as list" else "Show as card grid", tint = Mint)
                    }
                }
                r.errors.forEach { StudioText("DeckBuilderScreen.Text.87", "Decks", true, it, color = CounterGold, fontSize = 11.sp) }
                fun inDeck(card: SupportedCard) = draft.countOf(card.name) + if (draft.commanders.any { it.equals(card.name, true) }) 1 else 0
                if (grid) LibraryGrid(r.cards, ::inDeck, gridState, onAdd = { onAdd(it.name) }, onPreview = { onPreview(it.name) }, modifier = Modifier.fillMaxSize().padding(top = 4.dp))
                else LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(r.cards, key = { it.name }) { card ->
                        LibraryRow(card, inDeck(card), canCommand = CardLibrary.isCommanderEligible(card),
                            onAdd = { onAdd(card.name) }, onCommander = { onCommander(card.name) }, onPreview = { onPreview(card.name) })
                    }
                }
            }
        }
    }
    if (sheetOpen) CardFilterSheet(lib, baseQuery, filters, onChange = { filters = it }, onDismiss = { sheetOpen = false })
}

@Composable
private fun LibraryRow(card: SupportedCard, inDeck: Int, canCommand: Boolean, onAdd: () -> Unit, onCommander: () -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).studioCombinedClickable("DeckBuilderScreen.combinedClickable.1", "Decks", onClick = onPreview, onLongClick = onPreview).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        card.imageUrl?.let { CardArt(it, Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop, fallbackName = card.name, fallbackType = card.typeLine) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            StudioText("DeckBuilderScreen.Text.88", "Decks", true, card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StudioText("DeckBuilderScreen.Text.89", "Decks", true, listOf(card.manaCost.ifBlank { "—" }, card.typeLine).joinToString("  ·  "), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StudioText("DeckBuilderScreen.Text.90", "Decks", true, listOfNotNull(
                card.power?.let { "$it/${card.toughness}" },
                card.setCode,
                card.rarity?.lowercase()?.replaceFirstChar(Char::uppercaseChar) ?: "Common".takeIf { card.setCode != null },
                "Arcana custom card".takeIf { card.custom },
            ).joinToString(" · "), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (inDeck > 0) StudioText("DeckBuilderScreen.Text.91", "Decks", true, "×$inDeck", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        if (canCommand) StudioTextButton("DeckBuilderScreen.TextButton.16", "Decks", onClick = onCommander, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.92", "Decks", false, "CMDR", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        StudioIconButton("DeckBuilderScreen.IconButton.10", "Decks", onClick = onAdd, modifier = Modifier.size(48.dp).testTag("add-${card.name}")) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) { StudioIcon("DeckBuilderScreen.Icon.11", "Decks", Icons.Filled.Add, "Add ${card.name}", tint = Ink) }
        }
    }
}

// ---- Deck pane ------------------------------------------------------------------------------

@Composable
private fun DeckPane(
    draft: DeckDraft,
    cat: SupportedCardCatalog,
    issues: List<DeckIssue>,
    stats: DeckStats,
    showStatsInline: Boolean,
    modifier: Modifier,
    onCount: (String, Int) -> Unit,
    onCommander: (String) -> Unit,
    onPartner: (String) -> Unit,
    onRemoveCommander: (String) -> Unit,
    onSideboard: (String) -> Unit,
    onMain: (String) -> Unit,
    onMaybe: (String) -> Unit,
    onFromMaybe: (String) -> Unit,
    onRemoveMaybe: (String) -> Unit,
    onReplace: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var grouping by rememberSaveable { mutableStateOf(DeckGrouping.TYPE) }
    val groups = remember(draft, grouping) { groupDeck(draft, cat, grouping) }
    val flagged = remember(issues) { issues.filter { it.level == IssueLevel.ERROR }.flatMap { it.cards }.map { it.lowercase() }.toSet() }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        item {
            StudioText("DeckBuilderScreen.Text.93", "Decks", false, "COMMANDER", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
            if (draft.commanders.isEmpty()) StudioText("DeckBuilderScreen.Text.94", "Decks", false, "No commander yet. Tap CMDR on a legendary creature in the library, or use a card's menu here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            draft.commanders.forEach { name ->
                val card = cat.find(name)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Panel).clickable { onPreview(name) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    card?.imageUrl?.let { CardArt(it, Modifier.size(width = 52.dp, height = 72.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop, fallbackName = name) }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        StudioText("DeckBuilderScreen.Text.95", "Decks", true, name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        StudioText("DeckBuilderScreen.Text.96", "Decks", true, card?.let { "${it.manaCost} · ${identityLabel(it.colors)}" } ?: "Not in this build's library", color = if (card == null) CounterGold else Muted, fontSize = 11.sp)
                    }
                    if (card == null) StudioTextButton("DeckBuilderScreen.TextButton.17", "Decks", onClick = { onReplace(name) }) { StudioText("DeckBuilderScreen.Text.97", "Decks", false, "REPLACE", color = Mint, fontSize = 11.sp) }
                    StudioTextButton("DeckBuilderScreen.TextButton.18", "Decks", onClick = { onRemoveCommander(name) }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.98", "Decks", false, "TO DECK", color = Muted, fontSize = 11.sp) }
                }
            }
        }
        item { IssueSummary(issues) }
        if (showStatsInline) item { MiniStats(stats) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioText("DeckBuilderScreen.Text.99", "Decks", false, "GROUP", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                DeckGrouping.entries.forEach { g -> StudioFilterChip("DeckBuilderScreen.FilterChip.12", "Decks", grouping == g, { grouping = g }, label = { StudioText("DeckBuilderScreen.Text.100", "Decks", true, g.label) }) }
            }
        }
        if (draft.main.isEmpty()) item { StudioText("DeckBuilderScreen.Text.101", "Decks", false, "The deck is empty. Add cards from the library.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp)) }
        groups.forEach { (group, lines) ->
            item(key = "g-$group") {
                StudioText("DeckBuilderScreen.Text.102", "Decks", true, "${group.uppercase()} · ${lines.sumOf { it.count }}", color = if (group == "Not in library") CounterGold else Mint, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            }
            items(lines, key = { "c-${it.name}" }) { line ->
                DeckRow(line, cat.find(line.name), line.name.lowercase() in flagged, canCommand = cat.find(line.name)?.let { CardLibrary.isCommanderEligible(it) } == true,
                    hasOneCommander = draft.commanders.size == 1,
                    onCount = { onCount(line.name, it) }, onCommander = { onCommander(line.name) }, onPartner = { onPartner(line.name) },
                    onSideboard = { onSideboard(line.name) }, onMaybe = { onMaybe(line.name) }, onReplace = { onReplace(line.name) }, onPreview = { onPreview(line.name) })
            }
        }
        if (draft.sideboard.isNotEmpty()) {
            item { StudioText("DeckBuilderScreen.Text.103", "Decks", true, "SIDEBOARD · ${draft.sideboard.sumOf { it.count }} (not in the 100)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp)) }
            items(draft.sideboard, key = { "s-${it.name}" }) { line ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioText("DeckBuilderScreen.Text.104", "Decks", true, "${line.count}× ${line.name}", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    StudioTextButton("DeckBuilderScreen.TextButton.19", "Decks", onClick = { onMain(line.name) }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.105", "Decks", false, "TO DECK", color = Mint, fontSize = 11.sp) }
                }
            }
        }
        if (draft.maybeboard.isNotEmpty()) {
            item { StudioText("DeckBuilderScreen.Text.106", "Decks", true, "MAYBEBOARD · ${draft.maybeboard.sumOf { it.count }} (considering; not in the 100)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp)) }
            items(draft.maybeboard, key = { "m-${it.name}" }) { line ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).clickable { onPreview(line.name) }.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        StudioText("DeckBuilderScreen.Text.107", "Decks", true, "${line.count}× ${line.name}", color = SoftWhite, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        cat.find(line.name)?.let { StudioText("DeckBuilderScreen.Text.108", "Decks", true, it.manaCost.ifBlank { it.typeLine.substringBefore(" —") }, color = Muted, fontSize = 10.sp, maxLines = 1) }
                            ?: StudioText("DeckBuilderScreen.Text.109", "Decks", false, "Not in this build's library", color = CounterGold, fontSize = 10.sp)
                    }
                    StudioTextButton("DeckBuilderScreen.TextButton.20", "Decks", onClick = { onFromMaybe(line.name) }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.110", "Decks", false, "TO DECK", color = Mint, fontSize = 11.sp) }
                    StudioIconButton("DeckBuilderScreen.IconButton.11", "Decks", onClick = { onRemoveMaybe(line.name) }, modifier = Modifier.size(44.dp)) { StudioIcon("DeckBuilderScreen.Icon.12", "Decks", Icons.Filled.Close, "Remove ${line.name} from maybeboard", tint = Muted) }
                }
            }
        }
        draft.companion?.let { c -> item { StudioText("DeckBuilderScreen.Text.111", "Decks", true, "COMPANION · $c (saved; not usable in games yet)", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) } }
        if (draft.unreadLines.isNotEmpty()) item {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF392B21)).padding(10.dp)) {
                StudioText("DeckBuilderScreen.Text.112", "Decks", false, "LINES THE IMPORTER COULDN'T READ · kept in the saved list", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                draft.unreadLines.forEach { StudioText("DeckBuilderScreen.Text.113", "Decks", true, it, color = SoftWhite, fontSize = 12.sp) }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun DeckRow(line: DeckLine, card: SupportedCard?, flagged: Boolean, canCommand: Boolean, hasOneCommander: Boolean,
                    onCount: (Int) -> Unit, onCommander: () -> Unit, onPartner: () -> Unit, onSideboard: () -> Unit, onMaybe: () -> Unit, onReplace: () -> Unit, onPreview: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(if (flagged) Color(0xFF392B21) else Panel)
        .border(if (flagged) 1.5.dp else 0.dp, if (flagged) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
        .studioClickable("DeckBuilderScreen.clickable.2", "Decks", onClick = onPreview).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StudioIconButton("DeckBuilderScreen.IconButton.12", "Decks", onClick = { onCount(line.count - 1) }, modifier = Modifier.size(44.dp)) { StudioIcon("DeckBuilderScreen.Icon.13", "Decks", Icons.Filled.Remove, "Remove one ${line.name}", tint = Muted) }
        StudioText("DeckBuilderScreen.Text.114", "Decks", true, "${line.count}", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.widthIn(min = 18.dp))
        StudioIconButton("DeckBuilderScreen.IconButton.13", "Decks", onClick = { onCount(line.count + 1) }, modifier = Modifier.size(44.dp)) { StudioIcon("DeckBuilderScreen.Icon.14", "Decks", Icons.Filled.Add, "Add one ${line.name}", tint = Muted) }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
            StudioText("DeckBuilderScreen.Text.115", "Decks", true, line.name, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card == null) StudioText("DeckBuilderScreen.Text.116", "Decks", false, "Not in this build's library · Arcana can't play it", color = CounterGold, fontSize = 10.sp)
            else StudioText("DeckBuilderScreen.Text.117", "Decks", true, card.manaCost.ifBlank { card.typeLine.substringBefore(" —") }, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        if (card == null) StudioTextButton("DeckBuilderScreen.TextButton.21", "Decks", onClick = onReplace, modifier = Modifier.heightIn(min = 44.dp)) { StudioText("DeckBuilderScreen.Text.118", "Decks", false, "REPLACE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Box {
            StudioIconButton("DeckBuilderScreen.IconButton.14", "Decks", onClick = { menu = true }, modifier = Modifier.size(44.dp)) { StudioIcon("DeckBuilderScreen.Icon.15", "Decks", Icons.Filled.MoreVert, "More for ${line.name}", tint = Muted) }
            DropdownMenu(menu, { menu = false }) {
                if (canCommand) StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.19", "Decks", text = { StudioText("DeckBuilderScreen.Text.119", "Decks", false, "Make commander") }, onClick = { menu = false; onCommander() })
                if (canCommand && hasOneCommander) StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.20", "Decks", text = { StudioText("DeckBuilderScreen.Text.120", "Decks", false, "Add as second commander") }, onClick = { menu = false; onPartner() })
                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.21", "Decks", text = { StudioText("DeckBuilderScreen.Text.121", "Decks", false, "Move to maybeboard") }, onClick = { menu = false; onMaybe() })
                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.22", "Decks", text = { StudioText("DeckBuilderScreen.Text.122", "Decks", false, "Move to sideboard") }, onClick = { menu = false; onSideboard() })
                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.23", "Decks", text = { StudioText("DeckBuilderScreen.Text.123", "Decks", false, "Suggest replacements") }, onClick = { menu = false; onReplace() })
                StudioDropdownMenuItem("DeckBuilderScreen.DropdownMenuItem.24", "Decks", text = { StudioText("DeckBuilderScreen.Text.124", "Decks", false, "Remove all copies") }, onClick = { menu = false; onCount(0) })
            }
        }
    }
}

@Composable
private fun IssueSummary(issues: List<DeckIssue>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val errors = issues.filter { it.level == IssueLevel.ERROR }
    val warnings = issues.filter { it.level == IssueLevel.WARNING }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
        .background(if (errors.isEmpty()) Color(0xFF1E3A30) else Color(0xFF392B21)).clickable { expanded = !expanded }.padding(10.dp)) {
        StudioText("DeckBuilderScreen.Text.125", "Decks", true, when {
            errors.isEmpty() && warnings.isEmpty() -> "✓ Commander-legal and every card is in this build's library"
            errors.isEmpty() -> "✓ Legal · ${warnings.size} note${if (warnings.size == 1) "" else "s"}"
            else -> "${errors.size} thing${if (errors.size == 1) "" else "s"} to fix before this deck can be played"
        }, color = if (errors.isEmpty()) Mint else CounterGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (expanded || errors.size <= 2) (errors + warnings).forEach { issue ->
            StudioText("DeckBuilderScreen.Text.126", "Decks", true, "${if (issue.level == IssueLevel.ERROR) "•" else "◦"} ${issue.message}", color = SoftWhite, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
            if (issue.cards.isNotEmpty() && issue.cards.size <= 8 && expanded) StudioText("DeckBuilderScreen.Text.127", "Decks", true, issue.cards.joinToString(", "), color = Muted, fontSize = 11.sp)
        } else StudioText("DeckBuilderScreen.Text.128", "Decks", false, "Tap to show", color = Muted, fontSize = 11.sp)
    }
}

// ---- Stats ----------------------------------------------------------------------------------

@Composable
private fun MiniStats(s: DeckStats) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        StatNumber("Creatures", s.creatures); StatNumber("Lands", s.lands); StatNumber("Avg MV", String.format("%.2f", s.averageManaValue))
        CurveBars(s.curve, Modifier.weight(1f).height(44.dp))
    }
}

@Composable
private fun StatsPanel(s: DeckStats, issues: List<DeckIssue>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StudioText("DeckBuilderScreen.Text.129", "Decks", false, "DECK STATS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StatNumber("Cards", s.total); StatNumber("Creatures", s.creatures); StatNumber("Lands", s.lands)
            StatNumber("Avg MV", String.format("%.2f", s.averageManaValue))
        }
        StudioText("DeckBuilderScreen.Text.130", "Decks", false, "MANA CURVE (nonland)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        CurveBars(s.curve, Modifier.fillMaxWidth().height(110.dp), labels = true)
        StudioText("DeckBuilderScreen.Text.131", "Decks", true, "COLOR SYMBOLS IN COSTS · identity ${identityLabel(s.identity)}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val totalPips = s.pips.values.sum().coerceAtLeast(1)
        s.pips.filter { it.value > 0 }.forEach { (c, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioText("DeckBuilderScreen.Text.132", "Decks", true, c.toString(), color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(PanelLight)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(n / totalPips.toFloat()).background(pipColor(c)))
                }
                StudioText("DeckBuilderScreen.Text.133", "Decks", true, " $n", color = Muted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
            }
        }
        val needs = s.pips.filter { it.value > 0 || (s.landSources[it.key] ?: 0) > 0 }.keys
        if (needs.isNotEmpty()) {
            StudioText("DeckBuilderScreen.Text.134", "Decks", false, "MANA SOURCES · lands that make each color", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            needs.forEach { c ->
                val pips = s.pips[c] ?: 0
                val lands = s.landSources[c] ?: 0
                val short = pips > 0 && lands == 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioText("DeckBuilderScreen.Text.135", "Decks", true, c.toString(), color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                    StudioText("DeckBuilderScreen.Text.136", "Decks", true, "$lands land${if (lands == 1) "" else "s"} for $pips symbol${if (pips == 1) "" else "s"}", color = if (short) CounterGold else SoftWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (short) StudioText("DeckBuilderScreen.Text.137", "Decks", false, "no source", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            StudioText("DeckBuilderScreen.Text.138", "Decks", false, "A guide from land types and rules text; mana rocks and dorks aren't counted.", color = Muted, fontSize = 10.sp)
        }
        StudioText("DeckBuilderScreen.Text.139", "Decks", false, "BY TYPE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        DECK_GROUP_ORDER.mapNotNull { g -> s.byType[g]?.let { g to it } }.forEach { (g, n) -> StudioText("DeckBuilderScreen.Text.140", "Decks", true, "$g  $n", color = SoftWhite, fontSize = 12.sp) }
        if (s.unsupported > 0) StudioText("DeckBuilderScreen.Text.141", "Decks", true, "${s.unsupported} card${if (s.unsupported == 1) "" else "s"} not in this build's library (not counted above)", color = CounterGold, fontSize = 12.sp)
        IssueSummary(issues)
    }
}

@Composable
private fun StatNumber(label: String, value: Any) {
    Column { StudioText("DeckBuilderScreen.Text.142", "Decks", true, "$value", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 18.sp); StudioText("DeckBuilderScreen.Text.143", "Decks", true, label, color = Muted, fontSize = 10.sp) }
}

@Composable
private fun CurveBars(curve: List<Int>, modifier: Modifier, labels: Boolean = false) {
    val max = (curve.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        curve.forEachIndexed { i, n ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (labels) StudioText("DeckBuilderScreen.Text.144", "Decks", true, "$n", color = Muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().fillMaxHeight((n / max.toFloat()).coerceAtLeast(0.02f) * if (labels) 0.75f else 1f).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Mint))
                if (labels) StudioText("DeckBuilderScreen.Text.145", "Decks", true, if (i == 7) "7+" else "$i", color = SoftWhite, fontSize = 10.sp)
            }
        }
    }
}

private fun pipColor(c: Char) = when (c) { 'W' -> Color(0xFFF2E8C9); 'U' -> Color(0xFF6FA8DC); 'B' -> Color(0xFF8E7F99); 'R' -> Color(0xFFE07A5F); 'G' -> Color(0xFF6DBE7B); 'C' -> Color(0xFFB0B4B8); else -> Color.Gray }

// ---- Card preview & replacements ------------------------------------------------------------

@Composable
private fun CardPreviewDialog(name: String, cat: SupportedCardCatalog, draft: DeckDraft, onAdd: () -> Unit, onCommander: () -> Unit, onMaybe: () -> Unit, onDismiss: () -> Unit) {
    val card = cat.find(name)
    StudioDialog("Deck Builder Screen 9", onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                card?.imageUrl?.let { CardArt(it, Modifier.fillMaxWidth().aspectRatio(0.716f).clip(RoundedCornerShape(10.dp)), fallbackName = card.name, fallbackType = card.typeLine) }
                StudioText("DeckBuilderScreen.Text.146", "Decks", true, card?.name ?: name, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                if (card == null) StudioText("DeckBuilderScreen.Text.147", "Decks", false, "This card isn't in this build's library, so Arcana can't play it. Use REPLACE in the deck list.", color = CounterGold, fontSize = 13.sp)
                else {
                    StudioText("DeckBuilderScreen.Text.148", "Decks", true, "${card.manaCost.ifBlank { "No mana cost" }}  ·  ${card.typeLine}", color = Muted, fontSize = 12.sp)
                    card.oracleText?.let { StudioText("DeckBuilderScreen.Text.149", "Decks", true, it, color = SoftWhite, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
                    if (card.power != null) StudioText("DeckBuilderScreen.Text.150", "Decks", true, "${card.power}/${card.toughness}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    StudioText("DeckBuilderScreen.Text.151", "Decks", true, listOfNotNull("Identity ${identityLabel(card.colors)}", card.setCode, card.rarity?.lowercase()?.replaceFirstChar(Char::uppercaseChar),
                        if (card.custom) "Arcana custom card" else "Argentum library", "in library, not gameplay-verified").joinToString(" · "),
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (CardLibrary.isCommanderEligible(card) && draft.commanders.none { it.equals(card.name, true) })
                            StudioOutlinedButton("DeckBuilderScreen.OutlinedButton.2", "Decks", onClick = { onCommander(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.152", "Decks", false, "MAKE COMMANDER", color = CounterGold) }
                        if (draft.maybeboard.none { it.name.equals(card.name, true) } && draft.countOf(card.name) == 0)
                            StudioOutlinedButton("DeckBuilderScreen.OutlinedButton.3", "Decks", onClick = { onMaybe(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.153", "Decks", false, "MAYBE", color = SoftWhite) }
                        StudioButton("DeckBuilderScreen.Button.2", "Decks", onClick = { onAdd() }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
                            StudioText("DeckBuilderScreen.Text.154", "Decks", true, "ADD${draft.countOf(card.name).takeIf { it > 0 }?.let { " (×$it)" }.orEmpty()}")
                        }
                    }
                }
                StudioTextButton("DeckBuilderScreen.TextButton.22", "Decks", onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.155", "Decks", false, "CLOSE", color = Muted) }
            }
        }
    }
}

@Composable
private fun ReplacementDialog(name: String, lib: CardLibrary, draft: DeckDraft, identity: Set<Char>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    val forCommander = draft.commanders.any { it.equals(name, true) }
    var original by remember { mutableStateOf<CardLibrary.Original?>(null) }
    var status by remember { mutableStateOf("Looking up ${name}…") }
    var manual by remember { mutableStateOf("") }
    LaunchedEffect(name) {
        // A playable card describes itself offline; an unplayable one needs Scryfall for its type and text.
        val local = lib.catalog.find(name)
        original = if (local != null) CardLibrary.Original(local.name, local.typeLine, local.manaValue, local.colors, local.oracleText, local.keywords)
        else runCatching { Scryfall.fuzzyNamedCard(name) }.getOrNull()?.let { s ->
            CardLibrary.Original(s.name, s.type, s.manaValue, colorsOf(s.manaCost.orEmpty()) + colorsOf(s.oracleText.orEmpty()), s.oracleText, s.keywords)
        }
        status = if (original == null) "Couldn't look up $name (offline?). Search the library for a swap below." else ""
    }
    val inDeck = remember(draft) { (draft.main.map { it.name } + draft.commanders).toSet() }
    val suggestions = remember(original, identity) {
        original?.let { lib.suggestReplacements(it, if (forCommander) null else identity.takeIf { draft.commanders.isNotEmpty() }, exclude = inDeck, forCommander = forCommander) }.orEmpty()
    }
    val manualResults = remember(manual) { if (manual.length >= 2) lib.search(manual, 12).cards else emptyList() }
    StudioDialog("Deck Builder Screen 10", onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                StudioText("DeckBuilderScreen.Text.156", "Decks", true, "REPLACE ${name.uppercase()}", color = SoftWhite, fontWeight = FontWeight.Black)
                StudioText("DeckBuilderScreen.Text.157", "Decks", false, "Suggestions come only from cards Arcana can play. Nothing changes until you pick one.", color = Muted, fontSize = 12.sp)
                if (status.isNotBlank()) StudioText("DeckBuilderScreen.Text.158", "Decks", true, status, color = CounterGold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions, key = { "s-${it.card.name}" }) { s ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelLight).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                StudioText("DeckBuilderScreen.Text.159", "Decks", true, s.card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                StudioText("DeckBuilderScreen.Text.160", "Decks", true, "${s.card.manaCost} · ${s.card.typeLine}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                StudioText("DeckBuilderScreen.Text.161", "Decks", false, "Why: " + s.reasons.joinToString(" · "), color = Mint, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            StudioTextButton("DeckBuilderScreen.TextButton.23", "Decks", onClick = { onChoose(s.card.name) }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.162", "Decks", false, "USE", color = Mint, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (original != null && suggestions.isEmpty()) item { StudioText("DeckBuilderScreen.Text.163", "Decks", false, "No close match fits this deck's colors. Search below.", color = Muted, fontSize = 12.sp) }
                    item {
                        StudioOutlinedTextField("DeckBuilderScreen.OutlinedTextField.7", "Decks", manual, { manual = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { StudioText("DeckBuilderScreen.Text.164", "Decks", false, "Or search the library") })
                    }
                    items(manualResults, key = { "m-${it.name}" }) { c ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioText("DeckBuilderScreen.Text.165", "Decks", true, "${c.name}  ·  ${c.manaCost}", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StudioTextButton("DeckBuilderScreen.TextButton.24", "Decks", onClick = { onChoose(c.name) }, modifier = Modifier.heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.166", "Decks", false, "USE", color = Mint) }
                        }
                    }
                }
                StudioTextButton("DeckBuilderScreen.TextButton.25", "Decks", onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { StudioText("DeckBuilderScreen.Text.167", "Decks", true, "KEEP ${name.uppercase()}", color = Muted) }
            }
        }
    }
}

// ---- Export ---------------------------------------------------------------------------------

private fun copyDeckText(context: Context, name: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(name, text))
    Toast.makeText(context, "Copied $name", Toast.LENGTH_SHORT).show()
}

private fun shareDeckText(context: Context, name: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
