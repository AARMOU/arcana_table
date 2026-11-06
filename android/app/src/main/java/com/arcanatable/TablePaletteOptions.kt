package com.arcanatable

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** Exact seven-role palettes from arcana-layout-studio.html (not approximate theme variants). */
internal data class TablePalettePreset(val name: String, val colors: TableColors)

internal val tablePalettePresets = listOf(
    TablePalettePreset("Arcana Original", TableColors(
        Color(0xFF101615), Color(0xFF192220), Color(0xFF26332F), Color(0xFF89E3B0),
        Color(0xFFEAF1ED), Color(0xFFA4B1AB), Color(0xFFFFD166))),
    TablePalettePreset("Slate", TableColors(
        Color(0xFF101416), Color(0xFF1C2527), Color(0xFF303A3D), Color(0xFFB6D5D2),
        Color(0xFFF0F5F3), Color(0xFF9EADAA), Color(0xFFD7C49A))),
    TablePalettePreset("Monochrome", TableColors(
        Color(0xFF0B0D0C), Color(0xFF1A1C1B), Color(0xFF2B302D), Color(0xFFF2F2EB),
        Color(0xFFF8F9F7), Color(0xFFA4A9A4), Color(0xFFC2C3BB))),
    TablePalettePreset("Deep Ocean", TableColors(
        Color(0xFF0B1419), Color(0xFF17252B), Color(0xFF26383E), Color(0xFF85C6CF),
        Color(0xFFE9F2F4), Color(0xFF9BB2B8), Color(0xFFD9C28C))),
)
internal const val CUSTOM_TABLE_PALETTE = "Custom"
internal const val TABLE_PALETTE_PREF = "ui_palette_name"
internal const val TABLE_CUSTOM_READY_PREF = "ui_custom_palette_initialized"
internal val tableColorRoles = listOf(
    "background" to "Canvas",
    "panel" to "Surface",
    "raised" to "Raised controls",
    "accent" to "Accent / borders",
    "text" to "Primary text",
    "muted" to "Secondary text",
    "counter" to "Gold / active phase",
)

internal fun tablePaletteByName(name: String): TableColors =
    tablePalettePresets.firstOrNull { it.name == name }?.colors ?: darkTableColors

internal fun TableColors.colorRole(role: String): Color = when (role) {
    "background" -> background
    "panel" -> panel
    "raised" -> raised
    "accent" -> accent
    "text" -> text
    "muted" -> muted
    "counter" -> counter
    else -> error("Unrecognized palette color role")
}

internal fun TableColors.withColorRole(role: String, color: Color): TableColors = when (role) {
    "background" -> copy(background = color)
    "panel" -> copy(panel = color)
    "raised" -> copy(raised = color)
    "accent" -> copy(accent = color)
    "text" -> copy(text = color)
    "muted" -> copy(muted = color)
    "counter" -> copy(counter = color)
    else -> error("Unrecognized palette color role")
}

internal fun tableColorHex(color: Color): String =
    String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)

internal fun parseTableColorHex(hex: String): Color? {
    if (!Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) return null
    return Color(0xFF000000.toInt() or hex.substring(1).toInt(16))
}

internal fun readCustomTablePalette(prefs: SharedPreferences): TableColors {
    var colors = darkTableColors
    tableColorRoles.forEach { (role, _) ->
        val persisted = prefs.getString("ui_custom_" + role, null)
        val parsed = persisted?.let(::parseTableColorHex)
        if (parsed != null) colors = colors.withColorRole(role, parsed)
    }
    return colors
}

internal fun SharedPreferences.Editor.writeCustomTablePalette(colors: TableColors): SharedPreferences.Editor {
    tableColorRoles.forEach { (role, _) -> putString("ui_custom_" + role, tableColorHex(colors.colorRole(role))) }
    return putBoolean(TABLE_CUSTOM_READY_PREF, true)
}

