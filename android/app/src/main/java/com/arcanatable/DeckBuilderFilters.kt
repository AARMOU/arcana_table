@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arcanatable

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Keeps the deck builder's filters across rotation and folding. */
internal val CardFiltersSaver = Saver<CardFilters, String>(save = { it.encode() }, restore = { CardFilters.decode(it) })

// ---- Saved filter sets (on this phone only) ---------------------------------------------------

internal object FilterPresetStore {
    private const val PREFS = "arcana-deck-builder"
    private const val KEY = "filterPresets"

    fun load(context: Context): List<FilterPreset> = runCatching {
        FilterPresets.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty())
    }.getOrDefault(emptyList())

    fun save(context: Context, presets: List<FilterPreset>) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, FilterPresets.encode(presets)).apply() }
    }
}

// ---- The FILTERS panel ------------------------------------------------------------------------

/**
 * Full-screen filter panel, laid out like a classic card-search form but in Arcana's table colors.
 * Changes apply as you make them; the button at the bottom shows how many cards match.
 */
@Composable
internal fun CardFilterSheet(
    lib: CardLibrary,
    baseQuery: String,
    filters: CardFilters,
    onChange: (CardFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var presets by remember { mutableStateOf(FilterPresetStore.load(context)) }
    var loadMenu by remember { mutableStateOf(false) }
    var savingPreset by remember { mutableStateOf(false) }
    var matchCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(baseQuery, filters) {
        delay(150)
        matchCount = withContext(Dispatchers.Default) { lib.search(baseQuery, filters, limit = 1).total }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to results", tint = SoftWhite) }
                    Text("FILTERS", color = CounterGold, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { loadMenu = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("filters-load")) {
                            Text("LOAD", color = Mint, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(loadMenu, { loadMenu = false }) {
                            if (presets.isEmpty()) DropdownMenuItem(text = { Text("No saved filters yet. Use SAVE below.", color = Muted) }, onClick = { loadMenu = false })
                            presets.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = { onChange(p.filters); loadMenu = false },
                                    trailingIcon = {
                                        IconButton(onClick = { presets = presets - p; FilterPresetStore.save(context, presets) }) { Icon(Icons.Filled.Close, "Delete saved filter ${p.name}", tint = Muted) }
                                    })
                            }
                        }
                    }
                    TextButton(onClick = { onChange(filters.cleared()) }, enabled = filters.activeCount > 0, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("CLEAR ALL", color = if (filters.activeCount > 0) CounterGold else Muted, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    TermSection("Oracle text", "Draw a card, Flying", "Every term must be in the rules text. Separate several with commas.",
                        filters.oracleTerms, { onChange(filters.copy(oracleTerms = it)) }, tag = "filter-oracle")
                    TermSection("Type line", "Legendary, Creature, Angel", "Every term must be in the type line.",
                        filters.typeTerms, { onChange(filters.copy(typeTerms = it)) }, tag = "filter-type")
                    TermSection("Mana cost", "2{G}{W}  or  2GW", "Cards whose cost includes these symbols.",
                        filters.manaCosts, { onChange(filters.copy(manaCosts = it)) }, tag = "filter-mana",
                        validate = { if (ManaCost.parse(it) == null) "Use symbols like 2{G}{W}, {X}{R} or 2GW." else null })
                    ColorSection(filters, onChange)
                    TermSection("Keyword", "Flying, Trample", "Keyword abilities the card has.",
                        filters.keywords, { onChange(filters.copy(keywords = it)) }, tag = "filter-keyword")
                    SetSection(lib, filters, onChange)
                    RaritySection(filters, onChange)
                    StatsSection(filters, onChange)
                    Column {
                        SectionLabel("Legality")
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onChange(filters.copy(commanderLegalOnly = !filters.commanderLegalOnly)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Commander-legal only", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Hides banned cards. Arcana custom cards have no legality data and always show.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            Switch(filters.commanderLegalOnly, { onChange(filters.copy(commanderLegalOnly = it)) }, modifier = Modifier.testTag("filter-legal"),
                                colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Mint))
                        }
                    }
                    SortSection(filters, onChange)
                    Text("Price, artist and finish filters aren't here: Arcana's offline library has one printing per card and no price data.",
                        color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { savingPreset = true }, enabled = filters.activeCount > 0 || filters.sort != CardSort.NAME,
                        modifier = Modifier.heightIn(min = 52.dp)) { Text("SAVE", color = if (filters.activeCount > 0) SoftWhite else Muted, fontWeight = FontWeight.Bold) }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("filters-show"),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) {
                        Text(when (val n = matchCount) { null -> "SHOW CARDS"; 0 -> "NO MATCHES"; 1 -> "SHOW 1 CARD"; else -> "SHOW $n CARDS" }, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
    if (savingPreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { savingPreset = false }, title = { Text("Save these filters") },
            text = {
                Column {
                    Text(filters.summaryChips().joinToString(" · ") { it.first }.ifBlank { "Sort: ${filters.sort.label}" }, fontSize = 12.sp, color = Muted)
                    OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }, placeholder = { Text("Cheap ramp") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    if (presets.any { it.name.equals(name.trim(), true) }) Text("Replaces the saved filter with this name.", color = CounterGold, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                presets = FilterPresets.upsert(presets, FilterPreset(name.trim(), filters)); FilterPresetStore.save(context, presets); savingPreset = false
            }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { savingPreset = false }) { Text("CANCEL") } })
    }
}

@Composable
private fun SectionLabel(text: String, note: String? = null) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        note?.let { Text("  $it", color = Muted, fontSize = 11.sp) }
    }
}

