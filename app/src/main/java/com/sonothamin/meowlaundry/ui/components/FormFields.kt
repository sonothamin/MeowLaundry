package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sonothamin.meowlaundry.data.Currencies
import com.sonothamin.meowlaundry.data.Suggestions

/** Small round colour swatch, outlined so white/cream stay visible on light surfaces. */
@Composable
fun ColorDot(argb: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/**
 * Free-text field with a suggestion menu. Suggestions narrow as you type (prefix matches first);
 * anything typed is accepted, so the list never blocks entering something new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    leadingIcon: @Composable (() -> Unit)? = null,
    itemLeadingIcon: (@Composable (String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.trim()
    val filtered = remember(query, suggestions) {
        suggestions
            .filter { !it.equals(query, ignoreCase = true) && (query.isEmpty() || it.contains(query, ignoreCase = true)) }
            .sortedBy { if (it.startsWith(query, ignoreCase = true)) 0 else 1 }
            .take(8)
    }
    val showMenu = expanded && filtered.isNotEmpty()

    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            placeholder = placeholder?.let { hint -> @Composable { Text(hint) } },
            leadingIcon = leadingIcon,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    leadingIcon = itemLeadingIcon?.let { icon -> @Composable { icon(suggestion) } },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Colour input with swatches for known colours. */
@Composable
fun ColorField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    SuggestionTextField(
        value = value,
        onValueChange = onValueChange,
        label = "Color",
        placeholder = "e.g. Navy",
        suggestions = suggestions,
        modifier = modifier,
        leadingIcon = Suggestions.colorArgb(value)?.let { argb -> @Composable { ColorDot(argb) } },
        itemLeadingIcon = { name -> Suggestions.colorArgb(name)?.let { ColorDot(it) } },
    )
}

/**
 * Read-only currency dropdown. The field shows just the 3-letter code ("USD"); the menu lists each
 * option as a symbol column plus its code, with the current one highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Currency",
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(selected) { Currencies.options(selected) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                val isSelected = code == selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Symbol column keeps the codes aligned; blank when the symbol is just the code again.
                            Text(
                                Currencies.symbolOrNull(code).orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                            )
                            Text(
                                code,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else null,
                            )
                        }
                    },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** One cell of a [SuggestionFieldGroup]. */
data class GroupedField(
    val value: String,
    val onValueChange: (String) -> Unit,
    val label: String,
    val placeholder: String,
    val suggestions: List<String>,
    /** Show a colour swatch beside the text when the value is a known colour. */
    val showColorDot: Boolean = false,
)

/**
 * Several short suggestion inputs joined into ONE outlined control, side by side with hairline
 * dividers. Each cell keeps its own label and suggestion menu; the shared outline highlights when
 * any cell has focus.
 */
@Composable
fun SuggestionFieldGroup(
    fields: List<GroupedField>,
    modifier: Modifier = Modifier,
) {
    var focusedCount by remember { mutableStateOf(0) }
    val focused = focusedCount > 0
    val outline = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .border(if (focused) 2.dp else 1.dp, outline, MaterialTheme.shapes.extraSmall),
    ) {
        fields.forEachIndexed { index, field ->
            if (index > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GroupedSuggestionCell(
                field = field,
                onFocusChange = { hasFocus -> focusedCount = (focusedCount + if (hasFocus) 1 else -1).coerceAtLeast(0) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GroupedSuggestionCell(
    field: GroupedField,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val query = field.value.trim()
    val filtered = remember(query, field.suggestions) {
        field.suggestions
            .filter { !it.equals(query, ignoreCase = true) && (query.isEmpty() || it.contains(query, ignoreCase = true)) }
            .sortedBy { if (it.startsWith(query, ignoreCase = true)) 0 else 1 }
            .take(8)
    }
    val showMenu = hasFocus && filtered.isNotEmpty()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    focusRequester.requestFocus()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                field.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (hasFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (field.showColorDot) {
                    Suggestions.colorArgb(field.value)?.let { ColorDot(it, Modifier.size(14.dp)) }
                }
                BasicTextField(
                    value = field.value,
                    onValueChange = field.onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused != hasFocus) {
                                hasFocus = it.isFocused
                                onFocusChange(it.isFocused)
                            }
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.value.isEmpty()) {
                                Text(
                                    field.placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        // Non-focusable popup so the keyboard and caret stay in the text field while suggestions show.
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = {},
            properties = PopupProperties(focusable = false),
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    leadingIcon = if (field.showColorDot) {
                        Suggestions.colorArgb(suggestion)?.let { argb -> @Composable { ColorDot(argb) } }
                    } else null,
                    onClick = { field.onValueChange(suggestion) },
                )
            }
        }
    }
}