/** Root screen owns persistence; editor controls only communicate through the composition local. */
internal data class TablePaletteEditor(
    val selectedName: String,
    val selectedColors: TableColors,
    val customColors: TableColors,
    val onSelect: (String) -> Unit,
    val onCustomColor: (String, Color) -> Unit,
    val onStudioTheme: (TableColors) -> Unit = {},
)
internal val LocalTablePaletteEditor = compositionLocalOf<TablePaletteEditor?> { null }

@Composable
internal fun TablePalettePicker() {
    val editor = LocalTablePaletteEditor.current ?: return
    var editedRole by remember { mutableStateOf<String?>(null) }
    val tiles = tablePalettePresets + TablePalettePreset(CUSTOM_TABLE_PALETTE, editor.customColors)
    Column(Modifier.fillMaxWidth()) {
        Text("COLOR PALETTE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = .8.sp, modifier = Modifier.padding(top = 13.dp, bottom = 3.dp))
        Text("Tap to apply to the table immediately. Your selection is saved.",
            color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pair.forEach { tile ->
                    val selected = tile.name == editor.selectedName
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tile.colors.panel)
                            .clickable { editor.onSelect(tile.name) }
                            .then(Modifier),
                    ) {
                        Column(Modifier.fillMaxWidth().then(
                            Modifier.padding(1.dp).clip(RoundedCornerShape(9.dp))
                                .background(tile.colors.panel)
                        ).padding(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tile.name, color = tile.colors.text, fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp, modifier = Modifier.weight(1f))
                                if (selected) Text("✓", color = tile.colors.accent, fontWeight = FontWeight.Black)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(tile.colors.background, tile.colors.panel, tile.colors.raised, tile.colors.accent,
                                    tile.colors.text, tile.colors.muted, tile.colors.counter).forEach { color ->
                                    Box(Modifier.weight(1f).height(19.dp).clip(RoundedCornerShape(3.dp)).background(color))
                                }
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (editor.selectedName == CUSTOM_TABLE_PALETTE) {
            Text("CUSTOM COLORS · TAP A SWATCH TO EDIT", color = Muted, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
            tableColorRoles.forEach { (role, label) ->
                val color = editor.customColors.colorRole(role)
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clickable { editedRole = role },
                    shape = RoundedCornerShape(8.dp),
                    color = PanelLight,
                ) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(color)
                            .then(Modifier))
                        Text(label, color = SoftWhite, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 10.dp).weight(1f))
                        Text(tableColorHex(color), color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    editedRole?.let { role ->
        TableRoleColorDialog(role, editor.customColors.colorRole(role),
            onColor = { editor.onCustomColor(role, it) },
            onDismiss = { editedRole = null })
    }
}

@Composable
private fun TableRoleColorDialog(role: String, current: Color, onColor: (Color) -> Unit, onDismiss: () -> Unit) {
    var hexText by remember(role) { mutableStateOf(tableColorHex(current)) }
    LaunchedEffect(current) { hexText = tableColorHex(current) }
    val label = tableColorRoles.first { it.first == role }.second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PICK COLOR · " + label) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(9.dp)).background(current))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { typed ->
                        hexText = typed.take(7)
                        parseTableColorHex(hexText)?.let(onColor)
                    },
                    singleLine = true,
                    label = { Text("HEX COLOR · #RRGGBB") },
                    isError = hexText.length == 7 && parseTableColorHex(hexText) == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                listOf(
                    Triple("RED", current.red, 0),
                    Triple("GREEN", current.green, 1),
                    Triple("BLUE", current.blue, 2),
                ).forEach { (channel, value, index) ->
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(channel, fontSize = 10.sp, modifier = Modifier.width(57.dp))
                        Slider(value = value, onValueChange = { n ->
                            val c = when (index) {
                                0 -> Color(n, current.green, current.blue)
                                1 -> Color(current.red, n, current.blue)
                                else -> Color(current.red, current.green, n)
                            }
                            onColor(c)
                        }, modifier = Modifier.weight(1f))
                        Text((value * 255).roundToInt().toString(), fontSize = 11.sp,
                            modifier = Modifier.width(32.dp))
                    }
                }
                Text("Changes are applied and saved instantly.", fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
    )
}
