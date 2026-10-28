@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

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
    var editing by rememberSaveable { mutableStateOf<String?>(null) }   // saved name, "" for a new deck
    var importing by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SavedDeck?>(null) }
    var deleting by remember { mutableStateOf<SavedDeck?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    val editTarget = editing
    if (editTarget != null) {
        val source = decks.firstOrNull { it.name == editTarget }
        DeckEditorScreen(
            initialName = source?.name ?: SavedDeckOps.uniqueName("New deck", decks),
            initialText = source?.text ?: pendingImport ?: "",
            savedName = source?.name,
            allDecks = decks,
            loadCatalog = loadCatalog,
            onSave = { original, deck ->
                onDecksChanged(SavedDeckOps.upsert(decks, original, deck))
                editing = deck.name
                pendingImport = null
            },
            onClose = { editing = null; pendingImport = null },
        )
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftWhite) }
                    Column(Modifier.weight(1f)) {
                        Text("DECK BUILDER", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.5.sp)
                        Text("Developer preview · your saved decks", color = Muted, fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { pendingImport = null; editing = "" }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("deck-new"),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("NEW DECK", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { importText = ""; importing = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("PASTE A LIST", color = SoftWhite, fontWeight = FontWeight.Bold) }
                }
                if (decks.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No saved decks yet.\nStart a new deck or paste a list from ManaBox, Moxfield, Archidekt or Arena.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(decks, key = { it.name }) { deck ->
                        val entries = remember(deck.text) { parseDeckListDetailed(deck.text) }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { editing = deck.name }.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(deck.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(
                                    entries.commanders.joinToString(" + ") { it.name }.ifBlank { null },
                                    "${entries.playableEntries.sumOf { it.count }} cards",
                                ).joinToString(" · "), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { editing = deck.name }, modifier = Modifier.heightIn(min = 48.dp)) { Text("EDIT", color = Mint, fontWeight = FontWeight.Bold) }
                            Box {
                                IconButton(onClick = { menuFor = deck.name }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.MoreVert, "More for ${deck.name}", tint = Muted) }
                                DropdownMenu(expanded = menuFor == deck.name, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(text = { Text("Play this deck") }, onClick = { menuFor = null; onPlay(deck) })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuFor = null; renaming = deck })
                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuFor = null; onDecksChanged(SavedDeckOps.duplicate(decks, deck)) })
                                    DropdownMenuItem(text = { Text("Copy list") }, onClick = { menuFor = null; copyDeckText(context, deck.name, deck.text) })
                                    DropdownMenuItem(text = { Text("Share list") }, onClick = { menuFor = null; shareDeckText(context, deck.name, deck.text) })
                                    DropdownMenuItem(text = { Text("Delete…", color = Color(0xFFE9978B)) }, onClick = { menuFor = null; deleting = deck })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (importing) AlertDialog(
        onDismissRequest = { importing = false },
        title = { Text("Paste a deck list") },
        text = {
            Column {
                Text("Works offline. Cards are matched against this build's library; anything it can't play is flagged in the editor, never swapped for you.", fontSize = 12.sp)
                OutlinedTextField(importText, { importText = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp).padding(top = 8.dp), placeholder = { Text("1 Sol Ring\n1 Command Tower\n…") })
            }
        },
        confirmButton = { TextButton(enabled = importText.isNotBlank(), onClick = { pendingImport = importText; importing = false; editing = "" }) { Text("OPEN IN EDITOR") } },
        dismissButton = { TextButton(onClick = { importing = false }) { Text("CANCEL") } },
    )
    renaming?.let { deck ->
        var value by remember(deck) { mutableStateOf(deck.name) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Rename deck") },
            text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Deck name") }) },
            confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onDecksChanged(SavedDeckOps.rename(decks, deck, value)); renaming = null }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("CANCEL") } })
    }
    deleting?.let { deck ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${deck.name}?") },
            text = { Text("This removes the saved deck from this phone. It can't be undone. Copy the list first if you might want it back.") },
            confirmButton = { TextButton(onClick = { onDecksChanged(SavedDeckOps.delete(decks, deck)); deleting = null }) { Text("DELETE", color = Color(0xFFE9978B)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("KEEP") } })
    }
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
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Ink) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loadFailed) Text("This build's card library couldn't be loaded.", color = CounterGold)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Mint); Text("Loading card library…", color = Muted, modifier = Modifier.padding(top = 10.dp)) }
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

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val twoPane = maxWidth >= 640.dp
                val threePane = maxWidth >= 1000.dp
                Column(Modifier.fillMaxSize()) {
                    // Top bar
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to decks", tint = SoftWhite) }
                        Column(Modifier.weight(1f).clickable { renameOpen = true }) {
                            Text(deckName, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val errs = issues.count { it.level == IssueLevel.ERROR }
                            Text("${draft.deckSize}/100 · ${if (errs == 0) "ready" else "$errs to fix"}${if (draft.toText() == savedText) " · saved" else ""}",
                                color = if (errs == 0) Mint else CounterGold, fontSize = 11.sp)
                        }
                        key(historyTick) {
                            IconButton(onClick = { draft = session.undo(); historyTick++ }, enabled = session.canUndo, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (session.canUndo) SoftWhite else Muted.copy(alpha = 0.4f)) }
                            IconButton(onClick = { draft = session.redo(); historyTick++ }, enabled = session.canRedo, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (session.canRedo) SoftWhite else Muted.copy(alpha = 0.4f)) }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.MoreVert, "Deck options", tint = SoftWhite) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
                                DropdownMenuItem(text = { Text("Copy list") }, onClick = { menuOpen = false; copyDeckText(context, deckName, draft.toText()) })
                                DropdownMenuItem(text = { Text("Share list") }, onClick = { menuOpen = false; shareDeckText(context, deckName, draft.toText()) })
                            }
                        }
                    }
                    if (!twoPane) {
                        TabRow(selectedTabIndex = tab.ordinal, containerColor = Panel, contentColor = Mint) {
                            Tab(tab == EditorTab.LIBRARY, { tab = EditorTab.LIBRARY }, text = { Text("Library") })
                            Tab(tab == EditorTab.DECK, { tab = EditorTab.DECK }, text = { Text("Deck ${draft.deckSize}") })
                            Tab(tab == EditorTab.STATS, { tab = EditorTab.STATS }, text = { Text("Stats") })
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
        CardPreviewDialog(name, cat, draft, onAdd = { edit { it.add(name) } }, onCommander = { edit { it.setCommander(name) } }, onDismiss = { previewName = null })
    }
    replacing?.let { name ->
        ReplacementDialog(name, lib, draft, identity, onChoose = { choice -> edit { it.replace(name, choice) }; replacing = null }, onDismiss = { replacing = null })
    }
    if (renameOpen) {
        var value by remember { mutableStateOf(deckName) }
        AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text("Rename deck") },
            text = { OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Deck name") }) },
            confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = {
                val others = allDecks.filterNot { it.name == persistedName }
                deckName = if (value.trim().equals(deckName, true)) value.trim() else SavedDeckOps.uniqueName(value.trim(), others)
                renameOpen = false
            }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("CANCEL") } })
    }
}