/** A text field whose entries become removable chips. Commas split several entries at once. */
@Composable
private fun TermSection(
    label: String,
    placeholder: String,
    help: String,
    terms: List<String>,
    onTerms: (List<String>) -> Unit,
    tag: String,
    validate: (String) -> String? = { null },
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun commit() {
        val parts = input.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val bad = parts.firstNotNullOfOrNull(validate)
        if (bad != null) { error = bad; return }
        onTerms((terms + parts).distinctBy { it.lowercase() })
        input = ""; error = null
    }
    Column {
        SectionLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it; error = null }, Modifier.weight(1f).testTag(tag), singleLine = true,
                placeholder = { Text(placeholder, color = Muted) }, isError = error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commit() }))
            IconButton(onClick = ::commit, enabled = input.isNotBlank(), modifier = Modifier.size(52.dp).testTag("$tag-add")) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (input.isNotBlank()) Mint else PanelLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Add $label", tint = if (input.isNotBlank()) Ink else Muted)
                }
            }
        }
        Text(error ?: help, color = if (error != null) CounterGold else Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (terms.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            terms.forEach { t -> RemovableChip(t) { onTerms(terms - t) } }
        }
    }
}

@Composable
internal fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(PanelLight).border(1.dp, Mint.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        .clickable(onClick = onRemove).heightIn(min = 36.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SoftWhite, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        Icon(Icons.Filled.Close, "Remove $label", tint = Muted, modifier = Modifier.padding(start = 4.dp).size(16.dp))
    }
}

/** An outlined button that opens a menu of [options]; the current one shows with a ▾. */
@Composable
private fun <T> MenuButton(current: String, options: List<T>, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier),
            contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text(current, color = SoftWhite, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null, tint = SoftWhite)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(label(o)) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@Composable
