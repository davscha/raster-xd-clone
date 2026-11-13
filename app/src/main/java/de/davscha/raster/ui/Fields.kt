package de.davscha.raster.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun formatNum(v: Float): String {
    val r = (v * 10f).roundToInt() / 10f
    return if (r == r.roundToInt().toFloat()) r.roundToInt().toString() else r.toString().replace('.', ',')
}

/** Zahlenfeld (X, Y, Breite …). Übernimmt den Wert bei „Fertig“ oder wenn das Feld den Fokus verliert. */
@Composable
fun NumberField(label: String, value: Float, modifier: Modifier = Modifier, onCommit: (Float) -> Unit) {
    val shown = formatNum(value)
    var text by remember(shown) { mutableStateOf(shown) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun commit() {
        val v = text.replace(',', '.').toFloatOrNull() ?: run { text = shown; return }
        if (formatNum(v) != shown) onCommit(v)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.onFocusChanged {
            if (focused && !it.isFocused) commit()
            focused = it.isFocused
        },
    )
}

/** Textfeld, das direkt ins Modell schreibt. */
@Composable
fun TextInput(label: String, value: String, modifier: Modifier = Modifier, singleLine: Boolean = true, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 6,
        textStyle = TextStyle(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier,
    )
}

/** Farbfeld mit Beschriftung, öffnet den Farbwähler. */
@Composable
fun ColorButton(label: String, color: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorChip(
            color,
            Modifier
                .size(30.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
