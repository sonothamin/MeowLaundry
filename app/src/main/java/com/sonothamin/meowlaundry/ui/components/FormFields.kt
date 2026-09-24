package com.sonothamin.meowlaundry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * Read-only currency dropdown. [compact] shows just the code ("USD") for tight rows;
 * otherwise "USD · US Dollar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Currency",
    compact: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(selected) { Currencies.options(selected) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = if (compact) selected else Currencies.label(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text("${Currencies.symbol(code)}  ${Currencies.label(code)}") },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}