private fun ColorSection(filters: CardFilters, onChange: (CardFilters) -> Unit) {
    Column {
        SectionLabel("Colors")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuButton(filters.colorMatch.label, ColorMatch.entries, { it.label }, { onChange(filters.copy(colorMatch = it)) }, tag = "filter-color-match")
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(filters.copy(useIdentity = !filters.useIdentity)) }.heightIn(min = 48.dp).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(filters.useIdentity, { onChange(filters.copy(useIdentity = it)) }, colors = CheckboxDefaults.colors(checkedColor = Mint, checkmarkColor = Ink))
                Text("Color identity", color = SoftWhite, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            "WUBRG".forEach { c ->
                ColorToggle(c.toString(), on = !filters.colorless && c in filters.colors) {
                    onChange(filters.copy(colorless = false, colors = if (c in filters.colors) filters.colors - c else filters.colors + c))
                }
            }
            ColorToggle("C", on = filters.colorless) { onChange(filters.copy(colorless = !filters.colorless, colors = emptySet())) }
        }
        Text("Number of colors", color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("0", color = Muted, fontSize = 13.sp)
            RangeSlider(
                value = filters.minColors.toFloat()..filters.maxColors.toFloat(),
                onValueChange = { r -> onChange(filters.copy(minColors = r.start.roundToInt(), maxColors = r.endInclusive.roundToInt())) },
                valueRange = 0f..5f, steps = 4,
                colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = PanelLight),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).testTag("filter-color-count"))
            Text("5", color = Muted, fontSize = 13.sp)
        }
        Text(when {
            filters.minColors == 0 && filters.maxColors == 5 -> "Any number of colors"
            filters.minColors == filters.maxColors -> "Exactly ${filters.minColors} color${if (filters.minColors == 1) "" else "s"}"
            else -> "${filters.minColors} to ${filters.maxColors} colors"
        } + if (filters.useIdentity) " in color identity" else "", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ColorToggle(symbol: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick).border(if (on) 3.dp else 0.dp, if (on) Mint else Color.Transparent, CircleShape)
        .alpha(if (on) 1f else 0.4f).testTag("filter-color-$symbol"), contentAlignment = Alignment.Center) {
        ManaPip(symbol)
    }
}

@Composable
private fun SetSection(lib: CardLibrary, filters: CardFilters, onChange: (CardFilters) -> Unit) {
    var input by remember { mutableStateOf("") }
    val suggestions = remember(input) {
        val q = input.trim().uppercase()
        if (q.isEmpty()) emptyList() else lib.setCodes.filter { it.startsWith(q) && it !in filters.sets.map(String::uppercase) }.take(8)
    }
    fun add(code: String) { onChange(filters.copy(sets = (filters.sets + code.uppercase()).distinct())); input = "" }
    Column {
        SectionLabel("Sets", "the printing Arcana ships")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuButton(if (filters.excludeSets) "≠" else "=", listOf(false, true), { if (it) "≠  Not in these sets" else "=  In these sets" },
                { onChange(filters.copy(excludeSets = it)) })
            OutlinedTextField(input, { input = it.take(6) }, Modifier.weight(1f).testTag("filter-set"), singleLine = true, placeholder = { Text("Set code, e.g. CMM", color = Muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { suggestions.firstOrNull()?.let(::add) ?: if (input.trim().uppercase() in lib.setCodes) add(input.trim()) else Unit }))
        }
        if (suggestions.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { code -> AssistChip(onClick = { add(code) }, label = { Text(code) }, modifier = Modifier.heightIn(min = 40.dp)) }
        } else if (input.isNotBlank()) Text("No set code starts with “${input.trim().uppercase()}”.", color = CounterGold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        if (filters.sets.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.sets.forEach { s -> RemovableChip(s.uppercase()) { onChange(filters.copy(sets = filters.sets - s)) } }
        }
    }
}

@Composable
private fun RaritySection(filters: CardFilters, onChange: (CardFilters) -> Unit) {
    Column {
        SectionLabel("Rarity")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardFilters.RARITY_ORDER.forEach { r ->
                val on = r in filters.rarities
                FilterChip(on, { onChange(filters.copy(rarities = if (on) filters.rarities - r else filters.rarities + r)) },
                    label = { Text(r.lowercase().replaceFirstChar(Char::uppercaseChar)) }, modifier = Modifier.heightIn(min = 44.dp).testTag("filter-rarity-$r"),
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(rarityColor(r))) })
            }
        }
    }
}

