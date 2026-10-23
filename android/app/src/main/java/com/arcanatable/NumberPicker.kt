package com.arcanatable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keeps a typed or stepped number inside [range]. */
fun clampToRange(value: Int, range: IntRange): Int = value.coerceIn(range.first, maxOf(range.first, range.last))

/**
 * A number chooser for X costs and "choose a number" prompts: −5 / −1 / +1 / +5, and tap the number
 * to type any value. Whatever is typed is held to [range] (Argentum's own limits).
 */
@Composable
fun NumberPicker(value: Int, range: IntRange, onChange: (Int) -> Unit, label: String? = null) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(editing) { mutableStateOf(value.toString()) }
    Column {
        if (label != null) Text(label, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(enabled = value > range.first, onClick = { onChange(clampToRange(value - 5, range)) }, modifier = Modifier.widthIn(min = 44.dp)) { Text("−5", fontSize = 14.sp) }
            TextButton(enabled = value > range.first, onClick = { onChange(clampToRange(value - 1, range)) }, modifier = Modifier.widthIn(min = 44.dp)) { Text("−", fontSize = 20.sp) }
            if (editing) OutlinedTextField(
                value = text,
                onValueChange = { typed ->
                    text = typed.filter(Char::isDigit).take(4)
                    text.toIntOrNull()?.let { onChange(clampToRange(it, range)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(76.dp).testTag("number-picker-field"),
            ) else Text(
                "$value", color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 48.dp).clickable { editing = true }.padding(horizontal = 8.dp, vertical = 6.dp).testTag("number-picker-value"),
            )
            TextButton(enabled = value < range.last, onClick = { onChange(clampToRange(value + 1, range)) }, modifier = Modifier.widthIn(min = 44.dp)) { Text("+", fontSize = 20.sp) }
            TextButton(enabled = value < range.last, onClick = { onChange(clampToRange(value + 5, range)) }, modifier = Modifier.widthIn(min = 44.dp)) { Text("+5", fontSize = 14.sp) }
        }
        Text(if (editing) "Type a number from ${range.first} to ${range.last}." else "Tap the number to type it · ${range.first}–${range.last}", color = Muted, fontSize = 10.sp)
    }
}
