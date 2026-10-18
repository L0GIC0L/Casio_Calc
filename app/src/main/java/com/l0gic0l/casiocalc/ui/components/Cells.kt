package com.l0gic0l.casiocalc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.math.MathView

/** A table cell showing a value in natural display; highlighted while it is being edited. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ValueCell(
    value: Value?,
    formatter: Formatter,
    selected: Boolean,
    width: Dp,
    height: Dp = 48.dp,
    /** Show decimals instead of fractions, as list editors do. */
    decimal: Boolean = false,
    /** Where the cell is, e.g. "List 1, row 3"; read by TalkBack together with the value. */
    description: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val row: MathRow? = remember(value, formatter, decimal) {
        value?.let { formatter.format(it, if (decimal) Formatter.Style.DECIMAL else Formatter.Style.AUTO) }
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier
            .padding(2.dp)
            .size(width, height)
            .semantics {
                val text = row?.let { RowText.linear(it) } ?: "empty"
                contentDescription = if (description != null) "$description: $text" else text
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.padding(horizontal = 4.dp).horizontalScroll(rememberScrollState()), contentAlignment = Alignment.CenterEnd) {
            if (row != null) MathView(row, fontSize = 16.sp)
        }
    }
}

@Composable
fun HeaderCell(text: String, width: Dp, height: Dp = 36.dp) {
    Box(Modifier.padding(2.dp).size(width, height), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
}