private fun rarityColor(r: String) = when (r) {
    "MYTHIC" -> Color(0xFFE0703A); "RARE" -> Color(0xFFD9B44A); "UNCOMMON" -> Color(0xFFAEB9C2); else -> Color(0xFF6B6F73)
}

@Composable
private fun StatsSection(filters: CardFilters, onChange: (CardFilters) -> Unit) {
    var stat by remember { mutableStateOf(StatField.MANA_VALUE) }
    var op by remember { mutableStateOf(StatOp.LTE) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val a = from.toDoubleOrNull()
    val b = to.toDoubleOrNull()
    val ready = a != null && (op != StatOp.BETWEEN || to.isBlank() || (b != null && b >= a))
    Column {
        SectionLabel("Stats", "(mana value, power, toughness)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton(stat.label, StatField.entries, { it.label }, { stat = it }, tag = "filter-stat")
            MenuButton(op.symbol, StatOp.entries, { if (it == StatOp.BETWEEN) "from … to" else it.symbol }, { op = it }, tag = "filter-stat-op")
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) }, Modifier.weight(1f).testTag("filter-stat-value"), singleLine = true,
                placeholder = { Text(if (op == StatOp.BETWEEN) "from" else "value", color = Muted) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (op == StatOp.BETWEEN) OutlinedTextField(to, { to = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("to (∞)", color = Muted) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = {
                val range = StatRange(stat, op, a ?: return@Button, if (op == StatOp.BETWEEN) b else null)
                onChange(filters.copy(stats = filters.stats.filterNot { it == range } + range)); from = ""; to = ""
            }, enabled = ready, modifier = Modifier.heightIn(min = 52.dp).testTag("filter-stat-add"),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("ADD", fontWeight = FontWeight.Bold) }
        }
        if (filters.stats.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.stats.forEach { s -> RemovableChip(s.label) { onChange(filters.copy(stats = filters.stats - s)) } }
        }
    }
}

@Composable
private fun SortSection(filters: CardFilters, onChange: (CardFilters) -> Unit) {
    Column {
        SectionLabel("Sort results")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuButton(filters.sort.label, CardSort.entries, { it.label }, { onChange(filters.copy(sort = it)) }, tag = "filter-sort")
            MenuButton(if (filters.descending) "↓ Descending" else "↑ Ascending", listOf(false, true), { if (it) "↓ Descending" else "↑ Ascending" },
                { onChange(filters.copy(descending = it)) })
        }
    }
}

// ---- Results as a card grid -------------------------------------------------------------------

/** Card pictures in a grid: tap to preview, the + adds a copy, ×N shows what's already in the deck. */
@Composable
internal fun LibraryGrid(
    cards: List<SupportedCard>,
    countInDeck: (SupportedCard) -> Int,
    state: LazyGridState,
    onAdd: (SupportedCard) -> Unit,
    onPreview: (SupportedCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(GridCells.Adaptive(104.dp), modifier, state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)) {
        items(cards, key = { it.name }) { card ->
            val inDeck = countInDeck(card)
            Column(Modifier.testTag("grid-${card.name}")) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.716f).clip(RoundedCornerShape(8.dp)).background(Panel)
                    .border(if (inDeck > 0) 2.dp else 0.dp, if (inDeck > 0) Mint else Color.Transparent, RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = { onPreview(card) }, onLongClick = { onPreview(card) })) {
                    val url = card.imageUrl
                    if (url != null) CardArt(url, Modifier.fillMaxSize(), ContentScale.Crop, fallbackName = card.name, fallbackType = card.typeLine)
                    else Text(card.name, color = SoftWhite, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                    if (inDeck > 0) Text("×$inDeck", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Mint).padding(horizontal = 6.dp, vertical = 2.dp))
                    IconButton(onClick = { onAdd(card) }, modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).testTag("grid-add-${card.name}")) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Mint).border(1.dp, Ink, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "Add ${card.name}", tint = Ink)
                        }
                    }
                }
                Text(card.name, color = SoftWhite, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
            }
        }
    }
}
