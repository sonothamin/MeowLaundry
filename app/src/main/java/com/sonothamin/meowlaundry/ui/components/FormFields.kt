package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.sonothamin.meowlaundry.ui.theme.Spacing

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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                // The popup below is non-focusable (so it can't dismiss itself); close it when the field loses focus.
                .onFocusChanged { if (!it.isFocused) expanded = false },
        )
        // A focusable popup steals focus from the text field every time it appears, which dismissed the
        // keyboard on each keystroke that changed the suggestions. Non-focusable keeps typing uninterrupted.
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
            properties = PopupProperties(focusable = false),
        ) {
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

/**
 * Currency selector styled as a filled tonal field (MD3 Expressive favours colour and depth over
 * thin outlines for a control this important). Tapping it opens [CurrencyPickerSheet], a searchable
 * bottom sheet — replacing the old bare dropdown list, which just dumped forty options on screen
 * with no way to filter and no visual hierarchy.
 */
@Composable
fun CurrencyDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Currency",
) {
    var showSheet by remember { mutableStateOf(false) }

    Surface(
        onClick = { showSheet = true },
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CurrencyBadge(code = selected)
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    selected,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSheet) {
        CurrencyPickerSheet(
            selected = selected,
            onSelect = {
                onSelect(it)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

/** Small tonal circle carrying a currency's symbol (or its first letter, as a fallback). */
@Composable
private fun CurrencyBadge(code: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Currencies.symbolOrNull(code) ?: code.take(1),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Full-height searchable currency picker. Filters by code or display name as you type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val options = remember(selected) { Currencies.options(selected) }
    val filtered = remember(query, options) {
        val q = query.trim()
        if (q.isEmpty()) options
        else options.filter {
            it.contains(q, ignoreCase = true) || Currencies.displayName(it).contains(q, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            Text(
                "Choose a currency",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by code or name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it }) { code ->
                    val isSelected = code == selected
                    Surface(
                        onClick = { onSelect(code) },
                        shape = MaterialTheme.shapes.large,
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CurrencyBadge(code = code, filled = isSelected)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    Currencies.displayName(code),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No currencies match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.lg),
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
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