// ---- Library pane ---------------------------------------------------------------------------

private data class QuickFilters(
    val type: String? = null,
    val colors: Set<Char> = emptySet(),
    val colorless: Boolean = false,
    val maxMv: Int? = null,
    val fitsCommander: Boolean = true,
    val commandersOnly: Boolean = false,
)

private fun QuickFilters.toQuery(identity: Set<Char>, hasCommander: Boolean): String = buildList {
    type?.let { add("t:$it") }
    if (colorless) add("c:c") else if (colors.isNotEmpty()) add("c:" + "WUBRG".filter { it in colors }.lowercase())
    maxMv?.let { add(if (it >= 7) "mv>=7" else "mv<=$it") }
    if (commandersOnly) add("is:commander")
    else if (fitsCommander && hasCommander) add("id:" + (if (identity.isEmpty()) "c" else "WUBRG".filter { it in identity }.lowercase()))
}.joinToString(" ")

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
    var filters by remember { mutableStateOf(QuickFilters(commandersOnly = draft.commanders.isEmpty())) }
    var result by remember { mutableStateOf<CardLibrary.Result?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hasCommander = draft.commanders.isNotEmpty()
    // Start a new deck by showing commanders; once one is chosen, show what fits it instead.
    LaunchedEffect(hasCommander) { if (hasCommander && filters.commandersOnly) filters = filters.copy(commandersOnly = false, fitsCommander = true) }
    val filterQuery = filters.toQuery(identity, hasCommander)
    LaunchedEffect(query, filterQuery) {
        delay(if (query.isBlank()) 0 else 220) // debounce typing
        result = withContext(Dispatchers.Default) { lib.search("$query $filterQuery".trim(), limit = 400) }
    }
    Column(modifier) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().testTag("library-search"), singleLine = true,
            label = { Text("Search ${lib.size} playable cards") },
            placeholder = { Text("name, or t:creature o:draw mv<=3") },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear search", tint = Muted) } })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(filters.commandersOnly, { filters = filters.copy(commandersOnly = !filters.commandersOnly) }, label = { Text("Commanders") })
            if (hasCommander) FilterChip(filters.fitsCommander && !filters.commandersOnly, { filters = filters.copy(fitsCommander = !filters.fitsCommander, commandersOnly = false) }, label = { Text("Fits ${identityLabel(identity)}") })
            var typeMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(filters.type != null, { typeMenu = true }, label = { Text(filters.type?.replaceFirstChar(Char::uppercaseChar) ?: "Type ▾") })
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    DropdownMenuItem(text = { Text("Any type") }, onClick = { filters = filters.copy(type = null); typeMenu = false })
                    listOf("creature", "instant", "sorcery", "artifact", "enchantment", "planeswalker", "land", "legendary").forEach { t ->
                        DropdownMenuItem(text = { Text(t.replaceFirstChar(Char::uppercaseChar)) }, onClick = { filters = filters.copy(type = t); typeMenu = false })
                    }
                }
            }
            "WUBRG".forEach { c ->
                FilterChip(c in filters.colors, { filters = filters.copy(colors = if (c in filters.colors) filters.colors - c else filters.colors + c, colorless = false) },
                    label = { Text(c.toString(), fontWeight = FontWeight.Bold) })
            }
            FilterChip(filters.colorless, { filters = filters.copy(colorless = !filters.colorless, colors = emptySet()) }, label = { Text("C") })
            var mvMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(filters.maxMv != null, { mvMenu = true }, label = { Text(filters.maxMv?.let { if (it >= 7) "MV 7+" else "MV ≤$it" } ?: "Mana value ▾") })
                DropdownMenu(mvMenu, { mvMenu = false }) {
                    DropdownMenuItem(text = { Text("Any") }, onClick = { filters = filters.copy(maxMv = null); mvMenu = false })
                    (1..6).forEach { n -> DropdownMenuItem(text = { Text("$n or less") }, onClick = { filters = filters.copy(maxMv = n); mvMenu = false }) }
                    DropdownMenuItem(text = { Text("7 or more") }, onClick = { filters = filters.copy(maxMv = 7); mvMenu = false })
                }
            }
            TextButton(onClick = { showHelp = !showHelp }) { Text(if (showHelp) "HIDE TIPS" else "SEARCH TIPS", color = Mint, fontSize = 11.sp) }
        }
        if (showHelp) Text(
            "t:creature · o:draw · c:ub (colors) · id:ub (fits commander) · mv<=3 · pow>=4 · k:flying · r:mythic · s:cmm · is:legendary · is:commander · -t:land to exclude. Combine freely.",
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        val r = result
        when {
            r == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Mint) }
            r.cards.isEmpty() -> Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("No playable cards match.", color = SoftWhite, fontWeight = FontWeight.Bold)
                Text(if (filterQuery.isNotBlank()) "Try removing a filter chip. Only cards this build of Arcana can play are listed." else "Only cards this build of Arcana can play are listed.", color = Muted, fontSize = 12.sp)
                r.errors.forEach { Text(it, color = CounterGold, fontSize = 12.sp) }
            }
            else -> {
                Text(buildString {
                    append(if (r.total > r.cards.size) "Showing ${r.cards.size} of ${r.total}" else "${r.total} card${if (r.total == 1) "" else "s"}")
                    if (r.fuzzy) append(" · closest names (check spelling)")
                }, color = if (r.fuzzy) CounterGold else Muted, fontSize = 11.sp)
                r.errors.forEach { Text(it, color = CounterGold, fontSize = 11.sp) }
                LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(r.cards, key = { it.name }) { card ->
                        val inDeck = draft.countOf(card.name) + if (draft.commanders.any { it.equals(card.name, true) }) 1 else 0
                        LibraryRow(card, inDeck, canCommand = CardLibrary.isCommanderEligible(card),
                            onAdd = { onAdd(card.name) }, onCommander = { onCommander(card.name) }, onPreview = { onPreview(card.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(card: SupportedCard, inDeck: Int, canCommand: Boolean, onAdd: () -> Unit, onCommander: () -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).combinedClickable(onClick = onPreview, onLongClick = onPreview).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        card.imageUrl?.let { CardArt(it, Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop, fallbackName = card.name, fallbackType = card.typeLine) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(card.manaCost.ifBlank { "—" }, card.typeLine).joinToString("  ·  "), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card.custom) Text("Arcana custom card", color = Muted, fontSize = 10.sp)
        }
        if (inDeck > 0) Text("×$inDeck", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
        if (canCommand) TextButton(onClick = onCommander, modifier = Modifier.heightIn(min = 48.dp)) { Text("CMDR", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        IconButton(onClick = onAdd, modifier = Modifier.size(48.dp).testTag("add-${card.name}")) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Add ${card.name}", tint = Ink) }
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
    onReplace: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var grouping by rememberSaveable { mutableStateOf(DeckGrouping.TYPE) }
    val groups = remember(draft, grouping) { groupDeck(draft, cat, grouping) }
    val flagged = remember(issues) { issues.filter { it.level == IssueLevel.ERROR }.flatMap { it.cards }.map { it.lowercase() }.toSet() }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        item {
            Text("COMMANDER", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
            if (draft.commanders.isEmpty()) Text("No commander yet. Tap CMDR on a legendary creature in the library, or use a card's menu here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            draft.commanders.forEach { name ->
                val card = cat.find(name)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Panel).clickable { onPreview(name) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    card?.imageUrl?.let { CardArt(it, Modifier.size(width = 52.dp, height = 72.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Crop, fallbackName = name) }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(card?.let { "${it.manaCost} · ${identityLabel(it.colors)}" } ?: "Not in this build's library", color = if (card == null) CounterGold else Muted, fontSize = 11.sp)
                    }
                    if (card == null) TextButton(onClick = { onReplace(name) }) { Text("REPLACE", color = Mint, fontSize = 11.sp) }
                    TextButton(onClick = { onRemoveCommander(name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("TO DECK", color = Muted, fontSize = 11.sp) }
                }
            }
        }
        item { IssueSummary(issues) }
        if (showStatsInline) item { MiniStats(stats) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("GROUP", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                DeckGrouping.entries.forEach { g -> FilterChip(grouping == g, { grouping = g }, label = { Text(g.label) }) }
            }
        }
        if (draft.main.isEmpty()) item { Text("The deck is empty. Add cards from the library.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp)) }
        groups.forEach { (group, lines) ->
            item(key = "g-$group") {
                Text("${group.uppercase()} · ${lines.sumOf { it.count }}", color = if (group == "Not in library") CounterGold else Mint, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            }
            items(lines, key = { "c-${it.name}" }) { line ->
                DeckRow(line, cat.find(line.name), line.name.lowercase() in flagged, canCommand = cat.find(line.name)?.let { CardLibrary.isCommanderEligible(it) } == true,
                    hasOneCommander = draft.commanders.size == 1,
                    onCount = { onCount(line.name, it) }, onCommander = { onCommander(line.name) }, onPartner = { onPartner(line.name) },
                    onSideboard = { onSideboard(line.name) }, onReplace = { onReplace(line.name) }, onPreview = { onPreview(line.name) })
            }
        }
        if (draft.sideboard.isNotEmpty()) {
            item { Text("SIDEBOARD · ${draft.sideboard.sumOf { it.count }} (not in the 100)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp)) }
            items(draft.sideboard, key = { "s-${it.name}" }) { line ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${line.count}× ${line.name}", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onMain(line.name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("TO DECK", color = Mint, fontSize = 11.sp) }
                }
            }
        }
        draft.companion?.let { c -> item { Text("COMPANION · $c (saved; not usable in games yet)", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) } }
        if (draft.unreadLines.isNotEmpty()) item {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF392B21)).padding(10.dp)) {
                Text("LINES THE IMPORTER COULDN'T READ · kept in the saved list", color = CounterGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                draft.unreadLines.forEach { Text(it, color = SoftWhite, fontSize = 12.sp) }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun DeckRow(line: DeckLine, card: SupportedCard?, flagged: Boolean, canCommand: Boolean, hasOneCommander: Boolean,
                    onCount: (Int) -> Unit, onCommander: () -> Unit, onPartner: () -> Unit, onSideboard: () -> Unit, onReplace: () -> Unit, onPreview: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(if (flagged) Color(0xFF392B21) else Panel)
        .border(if (flagged) 1.5.dp else 0.dp, if (flagged) CounterGold else Color.Transparent, RoundedCornerShape(8.dp))
        .clickable(onClick = onPreview).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onCount(line.count - 1) }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Remove, "Remove one ${line.name}", tint = Muted) }
        Text("${line.count}", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.widthIn(min = 18.dp))
        IconButton(onClick = { onCount(line.count + 1) }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Add, "Add one ${line.name}", tint = Muted) }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text(line.name, color = SoftWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card == null) Text("Not in this build's library · Arcana can't play it", color = CounterGold, fontSize = 10.sp)
            else Text(card.manaCost.ifBlank { card.typeLine.substringBefore(" —") }, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        if (card == null) TextButton(onClick = onReplace, modifier = Modifier.heightIn(min = 44.dp)) { Text("REPLACE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.MoreVert, "More for ${line.name}", tint = Muted) }
            DropdownMenu(menu, { menu = false }) {
                if (canCommand) DropdownMenuItem(text = { Text("Make commander") }, onClick = { menu = false; onCommander() })
                if (canCommand && hasOneCommander) DropdownMenuItem(text = { Text("Add as second commander") }, onClick = { menu = false; onPartner() })
                DropdownMenuItem(text = { Text("Move to sideboard") }, onClick = { menu = false; onSideboard() })
                DropdownMenuItem(text = { Text("Suggest replacements") }, onClick = { menu = false; onReplace() })
                DropdownMenuItem(text = { Text("Remove all copies") }, onClick = { menu = false; onCount(0) })
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
        Text(when {
            errors.isEmpty() && warnings.isEmpty() -> "✓ Commander-legal and every card is in this build's library"
            errors.isEmpty() -> "✓ Legal · ${warnings.size} note${if (warnings.size == 1) "" else "s"}"
            else -> "${errors.size} thing${if (errors.size == 1) "" else "s"} to fix before this deck can be played"
        }, color = if (errors.isEmpty()) Mint else CounterGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (expanded || errors.size <= 2) (errors + warnings).forEach { issue ->
            Text("${if (issue.level == IssueLevel.ERROR) "•" else "◦"} ${issue.message}", color = SoftWhite, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
            if (issue.cards.isNotEmpty() && issue.cards.size <= 8 && expanded) Text(issue.cards.joinToString(", "), color = Muted, fontSize = 11.sp)
        } else Text("Tap to show", color = Muted, fontSize = 11.sp)
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
        Text("DECK STATS", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StatNumber("Cards", s.total); StatNumber("Creatures", s.creatures); StatNumber("Lands", s.lands)
            StatNumber("Avg MV", String.format("%.2f", s.averageManaValue))
        }
        Text("MANA CURVE (nonland)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        CurveBars(s.curve, Modifier.fillMaxWidth().height(110.dp), labels = true)
        Text("COLOR SYMBOLS IN COSTS · identity ${identityLabel(s.identity)}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val totalPips = s.pips.values.sum().coerceAtLeast(1)
        s.pips.filter { it.value > 0 }.forEach { (c, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.toString(), color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(PanelLight)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(n / totalPips.toFloat()).background(pipColor(c)))
                }
                Text(" $n", color = Muted, fontSize = 11.sp, modifier = Modifier.width(36.dp))
            }
        }
        Text("BY TYPE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        DECK_GROUP_ORDER.mapNotNull { g -> s.byType[g]?.let { g to it } }.forEach { (g, n) -> Text("$g  $n", color = SoftWhite, fontSize = 12.sp) }
        if (s.unsupported > 0) Text("${s.unsupported} card${if (s.unsupported == 1) "" else "s"} not in this build's library (not counted above)", color = CounterGold, fontSize = 12.sp)
        IssueSummary(issues)
    }
}

@Composable
private fun StatNumber(label: String, value: Any) {
    Column { Text("$value", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(label, color = Muted, fontSize = 10.sp) }
}

@Composable
private fun CurveBars(curve: List<Int>, modifier: Modifier, labels: Boolean = false) {
    val max = (curve.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        curve.forEachIndexed { i, n ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (labels) Text("$n", color = Muted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().fillMaxHeight((n / max.toFloat()).coerceAtLeast(0.02f) * if (labels) 0.75f else 1f).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Mint))
                if (labels) Text(if (i == 7) "7+" else "$i", color = SoftWhite, fontSize = 10.sp)
            }
        }
    }
}

private fun pipColor(c: Char) = when (c) { 'W' -> Color(0xFFF2E8C9); 'U' -> Color(0xFF6FA8DC); 'B' -> Color(0xFF8E7F99); 'R' -> Color(0xFFE07A5F); 'G' -> Color(0xFF6DBE7B); else -> Color.Gray }

// ---- Card preview & replacements ------------------------------------------------------------

@Composable
private fun CardPreviewDialog(name: String, cat: SupportedCardCatalog, draft: DeckDraft, onAdd: () -> Unit, onCommander: () -> Unit, onDismiss: () -> Unit) {
    val card = cat.find(name)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                card?.imageUrl?.let { CardArt(it, Modifier.fillMaxWidth().aspectRatio(0.716f).clip(RoundedCornerShape(10.dp)), fallbackName = card.name, fallbackType = card.typeLine) }
                Text(card?.name ?: name, color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                if (card == null) Text("This card isn't in this build's library, so Arcana can't play it. Use REPLACE in the deck list.", color = CounterGold, fontSize = 13.sp)
                else {
                    Text("${card.manaCost.ifBlank { "No mana cost" }}  ·  ${card.typeLine}", color = Muted, fontSize = 12.sp)
                    card.oracleText?.let { Text(it, color = SoftWhite, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
                    if (card.power != null) Text("${card.power}/${card.toughness}", color = SoftWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text(listOfNotNull("Identity ${identityLabel(card.colors)}", card.setCode, card.rarity?.lowercase()?.replaceFirstChar(Char::uppercaseChar),
                        if (card.custom) "Arcana custom card" else "Argentum library", "in library, not gameplay-verified").joinToString(" · "),
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (CardLibrary.isCommanderEligible(card) && draft.commanders.none { it.equals(card.name, true) })
                            OutlinedButton(onClick = { onCommander(); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("MAKE COMMANDER", color = CounterGold) }
                        Button(onClick = { onAdd() }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
                            Text("ADD${draft.countOf(card.name).takeIf { it > 0 }?.let { " (×$it)" }.orEmpty()}")
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("CLOSE", color = Muted) }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f), shape = RoundedCornerShape(16.dp), color = Panel) {
            Column(Modifier.padding(14.dp)) {
                Text("REPLACE ${name.uppercase()}", color = SoftWhite, fontWeight = FontWeight.Black)
                Text("Suggestions come only from cards Arcana can play. Nothing changes until you pick one.", color = Muted, fontSize = 12.sp)
                if (status.isNotBlank()) Text(status, color = CounterGold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions, key = { "s-${it.card.name}" }) { s ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(PanelLight).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.card.name, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${s.card.manaCost} · ${s.card.typeLine}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Why: " + s.reasons.joinToString(" · "), color = Mint, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            TextButton(onClick = { onChoose(s.card.name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("USE", color = Mint, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (original != null && suggestions.isEmpty()) item { Text("No close match fits this deck's colors. Search below.", color = Muted, fontSize = 12.sp) }
                    item {
                        OutlinedTextField(manual, { manual = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Or search the library") })
                    }
                    items(manualResults, key = { "m-${it.name}" }) { c ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(PanelLight).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${c.name}  ·  ${c.manaCost}", color = SoftWhite, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onChoose(c.name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("USE", color = Mint) }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("KEEP ${name.uppercase()}", color = Muted) }
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
